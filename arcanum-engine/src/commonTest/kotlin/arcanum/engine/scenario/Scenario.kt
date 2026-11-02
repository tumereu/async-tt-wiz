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
import arcanum.engine.state.Bindings
import arcanum.engine.state.CardId
import arcanum.engine.state.NamedValue
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
fun scenario(players: Int = 2, seed: Long = 1, start: ((List<PlayerId>) -> GameState)? = null, block: Scenario.() -> Unit) {
    val scenario = Scenario(players, seed, start)
    scenario.block()
    scenario.checkEveryChangeReverts()
}

class Scenario internal constructor(playerCount: Int, seed: Long, start: ((List<PlayerId>) -> GameState)? = null) {
    val players: List<ScenarioPlayer> = NAMES.take(playerCount).map { ScenarioPlayer(this, PlayerId(it)) }
    val alice: ScenarioPlayer get() = players[0]
    val bob: ScenarioPlayer get() = players[1]
    val carol: ScenarioPlayer get() = players[2]
    val dave: ScenarioPlayer get() = players[3]

    /** The game: changed directly during setup, only by changes afterwards; [start] makes it from the player ids instead
     * (a game before it begins, [Engine.newGame]). */
    val state: GameState = start?.invoke(players.map { it.id })
        ?: GameState.start(players.map { it.id }).also { it.turn = it.turn.copy(turnStarted = players.associate { p -> p.id to 0 } + (players.first().id to 1)) }

