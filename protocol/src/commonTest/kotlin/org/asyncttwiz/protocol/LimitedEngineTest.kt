package org.asyncttwiz.protocol

import kotlin.test.*

class LimitedEngineTest {
    private val players = listOf("ann", "bob", "cat", "dan")
    private val seed = RandomV1.encodeSeed(ByteArray(32) { it.toByte() })
    private val otherSeed = RandomV1.encodeSeed(ByteArray(32) { (it * 7 + 1).toByte() })
    private fun cube(size: Int) = List(size) { DraftCubeEntry("card-" + it.toString().padStart(3, '0'), 1) }

    // DRF-021, DRF-023, DRF-141: every engine's defaults, ranges and player limits.
    @Test fun defaultsRangesAndPlayerLimits() {
        assertEquals(DraftSettings(DraftMode.ROCHESTER, packsPerPlayer = 3, cardsPerPack = 15), DraftSettings.rochester())
        assertNull(DraftSettings.rochester().tablePacksOpen)
        assertEquals(45, DraftSettings.rotisserie().cardsPerPlayer)
        assertEquals(listOf(3, 3), DraftSettings.winston().let { listOf(it.packsPerPlayer, it.piles) })
        assertEquals(listOf(3, 4), DraftSettings.winchester().let { listOf(it.packsPerPlayer, it.piles) })
        assertEquals(listOf(6, 45), DraftSettings.sealed().let { listOf(it.packsPerPlayer, it.cardsPerPlayer) })
        val limits = mapOf(DraftMode.BOOSTER to 8, DraftMode.GRID to 4, DraftMode.ROCHESTER to 8, DraftMode.ROTISSERIE to 8,
            DraftMode.WINSTON to 4, DraftMode.WINCHESTER to 4, DraftMode.SEALED to 8)
        for ((mode, most) in limits) {
            val settings = DraftSettings.defaults(mode)
            assertEquals(2..most, mode.players)
            for (count in 2..most) assertTrue(settings.validate(count).isEmpty(), "$mode $count")
            for (count in listOf(1, most + 1)) assertEquals(listOf(DraftSettingsIssue.PLAYERS), settings.validate(count), "$mode $count")
        }
        fun issues(settings: DraftSettings) = settings.validate(2)
        val rochester = DraftSettings.rochester()
        assertEquals(listOf(DraftSettingsIssue.PACKS_PER_PLAYER), issues(rochester.copy(packsPerPlayer = 16)))
        assertEquals(listOf(DraftSettingsIssue.CARDS_PER_PACK), issues(rochester.copy(cardsPerPack = 4)))
        assertTrue(issues(rochester.copy(packsPerPlayer = 15, cardsPerPack = 30, tablePacksOpen = 120)).isEmpty())
        assertEquals(listOf(DraftSettingsIssue.PACKS_OPEN_AT_ONCE), issues(rochester.copy(tablePacksOpen = 0)))
        assertEquals(listOf(DraftSettingsIssue.CARDS_PER_PLAYER), issues(DraftSettings.rotisserie().copy(cardsPerPlayer = 226)))
        assertTrue(issues(DraftSettings.rotisserie().copy(cardsPerPlayer = 5)).isEmpty())
        assertEquals(listOf(DraftSettingsIssue.PILES), issues(DraftSettings.winston().copy(piles = 7)))
        assertTrue(issues(DraftSettings.winchester().copy(piles = 8)).isEmpty())
        assertEquals(listOf(DraftSettingsIssue.PILES), issues(DraftSettings.winchester().copy(piles = 1)))
        assertEquals(listOf(DraftSettingsIssue.PACKS_PER_PLAYER), issues(DraftSettings.sealed().copy(packsPerPlayer = 31)))
        assertEquals(listOf(DraftSettingsIssue.LIBRARIANS), issues(DraftSettings.rochester().copy(librarians = 1)))
        // Cube supply: cards each for Rotisserie and Sealed, packs × pack size for the others.
        assertEquals(DraftSupply(90, 100), DraftSettings.rotisserie().supply(2, cube(100)))
        assertEquals(DraftSupply(135, 100), DraftSettings.winston().supply(3, cube(100)))
        assertEquals(DraftSupply(180, 100), DraftSettings.sealed().supply(4, cube(100)))
    }
}
