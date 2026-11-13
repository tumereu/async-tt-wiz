package org.asyncttwiz.android

import arcanum.engine.state.GameState
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import org.asyncttwiz.automatum.ComputedBoard
import org.asyncttwiz.automatum.EngineProjection
import org.asyncttwiz.automatum.boundary.BoardMirror
import org.asyncttwiz.automatum.boundary.BoardStatesIn
import org.asyncttwiz.automatum.boundary.BoardStatesOut
import org.asyncttwiz.automatum.boundary.HeldWire
import org.asyncttwiz.automatum.boundary.ListDelta
import org.asyncttwiz.automatum.boundary.MirrorShadow
import org.asyncttwiz.automatum.boundary.NoteWire
import org.asyncttwiz.automatum.boundary.ShownWire
import org.asyncttwiz.automatum.boundary.StateOrigin
import org.asyncttwiz.automatum.boundary.StateRef
import org.asyncttwiz.automatum.boundary.StatusWire
import org.asyncttwiz.automatum.boundary.TableBoardWire
import org.asyncttwiz.automatum.boundary.TableChangeWire
import org.asyncttwiz.automatum.boundary.TransitionWire
import org.asyncttwiz.automatum.boundary.TurnChangeWire
import org.asyncttwiz.automatum.boundary.UndoneWire
import org.asyncttwiz.automatum.boundary.WireDefinitionsIn
import org.asyncttwiz.automatum.boundary.WireDefinitionsOut
import org.asyncttwiz.automatum.boundary.WireEventsIn
import org.asyncttwiz.automatum.boundary.WireEventsOut
import org.asyncttwiz.protocol.game.EngineJson
import org.asyncttwiz.protocol.game.EngineView
import org.asyncttwiz.protocol.game.GameDevice
import org.asyncttwiz.protocol.game.GameEvent
import org.asyncttwiz.protocol.game.GameModel
import org.asyncttwiz.protocol.game.TableState

/*
 * Web client step 4c (docs/specs/web-client.md §1 item 3): a game's published boards ([EngineGame.Shown]) across the engine
 * boundary, one JSON message each ([ShownWire]): [ShownEncoder] on the engine side (the browser's Web Worker, which holds
 * the device), [ShownDecoder] on the page, which keeps its own copy of each board's engine state ([BoardMirror], advanced
 * by events) so the table never needs the engine to draw. Both are made per game and see the same boards in the same
 * order; neither is thread-safe. Android hands its boards over in-process and needs neither.
 */

/** The engine side: each published board of game [gameId] (with the notes published with it) as the page's message. */
class ShownEncoder(val gameId: String, private val codec: EngineJson) {
    private val events = WireEventsOut(codec)
    private val definitions = WireDefinitionsOut()
    private val states = BoardStatesOut()
    val shadow = MirrorShadow(codec)
    /** The boards the page holds by number (the newest [KEEP]), as last sent. */
    private val boards = LinkedHashMap<Long, TableBoard>()
    private var line: List<String> = emptyList()
    private var older: List<String> = emptyList()
    private var visible: List<ObjectId> = emptyList()
    private var transition: EngineGame.Transition? = null
    private var turnChange: TurnChange? = null

    /** Boards sent whole, with their engine state described for the page's copy (tests and the log). */
    var fresh = 0
        private set

    fun encode(shown: EngineGame.Shown, notes: List<GameDevice.Note> = emptyList()): String = ShownWire.encode(wire(shown, notes))

