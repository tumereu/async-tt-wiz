package org.asyncttwiz.android

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.setValue
import org.asyncttwiz.protocol.*
import kotlinx.collections.immutable.toPersistentMap

enum class HistoryMode { LIVE, PLAYBACK }
/** What the timeline transport is doing, for the table's centre text and corner flash. */
enum class TimelinePlaybackStatus { PLAYING, PAUSED, REWINDING }
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
    /** Snapshot state: texts made later (a reviewed board) reach the timeline and tree dialog. Lazy text
     * maps compare equal while still empty, so a new map always counts as a change. */
    var texts by mutableStateOf(texts, referentialEqualityPolicy()); internal set
    private val changed = if (source != null && previous?.source != null) source.changedNodeIdsSince(previous.source) else null
    private val priorIndex: HistoryIndex? = previous?.takeIf { changed != null && (!lazyPresentation || it.hasPresentationIndex) }?.index
    private val priorNodes: Map<String, GameplayMutation> = if (priorIndex != null) previous?.nodes.orEmpty() else emptyMap()
    private val presentationIndex: Lazy<HistoryIndex> = lazy { HistoryIndex.build(rootId, nodes, containers, priorIndex,
        priorNodes, if (priorIndex != null) changed!! else nodes.keys, source?.initial) }
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
        // Geometry is ingested with the ordinary history index, never on dialog open.
        index
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
    /** One group forward from [cursor] along the line ending at [endpoint] (the live line by default). */
    internal fun forwardGroup(cursor: String, endpoint: String = sharedHeadId): String {
        val path = path(endpoint)
        var offset = depth(cursor) + 1
        if (offset >= path.size) return sharedHeadId
        val group = group(path[offset])?.id
        while (offset + 1 < path.size && group(path[offset + 1])?.id == group) { navigationReads++; offset++ }
        return path[offset]
    }
    fun version(id: String) = if (id == sharedHeadId) liveVersion else versions[id] ?: id
    private fun children(id: String): Collection<String> =
        if (source != null) source.endpointIndex().children[id].orEmpty() else index.children[id].orEmpty()
    private fun branchOf(id: String): String? = nodes[id]?.branchId ?: index.layout.chips[rootId]?.branch
    /** The tip of the line through [id]: at each move it keeps to the move's own branch (the lane the game
     * tree draws it on); where that branch ends but new branches start, it takes the most recent of them
     * (the newest lane). Walks forward over IDs only, never a board. */
    internal fun branchTip(id: String): String {
        var cursor = id
        while (true) {
            val next = children(cursor)
            if (next.isEmpty()) return cursor
            navigationReads++
            val branch = branchOf(cursor)
            cursor = next.firstOrNull { nodes[it]?.branchId == branch } ?: next.maxWith(
                compareBy({ index.layout.branches[nodes[it]?.branchId]?.lane ?: -1 }, { it }))
        }
    }

    companion object {
        /** Owned by the downloaded generation, not by the lifetime of a table screen. */
        fun ingest(tree: GameplayTree, version: String, previous: HistoryTree? = null) = HistoryTree(
            tree.initial.root.id, emptyList(), tree.currentNodeId, version,
            containers = tree.initial.board.containers, source = tree,
            previous = previous?.takeIf { it.rootId == tree.initial.root.id }, lazyPresentation = true)
    }
}

