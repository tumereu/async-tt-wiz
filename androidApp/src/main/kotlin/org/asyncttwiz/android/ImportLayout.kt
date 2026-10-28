package org.asyncttwiz.android

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.asyncttwiz.storage.DeckImportResult
import org.asyncttwiz.storage.DeckSection
import org.asyncttwiz.storage.ImportIssue

/**
 * Deck and cube imports share one screen: the paste-list editor. The top bar has an
 * "Import from link" action (icon and label; an icon with a tooltip on narrow portrait screens), the input
 * column holds name/cover and the full-height list, then its result feedback and one full-width Save
 * at the bottom. Portrait stacks input above a fixed feedback area; landscape puts the feedback and
 * Save in a right column. Feedback never moves the input or Save.
 */
@Composable
internal fun ImportScaffold(title: String, navigation: (() -> Unit)?, landscape: Boolean,
    openLink: () -> Unit, linkEnabled: Boolean, canSave: Boolean, saving: Boolean, save: () -> Unit,
    saveTag: String, input: @Composable ColumnScope.() -> Unit, feedback: @Composable ColumnScope.() -> Unit) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val saveButton: @Composable () -> Unit = {
        ScreenButton(onClick = { keyboard?.hide(); focus.clearFocus(); save() },
            Modifier.fillMaxWidth().testTag(saveTag), enabled = canSave) {
            if (saving) CircularProgressIndicator(Modifier.size(20.dp), LocalContentColor.current, 2.dp)
            else EntryIcon(null, "save")
            Spacer(Modifier.width(8.dp)); Text("Save")
        }
    }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(title, navigation) {
            val open = { keyboard?.hide(); focus.clearFocus(); openLink() }
            if (landscape || LocalConfiguration.current.screenWidthDp >= 400) ScreenOutlinedButton(open, Modifier.testTag("open-import-link"), enabled = linkEnabled) {
                EntryIcon(null, "link"); Spacer(Modifier.width(8.dp)); Text("Import from link")
            } else ScreenIconButton(IMPORT_FROM_LINK, "link", open, Modifier.testTag("open-import-link"), enabled = linkEnabled)
        }
        if (landscape) Row(Modifier.weight(1f).fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp), content = input)
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp), content = feedback)
                saveButton()
            }
        } else Column(Modifier.weight(1f).fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp), content = input)
            Column(Modifier.height(96.dp).fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp), content = feedback)
            saveButton()
        }
    }
}

internal const val IMPORT_FROM_LINK = "Import from link"

/**
 * The link dialog: a link field with a paste icon inside it, progress and errors, and Cancel /
 * Import at the bottom right. The paste icon pastes the clipboard and imports at once; typed or
 * keyboard-pasted text waits for Import. A successful import closes the dialog and fills the editor.
 */
@Composable
internal fun ImportLinkDialog(value: String, onValue: (String) -> Unit, placeholder: String, tag: String,
    pasteDescription: String, pasteAndImport: (String?) -> Unit, import: () -> Unit,
    importEnabled: Boolean, fetching: Boolean, error: String?, dismiss: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val start = { keyboard?.hide(); focus.clearFocus() }
    ScreenDialog(dismiss) {
        Text(IMPORT_FROM_LINK, style = MaterialTheme.typography.titleMedium)
        ScreenTextField(value, onValue, Modifier.fillMaxWidth().testTag(tag), placeholder = placeholder,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
            keyboardActions = submitKeyboardActions(importEnabled && !fetching) { start(); import() },
            trailingIcon = {
                IconButton(onClick = { start(); pasteAndImport(clipboard.getText()?.text) }, Modifier.size(48.dp)) {
                    EntryIcon(pasteDescription, "paste")
                }
            })
        ImportError(error)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            ScreenOutlinedButton(dismiss, Modifier.testTag("cancel-link")) { Text("Cancel") }
            ScreenButton(onClick = { start(); import() }, Modifier.testTag("import-link"), enabled = importEnabled && !fetching) {
                if (fetching) CircularProgressIndicator(Modifier.size(20.dp), LocalContentColor.current, 2.dp)
                else EntryIcon(null, "download")
                Spacer(Modifier.width(8.dp)); Text("Import")
            }
        }
    }
}

/** The pasted list fills the free height; its corner paste icon only replaces the text with the clipboard. */
@Composable
internal fun ColumnScope.ImportListField(text: String, onText: (String) -> Unit, description: String, tag: String) {
    val clipboard = LocalClipboardManager.current
    ScreenTextField(text, onText, Modifier.weight(1f).fillMaxWidth().testTag(tag).semantics { contentDescription = description },
        singleLine = false, placeholder = description,
        textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
        trailingIcon = {
            IconButton(onClick = { clipboard.getText()?.text?.let(onText) }, Modifier.size(48.dp)) {
                EntryIcon("Paste from clipboard", "paste")
            }
        })
}

@Composable
internal fun ImportSideboardToggle(checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.heightIn(min = 48.dp).toggleable(checked, role = Role.Checkbox, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked, null)
        Spacer(Modifier.width(4.dp))
        Text("Include sideboard")
    }
}

@Composable
internal fun ImportError(message: String?) {
    if (message != null) Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
}

/** Counts of the resolved cards, then substitutions and the problems that block saving. */
@Composable
internal fun ImportResultSummary(result: DeckImportResult?, deck: Boolean, lineNumbers: Boolean) {
    result ?: return
    if (result.entries.isNotEmpty()) Text(if (deck) "${result.count(DeckSection.MAINBOARD)} main · ${result.count(DeckSection.SIDEBOARD)} side"
        else result.count(DeckSection.MAINBOARD).let { if (it == 1L) "1 card" else "$it cards" }, fontWeight = FontWeight.Bold)
    fun line(issue: ImportIssue) = (if (lineNumbers && issue.line > 0) "Line ${issue.line}: " else "") + issue.message
    result.substitutions.forEach {
        Text(line(it), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    }
    result.issues.forEach {
        Text(line(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
}
