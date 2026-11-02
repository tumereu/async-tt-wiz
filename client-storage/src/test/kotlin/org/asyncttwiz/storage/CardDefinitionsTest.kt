package org.asyncttwiz.storage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import arcanum.api.Ability
import arcanum.api.CardDefinition
import arcanum.api.Color
import arcanum.api.Keyword
import arcanum.api.Layout
import arcanum.parser.OracleCard
import arcanum.parser.OracleParser
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.sql.DriverManager
import java.util.Properties
import java.util.UUID

/** Arcanum definitions of catalog cards parsed on the device and cached (docs/specs/arcanum-atw-integration.md I-D2).
 * Every card is invented, with wordings from oracle templates. */
class CardDefinitionsTest {
    @TempDir lateinit var temporary: Path
    private val catalogDirectory get() = temporary.resolve("catalog").toFile()
    private val storageFile get() = temporary.resolve("replica.db")
    private val opened = mutableListOf<ClientStorage>()

    @AfterEach fun close() = opened.forEach { it.close() }

    private fun catalog() = CatalogRepository(catalogDirectory) { JdbcSqliteDriver("jdbc:sqlite:${it.absolutePath}") }
    private fun storage(): ClientStorage = (ClientStorage.open {
        JdbcSqliteDriver("jdbc:sqlite:${storageFile.toAbsolutePath()}", Properties().apply { setProperty("foreign_keys", "true") })
    } as StorageOpenResult.Opened).storage.also { opened += it }

    /** Counts parses, so tests see which cards were read from the cache. */
    private val parsed = mutableListOf<String>()
    private fun definitions(storage: ClientStorage = storage(), parserVersion: Int = OracleParser.VERSION) =
        CardDefinitions(catalog(), storage, parserVersion) { layout, faces ->
            parsed += faces.first().name
            OracleParser.parseCard(layout, faces)
        }

    // ---- An invented MTGJSON-shaped catalog

    private fun id(vararg parts: Any) = UUID.nameUUIDFromBytes(parts.joinToString("|").toByteArray()).toString()
    private fun array(values: List<String>) = JsonArray().apply { values.forEach(::add) }

    private fun card(number: String, name: String, type: String, cost: String = "", colors: String = "", text: String = "",
        layout: String = "normal", power: String? = null, toughness: String? = null, loyalty: String? = null,
        side: String? = null, faceName: String? = null, others: List<String> = emptyList(),
        uuid: String = id(number, side ?: "")) = JsonObject().apply {
        addProperty("uuid", uuid); addProperty("name", name); addProperty("setCode", "INV"); addProperty("number", number)
        addProperty("type", type); if (cost.isNotEmpty()) addProperty("manaCost", cost); addProperty("text", text)
        addProperty("layout", layout); addProperty("language", "English"); addProperty("rarity", "common")
        add("colors", array(colors.map(Char::toString)))
        add("identifiers", JsonObject().apply { addProperty("scryfallId", id("scryfall", uuid)) })
        power?.let { addProperty("power", it) }; toughness?.let { addProperty("toughness", it) }; loyalty?.let { addProperty("loyalty", it) }
        side?.let { addProperty("side", it) }; faceName?.let { addProperty("faceName", it) }
        if (others.isNotEmpty()) add("otherFaceIds", array(others))
    }

    /** Two linked faces sharing one card name, as MTGJSON stores split, adventurer and double-faced cards. */
    private fun twoFaces(number: String, layout: String, front: JsonObject.() -> Unit, back: JsonObject.() -> Unit): List<JsonObject> {
        val a = id(number, "a"); val b = id(number, "b")
        return listOf(
            card(number, "", "", layout = layout, side = "a", others = listOf(b), uuid = a).apply(front),
            card(number, "", "", layout = layout, side = "b", others = listOf(a), uuid = b).apply(back),
        ).also { faces ->
            val name = faces.joinToString(" // ") { it.get("faceName").asString }
            faces.forEach { it.addProperty("name", name) }
        }
    }

    private fun JsonObject.face(name: String, type: String, cost: String, colors: String, text: String,
        power: String? = null, toughness: String? = null) {
        addProperty("faceName", name); addProperty("type", type); addProperty("text", text)
        if (cost.isNotEmpty()) addProperty("manaCost", cost) else remove("manaCost")
        add("colors", array(colors.map(Char::toString)))
        power?.let { addProperty("power", it) }; toughness?.let { addProperty("toughness", it) }
    }