/** Device-local transport only. No sync, request, activation, Undo or prediction ownership here. */
class HistoryPlayback(initial: HistoryTree,
    private val onStep: (String, String, HistoryTree) -> Unit = { _, _, _ -> },
    private val onJump: () -> Unit = {}) {
    var tree: HistoryTree by mutableStateOf(initial); private set
    var position by mutableStateOf(HistoryPosition(initial.sharedHeadId, displayVersion = initial.liveVersion)); private set
    var foreground by mutableStateOf(true); private set
    /** A remote flip or final board is still LIVE, but must settle before moving on. */
    var livePanelReveal by mutableStateOf(false); private set
    private var panelViewer: String? = null
    private var removedParents: Map<String, String> = emptyMap()
    private var rollbackPlan: Pair<String, String>? = null
    private var autoPauseCursor: String? = null
    private var finalAnimationCursor: String? = null
    /** The shown point is off the current head's path because the line was continued differently
     * (detected on open, resume and receipt while paused). Persists until the rewind passes the
     * common point or the player scrubs; Previous/Next and the strip stay locked and the Auto button
     * only offers the rewind (no hold-to-branch). */
    var branched by mutableStateOf(false); private set
    /** A point the player chose explicitly (tree, review): its line stays the visible line while the
     * cursor stays on it, also on the part shared with the live line. Null: the cursor's own line. */
    private var lineAnchor by mutableStateOf<String?>(null)
    private var tipCache: Pair<HistoryTree, String>? = null
    /** Leaving the app paused live mode at the shown point; resolved once the return has caught up. */
    var resumeHold by mutableStateOf(false); private set
    /** Held at the head with nothing unseen yet: keep presenting it as live (no timeline flash). */
    val resumeHeld get() = resumeHold && position.mode == HistoryMode.PLAYBACK && position.cursorId == tree.sharedHeadId
    /** Only the running rewind to the common point is locked; a paused pending branch is not. */
    val transportLocked get() = position.playing && rollbackPlan != null && !tree.isAncestor(position.cursorId, tree.sharedHeadId)
    /** A pending branch locks the strip too: the player must watch the rewind, only choosing when it starts. */
    val stripLocked get() = branched || transportLocked
    /** The shown point is not on the live line (an old branch). Auto then means rewind, then play on. */
    val offLiveLine get() = position.mode == HistoryMode.PLAYBACK && !tree.isAncestor(position.cursorId, tree.sharedHeadId)
    /** An old branch the player went to themselves: not locked, unlike [branched]. */
    val oldBranch get() = offLiveLine && !branched
    /** The tip of the line Previous/Next and the strip move along: the live line, or the old branch the
     * cursor is on (from the root to that branch's own tip, see [HistoryTree.branchTip]). */
    val visibleTip: String get() {
        if (position.mode == HistoryMode.LIVE) return tree.sharedHeadId
        val cursor = position.cursorId
        lineAnchor?.takeIf(tree::contains)?.let(::lineTip)?.takeIf { tree.isAncestor(cursor, it) }?.let { return it }
        return lineTip(cursor)
    }
    private fun lineTip(id: String): String {
        if (tree.isAncestor(id, tree.sharedHeadId)) return tree.sharedHeadId
        // The per-move rule does not depend on where the walk starts: any point of a known line has its tip.
        tipCache?.takeIf { it.first === tree && tree.isAncestor(id, it.second) }?.let { return it.second }
        return tree.branchTip(id).also { tipCache = tree to it }
    }
    /** Automatic steps head for the live game: an explicit line no longer holds once left or once live. */
    private fun dropStaleAnchor(live: Boolean) {
        val anchor = lineAnchor ?: return
        if (!tree.contains(anchor) || (live && tree.isAncestor(position.cursorId, tree.sharedHeadId)) ||
            !tree.isAncestor(position.cursorId, lineTip(anchor))) lineAnchor = null
    }
    val status get() = when {
        transportLocked -> TimelinePlaybackStatus.REWINDING
        position.playing -> TimelinePlaybackStatus.PLAYING
        else -> TimelinePlaybackStatus.PAUSED
    }
    /** A manual start takes its first step at once; later steps (and auto-continuation) keep the pause. */
    private var immediateStepCursor: String? = null

    /** No route list or replay is built up front. A missing/deleted bookmark starts at root.
     * Anything unseen opens timeline mode paused at the last seen point; nothing plays by itself. */
    fun openFrom(lastSeen: String?) {
        // No saved point (first open, another device): open live at the newest move, never from the start.
        if (lastSeen == null) return
        val cursor = tree.source?.resolveCursor(lastSeen) ?: lastSeen.takeIf(tree::contains) ?: tree.rootId
        if (cursor == tree.sharedHeadId) return
        position = HistoryPosition(cursor, HistoryMode.PLAYBACK, false, tree.version(cursor))
        lineAnchor = null
        detectBranch()
    }
    /** Cheap (common-ancestor walk over the divergent part only): never a replay from the root. */
    private fun planRollback(): String {
        if (tree.isAncestor(position.cursorId, tree.sharedHeadId)) return position.cursorId
        rollbackPlan?.takeIf { it.first == tree.sharedHeadId && tree.isAncestor(it.second, position.cursorId) }?.let { return it.second }
        val common = tree.commonAncestor(position.cursorId, tree.sharedHeadId)
        rollbackPlan = tree.sharedHeadId to common
        return common
    }
    private fun detectBranch() {
        if (position.mode != HistoryMode.PLAYBACK || tree.isAncestor(position.cursorId, tree.sharedHeadId)) {
            branched = false; rollbackPlan = null
        } else { planRollback(); branched = true }
    }
    /** Forward groups wait [normal]; rolling back toward a branch point waits the separate [rewind]. */
    fun stepPauseMillis(normal: Long, rewind: Long = HistoryPlaybackPreferences.DEFAULT_REWIND_PAUSE_MILLIS): Long =
        if (!tree.isAncestor(position.cursorId, tree.sharedHeadId)) rewind.coerceAtLeast(1) else normal
    /** The wait before the next automatic step: none right after a manual start, else [stepPauseMillis]. */
    fun nextStepDelayMillis(normal: Long, rewind: Long = HistoryPlaybackPreferences.DEFAULT_REWIND_PAUSE_MILLIS): Long =
        if (position.playing && immediateStepCursor == position.cursorId) 0 else stepPauseMillis(normal, rewind)

    fun receive(next: HistoryTree, viewer: String? = null) {
        require(next.rootId == tree.rootId) { "Create fresh playback for a new play incarnation" }
        removedParents = if (next.source != null) next.removedParents else {
            var parents = removedParents.toPersistentMap()
            for ((id, node) in tree.nodes) if (id !in next.nodes) parents = parents.put(id, node.parentId)
            parents
        }
        // Only a head that moved away from the shown point is a new branch; a point the player
        // scrubbed to on an old line stays a plain scrub.
        val onHeadPath = position.mode == HistoryMode.PLAYBACK && tree.isAncestor(position.cursorId, tree.sharedHeadId)
        tree = next
        panelViewer = viewer
        val cursor = resolveCursor(position.cursorId)
        if (cursor != position.cursorId) onJump()
        val held = livePanelReveal && tree.contains(position.cursorId) && tree.isAncestor(position.cursorId, tree.sharedHeadId)
        val reveal = if (foreground && position.mode == HistoryMode.LIVE) {
            if (held) position.cursorId else nextPanelReveal(cursor)
        } else null
        livePanelReveal = reveal != null
        position = when {
            reveal != null -> HistoryPosition(reveal, displayVersion = tree.version(reveal))
            foreground && position.mode == HistoryMode.LIVE -> HistoryPosition(tree.sharedHeadId, displayVersion = tree.liveVersion)
            resumeHold && position.mode == HistoryMode.PLAYBACK && position.displayVersion == tree.liveVersion ->
                position.copy(cursorId = tree.sharedHeadId) // Expected own confirmation: no unseen presentation difference.
            cursor != position.cursorId -> position.copy(cursorId = cursor,
                playing = false, displayVersion = tree.version(cursor))
            foreground && position.mode == HistoryMode.PLAYBACK -> position.copy(displayVersion = tree.version(cursor))
            else -> position // Accepted receipt never advances a paused or background cursor.
        }
        if (position.playing) planRollback()
        else if (position.mode == HistoryMode.PLAYBACK && (branched || onHeadPath)) detectBranch()
        dropStaleAnchor(live = false)
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

    /** Leaving pauses live mode at the shown point at once: events delivered later (while away, or
     * by the reconnect catch-up after returning) must not play back as live. */
    fun background() {
        foreground = false
        if (position.mode != HistoryMode.LIVE) return
        livePanelReveal = false; finalAnimationCursor = null; autoPauseCursor = null
        position = position.copy(mode = HistoryMode.PLAYBACK, playing = false)
        resumeHold = true
    }
    /** Returning keeps the pause; [caughtUp] decides once the missed events have been received. */
    fun foreground() { foreground = true }
    /** The return has received everything the server had. Only unseen nodes justify timeline mode;
     * a revision bump without one (e.g. chat) silently returns to live. */
    fun caughtUp() {
        if (!foreground || !resumeHold) return
        resumeHold = false
        if (position.mode == HistoryMode.PLAYBACK && !position.playing && position.cursorId == tree.sharedHeadId) continuationApplied()
        else if (position.mode == HistoryMode.PLAYBACK && !position.playing) detectBranch()
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
    /** An explicit pick (tree, review): its line becomes the visible line. */
    fun scrub(id: String) { scrubTo(id, true); lineAnchor = position.cursorId }
    /** Strip drag, chip tap and accessibility scroll: ignored while a branch is pending or rewinding.
     * The strip shows the visible line, so the visible line stays. */
    fun scrubFromStrip(id: String) { if (!stripLocked) { keepVisibleLine(); scrubTo(id, true) } }
    /** Steps and strip moves stay on the line shown, also onto the part it shares with the live line. */
    private fun keepVisibleLine() { lineAnchor = visibleTip }
    fun pauseFromStrip() { if (!stripLocked) pause() }
    private fun scrubTo(id: String, jump: Boolean) {
        if (jump) onJump()
        // A queued UI/review callback can refer to a deleted or not-yet-indexed boundary.
        val cursor = resolveCursor(id)
        livePanelReveal = false
        autoPauseCursor = null
        finalAnimationCursor = null
        resumeHold = false
        position = HistoryPosition(cursor, HistoryMode.PLAYBACK, false, tree.version(cursor))
        rollbackPlan = null; branched = false
    }
    fun pause() {
        if (transportLocked) return
        autoPauseCursor = null; immediateStepCursor = null; position = position.copy(playing = false)
    }
    fun play() {
        if (position.mode != HistoryMode.PLAYBACK) return
        if (transportLocked) return
        if (position.cursorId == tree.sharedHeadId) { continuationApplied(); return }
        resumeHold = false; livePanelReveal = false; position = position.copy(playing = true); planRollback()
        immediateStepCursor = position.cursorId
    }

    /** One group back along the visible line, like a one-group strip swipe. Never starts playback. */
    fun previous() {
        if (stripLocked) return
        keepVisibleLine()
        val from = position.cursorId
        scrubTo(tree.previousGroup(position.cursorId), false)
        onStep(from, position.cursorId, tree)
    }

    /** Manual: one group forward along the visible line (live line or the old branch shown), like a
     * one-group strip swipe; never a rewind or playback. Automatic: one concrete group endpoint toward
     * the live game, including rollback groups on a displaced retained path. */
    fun next(automatic: Boolean = false, viewer: String? = null,
        autoPause: Set<PlaybackAutoPause> = PlaybackAutoPause.defaults, board: BoardState? = null,
        definitions: List<SubmittedDefinition> = emptyList()) {
        if (!automatic) {
            if (stripLocked) return
            val from = position.cursorId
            val tip = visibleTip
            if (from == tip) return
            lineAnchor = tip
            val target = tree.forwardGroup(from, tip)
            scrubTo(target, false)
            onStep(from, position.cursorId, tree)
            return
        }
        if (!foreground || !position.playing) return
        livePanelReveal = false; resumeHold = false
        val common = planRollback()
        immediateStepCursor = null
        val target: String
        if (position.cursorId != common) {
            val previous = tree.previousGroup(position.cursorId)
            target = if (tree.depth(previous) < tree.depth(common)) common else previous
        } else {
            target = tree.forwardGroup(position.cursorId)
        }
        autoPauseCursor = target.takeIf { viewer != null && position.cursorId == common &&
            target != tree.sharedHeadId && tree.autoPauseBetween(position.cursorId, target, viewer, autoPause, board, definitions) }
        // Keep the historical scene mounted until its final movement has actually settled.
        finalAnimationCursor = target.takeIf { it == tree.sharedHeadId }
        val from = position.cursorId
        position = HistoryPosition(target, HistoryMode.PLAYBACK, true, tree.version(target))
        onStep(from, target, tree)
        if (target == common) { branched = false; rollbackPlan = null }
        dropStaleAnchor(live = true)
    }

    /** Idempotent, cursor-fenced handoff: a stale animation callback cannot skip newer events. */
    fun finishAnimation(cursor: String): Boolean {
        if (!foreground || position.cursorId != cursor || finalAnimationCursor != cursor) return false
        finalAnimationCursor = null
        if (cursor != tree.sharedHeadId) return false
        continuationApplied()
        return true
    }

    /** Invoked only once the selected group's scene is on screen. Resuming cannot re-trigger it. */
    fun pauseAfterAnimation(): Boolean {
        if (!foreground || !position.playing || autoPauseCursor != position.cursorId) return false
        pause()
        return true
    }

    /** Capture on pointer down. Deletion/fallback must never silently arm a different boundary. */
    fun canContinue(capturedId: String) = position.mode == HistoryMode.PLAYBACK && !position.playing &&
        position.cursorId == capturedId && tree.contains(capturedId)
    fun continuationApplied() {
        if (position.cursorId != tree.sharedHeadId) onJump()
        livePanelReveal = false; finalAnimationCursor = null; autoPauseCursor = null
        rollbackPlan = null; branched = false; resumeHold = false; lineAnchor = null
        position = HistoryPosition(tree.sharedHeadId, displayVersion = tree.liveVersion)
    }
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

/** Receipts, count labels, ownership and disclosure metadata are not motion targets. A dock pile's members are:
 * they have no drawing of their own, so a move between two piles changes nothing else in the scene. */
internal val TableItem.animationId get() = if (kind == TableItemKind.CARD) "card/$cardId" else id
internal val TableScene.animationTargets get() = (items + stackProjections).associate { it.animationId to
    listOf(it.bounds, it.tapped, it.covered, it.faceId, it.cards.takeIf { _ -> it.dockPile }) }
private val TableItem.dockPile get() = kind == TableItemKind.PILE && zone in DOCK_PILE_ZONES

internal const val CARD_ROTATION_DURATION_MILLIS = 90
internal const val CARD_FLIP_DURATION_MILLIS = 350
internal const val CARD_FADE_DURATION_MILLIS = 350
internal const val LIBRARY_SHUFFLE_DURATION_MILLIS = 280
internal const val HAND_REFLOW_DURATION_MILLIS = 220
private val DOCK_PILE_ZONES = setOf(PlayZone.LIBRARY, PlayZone.GRAVEYARD, PlayZone.EXILE)

/** The scene consumer samples these bounds for drawing AND hit testing/accessibility. No mutation waypoint queue. */
class HistorySceneAnimation(initial: TableScene) {
    internal var cardMoveSpeedDpPerMs: Float = DEFAULT_CARD_MOVE_SPEED
    private var sceneMoveSpeed = DEFAULT_CARD_MOVE_SPEED
    private var destination = initial
    private var start = initial.items.associate { it.animationId to HistoryAnimatedItem(it, it.bounds, 1f) }
    private var end = start
    var durationMillis: Int = MAX_CARD_MOVE_DURATION_MILLIS; private set
    internal fun remainingDurationMillis(progress: Float) =
        (durationMillis * (1f - progress.coerceIn(0f, 1f))).toInt().coerceAtLeast(1)
    internal var rotatingCardIds: Set<String> = emptySet(); private set
    private var flippingCardIds: Set<String> = emptySet()
    private var stackFlightIds: Set<String> = emptySet()
    private var shufflingPileIds: Set<String> = emptySet()
    private var lastShuffleCursor: String? = null
    private var latestScene = initial
    private var latestItems = initial.items.associateBy { it.animationId }
    private fun rotationOnly(a: HistoryAnimatedItem, b: HistoryAnimatedItem): Boolean =
        a.item.kind == TableItemKind.CARD && a.item.faceTransform(a.bounds).rotation != b.item.faceTransform(b.bounds).rotation &&
            a.item.viewport == b.item.viewport && (a.item.footprint == b.item.footprint ||
            a.bounds.center == b.bounds.center || a.item.zone == PlayZone.HAND && b.item.zone == PlayZone.HAND &&
            a.item.footprint.x == b.item.footprint.x && a.item.footprint.center.y == b.item.footprint.center.y) &&
            a.item.faceTransform(a.bounds).let { from -> b.item.faceTransform(b.bounds).let { to -> from.width == to.width && from.height == to.height } }
    private fun flipping(a: HistoryAnimatedItem, b: HistoryAnimatedItem) = b.item.cardId in flippingCardIds ||
        a.item.covered != b.item.covered && a.item.viewport.startsWith("cardPanel/") && b.item.viewport == a.item.viewport
    // Retargeted flights already carry destination metadata, but have not joined the hand yet.
    private fun handReflow(a: HistoryAnimatedItem, b: HistoryAnimatedItem) =
        a.item.kind == TableItemKind.CARD && b.item.kind == TableItemKind.CARD &&
            !a.item.inTransit &&
            a.item.zone == PlayZone.HAND && b.item.zone == PlayZone.HAND && a.item.viewport == b.item.viewport
    private fun handReflowDuration() = kotlin.math.round(HAND_REFLOW_DURATION_MILLIS * DEFAULT_CARD_MOVE_SPEED / sceneMoveSpeed)
        .toInt().coerceAtLeast(1)
    private fun moveDuration(a: HistoryAnimatedItem, b: HistoryAnimatedItem): Int = when {
        a.bounds == b.bounds -> 0
        rotationOnly(a, b) -> CARD_ROTATION_DURATION_MILLIS
        handReflow(a, b) -> handReflowDuration()
        b.item.kind != TableItemKind.CARD -> MAX_CARD_MOVE_DURATION_MILLIS
        a.bounds.center == b.bounds.center -> MAX_CARD_MOVE_DURATION_MILLIS
        else -> cardMoveDurationMillis(kotlin.math.hypot(b.bounds.center.x - a.bounds.center.x,
            b.bounds.center.y - a.bounds.center.y), sceneMoveSpeed)
    }
    fun updateMetadata(target: TableScene) {
        if (target.animationTargets == destination.animationTargets) destination = target
    }
    fun frame(progress: Float, presentation: TableScene = destination): List<HistoryAnimatedItem> {
        val sceneProgress = progress.coerceIn(0f, 1f)
        val elapsed = sceneProgress * durationMillis
        fun channel(duration: Int) = (elapsed / duration).coerceIn(0f, 1f)
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
                id !in shufflingPileIds && a.item.cardId !in flippingCardIds && a.item.covered == b.item.covered && a.item.faceTransform(a.bounds) == b.item.faceTransform(b.bounds)) {
                val shown = latestItems[id] ?: b.item
                return@map if (shown === b.item) b else HistoryAnimatedItem(shown, b.bounds, b.alpha)
            }
            val rotationProgress = channel(CARD_ROTATION_DURATION_MILLIS)
            val flipProgress = channel(CARD_FLIP_DURATION_MILLIS)
            val reflow = handReflow(a, b)
            val t = if (rotationOnly(a, b)) rotationProgress else if (reflow) {
                val t = channel(handReflowDuration())
                1f - (1f - t) * (1f - t)
            } else if (b.item.kind == TableItemKind.CARD)
                cardMoveProgress(a.bounds, b.bounds, channel(MAX_CARD_MOVE_DURATION_MILLIS), MAX_CARD_MOVE_DURATION_MILLIS, sceneMoveSpeed)
                else channel(MAX_CARD_MOVE_DURATION_MILLIS)
            fun mix(x: Float, y: Float) = x + (y - x) * t
            val flipping = flipping(a, b)
            val metadata = if (flipping && flipProgress < .5f) a.item else latestItems[id] ?: b.item
            // Real spells share the destination card's identity. While flying they still leave
            // the screen-fixed Stack, not the clipped Battlefield viewport they are entering.
            val shown = if (id in stackFlightIds && t < 1f) metadata.copy(zone = PlayZone.STACK, viewport = a.item.viewport)
                else metadata
            // A flying face cannot expose a Library top, including at the exact departure frame.
            val item = if (t < 1f && shown.kind == TableItemKind.CARD && a.bounds != b.bounds &&
                listOf(a.item, b.item).any { it.zone == PlayZone.LIBRARY && !it.viewport.startsWith("cardPanel/") })
                shown.copy(covered = true, panelRevealed = false, artwork = null, value = "", note = "", corner = "",
                    typeIcon = "", counters = emptyMap(), label = "Covered card") else shown
            val face = if (item.kind == TableItemKind.CARD) {
                val from = a.item.faceTransform(a.bounds); val to = b.item.faceTransform(b.bounds)
                TableCardTransform(mix(from.width, to.width), mix(from.height, to.height),
                    from.rotation + (to.rotation - from.rotation) * rotationProgress)
            } else null
            val bounds = when {
                t == 0f -> a.bounds
                t == 1f && (face == null || face.rotation == b.item.faceTransform(b.bounds).rotation) -> b.bounds
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
            val alphaProgress = channel(CARD_FADE_DURATION_MILLIS)
            val shuffle = channel(LIBRARY_SHUFFLE_DURATION_MILLIS)
            val shuffledBounds = if (id in shufflingPileIds) bounds.copy(x = bounds.x +
                (kotlin.math.sin(shuffle * 6f * kotlin.math.PI) * 6f * (1f - shuffle)).toFloat()) else bounds
            HistoryAnimatedItem(item.copy(cardTransform = face, flipScale = if (flipping) kotlin.math.abs(1f - 2f * flipProgress).coerceAtLeast(.02f) else 1f,
                inTransit = item.kind == TableItemKind.CARD && (t < 1f && a.bounds != b.bounds && !reflow ||
                    rotationProgress < 1f && a.item.faceTransform(a.bounds).rotation != b.item.faceTransform(b.bounds).rotation)), shuffledBounds,
                a.alpha + (b.alpha - a.alpha) * alphaProgress)
        }.sortedBy { it.item.inTransit }
    }
    /** False means confirmation/metadata only: the caller must leave the running clock alone. */
    fun retarget(target: TableScene, currentProgress: Float, faceChanges: Set<String> = emptySet(),
        shuffles: Set<String> = emptySet(), shuffleCursor: String? = null): Boolean {
        val freshShuffle = shuffles.isNotEmpty() && shuffleCursor != null && shuffleCursor != lastShuffleCursor
        if (target.animationTargets == destination.animationTargets && !freshShuffle) {
            if (shuffleCursor != null) lastShuffleCursor = shuffleCursor
            destination = target
            return false
        }
        start = frame(currentProgress).filter { it.alpha > 0f }.associateBy { it.item.animationId }
        // A preference update applies to the next scene, not to an already-sized running clock.
        sceneMoveSpeed = cardMoveSpeedDpPerMs
        shufflingPileIds = if (freshShuffle) target.items.filter { it.id in shuffles && it.zone == PlayZone.LIBRARY && it.kind == TableItemKind.PILE }
            .map { it.animationId }.toSet() else emptySet()
        if (shuffleCursor != null) lastShuffleCursor = shuffleCursor
        end = target.items.associate { it.animationId to HistoryAnimatedItem(it, it.bounds, 1f) }
        flippingCardIds = faceChanges.filter { id ->
            val a = start["card/$id"]?.item; val b = end["card/$id"]?.item
            a != null && b != null && (a.covered != b.covered || a.faceId != b.faceId)
        }.toSet()
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
            val bounds = pile.bounds
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
        // Dock pile members (Library, Graveyard, Exile, Temp piles) have no drawing of their own, so a card moving
        // from one pile to another flies from pile to pile: with the face its pile shows (Graveyard/Exile), else
        // face down. A Library endpoint keeps the flight covered (see frame), exactly like draws.
        val formerPiles = destination.pileMembers()
        for ((cardId, to) in target.pileMembers()) {
            val from = formerPiles[cardId] ?: continue
            val key = "card/$cardId"
            if (from.id == to.id || key in start || key in end) continue
            fun drawing(scene: TableScene, pile: TableItem) = scene.pileCards[cardId]?.copy(bounds = pile.bounds,
                footprint = pile.bounds, viewport = pile.viewport) ?: TableItem(key, pile.bounds, "Covered card", "",
                pile.owner, pile.zone, kind = TableItemKind.CARD, viewport = pile.viewport, cardId = cardId,
                covered = true, footprint = pile.bounds)
            start = start + (key to HistoryAnimatedItem(drawing(destination, from), from.bounds, 1f))
            end = end + (key to HistoryAnimatedItem(drawing(target, to), to.bounds, 0f))
        }
        val flights = stackRemovalFlights(destination, target)
        stackFlightIds = flights.map { it.entry.animationId }.toSet()
        for (flight in flights) {
            val entry = flight.entry
            val departure = start[entry.animationId] ?: HistoryAnimatedItem(entry, entry.bounds, 1f)
            start = start + (entry.animationId to departure)
            if (entry.projection) {
                // The real card is already at its accepted destination; do not also fly it from
                // Hand/Battlefield while its Stack projection is resolving to that same place.
                val realId = "card/${entry.sourceId}"
                end[realId]?.let { start = start + (realId to it) }
                end = end + (entry.animationId to departure.copy(bounds = flight.destination.bounds, alpha = 0f))
            }
        }
        // Rotation changes its endpoint AABB/anchor too; that alone is not a move. Any other
        // transition (including a move alongside a tap) keeps the longer scene duration.
        rotatingCardIds = end.values.mapNotNull { b ->
            val a = start[b.item.animationId] ?: return@mapNotNull null
            b.item.cardId?.takeIf { b.item.kind == TableItemKind.CARD &&
                a.item.faceTransform(a.bounds).rotation != b.item.faceTransform(b.bounds).rotation }
        }.toSet()
        durationMillis = (start.keys + end.keys).maxOfOrNull { id ->
            val a = start[id] ?: end.getValue(id).copy(alpha = 0f)
            val b = end[id] ?: start.getValue(id).copy(alpha = 0f)
            maxOf(moveDuration(a, b),
                if (a.item.faceTransform(a.bounds).rotation != b.item.faceTransform(b.bounds).rotation) CARD_ROTATION_DURATION_MILLIS else 0,
                if (flipping(a, b)) CARD_FLIP_DURATION_MILLIS else 0,
                if (a.alpha != b.alpha) CARD_FADE_DURATION_MILLIS else 0)
        }?.coerceAtLeast(if (shufflingPileIds.isEmpty()) 1 else LIBRARY_SHUFFLE_DURATION_MILLIS) ?: 1
        destination = target
        return true
    }
    fun hitTest(x: Float, y: Float, progress: Float): TableItem? = frame(progress).asReversed().firstOrNull {
        it.alpha > 0f && x >= it.bounds.x && x <= it.bounds.x + it.bounds.width &&
            y >= it.bounds.y && y <= it.bounds.y + it.bounds.height
    }?.item

    /** Card ID -> the dock pile (Library, Graveyard, Exile or Temp pile) holding it. */
    private fun TableScene.pileMembers(): Map<String, TableItem> = buildMap {
        for (item in items) if (item.dockPile) for (id in item.cards) put(id, item)
    }
    private fun TableScene.stackContains(id: String?) = items.any {
        it.zone == org.asyncttwiz.protocol.PlayZone.STACK && it.kind == TableItemKind.PILE && id in it.cards
    }
    private fun TableScene.realCardBounds(source: String?): TableBounds? {
        items.firstOrNull { it.kind == TableItemKind.CARD && !it.projection && it.cardId == source }?.let { return it.bounds }
        val pile = items.firstOrNull { (it.kind == TableItemKind.PILE || it.copies > 1) && source in it.cards } ?: return null
        return pile.bounds
    }
}
