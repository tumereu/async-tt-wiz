package org.asyncttwiz.android

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import org.asyncttwiz.protocol.*
import org.junit.Test
import java.util.UUID
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit
import kotlin.test.*

class LobbyControllerTest {
    @Test fun ownerChangeMidPaginationDropsResultAndStopsRemainingPrivateReads() {
        val worker = Worker(); val posts = mutableListOf<() -> Unit>()
        lateinit var controller: LobbyController
        var calls = 0
        val api = object : Api() {
            override suspend fun contacts(token: String, after: String?): ContactList {
                calls++; controller.clear()
                return ContactList(listOf(recipient), "next")
            }
            override suspend fun games(token: String, invitations: Boolean, after: String?): GameList = error("Obsolete game read")
        }
        controller = LobbyController(api, { "token" }, worker, { posts += it })
        try {
            controller.refresh(); worker.drain()
            posts.forEach { it() }
            assertEquals(1, calls)
            assertTrue(controller.state.contacts.isEmpty())
            assertFalse(controller.state.busy)
            assertNull(controller.state.error)
        } finally { controller.close(); worker.drain() }
    }
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
    private val owner = AccountIdentity(id(), "Inviter")
    private val recipient = AccountIdentity(id(), "Recipient")
    private val game = GameSummary(id(), "Azure Spark", "2026-10-22T00:00:00Z", owner, recipient, id(), id(), InvitationState.PENDING)
    private open inner class Api : Lobbies {
        var contacts = listOf(recipient)
        var games = emptyList<GameSummary>()
        val creates = mutableListOf<CreateInvitation>()
        val adds = mutableListOf<AddContact>()
        var loseReply = false
        override suspend fun search(token: String, query: String) = UserSearch(listOf(recipient))
        override suspend fun contacts(token: String, after: String?) = ContactList(contacts)
        override suspend fun games(token: String, invitations: Boolean, after: String?) = GameList(if (invitations) emptyList() else games)
        override suspend fun head(token: String, id: String) = game.copy(id = id)
        override suspend fun add(token: String, input: AddContact): AccountIdentity { adds.add(input); return recipient }
        override suspend fun create(token: String, input: CreateInvitation): GameSummary {
            creates.add(input); games = listOf(game)
            if (loseReply) throw LobbyFailure("network")
            return game.copy(gameName = input.gameName)
        }
        override fun close() = Unit
    }

    @Test fun capturedRecipientSuggestionsCancelBlankAndUncertainOriginalCreate() {
        val worker = Worker(); val api = Api(); var opens = 0; var names = 0
        val controller = LobbyController(api, { "A".repeat(43) }, worker, { it() }, { opens++ }, { "Suggestion ${++names}" })
        try {
            controller.refresh(); worker.drain(); controller.begin(recipient)
            assertEquals("Suggestion 1", controller.state.draft!!.name)
            controller.reroll(); assertEquals("Suggestion 2", controller.state.draft!!.name)
            controller.dismiss(); assertTrue(api.creates.isEmpty())
            controller.begin(recipient); controller.name(" \n "); controller.create(); worker.drain()
            assertTrue(api.creates.isEmpty()); assertEquals("Enter a game name.", controller.state.error)
            controller.name(" Exact edited name "); api.loseReply = true; controller.create(); worker.drain()
            val original = api.creates.first(); assertEquals(3, api.creates.size); assertEquals(1, api.creates.distinct().size); assertTrue(controller.state.uncertain)
            controller.name("Do not change uncertain intent"); controller.reroll()
            assertEquals(original.gameName, controller.state.draft!!.name)
            controller.dismiss(); controller.begin(recipient); assertEquals(original.gameName, controller.state.draft!!.name)
            api.loseReply = false
            controller.create(); worker.drain(); assertEquals(List(4) { original }, api.creates)
            assertEquals(recipient.accountId, original.recipient); assertEquals(1, opens)
            assertEquals(game.id, controller.state.selected); assertEquals(game.id, controller.state.detail!!.id)
            controller.back(); worker.drain(); assertNull(controller.state.selected)
            controller.open(game.id); worker.drain(); assertEquals(game.id, controller.state.detail!!.id)
        } finally { controller.close(); worker.drain() }
    }

