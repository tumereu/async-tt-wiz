package org.asyncttwiz.android

import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.ui.platform.testTag
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import arcanum.engine.setup.GameResult
import arcanum.engine.state.PlayerId
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.asyncttwiz.protocol.*
import org.asyncttwiz.protocol.game.GameDevice

/**
 * The table of a game (every game is played on engine state, I-D56): the same table
 * surface, panels and controls as today's, drawn from the engine's state ([EngineGame.play]) and acting through the device
 * core ([EngineTableRoutes]), with the async play panels ([EngineAsyncHost]); timeline mode shows the main
 * line up to the viewer's local head, view-only ([EngineTimeline], HIS-001–HIS-019), the change flashes and the event
 * chip come from the shown events ([EngineGame.Transition], HIS-031, HIS-075).
 */
@Composable
internal fun EngineTableScreen(controller: LobbyController, accountId: String, hamburger: (() -> Unit)?, landscape: Boolean) {
    val state = controller.table.value
    val detail = state.detail ?: return
    val live = state.board ?: return
    // TRN-065: a table staying on after its play ended (until the player's own OK) still draws that play
    val game = if (detail.playId == live.playId) detail else detail.copy(playId = live.playId)
    val engine = controller.engineGame() ?: return
    // What the engine thread published last (I-D56 step D2): the table reads nothing else of the device
    val shown = engine.shown ?: return
    val interactive = game.id !in state.reconnectingGames
    val routes = remember(controller, game.id, accountId) {
        EngineTableRoutes(accountId, { controller.engineGame() }, { controller.engineSent() },
            { controller.table.value.board?.takeIf { it.gameId == game.id } }, { controller.acknowledgeResult() })
    }
    val context = LocalContext.current
    val tokenArt = remember(context) { AndroidTokenArt.of(context.applicationContext) }
    val playId = live.playId
    val toolkit = remember(controller, game.id, playId, accountId) {
        TableToolkitController(accountId, { controller.table.value.board?.takeIf { it.playId == playId && it.gameId == game.id } },
            { game.id !in controller.table.value.reconnectingGames && routes.canAct() && routes.review == null },
            { action, _ -> routes.local(listOf(action)) },
            ending = { intent -> engineEnding(routes, intent) },
            waitingForConnection = { game.id !in controller.table.value.connectedGames },
            playBatch = { actions, requestId -> routes.local(actions, requestId) },
            automatumWork = AutomatumWork.Background, tokenArt = tokenArt, engine = routes,
            // TRN-064: Concede whenever the player is at the table, their move or not
            atTable = { game.id !in controller.table.value.reconnectingGames && routes.review == null })
    }
    // I-D43: problem and crash reports carry this game's device bundle while it is the latest open
    DisposableEffect(controller, game.id) {
        AutomatumReports.engine = { controller.engineGame()?.repro() }
        AutomatumReports.board = { controller.engineGame()?.logBoard() }
        onDispose { }
    }
    // SYS-072: the engine's tokens show the best matching token picture
    SideEffect { engine.tokenArt = tokenArt }
    // LOB-113 (I-D56 §7): a new game starts with the player's defaults, which the server gives its seat from their account
    // I-D56 §13: Autotap is the player's private setting of this game; the cast flow reads it
    SideEffect { toolkit.cast.autotap = shown.settings.autotap }
    // LOB-135: Table actions → Game settings changes the game's assume reveal settings (the host's)
    SideEffect { toolkit.assumeReveals = controller::assumeReveals }
    // The device's own steps (its passes, assumptions, a live wait turning async) and its local-head reports
    // On the main thread's clock while the table is shown, in the background too, as before (LobbyController.engineTicks)
    DisposableEffect(controller, game.id) { controller.engineTick(); val stop = controller.engineTicks(game.id); onDispose { stop() } }
    val locked = shown.locked
    LaunchedEffect(locked) { if (locked) { toolkit.dismiss(); toolkit.peek(null) } }
    val names = remember(game) { mapOf(game.inviter.accountId to game.inviter.username, game.recipient.accountId to game.recipient.username) }
    routes.opponentName = names[shown.opponent.value] ?: "The other player"
    val playbackPreferences = rememberHistoryPlaybackPreferences(accountId)
    val playbackPause by playbackPreferences.pauseState()
    // HIS-075: the event chip
    val eventTextEnabled by rememberEventTextEnabled(accountId).first
    val eventText = remember(game.id) { EventTextFeed({ it.run() }, { it() }) }
    SideEffect { eventText.enabled = eventTextEnabled }
    // HIS-031: the change flashes; HIS-030: the card motion of the shown events (none on a load)
    val changes = remember(game.id, playId) { TableChanges() }
    val applied = remember(game.id, playId) { AppliedMarks() }
    // Timeline mode (HIS-004, HIS-012): the board at the review cursor, view-only
    val reviewId = routes.review
    val reviewNow = remember(reviewId, engine.version) { reviewId?.let { engine.reviewPlay(it) } }
    // HIS-005 (owner 2026-11-07: the timeline reaches the game's first event): a point whose board needs older history
    // keeps the board shown before until its page is in; one that can't be shown leaves timeline mode
    val reviewKept = remember(game.id) { ReviewKept() }
    val reviewPlay = reviewNow ?: reviewKept.board.takeIf { reviewId != null && engine.historyLoading }
    reviewKept.board = if (reviewId == null) null else reviewNow ?: reviewKept.board
    LaunchedEffect(reviewId, reviewNow == null, engine.historyLoading) {
        if (reviewId != null && reviewNow == null && !engine.historyLoading && !engine.loadFor(reviewId)) routes.leaveReview()
    }
    // TBL-040 (owner playtest 2026-11-04): what a published board changed plays step by step, in event order
    val motion = remember(game.id, playId) { EngineMotionQueue() }
    // CMB-040 (owner, 2026-11-05): while waiting for the defender's blocks the attacker's lanes show the blocks placed to assume
    val play = reviewPlay ?: motion.board(shown.version, shown.transition, live).let { b -> routes.assuming()?.board(b) ?: b }
    SideEffect { routes.motionBusy = { motion.playing } }
    // HIS-113: a combat holds the view on the attackers and on the damage, each for the plays' time, once the table shows
    // it (owner playtest 2026-11-09: the damage right after it is dealt, before the creatures it destroys die); a held
    // finger pauses that time; a double tap on the table skips it; timeline mode ends it (EngineAsyncHost)
    val combatHold = shown.combatHold?.takeIf { reviewId == null }
    SideEffect { motion.stopAt = combatHold?.kind }
    val combatHoldMillis by playbackPreferences.showPlaysState()
    if (combatHold != null) LaunchedEffect(combatHold) {
        snapshotFlow { !motion.playing || motion.stoppedAt(combatHold.kind) }.first { it }
        // Problem report QVERHX: when the hold's time starts (the steps reached the attackers or the damage, or ended)
        AppLog.info("game", "Combat hold ${combatHold.kind::class.simpleName} in ${combatHold.event.take(8)}: ${combatHoldMillis} ms" +
            (if (motion.playing) " (steps stopped at the ${if (combatHold.kind == GameDevice.CombatMoment.Damage) "damage dealt" else "attackers"})"
                else " (steps over, the board as it is)") + if (routes.viewPause.paused) ", paused by a finger or Inspect" else "")
        routes.viewPause.wait(combatHoldMillis)
        engine.dismissCombatHold(combatHold); controller.engineSent()
    }
    // HIS-107: an Undo tapped while the player's own action is still being worked out waits for it
    SideEffect { routes.working = { toolkit.flows.busy || toolkit.cast.busy } }
    LaunchedEffect(routes, toolkit) { snapshotFlow { toolkit.flows.busy || toolkit.cast.busy }.collect { if (!it) routes.workDone() } }
    // HIS-097, HIS-022 (owner playtest 2026-11-05): a branch's rewind back to the branch point waits the Rewind pause
    // after each step
    val rewindPause by playbackPreferences.rewindPauseState()
    // HIS-106 (owner 2026-11-08): once the rewind has played, it stops at the branch point as playback stops: the table
    // is live there with the centre text, and Back, the menu and the timeline work again
    val branchRewound = shown.branchStop && !(shown.transition?.undone?.branch == true && motion.playing)
    val takingBack = shown.takingBack && reviewPlay == null && !branchRewound
    val rewindingBranch = takingBack && shown.transition?.undone?.branch == true && motion.playing
    val scope = rememberCoroutineScope()
    SideEffect {
        if (reviewPlay != null) {
            // TRN-074: never a turn banner in timeline mode
            routes.banners.drop()
            shown.turnChange?.let { applied.turnChange = it.serial }
            if (applied.reviewed != reviewPlay.nodeId) {
                applied.reviewed = reviewPlay.nodeId
                changes.applyEngine(reviewPlay.nodeId, jump = false, emptyList(), emptySet())
                // HIS-076: in timeline mode every event shown has its chip, yours too
                engine.event(reviewPlay.nodeId)?.let { eventText.post(it.id, engine.timeline.reviewText(engine, it, names)) }
            }
        } else {
            applied.reviewed = null
            shown.transition?.takeIf { it.serial != applied.transition }?.let { t ->
                applied.transition = t.serial
                EngineTableProbe.applied?.invoke(accountId, t, !applied.drawn)
                // TRN-074: a take-back ends the banner showing
                if (t.undone != null) routes.banners.drop()
                changes.applyEngine(t.cursor, t.jump, t.changes, t.shuffled)
                // HIS-076 (owner playtest 2026-11-04 #5): live, the newest event just shown, yours too (your own action's
                // text fades at the top like the other player's); quiet on open and return. §29.4: an undo says what it
                // took back ("Undid: cast Storm Spark", the other player "Alice undid: …"), even with Event text off
                val told = t.forward.lastOrNull { !EngineEventText.hidden(it) }
                val undone = t.undone
                when {
                    // HIS-097: a branch's take-back has its note instead
                    undone != null && undone.branch -> eventText.moved(t.cursor)
                    undone != null -> eventText.post(t.cursor, EngineEventText.undid(undone.event, undone.state, accountId, undone.by, names),
                        always = true)
                    told != null -> eventText.post(t.cursor, engine.timeline.text(engine, told, names))
                    else -> eventText.moved(t.cursor)
                }
            }
            // TRN-074: at a turn change, live and in catch-up playback, in order with the plays shown; not on opening the game
            shown.turnChange?.takeIf { it.serial != applied.turnChange }?.let { change ->
                applied.turnChange = change.serial
                if (applied.drawn) routes.banners.post(change, shown.show)
            }
            applied.drawn = true
        }
        if (rewindingBranch) EngineTableProbe.rewinding?.invoke(accountId)
    }
    // TRN-075: the move passing to you from the other player: one haptic tick and one pulse of the main button, not in
    // timeline mode
    val moveCue = remember(game.id) { YourMoveCue() }
    val haptic = LocalHapticFeedback.current
    val recordShown = LocalShownTexts.current
    SideEffect {
        val cue = moveCue.see(shown.status, shown.line, shown.tip, shown.show != null || shown.combatHold != null || shown.reveal != null, accountId, SystemClock.uptimeMillis())
        if (cue && reviewPlay == null) {
            routes.moveCue++
            haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
            recordShown(YOUR_MOVE_CUE)
        }
    }
    val status = shown.status
    val playing = status is GameDevice.Status.Playing
    // HIS-094, HIS-095 (owner 2026-11-05): catch-up playback looks like live play: its paused start and a stop its pause
    // made show only the centre text that resumes it; a tap on the table while it runs pauses it
    val catchUp = status == GameDevice.Status.CatchUp
    val pausedStop = (status as? GameDevice.Status.Stopped)?.paused == true
    // HIS-017: holding a card to inspect it stops timeline mode's Auto, and releasing it doesn't resume it (live and in
    // catch-up playback the hold only pauses what the view waits on, [ViewPause])
    val held = toolkit.peek != null
    LaunchedEffect(held) { if (held && routes.review != null) routes.reviewPlaying = false }
    // HIS-023 (owner playtest 2026-11-05): the centre text goes at once when tapped, before the engine thread publishes
    // the playing board
    var watchTappedAt by remember(game.id) { mutableStateOf<Long?>(null) }
    val back = remember(game.id) { TableBackStateMachine() }
    // LOB-145: a pod game's chat has the pod's beside it; its unread messages light the chat button (no bubbles)
    val podChat = LocalPodChat.current
    val podId = game.podId
    if (podChat != null && podId != null) PodChatRefresh(podChat, podId, state.chatOpen, "table")
    val podView = podId?.let { podChat?.views?.get(it) }?.takeIf { !it.unavailable }
    val podUnread = podView?.unread(accountId) ?: 0
    val podAvailable = podChat != null && podId != null && podChat.views[podId]?.unavailable != true
    val overlay = toolkit.hasBackOverlay || state.chatOpen
    BackHandler {
        // HIS-097: a branch's take-back can't be skipped or left
        if (takingBack) return@BackHandler
        if (!overlay && routes.review != null) { routes.leaveReview(); return@BackHandler }
        when (back.back(SystemClock.elapsedRealtime(), overlay, false)) {
            TableBackAction.CLOSE_OVERLAY -> if (state.chatOpen) controller.closeChat() else toolkit.dismissBackOverlay()
            TableBackAction.PROMPT -> Toast.makeText(context, "Press back again to leave", Toast.LENGTH_SHORT).show()
            TableBackAction.LEAVE -> controller.back()
            TableBackAction.EXIT_REVIEW -> {}
        }
    }
    // HIS-008: timeline mode's look, also while a branch's take-back goes back (HIS-106)
    val reviewing = reviewPlay != null || rewindingBranch
    var playbackSettingsOpen by remember(game.id) { mutableStateOf(false) }
    val timeline = engine.timeline.events(engine, names)
    // A point no longer on the timeline (the device let it go) can't be looked at: timeline mode ends
    LaunchedEffect(reviewId, timeline) { if (reviewId != null && timeline.none { it.id == reviewId }) routes.leaveReview() }
    // HIS-075: while the other player makes a choice the event text says so (also with Event text off, never in timeline
    // mode); TBL-118: a refused control's shake meanwhile flashes it
    val choosing = if (reviewPlay == null) routes.choosingText(play) else null
    // TRN-066: what the band says reaches the report, once per change
    val waitLogged = remember(game.id) { java.util.concurrent.atomic.AtomicReference<String?>(null) }
    CompositionLocalProvider(LocalEngineRoutes provides routes, LocalRefusedTap provides toolkit::refusedTap) {
        Column(Modifier.fillMaxSize().background(Color(0xfff3f5f5))) {
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                TableSurface(game, play, accountId, landscape, Modifier.fillMaxSize(), connected = interactive && !locked && reviewPlay == null,
                    onIntent = toolkit::handle, toolkit = toolkit,
                    runtime = TableRuntimeHooks(chat = { controller.openChat(tab = ChatTabs.opening(podAvailable, state.chatUnread, podUnread, controller.lastChatTab(game.id))) },
                        navigation = hamburger.takeIf { !takingBack }, chatUnread = ChatTabs.buttonUnread(state.chatUnread, podUnread),
                        undo = { routes.undo() }, undoHold = { routes.holdUndo() }, undoShakes = routes.undoShake,
                        opponentPresence = state.opponentPresence, gameActionsBlocked = locked,
                        errorMessage = state.connectionMessage ?: state.error, dismissError = controller::dismissSessionError,
                        resultControls = TableResultControls(interactive,
                            reject = { engine.answerSuggestion(false); controller.engineSent() },
                            accept = { engine.answerSuggestion(true); controller.engineSent() }),
                        controlsConnected = interactive && !locked && reviewPlay == null,
                        reviewing = reviewing, liveView = !reviewing, changes = changes,
                        onSceneSettled = if (reviewPlay != null) null else ({ _ ->
                            if (rewindingBranch) scope.launch { delay(rewindPause); motion.settled() } else motion.settled()
                        }),
                        sceneVersion = if (reviewPlay != null) null else motion.key(),
                        waitingLine = routes.waitingLine(play).also { line -> if (waitLogged.getAndSet(line) != line) AppLog.info("game", "Wait: $line") },
                        timelinePlayback = when {
                            rewindingBranch -> TimelinePlaybackStatus.REWINDING
                            reviewPlay != null -> if (routes.reviewPlaying) TimelinePlaybackStatus.PLAYING else TimelinePlaybackStatus.PAUSED
                            else -> null
                        },
                        history = if (timeline.isNotEmpty() && !takingBack) ({ routes.enterReview(timeline.last().id) }) else null,
                        returnCurrent = if (reviewPlay != null) ({ routes.leaveReview() }) else null,
                        bottomPanel = when {
                            reviewPlay != null && timeline.isNotEmpty() -> ({ _ ->
                                EngineReviewPanel(routes, timeline, accountId, landscape, playbackPause) { playbackSettingsOpen = true }
                            })
                            rewindingBranch -> ({ _ -> EngineRewindPanel(landscape) })
                            else -> null
                        },
                        centreAction = if (reviewPlay == null && (catchUp || pausedStop) && !takingBack && watchTappedAt != shown.version)
                            // HIS-094, HIS-106, HIS-108: what waits: a take-back held on the return, or the other player's moves
                            TableCentreAction(if (shown.branchStop) "${routes.opponentName} responded differently from here · Tap to continue"
                            else when (shown.takeBack?.undo) {
                                false -> "${routes.opponentName} responded differently · Tap to watch"
                                true -> "${routes.opponentName} undid moves · Tap to watch"
                                null -> "${routes.opponentName} has made moves · Tap to watch"
                            }) {
                                watchTappedAt = shown.version; engine.startPlayback(); controller.engineSent() }
                            else null,
                        boardTap = if (reviewPlay == null && playing && !shown.pausing && !takingBack) ({
                            engine.pause(); controller.engineSent() }) else null,
                        holdTap = combatHold?.let { hold -> ({ engine.dismissCombatHold(hold, "double tap"); controller.engineSent() }) },
                        // LOB-136, LOB-137: chat bubbles, also in timeline mode (chat isn't game history); the own one
                        // waits while the chat covers the table (portrait)
                        chatBubbles = TableChatBubbles(state.chatBubble, state.chatSent, tableShown = !state.chatOpen || landscape,
                            routes.opponentName, read = { controller.readChatBubble(game.id, it) },
                            open = { controller.openChat(it) }, sentShown = { controller.chatSentShown(game.id, it) }),
                        // LOB-139: quick messages from your own Life panel, sent as any message; Write opens the chat
                        quickChat = TableQuickChat(interactive && game.lifecycle == GameLifecycle.ACTIVE && game.playId != null,
                            send = controller::sendQuickChat, write = { controller.writeChat() })))
                // Over the top row (whose move/step/turn), never taking its taps.
                EventTextOverlay(eventText, playbackPause, Modifier.align(Alignment.TopCenter), status = choosing,
                    flashes = toolkit.choiceFlashes)
                TurnBannerOverlay(routes.banners, shown.show != null, accountId, names, Modifier.align(Alignment.Center))
            }
        }
    }
    // HIS-010, HIS-028: the timeline panel's gear opens the playback settings
    if (playbackSettingsOpen) PlaybackSettingsDialog(playbackPreferences, accountId) { playbackSettingsOpen = false }
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

