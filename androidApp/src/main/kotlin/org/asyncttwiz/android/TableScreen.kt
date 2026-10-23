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
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Personal panel identity, not catalog/replica authority or an outgoing journal. */
data class TableTokenCatalogIdentity(val accountId: String, val gameId: String, val playId: String?,
    val installedGeneration: String?, val lifecycleEpoch: Long)
data class TableTokenCatalog(val identity: TableTokenCatalogIdentity, val source: TokenSearchSource?,
    val loading: Boolean = false, val error: String? = null)

/** Other runtime owners supply real routes. Null means unavailable, never a successful-looking stub. */
data class TableRuntimeHooks(val chat: (() -> Unit)? = null, val history: (() -> Unit)? = null,
    val undo: (() -> Boolean)? = null, val redo: (() -> Boolean)? = null, val reviewing: Boolean = false,
    val controlsConnected: Boolean? = null,
    val ping: ((EventPingIntent) -> Boolean)? = null, val tokenCatalog: TableTokenCatalog? = null,
    val pingReference: EventReference? = null, val navigation: (() -> Unit)? = null,
    val tree: (() -> Unit)? = null, val continueHere: (() -> Unit)? = null,
    val returnCurrent: (() -> Unit)? = null, val chatUnread: Int = 0,
    val menuContent: (@Composable () -> Unit)? = null, val reviewPanel: (@Composable () -> Unit)? = null,
    val continuationArmed: Boolean = false)

