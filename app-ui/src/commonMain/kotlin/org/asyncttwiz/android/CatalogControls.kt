package org.asyncttwiz.android

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.asyncttwiz.appui.FormatLocale
import org.asyncttwiz.appui.formatIn

/** WEB-009: under the installed line when the browser couldn't keep the card records for later visits. */
const val CARD_RECORDS_NOT_KEPT = "This browser couldn't keep them, so they download again on your next visit"

/** LOB-093: Settings → Card records: what is installed, how far an installation is, Retry after a failure. */
@Composable
fun CatalogControls(state: CatalogUiState, retry: () -> Unit) {
    Column(Modifier.fillMaxWidth().testTag("catalog-controls")) {
        SettingsSectionHeader("Card records")
        val installed = state.installed?.takeIf { state.ready }
        SettingsLabel(installed?.let { "Installed: ${it.date} · ${formatIn(FormatLocale.Us, "%,d", it.records)} records" }
            ?: state.records.line().orEmpty(), CARD_RECORDS_NOT_KEPT.takeIf { installed != null && !state.kept },
            Modifier.padding(vertical = 4.dp))
        if (!state.ready && !state.failed) PlainProgressBar(progress = { (state.progress?.percent ?: 0) / 100f },
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).testTag("catalog-status"))
        if (state.failed) SettingsActionRow("Retry", "refresh", retry, Modifier.testTag("catalog-retry"))
    }
}

/** A progress bar of how far something has come (owner 2026-11-22): one even bar without Material 3's stop dot and gap at its
 * right end; every determinate progress bar of the app is one. */
@Composable
fun PlainProgressBar(progress: () -> Float, modifier: Modifier = Modifier) =
    LinearProgressIndicator(progress = progress, modifier = modifier, gapSize = 0.dp, drawStopIndicator = {})
