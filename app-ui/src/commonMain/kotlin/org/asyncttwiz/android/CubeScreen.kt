package org.asyncttwiz.android

import androidx.compose.ui.text.input.ImeAction
import org.asyncttwiz.appui.BackHandler
import org.asyncttwiz.appui.ScreenBackHandler
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
import org.asyncttwiz.protocol.SavedDeckSection
import org.asyncttwiz.storage.DeckSection

@Composable
fun ManageCollectionScreen(cubes: CubeController, decks: DeckLibraryController?,
    importer: DeckImportController?, landscape: Boolean, navigation: (() -> Unit)?, catalog: CatalogViewModel?,
    draftCube: ((String) -> Unit)? = null, initiallyCubes: Boolean = false) {
    // The web keeps the tab where its addresses read and set it (WEB-001: /cubes/<id>); Android keeps it here.
    val shared = LocalCollectionTab.current
    val own = remember(initiallyCubes) { mutableStateOf(initiallyCubes) }
    if (shared != null) remember(initiallyCubes) {
        shared.value = initiallyCubes || cubes.state.selected != null || cubes.state.editing
    }
    var cubeTab by (shared ?: own)
    if (cubeTab && (cubes.state.editing || cubes.state.selected != null)) {
        // A cube's cards need card records (section 3.6)
        if (!cardRecordsReady()) { CardRecordsScreen("Cube", navigation, cubes::back); return }
        CubeScreen(cubes, navigation, draftCube, landscape); return
    }
    if (!cubeTab && importer?.state?.active == true) {
        DeckImportScreen(importer, landscape, navigation, catalog); return
    }
    if (!cubeTab && decks?.state?.selected != null) {
        if (!cardRecordsReady()) { CardRecordsScreen("Saved deck", navigation, decks::back); return }
        SavedDeckScreen(decks, navigation, importDeck = importer?.let { editor -> { detail -> decks.dismiss(); editor.openEditor(detail) } }); return
    }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Manage decks", navigation) {
            if (cubeTab) ScreenCreateAction("New cube", "add", cubes::newCube, Modifier.testTag("new-cube"),
                enabled = !cubes.state.busy && cardRecordsReady())
            else if (importer != null) ScreenCreateAction("Import deck", "import", importer::open, Modifier.testTag("import-deck"),
                enabled = cardRecordsReady())
        }
        CardRecordsNote(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp))
        TabRow(selectedTabIndex = if (cubeTab) 1 else 0) {
            Tab(selected = !cubeTab, onClick = { cubeTab = false }, text = { Text("Decks") })
            Tab(selected = cubeTab, onClick = { cubeTab = true }, text = { Text("Cubes") })
        }
        Box(Modifier.weight(1f)) {
            if (cubeTab) CubeCollection(cubes) else if (decks != null) SavedDeckCollection(decks)
        }
    }
}

/** Whether Manage decks shows its Cubes tab, where the platform keeps it (the web: by address); null keeps it in the screen. */
val LocalCollectionTab = staticCompositionLocalOf<MutableState<Boolean>?> { null }

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
                            // DRF-161
                            AutomatumSupportIcon(cube.automatum, onTap = { controller.open(cube.id) })
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
fun CubeScreen(controller: CubeController, navigation: (() -> Unit)?, draftCube: ((String) -> Unit)? = null,
    landscape: Boolean = false) {
    val state = controller.state
    ScreenBackHandler { controller.back() }
    if (state.editing) { CubeImportScreen(controller, navigation, landscape); return }
    val columns = rememberDeckViewColumns(DeckViewScreen.CUBE)
    var held by remember { mutableStateOf<Pair<String, PreparationTile>?>(null) }
    val previewScroll = rememberScrollState()
    val previewScreen = holdPreviewScreenHeight()
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader("Saved cube", navigation) {
                ScreenOutlinedButton(onClick = controller::edit, enabled = state.detail != null && !state.busy) {
                    EntryIcon(null, "edit"); Spacer(Modifier.width(6.dp)); Text("Edit")
                }
            }
            InitialLoadArea(state.detail != null, Modifier.weight(1f), state.error != null, controller::retry, loading = state.busy) {
                val detail = state.detail
                // DRF-010, LOB-056: the saved list view shared with decks: Main and Maybe sections; tap inspects, hold previews.
                if (detail != null) LazyColumn(Modifier.fillMaxSize().testTag("cube-view"), contentPadding = PaddingValues(bottom = 16.dp)) {
                    item("cube-header") {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(detail.saved.cube.name, style = MaterialTheme.typography.titleLarge)
                                    Text("${detail.saved.cube.totalCopies} total copies")
                                }
                                AutomatumSupportIcon(detail.saved.cube.automatum, Modifier.testTag("automatum-support"))
                                DeckViewColumnsControl(columns)
                            }
                            if (draftCube != null) ScreenButton(onClick = { draftCube(detail.saved.cube.id) }) {
                                EntryIcon(null, "pods"); Spacer(Modifier.width(8.dp)); Text("Draft this cube")
                            }
                        }
                    }
                    for (section in savedListSections(listOf(SavedDeckSection.MAINBOARD), detail.saved.entries.map { it.section }))
                    visualDeckSection(section, detail.saved.entries.filter { it.section == section }.sumOf { it.quantity.toLong() },
                        preparationGroups(detail.saved, section, detail.definitions), columns.count) { tile ->
                        val key = "$section/${tile.digest}"
                        Box {
                            VisualDeckCard(tile, key, section.sectionLabel(), onHold = { open ->
                                if (open) {
                                    previewScroll.dispatchRawDelta(-previewScroll.value.toFloat())
                                    held = key to tile
                                } else if (held?.first == key) held = null
                            }, onHoldScroll = { previewScroll.holdPreviewScroll(it, previewScreen) })
                            // DRF-161, LOB-144: a card Automatum can't play yet, as in the cube editor
                            CardSupportMark(tile.definition?.faces?.firstOrNull()?.scryfallId, Modifier.matchParentSize())
                        }
                    }
                }
            }
        }
        if (state.dialog == null) FloatingTransientError(state.error, controller::dismissError, Modifier.align(Alignment.BottomCenter))
    }
    held?.second?.let { tile ->
        val card = remember(tile.digest, tile.definition) {
            tile.definition?.takeIf { it.faces.isNotEmpty() }?.let { InspectedCard.retained(it) }
        }
        PodCardArtPreview(card, tile.name, previewScroll)
    }
    CubeDialogs(controller)
}

