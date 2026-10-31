package org.asyncttwiz.storage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.serialization.json.Json
import org.asyncttwiz.protocol.CardSearchCard
import org.asyncttwiz.protocol.CardSearchParse
import org.asyncttwiz.protocol.CardSearchQuery
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import java.sql.DriverManager
import java.util.Locale
import java.util.UUID

/** Card search over an installed MTGJSON-shaped catalog: traits from the download, prefilter, paging and work done. */
class CatalogCardSearchTest {
    @TempDir lateinit var temporary: Path
    private val directory get() = temporary.resolve("catalog").toFile()
    private fun repo() = CatalogRepository(directory) { JdbcSqliteDriver("jdbc:sqlite:${it.absolutePath}") }
    private val classic = mapOf("legacy" to "Legal", "vintage" to "Legal", "commander" to "Legal")

    private fun id(vararg parts: Any) = UUID.nameUUIDFromBytes(parts.joinToString("|").toByteArray()).toString()
    private fun array(values: List<String>) = JsonArray().apply { values.forEach(::add) }

    private fun card(set: String, number: String, name: String, type: String, cost: String = "", colors: String = "",
        identity: String = colors, rarity: String = "common", text: String = "", layout: String = "normal",
        legal: Map<String, String> = classic, oracle: String? = id("oracle", name), power: String? = null,
        toughness: String? = null, loyalty: String? = null, language: String = "English", promo: Boolean = false,
        side: String? = null, faceName: String? = null, others: List<String> = emptyList(),
        uuid: String = id(set, number, name, side ?: "")) = JsonObject().apply {
        addProperty("uuid", uuid); addProperty("name", name); addProperty("setCode", set); addProperty("number", number)
        addProperty("type", type); addProperty("manaCost", cost); addProperty("text", text); addProperty("layout", layout)
        addProperty("rarity", rarity); addProperty("language", language); addProperty("isPromo", promo)
        add("colors", array(colors.map(Char::toString))); add("colorIdentity", array(identity.map(Char::toString)))
        add("legalities", JsonObject().apply { legal.forEach { (format, status) -> addProperty(format, status) } })
        add("availability", array(listOf("paper")))
        add("identifiers", JsonObject().apply { addProperty("scryfallId", id("scryfall", uuid)); oracle?.let { addProperty("scryfallOracleId", it) } })
        power?.let { addProperty("power", it) }; toughness?.let { addProperty("toughness", it) }; loyalty?.let { addProperty("loyalty", it) }
        side?.let { addProperty("side", it) }; faceName?.let { addProperty("faceName", it) }
        if (others.isNotEmpty()) add("otherFaceIds", array(others))
        add("foreignData", JsonArray()); addProperty("edhrecRank", 1)
    }

    /** Two linked faces sharing one card name, as MTGJSON stores split and double-faced cards. */
    private fun faces(set: String, number: String, name: String, layout: String, rarity: String, identity: String,
        legal: Map<String, String> = classic, vararg parts: JsonObject.() -> Unit): List<JsonObject> {
        val ids = listOf(id(set, number, name, "a"), id(set, number, name, "b"))
        return parts.mapIndexed { index, part ->
            card(set, number, name, "", layout = layout, rarity = rarity, identity = identity, legal = legal,
                side = "ab"[index].toString(), others = listOf(ids[1 - index]), uuid = ids[index]).apply(part)
        }
    }
    private fun JsonObject.face(faceName: String, type: String, cost: String, colors: String, text: String,
        power: String? = null, toughness: String? = null, loyalty: String? = null) {
        addProperty("faceName", faceName); addProperty("type", type); addProperty("manaCost", cost); addProperty("text", text)
        add("colors", array(colors.map(Char::toString)))
        power?.let { addProperty("power", it) }; toughness?.let { addProperty("toughness", it) }; loyalty?.let { addProperty("loyalty", it) }
    }

