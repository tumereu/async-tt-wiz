package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.*

class GameResumeTest {
    private class Fixture : AutoCloseable {
        val data = GameplayFixture()
        var time = 0L
        var credential: String? = "A".repeat(43)
        var account = data.actor
        val frames = mutableListOf<GameClientMessage>()
        var opens = 0
        var traffic = 0L
        lateinit var listener: GameConnection.Listener
        val wire = object : GameConnection {
            override fun connect(token: String, listener: GameConnection.Listener) { opens++; this@Fixture.listener = listener }
            override fun send(message: GameClientMessage): Boolean { frames.add(message); traffic = time; return true }
            override fun baseline(gameId: String, requestIds: List<String>, rejected: (String) -> Unit,
            complete: (GameplayBaseline?) -> Unit): Boolean {
                complete(GameplayBaseline(data.snapshot(), requestIds.map { GameRequestStatus(it, "notAdmitted") }))
                return true
            }
            override fun disconnect() = Unit
            override fun close() = Unit
        }
        val session = GameSession(wire, { credential }, { it() }, {}, {}, { _, _ -> }, {},
            accountId = { account }, now = { time })
        init { session.watch(listOf(data.game)); accept() }
        fun accept(generation: String = "1", statuses: List<GameRequestStatus> = emptyList()) {
            listener.opened(); listener.message(GameWelcome(gameplayRulesVersion = CURRENT_GAMEPLAY_RULES_VERSION))
            val stream = frames.mapNotNull { when (it) {
                is OpenGameStream -> it.streamId
                is ResumeGameStream -> it.streamId
                else -> null
            } }.last()
            val recovery = frames.filterIsInstance<ResumeGameStream>().lastOrNull()?.requestIds.orEmpty()
            listener.message(GameStreamReady(data.game.id, stream, generation,
                if (statuses.isNotEmpty()) statuses else recovery.map { GameRequestStatus(it, "notAdmitted") }))
            listener.message(data.head())
        }
        fun play() = session.gameplay(data.game, data.actor, PlayAction.AdjustLife(data.actor, "1"))
        override fun close() = session.close()
    }

    // SYS-019
    @Test fun resumeAndDisconnectAloneStaySilentIndefinitely() = Fixture().use { f ->
        f.listener.disconnected()
        repeat(20) { f.time += 1_000; f.session.tick() }
        assertNull(f.session.error)
        assertFalse(f.session.reconnecting(f.data.game.id))
        assertEquals(f.data.initial, f.session.play(f.data.game))
        assertEquals(2, f.opens)
    }

    // SYS-014
    @Test fun actionWaitsForRecoveredStreamAndSendsItsOriginalExactlyOnce() = Fixture().use { f ->
        f.listener.disconnected()
        assertTrue(f.play())
        assertTrue(f.session.sendChat(f.data.game, "hello"))
        assertTrue(f.frames.filterIsInstance<GameCommand>().isEmpty())
        assertEquals("21", f.data.life(f.session.play(f.data.game)))
        f.time = 2_000
        assertFalse(f.session.reconnecting(f.data.game.id))
        f.accept("2")
        assertTrue(f.session.ready(f.data.game.id))
        assertEquals(2, f.frames.filterIsInstance<GameCommand>().size)
        assertEquals(1, f.frames.filterIsInstance<GameCommand>().count { it.action is GameAction.SendChat })
    }

    // SYS-014
    @Test fun queuedMovesUseRecoveredGenerationAndOldSocketCallbacksCannotCancelThem() = Fixture().use { f ->
        val old = f.listener
        old.disconnected()
        assertTrue(f.play()); assertTrue(f.play())
        f.time = 2_000
        f.accept("2")
        val commands = f.frames.filterIsInstance<GameCommand>()
        assertEquals(2, commands.size)
        assertTrue(commands.all { it.generation == "2" })
        old.disconnected()
        f.session.tick()
        assertTrue(f.session.ready(f.data.game.id))
        assertEquals(commands, f.frames.filterIsInstance<GameCommand>())
        assertEquals("22", f.data.life(f.session.play(f.data.game)))
        assertNull(f.session.error)
    }

    @Test fun onlyUnfulfilledUserDemandShowsReconnectAfterThreeSecondsAndClearFencesIt() = Fixture().use { f ->
        f.listener.disconnected()
        assertTrue(f.play())
        f.time = 2_999; assertFalse(f.session.reconnecting(f.data.game.id))
        f.time = 3_000; assertTrue(f.session.reconnecting(f.data.game.id)); assertFalse(f.play())
        f.session.foreground()
        assertFalse(f.session.reconnecting(f.data.game.id), "Returning to the table must not revive old reconnect UI")
        f.accept("2")
        assertFalse(f.session.reconnecting(f.data.game.id))
        f.listener.disconnected(); assertFalse(f.play(), "An unresolved old socket original must recover first")
        f.account = f.data.other; f.session.tick()
        assertFalse(f.session.pending(f.data.game.id))
        assertFalse(f.session.reconnecting(f.data.game.id))
    }

    @Test fun returnIsCaughtUpOnlyAfterItsPongOrAReconnectedStreamHead() = Fixture().use { f ->
        val game = f.data.game.id
        assertTrue(f.session.caughtUp(game))
        f.session.background()
        assertFalse(f.session.caughtUp(game))
        // A socket kept open while away: undelivered events precede the answer to the return ping.
        f.session.foreground()
        val ping = f.frames.filterIsInstance<GamePing>().last()
        assertFalse(f.session.caughtUp(game))
        f.listener.message(GamePong("unrelated"))
        assertFalse(f.session.caughtUp(game))
        f.listener.message(GamePong(ping.correlationId))
        assertTrue(f.session.caughtUp(game))
        // A socket lost while away: the reconnected stream's head is the catch-up.
        f.session.background()
        f.listener.disconnected()
        f.session.foreground()
        assertFalse(f.session.caughtUp(game))
        f.accept("2")
        assertTrue(f.session.caughtUp(game))
    }

    @Test fun protocolHeartbeatPreventsServersTextTrafficLeaseExpiringOnHealthyIdleSocket() = Fixture().use { f ->
        repeat(20) {
            f.time += 25_000
            // GameHub.heartbeat's lease is based on text receive(), NOT WebSocket control pings.
            if (f.time - f.traffic > 90_000) f.listener.disconnected()
            f.session.tick()
        }
        assertEquals(1, f.opens)
        assertEquals(20, f.frames.filterIsInstance<GamePing>().size)
        assertTrue(f.session.ready(f.data.game.id))
        assertEquals(f.data.initial, f.session.acceptedPlay(f.data.game))
    }
}