/** The normal table route. No local initialization, entropy or placeholder future action handlers. */
@Composable
internal fun TableScreen(controller: LobbyController, accountId: String, hamburger: (() -> Unit)?, landscape: Boolean,
    onIntent: ((TableIntent) -> Boolean)? = null, placements: Map<String, TablePlacement> = emptyMap(),
    runtime: TableRuntimeHooks = TableRuntimeHooks()) {
    val state = controller.state
    val game = state.detail ?: return
    val live = state.play
    val history = remember(controller, game.id, live?.id, accountId) { controller.history }
    val tree = controller.retainedTree()
    LaunchedEffect(tree, live?.currentNodeId, live?.board, game.revision) {
        tree?.let { history.update(it, "${game.revision}/${live?.currentNodeId}", game, accountId) }
    }
    val playback = history.playback
    val position = playback?.position
    LaunchedEffect(position, tree, playback?.tree) { history.displayPosition() }
    val reviewing = position?.mode == HistoryMode.PLAYBACK && !history.provisional
    val editable = !reviewing || history.armed != null
    var explorerOpen by remember(game.id, live?.id) { mutableStateOf(false) }
    var advanced by remember(game.id, live?.id) { mutableStateOf(false) }
    var showHidden by remember(game.id, live?.id) { mutableStateOf(false) }
    val graphWorker = remember(history) { java.util.concurrent.Executors.newSingleThreadExecutor() }
    val explorer = remember(history) { HistoryExplorerController(graphWorker, { action -> activityPost(action) }, { game.id in controller.state.connectedGames }, { scope, action ->
        controller.state.detail?.playId == scope.playId && controller.historyControl(action)
    }) }
    DisposableEffect(explorer) { onDispose { explorer.close(); graphWorker.shutdown() } }
    LaunchedEffect(tree, explorerOpen, showHidden) {
        if (explorerOpen && tree != null) explorer.update(tree, openingNodeId = position?.cursorId, showHidden = showHidden)
    }
    var appliedActivity by remember(history) { mutableStateOf<ActivityTarget?>(null) }
    val target = state.activityTarget
    val waitingActivity = target?.destination == ActivityDestination.GROUP && target != appliedActivity
    val awaitingIndex = tree != null && live?.currentNodeId == state.acceptedPlay?.currentNodeId &&
        playback?.tree?.sharedHeadId != tree.currentNodeId
    val play = if (waitingActivity) null else if (reviewing || awaitingIndex) history.view?.state else live
    LaunchedEffect(target, tree) {
        if (target?.destination == ActivityDestination.GROUP && target != appliedActivity && target.playId != null && target.boundaryNodeId != null) {
            val captured = CapturedHistoryTarget(accountId, target.gameId, requireNotNull(target.playId), target.groupId, requireNotNull(target.boundaryNodeId))
            controller.reviewHistoryTarget(captured, { controller.state.activityTarget == target }) {
                history.acceptedTarget(it)
                if (it.view.state != null) appliedActivity = target
            }
        }
    }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var catalogLifetime by remember(history) { mutableLongStateOf(0L) }
    DisposableEffect(history, lifecycleOwner) {
        history.updateForeground(lifecycleOwner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED))
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) { history.updateForeground(false); catalogLifetime++ }
            if (event == androidx.lifecycle.Lifecycle.Event.ON_START) { history.updateForeground(true); catalogLifetime++ }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    var timelineEnd by remember(history) { mutableStateOf<String?>(null) }
    LaunchedEffect(position, playback?.tree) {
        if (playback != null && position != null) {
            val endpoint = timelineEnd
            if (position.mode == HistoryMode.LIVE) timelineEnd = playback.tree.sharedHeadId
            else if (endpoint == null || !playback.tree.contains(endpoint) || position.cursorId !in playback.tree.path(endpoint))
                timelineEnd = position.cursorId
        }
    }
    var dismissedResult by remember(history) { mutableStateOf<String?>(null) }
    LaunchedEffect(play?.currentNodeId, state.acceptedPlay, position?.mode, history.armed) {
        if (play != null) {
            withFrameNanos { }
            history.presented(play, controller.state.acceptedPlay,
                history.playback?.position?.mode == HistoryMode.LIVE && history.armed == null)
        }
    }
    val activity = tableActivity(LocalContext.current)
    val retained = remember(activity) { activity?.let { ViewModelProvider(it)[TableToolkitViewModel::class.java] } }
    val catalog = remember(activity) { activity?.let { ViewModelProvider(it)[AuthViewModel::class.java].catalog } }
    val catalogState = catalog?.state
    val catalogSource = remember(catalog) { catalog?.let { model -> RepositoryTokenSearchSource { model.repository } } }
    val catalogGeneration by produceState<String?>(null, catalog, catalogState?.installed, catalogState?.busy, catalogLifetime) {
        value = withContext(Dispatchers.IO) { runCatching { catalog?.repository?.tokenGeneration() }.getOrNull() }
    }
    val catalogMount = TableTokenCatalog(TableTokenCatalogIdentity(accountId, game.id, live?.id, catalogGeneration, catalogLifetime),
        catalogSource, catalogState?.busy == true, catalogState?.takeIf { it.error }?.status)
    val toolkit = remember(controller, game.id, live?.id, accountId) {
        val playId = live?.id
        val create = { TableToolkitController(accountId, {
            val personal = controller.history
            (if (personal.playback?.position?.mode == HistoryMode.PLAYBACK && !personal.provisional) personal.view?.state else controller.state.play)
                ?.takeIf { it.id == playId && it.gameId == game.id }
        }, { history.foreground && game.id in controller.state.connectedGames && (history.playback?.position?.mode != HistoryMode.PLAYBACK || history.armed != null) },
            controller::gameplay, ending = { intent ->
                val target = controller.captureResult()
                if (target == null) false else when (intent.tool) {
                    EndingTool.SUGGEST_WIN -> controller.suggestResult(target, SuggestedResult.WIN)
                    EndingTool.SUGGEST_LOSS -> controller.suggestResult(target, SuggestedResult.LOSS)
                    EndingTool.SUGGEST_DRAW -> controller.suggestResult(target, SuggestedResult.DRAW)
                    EndingTool.ACCEPT -> intent.suggestionId?.takeIf { history.gate?.suggestionId == it }
                        ?.let { controller.acceptResult(ResultAcceptanceTarget(target, it)) } ?: false
                }
            }, acceptedInspection = controller::inspectFace) }
        retained?.obtain(controller, listOf(game.id, playId.orEmpty(), accountId), create) ?: create()
    }
    DisposableEffect(toolkit) { onDispose { if (activity?.isChangingConfigurations != true) retained?.release(toolkit) } }
    SideEffect { toolkit.reviewContext("${target?.boundaryNodeId}/${if (reviewing) position?.cursorId else "live"}") }
    Column(Modifier.fillMaxSize().background(Color(0xfff3f5f5))) {
        if (play == null) {
            TableUnavailable(game.gameName, landscape, Modifier.weight(1f), hamburger,
                message = history.error ?: state.connectionMessage,
                loading = game.id in state.connectedGames && history.error == null,
                retry = controller::reconnect,
                returnCurrent = if (waitingActivity) controller::leaveActivityReview else null)
        } else {
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val menuAnnotations = TableMenuAnnotations(EventReference(play.id, play.currentNodeId,
                    history.view?.takeIf { it.cursor == play.currentNodeId }?.contextVersion)) { intent ->
                    if (!history.foreground || controller.state.detail?.id != game.id ||
                        controller.state.detail?.playId != play.id || intent.reference.playId != play.id ||
                        game.id !in controller.state.connectedGames) false else {
                        var sent = false
                        intent.actions().forEach { action ->
                            sent = (if (history.armed != null) controller.gameplay(action) else controller.annotate(action)) || sent
                        }
                        sent
                    }
                }
                CompositionLocalProvider(LocalTableMenuAnnotations provides menuAnnotations) {
                TableSurface(game, play, accountId, landscape, Modifier.fillMaxSize(),
                    connected = game.id in state.connectedGames && editable, onIntent = onIntent ?: toolkit::handle, placements = placements,
                    toolkit = toolkit, accepted = state.acceptedPlay.takeIf { history.gate != null },
                    runtime = runtime.copy(chat = runtime.chat ?: controller::openChat,
                        navigation = hamburger, chatUnread = state.chat.unread,
                        history = playback?.let { player -> { history.select(player.position.cursorId) } },
                        tree = { explorerOpen = true },
                        continueHere = if (reviewing && history.armed == null && game.id in state.connectedGames &&
                            history.foreground && history.canRequestContinuation(position.cursorId)) ({ history.requestContinuation(position.cursorId) }) else null,
                        returnCurrent = if (reviewing || history.armed != null) history::current else null,
                        menuContent = {
                            if (history.armed != null) DropdownMenuItem(text = { Text("Cancel continuation") }, onClick = history::cancelContinuation)
                            DropdownMenuItem(text = { Text("Download history") }, onClick = controller::downloadHistory,
                                enabled = game.id in state.connectedGames)
                            if (reviewing) HistoryEventActions(history, play, toolkit, game.id in state.connectedGames, controller::annotate)
                        }, reviewPanel = if (playback != null && reviewing && history.armed == null) ({
                            HistoryPlaybackPanel(playback, timelineEnd ?: playback.tree.sharedHeadId, accountId,
                                game.id in state.connectedGames, 240L, { history.requestContinuation(it) },
                                continuationReady = position?.cursorId?.let(history::canArm) == true,
                                overlay = true, landscape = landscape, menuContent = {
                                    HistoryEventActions(history, play, toolkit, game.id in state.connectedGames, controller::annotate)
                                    DropdownMenuItem(text = { Text("Download history") }, onClick = controller::downloadHistory,
                                        enabled = game.id in state.connectedGames)
                                })
                        }) else null, undo = { controller.historyControl(GameAction.Undo) },
                        redo = { controller.historyControl(GameAction.Redo) }, reviewing = !editable,
                        continuationArmed = history.armed != null,
                        controlsConnected = game.id in state.connectedGames && history.foreground,
                        pingReference = menuAnnotations.reference, ping = menuAnnotations.ping, tokenCatalog = catalogMount))
                if (!history.provisional) history.armed?.let { boundary ->
                    HistoryArmedIndicator(playback?.tree?.texts?.get(boundary)?.label ?: "selected event",
                        landscape, history::cancelContinuation)
                }
                }
            }
            if (game.id !in state.connectedGames) Text("Disconnected · read-only", Modifier.padding(horizontal = 12.dp),
                style = MaterialTheme.typography.bodySmall)
            else state.connectionMessage?.let { Text(it, Modifier.padding(horizontal = 12.dp), style = MaterialTheme.typography.bodySmall) }
        }
    }
    history.continuationTarget?.let { target ->
        HistoryContinueConfirmation(game.id in state.connectedGames && history.foreground && history.canArm(target),
            history::dismissContinuation, { history.confirmContinuation() })
    }
    if (explorerOpen && tree != null) HistoryExplorer(explorer, game.id in state.connectedGames,
        mapOf(accountId to OwnTableColor, game.inviter.accountId.takeIf { it != accountId }.orEmpty() to OpponentTableColor,
            game.recipient.accountId.takeIf { it != accountId }.orEmpty() to OpponentTableColor),
        showHidden, { showHidden = it }, { explorerOpen = false }, onReview = { reviewed -> history.select(reviewed.currentNodeId) },
        onAdvanced = { advanced = true }, texts = playback?.tree?.texts.orEmpty())
    if (advanced && tree != null) HistoryAdvanced(history, tree, position?.cursorId ?: tree.currentNodeId,
        game, accountId, landscape, game.id in state.connectedGames, { advanced = false })
    if (history.gate?.suggestionId != dismissedResult) ResultAcceptanceDialog(game, state.acceptedPlay, accountId,
        history.gate, game.id in state.connectedGames,
        dismiss = { dismissedResult = history.gate?.suggestionId }, accept = { controller.acceptResult(it) })
    if (state.chatOpen) {
        // A modal panel owns focus/IME/Back; the drawer cannot obscure viewed rows.
        Dialog(onDismissRequest = controller::closeChat, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize()) {
                ChatPanel(controller, accountId, Modifier.align(Alignment.CenterEnd)
                    .fillMaxWidth(if (landscape) .5f else 1f))
            }
        }
    }
}

private fun activityPost(action: () -> Unit) { android.os.Handler(android.os.Looper.getMainLooper()).post { action() } }

internal fun tablePingTarget(play: PlayState, item: TableItem?): PingTarget? = when {
    item == null -> null
    item.cardId != null && play.board.cards.any { it.id == item.cardId } -> PingTarget(PingTargetKind.CARD, item.cardId)
    item.cardId != null && (play.board.stackEffects.any { it.id == item.cardId } ||
        CombatStackRules.occurrences(play.board.combat).any { it.id == item.cardId }) -> PingTarget(PingTargetKind.OCCURRENCE, item.cardId)
    item.owner != null && item.kind in setOf(TableItemKind.LIFE, TableItemKind.COUNTER) -> PingTarget(PingTargetKind.PLAYER, item.owner)
    play.board.containers.any { it.id == item.id } -> PingTarget(PingTargetKind.AREA, item.id)
    else -> null
}

internal val OwnTableColor = Color(0xff237d68)
internal val OpponentTableColor = Color(0xffb44853)

private tailrec fun tableActivity(context: Context): ComponentActivity? = when (context) {
    is ComponentActivity -> context
    is ContextWrapper -> tableActivity(context.baseContext)
    else -> null
}