    private val modern = classic + mapOf("modern" to "Legal", "pauper" to "Legal")
    private fun sets(): Map<String, Pair<String, List<JsonObject>>> = linkedMapOf(
        "AAA" to ("2020-01-01" to listOf(
            card("AAA", "1", "Lightning Bolt", "Instant", "{R}", "R", text = "Lightning Bolt deals 3 damage to any target.", legal = modern),
            card("AAA", "30", "Kitchen Finks", "Creature — Ouphe", "{1}{G/W}{G/W}", "GW", rarity = "uncommon", text = "Persist", power = "3", toughness = "2"),
            card("AAA", "40", "Tarmogoyf", "Creature — Lhurgoyf", "{1}{G}", "G", rarity = "rare", power = "*", toughness = "1+*"),
            card("AAA", "60", "Æther Vial", "Artifact", "{1}", text = "At the beginning of your upkeep, you may put a charge counter on Æther Vial."),
            card("AAA", "250", "Plains", "Basic Land — Plains", identity = "W", legal = modern + ("standard" to "Legal")),
            card("AAA", "70", "Tazeem", "Plane — Zendikar", layout = "planar", text = "Creatures can't block.", legal = emptyMap()),
            card("AAA", "80", "Mystery Card", "Sorcery", "{B}", "B", oracle = null),
            card("AAA", "90", "Lightning Bolt // Lightning Bolt", "Card", layout = "art_series", oracle = id("art bolt")),
        ) + faces("AAA", "10", "Fire // Ice", "split", "uncommon", "UR", modern,
            { face("Fire", "Instant", "{1}{R}", "R", "Fire deals 2 damage divided as you choose among one or two targets.") },
            { face("Ice", "Instant", "{1}{U}", "U", "Tap target permanent.\nDraw a card.") })),
        "BBB" to ("2022-01-01" to listOf(
            card("BBB", "5", "Lightning Bolt", "Instant", "{R}", "R", rarity = "uncommon", text = "Lightning Bolt deals 3 damage to any target.", legal = modern),
            card("BBB", "31", "Birthing Pod", "Artifact", "{3}{G/P}", "G", rarity = "rare", legal = classic + ("modern" to "Banned")),
            card("BBB", "41", "Tarmogoyf", "Creature — Lhurgoyf", "{1}{G}", "G", rarity = "mythic", power = "*", toughness = "1+*"),
            card("BBB", "50", "Jace, the Mind Sculptor", "Legendary Planeswalker — Jace", "{2}{U}{U}", "U", rarity = "mythic", loyalty = "3",
                text = "+2: Look at the top card of target player's library."),
            card("BBB", "260", "Plains", "Basic Land — Plains", identity = "W", legal = modern),
            card("BBB", "261", "Plains", "Basic Land — Plains", identity = "W", legal = modern),
        ) + faces("BBB", "20", "Delver of Secrets // Insectile Aberration", "transform", "common", "U", modern,
            { face("Delver of Secrets", "Creature — Human Wizard", "{U}", "U", "Transform it.", "1", "1") },
            { face("Insectile Aberration", "Creature — Human Insect", "", "U", "Flying", "3", "2") })),
        "PRM" to ("2023-01-01" to listOf(
            card("PRM", "1", "Lightning Bolt", "Instant", "{R}", "R", rarity = "rare", promo = true, legal = modern))),
        "JPN" to ("2024-01-01" to listOf(
            card("JPN", "1", "Lightning Bolt", "Instant", "{R}", "R", rarity = "special", language = "Japanese", legal = modern))),
        "FIL" to ("2019-01-01" to (1..300).map { n ->
            card("FIL", "$n", "Filler Creature %03d".format(n), "Creature — Test", "{2}", "G", power = "${n % 10}", toughness = "1",
                legal = mapOf("commander" to "Legal"))
        }),
    )

