package org.asyncttwiz.android

import java.util.UUID
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit
import org.asyncttwiz.protocol.*
import org.asyncttwiz.storage.*
import org.junit.Test
import kotlin.test.*

class CubeControllerTest {
    @Test fun cobraClipboardIsReadOncePerEditorAndNeverStartsImport() {
        val worker = Worker(); val api = Api(); var fetches = 0; var reads = 0
        val controller = CubeController(api, { "token" }, { _, _ -> result() }, { result() }, worker, { it() },
            fetchCobra = { fetches++; DeckTextParser.parse("1 Retained card") })
        controller.newCube()
        controller.prefillCobra { reads++; "https://cubecobra.com/cube/list/fixture" }
        controller.cobraTab(false); controller.cobraTab(true)
        controller.prefillCobra { reads++; "another" }
        assertEquals(1, reads); assertEquals(0, fetches)
        assertEquals("https://cubecobra.com/cube/list/fixture", controller.state.cobraInput)
        controller.importCobra(); worker.drain()
        assertEquals(1, fetches); assertFalse(controller.state.cobraTab)
        assertEquals("fixture", controller.state.name)
        controller.back(); controller.newCube(); controller.cobraInput("typed")
        controller.prefillCobra { reads++; "another" }
        assertEquals("typed", controller.state.cobraInput); assertEquals(2, reads)
        controller.close(); worker.drain()
    }

    @Test fun cubeEditorRemainsEditableDuringSaveAndRetainsNewerInput() {
        val worker = Worker(); val api = Api(); val controller = controller(api, worker)
        controller.newCube(); controller.name("First"); controller.text("2 Retained card"); controller.lookup(); worker.drain()
        controller.save(); assertTrue(controller.state.saving)
        controller.name("Second"); controller.text("3 Retained card")
        worker.drain()
        assertEquals("First", api.creates.single().name)
        assertTrue(controller.state.editing); assertFalse(controller.state.busy)
        assertEquals("Second", controller.state.name); assertEquals("3 Retained card", controller.state.text)
        assertNotNull(controller.state.target)
        controller.close(); worker.drain()
    }

