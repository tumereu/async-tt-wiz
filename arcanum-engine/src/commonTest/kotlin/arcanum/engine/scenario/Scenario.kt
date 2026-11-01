package arcanum.engine.scenario

import arcanum.api.CardDefinition
import arcanum.api.CounterKind
import arcanum.api.Mechanic
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.Engine
import arcanum.engine.Outcome
import arcanum.engine.cards.Cards
import arcanum.engine.change.Change
import arcanum.engine.change.Changes
import arcanum.engine.change.Meaning
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.decision.FilledObject
import arcanum.engine.decision.RandomKind
import arcanum.engine.input.Input
import arcanum.engine.state.CardId
import arcanum.engine.state.ControlSince
import arcanum.engine.state.CounterEntry
import arcanum.engine.state.EffectId
import arcanum.engine.state.EffectObject
import arcanum.engine.state.GameObject
import arcanum.engine.state.GameState
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import arcanum.engine.state.PriorityState
import arcanum.engine.state.TargetRef
import arcanum.engine.state.TurnState
import arcanum.engine.view.View
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.fail

/**
 * Runs an engine scenario (D61): setup, then moves, then expectations, in that order and readable as a game.
 *
 * ```
 * scenario {
 *     alice.battlefield("Mountain", "Lineage Druid")
 *     alice.hand("Storm Spark")
 *     // CR 601.2
 *     alice.casts("Storm Spark", target = "Lineage Druid")
 *     allPass()
 *     expect {
 *         alice.graveyard has "Lineage Druid"
 *     }
 * }
 * ```
 *
 * Every scenario checks D59 at the end: all changes applied since the setup are reverted, last first, and the state
 * must equal the state after setup (the timestamp counter aside, D34).
 */
fun scenario(players: Int = 2, seed: Long = 1, block: Scenario.() -> Unit) {
    val scenario = Scenario(players, seed)
    scenario.block()
    scenario.checkEveryChangeReverts()
}

class Scenario internal constructor(playerCount: Int, seed: Long) {
    val players: List<ScenarioPlayer> = NAMES.take(playerCount).map { ScenarioPlayer(this, PlayerId(it)) }
    val alice: ScenarioPlayer get() = players[0]
    val bob: ScenarioPlayer get() = players[1]
    val carol: ScenarioPlayer get() = players[2]
    val dave: ScenarioPlayer get() = players[3]

    /** The game: changed directly during setup, only by changes afterwards. */
    val state: GameState = GameState.start(players.map { it.id }).also { it.turn = it.turn.copy(turnStarted = players.associate { p -> p.id to 0 } + (players.first().id to 1)) }

    /** All changes applied since setup ended, in order. */
    val applied = mutableListOf<Change>()
    private var setupState: GameState? = null
    private val random = Random(seed)
    private val scripted = ArrayDeque<Answer>()
    private val secrets = mutableMapOf<CardId, CardDefinition>()
    private var nextCard = 0

    // ---- Setup

    /** Sets the turn: its [number], [active] player, [step], and who has priority (the active player by default). */
    fun turn(number: Int = 1, active: ScenarioPlayer = alice, step: TurnStep = TurnStep.PrecombatMain, priority: ScenarioPlayer? = active) {
        setup()
        val started = players.associate { p ->
            val order = players.indexOf(p) - players.indexOf(active)
            p.id to (number + order - (if (order > 0) players.size else 0)).coerceAtLeast(0)
        }
        state.turn = TurnState(number, active.id, step, turnStarted = started)
        state.priority = PriorityState(priority?.id)
    }

    /** Scripts the next random results, used before seeded ones (D25): coin flips, die rolls, random picks. */
    fun random(vararg answers: Answer) {
        scripted.addAll(answers)
    }

