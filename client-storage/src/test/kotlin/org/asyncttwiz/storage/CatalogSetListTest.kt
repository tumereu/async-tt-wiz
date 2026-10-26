package org.asyncttwiz.storage

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CatalogSetListTest {
    @Test fun largeEmbeddedDeckListsDoNotBreakSetListParsing() {
        // Real MTGJSON SetList entries embed whole decks and sealed products, far above one record budget.
        val decks = (1..4000).joinToString(",") { """{"name":"Deck $it","code":"D$it","mainBoard":[{"count":1,"uuid":"${"0".repeat(36)}"}]}""" }
        val json = """{"meta":{"date":"2026-10-26","version":"5.3.0+20261026"},"data":[
            {"code":"BIG","name":"Big","releaseDate":"2026-10-01","decks":[$decks],"sealedProduct":[]},
            {"code":"SML","name":"Small","releaseDate":"2025-01-02"}]}"""
        val list = MtgJsonCatalog().setList(json.byteInputStream())
        assertEquals(listOf(CatalogSet("BIG", "2026-10-01", name = "Big"), CatalogSet("SML", "2025-01-02", name = "Small")), list.sets)
    }

    @Test fun missingNamesRemainOptional() {
        val json = """{"meta":{"date":"2026-10-26","version":"5.3.0"},"data":[
            {"code":"OLD","releaseDate":"2000-01-01"}]}"""
        assertEquals(listOf(CatalogSet("OLD", "2000-01-01")), MtgJsonCatalog().setList(json.byteInputStream()).sets)
    }
}
