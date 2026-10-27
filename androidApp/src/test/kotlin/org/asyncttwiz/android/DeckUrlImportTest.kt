package org.asyncttwiz.android

import org.asyncttwiz.storage.DeckSection
import org.junit.Test
import kotlin.test.*

class DeckUrlImportTest {
    private val mox = "aBcDeFgHiJkLmNoPqRsT_1"
    private fun fixture(name: String) = javaClass.getResource("/import/$name")!!.readText()

    @Test fun detectsCopiedUrlsAndSiteQualifiedIds() {
        listOf("https://archidekt.com/decks/12345", "https://www.archidekt.com/decks/12345/my_deck?share=true#cards", "archidekt:12345").forEach {
            assertEquals(DeckImportLocation(DeckImportSite.ARCHIDEKT, "12345"), DeckUrlImport.location(it))
        }
        listOf("https://www.mtggoldfish.com/deck/12345#paper", "https://mtggoldfish.com/deck/12345?view=visual", "https://www.mtggoldfish.com/deck/download/12345", "mtggoldfish:12345").forEach {
            assertEquals(DeckImportLocation(DeckImportSite.MTGGOLDFISH, "12345"), DeckUrlImport.location(it))
        }
        listOf("https://moxfield.com/decks/$mox", "https://www.moxfield.com/decks/$mox?share=true", "moxfield:$mox", mox).forEach {
            assertEquals(DeckImportLocation(DeckImportSite.MOXFIELD, mox), DeckUrlImport.location(it))
        }
        assertEquals("https://archidekt.com/api/decks/12345/", DeckUrlImport.location("archidekt:12345")!!.endpoint)
        assertEquals("https://www.mtggoldfish.com/deck/download/12345", DeckUrlImport.location("mtggoldfish:12345")!!.endpoint)
        assertEquals("https://api2.moxfield.com/v3/decks/all/$mox", DeckUrlImport.location(mox)!!.endpoint)
    }

    @Test fun rejectsUnrelatedOrMisleadingLocationsAndAmbiguousNumericIds() {
        listOf("12345", "a deck", "https://moxfield.com/", "https://archidekt.com/decks/nope", "https://mtggoldfish.com/deck/-1",
            "https://archidekt.com.evil.test/decks/12345", "https://evil.test/archidekt.com/decks/12345", "https://evil@archidekt.com/decks/12345",
            "https://archidekt.com:8443/decks/12345", "file://archidekt.com/decks/12345", "https://moxfield.com/decks/../../secret",
            "mtggoldfish:12/34", "https://cubecobra.com/cube/list/runes").forEach { assertNull(DeckUrlImport.location(it), it) }
    }

    @Test fun archidektPreservesQuantitiesAndSectionsAndOmitsMaybeboard() {
        val deck = DeckUrlImport.parse(DeckImportSite.ARCHIDEKT, fixture("archidekt.json"))
        assertEquals("Cooperative spells", deck.name)
        assertTrue(deck.parsed.issues.isEmpty())
        assertEquals(listOf("Rune Spark", "Rune Captain", "Rune Shield"), deck.parsed.lines.map { it.name })
        assertEquals(listOf(4, 1, 2), deck.parsed.lines.map { it.quantity })
        assertEquals(listOf(DeckSection.MAINBOARD, DeckSection.MAINBOARD, DeckSection.SIDEBOARD), deck.parsed.lines.map { it.section })
    }

    @Test fun moxfieldImportsAllThreeSupportedBoardsButNotMaybeboard() {
        val deck = DeckUrlImport.parse(DeckImportSite.MOXFIELD, fixture("moxfield.json"))
        assertEquals("Cooperative creatures", deck.name)
        assertTrue(deck.parsed.issues.isEmpty())
        assertEquals(listOf("Rune Bear", "Rune Captain", "Rune Shield"), deck.parsed.lines.map { it.name })
        assertEquals(listOf(4, 1, 2), deck.parsed.lines.map { it.quantity })
        assertEquals(DeckSection.SIDEBOARD, deck.parsed.lines.last().section)
        val keyed = """{"name":"Keyed","boards":{"mainboard":{"cards":{"key":{"quantity":2,"card":{"name":"Rune Bear"}}}}}}"""
        assertEquals(2, DeckUrlImport.parse(DeckImportSite.MOXFIELD, keyed).parsed.lines.single().quantity)
    }

    @Test fun goldfishUsesDownloadedTextAndPublicDeckName() {
        val name = DeckUrlImport.goldfishName(fixture("goldfish.html"))
        assertEquals("Runes & Bears", name)
        assertEquals("Runes & Bears", DeckUrlImport.goldfishName("<title>Runes &amp; Bears - MTGGoldfish</title>"))
        val deck = DeckUrlImport.parse(DeckImportSite.MTGGOLDFISH, fixture("goldfish.txt"), name)
        assertEquals(name, deck.name)
        assertTrue(deck.parsed.issues.isEmpty())
        assertEquals(listOf(4, 2, 2), deck.parsed.lines.map { it.quantity })
        assertEquals(listOf(DeckSection.MAINBOARD, DeckSection.MAINBOARD, DeckSection.SIDEBOARD), deck.parsed.lines.map { it.section })
        assertEquals("Named list", DeckUrlImport.parse(DeckImportSite.MTGGOLDFISH, "// NAME: Named list\n1 Rune Bear").name)
    }

    @Test fun malformedProviderEntriesNeverBecomePartialSavableDecks() {
        assertFails { DeckUrlImport.parse(DeckImportSite.ARCHIDEKT, fixture("archidekt.json").replace("\"quantity\": 4", "\"quantity\": 0")) }
        assertFails { DeckUrlImport.parse(DeckImportSite.ARCHIDEKT, fixture("archidekt.json").replace("\"Rune Spark\"", "null")) }
        assertFails { DeckUrlImport.parse(DeckImportSite.MOXFIELD, "<html>Blocked</html>") }
        assertFails { DeckUrlImport.parse(DeckImportSite.MOXFIELD, """{"name":"Broken","boards":{}}""") }
        val text = DeckUrlImport.parse(DeckImportSite.MTGGOLDFISH, "1 Rune Bear\nnot a card entry", "Invalid")
        assertTrue(text.parsed.issues.isNotEmpty())
    }

    @Test fun clipboardOnlyMatchesTheCorrectImportKind() {
        assertEquals("runes", ImportClipboard.cube(" runes \n"))
        assertEquals("https://cubecobra.com/cube/list/runes", ImportClipboard.cube("https://cubecobra.com/cube/list/runes"))
        assertNull(ImportClipboard.cube("https://archidekt.com/decks/123"))
        assertNull(ImportClipboard.cube("a pasted card list\n1 Rune Bear"))
        assertNull(ImportClipboard.cube(null))
        assertEquals("https://mtggoldfish.com/deck/123#paper", ImportClipboard.deck(" https://mtggoldfish.com/deck/123#paper\n"))
        assertEquals(mox, ImportClipboard.deck(mox))
        assertEquals("archidekt:123", ImportClipboard.deck("archidekt:123"))
        assertNull(ImportClipboard.deck("https://cubecobra.com/cube/list/runes"))
        assertNull(ImportClipboard.deck("123"))
        assertNull(ImportClipboard.deck("1 Rune Bear"))
        assertNull(ImportClipboard.deck(null))
    }
}
