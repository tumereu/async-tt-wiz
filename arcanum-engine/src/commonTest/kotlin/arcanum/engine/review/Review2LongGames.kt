package arcanum.engine.review

import arcanum.api.CardType
import arcanum.api.Zone
import arcanum.engine.Engine
import arcanum.engine.Host
import arcanum.engine.Outcome
import arcanum.engine.action.Knowledge
import arcanum.engine.cards.Cards
import arcanum.engine.change.Change
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.decision.FilledObject
import arcanum.engine.decision.ManaUse
import arcanum.engine.decision.Share
import arcanum.engine.view.AbilityOrigin
import arcanum.engine.state.TargetRef
import arcanum.api.Color
import arcanum.api.Subtype
import arcanum.engine.decision.RandomKind
import arcanum.engine.input.Input
import arcanum.engine.setup.DeckCard
import arcanum.engine.setup.Seat
import arcanum.engine.setup.started
import arcanum.engine.state.CardId
import arcanum.engine.state.GameState
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import kotlinx.serialization.json.Json
import kotlin.random.Random

/**
 * Review 2: whole games from [Engine.newGame] with two 40-card decks of random cube cards, played by a seeded random
 * policy over [Engine.options] until the game ends, with the invariants checked after every input:
 *
 * - every listed input is accepted ([Engine.perform] doesn't throw) and doesn't change the state it is given (D56);
 * - its outcome reverts exactly (D59), goes through JSON unchanged, and so does the state with its continuation (D58);
 *   answering a decision on the state after JSON gives the same outcome;
 * - some player has an option unless the game is over;
 * - every object is in exactly one zone list, the one it says, and every zone entry is an object or a Stack item;
 *   no id twice; counters and damage never below 0;
 * - the timestamp counter never goes back and no object or effect has a timestamp above it (D34);
 * - the game ends within [maxTurns]; at the end, reverting every change gives back the game before it began (D59).
 *
 * With a [viewer], the game is played on that player's view (D60, [GameState.viewer]): the driver is the host, knows
 * every card and answers the fills and new ids; the other players' hands are unknown to the view, so they play only
 * what is public. Then also: every card face up in a public zone is known, and no card in another player's hand or
 * face down where the viewer can't look is.
 *
 * A broken invariant throws [LongGameFailure] with the seed, the step and the last inputs, so it can be reproduced.
 */