    /** An effect object made by hand during setup (as if a spell had resolved earlier). */
    fun effect(
        controller: ScenarioPlayer, vararg mechanics: Mechanic, on: List<String>, duration: arcanum.api.Duration = arcanum.api.Duration.EndOfTurn,
    ): EffectId {
        setup()
        val id = EffectId("E${state.effects.size + 1}")
        val timestamp = ++state.timestampCounter
        state.effects[id] = EffectObject(
            id = id, mechanics = mechanics.toList(), affects = null, locked = on.map { card(it) }, source = null,
            controller = controller.id, timestamp = timestamp, createdTurn = state.turn.number, duration = duration,
        )
        return id
    }

    internal fun put(
        owner: ScenarioPlayer, names: List<String>, zone: Zone, tapped: Boolean = false, sick: Boolean = false,
        hidden: Boolean = false, counters: List<Pair<CounterKind, Int>> = emptyList(), top: Boolean = false,
    ): List<ObjectId> {
        setup()
        return names.map { name ->
            val definition = Cards.definition(name)
            val card = CardId("${owner.id.value}.${++nextCard}")
            val id = ObjectId.Card(card, 0)
            val timestamp = ++state.timestampCounter
            if (hidden) secrets[card] = definition
            val onBattlefield = zone == Zone.Battlefield
            val o = GameObject(
                id = id, owner = owner.id, controller = if (onBattlefield || zone == Zone.Stack) owner.id else null,
                zone = zone, definition = if (hidden) null else definition, tapped = tapped,
                counters = counters.map { (kind, n) -> CounterEntry(kind, n, timestamp) }
                    .sortedWith(compareBy({ it.timestamp }, { it.kind.toString() })),
                timestamp = timestamp,
                // Set up on the battlefield: controlled since before the game began, unless [sick] (CR 302.6)
                controlSince = if (onBattlefield) ControlSince(owner.id, if (sick) state.turn.number else -1) else null,
            )
            state.objects[id] = o
            val list = state.zone(zone, owner.id).toMutableList()
            if (top) list.add(0, id) else list.add(id)
            setZone(zone, owner.id, list)
            id
        }
    }

    private fun setZone(zone: Zone, owner: PlayerId, ids: List<ObjectId>) {
        when (zone) {
            Zone.Battlefield -> state.battlefield = ids.toMutableList()
            Zone.Stack -> state.stack = ids.toMutableList()
            Zone.Exile -> state.exile = ids.toMutableList()
            Zone.Command -> state.command = ids.toMutableList()
            else -> {
                val index = state.players.indexOfFirst { it.id == owner }
                val p = state.players[index]
                state.players[index] = when (zone) {
                    Zone.Library -> p.copy(library = ids)
                    Zone.Hand -> p.copy(hand = ids)
                    Zone.Graveyard -> p.copy(graveyard = ids)
                    else -> p.copy(outside = ids)
                }
            }
        }
    }

    internal fun setup() {
        check(setupState == null) { "Setup comes before the first move" }
    }

    private fun startMoves() {
        if (setupState == null) setupState = state.deepCopy()
    }

    // ---- Finding objects

    /** The current id of the [index]th card (in setup order) whose name, or one of whose faces' names, is [name]. */
    fun card(name: String, index: Int = 0, owner: ScenarioPlayer? = null): ObjectId {
        val matches = state.objects.values
            .filter { owner == null || it.owner == owner.id }
            .filter { o -> nameOf(o) == name || definitionOf(o)?.faces?.any { it.name == name } == true }
            .sortedBy { it.card.value.substringAfter('.').toInt() }
        return matches.getOrNull(index)?.id ?: fail("No card \"$name\" (#$index) in the game")
    }

    /** A player or an object by name, as a target. */
    fun target(name: String): TargetRef =
        players.firstOrNull { it.id.value == name }?.let { TargetRef.Player(it.id) } ?: TargetRef.Object(card(name))

    internal fun definitionOf(o: GameObject): CardDefinition? = o.definition ?: secrets[o.card]

    internal fun nameOf(o: GameObject): String? = definitionOf(o)?.faces?.joinToString(" // ") { it.name }

