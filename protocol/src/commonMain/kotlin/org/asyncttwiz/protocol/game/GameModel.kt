package org.asyncttwiz.protocol.game

import arcanum.engine.Engine
import arcanum.engine.EngineTrace
import arcanum.engine.Host
import arcanum.engine.Outcome
import arcanum.engine.PassCheck
import arcanum.engine.change.Change
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.state.GameState
import arcanum.engine.state.LoggedChange
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import arcanum.engine.view.View

/**
 * A game's state at one event (I-D54 §2): the engine's [engine] state and ATW's [table] state. Mutable for speed; only
 * [apply], [revert] and an [EventBuilder] change it.
 *
 * **The state at an event is the same however it was reached** (from the root, a checkpoint, or another event's state
 * by reverting and applying), because new objects' timestamps and ids read the engine's timestamp counter and the
 * rules' "this turn" questions read its log (decision ids read neither: what is asked, of whom, after how many inputs):
 * - reverting an event puts the timestamp counter back ([EnginePart.timestampBefore]);
 * - after each event, logged changes older than last turn are dropped ([GameState.forgetOlderTurns]: the rules ask only
 *   about this and last turn, D33), which keeps checkpoints and the engine's log lookups small in long games. Reverting
 *   an event that began a turn puts back what its drop dropped, while this model still holds it ([kept] drops); a model
 *   that no longer does (decoded from a checkpoint, or more than [KEPT_DROPS] turns back) becomes [stale], and its
 *   caller gets the state from a checkpoint instead ([GameHistory]).
 *
 * **The layers are worked out once per state** (engine-responsiveness R2): [view] and each player's [visible] set are
 * kept until the engine state next changes through this model ([apply], [revert], an [EventBuilder] on it, an event's
 * drop of older log entries), counted by [changes]. Change [engine] only through those; a [copy] keeps nothing. So are
 * each player's [passCheck] and the outcome of an [EventBuilder]'s first input (End turn engine speed): an abandoned
 * builder ([EventBuilder.abandon], a probe taken back) puts back what was kept of the state it began on, so the same
 * input made again there (the probed assumption sent) is applied without the engine working it out again.
 */
class GameModel(val engine: GameState, table: TableState = TableState()) {
    var table: TableState = table
        private set

    /** Set when a revert went back to a turn whose last turn's logged changes this model no longer holds: it must not be
     * used further (the engine would see another log than the same state reached forwards). */
    var stale: Boolean = false
        private set

    /** The lowest turn whose logged changes are all in the log: the log is complete for turns from here on. */
    private var floor: Int = (engine.turn.number - 1).coerceAtLeast(0)

    private class Drop(val turn: Int, val floor: Int, val dropped: List<LoggedChange>)
    private val kept = ArrayDeque<Drop>()

    /** How many times [engine] changed through this model: the kept [view] and [visible] sets are of the state since the
     * last one. */
    var changes: Long = 0L
        private set
    private var keptView: View? = null
    private val keptVisible = HashMap<PlayerId, Set<ObjectId>>()
    private val keptPasses = HashMap<PlayerId, PassCheck>()
    private val keptOutcomes = HashMap<Input, Outcome>()

    /** The engine state's view ([Engine.view]), worked out once per state: kept until the next change ([changes]). */
    fun view(): View {
        keptView?.let { kept -> return checked(kept) }
        return Engine.view(engine).also { if (keepViews) keptView = it }
    }

    /** What [player] may see of the engine state ([Host.visible], from [view]), kept until the next change. */
    fun visible(player: PlayerId): Set<ObjectId> {
        keptVisible[player]?.let { kept ->
            if (checkKept) check(kept == Host.visible(engine, player)) { "A kept visible set differs from the state's" }
            return kept
        }
        return Host.visible(engine, player, view()).also { if (keepViews) keptVisible[player] = it }
    }

    /** [Host.passCheck] of [player] on the engine state (with [view]), kept until the next change. */
    fun passCheck(player: PlayerId): PassCheck {
        keptPasses[player]?.let { kept ->
            if (checkKept) check(kept == Host.passCheck(engine, player)) { "A kept pass check differs from the state's" }
            return kept
        }
        return Host.passCheck(engine, player, view()).also { if (keepViews) keptPasses[player] = it }
    }

