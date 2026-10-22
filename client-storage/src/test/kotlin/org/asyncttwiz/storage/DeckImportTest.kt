package org.asyncttwiz.storage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class DeckImportTest {
    @TempDir lateinit var temporary: Path
    private fun repository(family: String): CatalogRepository {
        val repository = CatalogRepository(temporary.resolve(family).toFile()) { JdbcSqliteDriver("jdbc:sqlite:${it.absolutePath}") }
        val edition = File(System.getProperty("catalog.fixtures"), "$family/v1")
        val metadata = File(edition, "Meta.json").inputStream().use { MtgJsonCatalog().metadata(it) }
        repository.stage { index -> File(edition, "AllPrintings.json").inputStream().use { MtgJsonCatalog().import(it, index, metadata) } }
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
            assertEquals(multi, com.google.gson.JsonParser.parseString(faces.first().json))
            assertEquals(regular, com.google.gson.JsonParser.parseString(result.entries.first().printing.json))
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
        val cases = listOf("1 No Such Card", "1 Coppercloud Echo", "1 Coppercloud Surveyor", "1 Velvet Prism Engine (ZPA) 999",
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
        assertTrue(resolver.resolve("2147483647 Quiet Lattice Bolt").resolved) // resource representation, not deck legality
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
}
