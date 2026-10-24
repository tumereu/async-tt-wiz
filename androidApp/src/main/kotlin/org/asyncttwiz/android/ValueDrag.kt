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

/** Total displacement, not velocity: complete 10dp steps on the dominant axis. Ties use vertical. */
internal fun valueDragDelta(dx: Float, dy: Float, step: Float = 10f): Long {
    require(step.isFinite() && step > 0f)
    if (!dx.isFinite() || !dy.isFinite()) return 0
    return ((if (abs(dx) > abs(dy)) dx else -dy) / step).toLong()
}

internal fun valueDragLabel(value: String?, delta: Long): String {
    val change = if (delta >= 0) "+$delta" else "$delta"
    val result = ManualText.add(value?.takeIf { it.isNotEmpty() } ?: "0", delta)
    return if (result != null) "$change -> $result" else change
}

/** Captures callbacks on down; counts from down (including touch slop), commits only on release. */
internal fun Modifier.valueDragGestures(enabled: Boolean, preview: (Long?) -> Unit,
    start: () -> Boolean = { true }, commit: (Long) -> Unit): Modifier = composed {
    val currentPreview by rememberUpdatedState(preview)
    val currentStart by rememberUpdatedState(start)
    val currentCommit by rememberUpdatedState(commit)
    pointerInput(enabled) {
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
                        val delta = valueDragDelta(distance.x, distance.y, 10.dp.toPx())
                        currentPreview(delta)
                        if (!change.pressed && delta != 0L) release(delta)
                    }
                } while (event.changes.any { it.pressed })
            } finally { currentPreview(null) }
        }
    }
}
