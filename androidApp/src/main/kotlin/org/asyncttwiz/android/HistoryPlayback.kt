package org.asyncttwiz.android

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.asyncttwiz.protocol.*
import kotlinx.collections.immutable.toPersistentMap

enum class HistoryMode { LIVE, PLAYBACK }
data class HistoryPosition(val cursorId: String, val mode: HistoryMode = HistoryMode.LIVE,
    val playing: Boolean = false, val displayVersion: String)

/** An index over the shared mutation model, not another history/replay engine. The caller supplies a
 * coherent accepted tree and a version of its already-rebased live target (not an acknowledgement revision). */
class HistoryTree(val rootId: String, mutations: List<GameplayMutation>, val sharedHeadId: String,
    val liveVersion: String, val versions: Map<String, String> = emptyMap(),
    texts: Map<String, HistoryText> = emptyMap(), val containers: List<PlayContainer> = emptyList(),
    internal val source: GameplayTree? = null, previous: HistoryTree? = null,
    lazyPresentation: Boolean = false) {
    val nodes = source?.nodes ?: mutations.associateBy { it.id }
    var texts = texts; internal set
    private val changed = if (source != null && previous?.source != null) source.changedNodeIdsSince(previous.source) else null
    private val priorIndex: HistoryIndex? = previous?.takeIf { changed != null && (!lazyPresentation || it.hasPresentationIndex) }?.index
    private val priorNodes: Map<String, GameplayMutation> = if (priorIndex != null) previous?.nodes.orEmpty() else emptyMap()
    private val presentationIndex: Lazy<HistoryIndex> = lazy { HistoryIndex.build(rootId, nodes, containers, priorIndex,
        priorNodes, if (priorIndex != null) changed!! else nodes.keys) }
    internal val index: HistoryIndex by presentationIndex
    internal val hasPresentationIndex: Boolean get() = presentationIndex.isInitialized()
    val indexedNodes get() = index.visited
    var navigationReads = 0; private set
    val removedParents: Map<String, String> = run {
        var result = previous?.removedParents.orEmpty().toPersistentMap()
        for (id in changed ?: emptySet()) previous?.nodes?.get(id)?.takeIf { id !in nodes }?.let {
            result = result.put(id, it.parentId)
        }
        result
    }
    init {
        require((source != null || nodes.size == mutations.size) && rootId !in nodes && (sharedHeadId == rootId || sharedHeadId in nodes))
        if (!lazyPresentation) index
    }
    fun contains(id: String) = id == rootId || id in nodes
    fun path(id: String): List<String> {
        require(contains(id))
        return source?.endpointIndex()?.paths?.get(id) ?: index.boundaries.getValue(id).path
    }
    fun depth(id: String): Int { navigationReads++; return source?.endpointIndex()?.positions?.get(id)?.depth ?: path(id).lastIndex }
    fun isAncestor(ancestor: String, endpoint: String): Boolean {
        navigationReads++
        if (!contains(ancestor) || !contains(endpoint)) return false
        return path(endpoint).getOrNull(depth(ancestor)) == ancestor
    }
    internal fun ends(id: String): List<String> { navigationReads++; return index.boundaries.getValue(id).groupEnds }
    fun groups(path: List<String>): Map<String, GameplayGroup> {
        return path.drop(1).associateWith { id -> group(id)!! }
    }
    internal fun group(id: String): GameplayGroup? = if (id == rootId) null else
        nodes[id]?.bookkeeping?.toGroup ?: index.boundaries.getValue(id).group
    internal fun alternatives(id: String): Int = ((if (source != null) source.endpointIndex().children[id]?.size ?: 0
        else index.children[id]?.size ?: 0) - 1).coerceAtLeast(0)
    internal fun previousGroup(id: String): String = group(id)?.firstNodeId?.let { nodes.getValue(it).parentId } ?: rootId
    internal fun commonAncestor(a: String, b: String): String {
        if (isAncestor(a, b)) return a
        if (isAncestor(b, a)) return b
        var left = a; var right = b
        var ld = depth(left); var rd = depth(right)
        while (left != right) {
            navigationReads++
            if (ld >= rd) { left = nodes.getValue(left).parentId; ld-- }
            else { right = nodes.getValue(right).parentId; rd-- }
        }
        return left
    }
    internal fun forwardGroup(cursor: String): String {
        val path = path(sharedHeadId)
        var offset = depth(cursor) + 1
        if (offset >= path.size) return sharedHeadId
        val group = group(path[offset])?.id
        while (offset + 1 < path.size && group(path[offset + 1])?.id == group) { navigationReads++; offset++ }
        return path[offset]
    }
    fun version(id: String) = if (id == sharedHeadId) liveVersion else versions[id] ?: id
}

