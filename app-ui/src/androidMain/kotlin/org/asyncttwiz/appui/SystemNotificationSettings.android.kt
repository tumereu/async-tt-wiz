package org.asyncttwiz.appui

import android.content.Intent
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import org.asyncttwiz.android.AppLog

/** LOB-278: Android's notification settings for the app, where each kind's channel has its sound and vibration. */
@Composable actual fun rememberSystemNotificationSettings(): (() -> Unit)? {
    val context = LocalContext.current
    return remember(context) {
        {
            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .apply { if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            runCatching { context.startActivity(intent) }
                .onSuccess { AppLog.info("settings", "Opened Android's notification settings for the app") }
                .onFailure { AppLog.warn("settings", "Couldn't open Android's notification settings for the app", it) }
        }
    }
}
