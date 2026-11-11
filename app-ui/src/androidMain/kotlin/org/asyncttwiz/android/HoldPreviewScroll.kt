package org.asyncttwiz.android

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import org.asyncttwiz.appui.screenHeightDp

/** TBL-104, SYS-049: every hold preview scrolls by at least as much as the finger moves, sped up so that a drag over
 * [SCREEN_SHARE] of the screen height scrolls the whole preview; movement past either end is not stored. */
object HoldPreviewScroll {
    const val SCREEN_SHARE = .9f

    /** Content scroll per finger pixel for a preview that scrolls [range] px on a screen [screenHeight] px tall. */
    fun gain(range: Float, screenHeight: Float): Float =
        if (range <= 0f || screenHeight <= 0f) 1f else maxOf(1f, range / (SCREEN_SHARE * screenHeight))

    /** The scroll position after a finger movement of [delta] px (positive reveals lower content), kept within 0..[range]. */
    fun target(value: Float, range: Float, delta: Float, screenHeight: Float): Float =
        (value + delta * gain(range, screenHeight)).coerceIn(0f, range.coerceAtLeast(0f))
}

/** Scrolls this hold preview by a finger movement of [delta] px on a screen [screenHeight] px tall. */
fun ScrollState.holdPreviewScroll(delta: Float, screenHeight: Float) {
    // Not measured yet: there is nothing to scroll.
    if (maxValue == Int.MAX_VALUE) return
    dispatchRawDelta(HoldPreviewScroll.target(value.toFloat(), maxValue.toFloat(), delta, screenHeight) - value)
}

/** The screen height in px that [holdPreviewScroll] measures the finger against. */
@Composable
fun holdPreviewScreenHeight(): Float = screenHeightDp() * LocalDensity.current.density