    @Test fun oldSearchAndPrivateCallbacksCannotReplaceEditedSearchOrClearedOwner() {
        val worker = Worker(); val posts = ArrayDeque<() -> Unit>(); val api = Api()
        val controller = LobbyController(api, { "A".repeat(43) }, worker, { posts.add(it) })
        try {
            controller.query("old"); controller.search(); worker.drain()
            controller.query("new"); while (posts.isNotEmpty()) posts.removeFirst()()
            assertEquals("new", controller.state.query); assertTrue(controller.state.results.isEmpty())
            controller.refresh(); worker.drain(); controller.clear()
            while (posts.isNotEmpty()) posts.removeFirst()()
            assertTrue(controller.state.contacts.isEmpty()); assertTrue(controller.state.games.isEmpty()); assertFalse(controller.state.loaded)
            controller.refresh(); controller.clear(); worker.drain()
            assertTrue(posts.isEmpty()) // worker-side fence stops even queued reads
        } finally { controller.close(); worker.drain() }
    }

    @Test fun backgroundRefreshRetainsAcceptedListsAndReportsRealErrors() {
        val worker = Worker(); val posts = ArrayDeque<() -> Unit>(); var failing = false
        val api = object : Api() {
            override suspend fun contacts(token: String, after: String?): ContactList {
                if (failing) throw LobbyFailure("network")
                return super.contacts(token, after)
            }
        }
        val controller = LobbyController(api, { "token" }, worker, { posts.add(it) })
        fun deliver() { worker.drain(); while (posts.isNotEmpty()) posts.removeFirst()() }
        try {
            controller.refresh(); deliver()
            failing = true
            controller.refresh(); worker.drain()
            controller.foreground(); deliver()
            assertNotNull(controller.state.error)
            assertEquals(listOf(recipient), controller.state.contacts)
            controller.sync()
            assertTrue(controller.state.backgroundLoading)
            deliver()
            assertNotNull(controller.state.error)
            controller.query("Player"); controller.search(); deliver()
            assertEquals(listOf(recipient), controller.state.results)
            failing = false; controller.sync(); deliver()
            assertFalse(controller.state.busy)
            assertNull(controller.state.error)
        } finally { controller.close(); worker.drain() }
    }

    @Test fun lifecycleInterruptionsAreSilentButOrdinaryAndTerminalErrorsAreNot() {
        for (failure in listOf(kotlinx.coroutines.CancellationException("Backgrounded"),
            java.net.SocketException("Socket closed"), LobbyFailure("network"))) {
            assertTrue(lobbyLifecycleInterruption(failure, interrupted = true))
            assertFalse(lobbyLifecycleInterruption(failure, interrupted = false))
        }
        assertFalse(lobbyLifecycleInterruption(LobbyFailure("authentication_required"), interrupted = true))
        assertFalse(lobbyLifecycleInterruption(LobbyFailure("invalid_response"), interrupted = true))
    }

    @Test fun cancellationDuringBackgroundIsSilentAndResumeRetriesWithoutAnIndicator() {
        val worker = Worker(); var calls = 0
        lateinit var controller: LobbyController
        val api = object : Api() {
            override suspend fun contacts(token: String, after: String?): ContactList {
                if (++calls == 1) {
                    controller.background()
                    throw kotlinx.coroutines.CancellationException("App backgrounded")
                }
                return super.contacts(token, after)
            }
        }
        controller = LobbyController(api, { "token" }, worker, { it() })
        try {
            controller.refresh(); worker.drain()
            assertNull(controller.state.error)
            assertFalse(controller.state.busy)
            controller.sync(); worker.drain()
            assertEquals(1, calls)
            controller.foreground()
            assertTrue(controller.state.backgroundLoading)
            worker.drain()
            assertEquals(2, calls)
            assertEquals(listOf(recipient), controller.state.contacts)
            assertNull(controller.state.error)
        } finally { controller.close(); worker.drain() }
    }

