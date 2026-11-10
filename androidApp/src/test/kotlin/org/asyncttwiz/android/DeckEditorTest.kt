package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.asyncttwiz.storage.*
import org.junit.Test
import java.util.UUID
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit
import kotlin.test.*

class DeckEditorTest {
    private fun id() = UUID.randomUUID().toString()
    private fun definition(number: String) = CardDefinition("Front // Back", listOf(
        DefinitionFace(id(), "Front", sourceData = """{"setCode":"TST","number":"$number","text":"Retained text"}""")),
        printingId = id(), sourceVersion = "old-catalog")
    private val first = definition("007")
    private val second = definition("8")
    private val definitions = listOf(first, second).associateBy(DeckJson::digest)
    private val cover = DeckJson.digest(first)
    private val detail = DeckDetail(SavedDeck(DeckSummary(id(), "Saved title", "1", "2026-10-24T00:00:00Z",
        cover = DeckCover(cover, first.name)), listOf(
        SavedDeckEntry(SavedDeckSection.MAINBOARD, cover, 4),
        SavedDeckEntry(SavedDeckSection.MAINBOARD, DeckJson.digest(second), 2),
        SavedDeckEntry(SavedDeckSection.SIDEBOARD, cover, 3))), definitions)
    /** [this] save's entries as the server stores them: every card here is one the deck holds from before, named by its digest. */
    private fun DeckSave.saved() = entries.map { SavedDeckEntry(it.section, it.printingId, it.quantity) }
    private class Worker : AbstractExecutorService() {
        val queue = mutableListOf<Runnable>()
        override fun execute(command: Runnable) { queue += command }
        fun drain() { while (queue.isNotEmpty()) queue.removeAt(0).run() }
        override fun shutdown() = Unit
        override fun shutdownNow() = mutableListOf<Runnable>()
        override fun isShutdown() = false
        override fun isTerminated() = false
        override fun awaitTermination(timeout: Long, unit: TimeUnit) = true
    }
    // LOB-057
    @Test fun prefillUsesImporterSyntaxAndRoundTripsExactPrintingsSnapshotsSectionsQuantitiesAndCover() {
        val worker = Worker()
        val editor = DeckImportController({ error("Retained cards must not require another catalog lookup") }, worker) { it() }
        var plan: DeckSave? = null
        editor.connectSave({ { input -> plan = input; SavedDeck(detail.saved.deck, input.saved()) } }, {})
        editor.openEditor(detail)
        assertTrue(editor.state.editing)
        assertEquals("Deck\n4 Front // Back (TST) 007\n2 Front // Back (TST) 8\nSideboard\n3 Front // Back (TST) 007", editor.state.text)
        val parsed = DeckTextParser.parse(editor.state.text)
        assertTrue(parsed.issues.isEmpty())
        assertEquals(listOf("007", "8", "007"), parsed.lines.map { it.number })
        worker.drain()
        editor.save(); worker.drain()
        // Card data from the server's catalog (owner 2026-11-10): a card the deck holds from before is named by its digest
        assertEquals(detail.saved.entries, plan!!.saved())
        assertEquals(definitions, plan!!.cards.associate { it.digest to it.definition })
        assertEquals(detail.saved.deck.id, plan!!.target)
        assertEquals(cover, plan!!.update().cover)
        assertEquals("Saved title", plan!!.update().name)
    }
    @Test fun savingRenamedListAndChosenCoverUpdatesExistingDeckNotCreateInOneRequest() {
        val worker = Worker(); var creates = 0; var reads = 0
        val updates = mutableListOf<Pair<String, UpdateDeck>>()
        var accepted = detail.saved
        val api = object : Decks {
            override suspend fun list(token: String, after: String?) = DeckList(emptyList())
            override suspend fun read(token: String, id: String): SavedDeck { reads++; return accepted }
            override suspend fun definitions(token: String, id: String, digests: List<String>) = digests.distinct().map { SubmittedDefinition(it, definitions.getValue(it)) }
            override suspend fun create(token: String, value: CreateDeck): SavedDeck { creates++; error("Must update") }
            override suspend fun update(token: String, id: String, value: UpdateDeck): SavedDeck {
                updates += id to value
                accepted = accepted.copy(deck = accepted.deck.copy(name = value.name!!,
                    cover = DeckCover(value.cover!!, second.name)), entries = value.contents!!.entries.map { SavedDeckEntry(it.section, it.printingId, it.quantity) })
                return accepted
            }
            override suspend fun delete(token: String, id: String, value: DeleteDeck) = DeckDeleted(id)
            override fun close() = Unit
        }
        var saved: SavedDeck? = null
        val editor = DeckImportController({ error("lookup") }, worker) { it() }
        editor.connectSave(api, { "token" }, { saved = it })
        editor.openEditor(detail); worker.drain()
        editor.cover("not-in-deck"); assertEquals(cover, editor.state.cover)
        editor.chooseCover(); editor.cover(DeckJson.digest(second)); assertFalse(editor.state.choosingCover)
        editor.name("New title")
        editor.edit(editor.state.text.replace("4 Front", "6 Front"))
        editor.save() // Save performs lookup/validation even without opening an import preview.
        worker.drain()
        assertEquals(0, creates); assertEquals(1, updates.size); assertEquals(0, reads)
        assertEquals(detail.saved.deck.id, updates.single().first)
        assertEquals(6, saved!!.entries.first().quantity)
        assertEquals("New title", saved!!.deck.name)
        assertEquals(DeckJson.digest(second), saved!!.deck.cover!!.definition)
    }
    // LOB-062: editing a deck lists its Maybe under "Maybeboard" and saving keeps it there.
    @Test fun maybeCardsRoundTripThroughTheEditorText() {
        val maybe = detail.copy(saved = detail.saved.copy(entries = detail.saved.entries +
            SavedDeckEntry(SavedDeckSection.MAYBEBOARD, DeckJson.digest(second), 1)))
        val source = DeckEditorSource(maybe)
        assertEquals("Deck\n4 Front // Back (TST) 007\n2 Front // Back (TST) 8\nSideboard\n3 Front // Back (TST) 007\nMaybeboard\n1 Front // Back (TST) 8",
            source.text)
        val resolved = source.resolve(source.text) { error("Existing snapshots require no new lookup") }
        assertEquals(maybe.saved.entries, DeckSave.from(resolved, "Unchanged", retained = source.definitions).saved())
    }
    @Test fun retainedVersionsOfTheSamePrintingAreNotCollapsedDuringRoundTrip() {
        val newer = first.copy(sourceVersion = "newer-catalog")
        val newerDigest = DeckJson.digest(newer)
        val mixed = detail.copy(saved = detail.saved.copy(entries = detail.saved.entries +
            SavedDeckEntry(SavedDeckSection.MAINBOARD, newerDigest, 5)), definitions = definitions + (newerDigest to newer))
        val source = DeckEditorSource(mixed)
        val resolved = source.resolve(source.text) { error("Existing snapshots require no new lookup") }
        val plan = DeckSave.from(resolved, "Unchanged", retained = source.definitions)
        assertEquals(mixed.saved.entries.toSet(), plan.saved().toSet())
        assertEquals(mixed.definitions, plan.cards.associate { it.digest to it.definition })
    }
    // Card data from the server's catalog (owner 2026-11-10): a card the deck holds from before is kept as stored, its
    // digest, cover and entries unchanged (no device uploads card data, so none is made anew)
    @Test fun editingMultipartSavedDeckKeepsItsCardsAsStored() {
        for (layout in listOf("adventure", "split")) {
            val old = first.copy(layout = layout, faces = first.faces + DefinitionFace(id(), "Back", rulesText = "Other half"))
            val oldDigest = DeckJson.digest(old)
            val normalized = PhysicalCardFaces.normalize(old)
            val normalizedDigest = DeckJson.digest(normalized)
            assertNotEquals(oldDigest, normalizedDigest)
            val saved = detail.saved.copy(deck = detail.saved.deck.copy(cover = DeckCover(oldDigest, old.name)),
                entries = detail.saved.entries.filter { it.definition == cover }.map { it.copy(definition = oldDigest) })
            val multipart = DeckDetail(saved, mapOf(oldDigest to old))
            val source = DeckEditorSource(multipart)
            val result = source.resolve(source.text) { error("Retained cards must not require lookup") }
            assertEquals(oldDigest, DeckSave.from(result, saved.deck.name, cover = oldDigest, retained = source.definitions).cover)
            for (reselect in listOf(false, true)) {
                val worker = Worker()
                var plan: DeckSave? = null
                val editor = DeckImportController({ error("Retained cards must not require lookup") }, worker) { it() }
                editor.connectSave({ { input -> plan = input; SavedDeck(saved.deck, input.saved()) } }, {})
                editor.openEditor(multipart); worker.drain()
                assertEquals(oldDigest, editor.state.cover)
                assertEquals(oldDigest, editor.currentCover?.definition)
                assertEquals(listOf(oldDigest), editor.coverCards.map { it.digest })
                assertEquals(old, editor.coverCards.single().definition)
                if (reselect) {
                    editor.chooseCover(); editor.cover(editor.coverCards.single().digest)
                    assertFalse(editor.state.choosingCover)
                }
                editor.edit(editor.state.text.replace("4 Front", "5 Front"))
                editor.save(); worker.drain()
                assertFalse(editor.state.active, editor.state.error)
                assertEquals(saved.deck.id, plan!!.target)
                assertEquals(oldDigest, plan!!.update().cover)
                assertEquals(saved.entries.map { it.copy(quantity = if (it.section == SavedDeckSection.MAINBOARD) 5 else it.quantity) }, plan!!.saved())
                assertEquals(listOf(SubmittedDefinition(oldDigest, old)), plan!!.cards)
            }
        }
    }
    // LOB-058
    @Test fun coverRemovedFromListIsClearedAndInvalidListCannotSaveAndCancelConfirmsDirtyInput() {
        val worker = Worker()
        val editor = DeckImportController({ text -> DeckImportResult(text, emptyList(), listOf(ImportIssue(2, "Unknown card")), null) }, worker) { it() }
        editor.openEditor(detail); worker.drain()
        editor.cancel(); assertFalse(editor.state.active) // Unchanged edit cancels without a prompt.
        editor.openEditor(detail); worker.drain()
        editor.edit("Deck\n2 Front // Back (TST) 8"); editor.lookup(); worker.drain()
        assertNull(editor.state.cover)
        editor.cover(cover); assertNull(editor.state.cover)
        editor.name("Draft title"); editor.cancel(); assertTrue(editor.state.discard)
        editor.keepEditing(); assertEquals("Draft title", editor.state.name)
        editor.edit("1 Unknown card"); editor.save(); worker.drain()
        assertFalse(editor.state.saving); assertFalse(editor.state.result!!.resolved)
        assertEquals("1 Unknown card", editor.state.text)
        editor.back(); assertTrue(editor.state.discard)
        editor.exit(); assertFalse(editor.state.active)
    }
    @Test fun newImportCoverGoesWithItsCreateAndUncertainRetryKeepsItsRequestIdentity() {
        val worker = Worker()
        val printing = CatalogPrinting(id(), "New card", "TST", "1", "English", "", null, false, emptyList(), "{}")
        val editor = DeckImportController({ text -> DeckImportResult(text,
            listOf(ResolvedDeckEntry(DeckSection.MAINBOARD, 1, listOf(printing), listOf(1))), emptyList(),
            CatalogMetadata("2026-10-24", "test")) }, worker) { it() }
        val creates = mutableListOf<CreateDeck>(); val updates = mutableListOf<UpdateDeck>()
        var accepted = detail.saved
        val api = object : Decks {
            override suspend fun list(token: String, after: String?) = DeckList(emptyList())
            override suspend fun read(token: String, id: String) = accepted
            override suspend fun definitions(token: String, id: String, digests: List<String>): List<SubmittedDefinition> = error("unused")
            override suspend fun create(token: String, value: CreateDeck): SavedDeck {
                creates += value
                // Fail the original attempt and both silent service retries.
                if (creates.size <= 3) throw DeckFailure("network")
                return accepted.copy(entries = value.entries.map { SavedDeckEntry(it.section, value.cover!!, it.quantity) },
                    deck = accepted.deck.copy(name = value.name, cover = DeckCover(value.cover!!, "New card")))
            }
            override suspend fun update(token: String, id: String, value: UpdateDeck): SavedDeck { updates += value; error("Must create") }
            override suspend fun delete(token: String, id: String, value: DeleteDeck) = DeckDeleted(id)
            override fun close() = Unit
        }
        var saved: SavedDeck? = null
        editor.connectSave(api, { "token" }, { saved = it })
        editor.open(); editor.name("Imported"); editor.edit("1 New card"); editor.lookup(); worker.drain()
        val digest = editor.coverCards.single().digest
        editor.cover(digest); editor.review(); editor.save(); worker.drain()
        assertTrue(editor.state.active); assertEquals(digest, editor.state.cover)
        editor.save(); worker.drain()
        // One request, its cover with it; each attempt replays it with the same request identity.
        assertEquals(4, creates.size); assertEquals(1, creates.distinct().size); assertTrue(updates.isEmpty())
        assertEquals(digest, creates.last().cover)
        assertEquals(digest, saved!!.deck.cover!!.definition)
    }
}
