package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import java.util.UUID
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit
import kotlin.test.*

class GameLobbyIntegrationTest {
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
    private fun id() = UUID.randomUUID().toString()
    private val inviter = AccountIdentity(id(), "Inviter")
    private val recipient = AccountIdentity(id(), "Recipient")
    private val game = GameSummary(id(), "Moonlit Rune", "2026-10-22T00:00:00Z", inviter, recipient, id(), id(), InvitationState.PENDING)
    private class Wire : GameConnection {
        lateinit var listener: GameConnection.Listener
        val frames = mutableListOf<GameClientMessage>()
        override fun connect(token: String, listener: GameConnection.Listener) { this.listener = listener }
        override fun send(message: GameClientMessage): Boolean { frames.add(message); return true }
        override fun disconnect() = Unit
        override fun close() = Unit
    }
    private inner class Fixture {
        val worker = Worker(); val authWorker = Worker(); val posts = ArrayDeque<() -> Unit>(); val wire = Wire()
        var identity = recipient; var httpGame = game; var visible = true
        lateinit var lobby: LobbyController
        val accounts = object : Accounts {
            override suspend fun identity(token: String) = identity
            override suspend fun register(username: String, email: String, password: String) = identity
            override suspend fun login(email: String, password: String) = SessionCreated("B".repeat(43), identity)
            override suspend fun revoke(token: String) = Unit
            override fun close() = Unit
        }
        val auth = AuthController(accounts, object : SessionStore {
            override fun load() = StoredSession.Present("A".repeat(43))
            override fun save(token: String) = Unit
            override fun clear() = Unit
        }, authWorker, clearPrivateWork = { lobby.clear() }, post = { posts.add(it) })
        val api = object : Lobbies {
            override suspend fun search(token: String, query: String) = UserSearch(emptyList())
            override suspend fun contacts(token: String, after: String?) = ContactList(emptyList())
            override suspend fun add(token: String, input: AddContact) = inviter
            override suspend fun create(token: String, input: CreateInvitation) = httpGame
            override suspend fun games(token: String, invitations: Boolean, after: String?) = GameList(
                if (visible && invitations == (httpGame.invitationState == InvitationState.PENDING && identity == recipient)) listOf(httpGame) else emptyList())
            override suspend fun head(token: String, id: String) = httpGame
            override fun close() = Unit
        }
        init {
            lobby = LobbyController(api, auth::sessionToken, worker, { posts.add(it) },
                openGame = { auth.navigate(ShellDestination.GAMES) }, gameConnection = wire,
                accountId = { auth.state.account?.accountId }, authenticationRequired = auth::authenticationRequired)
            drain(); auth.navigate(ShellDestination.GAMES); lobby.refresh(); drain()
        }
        fun drain() {
            while (worker.queue.isNotEmpty() || authWorker.queue.isNotEmpty() || posts.isNotEmpty()) {
                worker.drain(); authWorker.drain(); while (posts.isNotEmpty()) posts.removeFirst()()
            }
        }
        fun message(message: GameServerMessage) { wire.listener.message(message); drain() }
        fun ready(statuses: List<GameRequestStatus> = emptyList(), generation: String = "1") {
            wire.listener.opened(); drain(); message(GameWelcome())
            val stream = wire.frames.mapNotNull { when (it) { is OpenGameStream -> it.streamId; is ResumeGameStream -> it.streamId; else -> null } }.last()
            message(GameStreamReady(game.id, stream, generation, statuses)); message(GameHead(game.id, httpGame.revision, httpGame))
        }
        fun close() { lobby.close(); auth.close(); drain() }
    }
    @Test fun nativeControllerCapturesAttemptAcceptOpensSameLobbyAndStaleHttpCannotRewindIt() {
        val f = Fixture()
        try {
            f.lobby.respond(game, true); assertTrue(f.wire.frames.filterIsInstance<GameCommand>().isEmpty())
            f.ready(); assertTrue(f.wire.frames.any { it is OpenGameStream }); assertFalse(f.wire.frames.any { it is ResumeGameStream })
            f.lobby.reinvite(game); assertTrue(f.wire.frames.filterIsInstance<GameCommand>().isEmpty()) // recipient cannot reinvite
            f.lobby.respond(game, true)
            val command = f.wire.frames.filterIsInstance<GameCommand>().single()
            assertEquals(GameScope(game.id, game.preparationRound, game.invitationId), command.scope)
            val accepted = game.copy(invitationState = InvitationState.ACCEPTED, revision = "2")
            f.message(GameRevision(game.id, "0", "2", command.requestId, recipient.accountId, command.scope, accepted))
            assertNull(f.lobby.state.selected); assertTrue(f.lobby.state.invitations.isEmpty())
            f.message(GameResult(command.requestId, command.scope, "accepted", ingressOrdinal = "1", commitRevision = "1"))
            assertEquals(game.id, f.lobby.state.selected); assertEquals(accepted, f.lobby.state.detail)
            assertEquals(listOf(accepted), f.lobby.state.games); assertTrue(f.lobby.state.invitations.isEmpty())
            f.lobby.back(); f.drain(); assertNull(f.lobby.state.selected)
            assertEquals(listOf(accepted), f.lobby.state.games)
        } finally { f.close() }
    }
    @Test fun transportToAuthSameAccountResumesOriginalButDifferentAccountAndLogoutFenceQueuesAndCallbacks() {
        for (switch in listOf(false, true)) {
            val f = Fixture()
            try {
                f.ready(); f.lobby.respond(game, false)
                val original = f.wire.frames.filterIsInstance<GameCommand>().single(); val old = f.wire.listener
                f.message(GameAdmissionError("unauthorized", false)); assertNull(f.auth.state.account)
                assertEquals(setOf(game.id), f.lobby.state.pendingGames)
                if (switch) f.identity = inviter
                f.auth.edit(email = "synthetic@example.test", password = "password"); f.auth.submit(); f.drain()
                if (switch) {
                    assertTrue(f.lobby.state.pendingGames.isEmpty()); assertTrue(f.lobby.state.invitations.isEmpty())
                } else {
                    assertEquals(ShellDestination.GAMES, f.auth.state.destination)
                    f.lobby.sync(); f.drain()
                    f.ready(listOf(GameRequestStatus(original.requestId, "notAdmitted")), "2")
                    assertEquals(original.copy(generation = "2"), f.wire.frames.filterIsInstance<GameCommand>().last())
                    val declined = game.copy(invitationState = InvitationState.DECLINED, revision = "1")
                    f.message(GameHead(game.id, "1", declined))
                    f.message(GameResult(original.requestId, original.scope, "accepted", ingressOrdinal = "1", commitRevision = "1"))
                    assertTrue(f.lobby.state.invitations.isEmpty()); assertTrue(f.lobby.state.games.isEmpty())
                }
                old.message(GameHead(game.id, "99", game.copy(revision = "99"))); f.drain()
                assertTrue(f.lobby.state.invitations.isEmpty())
                val late = f.wire.listener; f.auth.logout(); f.drain()
                late.message(GameHead(game.id, "100", game.copy(revision = "100"))); f.drain()
                assertTrue(f.lobby.state.games.isEmpty()); assertTrue(f.lobby.state.pendingGames.isEmpty())
            } finally { f.close() }
        }
    }
}
