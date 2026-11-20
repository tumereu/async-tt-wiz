package org.asyncttwiz.protocol

import kotlinx.serialization.Serializable

/**
 * GLIMPSE is only read from pods and presets saved before Glimpse became a preset of BOOSTER (DRF-020, DRF-031).
 * CUSTOM_BOOSTER is Booster's pack passing with the creator's list of passes in place of keep, burn and discard (DRF-260).
 * ARENA: each player drafts alone from offers of their own (DRF-280, [ArenaEngine]).
 */
@Serializable enum class DraftMode { BOOSTER, GLIMPSE, GRID, ROCHESTER, ROTISSERIE, WINSTON, WINCHESTER, SEALED, CUSTOM_BOOSTER, ARENA }
@Serializable enum class DraftColor { W, U, B, R, G }

/**
 * One pass of a Custom booster pack (DRF-260): the player it reaches picks [pick] cards and then burns [burn] (they go to
 * nobody), and passes the pack on.
 */
@Serializable data class DraftPass(val pick: Int = 1, val burn: Int = 0) {
    companion object {
        /** The most passes a list holds, and the most cards one pass takes: a pack holds at most 30 cards (DRF-023). */
        const val MAX = 30
    }
}

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
    /** The Cogwork Librarian's card data, filled by the server from its catalog when [librarians] > 0; not part of pack supply. */
    val librarianDefinition: SubmittedDefinition? = null,
    /**
     * Grid mode with a set source only (ignored otherwise): each grid gets its own freshly generated booster and takes
     * random cards from it, instead of filling grids from boosters in card order.
     */
    val freshBoosters: Boolean = true,
    /** Rotisserie, and Sealed from a cube: the cards each player ends with (Rotisserie: picks per player). */
    val cardsPerPlayer: Int = 45,
    /** Winston and Winchester: how many piles. */
    val piles: Int = 3,
    /**
     * Rotisserie (DRF-152, owner 2026-11-14): the cards per player laid out in the pool, at least [cardsPerPlayer]; the cards
     * nobody picks stay in it. Null: as many as each picks.
     */
    val poolPerPlayer: Int? = null,
    /** Rochester: packs open at once across the table; null = one per player. */
    val tablePacksOpen: Int? = null,
    /** Winston and Winchester: independent tables the cards are split into (DRF-156, DRF-158); more than the players gives one per player. */
    val tables: Int = 1,
    /**
     * Custom booster (DRF-260): the passes every pack goes through, in order; the n-th manual pick from a pack is its n-th
     * pass. When they run out, the cards left in the pack are thrown away. Unused by the other modes.
     */
    val passes: List<DraftPass> = emptyList(),
    /**
     * Arena (DRF-281): every this-many-th pick offers rares; null: never. Arena keeps its number of picks in
     * [packsPerPlayer], its cards per offer in [cardsPerPack] and its picks per offer in [keep].
     */
    val rareEvery: Int? = 10,
    /** Arena (DRF-281): every this-many-th pick offers uncommons (a rare pick wins); null: never. */
    val uncommonEvery: Int? = 4,
    /** Arena (DRF-282): each card of a rare pick's offer is a mythic rare with chance 1 in this; null: never. */
    val mythicOneIn: Int? = 8,
    /** Arena from a cube (DRF-283): every offer is drawn from the whole cube, whatever the rarities. */
    val ignoreRarity: Boolean = false,
    /** Arena from a cube (DRF-284): a card a player picked may be offered to that player again. */
    val reuseCards: Boolean = false,
) {
    private val custom: Boolean get() = mode == DraftMode.CUSTOM_BOOSTER

    /**
     * The pass a pack is on after [taken] manual picks from it: Booster's keep and burn on every pick, a Custom booster's
     * [passes] entry (null once they have run out: the pack is then empty).
     */
    fun pass(taken: Int): DraftPass? = if (custom) passes.getOrNull(taken) else DraftPass(keep, burn)
    /** Cards a pick keeps on the pass after [taken] manual picks (0 once a Custom booster's passes have run out). */
    fun keepAt(taken: Int): Int = pass(taken)?.pick ?: 0
    /** Cards a pick burns on the pass after [taken] manual picks. */
    fun burnAt(taken: Int): Int = pass(taken)?.burn ?: 0
    /** Booster's "discard" threshold (DRF-046); a Custom booster has none (its passes end the pack instead). */
    val discardAt: Int get() = if (custom) 0 else discardRemainingAt
    /** A Custom booster's passes have run out after [taken] manual picks from a pack: the rest of it is thrown away (DRF-261). */
    fun passesEnd(taken: Int): Boolean = custom && taken >= passes.size

    /**
     * DRF-045: a pick from a pack holding [cards] cards (spent Librarians included) with [keep] keep slots needs only the
     * keeps when the rest would all go anyway: burned, or discarded at the threshold after the burns. The engine then
     * burns up to burn-count of the rest and discards the others. A Librarian spend adds as many cards as keep slots, so
     * it never changes the answer. [taken]: the manual picks already taken from the pack (a Custom booster's pass).
     */
    fun keepsOnly(cards: Int, keep: Long, taken: Int = 0): Boolean = cards <= keep + burnAt(taken) + discardAt

    fun validate(players: Int): List<DraftSettingsIssue> = buildList {
        if (sideboardLimit != null && sideboardLimit < 0) add(DraftSettingsIssue.SIDEBOARD_LIMIT)
        if (librarians !in 0..10 || !mode.packPassing && librarians != 0) add(DraftSettingsIssue.LIBRARIANS)
        if (players !in mode.players) add(DraftSettingsIssue.PLAYERS)
        if (mode.limited) {
            when (mode) {
                DraftMode.ROTISSERIE -> {
                    if (cardsPerPlayer !in 5..225) add(DraftSettingsIssue.CARDS_PER_PLAYER)
                    if (poolPerPlayer != null && (poolPerPlayer !in 5..225 || poolPerPlayer < cardsPerPlayer))
                        add(DraftSettingsIssue.POOL_PER_PLAYER)
                }
                DraftMode.SEALED -> {
                    if (cardsPerPlayer !in 5..225) add(DraftSettingsIssue.CARDS_PER_PLAYER)
                    if (packsPerPlayer !in 1..30) add(DraftSettingsIssue.PACKS_PER_PLAYER)
                }
                else -> {
                    if (packsPerPlayer !in 1..15) add(DraftSettingsIssue.PACKS_PER_PLAYER)
                    if (cardsPerPack !in 5..30) add(DraftSettingsIssue.CARDS_PER_PACK)
                    if (mode == DraftMode.WINSTON && piles !in 2..6 || mode == DraftMode.WINCHESTER && piles !in 2..8)
                        add(DraftSettingsIssue.PILES)
                    if ((mode == DraftMode.WINSTON || mode == DraftMode.WINCHESTER) && tables !in 1..mode.players.last) add(DraftSettingsIssue.TABLES)
                    if (mode == DraftMode.ROCHESTER && tablePacksOpen != null && tablePacksOpen !in 1..packsPerPlayer * DraftMode.ROCHESTER.players.last)
                        add(DraftSettingsIssue.PACKS_OPEN_AT_ONCE)
                }
            }
            return@buildList
        }
        if (custom) {
            // DRF-262: 1–30 passes, each picking 1–30 and burning 0 or more, at most 30 cards together
            if (passes.size !in 1..DraftPass.MAX || passes.any { it.pick < 1 || it.burn < 0 || it.pick + it.burn > DraftPass.MAX })
                add(DraftSettingsIssue.PASSES)
            if (packsPerPlayer !in 1..30) add(DraftSettingsIssue.PACKS_PER_PLAYER)
            if (cardsPerPack !in 1..30) add(DraftSettingsIssue.CARDS_PER_PACK)
            if (packsOpenAtOnce !in 1..packsPerPlayer) add(DraftSettingsIssue.PACKS_OPEN_AT_ONCE)
            return@buildList
        }
        if (mode == DraftMode.ARENA) {
            // DRF-281, DRF-282: picks 1–60, offers of 2–15 cards, taking fewer than an offer holds
            if (packsPerPlayer !in 1..ArenaEngine.MAX_PICKS) add(DraftSettingsIssue.PACKS_PER_PLAYER)
            if (cardsPerPack !in 2..ArenaEngine.MAX_OFFER) add(DraftSettingsIssue.CARDS_PER_PACK)
            if (keep < 1 || keep >= cardsPerPack) add(DraftSettingsIssue.KEEP)
            if (burn != 0) add(DraftSettingsIssue.BURN)
            if (discardRemainingAt != 0) add(DraftSettingsIssue.DISCARD_REMAINING_AT)
            if (packsOpenAtOnce != 1) add(DraftSettingsIssue.PACKS_OPEN_AT_ONCE)
            if (rareEvery != null && rareEvery !in 1..ArenaEngine.MAX_PICKS ||
                uncommonEvery != null && uncommonEvery !in 1..ArenaEngine.MAX_PICKS) add(DraftSettingsIssue.RARITY_SCHEDULE)
            if (mythicOneIn != null && mythicOneIn !in 1..ArenaEngine.MAX_MYTHIC_ONE_IN) add(DraftSettingsIssue.MYTHIC_CHANCE)
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

    /** Rotisserie: the cards per player laid out in the pool ([poolPerPlayer], else [cardsPerPlayer]). */
    val rotisseriePool: Int get() = poolPerPlayer ?: cardsPerPlayer

    val rounds: Int get() = if (mode == DraftMode.GRID) (grids + gridsOpenAtOnce - 1) / gridsOpenAtOnce
        else (packsPerPlayer + packsOpenAtOnce - 1) / packsOpenAtOnce

    fun gridRefills(players: Int): Boolean = players >= 3 || twoPicks

    companion object {
        fun booster() = DraftSettings()
        /** Custom booster's default passes (DRF-262): pick 1, burn 1, seven times (a 15-card pack's last card is thrown away). */
        fun customBooster() = DraftSettings(mode = DraftMode.CUSTOM_BOOSTER, passes = List(7) { DraftPass(1, 1) })
        /** The Glimpse preset of Booster every account starts with (DRF-020, DRF-031). */
        fun glimpse() = DraftSettings(packsPerPlayer = 9, burn = 2)
        fun grid() = DraftSettings(mode = DraftMode.GRID)
        fun rochester() = DraftSettings(mode = DraftMode.ROCHESTER)
        fun rotisserie() = DraftSettings(mode = DraftMode.ROTISSERIE)
        fun winston() = DraftSettings(mode = DraftMode.WINSTON, piles = 3)
        fun winchester() = DraftSettings(mode = DraftMode.WINCHESTER, piles = 4)
        fun sealed() = DraftSettings(mode = DraftMode.SEALED, packsPerPlayer = 6)
        /** Arena's defaults (DRF-281): 45 picks of offers of 5 cards, taking 1. */
        fun arena() = DraftSettings(mode = DraftMode.ARENA, packsPerPlayer = 45, cardsPerPack = 5, keep = 1)
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
            DraftMode.CUSTOM_BOOSTER -> customBooster()
            DraftMode.ARENA -> arena()
        }
    }
}

@Serializable enum class DraftSettingsIssue {
    PLAYERS, PACKS_PER_PLAYER, CARDS_PER_PACK, KEEP, BURN, KEEP_AND_BURN,
    DISCARD_REMAINING_AT, PACKS_OPEN_AT_ONCE, SIDEBOARD_LIMIT,
    GRIDS, TWO_PICKS, GRIDS_OPEN_AT_ONCE, LIBRARIANS, CARDS_PER_PLAYER, PILES, TABLES,
    /** Rotisserie: the pool per player is out of range or smaller than the picks per player. */
    POOL_PER_PLAYER,
    /** Custom booster: no passes, too many, or one out of range (DRF-262). */
    PASSES,
    /** Arena: a rarity interval out of range (DRF-281). */
    RARITY_SCHEDULE,
    /** Arena: the mythic chance out of range (DRF-282). */
    MYTHIC_CHANCE,
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
    else if (mode == DraftMode.ARENA) ArenaEngine.needed(this)
    else players.toLong() * packsPerPlayer * cardsPerPack,
    cube.sumOf { it.quantity.toLong() },
)
