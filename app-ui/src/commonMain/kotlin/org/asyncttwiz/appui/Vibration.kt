package org.asyncttwiz.appui

import androidx.compose.runtime.Composable

/**
 * TRN-075: the short vibration of the Your move cue; the returned function asks for one, [why] saying for the app log what
 * it is for. Android: a real one-shot vibration through the device's vibrator (felt whatever the touch feedback setting,
 * which a view's haptic feedback obeys; owner report 2026-11-19: the haptic tick it was before was never felt). The web:
 * the shared UI's haptic tick (WEB-012).
 */
@Composable expect fun rememberMoveVibration(): (why: String) -> Unit
