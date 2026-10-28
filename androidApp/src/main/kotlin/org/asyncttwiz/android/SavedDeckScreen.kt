package org.asyncttwiz.android

import androidx.compose.ui.text.input.ImeAction
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import org.asyncttwiz.protocol.*
import org.asyncttwiz.storage.ArtworkReference

@Composable
internal fun SavedDeckScreen(controller: DeckLibraryController, openNavigation: (() -> Unit)?,
    importDeck: ((DeckDetail) -> Unit)? = null) {
    val state = controller.state
    BackHandler { controller.back() }
    if (state.dialog == DeckDialog.COVER && state.detail != null) {
        CoverScreen(controller, openNavigation); return
    }
    ScreenBusyOverlay(if (state.mutating) "Saving deck…" else if (state.busy && state.detail == null) "Loading saved deck…" else null) {
    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Saved deck", openNavigation) {
            ScreenOutlinedButton(onClick = { state.detail?.let { importDeck?.invoke(it) } },
                enabled = state.detail != null && !state.mutating && importDeck != null) {
                EntryIcon(null, "edit"); Spacer(Modifier.width(6.dp)); Text("Edit")
            }
        }
        InitialLoadArea(state.detail != null, Modifier.weight(1f).fillMaxWidth(), state.error != null, controller::retry, loading = state.busy) {
        Column(Modifier.fillMaxSize()) {
        state.detail?.let { detail ->
            DeckMetadataHeader(detail.saved.deck.name, detail.saved.deck.cover)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedCard(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (section in SavedDeckSection.entries) {
                    val total = detail.saved.entries.filter { it.section == section }.sumOf { it.quantity.toLong() }
                    ScreenOutlinedButton(onClick = { controller.section(section) }, Modifier.weight(1f)
                        .semantics { selected = section == state.section }, colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (section == state.section) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.onSurface)) {
                        EntryIcon(null, if (section == SavedDeckSection.MAINBOARD) "decks" else "import"); Spacer(Modifier.width(8.dp))
                         Column(horizontalAlignment = Alignment.CenterHorizontally) {
                             Text(if (section == SavedDeckSection.MAINBOARD) "Mainboard" else "Sideboard", maxLines = 1,
                                 fontWeight = if (section == state.section) FontWeight.Bold else FontWeight.Normal)
                             Text("$total cards", maxLines = 1, style = MaterialTheme.typography.labelSmall)
                         }
                    }
                }
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                for (entry in detail.saved.entries.filter { it.section == state.section }) {
                    val definition = detail.definitions.getValue(entry.definition)
                    Row(Modifier.fillMaxWidth().clickable(onClickLabel = "Inspect ${definition.name}") { controller.inspect(entry.definition) }
                         .heightIn(min = 48.dp).padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(entry.quantity.toString(), Modifier.widthIn(min = 24.dp))
                          Box(Modifier.width(28.dp), contentAlignment = Alignment.Center) { DefinitionThumbnail(entry.definition, definition) }
                        Text(definition.name, Modifier.weight(1f))
                    }
                    HorizontalDivider()
                }
            }
            }
             TextButton(onClick = { controller.edit(DeckDialog.DELETE) },
                 colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Delete saved deck") }
            }
        }
    }
    }
    }
    FloatingTransientError(state.error.takeIf { state.dialog == null }, controller::dismissError, Modifier.align(Alignment.BottomCenter))
    }
    }
    DeckCardInspection(controller.inspection)
    if (state.dialog == DeckDialog.RENAME || state.dialog == DeckDialog.DELETE) {
        val deleting = state.dialog == DeckDialog.DELETE
        ScreenDialog(controller::dismiss, busyMessage = if (state.mutating) {
            if (deleting) "Deleting deck…" else "Saving deck name…"
        } else null, error = state.error, dismissError = controller::dismissError,
            title = if (deleting) "Delete saved deck?" else "Rename deck", actions = {
                DialogCancelButton(if (deleting) "Keep deck" else "Cancel", controller::dismiss)
                DialogMainButton(if (deleting) "Delete saved deck" else "Save name", controller::confirm,
                    enabled = !state.mutating, danger = deleting)
            }) {
                    if (deleting) {
                        Text(state.detail?.saved?.deck?.name.orEmpty(), fontWeight = FontWeight.Bold)
                        Text("Deletion cannot be undone.")
                    } else {
                        val name = rememberDialogField(state.draftName)
                        OutlinedTextField(name.value, { name.value = it; controller.name(it.text) }, label = { Text("Deck name") },
                            modifier = Modifier.fillMaxWidth().dialogField(name), enabled = !state.mutating, singleLine = true,
                            keyboardOptions = imeOptions(ImeAction.Done), keyboardActions = submitKeyboardActions(!state.mutating, submit = controller::confirm))
                    }
        }
    }
}

