package org.asyncttwiz.storage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

// What client-storage read with Gson before web client step 3a, now common (kotlinx.serialization), checked on every
// platform the app runs on (the browser's regular expressions included). Android's host tests check the rest.
class RecordReadsTest {
    @Test
    fun cubeCobraLinksAreFoundInAnyCase() {
        assertEquals("HTTPS://CubeCobra.com/cube/list/synergy?view=table",
            CubeCobraImport.link("Look: HTTPS://CubeCobra.com/cube/list/synergy?view=table"))
        assertEquals("synergy", CubeCobraImport.cubeId("www.cubecobra.com/cube/api/cubeJSON/synergy"))
        assertNull(CubeCobraImport.link("notcubecobra.com/cube/list/synergy"))
        assertFailsWith<IllegalArgumentException> { CubeCobraImport.cubeId("https://evil.invalid/cube/list/synergy") }
    }

    @Test
    fun cubeCobraCardsAreReadAsBefore() {
        val parsed = CubeCobraImport.parse("""{"cards":{"mainboard":[{"details":{"name":"Quiet Lattice Bolt","set":"tst",
            "collector_number":"7"},"quantity":"2"},{"details":{"name":["Listed Name"]}},{"details":null},7],
            "maybeboard":[{"cardID":"00000000-0000-4000-8000-000000000001"}]}}""")
        assertEquals(listOf(2, 1, 1), parsed.lines.map { it.quantity })
        assertEquals(listOf("Quiet Lattice Bolt", "Listed Name", "Scryfall 00000000-0000-4000-8000-000000000001"),
            parsed.lines.map { it.name })
        assertEquals("TST", parsed.lines.first().set)
        assertEquals(listOf(3, 4), parsed.issues.map { it.line })
        assertEquals(DeckSection.MAYBEBOARD, parsed.lines.last().section)
    }

    @Test
    fun aPrintingsPictureAndNamesComeFromItsRecord() {
        val record = CatalogPrinting("p", "Dawn Hunter // Dusk Stalker", "TST", "3", "English", "b", "00000000-0000-4000-8000-000000000004",
            false, emptyList(), """{"faceName":"Dusk Stalker","type":"Creature — Vampire"}""")
        assertEquals(ArtworkReference(record.scryfallId, 1, faceName = "Dusk Stalker"), ArtworkReference.from(record))
        assertEquals(ArtworkReference(record.scryfallId, 1, faceName = record.name), ArtworkReference.from(record.copy(json = "{")))
        assertEquals(listOf(CatalogNamedPart("Dusk Stalker", "Creature — Vampire")),
            CatalogCard("k", record.name, listOf(record)).namedParts())
    }

    @Test
    fun aMeldBackIsLookedUpAsItsOwnCard() {
        val id = "00000000-0000-4000-8000-000000000001"
        val result = "00000000-0000-4000-8000-000000000009"
        val card = """{"object":"card","id":"$id","name":"Front","layout":"meld","image_uris":{"normal":"https://x/n.jpg"},
            "all_parts":[{"component":"meld_result","name":"Melded","id":"$result"}]}"""
        assertEquals(ScryfallImages.Lookup.MeldResult(result),
            ScryfallImages.lookup(ArtworkReference(id, 1, faceName = "Melded"), card, id))
        assertEquals(ScryfallImages.Lookup.Images(listOf("https://x/n.jpg")), ScryfallImages.lookup(ArtworkReference(id, 0), card, id))
        assertFailsWith<IllegalStateException> { ScryfallImages.lookup(ArtworkReference(id, 0), card, result) }
    }

    @Test
    fun collectorNumbersCompareAsNumbers() {
        assertEquals(listOf("2", "10", "10a", "000011", "123456789012345678901"),
            listOf("123456789012345678901", "10a", "000011", "10", "2").sortedWith { a, b -> compareCollectorNumbers(a, b) })
    }
}
