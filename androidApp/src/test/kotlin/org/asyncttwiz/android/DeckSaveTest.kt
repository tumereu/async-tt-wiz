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
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.ByteArrayInputStream
import org.junit.Rule
import org.junit.rules.TemporaryFolder

class DeckSaveTest {
    @get:Rule val temporary = TemporaryFolder()
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
    /** [this] save's entries as the server stores them (one card in these tests). */
    private fun DeckSave.saved() = entries.map { SavedDeckEntry(it.section, it.printingId, "Front // Back", it.quantity) }

    // Card data from the server's catalog (owner 2026-11-10): a save names printings; the phone keeps its own card data
    // of them (the server's) for the pictures and the support check
    @Test fun mapsCompleteFacesDefaultsProvenanceAndExactSectionsWithoutArtwork() {
        val plan = DeckSave.from(result(), " Exact name ")
        val submitted = plan.cards.single()
        assertEquals(DeckJson.digest(submitted.definition), submitted.digest)
        assertTrue(DeckValidation.definition(submitted.definition))
        assertEquals(listOf("Front", "Back"), submitted.definition.faces.map { it.name })
        assertEquals(listOf(second), submitted.definition.faces.first().otherFaceIds)
        assertEquals("*/2", submitted.definition.faces.first().corner)
        assertEquals("", submitted.definition.faces.last().defense)
        assertTrue(submitted.definition.faces.first().sourceData!!.contains("transform"))
        assertNull(submitted.definition.faces.first().imageUri)
        assertEquals(" Exact name ", plan.name)
        assertEquals(listOf(4, 2), plan.entries.map { it.quantity })
        assertEquals(listOf(first, first), plan.entries.map { it.printingId })
        assertEquals(SavedDeckSection.PREPARED, plan.entries.map { it.section })
        assertEquals(CreateDeck(plan.requestId, " Exact name ", plan.entries), plan.create())
        // The same printing twice in a section is one entry
        val twice = result().let { it.copy(entries = it.entries + it.entries.first()) }
        assertEquals(listOf(8, 2), DeckSave.from(twice, "Twice").entries.map { it.quantity })
        val maybe = result().let { it.copy(entries = it.entries + it.entries.first().copy(section = DeckSection.MAYBEBOARD, quantity = 3)) }
        assertEquals(SavedDeckSection.entries to listOf(4, 2, 3),
            DeckSave.from(maybe, "Maybe").entries.let { entries -> entries.map { it.section } to entries.map { it.quantity } })
        assertFailsWith<DeckFailure> { DeckSave.from(result(), " \n") }
    }

