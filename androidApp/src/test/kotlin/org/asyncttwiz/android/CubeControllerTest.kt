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
        val controller = CubeController(api, { "token" }, { result() }, { result() }, worker, { it() },
            fetchCobra = { fetches++; DeckTextParser.parse("1 Retained card") })
        controller.newCube()
        controller.prefillCobra { reads++; "https://cubecobra.com/cube/list/early" }
        assertEquals(0, reads, "The clipboard is read only once the dialog opens")
        controller.openLink()
        controller.prefillCobra { reads++; "https://cubecobra.com/cube/list/fixture" }
        controller.closeLink(); controller.openLink()
        controller.prefillCobra { reads++; "another" }
        assertEquals(1, reads); assertEquals(0, fetches)
        assertEquals("https://cubecobra.com/cube/list/fixture", controller.state.cobraInput)
        controller.importCobra(); assertTrue(controller.state.fetching); worker.drain()
        assertEquals(1, fetches); assertFalse(controller.state.linkDialog); assertFalse(controller.state.fetching)
        assertEquals("fixture", controller.state.name); assertEquals("1 Retained card", controller.state.text)
        controller.back(); controller.newCube(); controller.openLink(); controller.cobraInput("typed")
        controller.prefillCobra { reads++; "another" }
        assertEquals("typed", controller.state.cobraInput); assertEquals(2, reads)
        controller.close(); worker.drain()
    }

    // DRF-009
    @Test fun cobraImportReplacesTheListKeepsATypedNameAndCancelDropsTheFetch() {
        val worker = Worker(); val api = Api(); var fetches = 0
        val controller = CubeController(api, { "token" }, { result() }, { result() }, worker, { it() },
            fetchCobra = { fetches++; DeckTextParser.parse("1 Retained card") })
        controller.newCube(); controller.name("My cube"); controller.text("5 Old card")
        controller.openLink(); controller.cobraInput("fixture"); controller.importCobra()
        controller.closeLink(); worker.drain()
        assertFalse(controller.state.busy); assertEquals("5 Old card", controller.state.text)
        controller.openLink(); controller.importCobra(); worker.drain()
        assertEquals(1, fetches, "The cancelled fetch never ran")
        assertEquals("My cube", controller.state.name); assertEquals("1 Retained card", controller.state.text)
        assertEquals("fixture", controller.state.provenance); assertTrue(controller.canSave)
        controller.close(); worker.drain()
    }

    // DRF-008, DRF-009
    @Test fun sharedCobraTextImportsItsCubeAndFetchFailuresKeepTheirReason() {
        val worker = Worker(); val api = Api(); val requested = mutableListOf<String>()
        var failure: Exception? = null
        val controller = CubeController(api, { "token" }, { result() }, { result() }, worker, { it() },
            fetchCobra = { id -> requested += id; failure?.let { throw it }; DeckTextParser.parse("1 Retained card") })
        controller.newCube()
        controller.cobraInput("Caleb Gannon's Powered Synergy Cube - Cube Cobra\nhttps://cubecobra.com/cube/list/synergy?view=table")
        controller.importCobra(); worker.drain()
        assertEquals(listOf("synergy"), requested)
        assertEquals("synergy", controller.state.provenance)
        controller.back(); controller.newCube()
        failure = CubeCobraFailure("Cube Cobra cube missing was not found.")
        controller.openLink(); controller.cobraInput("cubecobra.com/cube/overview/missing"); controller.importCobra(); worker.drain()
        assertEquals("Cube Cobra cube missing was not found.", controller.state.linkError)
        assertTrue(controller.state.linkDialog); assertNull(controller.state.error)
        assertTrue(controller.state.editing)
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

    private val card = CardDefinition("Retained card", listOf(DefinitionFace(UUID.randomUUID().toString(), "Retained card")),
        printingId = UUID.randomUUID().toString())
    /** The cube's card, by its printing (owner 2026-11-10). */
    private val digest = card.printingId!!
    private val summary = CubeSummary(UUID.randomUUID().toString(), "Accepted cube", "1", "2026-10-26T00:00:00Z", "2026-10-26T00:00:00Z", 1500, "fixture")
    private inner class Api : Cubes {
        var saved: SavedCube? = SavedCube(summary, listOf(SavedCubeEntry(digest, card.name, 1500)))
        val creates = mutableListOf<CreateCube>(); val updates = mutableListOf<UpdateCube>(); val deletes = mutableListOf<DeleteCube>()
        /** The cards of the card records, by printing. */
        val cards = mutableMapOf(digest to card)
        var fail = false
        var afterRead: () -> Unit = {}
        var cubeReads = 0
        /** The card records' reads, each its printing ids. */
        val recordReads = mutableListOf<Collection<String>>()
        val records = SavedCardRecords { ids -> recordReads += ids; testCardRecords(cards.values).read(ids) }
        /** [entries] as the server stores them: each card by printing and name. */
        private fun stored(entries: List<CardListEntry>) = entries.map { entry ->
            SavedCubeEntry(entry.printingId, entry.name ?: cards.getValue(entry.printingId!!).name, entry.quantity, entry.section)
        }
        override suspend fun create(token: String, value: CreateCube): SavedCube {
            creates += value
            if (fail) throw DeckFailure("request_conflict")
            return SavedCube(summary.copy(name = value.name, totalCopies = value.entries.sumOf { it.quantity.toLong() }, cubeCobraId = value.cubeCobraId), stored(value.entries)).also { saved = it }
        }
        override suspend fun listCubes(token: String, after: String?) = CubeList(listOfNotNull(saved?.cube))
        override suspend fun readCube(token: String, id: String): SavedCube { cubeReads++; afterRead(); return saved ?: throw DeckFailure("not_found") }
        override suspend fun update(token: String, id: String, value: UpdateCube): SavedCube {
            updates += value
            if (fail) throw DeckFailure("request_conflict")
            val old = saved!!
            return old.copy(cube = old.cube.copy(name = value.name ?: old.cube.name, revision = "2",
                totalCopies = value.entries?.sumOf { it.quantity.toLong() } ?: old.cube.totalCopies), entries = value.entries?.let(::stored) ?: old.entries).also { saved = it }
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
        listOf(ResolvedDeckEntry(DeckSection.MAINBOARD, quantity, testPrintings(card), listOf(1))), emptyList(), null)
    private fun controller(api: Api, worker: Worker, lookup: (String) -> DeckImportResult = { result() }, post: (() -> Unit) -> Unit = { it() }, credential: () -> String? = { "token" }) =
        CubeController(api, credential, lookup, { result() }, worker, post,
            fetchCobra = { CubeCobraImport.parse("""{"cards":{"mainboard":[{"details":{"name":"Retained card"}}]}}""") })
            .also { it.cardRecords = api.records }

    // Owner 2026-11-10 (card data from the server's catalog): a saved cube opens from the card records, and a save names printings
    @Test fun editPrefillsAndPreservesMoreThanOneThousandAcceptedCopies() {
        val api = Api(); val worker = Worker()
        val controller = controller(api, worker, lookup = testResolver(card, cube = true))
        controller.open(summary.id); worker.drain()
        controller.edit(); worker.drain()
        assertTrue(controller.state.text.contains("1500 Retained card"))
        assertTrue(controller.state.result!!.resolved)
        controller.text("Deck\n2000 Retained card"); controller.lookup(); worker.drain()
        controller.save(); worker.drain()
        assertEquals(CardListEntry(SavedDeckSection.MAINBOARD, digest, 2000), api.updates.single().entries!!.single())
        assertFalse(controller.state.editing)
        assertEquals(2000L, controller.state.detail!!.saved.cube.totalCopies)
        controller.close(); worker.drain()
    }
    // DRF-003: a cube's Maybe survives editing and saving, and pasted sideboard lines go to Maybe.
    @Test fun editingACubeKeepsItsMaybeSectionAndSideboardLinesGoToMaybe() {
        val api = Api(); val worker = Worker()
        api.saved = SavedCube(summary.copy(maybeCopies = 2),
            listOf(SavedCubeEntry(digest, card.name, 1500), SavedCubeEntry(digest, card.name, 2, SavedDeckSection.MAYBEBOARD)))
        val controller = controller(api, worker, lookup = testResolver(card, cube = true))
        controller.open(summary.id); worker.drain(); controller.edit(); worker.drain()
        assertEquals("Deck\n1500 Retained card (TST) 1\nMaybeboard\n2 Retained card (TST) 1", controller.state.text)
        assertEquals(listOf(DeckSection.MAINBOARD, DeckSection.MAYBEBOARD), controller.state.result!!.entries.map { it.section })
        controller.text("Deck\n1500 Retained card\nSideboard\n3 Retained card"); controller.lookup(); worker.drain()
        controller.save(); worker.drain()
        assertEquals(listOf(CardListEntry(SavedDeckSection.MAINBOARD, digest, 1500), CardListEntry(SavedDeckSection.MAYBEBOARD, digest, 3)),
            api.updates.single().entries)
        controller.close(); worker.drain()
    }
    @Test fun createUnknownBlocksAndCobraProvenanceAndImmutableRetryAreRetained() {
        val api = Api(); val worker = Worker()
        val controller = controller(api, worker, lookup = { text -> DeckImportResult(text, emptyList(), listOf(ImportIssue(1, "Unknown card")), null) })
        controller.newCube(); controller.name("New cube"); controller.text("1 Unknown card"); controller.lookup(); worker.drain()
        controller.save(); worker.drain(); assertTrue(api.creates.isEmpty())
        controller.cobraInput("https://cubecobra.com/cube/list/fixture"); controller.importCobra(); worker.drain()
        assertEquals("fixture", controller.state.provenance)
        controller.save(); worker.drain()
        assertEquals("fixture", api.creates.single().cubeCobraId)
        assertEquals(2L, controller.state.detail!!.saved.cube.totalCopies)
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
        assertTrue(api.recordReads.isEmpty())
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
            assertEquals("Edited", controller.state.detail!!.saved.cube.name)
            assertFalse(controller.state.editing)
            controller.back(); deliver(); assertNull(controller.state.selected)
            controller.close(); worker.drain()
        }
    }
    // Owner 2026-11-10: the largest cube's cards come from the card records in one read, none from the server
    @Test fun maximumCubeReadsItsCardsFromTheRecordsOnce() {
        val api = Api()
        api.cards.clear()
        repeat(3000) { index ->
            val card = CardDefinition("Card $index", listOf(DefinitionFace(UUID.randomUUID().toString(), "Card $index")), printingId = UUID.randomUUID().toString())
            api.cards[card.printingId!!] = card
        }
        api.saved = SavedCube(summary.copy(totalCopies = 3000), api.cards.values.map { SavedCubeEntry(it.printingId, it.name, 1) })
        val worker = Worker(); val controller = controller(api, worker)
        controller.open(summary.id); worker.drain()
        assertNull(controller.state.error)
        assertEquals(3000, controller.state.detail!!.definitions.size)
        assertEquals(api.cards.values.map { it.name }.toSet(), controller.state.detail!!.definitions.values.map { it.name }.toSet())
        assertEquals(1, api.recordReads.size); assertEquals(1, api.cubeReads)
        controller.close(); worker.drain()
    }
}
