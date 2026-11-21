package org.asyncttwiz.android

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.asyncttwiz.appui.AppPlatform

/** The test tag of a large panel's drag handle ([ResizablePanelContent], STK-222). */
const val PANEL_HANDLE_TAG = "panel-handle"

/**
 * STK-222, STK-223: the size of the large Automatum panels' scrolling content, one for all of them. Pure rules, so they are
 * tested without a screen.
 */
object PanelSizes {
    /** The smallest content height (dp) a drag gives. */
    const val MIN = 120f
    /** The largest content height (dp) a drag gives: this share of the room the content has, so the table stays in view. */
    const val ROOM_SHARE = .75f
    /** The largest size kept at all (dp), whatever the room. */
    private const val MAX_KEPT = 2000f

    /** A kept size read back: a finite value within [MIN] and [MAX_KEPT]; null for none. */
    fun kept(value: Float?): Float? = value?.takeIf { it.isFinite() }?.coerceIn(MIN, MAX_KEPT)

    /**
     * STK-222 (owner 2026-11-21, E14): whether a panel whose content is [content] dp tall, shown [shown] dp tall, has the handle:
     * only while something of it is cut off (it would need scrolling at this size), and throughout a drag of it ([dragging]),
     * so the handle doesn't leave under the finger. A panel whose content fits never has one.
     */
    fun handle(content: Float, shown: Float, dragging: Boolean) = dragging || content > shown + CUT
    /** How much (dp) of the content must be cut off to count, past rounding to pixels. */
    private const val CUT = .5f

    /**
     * The size a drag gives (dp): the content was shown [start] dp tall at its start, with the size then [before]; [grow] dp
     * is how far the drag has moved the panel's edge outward (negative: inward); [content] is the content's whole height and
     * [room] the height it has. The panel's height follows the drag between [MIN] and [ROOM_SHARE] of the room (never below
     * a height it already had); while the content is all shown the size taller than it isn't lowered, so a short panel's
     * drag outward doesn't shrink the longer ones.
     */
    fun dragged(start: Float, before: Float, grow: Float, content: Float, room: Float): Float {
        val most = maxOf(room * ROOM_SHARE, start, MIN)
        val height = (start + grow).coerceIn(MIN, most)
        return if (height < content) height else maxOf(before, height)
    }
}

/**
 * STK-223, WEB-141: the large panels' size kept on this device, one file per layout (as card sizes are, WEB-022), for every
 * account alike; null until a panel is first resized (each panel then opens at its own size).
 */
class PanelSizePreferences(platform: AppPlatform, private val layout: AppLayout = AppLayout.Phone) {
    private val store = platform.preferences(if (layout == AppLayout.Desktop) "panel-size-desktop" else "panel-size")
    fun read(): Float? = if (store.contains(KEY)) PanelSizes.kept(store.getFloat(KEY, PanelSizes.MIN)) else null
    fun write(size: Float) {
        val kept = PanelSizes.kept(size) ?: return
        AppLog.info("panel", "Panel size kept: ${kept.toInt()} dp (${if (layout == AppLayout.Desktop) "desktop" else "phone"} layout)")
        store.edit().putFloat(KEY, kept).apply()
    }
    fun observe(change: () -> Unit): () -> Unit = store.observe { if (it == null || it == KEY) change() }

    private companion object { const val KEY = "content-height" }
}

/**
 * STK-222, STK-223: a large Automatum panel's scrolling content ([content], laid out with [arrangement] inside [modifier]'s
 * padding), at most the shared panel size high ([default] until a panel is first resized), with the drag handle at the
 * middle of the panel's top edge while some of the content is cut off ([PanelSizes.handle]) and [resizable]. A drag resizes it, and
 * with it every such panel, and the size is kept on the device when the finger lifts. WEB-141: in a desktop centred overlay
 * ([LocalPromptDim]), which grows both ways, the content grows twice the drag, so the edge follows the pointer. [what] names
 * the panel in the log.
 */
