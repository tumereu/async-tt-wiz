package org.asyncttwiz.android

import arcanum.engine.input.Block
import arcanum.engine.input.Input
import arcanum.engine.setup.GameResult
import arcanum.engine.state.GameState
import arcanum.engine.state.PlayerId
import org.asyncttwiz.automatum.EngineProjection
import org.asyncttwiz.protocol.BoardState
import org.asyncttwiz.protocol.EngineRewindRequest
import org.asyncttwiz.protocol.EngineSnapshot
import org.asyncttwiz.protocol.GameAction
import org.asyncttwiz.protocol.GameRevision
import org.asyncttwiz.protocol.PlayState
import org.asyncttwiz.protocol.game.EngineJson
import org.asyncttwiz.protocol.game.GameDevice
import org.asyncttwiz.protocol.game.GameEvent
import org.asyncttwiz.protocol.game.GameModel
import org.asyncttwiz.protocol.game.GameRepro
import org.asyncttwiz.protocol.game.PlaySettings
import org.asyncttwiz.protocol.game.TableChange
import java.util.UUID
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors

/**
 * Where a game's engine work runs (I-D56 step D2, engine-state-architecture §12 item 11): one engine thread owns every
 * [GameDevice] and does its work in order (the server's events, the device's own steps, the board's projection); the
 * table reads only the snapshots it publishes ([EngineGame.Shown]) on the main thread.
 */
interface EngineWorker {
    /** Runs [work] on the engine thread, after the work queued before it. */
    fun run(work: () -> Unit)

    /** Runs [work] on the engine thread and waits for its result: the player's own action, whose local result shows at
     * once (SYS-025); inline when already on the engine thread. */
    fun <T> call(work: () -> T): T

    /** Runs [work] on the main thread. */
    fun main(work: () -> Unit)

    /** On the main thread: runs [work] once the main thread's next frame is drawn (the own-action probe, SYS-030); at
     * once by default (tests). */
    fun frame(work: () -> Unit) = main(work)

    /** Runs [work] on the engine thread and waits only until it calls its release (or ends): an own action whose board
     * shows before its event is finished (SYS-025). Without a release (null: inline when already on the engine thread,
     * and [call] by default, so tests run it in order) the caller waits for all of it. */
    fun early(work: (release: (() -> Unit)?) -> Unit) { call { work(null) } }

    /** Everything inline on the caller's thread (tests and simulations: the same order, synchronously). */
    object Direct : EngineWorker {
        override fun run(work: () -> Unit) = work()
        override fun <T> call(work: () -> T): T = work()
        override fun main(work: () -> Unit) = work()
    }

    /** The app's one engine thread (made on first use), results posted to the main looper. */
    object Background : EngineWorker {
        @Volatile private var thread: Thread? = null
        private val executor by lazy {
            Executors.newSingleThreadExecutor { work -> Thread(work, "atw-engine").apply { isDaemon = true; thread = this } }
        }
        private val handler by lazy { android.os.Handler(android.os.Looper.getMainLooper()) }

        override fun run(work: () -> Unit) {
            executor.execute {
                // A failing step never stops the thread: the next event or action carries on (a device that can't follow
                // asks to load the game again)
                try { work() } catch (failure: Throwable) { AppLog.error("engine", "Engine work failed", failure) }
            }
        }

        override fun <T> call(work: () -> T): T {
            if (Thread.currentThread() === thread) return work()
            try { return executor.submit<T> { work() }.get() }
            catch (failure: ExecutionException) { throw failure.cause ?: failure }
        }

        override fun main(work: () -> Unit) {
            if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) work() else handler.post(work)
        }

        /** The next frame's callback posts [work], which runs once that frame's main-thread work (the recomposition and
         * the drawing it brings) is done. */
        override fun frame(work: () -> Unit) = main {
            android.view.Choreographer.getInstance().postFrameCallback { handler.post(work) }
        }

        override fun early(work: (release: (() -> Unit)?) -> Unit) {
            if (Thread.currentThread() === thread) { work(null); return }
            val gate = java.util.concurrent.CountDownLatch(1)
            executor.execute {
                try { work { gate.countDown() } } catch (failure: Throwable) { AppLog.error("engine", "Engine work failed", failure) }
                finally { gate.countDown() }
            }
            gate.await()
        }
    }
}

/**
 * One game played on engine state on this device (I-D54 item 2, I-D56 step C): the device core ([GameDevice], which
 * holds the main line, playback, assumptions, undo and requests) and the board the table shows of it ([TableBoard], the
 * render model: [EngineProjection]'s board with the engine state beside it, engine-responsiveness R4a). Everything it sends is in [take]; the
 * server's revisions and refusals come in through [revision] and [refused]. Times are the caller's.
 *
 * Threads (step D2): the device lives on the [worker]'s engine thread; every change there ends by publishing a new
 * [Shown] snapshot, which the main thread reads ([shown], [onShown] tells it a new one is in). The player's own actions
 * wait for their local result on the engine thread; the device's own steps after them, the server's events and the
 * board's projection don't hold the main thread. [device] is the engine thread's only (tests run [EngineWorker.Direct]).
 *
 * [base] is the play as it started (its ids, containers, players and card data), which the server still sends with
 * every head.
 */
