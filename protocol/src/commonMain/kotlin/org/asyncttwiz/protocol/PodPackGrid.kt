package org.asyncttwiz.protocol

import kotlinx.serialization.Serializable

/**
 * The pack grid (multiset drafting, DRF-173): which set each pack of a set pod comes from. One row per round and one cell
 * per pack of that round, sized by the pod's settings; the grid read row by row is each player's packs in the order they
 * are opened (Grid: its grids, Sealed: its boosters), the same set for every player.
 *
 * Pods and presets keep it as that list, normalized ([normalize]): never longer than the grid, trailing repeats dropped,
 * so a pod of one set is just `[code]`; a cell past the list's end takes its last set ([cells]). Settings changes only
 * resize it: a grid that grows repeats the last set, one that shrinks drops its last packs.
 */
object PodPackGrid {
    /** The longest list a pod or preset sends: Grid's most grids (DRF-023). */
    const val MAX_PACKS = 60

    /**
     * The grid's row lengths: Booster one row per round of open packs; Grid one per round of open grids; Rochester one per
     * pack each player opens; Winston, Winchester and Sealed one row of every pack (their packs are all opened at once).
     * Null when the mode takes one set for the whole pod (Rotisserie opens boosters until its pool is full).
     */
    fun rows(settings: DraftSettings): List<Int>? = when (settings.mode) {
        DraftMode.BOOSTER, DraftMode.GLIMPSE -> chunks(settings.packsPerPlayer, settings.packsOpenAtOnce)
        DraftMode.GRID -> chunks(settings.grids, settings.gridsOpenAtOnce)
        DraftMode.ROCHESTER -> List(settings.packsPerPlayer.coerceAtLeast(1)) { 1 }
        DraftMode.WINSTON, DraftMode.WINCHESTER, DraftMode.SEALED -> listOf(settings.packsPerPlayer.coerceAtLeast(1))
        DraftMode.ROTISSERIE -> null
    }

    private fun chunks(total: Int, size: Int): List<Int> {
        val count = total.coerceAtLeast(1)
        val width = size.coerceIn(1, count)
        return List((count + width - 1) / width) { minOf(width, count - it * width) }
    }

    /** How many cells the grid has (1 without a grid). */
    fun count(settings: DraftSettings): Int = rows(settings)?.sum() ?: 1

    /** Each cell's set, the grid read row by row: [packSets] cut to the grid, or carried on with its last set. */
    fun cells(packSets: List<String>, settings: DraftSettings): List<String> {
        require(packSets.isNotEmpty()) { "No sets" }
        return List(count(settings)) { packSets.getOrElse(it) { packSets.last() } }
    }

    /** The stored form of [packSets] for [settings]: cut to the grid, trailing repeats dropped. */
    fun normalize(packSets: List<String>, settings: DraftSettings): List<String> {
        val cells = cells(packSets, settings).toMutableList()
        while (cells.size > 1 && cells[cells.size - 1] == cells[cells.size - 2]) cells.removeAt(cells.lastIndex)
        return cells
    }

    /** Whether the packs come from more than one set. */
    fun mixed(packSets: List<String>): Boolean = packSets.distinct().size > 1

    /** The cell index of [row]'s pack [column] (both zero-based). */
    fun index(settings: DraftSettings, row: Int, column: Int): Int = rows(settings).orEmpty().take(row).sum() + column

    /** Cell [index] (the grid read row by row) set to [code]. */
    fun setCell(packSets: List<String>, settings: DraftSettings, index: Int, code: String): List<String> =
        set(packSets, settings, setOf(index), code)

    /** Every pack of round [row] set to [code] (a row label, "Round 2"). */
    fun setRow(packSets: List<String>, settings: DraftSettings, row: Int, code: String): List<String> {
        val rows = rows(settings) ?: return listOf(code)
        if (row !in rows.indices) return normalize(packSets, settings)
        val start = rows.take(row).sum()
        return set(packSets, settings, (start until start + rows[row]).toSet(), code)
    }

    /** Pack [column] of every round set to [code] (a column label, "Pack 1"); rounds without that pack are left alone. */
    fun setColumn(packSets: List<String>, settings: DraftSettings, column: Int, code: String): List<String> {
        val rows = rows(settings) ?: return listOf(code)
        var start = 0
        val indices = rows.mapNotNull { length -> (start + column).takeIf { column in 0 until length }.also { start += length } }
        return set(packSets, settings, indices.toSet(), code)
    }

    private fun set(packSets: List<String>, settings: DraftSettings, indices: Set<Int>, code: String): List<String> =
        normalize(cells(packSets, settings).mapIndexed { index, set -> if (index in indices) code else set }, settings)

    /**
     * The set of each pack an engine deals, in its order, for [players]: Booster, Rochester, Winston and Winchester deal
     * every player's first pack, then every player's second (pack i is each player's pack i / players); Sealed deals each
     * player's boosters together (booster i % boosters); Grid one per grid. Rotisserie has one set.
     */
    fun packSets(packSets: List<String>, settings: DraftSettings, players: Int): List<String> {
        val cells = cells(packSets, settings)
        return when (settings.mode) {
            DraftMode.GRID -> cells
            DraftMode.ROTISSERIE -> cells
            DraftMode.SEALED -> List(players * settings.packsPerPlayer) { cells[it % settings.packsPerPlayer] }
            else -> List(players * settings.packsPerPlayer) { cells[it / players] }
        }
    }

    /** Whether [packSets] is a list of set codes a pod or preset may send. */
    fun valid(packSets: List<String>): Boolean = packSets.size in 1..MAX_PACKS && packSets.all(PodPresetValidation::setCode)
}

/**
 * A set pod's source, server-only: the pack grid read row by row ([packs], normalized as [PodPackGrid.normalize]) and the
 * snapshot of every set it names, each kept when it was chosen. The engines build each pack from its own set.
 */
@Serializable data class SetPackSources(val packs: List<String>, val sets: List<SetSourceSnapshot>) {
    /** Set [code]'s snapshot. */
    fun set(code: String): SetSourceSnapshot = sets.first { it.setCode == code }

    /** The set the pod's first pack comes from: what a pod of one set is named by. */
    val first: SetSourceSnapshot get() = set(packs.first())

    companion object {
        /** One set for every pack. */
        fun of(set: SetSourceSnapshot) = SetPackSources(listOf(set.setCode), listOf(set))
    }
}