    fun player(id: PlayerId): ScenarioPlayer = players.first { it.id == id }

    // ---- Moves

    /** Performs [input] and applies its changes (D58); answers random results and host fills at once (D25, D60). */
    fun perform(input: Input): Outcome {
        startMoves()
        val before = state.deepCopy()
        val outcome = Engine.perform(state, input)
        assertEquals(before, state, "Engine.perform changed the state it was given (D56)")
        outcome.changes.forEach { apply(it) }
        val decision = state.pending?.decision
        return when {
            decision is Decision.Random -> perform(Input.Answer(decision.id, randomAnswer(decision.what)))
            decision is Decision.HostFill -> perform(Input.Answer(decision.id, Answer.Filled(decision.objects.map { id ->
                FilledObject(id, secrets[state.objOrThrow(id).card] ?: fail("The host knows nothing about $id"))
            })))
            else -> outcome
        }
    }

    /** Applies a change made by hand (a manual move, D42) as part of the scenario. */
    fun change(change: Change) {
        startMoves()
        apply(change)
    }

    /** Moves a card by hand ([Meaning.Manual], D42). */
    fun move(name: String, to: Zone) = change(Changes.move(state, listOf(card(name)), to, meanings = listOf(Meaning.Manual)))

    private fun apply(change: Change) {
        state.apply(change)
        applied += change
    }

    /** The open decision, if any. */
    val pending: Decision? get() = state.pending?.decision

    /** Answers the open decision. */
    fun answer(answer: Answer): Outcome = perform(Input.Answer(pending?.id ?: fail("Nothing to answer"), answer))

    /** The player with priority passes it (CR 117.3d). */
    fun passPriority(): Outcome = perform(Input.PassPriority(state.priority.holder ?: fail("Nobody has priority")))

    /** Every player passes in turn, starting with the one who has priority (CR 117.4), so the top of the Stack resolves
     * or the step ends. */
    fun allPass() {
        repeat(state.players.count { !it.lost }) { if (state.priority.holder != null && pending == null) passPriority() }
    }

    /** Players pass priority until [step] of turn [turn] begins. */
    fun advanceTo(step: TurnStep, turn: Int = state.turn.number) {
        var guard = 200
        while ((state.turn.step != step || state.turn.number != turn) && guard-- > 0) {
            if (pending != null) fail("Waiting for ${pending} before $step")
            passPriority()
        }
        if (guard <= 0) fail("Never reached $step of turn $turn")
    }

    private fun randomAnswer(kind: RandomKind): Answer {
        scripted.removeFirstOrNull()?.let { return it }
        return when (kind) {
            RandomKind.CoinFlip -> Answer.CoinFlip(random.nextBoolean())
            is RandomKind.Die -> Answer.DieRolls(List(kind.count) { random.nextInt(1, kind.sides + 1) })
            is RandomKind.Objects -> Answer.RandomObjects(kind.among.shuffled(random).take(kind.count))
        }
    }

    // ---- Expectations

    /** Checks the game now; the view is worked out once for the block. */
    fun expect(block: Expectations.() -> Unit) {
        Expectations(this, Engine.view(state)).block()
    }

    /** The derived view now. */
    fun view(): View = Engine.view(state)

    /** D59: reverting every applied change, last first, gives back the state after setup. */
    internal fun checkEveryChangeReverts() {
        val start = setupState ?: return
        val reverted = state.deepCopy()
        applied.asReversed().forEach { reverted.revert(it) }
        check(reverted.timestampCounter >= start.timestampCounter) { "The timestamp counter went back (D34)" }
        reverted.timestampCounter = start.timestampCounter
        if (reverted != start) fail("Reverting the scenario's changes didn't restore its start (D59):\n${StateDiff.between(start, reverted)}")
    }

    companion object {
        val NAMES = listOf("alice", "bob", "carol", "dave", "erin", "frank")
    }
}
