package org.asyncttwiz.android

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.asyncttwiz.appui.rememberSystemNotificationSettings
import org.asyncttwiz.protocol.NotificationKind
import org.asyncttwiz.protocol.NotificationSettings

/**
 * LOB-276 (owner, 2026-11-26): Settings → Notifications: a switch per kind of notification, each on by default, kept on the
 * server for the account so every phone and browser follows it; LOB-278: on Android, "Sound and vibration" under them opens
 * Android's notification settings for the app (each kind is a channel of its own there, LOB-072).
 */
@Composable
fun NotificationSettingsRows(state: AccountSettingsState) {
    val settings = state.settings?.notifications ?: NotificationSettings.ALL_ON
    for (kind in NotificationKind.all) {
        val on = settings.allows(kind)
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("notification-kind-${kind.id}")
            .toggleable(on, role = Role.Switch, onValueChange = { state.setNotification(kind, it) }),
            verticalAlignment = Alignment.CenterVertically) {
            SettingsLabel(kind.label, kind.explanation, Modifier.weight(1f).padding(end = 12.dp, top = 4.dp, bottom = 4.dp))
            Switch(checked = on, onCheckedChange = null)
        }
    }
    rememberSystemNotificationSettings()?.let { open ->
        SettingsActionRow("Sound and vibration", "notification-sound", open, Modifier.testTag("notification-sound"))
    }
}
