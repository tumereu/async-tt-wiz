package org.asyncttwiz.storage

import org.asyncttwiz.protocol.CardDefinition
import org.asyncttwiz.protocol.DefinitionFace
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** LOB-112 (I-D21): card data saved before it kept split halves and colour indicators is rebuilt from the catalog. */
class CardDataRefreshTest {
    private val meta = CatalogMetadata("2026-11-01", "5.2.2")
    private val fire = CatalogPrinting("fire", "Fire // Ice", "TST", "1", "English", "a", null, false, listOf("ice"),
        """{"name":"Fire // Ice","faceName":"Fire","layout":"split","side":"a","type":"Instant","manaCost":"{1}{R}","text":"Fire deals 2 damage divided as you choose among one or two targets.","colors":["R"]}""")
    private val ice = CatalogPrinting("ice", "Fire // Ice", "TST", "1", "English", "b", null, false, listOf("fire"),
        """{"name":"Fire // Ice","faceName":"Ice","layout":"split","side":"b","type":"Instant","manaCost":"{1}{U}","text":"Tap target permanent.\nDraw a card.","colors":["U"]}""")
    private val front = CatalogPrinting("front", "Wolf Pup // Moon Wolf", "TST", "2", "English", "a", null, false, listOf("back"),
        """{"name":"Wolf Pup // Moon Wolf","faceName":"Wolf Pup","layout":"transform","side":"a","type":"Creature — Wolf","manaCost":"{1}{G}","text":"","colors":["G"],"power":"2","toughness":"2"}""")
    private val back = CatalogPrinting("back", "Wolf Pup // Moon Wolf", "TST", "2", "English", "b", null, false, listOf("front"),
        """{"name":"Wolf Pup // Moon Wolf","faceName":"Moon Wolf","layout":"transform","side":"b","type":"Creature — Wolf","text":"Trample","colors":["G"],"power":"4","toughness":"4"}""")
    private val catalog = listOf(fire, ice, front, back).associateBy { it.id }

    private fun imported(vararg faces: CatalogPrinting) = ResolvedDeckEntry(DeckSection.MAINBOARD, 1, faces.toList(), emptyList()).definition(meta)

    @Test fun oldSplitCardDataGetsItsHalvesFromTheCatalog() {
        val now = imported(fire, ice)
        assertEquals(2, now.faces.single().parts.size)
        assertFalse(CardDataRefresh.stale(now), "made today")
        // Saved before parts existed: the same single face with the combined text, without parts
        val old = now.copy(faces = now.faces.map { it.copy(parts = emptyList()) })
        assertTrue(CardDataRefresh.stale(old))
        assertEquals(now, CardDataRefresh.rebuild(old, meta, catalog::get))
    }

    @Test fun oldBackFacesGetTheirColourIndicator() {
        val now = imported(front, back)
        assertEquals("G", now.faces[1].colorIndicator)
        val old = now.copy(faces = now.faces.map { it.copy(colorIndicator = null) })
        assertTrue(CardDataRefresh.stale(old))
        assertEquals(now, CardDataRefresh.rebuild(old, meta, catalog::get))
    }

    @Test fun cardsTheCatalogDoesntHaveAndOtherCardDataStayAsTheyAre() {
        val old = imported(fire, ice).let { it.copy(faces = it.faces.map { f -> f.copy(parts = emptyList()) }) }
        assertNull(CardDataRefresh.rebuild(old, meta) { if (it == "ice") null else catalog[it] }, "a face is missing")
        assertNull(CardDataRefresh.rebuild(old.copy(printingId = "gone"), meta, catalog::get))
        val handMade = CardDefinition("Custom", listOf(DefinitionFace("c", "Custom", typeLine = "Creature")), layout = "split")
        assertFalse(CardDataRefresh.stale(handMade), "not catalog card data")
    }
}
