package org.asyncttwiz.android

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** TBL-118 (owner 2026-11-19): how long a refusal's bubble stays before it fades. */
const val REFUSAL_BUBBLE_MILLIS = 2_000L
/** How long the bubble takes to rise into place, and to fade away. */
private const val REFUSAL_BUBBLE_RISE_MILLIS = 180
private const val REFUSAL_BUBBLE_FADE_MILLIS = 300

/** What a shake with no known reason says (and reports, [refusalReason]). */
const val REFUSAL_STAND_IN = "That can't be done now."

/**
 * TBL-118 (owner 2026-11-19: the "can't do" explainer): every shake says why in one plain line, a short-lived bubble that rises
 * from what shook. A shake without a reason is a fault: [REFUSAL_STAND_IN] shows, and it is logged and reported by itself as
 * placeholder text is (LOB-090), with [where] the shake came from.
 */
fun refusalReason(reason: String?, where: String): String = reason?.takeIf { it.isNotBlank() }
    ?: org.asyncttwiz.protocol.Faults.placeholder("Refusal", "shake without a reason", REFUSAL_STAND_IN, mapOf("where" to where))

/**
 * The refusal bubbles of one window (personal UI): the newest one only, by [Bubble.serial]. A bubble rises from [Bubble.anchor]
 * (in the window's root coordinates, px; null: nothing known to rise from, it shows in the middle).
 */
class RefusalBubbles {
    class Bubble(val text: String, val anchor: Rect?, val serial: Long)
    var bubble by mutableStateOf<Bubble?>(null); private set
    private var serial = 0L

    /** Shows [text] rising from [anchor]; the bubble shown before goes at once. */
    fun show(text: String, anchor: Rect?) {
        AppLog.info("table", "Refusal bubble: \"$text\"${if (anchor == null) " (nothing to rise from: in the middle)" else ""}")
        bubble = Bubble(text, anchor, ++serial)
    }

    /** The bubble [serial] is done (its time is up). */
    fun expire(serial: Long) { if (bubble?.serial == serial) bubble = null }
}

/** The window's [RefusalBubbles] (the game table's, a dialog's); null where nothing shows them. */
val LocalRefusalBubbles = staticCompositionLocalOf<RefusalBubbles?> { null }

/** A [RefusalBubbles] remembered for the window it is made in. */
@Composable
fun rememberRefusalBubbles(): RefusalBubbles = remember { RefusalBubbles() }

/**
 * Draws [bubbles]' bubble over what lies below it, never taking a tap: centred above its anchor (below it when there is no
 * room above), kept on screen, rising a little as it appears; it fades after [REFUSAL_BUBBLE_MILLIS] (TBL-118).
 */
@Composable
fun RefusalBubbleOverlay(bubbles: RefusalBubbles, modifier: Modifier = Modifier) {
    val bubble = bubbles.bubble
    LaunchedEffect(bubble?.serial) {
        val b = bubble ?: return@LaunchedEffect
        delay(REFUSAL_BUBBLE_MILLIS)
        bubbles.expire(b.serial)
    }
    val origin = remember { arrayOf(Offset.Zero) }
    RecordShown(bubble?.text, bubble?.serial)
    Box(modifier.fillMaxSize().onGloballyPositioned { origin[0] = it.positionInRoot() }) {
        InstantShowFadeOut(bubble, Modifier.fillMaxSize(), fadeMillis = REFUSAL_BUBBLE_FADE_MILLIS) { shown ->
            val rise = remember(shown.serial) { Animatable(0f) }
            LaunchedEffect(shown.serial) { rise.animateTo(1f, tween(REFUSAL_BUBBLE_RISE_MILLIS)) }
            Layout(content = { RefusalBubbleBody(shown.text) }, modifier = Modifier.fillMaxSize()) { measurables, constraints ->
                val margin = 8.dp.roundToPx()
                val gap = 6.dp.roundToPx()
                val width = constraints.maxWidth
                val height = constraints.maxHeight
                val body = measurables.single().measure(Constraints(maxWidth = (width - 2 * margin).coerceAtLeast(0)))
                layout(width, height) {
                    val anchor = shown.anchor?.translate(-origin[0])
                    val x = ((anchor?.center?.x ?: (width / 2f)) - body.width / 2f).roundToInt()
                        .coerceIn(margin, (width - margin - body.width).coerceAtLeast(margin))
                    val above = anchor?.let { (it.top - gap - body.height).roundToInt() }
                    val y = when {
                        anchor == null -> (height - body.height) / 2
                        above != null && above >= margin -> above
                        else -> (anchor.bottom + gap).roundToInt()
                    }.coerceIn(margin, (height - margin - body.height).coerceAtLeast(margin))
                    val lift = ((1f - rise.value) * 8.dp.toPx()).roundToInt()
                    body.place(x, y + lift)
                }
            }
        }
    }
}

/** The bubble itself: one plain line (wrapping onto a second when long) in white on the event text's dark fill. */
@Composable
private fun RefusalBubbleBody(text: String) {
    Box(Modifier.widthIn(max = 300.dp).background(Color(0xf0253d44), RoundedCornerShape(12.dp))
        .padding(horizontal = 12.dp, vertical = 6.dp).testTag("refusal-bubble")
        .clearAndSetSemantics { contentDescription = text; liveRegion = LiveRegionMode.Polite }) {
        Text(text, color = Color.White, fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** TBL-118, STK-047: a tap on what isn't a legal target while targets are picked. */
const val NOT_A_TARGET = "Not a legal target."
/** TBL-118: a tap on what isn't among a choice's options while it is picked on the table. */
const val NOT_A_CHOICE = "Not one of the choices."
/** TBL-118, ZON-130: a Library Automatum offers nothing for now. */
const val LIBRARY_NOTHING = "Nothing to do with this library now."
/** TBL-118, ZON-033: an empty graveyard or exile. */
const val PILE_EMPTY = "Nothing here yet."
/** TBL-118, CMB-040: a block placed to assume that the rules don't allow. */
const val CANT_BLOCK_THERE = "That can't block there."
/** TBL-118, ZON-080: a hand card none of whose ways can be paid for now. */
const val CANT_PAY = "Costs more than you can pay."
/** TBL-118, STK-049: a payment's source that no longer pays it. */
const val NO_LONGER_PAYS = "That doesn't help pay this any more."
