package org.asyncttwiz.android

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.asyncttwiz.storage.CatalogProgress
import org.asyncttwiz.storage.CatalogPhase
import java.util.Locale

/** Sizes in one unit picked from [scale]: KB below 1 MB, one decimal below 10 MB, whole MB above. */
private fun catalogAmount(bytes: Long, scale: Long): String = when {
    scale < 1_000_000 -> String.format(Locale.US, "%.0f", bytes / 1_000.0)
    scale < 10_000_000 -> String.format(Locale.US, "%.1f", bytes / 1_000_000.0)
    else -> String.format(Locale.US, "%.0f", bytes / 1_000_000.0)
}
private fun catalogUnit(scale: Long) = if (scale < 1_000_000) "KB" else "MB"
internal fun catalogSize(bytes: Long): String = "${catalogAmount(bytes, bytes)} ${catalogUnit(bytes)}"
/** "done / total unit", both in the total's unit so the pair reads as one quantity. */
internal fun catalogSizes(done: Long, total: Long?): String {
    val scale = total ?: done
    return catalogAmount(done, scale) + (total?.let { " / ${catalogAmount(it, scale)}" } ?: "") + " " + catalogUnit(scale)
}
internal fun CatalogProgress.label(): String {
    if (phase == CatalogPhase.FINISHING) return "Finishing up — verifying catalog…"
    val bytes = catalogSizes(done, total)
    return if (phase == CatalogPhase.DOWNLOADING) "Downloading card records: $bytes"
    else "Indexing card records: $bytes processed · ${String.format(Locale.US, "%,d", records)} cards"
}

@Composable
internal fun CatalogStartupDialog(state: CatalogUiState, download: () -> Unit, dismiss: () -> Unit) {
    val initial = state.installed == null
    ScreenDialog(if (initial) ({}) else dismiss, title = if (state.installed == null) "Download card records?" else "Update card records?",
        actions = {
            DialogCancelButton(if (initial) "Exit" else "Not now", dismiss)
            DialogMainButton("Download", download)
        }) {
        Text(if (initial) "The app needs the card catalog to work. Artwork downloads separately, only when needed."
            else "Card records are needed to import decks. Artwork downloads separately, only when needed.")
        Text(if (initial && state.size == null) "Approximate download size: 450 MB (varies by catalog version)." else catalogDownloadEstimate(state))
        Text(if (state.metered) "You’re on a metered connection. This download may incur data charges."
            else "A metered connection may incur data charges. You’re currently using an unmetered connection.",
            style = MaterialTheme.typography.bodyMedium)
        Text("Indexed records may use up to 4 GB of storage.", style = MaterialTheme.typography.bodySmall)
    }
}

/** Manual retry, incremental updates and explicit full re-import. */
@Composable
internal fun CatalogControls(controller: CatalogViewModel) {
    val state = controller.state
    if (state.fullRefresh != null) ScreenDialog(controller::dismissFullRefresh, title = "Refresh full catalog?", actions = {
        DialogCancelButton("Cancel", controller::dismissFullRefresh)
        DialogMainButton("Refresh full catalog", controller::confirmFullRefresh)
    }) {
        Text("This re-downloads and replaces all card records. Normal updates download only new and recent sets.")
        Text(state.fullSize?.let { "Download size: ${catalogSize(it)}." } ?: "Download size unavailable (up to 4 GB).")
        Text("Allow up to 4 GB for indexed records. Your installed catalog stays available if the refresh fails.")
        if (state.metered) Text("You’re on a metered connection. This download may incur data charges.")
    }
    InitialLoadArea(state.loaded, Modifier.widthIn(max = 540.dp).fillMaxWidth()
        .heightIn(min = if (state.loaded) 0.dp else 160.dp).testTag("catalog-controls")) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Card records", style = MaterialTheme.typography.titleMedium)
        Text(state.installed?.let { "Installed: ${it.date} · ${it.records} records" }
            ?: "Download card records before importing a new deck. Artwork loads only when needed.")
        Box(Modifier.fillMaxWidth().testTag("catalog-status")) {
            Text(state.status?.takeIf { !state.background && !state.error }.orEmpty(),
                Modifier.fillMaxWidth().padding(bottom = 8.dp), minLines = 2, maxLines = 2)
            if (!state.background) state.progress?.let {
                CatalogProgressBar(it, Modifier.fillMaxWidth().align(Alignment.BottomCenter))
            }
        }
        if (state.available != null) {
            Text("${catalogDownloadEstimate(state)} ${if (state.metered) "This connection may charge for data." else "Using an unmetered connection."}")
            ScreenButton(onClick = controller::consent, Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = !state.busy) {
                EntryIcon(null, "download"); Spacer(Modifier.width(8.dp)); Text(if (state.error) "Retry download" else "Download card records")
            }
        }
        TextButton(onClick = { controller.foreground(force = true) }, Modifier.fillMaxWidth().height(48.dp)
            .testTag("catalog-check"), enabled = !state.busy) {
            // Full width keeps Check/Retry stable; left-aligned like the other text actions.
            EntryIcon(null, "refresh"); Spacer(Modifier.width(8.dp)); Text(if (state.error && state.available == null) "Retry" else "Check for updates")
            Spacer(Modifier.weight(1f))
        }
        if (state.installed != null) TextButton(onClick = controller::requestFullRefresh,
            Modifier.heightIn(min = 48.dp), enabled = !state.busy) { Text("Refresh full catalog") }
    }
    }
}

private fun catalogDownloadEstimate(state: CatalogUiState): String =
    if (state.plan?.full == false) "Update ${state.plan.sets.size} new/recent sets. Download size unavailable (at most 256 MiB per set)."
    else state.size?.let { "Download size: ${catalogSize(it)}." } ?: "Download size unavailable (up to 4 GB)."
