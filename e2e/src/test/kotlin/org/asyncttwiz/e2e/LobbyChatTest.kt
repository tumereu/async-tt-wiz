package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import org.asyncttwiz.protocol.GameLifecycle
import org.junit.Test

/**
 * Owner playtest, 2026-11-22 (LOB-271, LOB-163): a game's chat before it starts: its lobby's header has a chat button with
 * the unread dot, opening the game's chat; a message written there reaches the other player's lobby.
 */
class LobbyChatTest : E2e() {
    private fun E2ePlayer.openLobby() {
        val game = this@LobbyChatTest.game
        waitUntil("$name's menu") { shows(hasTestTag("menu-GAMES")) }
        tagged("menu-GAMES").performClick()
        waitUntil("$name's game in the Games list") { shows(hasTestTag("new-game")) && showsText(game.gameName) }
        nodes(hasText(game.gameName)).onLast().performScrollTo().performClick()
        waitUntil("$name's lobby of the game, with its chat button") {
            lobby.state.detail?.id == game.id && lobby.state.detail?.lifecycle == GameLifecycle.LOBBY &&
                game.id in lobby.state.connectedGames && shows(hasTestTag("lobby-chat-button"))
        }
    }

    // LOB-271, LOB-163, LOB-069, LOB-138: Alice writes in the lobby's chat before the game starts; Bob's lobby lights the chat
    // button's dot, his chat shows her message (which reads it: the dot goes and Alice sees it marked Read)
    @Test fun aMessageWrittenInTheLobbyReachesTheOtherPlayersLobby() {
        startGame(ready = false)
        alice.openLobby()
        bob.openLobby()
        kotlin.test.assertFalse(bob.shows(hasTestTag("lobby-chat-unread")), "nothing unread yet")

        alice.tagged("lobby-chat-button").performClick()
        waitUntil("Alice's chat open in the lobby") { alice.lobby.state.chatOpen && alice.shows(androidx.compose.ui.test.hasContentDescription("Chat message draft")) }
        alice.described("Chat message draft").performTextInput("Cube or constructed?")
        waitUntil("Alice's draft") { alice.lobby.state.chat.draft?.text == "Cube or constructed?" }
        alice.described("Send").performClick()
        waitUntil("Alice's message accepted") { alice.lobby.state.chat.messages.any { it.text == "Cube or constructed?" } }

        waitUntil("the unread dot on Bob's lobby chat button") { bob.shows(hasTestTag("lobby-chat-unread")) }
        bob.tagged("lobby-chat-button").performClick()
        waitUntil("Alice's message in Bob's chat, read") {
            bob.showsText("Cube or constructed?") && bob.lobby.state.chat.unread == 0 && bob.lobby.state.detail?.lifecycle == GameLifecycle.LOBBY
        }
        bob.described("Close chat").performClick()
        waitUntil("Bob's chat closed, no dot left") { !bob.lobby.state.chatOpen && !bob.shows(hasTestTag("lobby-chat-unread")) }
        waitUntil("Alice's message read by Bob") { alice.nodes(hasText("Read")).fetchSemanticsNodes().size == 1 }
    }
}
