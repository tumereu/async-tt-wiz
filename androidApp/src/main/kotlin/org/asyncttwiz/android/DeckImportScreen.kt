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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.asyncttwiz.storage.*
import org.asyncttwiz.protocol.DeckImportMode

@Composable
internal fun DeckImportScreen(controller: DeckImportController, landscape: Boolean,
    openNavigation: (() -> Unit)?, catalog: CatalogViewModel?) {
    val state = controller.state
    val keyboard = LocalSoftwareKeyboardController.current
    val clipboard = LocalClipboardManager.current
    LaunchedEffect(state.fromUrl) {
        if (state.fromUrl) controller.prefillUrl { clipboard.getText()?.text }
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
    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(if (state.editing) "Edit deck" else if (state.target == null) "Import deck" else "Import decklist", openNavigation) {
            ScreenButton(onClick = { keyboard?.hide(); controller.save() }, enabled = state.result?.resolved == true && !state.busy && !state.saving,
                modifier = Modifier.testTag("save-deck")) {
                EntryIcon("Save deck", "save")
            }
        }
        TabRow(selectedTabIndex = if (state.fromUrl) 0 else 1) {
            Tab(state.fromUrl, onClick = { controller.fromUrl(true) }, text = { Text("From URL") })
            Tab(!state.fromUrl, onClick = { controller.fromUrl(false) }, text = { Text("Paste list") })
        }
        if (state.fromUrl) {
            Column(Modifier.weight(1f).fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(state.url, controller::url, Modifier.fillMaxWidth().testTag("deck-url"),
                    label = { Text("Deck URL or site:ID") }, singleLine = true)
                ScreenButton(onClick = { keyboard?.hide(); controller.importUrl() }, enabled = state.url.isNotBlank() && !state.busy && !state.saving) {
                    EntryIcon("Import deck", "import")
                }
            }
        } else {
            DeckMetadataHeader(state.name, controller.currentCover, controller::name,
                if (state.result?.resolved == true) controller::chooseCover else null)
            if (state.target != null && !state.editing) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
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
            val list: @Composable ColumnScope.() -> Unit = {
                ScreenTextField(state.text, controller::edit, singleLine = false,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth().heightIn(min = if (landscape) 200.dp else 260.dp)
                        .testTag("deck-list").semantics { contentDescription = "Deck list" })
                // Always reserved and independently scrollable: any number of line issues fits here.
                Column(Modifier.fillMaxWidth().height(80.dp).verticalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
                    state.result?.issues?.forEach { issue ->
                        Text((if (issue.line > 0) "Line ${issue.line}: " else "") + issue.message,
                            color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Box(Modifier.fillMaxWidth().height(48.dp)) {
                    if (state.lookupFailed || state.result?.resolved == false) ScreenOutlinedButton(onClick = { keyboard?.hide(); controller.lookup() },
                        enabled = !state.busy && !state.saving && catalog?.state?.busy != true, modifier = Modifier.testTag("retry-lookup")) {
                        EntryIcon("Retry lookup", "refresh")
                    }
                }
            }
            // Route and screen properties alone determine columns; lookup never changes geometry.
            if (landscape) Row(Modifier.weight(1f).fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()), content = list)
                Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
                    if (catalog != null) CatalogControls(catalog)
                }
            } else Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
                list()
                if (catalog != null) {
                    Spacer(Modifier.height(16.dp))
                    CatalogControls(catalog)
                }
            }
        }
    }
    FloatingTransientError(state.error ?: catalog?.state?.status?.takeIf { catalog.state.error },
        { if (state.error != null) controller.dismissError() else catalog?.dismissError() }, Modifier.align(Alignment.BottomCenter))
    }
    if (state.discard) ScreenDialog(controller::keepEditing) {
        Text("Discard unsaved changes?", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ScreenOutlinedButton(controller::keepEditing, Modifier.weight(1f)) { Text("Keep editing") }
            ScreenButton(controller::exit, Modifier.weight(1f)) { Text("Discard") }
        }
    }
}
