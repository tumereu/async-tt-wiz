package org.asyncttwiz.android

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.asyncttwiz.protocol.SavedDeckSection
import org.asyncttwiz.storage.ArtworkReference

@Composable
internal fun SavedDeckScreen(controller: DeckLibraryController, openNavigation: (() -> Unit)?) {
    val state = controller.state
    BackHandler { controller.back() }
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (openNavigation != null) IconButton(onClick = openNavigation) { EntryIcon("Open navigation", "menu") }
            Text(state.detail?.saved?.deck?.name ?: "Saved deck", Modifier.padding(12.dp),
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
            if (state.busy) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Loading saved deck…") }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error)
                Button(onClick = controller::retry) { Text("Retry saved deck") } }
            state.detail?.let { detail ->
                for (section in SavedDeckSection.entries) {
                    val entries = detail.saved.entries.filter { it.section == section }
                    Text("${if (section == SavedDeckSection.MAINBOARD) "Mainboard" else "Sideboard"} · ${entries.sumOf { it.quantity.toLong() }}",
                        style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 12.dp))
                    for (entry in entries) {
                        val definition = detail.definitions.getValue(entry.definition)
                        val face = definition.faces.first()
                        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(entry.quantity.toString(), Modifier.widthIn(min = 24.dp))
                            CardArtwork(ArtworkReference(face.scryfallId, 0, entry.definition, "small", face.imageUri),
                                definition.name, 48, 68, Modifier.size(36.dp, 50.dp))
                            Text(definition.name, Modifier.weight(1f))
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
internal fun SavedDeckCollection(controller: DeckLibraryController) {
    val state = controller.state
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        if (state.busy) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Loading saved decks…") }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (state.loaded && state.decks.isEmpty()) Text("No saved decks yet.")
        for (deck in state.decks) {
            OutlinedButton(onClick = { controller.open(deck.id) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                EntryIcon(null, "decks"); Spacer(Modifier.width(8.dp)); Text(deck.name, Modifier.weight(1f)); Text("›")
            }
        }
        TextButton(onClick = controller::refresh, enabled = !state.busy) { Text("Refresh saved decks") }
    }
}
