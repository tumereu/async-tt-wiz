package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
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

    // LOB-136, LOB-137 (owner, 2026-11-08): the bubble lies at Bob's life square over his hand's cards on Alice's table, and
    // a tap on it reads it without touching the card under it; Bob's own bubble lies above his square
    @Test fun aBubbleOverAHandCardIsReadByATapThatTouchesNoCard() {
        startGame()
        bothOpen()
        bob.described("Chat, ", substring = true).performClick()
        waitUntil("Bob's chat open") { bob.shows(hasContentDescription("Chat message draft")) }
        bob.described("Chat message draft").performTextInput("Give me a moment, I have a lot of triggers to order here")
        waitUntil("Bob's draft") { bob.lobby.state.chat.draft != null }
        bob.described("Send").performClick()
        waitUntil("Bob's message accepted") { bob.lobby.state.chat.messages.size == 1 }
        bob.described("Close chat").performClick()
        waitUntil("Bob's own bubble above his life square") { bob.shows(hasTestTag("own-chat-bubble")) }
        val square = bob.node(hasContentDescription("${bob.name}, life 20", substring = true)).fetchSemanticsNode().boundsInRoot
        assertTrue(bob.tagged("own-chat-bubble").fetchSemanticsNode().boundsInRoot.bottom <= square.top, bob.screen())
        waitUntil("Bob's bubble on Alice's table") { alice.shows(bubble) }
        val at = alice.node(bubble).fetchSemanticsNode().boundsInRoot
        val cards = alice.nodes(hasContentDescription(", owner ${bob.name}, ", substring = true)).fetchSemanticsNodes()
        assertTrue(cards.any { it.boundsInRoot.overlaps(at) }, "over Bob's hand: $at ${alice.screen()}")
        alice.node(bubble).performClick()
        waitUntil("Alice read it") { !alice.shows(bubble) && alice.lobby.state.chat.unread == 0 }
        assertTrue(!alice.shows(hasContentDescription("Card action ring")), "no card's menu: ${alice.screen()}")
    }

    // LOB-139 (owner, 2026-11-08): Write in the Life panel opens the chat ready to type: its input has the focus, and the
    // text typed is the draft
    @Test fun writeOpensTheChatWithItsInputFocused() {
        startGame()
        bothOpen()
        bob.node(hasContentDescription("${bob.name}, life 20", substring = true)).performClick()
        waitUntil("Bob's Life panel") { bob.shows(hasContentDescription("Quick messages")) }
        pastPopupGuard()
        bob.described("Write a message").performClick()
        waitUntil("Bob's chat with its input focused") { bob.shows(hasContentDescription("Chat message draft") and isFocused()) }
        bob.described("Close chat").performClick()
        waitUntil("Bob's chat closed") { !bob.shows(hasContentDescription("Chat message draft")) }
        // Opened from the chat button it isn't focused
        bob.described("Chat, ", substring = true).performClick()
        waitUntil("Bob's chat open") { bob.shows(hasContentDescription("Chat message draft")) }
        run(skip = java.time.Duration.ofSeconds(1))
        assertTrue(!bob.shows(hasContentDescription("Chat message draft") and isFocused()), "the chat button only opens the chat")
    }
}