internal class LongGame(
    val seed: Long,
    /** Players at the table (2–6). */
    private val players: Int = 2,
    private val maxTurns: Int = 200,
    private val maxInputs: Int = 20_000,
    /** Check JSON round trips every this many inputs (1: always). */
    private val jsonEvery: Int = 1,
    /** A host that drops old turns' events at each new turn ([GameState.forgetOlderTurns]). */
    private val forgetOldTurns: Boolean = true,
    /** Invariant checks to leave out (known findings), by name: "orphanStackItem" (none open now). */
    private val skip: Set<String> = emptySet(),
    /** Play on this player's view (D60) instead of with full information. */
    private val viewer: PlayerId? = null,
    /** At every decision, also try answers of every kind with odd contents: each must be accepted or refused with
     * [IllegalArgumentException], never crash (the one answer-validation path). */
    private val malformed: Boolean = false,
    /** Each player's 40 card names (review 3's themed cube decks), instead of random ones. */
    private val decks: Map<PlayerId, List<String>>? = null,
) {
    private val rnd = Random(seed)
    private val json = Json
    private val ids = listOf("alice", "bob", "carol", "dave", "erin", "frank").take(players).map { PlayerId(it) }

    private val truths = mutableMapOf<CardId, arcanum.api.CardDefinition>()
    val state: GameState = Engine.newGame(ids.map { Seat(it, deck(it)) }).also { game ->
        if (viewer != null) {
            game._viewer = viewer
            game.objects.values.toList().forEach { o ->
                truths[o.card] = o.definition!!
                if (!Knowledge.mayKnow(o, viewer)) game._objects[o.id] = o.copy(definition = null)
            }
        }
    }
    private val start = state.deepCopy()
    private var newCards = 0
    private val applied = mutableListOf<Change>()
    private val recent = ArrayDeque<String>()
    var inputs = 0
        private set
    private var lastTurn = 0
    private var inputsThisTurn = 0
    private var lastTimestamp = 0L

    /** 16 basics of two colours and 24 random cube cards. */
    private fun deck(player: PlayerId): List<DeckCard> {
        decks?.get(player)?.let { names ->
            return names.mapIndexed { i, name -> DeckCard(CardId("${player.value}.$i"), Cards.definition(name)) }
        }
        val basics = BASICS.shuffled(rnd).take(2)
        val names = List(16) { basics[it % 2] } + List(24) { Cards.cubeNames.random(rnd) }
        return names.shuffled(rnd).mapIndexed { i, name -> DeckCard(CardId("${player.value}.$i"), Cards.definition(name)) }
    }

    /** Plays the game to its end; returns the number of turns. */
    fun play(): Int {
        while (Engine.result(state) == null) {
            if (inputs >= maxInputs) fail("No end after $inputs inputs (turn ${state.turn.number})")
            if (state.turn.number > maxTurns) fail("No end after $maxTurns turns")
            step()
        }
        checkWholeGameReverts()
        return state.turn.number
    }

    private fun step() {
        if (malformed) state.pending?.decision?.let { d -> malformedAnswers().forEach { tryOnly(Input.Answer(d.id, it)) } }
        val input = choose()
        perform(input)
    }

    /** [input] is accepted or refused with [IllegalArgumentException]; nothing else. */
    private fun tryOnly(input: Input) {
        try {
            Engine.perform(state, input)
        } catch (e: IllegalArgumentException) {
            // refused: fine
        } catch (e: Throwable) {
            fail("$input crashed with ${e::class.simpleName}: ${e.message}", e)
        }
    }

    /** One answer of every kind with odd contents: unknown and repeated objects and players, out-of-range numbers. */
    private fun malformedAnswers(): List<Answer> {
        val nobody = ObjectId.Card(CardId("nobody"), 0)
        val obj = state.objects.keys.toList().ifEmpty { listOf(nobody) }.random(rnd)
        val noPlayer = PlayerId("nobody")
        return listOf(
            Answer.Targets(emptyList()), Answer.Targets(listOf(TargetRef.Object(obj), TargetRef.Object(obj))),
            Answer.Targets(listOf(TargetRef.Player(noPlayer))), Answer.Modes(listOf(-1)), Answer.Modes(listOf(0, 0, 0, 0)),
            Answer.Number(-1), Answer.Number(Int.MAX_VALUE), Answer.ManaPayment(listOf(ManaUse.Pool(99))),
            Answer.ManaPayment(listOf(ManaUse.Ability(obj, AbilityOrigin.Printed(9, 9)))), Answer.ManaPayment(listOf(ManaUse.Life(-1))),
            Answer.YesNo(true), Answer.Objects(listOf(obj, obj)), Answer.Objects(listOf(nobody)), Answer.Option("nonsense"),
            Answer.ColorChoice(Color.White), Answer.SubtypeChoice(Subtype("")), Answer.CardTypeChoice(CardType.Battle),
            Answer.CardName(""), Answer.Player(noPlayer), Answer.Division(listOf(Share(TargetRef.Object(obj), -3))),
            Answer.Order(listOf(5, 5)), Answer.Replacement(-1), Answer.Keep(nobody), Answer.CoinFlip(true),
            Answer.DieRolls(listOf(0, 99)), Answer.RandomObjects(listOf(obj, obj)), Answer.RandomObjects(listOf(nobody)),
            Answer.Filled(listOf(FilledObject(obj, Cards.definition("Forest")))), Answer.Done,
        )
    }

    private fun choose(): Input {
        val d = state.pending?.decision
        if (d is Decision.Random) return Input.Answer(d.id, random(d.what))
        if (d is Decision.HostFill) {
            return Input.Answer(d.id, Answer.Filled(d.objects.map { FilledObject(it, truth(it)) }))
        }
        if (d is Decision.OtherView) {
            // As the host: the identities, and the other player's next answer in their view (D31), when one is needed
            val filled = d.objects.map { FilledObject(it, truth(it)) }
            val next = Host.otherViewDecision(state, filled)
            return Input.Answer(d.id, Answer.OtherView(filled, listOfNotNull(next?.let { someAnswer(it) })))
        }
        if (!state.started && d == null) return Input.StartGame()
        val listed = state.players.flatMap { Engine.options(state, it.id) }.distinct()
        if (listed.isEmpty()) fail("Stuck: nothing listed at ${d ?: state.turn}")
        if (d != null) return listed.random(rnd)
        val doing = listed.filterNot { it is Input.PassPriority || (it is Input.DeclareAttackers && it.attacks.isEmpty()) ||
            (it is Input.DeclareBlockers && it.blocks.isEmpty()) }
        // Keep turns moving: after many inputs in one turn, only pass or declare
        val busy = inputsThisTurn > 60
        return if (!busy && doing.isNotEmpty() && rnd.nextInt(3) > 0) doing.random(rnd)
        else listed.filter { it !in doing || it is Input.DeclareAttackers || it is Input.DeclareBlockers }.ifEmpty { listed }.random(rnd)
    }

    /** A legal answer to [d], a decision another player faces in their view ([Host.otherViewDecision]), chosen at
     * random where it is a choice. */
    private fun someAnswer(d: Decision): Answer = when (d) {
        is Decision.Random -> random(d.what)
        is Decision.May -> Answer.YesNo(rnd.nextBoolean())
        is Decision.ChooseObjects -> Answer.Objects(d.options.shuffled(rnd).take(rnd.nextInt(d.min, (d.max ?: d.options.size).coerceAtLeast(d.min) + 1)))
        is Decision.Targets -> Answer.Targets(d.options.shuffled(rnd).take(d.min.coerceAtLeast(1).coerceAtMost(d.max ?: d.options.size)))
        is Decision.Modes -> Answer.Modes(d.options.filter { it.available }.map { it.index }.take(d.min))
        is Decision.ChooseOption -> Answer.Option(d.options.random(rnd))
        is Decision.ChooseColor -> Answer.ColorChoice(d.among.random(rnd))
        is Decision.ChoosePlayer -> Answer.Player(d.options.random(rnd))
        is Decision.ChooseNumber -> Answer.Number(d.min)
        is Decision.Announce -> Answer.Number(d.min)
        is Decision.ChooseCardType -> Answer.CardTypeChoice(d.among.random(rnd))
        is Decision.ChooseSubtype -> Answer.SubtypeChoice(d.among?.random(rnd) ?: Subtype("Elf"))
        is Decision.ChooseCardName -> Answer.CardName("Forest")
        is Decision.OrderTriggers -> Answer.Order(d.triggers.map { it.index })
        is Decision.ChooseReplacement -> Answer.Replacement(d.options.first().index)
        is Decision.LegendRule -> Answer.Keep(d.among.first())
        is Decision.ManaPayment -> Answer.ManaPayment(d.suggestion)
        is Decision.Divide -> Answer.Division(d.suggested.ifEmpty { listOf(Share(d.among.first(), d.amount)) })
        is Decision.Manual -> Answer.Done
        is Decision.HostFill, is Decision.OtherView -> fail("A host's decision inside another view: $d")
    }

    private fun truth(id: ObjectId): arcanum.api.CardDefinition =
        truths[(id as ObjectId.Card).card] ?: fail("The host knows nothing about $id")

    private fun random(kind: RandomKind): Answer = when (kind) {
        RandomKind.CoinFlip -> Answer.CoinFlip(rnd.nextBoolean())
        is RandomKind.Die -> Answer.DieRolls(List(kind.count) { rnd.nextInt(1, kind.sides + 1) })
        is RandomKind.Objects -> if (!kind.newIds) Answer.RandomObjects(kind.among.shuffled(rnd).take(kind.count))
            // As the host: new ids for the cards in their new order, whose identities only the host keeps (D60)
            else Answer.RandomObjects(kind.among.shuffled(rnd).take(kind.count).map { id ->
                val card = CardId("new.${++newCards}")
                truths[card] = truth(id)
                ObjectId.Card(card, 0)
            })
    }

    fun perform(input: Input) {
        remember(input)
        val before = state.deepCopy()
        val outcome = try {
            Engine.perform(state, input)
        } catch (e: Throwable) {
            fail("$input was refused: ${e::class.simpleName}: ${e.message}", e)
        }
        if (before != state) fail("Engine.perform changed the state it was given (D56)")
        // D58: the same outcome from the state after JSON
        if (inputs % jsonEvery == 0 || state.pending != null) {
            val restored = json.decodeFromString(GameState.serializer(), json.encodeToString(GameState.serializer(), state))
            if (restored != state) fail("The state changed through JSON")
            val again = try { Engine.perform(restored, input) } catch (e: Throwable) { fail("Refused after JSON: ${e.message}", e) }
            if (again != outcome) fail("Another outcome after JSON (D58)")
            val outcomeBack = json.decodeFromString(Outcome.serializer(), json.encodeToString(Outcome.serializer(), outcome))
            if (outcomeBack != outcome) fail("The outcome changed through JSON")
        }
        // D59: the outcome reverts
        val copy = state.deepCopy()
        try {
            outcome.changes.forEach { copy.apply(it) }
            outcome.changes.asReversed().forEach { copy.revert(it) }
        } catch (e: Throwable) {
            fail("Applying or reverting the outcome of $input failed: ${e.message}", e)
        }
        copy._timestampCounter = state.timestampCounter
        if (copy != state) fail("Reverting the outcome of $input didn't restore the state (D59)")
        outcome.changes.forEach { state.apply(it); applied += it }
        // D60: a card that becomes hidden from the viewer (put into another player's hand, face down where the viewer
        // can't look, shuffled) arrives unknown
        if (viewer != null) outcome.changes.forEach { c ->
            val arrived = (c as? Change.ZoneChange)?.moves.orEmpty().map { it.after }
            arrived.filter { !it.unknown && it.zone != Zone.Library && !Knowledge.mayKnow(it, viewer) }
                .forEach { fail("${it.id} (${it.definition?.faces?.first()?.name}) arrived in ${it.owner}'s ${it.zone} known to $viewer") }
            if (c is Change.Shuffle && c.after.size > 1) c.after.filter { a -> c.before.none { it.id == a.id } && !a.unknown }
                .forEach { fail("${it.id} (${it.definition?.faces?.first()?.name}) is known after a shuffle") }
        }
        inputs++
        inputsThisTurn++
        if (state.turn.number != lastTurn) {
            lastTurn = state.turn.number
            inputsThisTurn = 0
            if (forgetOldTurns) state.forgetOlderTurns()
        }
        checkInvariants()
    }

    private fun remember(input: Input) {
        recent.addLast("#$inputs t${state.turn.number} ${state.turn.step}: $input")
        if (recent.size > 12) recent.removeFirst()
    }

    fun checkInvariants() {
        val s = state
        // Every object in exactly the zone list it says
        val lists = mutableMapOf<ObjectId, String>()
        fun add(ids: List<ObjectId>, label: String) = ids.forEach { id ->
            lists.put(id, label)?.let { fail("$id is in $it and in $label") }
        }
        s.players.forEach { p ->
            add(p.library, "${p.id} library"); add(p.hand, "${p.id} hand"); add(p.graveyard, "${p.id} graveyard")
            add(p.outside, "${p.id} outside")
        }
        add(s.battlefield, "battlefield"); add(s.stack, "stack"); add(s.exile, "exile"); add(s.command, "command")
        s.objects.forEach { (id, o) ->
            if (o.id != id) fail("Object under $id has id ${o.id}")
            val where = lists[id] ?: fail("$id (${o.definition?.faces?.first()?.name}) is in ${o.zone} by its field, in no zone list")
            val expected = if (o.zone in listOf(Zone.Library, Zone.Hand, Zone.Graveyard, Zone.Outside)) "${o.owner} ${label(o.zone)}" else label(o.zone)
            if (where != expected) fail("$id says ${o.zone} but is in $where")
            if (o.counters.any { it.count < 0 }) fail("$id has negative counters: ${o.counters}")
            if (o.counters.groupBy { it.kind }.any { it.value.size > 1 }) fail("$id has two entries of one counter kind: ${o.counters}")
            if (o.damage < 0) fail("$id has negative damage")
            if (o.timestamp > s.timestampCounter) fail("$id has timestamp ${o.timestamp} above the counter ${s.timestampCounter}")
        }
        lists.keys.forEach { id -> if (s.objects[id] == null && s.stackItems[id] == null) fail("$id in ${lists[id]} is no object") }
        if ("orphanStackItem" !in skip) s.stackItems.forEach { (it, item) -> if (it !in s.stack) fail("Stack item $it isn't on the Stack: $item; object ${s.objects[it]?.let { o -> "${o.definition?.faces?.map { f -> f.name }} in ${o.zone}" }}") }
        // Card ids: one object per card
        s.objects.values.filterNot { it.token }.groupBy { it.card }.forEach { (card, os) -> if (os.size > 1) fail("$card is ${os.map { it.id }}") }
        s.players.forEach { p ->
            if (p.counters.any { it.count < 0 }) fail("${p.id} has negative counters")
        }
        s.effects.values.forEach { if (it.timestamp > s.timestampCounter) fail("Effect ${it.id} stamped above the counter") }
        if (s.timestampCounter < lastTimestamp) fail("The timestamp counter went back")
        lastTimestamp = s.timestampCounter
        // D60: the viewer's state knows every public card
        if (viewer != null) s.objects.values.forEach { o ->
            if (Knowledge.public(o) && o.unknown) fail("${o.id} is face up in ${o.zone} but unknown")
        }
        // The view can be worked out; battlefield objects are permanents
        val view = try { Engine.view(s) } catch (e: Throwable) { fail("The view failed: ${e.message}", e) }
        s.battlefield.forEach { id ->
            val c = view[id]?.characteristics
            if (c != null && c.types.none { it in PERMANENT_TYPES } && s.objects[id]?.faceDown != true) {
                fail("$id (${c.name}) on the battlefield isn't a permanent: ${c.types}")
            }
        }
    }

    private fun label(zone: Zone) = when (zone) {
        Zone.Library -> "library"; Zone.Hand -> "hand"; Zone.Graveyard -> "graveyard"; Zone.Outside -> "outside"
        Zone.Battlefield -> "battlefield"; Zone.Stack -> "stack"; Zone.Exile -> "exile"; Zone.Command -> "command"
    }

    private fun checkWholeGameReverts() {
        val reverted = state.deepCopy()
        try {
            applied.asReversed().forEach { reverted.revert(it) }
        } catch (e: Throwable) {
            fail("Reverting the whole game failed: ${e.message}", e)
        }
        reverted._timestampCounter = start.timestampCounter
        if (reverted != start) fail("Reverting the whole game didn't give back its start (D59)")
    }

    fun fail(message: String, cause: Throwable? = null): Nothing =
        throw LongGameFailure("Game seed $seed, input $inputs, turn ${state.turn.number} ${state.turn.step}: $message\n" +
            "Last inputs:\n" + recent.joinToString("\n"), cause)

    companion object {
        val BASICS = listOf("Plains", "Island", "Swamp", "Mountain", "Forest")
        private val PERMANENT_TYPES = listOf(CardType.Artifact, CardType.Creature, CardType.Enchantment, CardType.Land,
            CardType.Planeswalker, CardType.Battle)
    }
}

internal class LongGameFailure(message: String, cause: Throwable?) : AssertionError(message, cause)
