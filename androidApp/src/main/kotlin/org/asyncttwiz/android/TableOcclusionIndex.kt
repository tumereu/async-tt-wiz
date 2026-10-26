package org.asyncttwiz.android

import kotlin.math.floor

/** Visible fragments only, bucketed in screen dp. Candidates retain painter order so subtraction
 * produces exactly the same fragments as the full scene scan (including Stack pile exceptions). */
internal class TableOcclusionIndex(private val items: List<TableItem>) {
    private val cells = HashMap<Long, MutableList<Int>>()

    init {
        items.forEachIndexed { index, item ->
            if (item.kind != TableItemKind.HAND) for (part in item.visible) cells(part) { key ->
                cells.getOrPut(key) { mutableListOf() }.add(index)
            }
        }
    }

    fun candidates(index: Int): List<Int> {
        val item = items[index]
        if (item.visible.isEmpty() || item.kind == TableItemKind.HAND) return emptyList()
        val candidates = HashSet<Int>()
        for (part in item.visible) cells(part) { key ->
            cells[key]?.forEach { if (it > index) candidates.add(it) }
        }
        return candidates.sorted()
    }

    private inline fun cells(bounds: TableBounds, visit: (Long) -> Unit) {
        if (bounds.width <= 0 || bounds.height <= 0) return
        val left = floor(bounds.x / CELL_SIZE).toInt()
        val right = floor(bounds.right / CELL_SIZE).toInt()
        val top = floor(bounds.y / CELL_SIZE).toInt()
        val bottom = floor(bounds.bottom / CELL_SIZE).toInt()
        for (x in left..right) for (y in top..bottom) visit((x.toLong() shl 32) xor (y.toLong() and 0xffffffffL))
    }

    private companion object { const val CELL_SIZE = 48f }
}