    /** The kept outcome of [input] performed on the engine state as it is now ([EventBuilder.perform]), or null. */
    internal fun keptOutcome(input: Input): Outcome? = keptOutcomes[input]?.also { kept ->
        if (checkKept) check(kept == Engine.perform(engine.deepCopy(), input)) { "A kept outcome differs from the state's" }
    }

    /** What is kept of the engine state as it is now ([restore] puts it back). */
    internal class Kept(val view: View?, val visible: Map<PlayerId, Set<ObjectId>>, val passes: Map<PlayerId, PassCheck>,
        val outcomes: MutableMap<Input, Outcome>)

    internal fun kept(): Kept = Kept(keptView, HashMap(keptVisible), HashMap(keptPasses), HashMap(keptOutcomes))

    /** The engine state is again the one [kept] was taken of (everything since taken back): what was kept of it is
     * kept again. [changes] still counts on. */
    internal fun restore(kept: Kept) {
        changing()
        if (!keepViews) return
        keptView = kept.view
        keptVisible.putAll(kept.visible)
        keptPasses.putAll(kept.passes)
        keptOutcomes.putAll(kept.outcomes)
    }

    /** The kept [view], or null when it isn't worked out for this state (nothing is worked out here). */
    internal fun keptView(): View? = keptView?.let(::checked)

    private fun checked(kept: View): View {
        if (checkKept) check(kept == Engine.view(engine)) { "A kept view differs from the state's" }
        return kept
    }

    /** [engine] is about to change: what was kept of it goes. */
    internal fun changing() {
        changes++
        keptView = null
        keptVisible.clear()
        keptPasses.clear()
        keptOutcomes.clear()
    }

    /** [view] is the view of [engine] as it is now (an [Outcome.view] of the changes just applied); null: none known. */
    internal fun keep(view: View?) {
        if (view != null && keepViews) keptView = view
    }

    fun copy(): GameModel = GameModel(engine.deepCopy(), table).also {
        it.floor = floor
        it.stale = stale
        it.kept.addAll(kept)
    }

    /** Applies [event] (its engine part, then its table part); a change that doesn't fit leaves the model as it was and
     * throws ([IllegalStateException] from the engine's or the table's before-value checks). */
    fun apply(event: GameEvent) {
        check(!stale) { "A stale model" }
        val engineChanges = event.engine?.changes.orEmpty()
        check(event.engine == null || engine.timestampCounter == event.engine.timestampBefore) {
            "Event ${event.id} was made on another timestamp counter (${event.engine?.timestampBefore}, here ${engine.timestampCounter})"
        }
        val counter = engine.timestampCounter
        val tableBefore = table
        var applied = 0
        if (engineChanges.isNotEmpty()) changing()
        try {
            for (change in engineChanges) { engine.apply(change); applied++ }
            for (change in event.table) table = change.apply(table)
        } catch (e: RuntimeException) {
            for (i in applied - 1 downTo 0) engine.revert(engineChanges[i])
            engine.timestampCounter = counter
            table = tableBefore
            throw e
        }
        afterEvent()
    }

    /** Takes [event] back exactly: it must be the last event applied to this model. */
    fun revert(event: GameEvent) {
        check(!stale) { "A stale model" }
        val turn = engine.turn.number
        for (change in event.table.asReversed()) table = change.revert(table)
        val part = event.engine ?: return
        val changes = part.changes
        changing()
        // Only the event that began this turn dropped older entries (the drop was the last thing it did): put them back
        // first, older than every entry kept
        val drop = kept.lastOrNull()?.takeIf { it.turn == turn && turnBefore(changes) != turn }
        if (drop != null) {
            kept.removeAt(kept.lastIndex)
            engine.log.addAll(0, drop.dropped)
            floor = drop.floor
        }
        for (change in changes.asReversed()) engine.revert(change)
        engine.timestampCounter = part.timestampBefore
        if ((engine.turn.number - 1).coerceAtLeast(0) < floor) stale = true
    }

    /** The turn number before [changes], as their first step change says (the turn now, if none changes it). */
    private fun turnBefore(changes: List<Change>): Int =
        changes.firstOrNull { it is Change.Step }?.let { (it as Change.Step).before.number } ?: engine.turn.number

