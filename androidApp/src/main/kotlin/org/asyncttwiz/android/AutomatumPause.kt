package org.asyncttwiz.android

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.outlined.PanTool
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import org.asyncttwiz.automatum.PausePoints
import org.asyncttwiz.protocol.*

/**
 * What the viewer's device does at a pause on the live table (integration spec I-D41, STK-037, STK-038).
 */
internal sealed interface PauseRole {
    /** How long the pause runs on this device. */
    val millis: Long

    /** The viewer holds priority at a pause: a progress bar over [millis]; [passes]: their device passes for them when
     * it runs out: the step that made it was the viewer's own, or the other player, whose device would go on, isn't
     * online (#11). */
    data class Yours(override val millis: Long, val passes: Boolean) : PauseRole

    /** [holder], the other player, holds priority at a pause the viewer's step made: the viewer's device passes for them
     * after [millis] (0: at once). */
    data class Theirs(val holder: String, override val millis: Long) : PauseRole
}

/** Finalreview-combat #6: how often a device tries its pass at a pause that ran out, and how long it waits in between. */
private const val AUTO_PASS_TRIES = 3
private const val AUTO_PASS_RETRY_MILLIS = 400L

internal object LivePause {
    /**
     * The viewer's part in the pause on [board] (the live table), when it has one: [author] made the board's latest
     * event; [opponentOnline]: the other player has the game open now (the green presence dot, TRN-042). The device of
     * whoever made the latest event goes on: after the pause for its own player, else at once when the other player
     * isn't online or [PauseSettings.proceedAtOnce], and after the response time when "Wait". When the other player
     * isn't online their device can't go on, so the viewer's own passes for them when their pause runs out (#11).
     */
    fun role(board: BoardState, viewer: String, author: String?, opponentOnline: Boolean, settings: PauseSettings): PauseRole? {
        val at = PausePoints.at(board)?.takeIf { it.point == PausePoints.Point.Pause } ?: return null
        return when {
            at.holder == viewer -> PauseRole.Yours(settings.responseMillis, passes = author == viewer || !opponentOnline)
            author == viewer -> PauseRole.Theirs(at.holder, if (!opponentOnline || settings.proceedAtOnce) 0 else settings.responseMillis)
            else -> null
        }
    }
}

/** A pause shown on the live board [node] since [startedAt] (the frame clock's time); [running] until it runs out. */
internal data class ShownPause(val node: String, val role: PauseRole, val startedAt: Long, val running: Boolean = true)

/** How a pause reaches the timeline (I-D41, STK-039): set by the table's host. */
internal interface PauseRoutes {
    /** Holds the viewer's pause at [node]: timeline mode there with a continuation armed (HIS-037). */
    fun hold(node: String): Boolean
    /** Whether the viewer holds at [node] (its continuation armed, or about to be). */
    fun holding(node: String): Boolean
    /** The live table, held on the pause, moves on to the game as it is now. */
    fun releaseLive()
    /** Leaves a hold for the live game, sending nothing. */
    fun leaveHold()
}

/**
 * The viewer's pauses (I-D41): the one shown on the live board ([shown], set by [AutomatumPauseDriver]) and the one they
 * hold ([heldNode]). Personal display state only; what goes to the game goes through [SharedFlowController].
 */
internal class PauseController {
    var shown by mutableStateOf<ShownPause?>(null); private set
    /** The point the viewer held at (STK-039). */
    var heldNode by mutableStateOf<String?>(null); private set
    /** The live game's newest event, to tell a pause the game has gone on from. */
    var liveHead by mutableStateOf<String?>(null)
    var routes: PauseRoutes? = null

    /** Shows [role]'s pause on [node] from [at], the frame clock's time (so its bar and its timer agree). */
    fun show(node: String, role: PauseRole, at: Long) { shown = ShownPause(node, role, at) }
    fun clear() { shown = null }
    /** The pause on [node] ran out: still shown, no longer timed. */
    fun end(node: String) { shown?.takeIf { it.node == node }?.let { shown = it.copy(running = false) } }
    /** The pause on [node] while it runs. */
    fun running(node: String): ShownPause? = shown?.takeIf { it.node == node && it.running }
    /** The part of [pause] still to run at [now] (frame clock), 1 to 0. */
    fun remaining(pause: ShownPause, now: Long): Float = if (pause.role.millis <= 0) 0f
        else (1f - (now - pause.startedAt).toFloat() / pause.role.millis).coerceIn(0f, 1f)

