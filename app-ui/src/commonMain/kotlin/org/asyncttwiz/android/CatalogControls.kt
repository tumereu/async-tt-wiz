package org.asyncttwiz.android

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import java.util.Locale

/** LOB-093: Settings → Card records: what is installed, how far an installation is, Retry after a failure. */
@Composable
fun CatalogControls(state: CatalogUiState, retry: () -> Unit) {
    Column(Modifier.fillMaxWidth().testTag("catalog-controls")) {
        SettingsSectionHeader("Card records")
        val installed = state.installed?.takeIf { state.ready }
        SettingsLabel(installed?.let { "Installed: ${it.date} · ${String.format(Locale.US, "%,d", it.records)} records" }
            ?: state.records.line().orEmpty(), null, Modifier.padding(vertical = 4.dp))
        if (!state.ready && !state.failed) LinearProgressIndicator(progress = { (state.progress?.percent ?: 0) / 100f },
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).testTag("catalog-status"))
        if (state.failed) SettingsActionRow("Retry", "refresh", retry, Modifier.testTag("catalog-retry"))
    }
}
