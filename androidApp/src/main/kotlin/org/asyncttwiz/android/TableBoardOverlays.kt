package org.asyncttwiz.android

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.asyncttwiz.protocol.*

/** What lies over the board below the chrome: the review dim, the playback flash, change and event
 * feedback, and a pending result. Inline, so it stays part of TableSurface's own recomposition scope. */
@Composable
internal inline fun TableBoardOverlays(game: GameSummary, play: PlayState, accountId: String, runtime: TableRuntimeHooks,
    scene: TableScene, rendered: State<TableRenderFrame>) {
    if (runtime.reviewing) scene.world?.visibleViewport?.let { board ->
        // Purely visual siblings: no pointer handlers, so review gestures and chrome stay usable.
        Box(Modifier.offset(board.x.dp, board.y.dp).size(board.width.dp, board.height.dp)
            .background(Color.Black.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
            TimelineCentreText(runtime.opponentBranched, runtime.timelinePlayback ?: TimelinePlaybackStatus.PAUSED, runtime.oldBranch)
        }
    }
    // Outside the review condition: the end of playback (back to live) still flashes the pause.
    scene.world?.visibleViewport?.let { board ->
        PlaybackStatusFlash(runtime.timelinePlayback, Modifier.offset(board.x.dp, board.y.dp).size(board.width.dp, board.height.dp))
    }
    TableChangeOverlay(runtime.changes, accountId, scene.appearance,
        item = { id -> rendered.value.items[id] })
    TableEventFeedbackOverlay(runtime.changes?.feedback, accountId, game.inviter.accountId, scene,
        renderedScene = { rendered.value.scene },
        usernames = mapOf(game.inviter.accountId to game.inviter.username, game.recipient.accountId to game.recipient.username))
    ResultActions.pendingPresentation(game, play, accountId)?.let { result ->
        val board = scene.world?.visibleViewport
        Box(if (board == null) Modifier.fillMaxSize() else
            Modifier.offset(board.x.dp, board.y.dp).size(board.width.dp, board.height.dp),
            contentAlignment = Alignment.Center) {
            ResultTablePanel(result, runtime.resultControls, Modifier.padding(8.dp))
        }
    }
}
