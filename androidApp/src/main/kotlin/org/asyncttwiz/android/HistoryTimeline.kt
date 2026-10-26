package org.asyncttwiz.android

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
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

data class HistoryTimelineEvent(val id: String, val icon: String, val description: String,
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
            return HistoryTimelineEvent(id, mutation?.grouping?.icon ?: if (mutation == null) "flag" else
                org.asyncttwiz.protocol.HistoryGrouping.classify(mutation.action, tree.containers, mutation.transition).icon,
                text.description,
                mutation?.author, tree.group(id)?.id, tree.alternatives(id), text.label)
        }
    }
    fun x(index: Int, selected: Int, width: Float, pitch: Float) =
        width / 2f + (index - selected) * pitch
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
    readOnly: Boolean = false, onEnter: () -> Unit = {},
    eventIcon: @Composable (String) -> Unit = { HistoryEventIcon(it) }) {
    val positions = remember(IdentityKey(events)) { if (events is HistoryTimelineLayout.IndexedHistoryEvents) null else events.withIndex().associate { it.value.id to it.index } }
    val selected = (events as? HistoryTimelineLayout.IndexedHistoryEvents)?.positionOf(selectedId) ?: positions?.get(selectedId) ?: -1
    require(selected >= 0)
    val pause by rememberUpdatedState(onPause); val select by rememberUpdatedState(onSelect)
    val selectedNow by rememberUpdatedState(selected)
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val pitch = 24f
    BoxWithConstraints(modifier.fillMaxWidth().height(TableTimelineHeight.dp).clipToBounds()
        .then(if (readOnly) Modifier.pressFeedback().clickable(
            onClickLabel = "Open timeline", onClick = onEnter) else Modifier.pointerInput(Unit) {
        awaitEachGesture { awaitFirstDown(requireUnconsumed = false); pause(); waitForUpOrCancellation() }
    }.pointerInput(IdentityKey(events), density) {
        var start = selectedNow; var delta = 0f
        detectDragGestures(onDragStart = { pause(); start = selectedNow; delta = 0f }) { change, amount ->
            change.consume(); delta += amount.x / density
            select(events[HistoryTimelineLayout.selectedAfterDrag(start, delta, pitch, events.size)].id)
        }
    })) {
        val width = maxWidth.value
        Box(Modifier.fillMaxSize().semantics {
            contentDescription = if (readOnly) "Open timeline" else events[selected].description
            if (!readOnly) {
                progressBarRangeInfo = ProgressBarRangeInfo(selected.toFloat(), 0f..events.lastIndex.toFloat(), maxOf(0, events.size - 2))
                setProgress { value -> pause(); select(events[value.roundToInt().coerceIn(events.indices)].id); true }
                customActions = listOf(
                    CustomAccessibilityAction("Previous event") { pause(); select(events[maxOf(0, selected - 1)].id); true },
                    CustomAccessibilityAction("Next event") { pause(); select(events[minOf(events.lastIndex, selected + 1)].id); true })
            }
        })
        Canvas(Modifier.fillMaxSize()) {
            val first = HistoryTimelineLayout.x(0, selected, size.width / density, pitch).dp.toPx()
            val last = HistoryTimelineLayout.x(events.lastIndex, selected, size.width / density, pitch).dp.toPx()
            if (first != last) drawLine(Color.Gray.copy(alpha = .3f), Offset(maxOf(0f, first), center.y),
                Offset(minOf(size.width, last), center.y), 1.dp.toPx())
            drawRect(Color(0xff287f85), Offset(center.x - 11.dp.toPx(), center.y - 12.dp.toPx()),
                Size(22.dp.toPx(), 24.dp.toPx()), style = Stroke(1.5.dp.toPx()))
        }
        for (i in HistoryTimelineLayout.visible(events.size, selected, width, pitch)) {
            val event = events[i]; val x = HistoryTimelineLayout.x(i, selected, width, pitch)
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
                Modifier.align(Alignment.CenterStart).offset((x - 10f).dp), eventIcon) {
                if (readOnly) onEnter() else { pause(); select(event.id) }
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
    eventIcon: @Composable (String) -> Unit, onClick: () -> Unit) {
    var tooltip by remember(event.id) { mutableStateOf(false) }
    val click by rememberUpdatedState(onClick)
    HistoryEventChip(event, color, selected, modifier.semantics { onClick { click(); true } }
        .pointerInput(event.id) {
            detectTapGestures(onPress = { try { tryAwaitRelease() } finally { tooltip = false } },
                onTap = { click() }, onLongPress = { tooltip = true })
        }) { icon ->
        eventIcon(icon)
        if (tooltip) HoldTooltip(event.description)
    }
}

internal fun historyAuthorColor(author: String?, viewer: String) = when (author) {
    null -> Color(0xff59656a); viewer -> Color(0xff287f85); else -> Color(0xffa84f58)
}

/** Same 20dp glyph and 24dp pitch vocabulary in the path and full-tree views. */
@Composable
internal fun HistoryEventChip(event: HistoryTimelineEvent, color: Color, selected: Boolean, modifier: Modifier = Modifier,
    eventIcon: @Composable (String) -> Unit = { HistoryEventIcon(it) }) {
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
    menuContent: (@Composable () -> Unit)? = null, animationFinished: Boolean = true, onTree: (() -> Unit)? = null,
    autoPause: Set<PlaybackAutoPause> = PlaybackAutoPause.defaults,
    board: org.asyncttwiz.protocol.BoardState? = null, onSettings: (() -> Unit)? = null,
    definitions: List<org.asyncttwiz.protocol.SubmittedDefinition> = emptyList(),
    background: Color = TimelineBarColor, readOnly: Boolean = false, onEnter: () -> Unit = {},
    playControls: (@Composable () -> Unit)? = null) {
    require(pauseMillis > 0)
    val position = playback.position
    val endpoint = when {
        readOnly -> playback.tree.sharedHeadId
        playback.tree.isAncestor(position.cursorId, timelineEndpoint) -> timelineEndpoint
        playback.tree.isAncestor(position.cursorId, playback.tree.sharedHeadId) -> playback.tree.sharedHeadId
        else -> position.cursorId
    }
    val events = remember(playback.tree, endpoint) { HistoryTimelineLayout.events(playback.tree, endpoint) }
    val continuation by rememberUpdatedState(onContinue)
    val latestPauseMillis by rememberUpdatedState(pauseMillis)
    val latestAutoPause by rememberUpdatedState(autoPause)
    val latestBoard by rememberUpdatedState(board)
    val latestDefinitions by rememberUpdatedState(definitions)
    val branchAlpha = remember(playback) { androidx.compose.animation.core.Animatable(1f) }
    LaunchedEffect(playback, playback.branched) {
        if (playback.branched) {
            branchAlpha.snapTo(1f)
            delay(1_500)
            branchAlpha.animateTo(0f, androidx.compose.animation.core.tween(150))
            playback.acknowledgeBranch()
        }
    }
    LaunchedEffect(position.cursorId, position.displayVersion, position.playing, playback.foreground,
        animationFinished, playback.branched) {
        if (playback.foreground && animationFinished && !playback.branched) {
            if (playback.finishAnimation(position.cursorId)) return@LaunchedEffect
            if (!position.playing) return@LaunchedEffect
            if (playback.pauseAfterAnimation()) return@LaunchedEffect
            // Sample once per completed animation: a setting change affects the next pause,
            // not a pause already in progress.
            delay(playback.stepPauseMillis(latestPauseMillis))
            playback.next(automatic = true, viewer = viewer, autoPause = latestAutoPause, board = latestBoard, definitions = latestDefinitions)
        }
    }
    val timeline: @Composable () -> Unit = {
        var options by remember { mutableStateOf(false) }
        Row(Modifier.height(TableTimelineHeight.dp)
            .then(if (readOnly) Modifier.clickable(onClickLabel = "Open timeline", onClick = onEnter) else Modifier),
            verticalAlignment = Alignment.CenterVertically) {
            HistoryTimeline(events, if (readOnly) playback.tree.sharedHeadId else position.cursorId, viewer, { if (!playback.transportLocked) playback.pause() },
                { if (!playback.transportLocked) playback.scrub(it) },
                Modifier.weight(1f), readOnly = readOnly, onEnter = onEnter)
            Row(Modifier.graphicsLayer { alpha = if (readOnly) 0f else 1f }
                .then(if (readOnly) Modifier.clearAndSetSemantics {} else Modifier)) {
                if (onSettings != null) IconButton(onClick = { playback.pause(); onSettings() },
                    enabled = !readOnly && !playback.transportLocked,
                    modifier = Modifier.size(if (landscape) 36.dp else 40.dp)) {
                    Icon(Icons.Outlined.Settings, contentDescription = "Playback settings")
                }
                if (onTree != null) IconButton(onClick = { playback.pause(); onTree() },
                    enabled = !readOnly && !playback.transportLocked,
                    modifier = Modifier.size(if (landscape) 36.dp else 40.dp).semantics { contentDescription = "Open game tree" }) {
                    TableChromeIcon("tree")
                }
                if (menuContent != null) Box {
                    IconButton(onClick = { options = true }, enabled = !readOnly, modifier = Modifier.size(40.dp)) { Icon(Icons.Outlined.MoreVert, "History options") }
                    DropdownMenu(options, { options = false }) { menuContent() }
                }
            }
        }
    }
    val transport: @Composable () -> Unit = {
        HistoryTransport(position, position.cursorId != playback.tree.rootId,
            position.cursorId != playback.tree.sharedHeadId, connected && continuationReady,
            playback::previous, { playback.next() }, playback::play, playback::pause, { captured ->
                if (connected && playback.canContinue(captured)) continuation(captured)
            }, controlsEnabled = !playback.transportLocked)
    }
    val panel: @Composable () -> Unit = {
        val timelineRow: @Composable () -> Unit = {
            Box(Modifier.height(TableTimelineHeight.dp)) {
                timeline()
                if (playback.branched) Surface(Modifier.align(Alignment.Center).graphicsLayer { alpha = branchAlpha.value },
                    color = Color(0xffa84f58), contentColor = Color.White, shape = MaterialTheme.shapes.small) {
                    Text("Opponent branched", Modifier.padding(horizontal = 24.dp, vertical = 10.dp), style = MaterialTheme.typography.titleSmall)
                }
            }
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

/** Literal glyph vocabulary, also replaceable with the host's existing event icon renderer. Unknown
 * known-type data must take compatibility/recovery handling, not silently become a generic dot. */
@Composable
fun HistoryEventIcon(icon: String) {
    if (icon == "swords") {
        Icon(painterResource(R.drawable.swords_outlined), null, Modifier.size(20.dp)); return
    }
    val vector = when (icon) {
        "flag" -> Icons.Outlined.Flag
        "rotate_90_degrees_cw" -> Icons.Outlined.Rotate90DegreesCw
        "favorite" -> Icons.Outlined.FavoriteBorder
        "open_with" -> Icons.Outlined.OpenWith
        "chat_bubble" -> Icons.AutoMirrored.Outlined.Chat
        "ads_click" -> Icons.Outlined.AdsClick
        "download" -> Icons.Outlined.Download
        "layers" -> Icons.Outlined.Layers
        "visibility" -> Icons.Outlined.Visibility
        "add_box" -> Icons.Outlined.AddBox
        "control_point_duplicate" -> Icons.Outlined.ControlPointDuplicate
        "exposure_plus_1" -> Icons.Outlined.ExposurePlus1
        "switch_account" -> Icons.Outlined.SwitchAccount
        "layers_clear" -> Icons.Outlined.LayersClear
        "casino" -> Icons.Outlined.Casino
        "outlined_flag" -> Icons.Outlined.OutlinedFlag
        "emoji_events" -> Icons.Outlined.EmojiEvents
        "sticky_note_2" -> Icons.AutoMirrored.Outlined.StickyNote2
        "delete" -> Icons.Outlined.DeleteOutline
        "shuffle" -> Icons.Outlined.Shuffle
        "pageview" -> Icons.Outlined.Pageview
        "close" -> Icons.Outlined.Close
        "monetization_on" -> Icons.Outlined.MonetizationOn
        "settings_backup_restore" -> Icons.Outlined.SettingsBackupRestore
        "flip_camera_android" -> Icons.Outlined.FlipCameraAndroid
        "flip" -> Icons.Outlined.Flip
        "flip_to_front" -> Icons.Outlined.FlipToFront
        "link" -> Icons.Outlined.Link
        "link_off" -> Icons.Outlined.LinkOff
        "remove_circle_outline" -> Icons.Outlined.RemoveCircleOutline
        "format_ink_highlighter" -> Icons.Outlined.BorderColor
        else -> error("Unsupported history icon: $icon")
    }
    Icon(vector, null, Modifier.size(20.dp))
}
internal fun historyIconGlyph(icon: String): String = when (icon) {
    "flag" -> "⚑"; "rotate_90_degrees_cw" -> "↻"; "favorite" -> "♥"; "open_with" -> "↔";
    "chat_bubble" -> "▤"; "ads_click" -> "◎"; "download" -> "⇩"; "layers" -> "▱";
    "visibility" -> "◉"; "add_box" -> "⊞"; "control_point_duplicate" -> "⊕"; "swords" -> "⚔";
    "exposure_plus_1" -> "+1"; "switch_account" -> "⇄"; "layers_clear" -> "⊠"; "casino" -> "⚄";
    "outlined_flag" -> "⚐"; "emoji_events" -> "♜"; "sticky_note_2" -> "▧"; "delete" -> "×";
    "shuffle" -> "⤨"; "pageview" -> "▣"; "close" -> "✕"; "monetization_on" -> "◉";
    "settings_backup_restore" -> "↺"; "flip_camera_android" -> "▣"; "flip" -> "▧"; "flip_to_front" -> "▤";
    "link" -> "∞"; "link_off" -> "∦"; "remove_circle_outline" -> "⊖"; "format_ink_highlighter" -> "▰";
    else -> error("Unsupported history icon: $icon")
}

@Composable
fun HistoryTransport(position: HistoryPosition, previousEnabled: Boolean, nextEnabled: Boolean, connected: Boolean,
    onPrevious: () -> Unit, onNext: () -> Unit, onPlay: () -> Unit, onPause: () -> Unit,
    onContinue: (String) -> Unit, modifier: Modifier = Modifier, controlsEnabled: Boolean = true) {
    val current by rememberUpdatedState(position)
    val continueAction by rememberUpdatedState(onContinue)
    val play by rememberUpdatedState(onPlay)
    val pause by rememberUpdatedState(onPause)
    val toggle = { if (controlsEnabled) { if (current.playing) pause() else play() } }
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onPrevious, modifier = Modifier.width(48.dp).semantics { contentDescription = "Previous" }, enabled = previousEnabled && controlsEnabled,
                colors = ButtonDefaults.textButtonColors(contentColor = Color(0xff253d44))) { TableChromeIcon("previous") }
            run {
                var captured by remember { mutableStateOf<String?>(null) }
                Surface(Modifier.weight(1f).heightIn(min = 48.dp).pressFeedback().semantics {
                    role = Role.Button; contentDescription = "Auto advance"
                    stateDescription = if (position.playing) "Auto: on" else "Auto: off"
                    if (!controlsEnabled) disabled() else onClick { toggle(); true }
                    if (connected && controlsEnabled) customActions = listOf(CustomAccessibilityAction("Continue playing from here") {
                        val cursor = current.cursorId
                        pause(); continueAction(cursor); true
                    })
                }.pointerInput(connected, controlsEnabled) {
                    if (!controlsEnabled) return@pointerInput
                    detectTapGestures(onPress = { captured = current.cursorId; tryAwaitRelease() },
                        onTap = { toggle() }, onLongPress = if (!connected) null else ({
                            pause()
                            captured?.let { continueAction(it) } // Host validates this original boundary; no fallback substitution.
                         }))
                }, color = Color.White, contentColor = Color(0xff253d44), border = BorderStroke(1.dp, MenuBorder)) {
                    Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(if (position.playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, null,
                            Modifier.size(20.dp), tint = if (position.playing) Color(0xffb57d45) else Color(0xff5b896c))
                        Column {
                            Text(if (position.playing) "Auto: on" else "Auto: off", fontSize = 16.sp, lineHeight = 20.sp)
                            Text("Tap: toggle · Hold: branch", fontSize = 10.sp, lineHeight = 12.sp, maxLines = 1)
                        }
                    }
                }
            }
            TextButton(onNext, modifier = Modifier.width(48.dp).semantics { contentDescription = "Next" }, enabled = nextEnabled && controlsEnabled,
                colors = ButtonDefaults.textButtonColors(contentColor = Color(0xff253d44))) { TableChromeIcon("next") }
        }
    }
}
