package org.asyncttwiz.android

internal data class HistorySpatialEntry(val range: HistoryRange, val left: Float, val top: Float,
    val right: Float, val bottom: Float)

/** Cold worker-built bounding volume tree. Pan frames query intersecting chips/edges only. */
internal class HistorySpatialIndex(entries: List<HistorySpatialEntry>) {
    private class Node(val left: Float, val top: Float, val right: Float, val bottom: Float,
        val entry: HistorySpatialEntry? = null, val a: Node? = null, val b: Node? = null)
    private fun build(entries: List<HistorySpatialEntry>, depth: Int): Node? {
        if (entries.isEmpty()) return null
        val left = entries.minOf { it.left }; val top = entries.minOf { it.top }
        val right = entries.maxOf { it.right }; val bottom = entries.maxOf { it.bottom }
        if (entries.size == 1) return Node(left, top, right, bottom, entries.single())
        val sorted = entries.sortedBy { if (depth % 2 == 0) it.left + it.right else it.top + it.bottom }
        val middle = sorted.size / 2
        return Node(left, top, right, bottom, a = build(sorted.subList(0, middle), depth + 1),
            b = build(sorted.subList(middle, sorted.size), depth + 1))
    }
    private val root = build(entries, 0)
    var visits = 0; private set
    fun query(left: Float, top: Float, right: Float, bottom: Float): List<HistoryRange> {
        visits = 0
        val found = mutableListOf<HistoryRange>()
        fun visit(node: Node?) {
            node ?: return
            visits++
            if (node.right < left || node.left > right || node.bottom < top || node.top > bottom) return
            node.entry?.let { found.add(it.range) }
            visit(node.a); visit(node.b)
        }
        visit(root)
        return found
    }
}
