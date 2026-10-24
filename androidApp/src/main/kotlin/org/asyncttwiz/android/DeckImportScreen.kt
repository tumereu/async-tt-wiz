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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.asyncttwiz.storage.*
import org.asyncttwiz.protocol.DeckImportMode

@Composable
internal fun DeckImportScreen(controller: DeckImportController, landscape: Boolean,
    openNavigation: (() -> Unit)?, catalog: CatalogViewModel?) {
    val state = controller.state
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(catalog?.state?.installed, catalog?.state?.busy) {
        controller.catalogChanged(catalog?.state?.installed, catalog?.state?.busy == true)
    }
    LaunchedEffect(state.text, catalog?.state?.busy) {
        if (state.text.isNotBlank() && state.result == null && !state.preview) {
            kotlinx.coroutines.delay(400); controller.lookup()
        }
    }
    BackHandler { controller.back() }
    if (state.choosingCover) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader("Choose cover", openNavigation) {
                ScreenIconButton("Cancel cover selection", "close", controller::closeCover)
            }
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
                if (controller.coverCards.isEmpty()) Text("Look up the deck list to choose a cover.")
                controller.coverCards.forEach { card ->
                    val face = card.definition.faces.first()
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { controller.cover(card.digest) }
                        .padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        DeckThumbnail(ArtworkReference(face.scryfallId, 0, card.digest, "small", face.imageUri), card.definition.name, 28.dp, 40.dp)
                        Text(card.definition.name, Modifier.weight(1f))
                        if (state.cover == card.digest) EntryIcon("Selected cover", "ready")
                    }
                    HorizontalDivider()
                }
            }
        }
        return
    }
    ScreenBusyOverlay(when { state.saving -> "Saving deck…"; state.busy -> "Looking up card records…"; else -> null }) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(if (state.editing) "Edit deck" else if (state.target == null) "Import deck" else "Import decklist", openNavigation) {
            if (state.editing) ScreenButton(onClick = { keyboard?.hide(); controller.save() }, enabled = !state.saving) {
                EntryIcon(null, "save"); Spacer(Modifier.width(6.dp)); Text("Save")
            }
            ScreenIconButton("Exit without saving", "close", { keyboard?.hide(); controller.cancel() }, enabled = !state.saving)
        }
        DeckMetadataHeader(state.name, controller.currentCover, controller::name, controller::chooseCover, !state.saving)
        val feedback: @Composable ColumnScope.() -> Unit = {
            if (!state.preview) {
                state.error?.let { Text(it, Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.error) }
                state.result?.issues?.forEach { issue ->
                    OutlinedCard(Modifier.fillMaxWidth(), colors = CardDefaults.outlinedCardColors(containerColor = androidx.compose.ui.graphics.Color(0xfffff3f3))) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) { EntryIcon(null, "error"); Spacer(Modifier.width(6.dp)); Text("Import needs correction", style = MaterialTheme.typography.labelMedium) }
                            Text((if (issue.line > 0) "Line ${issue.line}: " else "") + issue.message,
                                color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                if (!state.editing) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ScreenButton(onClick = { keyboard?.hide(); controller.review() }, enabled = state.result?.resolved == true && !state.busy,
                        modifier = Modifier.weight(1f).testTag("review-import")) { EntryIcon(null, "save"); Spacer(Modifier.width(8.dp)); Text("Review import") }
                    ScreenOutlinedButton(onClick = { keyboard?.hide(); controller.lookup() }, enabled = !state.busy && catalog?.state?.busy != true,
                        modifier = Modifier.weight(1f).testTag("retry-lookup")) { EntryIcon(null, "refresh"); Spacer(Modifier.width(8.dp)); Text("Retry lookup") }
                }
                if (state.editing) TextButton(onClick = controller::lookup, enabled = !state.busy && !state.saving) { Text("Retry lookup") }
            } else {
                state.error?.let { Text(it, Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.error) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ScreenButton(onClick = { keyboard?.hide(); controller.save() }, enabled = !state.saving,
                        modifier = Modifier.weight(1f).testTag("save-deck")) { EntryIcon(null, "save"); Spacer(Modifier.width(8.dp)); Text("Save deck") }
                    ScreenOutlinedButton(onClick = { keyboard?.hide(); controller.editList() }, enabled = !state.saving,
                        modifier = Modifier.weight(1f).testTag("edit-pasted-list")) {
                        EntryIcon(null, "edit"); Spacer(Modifier.width(8.dp)); Text("Edit pasted list")
                    }
                }
            }
            if (catalog != null && (catalog.state.busy || state.result?.issues?.isNotEmpty() == true)) CatalogControls(catalog)
        }
        val list: @Composable ColumnScope.() -> Unit = {
            if (!state.preview) {
                 if (state.target != null && !state.editing) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        DeckImportMode.entries.forEach { mode ->
                            ScreenOutlinedButton(onClick = { controller.mode(mode) }, Modifier.weight(1f), enabled = !state.saving,
                                colors = ButtonDefaults.outlinedButtonColors(containerColor = if (state.mode == mode) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                    contentColor = MaterialTheme.colorScheme.onSurface)) {
                                EntryIcon(null, if (mode == DeckImportMode.OVERWRITE) "refresh" else "add"); Spacer(Modifier.width(8.dp))
                                Text(if (mode == DeckImportMode.OVERWRITE) "Overwrite" else "Merge")
                            }
                        }
                    }
                }
                Text("Deck list", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                ScreenTextField(state.text, controller::edit, singleLine = false, enabled = !state.saving,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth().heightIn(min = if (landscape) 260.dp else 320.dp)
                        .testTag("deck-list").semantics { contentDescription = "Deck list" })
            } else {
                val result = checkNotNull(state.result)
                for (section in DeckSection.entries) {
                    Text("${section.title} · ${result.count(section)}", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 12.dp))
                    for (entry in result.entries.filter { it.section == section }) {
                        Row(Modifier.fillMaxWidth().clickable(onClickLabel = "Inspect ${entry.printing.name}") { controller.inspect(entry) }
                             .heightIn(min = 48.dp).padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(entry.quantity.toString(), Modifier.widthIn(min = 24.dp))
                              Box(Modifier.width(28.dp), contentAlignment = Alignment.Center) {
                                 DeckThumbnail(ArtworkReference.from(entry.printing, result.catalog?.checksum.orEmpty(), "small"), entry.printing.name, 22.dp, 30.dp)
                            }
                            Column(Modifier.weight(1f)) {
                                Text(entry.printing.name)
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
        if (landscape && (state.target == null || state.preview || state.result?.issues?.isNotEmpty() == true)) Row(Modifier.weight(1f).fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedCard(Modifier.weight(1f).fillMaxHeight()) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), content = list)
            }
            Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.SpaceBetween) {
                if (state.preview) OutlinedCard { Column(Modifier.padding(12.dp), content = feedback) } else feedback()
            }
        } else Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
            OutlinedCard { Column(Modifier.fillMaxWidth().padding(12.dp)) {
                list()
                if (state.target != null && !state.preview) { Spacer(Modifier.height(12.dp)); feedback() }
            } }
            if (state.target == null || state.preview) {
                Spacer(Modifier.height(12.dp))
                if (state.preview) OutlinedCard { Column(Modifier.fillMaxWidth().padding(12.dp), content = feedback) } else feedback()
            }
        }
    }
    }
    DeckCardInspection(controller.inspection)
    if (state.discard) ScreenDialog(controller::keepEditing) {
        Text("Discard unsaved changes?", style = MaterialTheme.typography.titleMedium)
        Text("Your saved deck will not change.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ScreenOutlinedButton(controller::keepEditing, Modifier.weight(1f)) { Text("Keep editing") }
            ScreenButton(controller::exit, Modifier.weight(1f)) { Text("Discard") }
        }
    }
}
