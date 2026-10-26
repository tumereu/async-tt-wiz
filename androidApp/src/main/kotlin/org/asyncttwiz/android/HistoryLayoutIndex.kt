package org.asyncttwiz.android

import kotlinx.collections.immutable.*
import org.asyncttwiz.protocol.*

internal data class HistoryLayoutBoundary(val chip: String, val offset: Int, val turn: HistoryTurnStart)
internal data class HistoryLayoutChip(val id: String, val group: String, val branch: String,
    val parent: String?, val column: Int, val lane: Int, val nodes: PersistentList<String>,
    val turnStart: HistoryTurnStart?) {
    val position get() = HistoryPoint(24f + column * 24f, 24f + lane * 36f)
    val geometry by lazy { HistoryChipGeometry(id, nodes.last(), parent, position, turnStart) }
    @Volatile var presentation: Pair<HistoryRange, HistoryTimelineEvent>? = null
}
internal data class HistoryLayoutBranch(val lane: Int, val fork: String?,
    val authors: PersistentMap<String, Int> = persistentMapOf(), val reveals: Int = 0, val comments: Int = 0) {
    val simple get() = authors.size == 1 && reveals == 0 && comments == 0
    fun change(node: GameplayMutation, delta: Int): HistoryLayoutBranch {
        val count = (authors[node.author] ?: 0) + delta
        return copy(authors = if (count == 0) authors.remove(node.author) else authors.put(node.author, count),
            reveals = reveals + if (node.revealsInformation) delta else 0,
            comments = comments + if (node.action is PlayAction.EventComment) delta else 0)
    }
}

/** Immutable ID-only geometry, edited in HistoryIndex's existing parent-first ingestion pass.
 * Trie/vector edits have fixed bounded depth; neither chips nor lanes are re-numbered. */
internal data class HistoryLayoutIndex(
    val boundaries: PersistentMap<String, HistoryLayoutBoundary>,
    val chips: PersistentMap<String, HistoryLayoutChip>,
    val branches: PersistentMap<String, HistoryLayoutBranch>,
    val lanes: PersistentMap<Int, PersistentMap<Int, String>>,
    val forks: HistoryForkIndex = HistoryForkIndex(),
    val nextLane: Int = 1, val maxColumn: Int = 0,
    /** Number of non-simple branches kept through each branch's fork ancestry (including itself). */
    val keptAncestry: PersistentMap<String, Int> = persistentMapOf(),
) {
    private fun keepAncestors(branchId: String, delta: Int, topology: HistoryLayoutIndex = this): HistoryLayoutIndex {
        var kept = keptAncestry
        var cursor: String? = branchId
        while (cursor != null) {
            val count = (kept[cursor] ?: 0) + delta
            check(count >= 0) { "Unbalanced kept branch ancestry" }
            kept = if (count == 0) kept.remove(cursor) else kept.put(cursor, count)
            cursor = topology.branches.getValue(cursor).fork?.let { fork ->
                topology.boundaries[fork]?.let { topology.chips.getValue(it.chip).branch }
            }
        }
        return copy(keptAncestry = kept)
    }

    private val HistoryLayoutBranch.kept get() = authors.isNotEmpty() && !simple

    fun append(node: GameplayMutation, group: String): HistoryLayoutIndex {
        val parentBoundary = checkNotNull(boundaries[node.parentId]) { "Unavailable retained ancestry" }
        val parent = chips.getValue(parentBoundary.chip)
        val reserved = branches[node.branchId]
        val branch = if (reserved != null && reserved.authors.isEmpty() && reserved.fork != null)
            reserved.copy(fork = node.parentId) else reserved ?: HistoryLayoutBranch(nextLane, node.parentId)
        val turn = HistoryTurnStart(
            node.transition.fields.lastOrNull { it.field == GameplayField.TURN_NUMBER }?.to ?: parentBoundary.turn.number,
            node.transition.fields.lastOrNull { it.field == GameplayField.TURN_PLAYER }?.to ?: parentBoundary.turn.player)
        val joins = parent.parent != null && parent.branch == node.branchId && parent.group == group &&
            parent.nodes.last() == node.parentId && turn == parentBoundary.turn
        val chip = if (joins) parent.copy(nodes = parent.nodes.add(node.id)) else
            HistoryLayoutChip(node.id, group, node.branchId, parent.id, parent.column + 1, branch.lane,
                persistentListOf(node.id), turn.takeIf { it != parentBoundary.turn })
        val isFork = !joins && chip.lane != parent.lane
        val changed = branch.change(node, 1)
        val result = copy(boundaries = boundaries.put(node.id, HistoryLayoutBoundary(chip.id, chip.nodes.lastIndex, turn)),
            chips = chips.put(chip.id, chip), branches = branches.put(node.branchId, changed),
            lanes = if (joins) lanes else lanes.put(chip.lane, (lanes[chip.lane] ?: persistentMapOf()).put(chip.column, chip.id)),
            forks = if (isFork) forks.put(parent.column, chip.lane, parent.lane, chip.id) else forks,
            nextLane = if (node.branchId in branches) nextLane else nextLane + 1,
            maxColumn = maxOf(maxColumn, chip.column))
        return if (branch.kept != changed.kept) result.keepAncestors(node.branchId, if (changed.kept) 1 else -1) else result
    }

    /** All descendants of changed records are supplied by the existing topology index. */
    fun remove(affected: Set<String>, oldNodes: Map<String, GameplayMutation>): HistoryLayoutIndex {
        var result = this
        val touched = mutableSetOf<String>()
        for (id in affected) {
            val boundary = result.boundaries[id] ?: continue
            val node = oldNodes[id] ?: continue
            touched.add(boundary.chip)
            val prior = result.branches.getValue(node.branchId)
            val branch = prior.change(node, -1)
            if (prior.kept != branch.kept)
                result = result.keepAncestors(node.branchId, if (branch.kept) 1 else -1, topology = this)
            result = result.copy(boundaries = result.boundaries.remove(id),
                // Keep the lane reservation across a pop/redo or a rewritten suffix as well.
                branches = result.branches.put(node.branchId, branch))
        }
        for (id in touched) {
            val chip = result.chips.getValue(id)
            var nodes = chip.nodes
            while (nodes.isNotEmpty() && nodes.last() in affected) nodes = nodes.removeAt(nodes.lastIndex)
            if (nodes.isNotEmpty()) result = result.copy(chips = result.chips.put(id, chip.copy(nodes = nodes)))
            else {
                val parent = result.chips[chip.parent] ?: chips[chip.parent]
                result = result.copy(chips = result.chips.remove(id),
                    lanes = result.lanes.put(chip.lane, result.lanes.getValue(chip.lane).remove(chip.column)),
                    forks = if (parent != null && parent.lane != chip.lane)
                        result.forks.remove(parent.column, chip.lane) else result.forks)
            }
        }
        return result
    }

    /** One pair per branch in the ancestry, never one entry per event. */
    fun path(nodeId: String): Map<String, HistoryLayoutBoundary> {
        val result = mutableMapOf<String, HistoryLayoutBoundary>()
        var cursor: String? = nodeId
        while (cursor != null) {
            val boundary = boundaries[cursor] ?: break
            val chip = chips.getValue(boundary.chip)
            check(result.put(chip.branch, boundary) == null) { "Cyclic branch ancestry" }
            cursor = branches.getValue(chip.branch).fork
        }
        return result
    }

    companion object {
        fun root(initial: PlayState): HistoryLayoutIndex {
            return root(initial.root.id, initial.root.branchId, HistoryTurnStart(initial.board.turn.number, initial.board.turn.accountId))
        }
        fun root(id: String, branch: String, turn: HistoryTurnStart = HistoryTurnStart("1", "")): HistoryLayoutIndex {
            val chip = HistoryLayoutChip(id, id, branch, null, 0, 0, persistentListOf(id), turn)
            return HistoryLayoutIndex(persistentMapOf(id to HistoryLayoutBoundary(id, 0, turn)), persistentMapOf(id to chip),
                persistentMapOf(branch to HistoryLayoutBranch(0, null)), persistentMapOf(0 to persistentMapOf(0 to id)))
        }
    }
}

