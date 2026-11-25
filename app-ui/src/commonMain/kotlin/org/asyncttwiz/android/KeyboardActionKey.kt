package org.asyncttwiz.android

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization

/** Keyboard options of a single-line field whose action key is [action]; [base] keeps e.g. the keyboard type. */
fun imeOptions(action: ImeAction, base: KeyboardOptions = KeyboardOptions.Default): KeyboardOptions =
    base.copy(imeAction = action)

/** LOB-272 (owner 2026-11-25): a message a player writes (game and pod chat, an undo request's message): the phone's keyboard
 * starts each sentence with a capital letter. */
val MessageKeyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)

/**
 * The keyboard action key does what the screen's matching button does: [submit], only while that button is
 * [enabled]; otherwise the key just closes the keyboard. [keepKeyboard] leaves it open after a submit (chat).
 */
@Composable
fun submitKeyboardActions(enabled: Boolean = true, keepKeyboard: Boolean = false, submit: () -> Unit): KeyboardActions {
    val keyboard = LocalSoftwareKeyboardController.current
    return KeyboardActions {
        if (enabled) submit()
        if (!enabled || !keepKeyboard) keyboard?.hide()
    }
}

/** For a field that already applies itself as typed (a live search filter): the action key closes the keyboard. */
@Composable
fun hideKeyboardActions(): KeyboardActions = submitKeyboardActions(enabled = false) {}