    fun wire(shown: EngineGame.Shown, notes: List<GameDevice.Note> = emptyList()): ShownWire {
        val ref = events::ref
        // The main board first: the boards of its transition walk from the page's copy of its state
        val board = board(shown.board, main = true)
        val transition = shown.transition?.let { t ->
            if (t === this.transition) TransitionWire(t.serial, known = true) else transition(t).also { this.transition = t }
        }
        val lineIds = shown.line.map(ref)
        val olderIds = shown.older.map(ref)
        val lineDelta = ListDelta.of(line, lineIds).also { line = lineIds }
        val olderDelta = ListDelta.of(older, olderIds).also { older = olderIds }
        val turnChange = shown.turnChange?.let { c ->
            if (c === this.turnChange) TurnChangeWire(c.serial, known = true)
            else TurnChangeWire(c.serial, false, c.player, ref(c.event), c.at, c.forward).also { this.turnChange = c }
        }
        val status = StatusWire.of(shown.status, ref)
        val noted = notes.map { NoteWire.of(it, ref) }
        // Every event referred to is known now: those the page lacks go with the message
        val out = ShownWire(shown.version, events.take(), definitions.take(), board, status, shown.locked, shown.settings,
            shown.opponent, shown.behind, shown.tip, shown.localHead, shown.drafting, shown.request, shown.indicator, shown.undoTarget,
            lineDelta, shown.held.size, transition, shown.needsReload, shown.pausing, shown.takingBack, shown.reveal, olderDelta,
            shown.olderComplete, shown.show, shown.showDone, shown.takeBack?.let { HeldWire(it.by, it.undo) }, shown.branchStop,
            turnChange, shown.combatHold, shown.ownActionShown, shown.opponentAt, shown.moments, shown.ownStepHeld, noted)
        // Both sides keep the events of the page's mirror line, the shown line and the older history loaded
        events.retain(retained(shadow.lineIds, lineIds, olderIds))
        return out
    }

    private fun transition(t: EngineGame.Transition): TransitionWire = TransitionWire(t.serial, false, t.cursor, t.jump,
        t.changes.map { c -> TableChangeWire(c.key.target, c.key.kind.name, c.key.color, c.key.line, c.delta.map { it.toString() }, c.owner,
            c.link, c.projectionLink) },
        t.shuffled.toList(), t.forward.map(events::ref), t.backward.map(events::ref), t.steps.map { board(it, main = false) },
        t.undone?.let { u ->
            val state = u.state
            val number = state?.let { s -> boards.entries.lastOrNull { it.value.engine?.state === s }?.key }
            UndoneWire(u.by, u.event?.let(events::ref), number,
                if (state != null && number == null) codec.encodeModelElement(GameModel(state, TableState())) else null, u.branch)
        })

    /** [b] as the page gets it: the very board or its engine state when the page holds them, else whole. [main]: the
     * message's own board (its state moves the page's mirror). */
    private fun board(b: TableBoard, main: Boolean): TableBoardWire {
        val values = b.values
        val engine = b.engine
        val number = values?.number
        if (number != null) {
            val had = boards[number]
            if (had === b) return TableBoardWire(number, TableBoardWire.SAME_BOARD)
            if (had != null && had.engine === engine) {
                remember(number, b)
                return TableBoardWire(number, TableBoardWire.SAME_ENGINE, b.gameId, b.playId, b.nodeId, states.wire(b.board),
                    definitions.ref(b.definitions), damageDealt = b.damageDealt)
            }
        }
        fresh++
        val state = engine?.let { e ->
            val table = values?.shownTable ?: e.table
            // The events the page's copy moves or walks by go with the message when the page lacks them
            when (val origin = values?.origin) {
                is StateOrigin.Line -> origin.events.forEach { events.ref(it) }
                is StateOrigin.Steps -> { origin.back.forEach { events.ref(it) }; origin.along.forEach { events.ref(it) } }
                else -> Unit
            }
            when (val origin = values?.origin) {
                is StateOrigin.Line -> if (main) shadow.line(origin, e.state, table) else shadow.whole(e.state, table)
                is StateOrigin.Steps -> if (main) shadow.whole(e.state, table) else shadow.steps(origin, e.state, table)
                StateOrigin.Whole, null -> shadow.whole(e.state, table)
            }
        }
        val seen = values?.shownVisible?.toList()
        val visibleDelta = seen?.let { ListDelta.of(visible, it).also { _ -> visible = it } }
        if (number != null) remember(number, b)
        return TableBoardWire(number, TableBoardWire.FRESH, b.gameId, b.playId, b.nodeId, states.wire(b.board), definitions.ref(b.definitions),
            state, values?.viewer ?: engine?.viewer?.value, visibleDelta, values?.computed?.wire(), values?.handPlays, values?.cardPlays,
            values?.openingBottom ?: 0, b.damageDealt)
    }

    private fun remember(number: Long, board: TableBoard) {
        boards.remove(number)
        boards[number] = board
        while (boards.size > KEEP) boards.remove(boards.keys.first())
    }

