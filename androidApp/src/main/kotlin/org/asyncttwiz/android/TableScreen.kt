package org.asyncttwiz.android

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
    val pingReference: EventReference? = null)

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
        tree?.let { history.update(it, "${game.revision}/${live?.currentNodeId}") }
    }
    val playback = history.playback
    val position = playback?.position
    LaunchedEffect(position, tree) { history.displayPosition() }
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
        if (!landscape || play == null) TableHeader(game, play, accountId, hamburger,
            chatUnread = state.chat.unread, openChat = controller::openChat)
        if (play == null) {
            Column(Modifier.weight(1f).padding(16.dp)) {
                Text(if (waitingActivity) "Loading authorized history boundary…" else "Loading accepted table…")
                history.error?.let { Text(it) }
                if (waitingActivity) TextButton(onClick = controller::leaveActivityReview) { Text("Return to current") }
                state.connectionMessage?.let { Text(it) }
                TextButton(onClick = controller::reconnect) { Text("Reconnect") }
            }
        } else {
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                TableSurface(game, play, accountId, landscape, Modifier.fillMaxSize(),
                    connected = game.id in state.connectedGames && editable, onIntent = onIntent ?: toolkit::handle, placements = placements,
                    toolkit = toolkit, accepted = state.acceptedPlay.takeIf { history.gate != null },
                    runtime = runtime.copy(chat = runtime.chat ?: controller::openChat,
                        history = { explorerOpen = true }, undo = { controller.historyControl(GameAction.Undo) },
                        redo = { controller.historyControl(GameAction.Redo) }, reviewing = !editable,
                        controlsConnected = game.id in state.connectedGames && history.foreground,
                        pingReference = EventReference(play.id, play.currentNodeId,
                            history.view?.takeIf { it.cursor == play.currentNodeId }?.contextVersion),
                        ping = { intent ->
                            if (!history.foreground || controller.state.detail?.id != game.id ||
                                controller.state.detail?.playId != play.id || intent.reference.playId != play.id ||
                                game.id !in controller.state.connectedGames) false else {
                                var sent = false
                                intent.actions().forEach { action ->
                                    // Ordinary history annotations append at live head; deliberate continuation
                                    // uses the same lowering as every other toolkit request, never a second engine.
                                    sent = (if (history.armed != null) controller.gameplay(action)
                                        else controller.annotate(action)) || sent
                                }
                                sent
                            }
                        }, tokenCatalog = catalogMount))
                if (landscape) TableHeader(game, play, accountId, hamburger,
                    Modifier.width(minOf(300.dp, maxWidth * .46f)).align(Alignment.TopStart),
                    chatUnread = state.chat.unread, openChat = controller::openChat)
            }
            if (playback != null) {
                if (history.armed == null) HistoryPlaybackPanel(playback, timelineEnd ?: playback.tree.sharedHeadId, accountId,
                    game.id in state.connectedGames, 240L, { history.arm(it) },
                    continuationReady = position?.cursorId?.let(history::canArm) == true)
                Row {
                    TextButton(onClick = history::current) { Text("Return to current") }
                    if (history.armed != null) TextButton(onClick = history::cancel) { Text("Cancel continuation") }
                    TextButton(onClick = controller::downloadHistory, enabled = game.id in state.connectedGames) { Text("Download history") }
                }
                HistoryEventActions(history, play, toolkit, game.id in state.connectedGames, controller::annotate)
            }
            if (game.id !in state.connectedGames) Text("Disconnected · read-only", Modifier.padding(horizontal = 12.dp),
                style = MaterialTheme.typography.bodySmall)
            else state.connectionMessage?.let { Text(it, Modifier.padding(horizontal = 12.dp), style = MaterialTheme.typography.bodySmall) }
        }
    }
    if (explorerOpen && tree != null) HistoryExplorer(explorer, game.id in state.connectedGames,
        mapOf(accountId to OwnTableColor, game.inviter.accountId.takeIf { it != accountId }.orEmpty() to OpponentTableColor,
            game.recipient.accountId.takeIf { it != accountId }.orEmpty() to OpponentTableColor),
        showHidden, { showHidden = it }, { explorerOpen = false }, onReview = { reviewed -> history.select(reviewed.currentNodeId) },
        onAdvanced = { advanced = true })
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

@Composable
private fun TableHeader(game: GameSummary, play: PlayState?, accountId: String, hamburger: (() -> Unit)?, modifier: Modifier = Modifier,
    chatUnread: Int, openChat: () -> Unit) {
    Row(modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        if (hamburger != null) IconButton(onClick = hamburger) { EntryIcon("Open navigation", "menu") }
        Column(Modifier.weight(1f)) {
            Text(game.gameName, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
            Text(gameDate(game), style = MaterialTheme.typography.labelSmall)
        }
        BadgedBox(badge = { if (chatUnread > 0) Badge { Text(chatUnread.toString()) } }) {
            TextButton(onClick = openChat, modifier = Modifier.semantics {
                contentDescription = "Chat, $chatUnread unread messages"
            }) { Text("Chat") }
        }
    }
}

internal val OwnTableColor = Color(0xff287f55)
internal val OpponentTableColor = Color(0xffa84f58)

private tailrec fun tableActivity(context: Context): ComponentActivity? = when (context) {
    is ComponentActivity -> context
    is ContextWrapper -> tableActivity(context.baseContext)
    else -> null
}
