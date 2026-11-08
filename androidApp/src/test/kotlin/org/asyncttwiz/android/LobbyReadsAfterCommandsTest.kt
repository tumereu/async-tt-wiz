package org.asyncttwiz.android

import org.asyncttwiz.protocol.GameAction
import org.asyncttwiz.protocol.GameCommand
import org.asyncttwiz.protocol.GameResult
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Owner, 2026-11-08: a game command doesn't read the games list; the summaries on the game's stream and the regular refresh keep it current. */
class LobbyReadsAfterCommandsTest {
    @Test fun aGameCommandSettlingReadsNoLists() = EngineLobbyFixture().use { f ->
        f.connect()
        f.keep()
        val played = f.commands().filter { it.action is GameAction.Submit }
        assertTrue(played.isNotEmpty(), "the keep was sent")
        f.worker.drain()
        val before = f.listReads
        played.forEach { f.settle(it) }
        f.worker.drain()
        assertEquals(before, f.listReads)
    }

    private fun EngineLobbyFixture.settle(command: GameCommand) =
        message(GameResult(command.requestId, command.scope, "accepted", ingressOrdinal = "7", commitRevision = "7"))
}