    /** The page loads the game anew (a new channel): everything is sent again. */
    fun clear() {
        events.clear(); states.clear(); shadow.clear(); boards.clear()
        line = emptyList(); older = emptyList(); visible = emptyList(); transition = null; turnChange = null
    }

    companion object {
        /** The boards both sides remember by number. */
        const val KEEP = 64

        internal fun retained(mirror: List<String>, line: List<String>, older: List<String>): Set<String> =
            HashSet<String>(mirror.size + line.size + older.size).apply { addAll(mirror); addAll(line); addAll(older) }
    }
}

/**
 * The page: game [gameId]'s published boards from their messages ([ShownEncoder]), each board's engine state its own copy,
 * advanced by events ([BoardMirror]). [engine] is the worker the boards' values work their lazy reads on (the page's own
 * thread: what the flows step leaves on the page), [lines] the game's record of the line each board stands on.
 */
class ShownDecoder(private val gameId: String, private val codec: EngineJson, private val engine: EngineWorker = EngineWorker.Direct,
    private val lines: org.asyncttwiz.automatum.boundary.BoardLines? = null) {
    private val events = WireEventsIn(codec)
    private val definitions = WireDefinitionsIn()
    private val states = BoardStatesIn()
    val mirror = BoardMirror(codec)
    private val boards = LinkedHashMap<Long, TableBoard>()
    private var line: List<String> = emptyList()
    private var older: List<String> = emptyList()
    private var visible: List<ObjectId> = emptyList()
    private var transition: EngineGame.Transition? = null
    private var turnChange: TurnChange? = null

    /** A published board and the notes published with it. */
    class Decoded(val shown: EngineGame.Shown, val notes: List<GameDevice.Note>)

    fun decode(text: String): Decoded = decode(ShownWire.decode(text))

    fun decode(w: ShownWire): Decoded {
        events.add(w.events)
        definitions.add(w.definitions)
        val board = board(w.board)
        val transition = w.transition?.let { t ->
            if (t.known) checkNotNull(this.transition?.takeIf { it.serial == t.serial }) { "The page lacks transition ${t.serial}" }
            else transition(t).also { this.transition = it }
        }
        val lineIds = w.line.applied(line).also { line = it }
        val olderIds = w.older.applied(older).also { older = it }
        val lineEvents = lineIds.map(events::get)
        val turnChange = w.turnChange?.let { c ->
            if (c.known) checkNotNull(this.turnChange?.takeIf { it.serial == c.serial }) { "The page lacks turn change ${c.serial}" }
            else TurnChange(c.serial, c.player, events[c.event], c.at, c.forward).also { this.turnChange = it }
        }
        val state = checkNotNull(board.engine?.state) { "A published board without engine state" }
        val shown = EngineGame.Shown(w.version, board, w.status.restored(events::get), w.locked, w.settings, w.opponent, w.behind, w.tip,
            w.localHead, w.drafting, w.request, w.indicator, w.undoTarget, lineEvents, lineEvents.take(w.held), transition, w.needsReload,
            w.pausing, w.takingBack, w.reveal, olderIds.map(events::get), w.olderComplete, w.show, w.showDone,
            w.takeBack?.let { EngineGame.Held(it.by, it.undo) }, w.branchStop, turnChange, w.combatHold, w.ownActionShown, w.opponentAt,
            w.moments, w.ownStepHeld, state)
        val notes = w.notes.map { it.restored(events::get) }
        events.retain(ShownEncoder.retained(mirror.events, lineIds, olderIds))
        return Decoded(shown, notes)
    }

    private fun transition(t: TransitionWire): EngineGame.Transition = EngineGame.Transition(t.serial, t.cursor, t.jump,
        t.changes.map { c -> TableChange(TableChangeKey(c.target, TableChangeKind.valueOf(c.kind), c.color, c.line),
            c.delta.map { org.asyncttwiz.appui.BigInteger(it) }, c.owner, c.link, c.projectionLink) },
        t.shuffled.toSet(), t.forward.map(events::get), t.backward.map(events::get), t.steps.map(::board),
        t.undone?.let { u ->
            val state = u.board?.let { n -> checkNotNull(boards[n]?.engine?.state) { "The page lacks board $n" } }
                ?: u.state?.let { codec.decodeModelElement(it).engine }
            EngineGame.Undone(u.by, u.event?.let(events::get), state, u.branch)
        })

    private fun board(w: TableBoardWire): TableBoard {
        val number = w.number
        if (w.reuse == TableBoardWire.SAME_BOARD) return checkNotNull(boards[checkNotNull(number)]) { "The page lacks board $number" }
        val board = states.board(checkNotNull(w.board) { "A board without its cards" })
        val defs = definitions.of(w.definitions)
        val game = w.gameId ?: gameId
        val playId = checkNotNull(w.playId)
        val nodeId = checkNotNull(w.nodeId)
        if (w.reuse == TableBoardWire.SAME_ENGINE) {
            val had = checkNotNull(boards[checkNotNull(number)]) { "The page lacks board $number" }
            return TableBoard(game, playId, nodeId, board, defs, had.engine, had.values, w.damageDealt).also { remember(number, it) }
        }
        val ref = w.state
        val viewer = w.viewer
        if (ref == null || viewer == null || number == null) {
            return TableBoard(game, playId, nodeId, board, defs, null, null, w.damageDealt)
        }
        mirror.move(ref, events::get)
        val (state, table) = state(ref)
        val seen = checkNotNull(w.visible) { "A board without what its viewer sees" }.applied(visible).also { visible = it }.toSet()
        val view = EngineView(state, table, PlayerId(viewer), seen)
        val computed = w.computed?.let { ComputedBoard.restored(it, view.state) } ?: ComputedBoard.NONE
        val values = EngineBoardValues(viewer, computed, EngineProjection.Board(board, defs), game, view.state, null, seen, engine, table,
            lines, number = number, bottom = w.openingBottom).also { it.restoredPlays(w.handPlays, w.cardPlays) }
        return TableBoard(game, playId, nodeId, board, defs, view, values, w.damageDealt).also { remember(number, it) }
    }

    /** The walk a message's boards of one motion share ([StateRef.Steps]): its events and where it has got to. */
    private class Walk(val moves: Long, val back: List<String>, val along: List<String>, val state: GameState, var table: TableState,
        var index: Int, var changes: Int)
    private var walk: Walk? = null

    /** The state and table of a board described by [ref] (the mirror moved already), which [EngineView] copies. */
    private fun state(ref: StateRef): Pair<GameState, TableState> = when (ref) {
        is StateRef.Line -> if (ref.draft == null) checkNotNull(mirror.model).let { it.engine to it.table } else mirror.state(ref, events::get)
        is StateRef.Load -> if (ref.draft == null) checkNotNull(mirror.model).let { it.engine to it.table } else mirror.state(ref, events::get)
        is StateRef.Whole -> mirror.state(ref, events::get)
        is StateRef.Steps -> steps(ref)
    }

    /** A step of a motion: on from where the walk got to when it is the same walk further along, else walked anew. */
    private fun steps(ref: StateRef.Steps): Pair<GameState, TableState> {
        val model = checkNotNull(mirror.model) { "No mirror to walk from" }
        val along = ref.along.map(events::get)
        var w = walk?.takeIf { it.moves == mirror.moves && it.back == ref.back && it.along == ref.along &&
            (it.index < ref.index || (it.index == ref.index && it.changes <= ref.changes)) }
        if (w == null) {
            val start = BoardMirror.reverted(model, ref.back.map(events::get))
            w = Walk(mirror.moves, ref.back, ref.along, start.engine, start.table, -1, 0)
            walk = w
        }
        // From where the walk is to the step: the rest of its event, the events between whole, the step's changes
        while (w.index < ref.index || (w.index == ref.index && w.changes < ref.changes)) {
            if (w.index < 0 || (w.index < ref.index && w.changes >= (along[w.index].engine?.changes?.size ?: 0))) {
                w.index++; w.changes = 0
                for (change in along[w.index].table) w.table = change.apply(w.table)
                continue
            }
            val all = along[w.index].engine?.changes.orEmpty()
            val until = if (w.index < ref.index) all.size else ref.changes
            while (w.changes < until) w.state.apply(all[w.changes++])
        }
        return w.state to w.table
    }

    private fun remember(number: Long, board: TableBoard) {
        boards.remove(number)
        boards[number] = board
        while (boards.size > ShownEncoder.KEEP) boards.remove(boards.keys.first())
    }
}