internal class EngineGame(val gameId: String, val me: String, base: PlayState, snapshot: EngineSnapshot,
    private val ids: () -> String = { UUID.randomUUID().toString() },
    private val worker: EngineWorker = EngineWorker.Direct, private val onShown: () -> Unit = {},
    /** The own-action probe's clock (SYS-030), in nanoseconds. */
    private val clock: () -> Long = System::nanoTime) {
    @Volatile var base: PlayState = base
        private set
    /** The device core: on the engine thread only. */
    lateinit var device: GameDevice
        private set
    lateinit var codec: EngineJson
        private set
    /** The revision of the game this device has applied (main thread). */
    var revision: String? = null
    /** The notes waiting to be shown once (§8, §10): the branch note, the take-back note, refusals (main thread). */
    val notes = ArrayDeque<GameDevice.Note>()

    /**
     * What the table shows now, published by the engine thread after each change: the board ([board]) and what the async
     * panels read of the device. Immutable; [engine] is the board's own copy of the engine state.
     */
    class Shown(val version: Long, val board: TableBoard, val status: GameDevice.Status, val locked: Boolean,
        val settings: PlaySettings, val opponent: PlayerId, val behind: Boolean, val tip: String, val localHead: String,
        val drafting: Boolean, val request: EngineRewindRequest?, val indicator: GameDevice.Indicator?, val undoTarget: String?,
        /** The events shown to the player: the main line up to the local head ([held]), then their own pending events. */
        val line: List<GameEvent>, val held: List<GameEvent>, val transition: Transition?, val needsReload: Boolean,
        /** The player paused playback (§27): it goes on to the next point where Full control stops. */
        val pausing: Boolean = false,
        /** The first passes of the player's recent End turns (§29.2: the Undo dialog lists them as "End turn"). */
        val endTurns: Set<String> = emptySet()) {
        val engine: GameState get() = board.engine!!.state
        /** Engine-responsiveness R3: what the engine thread worked out with [board]: its computed card values, the player's
         * hand plays (none yet on an own action's early board: they come with the board after it) and, when first asked,
         * the card menus' plays, the shared flow and combat. */
        val values: EngineBoardValues? get() = board.values
    }

    /** The last published snapshot (main thread); null until the game first loaded. */
    @Volatile var shown: Shown? = null
        private set
    /** Bumped whenever anything shown changes (the table and the async panels recompose on it). */
    val version: Long get() = shown?.version ?: 0L
    val needsReload: Boolean get() = shown?.needsReload == true
    /** The server's snapshot couldn't be loaded (main thread): the game can't be opened on this device. */
    @Volatile var loadFailed = false
        private set

    // ---- Engine thread state
    private var published = 0L
    private var live = false
    private var now = 0L
    private var previous: TableBoard? = null
    private var lastTransition: Transition? = null

    /** The last [Transition] published (the screen applies each once, by its serial). */
    val transition: Transition? get() = shown?.transition

    /** (Re)loads the game from the server's [snapshot] (a new head, or the device asked to load again). */
    fun load(snapshot: EngineSnapshot, base: PlayState = this.base) {
        this.base = base
        worker.run {
            val (loaded, json) = try { GameDevice.load(snapshot, PlayerId(me), ids) } catch (failure: Exception) {
                // The table says the game can't be opened instead of waiting for a board that never comes
                AppLog.error("engine", "Engine game failed to load", failure)
                worker.main { loadFailed = true; onShown() }
                return@run
            }
            // A game loaded again (the device couldn't follow the server) replaces the board it showed
            if (this::device.isInitialized) {
                val old = device.session
                takenBack("loaded again from the server's head ${snapshot.headId}", old.tip, old.localHead, loaded.session)
            }
            device = loaded; codec = json
            device.clock = clock
            device.presence(live, now)
            this.loaded = true
            previous = null
            publish()
        }
    }

    /** The board the table shows now: the projected state at the local head and the device's own pending events. */
    fun play(): TableBoard = requireNotNull(shown) { "Not loaded yet" }.board

    /** Publishes what changed (engine thread): the board projected anew, the notes drained, then the main thread told.
     * [timing]: the own action whose board this is (its probe times the building and hears when it is shown). */
    private fun publish(drained: List<GameDevice.Note> = emptyList(), timing: OwnActionTiming? = null) {
        timing?.boardStart = clock()
        val notes = drained + device.notes()
        val status = device.status
        val attention = when (status) {
            GameDevice.Status.YourMove, GameDevice.Status.Drafting, is GameDevice.Status.Stopped -> me
            is GameDevice.Status.Waiting -> device.opponent.value
            else -> null
        }
        val session = device.session
        val tip = session.tip
        // TRN-058 (I-D57): the move pill and the bar colours show whom the game really waits for, as the game list does
        val play = board(tip, attention, device.move?.value)
        // R3: the player's hand plays go out with the board (a board published again unchanged keeps them)
        play.values?.workedHandPlays()
        val local = session.indexOf(session.localHead) ?: -1
        val held = session.events.take(local + 1)
        val line = held + session.pending
        val before = previous
        if (before == null || before.nodeId != tip || before.board.turn != play.board.turn)
            transition(before, play, tip, line, attention, device.move?.value)
        previewed = null
        previous = play
        val shown = Shown(++published, play, status, device.locked, device.settings, device.opponent, session.behind, tip,
            session.localHead, session.drafting, device.request, device.indicator(), device.undoTarget(), line, held, lastTransition,
            device.needsReload, device.pausing, held.mapNotNullTo(HashSet()) { e -> e.id.takeIf(device::endedTurn) })
        lastShown = shown
        timing?.let { it.boardEnd = clock(); it.version = shown.version }
        worker.main {
            this.notes.addAll(notes)
            this.shown = shown
            loadFailed = false
            measure(shown, timing)
            onShown()
        }
    }

    /** The last [Shown] published (engine thread). */
    private var lastShown: Shown? = null

    /** What the board built last ([board]) was made from (engine thread). */
    private class Built(val model: GameModel, val changes: Long, val table: org.asyncttwiz.protocol.game.TableState,
        val staging: EngineProjection.Staging?, val tokenArt: org.asyncttwiz.automatum.TokenArt, val base: PlayState, val play: TableBoard)
    private var built: Built? = null

    /** Tests and measurements (engine-responsiveness R3): the boards projected from the device's model to publish. */
    var boardsBuilt = 0
        private set

    /**
     * Engine-responsiveness R3: the board to publish on [nodeId] with [attention] and [move], built once: while the model
     * (its engine state, [GameModel.changes], and table state), the staging, the token pictures and the base are those
     * of the board built last (an own action's early board published again with its whole outcome when nothing followed
     * it, or a board published again for its status alone), that board is published again with only these fields new;
     * otherwise it is projected anew ([EngineProjection.project], with a snapshot of the state, [table]) with its computed
     * values ([EngineBoardValues], from the model's one view). Engine thread.
     */
    private fun board(nodeId: String, attention: String?, move: String?): TableBoard {
        val model = device.model
        val last = built
        if (last != null && last.model === model && last.changes == model.changes && last.table === model.table &&
            last.staging === staging && last.tokenArt === tokenArt && last.base === base) {
            val old = last.play
            if (old.nodeId == nodeId && old.board.attentionHolder == attention && old.board.move == move) return old
            val board = if (old.board.attentionHolder == attention && old.board.move == move) old.board
                else old.board.copy(attentionHolder = attention, move = move)
            return old.with(board, nodeId)
                .also { built = Built(model, last.changes, last.table, last.staging, last.tokenArt, last.base, it) }
        }
        boardsBuilt++
        val play = table(EngineProjection.project(base, model, me, nodeId, attention, staging, tokenArt, pictures, move), model, nodeId)
        built = Built(model, model.changes, model.table, staging, tokenArt, base, play)
        return play
    }

    /**
     * The render model of [projected], just projected from [model] on [nodeId] for [viewer] (engine-responsiveness R4a):
     * with a snapshot of the model's engine and table state beside it and its computed values (and later plays, R3) from
     * the model's view and visible set. Engine thread.
     */
    private fun table(projected: EngineProjection.Board, model: GameModel, nodeId: String, viewer: String = me): TableBoard {
        val visible = runCatching { model.visible(PlayerId(viewer)) }.getOrDefault(emptySet())
        val engine = org.asyncttwiz.protocol.game.EngineView(model.engine, model.table, PlayerId(viewer), visible)
        val values = EngineBoardValues.make(projected, gameId, engine.state, viewer, runCatching { model.view() }.getOrNull(), visible, worker)
        return TableBoard(gameId, base.id, nodeId, projected.board, projected.definitions, engine, values)
    }

    /** [this] board on [nodeId] with [board] (the same engine state and values). */
    private fun TableBoard.with(board: BoardState = this.board, nodeId: String = this.nodeId): TableBoard =
        copy(board = board, nodeId = nodeId)

    /**
     * SYS-025: the board with the player's own action made, while its event is still being finished (what follows by
     * itself folded in): the last published panels with the board projected anew. Engine thread, inside the action; the
     * board after it ([publish]) animates from the board before the action as usual.
     */
    private fun preview(timing: OwnActionTiming): Boolean {
        val last = lastShown ?: return false
        timing.boardStart = clock()
        val attention = when (last.status) {
            GameDevice.Status.YourMove, GameDevice.Status.Drafting, is GameDevice.Status.Stopped -> me
            is GameDevice.Status.Waiting -> device.opponent.value
            else -> null
        }
        // On the board shown before (its node): the table animates it from there, and the panels stay as they were until
        // the action's whole outcome is published (owner playtest 2026-11-04: nothing else flickers)
        val play = board(last.board.nodeId, attention, last.board.board.move)
        previewed = play
        // R3: its computed values come with it; its hand plays with the board published after it
        val shown = Shown(++published, play, last.status, last.locked, last.settings, last.opponent, last.behind, last.tip,
            last.localHead, last.drafting, last.request, last.indicator, last.undoTarget, last.line, last.held, last.transition,
            last.needsReload, last.pausing, last.endTurns)
        timing.boardEnd = clock(); timing.version = shown.version
        worker.main { this.shown = shown; measure(shown, timing); onShown() }
        return true
    }

    /** Runs [work] with the device on the engine thread and waits for it (a dialog's or a route's read of the device). */
    fun <T> read(work: (GameDevice) -> T): T = worker.call { work(device) }

    /**
     * What the table animates between the board shown before and [play] (HIS-030, HIS-031): the events that led from
     * one to the other along the line (forward: shown, made or played back; backward: taken back), their change flashes
     * and shuffles, and the event chip's text (HIS-075: the other player's newest event). A load or a reload is a jump:
     * nothing flashes or moves.
     */
    class Transition(val serial: Long, val cursor: String, val jump: Boolean, val changes: List<org.asyncttwiz.android.TableChange>,
        val shuffled: Set<String>, val forward: List<GameEvent>, val backward: List<GameEvent>,
        /** TBL-040 (owner playtest 2026-11-04): the boards in between, in event order, each after a step of the forward
         * events that moved, tapped, made or turned something (the board shown before and the last one left out): the
         * table plays them one after another at the card move speed, so every card goes to its place in its turn. */
        val steps: List<TableBoard> = emptyList(),
        /** §29.4: an undo (a rewind) took back events shown here: who undid, and the first action taken back with the
         * state it was shown on (for its words, "Undid: cast Storm Spark"); [steps] then fly the cards back. */
        val undone: Undone? = null)

    /** §29.4: what an undo took back of what this player had seen: [by] undid, [event] first (null when unknown). */
    class Undone(val by: String, val event: GameEvent?, val state: arcanum.engine.state.GameState?)

    /** A rewind arriving (engine thread): who undid, to where, and the line shown before it. */
    private class Rewinding(val by: String, val target: String, val line: List<GameEvent>, val state: arcanum.engine.state.GameState?)
    private var rewinding: Rewinding? = null

    private var transitions = 0L
    private var pt: Map<String, Pair<Int, Int>> = emptyMap()
    private var loaded = true
    /** The board [preview] published last (engine thread): the next transition's steps start with it. */
    private var previewed: TableBoard? = null

    private fun transition(before: TableBoard?, after: TableBoard, tip: String, line: List<GameEvent>, attention: String?, move: String?) {
        val ptAfter = EngineChanges.powerToughness(device.model)
        val from = before?.nodeId?.let { id -> line.indexOfFirst { it.id == id }.takeIf { it >= 0 } ?: if (id == device.session.lineStart) -1 else null }
        val to = line.indexOfFirst { it.id == tip }.takeIf { it >= 0 } ?: if (tip == device.session.lineStart) -1 else null
        if (!loaded && before != null && from == null) rewinding?.let { r -> if (undo(r, before, after, attention, move)) return }
        val jump = loaded || before == null || from == null || to == null
        val forward = if (!jump && to!! > from!!) line.subList(from + 1, to + 1).toList() else emptyList()
        val backward = if (!jump && to!! < from!!) line.subList(to + 1, from + 1).reversed() else emptyList()
        val changes = if (jump || before == null) emptyList() else EngineChanges.between(before, after, pt, ptAfter, forward + backward)
        // On the transition's node, so the table animates it as one of its steps
        val shownFirst = previewed?.with(nodeId = tip)
        val steps = if (jump || forward.isEmpty()) emptyList() else runCatching {
            EngineMotion.trim(motionSteps(forward, tip, attention, move), before, after, shownFirst)
        }.onFailure { AppLog.info("engine", "No motion steps: ${it.message}") }.getOrDefault(emptyList())
        lastTransition = Transition(++transitions, tip, jump, changes, if (jump) emptySet() else EngineChanges.shuffled(after, forward),
            forward, backward, steps)
        pt = ptAfter
        loaded = false
    }

    /**
     * §29.4 (owner 2026-11-04): an undo that took back events shown here animates backwards, quickly: the boards after
     * each step of the events taken back (as [motionSteps] makes them, from the point gone back to), in reverse order, so
     * the cards fly back the way they came, at the card move speed and with no playback pause; the transition names who
     * undid and the first action taken back. False when the board shown before isn't on the line shown before (nothing
     * of it was taken back) or the point isn't on it.
     */
    private fun undo(r: Rewinding, before: TableBoard, after: TableBoard, attention: String?, move: String?): Boolean {
        val old = r.line
        val from = old.indexOfFirst { it.id == before.nodeId }.takeIf { it >= 0 } ?: return false
        val to = old.indexOfFirst { it.id == r.target }.takeIf { it >= 0 } ?: if (old.firstOrNull()?.parentId == r.target) -1 else return false
        if (to >= from) return false
        val undone = old.subList(to + 1, from + 1).toList()
        val ptNow = EngineChanges.powerToughness(device.model)
        val changes = EngineChanges.between(before, after, pt, ptNow, undone)
        val steps = runCatching {
            val start = (if (device.session.tip == r.target) device.model.copy() else device.session.stateAt(r.target)) ?: return@runCatching emptyList()
            if (undone.size > EngineMotion.MAX_EVENTS) return@runCatching emptyList()
            val tables = start.copy().let { t -> undone.map { e -> t.apply(e); t.table } }
            val boards = boardsAlong(start, undone, tables, after.nodeId, attention, move)
            EngineMotion.trim(boards.asReversed(), before, after, null)
        }.onFailure { AppLog.info("engine", "No undo motion: ${it.message}") }.getOrDefault(emptyList())
        val first = undone.firstOrNull { !EngineEventText.hidden(it) || EngineEventText.pass(it) }
        lastTransition = Transition(++transitions, after.nodeId, false, changes, emptySet(), emptyList(), undone.asReversed(), steps,
            Undone(r.by, first, r.state))
        pt = ptNow
        loaded = false
        return true
    }

    /**
     * TBL-040: the boards after each engine step of [forward] (the events that end at the tip) that changed what the
     * table draws ([EngineMotion.objects]), projected on [tip] with the final [attention] and [move] (the bars keep their
     * colour), the last [EngineMotion.MAX_STEPS] at most. Worked out from the tip backwards (each event reverts exactly),
     * then forwards step by step on a copy: nothing is replayed from the line's start (owner rule).
     */
    private fun motionSteps(all: List<GameEvent>, tip: String, attention: String?, move: String?): List<TableBoard> {
        // A long run of events (a revision with many) shows its last ones step by step, what came before at once
        val forward = all.takeLast(EngineMotion.MAX_EVENTS)
        if (forward.sumOf { it.engine?.steps?.size ?: 0 } < 2) return emptyList()
        val walk = device.model.copy()
        val tables = ArrayList<org.asyncttwiz.protocol.game.TableState>(forward.size)
        for (e in forward.asReversed()) { tables.add(0, walk.table); walk.revert(e) }
        if (walk.stale) return emptyList()
        return boardsAlong(walk, forward, tables, tip, attention, move)
    }

    /** The boards after each engine step of [forward] from [start] (a copy, used up; [tables]: the table after each
     * event) that changed what the table draws, projected on [tip] with [attention] and [move], the last
     * [EngineMotion.MAX_STEPS] at most. */
    private fun boardsAlong(start: GameModel, forward: List<GameEvent>, tables: List<org.asyncttwiz.protocol.game.TableState>,
        tip: String, attention: String?, move: String?): List<TableBoard> {
        val state = start.engine
        var seen = EngineMotion.objects(state)
        val boards = ArrayList<Pair<EngineProjection.Board, GameModel>>()
        forward.forEachIndexed { i, e ->
            for (step in e.engine?.steps.orEmpty()) {
                step.changes.forEach(state::apply)
                val now = EngineMotion.objects(state)
                if (now == seen) continue
                seen = now
                val model = GameModel(state.deepCopy(), tables[i])
                boards += EngineProjection.project(base, model, me, tip, attention, staging, tokenArt, pictures, move) to model
                if (boards.size > EngineMotion.MAX_STEPS) boards.removeAt(0)
            }
        }
        // R3: each step kept comes with its computed values (from the view its projection worked out)
        return boards.map { (play, model) -> table(play, model, tip) }
    }

    /** The timeline's events and texts (HIS-001–HIS-005 on engine events; main thread). */
    val timeline = EngineTimeline()

    /** The event [id] among those shown to the player (the timeline's), or null. */
    fun event(id: String): GameEvent? = shown?.line?.firstOrNull { it.id == id }

    /**
     * Timeline mode (HIS-004, HIS-012): the board at shown event [id] (on the main line up to the local head, or one of the
     * player's own pending events), view-only; null when the device can't reach it exactly (it was trimmed, or a draft
     * is open).
     */
    fun reviewPlay(id: String): TableBoard? = worker.call {
        val session = device.session
        if (session.drafting) return@call null
        val model = when {
            id == session.tip -> device.model.copy()
            session.pending.any { it.id == id } -> device.model.copy().also { m ->
                for (e in session.pending.asReversed()) { if (e.id == id) break; m.revert(e) }
            }
            else -> runCatching { session.stateAt(id) }.getOrNull()
        } ?: return@call null
        if (model.stale) return@call null
        table(EngineProjection.project(base, model, me, id, null, null, tokenArt, pictures), model, id)
    }

    /** The board of [model] (the point an undo goes back to) as [viewer] sees it, for a view-only preview. */
    fun preview(model: GameModel, at: String, viewer: String): TableBoard? = worker.call {
        runCatching { table(EngineProjection.project(base, model, viewer, at, null, null, tokenArt), model, at, viewer) }.getOrNull()
    }

    /** The token pictures for the engine's tokens (SYS-072), from the device's card records. */
    var tokenArt: org.asyncttwiz.automatum.TokenArt = org.asyncttwiz.automatum.TokenArt.None
        set(value) { if (field !== value) { field = value; worker.run { publish() } } }
    private val pictures = HashMap<arcanum.engine.state.ObjectId, org.asyncttwiz.protocol.SubmittedDefinition>()

    /** The player's own staging of a declaration made behind the head (at a decision stop in playback: their lanes and
     * attack choices, CMB-028, CMB-034), shown while the point it was made at lasts and sent only as the declaration itself
     * (I-D56 §12); at the head staging is shared as table changes instead (I-D54 §8, [EngineDrafts]). Engine thread. */
    private var staging: EngineProjection.Staging? = null

    /** Stages [board]'s combat lanes and attack choices (a board the combat controls made from the shown one). */
    fun stage(board: BoardState) = worker.call {
        val records = board.engineRecords.filter { it.kind == org.asyncttwiz.protocol.EngineRecordKind.PreAnswer }
        staging = EngineProjection.Staging(EngineProjection.Staging.keyOf(device.model.engine), board.combat, records)
        publish()
    }

    /** Drops the device's own staging: the shared drafts of the table show (I-D54 §8). */
    fun unstage() = worker.call { if (staging != null) { staging = null; publish() } }

    /** The player's opening: their mulligan (I-D54 §8: simultaneous mulligans), or null when they can't take one. */
    fun mulligan(): GameDevice.Acted? = acting(STALE, after = false, same = GameDevice::sameDecision) { setupInput(arcanum.engine.decision.Answer.Option("Mulligan"))?.let(::act) }

    /** The player keeps, putting [bottom] (card ids) on the bottom of their library when cards are owed. */
    fun keep(bottom: List<String>): GameDevice.Acted? = acting(STALE, after = false, same = GameDevice::sameDecision) {
        val keep = setupInput(arcanum.engine.decision.Answer.Option("Keep")) ?: return@acting null
        if (bottom.isEmpty()) return@acting act(keep)
        val objects = bottom.mapNotNull { id -> device.model.engine.player(PlayerId(me)).hand.firstOrNull { (it as? arcanum.engine.state.ObjectId.Card)?.card?.value == id } }
        send(listOf(keep)).let { first ->
            if (first !is GameDevice.Acted.Sent) first
            else setupInput(arcanum.engine.decision.Answer.Objects(objects))?.let { act(it) } ?: first
        }
    }

    /** The input answering the player's own setup decision with [answer]: the open decision when it is theirs, else their
     * declaration ahead of it ([Input.SetupAnswer]); null when the engine lists neither. Engine thread. */
    private fun setupInput(answer: arcanum.engine.decision.Answer): Input? = device.openingInput(answer)

    /** The step arrow's pass: the player's own priority (at a decision stop: their answer to what was assumed). */
    fun pass(): GameDevice.Acted = early("pass") { performed ->
        val holder = device.model.engine.priority.holder ?: return@early GameDevice.Acted.Refused("noPriority")
        if (arcanum.engine.Engine.decider(device.model.engine, holder).value != me) return@early GameDevice.Acted.Refused("notYours")
        device.act(Input.PassPriority(holder), performed = performed)
    }

    // ---- The server

    /** An accepted revision of this play: its engine events in order and the rest of what it changed. */
    fun revision(message: GameRevision) {
        revision = message.resultRevision
        val update = message.engine
        val texts = listOfNotNull(message.engineEvent) + update?.events.orEmpty()
        if (texts.isEmpty() && update == null) return
        worker.run {
            val events: List<GameEvent> = texts.map(codec::decodeEvent)
            val session = device.session
            val tip = session.tip
            val local = session.localHead
            val head = session.head
            // HIS-095 (§29.2, smoke 2026-11-04): a device that had shown everything (its live game) shows the other
            // player's passes at once instead of playing them back
            val live = !session.behind && !device.catchUp
            // §29.4: an undo animates backwards from the board shown before it
            rewinding = update?.target?.let { target ->
                lastShown?.let { Rewinding(update.rewoundBy ?: events.firstOrNull()?.author ?: "", target, it.line, it.engine) }
            }
            device.revision(events, update)
            // A board already shown taken back by the server's revision (owner, N7FU6Y: one line per take-back)
            if (!onLine(tip)) takenBack(when {
                device.needsReload -> "the server's main line can't be followed (reload)"
                update?.target != null -> "rewind to ${update.target} by ${update.rewoundBy ?: "?"}"
                events.firstOrNull()?.let { it.parentId != head } == true -> "branch by ${events.first().author} at ${events.first().parentId}"
                else -> "own pending events dropped: another event came first"
            } + " (events ${events.joinToString(",") { it.id }})", tip, local)
            if (live) device.showPasses()
            device.advance(now)
            publish()
            rewinding = null
        }
    }

    /** The server refused [action] (outcome other than accepted) for [reason]. */
    fun refused(action: GameAction, reason: String?) = worker.run {
        val tip = device.session.tip
        val local = device.session.localHead
        device.refused(action, reason ?: "refused")
        if (device.session.tip != tip) takenBack("server refused ${action::class.simpleName}" +
            ((action as? GameAction.Submit)?.submission?.let { " ${it.id} on ${it.parentId}" } ?: "") + ": ${reason ?: "refused"}", tip, local)
        device.advance(now)
        publish()
    }

    /** The opponent's presence (the green dot, TRN-042) at [now]; makes the device's own steps (passes, assumptions,
     * a live wait turning async after the grace). */
    fun tick(live: Boolean, now: Long) = worker.run {
        this.live = live; this.now = now
        val before = device.status
        val tip = device.session.tip
        val request = device.request
        device.presence(live, now)
        device.advance(now)
        val drained = device.notes()
        if (device.status != before || device.session.tip != tip || device.request != request || drained.isNotEmpty()) publish(drained)
    }

    /** The device's reproduction bundle (I-D43) for a problem report; null while it can't be made. */
    fun repro(): GameRepro? = worker.call { runCatching {
        val repro = device.repro(codec)
        val session = device.session
        if (repro.base != null || session.drafting) return@runCatching repro
        // The device can't go back exactly to the start of the line it holds (more than a few turns back): the bundle
        // starts at the earliest event it can reach (reachable events are a suffix of the line: binary search)
        val events = session.events
        var low = 0; var high = events.size - 1; var found: Pair<Int, GameModel>? = null
        while (low <= high) {
            val mid = (low + high) / 2
            val model = session.stateAt(events[mid].id)
            if (model != null) { found = mid to model; high = mid - 1 } else low = mid + 1
        }
        val (at, model) = found ?: return@runCatching repro
        repro.copy(lineStart = events[at].id, base = codec.encodeModel(model), events = events.drop(at + 1).map(codec::encodeEvent))
    }.getOrNull() }

    /** What to send now (drained), waiting for the engine thread. */
    fun take(now: Long): List<GameAction> = worker.call { device.take(now) }

    /** What to send now (drained), given to [deliver] on the main thread without waiting for the engine thread. */
    fun take(now: Long, deliver: (List<GameAction>) -> Unit) = worker.run {
        val actions = device.take(now)
        if (actions.isNotEmpty()) worker.main { deliver(actions) }
    }

    // ---- The player's actions (each waits for its own local result; the device's steps after it don't hold the caller)

    /** The player's own [inputs] of one flow (a cast with its answers, an answer with its orders, a declaration), as
     * one acting point; the device answers the randomness and makes the passes that follow by itself. */
    fun send(inputs: List<Input>, table: List<TableChange> = emptyList()): GameDevice.Acted {
        if (inputs.isEmpty()) return GameDevice.Acted.Refused("nothing")
        return early("send") { performed ->
            val first = inputs.first()
            val rest = inputs.drop(1).iterator()
            // I-D54 §8: a declaration ends the shared staging of it in the same event (at the head; at a decision stop the
            // answer must be the input alone to confirm what was assumed)
            val t = device.model.table
            val clear = if (device.session.behind) emptyList() else listOfNotNull(
                t.attackDraft?.takeIf { inputs.any { it is Input.DeclareAttackers } }?.let { TableChange.AttackDraft(it, null) },
                t.blockDraft?.takeIf { inputs.any { it is Input.DeclareBlockers } }?.let { TableChange.BlockDraft(it, null) })
            device.act(first, table + clear, performed) { if (rest.hasNext()) rest.next() else null }
        }
    }

    /** One input of the player's own (a pass, a keep, a concession). */
    fun act(input: Input): GameDevice.Acted = early("act") { performed -> device.act(input, performed = performed) }

    /** The step arrow's End turn (TRN-057): the device passes for the player until the turn ends. SYS-011 (smoke
     * 2026-11-04): tapped on a board the device has moved on from within the player's own turn (its own steps after
     * their previous action), it ends that turn all the same ([GameDevice.sameEndTurn]). */
    fun endTurn() { acting(Unit, "endTurn", same = { seen, moved -> GameDevice.sameEndTurn(seen, moved, PlayerId(me)) }) { device.endTurn(now) } }

    /** The waiting bar's Assume button (§4): the opponent's pass or keep, or [blocks] (the Assume blockers screen's). */
    fun assume(blocks: List<Block> = emptyList()): GameDevice.Acted = acting(STALE, "assume") { device.assume(blocks) }

    /** Back on the Assume blockers screen: it stays a wait. */
    fun closeBlockersScreen() = worker.run { device.closeBlockersScreen(); publish() }

    fun table(changes: List<TableChange>): GameDevice.Acted = acting(STALE, "table") { device.table(changes) }
    /** Conceding is taken at any time (CR 104.3a), whatever the table showed. */
    fun concede(): GameDevice.Acted = worker.call { device.concede() }.also { after() }
    fun suggest(result: GameResult): GameDevice.Acted = acting(STALE) { device.suggest(result) }
    fun answerSuggestion(accept: Boolean): GameDevice.Acted = acting(STALE) { device.answerSuggestion(accept) }
    fun confirmResult(): GameDevice.Acted = acting(STALE) { device.confirmResult() }
    fun settings(settings: PlaySettings) { worker.call { device.settings(settings) }; after() }

    /** Leaves the catch-up screen (§7): playback starts. */
    fun startPlayback() = worker.run { device.play(); publish() }

    /** The play/pause button (§27): pausing makes playback stop at the next point where Full control stops; playing
     * again ends it (at a stop the pause made, [play] answers the same as assumed and goes on). */
    fun pause(on: Boolean) = worker.call { device.pausing = on; publish() }

    /** Playback: the next buffered event, unless it stops before it. */
    fun step(): GameEvent? = worker.call { device.step() }.also { if (it != null) after() }

    /** Return to current / Back while behind: plays on to the next decision point. */
    fun playToStop(): List<GameEvent> = worker.call { device.playToStop() }.also { after() }

    fun undo(target: String? = null): GameDevice.Undo =
        acting(GameDevice.Undo.Unavailable(STALE_REASON), "undo") { device.undo(target ?: device.undoTarget()) }

    /** Whether taking the line back to just after [target] needs the other player's approval (§10), worked out as the
     * device's undo does without sending anything; null when nothing would be undone. */
    fun undoNeedsRequest(target: String): Boolean? = worker.call {
        val session = device.session
        if (session.drafting) return@call null
        val after = session.after(target) ?: return@call true
        if (after.isEmpty()) return@call null
        val at = session.stateAt(target) ?: return@call true
        val others = device.localHeads.filterKeys { it != PlayerId(me) }.values.toList()
        org.asyncttwiz.protocol.game.Rewind.requestNeeded(at, target, after, PlayerId(me), others) != null
    }
    fun requestUndo(target: String, message: String) { worker.call { device.requestUndo(target, message) }; after() }
    fun answerRequest(accept: Boolean) { worker.call { device.answerRequest(accept) }; after() }
    fun cancelRequest() { worker.call { device.cancelRequest() }; after() }

    /**
     * The player's own action, made on the board shown now (called on the main thread; inline in an action already on
     * the engine thread): [work] on the device, waiting for its local result (SYS-025), then [after] unless [after] is
     * false (the action calls another one that does). Switchreview #6, SYS-011: when the engine thread has published a
     * board newer than the one shown (the device's own steps after an earlier action, or a server event, not on screen
     * yet: a second tap made on the old board), [stale] instead, with SYS-012's note, so nothing acts at a point the
     * player never saw; with [same] (a decision's answer: an opening declaration, [GameDevice.sameDecision]; End turn,
     * [GameDevice.sameEndTurn]) it is made on the newer board when [same] holds for it ([stillSame]). Checked once per
     * action: what it does in turn (another action, staging) isn't checked again. A refusal publishes its note with the
     * board (never only queued: the table reads notes when a board arrives).
     * [what]: the action's name for the probe (SYS-025, SYS-030, [OwnActionTiming]), which splits its time into its parts;
     * a slow one is logged and, with reports on, recorded like every slow action.
     */
    private fun <T> acting(stale: T, what: String? = null, after: Boolean = true,
        same: ((arcanum.engine.state.GameState, arcanum.engine.state.GameState) -> Boolean)? = null, work: () -> T): T {
        val board = shown
        val seen = board?.version
        var refused = false
        val timing = what?.let(::timing)
        val result = worker.call {
            timing?.started = clock()
            val info = informationNanos()
            try {
                if (inAction) work()
                else if (seen != null && seen != published && !(same != null && stillSame(what ?: "action", board, same))) {
                    refused = true; staleBoard(what ?: "action", seen); refusedStale(); stale
                }
                else { inAction = true; try { work() } finally { inAction = false } }
            } finally { timing?.let { it.worked = clock(); it.info = (informationNanos() - info).coerceAtLeast(0) } }
        }
        if (!refused && after) after(timing)
        return result
    }

    /**
     * The player's own action [what] through [GameDevice.act] (SYS-025): like [acting], but the caller waits only until
     * the action is made: its board is published at [GameDevice.act]'s performed point, before what follows by itself
     * is folded into its event (on a phone that can take longer than a frame), and the caller gets [GameDevice.Acted.Made].
     * A refusal after that (what follows by itself would show a hidden card on a tentative line, TRN-067, or an engine
     * failure) is told as a note and the board goes back. Without the performed point (refused before it) it is [acting].
     */
    private fun early(what: String, work: (performed: () -> Unit) -> GameDevice.Acted): GameDevice.Acted {
        val board = shown
        val seen = board?.version
        val timing = timing(what)
        val result = java.util.concurrent.atomic.AtomicReference<GameDevice.Acted?>(null)
        worker.early { release ->
            timing.started = clock()
            val info = informationNanos()
            if (!inAction && seen != null && seen != published && !stillSame(what, board)) {
                staleBoard(what, seen)
                result.set(STALE)
                refusedStale()
                return@early
            }
            val outer = !inAction && release != null
            val tip = device.session.tip
            val local = device.session.localHead
            val wasIn = inAction
            inAction = true
            var early = false
            var shownInfo = 0L
            val acted = try {
                work {
                    if (outer) {
                        timing.worked = clock(); timing.info = (informationNanos() - info).coerceAtLeast(0)
                        early = preview(timing)
                        timing.early = early
                        shownInfo = informationNanos()
                        result.set(GameDevice.Acted.Made)
                        release!!()
                    }
                }
            } finally { if (!wasIn) inAction = false }
            if (!early) { timing.worked = clock(); timing.info = (informationNanos() - info).coerceAtLeast(0); result.set(acted) }
            else {
                // The rest of its event, worked out after its board was shown
                timing.after(timing.boardEnd!!, clock(), informationNanos() - shownInfo)
                if (acted is GameDevice.Acted.Refused) {
                    takenBack("own $what refused on the device after it showed: ${acted.reason}", tip, local)
                    worker.main { notes.add(GameDevice.Note.Refused(acted.reason)) }
                }
            }
            after(timing, shownEarly = early)
        }
        return result.get() ?: GameDevice.Acted.Refused("failed")
    }

    /** Engine thread: whether [id] is still on the line the device shows (the main line it holds, or its own pending). */
    private fun onLine(id: String): Boolean {
        val s = device.session
        return id == s.lineStart || s.indexOf(id) != null || s.pending.any { it.id == id }
    }

    /**
     * Owner, problem report N7FU6Y: one info line in the app log (sent with problem reports) whenever a board already
     * shown is taken back or replaced: [reason] (with the ids involved), the shown tip and local head before ([tip],
     * [local]) and after ([after]). Engine thread.
     */
    private fun takenBack(reason: String, tip: String, local: String, after: org.asyncttwiz.protocol.game.GameSession = device.session) =
        AppLog.info("engine", "Shown board taken back: $reason; tip $tip → ${after.tip}, local head $local → ${after.localHead}")

    /** An own action [what] tapped on a board older than the one published ([seen]: its version): not taken (SYS-011),
     * the newer board shows. Engine thread. */
    private fun staleBoard(what: String, seen: Long) = AppLog.info("engine",
        "Own $what not taken: made on board v$seen, the device had published v$published (tip ${device.session.tip})")

    /** SYS-012 (smoke 2026-11-04): a stale tap's note goes out with a board of its own, so the table, which reads notes
     * when a board arrives, tells it at once (never left queued until something else changes). Engine thread. */
    private fun refusedStale() = publish(listOf(GameDevice.Note.Refused(STALE_REASON)))

    /**
     * Owner, 2026-11-04 (SYS-011): an own action [what] tapped on [board], older than the one published, is made on the
     * device's board now when the same decision still waits for the player there ([GameDevice.sameDecision]: e.g. the
     * other player's pass arrived for the one assumed, or an event that changed nothing in the game); the action then
     * acts on the newer board, where the engine checks it again (SYS-057). Engine thread.
     */
    private fun stillSame(what: String, board: Shown?,
        same: (arcanum.engine.state.GameState, arcanum.engine.state.GameState) -> Boolean = GameDevice::sameDecision): Boolean {
        val seen = board?.board?.engine?.state ?: return false
        if (device.session.drafting || !same(seen, device.model.engine)) return false
        AppLog.info("engine", "Own $what made on board v$published: the same decision as on board v${board.version} it was tapped on")
        return true
    }

    /** Engine thread: an action is being worked out ([acting]). */
    private var inAction = false

    /** [work] with the device for the table's own actions worked out from the board shown now ([acting]: [stale] when
     * the device has moved on from it), e.g. a card's place and the combat staging. */
    fun <T> onShown(stale: T, work: (GameDevice) -> T): T = acting(stale, after = false) { work(device) }

    /** After the player's own action, on the engine thread without holding the caller: its own board first (SYS-025: the
     * action shows at once, not after the steps that follow it), then the device's own steps (passes, assumptions) and,
     * if they changed anything, the board again. [timing]: the action's probe (SYS-030), told once its board is shown. */
    private fun after(timing: OwnActionTiming? = null, shownEarly: Boolean = false) = worker.run {
        // Owner playtest 2026-11-04: an action already shown at its performed point ([preview]) publishes once, with what
        // follows it by itself: its outcome on the way (the waiting bar, the bars' colour, the move pill) never flashes
        if (shownEarly) {
            val start = clock()
            val info = informationNanos()
            device.advance(now)
            publish(device.notes())
            if (timing != null) { timing.after(start, clock(), informationNanos() - info); afterDone(timing) }
            return@run
        }
        publish(timing = timing)
        val info = informationNanos()
        try { afterBoard() } finally {
            timing?.let { it.after(it.boardEnd!!, clock(), informationNanos() - info); afterDone(it) }
        }
    }

    /** [after] the board of an own action not shown early: the device's own steps, and the board again when they changed
     * anything. Engine thread. */
    private fun afterBoard() {
        val tip = device.session.tip
        val status = device.status
        val request = device.request
        val pending = device.session.pending.size
        device.advance(now)
        val drained = device.notes()
        if (device.session.tip != tip || device.status != status || device.request != request || device.session.pending.size != pending ||
            drained.isNotEmpty()) publish(drained)
    }

    // ---- The own-action probe (SYS-025, SYS-030, engine-responsiveness R1)

    /** A new own action [what]'s probe, asked for now. */
    private fun timing(what: String) = OwnActionTiming(what, clock,
        DebugLatency.begin(gameId, "engine:$what", 0)?.also { it.submitted(null, predicted = true) })

    /** Engine thread: [timing]'s work after its board is done; the main thread checks whether every part is in. */
    private fun afterDone(timing: OwnActionTiming) {
        timing.afterDone()
        worker.main { arrived(timing) }
    }

    /** The own action measured now, waiting for the parts after its board (main thread). */
    private var measuring: OwnActionTiming? = null

    /**
     * Main thread, [shown] just set: the boards from an action's own one on count for its computed values; [timing]'s own
     * board (the first) is shown now: its time to its board is known (the probe, the recorder), the next frame is
     * awaited, and an older action still waiting is reported with the parts it has.
     */
    private fun measure(shown: Shown, timing: OwnActionTiming?) {
        if (timing != null && timing !== measuring && !timing.reported) {
            measuring?.let(::report)
            measuring = timing
            OwnActionProbes.add(timing, ::arrived)
            timing.trace?.finish()
            val parts = timing.parts()
            probe?.invoke(timing.what, nanos(parts[OwnActionTiming.WAIT]),
                nanos(parts[OwnActionTiming.ENGINE]) + nanos(parts[OwnActionTiming.INFO]), nanos(parts[OwnActionTiming.BOARD]))
            worker.frame { timing.frame = clock(); arrived(timing) }
        }
        measuring?.boards?.add(shown.board.board)
    }

    /** Main thread: a part of [timing]'s arrived; reported once every part is in. */
    private fun arrived(timing: OwnActionTiming) { if (timing.complete) report(timing) }

    /**
     * Main thread: [timing]'s parts, once (every part in, or a newer action's board came first): in the app log (and so
     * in problem reports) for every own action in test and debug builds, and in others when slow (over SYS-025's 20 ms to
     * its board); and to the recorder's sample (SYS-030: recorded and uploaded when slow, with reports on).
     */
    private fun report(timing: OwnActionTiming) {
        if (timing.reported) return
        timing.reported = true
        OwnActionProbes.remove(timing)
        if (measuring === timing) measuring = null
        val parts = timing.parts()
        val line = timing.line(parts)
        // Test and debug builds log every own action in the app log, so a problem report carries a whole sample, not
        // only the slow ones
        val slow = (parts[OwnActionTiming.TOTAL] ?: 0.0) > SLOW_MS
        if (slow || Diagnostics.alwaysOn) AppLog.info("engine", "${if (slow) "Slow own action" else "Own action"} $line")
        timing.trace?.parts(parts)
        measured?.invoke(timing)
    }

    private fun nanos(ms: Double?): Long = ((ms ?: 0.0) * 1e6).toLong()

    /** Engine thread: the device's information-rule time so far ([GameDevice.informationNanos]; 0 before the first load). */
    private fun informationNanos(): Long = if (this::device.isInitialized) device.informationNanos else 0L

    /** Tests and measurements: each own action's wait for the engine thread, its work and its board, in nanoseconds,
     * told when its board is shown. */
    var probe: ((what: String, waited: Long, acted: Long, board: Long) -> Unit)? = null

    /** Tests and measurements: each own action's probe once reported, with every part that arrived (main thread). */
    var measured: ((OwnActionTiming) -> Unit)? = null

    // Last, once every property above is set
    init { load(snapshot, base) }

    private companion object {
        /** SYS-025's budget for an action's local result. */
        const val SLOW_MS = 20.0
        /** An action made on a board the device has moved on from ([refusedText]: SYS-012's note). */
        const val STALE_REASON = "stale"
        val STALE = GameDevice.Acted.Refused(STALE_REASON)
    }
}
