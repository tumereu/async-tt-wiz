package org.asyncttwiz.android

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.asyncttwiz.appui.AppPlatform
import org.asyncttwiz.appui.moveVibrationSetting

/**
 * TRN-167 (owner, 2026-11-20): whether the phone vibrates with the Your move cue (TRN-075), on its own, apart from the "Your
 * move" sound (LOB-146): on by default; kept on the device, since it is about this phone. The Android app only.
 */
class MoveVibrationPreferences(platform: AppPlatform) {
    private val preferences = platform.preferences("move-cue")
    var enabled: Boolean
        get() = preferences.getBoolean(KEY, true)
        set(value) {
            AppLog.info("settings", "Vibrate on your move ${if (value) "on" else "off"}")
            preferences.edit().putBoolean(KEY, value).apply()
        }
    fun observe(onChange: () -> Unit): () -> Unit = preferences.observe { changed -> if (changed == null || changed == KEY) onChange() }
    private companion object { const val KEY = "vibrate" }
}

/** TRN-167: the Settings switch, in the Games section under the "Your move" sound; the Android app only. */
@Composable
fun MoveVibrationSetting() {
    if (!moveVibrationSetting) return
    val platform = AppPlatform.current
    val preferences = remember(platform) { MoveVibrationPreferences(platform) }
    val state = remember(preferences) { mutableStateOf(preferences.enabled) }
    DisposableEffect(preferences) {
        val stop = preferences.observe { state.value = preferences.enabled }
        state.value = preferences.enabled
        onDispose { stop() }
    }
    val enabled = state.value
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("move-vibration")
        .toggleable(enabled, role = Role.Switch, onValueChange = { preferences.enabled = it }),
        verticalAlignment = Alignment.CenterVertically) {
        SettingsLabel("Vibrate on your move", "Vibrates when the move passes to you while a game is open",
            Modifier.weight(1f).padding(end = 12.dp, top = 4.dp, bottom = 4.dp))
        Switch(checked = enabled, onCheckedChange = null)
    }
}