    /** What every event ends with: logged changes older than last turn are dropped. */
    internal fun afterEvent() {
        val turn = engine.turn.number
        val keepFrom = turn - 1
        if (keepFrom <= floor) return
        // Turns only go up and entries are logged in order, so the dropped entries are the log's first ones
        val dropped = engine.log.takeWhile { it.turn < keepFrom }
        if (dropped.isNotEmpty()) changing()
        engine.forgetOlderTurns()
        kept.addLast(Drop(turn, floor, dropped))
        if (kept.size > KEPT_DROPS) kept.removeAt(0)
        floor = keepFrom
    }

    /** [table] changed by [change], for an [EventBuilder]. */
    internal fun applyTable(change: TableChange) { table = change.apply(table) }

    /** The same engine and table state as [other] (what makes two models of one event interchangeable). */
    fun sameAs(other: GameModel): Boolean = table == other.table && engine == other.engine

    companion object {
        /** How many turns' dropped log entries a model keeps for reverting across turns. */
        const val KEPT_DROPS = 4

        /** For tests: every kept view and visible set read is checked against working it out afresh (as the engine's
         * own `Layers.checkShortcuts`). */
        var checkKept: Boolean = false

        /** For tests (engine-responsiveness R2's count of layer computations): false, nothing is kept, and every read
         * works the view out afresh as before R2. */
        var keepViews: Boolean = true
    }
}

/**
 * Makes one [GameEvent] on [model] (I-D35: one acting point): the player's input with what follows it automatically
 * ([perform] each), the line's random answers ([answerRandoms]), a new line's reshuffle ([startLine]) and table changes
 * ([table]), each applied at once, so the model is the event's state as it is built. [build] ends the event; [abandon]
 * takes everything back. Random answers count on the state's line ([TableState.line]); [build] records how far it moved.
 */
class EventBuilder(private val model: GameModel, private val id: String, parentId: String, private val author: String) {
    /** The event the new one is made on; a device moves it when another player's table-only event slips in under its
     * draft ([GameSession], I-D56 §6: they commute). */
    var parentId: String = parentId
        internal set
    /** The response mode the event records ([GameEvent.mode]). */
    var mode: ResponseMode? = null
    /** The main button press the event records ([GameEvent.press]). */
    var press: MainPress? = null
    private val timestampBefore = model.engine.timestampCounter
    private val lineBefore = model.table.line
    private var line = lineBefore
    private val steps = mutableListOf<EngineStep>()
    private val table = mutableListOf<TableChange>()
    private val randomSteps = mutableSetOf<Int>()
    private var done = false
    /** What the model kept of the state this builder began on, while its engine state is still that one ([pristine]):
     * [abandon] puts it back, with the outcome of the first input performed. */
    private val keptBefore = model.kept()
    private var pristine = true

    /** Whether the event was built or abandoned: nothing more can be made on it. */
    val finished: Boolean get() = done

    /** Whether the event starts a new line ([startLine]). */
    val startsLine: Boolean get() = line.number != lineBefore.number

    /** Whether nothing was made yet. */
    val empty: Boolean get() = steps.isEmpty() && table.isEmpty() && line == lineBefore

    /** The inputs performed so far, in order. */
    val inputs: List<Input> get() = steps.mapNotNull { it.input }

    /** The engine changes made so far, in order. */
    val changes: List<Change> get() = steps.flatMap { it.changes }

    /** The engine steps made so far, in order. */
    val engineSteps: List<EngineStep> get() = steps.toList()

    /** The inputs performed so far that weren't answers to the engine's random decisions (those come from the line's
     * stream): what a player's flow chose, in order. */
    val playerInputs: List<Input> get() = steps.withIndex().mapNotNull { (i, step) -> step.input?.takeIf { i !in randomSteps } }

    /** The table changes made so far, in order (without the line's move, which [build] adds). */
    val tableChanges: List<TableChange> get() = table.toList()

