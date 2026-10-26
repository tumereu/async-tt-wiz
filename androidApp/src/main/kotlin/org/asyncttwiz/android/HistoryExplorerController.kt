package org.asyncttwiz.android

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.asyncttwiz.protocol.*
import java.util.concurrent.Executor

data class HistoryPoint(val x: Float, val y: Float)
data class HistoryTurnStart(val number: String, val player: String) {
    val label get() = "T$number"
}

/** Screen-dp translation; zoom keeps the point under the gesture centroid anchored. */
internal data class HistoryTreeViewport(val pan: HistoryPoint = HistoryPoint(0f, 0f), val zoom: Float = 1f) {
    fun transform(centroid: HistoryPoint, delta: HistoryPoint, scale: Float, graph: HistoryGraph,
        width: Float, height: Float): HistoryTreeViewport {
        if (!scale.isFinite() || scale <= 0f) return this
        val nextZoom = (zoom * scale).coerceIn(.5f, 3f)
        val ratio = nextZoom / zoom
        return HistoryTreeViewport(HistoryPoint(
            (centroid.x - (centroid.x - pan.x) * ratio + delta.x).coerceIn(-graph.width * nextZoom, width),
            (centroid.y - (centroid.y - pan.y) * ratio + delta.y).coerceIn(-graph.height * nextZoom, height)), nextZoom)
    }
    fun screen(point: HistoryPoint) = HistoryPoint(point.x * zoom + pan.x, point.y * zoom + pan.y)
    fun visible(graph: HistoryGraph, width: Float, height: Float) = graph.visible(-pan.x / zoom, -pan.y / zoom, width / zoom, height / zoom)
}
enum class HistoryGlyph(val label: String, val icon: String) {
    ROOT("Initial state", "flag"), TAP("Tap / untap", "rotate_90_degrees_cw"), MOVE("Move", "open_with"), LIFE("Life", "favorite"),
    COUNTER("Counter", "exposure_plus_1"), TURN("Turn / step", "outlined_flag"), DIE("Die", "casino"),
    COIN("Coin", "monetization_on"), SHUFFLE("Shuffle", "shuffle"), VIEW("View", "pageview"),
    INSPECT("Inspect / reveal", "visibility"), DISMISS("Dismiss contents", "close"), COMMENT("Comment", "chat_bubble"),
    PING("Ping / unping", "ads_click"), RESET("Reset corner", "settings_backup_restore"), NOTE("Note", "sticky_note_2"),
    OWNER("Owner", "switch_account"), FACE("Face", "flip_camera_android"), COVER("Cover", "flip"),
    UNCOVER("Uncover", "flip_to_front"), TOKEN("Token", "add_box"), CLONE("Clone", "control_point_duplicate"),
    REMOVE("Remove token", "delete"), LINK("Link", "link"), UNLINK("Remove link", "link_off"),
    COMBAT("Combat", "swords"), CLOSE("Close / remove Stack effect", "layers_clear"),
    OCCURRENCE_REMOVE("Remove occurrence", "remove_circle_outline"), STACK("Stack entry / removal", "layers"), DRAW("Draw", "download"),
    TEXT_MARK("Mark Stack text", "format_ink_highlighter"), RESULT("Result suggestion", "emoji_events");