/**
 * Timeline mode on engine state (HIS-004, HIS-010–HIS-015, HIS-021): the strip (swipe or tap an event to look at the board
 * there), Previous / Auto / Next one event at a time up to the local head; automatic playback reaching the newest event
 * returns to play mode. View-only: no branching (HIS-019).
 */
@Composable
internal fun EngineReviewPanel(routes: EngineTableRoutes, events: List<HistoryTimelineEvent>, viewer: String, landscape: Boolean,
    pauseMillis: Long, onSettings: () -> Unit = {}) {
    val cursor = routes.review ?: return
    val at = events.indexOfFirst { it.id == cursor }.takeIf { it >= 0 } ?: return
    val engine = routes.engine
    // HIS-005 (owner 2026-11-07): coming near the oldest event held asks for older history, back to the game's first event
    LaunchedEffect(at, events.size) { if (at < OLDER_HISTORY_MARGIN) engine?.loadOlder() }
    LaunchedEffect(routes.reviewPlaying, cursor) {
        if (!routes.reviewPlaying) return@LaunchedEffect
        delay(pauseMillis.coerceIn(100L, 10_000L))
        if (at >= events.lastIndex) routes.leaveReview() else routes.review = events[at + 1].id
    }
    val position = HistoryPosition(cursor, HistoryMode.PLAYBACK, routes.reviewPlaying, cursor)
    val strip: @Composable () -> Unit = {
        Box(Modifier.fillMaxWidth().height(TableTimelineHeight.dp)) {
            HistoryTimeline(events, cursor, viewer, { routes.reviewPlaying = false }, { routes.review = it }, Modifier.fillMaxWidth(),
                loadingStart = engine != null && engine.historyLoading && engine.shown?.olderComplete == false)
            IconButton(onClick = { routes.reviewPlaying = false; onSettings() }, modifier = Modifier.align(Alignment.CenterEnd)
                .background(TimelineBarColor).size(if (landscape) 36.dp else 40.dp)) {
                Icon(Icons.Outlined.Settings, contentDescription = "Playback settings")
            }
        }
    }
    val transport: @Composable () -> Unit = {
        Box(Modifier.fillMaxSize().padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
            HistoryTransport(position, at > 0, at < events.lastIndex,
                onPrevious = { routes.reviewPlaying = false; routes.review = events[(at - 1).coerceAtLeast(0)].id },
                onNext = { routes.reviewPlaying = false; routes.review = events[(at + 1).coerceAtMost(events.lastIndex)].id },
                onPlay = { routes.reviewPlaying = true }, onPause = { routes.reviewPlaying = false },
                // HIS-015, HIS-018 (owner, 2026-11-08): at the last event one tap leaves timeline mode, to where you were
                onExit = if (at == events.lastIndex && !routes.reviewPlaying) routes::leaveReview else null)
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        // TBL-009: in landscape your life square, piles and emblems keep the bar's left end (the table's own grey bar
        // lies under them); the strip and Previous / Auto / Next share the rest
        // TBL-009 (owner, 2026-11-08): in the controls' fixed-width place at the bar's right end, your hand left of it
        if (landscape) Row(Modifier.align(Alignment.BottomEnd).width(TablePresentation.Landscape.controls(maxWidth.value).dp)
            .height(tableBottomHeight(true, timeline = true).dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).height(TableTimelineHeight.dp)) { strip() }
            Box(Modifier.weight(1f).height(TableControlsHeight.dp)) { transport() }
        } else Column(Modifier.fillMaxWidth().height(tableBottomHeight(false, timeline = true).dp).background(TimelineBarColor)) {
            Box(Modifier.height(TableTimelineHeight.dp)) { strip() }
            Box(Modifier.fillMaxWidth().height(TableControlsHeight.dp)) { transport() }
        }
    }
}