    /** Hold or Respond in a running pause: the viewer stays at [node] as long as they like (STK-039). */
    fun hold(node: String): Boolean {
        val routes = routes ?: return false
        if (!routes.hold(node)) return false
        heldNode = node
        shown = null
        return true
    }
    /**
     * #1, #2 (STK-039): a reaction the viewer begins (a cast or ability, Respond) holds their pause first, with its bar
     * running or not, so it continues from that point (a branch once the game went on) and nothing passes for them
     * meanwhile: the pause shown on the live table (also while it is held there after the game went on), else [play]
     * (the board acted on) when it is [viewer]'s pause. Nothing when it is held (or continued from) already.
     */
    fun holdForReaction(play: PlayState?, viewer: String) {
        val node = shown?.takeIf { it.role is PauseRole.Yours }?.node
            ?: play?.takeIf { PausePoints.pauseFor(it.board, viewer) }?.currentNodeId ?: return
        if (!holding(node) && routes?.holding(node) != true) hold(node)
    }
    /** Whether the viewer holds at [node]. */
    fun holding(node: String): Boolean = heldNode == node && routes?.holding(node) == true

    /** Whether [viewer]'s own pause is shown on [play]'s board: running, or held there. */
    fun ownPause(play: PlayState, viewer: String): Boolean =
        (running(play.currentNodeId)?.role is PauseRole.Yours || holding(play.currentNodeId) && PausePoints.pauseFor(play.board, viewer))

    /** The board (node) whose pause bar the viewer hid with its eye (I-D10, finalreview-combat #2); null: none. */
    var hiddenAt by mutableStateOf<String?>(null); private set
    fun toggleHidden(node: String) { hiddenAt = if (hiddenAt == node) null else node }

    /** #4 (SYS-004): the pauses this device has passed by itself, newest last (a few hundred at most). */
    private val passed = LinkedHashSet<String>()
    /** Whether this device passed by itself at [node] already: an Undo brought it back, and isn't redone by itself. */
    fun passedAt(node: String): Boolean = node in passed
    /** This device passed by itself at [node]. */
    fun passed(node: String) {
        passed += node
        if (passed.size > MAX_PASSED) passed.remove(passed.first())
    }

    private companion object { const val MAX_PASSED = 256 }
    /** Whether the live game has gone on from [node]. */
    fun behind(node: String): Boolean = liveHead != null && liveHead != node

    /**
     * Passing on a pause shown at [node] when the game has already gone on from it: a held pause goes back to the live
     * game, the live table held on it moves on. Nothing is sent (the other player's device passed already). False when
     * the game is still at [node]: then the pass is sent as usual.
     */
    fun passBehind(node: String): Boolean {
        if (!behind(node)) return false
        if (holding(node)) { heldNode = null; routes?.leaveHold() } else { shown = null; routes?.releaseLive() }
        return true
    }
}

/**
 * Runs the pauses of the live table (I-D41): shows the viewer's own with its time running and holds the live table on it
 * meanwhile (STK-037); passes for the other player when the viewer's step made their pause (STK-038); copies the
 * viewer's Full control into the game after it was changed on this device (SYS-071, [sync]). [play] is the board shown,
 * [liveShown] whether it is the live game.
 */
@Composable
internal fun AutomatumPauseDriver(toolkit: TableToolkitController, playback: HistoryPlayback?, play: PlayState?,
    liveHead: String?, liveShown: Boolean, opponentOnline: Boolean, settings: PauseSettings,
    sync: FullControlSync = remember { FullControlSync.Memory() }) {
    val pause = toolkit.pause
    SideEffect { pause.liveHead = liveHead }
    val node = play?.currentNodeId
    val role = if (play != null && node != null && liveShown)
        LivePause.role(play.board, toolkit.viewer, toolkit.authorOf(node), opponentOnline, settings) else null
    /**
     * Passes for [holder] at [node], once per device (#4: a pause an Undo brought back isn't passed again by itself).
     * Finalreview-combat #6: counted as passed only once the batch went out; while the device is busy with another call it
     * waits for it, and a pass that wasn't sent (the table changed meanwhile) is tried again, a few times at most.
     */
    suspend fun autoPass(holder: String, node: String) {
        repeat(AUTO_PASS_TRIES) { attempt ->
            if (pause.passedAt(node)) return
            if (attempt > 0) delay(AUTO_PASS_RETRY_MILLIS)
            snapshotFlow { toolkit.flows.busy }.first { !it }
            val sent = CompletableDeferred<Boolean>()
            // Marked in the callback itself, so a pass that went out counts even when this effect was cancelled meanwhile
            if (!toolkit.flows.autoPass(holder, node) { if (it) pause.passed(node); sent.complete(it) }) {
                if (toolkit.flows.busy) return@repeat
                return
            }
            if (sent.await()) return
        }
    }
    LaunchedEffect(node, role) {
        if (node == null || role == null) {
            pause.clear()
            playback?.releasePause()
            return@LaunchedEffect
        }
        pause.show(node, role, withFrameMillis { it })
        when (role) {
            is PauseRole.Yours -> {
                playback?.holdPause(node)
                delay(role.millis)
                pause.end(node)
                if (pause.holding(node)) return@LaunchedEffect
                if (role.passes) {
                    // #3: never under the viewer's own open cast; it goes on once that is sent or dropped
                    snapshotFlow { toolkit.cast.open }.first { !it }
                    if (pause.holding(node)) return@LaunchedEffect
                    autoPass(toolkit.viewer, node)
                }
                playback?.releasePause()
            }
            is PauseRole.Theirs -> {
                if (role.millis > 0) delay(role.millis)
                pause.end(node)
                autoPass(role.holder, node)
            }
        }
    }
    // SYS-071 (#5): the device's Full control goes into each game Automatum plays, so the other player's device follows
    // it; only after it was changed on this device, so the game's value (another device of the player may have set it)
    // otherwise wins
    val own = play?.board?.players?.firstOrNull { it.accountId == toolkit.viewer }?.fullControl
    LaunchedEffect(liveShown, own, settings.fullControl, settings.fullControlChanged, node) {
        val board = play?.board ?: return@LaunchedEffect
        if (!liveShown || own == null) return@LaunchedEffect
        val game = play.gameId
        val changed = settings.fullControlChanged
        if (own == settings.fullControl) { if (sync.synced(game) < changed) sync.synced(game, changed); return@LaunchedEffect }
        if (sync.synced(game) >= changed) return@LaunchedEffect
        if (toolkit.enabled() && !toolkit.cast.open && !Automatum.flowOpen(board) &&
            toolkit.submit(PlayAction.Mtg(MtgAction.SetFullControl(settings.fullControl)))) sync.synced(game, changed)
    }
}