    @Test fun multipartCatalogCardsAndTokensUseOneArtworkAndRetainBothHalvesText() {
        for (layout in listOf("adventure", "split", "flip", "aftermath", "transform", "modal_dfc", "meld", "reversible_card")) {
            for (token in listOf(false, true)) {
                val imported = result().let { original -> original.copy(entries = original.entries.map { entry ->
                    entry.copy(faces = entry.faces.map { it.copy(token = token, json = it.json.replace("transform", layout)) })
                }) }
                val definition = imported.entries.first().definition(imported.catalog)
                val twoSided = layout in listOf("transform", "modal_dfc", "meld", "reversible_card")
                assertEquals(if (twoSided) 2 else 1, definition.faces.size, "$layout token=$token")
                assertTrue(DeckValidation.definition(definition))
                if (!twoSided) {
                    val face = definition.faces.single()
                    assertEquals(first, face.faceId)
                    assertTrue(face.otherFaceIds.isEmpty())
                    assertContains(face.rulesText, "Front\nCreature\nExact  text\nUnicode 🧙\n*/2")
                    assertContains(face.rulesText, "────────")
                    assertContains(face.rulesText, "Back\nCreature")
                    assertEquals(definition, PhysicalCardFaces.normalize(definition))
                }
                assertEquals(definition, DeckSave.from(imported, "Deck").cards.single().definition)
            }
        }
    }
    @Test fun trimmedCatalogImportsProduceValidServerDefinitionsWithoutUnusedProviderData() {
        val directory = temporary.newFolder()
        val repository = CatalogRepository(directory) { JdbcSqliteDriver("jdbc:sqlite:${it.absolutePath}") }
        repository.stage { index ->
            MtgJsonCatalog().import(ByteArrayInputStream("""{"meta":{"date":"2026-10-25","version":"5.2.2"},"data":{"TST":{
                "code":"TST","releaseDate":"2026-10-25","cards":[{"uuid":"$first","name":"Trimmed fixture","number":"1",
                "setCode":"TST","language":"English","type":"Creature","manaCost":"{G}","text":"Exact rules text 🧙",
                "power":"2","toughness":"3","side":"a","otherFaceIds":["$second"],"layout":"transform",
                "identifiers":{"scryfallId":"$first","scryfallOracleId":"$first","mtgArenaId":"42"},
                "foreignData":[{"name":"Unused"}],"legalities":{"modern":"Legal"},"purchaseUrls":{"x":"unused"}},
                {"uuid":"$second","name":"Trimmed fixture","number":"1","side":"b","otherFaceIds":["$first"],
                "faceName":"Back","text":"Exact back","layout":"transform"}]}}}""".toByteArray()),
                index, CatalogMetadata("2026-10-25", "5.2.2"))
        }
        val result = DeckImportResolver(repository).resolve("2 Trimmed fixture (TST) 1")
        assertTrue(result.resolved, result.issues.toString())
        val plan = DeckSave.from(result, "Trimmed deck")
        val definition = plan.cards.single().definition
        // The same check the server makes of the card data it builds.
        assertTrue(DeckValidation.definition(definition))
        assertEquals("Exact rules text 🧙", definition.faces.first().rulesText)
        assertEquals("Exact back", definition.faces.last().rulesText)
        assertEquals("2/3", definition.faces.first().corner)
        assertEquals(first, definition.faces.first().scryfallId)
        assertEquals(listOf(second), definition.faces.first().otherFaceIds)
        definition.faces.forEach { face ->
            assertFalse(face.sourceData!!.contains("foreignData"))
            assertFalse(face.sourceData!!.contains("purchaseUrls"))
            assertFalse(face.sourceData!!.contains("mtgArenaId"))
        }
        assertEquals(definition, DeckJson.format.decodeFromString<CardDefinition>(DeckJson.format.encodeToString(definition)))
    }
    @Test fun editingDuringSaveKeepsNewDraftAndDoesNotMutateTheCapturedRequest() {
        val worker = Worker(); val attempts = mutableListOf<DeckSave>(); var successes = 0
        val controller = DeckImportController({ result() }, worker) { it() }
        controller.connectSave({ { plan -> attempts += plan; SavedDeck(summary, plan.saved()) } }, { successes++ })
        controller.open(); controller.edit(result().text); controller.lookup(); worker.drain()
        controller.name("First"); controller.save()
        controller.name("Second"); controller.edit("3 New input")
        assertTrue(controller.state.saving); assertEquals("3 New input", controller.state.text)
        worker.drain()
        assertEquals("First", attempts.single().name)
        assertEquals("Second", controller.state.name); assertEquals("3 New input", controller.state.text)
        assertTrue(controller.state.active); assertFalse(controller.state.saving)
        assertEquals(summary.id, controller.state.target); assertEquals(0, successes)
        controller.close()
    }

    // LOB-097
    @Test fun savedDeckHandsItsCardsToTheArtworkDownloads() {
        val worker = Worker(); val handed = mutableListOf<Pair<String, List<SubmittedDefinition>>>()
        val attempts = mutableListOf<DeckSave>()
        val controller = DeckImportController({ result() }, worker) { it() }
        controller.acceptedDefinitions = { deck, definitions -> handed += deck to definitions }
        controller.connectSave({ { plan -> attempts += plan; SavedDeck(summary, plan.saved()) } }, {})
        controller.open(); controller.edit(result().text); controller.lookup(); worker.drain()
        controller.name("Deck"); controller.save()
        assertTrue(handed.isEmpty(), "Nothing is downloaded before the server accepts the deck")
        worker.drain()
        assertEquals(listOf(summary.id to attempts.single().cards), handed)
        controller.close()
    }

