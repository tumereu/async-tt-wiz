package org.asyncttwiz.android

import org.asyncttwiz.appui.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.asyncttwiz.storage.*

@Composable
fun DeckImportScreen(controller: DeckImportController, landscape: Boolean,
    openNavigation: (() -> Unit)?, catalog: CatalogViewModel?) {
    val state = controller.state
    val keyboard = LocalSoftwareKeyboardController.current
    val clipboard = LocalClipboardManager.current
    LaunchedEffect(state.linkDialog) {
        if (state.linkDialog) controller.prefillUrl { clipboard.getText()?.text }
    }
    val installing = catalog != null && !catalog.state.ready
    LaunchedEffect(catalog?.state?.installed, installing) {
        controller.catalogChanged(catalog?.state?.installed?.takeIf { !installing }, installing)
    }
    LaunchedEffect(state.text, installing) {
        if (state.text.isNotBlank() && state.result == null) {
            kotlinx.coroutines.delay(400); controller.lookup()
        }
    }
    BackHandler { controller.back() }
    var tab by rememberCardListTab(state.editing)
    if (state.choosingCover) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader("Choose cover", openNavigation)
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
                // LOB-058 (owner playtest 2026-11-09): listed alphabetically
                controller.coverCards.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.definition.name }).forEach { card ->
                    val face = card.definition.faces.first()
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { card.printing?.let(controller::cover) }
                        .padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        DeckThumbnail(ArtworkReference(face.scryfallId, 0, "small", face.imageUri), card.definition.name, 28.dp, 40.dp)
                        Text(card.definition.name, Modifier.weight(1f))
                        if (controller.currentCover?.definition == card.printing) EntryIcon("Selected cover", "ready")
                    }
                    HorizontalDivider()
                }
            }
        }
        return
    }
    CardListEditorScaffold(if (state.editing) "Edit deck" else if (state.target == null) "Import deck" else "Import decklist",
        openNavigation, landscape, controller, controller.search, tab, { tab = it }, controller::openLink, !state.saving,
        controller.canSave, state.saving, controller::save, "save-deck",
        lookupPending = state.busy || (state.text.isNotBlank() && state.result == null && !state.lookupFailed),
        error = state.error, dismissError = controller::dismissError,
        nameRow = {
            DeckMetadataHeader(state.name, controller.currentCover, controller::name,
                if (state.result?.resolved == true) controller::chooseCover else null, padding = PaddingValues(0.dp))
        },
        listField = { ImportListField(state.text, controller::edit, "Deck list", "deck-list", problemLines(state.text, state.result)) },
        feedback = {
            ImportError(state.error)
            if (state.lookupFailed) ScreenOutlinedButton(onClick = { keyboard?.hide(); controller.lookup() },
                enabled = !state.busy && !state.saving && !installing, modifier = Modifier.testTag("retry-lookup")) {
                EntryIcon(null, "refresh"); Spacer(Modifier.width(8.dp)); Text("Retry lookup")
            }
            ImportResultSummary(state.result, lineNumbers = true)
        }, deleteLabel = if (state.editing && state.target != null) "Delete deck" else null, delete = controller::requestDelete,
        deleteEnabled = controller.canDelete, textProblems = state.result?.takeIf { it.text == state.text }?.issues?.isNotEmpty() == true)
    // LOB-057: deleting a saved deck from its editor is confirmed first.
    if (state.deleteDialog) ScreenDialog(controller::dismissDelete, busyMessage = if (state.deleting) "Deleting deck…" else null,
        error = state.deleteError, dismissError = controller::dismissDeleteError, title = "Delete deck?", actions = {
            DialogCancelButton("Keep deck", controller::dismissDelete, enabled = !state.deleting)
            DialogMainButton("Delete deck", controller::confirmDelete, enabled = !state.deleting, danger = true)
        }) {
        Text(controller.savedName, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        Text("Deletion cannot be undone.")
    }
    if (state.linkDialog) ImportLinkDialog(state.url, controller::url, "Deck link or site:ID", "deck-url", "Paste deck link",
        controller::pasteUrl, controller::importUrl, state.url.isNotBlank() && !state.saving && (!state.busy || state.fetchingUrl),
        state.fetchingUrl, state.linkError, controller::closeLink)
    if (state.discard) DiscardChangesDialog(controller::keepEditing, controller::exit)
}
