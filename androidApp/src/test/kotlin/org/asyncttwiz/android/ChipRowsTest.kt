package org.asyncttwiz.android

import org.junit.Test
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** TBL-124 (owner gallery review 2026-11-06): ability chips take the fewest rows in a stable order close to the card's. */
class ChipRowsTest {
    private val gap = 4f

    // TBL-124: card order wrapped as it comes when that already takes the fewest rows
    @Test fun cardOrderStaysWhenItTakesTheFewestRows() {
        assertEquals(listOf(listOf(0, 1), listOf(2)), ChipRows.rows(listOf(100f, 100f, 100f), 250f, gap))
        assertEquals(listOf(listOf(0, 1, 2)), ChipRows.rows(listOf(60f, 80f, 90f), 250f, gap))
        assertEquals(listOf(listOf(0), listOf(1), listOf(2)), ChipRows.rows(listOf(200f, 200f, 200f), 250f, gap))
        assertEquals(emptyList(), ChipRows.rows(emptyList(), 250f, gap))
    }

    // TBL-124: when card order would take an extra row, the chips share rows differently, as close to card order as can be
    @Test fun fewerRowsOutOfOrderWhenCardOrderTakesMore() {
        // In order: [150] [150, 90] [90], three rows; two rows can hold them
        assertEquals(listOf(listOf(0, 2), listOf(1, 3)), ChipRows.rows(listOf(150f, 150f, 90f, 90f), 250f, gap))
        // In order: [180] [180, 60] [60]; the first short chip moves up to the first row instead
        assertEquals(listOf(listOf(0, 2), listOf(1, 3)), ChipRows.rows(listOf(180f, 180f, 60f, 60f), 250f, gap))
    }

    // TBL-124: a chip wider than a row has a row of its own
    @Test fun aChipWiderThanARowHasItsOwn() {
        assertEquals(listOf(listOf(0), listOf(1, 2)), ChipRows.rows(listOf(400f, 50f, 50f), 250f, gap))
    }

    // TBL-124: deterministic, the fewest rows there can be, every chip once, each row fitting and in card order; card order
    // whenever it already takes the fewest rows (several chip-width sets)
    @Test fun alwaysTheFewestRowsAndTheSameRowsForTheSameWidths() {
        val random = Random(2026_11_06)
        repeat(400) {
            val count = random.nextInt(1, 8)
            val widths = List(count) { listOf(40f, 60f, 90f, 120f, 150f, 170f, 210f)[random.nextInt(7)] + random.nextInt(0, 20) }
            val row = listOf(220f, 250f, 300f, 380f)[random.nextInt(4)]
            val rows = ChipRows.rows(widths, row, gap)
            assertEquals(rows, ChipRows.rows(widths, row, gap), "the same widths, the same rows")
            assertEquals(widths.indices.toList(), rows.flatten().sorted(), "every chip once: $widths")
            for (r in rows) {
                assertEquals(r.sorted(), r, "card order within a row")
                assertTrue(r.size == 1 || r.sumOf { widths[it].toDouble() } + gap * (r.size - 1) <= row + .01, "$r fits $row: $widths")
            }
            assertEquals(fewest(widths, row), rows.size, "the fewest rows for $widths in $row")
            val ordered = inOrder(widths, row)
            if (ordered.size == rows.size) assertEquals(ordered, rows, "card order when it is already the fewest")
        }
    }

    /** Card order wrapped as it comes. */
    private fun inOrder(widths: List<Float>, row: Float): List<List<Int>> {
        val rows = mutableListOf<MutableList<Int>>()
        var used = 0f
        for ((i, w) in widths.withIndex()) {
            val size = minOf(w, row)
            if (rows.isEmpty() || used + gap + size > row + 1e-3f) { rows += mutableListOf(i); used = size } else { rows.last() += i; used += gap + size }
        }
        return rows
    }

    /** The fewest rows any sharing of rows takes, by trying them all. */
    private fun fewest(widths: List<Float>, row: Float): Int {
        var best = widths.size
        fun place(i: Int, rows: MutableList<Float>) {
            if (rows.size >= best) return
            if (i == widths.size) { best = rows.size; return }
            val size = minOf(widths[i], row)
            for (r in rows.indices) if (rows[r] + gap + size <= row + 1e-3f) {
                val before = rows[r]; rows[r] = before + gap + size; place(i + 1, rows); rows[r] = before
            }
            rows += size; place(i + 1, rows); rows.removeAt(rows.lastIndex)
        }
        place(0, mutableListOf())
        return best
    }
}
