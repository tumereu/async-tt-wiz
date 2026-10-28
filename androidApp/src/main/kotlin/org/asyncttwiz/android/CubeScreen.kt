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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.delay
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.asyncttwiz.storage.DeckSection

@Composable
internal fun ManageCollectionScreen(cubes: CubeController, decks: DeckLibraryController?,
    importer: DeckImportController?, landscape: Boolean, navigation: (() -> Unit)?, catalog: CatalogViewModel?,
    draftCube: ((String) -> Unit)? = null, initiallyCubes: Boolean = false) {
    var cubeTab by remember(initiallyCubes) { mutableStateOf(initiallyCubes) }
    if (cubeTab && (cubes.state.editing || cubes.state.selected != null)) {
        CubeScreen(cubes, navigation, draftCube, landscape); return
    }
    if (!cubeTab && importer?.state?.active == true) {
        DeckImportScreen(importer, landscape, navigation, catalog); return
    }
    if (!cubeTab && decks?.state?.selected != null) {
        SavedDeckScreen(decks, navigation, importDeck = importer?.let { editor -> { detail -> decks.dismiss(); editor.openEditor(detail) } }); return
    }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Manage decks", navigation) {
            if (cubeTab) ScreenCreateAction("New cube", "add", cubes::newCube, Modifier.testTag("new-cube"), enabled = !cubes.state.busy)
            else if (importer != null) ScreenCreateAction("Import deck", "import", importer::open, Modifier.testTag("import-deck"))
        }
        TabRow(selectedTabIndex = if (cubeTab) 1 else 0) {
            Tab(selected = !cubeTab, onClick = { cubeTab = false }, text = { Text("Decks") })
            Tab(selected = cubeTab, onClick = { cubeTab = true }, text = { Text("Cubes") })
        }
        Box(Modifier.weight(1f)) {
            if (cubeTab) CubeCollection(cubes) else if (decks != null) SavedDeckCollection(decks)
        }
    }
}

@Composable
private fun CubeCollection(controller: CubeController) {
    val state = controller.state
    LaunchedEffect(controller) { controller.refresh() }
    Box(Modifier.fillMaxSize()) {
        InitialLoadArea(state.loaded, failed = state.error != null, retry = controller::refresh, loading = state.busy) {
            LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Text("CUBES (${state.cubes.size})", style = MaterialTheme.typography.labelMedium)
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
internal fun CubeScreen(controller: CubeController, navigation: (() -> Unit)?, draftCube: ((String) -> Unit)? = null,
    landscape: Boolean = false) {
    val state = controller.state
    BackHandler { controller.back() }
    if (state.editing) { CubeImportScreen(controller, navigation, landscape); return }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader("Saved cube", navigation) {
                ScreenOutlinedButton(onClick = controller::edit, enabled = state.detail != null && !state.busy) {
                    EntryIcon(null, "edit"); Spacer(Modifier.width(6.dp)); Text("Edit")
                }
            }
            InitialLoadArea(state.detail != null, Modifier.weight(1f), state.error != null, controller::retry, loading = state.busy) {
                val detail = state.detail
                if (detail != null) LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(detail.saved.cube.name, style = MaterialTheme.typography.titleLarge)
                            Text("${detail.saved.cube.totalCopies} total copies")
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ScreenOutlinedButton(onClick = { controller.dialog(CubeDialog.RENAME) }) {
                                    EntryIcon(null, "rename"); Spacer(Modifier.width(6.dp)); Text("Rename")
                                }
                                TextButton(onClick = { controller.dialog(CubeDialog.DELETE) }, Modifier.heightIn(min = 48.dp),
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Delete cube") }
                            }
                            if (draftCube != null) ScreenButton(onClick = { draftCube(detail.saved.cube.id) }) {
                                EntryIcon(null, "pods"); Spacer(Modifier.width(8.dp)); Text("Draft this cube")
                            }
                        }
                    }
                    items(detail.saved.entries, key = { it.definition }) { entry ->
                        Text("${entry.quantity} ${detail.definitions.getValue(entry.definition).name}")
                        HorizontalDivider()
                    }
                }
            }
        }
        if (state.dialog == null) FloatingTransientError(state.error, controller::dismissError, Modifier.align(Alignment.BottomCenter))
    }
    if (state.dialog != null) {
        val deleting = state.dialog == CubeDialog.DELETE
        ScreenDialog(controller::dismiss, error = state.error, dismissError = controller::dismissError,
            title = if (deleting) "Delete cube?" else "Rename cube", actions = {
                DialogCancelButton(if (deleting) "Keep cube" else "Cancel", controller::dismiss, enabled = !state.busy)
                DialogMainButton(if (deleting) "Delete cube" else "Save name", controller::confirm, enabled = !state.busy, danger = deleting)
            }) {
            if (deleting) Text("Delete ${state.detail?.saved?.cube?.name}? Deletion cannot be undone.")
            else {
                val name = rememberDialogField(state.name)
                OutlinedTextField(name.value, { name.value = it; controller.name(it.text) }, Modifier.fillMaxWidth().dialogField(name),
                    label = { Text("Cube name") }, singleLine = true)
            }
        }
    }
}

/** New/edit cube: the deck import screen (name only, cubes have no cover), with Cube Cobra as the link source. */
@Composable
private fun CubeImportScreen(controller: CubeController, navigation: (() -> Unit)?, landscape: Boolean) {
    val state = controller.state
    val clipboard = LocalClipboardManager.current
    LaunchedEffect(state.linkDialog) {
        if (state.linkDialog) controller.prefillCobra { clipboard.getText()?.text }
    }
    // Pasted lists resolve on their own, like deck lists; a failed attempt waits for the next edit.
    LaunchedEffect(state.text, state.includeSideboard, state.busy) {
        delay(400)
        val current = controller.state
        if (current.text.isNotBlank() && current.result == null && current.error == null && !current.busy) controller.lookup()
    }
    ImportScaffold(if (state.target == null) "New cube" else "Edit cube", navigation, landscape,
        controller::openLink, !state.saving, controller.canSave, state.saving, controller::save, "save-cube",
        input = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ScreenTextField(state.name, controller::name, Modifier.weight(1f).testTag("cube-name")
                    .semantics { contentDescription = "Cube name" }, placeholder = "Cube name")
                ImportSideboardToggle(state.includeSideboard, controller::sideboard)
            }
            ImportListField(state.text, controller::text, "Card list", "cube-list")
        },
        feedback = {
            ImportError(state.error)
            ImportResultSummary(state.result, deck = false, lineNumbers = true)
        })
    if (state.linkDialog) ImportLinkDialog(state.cobraInput, controller::cobraInput, "Cube Cobra link or ID", "cube-url",
        "Paste Cube Cobra link", controller::pasteCobra, controller::importCobra,
        state.cobraInput.isNotBlank() && !state.busy, state.fetching, state.linkError, controller::closeLink)
}
