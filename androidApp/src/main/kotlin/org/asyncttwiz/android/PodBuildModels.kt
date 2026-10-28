package org.asyncttwiz.android

import org.asyncttwiz.protocol.*

internal data class PodBuildTile(
    val digest: String,
    val copyIds: List<String>,
    val definition: CardDefinition?,
    val colors: Set<DraftColor>,
)

internal fun podBuildColumns(
    build: PodDeckBuild,
    category: PodDeckCategory,
    copies: Map<String, DraftCopy>,
    definitions: Map<String, CardDefinition>,
    columns: Int,
): List<List<PodBuildTile>> {
    val tiles = build.copies.filter { it.category == category }.groupBy {
        copies[it.copyId]?.definition ?: "missing:${it.copyId}"
    }.map { (digest, entries) ->
        PodBuildTile(digest, entries.map { it.copyId }, definitions[digest],
            copies[entries.first().copyId]?.colors.orEmpty())
    }
    val grouped = tiles.groupBy { it.definition?.let { card -> PodDeckColumns.bucket(columns, card) } ?: 1 }
    return PodDeckColumns.buckets(columns).indices.map { index ->
        grouped[index].orEmpty().sortedWith { a, b ->
            val first = a.definition
            val second = b.definition
            if (first == null && second == null) a.digest.compareTo(b.digest)
            else if (first == null) 1
            else if (second == null) -1
            else PodDeckColumns.comparator { card -> if (card === first) a.colors else b.colors }.compare(first, second)
        }
    }
}

internal fun podBuildCount(build: PodDeckBuild, category: PodDeckCategory): Int =
    build.copies.count { it.category == category }

/** "Main X + Y = Z": X nonland pool cards, Y every land in Main (drafted lands and basics). */
internal fun podBuildMainTitle(build: PodDeckBuild, copies: Map<String, DraftCopy>, definitions: Map<String, CardDefinition>): String {
    val main = build.copies.filter { it.category == PodDeckCategory.MAIN }
    val drafted = main.count { entry -> copies[entry.copyId]?.definition?.let(definitions::get)?.let(::podStatsIsLand) == true }
    val lands = drafted + build.basics.total
    return "Main ${main.size - drafted} + $lands = ${main.size + build.basics.total}"
}

internal fun podBuildSubmitEnabled(
    build: PodDeckBuild, copies: Map<String, DraftCopy>, sideboardLimit: Int?, editable: Boolean, canSubmit: Boolean,
): Boolean = editable && canSubmit && PodDeckRules.validate(build, copies.keys.toList(), sideboardLimit).isEmpty()

internal fun podBuildIssueText(issue: PodDeckIssue, sideboardLimit: Int?): String = when (issue) {
    PodDeckIssue.MAIN_TOO_SMALL -> "Main needs 40 cards"
    PodDeckIssue.DECK_TOO_LARGE -> "Main + Side including basics exceeds ${DeckValidation.MAX_CARDS} cards"
    PodDeckIssue.SIDEBOARD_LIMIT -> "Side exceeds its limit of $sideboardLimit cards"
    PodDeckIssue.NEGATIVE_BASICS -> "Basic counts must be at least 0"
    PodDeckIssue.UNKNOWN_COPY -> "Deck contains a card outside your pool"
    PodDeckIssue.DUPLICATE_COPY -> "A pool copy is used more than once"
    PodDeckIssue.MISSING_COPY -> "Every pool copy needs a category"
}

internal fun PodDeckCategory.podBuildLabel(): String = when (this) {
    PodDeckCategory.MAIN -> "Main"
    PodDeckCategory.SIDE -> "Side"
    PodDeckCategory.TRASH -> "Trash"
}

internal fun PodDeckBasics.podBuildValues(): List<Int> = listOf(plains, island, swamp, mountain, forest)

internal fun PodDeckBasics.podBuildAdjust(index: Int, delta: Int): PodDeckBasics {
    val values = podBuildValues().toMutableList()
    values[index] = (values[index].toLong() + delta).coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
    return PodDeckBasics(values[0], values[1], values[2], values[3], values[4])
}
