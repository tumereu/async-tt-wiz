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
    ScreenBusyOverlay(when { state.saving -> "Saving deck…"; state.busy -> "Looking up card records…"; else -> null }) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(if (state.target == null) "Import deck" else "Import decklist", openNavigation) {
            ScreenIconButton("Exit without saving", "close", { keyboard?.hide(); controller.exit() })
        }
        val feedback: @Composable ColumnScope.() -> Unit = {
            if (!state.preview) {
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
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ScreenButton(onClick = { keyboard?.hide(); controller.review() }, enabled = state.result?.resolved == true && !state.busy,
                        modifier = Modifier.weight(1f).testTag("review-import")) { EntryIcon(null, "save"); Spacer(Modifier.width(8.dp)); Text("Review import") }
                    ScreenOutlinedButton(onClick = { keyboard?.hide(); controller.lookup() }, enabled = !state.busy && catalog?.state?.busy != true,
                        modifier = Modifier.weight(1f).testTag("retry-lookup")) { EntryIcon(null, "refresh"); Spacer(Modifier.width(8.dp)); Text("Retry lookup") }
                }
            } else {
                 if (state.target == null) {
                     Text("Deck name", style = MaterialTheme.typography.titleSmall)
                     Spacer(Modifier.height(8.dp))
                     ScreenTextField(state.name, controller::name, enabled = !state.saving,
                         modifier = Modifier.fillMaxWidth().testTag("deck-name").semantics { contentDescription = "Deck name" })
                     Spacer(Modifier.height(12.dp))
                 }
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
                if (state.target != null) {
                    Text(state.name, style = MaterialTheme.typography.titleSmall)
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
                ScreenTextField(state.text, controller::edit, singleLine = false,
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
                            .padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(entry.quantity.toString(), Modifier.widthIn(min = 24.dp))
                             Box(Modifier.size(48.dp).clickable(onClickLabel = "Inspect ${entry.printing.name} artwork") {
                                controller.inspect(entry)
                            }.semantics { contentDescription = "Inspect artwork for ${entry.printing.name}" }, contentAlignment = Alignment.Center) {
                                 DeckThumbnail(ArtworkReference.from(entry.printing, result.catalog!!.checksum, "small"), entry.printing.name, 22.dp, 30.dp)
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
}
