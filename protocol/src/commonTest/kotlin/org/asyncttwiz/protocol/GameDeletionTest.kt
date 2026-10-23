package org.asyncttwiz.protocol

import kotlinx.serialization.encodeToString
import kotlin.test.*

class GameDeletionTest {
    private val game = "00000000-0000-4000-8000-000000000001"
    private val play = "00000000-0000-4000-8000-000000000002"
    private fun command() = GameCommand(game, GameScope(game, playId = play), GameAction.DeleteGame, game, "1")
    @Test fun deleteUsesOnlyCapturedActiveIncarnationAndSameCanonicalRequestIdentity() {
        val command = command()
        val encoded = GameWire.json.encodeToString<GameClientMessage>(command)
        assertTrue(encoded.contains("\"type\":\"deleteGame\""))
        assertEquals(command, GameWire.decodeClient(encoded)); assertTrue(GameWire.valid(command))
        assertEquals(GameWire.digest(command), GameWire.digest(command.copy(generation = "2", streamId = play)))
        assertNotEquals(GameWire.digest(command), GameWire.digest(command.copy(scope = GameScope(game, playId = game))))
        for (scope in listOf(GameScope(game), GameScope(game, game), GameScope(game, game, game),
            GameScope(game, preparationId = game, playId = play), GameScope(game, invitationId = game, playId = play)))
            assertFalse(GameWire.valid(command.copy(scope = scope)))
        assertFalse(GameWire.valid(command.copy(continuation = Continuation(game, true))))
        assertFalse(GameWire.valid(command.copy(action = GameAction.Ready)))
    }
}
