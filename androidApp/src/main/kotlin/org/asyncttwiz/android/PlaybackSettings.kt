package org.asyncttwiz.android

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** Shared by Settings and the timeline dialog; both edit the same account-local preferences. [plays]: the time a play
 * shows (HIS-112), in Settings only (HIS-028). */
@Composable
internal fun PlaybackSettings(preferences: HistoryPlaybackPreferences, accountId: String, heading: Boolean = true,
    moveSpeed: Boolean = true, plays: Boolean = false) {
    val pause by preferences.pauseState()
    val rewind by preferences.rewindPauseState()
    Column(Modifier.fillMaxWidth().testTag("playback-settings")) {
        if (heading) Text("Playback", style = MaterialTheme.typography.titleMedium)
        SettingsLabel("History playback pause", "${pause / 1000.0} s after each group")
        Slider(value = pause.toFloat(), onValueChange = { preferences.pauseMillis = (it / 100).roundToInt() * 100L },
            valueRange = HistoryPlaybackPreferences.MIN_PAUSE_MILLIS.toFloat()..HistoryPlaybackPreferences.MAX_PAUSE_MILLIS.toFloat(),
            modifier = Modifier.testTag("history-playback-pause"))
        SettingsLabel("Rewind pause", "${rewind / 1000.0} s after each rewound group")
        Slider(value = rewind.toFloat(), onValueChange = { preferences.rewindPauseMillis = (it / 10).roundToInt() * 10L },
            valueRange = HistoryPlaybackPreferences.MIN_REWIND_PAUSE_MILLIS.toFloat()..HistoryPlaybackPreferences.MAX_REWIND_PAUSE_MILLIS.toFloat(),
            modifier = Modifier.testTag("history-rewind-pause"))
        if (plays) ShowPlaysSetting(preferences)
        Spacer(Modifier.height(8.dp))
        if (moveSpeed) CardMoveSpeedSetting(accountId)
    }
}


/** HIS-112, HIS-113, LOB-082: how long the other player's plays (and the player's own triggers) show large on the table and a
 * combat holds the view on its attackers and on its damage, 0 (Off) to 3 s in 0.1 s steps. */
@Composable
internal fun ShowPlaysSetting(preferences: HistoryPlaybackPreferences) {
    val millis by preferences.showPlaysState()
    SettingsLabel("Show plays and combat", if (millis == 0L) "Off" else "${millis / 1000.0} s each")
    Slider(value = millis.toFloat(), onValueChange = { preferences.showPlaysMillis = (it / 100).roundToInt() * 100L },
        valueRange = 0f..HistoryPlaybackPreferences.MAX_SHOW_PLAYS_MILLIS.toFloat(),
        modifier = Modifier.testTag("show-plays-time"))
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