    private fun set(code: String, value: Pair<String, List<JsonObject>>) = JsonObject().apply {
        addProperty("code", code); addProperty("name", "Set $code"); addProperty("type", "expansion")
        addProperty("releaseDate", value.first); add("cards", JsonArray().apply { value.second.forEach(::add) })
        if (code == "AAA") add("tokens", JsonArray().apply {
            add(card("AAA", "T1", "Goblin", "Token Creature — Goblin", colors = "R", layout = "token", power = "1", toughness = "1"))
        })
    }
    private fun meta() = JsonObject().apply { addProperty("date", "2026-10-31"); addProperty("version", "5.3.0") }
    private fun root(sets: Map<String, Pair<String, List<JsonObject>>> = sets()) = JsonObject().apply {
        add("meta", meta())
        add("data", JsonObject().apply { sets.forEach { (code, value) -> add(code, set(code, value)) } })
    }

    private fun install(repository: CatalogRepository = repo(), root: JsonObject = root()): CatalogRepository {
        repository.stage { index ->
            MtgJsonCatalog().import(root.toString().byteInputStream(), index, CatalogMetadata("2026-10-31", "5.3.0"))
        }
        return repository
    }

    private fun page(repository: CatalogRepository, query: String, limit: Int = 60, budget: Int = 3000,
        cursor: CatalogCardCursor? = null): CatalogCardSearch.Page {
        val result = repository.searchCards(query, cursor, limit, budget)
        assertInstanceOf(CatalogCardSearch.Page::class.java, result, "$query: $result")
        return result as CatalogCardSearch.Page
    }
    private fun all(repository: CatalogRepository, query: String, limit: Int = 60, budget: Int = 3000): Pair<List<CatalogCard>, Int> {
        val cards = mutableListOf<CatalogCard>()
        var examined = 0
        var cursor: CatalogCardCursor? = null
        do {
            val page = page(repository, query, limit, budget, cursor)
            cards += page.cards; examined += page.examined; cursor = page.next
        } while (cursor != null)
        return cards to examined
    }
    private fun names(repository: CatalogRepository, query: String) = all(repository, query).first.map { it.name }

    @Test fun searchReturnsDistinctCardsWithTheDeckImportDefaultPrintingAndCountsOnlyMatchingRows() {
        val repository = install()
        val bolt = page(repository, "bolt")
        assertEquals(listOf("Lightning Bolt"), bolt.cards.map { it.name }, "art series is not a card")
        assertEquals("BBB", bolt.cards.single().printing.set, "English, not promo, newest — as deck import")
        assertEquals(DeckImportResolver(repository).resolve("1 Lightning Bolt").entries.single().printing, bolt.cards.single().printing)
        assertEquals(1, bolt.examined)
        assertNull(bolt.next)
        assertEquals(bolt.cards.single().key, id("oracle", "Lightning Bolt"))
    }

    @Test fun printingTermsChooseTheFirstMatchingPrinting() {
        val repository = install()
        fun printing(query: String) = page(repository, query).cards.single().printing.let { "${it.set} ${it.number}" }
        assertEquals("AAA 1", printing("bolt s:aaa"))
        assertEquals("PRM 1", printing("bolt r:rare"))
        assertEquals("JPN 1", printing("bolt r:special"))
        assertEquals("AAA 1", printing("bolt -s:bbb"))
        assertEquals("BBB 5", printing("bolt r>=common"))
        assertEquals(listOf("Fire // Ice", "Kitchen Finks"), names(repository, "s:aaa r:uncommon"))
        assertEquals(emptyList<String>(), names(repository, "s:bbb r:common bolt"))
    }

    @Test fun multiFaceCardsReturnAllFacesFrontFirst() {
        val repository = install()
        val fire = page(repository, "!ice").cards.single()
        assertEquals(listOf("a", "b"), fire.faces.map { it.side })
        assertEquals("Fire // Ice", fire.name)
        val delver = page(repository, "t:insect").cards.single()
        assertEquals("Delver of Secrets // Insectile Aberration", delver.name)
        assertEquals(2, delver.faces.size)
        assertEquals(DeckImportResolver(repository).resolve("1 Delver of Secrets").entries.single().faces, delver.faces)
    }

