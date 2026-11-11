package org.asyncttwiz.android

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.asyncttwiz.appui.AppPlatform

/**
 * SYS-085 (owner, 2026-11-11): "Edge to edge" draws the game table into the strip of the screen around the camera (the display
 * cutout), off by default; saved on the device, as card sizes are (TBL-034), since it is about this phone's screen.
 */
class EdgeToEdgePreferences(platform: AppPlatform) {
    private val preferences = platform.preferences("table-display")
    var enabled: Boolean
        get() = preferences.getBoolean(KEY, false)
        set(value) {
            AppLog.info("settings", "Edge to edge ${if (value) "on" else "off"}")
            preferences.edit().putBoolean(KEY, value).apply()
        }
    fun observe(onChange: () -> Unit): () -> Unit = preferences.observe { changed -> if (changed == null || changed == KEY) onChange() }
    private companion object { const val KEY = "edge-to-edge" }
}

@Composable
fun rememberEdgeToEdge(): Pair<State<Boolean>, EdgeToEdgePreferences> {
    val platform = AppPlatform.current
    val preferences = remember(platform) { EdgeToEdgePreferences(platform) }
    val state = remember(preferences) { mutableStateOf(preferences.enabled) }
    DisposableEffect(preferences) {
        val stop = preferences.observe { state.value = preferences.enabled }
        state.value = preferences.enabled
        onDispose { stop() }
    }
    return state to preferences
}

/** SYS-085: the Settings switch, in the Table section. */
@Composable
fun EdgeToEdgeSetting() {
    val (state, preferences) = rememberEdgeToEdge()
    val enabled = state.value
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("edge-to-edge-setting")
        .toggleable(enabled, role = Role.Switch, onValueChange = { preferences.enabled = it }),
        verticalAlignment = Alignment.CenterVertically) {
        SettingsLabel("Edge to edge", "Draw the table into the strip around the camera",
            Modifier.weight(1f).padding(end = 12.dp, top = 4.dp, bottom = 4.dp))
        Switch(checked = enabled, onCheckedChange = null)
    }
}
