package org.asyncttwiz.appui

import androidx.compose.runtime.Composable

/** LOB-278: a browser has no notification settings of the app's to open. */
@Composable actual fun rememberSystemNotificationSettings(): (() -> Unit)? = null
