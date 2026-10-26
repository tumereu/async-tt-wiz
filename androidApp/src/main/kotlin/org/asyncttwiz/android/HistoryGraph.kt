package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import kotlin.math.ceil
import kotlin.math.floor

data class HistoryChipGeometry(val id: String, val targetId: String, val parentRangeId: String?,
    val position: HistoryPoint, val turnStart: HistoryTurnStart?)

/** A lightweight view of an already-ingested index. Only viewport queries materialize chips/text. */
class HistoryGraph internal constructor(private val tree: GameplayTree, private val layout: HistoryLayoutIndex,
    private val showHidden: Boolean = true, selected: String = tree.currentNodeId,
    val width: Float = (layout.maxColumn + 2) * 24f, val height: Float = layout.nextLane * 36f + 48f) {
    val currentNodeId = tree.currentNodeId
    private val active = layout.path(currentNodeId)
    private var selectedPath = layout.path(selected)
    var nodeVisits = active.size + selectedPath.size; private set
    var spatialVisits = 0; private set
    private val exactEvents = object : LinkedHashMap<String, HistoryTimelineEvent>(128, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, HistoryTimelineEvent>?) = size > 128
    }
    internal fun select(id: String) { selectedPath = layout.path(id); nodeVisits += selectedPath.size }
    private fun onPath(chip: HistoryLayoutChip, path: Map<String, HistoryLayoutBoundary>): Boolean {
        val end = path[chip.branch] ?: return false
        return chip.column <= layout.chips.getValue(end.chip).column
    }
    private fun included(chip: HistoryLayoutChip) = showHidden || !layout.branches.getValue(chip.branch).simple ||
        onPath(chip, active) || onPath(chip, selectedPath)
    private fun chipOf(id: String): HistoryLayoutChip? = layout.boundaries[id]?.let { layout.chips[it.chip] }?.takeIf(::included)
    private fun nodeOnPath(id: String): Boolean {
        val boundary = layout.boundaries[id] ?: return false
        val chip = layout.chips.getValue(boundary.chip)
        val end = active[chip.branch] ?: return false
        val last = layout.chips.getValue(end.chip)
        return chip.column < last.column || (chip.column == last.column && boundary.offset <= end.offset)
    }
    private fun event(id: String): HistoryTimelineEvent {
        nodeVisits++
        val node = tree.nodes[id]
        val text = historyText(node)
        val icon = node?.grouping?.icon ?: node?.let {
            HistoryGrouping.classify(it.action, tree.initial.board.containers, it.transition).icon
        } ?: "flag"
        return HistoryTimelineEvent(id, icon, text.description, node?.author,
            layout.boundaries[id]?.let { layout.chips.getValue(it.chip).group }, label = text.label)
    }
    private fun range(chip: HistoryLayoutChip): HistoryRange {
        chip.presentation?.let { return it.first }
        val last = chip.nodes.last()
        val event = event(last)
        val range = HistoryRange(chip.id, chip.group, chip.nodes, last.takeIf { chip.parent != null },
            HistoryGlyph.fromIcon(event.icon), event.author, chip.parent, chip.position, chip.turnStart)
        chip.presentation = range to event
        return range
    }
    fun positionOfRange(id: String?) = layout.chips[id]?.position
    fun isActive(id: String) = layout.chips[id]?.let { onPath(it, active) } == true
    fun isHead(id: String): Boolean {
        val chip = layout.chips[id] ?: return false
        val head = tree.branchHeads[chip.branch]
        return (head != null && layout.boundaries[head]?.chip == id) ||
            tree.endpointIndex().children[chip.nodes.last()].isNullOrEmpty()
    }

    fun visible(x: Float, y: Float, width: Float, height: Float): List<HistoryChipGeometry> {
        spatialVisits = 0
        val left = floor((x - 48f) / 24f).toInt().coerceAtLeast(0)
        val right = ceil((x + width) / 24f).toInt().coerceAtMost(layout.maxColumn)
        val top = floor((y - 40f) / 36f).toInt().coerceAtLeast(0)
        val bottom = ceil((y + height + 8f) / 36f).toInt().coerceAtMost(layout.nextLane - 1)
        val found = linkedSetOf<String>()
        for (lane in top..bottom) {
            val columns = layout.lanes[lane] ?: continue
            for (column in left..right) {
                spatialVisits++
                columns[column]?.let(found::add)
            }
        }
        for (column in left..right) layout.forks.query(column, top, bottom, { spatialVisits++ }, found::add)
        return found.mapNotNull { id -> layout.chips[id]?.takeIf(::included)?.geometry }
    }

    // Compatibility/read-only accessors are lazy maps/sets: individual lookups never enumerate history.
    // Full enumeration is useful to tests, but deliberately not used by the dialog.
    val positions: Map<String, HistoryPoint> = HistoryLookup({ layout.boundaries.keys }, { id -> chipOf(id)?.position })
    val rangeOf: Map<String, String> = HistoryLookup({ layout.boundaries.keys }, { id -> chipOf(id)?.id })
    val rangesById: Map<String, HistoryRange> = HistoryLookup({ layout.chips.keys }, { id -> layout.chips[id]?.takeIf(::included)?.let(::range) })
    val eventsById: Map<String, HistoryTimelineEvent> = HistoryLookup({ layout.boundaries.keys }, { id ->
        chipOf(id)?.let { chip ->
            if (id == chip.nodes.last()) { range(chip); chip.presentation!!.second }
            else exactEvents.getOrPut(id) { event(id) }
        }
    })
    val ranges get() = rangesById.values.toList()
    val events get() = eventsById.values.toList()
    val activeNodeIds: Set<String> = HistoryLookup({ layout.boundaries.keys }, { id -> Unit.takeIf { nodeOnPath(id) } }).keys
    val activeRanges: Set<String> = HistoryLookup({ layout.chips.keys }, { id -> Unit.takeIf { isActive(id) } }).keys
    val headRanges: Set<String> = HistoryLookup({ layout.chips.keys }, { id -> Unit.takeIf { isHead(id) } }).keys
    val branchHeadIds get() = tree.branchHeads.values.toSet()
    fun copy(width: Float = this.width, height: Float = this.height) = HistoryGraph(tree, layout, showHidden, width = width, height = height)
    fun pan(current: HistoryPoint, dx: Float, dy: Float, viewportWidth: Float, viewportHeight: Float) =
        HistoryPoint((current.x + dx).coerceIn(-width, viewportWidth), (current.y + dy).coerceIn(-height, viewportHeight))
    fun centeredOn(nodeId: String, viewportWidth: Float, viewportHeight: Float): HistoryPoint {
        val point = positions.getValue(nodeId)
        return HistoryPoint(viewportWidth / 2f - point.x, viewportHeight / 2f - point.y)
    }
}

private class HistoryLookup<V>(private val ids: () -> Iterable<String>, private val lookup: (String) -> V?) : AbstractMap<String, V>() {
    override fun get(key: String): V? = lookup(key)
    override fun containsKey(key: String) = lookup(key) != null
    override val entries: Set<Map.Entry<String, V>> get() = ids().mapNotNull { id -> lookup(id)?.let { value ->
        object : Map.Entry<String, V> { override val key = id; override val value = value }
    } }.toSet()
}
