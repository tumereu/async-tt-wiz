package org.asyncttwiz.android

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.coroutines.flow.first

/** Focuses the field this requester is attached to when its dialog opens (or [keys] change), and shows the soft keyboard. */
@Composable
internal fun rememberOpenFocus(vararg keys: Any?): FocusRequester {
    val requester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val window = LocalWindowInfo.current
    LaunchedEffect(requester, *keys) {
        suspend fun focus() {
            withFrameNanos { } // after the dialog's own initial focus handling
            // A disabled field cannot take focus; the dialog stays usable without it.
            runCatching { requester.requestFocus() }
            keyboard?.show()
        }
        focus()
        // The IME ignores a window without focus: repeat once the dialog window has it.
        if (!window.isWindowFocused) {
            snapshotFlow { window.isWindowFocused }.first { it }
            focus()
        }
    }
    return requester
}

/** An input dialog's text field: it opens focused, its whole text selected, with the soft keyboard shown. */
@Stable
internal class DialogFieldState(text: String, internal val focus: FocusRequester) {
    var value by mutableStateOf(TextFieldValue(text, TextRange(0, text.length)))
    val text: String get() = value.text
}

/** [text] is the initial value; later outside changes (e.g. a controller's normalization) are adopted.
 * [focusOnOpen] false keeps the keyboard closed for dialogs whose main content is not the field. */
@Composable
internal fun rememberDialogField(text: String, vararg keys: Any?, focusOnOpen: Boolean = true): DialogFieldState {
    val focus = if (focusOnOpen) rememberOpenFocus() else remember { FocusRequester() }
    val state = remember(*keys) { DialogFieldState(text, focus) }
    LaunchedEffect(state, text) { if (state.value.text != text) state.value = TextFieldValue(text, TextRange(text.length)) }
    return state
}

internal fun Modifier.dialogField(state: DialogFieldState): Modifier = focusRequester(state.focus)
