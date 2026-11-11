package org.asyncttwiz.android

import org.asyncttwiz.protocol.Faults
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.roundToInt
import org.asyncttwiz.protocol.HistoryIcon
import org.asyncttwiz.appui.AppPlatform

/**
 * One event of the timeline (HIS-005): its icon, text and author (its colour); [separator] is the turn and step change
 * the game went through since the event before it, drawn as a labelled line in the gap before this event.
 */
data class HistoryTimelineEvent(val id: String, val icon: HistoryIcon, val description: String,
    val author: String?, val separator: TimelineSeparator? = null, val label: String = "Change")

/**
 * HIS-005: the turn and step the game had reached before an event, when it moved on since the event before it (the
 * latest change when several fell in between): "T3 · Combat". [newTurn]: the turn changed too (a stronger line);
 * [active] is the turn's player (the label's colour).
 */
data class TimelineSeparator(val label: String, val newTurn: Boolean, val active: String?)

object HistoryTimelineLayout {
    fun x(index: Int, selected: Int, width: Float, pitch: Float) =
        x(index, selected.toFloat(), width, pitch)
    fun x(index: Int, center: Float, width: Float, pitch: Float) =
        width / 2f + (index - center) * pitch
    /** HIS-005: a separator's line sits in the middle of the gap before its event at [x]. */
    fun separatorX(x: Float, pitch: Float) = x - pitch / 2f
    fun selectedAfterDrag(start: Int, delta: Float, pitch: Float, count: Int): Int {
        require(count > 0 && pitch > 0)
        return (start - (delta / pitch).roundToInt()).coerceIn(0, count - 1)
    }
    fun visible(count: Int, selected: Int, width: Float, pitch: Float): IntRange {
        require(count > 0 && selected in 0 until count && width >= 0 && pitch > 0)
        val radius = (width / (2f * pitch)).toInt() + 1
        return maxOf(0, selected - radius)..minOf(count - 1, selected + radius)
    }

    /**
     * HIS-005: which separator labels show, given each line's x and its label's width (the same units), left to right:
     * a label starts [gap] right of its line and shows only when it ends [gap] before the next shown label's line; the
     * later ones are placed first, so where labels would overlap the newest change keeps its label. Lines always show.
     */
    fun shownLabels(lines: List<Pair<Float, Float>>, gap: Float): Set<Int> {
        val shown = HashSet<Int>()
        var limit = Float.POSITIVE_INFINITY
        for (i in lines.indices.reversed()) {
            val (x, width) = lines[i]
            if (x + gap + width + gap <= limit) { shown += i; limit = x }
        }
        return shown
    }
}

/** Dense path window with a fixed center selection. The padding is real empty space at endpoints,
 * never fabricated nodes or an extended path line. onPause runs before any touch-driven exact selection.
 * HIS-005: the turn and step changes between events are labelled vertical lines; there are no branch lines. */