    @Test fun signedOutResumeAllowsRefreshAfterLogin() {
        val worker = Worker(); var token: String? = null
        val controller = LobbyController(Api(), { token }, worker, { it() })
        try {
            controller.background(); controller.foreground(); worker.drain()
            assertFalse(controller.state.busy)
            assertFalse(controller.state.loaded)
            token = "token"
            controller.refresh(); worker.drain()
            assertTrue(controller.state.loaded)
            assertEquals(listOf(recipient), controller.state.contacts)
            assertNull(controller.state.error)
        } finally { controller.close(); worker.drain() }
    }

    @Test fun resumeFencesLateFailuresAndDoesNotLetAnOldBusyLoadBlockTheRetry() {
        val worker = Worker(); val posts = ArrayDeque<() -> Unit>(); var failing = true
        val api = object : Api() {
            override suspend fun contacts(token: String, after: String?): ContactList {
                if (failing) throw kotlinx.coroutines.CancellationException("Old request cancelled")
                return super.contacts(token, after)
            }
        }
        val controller = LobbyController(api, { "token" }, worker, { posts.add(it) })
        try {
            controller.refresh(); worker.drain()
            assertTrue(controller.state.busy)
            controller.background(); failing = false; controller.foreground()
            posts.removeFirst()()
            assertNull(controller.state.error)
            assertTrue(controller.state.busy)
            assertTrue(controller.state.backgroundLoading)
            worker.drain(); while (posts.isNotEmpty()) posts.removeFirst()()
            assertFalse(controller.state.busy)
            assertTrue(controller.state.loaded)
            assertNull(controller.state.error)
        } finally { controller.close(); worker.drain() }
    }

    @Test fun listPagesAndCapturedRowIdsSurviveRefreshWithoutRetargeting() {
        val worker = Worker(); val other = recipient.copy(accountId = id(), username = "Other")
        val api = object : Api() {
            override suspend fun contacts(token: String, after: String?) = if (after == null) ContactList(listOf(recipient), recipient.accountId) else ContactList(listOf(other))
            override suspend fun games(token: String, invitations: Boolean, after: String?) =
                if (invitations) GameList(listOf(game.copy(id = id()))) else GameList(listOf(game))
        }
        val controller = LobbyController(api, { "A".repeat(43) }, worker, { it() }, suggest = { "Test Mist" })
        try {
            controller.refresh(); worker.drain(); assertEquals(listOf(recipient, other), controller.state.contacts)
            controller.begin(recipient); controller.refresh(); worker.drain(); controller.create(); worker.drain()
            assertEquals(recipient.accountId, api.creates.single().recipient)
            assertEquals(game.id, controller.state.selected)
            assertEquals(1, controller.state.invitations.size)
        } finally { controller.close(); worker.drain() }
    }

    @Test fun everyTransportRouteReports401ButDoesNotTreatOrdinaryFailureAsLogout() = runBlocking {
        var status = HttpStatusCode.Unauthorized; val rejected = mutableListOf<String>()
        val api = LobbyApi("https://example.test", HttpClient(MockEngine { respond("gateway text", status) }), rejected::add)
        val token = "A".repeat(43)
        try {
            val calls: List<suspend () -> Unit> = listOf({ api.search(token, "user") }, { api.contacts(token) },
                { api.add(token, AddContact(id(), recipient.accountId)) }, { api.create(token, CreateInvitation(id(), recipient.accountId, "Name")) },
                { api.games(token) }, { api.games(token, true) }, { api.head(token, game.id) })
            calls.forEach { assertEquals("authentication_required", assertFailsWith<LobbyFailure> { it() }.code) }
            assertEquals(List(7) { token }, rejected)
            status = HttpStatusCode.ServiceUnavailable
            assertEquals("unavailable", assertFailsWith<LobbyFailure> { api.contacts(token) }.code); assertEquals(7, rejected.size)
        } finally { api.close() }
    }

