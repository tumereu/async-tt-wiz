package org.asyncttwiz.android

import arcanum.engine.input.Block
import arcanum.engine.input.Input
import arcanum.engine.setup.GameResult
import arcanum.engine.state.PlayerId
import org.asyncttwiz.automatum.EngineProjection
import org.asyncttwiz.protocol.EngineSnapshot
import org.asyncttwiz.protocol.GameAction
import org.asyncttwiz.protocol.GameRevision
import org.asyncttwiz.protocol.PlayState
import org.asyncttwiz.protocol.game.EngineJson
import org.asyncttwiz.protocol.game.GameDevice
import org.asyncttwiz.protocol.game.GameEvent
import org.asyncttwiz.protocol.game.GameRepro
import org.asyncttwiz.protocol.game.PlaySettings
import org.asyncttwiz.protocol.game.TableChange
import java.util.UUID

/**
 * The engine path's switch (I-D56 step C, behind the seam until step D): when on, a game whose play is engine state
 * ([org.asyncttwiz.protocol.GameHead.engine]) is played on its [EngineGame]; when off (the default), every game is
 * played on today's model. A debug/test setting (Settings → Automatum in debug builds).
 */
internal object EnginePath {
    @Volatile var enabled: Boolean = false

    /** Reads the switch kept on this device (debug builds only; release builds always play today's model). */
    fun load(context: android.content.Context) {
        enabled = BuildConfig.DEBUG && context.getSharedPreferences(PREFERENCES, android.content.Context.MODE_PRIVATE).getBoolean(KEY, false)
    }

    fun set(context: android.content.Context, on: Boolean) {
        context.getSharedPreferences(PREFERENCES, android.content.Context.MODE_PRIVATE).edit().putBoolean(KEY, on).apply()
        enabled = on && BuildConfig.DEBUG
    }

    private const val PREFERENCES = "engine-path"
    private const val KEY = "enabled"
}

/**
 * One game played on engine state on this device (I-D54 item 2, I-D56 step C): the device core ([GameDevice], which
 * holds the main line, playback, assumptions, undo and requests) and the board the table shows of it
 * ([EngineProjection]: the projected [PlayState] carrying the engine's state). Everything it sends is in [take]; the
 * server's revisions and refusals come in through [revision] and [refused]. Main thread; times are the caller's.
 *
 * [base] is the play as it started (its ids, containers, players and card data), which the server still sends with
 * every head.
 */
