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
                assertEquals(definition, DeckSavePlan.from(imported, "Deck").definitions.definitions.single().definition)
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
        val plan = DeckSavePlan.from(result, "Trimmed deck")
        val definition = plan.definitions.definitions.single().definition
        // This is the same shared validation used by the server definitions endpoint.
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
        val worker = Worker(); val attempts = mutableListOf<DeckSavePlan>(); var successes = 0
        val controller = DeckImportController({ result() }, worker) { it() }
        controller.connectSave({ { plan -> attempts += plan; SavedDeck(summary, plan.deck.entries) } }, { successes++ })
        controller.open(); controller.edit(result().text); controller.lookup(); worker.drain()
        controller.name("First"); controller.save()
        controller.name("Second"); controller.edit("3 New input")
        assertTrue(controller.state.saving); assertEquals("3 New input", controller.state.text)
        worker.drain()
        assertEquals("First", attempts.single().deck.name)
        assertEquals("Second", controller.state.name); assertEquals("3 New input", controller.state.text)
        assertTrue(controller.state.active); assertFalse(controller.state.saving)
        assertEquals(summary.id, controller.state.target); assertEquals(0, successes)
        controller.close()
    }

    // LOB-097
    @Test fun savedDeckHandsItsCardsToTheArtworkDownloads() {
        val worker = Worker(); val handed = mutableListOf<Pair<String, List<SubmittedDefinition>>>()
        val attempts = mutableListOf<DeckSavePlan>()
        val controller = DeckImportController({ result() }, worker) { it() }
        controller.acceptedDefinitions = { deck, definitions -> handed += deck to definitions }
        controller.connectSave({ { plan -> attempts += plan; SavedDeck(summary, plan.deck.entries) } }, {})
        controller.open(); controller.edit(result().text); controller.lookup(); worker.drain()
        controller.name("Deck"); controller.save()
        assertTrue(handed.isEmpty(), "Nothing is downloaded before the server accepts the deck")
        worker.drain()
        assertEquals(listOf(summary.id to attempts.single().definitions.definitions), handed)
        controller.close()
    }

    @Test fun successfulMergeClosesEditorDespiteNameCorrectionAndCannotApplyQuantitiesAgain() {
        val worker = Worker(); val posts = ArrayDeque<() -> Unit>(); val attempts = mutableListOf<DeckSavePlan>()
        val imported = result().let { it.copy(text = "Deck\n2 Front // Back", entries = listOf(it.entries.first().copy(quantity = 2))) }
        var accepted = SavedDeck(summary, listOf(DeckSavePlan.from(result(), summary.name).deck.entries.first()))
        var successes = 0
        val controller = DeckImportController({ imported }, worker) { posts += it }
        controller.connectSave({ { plan ->
            attempts += plan
            val contents = checkNotNull(plan.update().contents)
            val entry = contents.entries.single()
            val quantity = if (contents.mode == DeckImportMode.MERGE) accepted.entries.single().quantity + entry.quantity else entry.quantity
            accepted = SavedDeck(summary, listOf(entry.copy(quantity = quantity)))
            accepted
        } }, { successes++; assertEquals(6, it.entries.single().quantity) })
        controller.openExisting(summary); controller.edit(imported.text); controller.lookup()
        worker.drain(); posts.removeFirst()()
        controller.mode(DeckImportMode.MERGE); controller.save(); controller.name("Corrected name")
        assertTrue(controller.state.active); assertTrue(controller.state.saving); assertEquals(4, accepted.entries.single().quantity)
        worker.drain(); posts.removeFirst()()
        assertEquals(6, accepted.entries.single().quantity)
        assertFalse(controller.state.active); assertEquals(1, successes)
        controller.save(); worker.drain()
        assertEquals(1, attempts.size); assertEquals(6, accepted.entries.single().quantity)
        controller.close()
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
    // LOB-065
    @Test fun contextualImportCapturesTargetDefaultsOverwriteAndRetriesMergeWithoutChangingIdentity() {
        val worker = Worker(); val posts = mutableListOf<() -> Unit>(); val attempts = mutableListOf<DeckSavePlan>()
        val controller = DeckImportController({ result() }, worker) { posts += it }
        controller.connectSave({ { plan -> attempts += plan; throw DeckFailure("network") } }, { error("not accepted") })
        controller.openExisting(summary)
        assertEquals(summary.id, controller.state.target); assertEquals(summary.name, controller.state.name)
        assertEquals(DeckImportMode.OVERWRITE, controller.state.mode)
        controller.edit(result().text); controller.lookup(); worker.drain(); posts.removeAt(0)(); controller.review()
        controller.mode(DeckImportMode.MERGE); controller.save(); controller.mode(DeckImportMode.OVERWRITE)
        worker.drain(); posts.removeAt(0)()
        assertEquals(DeckImportMode.MERGE, controller.state.mode) // cannot retarget an in-flight save
        controller.save(); worker.drain(); posts.removeAt(0)()
        assertEquals(attempts[0], attempts[1]); assertEquals(summary.id, attempts[0].target)
        assertEquals(DeckImportMode.MERGE, attempts[0].update().contents!!.mode)
        assertNull(attempts[0].update().name)
        assertEquals(attempts[0].deck.entries.first().definition, attempts[0].update().cover)
        assertEquals(listOf(4, 2), attempts[0].update().contents!!.entries.map { it.quantity })
        controller.editList(); controller.review(); controller.mode(DeckImportMode.OVERWRITE)
        controller.save(); worker.drain(); posts.removeAt(0)()
        assertNotEquals(attempts[1].deck.requestId, attempts[2].deck.requestId)
        assertEquals(DeckImportMode.OVERWRITE, attempts[2].update().contents!!.mode)
        controller.back(); assertEquals(summary.id, controller.state.target); controller.back()
        controller.openExisting(summary.copy(id = second))
        assertEquals(second, controller.state.target); assertEquals(DeckImportMode.OVERWRITE, controller.state.mode)
        assertEquals("", controller.state.text)
        controller.open(); assertNull(controller.state.target)
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
            return DeckList(listOf(if (after == null) summary else summary.copy(id = second)), if (after == null) summary.id else null) }
        override suspend fun read(token: String, id: String) = SavedDeck(summary, plan.deck.entries)
        override suspend fun definition(token: String, id: String, digest: String) = plan.definitions.definitions.single()
        override suspend fun submit(token: String, value: SubmitDefinitions) = DefinitionsAccepted(value.definitions.map { it.digest })
        override suspend fun create(token: String, value: CreateDeck) = SavedDeck(summary, value.entries)
        override suspend fun update(token: String, id: String, value: UpdateDeck) = SavedDeck(summary, plan.deck.entries)
        override suspend fun delete(token: String, id: String, value: DeleteDeck) = DeckDeleted(id)
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
                else -> if (request.method == HttpMethod.Put) {
                    assertEquals(plan.copy(target = summary.id, mode = DeckImportMode.MERGE).update(),
                        DeckJson.format.decodeFromString<UpdateDeck>(body!!))
                    DeckJson.format.encodeToString(SavedDeck(summary, plan.deck.entries))
                } else if ("/definitions/" in request.url.encodedPath)
                    DeckJson.format.encodeToString(plan.definitions.definitions.single())
                    else DeckJson.format.encodeToString(SavedDeck(summary, plan.deck.entries))
            }
            respond(json, headers = headersOf("Content-Type", "application/json"))
        }) { followRedirects = false })
        api.use { val token = "A".repeat(43)
            it.submit(token, plan.definitions); it.create(token, plan.deck); it.list(token)
            it.read(token, summary.id); it.definition(token, summary.id, plan.deck.entries.first().definition)
            it.update(token, summary.id, plan.copy(target = summary.id, mode = DeckImportMode.MERGE).update())
        }
        assertEquals(6, paths.size)
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