    val symbol: String get() = historyIconGlyph(icon)
    companion object {
        fun fromIcon(icon: String) = entries.singleOrNull { it.icon == icon }
            ?: error("Unsupported history icon: $icon")
    }
}
data class HistoryRange(val id: String, val groupId: String, val nodeIds: List<String>,
    val representativeId: String?, val glyph: HistoryGlyph, val author: String?,
    val parentRangeId: String?, val position: HistoryPoint, val turnStart: HistoryTurnStart? = null) {
    val targetId: String get() = nodeIds.last()
}
data class HistoryGraph(val ranges: List<HistoryRange>, val positions: Map<String, HistoryPoint>,
    val activeNodeIds: Set<String>, val currentNodeId: String, val width: Float, val height: Float,
    val events: List<HistoryTimelineEvent> = emptyList(), val branchHeadIds: Set<String> = emptySet()) {
    val rangesById = ranges.associateBy { it.id }
    val eventsById = events.associateBy { it.id }
    val rangeOf = ranges.flatMap { range -> range.nodeIds.map { it to range.id } }.toMap()
    val activeRanges = ranges.filter { range -> range.nodeIds.any { it in activeNodeIds } }.map { it.id }.toSet()
    val headRanges = branchHeadIds.mapNotNull { rangeOf[it] }.toSet()
    private val spatial = HistorySpatialIndex(ranges.map { range ->
        val parent = rangesById[range.parentRangeId]?.position ?: range.position
        HistorySpatialEntry(range, minOf(parent.x, range.position.x) - 24f, minOf(parent.y, range.position.y) - 32f,
            maxOf(parent.x, range.position.x) + 24f, maxOf(parent.y, range.position.y) + 16f)
    })
    fun visible(x: Float, y: Float, width: Float, height: Float) = spatial.query(x, y, x + width, y + height)
    val spatialVisits get() = spatial.visits
    fun pan(current: HistoryPoint, dx: Float, dy: Float, viewportWidth: Float, viewportHeight: Float) =
        HistoryPoint((current.x + dx).coerceIn(-width, viewportWidth),
            (current.y + dy).coerceIn(-height, viewportHeight))

    fun centeredOn(nodeId: String, viewportWidth: Float, viewportHeight: Float): HistoryPoint {
        val point = positions.getValue(nodeId)
        return HistoryPoint(viewportWidth / 2f - point.x, viewportHeight / 2f - point.y)
    }
}

/** Worker-only group projection using the same GameplayHistory.append as HistoryTree.groups.
 * Filtering affects visibility, never retained data or access to an exact review cursor. */
object HistoryGraphLayout {
    fun build(tree: GameplayTree, membership: Map<String, String> = emptyMap(),
        visibleNodeIds: Set<String> = tree.nodes.keys): HistoryGraph {
        val root = tree.initial.root.id
        val allChildren = tree.nodes.values.groupBy { it.parentId }.mapValues { (_, nodes) -> nodes.sortedBy { it.id } }
        val active = linkedSetOf<String>()
        var cursor: String? = tree.currentNodeId
        while (cursor != null && active.add(cursor) && cursor != root) cursor = tree.nodes[cursor]?.parentId
        require(root in active) { "Unavailable current ancestry" }
        val visible = visibleNodeIds.toMutableSet().also { it.addAll(active) }
        // Visible paths always retain their actual junctions/shared prefix.
        for (id in visible.toList()) {
            var parent = tree.nodes[id]?.parentId
            while (parent != null && parent != root && visible.add(parent)) parent = tree.nodes[parent]?.parentId
        }
        data class Segment(val id: String, val group: String, val parent: String?, val nodes: MutableList<String>)
        val segments = linkedMapOf(root to Segment(root, root, null, mutableListOf(root)))
        val segmentOf = mutableMapOf(root to root)
        val groups = mutableMapOf<String, GameplayGroup>()
        val initialTurn = tree.initial.board.turn
        val turns = mutableMapOf(root to HistoryTurnStart(initialTurn.number, initialTurn.accountId))
        val turnStarts = mutableMapOf(root to turns.getValue(root))
        val stack = ArrayDeque<GameplayMutation>()
        allChildren[root].orEmpty().asReversed().forEach(stack::addLast)
        val seen = mutableSetOf<String>()
        while (stack.isNotEmpty()) {
            val node = stack.removeLast()
            require(seen.add(node.id)) { "Cyclic tree" }
            if (node.id !in visible) continue
            val parentSegment = segments.getValue(segmentOf.getValue(node.parentId))
            val previousTurn = turns.getValue(node.parentId)
            val turn = HistoryTurnStart(
                node.transition.fields.lastOrNull { it.field == GameplayField.TURN_NUMBER }?.to ?: previousTurn.number,
                node.transition.fields.lastOrNull { it.field == GameplayField.TURN_PLAYER }?.to ?: previousTurn.player)
            turns[node.id] = turn
            if (turn != previousTurn) turnStarts[node.id] = turn
            val group = GameplayHistory.append(groups[node.parentId], node, tree.initial.board.containers)
            groups[node.id] = group
            val groupId = membership[node.id] ?: group.id
            // A real fork ends the rendered range even if the automatic group continues through it.
            val joins = parentSegment.id != root && parentSegment.group == groupId &&
                allChildren[node.parentId].orEmpty().size == 1 && node.id !in turnStarts
            val segment = if (joins) parentSegment else Segment(node.id, groupId, parentSegment.id,
                mutableListOf()).also { segments[it.id] = it }
            segment.nodes.add(node.id); segmentOf[node.id] = segment.id
            allChildren[node.id].orEmpty().asReversed().forEach(stack::addLast)
        }
        require(seen.containsAll(visible.filter { it != root })) { "Unavailable retained ancestry" }
        val children = segments.values.filter { it.parent != null }.groupBy { it.parent }
        val depth = mutableMapOf(root to 0)
        for (segment in segments.values.drop(1)) depth[segment.id] = depth.getValue(segment.parent!!) + 1
        val lanes = mutableMapOf<String, Float>()
        var nextLane = 0f
        // Iterative postorder keeps very long ordinary histories off both stack and UI limits.
        for (segment in segments.values.toList().asReversed()) {
            val descendants = children[segment.id].orEmpty()
            lanes[segment.id] = if (descendants.isEmpty()) nextLane++ else
                lanes.getValue((descendants.firstOrNull { child -> child.nodes.any { it in active } } ?: descendants.first()).id)
        }
        val positions = mutableMapOf<String, HistoryPoint>()
        val ranges = segments.values.map { segment ->
            val point = HistoryPoint(24f + depth.getValue(segment.id) * 24f, 24f + lanes.getValue(segment.id) * 36f)
            val representativeEvent = tree.nodes[segment.nodes.last()]
            val range = HistoryRange(segment.id, segment.group, segment.nodes.toList(), representativeEvent?.id,
                representativeEvent?.let { HistoryGlyph.fromIcon((it.grouping ?:
                    HistoryGrouping.classify(it.action, tree.initial.board.containers, it.transition)).icon) } ?: HistoryGlyph.ROOT,
                 representativeEvent?.author, segment.parent, point, turnStarts[segment.nodes.first()])
            // Exact interior cursors are still reviewable, but share their group's single chip.
            segment.nodes.forEach { positions[it] = point }
            range
        }
        val events = positions.keys.map { id ->
            val mutation = tree.nodes[id]
            val text = historyText(mutation)
            HistoryTimelineEvent(id, mutation?.grouping?.icon ?: mutation?.let {
                HistoryGrouping.classify(it.action, tree.initial.board.containers, it.transition).icon
            } ?: "flag", text.description, mutation?.author, groups[id]?.id, label = text.label)
        }
        return HistoryGraph(ranges, positions, active, tree.currentNodeId,
            (ranges.maxOfOrNull { it.position.x } ?: 24f) + 24f, nextLane * 36f + 48f,
            events, (tree.branchHeads.values + positions.keys.filter { allChildren[it].isNullOrEmpty() }).toSet())
    }

}

