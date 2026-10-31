package org.asyncttwiz.protocol

import kotlinx.serialization.Serializable

/** GLIMPSE is only read from pods and presets saved before Glimpse became a built-in preset of BOOSTER (DRF-020). */
@Serializable enum class DraftMode { BOOSTER, GLIMPSE, GRID, ROCHESTER, ROTISSERIE, WINSTON, WINCHESTER, SEALED }
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
    val grids: Int = 18,
    val twoPicks: Boolean = false,
    val gridsOpenAtOnce: Int = 1,
    val librarians: Int = 0,
    /** Catalog snapshot supplied by the creator; not part of pack supply. */
    val librarianDefinition: SubmittedDefinition? = null,
    /**
     * Grid mode with a set source only (ignored otherwise): each grid gets its own freshly generated booster and takes
     * random cards from it, instead of filling grids from boosters in card order.
     */
    val freshBoosters: Boolean = true,
    /** Rotisserie, and Sealed from a cube: the cards each player ends with. */
    val cardsPerPlayer: Int = 45,
    /** Winston and Winchester: how many piles. */
    val piles: Int = 3,
    /** Rochester: packs open at once across the table; null = one per player. */
    val tablePacksOpen: Int? = null,
) {
    fun validate(players: Int): List<DraftSettingsIssue> = buildList {
        if (sideboardLimit != null && sideboardLimit < 0) add(DraftSettingsIssue.SIDEBOARD_LIMIT)
        if (librarians !in 0..10 || !mode.packPassing && librarians != 0) add(DraftSettingsIssue.LIBRARIANS)
        if (players !in mode.players) add(DraftSettingsIssue.PLAYERS)
        if (mode.limited) {
            when (mode) {
                DraftMode.ROTISSERIE -> if (cardsPerPlayer !in 5..225) add(DraftSettingsIssue.CARDS_PER_PLAYER)
                DraftMode.SEALED -> {
                    if (cardsPerPlayer !in 5..225) add(DraftSettingsIssue.CARDS_PER_PLAYER)
                    if (packsPerPlayer !in 1..30) add(DraftSettingsIssue.PACKS_PER_PLAYER)
                }
                else -> {
                    if (packsPerPlayer !in 1..15) add(DraftSettingsIssue.PACKS_PER_PLAYER)
                    if (cardsPerPack !in 5..30) add(DraftSettingsIssue.CARDS_PER_PACK)
                    if (mode == DraftMode.WINSTON && piles !in 2..6 || mode == DraftMode.WINCHESTER && piles !in 2..8)
                        add(DraftSettingsIssue.PILES)
                    if (mode == DraftMode.ROCHESTER && tablePacksOpen != null && tablePacksOpen !in 1..packsPerPlayer * DraftMode.ROCHESTER.players.last)
                        add(DraftSettingsIssue.PACKS_OPEN_AT_ONCE)
                }
            }
            return@buildList
        }
        if (mode == DraftMode.GRID) {
            if (grids !in 1..60) add(DraftSettingsIssue.GRIDS)
            if (twoPicks && players != 2) add(DraftSettingsIssue.TWO_PICKS)
            if (gridsOpenAtOnce !in 1..grids) add(DraftSettingsIssue.GRIDS_OPEN_AT_ONCE)
            return@buildList
        }
        if (packsPerPlayer !in 1..30) add(DraftSettingsIssue.PACKS_PER_PLAYER)
        if (cardsPerPack !in 1..30) add(DraftSettingsIssue.CARDS_PER_PACK)
        if (keep < 1) add(DraftSettingsIssue.KEEP)
        if (burn < 0) add(DraftSettingsIssue.BURN)
        if (keep.toLong() + burn > cardsPerPack) add(DraftSettingsIssue.KEEP_AND_BURN)
        if (discardRemainingAt !in 0..cardsPerPack) add(DraftSettingsIssue.DISCARD_REMAINING_AT)
        if (packsOpenAtOnce !in 1..packsPerPlayer) add(DraftSettingsIssue.PACKS_OPEN_AT_ONCE)
    }

    val rounds: Int get() = if (mode == DraftMode.GRID) (grids + gridsOpenAtOnce - 1) / gridsOpenAtOnce
        else (packsPerPlayer + packsOpenAtOnce - 1) / packsOpenAtOnce

    fun gridRefills(players: Int): Boolean = players >= 3 || twoPicks

    companion object {
        fun booster() = DraftSettings()
        /** The built-in Glimpse preset of Booster (DRF-020). */
        fun glimpse() = DraftSettings(packsPerPlayer = 9, burn = 2)
        fun grid() = DraftSettings(mode = DraftMode.GRID)
        fun rochester() = DraftSettings(mode = DraftMode.ROCHESTER)
        fun rotisserie() = DraftSettings(mode = DraftMode.ROTISSERIE)
        fun winston() = DraftSettings(mode = DraftMode.WINSTON, piles = 3)
        fun winchester() = DraftSettings(mode = DraftMode.WINCHESTER, piles = 4)
        fun sealed() = DraftSettings(mode = DraftMode.SEALED, packsPerPlayer = 6)
        /** A mode's default settings (Glimpse is a preset of Booster). */
        fun defaults(mode: DraftMode) = when (mode) {
            DraftMode.BOOSTER -> booster()
            DraftMode.GLIMPSE -> glimpse()
            DraftMode.GRID -> grid()
            DraftMode.ROCHESTER -> rochester()
            DraftMode.ROTISSERIE -> rotisserie()
            DraftMode.WINSTON -> winston()
            DraftMode.WINCHESTER -> winchester()
            DraftMode.SEALED -> sealed()
        }
    }
}

@Serializable enum class DraftSettingsIssue {
    PLAYERS, PACKS_PER_PLAYER, CARDS_PER_PACK, KEEP, BURN, KEEP_AND_BURN,
    DISCARD_REMAINING_AT, PACKS_OPEN_AT_ONCE, SIDEBOARD_LIMIT,
    GRIDS, TWO_PICKS, GRIDS_OPEN_AT_ONCE, LIBRARIANS, CARDS_PER_PLAYER, PILES,
}

@Serializable data class DraftCubeEntry(val definition: String, val quantity: Int, val colors: Set<DraftColor> = emptySet(), val librarian: Boolean = false)
@Serializable data class DraftCopy(val id: String, val definition: String, val colors: Set<DraftColor> = emptySet(), val librarian: Boolean = false)
@Serializable data class DraftSupply(val needed: Long, val available: Long) {
    val sufficient: Boolean get() = needed <= available
}

/** Supply is counted, not expanded, so the setup UI can display invalid/insufficient settings too. */
fun DraftSettings.supply(players: Int, cube: List<DraftCubeEntry>) = DraftSupply(
    if (mode == DraftMode.GRID) grids.toLong() * (9 + if (gridRefills(players)) 3 else 0)
    else if (mode.limited) LimitedEngine.needed(this, players)
    else players.toLong() * packsPerPlayer * cardsPerPack,
    cube.sumOf { it.quantity.toLong() },
)
