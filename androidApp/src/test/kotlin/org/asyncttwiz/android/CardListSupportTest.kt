package org.asyncttwiz.android

import arcanum.api.Layout
import arcanum.parser.OracleCard
import arcanum.parser.OracleParser
import org.asyncttwiz.protocol.*
import org.asyncttwiz.storage.ParsedCard
import org.junit.Test
import java.util.UUID
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit
import kotlin.test.*

/** I-D51 (LOB-115, DRF-161): decks' and cubes' Automatum support, worked out on the device. Invented cards. */
class CardListSupportTest {
    private fun id() = UUID.randomUUID().toString()
    private fun card(name: String, text: String) = CardDefinition(name, listOf(DefinitionFace(id(), name, typeLine = "Creature — Bear",
        manaCost = "{1}{G}", rulesText = text, power = "2", toughness = "2", sourceData = """{"setCode":"TST","number":"1"}""")),
        printingId = id())
    private val plain = card("Plain Cub", "Trample")
    private val odd = card("Odd Cub", "Whenever the moon is full, this creature dances a slow jig.")
    private val banded = card("Banded Cub", "Whenever the tide turns, this creature hums an old sea song.")
    private val unreadable = CardDefinition("Broken Cub", listOf(DefinitionFace(id(), "Broken Cub", typeLine = "Creature — Bear", rulesText = "Trample")),
        layout = "split")
    private val cards = listOf(plain, odd, banded, unreadable).associateBy(DeckJson::digest)
    private val parsed = mutableListOf<String>()

    /** As the device parses a game's card data (CardDefinitions), without its cache. */
    private val check = CardListSupportCheck({ submitted ->
        submitted.associate { item ->
            parsed += item.definition.name
            val face = item.definition.faces.single()
            item.digest to if (item.definition.layout != "normal") ParsedCard.Unreadable("halves missing")
                else ParsedCard.Read(arcanum.api.CardDefinition(Layout.Normal, listOf(OracleParser.parse(OracleCard(face.name,
                    face.manaCost, face.typeLine, face.rulesText, power = face.power, toughness = face.toughness)))), 0)
        }
    }, parserVersion = 21, engineVersion = 3)
    private fun submitted(vararg definitions: CardDefinition) = definitions.map { SubmittedDefinition(DeckJson.digest(it), it) }

    // LOB-115: unread text (two cards) and unreadable card data are by hand; each card is read once (no keyword is by hand any more)
    @Test fun aListNamesItsCardsPlayedByHand() {
        assertEquals(CardListSupport(21, engineVersion = 3), check.support(submitted(plain)))
        assertEquals(CardListSupport(21, listOf("Banded Cub", "Broken Cub", "Odd Cub"), 3), check.support(submitted(plain, odd, banded, unreadable)))
        assertEquals(listOf("Banded Cub", "Broken Cub", "Odd Cub", "Plain Cub"), parsed.sorted())
        assertEquals("Automatum plays 3 cards by hand: Banded Cub, Broken Cub, Odd Cub", automatumSupportTip(check.support(submitted(odd, banded, unreadable))))
        assertEquals("Automatum plays 1 card by hand: Odd Cub", automatumSupportTip(CardListSupport(21, listOf("Odd Cub"))))
    }

    // LOB-123 (I-D52): a card that ventures or takes the initiative is by hand when the card records hold no dungeon Automatum plays
    // fully for it (here only a plain venture's)
    @Test fun aVenturingCardNeedsADungeonTheDeviceCanBring() {
        fun spell(name: String, type: String, text: String) = CardDefinition(name, listOf(DefinitionFace(id(), name, typeLine = type,
            manaCost = "{W}", rulesText = text)), printingId = id())
        val map = spell("Torch Map", "Sorcery", "Venture into the dungeon.")
        val claim = spell("Claim Rite", "Instant", "You take the initiative.")
        val plainOnly = CardListSupportCheck({ submitted -> submitted.associate { item ->
            val face = item.definition.faces.single()
            item.digest to ParsedCard.Read(arcanum.api.CardDefinition(Layout.Normal, listOf(OracleParser.parse(OracleCard(face.name,
                face.manaCost, face.typeLine, face.rulesText, power = face.power.ifBlank { null }, toughness = face.toughness.ifBlank { null })))), 0)
        } }, parserVersion = 21, engineVersion = 3, dungeons = { setOf(org.asyncttwiz.protocol.game.OutsideCards.Need.Dungeon) })
        assertEquals(CardListSupport(21, listOf("Claim Rite"), 3), plainOnly.support(submitted(map, claim, plain)))
    }

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

