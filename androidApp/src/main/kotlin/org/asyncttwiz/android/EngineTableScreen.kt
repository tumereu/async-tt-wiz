package org.asyncttwiz.android

import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import arcanum.engine.setup.GameResult
import arcanum.engine.state.PlayerId
import kotlinx.coroutines.delay
import org.asyncttwiz.protocol.*
import org.asyncttwiz.protocol.game.GameDevice

/**
 * The table of a game played on engine state (I-D56 step C, behind [EnginePath] until step D): the same table surface,
 * panels and controls as today's, drawn from the engine's state ([EngineGame.play]) and acting through the device core
 * ([EngineTableRoutes]); no timeline review, tree or branching: the async play panels ([EngineAsyncHost]) instead.
 */
@Composable
internal fun EngineTableScreen(controller: LobbyController, accountId: String, hamburger: (() -> Unit)?, landscape: Boolean) {
    val state = controller.table.value
    val game = state.detail ?: return
    val play = state.play ?: return
    val engine = controller.engineGame() ?: return
    val interactive = game.id !in state.reconnectingGames
    val routes = remember(controller, game.id, accountId) {
        EngineTableRoutes(accountId, { controller.engineGame() }, { controller.engineSent() },
            { controller.table.value.play?.takeIf { it.gameId == game.id } })
    }
    val context = LocalContext.current
    val tokenArt = remember(context) { AndroidTokenArt.of(context.applicationContext) }
    val playId = play.id
    val toolkit = remember(controller, game.id, playId, accountId) {
        TableToolkitController(accountId, { controller.table.value.play?.takeIf { it.id == playId && it.gameId == game.id } },
            { game.id !in controller.table.value.reconnectingGames && routes.canAct() },
            { action, _ -> routes.local(listOf(action)) },
            ending = { intent -> engineEnding(routes, intent) },
            waitingForConnection = { game.id !in controller.table.value.connectedGames },
            playBatch = { actions, _ -> routes.local(actions) },
            automatumWork = AutomatumWork.Background, tokenArt = tokenArt, engine = routes)
    }
    // LOB-113 (I-D56 §7): a new game starts with the player's defaults from Settings, given once by this device
    val (_, pausePreferences) = rememberPauseSettings(accountId)
    LaunchedEffect(game.id, playId) {
        if (!pausePreferences.engineDefaultsGiven(playId)) {
            val defaults = pausePreferences.engineDefaults
            if (engine.device.settings != defaults) { engine.settings(defaults); controller.engineSent() }
            pausePreferences.engineDefaultsGiven(playId, true)
        }
    }
    // I-D56 §13: Autotap is the player's private setting of this game; the cast flow reads it
    SideEffect { toolkit.cast.autotap = engine.device.settings.autotap }
    // The device's own steps (its passes, assumptions, a live wait turning async) and its local-head reports
    LaunchedEffect(controller, game.id) { while (true) { controller.engineTick(); delay(1_000) } }
    val locked = engine.device.locked
    LaunchedEffect(locked) { if (locked) { toolkit.dismiss(); toolkit.closeInspection() } }
    val back = remember(game.id) { TableBackStateMachine() }
    val overlay = toolkit.hasBackOverlay || state.chatOpen
    BackHandler {
        // §7: Back while behind plays on to the next decision point, never past it
        if (!overlay && engine.device.session.behind) { engine.playToStop(); controller.engineSent(); return@BackHandler }
        when (back.back(SystemClock.elapsedRealtime(), overlay, false)) {
            TableBackAction.CLOSE_OVERLAY -> if (state.chatOpen) controller.closeChat() else toolkit.dismissBackOverlay()
            TableBackAction.PROMPT -> Toast.makeText(context, "Press back again to leave", Toast.LENGTH_SHORT).show()
            TableBackAction.LEAVE -> controller.back()
            TableBackAction.EXIT_REVIEW -> {}
        }
    }
    CompositionLocalProvider(LocalEngineRoutes provides routes) {
        Column(Modifier.fillMaxSize().background(Color(0xfff3f5f5))) {
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                TableSurface(game, play, accountId, landscape, Modifier.fillMaxSize(), connected = interactive && !locked,
                    onIntent = toolkit::handle, toolkit = toolkit,
                    runtime = TableRuntimeHooks(chat = controller::openChat, navigation = hamburger, chatUnread = state.chatUnread,
                        undo = { routes.undo() }, undoHold = { routes.holdUndo() }, undoShakes = routes.undoShake,
                        opponentPresence = state.opponentPresence, gameActionsBlocked = locked,
                        errorMessage = state.connectionMessage ?: state.error, dismissError = controller::dismissSessionError,
                        resultControls = TableResultControls(interactive,
                            reject = { engine.answerSuggestion(false); controller.engineSent() },
                            accept = { engine.answerSuggestion(true); controller.engineSent() }),
                        controlsConnected = interactive && !locked, returnCurrent = if (engine.device.session.behind) ({
                            engine.playToStop(); controller.engineSent() }) else null))
            }
        }
    }
    if (state.chatOpen) {
        Dialog(onDismissRequest = controller::closeChat, properties = DialogProperties(usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false)) {
            TrackGameDialogInput()
            Box(Modifier.fillMaxSize()) {
                ChatPanel(controller, accountId, Modifier.align(Alignment.CenterEnd).fillMaxWidth(if (landscape) .5f else 1f))
            }
        }
    }
}

/** The ending tools on engine state (TRN-046, TRN-064): suggestions are table changes, conceding an engine input. */
private fun engineEnding(routes: EngineTableRoutes, intent: TableActionIntent.Ending): Boolean {
    val game = routes.engine ?: return false
    val me = PlayerId(routes.viewer)
    val acted = when (intent.tool) {
        EndingTool.SUGGEST_WIN -> game.suggest(GameResult.Won(me))
        EndingTool.SUGGEST_LOSS -> game.suggest(GameResult.Won(game.device.opponent))
        EndingTool.SUGGEST_DRAW -> game.suggest(GameResult.Draw)
        EndingTool.ACCEPT -> game.answerSuggestion(true)
        EndingTool.CONCEDE -> game.concede()
    }
    routes.pump()
    return acted is GameDevice.Acted.Sent
}