@Composable
private fun CoverScreen(controller: DeckLibraryController, navigation: (() -> Unit)?) {
    val state = controller.state
    val detail = state.detail!!
    val landscape = LocalConfiguration.current.screenWidthDp > LocalConfiguration.current.screenHeightDp
    val actions: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ScreenOutlinedButton(onClick = controller::dismiss, Modifier.weight(1f)) { EntryIcon(null, "close"); Spacer(Modifier.width(8.dp)); Text("Cancel") }
            ScreenButton(onClick = controller::confirm, Modifier.weight(1f), enabled = !state.mutating && state.draftCover != null) { EntryIcon(null, "ready"); Spacer(Modifier.width(8.dp)); Text("Use cover") }
        }
    }
    ScreenBusyOverlay(if (state.mutating) "Saving cover…" else null) {
    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Choose cover", navigation)
        val grid: @Composable (Modifier) -> Unit = { modifier ->
        Column(modifier) {
        Text("DECK: ${detail.saved.deck.name.uppercase()}", Modifier.padding(16.dp), style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyVerticalGrid(GridCells.Fixed(if (landscape) 3 else 2),
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(detail.saved.entries.distinctBy { it.definition }, key = { it.definition }) { entry ->
                val definition = detail.definitions.getValue(entry.definition)
                val chosen = state.draftCover == entry.definition
                Surface(selected = chosen, onClick = { controller.cover(entry.definition) },
                    enabled = !state.mutating, modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(if (chosen) 3.dp else 1.dp,
                    if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant), shape = MaterialTheme.shapes.small) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally,
                         verticalArrangement = Arrangement.spacedBy(12.dp)) {
                         BoxWithConstraints(Modifier.fillMaxWidth()) {
                             val face = definition.faces.first()
                             DeckThumbnail(ArtworkReference(face.scryfallId, 0, entry.definition, "small", face.imageUri),
                                 definition.name, maxWidth, 64.dp)
                         }
                        Text(definition.name)
                    }
                }
            }
        }
        }
        }
        if (landscape) Row(Modifier.weight(1f)) {
            grid(Modifier.weight(1f).fillMaxHeight())
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("COVER PREVIEW", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    state.draftCover?.let { digest -> detail.definitions[digest]?.let { definition ->
                        OutlinedCard(Modifier.fillMaxWidth(), colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                val face = definition.faces.first()
                                DeckThumbnail(ArtworkReference(face.scryfallId, 0, digest, "small", face.imageUri), definition.name, 120.dp, 64.dp)
                                Text(definition.name, fontWeight = FontWeight.Bold)
                            }
                        }
                    } }
                }
                actions()
            }
        } else {
            grid(Modifier.weight(1f))
            actions()
        }
    }
    FloatingTransientError(state.error, controller::dismissError, Modifier.align(Alignment.BottomCenter))
    }
    }
}

@Composable
private fun DefinitionThumbnail(digest: String, definition: CardDefinition) {
    val face = definition.faces.first()
    DeckThumbnail(ArtworkReference(face.scryfallId, 0, digest, "small", face.imageUri), definition.name, 22.dp, 30.dp)
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun SavedDeckCollection(controller: DeckLibraryController) {
    val state = controller.state
    Box(Modifier.fillMaxSize()) {
    InitialLoadArea(state.loaded, failed = state.error != null, retry = controller::refresh, loading = state.busy) {
    PullToRefreshBox(isRefreshing = state.busy, onRefresh = { if (!state.busy) controller.refresh() }, modifier = Modifier.fillMaxSize()) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text("SAVED LISTS (${state.decks.size})", Modifier.padding(vertical = 12.dp), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (state.loaded && state.decks.isEmpty()) Text("No saved decks yet.")
        }
        items(state.decks, key = { it.id }) { deck ->
            OutlinedCard(onClick = { controller.open(deck.id) }, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val cover = deck.cover
                    DeckThumbnail(cover?.let { ArtworkReference(it.scryfallId, 0, it.definition, "small", it.imageUri) }, cover?.name ?: deck.name, 40.dp, 52.dp)
                    Column(Modifier.weight(1f)) {
                        Text(deck.name, fontWeight = FontWeight.Bold)
                        Text("${deck.mainboard} main / ${deck.sideboard} side", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    EntryIcon(null, "chevron")
                }
            }
        }
    }
    }
    }
    FloatingTransientError(state.error, controller::dismissError, Modifier.align(Alignment.BottomCenter))
    }
}
