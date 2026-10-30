package org.asyncttwiz.android

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import org.asyncttwiz.protocol.ManualText

/** Players occupy top/bottom edges in both current table orientations. */
enum class ValueDragSide { TOP, BOTTOM }
internal val LocalValueDragSide = compositionLocalOf { ValueDragSide.BOTTOM }

internal fun cardValueDragSide(board: org.asyncttwiz.protocol.BoardState, card: org.asyncttwiz.protocol.PlayCard,
    viewer: String): ValueDragSide {
    val controller = board.containers.singleOrNull { it.id == card.containerId }?.accountId ?: card.owner
    return if (controller == viewer) ValueDragSide.BOTTOM else ValueDragSide.TOP
}

/** The initial unit distance (dp) of every life/counter/value drag: 70% of the former 48dp. */
internal const val VALUE_DRAG_STEP_DP = 33.6f

/** First three units cost [VALUE_DRAG_STEP_DP] (33.6dp) each, then a smooth quadratic accelerates to ~30
 * at 288.4dp; the curve is defined in units, so its shape is independent of the step.
 * Magnitudes truncate to whole units. [step] is the initial unit distance in the caller's
 * coordinates. Ties use the vertical axis. */
internal fun valueDragDelta(dx: Float, dy: Float, step: Float = VALUE_DRAG_STEP_DP, side: ValueDragSide = ValueDragSide.BOTTOM): Long {
    require(step.isFinite() && step > 0f)
    if (!dx.isFinite() || !dy.isFinite()) return 0
    val distance = if (abs(dx) > abs(dy)) dx else if (side == ValueDragSide.BOTTOM) dy else -dy
    val units = abs(distance.toDouble()) / step
    val extra = (units - 3).coerceAtLeast(0.0)
    val magnitude = (units + .6871 * extra * extra).toLong()
    return if (distance < 0) -magnitude else magnitude
}

internal fun valueDragLabel(value: String?, delta: Long): String {
    val change = if (delta >= 0) "+$delta" else delta.toString().replace('-', '−')
    val result = ManualText.add(value?.takeIf { it.isNotEmpty() } ?: "0", delta)
    return if (result != null) "$change → $result" else change
}

internal fun cornerDragLabel(value: String, delta: Long): String {
    val change = if (delta >= 0) "+$delta" else delta.toString().replace('-', '−')
    val result = cornerDragValue(value, delta)
    return if (result != null) "$change → $result" else change
}

internal fun cornerDragValue(value: String, delta: Long): String? {
    val parts = value.trim().split('/')
    if (parts.size !in 1..2 || parts.any { it != it.trim() }) return null
    return parts.map { ManualText.add(it, delta) ?: return null }.joinToString("/")
}

/** Captures callbacks on down; counts from down (including touch slop), commits only on release. */
internal fun Modifier.valueDragGestures(enabled: Boolean, preview: (Long?) -> Unit,
    start: () -> Boolean = { true }, side: ValueDragSide? = null, commit: (Long) -> Unit): Modifier = composed {
    val dragSide = side ?: LocalValueDragSide.current
    val currentPreview by rememberUpdatedState(preview)
    val currentStart by rememberUpdatedState(start)
    val currentCommit by rememberUpdatedState(commit)
    pointerInput(enabled, dragSide) {
        if (!enabled) return@pointerInput
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val release = currentCommit
            var dragging = false
            try {
                do {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id }
                    if (change == null || change.isConsumed || event.changes.count { it.pressed } > 1) break
                    val distance = change.position - down.position
                    if (!dragging && distance.getDistance() > viewConfiguration.touchSlop) {
                        if (!currentStart()) break
                        dragging = true
                    }
                    if (dragging) {
                        change.consume()
                        val delta = valueDragDelta(distance.x, distance.y, VALUE_DRAG_STEP_DP.dp.toPx(), dragSide)
                        currentPreview(delta)
                        if (!change.pressed && delta != 0L) release(delta)
                    }
                } while (event.changes.any { it.pressed })
            } finally { currentPreview(null) }
        }
    }
}
