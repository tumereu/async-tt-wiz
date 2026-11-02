package arcanum.engine.scenario

import arcanum.api.Ability
import arcanum.api.CounterKind
import arcanum.api.Zone
import arcanum.engine.Outcome
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.decision.ManaUse
import arcanum.engine.input.Attack
import arcanum.engine.input.Block
import arcanum.engine.input.CastWay
import arcanum.engine.input.Input
import arcanum.engine.state.CounterEntry
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import arcanum.engine.state.TargetRef
import kotlin.test.fail

/** One player of a [Scenario]: setup of their zones, their moves, and what expectations read about them. */
class ScenarioPlayer internal constructor(private val scenario: Scenario, val id: PlayerId) {
    private val state get() = scenario.state

    // ---- Setup

    /** Puts cards onto the battlefield under this player's control, controlled since before this turn unless [sick]
     * (CR 302.6). */
    fun battlefield(
        vararg names: String, tapped: Boolean = false, sick: Boolean = false, faceDown: Boolean = false,
        counters: List<Pair<CounterKind, Int>> = emptyList(),
    ) {
        scenario.put(this, names.toList(), Zone.Battlefield, tapped = tapped, sick = sick, counters = counters, faceDown = faceDown)
    }

    fun hand(vararg names: String) {
        scenario.put(this, names.toList(), Zone.Hand)
    }

    /** Cards in hand the caller can't see (D60): the scenario's host knows them and fills them in when asked. */
    fun hiddenHand(vararg names: String) {
        scenario.put(this, names.toList(), Zone.Hand, hidden = true)
    }

    /** Puts cards on top of the library: [top] first is the top card. */
    fun library(vararg top: String, hidden: Boolean = false) {
        top.reversed().forEach { scenario.put(this, listOf(it), Zone.Library, hidden = hidden, top = true) }
    }

    fun graveyard(vararg names: String) {
        scenario.put(this, names.toList(), Zone.Graveyard)
    }

    fun exile(vararg names: String) {
        scenario.put(this, names.toList(), Zone.Exile)
    }

    /** Life total: set during setup, read in expectations. */
    var life: Int
        get() = state.player(id).life
        set(value) {
            scenario.setup()
            state.updatePlayerForSetup { it.copy(life = value) }
        }

    /** Sets this player's counters of [kind] (poison, energy, …) during setup. */
    fun counters(kind: CounterKind, count: Int) {
        scenario.setup()
        state.timestampCounter++
        val stamp = state.timestampCounter
        state.updatePlayerForSetup { p ->
            p.copy(counters = (p.counters.filter { it.kind != kind } + CounterEntry(kind, count, stamp))
                .sortedWith(compareBy({ it.timestamp }, { it.kind.toString() })))
        }
    }

    private fun arcanum.engine.state.GameState.updatePlayerForSetup(change: (arcanum.engine.state.PlayerState) -> arcanum.engine.state.PlayerState) {
        val index = players.indexOfFirst { it.id == id }
        players[index] = change(players[index])
    }

    /** This player's counters of [kind]. */
    fun counters(kind: CounterKind): Int = state.player(id).counters(kind)

    // ---- Moves

    /** Plays the land [name] (CR 305.1), with [face] up (a modal double-faced card's back, CR 712.11). */
    fun plays(name: String, face: Int = 0): Outcome = scenario.perform(Input.PlayLand(id, toCast(name), face))

    /** The card named [name] this player would cast or play: their own in hand, else their own elsewhere off the
     * battlefield and the Stack, else anyone's there (a permission to cast another player's card). */
    private fun toCast(name: String): ObjectId = scenario.preferred(name, listOf(
        { it.owner == id && it.zone == Zone.Hand },
        { it.owner == id && it.zone != Zone.Battlefield && it.zone != Zone.Stack },
        { it.zone != Zone.Battlefield && it.zone != Zone.Stack },
    ))

    /** The object named [name] whose ability this player activates: a permanent they control, else their own card
     * elsewhere (channel, cycling, unearth), else any. */
    private fun toActivate(name: String): ObjectId = scenario.preferred(name, listOf(
        { it.zone == Zone.Battlefield && scenario.view()[it.id]?.controller == id },
        { it.zone == Zone.Battlefield },
        { it.owner == id },
    ))

