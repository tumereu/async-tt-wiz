package org.asyncttwiz.android

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

data class HistoryTimelineEvent(val id: String, val icon: String, val description: String,
    val author: String?, val groupId: String?, val alternatives: Int = 0)

object HistoryTimelineLayout {
    fun events(tree: HistoryTree, endpoint: String): List<HistoryTimelineEvent> {
        val path = tree.path(endpoint); val groups = tree.groups(path)
        val children = tree.nodes.values.groupingBy { it.parentId }.eachCount()
        return path.map { id ->
            val mutation = tree.nodes[id]
            HistoryTimelineEvent(id, mutation?.grouping?.icon ?: if (mutation == null) "flag" else
                org.asyncttwiz.protocol.HistoryGrouping.classify(mutation.action).icon,
                if (mutation == null) "Initial state, boundary $id" else
                    "${mutation.grouping?.icon ?: mutation.action::class.simpleName}, author ${mutation.author}, boundary $id, ${mutation.transition}",
                mutation?.author, groups[id]?.id, ((children[id] ?: 0) - 1).coerceAtLeast(0))
        }
    }
    fun x(index: Int, selected: Int, width: Float, pitch: Float) = width / 2f + (index - selected) * pitch
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
    eventIcon: @Composable (String) -> Unit = { HistoryEventIcon(it) }) {
    require(events.isNotEmpty() && events.map { it.id }.distinct().size == events.size)
    val selected = events.indexOfFirst { it.id == selectedId }; require(selected >= 0)
    val pause by rememberUpdatedState(onPause); val select by rememberUpdatedState(onSelect)
    val selectedNow by rememberUpdatedState(selected)
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val pitch = 24f
    BoxWithConstraints(modifier.fillMaxWidth().height(48.dp).clipToBounds().pointerInput(Unit) {
        awaitEachGesture { awaitFirstDown(requireUnconsumed = false); pause(); waitForUpOrCancellation() }
    }.pointerInput(events, density) {
        var start = selectedNow; var delta = 0f
        detectDragGestures(onDragStart = { pause(); start = selectedNow; delta = 0f }) { change, amount ->
            change.consume(); delta += amount.x / density
            select(events[HistoryTimelineLayout.selectedAfterDrag(start, delta, pitch, events.size)].id)
        }
    }) {
        val width = maxWidth.value
        Box(Modifier.fillMaxSize().semantics {
            contentDescription = events[selected].description
            progressBarRangeInfo = ProgressBarRangeInfo(selected.toFloat(), 0f..events.lastIndex.toFloat(), maxOf(0, events.size - 2))
            setProgress { value -> pause(); select(events[value.roundToInt().coerceIn(events.indices)].id); true }
            customActions = listOf(
                CustomAccessibilityAction("Previous event") { pause(); select(events[maxOf(0, selected - 1)].id); true },
                CustomAccessibilityAction("Next event") { pause(); select(events[minOf(events.lastIndex, selected + 1)].id); true })
        })
        Canvas(Modifier.fillMaxSize()) {
            val first = HistoryTimelineLayout.x(0, selected, size.width, pitch.dp.toPx())
            val last = HistoryTimelineLayout.x(events.lastIndex, selected, size.width, pitch.dp.toPx())
            if (first != last) drawLine(Color.Gray.copy(alpha = .3f), Offset(maxOf(0f, first), center.y),
                Offset(minOf(size.width, last), center.y), 1.dp.toPx())
            drawRect(Color(0xff287f85), Offset(center.x - 11.dp.toPx(), center.y - 12.dp.toPx()),
                Size(22.dp.toPx(), 24.dp.toPx()), style = Stroke(1.5.dp.toPx()))
        }
        for (i in HistoryTimelineLayout.visible(events.size, selected, width, pitch)) {
            val event = events[i]; val x = HistoryTimelineLayout.x(i, selected, width, pitch)
            if (abs(x - width / 2f) > width / 2f + 12f) continue
            val authorColor = when (event.author) { null -> Color(0xff59656a); viewer -> Color(0xff287f85); else -> Color(0xffa84f58) }
            Box(Modifier.offset((x - 10f).dp, 14.dp).size(20.dp).clickable { pause(); select(event.id) }
                .semantics { contentDescription = event.description; this.selected = i == selected }, contentAlignment = Alignment.Center) {
                CompositionLocalProvider(LocalContentColor provides authorColor) { eventIcon(event.icon) }
            }
            if (i > 0 && event.groupId != events[i - 1].groupId) Canvas(Modifier.offset((x - 12f).dp).width(1.dp).fillMaxHeight()) {
                drawLine(Color.Gray.copy(alpha = .25f), Offset(0f, 4.dp.toPx()), Offset(0f, size.height - 4.dp.toPx()), 1.dp.toPx())
            }
            if (event.alternatives > 0) Canvas(Modifier.offset(x.dp, 4.dp).size(10.dp)) {
                drawLine(authorColor, Offset.Zero, Offset(size.width, size.height), 1.dp.toPx())
            }
        }
    }
}