    // An import into an existing deck replaces its list and closes the editor once accepted.
    @Test fun successfulImportIntoAnExistingDeckReplacesItsListAndClosesTheEditor() {
        val worker = Worker(); val posts = ArrayDeque<() -> Unit>(); val attempts = mutableListOf<DeckSave>()
        val imported = result().let { it.copy(text = "Deck\n2 Front // Back", entries = listOf(it.entries.first().copy(quantity = 2))) }
        var accepted = SavedDeck(summary, listOf(DeckSave.from(result(), summary.name).saved().first()))
        var successes = 0
        val controller = DeckImportController({ imported }, worker) { posts += it }
        controller.connectSave({ { plan ->
            attempts += plan
            assertEquals(plan.entries, checkNotNull(plan.update().contents).entries)
            accepted = SavedDeck(summary, plan.saved())
            accepted
        } }, { successes++; assertEquals(2, it.entries.single().quantity) })
        controller.openExisting(summary); controller.edit(imported.text); controller.lookup()
        worker.drain(); posts.removeFirst()()
        controller.save()
        assertTrue(controller.state.active); assertTrue(controller.state.saving); assertEquals(4, accepted.entries.single().quantity)
        worker.drain(); posts.removeFirst()()
        assertEquals(2, accepted.entries.single().quantity)
        assertFalse(controller.state.active); assertEquals(1, successes); assertEquals(1, attempts.size)
        controller.close()
    }