/** Device-local transport only. No sync, request, activation, Undo or prediction ownership here. */
class HistoryPlayback(initial: HistoryTree) {
    var tree: HistoryTree by mutableStateOf(initial); private set
    var position by mutableStateOf(HistoryPosition(initial.sharedHeadId, displayVersion = initial.liveVersion)); private set
    var foreground by mutableStateOf(true); private set
    /** A remote flip or final board is still LIVE, but must settle before moving on. */
    var livePanelReveal by mutableStateOf(false); private set
    private var panelViewer: String? = null
    private var removedParents: Map<String, String> = emptyMap()
    private var rollbackPlan: Pair<String, String>? = null
    var branched by mutableStateOf(false); private set

    /** No route list or replay is built up front. A missing/deleted bookmark starts at root. */
    fun openFrom(lastSeen: String?) {
        val cursor = lastSeen?.let { tree.source?.resolveCursor(it) ?: it.takeIf(tree::contains) } ?: tree.rootId
        if (cursor == tree.sharedHeadId) return
        position = HistoryPosition(cursor, HistoryMode.PLAYBACK, true, tree.version(cursor))
        planRollback()
    }
    private fun planRollback(): String {
        if (tree.isAncestor(position.cursorId, tree.sharedHeadId)) return position.cursorId
        rollbackPlan?.takeIf { it.first == tree.sharedHeadId && tree.isAncestor(it.second, position.cursorId) }?.let { return it.second }
        val common = tree.commonAncestor(position.cursorId, tree.sharedHeadId)
        rollbackPlan = tree.sharedHeadId to common
        branched = common != position.cursorId
        return common
    }
    fun acknowledgeBranch() { branched = false }
    fun stepPauseMillis(normal: Long): Long = if (!tree.isAncestor(position.cursorId, tree.sharedHeadId))
        minOf(100L, normal / 4).coerceAtLeast(1) else normal

    fun receive(next: HistoryTree, viewer: String? = null) {
        require(next.rootId == tree.rootId) { "Create fresh playback for a new play incarnation" }
        removedParents = if (next.source != null) next.removedParents else {
            var parents = removedParents.toPersistentMap()
            for ((id, node) in tree.nodes) if (id !in next.nodes) parents = parents.put(id, node.parentId)
            parents
        }
        tree = next
        panelViewer = viewer
        val cursor = resolveCursor(position.cursorId)
        val held = livePanelReveal && tree.contains(position.cursorId) && tree.isAncestor(position.cursorId, tree.sharedHeadId)
        val reveal = if (foreground && position.mode == HistoryMode.LIVE) {
            if (held) position.cursorId else nextPanelReveal(cursor)
        } else null
        livePanelReveal = reveal != null
        position = when {
            reveal != null -> HistoryPosition(reveal, displayVersion = tree.version(reveal))
            foreground && position.mode == HistoryMode.LIVE -> HistoryPosition(tree.sharedHeadId, displayVersion = tree.liveVersion)
            !foreground && position.mode == HistoryMode.LIVE && position.displayVersion == tree.liveVersion ->
                position.copy(cursorId = tree.sharedHeadId) // Expected own confirmation: no unseen presentation difference.
            cursor != position.cursorId -> position.copy(cursorId = cursor,
                playing = false, displayVersion = tree.version(cursor))
            foreground && position.mode == HistoryMode.PLAYBACK -> position.copy(displayVersion = tree.version(cursor))
            else -> position // Accepted receipt never advances a paused or background cursor.
        }
        if (position.playing) planRollback()
    }

