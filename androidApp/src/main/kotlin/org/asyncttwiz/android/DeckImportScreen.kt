package org.asyncttwiz.android

import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.asyncttwiz.storage.*

@Composable
internal fun DeckImportScreen(controller: DeckImportController, landscape: Boolean,
    openNavigation: (() -> Unit)?, catalog: CatalogViewModel?) {
    val state = controller.state
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(state.text) {
        if (state.text.isNotBlank() && state.result == null && !state.preview) {
            kotlinx.coroutines.delay(400); controller.lookup()
        }
    }
    BackHandler { controller.back() }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (openNavigation != null) IconButton(onClick = openNavigation) { EntryIcon("Open navigation", "menu") }
            Text("Import deck", Modifier.weight(1f).padding(12.dp).testTag("import-title"),
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            IconButton(onClick = { keyboard?.hide(); controller.exit() }) { EntryIcon("Exit without saving", "close") }
        }
        val feedback: @Composable ColumnScope.() -> Unit = {
            if (!state.preview) {
                if (state.busy) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Looking up card records…") }
                state.result?.issues?.forEach { issue ->
                    Text((if (issue.line > 0) "Line ${issue.line}: " else "") + issue.message,
                        color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 6.dp))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { keyboard?.hide(); controller.review() }, enabled = state.result?.resolved == true && !state.busy,
                        modifier = Modifier.weight(1f).testTag("review-import")) { EntryIcon(null, "decks"); Text("Review import") }
                    OutlinedButton(onClick = { keyboard?.hide(); controller.lookup() }, enabled = !state.busy,
                        modifier = Modifier.weight(1f).testTag("retry-lookup")) { Text("Retry lookup") }
                }
                if (state.result?.issues?.isNotEmpty() == true && catalog != null) CatalogControls(catalog)
            } else {
                OutlinedTextField(state.name, controller::name, label = { Text("Deck name") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("deck-name"))
                Text("Saving decks is not available in this build yet.", Modifier.padding(vertical = 12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {}, enabled = false, modifier = Modifier.weight(1f)) { Text("Save deck") }
                    OutlinedButton(onClick = { keyboard?.hide(); controller.editList() }, modifier = Modifier.weight(1f).testTag("edit-pasted-list")) {
                        Text("Edit pasted list")
                    }
                }
            }
        }
        val list: @Composable ColumnScope.() -> Unit = {
            if (!state.preview) {
                OutlinedTextField(state.text, controller::edit, label = { Text("Deck list") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = if (landscape) 200.dp else 300.dp).testTag("deck-list"),
                    minLines = if (landscape) 7 else 12)
            } else {
                val result = checkNotNull(state.result)
                for (section in DeckSection.entries) {
                    Text("${section.title} · ${result.count(section)}", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 12.dp))
                    for (entry in result.entries.filter { it.section == section }) {
                        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(entry.quantity.toString(), Modifier.widthIn(min = 24.dp))
                            CardArtwork(ArtworkReference.from(entry.printing, result.catalog!!.checksum, "small"),
                                entry.printing.name, 48, 68, Modifier.size(36.dp, 50.dp))
                            Column(Modifier.weight(1f)) {
                                Text(entry.printing.name)
                                Text("(${entry.printing.set}) ${entry.printing.number}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
        if (landscape) Row(Modifier.weight(1f).fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()), content = list)
            Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()), content = feedback)
        } else Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
            list(); Spacer(Modifier.height(12.dp)); feedback()
        }
    }
}
