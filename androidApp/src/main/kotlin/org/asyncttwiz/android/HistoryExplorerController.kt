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
        if (!centroid.x.isFinite() || !centroid.y.isFinite() || !delta.x.isFinite() || !delta.y.isFinite() ||
            !scale.isFinite() || scale <= 0f || !width.isFinite() || !height.isFinite() ||
            !graph.width.isFinite() || !graph.height.isFinite()) return this
        val nextZoom = (zoom * scale).coerceIn(.5f, 3f)
        val ratio = nextZoom / zoom
        return HistoryTreeViewport(HistoryPoint(
            (centroid.x - (centroid.x - pan.x) * ratio + delta.x).coerceIn(-graph.width * nextZoom, width),
            (centroid.y - (centroid.y - pan.y) * ratio + delta.y).coerceIn(-graph.height * nextZoom, height)), nextZoom)
    }
    fun screen(point: HistoryPoint) = HistoryPoint(point.x * zoom + pan.x, point.y * zoom + pan.y)
    fun visible(graph: HistoryGraph, width: Float, height: Float) = graph.visible(-pan.x / zoom, -pan.y / zoom, width / zoom, height / zoom)
}
/** The history tree's name for each icon. */
internal fun historyIconLabel(icon: HistoryIcon): String = when (icon) {
    HistoryIcon.FLAG -> "Initial state"; HistoryIcon.ROTATE_90_DEGREES_CW -> "Tap / untap"; HistoryIcon.OPEN_WITH -> "Move"
    HistoryIcon.FAVORITE -> "Life"; HistoryIcon.EXPOSURE_PLUS_1 -> "Counter"; HistoryIcon.OUTLINED_FLAG -> "Turn / step"
    HistoryIcon.CASINO -> "Die"; HistoryIcon.MONETIZATION_ON -> "Coin"; HistoryIcon.SHUFFLE -> "Shuffle"; HistoryIcon.PAGEVIEW -> "View"
    HistoryIcon.VISIBILITY -> "Inspect / reveal"; HistoryIcon.CLOSE -> "Dismiss contents"; HistoryIcon.CHAT_BUBBLE -> "Comment"
    HistoryIcon.ADS_CLICK -> "Ping / unping"; HistoryIcon.SETTINGS_BACKUP_RESTORE -> "Reset corner"; HistoryIcon.STICKY_NOTE_2 -> "Note"
    HistoryIcon.SWITCH_ACCOUNT -> "Owner"; HistoryIcon.FLIP_CAMERA_ANDROID -> "Face"; HistoryIcon.FLIP -> "Cover"
    HistoryIcon.FLIP_TO_FRONT -> "Uncover"; HistoryIcon.ADD_BOX -> "Token"; HistoryIcon.CONTROL_POINT_DUPLICATE -> "Clone"
    HistoryIcon.DELETE -> "Remove token"; HistoryIcon.LINK -> "Link"; HistoryIcon.LINK_OFF -> "Remove link"
    HistoryIcon.SWORDS -> "Combat"; HistoryIcon.LAYERS_CLEAR -> "Close / remove Stack effect"
    HistoryIcon.REMOVE_CIRCLE_OUTLINE -> "Remove occurrence"; HistoryIcon.LAYERS -> "Stack entry / removal"; HistoryIcon.DOWNLOAD -> "Draw / to hand"
    HistoryIcon.FORMAT_INK_HIGHLIGHTER -> "Mark Stack text"; HistoryIcon.EMOJI_EVENTS -> "Result suggestion"
    HistoryIcon.INBOX -> "Temporary pile"; HistoryIcon.SWAP_HORIZ -> "Stack controller"
    HistoryIcon.SPACE_DASHBOARD -> "To battlefield"; HistoryIcon.DELETE_SWEEP -> "To graveyard"; HistoryIcon.BLOCK -> "To exile"
    HistoryIcon.LIBRARY_BOOKS -> "To library"; HistoryIcon.HEART_BROKEN -> "Life loss"
    HistoryIcon.EXPOSURE_NEG_1 -> "Counter removed"; HistoryIcon.EXPOSURE_ZERO -> "Counters cleared"
    HistoryIcon.ARROW_CIRCLE_UP -> "Player counter up"; HistoryIcon.ARROW_CIRCLE_DOWN -> "Player counter down"
    HistoryIcon.TRENDING_UP -> "Power/toughness up"; HistoryIcon.TRENDING_DOWN -> "Power/toughness down"
    HistoryIcon.LOCAL_FIRE_DEPARTMENT -> "Damage"; HistoryIcon.HEALING -> "Damage removed"
    HistoryIcon.ARROW_FORWARD -> "Next step"; HistoryIcon.SKIP_NEXT -> "Next turn"; HistoryIcon.VISIBILITY_OFF -> "Close view"
    HistoryIcon.CHECK -> "Close combat"; HistoryIcon.GPS_FIXED -> "Attack target"; HistoryIcon.UNKNOWN -> "Change"
}