    private fun nextPanelReveal(after: String): String? {
        val viewer = panelViewer
        if (!tree.isAncestor(after, tree.sharedHeadId)) return null
        val path = tree.path(tree.sharedHeadId)
        for (index in tree.depth(after) + 1 until path.size) {
            val node = tree.nodes.getValue(path[index])
            if (viewer != null && node.author != viewer && node.action is PlayAction.RevealContentsCard) return node.id
            if (node.action is PlayAction.SuggestResult && index > tree.depth(after) + 1) return node.parentId
        }
        return null
    }

    /** Called after the face-up endpoint's animation and the configured history group pause. */
    fun finishPanelReveal() {
        if (!foreground || !livePanelReveal || position.mode != HistoryMode.LIVE) return
        val reveal = nextPanelReveal(position.cursorId)
        livePanelReveal = reveal != null
        val target = reveal ?: tree.sharedHeadId
        position = HistoryPosition(target, displayVersion = tree.version(target))
    }
    /** Present the final board before a result without enabling personal timeline mode. */
    fun holdLiveAt(id: String) {
        if (position.mode != HistoryMode.LIVE || !tree.contains(id)) return
        livePanelReveal = true
        position = HistoryPosition(id, displayVersion = tree.version(id))
    }

    fun background() { foreground = false }
    fun foreground() {
        val resuming = !foreground
        foreground = true
        if (!resuming || position.mode != HistoryMode.LIVE) return
        // Only unseen nodes justify timeline mode; a revision bump without one (e.g. chat) stays live.
        position = if (position.cursorId != tree.sharedHeadId) position.copy(mode = HistoryMode.PLAYBACK, playing = false)
            else HistoryPosition(tree.sharedHeadId, displayVersion = tree.liveVersion)
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
        livePanelReveal = false
        position = HistoryPosition(cursor, HistoryMode.PLAYBACK, false, tree.version(cursor))
        rollbackPlan = null; branched = false
    }
    fun pause() { position = position.copy(playing = false) }
    fun play() {
        if (position.mode != HistoryMode.PLAYBACK) return
        livePanelReveal = false; position = position.copy(playing = true); planRollback()
    }

    fun previous() {
        scrub(tree.previousGroup(position.cursorId))
    }

    /** One concrete group endpoint, including rollback groups on a displaced retained path. */
    fun next(automatic: Boolean = false) {
        if (automatic && (!foreground || !position.playing)) return
        livePanelReveal = false
        val common = planRollback()
        val target: String
        if (position.cursorId != common) {
            val previous = tree.previousGroup(position.cursorId)
            target = if (tree.depth(previous) < tree.depth(common)) common else previous
        } else {
            target = tree.forwardGroup(position.cursorId)
        }
        val live = target == tree.sharedHeadId
        position = HistoryPosition(target, if (live) HistoryMode.LIVE else HistoryMode.PLAYBACK,
            automatic && !live, tree.version(target))
        if (target == common || live) branched = false
    }

    /** Capture on pointer down. Deletion/fallback must never silently arm a different boundary. */
    fun canContinue(capturedId: String) = position.mode == HistoryMode.PLAYBACK && !position.playing &&
        position.cursorId == capturedId && tree.contains(capturedId)
    fun continuationApplied() { livePanelReveal = false; position = HistoryPosition(tree.sharedHeadId, displayVersion = tree.liveVersion) }
}

