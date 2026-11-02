package org.asyncttwiz.android

import kotlinx.coroutines.runBlocking
import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.assertEquals

/** LOB-112 (I-D21): saved decks' and cubes' stale card data is rebuilt from the catalog; everything else about them stays. */
class CardDataRefreshRunTest {
    private fun definition(name: String, stale: Boolean = false) =
        CardDefinition(name, listOf(DefinitionFace(java.util.UUID.nameUUIDFromBytes(name.toByteArray()).toString(), name, rulesText = if (stale) "old" else "new")), provider = "mtgjson",
            printingId = "p-$name")
    private fun submitted(d: CardDefinition) = SubmittedDefinition(DeckJson.digest(d), d)
    private val oldSplit = submitted(definition("Fire // Ice", stale = true))
    private val missing = submitted(definition("Gone", stale = true))
    private val plain = submitted(definition("Island"))
    private val newSplit = submitted(definition("Fire // Ice"))

    private class Store : Decks, Cubes {
        val definitions = mutableMapOf<String, CardDefinition>()
        val decks = mutableMapOf<String, SavedDeck>()
        val cubes = mutableMapOf<String, SavedCube>()
        val deckUpdates = mutableListOf<Pair<String, UpdateDeck>>()
        val cubeUpdates = mutableListOf<Pair<String, UpdateCube>>()
        override suspend fun submit(token: String, value: SubmitDefinitions): DefinitionsAccepted {
            value.definitions.forEach { definitions[it.digest] = it.definition }
            return DefinitionsAccepted(value.definitions.map { it.digest })
        }
        override suspend fun list(token: String, after: String?) = DeckList(decks.values.map { it.deck })
        override suspend fun read(token: String, id: String) = decks.getValue(id)
        override suspend fun definition(token: String, id: String, digest: String) = SubmittedDefinition(digest, definitions.getValue(digest))
        override suspend fun update(token: String, id: String, value: UpdateDeck): SavedDeck { deckUpdates += id to value; return decks.getValue(id) }
        override suspend fun listCubes(token: String, after: String?) = CubeList(cubes.values.map { it.cube })
        override suspend fun readCube(token: String, id: String) = cubes.getValue(id)
        override suspend fun cubeDefinitions(token: String, id: String, value: ReadCubeDefinitions) =
            SubmittedDefinitions(value.digests.map { SubmittedDefinition(it, definitions.getValue(it)) })
        override suspend fun update(token: String, id: String, value: UpdateCube): SavedCube { cubeUpdates += id to value; return cubes.getValue(id) }
        override suspend fun create(token: String, value: CreateDeck): SavedDeck = error("unused")
        override suspend fun delete(token: String, id: String, value: DeleteDeck): DeckDeleted = error("unused")
        override suspend fun create(token: String, value: CreateCube): SavedCube = error("unused")
        override suspend fun cubeDefinition(token: String, id: String, digest: String): SubmittedDefinition = error("unused")
        override suspend fun delete(token: String, id: String, value: DeleteCube): CubeDeleted = error("unused")
        override fun close() = Unit
    }

    @Test fun staleCardsAreRebuiltAndTheRestOfEachListStays() {
        val store = Store()
        listOf(oldSplit, missing, plain).forEach { store.definitions[it.digest] = it.definition }
        fun deck(id: String, entries: List<SavedDeckEntry>, cover: String? = null, draft: Boolean = false) = SavedDeck(
            DeckSummary(id, "Deck $id", "1", "2026-11-01", cover = cover?.let { DeckCover(it, "Cover") },
                draft = if (draft) DraftDeckOrigin("pod", "Pod") else null), entries)
        store.decks["a"] = deck("a", listOf(SavedDeckEntry(SavedDeckSection.MAINBOARD, oldSplit.digest, 2),
            SavedDeckEntry(SavedDeckSection.SIDEBOARD, oldSplit.digest, 1), SavedDeckEntry(SavedDeckSection.MAINBOARD, plain.digest, 20)),
            cover = oldSplit.digest)
        store.decks["b"] = deck("b", listOf(SavedDeckEntry(SavedDeckSection.MAINBOARD, plain.digest, 40)))
        store.decks["c"] = deck("c", listOf(SavedDeckEntry(SavedDeckSection.MAINBOARD, oldSplit.digest, 1)), draft = true)
        store.decks["d"] = deck("d", listOf(SavedDeckEntry(SavedDeckSection.MAINBOARD, missing.digest, 1)))
        store.cubes["k"] = SavedCube(CubeSummary("k", "Cube", "1", "2026-11-01", "2026-11-01", 2),
            listOf(SavedCubeEntry(oldSplit.digest, 1), SavedCubeEntry(plain.digest, 1)))
        val run = CardDataRefreshRun(store, store, { it.faces.single().rulesText == "old" }) { d ->
            if (d.name == "Gone") null else d.copy(faces = d.faces.map { it.copy(rulesText = "new") }) }
        val outcome = runBlocking { run.run("token") }
        assertEquals(CardDataRefreshRun.Outcome(1, 1, listOf("Gone in Deck d")), outcome)
        val (id, update) = store.deckUpdates.single()
        assertEquals("a", id)
        assertEquals(listOf(SavedDeckEntry(SavedDeckSection.MAINBOARD, newSplit.digest, 2),
            SavedDeckEntry(SavedDeckSection.SIDEBOARD, newSplit.digest, 1), SavedDeckEntry(SavedDeckSection.MAINBOARD, plain.digest, 20)),
            update.contents!!.entries)
        assertEquals(newSplit.digest, update.cover)
        assertEquals(null, update.name, "the name stays")
        assertEquals(newSplit.definition, store.definitions[newSplit.digest], "the rebuilt card data was submitted")
        assertEquals(listOf(SavedCubeEntry(newSplit.digest, 1), SavedCubeEntry(plain.digest, 1)), store.cubeUpdates.single().second.entries)
    }
}
