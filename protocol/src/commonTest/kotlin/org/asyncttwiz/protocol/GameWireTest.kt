package org.asyncttwiz.protocol

import kotlin.test.*
import kotlinx.serialization.encodeToString

class GameWireTest {
    private val id = "00000000-0000-4000-8000-000000000001"
    private fun command() = GameCommand(id, GameScope(id, id, id), GameAction.AcceptInvitation, id, "1")
    @Test fun gameplayRulesHandshakeRoundTripsAndMissingVersionsMeanOldestRules() {
        val hello = GameHello(id, id, gameplayRulesVersion = CURRENT_GAMEPLAY_RULES_VERSION)
        assertEquals(hello, GameWire.decodeClient(GameWire.json.encodeToString<GameClientMessage>(hello)))
        val welcome = GameWelcome(gameplayRulesVersion = CURRENT_GAMEPLAY_RULES_VERSION)
        assertEquals(welcome, GameWire.json.decodeFromString<GameServerMessage>(GameWire.encode(welcome)))
        assertEquals(1, (GameWire.decodeClient("""{"type":"hello","processId":"$id","installationId":"$id"}""") as GameHello).gameplayRulesVersion)
        assertEquals(1, (GameWire.json.decodeFromString<GameServerMessage>("""{"type":"welcome"}""") as GameWelcome).gameplayRulesVersion)
    }
    @Test fun opponentPresenceIsMinorOneAndOmittedFromHeadsWithoutIt() {
        val hello = GameHello(id, id, maxMinor = GAME_PROTOCOL_MINOR, gameplayRulesVersion = CURRENT_GAMEPLAY_RULES_VERSION)
        assertEquals(hello, GameWire.decodeClient(GameWire.json.encodeToString<GameClientMessage>(hello)))
        val summary = GameSummary(id, "Game", "2026-10-29T00:00:00Z", AccountIdentity(id, "A"), AccountIdentity(id, "B"), id, id,
            InvitationState.PENDING)
        val plain = GameHead(id, "0", summary)
        assertFalse(GameWire.encode(plain).contains("opponentPresence"), "older clients never see the new field")
        val head = plain.copy(opponentPresence = OpponentPresence(PresenceState.IDLE, idleSeconds = 130, lastSeenSeconds = 2))
        assertTrue(GameWire.encode(head).contains(""""opponentPresence":{"state":"IDLE","idleSeconds":130,"lastSeenSeconds":2}"""))
        assertEquals(head, GameWire.json.decodeFromString<GameServerMessage>(GameWire.encode(head)))
        val update = GamePresenceUpdate(id, OpponentPresence(PresenceState.OFFLINE))
        assertEquals("""{"type":"presence","gameId":"$id","opponent":{"state":"OFFLINE","idleSeconds":null,"lastSeenSeconds":null}}""",
            GameWire.encode(update))
        assertEquals(update, GameWire.json.decodeFromString<GameServerMessage>("""{"type":"presence","gameId":"$id","opponent":{"state":"OFFLINE"}}"""))
    }
    @Test fun typedRoundTripsAndTransportIndependentCanonicalIntent() {
        val command = command()
        val encoded = GameWire.json.encodeToString<GameClientMessage>(command)
        assertEquals(command, GameWire.decodeClient(encoded)); assertTrue(GameWire.valid(command))
        assertEquals(GameWire.digest(command), GameWire.digest(command.copy(generation = "99", streamId = id.replace("001", "002"))))
        assertNotEquals(GameWire.digest(command), GameWire.digest(command.copy(action = GameAction.DeclineInvitation)))
        assertNotEquals(GameWire.digest(command), GameWire.digest(command.copy(scope = command.scope.copy(preparationId = id.replace("001", "003")))))
        assertTrue(GameWire.canonical(command).contains("\"continuation\":null"))
        assertTrue(GameWire.canonical(command).contains("\"invocationId\":null"))
        assertFalse(GameWire.canonical(command).contains("generation"))
        val minimal = """{"type":"command","requestId":"$id","scope":{"gameId":"$id","preparationId":"$id","invitationId":"$id"},"action":{"type":"acceptInvitation"},"streamId":"$id","generation":"1"}"""
        assertEquals(command, GameWire.decodeClient(minimal))
        val result = GameResult(id, command.scope, "accepted", ingressOrdinal = "9007199254740993", commitRevision = "9223372036854775807")
        assertEquals(result, GameWire.json.decodeFromString<GameServerMessage>(GameWire.encode(result)))
    }
    @Test fun strictUnknownDuplicateDepthAndVersionRejection() {
        val encoded = GameWire.json.encodeToString<GameClientMessage>(command())
        for (bad in listOf(encoded.replace("\"generation\":\"1\"", "\"generation\":\"1\",\"generation\":\"2\""),
            encoded.replace("\"type\":\"acceptInvitation\"", "\"type\":\"acceptInvitation\",\"extra\":1"),
            encoded.replace("\"type\":\"acceptInvitation\"", "\"type\":\"unknown\""),
            encoded.replace("\"gameId\":", "\"gameId\":\"$id\",\"gameId\":"),
            "[".repeat(34) + "0" + "]".repeat(34), encoded + "x")) assertFails { GameWire.decodeClient(bad) }
        assertFalse(GameWire.valid(command().copy(commandVersion = 2)))
        assertFalse(GameWire.valid(command().copy(scope = GameScope(id))))
        assertFalse(GameWire.valid(command().copy(continuation = Continuation(id, true))))
        assertFalse(GameWire.valid(command().copy(requestId = id.uppercase().replace('0', 'A'))))
    }
    @Test fun canonicalDecimalOrdinalsNeverRoundOrCoerce() {
        for (value in listOf("0", "1", "9007199254740993", "9223372036854775807")) assertEquals(value.toLong(), GameWire.ordinal(value))
        for (value in listOf("01", "-1", "+1", "1.0", "1e2", "9223372036854775808", " 1")) assertFails { GameWire.ordinal(value) }
        assertFails { GameWire.decodeClient(GameWire.json.encodeToString<GameClientMessage>(command()).replace("\"generation\":\"1\"", "\"generation\":1")) }
    }
    @Test fun gameplayScopesAndNestedIntentStayDistinctFromLifecycleAndTransport() {
        val command = command().copy(scope = GameScope(id, playId = id), action = GameAction.Play(PlayAction.SetTapped(id, true)))
        assertTrue(GameWire.valid(command))
        for (scope in listOf(GameScope(id), GameScope(id, preparationId = id), GameScope(id, id, playId = id),
            GameScope(id, invitationId = id, playId = id))) assertFalse(GameWire.valid(command.copy(scope = scope)))
        assertNotEquals(GameWire.digest(command), GameWire.digest(command.copy(action = GameAction.Play(PlayAction.SetTapped(id, false)))))
        assertEquals(GameWire.digest(command), GameWire.digest(command.copy(streamId = id.replace("001", "002"), generation = "2")))
        val encoded = GameWire.json.encodeToString<GameClientMessage>(command)
        assertFails { GameWire.decodeClient(encoded.replace("\"tapped\":true", "\"tapped\":true,\"previousTapped\":false")) }
        assertTrue(GameWire.valid(command.copy(continuation = Continuation(id, true))))
        assertFalse(GameWire.valid(command.copy(continuation = Continuation(id, false))))
    }
}
