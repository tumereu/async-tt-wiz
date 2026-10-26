package org.asyncttwiz.storage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import java.sql.DriverManager
import java.util.UUID

class DeckImportTest {
    @Test fun oversizedImportsProduceOrdinaryLineIssuesWithoutAPartialPreview() {
        val resolver = DeckImportResolver(repository("prism"))
        for ((text, line) in listOf("2147483647 Quiet Lattice Bolt" to 1,
            "1001 Quiet Lattice Bolt" to 1,
            "600 Quiet Lattice Bolt\nSideboard\n401 Quiet Lattice Bolt" to 3,
            "600 Quiet Lattice Bolt\n401 Quiet Lattice Bolt" to 2)) {
            val result = resolver.resolve(text)
            assertFalse(result.resolved, text)
            assertTrue(result.entries.isEmpty())
            assertEquals(line, result.issues.first().line)
        }
        assertTrue(resolver.resolve("600 Quiet Lattice Bolt\nSideboard\n400 Quiet Lattice Bolt").resolved)
    }

    @TempDir lateinit var temporary: Path
    private fun repository(family: String): CatalogRepository {
        val repository = CatalogRepository(temporary.resolve(family).toFile()) { JdbcSqliteDriver("jdbc:sqlite:${it.absolutePath}") }
        val edition = File(System.getProperty("catalog.fixtures"), "$family/v1")
        val metadata = File(edition, "Meta.json").inputStream().use { MtgJsonCatalog().metadata(it) }
        repository.stage { index -> File(edition, "AllPrintings.json").inputStream().use { MtgJsonCatalog().import(it, index, metadata) } }
        return repository
    }
    private fun card(id: Int, number: String = "$id", oracle: String? = "same-card", name: String = "Default Card"): JsonObject =
        JsonObject().apply {
            addProperty("uuid", UUID.nameUUIDFromBytes("printing-$id".toByteArray()).toString())
            addProperty("name", name); addProperty("number", number); addProperty("language", "English")
            addProperty("isOnlineOnly", false)
            add("availability", JsonArray().apply { add("paper") })
            if (oracle != null) add("identifiers", JsonObject().apply {
                addProperty("scryfallOracleId", UUID.nameUUIDFromBytes(oracle.toByteArray()).toString())
            })
        }
    private fun install(vararg sets: Triple<String, String?, List<JsonObject>>, tokens: List<JsonObject> = emptyList()): CatalogRepository {
        val root = JsonObject().apply {
            add("meta", JsonObject().apply { addProperty("date", "2026-10-23"); addProperty("version", "5.2.2") })
            add("data", JsonObject().apply {
                sets.forEach { (code, date, cards) -> add(code, JsonObject().apply {
                    // Dates intentionally follow cards, as in the fixture and real sorted-key data.
                    add("cards", JsonArray().apply { cards.forEach { add(it) } })
                    add("tokens", JsonArray().apply { tokens.forEach { add(it) } })
                    date?.let { addProperty("releaseDate", it) }
                }) }
            })
        }
        val repository = CatalogRepository(temporary.resolve("custom").toFile()) { JdbcSqliteDriver("jdbc:sqlite:${it.absolutePath}") }
        repository.stage { index -> root.toString().byteInputStream().use {
            MtgJsonCatalog().import(it, index, CatalogMetadata("2026-10-23", "5.2.2"))
        } }
        return repository
    }
    @Test fun commonSyntaxPreservesOriginalTextAndExplicitSectionsIgnoreBlankSpacing() {
        val text = "\r\nDeck\r\n4x Card  With Spaces (set) 007\r\n\r\n2 Other Card\r\nSideboard\r\n1 Side Card\r\n"
        val parsed = DeckTextParser.parse(text)
        assertEquals(text, parsed.text)
        assertEquals(emptyList<ImportIssue>(), parsed.issues)
        assertEquals(listOf(3, 5, 7), parsed.lines.map { it.line })
        assertEquals(listOf(DeckSection.MAINBOARD, DeckSection.MAINBOARD, DeckSection.SIDEBOARD), parsed.lines.map { it.section })
        assertEquals("Card  With Spaces", parsed.lines.first().name)
        assertEquals("SET", parsed.lines.first().set)
        assertEquals("007", parsed.lines.first().number)
        val headerless = DeckTextParser.parse("\n2 Main\n1 Other\n\n1 Side\nSB: 2 More Side")
        assertEquals(listOf(DeckSection.MAINBOARD, DeckSection.MAINBOARD, DeckSection.SIDEBOARD, DeckSection.SIDEBOARD), headerless.lines.map { it.section })
        assertEquals(DeckSection.MAINBOARD, DeckTextParser.parse("SB: 1 Side\n2 Main").lines.last().section)
    }
    @Test fun bothRealFileBackedFamiliesResolveExactPrintingFacesDefaultsAndDuplicateCounts() {
        for ((family, set) in listOf("prism" to "ZPA", "quilt" to "ZQA")) {
            val repository = repository(family)
            // Derive identity-distinct source names, never hard-code provider UUIDs in the resolver.
            val edition = com.google.gson.JsonParser.parseString(File(System.getProperty("catalog.fixtures"), "$family/v1/AllPrintings.json").readText()).asJsonObject
            val cards = edition.getAsJsonObject("data").entrySet().first().value.asJsonObject.getAsJsonArray("cards")
            val regular = cards.first { it.asJsonObject.get("number").asString == "2" }.asJsonObject
            val multi = cards.first { it.asJsonObject.has("otherFaceIds") }.asJsonObject
            val name = regular.get("name").asString
            val faceName = multi.get("name").asString
            val text = "Deck\n2 $name\n3 $name ($set) 2\n1 $faceName ($set) 12\n\nSideboard\n7 $name"
            val result = DeckImportResolver(repository).resolve(text)
            assertTrue(result.resolved, result.issues.toString())
            assertEquals(text, result.text)
            assertEquals(6L, result.count(DeckSection.MAINBOARD)); assertEquals(7L, result.count(DeckSection.SIDEBOARD))
            assertEquals(listOf(2, 3), result.entries.first().sourceLines)
            val faces = result.entries[1].faces
            assertEquals(listOf("a", "b"), faces.map { it.side })
            assertTrue(faces.all { it.otherFaces.single() == faces.first { other -> other.id != it.id }.id })
            assertEquals(MtgJsonCatalog.retainedRecord(multi), com.google.gson.JsonParser.parseString(faces.first().json))
            assertEquals(MtgJsonCatalog.retainedRecord(regular), com.google.gson.JsonParser.parseString(result.entries.first().printing.json))
            println("PASS $family actual file-backed name/printing, complete faces, unchanged raw/default metadata, duplicate arithmetic and sections")
        }
    }
    @Test fun allExistingImportFixturesInBothFamiliesKeepTheirExpectedOutcomes() {
        for (family in listOf("prism", "quilt")) {
            val resolver = DeckImportResolver(repository(family))
            val cases = com.google.gson.JsonParser.parseString(File(System.getProperty("catalog.fixtures"), "$family/v1/import-cases.json").readText()).asJsonArray
            cases.forEach { element ->
                val case = element.asJsonObject
                val result = resolver.resolve(case.get("text").asString)
                if (case.get("expected").asString == "resolved") {
                    assertTrue(result.resolved, case.toString() + result.issues)
                    case.get("mainQuantity")?.let { assertEquals(it.asLong, result.count(DeckSection.MAINBOARD)) }
                    case.get("sideQuantity")?.let { assertEquals(it.asLong, result.count(DeckSection.SIDEBOARD)) }
                    case.get("printing")?.let { assertEquals(it.asString, result.entries.single().printing.id) }
                } else { assertFalse(result.resolved); assertTrue(result.entries.isEmpty()) }
            }
        }
    }
    @Test fun unknownAmbiguousMismatchMalformedUnsupportedAndOverflowBlockWholePreview() {
        val resolver = DeckImportResolver(repository("prism"))
        val cases = listOf("1 No Such Card", "1 Coppercloud Echo", "1 Coppercloud Surveyor (UNKNOWN) 1", "1 Velvet Prism Engine (ZPA) 999",
            "0 Velvet Prism Engine", "-1 Velvet Prism Engine", "1.5 Velvet Prism Engine", "999999999999 Velvet Prism Engine",
            "Commander\n1 Velvet Prism Engine", "Companion\n1 Velvet Prism Engine", "1 Velvet Prism Engine\n2147483647 Velvet Prism Engine")
        cases.forEach { bad ->
            val text = "Deck\n2 Quiet Lattice Bolt\n$bad"
            val result = resolver.resolve(text)
            assertFalse(result.resolved, bad); assertTrue(result.entries.isEmpty(), bad)
            assertEquals(3, result.issues.first().line + if (bad.startsWith("1 Velvet Prism Engine\n")) -1 else 0, bad)
            assertEquals(text, result.text)
        }
        assertTrue(resolver.resolve("1 Coppercloud Echo (zpa) 10").resolved)
        assertTrue(resolver.resolve("1000 Quiet Lattice Bolt").resolved) // resource budget, not deck legality
        assertTrue(resolver.resolve("1 Coppercloud Echo!").resolved) // no punctuation/fuzzy substitution
        val exactSpaces = resolver.resolve("1 Velvet  Prism Engine")
        assertFalse(exactSpaces.resolved)
    }
    @Test fun absentCatalogAndEmptyInputAreActionableAndOptionalDefaultsDoNotBlock() {
        val empty = CatalogRepository(temporary.resolve("empty").toFile()) { JdbcSqliteDriver("jdbc:sqlite:${it.absolutePath}") }
        assertTrue(DeckImportResolver(empty).resolve("1 Anything").issues.single().message.contains("Download"))
        val repository = repository("prism")
        assertFalse(DeckImportResolver(repository).resolve("\nDeck\n").resolved)
        val missing = DeckImportResolver(repository).resolve("1 Mauve Window Expanse")
        assertTrue(missing.resolved, missing.issues.toString())
        val json = com.google.gson.JsonParser.parseString(missing.entries.single().printing.json).asJsonObject
        assertFalse(json.has("power")); assertFalse(json.has("toughness"))
    }

