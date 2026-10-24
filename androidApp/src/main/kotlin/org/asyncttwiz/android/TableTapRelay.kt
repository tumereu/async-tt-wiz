package org.asyncttwiz.android

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.onGloballyPositioned

/** A newly opened local menu must not steal the second tap from its originating surface. */
internal class TableTapRelay(val accepts: (Offset, Long) -> Boolean,
    val down: (Offset, Long) -> Unit, val up: (Offset, Long) -> Unit, val cancel: () -> Unit,
    val dismiss: () -> Unit = {}) {
    var replacement: TableTapRelay? = null
}

internal val LocalTableTapRelay = compositionLocalOf<TableTapRelay?> { null }

internal fun Modifier.tableTapRelay(): Modifier = composed {
    val relay by rememberUpdatedState(LocalTableTapRelay.current)
    var origin by remember { mutableStateOf(Offset.Zero) }
    onGloballyPositioned { origin = it.localToScreen(Offset.Zero) }.pointerInput(Unit) {
        awaitEachGesture {
            val first = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val owner = relay?.replacement?.takeIf { it.accepts(origin + first.position, first.uptimeMillis) } ?: relay
            if (owner != null && owner.accepts(origin + first.position, first.uptimeMillis)) {
                owner.down(origin + first.position, first.uptimeMillis)
                first.consume()
                var valid = true
                try {
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == first.id }
                        if (change == null || event.changes.count { it.pressed } > 1 ||
                            (change.position - first.position).getDistance() > viewConfiguration.touchSlop) {
                            if (valid) owner.cancel()
                            valid = false
                        } else if (!change.pressed && valid) owner.up(origin + change.position, change.uptimeMillis)
                        event.changes.forEach { it.consume() }
                    } while (event.changes.any { it.pressed })
                } finally { owner.cancel() }
            }
        }
    }
}
