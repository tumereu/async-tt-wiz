package org.asyncttwiz.automatum.boundary

import arcanum.engine.state.GameState
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import org.asyncttwiz.protocol.game.EngineJson
import org.asyncttwiz.protocol.game.GameEvent
import org.asyncttwiz.protocol.game.GameModel
import org.asyncttwiz.protocol.game.TableState

/*
 * Web client step 4c (docs/specs/web-client.md §1 item 3): the page keeps its own copy of each board's engine state, so it
 * never needs the engine to draw. The engine side (the browser's Web Worker, which holds the device) says how it made a
 * board's state ([StateOrigin], recorded as the board is built); [MirrorShadow] turns that into a [StateRef] relative to
 * the page's copy ([BoardMirror]: a game's model at the line of the boards it was last sent), which the page resolves
 * with the same code. The engine side moves its own copy of the page's mirror first, so it never sends a move the page
 * can't make (a revert the mirror's log no longer allows, more events than a snapshot costs): it sends the state whole
 * instead.
 */

/** How the engine side made a board's engine state, recorded as the board is built. */
sealed interface StateOrigin {
    /** The state after [events], the line after the event [start] (the device's main line up to its local head and its own
     * pending events), then [draft]'s changes applied straight to it (an event still being made: no end-of-event step). */
    class Line(val start: String, val events: List<GameEvent>, val draft: GameEvent? = null) : StateOrigin

    /**
     * A board between two (a step of a board's motion, a branch's take-back): from the state of [base], [back] reverted
     * (the latest first) as a model reverts events, then [along]'s changes applied straight: those of the events before
     * [index] whole and the first [changes] of [along][index]; the table as after [along][index] whole.
     */
    class Steps(val base: Line, val back: List<GameEvent>, val along: List<GameEvent>, val index: Int, val changes: Int) : StateOrigin

    /** Reached otherwise (from a checkpoint, older history, another line): sent whole. */
    data object Whole : StateOrigin
}

/** A board's engine state as the page gets it, relative to its [BoardMirror]. Event ids name events the page holds. */
@Serializable
sealed interface StateRef {
    /**
     * The mirror moves along its line to the line after [start]: its events before its own start that this line has
     * ([prefix]), then its own from index [from] ([shared] of them stay; the rest are reverted, the latest first), then
     * [apply] applied. The board's state is the mirror's then, with [draft]'s changes applied straight on a copy.
     */
    @Serializable @SerialName("line")
    data class Line(val start: String, val prefix: List<String> = emptyList(), val from: Int = 0, val shared: Int = 0,
        val apply: List<String> = emptyList(), val draft: JsonElement? = null) : StateRef

    /** The mirror loaded whole ([model], in the engine's stored format) at the line [events] after [start]; then [draft]. */
    @Serializable @SerialName("load")
    data class Load(val model: JsonElement, val start: String, val events: List<String>, val draft: JsonElement? = null) : StateRef

    /** [StateOrigin.Steps] from the mirror's state (the main board of the same message), the mirror not moved. */
    @Serializable @SerialName("steps")
    data class Steps(val back: List<String>, val along: List<String>, val index: Int, val changes: Int) : StateRef

    /** The state whole ([model], the engine's stored format), the mirror not moved. */
    @Serializable @SerialName("whole")
    data class Whole(val model: JsonElement) : StateRef
}

/**
 * The page's copy of a game's state at the line of the boards it was last sent ([model], with its log and dropped turns as
 * the engine's model keeps them, so it reverts exactly as the device's does), and the line's event ids ([events] after
 * [start]). The engine side keeps the same one ([MirrorShadow]) and moves it the same way. Not thread-safe.
 */
class BoardMirror(private val codec: EngineJson) {
    var model: GameModel? = null
        private set
    var start: String = ""
        private set
    var events: List<String> = emptyList()
        private set
    /** Counts the mirror's moves and loads: a walk from it worked out before is still good while this stays. */
    var moves = 0L
        private set

