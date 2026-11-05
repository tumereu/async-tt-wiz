package org.asyncttwiz.android

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.asyncttwiz.protocol.*
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import android.os.SystemClock
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.delay

/** Other runtime owners supply real routes. Null means unavailable, never a successful-looking stub. */
data class TableRuntimeHooks(val chat: (() -> Unit)? = null, val history: (() -> Unit)? = null,
    val undo: (() -> Boolean)? = null,
    /** HIS-083: holding Undo (the rewind dialog, I-D56 §10); null: holding it only says what it is. */
    val undoHold: (() -> Unit)? = null,
    /** Bumped when an Undo can't do anything: the button shakes. */
    val undoShakes: Int = 0, val reviewing: Boolean = false,
    val controlsConnected: Boolean? = null,
    val navigation: (() -> Unit)? = null,
    val returnCurrent: (() -> Unit)? = null, val chatUnread: Int = 0,
    val liveTimeline: (@Composable () -> Unit)? = null,
    val bottomPanel: (@Composable (@Composable () -> Unit) -> Unit)? = null,
    val errorMessage: String? = null, val dismissError: (() -> Unit)? = null,
    val onSceneSettled: ((String) -> Unit)? = null, val sceneVersion: String? = null,
    val liveView: Boolean = !reviewing,
    val changes: TableChanges? = null,
    /** The opponent's presence: a dot on their life square and a line in their life panel. */
    val opponentPresence: ReceivedPresence? = null,
    val gameActionsBlocked: Boolean = false, val resultControls: TableResultControls? = null,
    /** Null outside timeline mode; drives the centre status line and the corner play/pause flash. */
    val timelinePlayback: TimelinePlaybackStatus? = null,
    /** TRN-066: why the table waits for the other player, faint over the battlefield (never taking a touch); null when
     * it doesn't. */
    val waitingLine: String? = null,
    /** HIS-094: the large text in the board's centre that a tap acts on ("Bob has made moves · Tap to watch"); null: none. */
    val centreAction: TableCentreAction? = null,
    /** HIS-095: a tap on the table (a card, a pile or the empty table) does only this (pausing playback); null: taps act
     * as usual. */
    val boardTap: (() -> Unit)? = null)

/** A large text in the board's centre that a tap acts on ([TableRuntimeHooks.centreAction]). */
data class TableCentreAction(val text: String, val run: () -> Unit)

/** The table route: a game played on engine state has its table ([EngineTableScreen], I-D56); until it first loads on
 * this device, a placeholder without cards that says it is loading (or why it can't). */
@Composable
internal fun TableScreen(controller: LobbyController, accountId: String, hamburger: (() -> Unit)?, landscape: Boolean) {
    // Never the whole lobby state: its games-list refresh would recompose the table every few seconds.
    val state = controller.table.value
    val game = state.detail ?: return
    if (controller.engineGame() != null) { EngineTableScreen(controller, accountId, hamburger, landscape); return }
    var loadTimedOut by remember(game.id) { mutableStateOf(false) }
    var loadAttempt by remember(game.id) { mutableIntStateOf(0) }
    LaunchedEffect(game.id, game.id in state.connectedGames, loadAttempt) {
        loadTimedOut = false
        delay(30_000); loadTimedOut = true
    }
    val back = remember(game.id) { TableBackStateMachine() }
    val context = LocalContext.current
    BackHandler {
        when (back.back(SystemClock.elapsedRealtime(), false, false)) {
            TableBackAction.PROMPT -> Toast.makeText(context, "Press back again to leave", Toast.LENGTH_SHORT).show()
            TableBackAction.LEAVE -> controller.back()
            TableBackAction.CLOSE_OVERLAY, TableBackAction.EXIT_REVIEW -> {}
        }
    }
    Column(Modifier.fillMaxSize().background(Color(0xfff3f5f5))) {
        TableUnavailable(game.gameName, landscape, Modifier.weight(1f), hamburger,
            message = state.connectionMessage ?: if (loadTimedOut) "Connection timed out." else null,
            loading = tableIsLoading(state, game.id, null, loadTimedOut),
            retry = { loadAttempt++; controller.reconnect() })
    }
}

/** Absence of an accepted head is normal during socket admission and initial catch-up, not failure. */
internal fun tableIsLoading(state: LobbyTableView, gameId: String, historyError: String?, timedOut: Boolean = false) =
    historyError == null && state.connectionMessage == null && gameId !in state.reconnectingGames && !timedOut


internal val OwnTableColor = Color(0xff237d68)
internal val OpponentTableColor = Color(0xffb44853)
/** Emblem chips (I-D25): the Automatum violet of the switch (integration spec §5). */
internal val EmblemChipColor = Color(0xff7a4fc4)
