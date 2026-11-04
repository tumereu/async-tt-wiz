package org.asyncttwiz.android

import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.border
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Text
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import arcanum.engine.setup.GameResult
import arcanum.engine.state.PlayerId
import kotlinx.coroutines.delay
import org.asyncttwiz.protocol.*
import org.asyncttwiz.protocol.game.GameDevice

/**
 * The table of a game (every game is played on engine state, I-D56): the same table
 * surface, panels and controls as today's, drawn from the engine's state ([EngineGame.play]) and acting through the device
 * core ([EngineTableRoutes]), with the async play panels ([EngineAsyncHost]); the strip and timeline mode show the main
 * line up to the viewer's local head, view-only ([EngineTimeline], HIS-001–HIS-019), the change flashes and the event
 * chip come from the shown events ([EngineGame.Transition], HIS-031, HIS-075).
 */
@Composable
internal fun EngineTableScreen(controller: LobbyController, accountId: String, hamburger: (() -> Unit)?, landscape: Boolean) {
    val state = controller.table.value
    val game = state.detail ?: return
    val live = state.play ?: return
    val engine = controller.engineGame() ?: return
    // What the engine thread published last (I-D56 step D2): the table reads nothing else of the device
    val shown = engine.shown ?: return
    val interactive = game.id !in state.reconnectingGames
    val routes = remember(controller, game.id, accountId) {
        EngineTableRoutes(accountId, { controller.engineGame() }, { controller.engineSent() },
            { controller.table.value.play?.takeIf { it.gameId == game.id } })
    }
    val context = LocalContext.current
    val tokenArt = remember(context) { AndroidTokenArt.of(context.applicationContext) }
    val playId = live.id
    val toolkit = remember(controller, game.id, playId, accountId) {
        TableToolkitController(accountId, { controller.table.value.play?.takeIf { it.id == playId && it.gameId == game.id } },
            { game.id !in controller.table.value.reconnectingGames && routes.canAct() && routes.review == null },
            { action, _ -> routes.local(listOf(action)) },
            ending = { intent -> engineEnding(routes, intent) },
            waitingForConnection = { game.id !in controller.table.value.connectedGames },
            playBatch = { actions, _ -> routes.local(actions) },
            automatumWork = AutomatumWork.Background, tokenArt = tokenArt, engine = routes)
    }
    // I-D43: problem and crash reports carry this game's device bundle while it is the latest open
    DisposableEffect(controller, game.id) {
        AutomatumReports.engine = { controller.engineGame()?.repro() }
        onDispose { }
    }
    // SYS-072: the engine's tokens show the best matching token picture
    SideEffect { engine.tokenArt = tokenArt }
    // LOB-113 (I-D56 §7): a new game starts with the player's defaults, which the server gives its seat from their account
    // I-D56 §13: Autotap is the player's private setting of this game; the cast flow reads it
    SideEffect { toolkit.cast.autotap = shown.settings.autotap }
    // The device's own steps (its passes, assumptions, a live wait turning async) and its local-head reports
    LaunchedEffect(controller, game.id) { while (true) { controller.engineTick(); delay(1_000) } }
    val locked = shown.locked
    LaunchedEffect(locked) { if (locked) { toolkit.dismiss(); toolkit.closeInspection() } }
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
    val reviewPlay = remember(reviewId, engine.version) { reviewId?.let { engine.reviewPlay(it) } }
    LaunchedEffect(reviewId, reviewPlay == null) { if (reviewId != null && reviewPlay == null) routes.leaveReview() }
    val play = reviewPlay ?: live
    SideEffect {
        if (reviewPlay != null) {
            if (applied.reviewed != reviewPlay.currentNodeId) {
                applied.reviewed = reviewPlay.currentNodeId
                changes.applyEngine(reviewPlay.currentNodeId, jump = false, emptyList(), emptySet())
                // HIS-076: in timeline mode every event shown has its chip, yours too
                engine.event(reviewPlay.currentNodeId)?.let { eventText.post(it.id, engine.timeline.text(engine, it, names)) }
            }
        } else {
            applied.reviewed = null
            shown.transition?.takeIf { it.serial != applied.transition }?.let { t ->
                applied.transition = t.serial
                changes.applyEngine(t.cursor, t.jump, t.changes, t.shuffled)
                // HIS-076 (owner playtest 2026-11-04 #5): live, the newest event just shown, yours too (your own action's
                // text fades at the top like the other player's); quiet on open, return and undo
                val told = t.forward.lastOrNull()
                if (told != null) eventText.post(t.cursor, engine.timeline.text(engine, told, names)) else eventText.moved(t.cursor)
            }
        }
    }
    val status = shown.status
    val playing = status is GameDevice.Status.Playing
    // HIS-013 (§27): playback, and a stop its pause made, have the strip and the play/pause button at the bottom
    val playback = playing || (status as? GameDevice.Status.Stopped)?.paused == true
    val back = remember(game.id) { TableBackStateMachine() }
    val overlay = toolkit.hasBackOverlay || state.chatOpen
    BackHandler {
        if (!overlay && routes.review != null) { routes.leaveReview(); return@BackHandler }
        // §7: Back while behind plays on to the next decision point, never past it
        if (!overlay && shown.behind) { engine.playToStop(); controller.engineSent(); return@BackHandler }
        when (back.back(SystemClock.elapsedRealtime(), overlay, false)) {
            TableBackAction.CLOSE_OVERLAY -> if (state.chatOpen) controller.closeChat() else toolkit.dismissBackOverlay()
            TableBackAction.PROMPT -> Toast.makeText(context, "Press back again to leave", Toast.LENGTH_SHORT).show()
            TableBackAction.LEAVE -> controller.back()
            TableBackAction.EXIT_REVIEW -> {}
        }
    }
    val reviewing = reviewPlay != null || playing
    var playbackSettingsOpen by remember(game.id) { mutableStateOf(false) }
    val timeline = engine.timeline.events(engine, names)
    CompositionLocalProvider(LocalEngineRoutes provides routes) {
        Column(Modifier.fillMaxSize().background(Color(0xfff3f5f5))) {
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                TableSurface(game, play, accountId, landscape, Modifier.fillMaxSize(), connected = interactive && !locked && reviewPlay == null,
                    onIntent = toolkit::handle, toolkit = toolkit,
                    runtime = TableRuntimeHooks(chat = controller::openChat, navigation = hamburger, chatUnread = state.chatUnread,
                        undo = { routes.undo() }, undoHold = { routes.holdUndo() }, undoShakes = routes.undoShake,
                        opponentPresence = state.opponentPresence, gameActionsBlocked = locked,
                        errorMessage = state.connectionMessage ?: state.error, dismissError = controller::dismissSessionError,
                        resultControls = TableResultControls(interactive,
                            reject = { engine.answerSuggestion(false); controller.engineSent() },
                            accept = { engine.answerSuggestion(true); controller.engineSent() }),
                        controlsConnected = interactive && !locked && reviewPlay == null,
                        reviewing = reviewing, liveView = !reviewing, changes = changes,
                        waitingLine = routes.waitingLine(play),
                        timelinePlayback = when {
                            reviewPlay != null -> if (routes.reviewPlaying) TimelinePlaybackStatus.PLAYING else TimelinePlaybackStatus.PAUSED
                            playing -> TimelinePlaybackStatus.PLAYING
                            else -> null
                        },
                        history = if (timeline.isNotEmpty()) ({ routes.enterReview(timeline.last().id) }) else null,
                        returnCurrent = when {
                            reviewPlay != null -> ({ routes.leaveReview() })
                            shown.behind -> ({ engine.playToStop(); controller.engineSent() })
                            else -> null
                        },
                        liveTimeline = if (timeline.isEmpty()) null else ({
                            EngineStrip(timeline, accountId, landscape) { routes.enterReview(timeline.last().id) }
                        }),
                        bottomPanel = when {
                            reviewPlay != null && timeline.isNotEmpty() -> ({ _ ->
                                EngineReviewPanel(routes, timeline, accountId, landscape, playbackPause) { playbackSettingsOpen = true }
                            })
                            playback -> ({ _ -> EnginePlaybackPanel(routes, timeline, accountId, landscape) })
                            else -> null
                        }))
                // Over the top row (whose move/step/turn), never taking its taps.
                EventTextOverlay(eventText, playbackPause, Modifier.align(Alignment.TopCenter))
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

/** HIS-001–HIS-004 on engine state: the read-only strip of the events shown to the viewer, newest centred, with the
 * history button that enters timeline mode at its end. */
@Composable
internal fun EngineStrip(events: List<HistoryTimelineEvent>, viewer: String, landscape: Boolean, onEnter: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(TableTimelineHeight.dp)) {
        HistoryTimeline(events, events.last().id, viewer, {}, {}, Modifier.fillMaxWidth(), readOnly = true)
        IconButton(onClick = onEnter, modifier = Modifier.align(Alignment.CenterEnd).background(TimelineBarColor)
            .size(if (landscape) 36.dp else 40.dp)) {
            Icon(Icons.Outlined.History, contentDescription = "Timeline mode")
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
    LaunchedEffect(routes.reviewPlaying, cursor) {
        if (!routes.reviewPlaying) return@LaunchedEffect
        delay(pauseMillis.coerceIn(100L, 10_000L))
        if (at >= events.lastIndex) routes.leaveReview() else routes.review = events[at + 1].id
    }
    val position = HistoryPosition(cursor, HistoryMode.PLAYBACK, routes.reviewPlaying, cursor)
    val strip: @Composable () -> Unit = {
        Box(Modifier.fillMaxWidth().height(TableTimelineHeight.dp)) {
            HistoryTimeline(events, cursor, viewer, { routes.reviewPlaying = false }, { routes.review = it }, Modifier.fillMaxWidth())
            IconButton(onClick = { routes.reviewPlaying = false; onSettings() }, modifier = Modifier.align(Alignment.CenterEnd)
                .background(TimelineBarColor).size(if (landscape) 36.dp else 40.dp)) {
                Icon(Icons.Outlined.Settings, contentDescription = "Playback settings")
            }
        }
    }
    val transport: @Composable () -> Unit = {
        Box(Modifier.fillMaxSize().padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
            HistoryTransport(position, at > 0, at < events.lastIndex, connected = false,
                onPrevious = { routes.reviewPlaying = false; routes.review = events[(at - 1).coerceAtLeast(0)].id },
                onNext = { routes.reviewPlaying = false; routes.review = events[(at + 1).coerceAtMost(events.lastIndex)].id },
                onPlay = { routes.reviewPlaying = true }, onPause = { routes.reviewPlaying = false }, onContinue = {})
        }
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        if (landscape) Row(Modifier.fillMaxWidth().height(tableBottomHeight(true).dp).background(TimelineBarColor),
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).height(TableTimelineHeight.dp)) { strip() }
            Box(Modifier.weight(1f).height(TableControlsHeight.dp)) { transport() }
        } else Column(Modifier.fillMaxWidth().height(tableBottomHeight(false).dp).background(TimelineBarColor)) {
            Box(Modifier.height(TableTimelineHeight.dp)) { strip() }
            Box(Modifier.fillMaxWidth().height(TableControlsHeight.dp)) { transport() }
        }
    }
}

/**
 * Playback on engine state (HIS-001, HIS-013, §27): the strip of the events shown so far, and below it, where timeline
 * mode's Auto is, the play/pause button: a tap pauses (playback goes on to the next point where Full control would stop,
 * where you may act or play on) or plays again; holding it opens the response mode dialog.
 */
@Composable
internal fun EnginePlaybackPanel(routes: EngineTableRoutes, events: List<HistoryTimelineEvent>, viewer: String, landscape: Boolean) {
    val game = routes.engine ?: return
    val shown = game.shown ?: return
    val stop = shown.status as? GameDevice.Status.Stopped
    val playing = shown.status is GameDevice.Status.Playing && !shown.pausing
    val label = when {
        playing -> "Pause"
        stop != null -> "Play"
        else -> "Pausing"
    }
    val strip: @Composable () -> Unit = {
        if (events.isNotEmpty()) EngineStrip(events, viewer, landscape) { routes.enterReview(events.last().id) }
    }
    val button: @Composable () -> Unit = {
        Box(Modifier.fillMaxSize().padding(horizontal = 56.dp), contentAlignment = Alignment.Center) {
            ActionSquare("Playback", "", true, Modifier.fillMaxWidth().height(48.dp).testTag("playback-toggle")
                .background(Color.White, androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                .border(1.dp, MenuBorder, androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                .semantics { stateDescription = label },
                fill = Color.White, ink = Color(0xff253d44), compactContent = true, holdName = "Response mode",
                onHold = { routes.modeDialog = true }, gestureKey = label,
                icon = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(if (playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, null, Modifier.size(20.dp),
                            tint = if (playing) Color(0xffb57d45) else Color(0xff5b896c))
                        Column {
                            Text(label, fontSize = 16.sp, lineHeight = 20.sp)
                            Text("Hold: response mode", fontSize = 10.sp, lineHeight = 12.sp, color = Color(0xff66757a))
                        }
                    }
                }) {
                when {
                    playing -> game.pause(true)
                    stop != null -> game.act(stop.assumed)
                    else -> game.pause(false)
                }
                routes.pump()
            }
        }
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        if (landscape) Row(Modifier.fillMaxWidth().height(tableBottomHeight(true).dp).background(TimelineBarColor),
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).height(TableTimelineHeight.dp)) { strip() }
            Box(Modifier.weight(1f).height(TableControlsHeight.dp)) { button() }
        } else Column(Modifier.fillMaxWidth().height(tableBottomHeight(false).dp).background(TimelineBarColor)) {
            Box(Modifier.height(TableTimelineHeight.dp)) { strip() }
            Box(Modifier.fillMaxWidth().height(TableControlsHeight.dp)) { button() }
        }
    }
}

/** What the engine table applied once: the last transition and the board reviewed. */
private class AppliedMarks { var transition = 0L; var reviewed: String? = null }

/** The ending tools on engine state (TRN-046, TRN-064): suggestions are table changes, conceding an engine input. */
private fun engineEnding(routes: EngineTableRoutes, intent: TableActionIntent.Ending): Boolean {
    val game = routes.engine ?: return false
    val me = PlayerId(routes.viewer)
    val acted = when (intent.tool) {
        EndingTool.SUGGEST_WIN -> game.suggest(GameResult.Won(me))
        EndingTool.SUGGEST_LOSS -> game.suggest(GameResult.Won(game.shown?.opponent ?: return false))
        EndingTool.SUGGEST_DRAW -> game.suggest(GameResult.Draw)
        EndingTool.ACCEPT -> game.answerSuggestion(true)
        EndingTool.CONCEDE -> game.concede()
    }
    routes.pump()
    return acted is GameDevice.Acted.Sent
}
