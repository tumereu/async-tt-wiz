package arcanum.engine.review

import arcanum.api.CardDefinition
import arcanum.api.Zone
import arcanum.engine.Engine
import arcanum.engine.Host
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.decision.FilledObject
import arcanum.engine.decision.ManaUse
import arcanum.engine.decision.RandomKind
import arcanum.engine.decision.Share
import arcanum.engine.input.Attack
import arcanum.engine.input.Block
import arcanum.engine.input.Input
import arcanum.engine.setup.DeckCard
import arcanum.engine.setup.Seat
import arcanum.engine.setup.started
import arcanum.engine.state.CardId
import arcanum.engine.state.GameState
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import arcanum.engine.state.TargetRef
import kotlinx.serialization.json.Json
import kotlin.random.Random

/**
 * Review 3: a host written only against the public API (`Engine.newGame/options/perform/visible/result`, `GameState.apply`,
 * the decisions, answers and changes), as an integrator would write one. It keeps the full-information state ([truth])
 * and one state per player's view (D60), each built separately: every input chosen on the truth is translated to each
 * view and performed there too; random results are shared (a shuffle in a view gets new card ids, D60), host fills in a
 * view are answered from what the host knows. The state goes through JSON before every call (an async gap, D58).
 *
 * After every input it compares what everyone can see (turn, priority, life, zone sizes, the cards in public zones,
 * permanents' status, the Stack, the open decision) between the truth and each view, by the host's card mapping, and
 * collects what a host runs into as [findings]: inputs a view refuses, decisions that differ, fills only another
 * player may see asked in a view (D31), cards hidden from a view that it knows, options that differ.
 */
