package org.asyncttwiz.android

import java.util.UUID
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit
import org.asyncttwiz.protocol.*
import org.asyncttwiz.storage.*
import org.junit.Test
import kotlin.test.*

class CubeControllerTest {
    private val card = CardDefinition("Retained card", listOf(DefinitionFace(UUID.randomUUID().toString(), "Retained card")))
    private val digest = DeckJson.digest(card)
    private val summary = CubeSummary(UUID.randomUUID().toString(), "Accepted cube", "1", "2026-10-26T00:00:00Z", "2026-10-26T00:00:00Z", 1500, "fixture")
    private inner class Api : Cubes {
        var saved: SavedCube? = SavedCube(summary, listOf(SavedCubeEntry(digest, 1500)))
        val creates = mutableListOf<CreateCube>(); val updates = mutableListOf<UpdateCube>(); val deletes = mutableListOf<DeleteCube>()
        val definitions = mutableMapOf(digest to card)
        var fail = false
        var afterRead: () -> Unit = {}
        var definitionReads = 0
        override suspend fun submit(token: String, value: SubmitDefinitions): DefinitionsAccepted {
            value.definitions.forEach { definitions[it.digest] = it.definition }
            return DefinitionsAccepted(value.definitions.map { it.digest })
        }
        override suspend fun create(token: String, value: CreateCube): SavedCube {
            creates += value
            if (fail) throw DeckFailure("request_conflict")
            return SavedCube(summary.copy(name = value.name, totalCopies = value.entries.sumOf { it.quantity.toLong() }, cubeCobraId = value.cubeCobraId), value.entries).also { saved = it }
        }
        override suspend fun listCubes(token: String, after: String?) = CubeList(listOfNotNull(saved?.cube))
        override suspend fun readCube(token: String, id: String): SavedCube { afterRead(); return saved ?: throw DeckFailure("not_found") }
        override suspend fun cubeDefinition(token: String, id: String, digest: String): SubmittedDefinition {
            definitionReads++; return SubmittedDefinition(digest, definitions.getValue(digest))
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
    private fun controller(api: Api, worker: Worker, lookup: (String, Boolean) -> DeckImportResult = { _, _ -> result() }, post: (() -> Unit) -> Unit = { it() }) =
        CubeController(api, { "token" }, lookup, { result() }, worker, post,
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
        assertNull(controller.state.detail)
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
