package org.asyncttwiz.android

import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import kotlin.test.Test
import kotlin.test.assertEquals

class MessageKeyboardTest {
    // LOB-272: the chat fields (their Send key kept) and the undo request's message start sentences with a capital letter
    @Test fun messagesCapitalizeSentences() {
        assertEquals(KeyboardCapitalization.Sentences, MessageKeyboard.capitalization)
        val chat = imeOptions(ImeAction.Send, MessageKeyboard)
        assertEquals(KeyboardCapitalization.Sentences, chat.capitalization)
        assertEquals(ImeAction.Send, chat.imeAction)
    }
}