/** The pause's time running out: a thin violet bar that empties (STK-037, STK-038). */
@Composable
internal fun PauseProgress(pause: PauseController, shown: ShownPause) {
    val progress = remember(shown) { Animatable(1f) }
    LaunchedEffect(shown) {
        progress.snapTo(pause.remaining(shown, withFrameMillis { it }))
        val left = (progress.value * shown.role.millis).toInt()
        if (left > 0) progress.animateTo(0f, tween(left, easing = LinearEasing))
    }
    LinearProgressIndicator(progress = { progress.value },
        modifier = Modifier.fillMaxWidth().height(4.dp).semantics { contentDescription = "Pause time" },
        color = AutomatumViolet, trackColor = AutomatumViolet.copy(alpha = .18f))
}

/**
 * A pause on an empty Stack (STK-037, STK-038): the step in which the viewer may act now, with the time running and Hold
 * (the step arrow goes on, UI review round 2 #1: no Pass of its own); held, "take your time"; the other player's, while
 * the viewer's device waits for them. Nothing otherwise (on the Stack the respond panel shows the pause, STK-027).
 */
@Composable
internal fun PauseBarHost(toolkit: TableToolkitController, play: PlayState, scene: TableScene?, names: Map<String, String>) {
    if (AutomatumBoards.sharedFlowShown(play) || toolkit.cast.open) return
    val pause = toolkit.pause
    val node = play.currentNodeId
    val viewer = toolkit.viewer
    val running = pause.running(node)
    val held = pause.holding(node) && PausePoints.pauseFor(play.board, viewer)
    if (running == null && !held) return
    val step = pauseStep(play.board, viewer, names)
    // UI review #4 (decided for the owner): docked full width above the hand like every Automatum panel, the eye and Hold
    // where they are in the respond panel
    AboveHand(handRow(scene, viewer)) {
        val role = running?.role
        if (role is PauseRole.Theirs) {
            if (role.millis <= 0) return@AboveHand
            AutomatumDockBar("Automatum waiting") {
                Column(Modifier.weight(1f).padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${names[role.holder] ?: "The other player"} may act · $step", fontSize = 13.sp, maxLines = 2,
                        overflow = TextOverflow.Ellipsis)
                    PauseProgress(pause, running)
                }
            }
            return@AboveHand
        }
        // I-D10 (finalreview-combat #2): the eye hides the panel to see the table under it; its time runs on meanwhile
        AutomatumDockPanel("Your pause", pause.hiddenAt == node, { pause.toggleHidden(node) }, "pause", buttons = {
            if (running != null) ChoiceButton("Hold", androidx.compose.material.icons.Icons.Outlined.PanTool) { pause.hold(node) }
        }) {
            Text(if (held) "$step: take your time" else "$step: you may act", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            if (running != null) PauseProgress(pause, running)
            else Text("Holding — cast or activate as you like", fontSize = 11.sp, color = Color(0xff4b5a5f))
        }
    }
}

/** What an empty-Stack pause is about, for the viewer: the step, or (finalreview-combat #7) on the other player's attack
 * "Alice attacks", so the defending player doesn't read it as their own declaration. */
internal fun pauseStep(board: BoardState, viewer: String, names: Map<String, String>): String {
    val position = TurnPosition.of(board.turn)
    if (position == TurnPosition.DECLARE_ATTACKERS && board.turn.accountId != viewer &&
        board.combat?.pairs?.any { it.attackers.isNotEmpty() } == true)
        return "${names[board.turn.accountId] ?: "The other player"} attacks"
    // UI review round 2 N9: the main phases by the phase chip's names ("Main 1: you may act")
    return when (position) {
        TurnPosition.PRECOMBAT_MAIN, TurnPosition.POSTCOMBAT_MAIN -> stepLabel(position)
        null -> "This step"
        else -> position.label
    }
}
