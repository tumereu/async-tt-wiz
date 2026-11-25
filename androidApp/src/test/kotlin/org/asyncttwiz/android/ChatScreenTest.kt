@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package org.asyncttwiz.android

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.asyncttwiz.protocol.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
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

    /** The text menu a selection opens (LOB-067), while it is open: its items by key. */
    private val menu = object : androidx.compose.foundation.text.contextmenu.provider.TextContextMenuProvider {
        var shown: androidx.compose.foundation.text.contextmenu.provider.TextContextMenuDataProvider? = null
        override suspend fun showTextContextMenu(dataProvider: androidx.compose.foundation.text.contextmenu.provider.TextContextMenuDataProvider) {
            shown = dataProvider
            try { kotlinx.coroutines.awaitCancellation() } finally { if (shown === dataProvider) shown = null }
        }
        fun item(key: Any): (() -> Unit)? = shown?.data()?.components
            ?.filterIsInstance<androidx.compose.foundation.text.contextmenu.data.TextContextMenuItem>()
            ?.firstOrNull { it.key == key }?.let { item -> { item.onClick(object : androidx.compose.foundation.text.contextmenu.data.TextContextMenuSession {
                override fun close() {}
            }) } }
    }

    private fun open(count: Int = 100) {
        lobby.seed(lobby.state.copy(selected = game.id, detail = game, chatOpen = true, chatLoaded = true,
            chat = ChatPresentation(messages = IndexedChatMessages().upsert((1..count).map { message(it) }))))
        compose.setContent {
            // The selection menu opens only after the platform's text classification of the selection, which runs on
            // Dispatchers.IO by default: waitForIdle doesn't wait for it, so on a loaded machine the menu came after the
            // assertion. On the composition's test dispatcher it runs within the test's clock and waitForIdle covers it.
            val testDispatcher = androidx.compose.runtime.rememberCoroutineScope()
                .coroutineContext[kotlin.coroutines.ContinuationInterceptor]!!
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.foundation.text.selection.LocalTextClassifierCoroutineContext provides testDispatcher,
                androidx.compose.foundation.text.contextmenu.provider.LocalTextContextMenuToolbarProvider provides menu) {
                ChatPanel(lobby, "a", contentInsets = WindowInsets(bottom = keyboardHeight.intValue))
            }
        }
        compose.waitForIdle()
    }

    // LOB-067 (owner, 2026-11-17): a message's text can be selected and copied: holding it selects a word, the menu's Select all
    // the whole message, and its Copy puts it on the clipboard
    @Test @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    @Config(shadows = [NoMagnifier::class])
    fun aMessagesTextCanBeSelectedAndCopied() {
        open(2)
        compose.onNodeWithText("Message 2").performTouchInput { longClick() }
        compose.waitForIdle()
        val keys = androidx.compose.foundation.text.contextmenu.data.TextContextMenuKeys
        val selectAll = assertNotNull(menu.item(keys.SelectAllKey), "holding the text selects it and offers its menu")
        compose.runOnIdle { selectAll() }
        compose.waitForIdle()
        val copy = assertNotNull(menu.item(keys.CopyKey), "the selection offers Copy")
        compose.runOnIdle { copy() }
        compose.waitForIdle()
        val clipboard = org.robolectric.RuntimeEnvironment.getApplication()
            .getSystemService(android.content.ClipboardManager::class.java)
        assertEquals("Message 2", clipboard.primaryClip?.getItemAt(0)?.text?.toString())
    }

    private fun incoming(number: Int) = compose.runOnIdle {
        val chat = lobby.state.chat
        lobby.seed(lobby.state.copy(chat = chat.copy(
            messages = (chat.messages as IndexedChatMessages).upsert(listOf(message(number))))))
    }

    // LOB-067
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

    // LOB-068
    @Test fun incomingPreservesHistoryAnchorAndSendReturnsToLatestIncludingDeliveryAcknowledgement() {
        open()
        compose.onNodeWithContentDescription("Chat messages").performScrollToIndex(30)
        val anchor = compose.onNodeWithText("Message 70").fetchSemanticsNode().boundsInRoot
        incoming(101)
        assertEquals(anchor, compose.onNodeWithText("Message 70").fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithText("Message 101").assertDoesNotExist()

        compose.runOnIdle { lobby.seed(lobby.state.copy(chat = lobby.state.chat.copy(draft = ChatDraft("Reply")))) }
        compose.onNodeWithContentDescription("Send").performClick()
        // The existing controller/session tests cover transport. Exercise both UI delivery states.
        val command = GameCommand("reply", GameScope(game.id, playId = game.playId), GameAction.SendChat("Reply"), "stream", "1")
        compose.runOnIdle { lobby.seed(lobby.state.copy(chat = lobby.state.chat.copy(
            outgoing = listOf(ChatSubmission(command)), draft = null))) }
        compose.onNodeWithText("Reply").assertIsDisplayed()
        compose.onNodeWithText("Checking delivery…").assertIsDisplayed()
        val pending = compose.onNodeWithText("Reply").fetchSemanticsNode().boundsInRoot
        compose.runOnIdle {
            val accepted = message(102, "a").copy(id = command.requestId, text = "Reply")
            val messages = (lobby.state.chat.messages as IndexedChatMessages).upsert(listOf(accepted))
            lobby.seed(lobby.state.copy(chat = lobby.state.chat.copy(outgoing = emptyList(), messages = messages)))
        }
        compose.onNodeWithText("Reply").assertIsDisplayed()
        assertEquals(pending, compose.onNodeWithText("Reply").fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithText("Checking delivery…").assertDoesNotExist()
    }

    // LOB-068, SYS-047
    @Test fun keyboardSendKeySendsLikeTheSendButton() {
        open()
        compose.onNodeWithContentDescription("Chat messages").performScrollToIndex(30)
        compose.onNodeWithText("Message 100").assertDoesNotExist()
        compose.runOnIdle { lobby.seed(lobby.state.copy(chat = lobby.state.chat.copy(draft = ChatDraft("Reply")))) }
        compose.onNodeWithContentDescription("Chat message draft")
            .assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.ImeAction,
                androidx.compose.ui.text.input.ImeAction.Send))
            .performImeAction()
        // Sending returns the transcript to the latest message, as the button does.
        compose.onNodeWithText("Message 100").assertIsDisplayed()
    }

    // LOB-067
    @Test fun keyboardShrinksOnlyMessageViewportAndKeepsLatestAboveComposer() = checkKeyboardResize(300)

    // LOB-067
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

/** The selection's magnifier, which Robolectric can't show or dismiss (no surface): nothing. */
@org.robolectric.annotation.Implements(android.widget.Magnifier::class)
class NoMagnifier {
    @org.robolectric.annotation.Implementation fun show(sourceCenterX: Float, sourceCenterY: Float) {}
    @org.robolectric.annotation.Implementation fun show(sourceCenterX: Float, sourceCenterY: Float, magnifierCenterX: Float, magnifierCenterY: Float) {}
    @org.robolectric.annotation.Implementation fun dismiss() {}
    @org.robolectric.annotation.Implementation fun update() {}
}
