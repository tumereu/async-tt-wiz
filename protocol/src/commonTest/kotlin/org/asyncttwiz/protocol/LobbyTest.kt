package org.asyncttwiz.protocol

import kotlin.test.*
import kotlinx.serialization.encodeToString

class LobbyTest {
    @Test fun creationIntentAndUsernameOnlyMetadataRoundTrip() {
        val input = CreateInvitation("00000000-0000-4000-8000-000000000001", "00000000-0000-4000-8000-000000000002", " Azure é 🧙 ")
        assertEquals(input, DeckJson.format.decodeFromString<CreateInvitation>(DeckJson.format.encodeToString(input)))
        assertNotEquals(DeckJson.intent("createInvitation", DeckJson.canonical(input)),
            DeckJson.intent("createInvitation", DeckJson.canonical(input.copy(gameName = "Other"))))
        assertTrue(LobbyValidation.name(input.gameName)); assertFalse(LobbyValidation.name(" \n\t"))
        assertFalse(LobbyValidation.name("bad\u0000")); assertFalse(LobbyValidation.name("\ud800"))
        val game = GameSummary(input.requestId, input.gameName, "2026-10-22T00:00:00Z", AccountIdentity(input.requestId, "Inviter"),
            AccountIdentity(input.recipient, "Recipient"), input.requestId, input.recipient, InvitationState.PENDING)
        assertEquals(game, DeckJson.format.decodeFromString<GameSummary>(DeckJson.format.encodeToString(game)))
        assertFalse(DeckJson.format.encodeToString(game).contains("email"))
        assertEquals(GameList(listOf(game)), DeckJson.format.decodeFromString<GameList>(DeckJson.format.encodeToString(GameList(listOf(game)))))
    }
}
