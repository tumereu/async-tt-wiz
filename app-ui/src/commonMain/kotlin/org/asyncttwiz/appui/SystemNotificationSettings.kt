package org.asyncttwiz.appui

import androidx.compose.runtime.Composable

/**
 * LOB-278: opens the device's own notification settings for the app (each kind's sound and vibration: its channel's); null
 * where there are none to open (a browser).
 */
@Composable expect fun rememberSystemNotificationSettings(): (() -> Unit)?
