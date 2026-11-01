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
    fun battlefield(vararg names: String, tapped: Boolean = false, sick: Boolean = false, counters: List<Pair<CounterKind, Int>> = emptyList()) {
        scenario.put(this, names.toList(), Zone.Battlefield, tapped = tapped, sick = sick, counters = counters)
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

    /** Plays the land [name] (CR 305.1). */
    fun plays(name: String): Outcome = scenario.perform(Input.PlayLand(id, scenario.card(name)))

    /**
     * Casts [name] (CR 601.2) and answers the cast's decisions from what is given: targets by name (players by their
     * name) in slot order, modes, X, and the mana sources to tap ([paying], by name; else the engine's suggestion).
     * Decisions nothing here answers stay open for the test.
     */
    fun casts(
        name: String, target: String? = null, targets: List<String> = listOfNotNull(target), x: Int? = null,
        modes: List<Int>? = null, paying: List<String>? = null, way: CastWay = CastWay(),
    ): Outcome {
        val outcome = scenario.perform(Input.Cast(id, scenario.card(name), way))
        return answerAnnouncing(outcome, targets, x, modes, paying)
    }

    /** Activates the [ability]th activated ability (from 0, in the order the object has them) of [name] (CR 602). */
    fun activates(
        name: String, ability: Int = 0, target: String? = null, targets: List<String> = listOfNotNull(target), x: Int? = null,
        modes: List<Int>? = null, paying: List<String>? = null,
    ): Outcome {
        val source = scenario.card(name)
        val abilities = scenario.view()[source]?.characteristics?.abilities.orEmpty().filter { it.ability is Ability.Activated }
        val chosen = abilities.getOrNull(ability) ?: fail("$name has no activated ability #$ability")
        val outcome = scenario.perform(Input.Activate(id, source, chosen.origin))
        return answerAnnouncing(outcome, targets, x, modes, paying)
    }

    /** Passes priority (CR 117.3d). */
    fun passes(): Outcome = scenario.perform(Input.PassPriority(id))

    /** Declares [attackers] attacking [defender] (the next player by default, CR 508.1). */
    fun attacks(vararg attackers: String, defender: String? = null): Outcome {
        val target = defender?.let { scenario.target(it) }
            ?: TargetRef.Player(state.opponents(id).first())
        return scenario.perform(Input.DeclareAttackers(id, attackers.map { Attack(scenario.card(it), target) }))
    }

    /** Declares blocks, blocker to attacker (CR 509.1). */
    fun blocks(vararg blocks: Pair<String, String>): Outcome =
        scenario.perform(Input.DeclareBlockers(id, blocks.map { (blocker, attacker) -> Block(scenario.card(blocker), scenario.card(attacker)) }))

    /** Answers the open decision (any player may answer for another, P7). */
    fun answers(answer: Answer): Outcome = scenario.answer(answer)

    /** Chooses objects by name for the open decision. */
    fun chooses(vararg names: String): Outcome = scenario.answer(Answer.Objects(names.map { scenario.card(it) }))

    private fun answerAnnouncing(first: Outcome, targets: List<String>, x: Int?, modes: List<Int>?, paying: List<String>?): Outcome {
        var outcome = first
        val queue = ArrayDeque(targets)
        while (true) {
            val answer = when (val d = scenario.pending) {
                is Decision.Targets -> if (queue.isEmpty()) null
                    else Answer.Targets(List(minOf(queue.size, d.max ?: queue.size)) { scenario.target(queue.removeFirst()) })
                is Decision.Modes -> modes?.let { Answer.Modes(it) }
                is Decision.Announce -> x?.let { Answer.Number(it) }
                is Decision.ManaPayment -> if (paying != null) Answer.ManaPayment(paying.map { manaUse(it, d) })
                    else d.suggestion.takeIf { it.isNotEmpty() }?.let { Answer.ManaPayment(it) }
                else -> null
            } ?: return outcome
            outcome = scenario.answer(answer)
        }
    }

    private fun manaUse(name: String, decision: Decision.ManaPayment): ManaUse {
        val source: ObjectId = scenario.card(name)
        return decision.options.filterIsInstance<ManaUse.Ability>().firstOrNull { it.source == source }
            ?: fail("$name can't pay for ${decision.cost}")
    }

    // ---- Zones, for expectations

    val hand: ZoneQuery get() = ZoneQuery(scenario, "$id's hand", state.player(id).hand)
    val library: ZoneQuery get() = ZoneQuery(scenario, "$id's library", state.player(id).library)
    val graveyard: ZoneQuery get() = ZoneQuery(scenario, "$id's graveyard", state.player(id).graveyard)
    val battlefield: ZoneQuery get() = ZoneQuery(scenario, "$id's battlefield",
        state.battlefield.filter { scenario.view()[it]?.controller == id })
    val exile: ZoneQuery get() = ZoneQuery(scenario, "$id's exile", state.exile.filter { state.obj(it)?.owner == id })

    override fun toString(): String = id.value
}
