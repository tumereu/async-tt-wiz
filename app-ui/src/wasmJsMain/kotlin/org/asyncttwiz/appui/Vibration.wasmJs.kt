package org.asyncttwiz.appui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import org.asyncttwiz.android.AppLog

/** WEB-012: the shared UI's haptic tick, which vibrates where the browser can. */
@Composable actual fun rememberMoveVibration(): (why: String) -> Unit {
    val haptic = LocalHapticFeedback.current
    return remember(haptic) { { why ->
        AppLog.info("game", "Your move vibration: the browser's haptic tick ($why)")
        haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
    } }
}
