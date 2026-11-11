package org.asyncttwiz.storage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import arcanum.api.Ability
import arcanum.api.Color
import arcanum.api.Keyword
import arcanum.api.Layout
import arcanum.parser.OracleCard
import arcanum.parser.OracleParser
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.asyncttwiz.protocol.DeckJson
import org.asyncttwiz.protocol.DeckValidation
import org.asyncttwiz.protocol.PhysicalCardFaces
import org.asyncttwiz.protocol.PrintedPart
import org.asyncttwiz.protocol.SubmittedDefinition
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.sql.DriverManager
import java.util.Properties
import java.util.UUID

/** Arcanum definitions of a game's cards, parsed on the device from the game's card data and cached
 * (docs/specs/arcanum-atw-integration.md I-D2). The card data is built from an invented catalog the way deck import
 * builds it; every card is invented, with wordings from oracle templates. */
class CardDefinitionsTest {
    @TempDir lateinit var temporary: Path
    private val storageFile get() = temporary.resolve("replica.db")
    private val opened = mutableListOf<ClientStorage>()

    @AfterEach fun close() = opened.forEach { it.close() }

    private fun storage(): ClientStorage = (ClientStorage.open {
        JdbcSqliteDriver("jdbc:sqlite:${storageFile.toAbsolutePath()}", Properties().apply { setProperty("foreign_keys", "true") })
    } as StorageOpenResult.Opened).storage.also { opened += it }

    /** Counts parses, so tests see which cards were read from the cache. */
    private val parsed = mutableListOf<String>()
    private fun definitions(storage: ClientStorage = storage(), parserVersion: Int = OracleParser.VERSION) =
        CardDefinitions(storage, parserVersion) { layout, faces ->
            parsed += faces.first().name
            OracleParser.parseCard(layout, faces)
        }

    // ---- An invented MTGJSON-shaped catalog, and game card data built from it as deck import does

    private fun id(vararg parts: Any) = UUID.nameUUIDFromBytes(parts.joinToString("|").toByteArray()).toString()
    private fun array(values: List<String>) = JsonArray().apply { values.forEach(::add) }

    private fun card(number: String, name: String, type: String, cost: String = "", colors: String = "", text: String = "",
        layout: String = "normal", power: String? = null, toughness: String? = null, side: String? = null,
        faceName: String? = null, others: List<String> = emptyList(), uuid: String = id(number, side ?: "")) = JsonObject().apply {
        addProperty("uuid", uuid); addProperty("name", name); addProperty("setCode", "INV"); addProperty("number", number)
        addProperty("type", type); if (cost.isNotEmpty()) addProperty("manaCost", cost); addProperty("text", text)
        addProperty("layout", layout); addProperty("language", "English")
        add("colors", array(colors.map(Char::toString)))
        add("identifiers", JsonObject().apply { addProperty("scryfallId", id("scryfall", uuid)) })
        power?.let { addProperty("power", it) }; toughness?.let { addProperty("toughness", it) }
        side?.let { addProperty("side", it) }; faceName?.let { addProperty("faceName", it) }
        if (others.isNotEmpty()) add("otherFaceIds", array(others))
    }

