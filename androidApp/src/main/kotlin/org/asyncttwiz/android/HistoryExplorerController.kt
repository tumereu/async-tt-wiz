package org.asyncttwiz.android

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.asyncttwiz.protocol.*
import java.util.concurrent.Executor

data class HistoryPoint(val x: Float, val y: Float)
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
    val parentRangeId: String?, val position: HistoryPoint) {
    val targetId: String get() = nodeIds.last()
}
data class HistoryGraph(val ranges: List<HistoryRange>, val positions: Map<String, HistoryPoint>,
    val activeNodeIds: Set<String>, val currentNodeId: String, val width: Float, val height: Float) {
    fun pan(current: HistoryPoint, dx: Float, dy: Float, viewportWidth: Float, viewportHeight: Float) =
        HistoryPoint((current.x + dx).coerceIn(-width, viewportWidth),
            (current.y + dy).coerceIn(-height, viewportHeight))
}

/** Group projection, never a second gameplay/grouping engine. Call on a worker. The membership seam
 * consumes effective authoritative grouping when the complete I22 processor is integrated. Until then
 * the default uses the existing shared GameplayHistory.append, without materializing boards. Filtering
 * is supplied by I25; all retained nodes are shown by default, never deleted or treated as access rules.
 */
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
        val stack = ArrayDeque<GameplayMutation>()
        allChildren[root].orEmpty().asReversed().forEach(stack::addLast)
        val seen = mutableSetOf<String>()
        while (stack.isNotEmpty()) {
            val node = stack.removeLast()
            require(seen.add(node.id)) { "Cyclic tree" }
            if (node.id !in visible) continue
            val parentSegment = segments.getValue(segmentOf.getValue(node.parentId))
            val group = GameplayHistory.append(groups[node.parentId], node, tree.initial.board.containers)
            groups[node.id] = group
            val groupId = membership[node.id] ?: group.id
            // A real fork ends the rendered range even if the automatic group continues through it.
            val joins = parentSegment.id != root && parentSegment.group == groupId &&
                allChildren[node.parentId].orEmpty().size == 1
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
                (lanes.getValue(descendants.first().id) + lanes.getValue(descendants.last().id)) / 2
        }
        val positions = mutableMapOf<String, HistoryPoint>()
        val ranges = segments.values.map { segment ->
            val point = HistoryPoint(28f + depth.getValue(segment.id) * 88f, 28f + lanes.getValue(segment.id) * 60f)
            val events = segment.nodes.mapNotNull(tree.nodes::get)
            val representative = events.fold<GameplayMutation, HistoryRepresentative?>(null) { chosen, event ->
                HistoryGrouping.choose(chosen, HistoryRepresentative(event.id,
                    event.grouping ?: HistoryGrouping.classify(event.action, tree.initial.board.containers, event.transition)))
            }
            val representativeEvent = representative?.let { tree.nodes.getValue(it.nodeId) }
            val range = HistoryRange(segment.id, segment.group, segment.nodes.toList(), representative?.nodeId,
                representative?.let { HistoryGlyph.fromIcon(it.semantic.icon) } ?: HistoryGlyph.ROOT,
                representativeEvent?.author, segment.parent, point)
            if (segment.id == root) positions[root] = point else {
                val parent = segments.getValue(segment.parent!!)
                val parentX = 28f + depth.getValue(parent.id) * 88f
                val parentY = 28f + lanes.getValue(parent.id) * 60f
                segment.nodes.forEachIndexed { index, id ->
                    val fraction = (index + 1).toFloat() / segment.nodes.size
                    positions[id] = HistoryPoint(parentX + (point.x - parentX) * fraction,
                        parentY + (point.y - parentY) * fraction)
                }
            }
            range
        }
        return HistoryGraph(ranges, positions, active, tree.currentNodeId,
            (ranges.maxOfOrNull { it.position.x } ?: 28f) + 28f, nextLane * 60f + 56f)
    }

}

data class HistoryActivation(val scope: GameScope, val nodeId: String)
data class HistoryExplorerState(val graph: HistoryGraph? = null, val selectedNodeId: String? = null,
    val reviewed: PlayState? = null, val busy: Boolean = false, val error: String? = null,
    val archivedSelection: Boolean = false)

/** UI-thread entry points, worker-only geometry/reconstruction, UI-thread post callback. The caller
 * supplies its existing coordinator/FIFO submit; this controller never activates or forks local state.
 * Close/account changes fence queued work. It does not own/shutdown the application's worker.
 */
class HistoryExplorerController(private val worker: Executor, private val post: (() -> Unit) -> Unit,
    private val connected: () -> Boolean, private val submit: (GameScope, GameAction) -> Boolean,
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
        tree = value; membership = groups.toMap(); visible = visibleNodeIds.toSet(); this.showHidden = showHidden
        val selection = if (sameScope) state.selectedNodeId else openingNodeId
        rebuild(selection ?: value.currentNodeId)
    }

    /** Exact timeline/detail access (including group interiors). A new group tap passes its range end. */
    fun select(nodeId: String) { if (!closed) rebuild(nodeId) }
    private var showHidden = true

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
                val hidden = if (includeHidden) emptySet() else {
                    val comments = value.nodes.values.filter { it.action is PlayAction.EventComment }.map { it.id }.toSet()
                    value.branches.values.filter { branch ->
                        BranchArchives.hiddenByDefault(value, branch.forkNodeId, value.branchHeads[branch.id] ?: branch.firstNodeId,
                            branch.id == value.currentBranchId, comments)
                    }.map { it.id }.toSet() + value.branchArchives.archivedBranchIds
                }
                Triple(HistoryGraphLayout.build(value, groups, (filter.filter { value.nodes[it]?.branchId !in hidden }.toSet() + selected)), selected, reviewed)
            }
            result.exceptionOrNull()?.let { failure ->
                // Diagnostics must not prevent the friendly error (including in host tests without Android).
                runCatching { logFailure("Rebuild failed: game=${value.initial.gameId} play=${value.initial.id} " +
                    "selected=$nodeId current=${value.currentNodeId} nodes=${value.nodes.size} branches=${value.branches.size}", failure) }
            }
            post {
                if (!closed && generation == epoch) result.fold({ resolved ->
                    val (graph, selected, reviewed) = resolved
                    state = HistoryExplorerState(graph, selected, reviewed, archivedSelection =
                        (value.nodes[selected]?.branchId ?: value.initial.root.branchId) in value.branchArchives.archivedBranchIds)
                }, { state = state.copy(busy = false, error = "History unavailable") })
            }
        }
    }

    fun captureMakeCurrent(): HistoryActivation? {
        val value = tree ?: return null
        if (closed || state.busy || state.error != null || state.archivedSelection) return null
        return state.selectedNodeId?.let { HistoryActivation(GameScope(value.initial.gameId, playId = value.initial.id), it) }
    }
    fun makeCurrent(target: HistoryActivation? = captureMakeCurrent()): Boolean {
        val value = tree ?: return false
        if (closed || !connected() || target == null || target.scope != GameScope(value.initial.gameId, playId = value.initial.id)) return false
        // Never re-resolve a captured action target, even if its event was removed after capture.
        return submit(target.scope, GameAction.MakeCurrent(target.nodeId))
    }
    fun armContinuation(invocationId: String): HistoricalPlayIntent? = captureMakeCurrent()?.let {
        HistoricalPlayIntent(it.scope, it.nodeId, invocationId)
    }
    override fun close() { closed = true; epoch++; tree = null; state = HistoryExplorerState() }
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
