package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import java.util.UUID
import kotlin.test.*

class GameDeletionTest {
    private fun id() = UUID.randomUUID().toString()
    private val a = AccountIdentity(id(), "Inviter")
    private val b = AccountIdentity(id(), "Recipient")
    private fun game() = GameSummary(id(), "Shared Spark", "2026-10-23T00:00:00Z", a, b, id(), id(),
        InvitationState.ACCEPTED, GameLifecycle.ACTIVE, "5", id())
    private class Wire : GameConnection {
        lateinit var listener: GameConnection.Listener
        val frames = mutableListOf<GameClientMessage>()
        override fun connect(token: String, listener: GameConnection.Listener) { this.listener = listener }
        override fun send(message: GameClientMessage): Boolean { frames.add(message); return true }
        override fun disconnect() = Unit
        override fun close() = Unit
    }
    private inner class Fixture : AutoCloseable {
        val wire = Wire(); var token: String? = "original"; val changed = mutableListOf<GameSummary>(); val results = mutableListOf<GameResult>()
        val session = GameSession(wire, { token }, { it() }, {}, changed::add, { _, result -> results.add(result) }, {})
        fun message(message: GameServerMessage) = wire.listener.message(message)
        fun connect(game: GameSummary, statuses: List<GameRequestStatus> = emptyList(), generation: String = "1") {
            session.watch(listOf(game)); wire.listener.opened(); message(GameWelcome())
            val stream = wire.frames.mapNotNull { when (it) { is OpenGameStream -> it.streamId; is ResumeGameStream -> it.streamId; else -> null } }.last()
            message(GameStreamReady(game.id, stream, generation, statuses))
        }
        fun head(game: GameSummary, play: PlayState? = null) = message(GameHead(game.id, game.revision, game, play = play))
        fun commands() = wire.frames.filterIsInstance<GameCommand>()
        override fun close() = session.close()
    }
    private fun play(game: GameSummary): PlayState {
        val root = PlayRoot(id(), id())
        return PlayState(game.id, game.playId!!, game.preparationRound, id(), root, root.id,
            BoardState(listOf(PlayPlayer(a.accountId), PlayPlayer(b.accountId)), emptyList(), emptyList(), TurnState(a.accountId)),
            emptyList(), emptyList(), InitialStarter(StarterChoice(a.accountId), a.accountId))
    }

    @Test fun capturedPlayConnectedOnlyNoHeadGuardAndResultDoesNotBecomeLifecycleState() = Fixture().use { f ->
        val game = game(); assertFalse(f.session.deleteGame(game))
        f.connect(game); f.head(game, play(game)); assertTrue(f.session.ready(game.id))
        val changed = game.copy(revision = "6")
        f.head(changed); assertTrue(f.session.deleteGame(game)) // Revision changed, same incarnation.
        val command = f.commands().single()
        assertEquals(GameScope(game.id, playId = game.playId), command.scope); assertTrue(GameWire.valid(command))
        assertFalse(f.session.deleteGame(game)); f.message(GameReceipt(command.requestId, command.scope, "7"))
        assertTrue(f.session.pending(game.id)); assertNotNull(f.session.play(game))
        f.message(GameResult(command.requestId, command.scope, "accepted", ingressOrdinal = "7", commitRevision = "7"))
        assertEquals(changed, f.session.summary(game)); assertNotNull(f.session.play(game)) // No result-as-state.
        val deleted = changed.copy(lifecycle = GameLifecycle.DELETED, revision = "7")
        f.message(GameRevision(game.id, "6", "7", command.requestId, a.accountId, command.scope, deleted))
        assertNull(f.session.play(game)); assertNull(f.session.preparation(game)); assertFalse(f.session.ready(game.id))
        f.head(game); f.session.watch(listOf(game.copy(revision = "99")))
        assertEquals(deleted, f.session.summary(game)); assertFalse(f.session.deleteGame(game))
    }

    @Test fun staleConfirmationRejectsNewPlayOrLobbyInsteadOfRetargeting() = Fixture().use { f ->
        val game = game(); f.connect(game); f.head(game)
        f.head(game.copy(playId = id(), revision = "6")); assertFalse(f.session.deleteGame(game))
        f.head(game.copy(lifecycle = GameLifecycle.LOBBY, playId = null, revision = "7")); assertFalse(f.session.deleteGame(game))
        assertTrue(f.commands().isEmpty())
    }

    @Test fun lostDeletionRecoversOriginalAfterTombstoneAndClearFencesAccountCallbacks() = Fixture().use { f ->
        val game = game(); f.connect(game); f.head(game); f.session.deleteGame(game)
        val command = f.commands().single()
        val final = GameResult(command.requestId, command.scope, "accepted", ingressOrdinal = "7", commitRevision = "6")
        val deleted = game.copy(lifecycle = GameLifecycle.DELETED, revision = "6")
        f.wire.listener.disconnected(); assertTrue(f.session.pending(game.id)); assertTrue(f.results.isEmpty())
        f.connect(game, listOf(GameRequestStatus(command.requestId, "final", final)), "2")
        assertEquals(listOf(final), f.results); assertEquals(1, f.commands().size)
        assertEquals(game, f.session.summary(game)) // Stream status still is not a replica.
        f.head(deleted); assertEquals(deleted, f.session.summary(game)); assertFalse(f.session.pending(game.id))
        val old = f.wire.listener; f.session.clear(); f.token = "different-account"
        old.message(GameHead(game.id, deleted.revision, deleted))
        assertEquals(game, f.session.summary(game)); assertTrue(f.session.pending(game.id).not())
        f.connect(game); f.head(game); assertEquals(game, f.session.summary(game)); assertEquals(1, f.commands().size)
    }

    @Test fun notAdmittedRetriesSameIdentityWhereasPendingNeverRetransmitsAndTimeoutNeverClaimsSuccess() {
        for (status in listOf("notAdmitted", "pending")) Fixture().use { f ->
            val game = game(); f.connect(game); f.head(game); f.session.deleteGame(game)
            val command = f.commands().single(); f.wire.listener.disconnected()
            assertTrue(f.session.pending(game.id)); assertTrue(f.results.isEmpty())
            f.connect(game, listOf(GameRequestStatus(command.requestId, status)), "2"); f.head(game)
            if (status == "notAdmitted") assertEquals(command.copy(generation = "2"), f.commands().last())
            else assertEquals(1, f.commands().size)
            assertTrue(f.session.pending(game.id)); assertEquals(game, f.session.summary(game))
        }
    }
}
