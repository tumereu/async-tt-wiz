package org.asyncttwiz.android

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.asyncttwiz.protocol.PlayZone

/** Rows share the table's instant local taps and release-bound peek; scrolling still wins on slop. */
internal fun Modifier.contentsCardGestures(id: String, zone: PlayZone, actions: () -> Unit,
    double: (() -> Unit)?, inspect: () -> Unit, peek: (Boolean) -> Unit): Modifier = composed {
    val parent = LocalTableTapRelay.current
    val density = LocalDensity.current.density
    val holdMillis = LocalViewConfiguration.current.longPressTimeoutMillis
    val currentActions by rememberUpdatedState(actions)
    val currentDouble by rememberUpdatedState(double)
    val currentPeek by rememberUpdatedState(peek)
    val currentInspect by rememberUpdatedState(inspect)
    var origin by remember { mutableStateOf(Offset.Zero) }
    val coordinator = remember(id, density, double != null, holdMillis) {
        TableGestureCoordinator({ intent -> when (intent) {
            is TableIntent.Actions -> if (zone == PlayZone.STACK && currentDouble != null) currentDouble?.invoke() else currentActions()
            is TableIntent.StackBubbles -> currentActions()
            is TableIntent.Rotate -> currentDouble?.invoke()
            is TableIntent.Peek -> currentPeek(intent.cardId != null)
            TableIntent.DismissTapUi -> parent?.dismiss?.invoke()
            else -> Unit
        } }, density = density, doubleTapEnabled = double != null, holdMillis = holdMillis)
    }
    val relay = remember(coordinator) { TableTapRelay(
        accepts = { p, t -> coordinator.replacesTap(coordinator.pointFromPixels(p.x - origin.x, p.y - origin.y), t) },
        down = { p, t -> coordinator.down(coordinator.pointFromPixels(p.x - origin.x, p.y - origin.y), t) },
        up = { p, t -> coordinator.up(coordinator.pointFromPixels(p.x - origin.x, p.y - origin.y), t) },
        cancel = coordinator::cancel) }
    onGloballyPositioned {
        origin = it.localToScreen(Offset.Zero)
        coordinator.update(TableScene(listOf(TableItem(id, TableBounds(0f, 0f, it.size.width / density, it.size.height / density),
            "Card", "", zone = zone, kind = TableItemKind.CARD, cardId = id)), emptyList()), TableSelection(), true)
    }.semantics(mergeDescendants = true) {
        onClick("Actions") { currentActions(); true }
        customActions = listOf(CustomAccessibilityAction("Inspect") { currentInspect(); true })
    }.pointerInput(coordinator) {
        coroutineScope {
            awaitEachGesture {
                val down = awaitFirstDown()
                coordinator.down(coordinator.pointFromPixels(down.position.x, down.position.y), down.uptimeMillis)
                val hold = launch { delay(viewConfiguration.longPressTimeoutMillis); coordinator.tick(down.uptimeMillis + viewConfiguration.longPressTimeoutMillis) }
                try {
                    var cancelled = false
                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change == null || change.isConsumed || event.changes.count { it.pressed } > 1 ||
                            (change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                            hold.cancel(); coordinator.cancel(); cancelled = true
                        } else if (!change.pressed && !cancelled) {
                            hold.cancel()
                            parent?.replacement = relay
                            coordinator.up(coordinator.pointFromPixels(change.position.x, change.position.y), change.uptimeMillis)
                            change.consume()
                        }
                    } while (event.changes.any { it.pressed })
                } finally { hold.cancel(); currentPeek(false) }
            }
        }
    }
}