    private val sentry = card("1", "Thornback Sentry", "Creature — Elf Warrior", "{1}{G}", "G", "Flying\nVigilance", power = "2", toughness = "3")
    private val moth = card("2", "Riddle Moth", "Creature — Insect", "{U}", "U",
        "Flying\nWhenever the moon sings, this creature hums a tune.", power = "1", toughness = "1")
    private val expanse = card("3", "Hollow Expanse", "Plane — Nowhere", layout = "planar", text = "Creatures can't block.")
    private val split = twoFaces("10", "split",
        { face("Kindle", "Instant", "{1}{R}", "R", "Kindle deals 2 damage to any target.") },
        { face("Rime", "Instant", "{1}{U}", "U", "Tap target creature.\nDraw a card.") })
    private val adventure = twoFaces("11", "adventure",
        { face("Hedge Ogre", "Creature — Ogre", "{2}{G}", "G", "Trample", "4", "3") },
        { face("Trample Down", "Sorcery — Adventure", "{1}{G}", "G", "Destroy target artifact.") })
    private val transform = twoFaces("12", "transform",
        { face("Moonlit Herder", "Creature — Human Werewolf", "{1}{G}", "G", "Reach", "2", "2") },
        { face("Howling Herder", "Creature — Werewolf", "", "G", "Trample", "4", "4") })
    private val modal = twoFaces("13", "modal_dfc",
        { face("Tidecaller's Call", "Instant", "{1}{U}", "U", "Draw two cards.") },
        { face("Tidecaller Grotto", "Land", "", "", "{T}: Add {U}.") })
    private val meld = run {
        val ridge = id("20", "a"); val colossus = id("20", "b"); val vale = id("21", "c")
        listOf(
            card("20", "Ridge Twin // Twin Colossus", "Creature — Giant", "{3}{R}", "R", "Haste", "meld", "3", "3",
                side = "a", faceName = "Ridge Twin", others = listOf(colossus), uuid = ridge),
            card("20", "Ridge Twin // Twin Colossus", "Creature — Giant", colors = "RG", text = "Trample", layout = "meld",
                power = "9", toughness = "9", side = "b", faceName = "Twin Colossus", others = listOf(ridge, vale), uuid = colossus),
            card("21", "Vale Twin // Twin Colossus", "Creature — Giant", "{3}{G}", "G", "Reach", "meld", "3", "3",
                side = "c", faceName = "Vale Twin", others = listOf(colossus), uuid = vale),
        )
    }
    private val token = card("T1", "Spirit", "Token Creature — Spirit", colors = "W", text = "Flying", layout = "token",
        power = "1", toughness = "1")

    private fun install(cards: List<JsonObject> = listOf(sentry, moth, expanse) + split + adventure + transform + modal + meld) {
        val set = JsonObject().apply {
            addProperty("code", "INV"); addProperty("name", "Invented"); addProperty("type", "expansion")
            addProperty("releaseDate", "2026-01-01")
            add("cards", JsonArray().apply { cards.forEach(::add) }); add("tokens", JsonArray().apply { add(token) })
        }
        val root = JsonObject().apply {
            add("meta", JsonObject().apply { addProperty("date", "2026-10-31"); addProperty("version", "5.3.0") })
            add("data", JsonObject().apply { add("INV", set) })
        }
        catalog().stage { index -> MtgJsonCatalog().import(root.toString().byteInputStream(), index, CatalogMetadata("2026-10-31", "5.3.0")) }
    }

    private fun uuid(card: JsonObject) = card.get("uuid").asString
    private fun read(result: ParsedCard?) = assertInstanceOf(ParsedCard.Read::class.java, result)

    // ---- Mapping catalog records to the parser's printed faces

    @Test fun singleFaceCardIsParsedFromItsPrintedValues() {
        install()
        val result = read(definitions().forCards(listOf(uuid(sentry)))[uuid(sentry)])
        assertEquals(OracleParser.parseCard(Layout.Normal, listOf(OracleCard("Thornback Sentry", "{1}{G}", "Creature — Elf Warrior",
            "Flying\nVigilance", power = "2", toughness = "3"))), result.definition)
        assertTrue(result.fullyParsed)
        assertEquals(listOf(Keyword.Flying, Keyword.Vigilance), result.definition.faces.single().abilities)
    }

