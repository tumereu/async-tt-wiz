package org.asyncttwiz.android

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.asyncttwiz.protocol.HistoryIcon

data class HistoryTimelineEvent(val id: String, val icon: HistoryIcon, val description: String,
    val author: String?, val groupId: String?, val alternatives: Int = 0, val label: String = "Change")

object HistoryTimelineLayout {
    fun events(tree: HistoryTree, endpoint: String): List<HistoryTimelineEvent> {
        return IndexedHistoryEvents(tree, endpoint)
    }
    internal class IndexedHistoryEvents(private val tree: HistoryTree, endpoint: String) : AbstractList<HistoryTimelineEvent>() {
        var projectedEvents = 0; private set
        private val path = tree.path(endpoint)
        override val size get() = path.size
        fun positionOf(id: String): Int = if (tree.contains(id) && path.getOrNull(tree.depth(id)) == id) tree.depth(id) else -1
        override fun get(index: Int): HistoryTimelineEvent {
            projectedEvents++
            val id = path[index]
            val mutation = tree.nodes[id]
            val text = tree.texts[id] ?: historyText(mutation)
            return HistoryTimelineEvent(id, mutation?.grouping?.icon ?: if (mutation == null) HistoryIcon.FLAG else
                org.asyncttwiz.protocol.HistoryGrouping.classify(mutation.action, tree.containers, mutation.transition).icon,
                text.description,
                mutation?.author, tree.group(id)?.id, tree.alternatives(id), text.label)
        }
    }
    fun x(index: Int, selected: Int, width: Float, pitch: Float) =
        x(index, selected.toFloat(), width, pitch)
    fun x(index: Int, center: Float, width: Float, pitch: Float) =
        width / 2f + (index - center) * pitch
    fun selectedAfterDrag(start: Int, delta: Float, pitch: Float, count: Int): Int {
        require(count > 0 && pitch > 0)
        return (start - (delta / pitch).roundToInt()).coerceIn(0, count - 1)
    }
    fun visible(count: Int, selected: Int, width: Float, pitch: Float): IntRange {
        require(count > 0 && selected in 0 until count && width >= 0 && pitch > 0)
        val radius = (width / (2f * pitch)).toInt() + 1
        return maxOf(0, selected - radius)..minOf(count - 1, selected + radius)
    }
}

/** Dense path window with a fixed center selection. The padding is real empty space at endpoints,
 * never fabricated nodes or an extended path line. onPause runs before any touch-driven exact selection. */
