package org.asyncttwiz.android

import org.asyncttwiz.protocol.game.GameCards
import org.asyncttwiz.protocol.game.OutsideCards
import org.junit.Test
import kotlin.test.assertEquals

/**
 * LOB-117, DRF-161: the catalog's dungeons are read and parsed once per catalog generation, not once per call: the set support
 * worker asks what they provide for every card it reads (the emulator smoke found it re-parsing them per card, keeping the phone
 * busy for an hour after a catalog install). Invented dungeons only.
 */
class CatalogDungeonCardsTest {
    @Test fun dungeonsAreParsedOncePerCatalogGeneration() {
        val dungeons = EngineGameFixture(dungeons = true).outside
        var loads = 0
        var parses = 0
        var generation = "first.db"
        val cards = CatalogDungeonCards({ loads++; dungeons }, { generation }, { parses++; GameCards.parse(it).definition })

        repeat(500) { assertEquals(setOf(OutsideCards.Need.Dungeon, OutsideCards.Need.Undercity), cards.provided()) }
        assertEquals(dungeons, cards.definitions())
        assertEquals(1, loads)
        assertEquals(dungeons.size, parses, "each dungeon is parsed once")

        // Updated card records are read again
        generation = "second.db"
        cards.provided()
        assertEquals(2, loads)
        assertEquals(2 * dungeons.size, parses)
    }
}
