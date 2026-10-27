package org.asyncttwiz.android

import org.asyncttwiz.storage.DeckSection
import org.junit.Test
import kotlin.test.*

class DeckUrlImportTest {
    private fun fixture(name: String) = javaClass.getResource("/import/$name")!!.readText()

    @Test fun detectsCopiedUrlsAndSiteQualifiedIds() {
        listOf("https://archidekt.com/decks/12345", "https://www.archidekt.com/decks/12345/my_deck?share=true#cards", "archidekt:12345").forEach {
            assertEquals(DeckImportLocation(DeckImportSite.ARCHIDEKT, "12345"), DeckUrlImport.location(it))
        }
        assertEquals("https://archidekt.com/api/decks/12345/", DeckUrlImport.location("archidekt:12345")!!.endpoint)
    }

    @Test fun rejectsUnrelatedOrMisleadingLocationsAndBareIds() {
        listOf("12345", "a deck", "https://archidekt.com/", "https://archidekt.com/decks/nope", "https://archidekt.com/decks/-1",
            "https://archidekt.com.evil.test/decks/12345", "https://evil.test/archidekt.com/decks/12345", "https://evil@archidekt.com/decks/12345",
            "https://archidekt.com:8443/decks/12345", "file://archidekt.com/decks/12345", "https://archidekt.com/decks/../../secret",
            "archidekt:12/34", "https://cubecobra.com/cube/list/runes").forEach { assertNull(DeckUrlImport.location(it), it) }
    }

    @Test fun unsupportedProvidersAreNeitherImportableNorPrefilled() {
        listOf("https://www.mtggoldfish.com/deck/12345#paper", "https://mtggoldfish.com/deck/download/12345", "mtggoldfish:12345",
            "https://moxfield.com/decks/aBcDeFgHiJkLmNoPqRsT_1", "https://www.moxfield.com/decks/aBcDeFgHiJkLmNoPqRsT_1?share=true",
            "moxfield:aBcDeFgHiJkLmNoPqRsT_1", "aBcDeFgHiJkLmNoPqRsT_1").forEach {
            assertNull(DeckUrlImport.location(it), it)
            assertNull(ImportClipboard.deck(it), it)
        }
    }

    @Test fun archidektPreservesQuantitiesAndSectionsAndOmitsMaybeboard() {
        val deck = DeckUrlImport.parse(DeckImportSite.ARCHIDEKT, fixture("archidekt.json"))
        assertEquals("Cooperative spells", deck.name)
        assertTrue(deck.parsed.issues.isEmpty())
        assertEquals(listOf("Rune Spark", "Rune Captain", "Rune Shield"), deck.parsed.lines.map { it.name })
        assertEquals(listOf(4, 1, 2), deck.parsed.lines.map { it.quantity })
        assertEquals(listOf(DeckSection.MAINBOARD, DeckSection.MAINBOARD, DeckSection.SIDEBOARD), deck.parsed.lines.map { it.section })
    }

    @Test fun malformedProviderEntriesNeverBecomePartialSavableDecks() {
        assertFails { DeckUrlImport.parse(DeckImportSite.ARCHIDEKT, fixture("archidekt.json").replace("\"quantity\": 4", "\"quantity\": 0")) }
        assertFails { DeckUrlImport.parse(DeckImportSite.ARCHIDEKT, fixture("archidekt.json").replace("\"Rune Spark\"", "null")) }
        assertFails { DeckUrlImport.parse(DeckImportSite.ARCHIDEKT, "<html>Blocked</html>") }
        assertFails { DeckUrlImport.parse(DeckImportSite.ARCHIDEKT, """{"name":"Broken"}""") }
    }

    @Test fun clipboardOnlyMatchesTheCorrectImportKind() {
        assertEquals("runes", ImportClipboard.cube(" runes \n"))
        assertEquals("https://cubecobra.com/cube/list/runes", ImportClipboard.cube("https://cubecobra.com/cube/list/runes"))
        assertEquals("https://cubecobra.com/cube/list/runes?view=table",
            ImportClipboard.cube("Rune Cube - Cube Cobra\nhttps://cubecobra.com/cube/list/runes?view=table"))
        assertNull(ImportClipboard.cube("https://archidekt.com/decks/123"))
        assertNull(ImportClipboard.cube("a pasted card list\n1 Rune Bear"))
        assertNull(ImportClipboard.cube(null))
        assertEquals("https://archidekt.com/decks/123?share=true#cards", ImportClipboard.deck(" https://archidekt.com/decks/123?share=true#cards\n"))
        assertEquals("archidekt:123", ImportClipboard.deck("archidekt:123"))
        assertNull(ImportClipboard.deck("https://cubecobra.com/cube/list/runes"))
        assertNull(ImportClipboard.deck("123"))
        assertNull(ImportClipboard.deck("1 Rune Bear"))
        assertNull(ImportClipboard.deck(null))
    }
}
