package org.asyncttwiz.android

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import org.asyncttwiz.protocol.*
import org.asyncttwiz.storage.*
import org.junit.Test
import java.util.UUID
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit
import kotlin.test.*

class DeckReauthenticationTest {
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
    private class Store : SessionStore {
        var value: StoredSession = StoredSession.Present("A".repeat(43))
        override fun load() = value
        override fun save(token: String) { value = StoredSession.Present(token) }
        override fun clear() { value = StoredSession.Empty }
    }
    private class AccountsApi : Accounts {
        var account = AccountIdentity(UUID.randomUUID().toString(), "Wizard")
        var failure: AccountFailure? = null
        override suspend fun identity(token: String) = account
        override suspend fun login(email: String, password: String): SessionCreated {
            failure?.let { throw it }; return SessionCreated("B".repeat(43), account)
        }
        override suspend fun register(username: String, email: String, password: String) = account
        override suspend fun revoke(token: String) = Unit
        override fun close() = Unit
    }
    private val resolved = DeckImportResult("4 Retained text", listOf(ResolvedDeckEntry(
        DeckSection.MAINBOARD, 4, listOf(CatalogPrinting(UUID.randomUUID().toString(), "Retained text",
            "SET", "7", "English", "", null, false, emptyList(),
            """{"name":"Retained text","type":"Creature","text":"Private 🧙"}""")), listOf(1))),
        emptyList(), CatalogMetadata("2026-10-22", "5.2.2", "checksum"))
    private val summary = DeckSummary(UUID.randomUUID().toString(), "Deck", "1", "2026-10-22T00:00:00Z")
    private val plan = DeckSavePlan.from(resolved, summary.name)
    private val saved = SavedDeck(summary, plan.deck.entries)
    private fun login(auth: AuthController, worker: Worker) {
        auth.edit(email = "wizard@example.test", password = "new password")
        auth.submit(); worker.drain()
    }

    @Test fun everyPrivateDeckRouteReportsExplicit401EvenWithNonJsonBodyBut503DoesNot() = runBlocking {
        val rejected = mutableListOf<String>(); var status = HttpStatusCode.Unauthorized
        val api = DeckApi("https://review.example", HttpClient(MockEngine {
            respond("gateway body", status)
        }), rejected::add)
        val token = "A".repeat(43)
        try {
            val calls: List<suspend () -> Unit> = listOf(
                { api.list(token) }, { api.read(token, summary.id) },
                { api.definition(token, summary.id, plan.deck.entries.single().definition) },
                { api.submit(token, plan.definitions) }, { api.create(token, plan.deck) },
                { api.update(token, summary.id, UpdateDeck(UUID.randomUUID().toString(), name = "Rename")) },
                { api.delete(token, summary.id, DeleteDeck(UUID.randomUUID().toString())) })
            calls.forEach { call -> assertEquals("authentication_required", assertFailsWith<DeckFailure> { call() }.code) }
            assertEquals(List(7) { token }, rejected)
            status = HttpStatusCode.ServiceUnavailable
            assertEquals("unavailable", assertFailsWith<DeckFailure> { api.list(token) }.code)
            assertEquals(7, rejected.size)
        } finally { api.close() }
    }