    private inner class Server : Decks, Cubes {
        val decks = linkedMapOf<String, SavedDeck>()
        val drafts = linkedMapOf<String, SavedDeck>()
        val cubes = linkedMapOf<String, SavedCube>()
        val stored = mutableListOf<Pair<String, SetCardListSupport>>()
        override suspend fun submit(token: String, value: SubmitDefinitions) = DefinitionsAccepted(value.definitions.map { it.digest })
        override suspend fun create(token: String, value: CreateDeck): SavedDeck =
            SavedDeck(DeckSummary(id(), value.name, "1", "2026-11-03T00:00:00Z"), value.entries).also { decks[it.deck.id] = it }
        override suspend fun list(token: String, after: String?) = DeckList(decks.values.map { it.deck })
        override suspend fun drafts(token: String) = DeckList(drafts.values.map { it.deck })
        override suspend fun read(token: String, id: String) = decks[id] ?: drafts.getValue(id)
        override suspend fun definition(token: String, id: String, digest: String) = SubmittedDefinition(digest, cards.getValue(digest))
        override suspend fun update(token: String, id: String, value: UpdateDeck): SavedDeck {
            val old = decks.getValue(id)
            return SavedDeck(old.deck.copy(name = value.name ?: old.deck.name, revision = "${old.deck.revision.toInt() + 1}", automatum = null),
                value.contents?.entries ?: old.entries).also { decks[id] = it }
        }
        override suspend fun delete(token: String, id: String, value: DeleteDeck) = DeckDeleted(id)
        override suspend fun setSupport(token: String, id: String, value: SetCardListSupport): CardListSupportStored {
            stored += id to value
            val deck = read(token, id)
            return CardListSupportStored(deck.deck.revision == value.revision)
        }
        override suspend fun create(token: String, value: CreateCube) = error("not used")
        override suspend fun listCubes(token: String, after: String?) = CubeList(cubes.values.map { it.cube })
        override suspend fun readCube(token: String, id: String) = cubes.getValue(id)
        override suspend fun cubeDefinition(token: String, id: String, digest: String) = SubmittedDefinition(digest, cards.getValue(digest))
        override suspend fun cubeDefinitions(token: String, id: String, value: ReadCubeDefinitions) =
            SubmittedDefinitions(value.digests.map { SubmittedDefinition(it, cards.getValue(it)) })
        override suspend fun update(token: String, id: String, value: UpdateCube) = error("not used")
        override suspend fun delete(token: String, id: String, value: DeleteCube) = CubeDeleted(id)
        override suspend fun setCubeSupport(token: String, id: String, value: SetCardListSupport): CardListSupportStored {
            stored += id to value
            return CardListSupportStored(cubes.getValue(id).cube.revision == value.revision)
        }
        override fun close() = Unit
    }

    private fun deck(name: String, revision: String, support: CardListSupport?, vararg definitions: CardDefinition) =
        SavedDeck(DeckSummary(id(), name, revision, "2026-11-03T00:00:00Z", automatum = support),
            definitions.map { SavedDeckEntry(SavedDeckSection.MAINBOARD, DeckJson.digest(it), 4) })

    // LOB-124: a deck gets its support when it is saved, stored for the saved revision
    @Test fun savingADeckStoresItsSupport() {
        val server = Server()
        val worker = Worker()
        val editor = DeckImportController({ error("lookup") }, worker) { it() }
        editor.supportCheck = check
        var saved: SavedDeck? = null
        editor.connectSave(server, { "token" }) { saved = it }
        val source = deck("Cubs", "1", null, plain, odd)
        server.decks[source.deck.id] = source
        editor.openEditor(DeckDetail(source, source.entries.associate { it.definition to cards.getValue(it.definition) }))
        worker.drain()
        editor.name("New cubs")
        editor.save(); worker.drain()
        assertEquals(listOf(source.deck.id to SetCardListSupport("2", CardListSupport(21, listOf("Odd Cub"), 3))), server.stored)
        assertEquals("New cubs", saved!!.deck.name)
        assertEquals(CardListSupport(21, listOf("Odd Cub"), 3), saved!!.deck.automatum)
    }

    // LOB-124, DRF-161: lists without support for this parser and engine version (none yet, or an older version's) get it in
    // the background
    @Test fun theBackgroundRunWorksOutMissingAndOlderSupportOnly() {
        val server = Server()
        val missing = deck("Missing", "3", null, plain, odd)
        val older = deck("Older", "5", CardListSupport(20), plain)
        val current = deck("Current", "2", CardListSupport(21, listOf("Plain Cub"), 3), plain)
        val olderEngine = deck("Older engine", "4", CardListSupport(21, engineVersion = 2), plain)
        val draft = deck("Cube night", "1", null, banded).let { it.copy(deck = it.deck.copy(draft = DraftDeckOrigin(id(), "Cube night"))) }
        listOf(missing, older, current, olderEngine).forEach { server.decks[it.deck.id] = it }
        server.drafts[draft.deck.id] = draft
        val cube = SavedCube(CubeSummary(id(), "Odd cube", "7", "2026-11-03T00:00:00Z", "2026-11-03T00:00:00Z", 2, automatum = CardListSupport(19)),
            listOf(SavedCubeEntry(DeckJson.digest(plain), 1), SavedCubeEntry(DeckJson.digest(unreadable), 1)))
        val fine = SavedCube(CubeSummary(id(), "Fine cube", "1", "2026-11-03T00:00:00Z", "2026-11-03T00:00:00Z", 1, automatum = CardListSupport(21, engineVersion = 3)),
            listOf(SavedCubeEntry(DeckJson.digest(plain), 1)))
        server.cubes[cube.cube.id] = cube; server.cubes[fine.cube.id] = fine
        val outcome = kotlinx.coroutines.runBlocking { AutomatumSupportRun(server, server, 21, 3, check::support).run("token") }
        assertEquals(AutomatumSupportRun.Outcome(decks = 4, cubes = 1), outcome)
        assertEquals(mapOf(
            missing.deck.id to SetCardListSupport("3", CardListSupport(21, listOf("Odd Cub"), 3)),
            older.deck.id to SetCardListSupport("5", CardListSupport(21, engineVersion = 3)),
            olderEngine.deck.id to SetCardListSupport("4", CardListSupport(21, engineVersion = 3)),
            draft.deck.id to SetCardListSupport("1", CardListSupport(21, listOf("Banded Cub"), 3)),
            cube.cube.id to SetCardListSupport("7", CardListSupport(21, listOf("Broken Cub"), 3))), server.stored.toMap())
    }
}
