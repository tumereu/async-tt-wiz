package org.asyncttwiz.android

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.asyncttwiz.protocol.*

enum class HistoryMode { LIVE, PLAYBACK }
data class HistoryPosition(val cursorId: String, val mode: HistoryMode = HistoryMode.LIVE,
    val playing: Boolean = false, val displayVersion: String)

/** An index over the shared mutation model, not another history/replay engine. The caller supplies a
 * coherent accepted tree and a version of its already-rebased live target (not an acknowledgement revision). */
class HistoryTree(val rootId: String, mutations: List<GameplayMutation>, val sharedHeadId: String,
    val liveVersion: String, val versions: Map<String, String> = emptyMap(),
    val texts: Map<String, HistoryText> = emptyMap(), val containers: List<PlayContainer> = emptyList()) {
    val nodes = mutations.associateBy { it.id }
    init {
        require(nodes.size == mutations.size && rootId !in nodes && (sharedHeadId == rootId || sharedHeadId in nodes))
        val done = mutableSetOf(rootId)
        for (node in nodes.keys) {
            val visiting = mutableSetOf<String>(); var current = node
            while (current !in done) {
                require(visiting.add(current)) { "Cyclic history" }
                current = requireNotNull(nodes[current]) { "Incomplete retained history" }.parentId
            }
            done.addAll(visiting)
        }
    }
    fun contains(id: String) = id == rootId || id in nodes
    fun path(id: String): List<String> {
        require(contains(id))
        val reverse = mutableListOf<String>(); var cursor = id
        while (cursor != rootId) { reverse += cursor; cursor = nodes.getValue(cursor).parentId }
        return listOf(rootId) + reverse.asReversed()
    }
    fun groups(path: List<String>): Map<String, GameplayGroup> {
        var previous: GameplayGroup? = null
        return path.drop(1).associateWith { id -> GameplayHistory.append(previous, nodes.getValue(id), containers).also { previous = it } }
    }
    fun version(id: String) = if (id == sharedHeadId) liveVersion else versions[id] ?: id
}

/** Device-local transport only. No sync, request, activation, Undo or prediction ownership here. */
class HistoryPlayback(initial: HistoryTree) {
    var tree: HistoryTree by mutableStateOf(initial); private set
    var position by mutableStateOf(HistoryPosition(initial.sharedHeadId, displayVersion = initial.liveVersion)); private set
    var foreground by mutableStateOf(true); private set
    private val removedParents = mutableMapOf<String, String>()

    fun receive(next: HistoryTree) {
        require(next.rootId == tree.rootId) { "Create fresh playback for a new play incarnation" }
        for ((id, node) in tree.nodes) if (id !in next.nodes) removedParents[id] = node.parentId
        tree = next
        val cursor = resolveCursor(position.cursorId)
        position = when {
            foreground && position.mode == HistoryMode.LIVE -> HistoryPosition(tree.sharedHeadId, displayVersion = tree.liveVersion)
            !foreground && position.mode == HistoryMode.LIVE && position.displayVersion == tree.liveVersion ->
                position.copy(cursorId = tree.sharedHeadId) // Expected own confirmation: no unseen presentation difference.
            cursor != position.cursorId -> position.copy(cursorId = cursor, mode = HistoryMode.PLAYBACK,
                playing = false, displayVersion = tree.version(cursor))
            foreground && position.mode == HistoryMode.PLAYBACK -> position.copy(displayVersion = tree.version(cursor))
            else -> position // Accepted receipt never advances a paused or background cursor.
        }
    }

    fun background() { foreground = false }
    fun foreground() {
        foreground = true
        if (position.mode == HistoryMode.LIVE && (position.cursorId != tree.sharedHeadId || position.displayVersion != tree.liveVersion))
            position = position.copy(mode = HistoryMode.PLAYBACK, playing = false)
    }
    private fun resolveCursor(id: String): String {
        var cursor = id
        val seen = mutableSetOf<String>()
        while (!tree.contains(cursor)) {
            if (!seen.add(cursor)) return tree.sharedHeadId
            cursor = removedParents[cursor] ?: return tree.sharedHeadId
        }
        return cursor
    }
    fun scrub(id: String) {
        // A queued UI/review callback can refer to a deleted or not-yet-indexed boundary.
        val cursor = resolveCursor(id)
        position = HistoryPosition(cursor, HistoryMode.PLAYBACK, false, tree.version(cursor))
    }
    fun pause() { position = position.copy(mode = HistoryMode.PLAYBACK, playing = false) }
    fun play() { position = position.copy(mode = HistoryMode.PLAYBACK, playing = true) }

    fun previous() {
        val path = tree.path(position.cursorId)
        val groups = tree.groups(path)
        val currentGroup = groups[position.cursorId]?.id
        val first = path.indexOfFirst { groups[it]?.id == currentGroup && it != tree.rootId }
        scrub(if (first <= 0) tree.rootId else path[first - 1])
    }