    @Test fun traitsFromTheDownloadDriveColourIdentityFormatAndRarityFilters() {
        val repository = install()
        val rows = listOf(
            "f:modern" to listOf("Delver of Secrets // Insectile Aberration", "Fire // Ice", "Lightning Bolt", "Plains"),
            "f:standard" to listOf("Plains"),
            "-f:modern -filler" to listOf("Æther Vial", "Birthing Pod", "Jace, the Mind Sculptor", "Kitchen Finks", "Mystery Card", "Tarmogoyf"),
            "id:w -filler" to listOf("Æther Vial", "Plains"),
            "id:ur -filler" to listOf("Æther Vial", "Delver of Secrets // Insectile Aberration", "Fire // Ice", "Jace, the Mind Sculptor", "Lightning Bolt"),
            "id:m" to listOf("Fire // Ice", "Kitchen Finks"),
            "id:b" to listOf("Æther Vial", "Mystery Card"),
            "r:mythic" to listOf("Jace, the Mind Sculptor", "Tarmogoyf"),
            "c:m" to listOf("Fire // Ice", "Kitchen Finks"),
            "c:c" to listOf("Æther Vial", "Plains"),
            "c=r" to listOf("Lightning Bolt"),
            "mv=4" to listOf("Birthing Pod", "Fire // Ice", "Jace, the Mind Sculptor"),
            "mv=3 -filler" to listOf("Kitchen Finks"),
            "pow>tou -filler" to listOf("Delver of Secrets // Insectile Aberration", "Kitchen Finks"),
            "pow=0 -filler" to listOf("Tarmogoyf"),
            "loy=3" to listOf("Jace, the Mind Sculptor"),
            "o:\"~ deals\"" to listOf("Fire // Ice", "Lightning Bolt"),
            "is:split or is:dfc" to listOf("Delver of Secrets // Insectile Aberration", "Fire // Ice"),
            "aether" to listOf("Æther Vial"),
            "goblin" to emptyList(),
            "tazeem" to emptyList(),
            "tazeem include:extras" to listOf("Tazeem"),
            "mystery" to listOf("Mystery Card"),
        )
        val failures = rows.mapNotNull { (query, expected) ->
            names(repository, query).takeIf { it != expected }?.let { "$query: expected $expected, got $it" }
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    @Test fun indexableTermsAreFilteredInSqlSoOnlyMatchingCardsAreExamined() {
        val repository = install()
        for (query in listOf("bolt", "t:planeswalker", "o:flying", "c=r", "c:m", "id<=gu -filler", "mv=4", "f:modern", "is:split",
            "-t:creature -t:land", "f:modern or t:planeswalker", "filler", "\"filler creature 01\"", "c=g mv=2 t:test")) {
            val page = page(repository, query, limit = 200)
            // A full page also examines one more matching row to know whether another page exists.
            assertEquals(page.cards.size + if (page.next != null) 1 else 0, page.examined, query)
        }
        // Printing-level and per-face terms are prefiltered where possible, then decided by the shared matcher.
        assertEquals(1 to 1, all(repository, "s:prm").let { it.first.size to it.second })
        assertEquals(2 to 3, all(repository, "s:aaa r:uncommon").let { it.first.size to it.second }, "Bolt is AAA common and BBB uncommon")
        assertEquals(30 to 30, all(repository, "pow=7").let { it.first.size to it.second }, "per-face power range column")
        assertEquals(2 to 10, all(repository, "pow>tou -filler").let { it.first.size to it.second }, "stat-to-stat comparisons are left to the matcher")
    }

    @Test fun pagesAreSortedByAccentFoldedNameAndResumeFromTheCursor() {
        val repository = install()
        val first = page(repository, "", limit = 5)
        assertEquals(listOf("Æther Vial", "Birthing Pod", "Delver of Secrets // Insectile Aberration", "Filler Creature 001", "Filler Creature 002"),
            first.cards.map { it.name })
        assertEquals(6, first.examined, "one extra row tells whether another page exists")
        val (cards, _) = all(repository, "", limit = 37)
        val names = cards.map { it.name }
        assertEquals(310, names.size)
        assertEquals(names.distinct(), names)
        assertEquals(names.sortedBy { org.asyncttwiz.protocol.CardSearchRules.foldName(it) }, names)
        val fillers = all(repository, "filler", limit = 50)
        assertEquals((1..300).map { "Filler Creature %03d".format(it) }, fillers.first.map { it.name })
        assertTrue(fillers.second <= 300 + 6)
    }

    @Test fun workBudgetBoundsEachCallAndPagingStillFindsEveryMatch() {
        val repository = install()
        val first = page(repository, "pow>tou filler", limit = 60, budget = 50)
        assertEquals(50, first.examined)
        assertTrue(first.cards.size < 60)
        assertNotNull(first.next)
        val (cards, examined) = all(repository, "pow>tou filler", limit = 60, budget = 50)
        assertEquals((1..300).filter { it % 10 >= 2 }.map { "Filler Creature %03d".format(it) }, cards.map { it.name })
        assertEquals(300, examined)
    }

    @Test fun storageResultsEqualTheSharedMatcherOverEveryCard() {
        val repository = install()
        val file = File(directory, File(directory, "current").readText())
        val every = DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}").use { connection ->
            connection.createStatement().executeQuery("SELECT c.name_key, d.data FROM search_card c JOIN search_data d ON d.oracle = c.oracle ORDER BY c.name_key, c.oracle").use { rows ->
                buildList { while (rows.next()) add(Json.decodeFromString(CardSearchCard.serializer(), rows.getString(2))) }
            }
        }
        val leaves = listOf("bolt", "fire", "t:creature", "t:instant", "o:draw", "o:\"~ deals\"", "c:r", "c=g", "c<=u", "c:m", "c:c", "c>=2",
            "id:gw", "id=u", "id>=w", "mv=1", "mv<=2", "mv>3", "mv!=2", "pow>=3", "tou<2", "pow>tou", "loy>0", "r:common", "r>=rare",
            "s:aaa", "s:bbb", "f:modern", "f:pauper", "is:split", "is:dfc", "is:transform", "!plains", "include:extras")
        val random = java.util.Random(11)
        val queries = leaves + leaves.flatMap { a -> leaves.map { b -> "$a -$b" } } + (1..400).map {
            fun leaf() = (if (random.nextInt(3) == 0) "-" else "") + leaves[random.nextInt(leaves.size)]
            when (random.nextInt(4)) {
                0 -> "${leaf()} or ${leaf()}"
                1 -> "-(${leaf()} or ${leaf()}) ${leaf()}"
                2 -> "(${leaf()} ${leaf()}) or -${leaves[random.nextInt(leaves.size)]}"
                else -> "${leaf()} ${leaf()} ${leaf()}"
            }
        }
        for (query in queries) {
            val parsed = (CardSearchQuery.parse(query) as CardSearchParse.Valid).query
            val expected = every.filter { parsed.matches(it) }.map { it.name }
            assertEquals(expected, all(repository, query, limit = 200).first.map { it.name }, query)
        }
    }

