package org.asyncttwiz.android

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.asyncttwiz.storage.*
import org.asyncttwiz.protocol.DeckImportMode

@Composable
internal fun DeckImportScreen(controller: DeckImportController, landscape: Boolean,
    openNavigation: (() -> Unit)?, catalog: CatalogViewModel?) {
    val state = controller.state
    val keyboard = LocalSoftwareKeyboardController.current
    val clipboard = LocalClipboardManager.current
    LaunchedEffect(state.linkDialog) {
        if (state.linkDialog) controller.prefillUrl { clipboard.getText()?.text }
    }
    LaunchedEffect(catalog?.state?.installed, catalog?.state?.busy) {
        controller.catalogChanged(catalog?.state?.installed, catalog?.state?.busy == true)
    }
    LaunchedEffect(state.text, catalog?.state?.busy) {
        if (state.text.isNotBlank() && state.result == null) {
            kotlinx.coroutines.delay(400); controller.lookup()
        }
    }
    BackHandler { controller.back() }
    if (state.choosingCover) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader("Choose cover", openNavigation)
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
                controller.coverCards.forEach { card ->
                    val face = card.definition.faces.first()
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { controller.cover(card.digest) }
                        .padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        DeckThumbnail(ArtworkReference(face.scryfallId, 0, card.digest, "small", face.imageUri), card.definition.name, 28.dp, 40.dp)
                        Text(card.definition.name, Modifier.weight(1f))
                        if (controller.currentCover?.definition == card.digest) EntryIcon("Selected cover", "ready")
                    }
                    HorizontalDivider()
                }
            }
        }
        return
    }
    val modes: @Composable () -> Unit = {
        if (state.target != null && !state.editing) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DeckImportMode.entries.forEach { mode ->
                ScreenOutlinedButton(onClick = { controller.mode(mode) }, Modifier.weight(1f), enabled = !state.saving,
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = if (mode == state.mode) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface)) {
                    EntryIcon(null, if (mode == DeckImportMode.OVERWRITE) "refresh" else "add"); Spacer(Modifier.width(8.dp))
                    Text(if (mode == DeckImportMode.OVERWRITE) "Overwrite" else "Merge")
                }
            }
        }
    }
    ImportScaffold(if (state.editing) "Edit deck" else if (state.target == null) "Import deck" else "Import decklist",
        openNavigation, landscape, controller::openLink, !state.saving, controller.canSave, state.saving, controller::save, "save-deck",
        input = {
            modes()
            DeckMetadataHeader(state.name, controller.currentCover, controller::name,
                if (state.result?.resolved == true) controller::chooseCover else null, padding = PaddingValues(0.dp))
            ImportListField(state.text, controller::edit, "Deck list", "deck-list")
        },
        feedback = {
            ImportError(state.error)
            if (state.lookupFailed) ScreenOutlinedButton(onClick = { keyboard?.hide(); controller.lookup() },
                enabled = !state.busy && !state.saving && catalog?.state?.busy != true, modifier = Modifier.testTag("retry-lookup")) {
                EntryIcon(null, "refresh"); Spacer(Modifier.width(8.dp)); Text("Retry lookup")
            }
            ImportResultSummary(state.result, deck = true, lineNumbers = true)
        })
    if (state.linkDialog) ImportLinkDialog(state.url, controller::url, "Deck link or site:ID", "deck-url", "Paste deck link",
        controller::pasteUrl, controller::importUrl, state.url.isNotBlank() && !state.saving && (!state.busy || state.fetchingUrl),
        state.fetchingUrl, state.linkError, controller::closeLink)
    if (state.discard) ScreenDialog(controller::keepEditing) {
        Text("Discard unsaved changes?", style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            ScreenOutlinedButton(controller::keepEditing) { Text("Keep editing") }
            ScreenButton(controller::exit) { Text("Discard") }
        }
    }
}
