package org.asyncttwiz.android

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.content.OutgoingContent
import kotlinx.coroutines.runBlocking
import org.asyncttwiz.protocol.*
import org.asyncttwiz.storage.*
import org.junit.Test
import java.util.UUID
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit
import kotlin.test.*

class DeckSaveTest {
    private val first = UUID.randomUUID().toString()
    private val second = UUID.randomUUID().toString()
    private val summary = DeckSummary(UUID.randomUUID().toString(), " Exact name ", "1", "2026-10-22T00:00:00Z")
    private fun result(): DeckImportResult {
        fun face(id: String, side: String, other: String, name: String) = CatalogPrinting(id, "Front // Back", "SET", "12",
            "English", side, null, false, listOf(other), """{"faceName":"$name","name":"Front // Back","layout":"transform","type":"Creature","power":"*","toughness":"2","text":"Exact  text\nUnicode 🧙"}""")
        val faces = listOf(face(first, "a", second, "Front"), face(second, "b", first, "Back"))
        return DeckImportResult("Deck\n4 Front // Back\nSideboard\n2 Front // Back", listOf(
            ResolvedDeckEntry(DeckSection.MAINBOARD, 4, faces, listOf(2)),
            ResolvedDeckEntry(DeckSection.SIDEBOARD, 2, faces, listOf(4))), emptyList(), CatalogMetadata("2026-10-11", "5.2.2"))
    }
    @Test fun mapsCompleteFacesDefaultsProvenanceAndExactSectionsWithoutArtwork() {
        val plan = DeckSavePlan.from(result(), " Exact name ")
        assertNotEquals(plan.definitions.requestId, plan.deck.requestId)
        val submitted = plan.definitions.definitions.single()
        assertEquals(DeckJson.digest(submitted.definition), submitted.digest)
        assertTrue(DeckValidation.definition(submitted.definition))
        assertEquals(listOf("Front", "Back"), submitted.definition.faces.map { it.name })
        assertEquals(listOf(second), submitted.definition.faces.first().otherFaceIds)
        assertEquals("*/2", submitted.definition.faces.first().corner)
        assertEquals("", submitted.definition.faces.last().defense)
        assertTrue(submitted.definition.faces.first().sourceData!!.contains("transform"))
        assertNull(submitted.definition.faces.first().imageUri)
        assertEquals(" Exact name ", plan.deck.name)
        assertEquals(listOf(4, 2), plan.deck.entries.map { it.quantity })
        assertEquals(SavedDeckSection.entries, plan.deck.entries.map { it.section })
        assertFailsWith<DeckFailure> { DeckSavePlan.from(result(), " \n") }
    }
    @Test fun uncertainRetryKeepsIdentitiesAndInputButChangedIntentGetsNewIds() {
        val worker = Worker(); val posts = mutableListOf<() -> Unit>(); val attempts = mutableListOf<DeckSavePlan>()
        var successes = 0
        val controller = DeckImportController({ result() }, worker) { posts += it }
        controller.connectSave({ { plan -> attempts += plan
            if (attempts.size != 3) throw DeckFailure("network")
            SavedDeck(summary, plan.deck.entries) } }, { successes++ })
        controller.open(); controller.edit(result().text); controller.lookup(); worker.drain(); posts.removeAt(0)()
        controller.review(); controller.name(" Exact name "); controller.save(); controller.save()
        worker.drain(); posts.removeAt(0)()
        assertTrue(controller.state.preview); assertEquals(result().text, controller.state.text)
        assertEquals(" Exact name ", controller.state.name); assertNotNull(controller.state.error)
        // Recreation's lookup is harmless and retry must reuse both complete immutable payloads.
        controller.lookup(); controller.save(); worker.drain(); posts.removeAt(0)()
        assertEquals(attempts[0], attempts[1]); assertEquals(0, successes)
        controller.name("Changed name"); controller.save(); worker.drain(); posts.removeAt(0)()
        assertNotEquals(attempts[1].deck.requestId, attempts[2].deck.requestId)
        assertNotEquals(attempts[1].definitions.requestId, attempts[2].definitions.requestId)
        assertEquals(1, successes); assertFalse(controller.state.active)
    }
    @Test fun logoutFencesSaveAndReadCallbacksAndQueuedOldAccountRequests() {
        val worker = Worker(); val posts = mutableListOf<() -> Unit>(); var success = false; var calls = 0
        val controller = DeckImportController({ result() }, worker) { posts += it }
        controller.connectSave({ { plan -> calls++; SavedDeck(summary, plan.deck.entries) } }, { success = true })
        controller.open(); controller.edit(result().text); controller.lookup(); worker.drain(); posts.removeAt(0)()
        controller.review(); controller.name(summary.name); controller.save(); worker.drain()
        controller.exit(); controller.open(); posts.removeAt(0)(); assertFalse(success); assertEquals("", controller.state.name)
        controller.edit(result().text); controller.lookup(); worker.drain(); posts.removeAt(0)()
        controller.review(); controller.name(summary.name); controller.save(); controller.exit(); worker.drain()
        assertEquals(1, calls)
        val api = FakeDecks(); val library = DeckLibraryController(api, { "token" }, worker) { posts += it }
        library.refresh(); worker.drain(); library.clear(); posts.removeAt(0)()
        assertTrue(library.state.decks.isEmpty()); assertFalse(library.state.loaded)
        library.refresh(); library.clear(); worker.drain(); assertEquals(2, api.pages)
    }
    @Test fun libraryFollowsPagesAndReadsServerDefinitionsWithoutCatalog() {
        val worker = Worker(); val api = FakeDecks()
        val library = DeckLibraryController(api, { "token" }, worker) { it() }
        library.refresh(); worker.drain()
        assertEquals(2, api.pages); assertEquals(2, library.state.decks.size)
        library.open(summary.id); worker.drain()
        val detail = library.state.detail!!
        assertEquals("Front // Back", detail.definitions.values.single().name)
        assertEquals(listOf(4, 2), detail.saved.entries.map { it.quantity })
        library.back(); worker.drain(); assertNull(library.state.selected)
    }
    private inner class FakeDecks : Decks {
        var pages = 0
        private val plan = DeckSavePlan.from(result(), summary.name)
        override suspend fun list(token: String, after: String?): DeckList { pages++
            return DeckList(listOf(summary), if (after == null) summary.id else null) }
        override suspend fun read(token: String, id: String) = SavedDeck(summary, plan.deck.entries)
        override suspend fun definition(token: String, id: String, digest: String) = plan.definitions.definitions.single()
        override suspend fun submit(token: String, value: SubmitDefinitions) = DefinitionsAccepted(value.definitions.map { it.digest })
        override suspend fun create(token: String, value: CreateDeck) = SavedDeck(summary, value.entries)
        override fun close() = Unit
    }
    @Test fun httpUsesNormalCompletePostsPrivateReadsBoundedErrorsAndNoCredentialRedirect() = runBlocking {
        val plan = DeckSavePlan.from(result(), summary.name); val paths = mutableListOf<String>()
        val api = DeckApi("https://decks.example", HttpClient(MockEngine { request ->
            paths += request.url.encodedPath
            assertEquals("Bearer ${"A".repeat(43)}", request.headers["Authorization"])
            assertNull(request.headers["Cookie"])
            val body = (request.body as? OutgoingContent.ByteArrayContent)?.bytes()?.decodeToString()
            val json = when (request.url.encodedPath) {
                "/v1/definitions" -> {
                    assertEquals(plan.definitions, DeckJson.format.decodeFromString<SubmitDefinitions>(body!!))
                    DeckJson.format.encodeToString(DefinitionsAccepted(plan.definitions.definitions.map { it.digest }))
                }
                "/v1/decks" -> if (body != null) {
                    assertEquals(plan.deck, DeckJson.format.decodeFromString<CreateDeck>(body))
                    DeckJson.format.encodeToString(SavedDeck(summary, plan.deck.entries))
                } else DeckJson.format.encodeToString(DeckList(listOf(summary)))
                else -> if ("/definitions/" in request.url.encodedPath)
                    DeckJson.format.encodeToString(plan.definitions.definitions.single())
                    else DeckJson.format.encodeToString(SavedDeck(summary, plan.deck.entries))
            }
            respond(json, headers = headersOf("Content-Type", "application/json"))
        }) { followRedirects = false })
        api.use { val token = "A".repeat(43)
            it.submit(token, plan.definitions); it.create(token, plan.deck); it.list(token)
            it.read(token, summary.id); it.definition(token, summary.id, plan.deck.entries.first().definition)
        }
        assertEquals(5, paths.size)
        for (status in listOf(401, 409, 503, 307)) {
            var calls = 0
            DeckApi("https://decks.example", HttpClient(MockEngine { calls++
                respond("<private response>", HttpStatusCode.fromValue(status), headersOf("Location", "https://other.example"))
            }) { followRedirects = false }).use {
                val failure = assertFailsWith<DeckFailure> { it.list("A".repeat(43)) }
                assertFalse(deckError(failure).contains("private"))
            }
            assertEquals(1, calls)
        }
    }
    private class Worker : AbstractExecutorService() {
        val queue = mutableListOf<Runnable>()
        override fun execute(command: Runnable) { queue += command }
        override fun shutdown() = Unit
        override fun shutdownNow(): MutableList<Runnable> = mutableListOf()
        override fun isShutdown() = false
        override fun isTerminated() = false
        override fun awaitTermination(timeout: Long, unit: TimeUnit) = false
        fun drain() { while (queue.isNotEmpty()) queue.removeAt(0).run() }
    }
}
