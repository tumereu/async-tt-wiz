package org.asyncttwiz.android

import org.asyncttwiz.protocol.CardDefinition
import org.asyncttwiz.protocol.PodDeckCategory

/** One pool copy in a feed; a ×N stack yields N consecutive feed cards. */
internal data class PodBuildFeedCard(val copyId: String, val digest: String, val definition: CardDefinition?, val column: Int)

/**
 * Reviewing one Build column (or a whole section) one copy at a time. [cards] is a snapshot taken when the
 * feed opens, so moves never reorder it. Each decision either keeps the copy in [category] or moves it one
 * tier lower ([target]); [demoted] records the decisions made so far, in order.
 */
internal data class PodBuildFeed(
    val category: PodDeckCategory,
    val target: PodDeckCategory,
    /** The reviewed column, or null for the whole section. */
    val column: Int?,
    val labels: List<String>,
    val cards: List<PodBuildFeedCard>,
    val demoted: List<Boolean> = emptyList(),
) {
    val position: Int get() = demoted.size
    val current: PodBuildFeedCard? get() = cards.getOrNull(position)
    val finished: Boolean get() = position >= cards.size
    val canUndo: Boolean get() = demoted.isNotEmpty()

    /** "Main · MV 2"; a section feed names the current card's column, and just the section once finished. */
    val title: String get() {
        val shown = column ?: current?.column
        val label = shown?.let(labels::getOrNull)?.let(::podBuildFeedColumnLabel)
        return if (label == null) category.podBuildLabel() else "${category.podBuildLabel()} · $label"
    }

    val progress: String get() = "${minOf(position + 1, cards.size)} / ${cards.size}"

    val summary: String get() = "Kept ${demoted.count { !it }} · ${demoted.count { it }} to ${target.podBuildLabel()}"

    fun decide(demote: Boolean): PodBuildFeed = if (finished) this else copy(demoted = demoted + demote)

    /** Back one card. The returned copy id, when present, was moved by that decision and must move back. */
    fun undo(): Pair<PodBuildFeed, String?> {
        if (!canUndo) return this to null
        val previous = copy(demoted = demoted.dropLast(1))
        return previous to cards[previous.position].copyId.takeIf { demoted.last() }
    }
}

/** The tier a feed swipe-left sends a copy to; Trash has no feed. */
internal fun podBuildFeedDemotion(category: PodDeckCategory): PodDeckCategory? = when (category) {
    PodDeckCategory.MAIN -> PodDeckCategory.SIDE
    PodDeckCategory.SIDE -> PodDeckCategory.TRASH
    PodDeckCategory.TRASH -> null
}

/** Bucket labels read as "Lands", "MV 0" … "MV 5+". */
internal fun podBuildFeedColumnLabel(label: String): String =
    if (label.firstOrNull()?.isDigit() == true) "MV $label" else label

/** A snapshot of [column] (or all columns, in display order) of [buckets]; null when there is nothing to review. */
internal fun podBuildFeed(
    category: PodDeckCategory, buckets: List<List<PodBuildTile>>, labels: List<String>, column: Int?,
): PodBuildFeed? {
    val target = podBuildFeedDemotion(category) ?: return null
    val cards = buckets.withIndex().filter { column == null || it.index == column }.flatMap { (index, tiles) ->
        tiles.flatMap { tile -> tile.copyIds.map { PodBuildFeedCard(it, tile.digest, tile.definition, index) } }
    }
    if (cards.isEmpty()) return null
    return PodBuildFeed(category, target, column, labels, cards)
}

/** The next non-empty column after [column], for the end screen's "Next" button. */
internal fun podBuildFeedNextColumn(buckets: List<List<PodBuildTile>>, column: Int): Int? =
    (column + 1 until buckets.size).firstOrNull { buckets[it].isNotEmpty() }

/** Width of each of [perRow] equal cards across [columnWidth], with [gap] between neighbours. */
internal fun podBuildCardWidth(columnWidth: Int, perRow: Int, gap: Int): Int =
    ((columnWidth - gap * (perRow - 1)) / perRow.coerceAtLeast(1)).coerceAtLeast(1)

/** Row-major (row, column) slot of the card at [index] when [perRow] cards sit side by side. */
internal fun podBuildGridSlot(index: Int, perRow: Int): Pair<Int, Int> = index / perRow to index % perRow