/**
 * HIS-106: while a branch's take-back goes back to the branch point, the bottom bar has timeline mode's grey look, at play
 * mode's height and without the controls (no event strip, HIS-001).
 */
@Composable
internal fun EngineRewindPanel(landscape: Boolean) {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = if (landscape) Alignment.BottomEnd else Alignment.BottomCenter) {
        // TBL-009: in landscape the controls' fixed-width place at the bar's right end
        Box(Modifier.then(if (landscape) Modifier.width(TablePresentation.Landscape.controls(maxWidth.value).dp) else Modifier.fillMaxWidth())
            .height(tableBottomHeight(landscape).dp).background(TimelineBarColor).testTag("rewind-panel"))
    }
}

/** HIS-005: the last board timeline mode showed, kept while the next point's older history loads. */
private class ReviewKept { var board: TableBoard? = null }

/** What the engine table applied once: the last transition and the board reviewed; [drawn]: the table has drawn a live
 * board of this game (a transition applied later animates from it). */
private class AppliedMarks { var transition = 0L; var reviewed: String? = null; var drawn = false; var turnChange = 0L }

/** Tests: what the engine table did with the boards it was given (main thread, after composition), with its player's
 * account id: each transition it applied, and whether it opened on it ([applied]'s `opened`: no board of the game drawn
 * before, so nothing animates); and each composition drawing a step of a branch's take-back (HIS-106, [rewinding]). */
internal object EngineTableProbe {
    @Volatile var applied: ((me: String, transition: EngineGame.Transition, opened: Boolean) -> Unit)? = null
    @Volatile var rewinding: ((me: String) -> Unit)? = null
}

/** The ending tools on engine state (TRN-046, TRN-064): suggestions are table changes, conceding an engine input. */
private fun engineEnding(routes: EngineTableRoutes, intent: TableActionIntent.Ending): Boolean {
    val game = routes.engine ?: return false
    val me = PlayerId(routes.viewer)
    val acted = when (intent.tool) {
        EndingTool.SUGGEST_WIN -> game.suggest(GameResult.Won(me), intent.note)
        EndingTool.SUGGEST_LOSS -> game.suggest(GameResult.Won(game.shown?.opponent ?: return false), intent.note)
        EndingTool.SUGGEST_DRAW -> game.suggest(GameResult.Draw, intent.note)
        EndingTool.ACCEPT -> game.answerSuggestion(true)
        EndingTool.CONCEDE -> game.concede()
    }
    routes.pump()
    return acted is GameDevice.Acted.Sent
}
