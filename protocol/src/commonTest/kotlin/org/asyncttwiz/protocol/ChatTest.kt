package org.asyncttwiz.protocol

import kotlinx.serialization.encodeToString
import kotlin.test.*

class ChatTest {
    @Test fun privateReadCursorAndPagedUnreadStateRoundTrip() {
        val state = ChatReadState("game", "12", "20", 3)
        assertEquals(state, GameWire.json.decodeFromString<ChatReadState>(GameWire.json.encodeToString(state)))
        val input = ReadChat("message")
        assertEquals(input, GameWire.json.decodeFromString<ReadChat>(GameWire.json.encodeToString(input)))
        val page = ChatPage("game", emptyList(), ChatSync("20"), readState = state)
        assertEquals(page, GameWire.json.decodeFromString<ChatPage>(GameWire.json.encodeToString(page)))
        assertNull(GameWire.json.decodeFromString<ChatPage>("""{"gameId":"game","messages":[],"sync":{}}""").readState)
    }
    private val game = "10000000-0000-4000-8000-000000000001"
    private val play = "10000000-0000-4000-8000-000000000002"
    private val command = GameCommand("10000000-0000-4000-8000-000000000003", GameScope(game, playId = play),
        GameAction.SendChat("  Hello\n😀 correction  "), "10000000-0000-4000-8000-000000000004", "1")
    @Test fun chatUsesExactPlayScopeAndExistingCanonicalRequestIdentity() {
        assertTrue(GameWire.valid(command))
        val text = GameWire.json.encodeToString<GameClientMessage>(command)
        assertTrue(text.contains("sendChat")); assertEquals(command, GameWire.decodeClient(text))
        assertEquals(GameWire.digest(command), GameWire.digest(command.copy(generation = "2")))
        assertNotEquals(GameWire.digest(command), GameWire.digest(command.copy(action = GameAction.SendChat("Hello"))))
        assertNotEquals(GameWire.digest(command), GameWire.digest(command.copy(scope = GameScope(game, playId = game))))
        assertFalse(GameWire.valid(command.copy(scope = GameScope(game, preparationId = play))))
        assertFalse(GameWire.valid(command.copy(scope = command.scope.copy(invitationId = play))))
        assertFalse(GameWire.valid(command.copy(scope = command.scope.copy(preparationId = play))))
        assertFalse(GameWire.valid(command.copy(continuation = Continuation(play, true))))
    }
    @Test fun textIsPreservedNotCardNormalizedAndTransportBoundsApply() {
        assertTrue(ChatValidation.text(" \nHello\n "))
        listOf("", " \n\t", "bad\u0000text", "\uD800", "x".repeat(DeckValidation.FIELD_BYTES + 1)).forEach {
            assertFalse(GameWire.valid(command.copy(action = GameAction.SendChat(it))))
        }
    }
}