data class HistoryAnimatedItem(val item: TableItem, val bounds: TableBounds, val alpha: Float)

/** Upright face dimensions never depend on the rotating footprint. */
data class TableCardTransform(val width: Float, val height: Float, val rotation: Float) {
    fun bounds(center: TablePoint): TableBounds {
        val radians = Math.toRadians(rotation.toDouble())
        val c = kotlin.math.abs(kotlin.math.cos(radians)).toFloat()
        val s = kotlin.math.abs(kotlin.math.sin(radians)).toFloat()
        val w = width * c + height * s
        val h = height * c + width * s
        return TableBounds(center.x - w / 2, center.y - h / 2, w, h)
    }
    /** A rigid rotating face can be wider than either endpoint. Keep its entire AABB in the cell. */
    fun boundsInside(center: TablePoint, footprint: TableBounds): TableBounds {
        val b = bounds(center)
        return b.copy(x = b.x.coerceIn(footprint.x, footprint.right - b.width),
            y = b.y.coerceIn(footprint.y, footprint.bottom - b.height))
    }
}

internal fun TableItem.faceTransform(bounds: TableBounds = this.bounds) = cardTransform ?: TableCardTransform(
    if (tapped) bounds.height else bounds.width, if (tapped) bounds.width else bounds.height,
    if (tapped) 90f else 0f)

/** Receipts, count labels, ownership and disclosure metadata are not motion targets. */
internal val TableItem.animationId get() = if (kind == TableItemKind.CARD) "card/$cardId" else id
internal val TableScene.animationTargets get() = (items + stackProjections).associate { it.animationId to Triple(it.bounds, it.tapped, it.covered) }

