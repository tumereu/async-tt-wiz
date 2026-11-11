package org.asyncttwiz.android

import androidx.compose.runtime.*

@Composable
fun JankTableState(reviewing: Boolean, popup: String?, dragging: Boolean,
    animating: () -> Boolean, visibleCards: () -> Int) {
    if (!Diagnostics.enabled) return
    SideEffect {
        DebugJank.state("table_mode" to if (reviewing) "timeline" else "play", "popup" to (popup ?: "none"), "dragging" to dragging.toString())
    }
    val animation by rememberUpdatedState(animating)
    val cards by rememberUpdatedState(visibleCards)
    LaunchedEffect(Unit) {
        snapshotFlow { animation() to cards() }.collect { (running, count) ->
            DebugJank.animation("table", running)
            DebugJank.state("visible_cards" to count.toString())
        }
    }
    DisposableEffect(Unit) { onDispose { DebugJank.animation("table", false) } }
}
