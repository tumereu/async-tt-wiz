package org.asyncttwiz.android

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import kotlin.math.ceil

/**
 * TBL-124 (owner gallery review 2026-11-06): which chips share a row when chips of [widths] (in card order) wrap into rows
 * [rowWidth] wide with [gap] between neighbours. The fewest rows, in a stable order: card order wrapped as it comes when
 * that already takes the fewest rows there can be (all the widths together ÷ the row width, rounded up); otherwise an
 * exact search over which chips share a row, choosing among the layouts with the fewest rows the one closest to card
 * order (the fewest chip pairs read out of order, then the earliest chips on the earliest rows). Deterministic: the same
 * widths always give the same rows. Each row lists its chips' indices in card order; a chip wider than a row has one of
 * its own.
 */
object ChipRows {
    /** More chips than this (never seen on one card) keep card order: the search would take too long. */
    const val MOST_SEARCHED = 9

    fun rows(widths: List<Float>, rowWidth: Float, gap: Float): List<List<Int>> {
        if (widths.isEmpty()) return emptyList()
        val sizes = widths.map { it.coerceIn(0f, rowWidth) }
        val ordered = inOrder(sizes, rowWidth, gap)
        // A row of m chips takes their widths and m - 1 gaps: k rows hold at most k × (rowWidth + gap) of width + gap
        val fewest = ceil((sizes.sum() + sizes.size * gap) / (rowWidth + gap) - 1e-4f).toInt().coerceAtLeast(1)
        if (ordered.size <= fewest || sizes.size > MOST_SEARCHED) return ordered
        for (count in fewest until ordered.size) closest(sizes, rowWidth, gap, count)?.let { return it }
        return ordered
    }

    /** Card order, each chip on the current row while it fits, else on a new one. */
    private fun inOrder(sizes: List<Float>, rowWidth: Float, gap: Float): List<List<Int>> {
        val rows = mutableListOf<MutableList<Int>>()
        var used = 0f
        for ((index, size) in sizes.withIndex()) {
            if (rows.isEmpty() || used + gap + size > rowWidth + 1e-3f) { rows += mutableListOf(index); used = size }
            else { rows.last() += index; used += gap + size }
        }
        return rows
    }

    /**
     * The layout in exactly [count] rows (none empty) closest to card order, or null when the chips don't fit in that many:
     * each chip in turn tried on each row (lowest first), so the first layout found with the fewest out-of-order pairs is
     * also the one with the earliest chips on the earliest rows.
     */
    private fun closest(sizes: List<Float>, rowWidth: Float, gap: Float, count: Int): List<List<Int>>? {
        val row = IntArray(sizes.size)
        val used = FloatArray(count)
        val members = IntArray(count)
        var best: IntArray? = null
        var bestCost = Int.MAX_VALUE
        fun place(index: Int, cost: Int, empty: Int) {
            if (cost >= bestCost) return
            if (index == sizes.size) {
                if (empty == 0) { best = row.copyOf(); bestCost = cost }
                return
            }
            // Too few chips left to fill the empty rows
            if (sizes.size - index < empty) return
            for (r in 0 until count) {
                val width = if (members[r] == 0) sizes[index] else used[r] + gap + sizes[index]
                if (width > rowWidth + 1e-3f) continue
                // Each earlier chip on a later row is one pair read out of order
                var added = 0
                for (earlier in 0 until index) if (row[earlier] > r) added++
                val before = used[r]
                row[index] = r; used[r] = width; members[r]++
                place(index + 1, cost + added, if (members[r] == 1) empty - 1 else empty)
                members[r]--; used[r] = before
            }
        }
        place(0, 0, count)
        val found = best ?: return null
        return (0 until count).map { r -> sizes.indices.filter { found[it] == r } }
    }
}

/**
 * The chips in [content] (one child each, in card order) in rows by [ChipRows]: the fewest rows in a stable order, each
 * row centred, [gap] between neighbouring chips and between rows.
 */
@Composable
fun ChipRowsLayout(gap: Dp, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Layout(content, modifier) { measurables, constraints ->
        val space = gap.roundToPx()
        val placeables = measurables.map { it.measure(Constraints(maxWidth = constraints.maxWidth)) }
        val rowWidth = if (constraints.hasBoundedWidth) constraints.maxWidth else placeables.sumOf { it.width + space }
        val rows = ChipRows.rows(placeables.map { it.width.toFloat() }, rowWidth.toFloat(), space.toFloat())
        val widths = rows.map { r -> r.sumOf { placeables[it].width } + space * (r.size - 1) }
        val heights = rows.map { r -> r.maxOf { placeables[it].height } }
        val width = (widths.maxOrNull() ?: 0).coerceIn(constraints.minWidth, constraints.maxWidth)
        val height = (heights.sum() + space * (rows.size - 1).coerceAtLeast(0)).coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(width, height) {
            var y = 0
            rows.forEachIndexed { n, r ->
                var x = (width - widths[n]) / 2
                for (index in r) {
                    val p = placeables[index]
                    p.place(x, y + (heights[n] - p.height) / 2)
                    x += p.width + space
                }
                y += heights[n] + space
            }
        }
    }
}