    @Test fun nameOnlyMultiplePrintingsResolveAndExactSuffixStillOverridesDefaultInBothFamilies() {
        for (family in listOf("prism", "quilt")) {
            val repository = repository(family)
            val root = JsonParser.parseString(File(System.getProperty("catalog.fixtures"), "$family/v1/AllPrintings.json").readText()).asJsonObject
            val sets = root.getAsJsonObject("data").entrySet().toList()
            val first = sets.first().value.asJsonObject.getAsJsonArray("cards").first().asJsonObject
            val second = sets.last().value.asJsonObject.getAsJsonArray("cards").first().asJsonObject
            val name = first.get("name").asString
            val result = DeckImportResolver(repository).resolve("20 $name\n2 $name (${sets.last().key}) ${second.get("number").asString}")
            assertTrue(result.resolved, result.issues.toString())
            assertEquals(listOf(first.get("uuid").asString, second.get("uuid").asString), result.entries.map { it.printing.id })
            assertEquals(listOf(20, 2), result.entries.map { it.quantity })
            assertEquals("2026-10-01", result.entries.first().printing.releaseDate)
        }
    }

    @Test fun earlierPreferencesOutrankLaterPreferences() {
        val englishPromoOnline = card(1).apply { addProperty("isPromo", true); addProperty("isOnlineOnly", true) }
        val foreignNonPromoPaper = card(2).apply { addProperty("language", "French") }
        val englishNonPromoOnline = card(3).apply { addProperty("isOnlineOnly", true) }
        val englishPromoPaper = card(4).apply { addProperty("isPromo", true) }
        for ((winner, loser) in listOf(englishPromoOnline to foreignNonPromoPaper, englishNonPromoOnline to englishPromoPaper)) {
            val repository = install(Triple("OLD", "2025-01-01", listOf(winner)), Triple("NEW", "2026-01-01", listOf(loser)))
            assertEquals(winner.get("uuid").asString, DeckImportResolver(repository).resolve("1 Default Card").entries.single().printing.id)
        }
    }

