package org.asyncttwiz.android

import arcanum.engine.input.Block
import arcanum.engine.input.Input
import arcanum.engine.setup.GameResult
import arcanum.engine.state.GameState
import arcanum.engine.state.PlayerId
import org.asyncttwiz.automatum.EngineProjection
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
 * holds the main line, playback, assumptions, undo and requests) and the board the table shows of it
 * ([EngineProjection]: the projected [PlayState] carrying the engine's state). Everything it sends is in [take]; the
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
    private val worker: EngineWorker = EngineWorker.Direct, private val onShown: () -> Unit = {}) {
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
     * What the table shows now, published by the engine thread after each change: the projected board ([play]) and what
     * the async panels read of the device. Immutable; [engine] is the board's own copy of the engine state.
     */
    class Shown(val version: Long, val play: PlayState, val status: GameDevice.Status, val locked: Boolean,
        val settings: PlaySettings, val opponent: PlayerId, val behind: Boolean, val tip: String, val localHead: String,
        val drafting: Boolean, val request: EngineRewindRequest?, val indicator: GameDevice.Indicator?, val undoTarget: String?,
        /** The events shown to the player: the main line up to the local head ([held]), then their own pending events. */
        val line: List<GameEvent>, val held: List<GameEvent>, val transition: Transition?, val needsReload: Boolean,
        /** The player paused playback (§27): it goes on to the next point where Full control stops. */
        val pausing: Boolean = false) {
        val engine: GameState get() = play.board.engine!!.state
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
    private var previous: PlayState? = null
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
            device = loaded; codec = json
            device.presence(live, now)
            this.loaded = true
            previous = null
            publish()
        }
    }

    /** The board the table shows now: the projected state at the local head and the device's own pending events. */
    fun play(): PlayState = requireNotNull(shown) { "Not loaded yet" }.play

    /** Publishes what changed (engine thread): the board projected anew, the notes drained, then the main thread told. */
    private fun publish(drained: List<GameDevice.Note> = emptyList()) {
        val notes = drained + device.notes()
        val status = device.status
        val attention = when (status) {
            GameDevice.Status.YourMove, GameDevice.Status.Drafting, is GameDevice.Status.Stopped -> me
            is GameDevice.Status.Waiting -> device.opponent.value
            else -> null
        }
        val session = device.session
        val tip = session.tip
        val play = EngineProjection.project(base, device.model, me, tip, attention, staging, tokenArt, pictures)
        val local = session.indexOf(session.localHead) ?: -1
        val held = session.events.take(local + 1)
        val line = held + session.pending
        val before = previous
        if (before == null || before.currentNodeId != tip || before.board.turn != play.board.turn) transition(before, play, tip, line)
        previous = play
        val shown = Shown(++published, play, status, device.locked, device.settings, device.opponent, session.behind, tip,
            session.localHead, session.drafting, device.request, device.indicator(), device.undoTarget(), line, held, lastTransition,
            device.needsReload, device.pausing)
        lastShown = shown
        worker.main {
            this.notes.addAll(notes)
            this.shown = shown
            loadFailed = false
            onShown()
        }
    }

    /** The last [Shown] published (engine thread). */
    private var lastShown: Shown? = null

    /**
     * SYS-025: the board with the player's own action made, while its event is still being finished (what follows by
     * itself folded in): the last published panels with the board projected anew. Engine thread, inside the action; the
     * board after it ([publish]) animates from the board before the action as usual.
     */
    private fun preview() {
        val last = lastShown ?: return
        val attention = when (last.status) {
            GameDevice.Status.YourMove, GameDevice.Status.Drafting, is GameDevice.Status.Stopped -> me
            is GameDevice.Status.Waiting -> device.opponent.value
            else -> null
        }
        val play = EngineProjection.project(base, device.model, me, device.session.tip, attention, staging, tokenArt, pictures)
        val shown = Shown(++published, play, last.status, last.locked, last.settings, last.opponent, last.behind, last.tip,
            last.localHead, last.drafting, last.request, last.indicator, last.undoTarget, last.line, last.held, last.transition,
            last.needsReload, last.pausing)
        worker.main { this.shown = shown; onShown() }
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
        val shuffled: Set<String>, val forward: List<GameEvent>, val backward: List<GameEvent>)

    private var transitions = 0L
    private var pt: Map<String, Pair<Int, Int>> = emptyMap()
    private var loaded = true

    private fun transition(before: PlayState?, after: PlayState, tip: String, line: List<GameEvent>) {
        val ptAfter = EngineChanges.powerToughness(device.model.engine)
        val from = before?.currentNodeId?.let { id -> line.indexOfFirst { it.id == id }.takeIf { it >= 0 } ?: if (id == device.session.lineStart) -1 else null }
        val to = line.indexOfFirst { it.id == tip }.takeIf { it >= 0 } ?: if (tip == device.session.lineStart) -1 else null
        val jump = loaded || before == null || from == null || to == null
        val forward = if (!jump && to!! > from!!) line.subList(from + 1, to + 1).toList() else emptyList()
        val backward = if (!jump && to!! < from!!) line.subList(to + 1, from + 1).reversed() else emptyList()
        val changes = if (jump || before == null) emptyList() else EngineChanges.between(before, after, pt, ptAfter, forward + backward)
        lastTransition = Transition(++transitions, tip, jump, changes, if (jump) emptySet() else EngineChanges.shuffled(after, forward),
            forward, backward)
        pt = ptAfter
        loaded = false
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
    fun reviewPlay(id: String): PlayState? = worker.call {
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
        EngineProjection.project(base, model, me, id, null, null, tokenArt, pictures)
    }

    /** The board of [model] (the point an undo goes back to) as [viewer] sees it, for a view-only preview. */
    fun preview(model: GameModel, at: String, viewer: String): PlayState? = worker.call {
        runCatching { EngineProjection.project(base, model, viewer, at, null, null, tokenArt) }.getOrNull()
    }

    /** The token pictures for the engine's tokens (SYS-072), from the device's card records. */
    var tokenArt: org.asyncttwiz.automatum.TokenArt = org.asyncttwiz.automatum.TokenArt.None
        set(value) { if (field !== value) { field = value; worker.run { publish() } } }
    private val pictures = HashMap<arcanum.engine.state.ObjectId, org.asyncttwiz.protocol.SubmittedDefinition>()

    /** The player's own staging of a declaration made behind the head (at a decision stop in playback: their lanes and
     * attack choices, CMB-028, CMB-034), shown while the point it was made at lasts and sent only as the declaration itself
     * (I-D56 §12); at the head staging is shared as table changes instead (I-D54 §8, [EngineDrafts]). Engine thread. */
    private var staging: EngineProjection.Staging? = null

    /** Stages [play]'s combat lanes and attack choices (a board the combat controls made from the shown one). */
    fun stage(play: PlayState) = worker.call {
        val records = play.board.engineRecords.filter { it.kind == org.asyncttwiz.protocol.EngineRecordKind.PreAnswer }
        staging = EngineProjection.Staging(EngineProjection.Staging.keyOf(device.model.engine), play.board.combat, records)
        publish()
    }

    /** Drops the device's own staging: the shared drafts of the table show (I-D54 §8). */
    fun unstage() = worker.call { if (staging != null) { staging = null; publish() } }

    /** The player's opening: their mulligan (I-D54 §8: simultaneous mulligans), or null when they can't take one. */
    fun mulligan(): GameDevice.Acted? = acting(STALE, after = false) { setupInput(arcanum.engine.decision.Answer.Option("Mulligan"))?.let(::act) }

    /** The player keeps, putting [bottom] (card ids) on the bottom of their library when cards are owed. */
    fun keep(bottom: List<String>): GameDevice.Acted? = acting(STALE, after = false) {
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
    private fun setupInput(answer: arcanum.engine.decision.Answer): Input? {
        val s = device.model.engine
        val d = s.pending?.decision
        val player = PlayerId(me)
        if (d != null && arcanum.engine.Engine.decider(s, d.player) == player) return Input.Answer(d.id, answer)
        val listed = arcanum.engine.Engine.options(s, player)
        return Input.SetupAnswer(player, answer).takeIf { listed.any { it is Input.SetupAnswer && it.player == player } }
    }

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
            device.revision(events, update)
            device.advance(now)
            publish()
        }
    }

    /** The server refused [action] (outcome other than accepted) for [reason]. */
    fun refused(action: GameAction, reason: String?) = worker.run {
        device.refused(action, reason ?: "refused")
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

    /** The step arrow's End turn (TRN-057): the device passes for the player until the turn ends. */
    fun endTurn() { acting(Unit, "endTurn") { device.endTurn(now) } }

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
     * player never saw. Checked once per action: what it does in turn (another action, staging) isn't checked again.
     * [what]: the action's name for the probe (SYS-025, SYS-030), which times the wait for the engine thread, the action
     * and its board; a slow one is logged and, with reports on, recorded like every slow action.
     */
    private fun <T> acting(stale: T, what: String? = null, after: Boolean = true, work: () -> T): T {
        val seen = shown?.version
        var refused = false
        val timing = what?.let { Timing(it, System.nanoTime(), DebugLatency.begin(gameId, "engine:$it", 0)?.also { t -> t.submitted(null, predicted = true) }) }
        val result = worker.call {
            timing?.started = System.nanoTime()
            if (inAction) work()
            else if (seen != null && seen != published) { refused = true; stale }
            else { inAction = true; try { work() } finally { inAction = false } }
        }.also { timing?.acted = System.nanoTime() }
        if (refused) worker.main { notes.add(GameDevice.Note.Refused(STALE_REASON)) } else if (after) after(timing)
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
        val seen = shown?.version
        val timing = Timing(what, System.nanoTime(), DebugLatency.begin(gameId, "engine:$what", 0)?.also { it.submitted(null, predicted = true) })
        val result = java.util.concurrent.atomic.AtomicReference<GameDevice.Acted?>(null)
        worker.early { release ->
            timing.started = System.nanoTime()
            if (!inAction && seen != null && seen != published) {
                result.set(STALE)
                worker.main { notes.add(GameDevice.Note.Refused(STALE_REASON)) }
                return@early
            }
            val outer = !inAction && release != null
            val wasIn = inAction
            inAction = true
            var early = false
            val acted = try {
                work {
                    if (outer) {
                        preview()
                        timing.acted = System.nanoTime(); timing.shown(timing.acted)
                        early = true
                        result.set(GameDevice.Acted.Made)
                        release!!()
                    }
                }
            } finally { if (!wasIn) inAction = false }
            if (!early) { timing.acted = System.nanoTime(); result.set(acted) }
            else if (acted is GameDevice.Acted.Refused) worker.main { notes.add(GameDevice.Note.Refused(acted.reason)) }
            after(if (early) null else timing)
        }
        return result.get() ?: GameDevice.Acted.Refused("failed")
    }

    /** Engine thread: an action is being worked out ([acting]). */
    private var inAction = false

    /** [work] with the device for the table's own actions worked out from the board shown now ([acting]: [stale] when
     * the device has moved on from it), e.g. a card's place and the combat staging. */
    fun <T> onShown(stale: T, work: (GameDevice) -> T): T = acting(stale, after = false) { work(device) }

    /** After the player's own action, on the engine thread without holding the caller: its own board first (SYS-025: the
     * action shows at once, not after the steps that follow it), then the device's own steps (passes, assumptions) and,
     * if they changed anything, the board again. [timing]: the action's probe (SYS-030), told once its board is shown. */
    private fun after(timing: Timing? = null) = worker.run {
        publish()
        timing?.shown(System.nanoTime())
        val tip = device.session.tip
        val status = device.status
        val request = device.request
        val pending = device.session.pending.size
        device.advance(now)
        val drained = device.notes()
        if (device.session.tip != tip || device.status != status || device.request != request || device.session.pending.size != pending ||
            drained.isNotEmpty()) publish(drained)
    }

    /** One own action's probe: when it was asked for (main thread), started and done on the engine thread, and shown. */
    private inner class Timing(val what: String, val asked: Long, val trace: LatencyRecorder.Trace?) {
        @Volatile var started = 0L
        @Volatile var acted = 0L
        fun shown(at: Long) = worker.main {
            trace?.finish()
            val total = (at - asked) / 1e6
            if (total > SLOW_MS) AppLog.info("engine", "Slow own action %s: %.0f ms to its board (waited %.0f ms for the engine thread, acted %.0f ms, board %.0f ms)"
                .format(what, total, (started - asked) / 1e6, (acted - started) / 1e6, (at - acted) / 1e6))
            probe?.invoke(what, started - asked, acted - started, at - acted)
        }
    }

    /** Tests and measurements: each own action's wait for the engine thread, its work and its board, in nanoseconds. */
    var probe: ((what: String, waited: Long, acted: Long, board: Long) -> Unit)? = null

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