    /** Moves the mirror by [ref] (a [StateRef.Line] or [StateRef.Load]; others leave it). [event] finds an event by id.
     * Throws when the move doesn't fit, leaving the mirror empty (the engine side never sends one it couldn't make). */
    fun move(ref: StateRef, event: (String) -> GameEvent) {
        when (ref) {
            is StateRef.Line -> {
                val m = checkNotNull(model) { "No mirror to move" }
                moves++
                try {
                    check(ref.from + ref.shared <= events.size) { "The mirror's line is shorter than the move" }
                    val reverted = events.subList(ref.from + ref.shared, events.size)
                    for (id in reverted.asReversed()) m.revert(event(id))
                    check(!m.stale) { "The mirror can't revert that far" }
                    for (id in ref.apply) m.apply(event(id))
                    events = ref.prefix + events.subList(ref.from, ref.from + ref.shared) + ref.apply
                    start = ref.start
                } catch (problem: Exception) {
                    clear()
                    throw problem
                }
            }
            is StateRef.Load -> load(codec.decodeModelElement(ref.model), ref.start, ref.events)
            is StateRef.Steps, is StateRef.Whole -> Unit
        }
    }

    /** The mirror at [model] (owned from now on), the line [events] after [start]. */
    fun load(model: GameModel, start: String, events: List<String>) {
        moves++
        this.model = model
        this.start = start
        this.events = events
    }

    fun clear() { moves++; model = null; start = ""; events = emptyList() }

    /**
     * The state and table of a board described by [ref], as a new state (the caller's): the mirror's (after [move], for a
     * [StateRef.Line] or [StateRef.Load]) with its draft, a [StateRef.Steps] walk from the mirror, or a whole state.
     */
    fun state(ref: StateRef, event: (String) -> GameEvent): Pair<GameState, TableState> = when (ref) {
        is StateRef.Line -> drafted(checkNotNull(model), ref.draft?.let(::decodeEvent))
        is StateRef.Load -> drafted(checkNotNull(model), ref.draft?.let(::decodeEvent))
        is StateRef.Steps -> walk(checkNotNull(model), ref.back.map(event), ref.along.map(event), ref.index, ref.changes)
        is StateRef.Whole -> codec.decodeModelElement(ref.model).let { it.engine to it.table }
    }

    private fun decodeEvent(element: JsonElement): GameEvent = codec.decodeElement(GameEvent.serializer(), element)

    companion object {
        /** [model]'s state with [draft]'s changes applied straight, as a new state. */
        fun drafted(model: GameModel, draft: GameEvent?): Pair<GameState, TableState> {
            val state = model.engine.deepCopy()
            var table = model.table
            if (draft != null) {
                for (change in draft.engine?.changes.orEmpty()) state.apply(change)
                for (change in draft.table) table = change.apply(table)
            }
            return state to table
        }

        /** [StateOrigin.Steps]'s state from [base] (not changed), as a new state. Throws when [back] can't be reverted exactly. */
        fun walk(base: GameModel, back: List<GameEvent>, along: List<GameEvent>, index: Int, changes: Int): Pair<GameState, TableState> {
            val walk = reverted(base, back)
            val state = walk.engine
            var table = walk.table
            for (i in 0..index) {
                val event = along[i]
                val all = event.engine?.changes.orEmpty()
                for (change in if (i < index) all else all.take(changes)) state.apply(change)
                for (change in event.table) table = change.apply(table)
            }
            return state to table
        }

        /** A copy of [base] with [back] reverted (the latest first); throws when it can't be reverted exactly. */
        fun reverted(base: GameModel, back: List<GameEvent>): GameModel {
            val walk = base.copy()
            for (event in back) walk.revert(event)
            check(!walk.stale) { "Reverted past the turns the model holds" }
            return walk
        }

        /** The line state under a drafted board: [state] (a board's, not changed) and [table] with [draft]'s changes taken
         * back, as a new model. */
        fun undrafted(state: GameState, table: TableState, draft: GameEvent?): GameModel {
            val line = state.deepCopy()
            var lineTable = table
            if (draft != null) {
                for (change in draft.table.asReversed()) lineTable = change.revert(lineTable)
                val part = draft.engine
                if (part != null) {
                    for (change in part.changes.asReversed()) line.revert(change)
                    line.rewindTimestampCounter(part.timestampBefore)
                }
            }
            return GameModel(line, lineTable)
        }
    }
}

/**
 * The engine side's copy of the page's [BoardMirror]: says how each board's state reaches the page ([line], [steps],
 * [whole]), moving itself exactly as the page's will, and falling back to a whole state where the page's copy couldn't
 * follow. Not thread-safe (the engine thread).
 */
class MirrorShadow(private val codec: EngineJson, private val most: Int = BoardLines.MOST_EVENTS) {
    val mirror = BoardMirror(codec)
    /** The mirror line's events, as the engine side holds them. */
    private var lineEvents: List<GameEvent> = emptyList()

    /** Snapshots sent so far (tests and the log). */
    var loads = 0
        private set

