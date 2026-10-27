package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import java.util.UUID
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit
import kotlin.test.*

class LobbyDeletionTest {
    private fun id() = UUID.randomUUID().toString()
    private val a = AccountIdentity(id(), "Inviter")
    private val b = AccountIdentity(id(), "Opponent")
    private val game = GameSummary(id(), "Shared Spark", "2026-10-23T00:00:00Z", a, b, id(), id(),
        InvitationState.ACCEPTED, GameLifecycle.ACTIVE, "5", id())
    private class Worker : AbstractExecutorService() {
        val queue = ArrayDeque<Runnable>()
        override fun execute(command: Runnable) { queue.add(command) }
        fun drain() { while (queue.isNotEmpty()) queue.removeFirst().run() }
        override fun shutdown() = Unit
        override fun shutdownNow() = mutableListOf<Runnable>()
        override fun isShutdown() = false
        override fun isTerminated() = false
        override fun awaitTermination(timeout: Long, unit: TimeUnit) = false
    }
    private class Wire : GameConnection {
        lateinit var listener: GameConnection.Listener
        val frames = mutableListOf<GameClientMessage>()
        override fun connect(token: String, listener: GameConnection.Listener) { this.listener = listener }
        override fun send(message: GameClientMessage): Boolean { frames.add(message); return true }
        override fun disconnect() = Unit
        override fun close() = Unit
    }
    private inner class Fixture(val actor: AccountIdentity = a) : AutoCloseable {
        val worker = Worker(); val posts = ArrayDeque<() -> Unit>(); val wire = Wire()
        var identity = actor; var httpGame = game; var token: String? = "original"
        val lobby = LobbyController(object : Lobbies {
            override suspend fun search(token: String, query: String) = UserSearch(emptyList())
            override suspend fun contacts(token: String, after: String?) = ContactList(emptyList())
            override suspend fun add(token: String, input: AddContact) = b
            override suspend fun create(token: String, input: CreateInvitation) = httpGame
            override suspend fun games(token: String, invitations: Boolean, after: String?) = GameList(if (invitations) emptyList() else listOf(httpGame))
            override suspend fun head(token: String, id: String) = httpGame
            override fun close() = Unit
        }, { token }, worker, { posts.add(it) }, gameConnection = wire, accountId = { identity.accountId })
        init { lobby.refresh(); drain() }
        fun drain() { while (worker.queue.isNotEmpty() || posts.isNotEmpty()) {
            worker.drain(); while (posts.isNotEmpty()) posts.removeFirst()()
        } }
        fun message(message: GameServerMessage) { wire.listener.message(message); drain() }
        fun connect() {
            wire.listener.opened(); drain(); message(GameWelcome(gameplayRulesVersion = CURRENT_GAMEPLAY_RULES_VERSION))
            val stream = wire.frames.filterIsInstance<OpenGameStream>().last()
            message(GameStreamReady(game.id, stream.streamId, "1")); head(game)
        }
        fun head(summary: GameSummary) = message(GameHead(game.id, summary.revision, summary))
        fun commands() = wire.frames.filterIsInstance<GameCommand>()
        override fun close() { lobby.close(); drain() }
    }

    @Test fun contextAndExtraWarningCaptureBothPlayersWithNoDispatchOnDismissAndEitherActorCanConfirm() {
        for (actor in listOf(a, b)) Fixture(actor).use { f ->
            f.connect(); f.lobby.gameContext(game)
            assertEquals(game, f.lobby.state.gameContext); assertTrue(f.commands().isEmpty()); assertNull(f.lobby.state.selected)
            f.lobby.dismissGameContext(); assertNull(f.lobby.state.gameContext)
            f.lobby.requestGameDeletion(game)
            val capture = f.lobby.state.deletion!!
            assertEquals("Delete Shared Spark?", capture.title)
            assertTrue(capture.warning.contains(game.opponent(actor.accountId).username))
            assertTrue(capture.warning.contains("both players")); assertTrue(capture.warning.contains("no restore"))
            assertTrue(f.commands().isEmpty()); f.lobby.dismissGameDeletion(); assertFalse(f.lobby.confirmGameDeletion())
            assertTrue(f.commands().isEmpty())
            f.lobby.requestGameDeletion(game); assertTrue(f.lobby.confirmGameDeletion())
            assertNull(f.lobby.state.deletion); assertEquals(GameScope(game.id, playId = game.playId), f.commands().single().scope)
        }
    }

    @Test fun staleDialogNeverRetargetsNewPlayLobbyOtherAccountOrDisconnectedSession() {
        for (change in listOf("play", "lobby", "account", "disconnect")) Fixture().use { f ->
            f.connect(); f.lobby.requestGameDeletion(game)
            when (change) {
                "play" -> f.head(game.copy(revision = "6", playId = id()))
                "lobby" -> f.head(game.copy(revision = "6", lifecycle = GameLifecycle.LOBBY, playId = null))
                "account" -> f.identity = b
                else -> { f.wire.listener.disconnected(); f.drain() }
            }
            assertFalse(f.lobby.confirmGameDeletion()); assertTrue(f.commands().isEmpty()); assertNotNull(f.lobby.state.error)
        }
        Fixture().use { f ->
            f.connect(); val setup = game.copy(lifecycle = GameLifecycle.LOBBY, playId = null)
            f.lobby.requestGameDeletion(setup); assertNull(f.lobby.state.deletion)
            f.lobby.gameContext(setup); assertNull(f.lobby.state.gameContext)
            assertFalse(f.lobby.deleteGame(setup)); assertTrue(f.commands().isEmpty())
        }
    }

    @Test fun incomingTombstoneRemovesListAndSelectionEvenDuringOldHttpAndRetainsUnknownOriginal() = Fixture().use { f ->
        f.connect(); f.lobby.open(game.id); f.drain(); assertEquals(game, f.lobby.state.detail)
        f.lobby.requestGameDeletion(game); assertTrue(f.lobby.confirmGameDeletion()); val command = f.commands().single()
        // Complete an HTTP read but delay its UI callback until after the accepted lifecycle message.
        f.lobby.refresh(); f.worker.drain()
        val deleted = game.copy(lifecycle = GameLifecycle.DELETED, revision = "6")
        f.message(GameRevision(game.id, "5", "6", command.requestId, a.accountId, command.scope, deleted))
        assertTrue(f.lobby.state.games.isEmpty()); assertTrue(f.lobby.state.invitations.isEmpty())
        assertNull(f.lobby.state.selected); assertNull(f.lobby.state.detail); assertNull(f.lobby.state.play)
        // Retain request recovery even though there is no navigation row left.
        f.message(GameResult(command.requestId, command.scope, "accepted", ingressOrdinal = "7", commitRevision = "6"))
        assertTrue(f.lobby.state.games.isEmpty()); assertNull(f.lobby.state.detail)
        f.lobby.refresh(); f.drain(); assertTrue(f.lobby.state.games.isEmpty())
        f.lobby.requestGameDeletion(game); f.lobby.clear(); assertNull(f.lobby.state.deletion)
        f.token = "different-account"; f.identity = b
        assertFalse(f.lobby.confirmGameDeletion()); assertTrue(f.lobby.state.games.isEmpty())
    }
}
