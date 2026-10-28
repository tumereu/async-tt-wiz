package org.asyncttwiz.android

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.layout.*
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

internal val PodPickColumnRange = 1..15
internal val PodBuildColumnRange = 2..10

/** Personal per-orientation Build column count; shared by the view settings panel and the Build view. */
@Stable
class PodBuildColumns internal constructor(private val preferences: SharedPreferences, private val key: String) {
    var count by mutableIntStateOf(preferences.getInt(key, 7).coerceIn(PodBuildColumnRange))
        private set
    fun choose(value: Int) { count = value.coerceIn(PodBuildColumnRange); preferences.edit().putInt(key, count).apply() }
}

@Composable
internal fun rememberPodBuildColumns(): PodBuildColumns {
    val context = LocalContext.current.applicationContext
    val orientation = LocalConfiguration.current.orientation
    val preferences = remember(context) { context.getSharedPreferences("pod-build", Context.MODE_PRIVATE) }
    return remember(preferences, orientation) { PodBuildColumns(preferences, "columns-$orientation") }
}

/** Header cog opening a small floating panel with every pod view's layout setting; values apply live. */
@Composable
internal fun PodViewSettingsButton(pick: PodPickColumns?, build: PodBuildColumns) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton({ open = true }, Modifier.size(48.dp)) { EntryIcon("View settings", "settings") }
        DropdownMenu(open, { open = false }, Modifier.testTag("pod-view-settings")) {
            Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (pick != null) PodViewSettingRow("Draft · cards per row") {
                    val value = pick.preferred.coerceIn(PodPickColumnRange)
                    PodColumnControl("$value", "Cards per row", value, PodPickColumnRange, pick::choose)
                }
                PodViewSettingRow("Build · columns") {
                    PodColumnControl("${build.count}", "Columns", build.count, PodBuildColumnRange, build::choose)
                }
            }
        }
    }
}

@Composable
private fun PodViewSettingRow(label: String, control: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.padding(end = 12.dp), style = MaterialTheme.typography.labelLarge, maxLines = 1, softWrap = false)
        Spacer(Modifier.weight(1f))
        control()
    }
}