@Composable
fun HistoryTimeline(events: List<HistoryTimelineEvent>, selectedId: String, viewer: String,
    onPause: () -> Unit, onSelect: (String) -> Unit, modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    eventIcon: @Composable (HistoryIcon) -> Unit = { HistoryEventIcon(it) }) {
    val positions = remember(IdentityKey(events)) { if (events is HistoryTimelineLayout.IndexedHistoryEvents) null else events.withIndex().associate { it.value.id to it.index } }
    val selected = (events as? HistoryTimelineLayout.IndexedHistoryEvents)?.positionOf(selectedId) ?: positions?.get(selectedId) ?: -1
    require(selected >= 0)
    val pause by rememberUpdatedState(onPause); val select by rememberUpdatedState(onSelect)
    val selectedNow by rememberUpdatedState(selected)
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val pitch = 24f
    // Panning the live strip is presentation only: never scrub the board/review cursor.
    val liveCenter = remember(readOnly) { Animatable(selected.toFloat()) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(readOnly, selectedId, events.last().id) {
        if (readOnly) liveCenter.animateTo(selected.toFloat(), tween(180))
    }
    val windowCenter = if (readOnly) liveCenter.value.coerceIn(0f, events.lastIndex.toFloat()) else selected.toFloat()
    BoxWithConstraints(modifier.fillMaxWidth().height(TableTimelineHeight.dp).clipToBounds()
        .then(if (readOnly) Modifier else Modifier.pointerInput(Unit) {
        awaitEachGesture { awaitFirstDown(requireUnconsumed = false); pause(); waitForUpOrCancellation() }
    }).pointerInput(IdentityKey(events), density, readOnly) {
        var start = 0f; var delta = 0f
        detectHorizontalDragGestures(onDragStart = {
            if (!readOnly) pause()
            start = if (readOnly) liveCenter.value else selectedNow.toFloat(); delta = 0f
        }) { change, amount ->
            change.consume(); delta += amount / density
            if (readOnly) scope.launch { liveCenter.snapTo((start - delta / pitch).coerceIn(0f, events.lastIndex.toFloat())) }
            else select(events[HistoryTimelineLayout.selectedAfterDrag(start.roundToInt(), delta, pitch, events.size)].id)
        }
    }) {
        val width = maxWidth.value
        Box(Modifier.fillMaxSize().semantics {
            contentDescription = if (readOnly) "Event strip" else events[selected].description
            if (!readOnly) {
                progressBarRangeInfo = ProgressBarRangeInfo(selected.toFloat(), 0f..events.lastIndex.toFloat(), maxOf(0, events.size - 2))
                setProgress { value -> pause(); select(events[value.roundToInt().coerceIn(events.indices)].id); true }
                customActions = listOf(
                    CustomAccessibilityAction("Previous event") { pause(); select(events[maxOf(0, selected - 1)].id); true },
                    CustomAccessibilityAction("Next event") { pause(); select(events[minOf(events.lastIndex, selected + 1)].id); true })
            }
        })
        Canvas(Modifier.fillMaxSize()) {
            val first = HistoryTimelineLayout.x(0, windowCenter, size.width / density, pitch).dp.toPx()
            val last = HistoryTimelineLayout.x(events.lastIndex, windowCenter, size.width / density, pitch).dp.toPx()
            if (first != last) drawLine(Color.Gray.copy(alpha = .3f), Offset(maxOf(0f, first), center.y),
                Offset(minOf(size.width, last), center.y), 1.dp.toPx())
            val selectionX = HistoryTimelineLayout.x(selected, windowCenter, size.width / density, pitch).dp.toPx()
            drawRect(Color(0xff287f85), Offset(selectionX - 11.dp.toPx(), center.y - 12.dp.toPx()),
                Size(22.dp.toPx(), 24.dp.toPx()), style = Stroke(1.5.dp.toPx()))
        }
        for (i in HistoryTimelineLayout.visible(events.size, windowCenter.roundToInt(), width, pitch)) {
            val event = events[i]; val x = HistoryTimelineLayout.x(i, windowCenter, width, pitch)
            if (abs(x - width / 2f) > width / 2f + 12f) continue
            val authorColor = historyAuthorColor(event.author, viewer)
            if (event.alternatives > 0) Canvas(Modifier.offset(x.dp).width(24.dp).fillMaxHeight()) {
                // Time runs left to right: fork at this event, then continue in a parallel lane.
                val fork = Offset(0f, center.y)
                val corner = Offset(0f, 5.dp.toPx())
                val edge = Color(0xff98abaa)
                drawLine(edge, fork, corner, 1.dp.toPx())
                drawLine(edge, corner, Offset(size.width, corner.y), 1.dp.toPx())
            }
            TimelineEventChip(event, authorColor, i == selected,
                Modifier.align(Alignment.CenterStart).offset((x - 10f).dp), eventIcon, describeOnTap = readOnly) {
                pause(); select(event.id)
            }
            if (i > 0 && event.groupId != events[i - 1].groupId) Canvas(Modifier.offset((x - 12f).dp).width(1.dp).fillMaxHeight()) {
                drawLine(Color.Gray.copy(alpha = .25f), Offset(0f, 4.dp.toPx()), Offset(0f, size.height - 4.dp.toPx()), 1.dp.toPx())
            }
        }
    }
}

/** A recognized hold describes the event and consumes the tap; it never moves the review cursor. */
@Composable
private fun TimelineEventChip(event: HistoryTimelineEvent, color: Color, selected: Boolean, modifier: Modifier,
    eventIcon: @Composable (HistoryIcon) -> Unit, describeOnTap: Boolean, onClick: () -> Unit) {
    var held by remember(event.id) { mutableStateOf(false) }
    var tapped by remember(event.id) { mutableStateOf(false) }
    var tapSerial by remember(event.id) { mutableIntStateOf(0) }
    LaunchedEffect(tapSerial) {
        if (tapSerial > 0) { delay(1_650); tapped = false }
    }
    val alpha by animateFloatAsState(if (held || tapped) 1f else 0f, tween(150), label = "Event description")
    val click by rememberUpdatedState(onClick)
    val tap = { if (describeOnTap) { tapped = true; tapSerial++ } else click() }
    HistoryEventChip(event, color, selected, modifier.semantics { onClick { tap(); true } }
        .pointerInput(event.id, describeOnTap) {
            detectTapGestures(onPress = { tapped = false; try { tryAwaitRelease() } finally { held = false } },
                onTap = { tap() }, onLongPress = { held = true })
        }) { icon ->
        eventIcon(icon)
        if (alpha > 0f) {
            val gap = with(androidx.compose.ui.platform.LocalDensity.current) { 8.dp.roundToPx() }
            androidx.compose.ui.window.Popup(popupPositionProvider = remember(gap) { HoldTooltipPosition(gap) }) {
                Surface(Modifier.graphicsLayer { this.alpha = alpha }, color = MenuInk, contentColor = Color.White,
                    shape = MaterialTheme.shapes.extraSmall) {
                    Text(event.description, Modifier.widthIn(max = 240.dp).padding(8.dp), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

internal fun historyAuthorColor(author: String?, viewer: String) = when (author) {
    null -> Color(0xff59656a); viewer -> Color(0xff287f85); else -> Color(0xffa84f58)
}

/** Same 20dp glyph and 24dp pitch vocabulary in the path and full-tree views. */
@Composable
internal fun HistoryEventChip(event: HistoryTimelineEvent, color: Color, selected: Boolean, modifier: Modifier = Modifier,
    eventIcon: @Composable (HistoryIcon) -> Unit = { HistoryEventIcon(it) }) {
    Box(modifier.size(20.dp).semantics { contentDescription = event.description; this.selected = selected },
        contentAlignment = Alignment.Center) {
        CompositionLocalProvider(LocalContentColor provides color) { eventIcon(event.icon) }
    }
}

/** Only remote panel flips need a paced endpoint while the ordinary table remains LIVE. */
@Composable
internal fun LivePanelRevealPacing(playback: HistoryPlayback?, pauseMillis: Long, animationFinished: Boolean) {
    val latestPauseMillis by rememberUpdatedState(pauseMillis)
    LaunchedEffect(playback, playback?.position, playback?.livePanelReveal, playback?.foreground, animationFinished) {
        if (playback?.livePanelReveal == true && playback.foreground && animationFinished) {
            delay(latestPauseMillis)
            playback.finishPanelReveal()
        }
    }
}

/** Ready-to-host native history chrome. The host resolves position.cursorId/version into the shared
 * replayed board, forwards lifecycle/accepted updates to playback, and reports completed animations.
 * Each tick selects one group endpoint; receipt of a whole network batch never skips those stages. */
@Composable
fun HistoryPlaybackPanel(playback: HistoryPlayback, timelineEndpoint: String, viewer: String, connected: Boolean,
    pauseMillis: Long, onContinue: (String) -> Unit, modifier: Modifier = Modifier,
    continuationReady: Boolean = true, overlay: Boolean = false, landscape: Boolean = false,
    animationFinished: Boolean = true, onTree: (() -> Unit)? = null,
    autoPause: Set<PlaybackAutoPause> = PlaybackAutoPause.defaults,
    board: org.asyncttwiz.protocol.BoardState? = null, onSettings: (() -> Unit)? = null,
    definitions: List<org.asyncttwiz.protocol.SubmittedDefinition> = emptyList(),
    background: Color = TimelineBarColor, readOnly: Boolean = false, onEnter: () -> Unit = {},
    playControls: (@Composable () -> Unit)? = null, playCursorId: String = playback.tree.sharedHeadId,
    rewindPauseMillis: Long = HistoryPlaybackPreferences.DEFAULT_REWIND_PAUSE_MILLIS) {
    require(pauseMillis > 0)
    val position = playback.position
    val stripCursor = if (readOnly) playCursorId else position.cursorId
    // Timeline mode: the whole visible line (live line, or the old branch the cursor is on) to its tip.
    val visibleTip = playback.visibleTip
    val endpoint = when {
        !readOnly -> visibleTip
        playback.tree.isAncestor(stripCursor, timelineEndpoint) -> timelineEndpoint
        playback.tree.isAncestor(stripCursor, playback.tree.sharedHeadId) -> playback.tree.sharedHeadId
        else -> stripCursor
    }
    val events = remember(playback.tree, endpoint) { HistoryTimelineLayout.events(playback.tree, endpoint) }
    val continuation by rememberUpdatedState(onContinue)
    val latestPauseMillis by rememberUpdatedState(pauseMillis)
    val latestRewindPauseMillis by rememberUpdatedState(rewindPauseMillis)
    val latestAutoPause by rememberUpdatedState(autoPause)
    val latestBoard by rememberUpdatedState(board)
    val latestDefinitions by rememberUpdatedState(definitions)
    LaunchedEffect(readOnly, position.cursorId, position.displayVersion, position.playing, playback.foreground,
        animationFinished) {
        if (!readOnly && playback.foreground && animationFinished) {
            if (playback.finishAnimation(position.cursorId)) return@LaunchedEffect
            if (!position.playing) return@LaunchedEffect
            if (playback.pauseAfterAnimation()) return@LaunchedEffect
            // Sample once per completed animation: a setting change affects the next pause,
            // not a pause already in progress. A manual start steps at once.
            delay(playback.nextStepDelayMillis(latestPauseMillis, latestRewindPauseMillis))
            playback.next(automatic = true, viewer = viewer, autoPause = latestAutoPause, board = latestBoard, definitions = latestDefinitions)
        }
    }
    val timeline: @Composable () -> Unit = {
        Box(Modifier.fillMaxWidth().height(TableTimelineHeight.dp)) {
            HistoryTimeline(events, stripCursor, viewer, playback::pauseFromStrip, playback::scrubFromStrip,
                Modifier.fillMaxWidth(), readOnly = readOnly)
            Row(Modifier.align(Alignment.CenterEnd).background(background)) {
                if (readOnly) IconButton(onClick = onEnter,
                    modifier = Modifier.size(if (landscape) 36.dp else 40.dp)) {
                    Icon(Icons.Outlined.History, contentDescription = "Timeline mode")
                } else {
                    if (onSettings != null) IconButton(onClick = { playback.pause(); onSettings() },
                        enabled = !playback.transportLocked,
                        modifier = Modifier.size(if (landscape) 36.dp else 40.dp)) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Playback settings")
                    }
                    if (onTree != null) IconButton(onClick = { playback.pause(); onTree() },
                        enabled = !playback.transportLocked,
                        modifier = Modifier.size(if (landscape) 36.dp else 40.dp).semantics { contentDescription = "Open game tree" }) {
                        TableChromeIcon("tree")
                    }
                }
            }
        }
    }
    val transport: @Composable () -> Unit = {
        HistoryTransport(position, position.cursorId != playback.tree.rootId,
            position.cursorId != visibleTip, connected && continuationReady,
            playback::previous, { playback.next() }, playback::play, playback::pause, { captured ->
                if (connected && playback.canContinue(captured)) continuation(captured)
            }, controlsEnabled = !playback.transportLocked, branchPending = playback.branched,
            offLiveLine = playback.offLiveLine)
    }
    val panel: @Composable () -> Unit = {
        val timelineRow: @Composable () -> Unit = {
            Box(Modifier.height(TableTimelineHeight.dp)) { timeline() }
        }
        val controlsRow: @Composable () -> Unit = {
            Crossfade(readOnly, Modifier.fillMaxWidth().height(TableControlsHeight.dp), animationSpec = tween(200), label = "Table controls") { live ->
                if (live) playControls?.invoke()
                else Box(Modifier.fillMaxSize().padding(horizontal = 8.dp), contentAlignment = Alignment.Center) { transport() }
            }
        }
        val barColor by animateColorAsState(background, tween(200), label = "Timeline bar")
        if (landscape) Row(Modifier.fillMaxWidth().height(tableBottomHeight(true).dp).background(barColor), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { timelineRow() }
            Box(Modifier.weight(1f)) { controlsRow() }
        } else Column(Modifier.fillMaxWidth().height(tableBottomHeight(false).dp).background(barColor)) {
            timelineRow(); controlsRow()
        }
    }
    if (overlay) Box(modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) { panel() }
    else Box(modifier) { panel() }
}

/** Literal glyph vocabulary, also replaceable with the host's existing event icon renderer. Every table below is an
 * exhaustive `when` over [HistoryIcon]: a new icon does not compile until each draws it. [HistoryIcon.UNKNOWN]
 * (a name from a newer server) draws as a neutral dot. */
@Composable
fun HistoryEventIcon(icon: HistoryIcon) {
    val vector = historyIconVector(icon)
    if (vector == null) Icon(painterResource(R.drawable.swords_outlined), null, Modifier.size(20.dp))
    else Icon(vector, null, Modifier.size(20.dp))
}

/** Null only for [HistoryIcon.SWORDS], drawn from the swords_outlined drawable. */
internal fun historyIconVector(icon: HistoryIcon): androidx.compose.ui.graphics.vector.ImageVector? = when (icon) {
    HistoryIcon.FLAG -> Icons.Outlined.Flag
    HistoryIcon.ROTATE_90_DEGREES_CW -> Icons.Outlined.Rotate90DegreesCw
    HistoryIcon.FAVORITE -> Icons.Outlined.FavoriteBorder
    HistoryIcon.OPEN_WITH -> Icons.Outlined.OpenWith
    HistoryIcon.CHAT_BUBBLE -> Icons.AutoMirrored.Outlined.Chat
    HistoryIcon.ADS_CLICK -> Icons.Outlined.AdsClick
    HistoryIcon.DOWNLOAD -> Icons.Outlined.Download
    HistoryIcon.LAYERS -> Icons.Outlined.Layers
    HistoryIcon.VISIBILITY -> Icons.Outlined.Visibility
    HistoryIcon.ADD_BOX -> Icons.Outlined.AddBox
    HistoryIcon.CONTROL_POINT_DUPLICATE -> Icons.Outlined.ControlPointDuplicate
    HistoryIcon.EXPOSURE_PLUS_1 -> Icons.Outlined.ExposurePlus1
    HistoryIcon.SWITCH_ACCOUNT -> Icons.Outlined.SwitchAccount
    HistoryIcon.LAYERS_CLEAR -> Icons.Outlined.LayersClear
    HistoryIcon.CASINO -> Icons.Outlined.Casino
    HistoryIcon.OUTLINED_FLAG -> Icons.Outlined.OutlinedFlag
    HistoryIcon.EMOJI_EVENTS -> Icons.Outlined.EmojiEvents
    HistoryIcon.STICKY_NOTE_2 -> Icons.AutoMirrored.Outlined.StickyNote2
    HistoryIcon.DELETE -> Icons.Outlined.DeleteOutline
    HistoryIcon.SHUFFLE -> Icons.Outlined.Shuffle
    HistoryIcon.PAGEVIEW -> Icons.Outlined.Pageview
    HistoryIcon.CLOSE -> Icons.Outlined.Close
    HistoryIcon.MONETIZATION_ON -> Icons.Outlined.MonetizationOn
    HistoryIcon.SETTINGS_BACKUP_RESTORE -> Icons.Outlined.SettingsBackupRestore
    HistoryIcon.FLIP_CAMERA_ANDROID -> Icons.Outlined.FlipCameraAndroid
    HistoryIcon.FLIP -> Icons.Outlined.Flip
    HistoryIcon.FLIP_TO_FRONT -> Icons.Outlined.FlipToFront
    HistoryIcon.LINK -> Icons.Outlined.Link
    HistoryIcon.LINK_OFF -> Icons.Outlined.LinkOff
    HistoryIcon.REMOVE_CIRCLE_OUTLINE -> Icons.Outlined.RemoveCircleOutline
    HistoryIcon.FORMAT_INK_HIGHLIGHTER -> Icons.Outlined.BorderColor
    HistoryIcon.INBOX -> Icons.Outlined.Inbox
    HistoryIcon.SWAP_HORIZ -> Icons.Outlined.SwapHoriz
    HistoryIcon.SPACE_DASHBOARD -> Icons.Outlined.SpaceDashboard
    HistoryIcon.DELETE_SWEEP -> Icons.Outlined.DeleteSweep
    HistoryIcon.BLOCK -> Icons.Outlined.Block
    HistoryIcon.LIBRARY_BOOKS -> Icons.AutoMirrored.Outlined.LibraryBooks
    HistoryIcon.HEART_BROKEN -> Icons.Outlined.HeartBroken
    HistoryIcon.EXPOSURE_NEG_1 -> Icons.Outlined.ExposureNeg1
    HistoryIcon.EXPOSURE_ZERO -> Icons.Outlined.ExposureZero
    HistoryIcon.ARROW_CIRCLE_UP -> Icons.Outlined.ArrowCircleUp
    HistoryIcon.ARROW_CIRCLE_DOWN -> Icons.Outlined.ArrowCircleDown
    HistoryIcon.TRENDING_UP -> Icons.AutoMirrored.Outlined.TrendingUp
    HistoryIcon.TRENDING_DOWN -> Icons.AutoMirrored.Outlined.TrendingDown
    HistoryIcon.LOCAL_FIRE_DEPARTMENT -> Icons.Outlined.LocalFireDepartment
    HistoryIcon.HEALING -> Icons.Outlined.Healing
    HistoryIcon.ARROW_FORWARD -> Icons.AutoMirrored.Outlined.ArrowForward
    HistoryIcon.SKIP_NEXT -> Icons.Outlined.SkipNext
    HistoryIcon.VISIBILITY_OFF -> Icons.Outlined.VisibilityOff
    HistoryIcon.CHECK -> Icons.Outlined.Check
    HistoryIcon.GPS_FIXED -> Icons.Outlined.GpsFixed
    HistoryIcon.SWORDS -> null
    HistoryIcon.UNKNOWN -> Icons.Outlined.FiberManualRecord
}
internal fun historyIconGlyph(icon: HistoryIcon): String = when (icon) {
    HistoryIcon.FLAG -> "⚑"; HistoryIcon.ROTATE_90_DEGREES_CW -> "↻"; HistoryIcon.FAVORITE -> "♥"; HistoryIcon.OPEN_WITH -> "↔"
    HistoryIcon.CHAT_BUBBLE -> "▤"; HistoryIcon.ADS_CLICK -> "◎"; HistoryIcon.DOWNLOAD -> "⇩"; HistoryIcon.LAYERS -> "▱"
    HistoryIcon.VISIBILITY -> "◉"; HistoryIcon.ADD_BOX -> "⊞"; HistoryIcon.CONTROL_POINT_DUPLICATE -> "⊕"; HistoryIcon.SWORDS -> "⚔"
    HistoryIcon.EXPOSURE_PLUS_1 -> "+1"; HistoryIcon.SWITCH_ACCOUNT -> "⇄"; HistoryIcon.LAYERS_CLEAR -> "⊠"; HistoryIcon.CASINO -> "⚄"
    HistoryIcon.OUTLINED_FLAG -> "⚐"; HistoryIcon.EMOJI_EVENTS -> "♜"; HistoryIcon.STICKY_NOTE_2 -> "▧"; HistoryIcon.DELETE -> "×"
    HistoryIcon.SHUFFLE -> "⤨"; HistoryIcon.PAGEVIEW -> "▣"; HistoryIcon.CLOSE -> "✕"; HistoryIcon.MONETIZATION_ON -> "◉"
    HistoryIcon.SETTINGS_BACKUP_RESTORE -> "↺"; HistoryIcon.FLIP_CAMERA_ANDROID -> "▣"; HistoryIcon.FLIP -> "▧"
    HistoryIcon.FLIP_TO_FRONT -> "▤"; HistoryIcon.LINK -> "∞"; HistoryIcon.LINK_OFF -> "∦"; HistoryIcon.REMOVE_CIRCLE_OUTLINE -> "⊖"
    HistoryIcon.FORMAT_INK_HIGHLIGHTER -> "▰"; HistoryIcon.INBOX -> "⊔"; HistoryIcon.SWAP_HORIZ -> "⇄"
    HistoryIcon.SPACE_DASHBOARD -> "▦"; HistoryIcon.DELETE_SWEEP -> "⌫"; HistoryIcon.BLOCK -> "⊘"; HistoryIcon.LIBRARY_BOOKS -> "▥"
    HistoryIcon.HEART_BROKEN -> "♡"; HistoryIcon.EXPOSURE_NEG_1 -> "−1"; HistoryIcon.EXPOSURE_ZERO -> "0"
    HistoryIcon.ARROW_CIRCLE_UP -> "⇑"; HistoryIcon.ARROW_CIRCLE_DOWN -> "⇓"; HistoryIcon.TRENDING_UP -> "↗"
    HistoryIcon.TRENDING_DOWN -> "↘"; HistoryIcon.LOCAL_FIRE_DEPARTMENT -> "♨"; HistoryIcon.HEALING -> "✚"
    HistoryIcon.ARROW_FORWARD -> "→"; HistoryIcon.SKIP_NEXT -> "⇥"; HistoryIcon.VISIBILITY_OFF -> "◌"; HistoryIcon.CHECK -> "✓"
    HistoryIcon.GPS_FIXED -> "⌖"
    HistoryIcon.UNKNOWN -> "•"
}

@Composable
fun HistoryTransport(position: HistoryPosition, previousEnabled: Boolean, nextEnabled: Boolean, connected: Boolean,
    onPrevious: () -> Unit, onNext: () -> Unit, onPlay: () -> Unit, onPause: () -> Unit,
    onContinue: (String) -> Unit, modifier: Modifier = Modifier, controlsEnabled: Boolean = true,
    branchPending: Boolean = false, offLiveLine: Boolean = branchPending) {
    // Auto always heads for the live game: off the live line that starts with a rewind to the split point.
    val rewind = branchPending || offLiveLine
    val current by rememberUpdatedState(position)
    val continueAction by rememberUpdatedState(onContinue)
    val play by rememberUpdatedState(onPlay)
    val pause by rememberUpdatedState(onPause)
    val toggle = { if (controlsEnabled) { if (current.playing) pause() else play() } }
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onPrevious, modifier = Modifier.size(48.dp).semantics { contentDescription = "Previous" },
                enabled = previousEnabled && controlsEnabled && !branchPending) { TableChromeIcon("previous") }
            run {
                var captured by remember { mutableStateOf<String?>(null) }
                // A pending opponent branch rewinds before playing on; branching here is not offered.
                val canBranch = connected && controlsEnabled && !branchPending
                Surface(Modifier.weight(1f).heightIn(min = 48.dp).pressFeedback().semantics {
                    role = Role.Button; contentDescription = "Auto advance"
                    stateDescription = if (rewind && !position.playing) "Rewind to the branch point, then play"
                        else if (position.playing) "Auto: on" else "Auto: off"
                    if (!controlsEnabled) disabled() else onClick { toggle(); true }
                    if (canBranch) customActions = listOf(CustomAccessibilityAction("Continue playing from here") {
                        val cursor = current.cursorId
                        pause(); continueAction(cursor); true
                    })
                }.pointerInput(canBranch, branchPending, controlsEnabled) {
                    if (!controlsEnabled) return@pointerInput
                    detectTapGestures(onPress = { captured = current.cursorId; tryAwaitRelease() },
                        // A pending branch swallows a hold silently: neither branch nor toggle.
                        onTap = { toggle() }, onLongPress = if (branchPending) ({ _ -> }) else if (!canBranch) null else ({
                            pause()
                            captured?.let { continueAction(it) } // Host validates this original boundary; no fallback substitution.
                         }))
                }, color = Color.White, contentColor = Color(0xff253d44), border = BorderStroke(1.dp, MenuBorder)) {
                    Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (rewind) Icon(Icons.Outlined.FastRewind, null,
                            Modifier.size(20.dp).semantics { testTag = "Rewind and play" }, tint = Color(0xffa84f58))
                        else Icon(if (position.playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, null,
                            Modifier.size(20.dp), tint = if (position.playing) Color(0xffb57d45) else Color(0xff5b896c))
                        // A pending branch can only be rewound; the player just chooses when it starts.
                        val showsRewind = rewind && !position.playing
                        Column {
                            Text(if (showsRewind) "Rewind" else if (position.playing) "Auto: on" else "Auto: off",
                                fontSize = 16.sp, lineHeight = 20.sp)
                            // HIS-013: holding Auto is the only way to branch, so the button says so.
                            if (!showsRewind) Text("Hold to branch", fontSize = 10.sp, lineHeight = 12.sp, color = Color(0xff66757a))
                        }
                    }
                }
            }
            IconButton(onNext, modifier = Modifier.size(48.dp).semantics { contentDescription = "Next" },
                enabled = nextEnabled && controlsEnabled && !branchPending) { TableChromeIcon("next") }
        }
    }
}
