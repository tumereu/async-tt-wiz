package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.*

/** The chat bubbles' rules (LOB-136, LOB-137, LOB-138, LOB-140): which message the other player's bubble shows and its
 * "k/n", the read mark, the own sent message, and where a bubble may go. */
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

    // LOB-140: the old place under the opponent's square when free; else the nearest free room, off every obstacle; none
    // when nothing is free
    @Test fun bubbleGoesUnderTheSquareOrToTheNearestFreeRoom() {
        val life = TableBounds(10f, 56f, 48f, 48f)
        val bars = listOf(TableBounds(0f, 0f, 412f, 48f), TableBounds(0f, 836f, 412f, 56f))
        val free = ChatBubbleLayout.place(life, true, 120f, 44f, 412f, 892f, ChatBubbleLayout.Obstacles(emptyList(), bars))
        assertEquals(TableBounds(10f, 56f + 48f + ChatBubbleLayout.TAIL + 1f, 120f, 44f), free)
        // Hand cards under the square: the bubble goes elsewhere, clear of them (and its tail too)
        val cards = (0 until 5).map { TableBounds(8f + it * 32f, 116f, 28f, 40f) }
        val moved = assertNotNull(ChatBubbleLayout.place(life, true, 120f, 44f, 412f, 892f, ChatBubbleLayout.Obstacles(cards, bars)))
        assertTrue(cards.none { overlaps(it, moved) }, "$moved")
        assertTrue(bars.none { overlaps(it, moved) }, "$moved")
        // Your own square: above it
        val own = TableBounds(10f, 776f, 48f, 48f)
        val above = assertNotNull(ChatBubbleLayout.place(own, false, 120f, 44f, 412f, 892f, ChatBubbleLayout.Obstacles(emptyList(), bars)))
        assertTrue(above.bottom <= own.y - ChatBubbleLayout.TAIL, "$above")
        // Everything near the square taken: no bubble
        val wall = listOf(TableBounds(0f, 0f, 412f, 892f))
        assertNull(ChatBubbleLayout.place(life, true, 120f, 44f, 412f, 892f, ChatBubbleLayout.Obstacles(wall, bars)))
    }

    // LOB-140: on real tables (portrait and landscape, cards in both hands and on both battlefields) neither player's
    // bubble covers a card, a pile or a life square
    @Test fun bubblesOnTablesNeverCoverCards() {
        val play = TableFixture.play(hand = 7, board = 6)
        for ((landscape, size) in listOf(false to (412f to 892f), true to (892f to 412f))) {
            val (width, height) = size
            val scene = TablePresentation.scene(TableFixture.game, tableBoard(play), "a", width, height, landscape)
            for ((player, below) in listOf("b" to true, "a" to false)) {
                val life = scene.items.single { it.id == "life/$player" }
                val obstacles = ChatBubbleLayout.obstacles(scene, life.id, width, height, tableBottomHeight(landscape))
                val bubble = assertNotNull(ChatBubbleLayout.place(life.bounds, below, 160f, 57f, width, height, obstacles),
                    "landscape=$landscape $player")
                val things = scene.items.filter { it.kind != TableItemKind.HAND && it.id != life.id }.flatMap { it.visible }
                assertTrue(things.none { overlaps(it, bubble) }, "landscape=$landscape $player: $bubble")
            }
        }
    }
}
