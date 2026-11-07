package org.asyncttwiz.android

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.asyncttwiz.protocol.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** LOB-136, LOB-137, LOB-139, LOB-140 (owner, 2026-11-07): chat messages as speech bubbles on the table, and the
 * composer's quick messages. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class ChatBubbleTest {
    @get:Rule val compose = createComposeRule()
    private val game = TableFixture.game
    private val play = TableFixture.play(hand = 3, board = 2)

    private fun message(revision: Int, author: String, text: String) =
        ChatMessage("m$revision", game.id, game.playId!!, author, text, revision.toString(), "date")

    /** The table of [play] for Alice ("a") with Bob's messages read through [readThrough] and the bubble taps going to
     * a [GameChat] as on a phone; [sent]: a message Alice just sent. */
    private inner class Harness(messages: List<ChatMessage>, readThrough: Int, sent: String? = null) {
        val chat = GameChat().also { it.accept(ChatReadState(game.id, readThrough.toString(),
            (messages.maxOfOrNull { m -> m.revision.toInt() } ?: 0).toString(),
            messages.count { m -> m.author != "a" && m.revision.toInt() > readThrough })) }
        val transcript = IndexedChatMessages().upsert(messages)
        var presentation by mutableStateOf(chat.presentation(game.id, "a", transcript, emptyList()))
        var opened: String? = null
        init { sent?.let { chat.sent(game.id, it, fromDraft = false); refresh() } }
        fun refresh() { presentation = chat.presentation(game.id, "a", transcript, emptyList()) }
        fun bubbles() = TableChatBubbles(presentation.bubble, presentation.sent, tableShown = true, authorName = "Bob",
            read = { chat.presented(transcript, setOf(it)); refresh() }, open = { opened = it },
            sentShown = { chat.sentShown(game.id, it); refresh() })
    }

    private fun render(harness: Harness, landscape: Boolean = false) {
        val board = engineTableBoard(play, "a")
        val toolkit = TableToolkitController("a", { board }, { true }, { _, _ -> true })
        compose.setContent { MaterialTheme {
            EngineFixtureTable(play, "a") {
                TableSurface(game, board, "a", landscape, Modifier.fillMaxSize(), connected = true, onIntent = toolkit::handle,
                    toolkit = toolkit, runtime = TableRuntimeHooks(chatBubbles = harness.bubbles()))
            }
        } }
        compose.waitForIdle()
    }

    private val bubble = hasTestTag("chat-bubble")

    // LOB-136: Bob's two unread messages: the bubble shows the oldest with 1/2; a tap reads it and shows the next, the
    // last tap leaves no bubble
    @Test fun tappingTheBubbleReadsEachMessageInTurn() {
        val harness = Harness(listOf(message(1, "b", "Thinking…"), message(2, "a", "Sure"), message(3, "b", "Got a response")), 0)
        render(harness)
        compose.onNode(bubble).assertContentDescriptionEquals("Chat bubble: Bob: Thinking…, 1/2")
        compose.onNode(bubble).performClick()
        compose.onNode(bubble).assertContentDescriptionEquals("Chat bubble: Bob: Got a response, 2/2")
        assertEquals(1, harness.presentation.unread)
        compose.onNode(bubble).performClick()
        compose.onNode(bubble).assertDoesNotExist()
        assertEquals(0, harness.presentation.unread, "the chat's dot clears")
    }

    // LOB-136: holding the bubble opens the chat at its message
    @Test fun holdingTheBubbleOpensTheChatAtIt() {
        val harness = Harness(listOf(message(1, "b", "One sec")), 0)
        render(harness)
        compose.onNode(bubble).performTouchInput { longClick() }
        compose.waitForIdle()
        assertEquals("m1", harness.opened)
        compose.onNode(bubble).assertExists()
    }

    // LOB-137: your sent message stays about 3 s, then fades and goes (on the test clock)
    @Test fun ownSentMessageFadesAfterAboutThreeSeconds() {
        val harness = Harness(emptyList(), 0, sent = "GG")
        compose.mainClock.autoAdvance = false
        render(harness)
        val own = hasTestTag("own-chat-bubble")
        compose.onNode(own).assertExists()
        compose.mainClock.advanceTimeBy(ChatBubbleRules.OWN_SHOWN_MILLIS - 500)
        compose.onNode(own).assertExists()
        compose.mainClock.advanceTimeBy(500L + ChatBubbleRules.OWN_FADE_MILLIS + 100)
        compose.waitForIdle()
        compose.onNode(own).assertDoesNotExist()
        assertEquals(null, harness.presentation.sent, "not shown again")
    }

    // LOB-136, LOB-140: the other player's bubble with 1/2 under their life square, off the cards
    @Test fun opponentBubbleShot() {
        render(Harness(listOf(message(1, "b", "Thinking… give me a moment, I have a lot of triggers to order here and then I " +
            "want to look at your graveyard again before I decide what to block with"),
            message(2, "b", "Your move?")), 0))
        compose.onRoot().captureShot("$SCREENSHOTS/chat/bubble-opponent.png",
            "Portrait table with two unread messages from Bob: a white speech bubble outlined in his red shows the older " +
                "one over four lines (cut with …) and 1/2 beside it, just below his hand row with its tail up towards his " +
                "life square, as the room right under the square would cover his hand's cards.")
    }

    // LOB-137: your own bubble halfway through its fade
    @Test fun ownFadingBubbleShot() {
        val harness = Harness(emptyList(), 0, sent = "No responses")
        compose.mainClock.autoAdvance = false
        render(harness)
        compose.mainClock.advanceTimeBy(ChatBubbleRules.OWN_SHOWN_MILLIS + ChatBubbleRules.OWN_FADE_MILLIS / 2)
        compose.onRoot().captureShot("$SCREENSHOTS/chat/bubble-own-fading.png",
            "Portrait table about 3.2 s after you sent the quick message No responses: its bubble, outlined in your green " +
                "above your life square, is halfway faded out.")
    }

    // LOB-136, LOB-137, LOB-140: in landscape the bubbles follow the life squares in the bars, under the top bar's and above
    // the bottom bar's, off the cards
    @Config(qualifiers = "w892dp-h412dp-land-mdpi")
    @Test fun landscapeBubblesShot() {
        render(Harness(listOf(message(1, "b", "GG")), 0, sent = "Thinking…"), landscape = true)
        val shown = compose.onNode(bubble).fetchSemanticsNode().boundsInRoot
        val own = compose.onNode(hasTestTag("own-chat-bubble")).fetchSemanticsNode().boundsInRoot
        assertTrue(shown.top >= 48f && own.bottom <= 412f - 56f, "$shown $own")
        compose.onRoot().captureShot("$SCREENSHOTS/chat/bubble-landscape.png",
            "Landscape table right after you sent Thinking… with Bob's GG unread: his red-outlined bubble hangs under his " +
                "life square in the top bar, yours (green) sits above your life square in the bottom bar, both clear of cards.")
    }
}

