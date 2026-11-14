package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import org.junit.Test

/**
 * Owner, 2026-11-14 (LOB-148): a game's chat opens from the Games list, from its row's menu, which counts the unread
 * messages; it reads and sends as the table's chat does, without opening the game.
 */
class ChatFromGamesListTest : E2e() {
    // LOB-148, LOB-026, LOB-069, LOB-138: Alice writes; on Bob's Games list holding the game's row offers "Chat · 1 unread",
    // which opens its chat (the game stays closed) and reads her message; Bob's answer from there reaches Alice's table
    @Test fun aGamesChatOpensFromItsRowsMenuWithItsUnreadCount() {
        startGame()
        ServerSetup.gameChat(alice.account, game, "Are you there?")
        waitUntil("Bob's menu") { bob.shows(androidx.compose.ui.test.hasTestTag("menu-GAMES")) }
        bob.tagged("menu-GAMES").performClick()
        waitUntil("Bob's game row") { bob.showsText(game.gameName) && game.id in bob.lobby.state.connectedGames }
        bob.nodes(hasText(game.gameName)).onLast().performScrollTo().performTouchInput { longClick() }
        waitUntil("the game's menu with its chat and unread count") { bob.shows(hasText("Chat · 1 unread")) }
        bob.text("Chat · 1 unread").performClick()
        waitUntil("Alice's message in Bob's chat, read, the game not opened") {
            bob.showsText("Are you there?") && bob.lobby.state.selected == null && bob.lobby.state.chatUnread[game.id] == null &&
                bob.lobby.state.chat.unread == 0
        }
        bob.described("Chat message draft").performTextInput("Back in a minute")
        waitUntil("Bob's draft") { bob.lobby.state.chat.draft?.text == "Back in a minute" }
        bob.described("Send").performClick()
        waitUntil("Bob's answer sent") { bob.lobby.state.chat.messages.any { it.text == "Back in a minute" } }
        bob.described("Close chat").performClick()
        waitUntil("Bob's chat closed, back on the list") { !bob.lobby.state.chatOpen && bob.lobby.state.listChat == null && bob.showsText(game.gameName) }
        // Holding the row again: Chat with nothing unread
        bob.nodes(hasText(game.gameName)).onLast().performTouchInput { longClick() }
        waitUntil("the menu's plain Chat") { bob.shows(hasText("Chat")) && !bob.shows(hasText("Chat · ", substring = true)) }
        bob.described("Close game actions").performClick()

        // Alice's table: Bob's answer as a bubble, and her own message marked Read in her chat
        alice.openGame(game, this)
        waitUntil("Bob's answer on Alice's table") {
            alice.shows(hasContentDescription("Chat bubble: ${bob.name}: Back in a minute", substring = true))
        }
        alice.described("Chat, ", substring = true).performClick()
        waitUntil("Alice's message read by Bob") { alice.nodes(hasText("Read")).fetchSemanticsNodes().size == 1 }
    }
}
