package org.asyncttwiz.server.catalog

import org.asyncttwiz.protocol.DeckValidation
import org.asyncttwiz.server.TestPrintings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The server's card catalog (docs/specs/web-client.md section 3) on the test cards' catalog the server tests run with. */
class ServerCatalogTest {
    // LOB-150, TBL-138: a deck's card of a printing under another name is the Oracle card, carrying the printed name beside
    // its own; Automatum's mark is the card's, and a saved card is found again by its Oracle name, as a printing under it
    @Test fun `a printing under another name is the Oracle card showing its printed name`() {
        val catalog = TestPrintings.catalog
        val lance = catalog.catalog.printings.single { it.flavorName == "Thunder Lance" }
        val card = checkNotNull(catalog.card(lance.id))
        assertEquals("Test Bolt", card.name)
        assertEquals("Thunder Lance", card.shownName)
        assertEquals(listOf("Thunder Lance"), card.faces.map { it.printedName })
        assertTrue(DeckValidation.definition(card))
        assertTrue(catalog.supported(lance.id), "supported as Test Bolt")
        assertEquals(listOf<String>(), catalog.support(listOf(lance.id to card.name)).byHand)
        val found = checkNotNull(catalog.printingNamed("Test Bolt"))
        assertNotEquals(lance.id, found)
        assertEquals("", catalog.catalog.printing.getValue(found).flavorName)
        assertNull(catalog.printingNamed("Thunder Lance"), "a saved card's name is its Oracle name")

        val hiker = catalog.catalog.printings.single { it.faceFlavorName == "Morning Hiker" }
        val dfc = checkNotNull(catalog.card(hiker.id))
        assertEquals("Dawnward Scout // Duskward Stalker", dfc.name)
        assertEquals("Morning Hiker // Evening Prowler", dfc.shownName)
        assertTrue(DeckValidation.definition(dfc))
    }
}
