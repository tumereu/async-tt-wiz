package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.*

/** The chat bubbles' rules (LOB-136, LOB-137, LOB-138): which message the other player's bubble shows and its "k/n", the
 * read mark, the own sent message, and where a bubble goes. */
class ChatBubbleRulesTest {
    private val game = "g"
    private fun message(revision: Int, author: String, text: String = "Message $revision") =
        ChatMessage("m$revision", game, "p", author, text, revision.toString(), "date")

    // LOB-136: the other player's oldest unread message, "k/n" among this stretch's unread; your own never
    @Test fun theOtherPlayersOldestUnreadMessageShowsWithItsPlace() {
        val messages = listOf(message(1, "b"), message(2, "a"), message(3, "b"), message(4, "b"))
        assertNull(ChatBubbleRules.incoming(messages, "a", 4, 4), "nothing unread")
        val first = assertNotNull(ChatBubbleRules.incoming(messages, "a", 1, 1))
        assertEquals("m3" to "1/2", first.messageId to first.counter)
        val second = assertNotNull(ChatBubbleRules.incoming(messages, "a", 3, 1), "the first read: the next shows")
        assertEquals("m4" to "2/2", second.messageId to second.counter)
        assertNull(ChatBubbleRules.incoming(messages, "a", 4, 1), "the last read: no bubble")
        val single = assertNotNull(ChatBubbleRules.incoming(messages, "a", 3, 3))
        assertNull(single.counter, "one unread message: no counter")
        assertNull(ChatBubbleRules.incoming(listOf(message(5, "a")), "a", 0, 0), "your own message is never your bubble")
    }

    private fun chat(unread: Int, readThrough: String = "0", peer: String = "0") = GameChat().also {
        it.accept(ChatReadState(game, readThrough, "5", unread, peer))
    }

    // LOB-136, LOB-066: each tap (a read) shows the next with its k/n; the last one leaves no bubble and no unread dot
    @Test fun readingTheBubbleAdvancesAndTheLastReadClearsTheDot() {
        val messages = IndexedChatMessages().upsert(listOf(message(1, "a"), message(2, "b", "Thinking…"), message(3, "b", "GG")))
        val chat = chat(unread = 2, readThrough = "1")
        fun shown() = chat.presentation(game, "a", messages, emptyList())
        assertEquals(IncomingChatBubble("m2", "Thinking…", 1, 2), shown().bubble)
        assertEquals(2, shown().unread)
        chat.presented(messages, setOf("m2"))
        assertEquals(IncomingChatBubble("m3", "GG", 2, 2), shown().bubble)
        assertEquals(1, shown().unread)
        chat.presented(messages, setOf("m3"))
        assertNull(shown().bubble)
        assertEquals(0, shown().unread)
        assertEquals(ReadChat("m3"), chat.pendingRead(game), "the read goes to the server")
        // A new message starts a new stretch: 1/1, no counter
        val more = messages.upsert(listOf(message(4, "b", "One sec")))
        assertEquals(IncomingChatBubble("m4", "One sec", 1, 1), chat.presentation(game, "a", more, emptyList()).bubble)
    }

    // LOB-136: no bubble before the server's read state is known (every loaded message would look unread)
    @Test fun noBubbleBeforeTheReadStateIsKnown() {
        val messages = IndexedChatMessages().upsert(listOf(message(1, "b"), message(2, "b")))
        val chat = GameChat()
        assertNull(chat.presentation(game, "a", messages, emptyList()).bubble)
        chat.accept(ChatReadState(game, "1", "2", 1))
        assertEquals(IncomingChatBubble("m2", "Message 2", 1, 1), chat.presentation(game, "a", messages, emptyList()).bubble)
    }

    // LOB-138: your messages up to the other player's read cursor are read
    @Test fun readMarkFollowsTheOtherPlayersCursor() {
        val messages = IndexedChatMessages().upsert(listOf(message(1, "a"), message(2, "a")))
        val shown = chat(unread = 0, readThrough = "0", peer = "1").presentation(game, "a", messages, emptyList())
        assertTrue(shown.read(messages[0]))
        assertFalse(shown.read(messages[1]))
    }

    // LOB-137, LOB-139: a sent message (typed or quick) is the own bubble until it has faded; a quick one keeps the draft
    @Test fun sentMessageIsTheOwnBubbleUntilShown() {
        val chat = GameChat()
        val summary = LiveTableFixture.game
        chat.compose(summary, "typed")
        chat.sent(summary.id, "GG", fromDraft = false)
        val sent = assertNotNull(chat.presentation(summary.id, "a", emptyList(), emptyList()).sent)
        assertEquals("GG", sent.text)
        assertEquals("typed", chat.presentation(summary.id, "a", emptyList(), emptyList()).draft?.text, "the draft stays")
        chat.sentShown(summary.id, sent.key)
        assertNull(chat.presentation(summary.id, "a", emptyList(), emptyList()).sent)
        chat.sent(summary.id, "typed")
        assertNull(chat.presentation(summary.id, "a", emptyList(), emptyList()).draft, "a typed message clears its draft")
    }

    private fun overlaps(a: TableBounds, b: TableBounds) = a.x < b.right && b.x < a.right && a.y < b.bottom && b.y < a.bottom

    // LOB-136, LOB-137 (owner, 2026-11-08): always at the square, whatever lies there: under the opponent's, above your own;
    // only the screen's edges move it
    @Test fun bubbleAlwaysStartsAtTheLifeSquare() {
        val life = TableBounds(10f, 56f, 48f, 48f)
        assertEquals(TableBounds(10f, 56f + 48f + ChatBubbleLayout.TAIL + 1f, 120f, 44f), ChatBubbleLayout.place(life, true, 120f, 44f, 412f, 892f))
        val own = TableBounds(10f, 776f, 48f, 48f)
        assertEquals(TableBounds(10f, 776f - ChatBubbleLayout.TAIL - 1f - 44f, 120f, 44f), ChatBubbleLayout.place(own, false, 120f, 44f, 412f, 892f))
        // A square by the screen's right edge: the bubble stays on the screen
        val right = TableBounds(360f, 56f, 48f, 48f)
        assertEquals(412f - 4f, ChatBubbleLayout.place(right, true, 120f, 44f, 412f, 892f).right)
    }

    // LOB-136, LOB-137: on real tables (portrait and landscape, cards in both hands and on both battlefields) each player's
    // bubble lies at their square, with its tail towards it, hand cards under it or not
    @Test fun bubblesOnTablesStayAtTheirSquares() {
        val play = TableFixture.play(hand = 7, board = 6)
        for ((landscape, size) in listOf(false to (412f to 892f), true to (892f to 412f))) {
            val (width, height) = size
            val scene = TablePresentation.scene(TableFixture.game, tableBoard(play), "a", width, height, landscape)
            for ((player, below) in listOf("b" to true, "a" to false)) {
                val life = scene.items.single { it.id == "life/$player" }.bounds
                val bubble = ChatBubbleLayout.place(life, below, 160f, 57f, width, height)
                assertFalse(overlaps(bubble, life), "landscape=$landscape $player: $bubble off its square")
                assertTrue(bubble.x >= 4f && bubble.right <= width - 4f && bubble.y >= 0f && bubble.bottom <= height, "on the screen: $bubble")
                assertEquals(below, ChatBubbleLayout.tail(bubble, life).top, "the tail towards the square")
            }
        }
    }
}
