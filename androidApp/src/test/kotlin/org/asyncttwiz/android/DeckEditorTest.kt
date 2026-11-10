package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.asyncttwiz.storage.*
import org.junit.Test
import java.util.UUID
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit
import kotlin.test.*

/** Owner 2026-11-10 (card data from the server's catalog): a saved deck holds printings; the editor builds its cards from the
 * phone's card records, and a save names printings (a card without a printing by its name). */
class DeckEditorTest {
    private fun id() = UUID.randomUUID().toString()
    private fun card(name: String) = CardDefinition(name, listOf(DefinitionFace(id(), name, typeLine = "Creature — Bear", rulesText = "Some text")),
        printingId = id())
    private val first = card("Front Card")
    private val second = card("Second Card")
    private val cover = first.printingId!!
    private val records = testCardRecords(first, second)
    private val detail = DeckDetail(SavedDeck(DeckSummary(id(), "Saved title", "1", "2026-10-24T00:00:00Z",
        cover = DeckCover(cover, first.name)), listOf(
        savedEntry(SavedDeckSection.MAINBOARD, first, 4),
        savedEntry(SavedDeckSection.MAINBOARD, second, 2),
        savedEntry(SavedDeckSection.SIDEBOARD, first, 3))), emptyMap())
    /** [this] save's entries as the server stores them. */
    private fun DeckSave.saved() = entries.map { entry -> SavedDeckEntry(entry.section, entry.printingId,
        listOf(first, second).firstOrNull { it.printingId == entry.printingId }?.name ?: entry.name!!, entry.quantity) }
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
    private fun editor(worker: Worker, resolve: (String) -> DeckImportResult = { error("Opening a saved deck needs no lookup") }) =
        DeckImportController(resolve, worker) { it() }.also { it.cardRecords = records }

