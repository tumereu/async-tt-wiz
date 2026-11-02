package org.asyncttwiz.android

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
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
import kotlinx.coroutines.delay
import org.asyncttwiz.automatum.PausePoints
import org.asyncttwiz.automatum.StepFlow
import org.asyncttwiz.protocol.*

/**
 * What the viewer's device does at a pause on the live table (integration spec I-D41, STK-037, STK-038).
 */
internal sealed interface PauseRole {
    /** How long the pause runs on this device. */
    val millis: Long

    /** The viewer holds priority at a pause: a progress bar over [millis]; [passes]: the step that made it was the
     * viewer's own, so their device passes for them when it runs out (else the other player's device goes on). */
    data class Yours(override val millis: Long, val passes: Boolean) : PauseRole

    /** [holder], the other player, holds priority at a pause the viewer's step made: the viewer's device passes for them
     * after [millis] (0: at once). */
    data class Theirs(val holder: String, override val millis: Long) : PauseRole
}

internal object LivePause {
    /**
     * The viewer's part in the pause on [board] (the live table), when it has one: [author] made the board's latest
     * event; [opponentOnline]: the other player has the game open now (the green presence dot, TRN-042). The device of
     * whoever made the latest event goes on: after the pause for its own player, else at once when the other player
     * isn't online or [PauseSettings.proceedAtOnce], and after the response time when "Wait".
     */
    fun role(board: BoardState, viewer: String, author: String?, opponentOnline: Boolean, settings: PauseSettings): PauseRole? {
        val at = PausePoints.at(board)?.takeIf { it.point == PausePoints.Point.Pause } ?: return null
        return when {
            at.holder == viewer -> PauseRole.Yours(settings.responseMillis, passes = author == viewer)
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
    /** Whether the viewer holds at [node]. */
    fun holding(node: String): Boolean = heldNode == node && routes?.holding(node) == true
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
 * viewer's Full control into the game (SYS-071). [play] is the board shown, [liveShown] whether it is the live game.
 */
@Composable
internal fun AutomatumPauseDriver(toolkit: TableToolkitController, playback: HistoryPlayback?, play: PlayState?,
    liveHead: String?, liveShown: Boolean, opponentOnline: Boolean, settings: PauseSettings) {
    val pause = toolkit.pause
    SideEffect { pause.liveHead = liveHead }
    val node = play?.currentNodeId
    val role = if (play != null && node != null && liveShown)
        LivePause.role(play.board, toolkit.viewer, toolkit.authorOf(node), opponentOnline, settings) else null
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
                if (role.passes) toolkit.flows.autoPass(toolkit.viewer, node)
                playback?.releasePause()
            }
            is PauseRole.Theirs -> {
                if (role.millis > 0) delay(role.millis)
                pause.end(node)
                toolkit.flows.autoPass(role.holder, node)
            }
        }
    }
    // SYS-071: the device's Full control goes into each game Automatum plays, so the other player's device follows it
    val own = play?.board?.players?.firstOrNull { it.accountId == toolkit.viewer }?.fullControl
    LaunchedEffect(liveShown, play?.board?.automatum, own, settings.fullControl, node) {
        val board = play?.board ?: return@LaunchedEffect
        if (liveShown && board.automatum && own != null && own != settings.fullControl && toolkit.enabled() &&
            !toolkit.cast.open && !Automatum.flowOpen(board))
            toolkit.submit(PlayAction.Mtg(MtgAction.SetFullControl(settings.fullControl)))
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
 * A pause on an empty Stack (STK-037, STK-038): the step in which the viewer may act now, with the time running, Hold and
 * Pass; held, "take your time"; the other player's, while the viewer's device waits for them. Nothing otherwise (on the
 * Stack the respond panel shows the pause, STK-027).
 */
@Composable
internal fun PauseBarHost(toolkit: TableToolkitController, play: PlayState, scene: TableScene?, names: Map<String, String>) {
    if (!play.board.automatum || AutomatumBoards.sharedFlowShown(play) || toolkit.cast.open) return
    val pause = toolkit.pause
    val node = play.currentNodeId
    val viewer = toolkit.viewer
    val running = pause.running(node)
    val held = pause.holding(node) && PausePoints.pauseFor(play.board, viewer)
    if (running == null && !held) return
    val step = TurnPosition.of(play.board.turn)?.label ?: "This step"
    val stack = scene?.stackRegion ?: scene?.items?.firstOrNull { it.zone == PlayZone.STACK && it.kind == TableItemKind.PILE }?.bounds
    CastPanelPlacement(stack, null) {
        val role = running?.role
        if (role is PauseRole.Theirs) {
            if (role.millis <= 0) return@CastPanelPlacement
            PauseSurface("Automatum waiting") {
                Text("${names[role.holder] ?: "The other player"} may act · $step", fontSize = 13.sp, maxLines = 2,
                    overflow = TextOverflow.Ellipsis)
                PauseProgress(pause, running)
            }
            return@CastPanelPlacement
        }
        PauseSurface("Your pause") {
            Text(if (held) "$step: take your time" else "$step: you may act", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            if (running != null) PauseProgress(pause, running)
            else Text("Holding — cast or activate as you like", fontSize = 11.sp, color = Color(0xff4b5a5f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, androidx.compose.ui.Alignment.End)) {
                if (running != null) OutlinedButton({ pause.hold(node) }) { Text("Hold") }
                Button({ if (!pause.passBehind(node)) toolkit.flows.step(StepFlow.Until.Pass) },
                    colors = ButtonDefaults.buttonColors(containerColor = AutomatumViolet)) { Text("Pass") }
            }
        }
    }
}

@Composable
private fun PauseSurface(description: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.semantics { contentDescription = description }, shape = RoundedCornerShape(8.dp), color = Color.White,
        shadowElevation = 6.dp, border = BorderStroke(2.dp, AutomatumViolet)) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
    }
}
