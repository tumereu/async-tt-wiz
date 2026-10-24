package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.asyncttwiz.storage.*
import org.junit.Test
import java.util.UUID
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit
import kotlin.test.*

class CardInspectionTest {
    private val front = UUID.randomUUID().toString()
    private val back = UUID.randomUUID().toString()
    private val printing = UUID.randomUUID().toString()
    private fun result(): DeckImportResult {
        val faces = listOf("a" to front, "b" to back).map { (side, id) ->
            CatalogPrinting(id, "Front // Back", "SET", "4", "English", side, printing, false,
                listOf(if (id == front) back else front),
                """{"faceName":"${if (side == "a") "Front" else "Back"}","side":"$side","layout":"transform","manaCost":"{2}{U}","type":"Creature — Wizard","text":"Exact  text\nUnicode 🧙","power":"*","toughness":"2"}""")
        }
        return DeckImportResult("Deck\n4 Front // Back", listOf(ResolvedDeckEntry(DeckSection.MAINBOARD, 4, faces, listOf(2))),
            emptyList(), CatalogMetadata("2026-10-11", "5.2.2", "catalog-identity"))
    }
    @Test fun previewAndSavedSnapshotHaveIdenticalTextDefaultsAndExplicitFaceArtwork() {
        val result = result()
        val entry = result.entries.single()
        val preview = InspectedCard.preview(entry, result.catalog)
        val submitted = DeckSavePlan.from(result, "Name").definitions.definitions.single()
        val saved = InspectedCard.retained(submitted.digest, submitted.definition)
        assertEquals(submitted.definition, preview.definition)
        assertEquals(listOf("Front", "Back"), saved.definition.faces.map { it.name })
        assertEquals("Exact  text\nUnicode 🧙", saved.definition.faces[1].rulesText)
        assertEquals("*/2", saved.definition.faces[0].corner)
        for (card in listOf(preview, saved)) {
            assertEquals(listOf(0, 1), card.artwork.map { it!!.face })
            assertEquals(listOf(printing, printing), card.artwork.map { it!!.printing })
            assertEquals(listOf("large", "large"), card.artwork.map { it!!.size })
        }
        assertEquals(listOf(submitted.digest, submitted.digest), saved.artwork.map { it!!.version })
        assertEquals(0, InspectedCard.retained("single", CardDefinition("Blank", listOf(DefinitionFace(front, "Blank"))))
            .artwork.single()!!.face)
        assertNull(InspectedCard.retained("bad-side", CardDefinition("Blank", listOf(DefinitionFace(front, "Blank",
            sourceData = """{"side":"not-a-side"}""")))).artwork.single())
    }
    @Test fun selectionZoomBoundsAndCloseAreOnlyPersonalCapturedPresentation() {
        val card = InspectedCard.preview(result().entries.single(), result().catalog)
        val controller = CardInspectionController()
        controller.open(card); controller.zoom(); assertTrue(controller.state!!.enlarged)
        controller.face(1); assertEquals(1, controller.state!!.face); assertFalse(controller.state!!.enlarged)
        controller.face(100); controller.face(-1); assertEquals(1, controller.state!!.face)
        assertSame(card, controller.state!!.card)
        controller.close(); controller.face(0); controller.zoom(); assertNull(controller.state)
    }
    @Test fun importBackRetainsDraftNameQuantityAndPreviewWithoutSaveAndLogoutClearsInspection() {
        val worker = Worker(); var saves = 0
        val result = result()
        val controller = DeckImportController({ result }, worker) { it() }
        controller.connectSave({ { plan -> saves++; SavedDeck(DeckSummary(front, "Name", "1", "date"), plan.deck.entries) } }, {})
        controller.open(); controller.edit(result.text); controller.lookup(); worker.drain()
        controller.review(); controller.name("Unfinished name")
        val before = controller.state
        controller.inspect(result.entries.single()); controller.inspection.face(1); controller.back()
        assertNull(controller.inspection.state); assertEquals(before, controller.state); assertEquals(0, saves)
        controller.inspect(result.entries.single()); controller.exit()
        assertNull(controller.inspection.state); assertFalse(controller.state.active)
        controller.open(); controller.inspect(result.entries.single()); assertNull(controller.inspection.state)
    }
    @Test fun savedInspectionUsesRetainedDefinitionWithoutCatalogOrNewRequestsAndClearsOnAccountChange() {
        val worker = Worker(); var reads = 0; var mutations = 0
        val definition = DeckSavePlan.from(result(), "Name").definitions.definitions.single()
        val summary = DeckSummary(front, "Name", "1", "date")
        val saved = SavedDeck(summary, listOf(SavedDeckEntry(SavedDeckSection.SIDEBOARD, definition.digest, 4)))
        val api = object : Decks {
            override suspend fun list(token: String, after: String?) = DeckList(listOf(summary))
            override suspend fun read(token: String, id: String): SavedDeck { reads++; return saved }
            override suspend fun definition(token: String, id: String, digest: String) = definition
            override suspend fun submit(token: String, value: SubmitDefinitions): DefinitionsAccepted = error("Unexpected submit")
            override suspend fun create(token: String, value: CreateDeck): SavedDeck = error("Unexpected create")
            override suspend fun update(token: String, id: String, value: UpdateDeck): SavedDeck { mutations++; return saved }
            override suspend fun delete(token: String, id: String, value: DeleteDeck): DeckDeleted { mutations++; return DeckDeleted(id) }
            override fun close() = Unit
        }
        val controller = DeckLibraryController(api, { "token" }, worker) { it() }
        controller.open(front); worker.drain(); controller.section(SavedDeckSection.SIDEBOARD)
        val before = controller.state
        controller.inspect("foreign"); assertNull(controller.inspection.state)
        controller.inspect(definition.digest); controller.inspection.face(1)
        controller.retry(); worker.drain()
        assertEquals(definition.definition, controller.inspection.state!!.card.definition)
        controller.back(); assertEquals(before, controller.state); assertEquals(2, reads); assertEquals(0, mutations)
        controller.inspect(definition.digest); controller.clear(); assertNull(controller.inspection.state)
        controller.inspect(definition.digest); assertNull(controller.inspection.state)
    }
    private class Worker : AbstractExecutorService() {
        private val queue = mutableListOf<Runnable>()
        override fun execute(command: Runnable) { queue += command }
        override fun shutdown() = Unit
        override fun shutdownNow(): MutableList<Runnable> = mutableListOf()
        override fun isShutdown() = false
        override fun isTerminated() = false
        override fun awaitTermination(timeout: Long, unit: TimeUnit) = false
        fun drain() { while (queue.isNotEmpty()) queue.removeAt(0).run() }
    }
}