    /**
     * Casts [name] (CR 601.2) and answers the cast's decisions from what is given: targets by name (players by their
     * name) in slot order, modes, X, optional costs ([kicked]: how many times, 0 declines), the option of an "A or B"
     * cost ([option]), the objects a cost uses ([choosing], by name, in cost order), a mana ability's colour ([color]),
     * and the mana sources to tap ([paying], by name, "name:R" for a colour, "name#1" for the second of that name; else the engine's suggestion).
     * Decisions nothing here answers stay open for the test.
     */
    fun casts(
        name: String, target: String? = null, targets: List<String> = listOfNotNull(target), x: Int? = null,
        modes: List<Int>? = null, paying: List<String>? = null, way: CastWay = CastWay(),
        kicked: Int? = null, option: String? = null, choosing: List<String> = emptyList(),
    ): Outcome {
        val outcome = scenario.perform(Input.Cast(id, toCast(name), way))
        return answerAnnouncing(outcome, targets, x, modes, paying, kicked, option, choosing, null)
    }

    /** Activates the [ability]th activated ability (from 0, in the order the object has them) of [name] (CR 602). */
    fun activates(
        name: String, ability: Int = 0, target: String? = null, targets: List<String> = listOfNotNull(target), x: Int? = null,
        modes: List<Int>? = null, paying: List<String>? = null, option: String? = null, choosing: List<String> = emptyList(),
        color: arcanum.api.Color? = null,
    ): Outcome {
        val source = toActivate(name)
        val abilities = scenario.view()[source]?.characteristics?.abilities.orEmpty().filter { it.ability is Ability.Activated }
        val chosen = abilities.getOrNull(ability) ?: fail("$name has no activated ability #$ability")
        val outcome = scenario.perform(Input.Activate(id, source, chosen.origin))
        return answerAnnouncing(outcome, targets, x, modes, paying, null, option, choosing, color)
    }

    /**
     * Puts [name] on the Stack as a spell this player cast (tagged cast, so cast triggers trigger), without paying its
     * costs: for scenarios about resolution (CR 608) rather than casting (CR 601.2). [targets] by name fill the target
     * slots in order (each up to its maximum); left-over targets go to an "enchant" slot (Auras); [divided] gives each
     * target's share (CR 601.2d). Resolve with [Scenario.resolve].
     */
    fun castsWithoutCosts(
        name: String, target: String? = null, targets: List<String> = listOfNotNull(target), x: Int? = null,
        modes: List<Int> = emptyList(), divided: List<Int> = emptyList(), kicked: Int = 0,
        paid: List<arcanum.engine.state.PaidCost> = emptyList(),
    ): Outcome {
        // The first such card this player owns in their hand, else elsewhere off the battlefield and the Stack
        val card = (0 until 20).asSequence().mapNotNull { runCatching { scenario.card(name, it) }.getOrNull() }
            .map { state.objOrThrow(it) }.filter { it.owner == id && it.zone != Zone.Battlefield && it.zone != Zone.Stack }
            .sortedBy { if (it.zone == Zone.Hand) 0 else 1 }.firstOrNull()?.id ?: scenario.card(name)
        return scenario.drive { r ->
            val o = r.state.objOrThrow(card)
            val face = o.definition!!.faces[o.face]
            val index = face.abilities.indexOfFirst { it is Ability.Spell }
            val ability = face.abilities.getOrNull(index) as? Ability.Spell
            val chosen = slotTargets(ability?.instructions, modes, targets)
            val act = arcanum.engine.action.Act(r, arcanum.engine.action.Asker.NONE)
            val spell = arcanum.engine.action.Actions.move(act, null, listOf(arcanum.engine.action.PlannedMove(card, Zone.Stack, controller = id)))[card]!!
            val now = r.state.objOrThrow(spell)
            val paidCosts = paid + if (kicked > 0) listOf(arcanum.engine.state.PaidCost(arcanum.api.Keyword.Kicker.Kind, kicked)) else emptyList()
            r.record(arcanum.engine.change.Change.Facts(spell, now.facts, now.facts.copy(castFrom = o.zone, x = x, modesChosen = modes, paid = paidCosts)))
            r.record(arcanum.engine.change.Change.StackItemAdded(arcanum.engine.state.StackItem(
                id = spell, kind = arcanum.engine.state.StackItemKind.Spell, controller = id, source = spell, ability = ability,
                origin = if (index >= 0) arcanum.engine.view.AbilityOrigin.Printed(o.face, index) else null,
                targets = chosen, modes = modes, announced = x?.let { mapOf("X" to it) }.orEmpty(),
                divided = divided(chosen, divided), timestamp = r.state.nextTimestamp(),
            ), null, listOf(arcanum.engine.change.Meaning.Cast)))
            arcanum.engine.Progress.Done
        }
    }

