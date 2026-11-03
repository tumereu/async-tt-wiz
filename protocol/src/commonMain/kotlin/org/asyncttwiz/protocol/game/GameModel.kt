package org.asyncttwiz.protocol.game

import arcanum.engine.Engine
import arcanum.engine.EngineTrace
import arcanum.engine.Outcome
import arcanum.engine.change.Change
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.state.GameState
import arcanum.engine.state.LoggedChange
import arcanum.engine.state.PlayerId

/**
 * A game's state at one event (I-D54 §2): the engine's [engine] state and ATW's [table] state. Mutable for speed; only
 * [apply], [revert] and an [EventBuilder] change it.
 *
 * **The state at an event is the same however it was reached** (from the root, a checkpoint, or another event's state
 * by reverting and applying), because the engine's own decision ids read its timestamp counter and log size:
 * - reverting an event puts the timestamp counter back ([EnginePart.timestampBefore]);
 * - after each event, logged changes older than last turn are dropped ([GameState.forgetOlderTurns]: the rules ask only
 *   about this and last turn, D33), which keeps checkpoints and the engine's log lookups small in long games. Reverting
 *   an event that began a turn puts back what its drop dropped, while this model still holds it ([kept] drops); a model
 *   that no longer does (decoded from a checkpoint, or more than [KEPT_DROPS] turns back) becomes [stale], and its
 *   caller gets the state from a checkpoint instead ([GameHistory]).
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
        // Only the event that began this turn dropped older entries (the drop was the last thing it did): put them back
        // first, older than every entry kept
        val drop = kept.lastOrNull()?.takeIf { it.turn == turn && turnBefore(changes) != turn }
        if (drop != null) {
            kept.removeLast()
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
        engine.forgetOlderTurns()
        kept.addLast(Drop(turn, floor, dropped))
        if (kept.size > KEPT_DROPS) kept.removeFirst()
        floor = keepFrom
    }

    /** [table] changed by [change], for an [EventBuilder]. */
    internal fun applyTable(change: TableChange) { table = change.apply(table) }

    /** The same engine and table state as [other] (what makes two models of one event interchangeable). */
    fun sameAs(other: GameModel): Boolean = table == other.table && engine == other.engine

    companion object {
        /** How many turns' dropped log entries a model keeps for reverting across turns. */
        const val KEPT_DROPS = 4
    }
}

/**
 * Makes one [GameEvent] on [model] (I-D35: one acting point): the player's input with what follows it automatically
 * ([perform] each), the line's random answers ([answerRandoms]), a new line's reshuffle ([startLine]) and table changes
 * ([table]), each applied at once, so the model is the event's state as it is built. [build] ends the event; [abandon]
 * takes everything back. Random answers count on the state's line ([TableState.line]); [build] records how far it moved.
 */
class EventBuilder(private val model: GameModel, private val id: String, private val parentId: String, private val author: String) {
    private val timestampBefore = model.engine.timestampCounter
    private val lineBefore = model.table.line
    private var line = lineBefore
    private val steps = mutableListOf<EngineStep>()
    private val table = mutableListOf<TableChange>()
    private var done = false

    /** Whether nothing was made yet. */
    val empty: Boolean get() = steps.isEmpty() && table.isEmpty() && line == lineBefore

    /** Performs [input] on the model's engine state and applies its changes; an input the engine refuses changes nothing
     * and throws ([IllegalArgumentException] for a wrong answer, [IllegalStateException] for an input it can't take).
     * [assumedBy]: the input is another player's, assumed by this one (I-D56 §3). */
    fun perform(input: Input, trace: EngineTrace = EngineTrace.None, assumedBy: PlayerId? = null): Outcome {
        check(!done)
        val random = model.engine.pending?.decision as? Decision.Random
        val outcome = Engine.perform(model.engine, input, trace)
        outcome.changes.forEach { model.engine.apply(it) }
        steps += EngineStep(input, outcome.changes, assumedBy)
        if (random != null && input is Input.Answer && input.decision == random.id) line = line.copy(drawn = line.drawn + 1)
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
     * ([Engine.unknownOrder]) are put in a new order from the new line's stream, one random answer per part, in players'
     * order; cards whose place a player knows stay where they are. Shown nowhere.
     */
    fun startLine(number: Int) {
        check(!done && steps.isEmpty()) { "A line starts with an event's first step" }
        line = Line(number, 0)
        val changes = mutableListOf<Change>()
        for (player in model.engine.players) {
            val parts = Engine.unknownOrder(model.engine, player.id).map { part ->
                LineRandom.order(model.table.secret, line.number, line.drawn, part).also { line = line.copy(drawn = line.drawn + 1) }
            }
            Engine.reorderUnknown(model.engine, player.id, parts)?.let { reorder ->
                model.engine.apply(reorder)
                changes += reorder
            }
        }
        if (changes.isNotEmpty()) steps += EngineStep(null, changes)
    }

    fun table(change: TableChange) {
        check(!done)
        model.applyTable(change)
        table += change
    }

    fun build(): GameEvent {
        check(!done)
        if (line != lineBefore) table(TableChange.LineMoved(lineBefore, line))
        done = true
        val event = GameEvent(id, parentId, author, if (steps.isEmpty()) null else EnginePart(timestampBefore, steps.toList()), table.toList())
        model.afterEvent()
        return event
    }

    fun abandon() {
        check(!done)
        done = true
        for (change in table.asReversed()) model.applyTable(change.inverse())
        for (step in steps.asReversed()) for (change in step.changes.asReversed()) model.engine.revert(change)
        model.engine.timestampCounter = timestampBefore
    }
}