@Composable
fun HistoryTimeline(events: List<HistoryTimelineEvent>, selectedId: String, viewer: String,
    onPause: () -> Unit, onSelect: (String) -> Unit, modifier: Modifier = Modifier,
    /** HIS-005 (owner 2026-11-07): older events are on their way: a small spinner where the next older event goes. */
    loadingStart: Boolean = false,
    eventIcon: @Composable (HistoryIcon) -> Unit = { HistoryEventIcon(it) }) {
    val positions = remember(IdentityKey(events)) { events.withIndex().associate { it.value.id to it.index } }
    val selected = positions[selectedId] ?: -1
    if (selected < 0) return Faults.report("HistoryTimeline", "selected event not on the timeline", mapOf("selected" to selectedId, "events" to events.size.toString()))
    val pause by rememberUpdatedState(onPause); val select by rememberUpdatedState(onSelect)
    val selectedNow by rememberUpdatedState(selected)
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val measurer = rememberTextMeasurer()
    val pitch = 24f
    val windowCenter = selected.toFloat()
    BoxWithConstraints(modifier.fillMaxWidth().height(TableTimelineHeight.dp).clipToBounds().pointerInput(Unit) {
        awaitEachGesture { awaitFirstDown(requireUnconsumed = false); pause(); waitForUpOrCancellation() }
    }.pointerInput(IdentityKey(events), density) {
        var start = 0; var delta = 0f
        detectHorizontalDragGestures(onDragStart = { pause(); start = selectedNow; delta = 0f }) { change, amount ->
            change.consume(); delta += amount / density
            select(events[HistoryTimelineLayout.selectedAfterDrag(start, delta, pitch, events.size)].id)
        }
    }) {
        val width = maxWidth.value
        val visible = HistoryTimelineLayout.visible(events.size, windowCenter.roundToInt(), width, pitch)
            .filter { abs(HistoryTimelineLayout.x(it, windowCenter, width, pitch) - width / 2f) <= width / 2f + 12f }
        // HIS-005: the separators in view, left to right, at their lines' x
        val separators = visible.mapNotNull { i -> events[i].separator?.let { s ->
            s to HistoryTimelineLayout.separatorX(HistoryTimelineLayout.x(i, windowCenter, width, pitch), pitch) } }
        Box(Modifier.fillMaxSize().semantics {
            contentDescription = events[selected].description
            progressBarRangeInfo = ProgressBarRangeInfo(selected.toFloat(), 0f..events.lastIndex.toFloat(), maxOf(0, events.size - 2))
            setProgress { value -> pause(); select(events[value.roundToInt().coerceIn(events.indices)].id); true }
            customActions = listOf(
                CustomAccessibilityAction("Previous event") { pause(); select(events[maxOf(0, selected - 1)].id); true },
                CustomAccessibilityAction("Next event") { pause(); select(events[minOf(events.lastIndex, selected + 1)].id); true })
        })
        Canvas(Modifier.fillMaxSize()) {
            val first = HistoryTimelineLayout.x(0, windowCenter, size.width / density, pitch).dp.toPx()
            val last = HistoryTimelineLayout.x(events.lastIndex, windowCenter, size.width / density, pitch).dp.toPx()
            if (first != last) drawLine(Color.Gray.copy(alpha = .3f), Offset(maxOf(0f, first), center.y),
                Offset(minOf(size.width, last), center.y), 1.dp.toPx())
            val selectionX = HistoryTimelineLayout.x(selected, windowCenter, size.width / density, pitch).dp.toPx()
            drawRect(Color(0xff287f85), Offset(selectionX - 11.dp.toPx(), center.y - 12.dp.toPx()),
                Size(22.dp.toPx(), 24.dp.toPx()), style = Stroke(1.5.dp.toPx()))
            // HIS-005: a labelled line where the turn or step changed (a new turn's stronger), the label above the icons
            // in the turn player's colour
            val texts = separators.map { (s, _) -> measurer.measure(s.label, SeparatorLabelStyle) }
            val gap = 2.dp.toPx()
            val labelled = HistoryTimelineLayout.shownLabels(
                separators.mapIndexed { n, (_, x) -> x.dp.toPx() to texts[n].size.width.toFloat() }, gap)
            separators.forEachIndexed { n, (s, x) ->
                val lineX = x.dp.toPx()
                drawLine(SeparatorInk.copy(alpha = if (s.newTurn) .7f else .35f), Offset(lineX, 1.dp.toPx()),
                    Offset(lineX, size.height - 3.dp.toPx()), (if (s.newTurn) 1.5.dp else 1.dp).toPx())
                if (n in labelled) drawText(texts[n], historyAuthorColor(s.active, viewer), Offset(lineX + gap, 0f))
            }
        }
        for ((s, x) in separators) Box(Modifier.offset(x.dp).width(1.dp).fillMaxHeight()
            .semantics { testTag = TimelineSeparatorTag; contentDescription = s.label })
        for (i in visible) {
            val event = events[i]; val x = HistoryTimelineLayout.x(i, windowCenter, width, pitch)
            val authorColor = historyAuthorColor(event.author, viewer)
            TimelineEventChip(event, authorColor, i == selected,
                Modifier.align(Alignment.CenterStart).offset((x - 10f).dp), eventIcon) {
                pause(); select(event.id)
            }
        }
        // HIS-005: the older events loading, in the place of the next older one (only when it is in view)
        val loadingX = HistoryTimelineLayout.x(-1, windowCenter, width, pitch)
        if (loadingStart && loadingX >= -12f) CircularProgressIndicator(Modifier.align(Alignment.CenterStart).offset((loadingX - 7f).dp)
            .size(14.dp).semantics { testTag = TimelineLoadingTag; contentDescription = "Loading earlier events" },
            color = SeparatorInk, strokeWidth = 1.5.dp)
    }
}

/** HIS-005: the test tag of a separator's line; its content description is its label. */
const val TimelineSeparatorTag = "timeline-separator"
/** HIS-005: the test tag of the strip's spinner while older events load. */
const val TimelineLoadingTag = "timeline-loading"
private val SeparatorInk = Color(0xff59656a)
private val SeparatorLabelStyle = TextStyle(fontSize = 9.sp, lineHeight = 11.sp, fontWeight = FontWeight.Medium)