    @Test fun invalidQueriesMissingCatalogsAndOldIndexesAreReportedNotThrownAsCrashes() {
        val missing = repo()
        assertThrows<CatalogNotInstalledException> { missing.searchCards("bolt") }
        val invalid = missing.searchCards("t:")
        assertEquals("Write a value after t:", (invalid as CatalogCardSearch.Invalid).error.message)
        val repository = install()
        val first = page(repository, "filler", limit = 10)
        assertThrows<IllegalArgumentException> { repository.searchCards("bolt", first.next) }
        val file = File(directory, File(directory, "current").readText())
        DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}").use { it.createStatement().execute("DROP TABLE search_ready") }
        assertThrows<CatalogSearchNotReadyException> { repository.searchCards("bolt") }
        install(repository)
        assertThrows<CatalogGenerationChangedException> { repository.searchCards("filler", first.next) }
        assertEquals(10, page(repository, "filler", limit = 10).cards.size)
    }

    @Test fun downloadTraitsAreKeptBesideTheRecordNotInIt() {
        val repository = install()
        val bolt = page(repository, "bolt").cards.single().printing
        val stored = com.google.gson.JsonParser.parseString(bolt.json).asJsonObject
        assertFalse(stored.has("rarity")); assertFalse(stored.has("legalities")); assertFalse(stored.has("colorIdentity"))
        val file = File(directory, File(directory, "current").readText())
        DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}").use { connection ->
            connection.createStatement().executeQuery("SELECT rarity, identity, legal, oracle FROM printing WHERE id='${bolt.id}'").use { rows ->
                assertTrue(rows.next())
                assertEquals("uncommon", rows.getString(1))
                assertEquals(8, rows.getInt(2))
                val legal = rows.getLong(3)
                assertEquals(setOf("modern", "legacy", "vintage", "commander", "pauper"),
                    org.asyncttwiz.protocol.CardSearchRules.FORMATS.filterIndexed { bit, _ -> legal and (1L shl bit) != 0L }.toSet())
                assertEquals(id("oracle", "Lightning Bolt"), rows.getString(4))
            }
            assertEquals(3, connection.createStatement().executeQuery("PRAGMA user_version").use { it.next(); it.getInt(1) })
        }
    }

    @Test fun malformedOptionalTraitsAreUnknownNotRejectedCards() {
        val sets = sets()
        val odd = card("AAA", "999", "Odd Card", "Creature", "{G}", "G").apply {
            addProperty("rarity", 5); addProperty("legalities", "unexpected"); addProperty("colorIdentity", "G")
        }
        val repository = install(root = root(sets + ("ODD" to ("2021-01-01" to listOf(odd.apply { addProperty("setCode", "ODD") })))))
        val found = page(repository, "odd card").cards.single()
        assertEquals("Odd Card", found.name)
        assertEquals(listOf("Odd Card"), names(repository, "odd id:g -f:commander"))
    }

    @Test fun incrementalSetRefreshRebuildsSearchOnTheNewGeneration() {
        val repository = install()
        repository.stage(copyInstalled = true) { index -> index.removeSet("BBB"); index.metadata() }
        assertEquals("AAA", page(repository, "bolt").cards.single().printing.set)
        assertEquals(emptyList<String>(), names(repository, "jace"))
        assertEquals(emptyList<String>(), names(repository, "s:bbb"))
        val sets = sets()
        repository.stage(copyInstalled = true) { index ->
            val bbb = JsonObject().apply { add("meta", meta()); add("data", set("BBB", sets.getValue("BBB"))) }
            MtgJsonCatalog().import(bbb.toString().byteInputStream(), index, CatalogMetadata("2026-10-31", "5.3.0"), "BBB")
        }
        assertEquals("BBB", page(repository, "bolt").cards.single().printing.set)
        assertEquals(listOf("Jace, the Mind Sculptor"), names(repository, "jace r:mythic"))
    }

    @Test fun lowerCaseHelpersStayLocaleIndependent() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr"))
            val repository = install()
            assertEquals(listOf("Lightning Bolt"), names(repository, "LIGHTNING"))
            assertEquals(listOf("Fire // Ice"), names(repository, "is:SPLIT"))
        } finally { Locale.setDefault(previous) }
    }
}