    @Test fun splitAdventureTransformAndModalCardsKeepTheirLayoutAndEveryFaceInPrintedOrder() {
        install()
        val ids = listOf(split, adventure, transform, modal).map { uuid(it.first()) }
        val result = definitions().forCards(ids)
        fun definition(faces: List<JsonObject>) = read(result[uuid(faces.first())]).definition
        assertEquals(OracleParser.parseCard(Layout.Split, listOf(
            OracleCard("Kindle", "{1}{R}", "Instant", "Kindle deals 2 damage to any target."),
            OracleCard("Rime", "{1}{U}", "Instant", "Tap target creature.\nDraw a card."),
        )), definition(split))
        assertEquals(OracleParser.parseCard(Layout.Adventure, listOf(
            OracleCard("Hedge Ogre", "{2}{G}", "Creature — Ogre", "Trample", power = "4", toughness = "3"),
            OracleCard("Trample Down", "{1}{G}", "Sorcery — Adventure", "Destroy target artifact."),
        )), definition(adventure))
        // The back face has no mana cost: its colour comes from its colour indicator (CR 204).
        assertEquals(OracleParser.parseCard(Layout.Transform, listOf(
            OracleCard("Moonlit Herder", "{1}{G}", "Creature — Human Werewolf", "Reach", power = "2", toughness = "2"),
            OracleCard("Howling Herder", null, "Creature — Werewolf", "Trample", power = "4", toughness = "4", colorIndicator = "G"),
        )), definition(transform))
        assertEquals(listOf(Color.Green), definition(transform).faces[1].colorIndicator)
        assertEquals(OracleParser.parseCard(Layout.ModalDoubleFaced, listOf(
            OracleCard("Tidecaller's Call", "{1}{U}", "Instant", "Draw two cards."),
            OracleCard("Tidecaller Grotto", null, "Land", "{T}: Add {U}."),
        )), definition(modal))
        assertNull(definition(modal).faces[1].colorIndicator)
    }

    @Test fun anyFaceIdGivesTheWholeCardFrontFirst() {
        install()
        val back = uuid(transform[1])
        assertEquals(listOf("Moonlit Herder", "Howling Herder"), read(definitions().forCards(listOf(back))[back]).definition.faces.map { it.name })
    }

    @Test fun meldCardIsItsFrontAndTheMeldedFaceWithThePartnerNamed() {
        install()
        val ridge = uuid(meld[0])
        val definition = read(definitions().forCards(listOf(ridge))[ridge]).definition
        assertEquals(Layout.Meld("Vale Twin"), definition.layout)
        assertEquals(listOf("Ridge Twin", "Twin Colossus"), definition.faces.map { it.name })
        assertInstanceOf(ParsedCard.Unreadable::class.java, definitions().forCards(listOf(uuid(meld[1])))[uuid(meld[1])])
    }

    @Test fun tokenTypeLineIsReadWithoutTheTokenWord() {
        install()
        val spirit = read(definitions().forCards(listOf(uuid(token)))[uuid(token)]).definition.faces.single()
        assertEquals(OracleParser.parse(OracleCard("Spirit", null, "Creature — Spirit", "Flying", power = "1", toughness = "1",
            colorIndicator = "W")), spirit)
    }

    @Test fun cardTheParserCantFullyReadKeepsItsUnparsedAbilitiesForPlayByHand() {
        install()
        val result = read(definitions().forCards(listOf(uuid(moth)))[uuid(moth)])
        assertFalse(result.fullyParsed)
        assertEquals(1, result.manualAbilities)
        assertEquals(listOf(Keyword.Flying, Ability.Unparsed("Whenever the moon sings, this creature hums a tune.")),
            result.definition.faces.single().abilities)
        // Read back from the cache, the count and the unparsed text are the same.
        assertEquals(result, definitions().forCards(listOf(uuid(moth)))[uuid(moth)])
    }

