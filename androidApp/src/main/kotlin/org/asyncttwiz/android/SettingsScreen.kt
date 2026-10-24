package org.asyncttwiz.android

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
internal fun SettingsContent(catalog: CatalogViewModel?, accountId: String = "", logout: () -> Unit) {
    val preferences = rememberHistoryPlaybackPreferences(accountId)
    val pause by preferences.pauseState()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)) {
        if (catalog != null) CatalogControls(catalog)
        Column {
            Text("History playback pause", style = MaterialTheme.typography.titleMedium)
            Text("${pause / 1000.0} s after each group")
            Slider(value = pause.toFloat(), onValueChange = { preferences.pauseMillis = (it / 100).roundToInt() * 100L },
                valueRange = 100f..10_000f, steps = 98, modifier = Modifier.testTag("history-playback-pause"))
        }
        TextButton(onClick = logout, modifier = Modifier.heightIn(min = 48.dp).testTag("logout"),
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
            EntryIcon(null, "logout"); Spacer(Modifier.width(8.dp)); Text("Log out")
        }
    }
}
