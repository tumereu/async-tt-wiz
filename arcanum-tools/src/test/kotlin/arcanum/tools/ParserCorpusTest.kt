package arcanum.tools

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The corpus from an invented AllPrintings: which cards count, once each, under which scope. */
class ParserCorpusTest {
    private val dir: File = createTempDirectory("parser-corpus").toFile()

    @AfterTest fun clean() {
        dir.deleteRecursively()
    }

    private fun card(
        name: String,
        oracle: String = "oracle-$name",
        uuid: String = "uuid-$name",
        layout: String = "normal",
        text: String = "Draw a card.",
        side: String? = null,
        others: List<String> = emptyList(),
        faceName: String? = null,
        flag: String? = null,
    ) = JsonObject().apply {
        addProperty("name", name)
        addProperty("uuid", uuid)
        addProperty("layout", layout)
        addProperty("type", "Sorcery")
        addProperty("text", text)
        side?.let { addProperty("side", it) }
        faceName?.let { addProperty("faceName", it) }
        flag?.let { addProperty(it, true) }
        if (others.isNotEmpty()) add("otherFaceIds", JsonArray().apply { others.forEach(::add) })
        add("identifiers", JsonObject().apply { addProperty("scryfallOracleId", oracle) })
        add("foreignData", JsonArray()) // skipped over by the tool
    }

    private fun set(type: String, date: String, vararg cards: JsonObject) = JsonObject().apply {
        addProperty("type", type)
        addProperty("releaseDate", date)
        add("cards", JsonArray().apply { cards.forEach(::add) })
        add("tokens", JsonArray())
    }

    @Test fun aSupportedSetTheFileLacksIsRefused() {
        val failure = runCatching { corpus(supported = setOf("SUP", "ZZZ")) }.exceptionOrNull()
        assertTrue(failure?.message.orEmpty().contains("ZZZ"), failure.toString())
    }

    private fun corpus(supported: Set<String> = setOf("SUP"), cubeFile: Boolean = true, fallbackCube: Set<String> = emptySet()): ParserCorpus {
        val data = JsonObject().apply {
            add("SUP", set("expansion", "2024-01-01", card("Reprinted Bolt", text = "Draw two cards."), card("Set Bear")))
            add("OLD", set("expansion", "2001-01-01", card("Reprinted Bolt", text = "Draw a card."), card("Held Sprite"), card("Cube Bear")))
            add("NEW", set("expansion", "2025-01-01", card("Reprinted Bolt", text = "Draw three cards."), card("Up Half", uuid = "a", side = "a",
                others = listOf("b"), layout = "transform", faceName = "Up"), card("Up Half", uuid = "b", side = "b", others = listOf("a"), layout = "transform", faceName = "Down")))
            add("FUN", set("funny", "2020-01-01", card("Funny Set Card"), card("Both Worlds")))
            add("TOK", set("token", "2020-01-01", card("Token Card")))
            add("ART", set("expansion", "2020-01-01", card("Art Card", layout = "art_series"), card("Both Worlds", layout = "reversible_card"),
                card("Flag Funny", flag = "isFunny"), card("Flag Online", flag = "isOnlineOnly"), card("Flag Rebalanced", flag = "isRebalanced")))
            add("NET", JsonObject().apply {
                addProperty("type", "expansion")
                addProperty("releaseDate", "2020-01-01")
                addProperty("isOnlineOnly", true)
                add("cards", JsonArray().apply { add(card("Online Set Card")) })
            })
            add("LAT", set("expansion", "2010-01-01", card("Both Worlds")))
        }
        val root = JsonObject().apply {
            add("meta", JsonObject().apply { addProperty("version", "9.9.9") })
            add("data", data)
        }
        val file = File(dir, "AllPrintings.json").apply { writeText(root.toString()) }
        val cube = JsonObject().apply {
            add("Cube Bear", JsonArray().apply { add(card("Cube Bear")) })
            add("Set Bear", JsonArray().apply { add(card("Set Bear")) })
            add("Up Half", JsonArray().apply { add(card("Up Half")) })
            add("Un Waiter", JsonArray().apply { add(JsonObject().apply {
                addProperty("name", "Un Waiter")
                addProperty("type", "Creature — Goblin")
                addProperty("text", "Flying")
                addProperty("layout", "normal")
            }) })
        }
        val cubeJson = File(dir, "cube.json").apply { writeText(cube.toString()) }
        return readCorpus(file, if (cubeFile) cubeJson else File(dir, "absent.json"), supported) { fallbackCube }
    }