/** Rename (no longer offered on screen) and the Delete cube confirmation, which the cube editor opens (LOB-057). */
@Composable
private fun CubeDialogs(controller: CubeController) {
    val state = controller.state
    if (state.dialog != null) {
        val deleting = state.dialog == CubeDialog.DELETE
        ScreenDialog(controller::dismiss, error = state.error, dismissError = controller::dismissError,
            title = if (deleting) "Delete cube?" else "Rename cube", actions = {
                DialogCancelButton(if (deleting) "Keep cube" else "Cancel", controller::dismiss, enabled = !state.busy)
                DialogMainButton(if (deleting) "Delete cube" else "Save name", controller::confirm, enabled = !state.busy, danger = deleting,
                icon = if (deleting) "delete" else "rename")
            }) {
            if (deleting) Text("Delete ${state.detail?.saved?.cube?.takeIf { !state.editing || it.id == state.target }?.name
                ?: state.name}? Deletion cannot be undone.")
            else {
                val name = rememberDialogField(state.name)
                OutlinedTextField(name.value, { name.value = it; controller.name(it.text) }, Modifier.fillMaxWidth().dialogField(name),
                    label = { Text("Cube name") }, singleLine = true, keyboardOptions = imeOptions(ImeAction.Done),
                    keyboardActions = submitKeyboardActions(!state.busy, submit = controller::confirm))
            }
        }
    }
}

/** New/edit cube: the deck editor (name only, cubes have no cover), with Cube Cobra as the link source. */
@Composable
private fun CubeImportScreen(controller: CubeController, navigation: (() -> Unit)?, landscape: Boolean) {
    val state = controller.state
    val clipboard = LocalClipboardManager.current
    LaunchedEffect(state.linkDialog) {
        if (state.linkDialog) controller.prefillCobra { clipboard.getText()?.text }
    }
    // Pasted lists resolve on their own, like deck lists; a failed attempt waits for the next edit.
    LaunchedEffect(state.text, state.busy) {
        delay(400)
        val current = controller.state
        if (current.text.isNotBlank() && current.result == null && current.error == null && !current.busy) controller.lookup()
    }
    var tab by rememberCardListTab(state.target != null)
    CardListEditorScaffold(if (state.target == null) "New cube" else "Edit cube", navigation, landscape, controller,
        controller.search, tab, { tab = it }, controller::openLink, !state.saving, controller.canSave, state.saving,
        controller::save, "save-cube", lookupPending = state.busy || (state.text.isNotBlank() && state.result == null && state.error == null),
        error = state.error, dismissError = controller::dismissError,
        nameRow = {
            ScreenTextField(state.name, controller::name, Modifier.fillMaxWidth().testTag("cube-name")
                .semantics { contentDescription = "Cube name" }, placeholder = "Cube name",
                keyboardOptions = imeOptions(ImeAction.Next))
        },
        listField = { ImportListField(state.text, controller::text, "Card list", "cube-list", problemLines(state.text, state.result)) },
        feedback = {
            ImportError(state.error)
            ImportResultSummary(state.result, lineNumbers = true)
        }, deleteLabel = if (state.target != null) "Delete cube" else null, delete = controller::requestDelete,
        deleteEnabled = controller.canDelete, textProblems = state.result?.takeIf { it.text == state.text }?.issues?.isNotEmpty() == true)
    CubeDialogs(controller)
    if (state.linkDialog) ImportLinkDialog(state.cobraInput, controller::cobraInput, "Cube Cobra link or ID", "cube-url",
        "Paste Cube Cobra link", controller::pasteCobra, controller::importCobra,
        state.cobraInput.isNotBlank() && !state.busy, state.fetching, state.linkError, controller::closeLink)
    if (state.discard) DiscardChangesDialog(controller::keepEditing, controller::discardEdit)
}