data class HistoryRange(val id: String, val groupId: String, val nodeIds: List<String>,
    val representativeId: String?, val icon: HistoryIcon, val author: String?,
    val parentRangeId: String?, val position: HistoryPoint, val turnStart: HistoryTurnStart? = null) {
    val targetId: String get() = nodeIds.last()
}

data class HistoryExplorerState(val graph: HistoryGraph? = null, val selectedNodeId: String? = null,
    val reviewed: PlayState? = null, val busy: Boolean = false, val error: String? = null)

/** Read-only selection and reconstruction. Normal callers supply the history ingestion index;
 * standalone/test callers can also feed successive trees, using the same delta indexer. */
class HistoryExplorerController(private val worker: Executor, private val post: (() -> Unit) -> Unit,
    private val logFailure: (String, Throwable) -> Unit = { context, failure ->
        Log.e("HistoryExplorer", context, failure); Unit
    }) : AutoCloseable {
    var state by mutableStateOf(HistoryExplorerState()); private set
    fun dismissError() { state = state.copy(error = null) }
    private var tree: GameplayTree? = null
    private var prepared: HistoryTree? = null
    @Volatile private var epoch = 0L
    private var closed = false
    private var showHidden = true
    private var graphTree: GameplayTree? = null
    private var graphHidden = true
    private var cachedGraph: HistoryGraph? = null
    private var indexedTree: GameplayTree? = null
    private var index: HistoryIndex? = null
    private var reviewedIdentity: String? = null
    private var reviewedValue: PlayState? = null

    fun update(value: GameplayTree, openingNodeId: String? = null, showHidden: Boolean = true,
        indexed: HistoryTree? = null) {
        if (closed) return
        val sameScope = tree?.initial?.let { it.gameId == value.initial.gameId && it.id == value.initial.id } == true
        if (!sameScope) state = HistoryExplorerState()
        tree = value; prepared = indexed; this.showHidden = showHidden
        val selection = openingNodeId ?: if (sameScope) state.selectedNodeId else null
        rebuild(selection ?: value.currentNodeId)
    }

    /** Exact interior cursors remain reviewable; a new chip tap passes its range end. */
    fun select(nodeId: String) { if (!closed) rebuild(nodeId) }
    /** Fence old callbacks and framing while the table's ingestion worker catches up. */
    fun opening(nodeId: String?) {
        if (closed) return
        epoch++
        state = HistoryExplorerState(selectedNodeId = nodeId, busy = true)
    }

    private fun rebuild(nodeId: String) {
        val value = tree ?: return
        val supplied = prepared
        val generation = ++epoch
        val includeHidden = showHidden
        state = state.copy(selectedNodeId = nodeId, busy = true, error = null)
        worker.execute {
            if (generation != epoch) return@execute
            val result = runCatching {
                val selected = checkNotNull(value.resolveCursor(nodeId)) { "Cannot resolve retained cursor $nodeId" }
                val identity = value.endpointIndex().positions[selected]?.identity
                val reviewed = if (selected == value.initial.root.id) value.initial else value.preparedState(selected) ?: reviewedValue?.takeIf {
                    identity != null && identity == reviewedIdentity && it.currentNodeId == selected &&
                        it.id == value.initial.id && it.gameId == value.initial.gameId &&
                        (selected != value.currentNodeId || it.currentBranchId == value.currentBranchId)
                } ?: checkNotNull(value.stateAt(selected)) { "Cannot reconstruct retained boundary $selected" }
                reviewedIdentity = identity; reviewedValue = reviewed
                val graph = cachedGraph?.takeIf { graphTree === value && graphHidden == includeHidden } ?: run {
                    val ready = supplied?.takeIf { it.nodes === value.nodes && it.rootId == value.initial.root.id }?.index
                    val next = ready ?: run {
                        val old = indexedTree?.takeIf { it.initial.root.id == value.initial.root.id }
                        val changed = old?.let(value::changedNodeIdsSince)
                        HistoryIndex.build(value.initial.root.id, value.nodes, value.initial.board.containers,
                            index.takeIf { changed != null }, old?.nodes.orEmpty(), changed ?: value.nodes.keys, value.initial)
                    }
                    indexedTree = value; index = next
                    HistoryGraph(value, next.layout, includeHidden, selected).also {
                        graphTree = value; graphHidden = includeHidden; cachedGraph = it
                    }
                }
                graph.select(selected)
                Triple(graph, selected, reviewed)
            }
            result.exceptionOrNull()?.let { failure ->
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

    override fun close() { closed = true; epoch++; tree = null; prepared = null; graphTree = null; cachedGraph = null
        indexedTree = null; index = null; reviewedValue = null; reviewedIdentity = null; state = HistoryExplorerState() }
}

/** Personal armed intent used by the individual-action lowering path, not an outgoing queue. */
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
