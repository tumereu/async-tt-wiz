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

/** Personal panel identity, not catalog/replica authority or an outgoing journal. */
data class TableTokenCatalogIdentity(val accountId: String, val gameId: String, val playId: String?,
    val installedGeneration: String?, val lifecycleEpoch: Long)
data class TableTokenCatalog(val identity: TableTokenCatalogIdentity, val source: TokenSearchSource?,
    val loading: Boolean = false, val error: String? = null, internal val favorites: TokenFavoritesSource? = null)

/** The production table mount shares startup/import's process snapshot, including live generation changes. */
@Composable
internal fun rememberTableTokenCatalog(accountId: String, gameId: String, playId: String?, lifecycleEpoch: Long,
    state: CatalogUiState? = null, favorites: TokenFavoritesSource? = null): TableTokenCatalog {
    val context = LocalContext.current.applicationContext
    val source = remember(context) { AndroidTokenCatalog.source(context) }
    val snapshot by source.snapshots.collectAsState()
    return TableTokenCatalog(TableTokenCatalogIdentity(accountId, gameId, playId, snapshot?.page?.generation, lifecycleEpoch),
        source, error = state?.takeIf { it.error }?.status, favorites = favorites)
}

/** Other runtime owners supply real routes. Null means unavailable, never a successful-looking stub. */
data class TableRuntimeHooks(val chat: (() -> Unit)? = null, val history: (() -> Unit)? = null,
    val undo: (() -> Boolean)? = null, val redo: (() -> Boolean)? = null, val reviewing: Boolean = false,
    val controlsConnected: Boolean? = null,
    val ping: ((EventPingIntent) -> Boolean)? = null, val tokenCatalog: TableTokenCatalog? = null,
    val pingReference: EventReference? = null, val navigation: (() -> Unit)? = null,
    val continueHere: (() -> Unit)? = null,
    val returnCurrent: (() -> Unit)? = null, val chatUnread: Int = 0,
    val reviewPanel: (@Composable () -> Unit)? = null, val liveTimeline: (@Composable () -> Unit)? = null,
    val bottomPanel: (@Composable (@Composable () -> Unit) -> Unit)? = null,
    val continuationArmed: Boolean = false, val errorMessage: String? = null, val dismissError: (() -> Unit)? = null,
    val onSceneSettled: ((String) -> Unit)? = null, val sceneVersion: String? = null,
    val cancelContinuation: (() -> Unit)? = null, val pauseForInspection: (() -> Unit)? = null,
    val liveView: Boolean = !reviewing, val attentionEvent: GameplayMutation? = null,
    val changes: TableChanges? = null)