data class HistoryExplorerState(val graph: HistoryGraph? = null, val selectedNodeId: String? = null,
    val reviewed: PlayState? = null, val busy: Boolean = false, val error: String? = null)

/** UI-thread entry points, worker-only geometry/reconstruction, UI-thread post callback.
 * This read-only controller never activates or forks shared state.
 * Close/account changes fence queued work. It does not own/shutdown the application's worker.
 */
class HistoryExplorerController(private val worker: Executor, private val post: (() -> Unit) -> Unit,
    private val logFailure: (String, Throwable) -> Unit = { context, failure ->
        Log.e("HistoryExplorer", context, failure); Unit
    }) : AutoCloseable {
    var state by mutableStateOf(HistoryExplorerState())
        private set
    private var tree: GameplayTree? = null
    private var membership: Map<String, String> = emptyMap()
    private var visible: Set<String> = emptySet()
    @Volatile private var epoch = 0L
    private var closed = false

    fun update(value: GameplayTree, groups: Map<String, String> = emptyMap(),
        visibleNodeIds: Set<String> = value.nodes.keys, openingNodeId: String? = null, showHidden: Boolean = true) {
        if (closed) return
        val sameScope = tree?.initial?.let { it.gameId == value.initial.gameId && it.id == value.initial.id } == true
        if (!sameScope) state = HistoryExplorerState()
        tree = value; membership = groups; visible = visibleNodeIds; this.showHidden = showHidden
        val selection = openingNodeId ?: if (sameScope) state.selectedNodeId else null
        rebuild(selection ?: value.currentNodeId)
    }

    /** Exact timeline/detail access (including group interiors). A new group tap passes its range end. */
    fun select(nodeId: String) { if (!closed) rebuild(nodeId) }
    private var showHidden = true
    private var graphTree: GameplayTree? = null
    private var graphGroups: Map<String, String>? = null
    private var graphFilter: Set<String>? = null
    private var graphHidden = true
    private var cachedGraph: HistoryGraph? = null

    private fun rebuild(nodeId: String) {
        val value = tree ?: return
        val generation = ++epoch
        val groups = membership; val filter = visible
        val includeHidden = showHidden
        state = state.copy(selectedNodeId = nodeId, busy = true, error = null)
        worker.execute {
            if (generation != epoch) return@execute
            val result = runCatching {
                val selected = checkNotNull(value.resolveCursor(nodeId)) { "Cannot resolve retained cursor $nodeId" }
                val reviewed = checkNotNull(value.stateAt(selected)) { "Cannot reconstruct retained boundary $selected" }
                val cached = cachedGraph?.takeIf { graphTree === value && graphGroups === groups &&
                    graphFilter === filter && graphHidden == includeHidden && selected in it.positions }
                val graph = cached ?: run {
                val hidden = if (includeHidden) emptySet() else {
                    val comments = value.nodes.values.filter { it.action is PlayAction.EventComment }.map { it.id }.toSet()
                    value.branches.values.filter { branch ->
                        simpleAlternative(value, branch, comments)
                    }.map { it.id }.toSet()
                }
                HistoryGraphLayout.build(value, groups, (filter.filter { value.nodes[it]?.branchId !in hidden }.toSet() + selected)).also {
                    graphTree = value; graphGroups = groups; graphFilter = filter; graphHidden = includeHidden; cachedGraph = it
                }
                }
                Triple(graph, selected, reviewed)
            }
            result.exceptionOrNull()?.let { failure ->
                // Diagnostics must not prevent the friendly error (including in host tests without Android).
                runCatching { logFailure("Rebuild failed: game=${value.initial.gameId} play=${value.initial.id} " +
                    "selected=$nodeId current=${value.currentNodeId} nodes=${value.nodes.size} branches=${value.branches.size}", failure) }
            }
            post {
                if (!closed && generation == epoch) result.fold({ resolved ->
                    val (graph, selected, reviewed) = resolved
                    state = HistoryExplorerState(graph, selected, reviewed)
                }, { state = state.copy(busy = false, error = "History unavailable") })
            }
        }
    }

    /** Personal filtering only: missing data, reveals and comments keep alternatives visible. */
    private fun simpleAlternative(tree: GameplayTree, branch: GameplayBranch, comments: Set<String>): Boolean {
        if (branch.id == tree.currentBranchId) return false
        val suffix = mutableListOf<GameplayMutation>()
        val seen = mutableSetOf<String>()
        var cursor = tree.branchHeads[branch.id] ?: branch.firstNodeId
        while (cursor != branch.forkNodeId) {
            if (!seen.add(cursor)) return false
            val node = tree.nodes[cursor] ?: return false
            suffix.add(node); cursor = node.parentId
        }
        return suffix.isNotEmpty() && suffix.map { it.author }.distinct().size == 1 &&
            suffix.none { it.revealsInformation || it.id in comments }
    }

    override fun close() { closed = true; epoch++; tree = null; graphTree = null; cachedGraph = null; state = HistoryExplorerState() }
}

/** Personal armed intent used by the existing individual-action lowering path, not an outgoing queue.
 * Failed/no-op predictions keep the original boundary; a successful preceding mutation supplies the
 * exact deterministic node for the next request. Accepted reconciliation/withdrawal remains coordinator-owned.
 */
class HistoricalPlayIntent(val scope: GameScope, nodeId: String, val invocationId: String) {
    private var boundary = nodeId
    fun lower(command: GameCommand): GameCommand {
        require(command.scope == scope && command.action is GameAction.Play)
        return command.copy(invocationId = invocationId, continuation = Continuation(boundary, true))
    }
    fun predicted(command: GameCommand, change: GameplayTreeChange) {
        val target = change.targetId
        if (command.scope == scope && command.invocationId == invocationId &&
            command.continuation == Continuation(boundary, true) && change.tree != null && target != null)
            boundary = target
    }
}
