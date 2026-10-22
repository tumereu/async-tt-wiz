package org.asyncttwiz.android

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import org.asyncttwiz.protocol.*
import org.asyncttwiz.storage.ArtworkReference

@Composable
internal fun SavedDeckScreen(controller: DeckLibraryController, openNavigation: (() -> Unit)?) {
    val state = controller.state
    BackHandler { controller.back() }
    if (state.dialog == DeckDialog.COVER && state.detail != null) {
        CoverScreen(controller); return
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (openNavigation != null) IconButton(onClick = openNavigation) { EntryIcon("Open navigation", "menu") }
            Text(state.detail?.saved?.deck?.name ?: "Saved deck", Modifier.weight(1f).padding(12.dp),
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Box {
                TextButton(onClick = { controller.edit(DeckDialog.EDIT) }, enabled = state.detail != null && !state.mutating) {
                    EntryIcon(null, "edit"); Text("Edit")
                }
                DropdownMenu(expanded = state.dialog == DeckDialog.EDIT, onDismissRequest = controller::dismiss) {
                    DropdownMenuItem(text = { Text("Rename") }, onClick = { controller.edit(DeckDialog.RENAME) })
                    DropdownMenuItem(text = { Text("Choose cover") }, onClick = { controller.edit(DeckDialog.COVER) })
                    DropdownMenuItem(text = { Text("Delete saved deck") }, onClick = { controller.edit(DeckDialog.DELETE) })
                }
            }
            IconButton(onClick = controller::back) { EntryIcon("Exit without saving", "close") }
        }
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (state.dialog == null) state.error?.let {
            Text(it, Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.error)
            TextButton(onClick = controller::retry) { Text("Retry saved deck") }
        }
        state.detail?.let { detail ->
            Row(Modifier.fillMaxWidth()) {
                for (section in SavedDeckSection.entries) {
                    val total = detail.saved.entries.filter { it.section == section }.sumOf { it.quantity.toLong() }
                    TextButton(onClick = { controller.section(section) }, Modifier.weight(1f)
                        .semantics { selected = section == state.section }) {
                        Text("${if (section == SavedDeckSection.MAINBOARD) "Mainboard" else "Sideboard"} · $total",
                            fontWeight = if (section == state.section) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp)) {
                items(detail.saved.entries.filter { it.section == state.section }, key = { it.definition }) { entry ->
                    val definition = detail.definitions.getValue(entry.definition)
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(entry.quantity.toString(), Modifier.widthIn(min = 24.dp))
                        DefinitionThumbnail(entry.definition, definition)
                        Text(definition.name, Modifier.weight(1f))
                    }
                    HorizontalDivider()
                }
            }
        }
    }
    if (state.dialog == DeckDialog.RENAME || state.dialog == DeckDialog.DELETE) {
        val deleting = state.dialog == DeckDialog.DELETE
        Dialog(onDismissRequest = controller::dismiss) {
            Surface(shape = MaterialTheme.shapes.large) {
                Column(Modifier.imePadding().heightIn(max = 480.dp).verticalScroll(rememberScrollState()).padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (deleting) "Delete saved deck?" else "Rename deck", Modifier.weight(1f),
                            style = MaterialTheme.typography.titleLarge)
                        IconButton(onClick = controller::dismiss) { EntryIcon("Cancel and close dialog", "close") }
                    }
                    if (deleting) {
                        Text(state.detail?.saved?.deck?.name.orEmpty(), Modifier.padding(vertical = 12.dp), fontWeight = FontWeight.Bold)
                        Text("Deletion cannot be undone.")
                    } else OutlinedTextField(state.draftName, controller::name, label = { Text("Deck name") },
                        modifier = Modifier.fillMaxWidth(), enabled = !state.mutating, singleLine = true)
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (state.mutating) LinearProgressIndicator(Modifier.fillMaxWidth())
                    Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = controller::dismiss, Modifier.weight(1f)) { Text(if (deleting) "Keep deck" else "Cancel") }
                        Button(onClick = controller::confirm, Modifier.weight(1f), enabled = !state.mutating,
                            colors = if (deleting) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                else ButtonDefaults.buttonColors()) { Text(if (deleting) "Delete saved deck" else "Save name") }
                    }
                }
            }
        }
    }
}

@Composable
private fun CoverScreen(controller: DeckLibraryController) {
    val state = controller.state
    val detail = state.detail!!
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Choose cover", Modifier.weight(1f).padding(16.dp), style = MaterialTheme.typography.titleLarge)
            IconButton(onClick = controller::dismiss) { EntryIcon("Cancel and close dialog", "close") }
        }
        state.error?.let { Text(it, Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.error) }
        if (state.mutating) LinearProgressIndicator(Modifier.fillMaxWidth())
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(detail.saved.entries.distinctBy { it.definition }, key = { it.definition }) { entry ->
                val definition = detail.definitions.getValue(entry.definition)
                val chosen = state.draftCover == entry.definition
                Surface(selected = chosen, onClick = { controller.cover(entry.definition) },
                    enabled = !state.mutating, modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(if (chosen) 3.dp else 1.dp,
                    if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        DefinitionThumbnail(entry.definition, definition)
                        Text(definition.name)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = controller::dismiss, Modifier.weight(1f)) { Text("Cancel") }
            Button(onClick = controller::confirm, Modifier.weight(1f), enabled = !state.mutating && state.draftCover != null) { Text("Use cover") }
        }
    }
}

@Composable
private fun DefinitionThumbnail(digest: String, definition: CardDefinition) {
    val face = definition.faces.first()
    CardArtwork(ArtworkReference(face.scryfallId, 0, digest, "small", face.imageUri), definition.name,
        48, 68, Modifier.size(48.dp, 68.dp))
}

@Composable
internal fun SavedDeckCollection(controller: DeckLibraryController, catalog: CatalogViewModel?) {
    val state = controller.state
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            if (state.busy) { LinearProgressIndicator(Modifier.fillMaxWidth()); if (!state.loaded) Text("Loading saved decks…") }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (state.loaded && state.decks.isEmpty()) Text("No saved decks yet.")
        }
        items(state.decks, key = { it.id }) { deck ->
            OutlinedCard(onClick = { controller.open(deck.id) }, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    deck.cover?.let { cover -> CardArtwork(ArtworkReference(cover.scryfallId, 0, cover.definition, "small", cover.imageUri),
                        cover.name, 48, 68, Modifier.size(48.dp, 68.dp)) } ?: EntryIcon(null, "decks")
                    Column(Modifier.weight(1f)) {
                        Text(deck.name, fontWeight = FontWeight.Bold)
                        Text("${deck.mainboard} / ${deck.sideboard}")
                    }
                    Text("›")
                }
            }
        }
        item { TextButton(onClick = controller::refresh, enabled = !state.busy) { Text("Refresh saved decks") } }
        if (catalog != null) item { CatalogControls(catalog) }
    }
}