    /** Two linked faces sharing one card name, as MTGJSON stores split, adventurer and double-faced cards. */
    private fun twoFaces(number: String, layout: String, front: JsonObject.() -> Unit, back: JsonObject.() -> Unit): List<JsonObject> {
        val a = id(number, "a"); val b = id(number, "b")
        val faces = listOf(
            card(number, "", "", layout = layout, side = "a", others = listOf(b), uuid = a).apply(front),
            card(number, "", "", layout = layout, side = "b", others = listOf(a), uuid = b).apply(back),
        )
        val name = faces.joinToString(" // ") { it.get("faceName").asString }
        return faces.onEach { it.addProperty("name", name) }
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
    // set check (TDM): card data names an Omen card's layout "adventure"
    private val omen = twoFaces("14", "adventure",
        { face("Tidewing Regent", "Creature — Dragon", "{3}{U}{U}", "U", "Flying", "4", "4") },
        { face("Swell and Surge", "Instant — Omen", "{1}{U}", "U", "Draw a card. (Then shuffle this card into its owner's library.)") })
    private val prepare = twoFaces("15", "prepare",
        { face("Quillward Tutor", "Creature — Human Wizard", "{1}{U}", "U",
            "This creature enters prepared. (While it's prepared, you may cast a copy of its spell. Doing so unprepares it.)", "2", "2") },
        { face("Ink Lesson", "Sorcery", "{U}", "U", "Draw a card.") })
    /** A meld pair's front (with its partner's name) and the back face it shares, as MTGJSON links them. */
    private val meld: List<JsonObject> = run {
        val front = id("16", "a"); val back = id("16", "b"); val partner = id("17", "a")
        listOf(
            card("16", "Bramble Twin // Thornwall Colossus", "Creature — Elf", "{2}{G}", "G", "Reach\n(Melds with Hollow Twin.)",
                layout = "meld", power = "2", toughness = "3", side = "a", faceName = "Bramble Twin", others = listOf(back), uuid = front),
            card("16b", "Thornwall Colossus", "Creature — Elemental", colors = "G", text = "Trample", layout = "meld", power = "9",
                toughness = "9", side = "b", faceName = "Thornwall Colossus", others = listOf(front, partner), uuid = back),
            card("17", "Hollow Twin // Thornwall Colossus", "Creature — Elf", "{1}{G}", "G", "(Melds with Bramble Twin.)",
                layout = "meld", power = "1", toughness = "1", side = "a", faceName = "Hollow Twin", others = listOf(back), uuid = partner),
        )
    }
    private val token = card("T1", "Spirit", "Token Creature — Spirit", colors = "W", text = "Flying", layout = "token",
        power = "1", toughness = "1")

    /** Game card data, by card: built from the installed catalog and normalized to physical sides, as a deck is saved. */
    private val cards: Map<String, SubmittedDefinition> by lazy {
        val catalog = CatalogRepository(temporary.resolve("catalog").toFile()) { JdbcSqliteDriver("jdbc:sqlite:${it.absolutePath}") }
        val set = JsonObject().apply {
            addProperty("code", "INV"); addProperty("name", "Invented"); addProperty("type", "expansion")
            addProperty("releaseDate", "2026-01-01")
            add("cards", JsonArray().apply { (listOf(sentry, moth, expanse) + split + adventure + transform + modal + omen + prepare + meld).forEach(::add) })
            add("tokens", JsonArray().apply { add(token) })
        }
        val root = JsonObject().apply {
            add("meta", JsonObject().apply { addProperty("date", "2026-10-31"); addProperty("version", "5.3.0") })
            add("data", JsonObject().apply { add("INV", set) })
        }
        val meta = catalog.stage { index -> MtgJsonCatalog().import(root.toString().byteInputStream(), index, CatalogMetadata("2026-10-31", "5.3.0")) }
        listOf(sentry, moth, expanse, split[0], adventure[0], transform[0], modal[0], omen[0], prepare[0], meld[0], token).associate { record ->
            val printing = checkNotNull(catalog.printing(record.get("uuid").asString))
            val faces = (printing.otherFaces + printing.id)
                .map { checkNotNull(catalog.printing(it)) }.sortedWith(compareBy<CatalogPrinting> { it.side }.thenBy { it.id })
            val definition = PhysicalCardFaces.normalize(ResolvedDeckEntry(DeckSection.MAINBOARD, 1, faces, emptyList()).definition(meta))
            assertTrue(DeckValidation.definition(definition), definition.name)
            printing.name to SubmittedDefinition(DeckJson.digest(definition), definition)
        }
    }
    private fun of(name: String) = cards.getValue(name)
    private fun parse(name: String): ParsedCard? = definitions().forDefinitions(listOf(of(name)))[of(name).digest]
    private fun read(result: ParsedCard?) = assertInstanceOf(ParsedCard.Read::class.java, result)

    // ---- Card data built from the catalog keeps the printed values the parser needs

    @Test fun builtCardDataKeepsBackFaceColourIndicatorAndThePrintedPartsOfSharedFaces() {
        val herder = of("Moonlit Herder // Howling Herder").definition
        assertEquals(listOf(null, "G"), herder.faces.map { it.colorIndicator })
        assertEquals(listOf(null), of("Thornback Sentry").definition.faces.map { it.colorIndicator })
        assertEquals(listOf(null, null), of("Tidecaller's Call // Tidecaller Grotto").definition.faces.map { it.colorIndicator })
        val split = of("Kindle // Rime").definition.faces.single()
        assertEquals(listOf(
            PrintedPart("Kindle", "Instant", "{1}{R}", "Kindle deals 2 damage to any target."),
            PrintedPart("Rime", "Instant", "{1}{U}", "Tap target creature.\nDraw a card."),
        ), split.parts)
        assertEquals(2, of("Hedge Ogre // Trample Down").definition.faces.single().parts.size)
    }

    // ---- Mapping card data to the parser's printed faces

    @Test fun singleFaceCardIsParsedFromItsPrintedValues() {
        val result = read(parse("Thornback Sentry"))
        assertEquals(OracleParser.parseCard(Layout.Normal, listOf(OracleCard("Thornback Sentry", "{1}{G}", "Creature — Elf Warrior",
            "Flying\nVigilance", power = "2", toughness = "3"))), result.definition)
        assertTrue(result.fullyParsed)
        assertEquals(listOf(Keyword.Flying, Keyword.Vigilance), result.definition.faces.single().abilities)
    }

    @Test fun splitAdventureTransformAndModalCardsKeepTheirLayoutAndEveryFaceInPrintedOrder() {
        val names = listOf("Kindle // Rime", "Hedge Ogre // Trample Down", "Moonlit Herder // Howling Herder",
            "Tidecaller's Call // Tidecaller Grotto")
        val result = definitions().forDefinitions(names.map(::of))
        fun definition(name: String) = read(result[of(name).digest]).definition
        assertEquals(OracleParser.parseCard(Layout.Split, listOf(
            OracleCard("Kindle", "{1}{R}", "Instant", "Kindle deals 2 damage to any target."),
            OracleCard("Rime", "{1}{U}", "Instant", "Tap target creature.\nDraw a card."),
        )), definition(names[0]))
        assertEquals(OracleParser.parseCard(Layout.Adventure, listOf(
            OracleCard("Hedge Ogre", "{2}{G}", "Creature — Ogre", "Trample", power = "4", toughness = "3"),
            OracleCard("Trample Down", "{1}{G}", "Sorcery — Adventure", "Destroy target artifact."),
        )), definition(names[1]))
        // The back face has no mana cost: its colour comes from its colour indicator (CR 204).
        assertEquals(OracleParser.parseCard(Layout.Transform, listOf(
            OracleCard("Moonlit Herder", "{1}{G}", "Creature — Human Werewolf", "Reach", power = "2", toughness = "2"),
            OracleCard("Howling Herder", null, "Creature — Werewolf", "Trample", power = "4", toughness = "4", colorIndicator = "G"),
        )), definition(names[2]))
        assertEquals(listOf(Color.Green), definition(names[2]).faces[1].colorIndicator)
        assertEquals(OracleParser.parseCard(Layout.ModalDoubleFaced, listOf(
            OracleCard("Tidecaller's Call", "{1}{U}", "Instant", "Draw two cards."),
            OracleCard("Tidecaller Grotto", null, "Land", "{T}: Add {U}."),
        )), definition(names[3]))
    }

    // set check (TDM, SOS, FIN): Omen cards (named "adventure" in card data, CR 720), preparation cards (CR 722) and meld
    // cards (CR 712.4) are read with their layouts, not left unreadable
    @Test fun omenPreparationAndMeldCardsKeepTheirLayout() {
        val result = definitions().forDefinitions(listOf("Tidewing Regent // Swell and Surge", "Quillward Tutor // Ink Lesson",
            "Bramble Twin // Thornwall Colossus").map(::of))
        fun definition(name: String) = read(result[of(name).digest]).definition
        assertEquals(OracleParser.parseCard(Layout.Omen, listOf(
            OracleCard("Tidewing Regent", "{3}{U}{U}", "Creature — Dragon", "Flying", power = "4", toughness = "4"),
            OracleCard("Swell and Surge", "{1}{U}", "Instant — Omen", "Draw a card. (Then shuffle this card into its owner's library.)"),
        )), definition("Tidewing Regent // Swell and Surge"))
        assertEquals(OracleParser.parseCard(Layout.Prepare, listOf(
            OracleCard("Quillward Tutor", "{1}{U}", "Creature — Human Wizard",
                "This creature enters prepared. (While it's prepared, you may cast a copy of its spell. Doing so unprepares it.)",
                power = "2", toughness = "2"),
            OracleCard("Ink Lesson", "{U}", "Sorcery", "Draw a card."),
        )), definition("Quillward Tutor // Ink Lesson"))
        assertEquals(OracleParser.parseCard(Layout.Meld("Hollow Twin"), listOf(
            OracleCard("Bramble Twin", "{2}{G}", "Creature — Elf", "Reach\n(Melds with Hollow Twin.)", power = "2", toughness = "3"),
            OracleCard("Thornwall Colossus", null, "Creature — Elemental", "Trample", power = "9", toughness = "9", colorIndicator = "G"),
        )), definition("Bramble Twin // Thornwall Colossus"))
    }

    @Test fun tokenTypeLineIsReadWithoutTheTokenWord() {
        val spirit = read(parse("Spirit")).definition.faces.single()
        assertEquals(OracleParser.parse(OracleCard("Spirit", null, "Creature — Spirit", "Flying", power = "1", toughness = "1",
            colorIndicator = "W")), spirit)
    }

    @Test fun cardDataMadeBeforeThePrintedValuesExistedIsReadWhereItCanBe() {
        // A back face without the colour indicator field: read from the catalog record kept with the face.
        val herder = of("Moonlit Herder // Howling Herder").definition
        val old = herder.copy(faces = herder.faces.map { it.copy(colorIndicator = null) })
        val parsedOld = definitions().forDefinitions(listOf(SubmittedDefinition(DeckJson.digest(old), old)))
        assertEquals(listOf(Color.Green), read(parsedOld.values.single()).definition.faces[1].colorIndicator)
        // A split card flattened without its parts: only the combined text is left.
        val split = of("Kindle // Rime").definition
        val flattened = split.copy(faces = split.faces.map { it.copy(parts = emptyList()) })
        assertInstanceOf(ParsedCard.Unreadable::class.java,
            definitions().forDefinitions(listOf(SubmittedDefinition(DeckJson.digest(flattened), flattened))).values.single())
    }

    @Test fun cardTheParserCantFullyReadKeepsItsUnparsedAbilitiesForPlayByHand() {
        val result = read(parse("Riddle Moth"))
        assertFalse(result.fullyParsed)
        assertEquals(1, result.manualAbilities)
        assertEquals(listOf(Keyword.Flying, Ability.Unparsed("Whenever the moon sings, this creature hums a tune.")),
            result.definition.faces.single().abilities)
        // Read back from the cache, the count and the unparsed text are the same.
        assertEquals(result, parse("Riddle Moth"))
    }

    @Test fun cardWithUnreadablePrintedValuesIsUnreadable() {
        assertTrue(assertInstanceOf(ParsedCard.Unreadable::class.java, parse("Hollow Expanse")).problem.contains("Plane — Nowhere"))
        assertEquals(parse("Hollow Expanse"), parse("Hollow Expanse"), "cached")
    }

    // ---- The cache

    private fun rows(): List<String> = DriverManager.getConnection("jdbc:sqlite:${storageFile.toAbsolutePath()}").use { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT definition_digest || '@' || parser_version FROM card_definition_cache ORDER BY 1").use { rows ->
                buildList { while (rows.next()) add(rows.getString(1)) }
            }
        }
    }

    @Test fun cachedDefinitionsAreReadInOneBatchWithoutParsingAgainAlsoAfterReopening() {
        val game = listOf("Thornback Sentry", "Riddle Moth", "Kindle // Rime", "Moonlit Herder // Howling Herder").map(::of)
        val first = definitions().forDefinitions(game)
        assertEquals(listOf("Thornback Sentry", "Riddle Moth", "Kindle", "Moonlit Herder"), parsed)
        parsed.clear()
        val storage = storage()
        assertEquals(first, definitions(storage).forDefinitions(game))
        storage.close()
        assertEquals(first, definitions().forDefinitions(game.reversed()))
        assertEquals(emptyList<String>(), parsed)
        assertEquals(game.map { "${it.digest}@${OracleParser.VERSION}" }.sorted(), rows())
        // Only the new card is parsed when a game needs one more.
        definitions().forDefinitions(game + of("Tidecaller's Call // Tidecaller Grotto"))
        assertEquals(listOf("Tidecaller's Call"), parsed)
    }

    @Test fun newParserVersionParsesAgainAndDropsDefinitionsOfOtherVersions() {
        val game = listOf(of("Thornback Sentry"), of("Riddle Moth"))
        val old = definitions().forDefinitions(game)
        parsed.clear()
        assertEquals(old, definitions(parserVersion = OracleParser.VERSION + 1).forDefinitions(game))
        assertEquals(listOf("Thornback Sentry", "Riddle Moth"), parsed)
        assertEquals(game.map { "${it.digest}@${OracleParser.VERSION + 1}" }.sorted(), rows())
    }

    @Test fun differentCardDataIsADifferentDigestAndParsedOnItsOwn() {
        val sentry = of("Thornback Sentry")
        definitions().forDefinitions(listOf(sentry))
        parsed.clear()
        val face = sentry.definition.faces.single()
        val erratum = sentry.definition.copy(faces = listOf(face.copy(rulesText = "Flying\nReach")))
        val result = definitions().forDefinitions(listOf(sentry, SubmittedDefinition(DeckJson.digest(erratum), erratum)))
        assertEquals(listOf("Thornback Sentry"), parsed)
        assertEquals(listOf(Keyword.Flying, Keyword.Reach), read(result[DeckJson.digest(erratum)]).definition.faces.single().abilities)
        assertEquals(listOf(Keyword.Flying, Keyword.Vigilance), read(result[sentry.digest]).definition.faces.single().abilities)
    }

    @Test fun cachedDefinitionTheModelCanNoLongerReadIsParsedAgain() {
        val expected = parse("Thornback Sentry")
        parsed.clear()
        DriverManager.getConnection("jdbc:sqlite:${storageFile.toAbsolutePath()}").use { connection ->
            connection.createStatement().use { it.execute("UPDATE card_definition_cache SET definition_json = '{\"layout\":\"unknown\"}'") }
        }
        assertEquals(expected, parse("Thornback Sentry"))
        assertEquals(listOf("Thornback Sentry"), parsed)
    }

    @Test fun noCardsNeedNoStorageWork() {
        assertEquals(emptyMap<String, ParsedCard>(), definitions().forDefinitions(emptyList()))
    }
}