    @Test fun printingPreferenceIsEnglishThenNonPromoThenPaperThenNewestSetThenNaturalCollectorNumber() {
        val preferred = card(1, "2a")
        val alternatives = listOf(
            "2030-01-01" to card(2, "1").apply { addProperty("language", "French") },
            "2030-01-01" to card(3, "1").apply { addProperty("isPromo", true) },
            "2030-01-01" to card(4, "1").apply { addProperty("isOnlineOnly", true) },
            "2030-01-01" to card(5, "1").apply { add("availability", JsonArray().apply { add("mtgo") }) },
            "2025-01-01" to card(6, "1"),
            "2026-01-01" to card(7, "10"),
            "2026-01-01" to card(8, "2b"),
        )
        for ((date, alternative) in alternatives) {
            val repository = install(Triple("OLD", date, listOf(alternative)), Triple("NEW", "2026-01-01", listOf(preferred)))
            val result = DeckImportResolver(repository).resolve("4 Default Card")
            assertTrue(result.resolved, result.issues.toString())
            assertEquals(preferred.get("uuid").asString, result.entries.single().printing.id, alternative.toString())
            assertEquals("2026-01-01", repository.printing(preferred.get("uuid").asString)!!.releaseDate)
            val exact = DeckImportResolver(repository).resolve("1 Default Card (OLD) ${alternative.get("number").asString}")
            assertEquals(alternative.get("uuid").asString, exact.entries.single().printing.id)
        }
    }