/** Ready-to-host native history chrome. The host resolves position.cursorId/version into the shared
 * replayed board, forwards lifecycle/accepted updates to playback, and supplies animation cadence.
 * Each tick selects one group endpoint; receipt of a whole network batch never skips those stages. */
@Composable
fun HistoryPlaybackPanel(playback: HistoryPlayback, timelineEndpoint: String, viewer: String, connected: Boolean,
    groupDurationMillis: Long, onContinue: (String) -> Unit, modifier: Modifier = Modifier,
    continuationReady: Boolean = true) {
    require(groupDurationMillis > 0)
    val position = playback.position
    val endpoint = when {
        playback.tree.contains(timelineEndpoint) && position.cursorId in playback.tree.path(timelineEndpoint) -> timelineEndpoint
        position.cursorId in playback.tree.path(playback.tree.sharedHeadId) -> playback.tree.sharedHeadId
        else -> position.cursorId
    }
    val events = remember(playback.tree, endpoint) { HistoryTimelineLayout.events(playback.tree, endpoint) }
    val continuation by rememberUpdatedState(onContinue)
    LaunchedEffect(position.playing, playback.foreground, groupDurationMillis) {
        while (playback.position.playing && playback.foreground) {
            delay(groupDurationMillis)
            playback.next(automatic = true)
        }
    }
    Column(modifier) {
        Text(if (position.playing) "Playing" else if (position.mode == HistoryMode.LIVE) "Live" else "Paused",
            style = MaterialTheme.typography.labelSmall)
        HistoryTimeline(events, position.cursorId, viewer, playback::pause, playback::scrub)
        HistoryTransport(position, position.cursorId != playback.tree.rootId,
            position.cursorId != playback.tree.sharedHeadId || position.mode != HistoryMode.LIVE, connected && continuationReady,
            playback::previous, { playback.next() }, playback::play, playback::pause, { captured ->
                if (connected && playback.canContinue(captured)) continuation(captured)
            })
    }
}

/** Literal glyph vocabulary, also replaceable with the host's existing event icon renderer. Unknown
 * known-type data must take compatibility/recovery handling, not silently become a generic dot. */
@Composable
fun HistoryEventIcon(icon: String) {
    val glyph = historyIconGlyph(icon)
    Text(glyph, color = LocalContentColor.current, style = MaterialTheme.typography.labelLarge)
}
internal fun historyIconGlyph(icon: String): String = when (icon) {
    "flag" -> "⚑"; "rotate_90_degrees_cw" -> "↻"; "favorite" -> "♥"; "open_with" -> "↔";
    "chat_bubble" -> "▤"; "ads_click" -> "◎"; "download" -> "⇩"; "layers" -> "▱";
    "visibility" -> "◉"; "add_box" -> "⊞"; "control_point_duplicate" -> "⊕"; "swords" -> "⚔";
    "exposure_plus_1" -> "+1"; "switch_account" -> "⇄"; "layers_clear" -> "⊠"; "casino" -> "⚄";
    "outlined_flag" -> "⚐"; "emoji_events" -> "♜"; "sticky_note_2" -> "▧"; "delete" -> "×";
    "shuffle" -> "⤨"; "pageview" -> "▣"; "close" -> "✕"; "monetization_on" -> "◉";
    "settings_backup_restore" -> "↺"; "flip_camera_android" -> "▣"; "flip" -> "▧"; "flip_to_front" -> "▤";
    "link" -> "∞"; "link_off" -> "∦"; "remove_circle_outline" -> "⊖";
    else -> error("Unsupported history icon: $icon")
}

@Composable
fun HistoryTransport(position: HistoryPosition, previousEnabled: Boolean, nextEnabled: Boolean, connected: Boolean,
    onPrevious: () -> Unit, onNext: () -> Unit, onPlay: () -> Unit, onPause: () -> Unit,
    onContinue: (String) -> Unit, modifier: Modifier = Modifier) {
    val current by rememberUpdatedState(position)
    val continueAction by rememberUpdatedState(onContinue)
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onPrevious, enabled = previousEnabled) { Text("‹ Previous") }
            if (position.playing) Button(onPause, Modifier.weight(1f)) { Text("Ⅱ Pause") }
            else {
                var captured by remember { mutableStateOf<String?>(null) }
                Surface(Modifier.weight(1f).heightIn(min = 48.dp).semantics {
                    role = Role.Button; contentDescription = "Play"
                    onClick { onPlay(); true }
                    if (connected) customActions = listOf(CustomAccessibilityAction("Continue playing from here") {
                        continueAction(current.cursorId); true
                    })
                }.pointerInput(connected) {
                    detectTapGestures(onPress = { captured = current.cursorId; tryAwaitRelease() },
                        onTap = { onPlay() }, onLongPress = if (!connected) null else ({
                            captured?.let { continueAction(it) } // Host validates this original boundary; no fallback substitution.
                        }))
                }, color = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary) {
                    Box(contentAlignment = Alignment.Center) { Text("▶ Play") }
                }
            }
            TextButton(onNext, enabled = nextEnabled) { Text("Next ›") }
        }
        TextButton({ onContinue(position.cursorId) }, enabled = connected && !position.playing) { Text("Continue playing from here") }
    }
}
