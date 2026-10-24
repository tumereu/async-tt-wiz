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
    @Test fun prefillUsesImporterSyntaxAndRoundTripsExactPrintingsSnapshotsSectionsQuantitiesAndCover() {
        val worker = Worker()
        val editor = DeckImportController({ error("Retained cards must not require another catalog lookup") }, worker) { it() }
        var plan: DeckSavePlan? = null
        editor.connectSave({ { input -> plan = input; SavedDeck(detail.saved.deck, input.deck.entries) } }, {})
        editor.openEditor(detail)
        assertTrue(editor.state.editing)
        assertEquals("Deck\n4 Front // Back (TST) 007\n2 Front // Back (TST) 8\nSideboard\n3 Front // Back (TST) 007", editor.state.text)
        val parsed = DeckTextParser.parse(editor.state.text)
        assertTrue(parsed.issues.isEmpty())
        assertEquals(listOf("007", "8", "007"), parsed.lines.map { it.number })
        worker.drain()
        editor.save(); worker.drain()
        assertEquals(detail.saved.entries, plan!!.deck.entries)
        assertEquals(definitions, plan!!.definitions.definitions.associate { it.digest to it.definition })
        assertEquals(detail.saved.deck.id, plan!!.target)
        assertEquals(cover, plan!!.update().cover)
        assertEquals("Saved title", plan!!.update().name)
    }
    @Test fun savingRenamedListAndChosenCoverUpdatesExistingDeckNotCreateAndReadsAcceptedState() {
        val worker = Worker(); var creates = 0; var reads = 0
        val updates = mutableListOf<Pair<String, UpdateDeck>>()
        var accepted = detail.saved
        val api = object : Decks {
            override suspend fun list(token: String, after: String?) = DeckList(emptyList())
            override suspend fun read(token: String, id: String): SavedDeck { reads++; return accepted }
            override suspend fun definition(token: String, id: String, digest: String) = SubmittedDefinition(digest, definitions.getValue(digest))
            override suspend fun submit(token: String, value: SubmitDefinitions) = DefinitionsAccepted(value.definitions.map { it.digest })
            override suspend fun create(token: String, value: CreateDeck): SavedDeck { creates++; error("Must update") }
            override suspend fun update(token: String, id: String, value: UpdateDeck): SavedDeck {
                updates += id to value
                accepted = accepted.copy(deck = accepted.deck.copy(name = value.name!!,
                    cover = DeckCover(value.cover!!, second.name)), entries = value.contents!!.entries)
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
        assertEquals(0, creates); assertEquals(1, updates.size); assertEquals(1, reads)
        assertEquals(detail.saved.deck.id, updates.single().first)
        assertEquals(6, saved!!.entries.first().quantity)
        assertEquals("New title", saved!!.deck.name)
        assertEquals(DeckJson.digest(second), saved!!.deck.cover!!.definition)
    }
    @Test fun retainedVersionsOfTheSamePrintingAreNotCollapsedDuringRoundTrip() {
        val newer = first.copy(sourceVersion = "newer-catalog")
        val newerDigest = DeckJson.digest(newer)
        val mixed = detail.copy(saved = detail.saved.copy(entries = detail.saved.entries +
            SavedDeckEntry(SavedDeckSection.MAINBOARD, newerDigest, 5)), definitions = definitions + (newerDigest to newer))
        val source = DeckEditorSource(mixed)
        val resolved = source.resolve(source.text) { error("Existing snapshots require no new lookup") }
        val plan = DeckSavePlan.from(resolved, "Unchanged", retained = source.definitions)
        assertEquals(mixed.saved.entries.toSet(), plan.deck.entries.toSet())
        assertEquals(mixed.definitions, plan.definitions.definitions.associate { it.digest to it.definition })
    }
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
    @Test fun newImportCoverUsesCreatedDeckAndUncertainRetryKeepsBothRequestIdentities() {
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
            override suspend fun definition(token: String, id: String, digest: String): SubmittedDefinition = error("unused")
            override suspend fun submit(token: String, value: SubmitDefinitions) = DefinitionsAccepted(value.definitions.map { it.digest })
            override suspend fun create(token: String, value: CreateDeck): SavedDeck {
                creates += value
                return accepted.copy(entries = value.entries, deck = accepted.deck.copy(name = value.name, cover = null))
            }
            override suspend fun update(token: String, id: String, value: UpdateDeck): SavedDeck {
                assertEquals(accepted.deck.id, id); updates += value
                // Fail the original attempt and both silent service retries.
                if (updates.size <= 3) throw DeckFailure("network")
                accepted = accepted.copy(deck = accepted.deck.copy(cover = DeckCover(value.cover!!, "New card")))
                return accepted
            }
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
        // Each attempt replays the whole plan (create, then cover update) with the same request identities.
        assertEquals(4, creates.size); assertEquals(1, creates.distinct().size); assertEquals(1, updates.distinct().size)
        assertEquals(digest, saved!!.deck.cover!!.definition)
    }
}
