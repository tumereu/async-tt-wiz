package org.asyncttwiz.android

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.asyncttwiz.protocol.GameLifecycle
import org.asyncttwiz.protocol.GameSummary
import org.asyncttwiz.protocol.PodAttention
import org.asyncttwiz.protocol.PodSummary

/**
 * LOB-267 (owner, 2026-11-22): what waits for you somewhere, as a dot's colour. Of several, the one of the highest [rank]
 * shows: your move over your pick over your deck to build.
 */
sealed class WaitingDot(val rank: Int, val color: Color, val tag: String) {
    /** Your move in a game (TRN-058), an invitation to you, or a pod challenge you can accept now. */
    data object Move : WaitingDot(3, Color(0xffe0393e), "move")
    /** Your pick in a pod. */
    data object Pick : WaitingDot(2, Color(0xff2f7fe0), "pick")
    /** Your deck to build in a pod (DRF-013's "Build your deck"). */
    data object Build : WaitingDot(1, Color(0xff2f9e52), "build")
}

/** LOB-267: [game]'s dot for [accountId]: your move in it, or an invitation (a pod challenge too) you can accept now. A
 * challenge held until you submit your deck (LOB-025) waits for nothing of yours yet. */
fun gameWaitingDot(game: GameSummary, accountId: String): WaitingDot? = when {
    game.lifecycle == GameLifecycle.ACTIVE && game.attentionHolder == accountId -> WaitingDot.Move
    incomingInvitation(game, accountId) && !game.invitationHeld -> WaitingDot.Move
    else -> null
}

/** LOB-267: [pod]'s dot: your pick, or your deck to build (DRF-013). */
fun podWaitingDot(pod: PodSummary): WaitingDot? = when (pod.attention) {
    PodAttention.YOUR_PICK -> WaitingDot.Pick
    PodAttention.BUILD -> WaitingDot.Build
    PodAttention.NONE -> null
}

/** The hamburger's dot and what lights it ([why]: the game or pod, for the log). */
data class NavigationDot(val dot: WaitingDot, val why: String)

/**
 * LOB-268: the hamburger's one dot, in the highest colour of everything waiting for [accountId] in [games] (the games and
 * invitations) and [pods], except the game ([openGame]) or pod ([openPod]) being looked at; null when nothing waits.
 */
fun navigationDot(games: List<GameSummary>, pods: List<PodSummary>, accountId: String, openGame: String? = null,
    openPod: String? = null): NavigationDot? {
    val waiting = games.distinctBy { it.id }.filter { it.id != openGame }.mapNotNull { game ->
        gameWaitingDot(game, accountId)?.let { NavigationDot(it, "game ${game.id.take(8)} \"${game.gameName}\" (" +
            "${if (game.lifecycle == GameLifecycle.ACTIVE) "your move" else if (game.podId != null) "pod challenge" else "invitation"})") }
    } + pods.distinctBy { it.id }.filter { it.id != openPod }.mapNotNull { pod ->
        podWaitingDot(pod)?.let { NavigationDot(it, "pod ${pod.id.take(8)} \"${pod.name}\" (${podStatus(pod)})") }
    }
    return waiting.maxByOrNull { it.dot.rank }
}

/** LOB-268: the dot the play screens' hamburgers (a game's table, a started pod's header, DRF-306) show; the shell works it
 * out with the game or pod open left out. */
val LocalNavigationDot = compositionLocalOf<WaitingDot?> { null }

/** LOB-267: the dot itself, the chat's unread dot's shape and size (LOB-163): a row's ("waiting-dot-…"), or with [hamburger]
 * the hamburger's ("navigation-dot-…"). */
@Composable
fun WaitingDotMark(dot: WaitingDot, modifier: Modifier = Modifier, hamburger: Boolean = false) {
    Box(modifier.testTag("${if (hamburger) "navigation" else "waiting"}-dot-${dot.tag}").size(6.dp).background(dot.color, CircleShape))
}

/** LOB-268: the hamburger's dot over the top right of its [button] (as the chat button's unread dot, LOB-163). */
@Composable
fun WithNavigationDot(dot: WaitingDot?, inset: Dp = 10.dp, button: @Composable () -> Unit) {
    Box {
        button()
        if (dot != null) WaitingDotMark(dot, Modifier.align(Alignment.TopEnd).padding(inset), hamburger = true)
    }
}