@Composable
fun ResizablePanelContent(default: Dp, what: String, modifier: Modifier = Modifier, resizable: Boolean = true,
    arrangement: Arrangement.Vertical = Arrangement.Top, content: @Composable ColumnScope.() -> Unit) {
    val platform = AppPlatform.current
    val layout = LocalAppLayout.current
    val preferences = remember(platform, layout) { PanelSizePreferences(platform, layout) }
    var kept by remember(preferences) { mutableStateOf(preferences.read()) }
    DisposableEffect(preferences) {
        val stop = preferences.observe { kept = preferences.read() }
        kept = preferences.read()
        onDispose { stop() }
    }
    // While a finger drags the handle, the size it gives (dp); kept when it lifts
    var dragging by remember { mutableStateOf<Float?>(null) }
    val size = dragging ?: kept ?: default.value
    val scroll = rememberScrollState()
    var shown by remember { mutableIntStateOf(0) }
    var room by remember { mutableIntStateOf(0) }
    val growth = if (LocalPromptDim.current != null) 2f else 1f
    val current by rememberUpdatedState(size)
    val density = LocalDensity.current
    Box {
        Column(modifier.layout { measurable, constraints ->
            room = constraints.maxHeight
            val placeable = measurable.measure(constraints.copy(maxHeight = minOf(size.dp.roundToPx(), constraints.maxHeight)
                .coerceAtLeast(constraints.minHeight)))
            shown = placeable.height
            layout(placeable.width, placeable.height) { placeable.place(0, 0) }
        }.verticalScroll(scroll), verticalArrangement = arrangement, content = content)
        val whole = with(density) { (shown + scroll.maxValue).toDp().value }
        val wholeNow by rememberUpdatedState(whole)
        val shownDp = with(density) { shown.toDp().value }
        val handle = resizable && PanelSizes.handle(whole, shownDp, dragging != null)
        LaunchedEffect(handle) {
            if (resizable && (handle || whole > 0f)) AppLog.info("panel", "Panel handle in the $what ${if (handle) "shown" else "hidden"}: content " +
                "${whole.toInt()} dp, shown ${shownDp.toInt()} dp")
        }
        if (handle) {
            var start by remember { mutableFloatStateOf(0f) }
            var before by remember { mutableFloatStateOf(0f) }
            var moved by remember { mutableFloatStateOf(0f) }
            Box(Modifier.align(Alignment.TopCenter).size(72.dp, 24.dp).testTag(PANEL_HANDLE_TAG)
                .semantics { contentDescription = "Panel size: drag to resize" }
                .pointerInput(growth) {
                    detectVerticalDragGestures(
                        onDragStart = {
                            start = shown.toDp().value; before = current; moved = 0f
                            dragging = current
                        },
                        onDragEnd = {
                            val end = dragging
                            dragging = null
                            if (end != null) {
                                AppLog.info("panel", "Panel resized in the $what: content ${start.toInt()} → ${end.toInt()} dp " +
                                    "(size was ${before.toInt()}, content ${wholeNow.toInt()} dp tall, room ${with(density) { room.toDp().value.toInt() }} dp)")
                                kept = end
                                preferences.write(end)
                            }
                        },
                        onDragCancel = { dragging = null },
                    ) { change, amount ->
                        change.consume()
                        moved += amount
                        // Up (negative y) grows the panel, its top edge facing the table
                        dragging = PanelSizes.dragged(start, before, -moved.toDp().value * growth, wholeNow, room.toDp().value)
                    }
                }) {
                Box(Modifier.align(Alignment.TopCenter).padding(top = 3.dp).size(36.dp, 4.dp)
                    .background(HandleGrey, RoundedCornerShape(2.dp)))
            }
        }
    }
}

/** The handle's grey: quiet on the panel's white, beside its violet top edge. */
private val HandleGrey = Color(0xffaba7b5)