    @Test fun unavailablePreferencesAndMissingOptionalMetadataStillResolveWithStableTies() {
        val first = card(1, "7", oracle = null).apply { remove("language"); remove("availability"); remove("isOnlineOnly") }
        val second = card(2, "7", oracle = null).apply { remove("language"); remove("availability"); remove("isOnlineOnly") }
        val a = install(Triple("SET", null, listOf(first, second)))
        val expected = DeckImportResolver(a).resolve("1 Default Card").entries.single().printing
        val b = install(Triple("SET", null, listOf(second, first)))
        assertEquals(expected, DeckImportResolver(b).resolve("1 Default Card").entries.single().printing)
        val foreign = card(3).apply { addProperty("language", "French"); addProperty("isPromo", true); addProperty("isOnlineOnly", true) }
        assertTrue(DeckImportResolver(install(Triple("SET", null, listOf(foreign)))).resolve("1 Default Card").resolved)
    }

    @Test fun oracleConflictsAndSharedFaceNamesRemainAmbiguousButReprintedFacesDoNot() {
        fun faces(id: Int, name: String, oracle: String? = null): List<JsonObject> {
            val front = card(id, "1", oracle, name)
            val back = card(id + 1, "1", oracle, name)
            front.addProperty("faceName", "Shared Front"); front.addProperty("side", "a")
            back.addProperty("faceName", name.substringAfter(" // ")); back.addProperty("side", "b")
            front.add("otherFaceIds", JsonArray().apply { add(back.get("uuid")) })
            back.add("otherFaceIds", JsonArray().apply { add(front.get("uuid")) })
            return listOf(front, back)
        }
        val first = faces(1, "Shared Front // First Back")
        val reprint = faces(3, "Shared Front // First Back")
        val repository = install(Triple("OLD", "2025-01-01", first), Triple("NEW", "2026-01-01", reprint))
        for (name in listOf("Shared Front", "First Back", "Shared Front // First Back")) {
            val result = DeckImportResolver(repository).resolve("1 $name")
            assertTrue(result.resolved, result.issues.toString())
            assertEquals(reprint.map { it.get("uuid").asString }, result.entries.single().faces.map { it.id })
        }
        val different = install(Triple("SET", null, first + faces(5, "Shared Front // Different Back")))
        assertTrue(DeckImportResolver(different).resolve("1 Shared Front").issues.single().message.contains("ambiguous"))
        val conflicting = install(Triple("SET", null, listOf(card(1, oracle = "one"), card(2, oracle = "two"))))
        assertTrue(DeckImportResolver(conflicting).resolve("1 Default Card").issues.single().message.contains("ambiguous"))
    }

