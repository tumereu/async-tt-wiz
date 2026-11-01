package org.asyncttwiz.android

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.asyncttwiz.storage.DeckImportResult
import org.asyncttwiz.storage.ImportIssue

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

/** The pasted list fills the free height; its corner paste icon only replaces the text with the clipboard. Lines in
 * [problemLines] (numbered from 1) are underlined in red (LOB-059). */
@Composable
internal fun ColumnScope.ImportListField(text: String, onText: (String) -> Unit, description: String, tag: String,
    problemLines: Set<Int> = emptySet()) {
    val clipboard = LocalClipboardManager.current
    val error = MaterialTheme.colorScheme.error
    val underline = remember(problemLines, error) { ProblemLines(problemLines, error) }
    ScreenTextField(text, onText, Modifier.weight(1f).fillMaxWidth().testTag(tag).semantics { contentDescription = description },
        singleLine = false, placeholder = description, visualTransformation = underline,
        textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
        trailingIcon = {
            IconButton(onClick = { clipboard.getText()?.text?.let(onText) }, Modifier.size(48.dp)) {
                EntryIcon("Paste from clipboard", "paste")
            }
        })
}

/** The lines of the list a lookup found problems in, numbered from 1; none once the text changed since that lookup. */
internal fun problemLines(text: String, result: DeckImportResult?): Set<Int> =
    result?.takeIf { it.text == text }?.issues?.map { it.line }?.filter { it > 0 }?.toSet().orEmpty()

/** Draws the [lines] (numbered from 1, split as the list parser splits them) red and underlined; the text is unchanged. */
internal class ProblemLines(private val lines: Set<Int>, private val color: Color) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        if (lines.isEmpty()) return TransformedText(text, OffsetMapping.Identity)
        val styled = AnnotatedString.Builder(text)
        val raw = text.text
        var start = 0
        var number = 1
        val breaks = LINE_BREAK.findAll(raw).iterator()
        while (true) {
            val next = if (breaks.hasNext()) breaks.next() else null
            val end = next?.range?.first ?: raw.length
            if (number in lines && end > start)
                styled.addStyle(SpanStyle(color = color, textDecoration = TextDecoration.Underline), start, end)
            start = (next ?: break).range.last + 1
            number++
        }
        return TransformedText(styled.toAnnotatedString(), OffsetMapping.Identity)
    }
    override fun equals(other: Any?) = other is ProblemLines && other.lines == lines && other.color == color
    override fun hashCode() = lines.hashCode() * 31 + color.hashCode()
    private companion object { val LINE_BREAK = Regex("\\r\\n|\\n|\\r") }
}

@Composable
internal fun ImportError(message: String?) {
    if (message != null) Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
}

/** Substitutions and the problems that block saving (LOB-059: no counts; the Cards tab has them). */
@Composable
internal fun ImportResultSummary(result: DeckImportResult?, lineNumbers: Boolean) {
    result ?: return
    fun line(issue: ImportIssue) = (if (lineNumbers && issue.line > 0) "Line ${issue.line}: " else "") + issue.message
    result.substitutions.forEach {
        Text(line(it), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    }
    result.issues.forEach {
        Text(line(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
}