    @Test fun cardWithUnreadablePrintedValuesIsUnreadableAndMissingCardsAreAbsent() {
        val definitions = definitions()
        assertEquals(emptyMap<String, ParsedCard>(), definitions.forCards(listOf(uuid(sentry))), "no catalog installed")
        install()
        val result = definitions.forCards(listOf(uuid(expanse), id("not in the catalog")))
        assertEquals(setOf(uuid(expanse)), result.keys)
        assertTrue(assertInstanceOf(ParsedCard.Unreadable::class.java, result[uuid(expanse)]).problem.contains("Plane — Nowhere"))
        assertEquals(result, definitions().forCards(listOf(uuid(expanse))), "cached")
    }

    // ---- The cache

    private fun rows(where: String = "1"): List<String> = DriverManager.getConnection("jdbc:sqlite:${storageFile.toAbsolutePath()}").use { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT card_id || '@' || parser_version FROM card_definition WHERE $where ORDER BY 1").use { rows ->
                buildList { while (rows.next()) add(rows.getString(1)) }
            }
        }
    }

    @Test fun cachedDefinitionsAreReadInOneBatchWithoutParsingAgainAlsoAfterReopening() {
        install()
        val ids = listOf(uuid(sentry), uuid(moth), uuid(split[0]), uuid(transform[0]))
        val first = definitions().forCards(ids)
        assertEquals(listOf("Thornback Sentry", "Riddle Moth", "Kindle", "Moonlit Herder"), parsed)
        parsed.clear()
        val storage = storage()
        assertEquals(first, definitions(storage).forCards(ids))
        storage.close()
        assertEquals(first, definitions().forCards(ids.reversed()))
        assertEquals(emptyList<String>(), parsed)
        assertEquals(ids.map { "$it@${OracleParser.VERSION}" }.sorted(), rows())
        // Only the new card is parsed when a game needs one more.
        definitions().forCards(ids + uuid(modal[0]))
        assertEquals(listOf("Tidecaller's Call"), parsed)
    }

    @Test fun newParserVersionParsesAgainAndDropsDefinitionsOfOtherVersions() {
        install()
        val ids = listOf(uuid(sentry), uuid(moth))
        val old = definitions().forCards(ids)
        parsed.clear()
        assertEquals(old, definitions(parserVersion = OracleParser.VERSION + 1).forCards(ids))
        assertEquals(listOf("Thornback Sentry", "Riddle Moth"), parsed)
        assertEquals(ids.map { "$it@${OracleParser.VERSION + 1}" }.sorted(), rows())
    }

    @Test fun changedCatalogRecordIsParsedAgainOthersStayCached() {
        install()
        val ids = listOf(uuid(sentry), uuid(moth))
        definitions().forCards(ids)
        parsed.clear()
        // A newer catalog with an oracle errata to one card.
        val erratum = sentry.deepCopy().apply { addProperty("text", "Flying\nReach") }
        install(listOf(erratum, moth))
        val result = definitions().forCards(ids)
        assertEquals(listOf("Thornback Sentry"), parsed)
        assertEquals(listOf(Keyword.Flying, Keyword.Reach), read(result[uuid(sentry)]).definition.faces.single().abilities)
    }

    @Test fun cachedDefinitionTheModelCanNoLongerReadIsParsedAgain() {
        install()
        val expected = definitions().forCards(listOf(uuid(sentry)))
        parsed.clear()
        DriverManager.getConnection("jdbc:sqlite:${storageFile.toAbsolutePath()}").use { connection ->
            connection.createStatement().use { it.execute("UPDATE card_definition SET definition_json = '{\"layout\":\"unknown\"}'") }
        }
        assertEquals(expected, definitions().forCards(listOf(uuid(sentry))))
        assertEquals(listOf("Thornback Sentry"), parsed)
    }

    @Test fun definitionJsonRoundTrips() {
        install()
        val all = (listOf(sentry, moth) + split + adventure + transform + modal).map(::uuid)
        definitions().forCards(all).values.filterIsInstance<ParsedCard.Read>().forEach { card ->
            val json = kotlinx.serialization.json.Json.encodeToString(CardDefinition.serializer(), card.definition)
            assertEquals(card.definition, kotlinx.serialization.json.Json.decodeFromString(CardDefinition.serializer(), json))
        }
    }
}