    @Test fun uncertainRetryKeepsIdentitiesAndInputButChangedIntentGetsNewIds() {
        val worker = Worker(); val posts = mutableListOf<() -> Unit>(); val attempts = mutableListOf<DeckSave>()
        var successes = 0
        val controller = DeckImportController({ result() }, worker) { posts += it }
        controller.connectSave({ { plan -> attempts += plan
            if (attempts.size != 3) throw DeckFailure("network")
            SavedDeck(summary, plan.saved()) } }, { successes++ })
        controller.open(); controller.edit(result().text); controller.lookup(); worker.drain(); posts.removeAt(0)()
        controller.review(); controller.name(" Exact name "); controller.save(); controller.save()
        worker.drain(); posts.removeAt(0)()
        assertTrue(controller.state.preview); assertEquals(result().text, controller.state.text)
        assertEquals(" Exact name ", controller.state.name); assertNotNull(controller.state.error)
        // Recreation's lookup is harmless and retry must reuse the complete immutable request.
        controller.lookup(); controller.save(); worker.drain(); posts.removeAt(0)()
        assertEquals(attempts[0], attempts[1]); assertEquals(0, successes)
        controller.name("Changed name"); controller.save(); worker.drain(); posts.removeAt(0)()
        assertNotEquals(attempts[1].requestId, attempts[2].requestId)
        assertEquals(1, successes); assertFalse(controller.state.active)
    }
    @Test fun logoutFencesSaveAndReadCallbacksAndQueuedOldAccountRequests() {
        val worker = Worker(); val posts = mutableListOf<() -> Unit>(); var success = false; var calls = 0
        val controller = DeckImportController({ result() }, worker) { posts += it }
        controller.connectSave({ { plan -> calls++; SavedDeck(summary, plan.saved()) } }, { success = true })
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
    @Test fun contextualImportCapturesTargetAndRetriesTheReplacementWithoutChangingIdentity() {
        val worker = Worker(); val posts = mutableListOf<() -> Unit>(); val attempts = mutableListOf<DeckSave>()
        val controller = DeckImportController({ result() }, worker) { posts += it }
        controller.connectSave({ { plan -> attempts += plan; throw DeckFailure("network") } }, { error("not accepted") })
        controller.openExisting(summary)
        assertEquals(summary.id, controller.state.target); assertEquals(summary.name, controller.state.name)
        controller.edit(result().text); controller.lookup(); worker.drain(); posts.removeAt(0)(); controller.review()
        controller.save(); worker.drain(); posts.removeAt(0)()
        controller.save(); worker.drain(); posts.removeAt(0)()
        assertEquals(attempts[0], attempts[1]); assertEquals(summary.id, attempts[0].target)
        assertNull(attempts[0].update().name)
        assertEquals(attempts[0].cards.first().definition.printingId, attempts[0].update().cover)
        assertEquals(listOf(4, 2), attempts[0].update().contents!!.entries.map { it.quantity })
        controller.editList(); controller.name("Renamed"); controller.review()
        controller.save(); worker.drain(); posts.removeAt(0)()
        assertNotEquals(attempts[1].requestId, attempts[2].requestId)
        controller.back(); assertEquals(summary.id, controller.state.target); controller.back()
        controller.openExisting(summary.copy(id = second))
        assertEquals(second, controller.state.target)
        assertEquals("", controller.state.text)
        controller.open(); assertNull(controller.state.target)
    }
    @Test fun libraryFollowsPagesAndReadsServerDefinitionsWithoutCatalog() {
        val worker = Worker(); val api = FakeDecks()
        val library = DeckLibraryController(api, { "token" }, worker) { it() }
        // The saved deck's cards come from the card records by printing (owner 2026-11-10)
        library.cardRecords = SavedCardRecords { ids -> SavedCardFaces(mapOf(first to result().entries.first().faces).filterKeys { it in ids }, null) }
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
        private val plan = DeckSave.from(result(), summary.name)
        override suspend fun list(token: String, after: String?): DeckList { pages++
            return DeckList(listOf(if (after == null) summary else summary.copy(id = second)), if (after == null) summary.id else null) }
        override suspend fun read(token: String, id: String) = SavedDeck(summary, plan.saved())
        override suspend fun create(token: String, value: CreateDeck) = SavedDeck(summary, plan.saved())
        override suspend fun update(token: String, id: String, value: UpdateDeck) = SavedDeck(summary, plan.saved())
        override suspend fun delete(token: String, id: String, value: DeleteDeck) = DeckDeleted(id)
        override fun close() = Unit
    }
    @Test fun httpUsesNormalCompletePostsPrivateReadsBoundedErrorsAndNoCredentialRedirect() = runBlocking {
        val plan = DeckSave.from(result(), summary.name); val paths = mutableListOf<String>()
        val api = DeckApi("https://decks.example", HttpClient(MockEngine { request ->
            paths += request.url.encodedPath
            assertEquals("Bearer ${"A".repeat(43)}", request.headers["Authorization"])
            assertNull(request.headers["Cookie"])
            val body = (request.body as? OutgoingContent.ByteArrayContent)?.bytes()?.decodeToString()
            val json = when (request.url.encodedPath) {
                "/v1/decks" -> if (body != null) {
                    assertEquals(plan.create(), DeckJson.format.decodeFromString<CreateDeck>(body))
                    DeckJson.format.encodeToString(SavedDeck(summary, plan.saved()))
                } else DeckJson.format.encodeToString(DeckList(listOf(summary)))
                else -> if (request.method == HttpMethod.Put) {
                    assertEquals(plan.copy(target = summary.id).update(),
                        DeckJson.format.decodeFromString<UpdateDeck>(body!!))
                    DeckJson.format.encodeToString(SavedDeck(summary, plan.saved()))
                } else DeckJson.format.encodeToString(SavedDeck(summary, plan.saved()))
            }
            respond(json, headers = headersOf("Content-Type", "application/json"))
        }) { followRedirects = false })
        api.use { val token = "A".repeat(43)
            it.create(token, plan.create()); it.list(token)
            it.read(token, summary.id)
            it.update(token, summary.id, plan.copy(target = summary.id).update())
        }
        assertEquals(4, paths.size)
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
