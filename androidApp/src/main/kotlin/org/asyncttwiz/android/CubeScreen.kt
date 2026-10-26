package org.asyncttwiz.android

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.asyncttwiz.storage.DeckSection

@Composable
internal fun ManageCollectionScreen(cubes: CubeController, decks: DeckLibraryController?,
    importer: DeckImportController?, landscape: Boolean, navigation: (() -> Unit)?, catalog: CatalogViewModel?) {
    var cubeTab by remember { mutableStateOf(false) }
    LaunchedEffect(cubeTab) { if (cubeTab && !cubes.state.loaded) cubes.refresh() }
    if (cubeTab && (cubes.state.editing || cubes.state.selected != null)) {
        CubeScreen(cubes, navigation); return
    }
    if (!cubeTab && importer?.state?.active == true) {
        DeckImportScreen(importer, landscape, navigation, catalog); return
    }
    if (!cubeTab && decks?.state?.selected != null) {
        SavedDeckScreen(decks, navigation, importDeck = importer?.let { editor -> { detail -> decks.dismiss(); editor.openEditor(detail) } }); return
    }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Manage decks", navigation)
        TabRow(selectedTabIndex = if (cubeTab) 1 else 0) {
            Tab(selected = !cubeTab, onClick = { cubeTab = false }, text = { Text("Decks") })
            Tab(selected = cubeTab, onClick = { cubeTab = true }, text = { Text("Cubes") })
        }
        Box(Modifier.weight(1f)) {
            if (cubeTab) CubeCollection(cubes) else if (decks != null) SavedDeckCollection(decks)
        }
        ScreenButton(onClick = { if (cubeTab) cubes.newCube() else importer?.open() },
            modifier = Modifier.fillMaxWidth().padding(16.dp), enabled = !cubes.state.busy) {
            EntryIcon(null, if (cubeTab) "add" else "import"); Spacer(Modifier.width(8.dp))
            Text(if (cubeTab) "+ New cube" else "Import deck")
        }
    }
}

@Composable
private fun CubeCollection(controller: CubeController) {
    val state = controller.state
    Box(Modifier.fillMaxSize()) {
        InitialLoadArea(state.loaded, failed = state.error != null, retry = controller::refresh, loading = state.busy) {
            LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("CUBES (${state.cubes.size})", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                        TextButton(onClick = controller::refresh, enabled = !state.busy) { Text("Refresh") }
                    }
                    if (state.cubes.isEmpty()) Text("No saved cubes yet.")
                }
                items(state.cubes, key = { it.id }) { cube ->
                    OutlinedCard(onClick = { controller.open(cube.id) }, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(cube.name, fontWeight = FontWeight.Bold)
                                Text("${cube.totalCopies} copies", style = MaterialTheme.typography.bodySmall)
                            }
                            EntryIcon(null, "chevron")
                        }
                    }
                }
            }
        }
        FloatingTransientError(state.error, controller::dismissError, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
internal fun CubeScreen(controller: CubeController, navigation: (() -> Unit)?) {
    val state = controller.state
    BackHandler { controller.back() }
    ScreenBusyOverlay(if (state.busy) "Working on cube…" else null) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader(if (state.editing) "Edit cube" else "Saved cube", navigation) {
                TextButton(onClick = controller::back, enabled = !state.busy) { Text("Back") }
            }
            if (state.editing) {
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(state.name, controller::name, Modifier.fillMaxWidth(), label = { Text("Cube name") }, singleLine = true)
                    OutlinedTextField(state.cobraInput, controller::cobraInput, Modifier.fillMaxWidth(), label = { Text("Cube Cobra URL or cube id") }, singleLine = true)
                    ScreenOutlinedButton(onClick = controller::importCobra, enabled = state.cobraInput.isNotBlank() && !state.busy) { Text("Import Cube Cobra mainboard") }
                    Text("Or paste a counted card list. Importing from Cube Cobra replaces the text below.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(state.text, controller::text, Modifier.fillMaxWidth().heightIn(min = 220.dp), label = { Text("Card list") })
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(state.includeSideboard, controller::sideboard)
                        Text("Include sideboard")
                    }
                    ScreenOutlinedButton(onClick = controller::lookup, enabled = !state.busy) { Text("Resolve cards") }
                    state.provenance?.let { Text("Cube Cobra snapshot: $it", style = MaterialTheme.typography.bodySmall) }
                    state.result?.let { result ->
                        if (result.resolved) {
                            Text("${result.count(DeckSection.MAINBOARD)} total copies", fontWeight = FontWeight.Bold)
                            if (result.substitutions.isNotEmpty()) {
                                Text("Printing substitutions — review before saving", fontWeight = FontWeight.Bold)
                                result.substitutions.forEach { Text("Line ${it.line}: ${it.message}") }
                            }
                            result.entries.forEach { Text("${it.quantity} ${it.printing.name} (${it.printing.set}) ${it.printing.number}") }
                        }
                        result.issues.forEach { issue ->
                            val offending = state.text.lines().getOrNull(issue.line - 1)
                            Text("Line ${issue.line}: ${issue.message}" + (offending?.let { "\n$it" } ?: ""), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                ScreenButton(onClick = controller::save, Modifier.fillMaxWidth().padding(16.dp),
                    enabled = state.result?.resolved == true && state.name.isNotBlank() && !state.busy) { Text("Save cube") }
            } else {
                InitialLoadArea(state.detail != null, Modifier.weight(1f), state.error != null, controller::retry, loading = state.busy) {
                    val detail = state.detail
                    if (detail != null) LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            Text(detail.saved.cube.name, style = MaterialTheme.typography.titleLarge)
                            Text("${detail.saved.cube.totalCopies} total copies")
                            detail.saved.cube.cubeCobraId?.let { Text("Cube Cobra snapshot: $it", style = MaterialTheme.typography.bodySmall) }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ScreenOutlinedButton(onClick = controller::edit) { Text("Edit") }
                                ScreenOutlinedButton(onClick = { controller.dialog(CubeDialog.RENAME) }) { Text("Rename") }
                                TextButton(onClick = { controller.dialog(CubeDialog.DELETE) }) { Text("Delete") }
                            }
                        }
                        items(detail.saved.entries, key = { it.definition }) { entry ->
                            Text("${entry.quantity} ${detail.definitions.getValue(entry.definition).name}")
                            HorizontalDivider()
                        }
                    }
                }
            }
            if (state.dialog == null) state.error?.let { Text(it, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error) }
        }
    }
    if (state.dialog != null) {
        val deleting = state.dialog == CubeDialog.DELETE
        ScreenDialog(controller::dismiss, busyMessage = if (state.busy) "Saving cube…" else null,
            error = state.error, dismissError = controller::dismissError) {
            Text(if (deleting) "Delete cube?" else "Rename cube", style = MaterialTheme.typography.titleLarge)
            if (deleting) Text("Delete ${state.detail?.saved?.cube?.name}? Deletion cannot be undone.")
            else OutlinedTextField(state.name, controller::name, label = { Text("Cube name") })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ScreenOutlinedButton(onClick = controller::dismiss, enabled = !state.busy) { Text("Cancel") }
                ScreenButton(onClick = controller::confirm, enabled = !state.busy) { Text(if (deleting) "Delete cube" else "Save name") }
            }
        }
    }
}