    /** A main board made at [origin] whose state is [state] with [table] (the board's own copy): how it reaches the page,
     * the mirror moved there. */
    fun line(origin: StateOrigin.Line, state: GameState, table: TableState): StateRef {
        val target = origin.events.map { it.id }
        val draft = origin.draft?.let { codec.encodeElement(GameEvent.serializer(), it) }
        val model = mirror.model
        val path = if (model != null && !model.stale) path(mirror.start, mirror.events, origin.start, target, most) else null
        if (path != null) {
            val ref = StateRef.Line(origin.start, path.prefix, path.from, path.shared, path.apply, draft)
            val known = HashMap<String, GameEvent>(lineEvents.size + origin.events.size)
            for (e in lineEvents) known[e.id] = e
            for (e in origin.events) known[e.id] = e
            val moved = runCatching { mirror.move(ref) { id -> known.getValue(id) } }.isSuccess
            if (moved) { lineEvents = origin.events; return ref }
        }
        // A snapshot of the line's state (the board's without its draft), loaded as the page will decode it
        val line = BoardMirror.undrafted(state, table, origin.draft)
        mirror.load(GameModel(line.engine.deepCopy(), line.table), origin.start, target)
        lineEvents = origin.events
        loads++
        return StateRef.Load(codec.encodeModelElement(line), origin.start, target, draft)
    }

    /** A board made at [origin] in the same message as the main board the mirror is at now: [StateRef.Steps], or the
     * board's [state] and [table] whole when the page's mirror can't walk back as the device's model did. */
    fun steps(origin: StateOrigin.Steps, state: GameState, table: TableState): StateRef {
        val model = mirror.model
        val key = origin.back.map { it.id }
        val fits = model != null && origin.base.draft == null && sameLine(origin.base) && (origin.back.isEmpty() || walks(model, key, origin.back))
        return if (fits) StateRef.Steps(key, origin.along.map { it.id }, origin.index, origin.changes) else whole(state, table)
    }

    /** The board's [state] and [table] whole. */
    fun whole(state: GameState, table: TableState): StateRef = StateRef.Whole(codec.encodeModelElement(GameModel(state, table)))

    /** The walks back checked in this message (by the events reverted): the page's mirror reverts the same. */
    private var walked: Pair<Long, Map<List<String>, Boolean>>? = null

    private fun walks(model: GameModel, key: List<String>, back: List<GameEvent>): Boolean {
        val checked = walked?.takeIf { it.first == mirror.moves }?.second.orEmpty()
        checked[key]?.let { return it }
        val fits = runCatching { BoardMirror.reverted(model, back) }.isSuccess
        walked = mirror.moves to (checked + (key to fits))
        return fits
    }

    /** Whether the mirror is at [line] (the main board the steps were made from). */
    private fun sameLine(line: StateOrigin.Line): Boolean =
        mirror.start == line.start && mirror.events.size == line.events.size && line.events.indices.all { mirror.events[it] == line.events[it].id }

    /** The page's mirror line's event ids (what it keeps, [WireEvents]). */
    val lineIds: List<String> get() = mirror.events

    /** The page loaded the game anew: the next board is sent whole. */
    fun clear() { mirror.clear(); lineEvents = emptyList() }

    /** How the mirror's line (events [a] after [aStart]) becomes the line [b] after [bStart]. */
    class Path(val prefix: List<String>, val from: Int, val shared: Int, val apply: List<String>, val reverted: Int)

    companion object {
        /** The path from line [a] (after [aStart]) to line [b] (after [bStart]), as [BoardLines.between] finds it; null when
         * they share no point or more than [most] events would be reverted and applied. */
        fun path(aStart: String, a: List<String>, bStart: String, b: List<String>, most: Int): Path? {
            val (aAt, bAt) = when {
                aStart == bStart -> 0 to 0
                else -> a.indexOf(bStart).takeIf { it >= 0 }?.let { (it + 1) to 0 }
                    ?: b.indexOf(aStart).takeIf { it >= 0 }?.let { 0 to (it + 1) }
                    ?: return null
            }
            var shared = 0
            while (aAt + shared < a.size && bAt + shared < b.size && a[aAt + shared] == b[bAt + shared]) shared++
            val reverted = a.size - aAt - shared
            val apply = b.subList(bAt + shared, b.size)
            if (reverted + apply.size > most) return null
            return Path(b.subList(0, bAt).toList(), aAt, shared, apply.toList(), reverted)
        }
    }
}
