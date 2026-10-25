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
    val dismiss: () -> Unit = {},
    val ringDown: (Offset, Long, String) -> Unit = { p, t, _ -> down(p, t) },
    val move: (Offset, Long) -> Unit = { _, _ -> },
    val transform: (Offset, Float, Offset) -> Unit = { _, _, _ -> }) {
    var replacement: TableTapRelay? = null
    var ring: TableRingInput? = null
}

internal class TableRingInput(val cardId: String, val outside: (Offset) -> Boolean, val dismiss: () -> Unit)

internal val LocalTableTapRelay = compositionLocalOf<TableTapRelay?> { null }

/** Lives on the table host, not the disposable ring: dismissal cannot cancel a drag or pinch. */
internal fun Modifier.tableRingRelay(relay: TableTapRelay): Modifier = composed {
    var origin by remember { mutableStateOf(Offset.Zero) }
    onGloballyPositioned { origin = it.localToScreen(Offset.Zero) }.pointerInput(relay) {
        awaitEachGesture {
            val first = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val ring = relay.ring
            val replacement = relay.replacement?.takeIf { it.accepts(origin + first.position, first.uptimeMillis) }
            // A recognized second tap belongs to its originating surface even when the menu it just
            // opened overlaps that spot (e.g. a card menu over a pile-browser row).
            if (!first.isConsumed && ring != null && (replacement != null || ring.outside(origin + first.position))) {
                val owner = replacement ?: relay
                ring.dismiss()
                if (replacement != null) owner.down(origin + first.position, first.uptimeMillis)
                else owner.ringDown(origin + first.position, first.uptimeMillis, ring.cardId)
                first.consume()
                var transformed = false
                var completed = false
                try {
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val active = event.changes.filter { it.pressed }
                        if (active.size >= 2) {
                            transformed = true
                            val a = active[0]; val b = active[1]
                            val center = (a.position + b.position) / 2f
                            val before = (a.previousPosition + b.previousPosition) / 2f
                            val span = (a.previousPosition - b.previousPosition).getDistance()
                            val both = a.previousPressed && b.previousPressed
                            owner.transform(if (both) center - before else Offset.Zero,
                                if (both && span > 0) (a.position - b.position).getDistance() / span else 1f, origin + center)
                        } else if (!transformed) {
                            val change = event.changes.firstOrNull { it.id == first.id }
                            if (change == null) owner.cancel()
                            else if (change.pressed) owner.move(origin + change.position, change.uptimeMillis)
                            else { owner.up(origin + change.position, change.uptimeMillis); completed = true }
                        }
                        event.changes.forEach { it.consume() }
                    } while (event.changes.any { it.pressed })
                } finally { if (!completed || transformed) owner.cancel() }
            }
        }
    }
}

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