internal class EngineGame(val gameId: String, val me: String, base: PlayState, snapshot: EngineSnapshot,
    private val ids: () -> String = { UUID.randomUUID().toString() }) {
    var base: PlayState = base
        private set
    lateinit var device: GameDevice
        private set
    lateinit var codec: EngineJson
        private set
    /** The revision of the game this device has applied. */
    var revision: String? = null
    /** Bumped whenever anything shown changes (the table and the async panels recompose on it). */
    var version: Long = 0
        private set
    private var shown: Pair<Long, PlayState>? = null
    /** The notes waiting to be shown once (§8, §10): the branch note, the take-back note, refusals. */
    val notes = ArrayDeque<GameDevice.Note>()
    private var live = false
    private var now = 0L

    /** (Re)loads the game from the server's [snapshot] (a new head, or the device asked to load again). */
    fun load(snapshot: EngineSnapshot, base: PlayState = this.base) {
        this.base = base
        val (loaded, json) = GameDevice.load(snapshot, PlayerId(me), ids)
        device = loaded; codec = json
        device.presence(live, now)
        this.loaded = true
        timeline.clear()
        changed()
    }

    val needsReload: Boolean get() = device.needsReload

    /** The board the table shows now: the projected state at the local head and the device's own pending events. */
    fun play(): PlayState {
        shown?.takeIf { it.first == version }?.let { return it.second }
        val status = device.status
        val attention = when (status) {
            GameDevice.Status.YourMove, GameDevice.Status.Drafting, is GameDevice.Status.Stopped -> me
            is GameDevice.Status.Waiting -> device.opponent.value
            else -> null
        }
        val tip = device.session.tip
        val play = EngineProjection.project(base, device.model, me, tip, attention, staging, tokenArt, pictures)
        val previous = shown?.second
        shown = version to play
        if (previous == null || previous.currentNodeId != tip || previous.board.turn != play.board.turn) transition(previous, play, tip)
        return play
    }

    /**
     * What the table animates between the board shown before and [play] (HIS-030, HIS-031): the events that led from
     * one to the other along the line (forward: shown, made or played back; backward: taken back), their change flashes
     * and shuffles, and the event chip's text (HIS-075: the other player's newest event). A load or a reload is a jump:
     * nothing flashes or moves.
     */
    class Transition(val serial: Long, val cursor: String, val jump: Boolean, val changes: List<org.asyncttwiz.android.TableChange>,
        val shuffled: Set<String>, val forward: List<GameEvent>, val backward: List<GameEvent>)

    /** The last [Transition] (the screen applies each once, by its serial). */
    var transition: Transition? = null
        private set
    private var transitions = 0L
    private var pt: Map<String, Pair<Int, Int>> = emptyMap()
    private var loaded = true

    private fun transition(before: PlayState?, after: PlayState, tip: String) {
        val ptAfter = EngineChanges.powerToughness(device.model.engine)
        val line = timeline.shown(this)
        val from = before?.currentNodeId?.let { id -> line.indexOfFirst { it.id == id }.takeIf { it >= 0 } ?: if (id == device.session.lineStart) -1 else null }
        val to = line.indexOfFirst { it.id == tip }.takeIf { it >= 0 } ?: if (tip == device.session.lineStart) -1 else null
        val jump = loaded || before == null || from == null || to == null
        val forward = if (!jump && to!! > from!!) line.subList(from + 1, to + 1).toList() else emptyList()
        val backward = if (!jump && to!! < from!!) line.subList(to + 1, from + 1).reversed() else emptyList()
        val changes = if (jump || before == null) emptyList() else EngineChanges.between(before, after, pt, ptAfter, forward + backward)
        transition = Transition(++transitions, tip, jump, changes, if (jump) emptySet() else EngineChanges.shuffled(after, forward),
            forward, backward)
        pt = ptAfter
        loaded = false
    }

    /** The timeline's events and texts (HIS-001–HIS-005 on engine events). */
    val timeline = EngineTimeline()

    /** The event [id] among those shown to the player (the timeline's), or null. */
    fun event(id: String): GameEvent? = timeline.shown(this).firstOrNull { it.id == id }

    /**
     * Timeline mode (HIS-004, HIS-012): the board at shown event [id] (on the main line up to the local head, or one of the
     * player's own pending events), view-only; null when the device can't reach it exactly (it was trimmed, or a draft
     * is open).
     */
    fun reviewPlay(id: String): PlayState? {
        val session = device.session
        if (session.drafting) return null
        val model = when {
            id == session.tip -> device.model.copy()
            session.pending.any { it.id == id } -> device.model.copy().also { m ->
                for (e in session.pending.asReversed()) { if (e.id == id) break; m.revert(e) }
            }
            else -> runCatching { session.stateAt(id) }.getOrNull()
        } ?: return null
        if (model.stale) return null
        return EngineProjection.project(base, model, me, id, null, null, tokenArt, pictures)
    }

    /** The token pictures for the engine's tokens (SYS-072), from the device's card records. */
    var tokenArt: org.asyncttwiz.automatum.TokenArt = org.asyncttwiz.automatum.TokenArt.None
        set(value) { if (field !== value) { field = value; changed() } }
    private val pictures = HashMap<arcanum.engine.state.ObjectId, org.asyncttwiz.protocol.SubmittedDefinition>()

    /** The player's own staging of a declaration made behind the head (at a decision stop in playback: their lanes and
     * attack choices, CMB-028, CMB-034), shown while the point it was made at lasts and sent only as the declaration itself
     * (I-D56 §12); at the head staging is shared as table changes instead (I-D54 §8, [EngineDrafts]). */
    var staging: EngineProjection.Staging? = null
        private set

    /** Stages [play]'s combat lanes and attack choices (a board the combat controls made from the shown one). */
    fun stage(play: PlayState) {
        val records = play.board.engineRecords.filter { it.kind == org.asyncttwiz.protocol.EngineRecordKind.PreAnswer }
        staging = EngineProjection.Staging(EngineProjection.Staging.keyOf(device.model.engine), play.board.combat, records)
        changed()
    }

    /** Drops the device's own staging: the shared drafts of the table show (I-D54 §8). */
    fun unstage() { if (staging != null) { staging = null; changed() } }

    /** The player's opening: their mulligan (I-D54 §8: simultaneous mulligans), or null when they can't take one. */
    fun mulligan(): GameDevice.Acted? = setup(arcanum.engine.decision.Answer.Option("Mulligan"))

    /** The player keeps, putting [bottom] (card ids) on the bottom of their library when cards are owed. */
    fun keep(bottom: List<String>): GameDevice.Acted? {
        val keep = setupInput(arcanum.engine.decision.Answer.Option("Keep")) ?: return null
        if (bottom.isEmpty()) return act(keep)
        val objects = bottom.mapNotNull { id -> device.model.engine.player(PlayerId(me)).hand.firstOrNull { (it as? arcanum.engine.state.ObjectId.Card)?.card?.value == id } }
        return send(listOf(keep)).let { first ->
            if (first !is GameDevice.Acted.Sent) first
            else setupInput(arcanum.engine.decision.Answer.Objects(objects))?.let { act(it) } ?: first
        }
    }

    private fun setup(answer: arcanum.engine.decision.Answer): GameDevice.Acted? = setupInput(answer)?.let(::act)

    /** The input answering the player's own setup decision with [answer]: the open decision when it is theirs, else their
     * declaration ahead of it ([Input.SetupAnswer]); null when the engine lists neither. */
    private fun setupInput(answer: arcanum.engine.decision.Answer): Input? {
        val s = device.model.engine
        val d = s.pending?.decision
        val player = PlayerId(me)
        if (d != null && arcanum.engine.Engine.decider(s, d.player) == player) return Input.Answer(d.id, answer)
        val listed = arcanum.engine.Engine.options(s, player)
        return Input.SetupAnswer(player, answer).takeIf { listed.any { it is Input.SetupAnswer && it.player == player } }
    }

    /** The step arrow's pass: the player's own priority (at a decision stop: their answer to what was assumed). */
    fun pass(): GameDevice.Acted {
        val holder = device.model.engine.priority.holder ?: return GameDevice.Acted.Refused("noPriority")
        if (arcanum.engine.Engine.decider(device.model.engine, holder).value != me) return GameDevice.Acted.Refused("notYours")
        return act(Input.PassPriority(holder))
    }

    private fun changed() { version++ }

    // ---- The server

    /** An accepted revision of this play: its engine events in order and the rest of what it changed. */
    fun revision(message: GameRevision) {
        revision = message.resultRevision
        val update = message.engine
        val texts = listOfNotNull(message.engineEvent) + update?.events.orEmpty()
        if (texts.isEmpty() && update == null) return
        val events: List<GameEvent> = texts.map(codec::decodeEvent)
        device.revision(events, update)
        drainNotes()
        device.advance(now)
        changed()
    }

    /** The server refused [action] (outcome other than accepted) for [reason]. */
    fun refused(action: GameAction, reason: String?) {
        device.refused(action, reason ?: "refused")
        drainNotes()
        device.advance(now)
        changed()
    }

    private fun drainNotes() { notes.addAll(device.notes()) }

    /** The opponent's presence (the green dot, TRN-042) at [now]; makes the device's own steps (passes, assumptions,
     * a live wait turning async after the grace). */
    fun tick(live: Boolean, now: Long) {
        this.live = live; this.now = now
        val before = device.status
        val tip = device.session.tip
        device.presence(live, now)
        device.advance(now)
        drainNotes()
        if (device.status != before || device.session.tip != tip || notes.isNotEmpty()) changed()
    }

    /** The device's reproduction bundle (I-D43) for a problem report; null while it can't be made. */
    fun repro(): GameRepro? = runCatching {
        val repro = device.repro(codec)
        val session = device.session
        if (repro.base != null || session.drafting) return@runCatching repro
        // The device can't go back exactly to the start of the line it holds (more than a few turns back): the bundle
        // starts at the earliest event it can reach (reachable events are a suffix of the line: binary search)
        val events = session.events
        var low = 0; var high = events.size - 1; var found: Pair<Int, org.asyncttwiz.protocol.game.GameModel>? = null
        while (low <= high) {
            val mid = (low + high) / 2
            val model = session.stateAt(events[mid].id)
            if (model != null) { found = mid to model; high = mid - 1 } else low = mid + 1
        }
        val (at, model) = found ?: return@runCatching repro
        repro.copy(lineStart = events[at].id, base = codec.encodeModel(model), events = events.drop(at + 1).map(codec::encodeEvent))
    }.getOrNull()

    /** What to send now (drained). */
    fun take(now: Long): List<GameAction> = device.take(now)

    // ---- The player's actions

    /** The player's own [inputs] of one flow (a cast with its answers, an answer with its orders, a declaration), as
     * one acting point; the device answers the randomness and makes the passes that follow by itself. */
    fun send(inputs: List<Input>, table: List<TableChange> = emptyList()): GameDevice.Acted {
        if (inputs.isEmpty()) return GameDevice.Acted.Refused("nothing")
        val first = inputs.first()
        val rest = inputs.drop(1).iterator()
        // I-D54 §8: a declaration ends the shared staging of it in the same event (at the head; at a decision stop the
        // answer must be the input alone to confirm what was assumed)
        val t = device.model.table
        val clear = if (device.session.behind) emptyList() else listOfNotNull(
            t.attackDraft?.takeIf { inputs.any { it is Input.DeclareAttackers } }?.let { TableChange.AttackDraft(it, null) },
            t.blockDraft?.takeIf { inputs.any { it is Input.DeclareBlockers } }?.let { TableChange.BlockDraft(it, null) })
        val result = device.act(first, table + clear) { if (rest.hasNext()) rest.next() else null }
        after()
        return result
    }

    /** One input of the player's own (a pass, a keep, a concession). */
    fun act(input: Input): GameDevice.Acted = device.act(input).also { after() }

    /** The step arrow's End turn (TRN-057): the device passes for the player until the turn ends. */
    fun endTurn() { device.endTurn(now); after() }

    /** The waiting bar's Assume button (§4): the opponent's pass or keep, or [blocks] (the Assume blockers screen's). */
    fun assume(blocks: List<Block> = emptyList()): GameDevice.Acted = device.assume(blocks).also { after() }

    /** Back on the Assume blockers screen: it stays a wait. */
    fun closeBlockersScreen() { device.closeBlockersScreen(); changed() }

    fun table(changes: List<TableChange>): GameDevice.Acted = device.table(changes).also { after() }
    fun concede(): GameDevice.Acted = device.concede().also { after() }
    fun suggest(result: GameResult): GameDevice.Acted = device.suggest(result).also { after() }
    fun answerSuggestion(accept: Boolean): GameDevice.Acted = device.answerSuggestion(accept).also { after() }
    fun confirmResult(): GameDevice.Acted = device.confirmResult().also { after() }
    fun settings(settings: PlaySettings) { device.settings(settings); after() }

    /** Leaves the catch-up screen (§7): playback starts. */
    fun startPlayback() { device.play(); changed() }

    /** Playback: the next buffered event, unless it stops before it. */
    fun step(): GameEvent? = device.step().also { if (it != null) after() }

    /** Return to current / Back while behind: plays on to the next decision point. */
    fun playToStop(): List<GameEvent> = device.playToStop().also { after() }

    fun undo(target: String? = device.undoTarget()): GameDevice.Undo = device.undo(target).also { after() }

    /** Whether taking the line back to just after [target] needs the other player's approval (§10), worked out as the
     * device's undo does without sending anything; null when nothing would be undone. */
    fun undoNeedsRequest(target: String): Boolean? {
        val session = device.session
        if (session.drafting) return null
        val after = session.after(target) ?: return true
        if (after.isEmpty()) return null
        val at = session.stateAt(target) ?: return true
        val others = device.localHeads.filterKeys { it != PlayerId(me) }.values.toList()
        return org.asyncttwiz.protocol.game.Rewind.requestNeeded(at, target, after, PlayerId(me), others) != null
    }
    fun requestUndo(target: String, message: String) { device.requestUndo(target, message); after() }
    fun answerRequest(accept: Boolean) { device.answerRequest(accept); after() }
    fun cancelRequest() { device.cancelRequest(); after() }

    private fun after() {
        drainNotes()
        device.advance(now)
        changed()
    }

    // Last, once every property above is set
    init { load(snapshot, base) }
}
