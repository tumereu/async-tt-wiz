package org.asyncttwiz.storage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class CubeImportTest {
    @TempDir lateinit var temporary: Path
    private fun resolver(): CubeImportResolver {
        val repository = CatalogRepository(temporary.resolve("catalog").toFile()) { JdbcSqliteDriver("jdbc:sqlite:${it.absolutePath}") }
        val edition = File(System.getProperty("catalog.fixtures"), "prism/v1")
        val metadata = File(edition, "Meta.json").inputStream().use { MtgJsonCatalog().metadata(it) }
        repository.stage { index -> File(edition, "AllPrintings.json").inputStream().use { MtgJsonCatalog().import(it, index, metadata) } }
        return CubeImportResolver(repository)
    }
    // DRF-003, DRF-007: a pasted cube's sideboard lines go to Maybe, and Maybe counts toward the 3000-copy limit.
    @Test fun sideboardLinesGoToMaybeAndCountTowardTheLimit() {
        val resolver = resolver()
        val text = "Deck\n2000 Quiet Lattice Bolt\nSideboard\n1000 Velvet Prism Engine"
        val both = resolver.resolve(text)
        assertTrue(both.resolved, both.issues.toString())
        assertEquals(2000L, both.count(DeckSection.MAINBOARD))
        assertEquals(1000L, both.count(DeckSection.MAYBEBOARD))
        assertEquals(0L, both.count(DeckSection.SIDEBOARD))
        assertFalse(resolver.resolve("$text\nSB: 1 Quiet Lattice Bolt").resolved)
        assertFalse(resolver.resolve("3001 Quiet Lattice Bolt").resolved)
        // The same card may be in Main and Maybe.
        val split = resolver.resolve("2 Quiet Lattice Bolt\nMaybeboard\n1 Quiet Lattice Bolt")
        assertEquals(listOf(DeckSection.MAINBOARD to 2, DeckSection.MAYBEBOARD to 1), split.entries.map { it.section to it.quantity })
        val blocks = resolver.resolve("1 Quiet Lattice Bolt\n\n1 Velvet Prism Engine")
        assertEquals(1L, blocks.count(DeckSection.MAYBEBOARD))
        assertFalse(resolver.resolve("1 Quiet Lattice Bolt\nSB: not a card").resolved)
    }
    // DRF-006
    @Test fun missingPrintingIsReportedButUnknownCardsBlockWithoutPartialEntries() {
        val resolver = resolver()
        val result = resolver.resolve("2 Quiet Lattice Bolt (UNKNOWN) 999")
        assertTrue(result.resolved, result.issues.toString())
        assertEquals(2L, result.count(DeckSection.MAINBOARD))
        assertEquals(1, result.substitutions.single().line)
        assertTrue(result.substitutions.single().message.contains("UNKNOWN"))
        val bad = resolver.resolve("1 Quiet Lattice Bolt\n2 Definitely Unknown (ZZZ) 1")
        assertFalse(bad.resolved)
        assertTrue(bad.entries.isEmpty())
        assertEquals(2, bad.issues.single().line)
        assertTrue(bad.issues.single().message.contains("Definitely Unknown"))
    }
    // DRF-009
    @Test fun cubeCobraFixtureConvertsMainboardAndMaybeboardAndPreservesRequestedPrinting() {
        val fixture = javaClass.getResource("/cubecobra.json")!!.readText()
        val parsed = CubeCobraImport.parse(fixture)
        assertTrue(parsed.issues.isEmpty())
        assertEquals(listOf(1, 2, 1), parsed.lines.map { it.quantity })
        assertEquals(listOf(DeckSection.MAINBOARD, DeckSection.MAINBOARD, DeckSection.MAYBEBOARD), parsed.lines.map { it.section })
        // Its sideboard (Cube Cobra cubes have none in use) is ignored; the text puts Maybe under a header.
        assertEquals(listOf("Quiet Lattice Bolt", "Velvet Prism Engine", "Not in the mainboard"), parsed.lines.map { it.name })
        assertEquals("1 Quiet Lattice Bolt (ZPA) 2\n2 Velvet Prism Engine\nMaybeboard\n1 Not in the mainboard", parsed.text)
        assertEquals(listOf(1, 2, 4), parsed.lines.map { it.line })
        assertEquals(parsed.lines.map { it.section to it.quantity }, CubeImportResolver.parse(parsed.text).lines.map { it.section to it.quantity })
        assertEquals("00000000-0000-4000-8000-000000000099", parsed.lines.first().scryfallId)
        assertEquals("ZPA", parsed.lines.first().set)
        val mainOnly = CubeCobraImport.parse(fixture.replace("Not in the mainboard", "Quiet Lattice Bolt"))
        val result = resolver().resolve(mainOnly)
        assertTrue(result.resolved, result.issues.toString())
        assertEquals(3L, result.count(DeckSection.MAINBOARD))
        assertEquals(1L, result.count(DeckSection.MAYBEBOARD))
        assertFalse(resolver().resolve(parsed).resolved) // An unknown Maybe card blocks saving like any other.
        assertEquals(1, result.substitutions.size)
        assertEquals("synergy", CubeCobraImport.cubeId("https://cubecobra.com/cube/list/synergy?view=table"))
        assertEquals("synergy", CubeCobraImport.cubeId("synergy"))
        assertThrows(IllegalArgumentException::class.java) { CubeCobraImport.cubeId("https://evil.invalid/cube/list/synergy") }
        val broken = CubeCobraImport.parse("""{"cards":{"mainboard":[{"details":{"name":"Quiet Lattice Bolt"}},{"details":{}}]}}""")
        assertEquals(2, broken.issues.single().line)
        assertFalse(resolver().resolve(broken).resolved)
        // Cube Cobra's basics are a separate list and never join the cube.
        val basics = CubeCobraImport.parse("""{"basics":["00000000-0000-4000-8000-000000000001"],"cards":{"mainboard":[{"details":{"name":"Quiet Lattice Bolt"}}],"maybeboard":[]}}""")
        assertEquals(listOf("Quiet Lattice Bolt"), basics.lines.map { it.name }); assertEquals("1 Quiet Lattice Bolt", basics.text)
    }
    @Test fun cubeCobraScryfallIdentityWinsOverConflictingPrintingMetadata() {
        val parsed = CubeCobraImport.parse("""{"cards":{"mainboard":[{"cardID":"f76be976-d1a3-58fb-9b57-abdf2c982a94","details":{"name":"Coppercloud Surveyor","set":"zpa","collector_number":"1"}}]}}""")
        val result = resolver().resolve(parsed)
        assertTrue(result.resolved, result.issues.toString())
        assertEquals("ZPB", result.entries.single().printing.set)
        assertEquals("101", result.entries.single().printing.number)
        assertTrue(result.substitutions.isEmpty())
    }
    // DRF-008
    @Test fun cubeCobraLinksAreFoundAnywhereInPastedOrSharedText() {
        for (input in listOf("https://cubecobra.com/cube/list/synergy", "http://cubecobra.com/cube/list/synergy",
            "cubecobra.com/cube/list/synergy", "www.cubecobra.com/cube/overview/synergy", "HTTPS://WWW.CubeCobra.com/cube/list/synergy",
            "https://cubecobra.com/cube/list/synergy?view=table&f=tag:\"cheap removal\"#top",
            "https://cubecobra.com/cube/list/synergy/?view=spoiler",
            "Caleb Gannon's Powered Synergy Cube - Cube Cobra\nhttps://cubecobra.com/cube/list/synergy",
            "Check this cube: https://cubecobra.com/cube/playtest/synergy.", "https://cubecobra.com/cube/api/cubeJSON/synergy",
            "  synergy \n")) {
            assertEquals("synergy", CubeCobraImport.cubeId(input), input)
        }
        assertEquals("https://cubecobra.com/cube/list/synergy?view=table",
            CubeCobraImport.link("Synergy - Cube Cobra https://cubecobra.com/cube/list/synergy?view=table"))
        assertNull(CubeCobraImport.link("synergy"))
        for (input in listOf("https://evil.invalid/cube/list/synergy", "notcubecobra.com/cube/list/synergy",
            "https://user@cubecobra.com/cube/list/synergy", "https://cubecobra.com/cube/list/", "my cube", "")) {
            assertThrows(IllegalArgumentException::class.java, { CubeCobraImport.cubeId(input) }, input)
        }
    }

    private fun synergy(): CatalogRepository {
        val repository = CatalogRepository(temporary.resolve("synergy").toFile()) { JdbcSqliteDriver("jdbc:sqlite:${it.absolutePath}") }
        repository.stage { index -> synergyFile("AllPrintings.json").byteInputStream().use {
            MtgJsonCatalog().import(it, index, CatalogMetadata("2026-10-23", "5.2.2"))
        } }
        return repository
    }
    /** Real exports of Cube Cobra's "synergy" cube, trimmed to a sample covered by the small real-record catalog. */
    private fun synergyFile(name: String) = javaClass.getResource("/cubecobra-synergy/$name")!!.readText()
    private fun cards(result: DeckImportResult) = result.entries.groupBy { it.printing.name }.mapValues { (_, rows) -> rows.sumOf { it.quantity } }
    private fun printing(result: DeckImportResult, name: String) = result.entries.single { it.printing.name == name }.printing.let { "${it.set} ${it.number}" }

    // DRF-005, DRF-006
    @Test fun everyCubeCobraExportOfOneCubeResolvesToTheSameCards() {
        val resolver = CubeImportResolver(synergy())
        // The real export's one maybeboard card is outside the small sample catalog; DRF-009 covers maybeboards.
        val export = com.google.gson.JsonParser.parseString(synergyFile("cubeJSON.json")).asJsonObject
        val maybe = CubeCobraImport.parse(export.toString()).lines.filter { it.section == DeckSection.MAYBEBOARD }
        assertEquals(listOf("Children of Korlis"), maybe.map { it.name })
        export.getAsJsonObject("cards").remove("maybeboard")
        val json = resolver.resolve(CubeCobraImport.parse(export.toString()))
        assertTrue(json.resolved, json.issues.toString())
        assertTrue(json.substitutions.isEmpty(), json.substitutions.toString())
        assertEquals(19L, json.count(DeckSection.MAINBOARD))
        val expected = cards(json)
        assertEquals(3, expected["Flooded Strand"])
        assertTrue(expected.keys.containsAll(listOf("Expansion // Explosion", "Brazen Borrower // Petty Theft",
            "Jace, Vryn's Prodigy // Jace, Telepath Unbound", "Birgi, God of Storytelling // Harnfel, Horn of Bounty")), expected.toString())
        for (file in listOf("plaintext.txt", "mtgo.txt", "forge.txt", "xmage.txt", "csv.csv")) {
            val result = resolver.resolve(synergyFile(file))
            assertTrue(result.resolved, file + result.issues)
            assertEquals(expected, cards(result), file)
            // Different Unstable cards share the name Ineffable Blessing: only a numbered printing is exact.
            val exact = file in listOf("xmage.txt", "csv.csv")
            assertEquals(if (exact) emptyList() else listOf("Ineffable Blessing"), result.substitutions.map { it.message.substringBefore(":") }, file)
            if (exact) {
                assertEquals("UST 113f", printing(result, "Ineffable Blessing"), file)
                assertEquals("SLD 38", printing(result, "Thalia, Guardian of Thraben"), file)
            }
        }
        assertEquals("UST 113f", printing(json, "Ineffable Blessing"))
        // Several printings of one card in a set are a default choice, never an error.
        assertTrue(printing(resolver.resolve(synergyFile("forge.txt")), "Thalia, Guardian of Thraben").startsWith("SLD "))
    }

    // DRF-005: maybeboard rows and sections, and sideboard sections, go to Maybe.
    @Test fun maybeboardRowsAndSectionsGoToMaybe() {
        val resolver = CubeImportResolver(synergy())
        val plaintext = resolver.resolve(synergyFile("plaintext.txt") + "\r\n# maybeboard\r\nWasteland\r\n")
        assertTrue(plaintext.resolved, plaintext.issues.toString())
        assertEquals(19L, plaintext.count(DeckSection.MAINBOARD)); assertEquals(1L, plaintext.count(DeckSection.MAYBEBOARD))
        val csv = resolver.resolve(synergyFile("csv.csv") +
            "\"Wasteland\",\"1\",\"Instant\",\"W\",\"zzz\",\"1\",\"rare\",\"\",\"Owned\",\"Non-foil\",\"maybeboard\",\"true\",\"\",\"\",\"\",\"Line one\nline two\",\"-1\",\"false\",\"false\",\"Nobody\"\n" +
            "\"Strip Mine\",\"1\",\"Instant\",\"W\",\"zzz\",\"1\",\"rare\",\"\",\"Owned\",\"Non-foil\",\"mainboard\",\"true\",\"\",\"\",\"\",\"\",\"-1\",\"false\",\"false\",\"Nobody\"\n")
        assertTrue(csv.resolved, csv.issues.toString())
        assertEquals(19L, csv.count(DeckSection.MAINBOARD)); assertEquals(2L, csv.count(DeckSection.MAYBEBOARD))
        val forge = resolver.resolve(synergyFile("forge.txt") + "\n[Sideboard]\n1 Wasteland\n")
        assertTrue(forge.resolved, forge.issues.toString())
        assertEquals(19L, forge.count(DeckSection.MAINBOARD)); assertEquals(1L, forge.count(DeckSection.MAYBEBOARD))
        val island = resolver.resolve("Island (M21)\nIsland|ZNR")
        assertTrue(island.resolved, island.issues.toString())
        assertTrue(island.substitutions.isEmpty())
        assertEquals(listOf("M21", "ZNR"), island.entries.map { it.printing.set })
    }

    @Test fun knownScryfallPrintingDoesNotDependOnProviderNameAndMissingIdentityUsesNameFallback() {
        val resolver = resolver()
        val byId = CubeCobraImport.parse("""{"cards":{"mainboard":[{"cardID":"f76be976-d1a3-58fb-9b57-abdf2c982a94"}]}}""")
        val result = resolver.resolve(byId)
        assertTrue(result.resolved, result.issues.toString())
        assertEquals("Coppercloud Surveyor", result.entries.single().printing.name)
        assertTrue(result.substitutions.isEmpty())
        val byName = CubeCobraImport.parse("""{"cards":{"mainboard":[{"cardID":"00000000-0000-4000-8000-000000000000","details":{"name":"Quiet Lattice Bolt"}}]}}""")
        assertEquals(1, resolver.resolve(byName).substitutions.size)
    }
}
