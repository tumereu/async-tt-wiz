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
internal fun PlaybackSettings(preferences: HistoryPlaybackPreferences, accountId: String) {
    val pause by preferences.pauseState()
    val rewind by preferences.rewindPauseState()
    val enabled by preferences.autoPauseState()
    Column(Modifier.fillMaxWidth().testTag("playback-settings")) {
        Text("Playback", style = MaterialTheme.typography.titleMedium)
        Text("Stop Auto after an opponent’s matching action", style = MaterialTheme.typography.bodySmall)
        for (trigger in PlaybackAutoPause.entries) {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .testTag("auto-pause-${trigger.name}")
                .toggleable(trigger in enabled, role = Role.Switch, onValueChange = { preferences.setAutoPause(trigger, it) }),
                verticalAlignment = Alignment.CenterVertically) {
                Text(trigger.label, Modifier.weight(1f))
                Switch(checked = trigger in enabled, onCheckedChange = null)
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("History playback pause", style = MaterialTheme.typography.titleSmall)
        Text("${pause / 1000.0} s after each group")
        Slider(value = pause.toFloat(), onValueChange = { preferences.pauseMillis = (it / 100).roundToInt() * 100L },
            valueRange = HistoryPlaybackPreferences.MIN_PAUSE_MILLIS.toFloat()..HistoryPlaybackPreferences.MAX_PAUSE_MILLIS.toFloat(),
            steps = 98, modifier = Modifier.testTag("history-playback-pause"))
        Text("Rewind pause", style = MaterialTheme.typography.titleSmall)
        Text("${rewind / 1000.0} s after each rewound group")
        Slider(value = rewind.toFloat(), onValueChange = { preferences.rewindPauseMillis = (it / 10).roundToInt() * 10L },
            valueRange = HistoryPlaybackPreferences.MIN_REWIND_PAUSE_MILLIS.toFloat()..HistoryPlaybackPreferences.MAX_REWIND_PAUSE_MILLIS.toFloat(),
            steps = 197, modifier = Modifier.testTag("history-rewind-pause"))
        val movePreferences = rememberCardMovePreferences(accountId)
        val multiplier by movePreferences.multiplierState()
        var exact by remember { mutableStateOf(false) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Card move speed", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            TextButton({ exact = true }, Modifier.testTag("card-move-speed-value")) { Text("${multiplier}×") }
        }
        Slider(value = multiplier, onValueChange = { movePreferences.multiplier = (it * 100).roundToInt() / 100f },
            valueRange = MIN_CARD_MOVE_MULTIPLIER..MAX_CARD_MOVE_MULTIPLIER, modifier = Modifier.testTag("card-move-speed"))
        if (exact) ScaleValueInput("Card move speed", multiplier, MIN_CARD_MOVE_MULTIPLIER..MAX_CARD_MOVE_MULTIPLIER,
            { exact = false }) {
            movePreferences.multiplier = it
            exact = false
        }
    }
}

@Composable
internal fun PlaybackSettingsDialog(preferences: HistoryPlaybackPreferences, accountId: String, dismiss: () -> Unit) {
    ScreenDialog(dismiss) {
        PlaybackSettings(preferences, accountId)
        TextButton(onClick = dismiss, modifier = Modifier.fillMaxWidth()) { Text("Done") }
    }
}