    // LOB-057, SYS-084
    @Test fun aSavedDeckOpensFromTheCardRecordsAndSavesItsPrintingsSectionsQuantitiesAndCover() {
        val worker = Worker()
        val editor = editor(worker)
        var plan: DeckSave? = null
        editor.connectSave({ { input -> plan = input; SavedDeck(detail.saved.deck, input.saved()) } }, {})
        editor.openEditor(detail); worker.drain()
        assertTrue(editor.state.editing)
        assertEquals("Deck\n4 Front Card (TST) 1\n2 Second Card (TST) 1\nSideboard\n3 Front Card (TST) 1", editor.state.text)
        assertTrue(editor.state.result!!.resolved)
        editor.save(); worker.drain()
        assertEquals(detail.saved.entries, plan!!.saved())
        assertEquals(listOf(first.printingId, second.printingId), plan!!.cards.map { it.definition.printingId })
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
            override suspend fun create(token: String, value: CreateDeck): SavedDeck { creates++; error("Must update") }
            override suspend fun update(token: String, id: String, value: UpdateDeck): SavedDeck {
                updates += id to value
                accepted = accepted.copy(deck = accepted.deck.copy(name = value.name!!, cover = DeckCover(value.cover!!, second.name)),
                    entries = value.contents!!.entries.map { entry -> SavedDeckEntry(entry.section, entry.printingId,
                        listOf(first, second).single { it.printingId == entry.printingId }.name, entry.quantity) })
                return accepted
            }
            override suspend fun delete(token: String, id: String, value: DeleteDeck) = DeckDeleted(id)
            override fun close() = Unit
        }
        var saved: SavedDeck? = null
        val editor = editor(worker, testResolver(first, second))
        editor.connectSave(api, { "token" }, { saved = it })
        editor.openEditor(detail); worker.drain()
        editor.cover("not-in-deck"); assertEquals(cover, editor.state.cover)
        editor.chooseCover(); editor.cover(second.printingId!!); assertFalse(editor.state.choosingCover)
        editor.name("New title")
        editor.edit(editor.state.text.replace("4 Front", "6 Front"))
        editor.save() // Save performs lookup/validation even without opening an import preview.
        worker.drain()
        assertEquals(0, creates); assertEquals(1, updates.size); assertEquals(0, reads)
        assertEquals(detail.saved.deck.id, updates.single().first)
        assertEquals(6, saved!!.entries.first().quantity)
        assertEquals("New title", saved!!.deck.name)
        assertEquals(second.printingId, saved!!.deck.cover!!.definition)
    }

    // LOB-062: editing a deck lists its Maybe under "Maybeboard" and saving keeps it there.
    @Test fun maybeCardsRoundTripThroughTheEditor() {
        val maybe = detail.copy(saved = detail.saved.copy(entries = detail.saved.entries + savedEntry(SavedDeckSection.MAYBEBOARD, second, 1)))
        val worker = Worker()
        val editor = editor(worker)
        var plan: DeckSave? = null
        editor.connectSave({ { input -> plan = input; SavedDeck(maybe.saved.deck, input.saved()) } }, {})
        editor.openEditor(maybe); worker.drain()
        assertEquals("Deck\n4 Front Card (TST) 1\n2 Second Card (TST) 1\nSideboard\n3 Front Card (TST) 1\nMaybeboard\n1 Second Card (TST) 1",
            editor.state.text)
        editor.save(); worker.drain()
        assertEquals(maybe.saved.entries, plan!!.saved())
    }

    // SYS-084, LOB-115: a card without a printing, and one the card records don't have, stay as they are: their names
    // alone, never the cover, saved as the deck holds them
    @Test fun cardsTheRecordsCantBuildAreKeptByTheirNameOrPrinting() {
        val unknown = card("Unknown Printing")
        val held = detail.copy(saved = detail.saved.copy(entries = listOf(savedEntry(SavedDeckSection.MAINBOARD, first, 4),
            SavedDeckEntry(SavedDeckSection.MAINBOARD, null, "Lost Card", 2), savedEntry(SavedDeckSection.SIDEBOARD, unknown, 1))))
        val worker = Worker()
        val editor = editor(worker)
        var plan: DeckSave? = null
        editor.connectSave({ { input -> plan = input; held.saved } }, {})
        editor.openEditor(held); worker.drain()
        assertEquals("Deck\n4 Front Card (TST) 1\n2 Lost Card\nSideboard\n1 Unknown Printing", editor.state.text)
        assertEquals(listOf(first.printingId, unknown.printingId), editor.coverCards.map { it.definition.printingId })
        assertNull(editor.retainedDefinition(first.printingId!!))
        assertNull(editor.retainedDefinition("name:Lost Card")!!.printingId)
        editor.save(); worker.drain()
        assertEquals(listOf(CardListEntry(SavedDeckSection.MAINBOARD, first.printingId, 4), CardListEntry(SavedDeckSection.MAINBOARD, null, 2, "Lost Card"),
            CardListEntry(SavedDeckSection.SIDEBOARD, unknown.printingId, 1)), plan!!.entries)
    }

    // LOB-058
    @Test fun coverRemovedFromListIsClearedAndInvalidListCannotSaveAndCancelConfirmsDirtyInput() {
        val worker = Worker()
        val editor = editor(worker, testResolver(second))
        editor.openEditor(detail); worker.drain()
        editor.cancel(); assertFalse(editor.state.active) // Unchanged edit cancels without a prompt.
        editor.openEditor(detail); worker.drain()
        editor.edit("Deck\n2 Second Card"); editor.lookup(); worker.drain()
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
            override suspend fun create(token: String, value: CreateDeck): SavedDeck {
                creates += value
                // Fail the original attempt and both silent service retries.
                if (creates.size <= 3) throw DeckFailure("network")
                return accepted.copy(entries = value.entries.map { SavedDeckEntry(it.section, it.printingId, "New card", it.quantity) },
                    deck = accepted.deck.copy(name = value.name, cover = DeckCover(value.cover!!, "New card")))
            }
            override suspend fun update(token: String, id: String, value: UpdateDeck): SavedDeck { updates += value; error("Must create") }
            override suspend fun delete(token: String, id: String, value: DeleteDeck) = DeckDeleted(id)
            override fun close() = Unit
        }
        var saved: SavedDeck? = null
        editor.connectSave(api, { "token" }, { saved = it })
        editor.open(); editor.name("Imported"); editor.edit("1 New card"); editor.lookup(); worker.drain()
        val chosen = editor.coverCards.single().definition.printingId!!
        assertEquals(printing.id, chosen)
        editor.cover(chosen); editor.review(); editor.save(); worker.drain()
        assertTrue(editor.state.active); assertEquals(chosen, editor.state.cover)
        editor.save(); worker.drain()
        // One request, its cover with it; each attempt replays it with the same request identity.
        assertEquals(4, creates.size); assertEquals(1, creates.distinct().size); assertTrue(updates.isEmpty())
        assertEquals(chosen, creates.last().cover)
        assertEquals(chosen, saved!!.deck.cover!!.definition)
    }
}
