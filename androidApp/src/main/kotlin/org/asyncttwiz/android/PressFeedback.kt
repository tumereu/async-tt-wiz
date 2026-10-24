package org.asyncttwiz.android

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.*

/** Observe only: custom recognizers keep ownership of clicks, holds, scrolling and cancellation. */
internal fun Modifier.pressFeedback(enabled: Boolean = true): Modifier = composed {
    var pressed by remember { mutableStateOf(false) }
    drawWithContent {
        drawContent()
        if (pressed && enabled) drawRect(Color.Black.copy(alpha = .16f))
    }.pointerInput(enabled) {
        if (!enabled) return@pointerInput
        try {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                pressed = true
                try {
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change == null || !change.pressed || event.changes.count { it.pressed } > 1 ||
                            (change.position - down.position).getDistance() > viewConfiguration.touchSlop) pressed = false
                    } while (event.changes.any { it.pressed })
                } finally { pressed = false }
            }
        } finally { pressed = false }
    }
}