    /** All changes applied since setup ended, in order. */
    val applied = mutableListOf<Change>()
    private var setupState: GameState? = null
    private val random = Random(seed)
    private val scripted = ArrayDeque<(RandomKind) -> Answer>()
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
        scripted.addAll(answers.map { a -> { _: RandomKind -> a } })
    }

    /** Scripts the next random results as answers to what is asked: a shuffle in a chosen order. */
    fun random(vararg answers: (RandomKind) -> Answer) {
        scripted.addAll(answers)
    }

    /**
     * An effect object made during setup, as if a spell had resolved earlier (CR 611.2), the way a resolution makes one
     * ([arcanum.engine.resolution.Effects.applyMechanics]): [mechanics] applied to the cards [on] (permanents first), or
     * to [affects] when given; layer mechanics lock their affected objects now (CR 611.2c), and so do mechanics on
     * players; others on [affects] keep applying to whatever matches. [names] are its frozen names (D37). It gets the
     * next timestamp (CR 613.7b).
     */
    fun effect(
        controller: ScenarioPlayer, vararg mechanics: Mechanic, on: List<String> = emptyList(),
        affects: arcanum.api.Affected? = null, names: Map<String, String> = emptyMap(),
        duration: arcanum.api.Duration = arcanum.api.Duration.EndOfTurn,
    ): EffectId {
        setup()
        val id = EffectId("E${state.effects.size + 1}")
        val timestamp = ++state.timestampCounter
        var frozen = Bindings(names.mapValues { (_, name) -> NamedValue.Objects(listOf(preferred(name, inPlay))) })
        val eval = arcanum.engine.casting.Snapshot(state).evaluator(controller.id, names = frozen)
        val layered = mechanics.any { it.layer != null }
        // As a resolution does: objects named by the effect itself are found by a frozen name by its other mechanics
        var affected = affects
        val locked: List<ObjectId>? = when {
            affects == null -> on.map { preferred(it, inPlay) }.also { ids ->
                frozen = frozen.with(LOCKED, NamedValue.Objects(ids))
                affected = arcanum.api.Affected.Objects(arcanum.api.Objects.Bound(LOCKED))
            }
            layered && affects is arcanum.api.Affected.Objects -> eval.objects(affects.objects)
            else -> null
        }
        state.effects[id] = EffectObject(
            id = id, mechanics = mechanics.toList(), affects = affected, locked = locked,
            lockedPlayers = (affects as? arcanum.api.Affected.Players)?.let { eval.players(it.players) },
            source = null, controller = controller.id, timestamp = timestamp, createdTurn = state.turn.number, duration = duration,
            names = frozen,
        )
        return id
    }

    /** Attaches the Aura or Equipment [attachment] to [to] during setup (CR 301.5, 303.4); it gets a new timestamp
     * (CR 613.7e). */
    fun attach(attachment: String, to: String) {
        setup()
        val id = card(attachment)
        state.objects[id] = state.objOrThrow(id).copy(attachedTo = target(to), timestamp = ++state.timestampCounter)
    }

    internal fun put(
        owner: ScenarioPlayer, names: List<String>, zone: Zone, tapped: Boolean = false, sick: Boolean = false,
        hidden: Boolean = false, counters: List<Pair<CounterKind, Int>> = emptyList(), top: Boolean = false,
        faceDown: Boolean = false,
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
                zone = zone, definition = if (hidden) null else definition, tapped = tapped, faceDown = faceDown,
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

    /**
     * The current id of the [index]th card (in setup order) whose name, or one of whose faces' names, is [name]; only
     * [owner]'s and only those in [zone] when given. Without an [index], cards of that name in different zones are
     * ambiguous and fail: a test that means one of them says which ([zone], [index]), so it can't pass by finding the
     * wrong one. The moves ([ScenarioPlayer.casts], [ScenarioPlayer.plays], [ScenarioPlayer.activates]) look where the
     * player would.
     */
    fun card(name: String, index: Int? = null, owner: ScenarioPlayer? = null, zone: Zone? = null): ObjectId {
        val matches = cards(name, owner, zone)
        if (index == null && matches.map { it.zone }.distinct().size > 1) {
            fail("\"$name\" is ambiguous: in ${matches.map { it.zone }.distinct()}; name the zone or the index")
        }
        return matches.getOrNull(index ?: 0)?.id ?: fail("No card \"$name\" (#${index ?: 0}${zone?.let { " in $it" } ?: ""}) in the game")
    }

    /** Every card named [name] (by name or a face's name), [owner]'s and in [zone] when given, in setup order, then
     * tokens and other cards the engine made. */
    internal fun cards(name: String, owner: ScenarioPlayer? = null, zone: Zone? = null): List<GameObject> = state.objects.values
        .filter { owner == null || it.owner == owner.id }
        .filter { zone == null || it.zone == zone }
        .filter { o -> nameOf(o) == name || definitionOf(o)?.faces?.any { it.name == name } == true }
        .sortedWith(compareBy({ it.card.value.substringAfter('.').toIntOrNull() ?: Int.MAX_VALUE }, { it.timestamp }))

    /**
     * The card named [name] where it is looked for: the first of [where] (in order) that has one, else [card] (which
     * fails when the name is in several zones). [taken] are skipped: a name given twice means two cards.
     */
    internal fun preferred(name: String, where: List<(GameObject) -> Boolean>, taken: Collection<ObjectId> = emptyList()): ObjectId {
        val all = cards(name).filter { it.id !in taken }
        where.forEach { test -> all.firstOrNull(test)?.let { return it.id } }
        return all.singleOrNull()?.id ?: card(name)
    }

    /** On the battlefield, else on the Stack: what targets, expectations and combat mean by a name. */
    internal val inPlay: List<(GameObject) -> Boolean> = listOf({ it.zone == Zone.Battlefield }, { it.zone == Zone.Stack })

    /** A player or an object by name, as a target: a permanent or a spell when there is one of that name. */
    fun target(name: String): TargetRef =
        players.firstOrNull { it.id.value == name }?.let { TargetRef.Player(it.id) } ?: TargetRef.Object(preferred(name, inPlay))

    internal fun definitionOf(o: GameObject): CardDefinition? = o.definition ?: secrets[o.card]

    internal fun nameOf(o: GameObject): String? = definitionOf(o)?.faces?.joinToString(" // ") { it.name }

    fun player(id: PlayerId): ScenarioPlayer = players.first { it.id == id }

    // ---- Moves

    /** Performs [input] and applies its changes (D58); answers random results and host fills at once (D25, D60). */
    fun perform(input: Input): Outcome {
        if (driving && input is Input.Answer) return driveAnswer(input)
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

    /** Players pass priority until [step] of turn [turn] begins; awaited declarations of attackers and blockers are
     * made empty ([declareNone]). */
    fun advanceTo(step: TurnStep, turn: Int = state.turn.number) {
        var guard = 200
        while ((state.turn.step != step || state.turn.number != turn) && guard-- > 0) {
            if (pending != null) fail("Waiting for ${pending} before $step")
            if (state.priority.holder == null) declareNone() else passPriority()
        }
        if (guard <= 0) fail("Never reached $step of turn $turn")
    }

    // ---- Driving resolution without priority

    /** Set once a scenario resolves through [drive]: answers then go on without giving priority. */
    private var driving = false

    /**
     * Runs an engine flow on a copy of the game as [Engine.perform] does (D56), then state-based actions and triggered
     * abilities until nothing happens (CR 704.3, 603.3), but gives no priority: for scenarios that put spells and
     * abilities on the Stack directly and resolve them. Random results and host fills are answered at once.
     */
    internal fun drive(block: (arcanum.engine.change.Recorder) -> arcanum.engine.Progress): Outcome {
        startMoves()
        driving = true
        val before = state.deepCopy()
        val recorder = arcanum.engine.change.Recorder(state.deepCopy())
        var progress = block(recorder)
        if (progress is arcanum.engine.Progress.Done) progress = arcanum.engine.Perform.settle(recorder) ?: arcanum.engine.Progress.Done
        val waiting = progress as? arcanum.engine.Progress.Waiting
        if (waiting != null) recorder.record(Changes.pending(recorder.state, waiting.pending))
        assertEquals(before, state, "The engine changed the state it was given (D56)")
        recorder.changes.forEach { apply(it) }
        val outcome = Outcome(recorder.changes.toList(), waiting?.pending)
        val decision = state.pending?.decision
        return when {
            decision is Decision.Random -> driveAnswer(Input.Answer(decision.id, randomAnswer(decision.what)))
            decision is Decision.HostFill -> driveAnswer(Input.Answer(decision.id, Answer.Filled(decision.objects.map { id ->
                FilledObject(id, secrets[state.objOrThrow(id).card] ?: fail("The host knows nothing about $id"))
            })))
            else -> outcome
        }
    }

    private fun driveAnswer(input: Input.Answer): Outcome = drive { r ->
        val open = r.state.pending ?: fail("No decision is open")
        assertEquals(open.decision.id, input.decision, "Answering another decision")
        r.record(Changes.pending(r.state, null))
        arcanum.engine.Perform.resume(r, open.continuation, input.answer)
    }

    /** Resolves the top of the Stack (CR 608), then state-based actions and triggers (CR 117.5), without priority. */
    fun resolve(): Outcome {
        if (state.stack.isEmpty()) fail("Nothing to resolve")
        if (pending != null) fail("Waiting for $pending")
        return drive { arcanum.engine.resolution.Resolution.resolveTop(it) }
    }

    /** Resolves the Stack until it is empty or a decision is open. */
    fun resolveStack() {
        var guard = 50
        while (state.stack.isNotEmpty() && pending == null && guard-- > 0) resolve()
    }

    /** Begins [step] of the current turn as a tagged event (CR 500, D33), so "at the beginning of" triggers trigger;
     * with [active], a new turn of that player begins first (its untap step aside). A stand-in for the turn structure
     * when a scenario is about triggers. */
    fun beginStep(step: TurnStep, active: ScenarioPlayer? = null): Outcome = drive { r ->
        if (active != null) {
            val number = r.state.turn.number + 1
            r.record(Changes.step(r.state, r.state.turn.copy(number = number, active = active.id, step = TurnStep.Untap,
                turnStarted = r.state.turn.turnStarted + (active.id to number)), listOf(arcanum.engine.change.Meaning.TurnBegan)))
        }
        r.record(Changes.step(r.state, r.state.turn.copy(step = step), listOf(arcanum.engine.change.Meaning.StepBegan)))
        arcanum.engine.Progress.Done
    }

    /** Checks state-based actions and puts waiting triggers on the Stack (CR 704.3, 603.3), without priority. */
    fun settle(): Outcome = drive { arcanum.engine.Progress.Done }

    /** The open decision as a [T], for expectations. */
    inline fun <reified T : Decision> decision(): T = (pending as? T) ?: fail("Expected a ${T::class.simpleName}, not $pending")

    /** Declares no attackers or no blockers for every player the game waits for (CR 508.1, 509.1). */
    fun declareNone() {
        val inputs = players.flatMap { p -> Engine.options(state, p.id) }.filter {
            (it is Input.DeclareAttackers && it.attacks.isEmpty()) || (it is Input.DeclareBlockers && it.blocks.isEmpty())
        }
        if (inputs.isEmpty()) fail("Nobody has priority and no declaration is awaited in ${state.turn}")
        inputs.forEach { perform(it) }
    }

    private fun randomAnswer(kind: RandomKind): Answer {
        scripted.removeFirstOrNull()?.let { return it(kind) }
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

        /** The name an effect made by [effect] gives the objects it names (as [arcanum.engine.resolution.Effects]). */
        private const val LOCKED = "\$affected"
    }
}