    /** Performs [input] on the model's engine state and applies its changes; an input the engine refuses changes nothing
     * and throws ([IllegalArgumentException] for a wrong answer, [IllegalStateException] for an input it can't take).
     * [assumedBy]: the input is another player's, assumed by this one (I-D56 §3). */
    fun perform(input: Input, trace: EngineTrace = EngineTrace.None, assumedBy: PlayerId? = null): Outcome {
        check(!done) { "The event was built or abandoned" }
        val random = model.engine.pending?.decision as? Decision.Random
        // The layers once per state (R2): the model's view of the state given, the outcome's of the state after it
        val kept = if (trace == EngineTrace.None) model.keptOutcome(input) else null
        val outcome = kept ?: Engine.perform(model.engine, input, model.keptView(), trace)
        if (pristine && trace == EngineTrace.None) keptBefore.outcomes[input] = outcome
        pristine = false
        model.changing()
        outcome.changes.forEach { model.engine.apply(it) }
        model.keep(outcome.view)
        steps += EngineStep(input, outcome.changes, assumedBy)
        if (random != null && input is Input.Answer && input.decision == random.id) {
            line = line.copy(drawn = line.drawn + 1)
            randomSteps += steps.size - 1
        }
        return outcome
    }

    /** The answer the line's stream gives the open random decision [decision] (I-D56 §9), without making it. */
    fun randomAnswer(decision: Decision.Random): Answer =
        LineRandom.answer(model.table.secret, line.number, line.drawn, decision.what)

    /** Answers the engine's random decisions from the line's stream until none is open (D25: the host's randomness). */
    fun answerRandoms() {
        while (true) {
            val d = model.engine.pending?.decision as? Decision.Random ?: return
            perform(Input.Answer(d.id, randomAnswer(d)))
        }
    }

    /**
     * Starts line [number] (I-D56 §9): as the event's first step, each player's library's unknown parts
     * ([Host.unknownOrder]) are put in a new order from the new line's stream, one random answer per part, in players'
     * order; cards whose place a player knows stay where they are. Shown nowhere.
     */
    fun startLine(number: Int) {
        check(!done && steps.isEmpty()) { "A line starts with an event's first step" }
        line = Line(number, 0)
        val changes = mutableListOf<Change>()
        for (player in model.engine.players) {
            val parts = Host.unknownOrder(model.engine, player.id).map { part ->
                LineRandom.order(model.table.secret, line.number, line.drawn, part).also { line = line.copy(drawn = line.drawn + 1) }
            }
            Host.reorderUnknown(model.engine, player.id, parts)?.let { reorder ->
                pristine = false
                model.changing()
                model.engine.apply(reorder)
                changes += reorder
            }
        }
        if (changes.isNotEmpty()) steps += EngineStep(null, changes)
    }

    /** How far the event is made: the engine steps so far, the timestamp counter and the line's random answers. */
    class Mark internal constructor(internal val steps: Int, internal val counter: Long, internal val line: Line, internal val tables: Int)

    /** Where the event is now, to [takeBack] to. */
    fun mark(): Mark = Mark(steps.size, model.engine.timestampCounter, line, table.size)

    /** Takes back everything made since [mark] (a step a device holds back, I-D56 §5); the rest stays. */
    fun takeBack(mark: Mark) {
        check(!done) { "The event was built or abandoned" }
        model.changing()
        while (table.size > mark.tables) model.applyTable(table.removeAt(table.lastIndex).inverse())
        while (steps.size > mark.steps) {
            val step = steps.removeAt(steps.lastIndex)
            for (change in step.changes.asReversed()) model.engine.revert(change)
            randomSteps.remove(steps.size)
        }
        model.engine.timestampCounter = mark.counter
        line = mark.line
    }

    fun table(change: TableChange) {
        check(!done) { "The event was built or abandoned" }
        model.applyTable(change)
        table += change
    }

    fun build(): GameEvent {
        check(!done) { "The event was built or abandoned" }
        if (line != lineBefore) table(TableChange.LineMoved(lineBefore, line))
        done = true
        val event = GameEvent(id, parentId, author, if (steps.isEmpty()) null else EnginePart(timestampBefore, steps.toList()), table.toList(), mode, press)
        model.afterEvent()
        return event
    }

    fun abandon() {
        check(!done) { "The event was built or abandoned" }
        done = true
        model.changing()
        for (change in table.asReversed()) model.applyTable(change.inverse())
        for (step in steps.asReversed()) for (change in step.changes.asReversed()) model.engine.revert(change)
        model.engine.timestampCounter = timestampBefore
        // The engine state is the one it began on again
        model.restore(keptBefore)
    }
}