    @Test fun sameAccountCreateAndMergeRetriesKeepUncertainPayloadAndDraftAcrossSignIn() {
        listOf(false, true).forEach { merge ->
            val worker = Worker(); val authWorker = Worker(); val accounts = AccountsApi(); val store = Store()
            var clears = 0
            val importer = DeckImportController({ resolved }, worker) { it() }
            lateinit var library: DeckLibraryController
            val auth = AuthController(accounts, store, authWorker,
                clearPrivateWork = { clears++; importer.exit(); library.clear() }, post = { it() })
            // An in-flow step leaves a non-empty history that same-account reauthentication must keep.
            authWorker.drain(); auth.navigate(ShellDestination.PODS); auth.push(ShellDestination.DECKS); authWorker.drain()
            var revoked = false; var lostReplies = 3; var effects = 0
            val mutationBodies = mutableListOf<String>(); val tokens = mutableListOf<String>()
            val identities = mutableSetOf<String>()
            var current = saved
            val api = DeckApi("https://review.example", HttpClient(MockEngine { request ->
                tokens.add(request.headers[HttpHeaders.Authorization]!!)
                if (revoked) respond("revoked", HttpStatusCode.Unauthorized)
                else {
                    val path = request.url.encodedPath
                    val body = when {
                        path == "/v1/definitions" -> DeckJson.format.encodeToString(DefinitionsAccepted(plan.definitions.definitions.map { it.digest }))
                        request.method == HttpMethod.Post || request.method == HttpMethod.Put -> {
                            val text = request.body.toByteArray().decodeToString(); mutationBodies.add(text)
                            if (identities.add(text)) {
                                effects++
                                current = saved.copy(entries = saved.entries.map { it.copy(quantity = if (merge) 6 else 4) })
                            }
                            if (lostReplies-- > 0) {
                                return@MockEngine respond("unavailable", HttpStatusCode.ServiceUnavailable)
                            }
                            DeckJson.format.encodeToString(current)
                        }
                        path.contains("/definitions/") -> DeckJson.format.encodeToString(plan.definitions.definitions.single())
                        else -> DeckJson.format.encodeToString(current)
                    }
                    respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                }
            }), auth::authenticationRequired)
            library = DeckLibraryController(api, auth::sessionToken, worker) { it() }
            importer.connectSave(api, auth::sessionToken) { library.open(it.deck.id) }
            try {
                if (merge) { importer.openExisting(summary); importer.mode(DeckImportMode.MERGE) } else importer.open()
                importer.edit(resolved.text); importer.lookup(); worker.drain(); importer.review(); importer.name(summary.name)
                importer.save(); worker.drain() // Committed but response lost: identity must survive the next 401.
                assertNotNull(importer.state.error); assertEquals(1, effects)
                revoked = true; importer.save(); worker.drain(); authWorker.drain()
                assertNull(auth.state.account); assertNull(auth.sessionToken()); assertIs<StoredSession.Empty>(store.value)
                assertTrue(importer.state.preview); assertEquals(resolved.text, importer.state.text)
                accounts.failure = AccountFailure("invalid_credentials"); login(auth, authWorker)
                assertNull(auth.state.account); assertTrue(importer.state.preview)
                accounts.failure = null; login(auth, authWorker)
                assertNotNull(auth.state.account); assertEquals(ShellDestination.DECKS, auth.state.destination)
                assertEquals(listOf(ShellDestination.PODS), auth.state.backStack); assertEquals(0, clears)
                // A delayed failure of the old request must not invalidate the replacement credential.
                auth.authenticationRequired("A".repeat(43)); assertNotNull(auth.state.account)
                revoked = false; importer.save(); worker.drain()
                assertFalse(importer.state.active); assertEquals(if (merge) 1 else 2, effects)
                assertEquals(if (merge) 4 else 5, mutationBodies.size)
                assertEquals(1, mutationBodies.take(4).distinct().size)
                if (!merge) {
                    val coverUpdate = DeckJson.format.decodeFromString<UpdateDeck>(mutationBodies.last())
                    assertEquals(plan.deck.entries.first().definition, coverUpdate.cover)
                    assertNull(coverUpdate.contents)
                }
                assertEquals(if (merge) 6 else 4, library.state.detail!!.saved.entries.single().quantity)
                assertTrue(tokens.contains("Bearer " + "B".repeat(43)))
            } finally { importer.close(); library.close(); worker.drain(); auth.close(); authWorker.drain() }
        }
    }