/** Vertical fork lines can cross a viewport whose endpoint chips are both offscreen.
 * Per-column interval tries use 31 fixed levels, so inserting/removing a fork is bounded too. */
internal data class HistoryForkIndex(private val columns: PersistentMap<Int, Node> = persistentMapOf()) {
    internal data class Node(val minStart: Int, val maxEnd: Int, val id: String? = null, val a: Node? = null, val b: Node? = null)
    private fun edit(node: Node?, low: Int, high: Int, lane: Int, start: Int, id: String?): Node? {
        if (low == high) return id?.let { Node(minOf(start, lane), maxOf(start, lane), it) }
        val middle = low + (high - low) / 2
        val a = if (lane <= middle) edit(node?.a, low, middle, lane, start, id) else node?.a
        val b = if (lane > middle) edit(node?.b, middle + 1, high, lane, start, id) else node?.b
        return if (a == null && b == null) null else Node(minOf(a?.minStart ?: Int.MAX_VALUE, b?.minStart ?: Int.MAX_VALUE),
            maxOf(a?.maxEnd ?: 0, b?.maxEnd ?: 0), a = a, b = b)
    }
    fun put(column: Int, lane: Int, start: Int, id: String) =
        HistoryForkIndex(columns.put(column, edit(columns[column], 0, Int.MAX_VALUE, lane, start, id)!!))
    fun remove(column: Int, lane: Int): HistoryForkIndex {
        val root = edit(columns[column], 0, Int.MAX_VALUE, lane, 0, null)
        return HistoryForkIndex(if (root == null) columns.remove(column) else columns.put(column, root))
    }
    fun query(column: Int, top: Int, bottom: Int, visit: () -> Unit, found: (String) -> Unit) {
        fun walk(node: Node?, low: Int, high: Int) {
            node ?: return
            visit()
            if (node.maxEnd < top || node.minStart > bottom) return
            node.id?.let { found(it); return }
            val middle = low + (high - low) / 2
            walk(node.a, low, middle); walk(node.b, middle + 1, high)
        }
        walk(columns[column], 0, Int.MAX_VALUE)
    }
}
