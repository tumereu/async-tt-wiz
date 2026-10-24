package org.asyncttwiz.android

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import org.asyncttwiz.protocol.*
import org.asyncttwiz.storage.*
import org.junit.Test
import java.util.UUID
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit
import kotlin.test.*

/** Independent B2 review: production import/library/auth controllers at their integration seams. */
class B2DeckIntegrationTest {
    private class Worker : AbstractExecutorService() {
        private val queue = ArrayDeque<Runnable>()
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
        val account = AccountIdentity(UUID.randomUUID().toString(), "Review wizard")
        override suspend fun identity(token: String) = account
        override suspend fun login(email: String, password: String) = SessionCreated("B".repeat(43), account)
        override suspend fun register(username: String, email: String, password: String) = account
        override suspend fun revoke(token: String) = Unit
        override fun close() = Unit
    }
    private val printing = CatalogPrinting(UUID.randomUUID().toString(), "Retained text", "SET", "7",
        "English", "", null, false, emptyList(),
        """{"name":"Retained text","type":"Creature","text":"Exact  text 🧙","power":"*","toughness":"2"}""")
    private val resolved = DeckImportResult("4 Retained text", listOf(
        ResolvedDeckEntry(DeckSection.MAINBOARD, 4, listOf(printing), listOf(1))), emptyList(),
        CatalogMetadata("2026-10-22", "5.2.2", "catalog-checksum"))
    private val summary = DeckSummary(UUID.randomUUID().toString(), "Review deck", "1", "2026-10-22T00:00:00Z")

    private class DeckServer(var saved: SavedDeck, val definitions: List<SubmittedDefinition>) : Decks {
        var creates = 0
        override suspend fun submit(token: String, value: SubmitDefinitions) = DefinitionsAccepted(value.definitions.map { it.digest })
        override suspend fun create(token: String, value: CreateDeck): SavedDeck {
            creates++; saved = SavedDeck(saved.deck.copy(name = value.name), value.entries); return saved
        }
        override suspend fun list(token: String, after: String?) = DeckList(listOf(saved.deck))
        override suspend fun read(token: String, id: String) = saved
        override suspend fun definition(token: String, id: String, digest: String) = definitions.single { it.digest == digest }
        override suspend fun update(token: String, id: String, value: UpdateDeck) = saved
        override suspend fun delete(token: String, id: String, value: DeleteDeck) = DeckDeleted(id)
        override fun close() = Unit
    }

    @Test fun acceptedImportOpensRetainedDefinitionAndSyncDoesNotRetargetInspection() {
        val worker = Worker()
        val plan = DeckSavePlan.from(resolved, summary.name)
        val server = DeckServer(SavedDeck(summary, plan.deck.entries), plan.definitions.definitions)
        val library = DeckLibraryController(server, { "token" }, worker) { it() }
        val importer = DeckImportController({ resolved }, worker) { it() }
        importer.connectSave({ { input -> runBlocking {
            server.submit("token", input.definitions); server.create("token", input.deck)
        } } }, { library.open(it.deck.id) })
        importer.open(); importer.edit(resolved.text); importer.lookup(); worker.drain()
        importer.review(); importer.name(summary.name); importer.save(); worker.drain()
        assertFalse(importer.state.active)
        assertEquals(1, server.creates)
        val digest = plan.deck.entries.single().definition
        assertEquals(4, library.state.detail!!.saved.entries.single().quantity)
        library.inspect(digest)
        val captured = library.inspection.state!!.card
        assertEquals("Exact  text 🧙", captured.definition.faces.single().rulesText)
        assertNull(captured.artwork.single()!!.printing) // Missing art does not block text inspection.
        library.edit(DeckDialog.RENAME); library.name("Unfinished name")
        server.saved = server.saved.copy(deck = summary.copy(name = "Newer server name", revision = "2"),
            entries = plan.deck.entries.map { it.copy(quantity = 9) })
        library.retry(); worker.drain()
        assertEquals(9, library.state.detail!!.saved.entries.single().quantity)
        assertEquals("Newer server name", library.state.detail!!.saved.deck.name)
        assertEquals("Unfinished name", library.state.draftName)
        assertEquals(captured, library.inspection.state!!.card)
        library.inspection.close(); assertEquals(DeckDialog.RENAME, library.state.dialog)
        importer.close(); library.close(); worker.drain()
    }

    @Test fun explicitLogoutFencesCompletedImportAndLibraryCallbacksTogether() {
        val authWorker = Worker(); val deckWorker = Worker(); val posts = ArrayDeque<() -> Unit>()
        val plan = DeckSavePlan.from(resolved, summary.name)
        val server = DeckServer(SavedDeck(summary, plan.deck.entries), plan.definitions.definitions)
        val importer = DeckImportController({ resolved }, deckWorker) { posts.add(it) }
        lateinit var library: DeckLibraryController
        val store = Store()
        val auth = AuthController(AccountsApi(), store, authWorker,
            clearPrivateWork = { importer.exit(); library.clear() }, post = { it() })
        authWorker.drain()
        library = DeckLibraryController(server, auth::sessionToken, deckWorker) { posts.add(it) }
        importer.connectSave({ auth.sessionToken()?.let { token -> { input -> runBlocking {
            server.submit(token, input.definitions); server.create(token, input.deck)
        } } } }, { library.open(it.deck.id) })
        importer.open(); importer.edit(resolved.text); importer.lookup(); deckWorker.drain(); posts.removeFirst()()
        importer.review(); importer.name(summary.name); importer.save(); deckWorker.drain()
        library.open(summary.id); deckWorker.drain() // Both private UI callbacks are still queued.
        auth.logout(); authWorker.drain()
        while (posts.isNotEmpty()) posts.removeFirst()()
        assertEquals(1, server.creates) // Already-authorized acceptance is permitted by D-289.
        assertNull(auth.state.account); assertNull(auth.sessionToken())
        assertIs<StoredSession.Empty>(store.value)
        assertFalse(importer.state.active); assertEquals("", importer.state.text)
        assertNull(library.state.detail); assertNull(library.state.selected)
        assertNull(library.inspection.state); assertTrue(library.state.decks.isEmpty())
        importer.close(); library.close(); deckWorker.drain(); auth.close(); authWorker.drain()
    }

    /** B2-R01: real transport invalidation reaches the same auth callback wired by AuthViewModel. */
    @Test fun revokedDeckSessionMustExposeSignInWithoutExplicitLogout() {
        val authWorker = Worker(); val deckWorker = Worker(); val store = Store()
        val auth = AuthController(AccountsApi(), store, authWorker) { it() }
        authWorker.drain(); auth.navigate(ShellDestination.DECKS); authWorker.drain()
        val api = DeckApi("https://review.example", HttpClient(MockEngine {
            respond("""{"code":"authentication_required","field":null}""", HttpStatusCode.Unauthorized,
                headersOf("Content-Type", "application/json"))
        }) { followRedirects = false }, authenticationRequired = auth::authenticationRequired)
        val library = DeckLibraryController(api, auth::sessionToken, deckWorker) { it() }
        try {
            library.refresh(); deckWorker.drain()
            authWorker.drain()
            assertTrue(library.state.error!!.contains("Sign in again"))
            println("HTTP 401 reached deck UI; authAccountPresent=${auth.state.account != null}; " +
                "oldTokenStillAvailable=${auth.sessionToken() != null}; destination=${auth.state.destination}")
            assertNull(auth.state.account, "A deck HTTP 401 must expose normal sign-in, not leave an unusable authenticated shell.")
        } finally {
            library.close(); deckWorker.drain(); auth.close(); authWorker.drain()
        }
    }
}