    @Test fun realTransportAndAuthKeepOriginalIntentThroughSameAccountReauthAndClearOnDifferentAccountOrLogout() {
        for (switch in listOf(false, true)) {
            val worker = Worker(); val authWorker = Worker(); val posts = ArrayDeque<() -> Unit>()
            var identity = owner; var clearCount = 0
            val accounts = object : Accounts {
                override suspend fun identity(token: String) = identity
                override suspend fun register(username: String, email: String, password: String) = identity
                override suspend fun login(email: String, password: String) = SessionCreated("B".repeat(43), identity)
                override suspend fun revoke(token: String) = Unit
                override fun close() = Unit
            }
            val store = object : SessionStore {
                var saved: StoredSession = StoredSession.Present("A".repeat(43))
                override fun load() = saved
                override fun save(token: String) { saved = StoredSession.Present(token) }
                override fun clear() { saved = StoredSession.Empty }
            }
            lateinit var lobby: LobbyController
            val auth = AuthController(accounts, store, authWorker, clearPrivateWork = { clearCount++; lobby.clear() }, post = { it() })
            authWorker.drain(); auth.navigate(ShellDestination.CONTACTS)
            val bodies = mutableListOf<String>(); var revoked = false; var lostReplies = 3
            val api = LobbyApi("https://example.test", HttpClient(MockEngine { request ->
                if (revoked) respond("revoked", HttpStatusCode.Unauthorized)
                else {
                    if (request.method == HttpMethod.Post) {
                        bodies.add(request.body.toByteArray().decodeToString())
                        if (lostReplies-- > 0) return@MockEngine respond("lost reply", HttpStatusCode.ServiceUnavailable)
                    }
                    respond(when (request.url.encodedPath) {
                        "/v1/contacts" -> DeckJson.format.encodeToString(ContactList(listOf(recipient)))
                        "/v1/invitations" -> if (request.method == HttpMethod.Post) DeckJson.format.encodeToString(game) else DeckJson.format.encodeToString(GameList(emptyList()))
                        "/v1/games" -> DeckJson.format.encodeToString(GameList(listOf(game)))
                        else -> DeckJson.format.encodeToString(game)
                    }, HttpStatusCode.OK)
                }
            }), auth::authenticationRequired)
            lobby = LobbyController(api, auth::sessionToken, worker, { posts.add(it) })
            fun drain() { while (worker.queue.isNotEmpty() || posts.isNotEmpty()) {
                worker.drain(); while (posts.isNotEmpty()) posts.removeFirst()()
            } }
            try {
                lobby.refresh(); drain(); lobby.begin(recipient); lobby.name("Captured Spark"); lobby.create(); drain()
                assertTrue(lobby.state.uncertain); revoked = true; lobby.create(); drain(); authWorker.drain()
                assertNull(auth.state.account); assertEquals("Captured Spark", lobby.state.draft!!.name)
                if (switch) identity = recipient
                auth.edit(email = "user@example.test", password = "password"); auth.submit(); authWorker.drain()
                revoked = false
                if (switch) { assertEquals(1, clearCount); assertNull(lobby.state.draft); assertTrue(lobby.state.contacts.isEmpty()) }
                else {
                    assertEquals(0, clearCount); assertEquals(ShellDestination.CONTACTS, auth.state.destination)
                    lobby.create(); drain(); assertEquals(4, bodies.size); assertEquals(1, bodies.distinct().size)
                }
                lobby.refresh(); worker.drain(); auth.logout(); authWorker.drain()
                while (posts.isNotEmpty()) posts.removeFirst()()
                assertNull(lobby.state.detail); assertTrue(lobby.state.games.isEmpty()); assertTrue(lobby.state.contacts.isEmpty())
            } finally { lobby.close(); worker.drain(); auth.close(); authWorker.drain() }
        }
    }
}