    /** One concrete group endpoint, including rollback groups on a displaced retained path. */
    fun next(automatic: Boolean = false) {
        if (automatic && (!foreground || !position.playing)) return
        val oldPath = tree.path(position.cursorId)
        val active = tree.path(tree.sharedHeadId)
        val common = oldPath.zip(active).takeWhile { it.first == it.second }.last().first
        val target: String
        if (position.cursorId != common) {
            val groups = tree.groups(oldPath); val group = groups.getValue(position.cursorId).id
            val start = oldPath.indexOfFirst { groups[it]?.id == group }
            target = oldPath[maxOf(start - 1, oldPath.indexOf(common))]
        } else {
            val index = active.indexOf(position.cursorId)
            if (index == active.lastIndex) target = tree.sharedHeadId else {
                val groups = tree.groups(active); val nextGroup = groups.getValue(active[index + 1]).id
                target = active.drop(index + 1).takeWhile { groups.getValue(it).id == nextGroup }.last()
            }
        }
        val live = target == tree.sharedHeadId
        position = HistoryPosition(target, if (live) HistoryMode.LIVE else HistoryMode.PLAYBACK,
            automatic && !live, tree.version(target))
    }

    /** Capture on pointer down. Deletion/fallback must never silently arm a different boundary. */
    fun canContinue(capturedId: String) = position.mode == HistoryMode.PLAYBACK && !position.playing &&
        position.cursorId == capturedId && tree.contains(capturedId)
    fun continuationApplied() { position = HistoryPosition(tree.sharedHeadId, displayVersion = tree.liveVersion) }
}

data class HistoryAnimatedItem(val item: TableItem, val bounds: TableBounds, val alpha: Float)

/** Receipts, count labels, ownership and disclosure metadata are not motion targets. */
internal val TableItem.animationId get() = if (kind == TableItemKind.CARD) "card/$cardId" else id
internal val TableScene.animationTargets get() = items.associate { it.animationId to it.bounds }

/** The scene consumer samples these bounds for drawing AND hit testing/accessibility. No mutation waypoint queue. */
class HistorySceneAnimation(initial: TableScene) {
    private var destination = initial
    private var start = initial.items.associate { it.animationId to HistoryAnimatedItem(it, it.bounds, 1f) }
    private var end = start
    fun updateMetadata(target: TableScene) {
        if (target.animationTargets == destination.animationTargets) destination = target
    }
    fun frame(progress: Float, presentation: TableScene = destination): List<HistoryAnimatedItem> {
        val t = progress.coerceIn(0f, 1f)
        val latest = presentation.items.associateBy { it.animationId }
        return (end.keys + start.keys).map { id ->
            val a = start[id] ?: end.getValue(id).copy(alpha = 0f)
            val b = end[id] ?: start.getValue(id).copy(alpha = 0f)
            fun mix(x: Float, y: Float) = x + (y - x) * t
            val item = latest[id] ?: b.item
            HistoryAnimatedItem(item.copy(inTransit = item.kind == TableItemKind.CARD && t < 1f && a.bounds != b.bounds),
                TableBounds(mix(a.bounds.x, b.bounds.x), mix(a.bounds.y, b.bounds.y),
                 mix(a.bounds.width, b.bounds.width), mix(a.bounds.height, b.bounds.height)), mix(a.alpha, b.alpha))
        }.sortedBy { it.item.inTransit }
    }
    /** False means confirmation/metadata only: the caller must leave the running clock alone. */
    fun retarget(target: TableScene, currentProgress: Float): Boolean {
        if (target.animationTargets == destination.animationTargets) {
            destination = target
            return false
        }
        start = frame(currentProgress).filter { it.alpha > 0f }.associateBy { it.item.animationId }
        end = target.items.associate { it.animationId to HistoryAnimatedItem(it, it.bounds, 1f) }
        // Hidden library instances have no individual scene item. Seed every newly drawn card
        // from its former pile, using stable instance identity (also covers multi-card draws).
        val arrivals = end.values.filter { it.item.kind == TableItemKind.CARD && it.item.animationId !in start }
        for (arrival in arrivals) {
            val pile = destination.items.firstOrNull { it.kind == TableItemKind.PILE && arrival.item.cardId in it.cards } ?: continue
            val bounds = TableBounds(pile.bounds.x + 8f, pile.bounds.y + 8f, 28f, 40f)
            start = start + (arrival.item.animationId to arrival.copy(bounds = bounds))
        }
        destination = target
        return true
    }
    fun hitTest(x: Float, y: Float, progress: Float): TableItem? = frame(progress).asReversed().firstOrNull {
        it.alpha > 0f && x >= it.bounds.x && x <= it.bounds.x + it.bounds.width &&
            y >= it.bounds.y && y <= it.bounds.y + it.bounds.height
    }?.item
}