internal class FakeHost(
    val seed: Long,
    decks: Map<PlayerId, List<String>>,
    /** Compare each viewer's options with the truth's every this many inputs (0: never). */
    private val optionsEvery: Int = 0,
    private val maxInputs: Int = 4000,
    /** The host's workaround for opponents' plays from hidden hands: fill the card by hand before forwarding the input. */
    private val fillBeforePlay: Boolean = false,
) {
    private val rnd = Random(seed)
    private val json = Json
    private val ids = decks.keys.toList()
    private val defs = mutableMapOf<CardId, CardDefinition>()

    /** One player's view and the host's mapping of its card ids to the truth's (D60: shuffles give new ids). */
    inner class View(val player: PlayerId, var state: GameState) {
        val toTruth = mutableMapOf<CardId, CardId>()
        val fromTruth = mutableMapOf<CardId, CardId>()
        var diverged: String? = null
        var newCards = 0
        /** Cards the host filled by hand before a play (its workaround); a cancelled cast leaves them known in hand. */
        val filledByHost = mutableSetOf<CardId>()
        fun truthCard(c: CardId): CardId = toTruth[c] ?: c
        fun viewCard(c: CardId): CardId = fromTruth[c] ?: c
        fun obj(id: ObjectId): ObjectId = when (id) {
            is ObjectId.Card -> viewCard(id.card).let { vc -> state.current(vc)?.id ?: ObjectId.Card(vc, id.zoneChanges) }
            is ObjectId.StackItem -> id
        }
        fun ref(r: TargetRef): TargetRef = (r as? TargetRef.Object)?.let { TargetRef.Object(obj(it.id)) } ?: r
    }

    var truth: GameState
        private set
    val views: List<View>

    /** What the host ran into, by kind, with the first example of each. */
    val findings = linkedMapOf<String, Int>()
    val examples = mutableMapOf<String, String>()
    var inputs = 0
        private set
    var privateFillsInOtherViews = 0
        private set
    /** Answers to [Decision.OtherView] a view got: steps only another player sees, worked out as in their view (D31). */
    var otherViewAnswers = 0
        private set

    init {
        val seats = ids.map { p -> Seat(p, decks.getValue(p).mapIndexed { i, n -> DeckCard(CardId("${p.value}.$i"), Cards.definition(n)) }) }
        seats.forEach { s -> s.deck.forEach { defs[it.card] = it.definition!! } }
        truth = Engine.newGame(seats)
        views = ids.map { v ->
            // A viewer's game made by the public API: decks as unknown cards, the viewer set (D60)
            val state = Engine.newGame(seats.map { s -> s.copy(deck = s.deck.map { it.copy(definition = null) }) })
            state.viewer = v
            View(v, state)
        }
    }

    private fun note(kind: String, example: String) {
        findings[kind] = (findings[kind] ?: 0) + 1
        examples.getOrPut(kind) { "seed $seed input $inputs t${truth.turn.number} ${truth.turn.step}: $example" }
    }

    /** Plays until the game ends or [maxInputs]; returns the turns played. */
    fun play(): Int {
        while (Engine.result(truth) == null && inputs < maxInputs) step()
        return truth.turn.number
    }

    fun step() {
        val input = choose()
        val decisionBefore = truth.pending?.decision
        truth = perform(truth, input) ?: error("The truth refused its own option $input")
        inputs++
        if (truth.turn.number > 1 && inputs % 50 == 0) truth.forgetOlderTurns()
        views.filter { it.diverged == null }.forEach { v -> sync(v, input, decisionBefore) }
        views.filter { it.diverged == null }.forEach { v -> compare(v) }
        if (optionsEvery > 0 && inputs % optionsEvery == 0) views.filter { it.diverged == null }.forEach { compareOptions(it) }
    }

    /** Through JSON (the host stores the state between calls), then [Engine.perform]; the new state, or null if refused. */
    private fun perform(state: GameState, input: Input): GameState? {
        val restored = json.decodeFromString(GameState.serializer(), json.encodeToString(GameState.serializer(), state))
        val outcome = try {
            Engine.perform(restored, input)
        } catch (e: IllegalArgumentException) {
            return null.also { lastRefusal = e.message }
        }
        outcome.changes.forEach { restored.apply(it) }
        val viewer = restored.viewer
        if (viewer != null) outcome.changes.forEach { c ->
            // Where a view comes to know a card in another player's hand
            (c as? arcanum.engine.change.Change.ZoneChange)?.moves?.filter { it.to == Zone.Hand && it.after.owner != viewer && !it.after.unknown }
                ?.forEach { note("a card put into another player's hand stays known", "$viewer's view: ${it.after.definition?.faces?.first()?.name} " +
                    "from ${it.from} (${it.meanings.joinToString { m -> m::class.simpleName ?: "" }}) by $input") }
        }
        return restored
    }

    private var lastRefusal: String? = null

    private fun choose(): Input {
        val d = truth.pending?.decision
        if (d is Decision.Random) return Input.Answer(d.id, random(d.what))
        check(d !is Decision.HostFill) { "The full-information state asked a host fill" }
        if (!truth.started && d == null) return Input.StartGame()
        val listed = truth.players.flatMap { Engine.options(truth, it.id) }.distinct()
        check(listed.isNotEmpty()) { "Stuck at ${d ?: truth.turn}" }
        if (d != null) return listed.random(rnd)
        val doing = listed.filterNot { it is Input.PassPriority || (it is Input.DeclareAttackers && it.attacks.isEmpty()) ||
            (it is Input.DeclareBlockers && it.blocks.isEmpty()) }
        return if (doing.isNotEmpty() && rnd.nextInt(3) > 0) doing.random(rnd)
        else listed.filter { it !in doing || it is Input.DeclareAttackers || it is Input.DeclareBlockers }.ifEmpty { listed }.random(rnd)
    }

    private fun random(kind: RandomKind): Answer = when (kind) {
        RandomKind.CoinFlip -> Answer.CoinFlip(rnd.nextBoolean())
        is RandomKind.Die -> Answer.DieRolls(List(kind.count) { rnd.nextInt(1, kind.sides + 1) })
        is RandomKind.Objects -> Answer.RandomObjects(kind.among.shuffled(rnd).take(kind.count))
    }

    // ---- One view

    private fun sync(v: View, input: Input, truthDecision: Decision?) {
        val viewDecision = v.state.pending?.decision
        // D31: in another player's view the steps they see are theirs; the view waits on an OtherView meanwhile
        val elsewhere = viewDecision is Decision.OtherView
        if ((truthDecision == null) != (viewDecision == null) ||
            (truthDecision != null && viewDecision != null && !elsewhere && truthDecision::class != viewDecision::class)) {
            diverge(v, "decision differs: truth ${truthDecision?.let { it::class.simpleName }}, view ${viewDecision?.let { it::class.simpleName }}")
            return
        }
        // What the other player decides in their view, for translating their answer (a shuffle there gives new ids)
        val asked = if (viewDecision is Decision.OtherView) Host.otherViewDecision(v.state, otherView(v, viewDecision, emptyList()).filled)
            else viewDecision
        val translated = translate(v, input, asked).let { t ->
            // The other player's answer in their view, sent with the identities only they may see
            if (viewDecision is Decision.OtherView && t is Input.Answer) {
                otherViewAnswers++
                Input.Answer(viewDecision.id, otherView(v, viewDecision, listOf(t.answer)))
            } else t
        }
        // A play or cast of a card the view doesn't know: the engine asks a public fill for it (review 3 #4); with
        // [fillBeforePlay] the host fills it in by hand first instead (a Change.Filled it makes itself, the old way).
        val played = when (translated) {
            is Input.PlayLand -> translated.card
            is Input.Cast -> translated.card
            is Input.Activate -> translated.source
            else -> null
        }
        val unknown = played?.let { v.state.obj(it) }?.takeIf { it.unknown }
        if (unknown != null) {
            note("a play, cast or activation of a card unknown to the view", "${v.player}'s view: $translated")
            if (fillBeforePlay) {
                v.state.apply(arcanum.engine.change.Change.Filled(unknown, unknown.copy(definition = defs.getValue(v.truthCard(unknown.card)))))
                v.filledByHost += unknown.card
            }
        }
        val next = perform(v.state, translated)
        if (next == null) {
            note("input refused in a view", "${v.player}'s view refused $translated: $lastRefusal")
            diverge(v, "refused $translated: $lastRefusal")
            return
        }
        v.state = next
        var guard = 50
        while (guard-- > 0) {
            val other = v.state.pending?.decision as? Decision.OtherView
            if (other != null) {
                // D31: the identities alone when the steps need no answer of the other player's now
                val answer = otherView(v, other, emptyList())
                if (Host.otherViewDecision(v.state, answer.filled) != null) break
                otherViewAnswers++
                v.state = perform(v.state, Input.Answer(other.id, answer)) ?: run {
                    diverge(v, "other view refused: $lastRefusal"); return
                }
                continue
            }
            val d = v.state.pending?.decision as? Decision.HostFill ?: break
            if (!d.public && d.player != v.player) {
                privateFillsInOtherViews++
                note("a fill only ${"another"} player may see, asked in a view (D31)", "${v.player}'s view asks to fill ${d.objects.size} " +
                    "card(s) for ${d.player} (${d.reason}); answering it puts their identities into ${v.player}'s state")
            }
            val answer = Answer.Filled(d.objects.map { id -> FilledObject(id, defs.getValue(v.truthCard((id as ObjectId.Card).card))) })
            v.state = perform(v.state, Input.Answer(d.id, answer)) ?: run {
                diverge(v, "fill refused: $lastRefusal"); return
            }
        }
    }

    /** As the host, the answer to [d] in [v]: the identities of its objects (only its player may see them; the engine
     * keeps none, D31) and [answers] from that player's view. */
    private fun otherView(v: View, d: Decision.OtherView, answers: List<Answer>): Answer.OtherView =
        Answer.OtherView(d.objects.map { id -> FilledObject(id, defs.getValue(v.truthCard((id as ObjectId.Card).card))) }, answers)

    private fun diverge(v: View, why: String) {
        if (v.diverged == null) note("view diverged", "${v.player}: $why")
        v.diverged = why
    }

    /** [input], chosen on the truth, as [v] has it. */
    private fun translate(v: View, input: Input, viewDecision: Decision?): Input = when (input) {
        is Input.PlayLand -> input.copy(card = v.obj(input.card))
        is Input.Cast -> input.copy(card = v.obj(input.card), way = input.way.copy(permission = input.way.permission?.let {
            it.copy(source = it.source?.let { s -> v.obj(s) })
        }))
        is Input.Activate -> input.copy(source = v.obj(input.source))
        // Night review 2026-11-04: enlisted creatures, bands and optional costs go with the declaration (CR 702.154, 702.22c, 508.1g)
        is Input.DeclareAttackers -> input.copy(attacks = input.attacks.map {
            it.copy(attacker = v.obj(it.attacker), target = v.ref(it.target), enlisted = it.enlisted.map { e -> v.obj(e) })
        })
        is Input.DeclareBlockers -> input.copy(blocks = input.blocks.map { Block(v.obj(it.blocker), v.obj(it.attacker)) })
        is Input.Answer -> Input.Answer(viewDecision?.id ?: input.decision, answer(v, input.answer, viewDecision))
        else -> input
    }

    private fun answer(v: View, a: Answer, d: Decision?): Answer = when (a) {
        is Answer.Targets -> Answer.Targets(a.chosen.map { v.ref(it) })
        is Answer.Objects -> Answer.Objects(a.chosen.map { v.obj(it) })
        is Answer.Keep -> Answer.Keep(v.obj(a.kept))
        is Answer.Division -> Answer.Division(a.shares.map { Share(v.ref(it.target), it.amount) })
        is Answer.ManaPayment -> Answer.ManaPayment(a.uses.map { u ->
            when (u) {
                is ManaUse.Ability -> u.copy(source = v.obj(u.source))
                is ManaUse.Special -> u.copy(objects = u.objects.map { v.obj(it) })
                else -> u
            }
        })
        is Answer.RandomObjects -> {
            val kind = ((d as? Decision.Random)?.what as? RandomKind.Objects)
            if (kind?.newIds == true) Answer.RandomObjects(a.chosen.map { id ->
                // As the host: the view's new id for the card at this position, mapped to the truth's card
                val tc = (id as ObjectId.Card).card
                val nc = CardId("${v.player.value}-view.${++v.newCards}")
                v.toTruth[nc] = tc
                v.fromTruth[tc] = nc
                ObjectId.Card(nc, 0)
            }) else Answer.RandomObjects(a.chosen.map { v.obj(it) })
        }
        else -> a
    }

    // ---- What everyone sees

    /** What every player can see of [s], with card ids as the truth has them ([card]). */
    private fun public(s: GameState, card: (CardId) -> CardId, kindless: Boolean = false): List<String> {
        fun id(o: ObjectId) = when (o) { is ObjectId.Card -> card(o.card).value; is ObjectId.StackItem -> o.toString() }
        val lines = mutableListOf<String>()
        lines += "turn ${s.turn.number} ${s.turn.active} ${s.turn.step} priority ${s.priority.holder} combat ${s.combat?.attackers?.map { id(it.id) }}"
        s.players.forEach { p ->
            lines += "${p.id}: life ${p.life} counters ${p.counters.map { "${it.kind}=${it.count}" }} lost ${p.lost} hand ${p.hand.size} " +
                "library ${p.library.size} pool ${p.manaPool.size} graveyard ${p.graveyard.map { id(it) }}"
        }
        s.battlefield.map { s.objOrThrow(it) }.map { o ->
            "${card(o.card).value} ctl ${o.controller} tapped ${o.tapped} down ${o.faceDown} counters ${o.counters.map { "${it.kind}=${it.count}" }} " +
                "damage ${o.damage} on ${(o.attachedTo as? TargetRef.Object)?.id?.let { id(it) } ?: (o.attachedTo as? TargetRef.Player)?.id}"
        }.sorted().forEach { lines += it }
        lines += "exile ${s.exile.map { s.objOrThrow(it) }.map { if (it.faceDown) "face down" else card(it.card).value }.sorted()}"
        lines += "stack ${s.stack.map { sid -> s.stackItems[sid]?.let { "${it.kind::class.simpleName}/${it.controller}/${it.source?.let { x -> id(x) }}" } ?: id(sid) }}"
        val d = s.pending?.decision
        // D31: while a view waits on steps another player decides in theirs (OtherView), what they decide isn't compared
        if (d != null && d !is Decision.HostFill && d !is Decision.Random && !kindless) lines += "pending ${d::class.simpleName} for ${d.player}"
        return lines
    }

    private fun compare(v: View) {
        val elsewhere = v.state.pending?.decision is Decision.OtherView
        val t = public(truth, { it }, elsewhere)
        val w = public(v.state, v::truthCard, elsewhere)
        if (t != w) {
            val i = t.indices.firstOrNull { it >= w.size || t[it] != w[it] } ?: t.size
            diverge(v, "public state differs:\n  truth ${t.getOrNull(i)}\n  view  ${w.getOrNull(i)}")
            return
        }
        // Cards face up in public zones are known with the truth's identity; another player's hand isn't known
        v.state.objects.values.forEach { o ->
            val public = (o.zone == Zone.Battlefield && !o.faceDown) || o.zone == Zone.Graveyard || (o.zone == Zone.Exile && !o.faceDown) ||
                (o.zone == Zone.Stack && !o.faceDown)
            val truthDef = defs[v.truthCard(o.card)]
            if (public && !o.token && o.definition == null) note("public card unknown in a view", "${o.id} in ${o.zone}, ${v.player}'s view")
            if (public && !o.token && o.definition != null && truthDef != null && o.definition!!.faces.first().name != truthDef.faces.first().name) {
                note("public card misidentified in a view", "${o.id} is ${o.definition!!.faces.first().name}, truly ${truthDef.faces.first().name}")
            }
            if (o.zone == Zone.Hand && o.owner != v.player && o.definition != null && o.card !in v.filledByHost) {
                note("another player's hand card known in a view", "${v.player} knows ${o.definition!!.faces.first().name} in ${o.owner}'s hand")
            }
        }
        // The pending decision's other player isn't shown hidden cards (D60) — counted, not a divergence
        if (v.state.pending != null && v.state.pending!!.decision.player != v.player) {
            val hidden = v.state.objects.values.filter { it.zone == Zone.Hand && it.owner != v.player && it.definition != null && it.card !in v.filledByHost }
            if (hidden.isNotEmpty()) note("pending decision state knows another's hand", "${hidden.size} cards")
        }
    }

    /** The viewer's own options in its view against the truth's, translated (D60: a player can do the same things in
     * their view as the host knows they can, the cards they can't see aside). */
    private fun compareOptions(v: View) {
        if (truth.pending?.decision is Decision.Random) return
        val inTruth = Engine.options(truth, v.player).map { normalize(translate(v, it, v.state.pending?.decision)) }.toSet()
        val inView = Engine.options(v.state, v.player).map { normalize(it) }.toSet()
        (inTruth - inView).forEach { note("an option only the truth lists", "${v.player}: $it") }
        (inView - inTruth).forEach { note("an option only the view lists", "${v.player}: $it") }
    }

    private fun normalize(i: Input): Input = if (i is Input.Answer) i.copy(decision = arcanum.engine.decision.DecisionId("")) else i

    fun report(): String = buildString {
        appendLine("seed $seed: ${inputs} inputs, turn ${truth.turn.number}, result ${Engine.result(truth)}; views: " +
            views.joinToString { "${it.player} ${it.diverged?.let { d -> "diverged: ${d.lines().first()}" } ?: "in step"}" })
        findings.forEach { (k, n) -> appendLine("  $k ×$n — ${examples[k]}") }
    }
}
