package org.asyncttwiz.catalog

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/** [CatalogCards.sourceRecordText] writes exactly what [CatalogCards.sourceRecord]'s JSON tree prints. */
class SourceRecordTextTest {
    private val fixtures = File(System.getProperty("catalog.fixture") ?: "../tests/fixtures/card-catalog")

    @Test fun `every printing of the test cards' catalog is written as its JSON tree prints`() {
        val catalog = CatalogBuilder(emptySet(), emptySet()).build { File(fixtures, "AllPrintings.json").inputStream() }.catalog
        for (printing in catalog.printings) {
            val face = catalog.faces[printing.face]
            assertEquals(CatalogCards.sourceRecord(printing, face).toString(), CatalogCards.sourceRecordText(printing, face), printing.id)
        }
    }

    @Test fun `every character is escaped as the JSON tree escapes it, and optional fields are left out alike`() {
        val all = (0..0xffff).joinToString("") { it.toChar().toString() }
        val face = CatalogFaceRecord(0, "Quote \" and \\ back", faceName = all, type = "Tab\there", text = all.reversed(),
            colors = listOf("W", "\u0001"), oracle = "")
        val printings = listOf(
            CatalogPrintingRecord("id-1", 0, "SET", "1"),
            CatalogPrintingRecord("id-2", 0, "SET", "2\n", scryfall = "s\"", others = listOf("a", "b\u001f"),
                availability = listOf("paper", "mtgo"), onlineOnly = true, promo = true),
        )
        for (oracle in listOf("", "oracle\u007f")) for (printing in printings) {
            val withOracle = face.copy(oracle = oracle)
            assertEquals(CatalogCards.sourceRecord(printing, withOracle).toString(), CatalogCards.sourceRecordText(printing, withOracle))
        }
    }
}