    /** Puts the [ability]th activated ability of [name] on the Stack (CR 602.2) without paying its costs. */
    fun activatesWithoutCosts(
        name: String, ability: Int = 0, target: String? = null, targets: List<String> = listOfNotNull(target), x: Int? = null,
    ): Outcome {
        val source = toActivate(name)
        val abilities = scenario.view()[source]?.characteristics?.abilities.orEmpty().filter { it.ability is Ability.Activated }
        val chosenAbility = abilities.getOrNull(ability) ?: fail("$name has no activated ability #$ability")
        val activated = chosenAbility.ability as Ability.Activated
        return scenario.drive { r ->
            val ts = r.state.nextTimestamp()
            val itemId = ObjectId.StackItem("ability:$ts")
            r.record(arcanum.engine.change.Change.StackItemAdded(arcanum.engine.state.StackItem(
                id = itemId, kind = arcanum.engine.state.StackItemKind.Activated, controller = id, source = source,
                ability = activated, origin = chosenAbility.origin, targets = slotTargets(activated.instructions, emptyList(), targets),
                announced = x?.let { mapOf("X" to it) }.orEmpty(), timestamp = ts,
            ), r.state.stack.size, listOf(arcanum.engine.change.Meaning.Activated)))
            r.record(arcanum.engine.change.Change.Happened(arcanum.engine.change.GameEvent.Activated(id, source, chosenAbility.origin.key, false)))
            arcanum.engine.Progress.Done
        }
    }

    private fun slotTargets(instructions: arcanum.api.Instructions?, modes: List<Int>, names: List<String>): Map<String, List<TargetRef>> {
        val queue = ArrayDeque(names)
        val slots = instructions?.targets.orEmpty() + modes.flatMap { instructions?.modes?.options?.getOrNull(it)?.instructions?.targets.orEmpty() }
        val result = LinkedHashMap<String, List<TargetRef>>()
        slots.forEach { slot ->
            val max = (slot.max as? arcanum.api.Value.Fixed)?.value ?: queue.size
            val taken = List(minOf(max, queue.size)) { scenario.target(queue.removeFirst()) }
            if (taken.isNotEmpty()) result[slot.name] = taken
        }
        if (queue.isNotEmpty()) result["enchant"] = queue.map { scenario.target(it) }
        return result
    }

    private fun divided(targets: Map<String, List<TargetRef>>, shares: List<Int>): List<arcanum.engine.state.DividedShare> {
        if (shares.isEmpty()) return emptyList()
        val all = targets.flatMap { (slot, refs) -> refs.map { slot to it } }
        return all.zip(shares).map { (t, n) -> arcanum.engine.state.DividedShare(t.first, t.second, n) }
    }

    /** Passes priority (CR 117.3d). */
    fun passes(): Outcome = scenario.perform(Input.PassPriority(id))

    /** Declares [attackers] attacking [defender] (the next player by default, CR 508.1). */
    fun attacks(vararg attackers: String, defender: String? = null): Outcome {
        val target = defender?.let { scenario.target(it) }
            ?: TargetRef.Player(state.opponents(id).first())
        return scenario.perform(Input.DeclareAttackers(id, attackers.map { Attack(scenario.preferred(it, scenario.inPlay), target) }))
    }

    /** Declares blocks, blocker to attacker (CR 509.1). */
    fun blocks(vararg blocks: Pair<String, String>): Outcome =
        scenario.perform(Input.DeclareBlockers(id, blocks.map { (blocker, attacker) ->
            Block(scenario.preferred(blocker, scenario.inPlay), scenario.preferred(attacker, scenario.inPlay))
        }))

    /** Answers the open decision (any player may answer for another, P7). */
    fun answers(answer: Answer): Outcome = scenario.answer(answer)

    /** Chooses objects by name for the open decision: among its options, a name given again meaning the next one. */
    fun chooses(vararg names: String): Outcome {
        val options = (scenario.pending as? Decision.ChooseObjects)?.options.orEmpty()
        return scenario.answer(Answer.Objects(names.fold(emptyList()) { chosen, name -> chosen + among(name, options, chosen) }))
    }