/** The scene consumer samples these bounds for drawing AND hit testing/accessibility. No mutation waypoint queue. */
class HistorySceneAnimation(initial: TableScene) {
    internal var cardMoveSpeedDpPerMs: Float = DEFAULT_CARD_MOVE_SPEED
    private var destination = initial
    private var start = initial.items.associate { it.animationId to HistoryAnimatedItem(it, it.bounds, 1f) }
    private var end = start
    var durationMillis: Int = 180; private set
    internal var rotatingCardIds: Set<String> = emptySet(); private set
    private var latestScene = initial
    private var latestItems = initial.items.associateBy { it.animationId }
    fun updateMetadata(target: TableScene) {
        if (target.animationTargets == destination.animationTargets) destination = target
    }
    fun frame(progress: Float, presentation: TableScene = destination): List<HistoryAnimatedItem> {
        val sceneProgress = progress.coerceIn(0f, 1f)
        if (presentation !== latestScene) {
            latestScene = presentation
            latestItems = presentation.items.associateBy { it.animationId }
        }
        return (end.keys + start.keys).map { id ->
            val a = start[id] ?: end.getValue(id).copy(alpha = 0f)
            val b = end[id] ?: start.getValue(id).copy(alpha = 0f)
            // Most of a large board is stationary during a tap. Reuse its immutable drawings
            // instead of allocating transforms and copied items for every card on every frame.
            if (a.bounds == b.bounds && a.alpha == b.alpha && a.item.tapped == b.item.tapped &&
                a.item.covered == b.item.covered && a.item.faceTransform(a.bounds) == b.item.faceTransform(b.bounds)) {
                val shown = latestItems[id] ?: b.item
                return@map if (shown === b.item) b else HistoryAnimatedItem(shown, b.bounds, b.alpha)
            }
            val t = if (b.item.kind == TableItemKind.CARD && durationMillis != 90)
                cardMoveProgress(a.bounds, b.bounds, sceneProgress, durationMillis, cardMoveSpeedDpPerMs) else sceneProgress
            fun mix(x: Float, y: Float) = x + (y - x) * t
            val flipping = a.item.covered != b.item.covered && a.item.viewport.startsWith("cardPanel/") && b.item.viewport == a.item.viewport
            val shown = if (flipping && t < .5f) a.item else latestItems[id] ?: b.item
            // A flying face cannot expose a Library top, including at the exact departure frame.
            val item = if (t < 1f && shown.kind == TableItemKind.CARD && a.bounds != b.bounds &&
                listOf(a.item, b.item).any { it.zone == PlayZone.LIBRARY && !it.viewport.startsWith("cardPanel/") })
                shown.copy(covered = true, known = false, artwork = null, value = "", note = "", corner = "",
                    typeIcon = "", counters = emptyMap(), label = "Covered card") else shown
            val face = if (item.kind == TableItemKind.CARD) {
                val from = a.item.faceTransform(a.bounds); val to = b.item.faceTransform(b.bounds)
                TableCardTransform(mix(from.width, to.width), mix(from.height, to.height), mix(from.rotation, to.rotation))
            } else null
            val bounds = when {
                t == 0f -> a.bounds
                t == 1f -> b.bounds
                face != null -> {
                    val center = TablePoint(mix(a.bounds.center.x, b.bounds.center.x), mix(a.bounds.center.y, b.bounds.center.y))
                    if (a.item.zone == PlayZone.BATTLEFIELD && b.item.zone == PlayZone.BATTLEFIELD &&
                        !item.projection && a.item.footprint == b.item.footprint &&
                        a.item.faceTransform(a.bounds).rotation != b.item.faceTransform(b.bounds).rotation &&
                        a.bounds.intersection(item.footprint) == a.bounds && b.bounds.intersection(item.footprint) == b.bounds &&
                        face.bounds(center).let { it.width <= item.footprint.width && it.height <= item.footprint.height })
                        face.boundsInside(center, item.footprint)
                    else face.bounds(center)
                }
                else -> TableBounds(mix(a.bounds.x, b.bounds.x), mix(a.bounds.y, b.bounds.y),
                    mix(a.bounds.width, b.bounds.width), mix(a.bounds.height, b.bounds.height))
            }
            HistoryAnimatedItem(item.copy(cardTransform = face, flipScale = if (flipping) kotlin.math.abs(1f - 2f * t).coerceAtLeast(.02f) else 1f,
                inTransit = item.kind == TableItemKind.CARD && t < 1f &&
                (a.bounds != b.bounds || a.item.faceTransform(a.bounds) != b.item.faceTransform(b.bounds))), bounds, mix(a.alpha, b.alpha))
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
        for (projection in destination.stackProjections) if (!target.stackContains(projection.cardId) && projection.animationId !in start) {
            start = start + (projection.animationId to HistoryAnimatedItem(projection, projection.bounds, 1f))
        }
        for (projection in target.stackProjections) if (!destination.stackContains(projection.cardId) && projection.animationId !in end) {
            end = end + (projection.animationId to HistoryAnimatedItem(projection, projection.bounds, 0f))
        }
        // Hidden library instances have no individual scene item. Seed every newly drawn card
        // from its former pile, using stable instance identity (also covers multi-card draws).
        val arrivals = end.values.filter { it.item.kind == TableItemKind.CARD && it.item.animationId !in start }
        for (arrival in arrivals) {
            val item = arrival.item
            if (item.zone == org.asyncttwiz.protocol.PlayZone.STACK && item.projection &&
                !destination.stackContains(item.cardId)) {
                destination.realCardBounds(item.sourceId)?.let { bounds ->
                    start = start + (item.animationId to arrival.copy(bounds = bounds, alpha = 1f))
                }
                continue
            }
            val pile = destination.items.firstOrNull { it.kind == TableItemKind.PILE && arrival.item.cardId in it.cards } ?: continue
            val bounds = TableBounds(pile.bounds.x + 8f, pile.bounds.y + 8f, 28f, 40f)
            start = start + (arrival.item.animationId to arrival.copy(bounds = bounds,
                item = arrival.item.copy(zone = pile.zone, viewport = pile.viewport)))
        }
        // A removed occurrence flies home independently of the real card, which stays drawn in place.
        // Membership (not the top-three drawings) distinguishes removal from a newly covered peek.
        for (departure in start.values.filter { it.item.kind == TableItemKind.CARD && it.item.projection &&
            it.item.zone == org.asyncttwiz.protocol.PlayZone.STACK && !target.stackContains(it.item.cardId) &&
            it.item.animationId !in end }) {
            val source = departure.item.sourceId
            if (destination.sources[source] != target.sources[source]) continue
            target.realCardBounds(source)?.let { bounds ->
                end = end + (departure.item.animationId to departure.copy(bounds = bounds, alpha = 0f))
            }
        }
        // Hidden pile arrivals have no individual destination drawing; fly back to the pile.
        for (departure in start.values.filter { it.item.kind == TableItemKind.CARD && !it.item.projection && it.item.animationId !in end }) {
            target.realCardBounds(departure.item.cardId)?.let { bounds ->
                val pile = target.items.firstOrNull { it.kind == TableItemKind.PILE && departure.item.cardId in it.cards }
                end = end + (departure.item.animationId to departure.copy(bounds = bounds, alpha = 0f,
                    item = departure.item.copy(zone = pile?.zone ?: departure.item.zone,
                        viewport = pile?.viewport ?: departure.item.viewport)))
            }
        }
        // Rotation changes its endpoint AABB/anchor too; that alone is not a move. Any other
        // transition (including a move alongside a tap) keeps the longer scene duration.
        rotatingCardIds = end.values.mapNotNull { b ->
            val a = start[b.item.animationId] ?: return@mapNotNull null
            b.item.cardId?.takeIf { b.item.kind == TableItemKind.CARD &&
                a.item.faceTransform(a.bounds).rotation != b.item.faceTransform(b.bounds).rotation }
        }.toSet()
        durationMillis = if ((start.keys + end.keys).all { id ->
            val a = start[id]; val b = end[id]
            a != null && b != null && a.alpha == b.alpha && (a.bounds == b.bounds && a.item.tapped == b.item.tapped ||
                a.item.kind == TableItemKind.CARD && a.item.faceTransform(a.bounds).rotation != b.item.faceTransform(b.bounds).rotation &&
                a.item.viewport == b.item.viewport && (a.item.footprint == b.item.footprint ||
                    a.bounds.center == b.bounds.center ||
                    a.item.zone == PlayZone.HAND && b.item.zone == PlayZone.HAND &&
                    a.item.footprint.x == b.item.footprint.x && a.item.footprint.center.y == b.item.footprint.center.y) &&
                a.item.faceTransform(a.bounds).let { from -> b.item.faceTransform(b.bounds).let { to ->
                    from.width == to.width && from.height == to.height
                } })
        }) 90 else 180
        destination = target
        return true
    }
    fun hitTest(x: Float, y: Float, progress: Float): TableItem? = frame(progress).asReversed().firstOrNull {
        it.alpha > 0f && x >= it.bounds.x && x <= it.bounds.x + it.bounds.width &&
            y >= it.bounds.y && y <= it.bounds.y + it.bounds.height
    }?.item

    private fun TableScene.stackContains(id: String?) = items.any {
        it.zone == org.asyncttwiz.protocol.PlayZone.STACK && it.kind == TableItemKind.PILE && id in it.cards
    }
    private fun TableScene.realCardBounds(source: String?): TableBounds? {
        items.firstOrNull { it.kind == TableItemKind.CARD && !it.projection && it.cardId == source }?.let { return it.bounds }
        val pile = items.firstOrNull { it.kind == TableItemKind.PILE && source in it.cards } ?: return null
        return TableBounds(pile.bounds.x + 8f, pile.bounds.y + 8f, cardWidth, cardHeight)
    }
}
