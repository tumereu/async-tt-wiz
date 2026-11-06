package org.asyncttwiz.android

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import kotlinx.coroutines.delay

/** TRN-063: how long a pending own action waits before its working sign shows, and the least time a shown sign stays. */
internal const val SLOW_ACTION_AFTER_MS = 150L
internal const val SLOW_ACTION_LEAST_MS = 300L

/**
 * TRN-063 (owner, 2026-11-06): whether the main button shows the "working" sign of the player's own action now. It
 * shows only once [pending] (an own action pressed whose result hasn't arrived) has held [SLOW_ACTION_AFTER_MS] without
 * a break, and once shown stays at least [SLOW_ACTION_LEAST_MS] (no flicker); an action whose result arrives sooner
 * looks exactly as before. [pending] reads snapshot state, so the sign follows it. Times are the composition's clock
 * (the controlled test clock in tests).
 */
@Composable
internal fun rememberSlowActionSign(pending: () -> Boolean): Boolean {
    val busy = pending()
    var shown by remember { mutableStateOf(false) }
    // Whether the shown sign has stayed its least time
    var stayed by remember { mutableStateOf(false) }
    LaunchedEffect(busy) {
        if (busy && !shown) { delay(SLOW_ACTION_AFTER_MS); stayed = false; shown = true }
    }
    LaunchedEffect(shown) {
        if (shown) { delay(SLOW_ACTION_LEAST_MS); stayed = true }
    }
    if (shown && stayed && !busy) SideEffect { shown = false }
    return shown
}

/**
 * The sign itself (TRN-063): a soft violet band sweeping across the main button's face and a thin violet line along its
 * bottom, over its content, with the state "Working" for accessibility. [modifier] sizes and clips it to the button.
 */
@Composable
internal fun SlowActionShimmer(modifier: Modifier) {
    val sweep by rememberInfiniteTransition(label = "slow action").animateFloat(-0.4f, 1.4f,
        infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart), label = "sweep")
    Box(modifier.testTag("main-button-working").semantics { stateDescription = "Working" }.drawBehind {
        val band = size.width * .45f
        val x = sweep * size.width
        drawRect(Brush.linearGradient(listOf(Color.Transparent, AutomatumViolet.copy(alpha = .24f), Color.Transparent),
            start = Offset(x - band / 2, 0f), end = Offset(x + band / 2, size.height)))
        drawRect(AutomatumViolet.copy(alpha = .6f), Offset(0f, size.height - 2.5f * density), Size(size.width, 2.5f * density))
    })
}