    /** The object named [name] among a decision's [options] not [taken] yet; when none is, any card of that name. */
    private fun among(name: String, options: List<ObjectId>, taken: List<ObjectId>): ObjectId =
        scenario.preferred(name, listOf { it.id in options }, taken)

    private fun answerAnnouncing(
        first: Outcome, targets: List<String>, x: Int?, modes: List<Int>?, paying: List<String>?,
        kicked: Int?, option: String?, choosing: List<String>, color: arcanum.api.Color?,
    ): Outcome {
        var outcome = first
        val queue = ArrayDeque(targets)
        val objects = ArrayDeque(choosing)
        while (true) {
            val answer = when (val d = scenario.pending) {
                is Decision.Targets -> if (queue.isEmpty()) null
                    else Answer.Targets(List(minOf(queue.size, d.max ?: queue.size)) { scenario.target(queue.removeFirst()) })
                is Decision.Modes -> modes?.let { Answer.Modes(it) }
                is Decision.Announce -> x?.let { Answer.Number(it) }
                is Decision.May -> if (d.cost != null) kicked?.let { Answer.YesNo(it > 0) } else null
                is Decision.ChooseNumber -> kicked?.let { Answer.Number(it) }
                is Decision.ChooseOption -> option?.let { o -> d.options.firstOrNull { it.startsWith(o) }?.let { Answer.Option(it) } }
                is Decision.ChooseObjects -> if (objects.isEmpty()) null
                    else Answer.Objects(List(minOf(objects.size, d.max ?: objects.size)) { objects.removeFirst() }
                        .fold(emptyList()) { chosen, name -> chosen + among(name, d.options, chosen) })
                is Decision.ChooseColor -> color?.let { Answer.ColorChoice(it) }
                is Decision.ManaPayment -> if (paying != null) Answer.ManaPayment(paying.fold(emptyList()) { uses, name -> uses + manaUse(name, d, uses) })
                    else d.suggestion.takeIf { it.isNotEmpty() }?.let { Answer.ManaPayment(it) }
                else -> null
            } ?: return outcome
            outcome = scenario.answer(answer)
        }
    }

    /** The use of the source named [name] for [decision]: a name given again means the next card of that name, not one
     * of the [taken] ones. */
    private fun manaUse(name: String, decision: Decision.ManaPayment, taken: List<ManaUse> = emptyList()): ManaUse {
        val color = name.substringAfter(':', "").singleOrNull()?.let { arcanum.api.Color.of(it) }
        val base = name.substringBefore(':')
        val used = taken.flatMap { u -> listOfNotNull((u as? ManaUse.Ability)?.source) + ((u as? ManaUse.Special)?.objects ?: emptyList()) }
        // "Forest#1": the second Forest
        val source: ObjectId = if ('#' in base) base.split('#').let { scenario.card(it[0], it[1].toInt()) }
            else (0 until 50).asSequence().map { runCatching { scenario.card(base, it) }.getOrNull() }
                .takeWhile { it != null }.filterNotNull().firstOrNull { it !in used } ?: scenario.card(base)
        return decision.options.filterIsInstance<ManaUse.Ability>().firstOrNull { it.source == source && (color == null || it.color == color) }
            ?: decision.options.filterIsInstance<ManaUse.Special>().firstOrNull { it.objects == listOf(source) }
            ?: fail("$name can't pay for ${decision.cost}")
    }

    /** The mana in this player's pool (CR 106.4). */
    val pool: List<arcanum.engine.state.ManaUnit> get() = state.player(id).manaPool

    // ---- Zones, for expectations

    val hand: ZoneQuery get() = ZoneQuery(scenario, "$id's hand", state.player(id).hand)
    val library: ZoneQuery get() = ZoneQuery(scenario, "$id's library", state.player(id).library)
    val graveyard: ZoneQuery get() = ZoneQuery(scenario, "$id's graveyard", state.player(id).graveyard)
    val battlefield: ZoneQuery get() = ZoneQuery(scenario, "$id's battlefield",
        state.battlefield.filter { scenario.view()[it]?.controller == id })
    val exile: ZoneQuery get() = ZoneQuery(scenario, "$id's exile", state.exile.filter { state.obj(it)?.owner == id })

    override fun toString(): String = id.value
}