    @Test fun moreThanOneLookupPageDoesNotHidePrintingsOrGenuineAmbiguity() {
        val cards = (1..1001).map { card(it) }
        val repository = install(Triple("SET", "2026-01-01", cards))
        assertEquals("1", DeckImportResolver(repository).resolve("1 Default Card").entries.single().printing.number)
        val different = card(1002, oracle = "different").apply { addProperty("uuid", "ffffffff-ffff-4fff-8fff-ffffffffffff") }
        val ambiguous = install(Triple("SET", "2026-01-01", cards + different))
        assertTrue(DeckImportResolver(ambiguous).resolve("1 Default Card").issues.single().message.contains("ambiguous"))
    }

    @Test fun playableFullAndFrontNamesOutrankTokenArtAndUnrelatedBackFaceAliases() {
        fun faces(id: Int, name: String, oracle: String): List<JsonObject> {
            val pair = listOf(card(id, "$id", oracle, name), card(id + 1, "$id", oracle, name))
            pair.forEachIndexed { index, raw ->
                raw.addProperty("faceName", name.split(" // ")[index])
                raw.addProperty("side", if (index == 0) "a" else "b")
                raw.add("otherFaceIds", JsonArray().apply { add(pair[1 - index].get("uuid")) })
            }
            return pair
        }
        val bolt = card(1, name = "Lightning Bolt", oracle = "bolt")
        val island = card(2, name = "Island", oracle = "island")
        val spell = faces(3, "Emeritus of Conflict // Lightning Bolt", "emeritus")
        val reversible = faces(5, "Island // Island", "island")
        val art = faces(7, "Island // Island", "art-island") + faces(9, "Lightning Bolt // Lightning Bolt", "art-bolt")
        val repository = install(Triple("SET", "2026-01-01", listOf(bolt, island) + spell + reversible), tokens = art)
        val result = DeckImportResolver(repository).resolve("4 Lightning Bolt\n4 Island\n1 Emeritus of Conflict")
        assertTrue(result.resolved, result.issues.toString())
        assertEquals(listOf(bolt.get("uuid").asString, island.get("uuid").asString, spell.first().get("uuid").asString),
            result.entries.map { it.printing.id })
        assertTrue(result.entries.flatMap { it.faces }.none { it.token })
        val exactBack = DeckImportResolver(repository).resolve("1 Lightning Bolt (SET) 3")
        assertEquals(spell.map { it.get("uuid").asString }, exactBack.entries.single().faces.map { it.id })
    }

    @Test fun oldPublicIndexStaysReadableAndRequestsRefreshForReleaseDates() {
        val repository = repository("prism")
        val directory = temporary.resolve("prism").toFile()
        val file = File(directory, File(directory, "current").readText())
        DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}").use { connection ->
            connection.createStatement().use {
                it.execute("DROP TABLE catalog_set"); it.execute("DROP TABLE import_name"); it.execute("PRAGMA user_version=0")
            }
        }
        val metadata = repository.metadata()!!
        assertEquals(0, metadata.indexVersion)
        assertTrue(CatalogUpdatePolicy.changed(metadata, metadata.copy(indexVersion = CATALOG_INDEX_VERSION)))
        val result = DeckImportResolver(repository).resolve("20 Coppercloud Surveyor\n1 Dayglass Loom")
        assertTrue(result.resolved, result.issues.toString())
        assertEquals(2, result.entries.last().faces.size)
        assertEquals("", result.entries.first().printing.releaseDate)
        assertEquals(metadata, repository.metadata())
    }
}