/** The normal table route. No local initialization, entropy or placeholder future action handlers. */
@Composable
internal fun TableScreen(controller: LobbyController, accountId: String, hamburger: (() -> Unit)?, landscape: Boolean,
    onIntent: ((TableIntent) -> Boolean)? = null, placements: Map<String, TablePlacement> = emptyMap(),
    runtime: TableRuntimeHooks = TableRuntimeHooks()) {
    val state = controller.state
    val game = state.detail ?: return
    val interactive = game.id !in state.reconnectingGames
    val live = state.play
    val history = remember(controller, game.id, live?.id, accountId) { controller.history }
    val playbackPreferences = rememberHistoryPlaybackPreferences(accountId)
    val tree = controller.retainedTree()
    val displayTree = controller.displayTree()
    LaunchedEffect(IdentityKey(displayTree), live?.currentNodeId, IdentityKey(live?.board), game.revision) {
        history.openFrom(playbackPreferences.lastSeen(game.id), playbackPreferences::shown)
        displayTree?.let { history.update(it, "${game.revision}/${live?.currentNodeId}", game, accountId,
            accepted = tree ?: it, indexed = controller.retainedHistory()) }
    }
    DisposableEffect(history) { onDispose { history.leaving() } }
    val playback = history.playback
    val position = playback?.position
    LaunchedEffect(position, IdentityKey(tree), playback?.tree, playback?.livePanelReveal) { history.displayPosition() }
    val reviewing = position?.mode == HistoryMode.PLAYBACK && !history.provisional
    val editable = !reviewing || history.armed != null
    var explorerOpen by remember(game.id, live?.id) { mutableStateOf(false) }
    var explorerOpeningCursor by remember(game.id, live?.id) { mutableStateOf<String?>(null) }
    var showHidden by remember(game.id, live?.id) { mutableStateOf(true) }
    val playbackPause by playbackPreferences.pauseState()
    val playbackAutoPause by playbackPreferences.autoPauseState()
    var playbackSettingsOpen by remember(history) { mutableStateOf(false) }
    var settledCursor by remember(history, position?.cursorId, position?.displayVersion) { mutableStateOf<String?>(null) }
    val graphWorker = remember(history) { java.util.concurrent.Executors.newSingleThreadExecutor() }
    val explorer = remember(history) { HistoryExplorerController(graphWorker, { action -> activityPost(action) }) }
    DisposableEffect(explorer) { onDispose { explorer.close(); graphWorker.shutdown() } }
    LaunchedEffect(IdentityKey(tree), history.explorerHistory, explorerOpen, showHidden) {
        // The live timeline includes provisional nodes; the explorer's review targets
        // remain accepted. A not-yet-accepted live cursor is not missing downloaded history.
        if (explorerOpen && tree != null && history.explorerHistory?.nodes === tree.nodes) {
            explorer.update(tree, openingNodeId = explorerOpeningCursor, showHidden = showHidden, indexed = history.explorerHistory)
            explorerOpeningCursor = null
        }
    }
    var appliedActivity by remember(history) { mutableStateOf<ActivityTarget?>(null) }
    val target = state.activityTarget
    val waitingActivity = target?.destination == ActivityDestination.GROUP && target != appliedActivity
    val awaitingIndex = displayTree != null && displayTree === tree && live?.currentNodeId == state.acceptedPlay?.currentNodeId &&
        playback?.tree?.sharedHeadId != displayTree.currentNodeId
    val endpoint = remember(history) { TableEndpointPresentation() }
    val play = if (waitingActivity || playback == null) null else if (reviewing) history.view?.state
        else endpoint.live(live, history.view?.state, awaitingIndex,
            position?.cursorId?.takeIf { playback.livePanelReveal })
    SideEffect {
        endpoint.presented(play)
        play?.let {
            history.showChanges(it.currentNodeId)
            history.changes.feedback.present(it,
                stopped = reviewing && position?.playing == false && position.cursorId == it.currentNodeId,
                nodes = playback?.tree?.nodes.orEmpty())
        }
    }
    var loadTimedOut by remember(game.id) { mutableStateOf(false) }
    var loadAttempt by remember(game.id) { mutableIntStateOf(0) }
    LaunchedEffect(game.id, play == null, game.id in state.connectedGames, loadAttempt) {
        loadTimedOut = false
        if (play == null) { delay(30_000); loadTimedOut = true }
    }
    LivePanelRevealPacing(playback, playbackPause,
        settledCursor == position?.cursorId && play?.currentNodeId == position?.cursorId)
    LaunchedEffect(target, IdentityKey(tree)) {
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
            else if (playback.tree.isAncestor(position.cursorId, playback.tree.sharedHeadId))
                timelineEnd = playback.tree.sharedHeadId
            else if (endpoint == null || !playback.tree.isAncestor(position.cursorId, endpoint)) timelineEnd = position.cursorId
        }
    }
    var dismissedResult by remember(history) { mutableStateOf<String?>(null) }
    LaunchedEffect(play?.currentNodeId, IdentityKey(state.acceptedPlay), position?.mode, history.armed) {
        if (play != null) {
            withFrameNanos { }
            history.presented(play, controller.state.acceptedPlay,
                history.playback?.position?.mode == HistoryMode.LIVE && history.armed == null)
        }
    }
    val activity = tableActivity(LocalContext.current)
    val retained = remember(activity) { activity?.let { ViewModelProvider(it)[TableToolkitViewModel::class.java] } }
    val catalog = remember(activity) { activity?.let { ViewModelProvider(it)[AuthViewModel::class.java].catalog } }
    val favorites = remember(activity, accountId) { activity?.let {
        val auth = ViewModelProvider(it)[AuthViewModel::class.java].controller
        AccountTokenFavoritesSource(it, accountId, auth::sessionToken)
    } }
    val catalogMount = rememberTableTokenCatalog(accountId, game.id, live?.id, catalogLifetime, catalog?.state, favorites)
    val toolkit = remember(controller, game.id, live?.id, accountId) {
        val playId = live?.id
        val create = { TableToolkitController(accountId, {
            val personal = controller.history
            (if (personal.playback?.position?.mode == HistoryMode.PLAYBACK && !personal.provisional) personal.view?.state else controller.state.play)
                ?.takeIf { it.id == playId && it.gameId == game.id }
        }, { history.foreground && game.id !in controller.state.reconnectingGames && (history.playback?.position?.mode != HistoryMode.PLAYBACK || history.armed != null) },
            controller::gameplay, ending = { intent ->
                val target = controller.captureResult()
                if (target == null) false else when (intent.tool) {
                    EndingTool.SUGGEST_WIN -> controller.suggestResult(target, SuggestedResult.WIN)
                    EndingTool.SUGGEST_LOSS -> controller.suggestResult(target, SuggestedResult.LOSS)
                    EndingTool.SUGGEST_DRAW -> controller.suggestResult(target, SuggestedResult.DRAW)
                    EndingTool.ACCEPT -> intent.suggestionId?.takeIf { history.gate?.suggestionId == it }
                        ?.let { controller.acceptResult(ResultAcceptanceTarget(target, it)) } ?: false
                }
            }, acceptedInspection = controller::inspectFace,
                waitingForConnection = { game.id !in controller.state.connectedGames }) }
        retained?.obtain(controller, listOf(game.id, playId.orEmpty(), accountId), create) ?: create()
    }
    DisposableEffect(toolkit) { onDispose { if (activity?.isChangingConfigurations != true) retained?.release(toolkit) } }
    SideEffect { toolkit.reviewContext("${target?.boundaryNodeId}/${if (reviewing) position?.cursorId else "live"}") }
    val back = remember(game.id) { TableBackStateMachine() }
    val context = LocalContext.current
    val selection = toolkit.selectionState.value
    SideEffect { DebugJank.state("screen" to "table", "history_nodes" to (tree?.nodes?.size ?: 0).toString()) }
    DisposableEffect(Unit) { onDispose { DebugJank.state("screen" to "games", "table_mode" to "play",
        "history_nodes" to "0", "visible_cards" to "0", "popup" to "none", "dragging" to "false") } }
    val overlay = toolkit.hasBackOverlay || explorerOpen || history.continuationTarget != null || state.chatOpen
    LaunchedEffect(overlay, reviewing, history.armed, selection) {
        if (overlay || reviewing || history.armed != null || selection.mode != TableMode.NORMAL || selection.ids.isNotEmpty()) back.reset()
    }
    BackHandler {
        when (back.back(SystemClock.elapsedRealtime(), overlay, reviewing || history.armed != null,
            selection.mode != TableMode.NORMAL || selection.ids.isNotEmpty())) {
            TableBackAction.CLOSE_OVERLAY -> when {
                state.chatOpen -> controller.closeChat()
                history.continuationTarget != null -> history.dismissContinuation()
                explorerOpen -> explorerOpen = false
                else -> toolkit.dismissBackOverlay()
            }
            // Back abandons personal review immediately rather than playing the path back to live.
            TableBackAction.EXIT_REVIEW -> history.cancelContinuation()
            TableBackAction.CLEAR_SELECTION -> toolkit.selectionState.value = TableSelection()
            TableBackAction.PROMPT -> Toast.makeText(context, "Press back again to leave", Toast.LENGTH_SHORT).show()
            TableBackAction.LEAVE -> controller.back()
        }
    }
    Column(Modifier.fillMaxSize().background(Color(0xfff3f5f5))) {
        if (play == null) {
            TableUnavailable(game.gameName, landscape, Modifier.weight(1f), hamburger,
                message = history.error ?: state.connectionMessage ?: if (loadTimedOut) "Connection timed out." else null,
                loading = tableIsLoading(state, game.id, history.error, loadTimedOut),
                retry = { loadAttempt++; controller.reconnect() },
                returnCurrent = if (waitingActivity) controller::leaveActivityReview else null)
        } else {
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val menuAnnotations = TableMenuAnnotations(EventReference(play.id, play.currentNodeId,
                    history.view?.takeIf { it.cursor == play.currentNodeId }?.contextVersion)) { intent ->
                    if (!history.foreground || controller.state.detail?.id != game.id ||
                        controller.state.detail?.playId != play.id || intent.reference.playId != play.id ||
                        game.id in controller.state.reconnectingGames) false else {
                        var sent = false
                        intent.actions().forEach { action ->
                            sent = (if (history.armed != null) controller.gameplay(action) else controller.annotate(action)) || sent
                        }
                        sent
                    }
                }
                CompositionLocalProvider(LocalTableMenuAnnotations provides menuAnnotations) {
                TableSurface(game, play, accountId, landscape, Modifier.fillMaxSize(),
                    connected = interactive && editable, onIntent = onIntent ?: toolkit::handle, placements = placements,
                    toolkit = toolkit, accepted = state.acceptedPlay.takeIf { history.gate != null },
                     runtime = runtime.copy(chat = runtime.chat ?: controller::openChat,
                          liveView = position?.mode == HistoryMode.LIVE, changes = history.changes,
                         attentionEvent = displayTree?.nodes?.get(play.currentNodeId),
                         pauseForInspection = { playback?.pause() },
                        errorMessage = state.connectionMessage ?: state.error, dismissError = controller::dismissSessionError,
                        navigation = hamburger, chatUnread = state.chat.unread,
                         history = playback?.let { player -> { history.select(player.tree.sharedHeadId) } },
                          onSceneSettled = { settledCursor = it; playback?.finishAnimation(it) }, sceneVersion = position?.displayVersion,
                         continueHere = if (reviewing && history.armed == null && interactive &&
                            history.foreground && history.canRequestContinuation(position.cursorId)) ({ history.requestContinuation(position.cursorId) }) else null,
                         returnCurrent = if (reviewing || history.armed != null) ({ history.requestExitReview(interactive) }) else null,
                         cancelContinuation = history::cancelContinuation,
                          bottomPanel = if (playback != null && history.armed == null) ({ playControls ->
                            HistoryPlaybackPanel(playback, timelineEnd ?: playback.tree.sharedHeadId, accountId,
                                   interactive, playbackPause, { history.requestContinuation(it) },
                                    autoPause = playbackAutoPause, board = play.board, definitions = play.definitions,
                                  onSettings = { playbackSettingsOpen = true },
                                 animationFinished = settledCursor == position?.cursorId && play.currentNodeId == position?.cursorId,
                                  onTree = {
                                      playback.pause()
                                       tree?.let {
                                           explorerOpeningCursor = it.resolveCursor(playback.position.cursorId)
                                           explorer.opening(explorerOpeningCursor)
                                          explorerOpen = true
                                      }
                                  },
                                 continuationReady = position?.cursorId?.let(history::canRequestContinuation) == true,
                                 overlay = true, landscape = landscape,
                                  background = tableBarColor(play.board, accountId, reviewing = !editable),
                                  readOnly = !reviewing, onEnter = { history.select(playback.tree.sharedHeadId) },
                                   playControls = playControls)
                        }) else null, undo = { controller.historyControl(GameAction.Undo) },
                        redo = { controller.historyControl(GameAction.Redo) }, reviewing = !editable,
                        continuationArmed = history.armed != null,
                         controlsConnected = interactive && history.foreground,
                        pingReference = menuAnnotations.reference, ping = menuAnnotations.ping, tokenCatalog = catalogMount))
                }
            }
        }
    }
    if (playbackSettingsOpen) PlaybackSettingsDialog(playbackPreferences, accountId) { playbackSettingsOpen = false }
    history.continuationTarget?.let { target ->
        HistoryContinueConfirmation(interactive && history.foreground && history.canArm(target),
             history::dismissContinuation, { history.confirmContinuation() }, history::current)
    }
    if (explorerOpen && tree != null) HistoryExplorer(explorer,
        mapOf(accountId to OwnTableColor, game.inviter.accountId.takeIf { it != accountId }.orEmpty() to OpponentTableColor,
            game.recipient.accountId.takeIf { it != accountId }.orEmpty() to OpponentTableColor),
        showHidden, { showHidden = it }, { explorerOpen = false }, onReview = { reviewed -> history.select(reviewed.currentNodeId) },
        texts = playback?.tree?.texts.orEmpty())
    if (history.gate?.suggestionId != dismissedResult) ResultAcceptanceDialog(game, state.acceptedPlay, accountId,
        history.gate, interactive,
        dismiss = { dismissedResult = history.gate?.suggestionId }, accept = { controller.acceptResult(it) })
    if (state.chatOpen) {
        // A modal panel owns focus/IME/Back; the drawer cannot obscure viewed rows.
        Dialog(onDismissRequest = controller::closeChat, properties = DialogProperties(
            usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            Box(Modifier.fillMaxSize()) {
                ChatPanel(controller, accountId, Modifier.align(Alignment.CenterEnd)
                    .fillMaxWidth(if (landscape) .5f else 1f))
            }
        }
    }
}

/** Absence of an accepted head is normal during socket admission and initial catch-up, not failure. */
internal fun tableIsLoading(state: LobbyState, gameId: String, historyError: String?, timedOut: Boolean = false) =
    historyError == null && state.connectionMessage == null && gameId !in state.reconnectingGames && !timedOut

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
