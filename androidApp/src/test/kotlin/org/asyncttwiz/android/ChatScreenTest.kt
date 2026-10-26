package org.asyncttwiz.android

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.asyncttwiz.protocol.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class ChatScreenTest {
    @get:Rule val compose = createComposeRule()
    private val lobby = ScreenFixtures().lobby
    private val game = LiveTableFixture.game
    private val keyboardHeight = mutableIntStateOf(0)

    private fun message(number: Int, author: String = "b") = ChatMessage("m$number", game.id,
        game.playId!!, author, "Message $number", number.toString(), "date")

    private fun open(count: Int = 100) {
        lobby.seed(lobby.state.copy(selected = game.id, detail = game, chatOpen = true,
            chat = ChatPresentation(messages = IndexedChatMessages().upsert((1..count).map { message(it) }))))
        compose.setContent { ChatPanel(lobby, "a", contentInsets = WindowInsets(bottom = keyboardHeight.intValue)) }
        compose.waitForIdle()
    }

    private fun incoming(number: Int) = compose.runOnIdle {
        val chat = lobby.state.chat
        lobby.seed(lobby.state.copy(chat = chat.copy(
            messages = (chat.messages as IndexedChatMessages).upsert(listOf(message(number))))))
    }

    @Test fun opensAtLatestAndFollowsIncomingAtBottomWithoutMaterializingTheTranscript() {
        open(10_000)
        compose.onNodeWithText("Message 10000").assertIsDisplayed()
        compose.onNodeWithText("Message 1").assertDoesNotExist()
        incoming(10_001)
        compose.onNodeWithText("Message 10001").assertIsDisplayed()
        val older = compose.onNodeWithText("Message 10000").fetchSemanticsNode().boundsInRoot
        val newest = compose.onNodeWithText("Message 10001").fetchSemanticsNode().boundsInRoot
        assertTrue(older.bottom < newest.top, "Messages remain in chronological visual order")
    }

    @Test fun incomingPreservesHistoryAnchorAndSendReturnsToLatestIncludingDeliveryAcknowledgement() {
        open()
        compose.onNodeWithContentDescription("Chat messages").performScrollToIndex(30)
        val anchor = compose.onNodeWithText("Message 70").fetchSemanticsNode().boundsInRoot
        incoming(101)
        assertEquals(anchor, compose.onNodeWithText("Message 70").fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithText("Message 101").assertDoesNotExist()

        compose.runOnIdle { lobby.seed(lobby.state.copy(chat = lobby.state.chat.copy(draft = ChatDraft(game.playId!!, "Reply")))) }
        compose.onNodeWithContentDescription("Send").performClick()
        // The existing controller/session tests cover transport. Exercise both UI delivery states.
        val command = GameCommand("reply", GameScope(game.id, playId = game.playId), GameAction.SendChat("Reply"), "stream", "1")
        compose.runOnIdle { lobby.seed(lobby.state.copy(chat = lobby.state.chat.copy(
            outgoing = listOf(ChatSubmission(command)), draft = null))) }
        compose.onNodeWithText("Reply").assertIsDisplayed()
        compose.onNodeWithText("Checking delivery…").assertIsDisplayed()
        compose.runOnIdle {
            val accepted = message(102, "a").copy(id = command.requestId, text = "Reply")
            val messages = (lobby.state.chat.messages as IndexedChatMessages).upsert(listOf(accepted))
            lobby.seed(lobby.state.copy(chat = lobby.state.chat.copy(outgoing = emptyList(), messages = messages)))
        }
        compose.onNodeWithText("Reply").assertIsDisplayed()
        compose.onNodeWithText("Checking delivery…").assertDoesNotExist()
    }

    @Test fun keyboardShrinksOnlyMessageViewportAndKeepsLatestAboveComposer() = checkKeyboardResize(300)

    @Test
    @Config(qualifiers = "w892dp-h412dp-land-mdpi")
    fun landscapeKeyboardAlsoKeepsHeaderComposerAndLatestVisible() = checkKeyboardResize(180)

    private fun checkKeyboardResize(height: Int) {
        open()
        val header = compose.onNodeWithText("Game chat").fetchSemanticsNode().boundsInRoot
        val input = compose.onNodeWithContentDescription("Chat message draft").fetchSemanticsNode().boundsInRoot
        val list = compose.onNodeWithContentDescription("Chat messages").fetchSemanticsNode().boundsInRoot
        compose.runOnIdle { keyboardHeight.intValue = height }
        compose.onNodeWithText("Game chat").assertIsDisplayed()
        compose.onNodeWithContentDescription("Chat message draft").assertIsDisplayed()
        compose.onNodeWithText("Message 100").assertIsDisplayed()
        assertEquals(header, compose.onNodeWithText("Game chat").fetchSemanticsNode().boundsInRoot)
        val resizedInput = compose.onNodeWithContentDescription("Chat message draft").fetchSemanticsNode().boundsInRoot
        val resizedList = compose.onNodeWithContentDescription("Chat messages").fetchSemanticsNode().boundsInRoot
        assertEquals(height.toFloat(), input.bottom - resizedInput.bottom, .5f)
        assertEquals(height.toFloat(), list.height - resizedList.height, .5f)
        assertEquals(list.top, resizedList.top, .5f)
        assertTrue(compose.onNodeWithText("Message 100").fetchSemanticsNode().boundsInRoot.bottom <= resizedInput.top)
        incoming(101)
        compose.onNodeWithText("Message 101").assertIsDisplayed()
        compose.runOnIdle { keyboardHeight.intValue = 0 }
        compose.onNodeWithText("Message 101").assertIsDisplayed()
        assertEquals(input, compose.onNodeWithContentDescription("Chat message draft").fetchSemanticsNode().boundsInRoot)
    }

    @Test fun shortConversationIsBottomAligned() {
        open(2)
        val last = compose.onNodeWithText("Message 2").fetchSemanticsNode().boundsInRoot
        val first = compose.onNodeWithText("Message 1").fetchSemanticsNode().boundsInRoot
        val list = compose.onNodeWithContentDescription("Chat messages").fetchSemanticsNode().boundsInRoot
        assertTrue(first.bottom < last.top)
        assertTrue(list.bottom - last.bottom < 30f, "Short conversations sit just above the composer")
    }
}
