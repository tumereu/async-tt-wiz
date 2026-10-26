package org.asyncttwiz.android

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.asyncttwiz.protocol.*

@Composable
internal fun PodSetPickerDialog(controller: PodSetPickerController) {
    ScreenDialog(controller::dismiss) {
        PodSetPickerContent(controller.state, controller::query, controller::choose, controller::redownload, controller::retry)
        ScreenOutlinedButton(controller::dismiss) { Text("Back") }
    }
}

@Composable
internal fun PodSetPickerContent(state: PodSetPickerState, query: (String) -> Unit, choose: (String) -> Unit,
    redownload: (String) -> Unit, retry: () -> Unit) {
    Text("Choose MTG set", style = MaterialTheme.typography.titleLarge)
    OutlinedTextField(state.query, query, Modifier.fillMaxWidth(), label = { Text("Search name, code or release date") }, singleLine = true)
    if (state.busy) {
        LinearProgressIndicator(Modifier.fillMaxWidth())
        Text(state.status ?: "Loading set source…")
    }
    state.error?.let {
        Text(it, color = MaterialTheme.colorScheme.error)
        TextButton(retry, enabled = !state.busy) { Text("Retry") }
    }
    state.warnings.forEach { Text(it, color = MaterialTheme.colorScheme.primary) }
    val rows = state.filtered
    if (state.loaded && rows.isEmpty()) Text(if (state.rows.isEmpty()) "No draftable sets. Install or update card records in Settings." else "No matching sets.")
    LazyColumn(Modifier.fillMaxWidth().height(320.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items(rows, key = { it.set.code }) { row ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton({ choose(row.set.code) }, Modifier.weight(1f), enabled = !state.busy) {
                    Column(Modifier.fillMaxWidth()) {
                        Text(row.set.name)
                        Text("${row.set.code} · ${row.set.releaseDate}", style = MaterialTheme.typography.bodySmall)
                        if (row.cached) Text("Cached", style = MaterialTheme.typography.labelSmall)
                    }
                }
                if (row.cached) ScreenIconButton("Redownload ${row.set.code}", "refresh", { redownload(row.set.code) }, enabled = !state.busy)
            }
        }
    }
}

@Composable
internal fun PodSourceSummary(source: PodSource) { Text(podSourceLabel(source)) }

@Composable
internal fun PodSourceSupply(source: PodSource, needed: Long) {
    Text(podSupplyLabel(source, needed), color = if (source.kind == PodSourceKind.CUBE && needed > source.totalCopies)
        MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
}
