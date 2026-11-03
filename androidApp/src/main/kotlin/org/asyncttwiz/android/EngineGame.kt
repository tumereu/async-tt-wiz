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

    init { load(snapshot, base) }

    /** (Re)loads the game from the server's [snapshot] (a new head, or the device asked to load again). */
    fun load(snapshot: EngineSnapshot, base: PlayState = this.base) {
        this.base = base
        val (loaded, json) = GameDevice.load(snapshot, PlayerId(me), ids)
        device = loaded; codec = json
        device.presence(live, now)
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
        val play = EngineProjection.project(base, device.model, me, device.session.tip, attention, staging)
        shown = version to play
        return play
    }

    /** The player's own staging of a declaration (their lanes and attack choices, CMB-028, CMB-034), shown while the
     * point it was made at lasts and sent only as the declaration itself (I-D56 §12). */
    var staging: EngineProjection.Staging? = null
        private set

    /** Stages [play]'s combat lanes and attack choices (a board the combat controls made from the shown one). */
    fun stage(play: PlayState) {
        val records = play.board.engineRecords.filter { it.kind == org.asyncttwiz.protocol.EngineRecordKind.PreAnswer }
        staging = EngineProjection.Staging(EngineProjection.Staging.keyOf(device.model.engine), play.board.combat, records)
        changed()
    }

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

    /** What to send now (drained). */
    fun take(now: Long): List<GameAction> = device.take(now)

    // ---- The player's actions

    /** The player's own [inputs] of one flow (a cast with its answers, an answer with its orders, a declaration), as
     * one acting point; the device answers the randomness and makes the passes that follow by itself. */
    fun send(inputs: List<Input>, table: List<TableChange> = emptyList()): GameDevice.Acted {
        if (inputs.isEmpty()) return GameDevice.Acted.Refused("nothing")
        val first = inputs.first()
        val rest = inputs.drop(1).iterator()
        val result = device.act(first, table) { if (rest.hasNext()) rest.next() else null }
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
}
