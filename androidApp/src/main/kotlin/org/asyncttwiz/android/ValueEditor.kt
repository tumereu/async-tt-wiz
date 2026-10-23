package org.asyncttwiz.android

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import org.asyncttwiz.protocol.DeckValidation

/** The draft is independent of sync's current preview, and publishes only on completed Set. */
class ValueDraft(val scope: ManualScope, val field: ManualValue, initial: String) {
    var text by mutableStateOf(initial)
    val valid: Boolean get() = DeckValidation.text(text.trim())
}

@Composable
internal fun ValueEditor(draft: ValueDraft, current: String?, enabled: Boolean, onCancel: () -> Unit,
    onSubmit: () -> Boolean, preview: @Composable () -> Unit) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = remember { FocusRequester() }
    var input by rememberSaveable(draft, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(draft.text, TextRange(0, draft.text.length)))
    }
    fun cancel() { keyboard?.hide(); onCancel() }
    fun complete() { if (enabled && draft.valid && onSubmit()) keyboard?.hide() }
    LaunchedEffect(draft) { focus.requestFocus() }
    AlertDialog(onDismissRequest = ::cancel, title = { Text("Set ${draft.field.label}") }, text = {
        Column(Modifier.imePadding()) {
            preview()
            Text("Current: ${current ?: "no value"}", style = MaterialTheme.typography.labelSmall)
            OutlinedTextField(input, onValueChange = { input = it; draft.text = it.text },
                modifier = Modifier.fillMaxWidth().focusRequester(focus), label = { Text(draft.field.label) },
                singleLine = draft.field.kind != ManualValueKind.NOTE, minLines = if (draft.field.kind == ManualValueKind.NOTE) 3 else 1,
                maxLines = if (draft.field.kind == ManualValueKind.NOTE) 8 else 1, isError = !draft.valid,
                keyboardOptions = KeyboardOptions(imeAction = if (draft.field.kind == ManualValueKind.NOTE) ImeAction.Default else ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { complete() }))
            if (!draft.valid) Text("Text is invalid or too long", color = MaterialTheme.colorScheme.error)
        }
    }, confirmButton = { TextButton(onClick = ::complete, enabled = enabled && draft.valid) { Text("Set") } },
        dismissButton = { TextButton(onClick = ::cancel) { Text("Cancel") } })
}
