package org.asyncttwiz.android

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** Shared by Settings and the timeline dialog; both edit the same account-local preferences. */
@Composable
internal fun PlaybackSettings(preferences: HistoryPlaybackPreferences, accountId: String, heading: Boolean = true,
    moveSpeed: Boolean = true) {
    val pause by preferences.pauseState()
    val rewind by preferences.rewindPauseState()
    val enabled by preferences.autoPauseState()
    val autoRewindLive by preferences.autoRewindLiveState()
    val autoRewindOpen by preferences.autoRewindOpenState()
    Column(Modifier.fillMaxWidth().testTag("playback-settings")) {
        if (heading) Text("Playback", style = MaterialTheme.typography.titleMedium)
        Text("Stop Auto after an opponent’s matching action", Modifier.padding(vertical = 4.dp),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        for (trigger in PlaybackAutoPause.entries) {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .testTag("auto-pause-${trigger.name}")
                .toggleable(trigger in enabled, role = Role.Switch, onValueChange = { preferences.setAutoPause(trigger, it) }),
                verticalAlignment = Alignment.CenterVertically) {
                Text(trigger.label, Modifier.weight(1f).padding(end = 12.dp), style = MaterialTheme.typography.bodyLarge)
                Switch(checked = trigger in enabled, onCheckedChange = null)
            }
        }
        Spacer(Modifier.height(8.dp))
        SettingsLabel("History playback pause", "${pause / 1000.0} s after each group")
        Slider(value = pause.toFloat(), onValueChange = { preferences.pauseMillis = (it / 100).roundToInt() * 100L },
            valueRange = HistoryPlaybackPreferences.MIN_PAUSE_MILLIS.toFloat()..HistoryPlaybackPreferences.MAX_PAUSE_MILLIS.toFloat(),
            modifier = Modifier.testTag("history-playback-pause"))
        SettingsLabel("Rewind pause", "${rewind / 1000.0} s after each rewound group")
        Slider(value = rewind.toFloat(), onValueChange = { preferences.rewindPauseMillis = (it / 10).roundToInt() * 10L },
            valueRange = HistoryPlaybackPreferences.MIN_REWIND_PAUSE_MILLIS.toFloat()..HistoryPlaybackPreferences.MAX_REWIND_PAUSE_MILLIS.toFloat(),
            modifier = Modifier.testTag("history-rewind-pause"))
        Spacer(Modifier.height(8.dp))
        Text("Rewind automatically when the opponent branched", Modifier.padding(vertical = 4.dp),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SettingsSwitchRow("During live play", autoRewindLive, "auto-rewind-live") { preferences.autoRewindLive = it }
        SettingsSwitchRow("When opening a game", autoRewindOpen, "auto-rewind-open") { preferences.autoRewindOpen = it }
        if (moveSpeed) CardMoveSpeedSetting(accountId)
    }
}

@Composable
private fun SettingsSwitchRow(label: String, checked: Boolean, tag: String, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(tag)
        .toggleable(checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f).padding(end = 12.dp), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** Multiplier for card flight animations; tap the value to type an exact one. */
@Composable
internal fun CardMoveSpeedSetting(accountId: String) {
    val movePreferences = rememberCardMovePreferences(accountId)
    val multiplier by movePreferences.multiplierState()
    var exact by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            SettingsLabel("Card move speed", modifier = Modifier.weight(1f))
            TextButton({ exact = true }, Modifier.testTag("card-move-speed-value"), contentPadding = SettingsValuePadding) { Text("${multiplier}×") }
        }
        Slider(value = multiplier, onValueChange = { movePreferences.multiplier = (it * 100).roundToInt() / 100f },
            valueRange = MIN_CARD_MOVE_MULTIPLIER..MAX_CARD_MOVE_MULTIPLIER, modifier = Modifier.testTag("card-move-speed"))
    }
    if (exact) ScaleValueInput("Card move speed", multiplier, MIN_CARD_MOVE_MULTIPLIER..MAX_CARD_MOVE_MULTIPLIER,
        { exact = false }) {
        movePreferences.multiplier = it
        exact = false
    }
}

@Composable
internal fun PlaybackSettingsDialog(preferences: HistoryPlaybackPreferences, accountId: String, dismiss: () -> Unit) {
    ScreenDialog(dismiss) {
        PlaybackSettings(preferences, accountId)
        TextButton(onClick = dismiss, modifier = Modifier.fillMaxWidth()) { Text("Done") }
    }
}
