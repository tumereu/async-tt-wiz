package org.asyncttwiz.android

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction

/** Keyboard options of a single-line field whose action key is [action]; [base] keeps e.g. the keyboard type. */
internal fun imeOptions(action: ImeAction, base: KeyboardOptions = KeyboardOptions.Default): KeyboardOptions =
    base.copy(imeAction = action)

/**
 * The keyboard action key does what the screen's matching button does: [submit], only while that button is
 * [enabled]; otherwise the key just closes the keyboard. [keepKeyboard] leaves it open after a submit (chat).
 */
@Composable
internal fun submitKeyboardActions(enabled: Boolean = true, keepKeyboard: Boolean = false, submit: () -> Unit): KeyboardActions {
    val keyboard = LocalSoftwareKeyboardController.current
    return KeyboardActions {
        if (enabled) submit()
        if (!enabled || !keepKeyboard) keyboard?.hide()
    }
}

/** For a field that already applies itself as typed (a live search filter): the action key closes the keyboard. */
@Composable
internal fun hideKeyboardActions(): KeyboardActions = submitKeyboardActions(enabled = false) {}
