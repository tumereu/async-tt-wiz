package org.asyncttwiz.android

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.asyncttwiz.protocol.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.*

/** LOB-271, LOB-163 (owner, 2026-11-22): a game's chat before it starts, from a chat button in its lobby's header. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class LobbyChatTest {
    @get:Rule val compose = createComposeRule()

    // LOB-271, LOB-163, LOB-067: the lobby's header has the chat button with the unread dot; it opens the game's chat over the
    // lobby, where the other player's message shows
    @Test fun theLobbysChatButtonShowsTheUnreadDotAndOpensTheGamesChat() {
        val fixture = ScreenFixtures().also { it.configure("lobby-select-deck") }
        val game = fixture.lobby.state.detail!!
        assertNull(game.playId)
        fixture.lobby.seed(fixture.lobby.state.copy(chatLoaded = true, chat = ChatPresentation(unread = 1, messages = listOf(
            ChatMessage("m1", game.id, null, "b", "Cube or constructed tonight?", "5", "2026-10-31T09:12:00Z")))))
        try {
            compose.setContent { ScreenTheme { NoRipples { WaitingLobby(fixture.lobby, "a", null) } } }
            compose.onNodeWithTag("lobby-chat-button").assertIsDisplayed()
            compose.onNodeWithTag("lobby-chat-unread", useUnmergedTree = true).assertIsDisplayed()
            captureShot("$SCREENSHOTS/chat/lobby-chat-button.png",
                "alex in the lobby of Crackling Mist, before the game starts: the header's chat button has a dot for riley's unread message.")
            compose.onNodeWithTag("lobby-chat-button").performClick()
            compose.waitForIdle()
            assertTrue(fixture.lobby.state.chatOpen)
            compose.onNodeWithText("Cube or constructed tonight?").assertIsDisplayed()
            captureShot("$SCREENSHOTS/chat/lobby-chat.png",
                "alex tapped the chat button in Crackling Mist's lobby: the game's chat with riley's message shows over the lobby as a side panel.")
            compose.onNodeWithContentDescription("Close chat").performClick()
            compose.waitForIdle()
            assertFalse(fixture.lobby.state.chatOpen)
        } finally { fixture.lobby.close() }
    }

    // LOB-271: a declined invitation's lobby has no chat button (the other player isn't in the game)
    @Test fun aDeclinedInvitationsLobbyHasNoChatButton() {
        val fixture = ScreenFixtures().also { it.configure("waiting-invite") }
        try {
            compose.setContent { ScreenTheme { WaitingLobby(fixture.lobby, "a", null) } }
            compose.onNodeWithText(fixture.lobby.state.detail!!.gameName).assertIsDisplayed()
            compose.onNodeWithTag("lobby-chat-button").assertDoesNotExist()
        } finally { fixture.lobby.close() }
    }
}
