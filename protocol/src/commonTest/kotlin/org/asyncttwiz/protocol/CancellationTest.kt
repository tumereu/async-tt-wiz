package org.asyncttwiz.protocol

import kotlinx.serialization.encodeToString
import kotlin.test.*

class CancellationTest {
    private val id = "00000000-0000-4000-8000-000000000001"
    private val other = "00000000-0000-4000-8000-000000000002"
    private val game = GameSummary(id, "Spark", "2026-10-23T00:00:00Z", AccountIdentity(id, "A"),
        AccountIdentity(other, "B"), id, other, InvitationState.PENDING)
    private val scope = GameScope(id, id)
    @Test fun cancellationCodecBindsRoundNotAttemptOrPlay() {
        val command = GameCommand(id, scope, GameAction.CancelSetup, id, "1")
        val encoded = GameWire.json.encodeToString<GameClientMessage>(command)
        assertTrue(encoded.contains("cancelSetup")); assertEquals(command, GameWire.decodeClient(encoded))
        assertTrue(GameWire.valid(command))
        for (bad in listOf(scope.copy(preparationId = null), scope.copy(invitationId = other), scope.copy(playId = other)))
            assertFalse(GameWire.valid(command.copy(scope = bad)))
        assertNotEquals(GameWire.digest(command), GameWire.digest(command.copy(scope = scope.copy(preparationId = other))))
        val ready = GameStreamReady(id, other, "2", listOf(GameRequestStatus(id, "pending")), game.copy(lifecycle = GameLifecycle.CANCELLED))
        assertEquals(ready, GameWire.json.decodeFromString<GameServerMessage>(GameWire.encode(ready)))
    }
    @Test fun eitherAdmittedParticipantCanCancelOnlyCapturedUnstartedRound() {
        assertNull(CancellationRules.rejection(game, id, scope))
        assertEquals("accessDenied", CancellationRules.rejection(game, other, scope))
        val joined = game.copy(invitationState = InvitationState.ACCEPTED)
        assertNull(CancellationRules.rejection(joined, other, scope))
        assertNull(CancellationRules.rejection(game.copy(invitationState = InvitationState.DECLINED), id, scope))
        assertEquals("inapplicable", CancellationRules.rejection(joined, other, scope.copy(preparationId = other)))
        assertEquals("inapplicable", CancellationRules.rejection(joined, other, scope.copy(gameId = other)))
        for (lifecycle in listOf(GameLifecycle.ACTIVE, GameLifecycle.CANCELLED, GameLifecycle.DELETED))
            assertEquals("lifecycleEnded", CancellationRules.rejection(joined.copy(lifecycle = lifecycle), id, scope))
    }
}