/** A recognized hold describes the event and consumes the tap; it never moves the review cursor. */
@Composable
private fun TimelineEventChip(event: HistoryTimelineEvent, color: Color, selected: Boolean, modifier: Modifier,
    eventIcon: @Composable (HistoryIcon) -> Unit, onClick: () -> Unit) {
    var held by remember(event.id) { mutableStateOf(false) }
    val alpha by animateFloatAsState(if (held) 1f else 0f, tween(150), label = "Event description")
    val click by rememberUpdatedState(onClick)
    HistoryEventChip(event, color, selected, modifier.semantics { onClick { click(); true } }
        .pointerInput(event.id) {
            detectTapGestures(onPress = { try { tryAwaitRelease() } finally { held = false } },
                onTap = { click() }, onLongPress = { held = true })
        }) { icon ->
        eventIcon(icon)
        if (alpha > 0f) {
            val gap = with(androidx.compose.ui.platform.LocalDensity.current) { 8.dp.roundToPx() }
            androidx.compose.ui.window.Popup(popupPositionProvider = remember(gap) { HoldTooltipPosition(gap) }) {
                Surface(Modifier.graphicsLayer { this.alpha = alpha }, color = MenuInk, contentColor = Color.White,
                    shape = MaterialTheme.shapes.extraSmall) {
                    CardText(event.description, Modifier.widthIn(max = 240.dp).padding(8.dp), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

fun historyAuthorColor(author: String?, viewer: String) = when (author) {
    null -> Color(0xff59656a); viewer -> Color(0xff287f85); else -> Color(0xffa84f58)
}

/** Same 20dp glyph and 24dp pitch vocabulary in the path and full-tree views. */
@Composable
fun HistoryEventChip(event: HistoryTimelineEvent, color: Color, selected: Boolean, modifier: Modifier = Modifier,
    eventIcon: @Composable (HistoryIcon) -> Unit = { HistoryEventIcon(it) }) {
    Box(modifier.size(20.dp).semantics { contentDescription = event.description; this.selected = selected },
        contentAlignment = Alignment.Center) {
        CompositionLocalProvider(LocalContentColor provides color) { eventIcon(event.icon) }
    }
}



/** Literal glyph vocabulary, also replaceable with the host's existing event icon renderer. Every table below is an
 * exhaustive `when` over [HistoryIcon]: a new icon does not compile until each draws it. [HistoryIcon.UNKNOWN]
 * (a name from a newer server) draws as a neutral dot. */
@Composable
fun HistoryEventIcon(icon: HistoryIcon) {
    val vector = historyIconVector(icon)
    if (vector == null) Icon(AppPlatform.current.swordsOutlined(), null, Modifier.size(20.dp))
    else Icon(vector, null, Modifier.size(20.dp))
}

/** Null only for [HistoryIcon.SWORDS], drawn from the swords_outlined drawable. */
fun historyIconVector(icon: HistoryIcon): androidx.compose.ui.graphics.vector.ImageVector? = when (icon) {
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
fun historyIconGlyph(icon: HistoryIcon): String = when (icon) {
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

/** Timeline mode's Previous, Auto and Next (HIS-010, HIS-013): view-only, nothing here branches or acts on the game. With
 * [onExit] (HIS-015: at the last event) the Auto button reads "Exit timeline" and a tap on it leaves timeline mode. */
@Composable
fun HistoryTransport(position: HistoryPosition, previousEnabled: Boolean, nextEnabled: Boolean,
    onPrevious: () -> Unit, onNext: () -> Unit, onPlay: () -> Unit, onPause: () -> Unit,
    modifier: Modifier = Modifier, controlsEnabled: Boolean = true, onExit: (() -> Unit)? = null) {
    val current by rememberUpdatedState(position)
    val play by rememberUpdatedState(onPlay)
    val pause by rememberUpdatedState(onPause)
    val exit by rememberUpdatedState(onExit)
    val toggle = { if (controlsEnabled) { exit?.invoke() ?: if (current.playing) pause() else play() } }
    val exits = onExit != null
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onPrevious, modifier = Modifier.size(48.dp).semantics { contentDescription = "Previous" },
                enabled = previousEnabled && controlsEnabled) { TableChromeIcon("previous") }
            Surface(Modifier.weight(1f).heightIn(min = 48.dp).pressFeedback().semantics {
                role = Role.Button
                if (exits) contentDescription = EXIT_TIMELINE
                else {
                    contentDescription = "Auto advance"
                    stateDescription = if (position.playing) "Auto: on" else "Auto: off"
                }
                if (!controlsEnabled) disabled() else onClick { toggle(); true }
            }.pointerInput(controlsEnabled) {
                if (!controlsEnabled) return@pointerInput
                detectTapGestures(onTap = { toggle() })
            }, color = Color.White, contentColor = Color(0xff253d44), border = BorderStroke(1.dp, MenuBorder)) {
                BoxWithConstraints(contentAlignment = Alignment.CenterStart) {
                    // HIS-015, TBL-009: in landscape's narrow place "Exit timeline" takes two smaller lines rather than being cut
                    val tight = exits && maxWidth < EXIT_TIMELINE_ONE_LINE
                    Row(Modifier.padding(horizontal = if (tight) 4.dp else 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(if (tight) 4.dp else 8.dp)) {
                        Icon(if (exits) Icons.Outlined.Close else if (position.playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, null,
                            Modifier.size(if (tight) 16.dp else 20.dp), tint = if (exits) Color(0xff253d44) else if (position.playing) Color(0xffb57d45) else Color(0xff5b896c))
                        Text(if (exits) EXIT_TIMELINE else if (position.playing) "Auto: on" else "Auto: off",
                            fontSize = if (tight) 12.sp else 16.sp, lineHeight = if (tight) 14.sp else 20.sp)
                    }
                }
            }
            IconButton(onNext, modifier = Modifier.size(48.dp).semantics { contentDescription = "Next" },
                enabled = nextEnabled && controlsEnabled) { TableChromeIcon("next") }
        }
    }
}

/** HIS-015: what the Auto button reads at the last event. */
const val EXIT_TIMELINE = "Exit timeline"
private val EXIT_TIMELINE_ONE_LINE = 150.dp
