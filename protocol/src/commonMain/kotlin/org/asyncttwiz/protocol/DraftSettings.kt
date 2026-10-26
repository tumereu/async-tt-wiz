package org.asyncttwiz.protocol

import kotlinx.serialization.Serializable

@Serializable enum class DraftMode { BOOSTER, GLIMPSE }
@Serializable enum class DraftColor { W, U, B, R, G }

@Serializable
data class DraftSettings(
    val mode: DraftMode = DraftMode.BOOSTER,
    val packsPerPlayer: Int = 3,
    val cardsPerPack: Int = 15,
    val keep: Int = 1,
    val burn: Int = 0,
    val discardRemainingAt: Int = 0,
    val packsOpenAtOnce: Int = 1,
    val colorBalance: Boolean = true,
    val sideboardLimit: Int? = null,
) {
    fun validate(players: Int): List<DraftSettingsIssue> = buildList {
        if (players !in 2..8) add(DraftSettingsIssue.PLAYERS)
        if (packsPerPlayer !in 1..30) add(DraftSettingsIssue.PACKS_PER_PLAYER)
        if (cardsPerPack !in 1..30) add(DraftSettingsIssue.CARDS_PER_PACK)
        if (keep < 1) add(DraftSettingsIssue.KEEP)
        if (burn < 0) add(DraftSettingsIssue.BURN)
        if (keep.toLong() + burn > cardsPerPack) add(DraftSettingsIssue.KEEP_AND_BURN)
        if (discardRemainingAt !in 0..cardsPerPack) add(DraftSettingsIssue.DISCARD_REMAINING_AT)
        if (packsOpenAtOnce !in 1..packsPerPlayer) add(DraftSettingsIssue.PACKS_OPEN_AT_ONCE)
        if (sideboardLimit != null && sideboardLimit < 0) add(DraftSettingsIssue.SIDEBOARD_LIMIT)
    }

    val rounds: Int get() = (packsPerPlayer + packsOpenAtOnce - 1) / packsOpenAtOnce

    companion object {
        fun booster() = DraftSettings()
        fun glimpse() = DraftSettings(mode = DraftMode.GLIMPSE, packsPerPlayer = 9, burn = 2)
    }
}

@Serializable enum class DraftSettingsIssue {
    PLAYERS, PACKS_PER_PLAYER, CARDS_PER_PACK, KEEP, BURN, KEEP_AND_BURN,
    DISCARD_REMAINING_AT, PACKS_OPEN_AT_ONCE, SIDEBOARD_LIMIT,
}

@Serializable data class DraftCubeEntry(val definition: String, val quantity: Int, val colors: Set<DraftColor> = emptySet())
@Serializable data class DraftCopy(val id: String, val definition: String, val colors: Set<DraftColor> = emptySet())
@Serializable data class DraftSupply(val needed: Long, val available: Long) {
    val sufficient: Boolean get() = needed <= available
}

/** Supply is counted, not expanded, so the setup UI can display invalid/insufficient settings too. */
fun DraftSettings.supply(players: Int, cube: List<DraftCubeEntry>) = DraftSupply(
    players.toLong() * packsPerPlayer * cardsPerPack,
    cube.sumOf { it.quantity.toLong() },
)
