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
    @Test fun includeSideboardIsOffByDefaultAndIgnoredLinesDoNotCountOrFail() {
        val resolver = resolver()
        val text = "Deck\n2000 Quiet Lattice Bolt\nSideboard\n1000 Velvet Prism Engine\nSB: 1 Unknown card"
        val mainOnly = resolver.resolve(text)
        assertTrue(mainOnly.resolved, mainOnly.issues.toString())
        assertEquals(2000L, mainOnly.count(DeckSection.MAINBOARD))
        assertFalse(resolver.resolve(text, true).resolved)
        val both = resolver.resolve(text.substringBefore("SB:"), true)
        assertTrue(both.resolved, both.issues.toString())
        assertEquals(3000L, both.count(DeckSection.MAINBOARD))
        assertEquals(0L, both.count(DeckSection.SIDEBOARD))
        assertFalse(resolver.resolve("3001 Quiet Lattice Bolt").resolved)
        assertTrue(resolver.resolve("1 Quiet Lattice Bolt\n\nnot a valid sideboard entry").resolved)
        assertTrue(resolver.resolve("1 Quiet Lattice Bolt\nSB: not a card").resolved)
    }
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
    @Test fun cubeCobraFixtureConvertsMainboardOnlyAndPreservesRequestedPrinting() {
        val parsed = CubeCobraImport.parse(javaClass.getResource("/cubecobra.json")!!.readText())
        assertTrue(parsed.issues.isEmpty())
        assertEquals(listOf(1, 2), parsed.lines.map { it.quantity })
        assertEquals("00000000-0000-4000-8000-000000000099", parsed.lines.first().scryfallId)
        assertEquals("ZPA", parsed.lines.first().set)
        val result = resolver().resolve(parsed)
        assertTrue(result.resolved, result.issues.toString())
        assertEquals(3L, result.count(DeckSection.MAINBOARD))
        assertEquals(1, result.substitutions.size)
        assertEquals("synergy", CubeCobraImport.cubeId("https://cubecobra.com/cube/list/synergy?view=table"))
        assertEquals("synergy", CubeCobraImport.cubeId("synergy"))
        assertThrows(IllegalArgumentException::class.java) { CubeCobraImport.cubeId("https://evil.invalid/cube/list/synergy") }
        val broken = CubeCobraImport.parse("""{"cards":{"mainboard":[{"details":{"name":"Quiet Lattice Bolt"}},{"details":{}}]}}""")
        assertEquals(2, broken.issues.single().line)
        assertFalse(resolver().resolve(broken).resolved)
    }
    @Test fun cubeCobraScryfallIdentityWinsOverConflictingPrintingMetadata() {
        val parsed = CubeCobraImport.parse("""{"cards":{"mainboard":[{"cardID":"f76be976-d1a3-58fb-9b57-abdf2c982a94","details":{"name":"Coppercloud Surveyor","set":"zpa","collector_number":"1"}}]}}""")
        val result = resolver().resolve(parsed)
        assertTrue(result.resolved, result.issues.toString())
        assertEquals("ZPB", result.entries.single().printing.set)
        assertEquals("101", result.entries.single().printing.number)
        assertTrue(result.substitutions.isEmpty())
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