    private val card = CardDefinition("Retained card", listOf(DefinitionFace(UUID.randomUUID().toString(), "Retained card")))
    private val digest = DeckJson.digest(card)
    private val summary = CubeSummary(UUID.randomUUID().toString(), "Accepted cube", "1", "2026-10-26T00:00:00Z", "2026-10-26T00:00:00Z", 1500, "fixture")
    private inner class Api : Cubes {
        var saved: SavedCube? = SavedCube(summary, listOf(SavedCubeEntry(digest, 1500)))
        val creates = mutableListOf<CreateCube>(); val updates = mutableListOf<UpdateCube>(); val deletes = mutableListOf<DeleteCube>()
        val submissions = mutableListOf<SubmitDefinitions>()
        val definitions = mutableMapOf(digest to card)
        var fail = false
        var afterRead: () -> Unit = {}
        var cubeReads = 0
        var definitionReads = 0
        val definitionBatches = mutableListOf<List<String>>()
        var failBatch: Int? = null
        override suspend fun submit(token: String, value: SubmitDefinitions): DefinitionsAccepted {
            submissions += value
            value.definitions.forEach { definitions[it.digest] = it.definition }
            return DefinitionsAccepted(value.definitions.map { it.digest })
        }
        override suspend fun create(token: String, value: CreateCube): SavedCube {
            creates += value
            if (fail) throw DeckFailure("request_conflict")
            return SavedCube(summary.copy(name = value.name, totalCopies = value.entries.sumOf { it.quantity.toLong() }, cubeCobraId = value.cubeCobraId), value.entries).also { saved = it }
        }
        override suspend fun listCubes(token: String, after: String?) = CubeList(listOfNotNull(saved?.cube))
        override suspend fun readCube(token: String, id: String): SavedCube { cubeReads++; afterRead(); return saved ?: throw DeckFailure("not_found") }
        override suspend fun cubeDefinition(token: String, id: String, digest: String): SubmittedDefinition {
            definitionReads++; return SubmittedDefinition(digest, definitions.getValue(digest))
        }
        override suspend fun cubeDefinitions(token: String, id: String, value: ReadCubeDefinitions): SubmittedDefinitions {
            definitionBatches += value.digests
            if (definitionBatches.size == failBatch) throw DeckFailure("try_later")
            return SubmittedDefinitions(value.digests.map { SubmittedDefinition(it, definitions.getValue(it)) })
        }
        override suspend fun update(token: String, id: String, value: UpdateCube): SavedCube {
            updates += value
            if (fail) throw DeckFailure("request_conflict")
            val old = saved!!
            return old.copy(cube = old.cube.copy(name = value.name ?: old.cube.name, revision = "2",
                totalCopies = value.entries?.sumOf { it.quantity.toLong() } ?: old.cube.totalCopies), entries = value.entries ?: old.entries).also { saved = it }
        }
        override suspend fun delete(token: String, id: String, value: DeleteCube): CubeDeleted {
            deletes += value
            if (fail) throw DeckFailure("request_conflict")
            saved = null; return CubeDeleted(id)
        }
        override fun close() = Unit
    }
    private class Worker : AbstractExecutorService() {
        private val queue = ArrayDeque<Runnable>()
        override fun execute(command: Runnable) { queue.add(command) }
        override fun shutdown() = Unit
        override fun shutdownNow() = mutableListOf<Runnable>()
        override fun isShutdown() = false
        override fun isTerminated() = false
        override fun awaitTermination(timeout: Long, unit: TimeUnit) = false
        fun drain() { while (queue.isNotEmpty()) queue.removeFirst().run() }
    }
    private fun result(quantity: Int = 2) = DeckImportResult("$quantity Retained card",
        listOf(ResolvedDeckEntry(DeckSection.MAINBOARD, quantity, listOf(CatalogPrinting(card.faces.first().faceId, card.name, "", "", "English", "a", null, false, emptyList(), "{}")), listOf(1))), emptyList(), null)
    private fun controller(api: Api, worker: Worker, lookup: (String, Boolean) -> DeckImportResult = { _, _ -> result() }, post: (() -> Unit) -> Unit = { it() }, credential: () -> String? = { "token" }) =
        CubeController(api, credential, lookup, { result() }, worker, post,
            fetchCobra = { CubeCobraImport.parse("""{"cards":{"mainboard":[{"details":{"name":"Retained card"}}]}}""") })

