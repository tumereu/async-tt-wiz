package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.*

class ChatPresentationTest {
    private val game = GameSummary("game", "Chat", "date", AccountIdentity("a", "A"), AccountIdentity("b", "B"),
        "round", "invite", InvitationState.ACCEPTED, GameLifecycle.ACTIVE, playId = "play")
    private fun message(id: String, author: String = "b") = ChatMessage(id, game.id, "play", author, "Text $id", id, "date")
    @Test fun openingIsNotViewingAndNewestVisiblePositionAdvancesCursor() {
        val local = GameChat(); val messages = listOf(message("1"), message("2"), message("3", "a"))
        assertEquals(2, local.presentation(game.id, "a", messages, emptyList()).unread)
        local.presented(messages, presentedChatIds(messages, listOf("pending-4", "1"), true))
        assertEquals(1, local.presentation(game.id, "a", messages, emptyList()).unread)
        local.presented(messages, presentedChatIds(messages, listOf("2"), false))
        assertEquals(1, local.presentation(game.id, "a", messages, emptyList()).unread)
        val newWhileScrolled = messages + message("4")
        local.presented(newWhileScrolled, presentedChatIds(newWhileScrolled, listOf("1"), true))
        assertEquals(2, local.presentation(game.id, "a", newWhileScrolled, emptyList()).unread)
        local.presented(newWhileScrolled, setOf("2", "4"))
        assertEquals(0, local.presentation(game.id, "a", newWhileScrolled, emptyList()).unread)
        local.presented(newWhileScrolled, setOf("1"))
        assertEquals(ReadChat("4"), local.pendingRead(game.id))
    }
    @Test fun authoritativeCursorSurvivesFreshUiAndCountsUnloadedMessagesNotOwnEchoes() {
        val local = GameChat()
        local.accept(ChatReadState(game.id, "2", "10", 5))
        val messages = listOf(message("1"), message("2"), message("9", "a"), message("10"), message("11"), message("12", "a"))
        assertEquals(6, local.presentation(game.id, "a", messages, emptyList()).unread)
        local.presented(messages, setOf("10"))
        assertEquals(1, local.presentation(game.id, "a", messages, emptyList()).unread)
        local.accept(ChatReadState(game.id, "10", "12", 1))
        assertNull(local.pendingRead(game.id))
        local.clear()
        local.accept(ChatReadState(game.id, "10", "12", 1))
        assertEquals(1, local.presentation(game.id, "a", messages, emptyList()).unread)
        local.accept(ChatReadState(game.id, "0", "10", 8)) // stale page must not resurrect unread
        assertEquals(1, local.presentation(game.id, "a", messages, emptyList()).unread)
        local.presented(messages, setOf("12")) // own visible message also advances the position
        assertEquals(0, local.presentation(game.id, "a", messages, emptyList()).unread)
    }
    @Test fun draftIsPrivateNotAcceptedAndCannotSilentlyRebindToFreshPlay() {
        val local = GameChat(); local.compose(game, "  next\nmessage  ")
        val p = local.presentation(game.id, "a", emptyList(), emptyList())
        assertTrue(p.messages.isEmpty()); assertEquals("  next\nmessage  ", p.draft!!.text)
        assertTrue(p.canSend(game, true)); assertFalse(p.canSend(game, false))
        val next = game.copy(playId = "fresh")
        local.compose(next, "still original draft")
        assertFalse(local.presentation(game.id, "a", emptyList(), emptyList()).canSend(next, true))
        local.compose(next, ""); local.compose(next, "new draft")
        assertTrue(local.presentation(game.id, "a", emptyList(), emptyList()).canSend(next, true))
        local.clear(); assertNull(local.presentation(game.id, "b", emptyList(), emptyList()).draft)
        assertEquals(1, local.presentation(game.id, "b", listOf(message("1", "a")), emptyList()).unread)
    }
}
