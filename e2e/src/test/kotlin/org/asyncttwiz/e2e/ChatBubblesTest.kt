package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Owner-approved feature, 2026-11-07 (chat messages as bubbles on the table): Bob sends a typed message from his chat
 * and a quick message from his own Life panel (LOB-139: a tap on his life square, then GG); Alice's table shows his
 * bubble with "1/2", two taps read both (the bubble goes and her chat's dot clears), and Bob's chat marks both messages
 * Read.
 */
class ChatBubblesTest : E2e() {
    private val bubble = hasTestTag("chat-bubble")

    // LOB-136, LOB-138, LOB-139, LOB-066, LOB-069
    @Test fun bobsMessagesShowAsABubbleAliceReadsByTapping() {
        startGame()
        bothOpen()
        // Bob writes one message in his chat, which has no quick messages
        bob.described("Chat, ", substring = true).performClick()
        waitUntil("Bob's chat open") { bob.shows(hasContentDescription("Chat message draft")) }
        assertTrue(!bob.shows(hasContentDescription("Quick messages")), bob.screen())
        bob.described("Chat message draft").performTextInput("Give me a moment")
        waitUntil("Bob's draft") { bob.lobby.state.chat.draft?.text == "Give me a moment" }
        bob.described("Send").performClick()
        waitUntil("Bob's message accepted") { bob.lobby.state.chat.messages.size == 1 }
        bob.described("Close chat").performClick()
        // LOB-139, TRN-030: a quick message from his own Life panel, which closes as it goes
        bob.node(hasContentDescription("${bob.name}, life 20", substring = true)).performClick()
        waitUntil("Bob's Life panel with the quick messages") { bob.shows(hasContentDescription("Quick messages")) }
        pastPopupGuard()
        bob.described("GG").performClick()
        waitUntil("Bob's quick message accepted and his panel closed") {
            bob.lobby.state.chat.messages.size == 2 && !bob.shows(hasContentDescription("Quick messages"))
        }
        // Alice's table: Bob's older message with 1/2
        waitUntil("Bob's bubble on Alice's table") {
            alice.shows(bubble and hasContentDescription("Chat bubble: ${bob.name}: Give me a moment, 1/2"))
        }
        assertEquals(2, alice.lobby.state.chat.unread)
        assertTrue(alice.shows(hasContentDescription("Chat, 2 unread messages")), alice.screen())
        alice.node(bubble).performClick()
        waitUntil("the next message on Alice's bubble") {
            alice.shows(bubble and hasContentDescription("Chat bubble: ${bob.name}: GG, 2/2"))
        }
        alice.node(bubble).performClick()
        waitUntil("Alice's bubble gone and her chat dot cleared") {
            !alice.shows(bubble) && alice.shows(hasContentDescription("Chat, 0 unread messages"))
        }
        // The server has Alice's read state: Bob's chat marks both his messages Read
        bob.described("Chat, ", substring = true).performClick()
        waitUntil("Bob's chat showing both messages read") { bob.nodes(hasText("Read")).fetchSemanticsNodes().size == 2 }
    }
}