    @Test fun editPrefillsAndPreservesMoreThanOneThousandAcceptedCopiesWithoutCatalog() {
        val api = Api(); val worker = Worker()
        val controller = controller(api, worker, lookup = { _, _ -> error("Retained card must not be re-resolved") })
        controller.open(summary.id); worker.drain()
        controller.edit(); worker.drain()
        assertTrue(controller.state.text.contains("1500 Retained card"))
        assertTrue(controller.state.result!!.resolved)
        controller.text("Deck\n2000 Retained card"); controller.lookup(); worker.drain()
        controller.save(); worker.drain()
        assertEquals(2000, api.updates.single().entries!!.single().quantity)
        assertEquals(card, api.definitions.getValue(api.updates.single().entries!!.single().definition))
        assertFalse(controller.state.editing)
        assertEquals(2000L, controller.state.detail!!.saved.cube.totalCopies)
        assertEquals(1, api.definitionBatches.size)
        controller.close(); worker.drain()
    }
    @Test fun createUnknownBlocksAndCobraProvenanceAndImmutableRetryAreRetained() {
        val api = Api(); val worker = Worker()
        val controller = controller(api, worker, lookup = { text, _ -> DeckImportResult(text, emptyList(), listOf(ImportIssue(1, "Unknown card")), null) })
        controller.newCube(); controller.name("New cube"); controller.text("1 Unknown card"); controller.lookup(); worker.drain()
        controller.save(); worker.drain(); assertTrue(api.creates.isEmpty())
        controller.cobraInput("https://cubecobra.com/cube/list/fixture"); controller.importCobra(); worker.drain()
        assertEquals("fixture", controller.state.provenance)
        controller.save(); worker.drain()
        assertEquals("fixture", api.creates.single().cubeCobraId)
        assertEquals(2L, controller.state.detail!!.saved.cube.totalCopies)
        // Use a retained editor source so the synthetic fixture resolves to the exact accepted definition.
        controller.back(); worker.drain(); controller.open(summary.id); worker.drain(); controller.edit(); worker.drain()
        controller.name("Edited"); api.fail = true; controller.save(); worker.drain(); controller.save(); worker.drain()
        assertEquals(2, api.updates.size); assertEquals(api.updates[0], api.updates[1])
        api.fail = false; controller.save(); worker.drain()
        assertEquals("Edited", controller.state.detail!!.saved.cube.name)
        controller.close(); worker.drain()
    }
    @Test fun renameDeleteConfirmationAndLogoutFence() {
        val api = Api(); val worker = Worker(); val controller = controller(api, worker)
        controller.open(summary.id); worker.drain()
        controller.dialog(CubeDialog.RENAME); controller.name("Renamed"); controller.confirm(); worker.drain()
        assertEquals("Renamed", controller.state.detail!!.saved.cube.name)
        assertNull(api.updates.single().entries)
        controller.dialog(CubeDialog.DELETE); controller.dismiss(); assertTrue(api.deletes.isEmpty())
        controller.dialog(CubeDialog.DELETE); api.fail = true; controller.confirm(); worker.drain(); controller.confirm(); worker.drain()
        assertEquals(api.deletes[0], api.deletes[1])
        api.fail = false; controller.confirm(); worker.drain()
        assertNull(controller.state.selected); assertTrue(controller.state.cubes.isEmpty())
        controller.close(); worker.drain()
        val another = controller(Api(), worker)
        another.refresh(); another.clear(); worker.drain()
        assertFalse(another.state.loaded); assertTrue(another.state.cubes.isEmpty())
        another.close(); worker.drain()
    }
    @Test fun logoutBetweenPrivateReadAndAttachmentsStopsFurtherCalls() {
        val api = Api(); val worker = Worker(); val controller = controller(api, worker)
        api.afterRead = controller::clear
        controller.open(summary.id); worker.drain()
        assertEquals(0, api.definitionReads)
        assertTrue(api.definitionBatches.isEmpty())
        assertNull(controller.state.detail)
        controller.close(); worker.drain()
    }
    @Test fun obsoleteSaveCompletionsReleaseBusyFenceDataAndRetainSaveIdentitiesForReauthentication() {
        for (failed in listOf(false, true)) {
            val api = Api(); val worker = Worker(); val posts = ArrayDeque<() -> Unit>()
            var token: String? = "original"
            val controller = controller(api, worker, post = { posts.add(it) }, credential = { token })
            fun deliver() {
                do { worker.drain(); while (posts.isNotEmpty()) posts.removeFirst()() } while (controller.state.busy)
            }
            controller.open(summary.id); deliver(); controller.edit(); deliver(); controller.name("Edited")
            api.fail = failed; controller.save(); worker.drain()
            assertTrue(controller.state.busy)
            token = null // Auth expiry arrives before the operation's posted completion.
            while (posts.isNotEmpty()) posts.removeFirst()()
            assertFalse(controller.state.busy)
            assertTrue(controller.state.editing)
            assertNull(controller.state.error)
            assertEquals(summary.name, controller.state.detail!!.saved.cube.name)
            token = "replacement"; api.fail = false
            controller.save(); deliver()
            assertEquals(2, api.updates.size)
            assertEquals(api.updates.first(), api.updates.last())
            assertEquals(api.submissions.first(), api.submissions.last())
            assertEquals("Edited", controller.state.detail!!.saved.cube.name)
            assertFalse(controller.state.editing)
            controller.back(); deliver(); assertNull(controller.state.selected)
            controller.close(); worker.drain()
        }
    }
    private fun populate(api: Api, count: Int) {
        api.definitions.clear()
        repeat(count) { index ->
            val card = CardDefinition("Card $index", listOf(DefinitionFace(UUID.randomUUID().toString(), "Card $index")))
            api.definitions[DeckJson.digest(card)] = card
        }
        api.saved = SavedCube(summary.copy(totalCopies = count.toLong()), api.definitions.keys.map { SavedCubeEntry(it, 1) })
    }
    @Test fun largeCubeLoadsInBatchesAndReusesDefinitionsAcrossReopenRenameAndOtherCubes() {
        val api = Api(); populate(api, 720)
        val worker = Worker(); val controller = controller(api, worker)
        controller.open(summary.id); worker.drain()
        assertNull(controller.state.error)
        assertEquals(api.definitions, controller.state.detail!!.definitions)
        assertEquals(4, api.cubeReads + api.definitionBatches.size + api.definitionReads)
        assertEquals(listOf(250, 250, 220), api.definitionBatches.map { it.size })
        assertEquals(0, api.definitionReads)
        controller.back(); worker.drain(); controller.open(summary.id); worker.drain()
        controller.dialog(CubeDialog.RENAME); controller.name("Renamed"); controller.confirm(); worker.drain()
        assertEquals("Renamed", controller.state.detail!!.saved.cube.name)
        val otherId = UUID.randomUUID().toString()
        api.saved = api.saved!!.copy(cube = api.saved!!.cube.copy(id = otherId))
        controller.open(otherId); worker.drain()
        assertEquals(720, controller.state.detail!!.definitions.size)
        assertEquals(3, api.definitionBatches.size)
        controller.clear(); controller.open(otherId); worker.drain()
        assertEquals(6, api.definitionBatches.size)
        controller.close(); worker.drain()
    }
    @Test fun maximumCubeUsesTwelveBatches() {
        val api = Api(); populate(api, 3000)
        val worker = Worker(); val controller = controller(api, worker)
        controller.open(summary.id); worker.drain()
        assertNull(controller.state.error)
        assertEquals(3000, controller.state.detail!!.definitions.size)
        assertEquals(12, api.definitionBatches.size)
        assertTrue(api.definitionBatches.all { it.size == 250 })
        assertEquals(0, api.definitionReads)
        controller.close(); worker.drain()
    }
    @Test fun retryAfterPartialFailureOnlyFetchesMissingDefinitions() {
        val api = Api(); populate(api, 720); api.failBatch = 2
        val worker = Worker(); val controller = controller(api, worker)
        controller.open(summary.id); worker.drain()
        assertNotNull(controller.state.error)
        assertNull(controller.state.detail)
        api.failBatch = null
        controller.retry(); worker.drain()
        assertNull(controller.state.error)
        assertEquals(720, controller.state.detail!!.definitions.size)
        assertEquals(listOf(250, 250, 250, 220), api.definitionBatches.map { it.size })
        assertEquals(api.definitionBatches[1], api.definitionBatches[2])
        assertTrue(api.definitionBatches[0].toSet().intersect(api.definitionBatches.drop(1).flatten().toSet()).isEmpty())
        controller.close(); worker.drain()
    }
    @Test fun cobraRequestedIdentitySurvivesCheckboxAndTextQuantityEdits() {
        val api = Api(); val worker = Worker()
        val requested = UUID.randomUUID().toString()
        val seen = mutableListOf<ParsedDeck>()
        val controller = CubeController(api, { "token" }, { _, _ -> error("Cobra lines must retain their requested printing") },
            { parsed -> seen += parsed; result() }, worker, { it() },
            fetchCobra = { CubeCobraImport.parse("""{"cards":{"mainboard":[{"cardID":"$requested","details":{"name":"Retained card"}}]}}""") })
        controller.newCube(); controller.cobraInput("fixture"); controller.importCobra(); worker.drain()
        controller.sideboard(true); controller.lookup(); worker.drain()
        controller.text("3 Retained card"); controller.lookup(); worker.drain()
        assertEquals(listOf(requested, requested, requested), seen.map { it.lines.single().scryfallId })
        assertEquals(3, seen.last().lines.single().quantity)
        controller.close(); worker.drain()
    }
}