    @Test fun readDefinitionAndMutationInvalidationKeepSameAccountSelectionAndRenameIdentity() {
        listOf("read", "definition", "update", "delete").forEach { rejectedRoute ->
            val worker = Worker(); val authWorker = Worker(); val auth = AuthController(AccountsApi(), Store(), authWorker) { it() }
            authWorker.drain(); auth.navigate(ShellDestination.DECKS); authWorker.drain()
            var reject = false; val mutationBodies = mutableListOf<String>()
            val api = DeckApi("https://review.example", HttpClient(MockEngine { request ->
                val route = if (request.method == HttpMethod.Put) "update" else if (request.method == HttpMethod.Delete) "delete"
                    else if (request.url.encodedPath.contains("/definitions/")) "definition"
                    else if (request.url.encodedPath == "/v1/decks") "list" else "read"
                if (route == "update" || route == "delete") mutationBodies.add(request.body.toByteArray().decodeToString())
                if (reject && route == rejectedRoute) respond("revoked", HttpStatusCode.Unauthorized)
                else respond(when (route) {
                    "definition" -> DeckJson.format.encodeToString(plan.definitions.definitions.single())
                    "delete" -> DeckJson.format.encodeToString(DeckDeleted(summary.id))
                    "list" -> DeckJson.format.encodeToString(DeckList(emptyList()))
                    else -> DeckJson.format.encodeToString(saved)
                }, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            }), auth::authenticationRequired)
            val library = DeckLibraryController(api, auth::sessionToken, worker) { it() }
            try {
                if (rejectedRoute in listOf("update", "delete")) {
                    library.open(summary.id); worker.drain()
                    library.edit(if (rejectedRoute == "delete") DeckDialog.DELETE else DeckDialog.RENAME)
                    library.name("Unfinished rename 🧙")
                    reject = true; library.confirm()
                } else { reject = true; library.open(summary.id) }
                worker.drain(); authWorker.drain(); assertNull(auth.state.account)
                assertEquals(summary.id, library.state.selected)
                login(auth, authWorker); reject = false
                if (rejectedRoute in listOf("update", "delete")) {
                    assertEquals("Unfinished rename 🧙", library.state.draftName)
                    library.confirm(); worker.drain()
                    assertEquals(2, mutationBodies.size); assertEquals(mutationBodies.first(), mutationBodies.last())
                } else { library.retry(); worker.drain(); assertNotNull(library.state.detail) }
                assertNull(library.state.error)
            } finally { library.close(); worker.drain(); auth.close(); authWorker.drain() }
        }
    }

    @Test fun differentAccountClearsPrivateWorkAndFencesQueuedCallbacksBeforeEnteringShell() {
        val worker = Worker(); val authWorker = Worker(); val posts = ArrayDeque<() -> Unit>(); val accounts = AccountsApi()
        val importer = DeckImportController({ resolved }, worker) { posts.add(it) }
        lateinit var library: DeckLibraryController
        var clears = 0
        val auth = AuthController(accounts, Store(), authWorker,
            clearPrivateWork = { clears++; importer.exit(); library.clear() }, post = { it() })
        authWorker.drain(); auth.navigate(ShellDestination.DECKS); authWorker.drain()
        val api = DeckApi("https://review.example", HttpClient(MockEngine { request ->
            respond(if (request.url.encodedPath.contains("/definitions/"))
                DeckJson.format.encodeToString(plan.definitions.definitions.single()) else DeckJson.format.encodeToString(saved),
                HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }), auth::authenticationRequired)
        library = DeckLibraryController(api, auth::sessionToken, worker) { posts.add(it) }
        try {
            importer.open(); importer.edit("private draft"); importer.lookup()
            library.open(summary.id); worker.drain() // Both old-account success callbacks wait on the UI queue.
            auth.authenticationRequired("A".repeat(43)); authWorker.drain()
            accounts.account = AccountIdentity(UUID.randomUUID().toString(), "Different wizard")
            login(auth, authWorker)
            assertEquals(1, clears); assertEquals(ShellDestination.MENU, auth.state.destination)
            while (posts.isNotEmpty()) posts.removeFirst()()
            assertEquals("", importer.state.text); assertFalse(importer.state.active); assertNull(importer.state.result)
            assertNull(library.state.detail); assertNull(library.state.selected); assertTrue(library.state.decks.isEmpty())
            assertEquals(accounts.account, auth.state.account)
        } finally { importer.close(); library.close(); worker.drain(); auth.close(); authWorker.drain() }
    }

    @Test fun explicitLogoutDuringReauthenticationStillDiscardsDraftsAndReturnContext() {
        val worker = Worker(); val authWorker = Worker(); val importer = DeckImportController({ resolved }, worker) { it() }
        var clears = 0
        val auth = AuthController(AccountsApi(), Store(), authWorker,
            clearPrivateWork = { clears++; importer.exit() }, post = { it() })
        try {
            authWorker.drain(); auth.navigate(ShellDestination.DECKS); authWorker.drain()
            importer.open(); importer.edit("private draft")
            auth.authenticationRequired("A".repeat(43)); authWorker.drain()
            assertTrue(importer.state.active)
            auth.logout(); authWorker.drain(); login(auth, authWorker)
            assertEquals(1, clears); assertFalse(importer.state.active); assertEquals("", importer.state.text)
            assertEquals(ShellDestination.MENU, auth.state.destination); assertTrue(auth.state.backStack.isEmpty())
        } finally { importer.close(); auth.close(); authWorker.drain() }
    }
}
