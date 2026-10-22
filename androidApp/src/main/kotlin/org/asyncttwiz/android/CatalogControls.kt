package org.asyncttwiz.android

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Download controls within Manage decks, not a separate catalog browser or mandatory startup gate. */
@Composable
internal fun CatalogControls(controller: CatalogViewModel) {
    val state = controller.state
    Column(Modifier.widthIn(max = 540.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Card records", style = MaterialTheme.typography.titleMedium)
        state.installed?.let { Text("Installed: ${it.date} · ${it.records} records") }
            ?: Text("Download card records before importing a new deck. Artwork loads only when needed.")
        state.status?.let { Text(it, color = if (state.error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface) }
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        else if (state.available != null) {
            val size = state.size?.let { "${(it + 1023) / 1024} KiB" } ?: "up to 4 GiB (size unavailable)"
            Text("${state.available.date} · Download $size. ${if (state.metered) "This connection may charge for data." else "Using an unmetered connection."} Indexed records may use up to 4 GiB of storage.")
            Button(onClick = controller::consent, Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(if (state.error) "Retry download" else "Download card records")
            }
        }
        if (!state.busy) TextButton(onClick = { controller.foreground(force = true) }, Modifier.heightIn(min = 48.dp)) {
            Text(if (state.error && state.available == null) "Retry" else "Check for updates")
        }
    }
}
