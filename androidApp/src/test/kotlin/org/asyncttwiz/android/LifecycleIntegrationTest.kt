package org.asyncttwiz.android

import java.util.UUID
import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.*

class LifecycleIntegrationTest {
    @Test fun setupContextStartDeleteContextAndChatBackStaySeparateAndRemoteRemovalClearsContent() = CancellationFixture().use { f ->
        f.lobby.showGameContext(f.game)
        assertEquals(f.game, f.lobby.setupCancellation.context)
        f.lobby.gameContext(f.game); f.lobby.requestGameDeletion(f.game)
        assertNull(f.lobby.state.gameContext); assertNull(f.lobby.state.deletion)
        f.lobby.back(); assertNull(f.lobby.setupCancellation.context)
        assertEquals(f.game.id, f.lobby.state.selected); assertTrue(f.commands().isEmpty())
        f.lobby.requestCancelSetup(f.game)
        val play = f.play()
        val active = f.game.copy(lifecycle = GameLifecycle.ACTIVE, playId = play.id, revision = "1")
        f.head(active, play = play)
        assertNull(f.lobby.setupCancellation.confirmation)
        assertFalse(f.lobby.offersSetupCancellation(active))
        assertTrue(f.lobby.offersGameDeletion(active))
        assertFalse(f.lobby.offersGameDeletion(f.game))
        f.lobby.requestCancelSetup(f.game); assertNull(f.lobby.setupCancellation.confirmation)
        f.lobby.openChat(); f.lobby.composeChat("captured text"); f.lobby.back()
        assertFalse(f.lobby.state.chatOpen); assertEquals(active.id, f.lobby.state.selected)
        f.lobby.gameContext(active); f.lobby.back(); assertNull(f.lobby.state.gameContext)
        f.lobby.requestGameDeletion(active); f.lobby.back(); assertNull(f.lobby.state.deletion)
        assertTrue(f.commands().isEmpty())
        f.lobby.openChat(); f.lobby.sendChat()
        val original = f.commands().single()
        assertEquals(GameScope(active.id, playId = play.id), original.scope)
        assertEquals(GameAction.SendChat("captured text"), original.action)
        f.lobby.requestGameDeletion(active)
        val removed = active.copy(lifecycle = GameLifecycle.DELETED, revision = "3")
        f.head(removed)
        assertNull(f.lobby.state.deletion); assertNull(f.lobby.state.selected)
        assertFalse(f.lobby.state.chatOpen); assertEquals(ChatPresentation(), f.lobby.state.chat)
        assertNull(f.lobby.state.play); assertFalse(f.lobby.confirmGameDeletion())
        f.lobby.gameContext(active); f.lobby.requestGameDeletion(active)
        assertNull(f.lobby.state.gameContext); assertNull(f.lobby.state.deletion)
        assertFalse(f.lobby.offersGameDeletion(active))
        // Delayed chat/read/result from the removed context cannot restore content or navigation.
        val chat = ChatMessage(original.requestId, active.id, play.id, f.owner.accountId, "captured text", "2", "date")
        f.message(GameHead(active.id, "2", active.copy(revision = "2"), play = play, chat = listOf(chat)))
        f.message(GameResult(original.requestId, original.scope, "accepted", ingressOrdinal = "1", commitRevision = "2"))
        f.lobby.sync()
        assertNull(f.lobby.state.selected); assertTrue(f.lobby.state.games.isEmpty())
        assertEquals(ChatPresentation(), f.lobby.state.chat); assertEquals(listOf(original), f.commands())
    }

    @Test fun bothTerminalRecoveriesSettleCapturedOriginalsWithoutReopeningOrReplayingChat() {
        for (lifecycle in listOf(GameLifecycle.CANCELLED, GameLifecycle.DELETED)) {
            val wire = CancellationFixture.Wire()
            val owner = AccountIdentity(UUID.randomUUID().toString(), "Owner")
            val other = AccountIdentity(UUID.randomUUID().toString(), "Other")
            fun id() = UUID.randomUUID().toString()
            val game = GameSummary(id(), "Captured", "date", owner, other, id(), id(), InvitationState.ACCEPTED,
                if (lifecycle == GameLifecycle.DELETED) GameLifecycle.ACTIVE else GameLifecycle.LOBBY,
                playId = if (lifecycle == GameLifecycle.DELETED) id() else null)
            val results = mutableListOf<GameResult>()
            val session = GameSession(wire, { "token" }, { it() }, {}, {}, { _, result -> results.add(result) }, {})
            session.watch(listOf(game)); wire.listener.opened(); wire.listener.message(GameWelcome())
            val stream = wire.frames.filterIsInstance<OpenGameStream>().single()
            wire.listener.message(GameStreamReady(game.id, stream.streamId, "1"))
            wire.listener.message(GameHead(game.id, "0", game))
            if (lifecycle == GameLifecycle.DELETED) assertTrue(session.sendChat(game, "original chat"))
            else assertTrue(session.submit(game, GameAction.CancelSetup))
            val original = wire.frames.filterIsInstance<GameCommand>().single()
            wire.listener.disconnected(); session.tick(); wire.listener.opened(); wire.listener.message(GameWelcome())
            val resume = wire.frames.filterIsInstance<ResumeGameStream>().single()
            assertEquals(listOf(original.requestId), resume.requestIds)
            val result = GameResult(original.requestId, original.scope, "accepted", ingressOrdinal = "1", commitRevision = "1")
            wire.listener.message(GameStreamReady(game.id, stream.streamId, "2",
                listOf(GameRequestStatus(original.requestId, "final", result)), game.copy(lifecycle = lifecycle, revision = "2")))
            assertEquals(listOf(result), results); assertFalse(session.pending(game.id)); assertFalse(session.ready(game.id))
            assertEquals(listOf(original), wire.frames.filterIsInstance<GameCommand>())
            assertEquals(1, wire.frames.filterIsInstance<SubscribeGame>().size)
            assertTrue(session.chat(game.id).isEmpty()); assertTrue(session.outgoingChat(game.id).isEmpty())
            assertFalse(session.sendChat(game, "late")); assertFalse(session.submit(game, GameAction.CancelSetup))
            session.close()
        }
    }
}