/** LOB-138, LOB-139: the chat's Read marks; its quick messages moved to your own Life panel. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class ChatQuickMessagesTest {
    @get:Rule val compose = createComposeRule()
    private val lobby = ScreenFixtures().lobby
    private val game = LiveTableFixture.game

    // LOB-139, LOB-138: no quick-message chips in the chat; your message Bob read says Read
    @Test fun chatHasNoQuickMessagesAndMarksRead() {
        val messages = IndexedChatMessages().upsert(listOf(
            ChatMessage("m1", game.id, game.playId!!, "b", "Your move?", "1", "date"),
            ChatMessage("m2", game.id, game.playId!!, "a", "One sec", "2", "date"),
            ChatMessage("m3", game.id, game.playId!!, "a", "Got a response", "3", "date")))
        lobby.seed(lobby.state.copy(selected = game.id, detail = game, chatOpen = true, chatLoaded = true,
            chat = ChatPresentation(messages = messages, peerReadThrough = 2)))
        compose.setContent { NoRipples { ChatPanel(lobby, "a", contentInsets = WindowInsets(0)) } }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Quick messages").assertDoesNotExist()
        for (text in listOf("Thinking…", "No responses", "GG"))
            assertTrue(compose.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().isEmpty(), text)
        assertEquals(1, compose.onAllNodesWithText("Read").fetchSemanticsNodes().size, "only the message Bob read")
    }
}