    @Test fun readsTheVersionAndSortsByName() {
        val c = corpus()
        assertEquals("9.9.9", c.version)
        assertEquals(c.cards.map { it.name }.sorted(), c.cards.map { it.name })
    }

    @Test fun onePrintingPerOracleIdTheNewest() {
        val bolts = corpus().cards.filter { it.name == "Reprinted Bolt" }
        assertEquals(1, bolts.size)
        assertEquals("Draw three cards.", bolts.single().faces.single().text)
    }

    @Test fun exclusionsLeaveOutFunnyOnlineTokenAndArtCards() {
        val names = corpus().cards.map { it.name }
        for (gone in listOf("Funny Set Card", "Token Card", "Art Card", "Flag Funny", "Flag Online", "Flag Rebalanced", "Online Set Card")) {
            assertFalse(gone in names, gone)
        }
    }

    @Test fun aCardWithAnyPlayablePrintingStaysAndReadsThatPrinting() {
        val both = corpus().cards.single { it.name == "Both Worlds" }
        assertEquals("normal", both.layout)
        assertEquals(CardScope.HeldOut, both.scope)
    }

    @Test fun facesComeInPrintedOrderOnce() {
        val card = corpus().cards.single { it.name == "Up Half" }
        assertEquals(listOf("Up", "Down"), card.faces.map { it.name })
        assertEquals("transform", card.layout)
    }

    @Test fun scopes() {
        val scope = corpus().cards.associate { it.name to it.scope }
        assertEquals(CardScope.SupportedSet, scope["Reprinted Bolt"], "any printing in a supported set counts")
        assertEquals(CardScope.SupportedSet, scope["Set Bear"], "a supported set outranks the cube")
        assertEquals(CardScope.Cube, scope["Cube Bear"])
        assertEquals(CardScope.Cube, scope["Up Half"])
        assertEquals(CardScope.HeldOut, scope["Held Sprite"])
    }

    @Test fun aCubeCardTheCorpusLeavesOutIsReadFromTheCubeList() {
        val card = corpus().cards.single { it.name == "Un Waiter" }
        assertEquals(CardScope.Cube, card.scope)
        assertEquals("cube:Un Waiter", card.id)
        assertTrue(cardModel(card.layout, card.faces).read)
    }

    @Test fun withoutACubeFileTheSupportedCardsListIsTheCube() {
        val cards = corpus(cubeFile = false, fallbackCube = setOf("Held Sprite", "Up Half", "Set Bear", "Un Waiter")).cards
        val scope = cards.associate { it.name to it.scope }
        assertEquals(CardScope.Cube, scope["Held Sprite"])
        assertEquals(CardScope.Cube, scope["Up Half"])
        assertEquals(CardScope.SupportedSet, scope["Set Bear"], "a supported set outranks the list")
        assertEquals(CardScope.HeldOut, scope["Cube Bear"])
        assertFalse(cards.any { it.name == "Un Waiter" }, "a listed name the corpus lacks has no faces to read")
    }

    @Test fun theSupportedCardsListReadsAsNames() {
        val names = supportedCards()
        assertTrue(names.isNotEmpty())
        assertTrue(names.none { it.startsWith("#") || it.isBlank() })
    }

    @Test fun readingTwiceGivesTheSameCardsInTheSameOrder() {
        fun models() = corpus().cards.map { Triple(it.id, it.scope, cardModel(it.layout, it.faces).json) }
        assertEquals(models(), models())
    }
}
