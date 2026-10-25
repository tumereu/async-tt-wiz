package org.asyncttwiz.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonIgnoreUnknownKeys
import kotlin.concurrent.Volatile

/** The boundary is a stable parent plus the removed continuation, not a captured shared head. */
@Serializable data class GameplayRedoEntry(val mutation: GameplayMutation, val successorId: String?,
    val otherChildIds: Set<String>)

/** One immutable retained tree. Root data is immutable; materialized boards are derived, not per-node snapshots.
 * Queue order is oldest to newest (last entry is next). These queues are current account control state.
 * Relational adapters must commit the returned tree/delta with their inbox outcome atomically.
 */
// Stored/cached trees from older releases contain removed control metadata. It has no effect on play.
@JsonIgnoreUnknownKeys
@Serializable data class GameplayTree(val initial: PlayState,
    val nodes: Map<String, GameplayMutation> = emptyMap(), val currentNodeId: String = initial.root.id,
    val redoByAccount: Map<String, List<GameplayRedoEntry>> = emptyMap(),
    val removedParents: Map<String, String> = emptyMap(),
    val branches: Map<String, GameplayBranch> = emptyMap(),
    val currentBranchId: String = nodes[currentNodeId]?.branchId ?: initial.root.branchId,
    val branchHeads: Map<String, String> = mapOf(initial.root.branchId to initial.root.id),
    val selectionControlId: String? = null) {
    init { require(initial.currentNodeId == initial.root.id) }

    // Disposable current-only checkpoint, populated solely by replay or the shared reducer below.
    // Plain copies start cold. Validated deltas and explicit execution-domain copies may transfer
    // derived endpoints after checking ancestry. This is never wire/caller-supplied authority.
    @Transient @Volatile private var currentMaterialization: PlayState? =
        if (currentNodeId == initial.root.id) initial.copy(currentBranchId = currentBranchId) else null
    // One disposable worker-prepared review endpoint, not a per-node snapshot map or accepted data.
    // Tree edits/copy/decoding discard it.
    @Transient @Volatile private var reviewMaterialization: Pair<String, PlayState>? = null

    /** Prepare the exact personal cursor on a worker before enabling historical mutation callbacks.
     * The UI cannot inject a board; only this tree's shared replay can populate the disposable slot. */
    fun prepareReview(nodeId: String): PlayState? = stateAt(nodeId)?.also { reviewMaterialization = nodeId to it }

    /** Read-only cache lookup: never replay on a presentation thread. */
    fun preparedState(nodeId: String): PlayState? =
        currentMaterialization?.takeIf { it.currentNodeId == nodeId }
            ?: reviewMaterialization?.takeIf { it.first == nodeId }?.second

    /** Independent mutable cache slots for another execution domain; boards/topology are immutable. */
    fun materializedCopy(): GameplayTree = copy().also {
        it.currentMaterialization = currentMaterialization
        it.reviewMaterialization = reviewMaterialization
    }

    /** Transfer a worker-derived endpoint back to the identical immutable presentation generation. */
    fun receivePreparedReview(prepared: GameplayTree) {
        require(initial === prepared.initial && nodes === prepared.nodes && currentNodeId == prepared.currentNodeId)
        reviewMaterialization = prepared.reviewMaterialization
    }

    /** Reuse only endpoints whose complete ancestry is unchanged, then traverse the edited suffix.
     * Removed/reparented heads must be reversed in the OLD topology, not the new one. */
    internal fun inheritMaterialization(previous: GameplayTree, unchanged: Set<String>) {
        val review = previous.reviewMaterialization?.takeIf { it.first in unchanged }
        var cursor = previous.currentNodeId
        while (cursor !in unchanged) cursor = previous.nodes[cursor]?.parentId ?: return
        previous.stateAt(cursor)?.let { reviewMaterialization = cursor to it }
        stateAt(currentNodeId)?.let(::checkpoint)
        if (review != null) reviewMaterialization = review
    }

    @Transient internal var replayedNodes: Int = 0
        private set

    fun parentOf(nodeId: String): String? = nodes[nodeId]?.parentId ?: removedParents[nodeId]

    /** D-238: only a deleted cursor falls back; retained nodes never move to the shared head. */
    fun resolveCursor(nodeId: String): String? {
        var cursor = nodeId
        val seen = mutableSetOf<String>()
        while (cursor != initial.root.id && cursor !in nodes) {
            if (!seen.add(cursor)) return null
            cursor = removedParents[cursor] ?: return null
        }
        return cursor
    }

    /** Walk from the nearest known endpoint, reversing to the common ancestor then applying down.
     * Only an old event lacking inverse data requires the legacy root replay. */
    fun stateAt(nodeId: String): PlayState? {
        if (nodeId == currentNodeId) currentMaterialization?.let { return it }
        reviewMaterialization?.takeIf { it.first == nodeId }?.let { return it.second }
        val candidates = listOfNotNull(currentMaterialization, reviewMaterialization?.second)
        for (source in candidates) {
            val node = nodes[source.currentNodeId]
            val adjacent = when {
                node?.parentId == nodeId -> GameplayHistory.unapply(source, node)
                nodes[nodeId]?.parentId == source.currentNodeId -> nodes[nodeId]?.takeIf(::referencesMatch)?.let { replay(source, it) }
                else -> null
            }
            if (adjacent != null) {
                val state = if (nodeId == currentNodeId) adjacent.copy(currentBranchId = currentBranchId) else adjacent
                if (nodeId == currentNodeId) currentMaterialization = state else reviewMaterialization = nodeId to state
                return state
            }
        }
        val targetPath = path(nodeId) ?: return null
        val targetIds = targetPath.map { it.id }.toSet() + initial.root.id
        fun distance(state: PlayState): Int {
            var cursor = state.currentNodeId
            var up = 0
            while (cursor !in targetIds) { cursor = nodes[cursor]?.parentId ?: return Int.MAX_VALUE; up++ }
            return up + targetPath.size - (targetPath.indexOfFirst { it.id == cursor } + 1)
        }
        val source = candidates.minByOrNull(::distance)
        val reconstructed = source?.let { traverse(it, nodeId) } ?: materialize(nodeId) ?: return null
        val state = if (nodeId == currentNodeId) reconstructed.copy(currentBranchId = currentBranchId) else reconstructed
        if (nodeId == currentNodeId) currentMaterialization = state else reviewMaterialization = nodeId to state
        return state
    }

    private fun traverse(source: PlayState, nodeId: String): PlayState? {
        val down = mutableListOf<GameplayMutation>()
        val ancestors = mutableSetOf(initial.root.id)
        var cursor = source.currentNodeId
        while (cursor != initial.root.id) {
            if (!ancestors.add(cursor)) return null
            cursor = nodes[cursor]?.parentId ?: return null
        }
        cursor = nodeId
        val seen = mutableSetOf<String>()
        while (cursor !in ancestors) {
            if (!seen.add(cursor)) return null
            val node = nodes[cursor] ?: return null
            down += node; cursor = node.parentId
        }
        var state = source
        while (state.currentNodeId != cursor) {
            val node = nodes[state.currentNodeId] ?: return null
            state = GameplayHistory.unapply(state, node) ?: return null
        }
        for (node in down.asReversed()) {
            if (!referencesMatch(node)) return null
            state = replay(state, node) ?: return null
        }
        return state
    }

    /** Only shared reducers may seed a checkpoint; copies never inherit potentially stale boards. */
    internal fun checkpoint(state: PlayState): GameplayTree {
        require(state.currentNodeId == currentNodeId)
        currentMaterialization = state.copy(currentBranchId = currentBranchId)
        return this
    }

    internal fun preservingCheckpoint(next: GameplayTree): GameplayTree {
        if (next.initial == initial && next.nodes == nodes && next.currentNodeId == currentNodeId)
            currentMaterialization?.let { next.checkpoint(it) }
        return next
    }

    /** Ordinary new play advances the validated current materialization with the sole shared reducer.
     * Undo budgets do not cap normal play, retained metadata or cold reconstruction. Only the effective
     * mutation author's queue is cleared. No suffix is edited or elided by an ordinary append.
     */
    fun play(actor: String, command: GameCommand): GameplayTreeChange {
        if (!GameWire.valid(command) || command.action !is GameAction.Play)
            return GameplayTreeChange(reason = "invalidInput")
        val target = command.continuation?.nodeId ?: currentNodeId
        val state = stateAt(target) ?: return GameplayTreeChange(reason = "historyUnavailable")
        // A retained future also exists beyond an explicitly activated internal current pointer.
        // Merely viewing/arming never reaches this code. Allocate metadata only after real success.
        val forks = target != currentNodeId || nodes.values.any { it.parentId == target }
        val branchId = if (forks) CommandIds.uuidV5(command.requestId, "history-branch") else currentBranchId
        val change = GameplayRules.change(state.copy(currentBranchId = branchId), actor, command, referenceResolver())
        val mutation = change.mutation?.let { GameplayHistory.recontextualize(state, it) }
            ?: return GameplayTreeChange(reason = change.reason)
        if (mutation.id in nodes || mutation.id in removedParents) return GameplayTreeChange(reason = "duplicateNode")
        val next = copy(nodes = nodes + (mutation.id to mutation), currentNodeId = mutation.id,
            currentBranchId = branchId,
            branchHeads = branchHeads + (branchId to mutation.id),
            selectionControlId = if (forks) command.requestId else selectionControlId,
            branches = if (!forks) branches else branches + (branchId to GameplayBranch(branchId,
                target, mutation.id, command.requestId, actor, currentNodeId, currentBranchId,
                materializationId(currentNodeId))),
            redoByAccount = if (forks) emptyMap() else redoByAccount - actor)
        next.currentMaterialization = change.state
        return GameplayTreeChange(next, targetId = mutation.id)
    }

    /** Legacy selection helper, no longer exposed as a command. Review never activates shared state. */
    fun activate(nodeId: String): GameplayTreeChange = when {
        nodeId != initial.root.id && nodeId !in nodes -> GameplayTreeChange(reason = "missingReference")
        nodeId == currentNodeId -> GameplayTreeChange()
        else -> GameplayTreeChange(copy(currentNodeId = nodeId,
            currentBranchId = nodes[nodeId]?.branchId ?: initial.root.branchId, redoByAccount = emptyMap()), targetId = nodeId)
    }

    /** The linear replay suffix is not the whole reference domain: an inactive offshoot may
     * annotate the target or a re-materialized successor. Protect it without replaying branches or
     * widening the 50-event suffix. This scan checks references, never serialized board data. */
    internal fun selectiveReferenceFailure(candidate: GameplayTree): String? {
        val changed = (nodes.keys - candidate.nodes.keys + (candidate.nodes.keys - nodes.keys)).toMutableSet()
        candidate.nodes.forEach { (id, node) -> if (nodes[id]?.parentId != node.parentId) changed.add(id) }
        val children = (nodes.values + candidate.nodes.values).groupBy { it.parentId }
        val pending = ArrayDeque(changed.toList())
        while (pending.isNotEmpty()) for (child in children[pending.removeFirst()].orEmpty()) {
            if (changed.add(child.id)) pending.add(child.id)
        }
        for (node in candidate.nodes.values) {
            if (node.references.any { it.nodeId !in candidate.nodes } || node.transition.annotation?.reference?.reference?.nodeId in changed)
                return "referenceBlocked"
        }
        return null
    }

    internal fun path(nodeId: String): List<GameplayMutation>? {
        val reverse = mutableListOf<GameplayMutation>()
        val seen = mutableSetOf<String>()
        var cursor = nodeId
        while (cursor != initial.root.id) {
            if (!seen.add(cursor)) return null
            val node = nodes[cursor] ?: return null
            if (node.id != cursor) return null
            reverse.add(node); cursor = node.parentId
        }
        return reverse.asReversed()
    }

    internal fun materialize(nodeId: String): PlayState? {
        val path = path(nodeId) ?: return null
        var state = initial
        for (node in path) {
            if (!referencesMatch(node)) return null
            state = replay(state, node) ?: return null
        }
        return state
    }

    internal fun referencesMatch(node: GameplayMutation): Boolean {
        return node.references.all {
            val referenced = nodes[it.nodeId] ?: return@all false
            referenced.transition == it.transition
        }
    }

    internal fun replay(state: PlayState, node: GameplayMutation): PlayState? {
        replayedNodes++
        return GameplayHistory.replay(state, node, referenceResolver()).state
    }

    /** Opaque public topology identity, never a hash of hidden faces/order/board values. Mutations
     * are immutable except parent pointers; the complete ancestry identifies the materialization. */
    fun materializationId(nodeId: String): String? {
        val path = path(nodeId) ?: return null
        var identity = initial.root.id
        for (node in path) identity = CommandIds.uuidV5(identity, "retained/${node.id}")
        return identity
    }

    fun contextVersion(nodeId: String): String? = materializationId(nodeId)?.replace("-", "")
        ?.take(15)?.toLong(16)?.toString()

    private fun referenceResolver() = EventReferenceResolver { _, actor, reference ->
        if (actor !in initial.board.players.map { it.accountId } || reference.playId != initial.id) null else {
            val identity = materializationId(reference.nodeId)
            val version = identity?.replace("-", "")?.take(15)?.toLong(16)?.toString()
            if (identity == null || (reference.contextVersion != null && reference.contextVersion != version)) null else {
                // Targets require a real retained read, including inactive offshoots. Break malformed
                // cyclic annotation dependencies rather than recursively consuming the worker stack.
                val state = referenceState(reference.nodeId)
                state?.let { RetainedAnnotationContext.from(it, identity, reference.contextVersion) }
            }
        }
    }

    private fun referenceState(nodeId: String): PlayState? {
        reviewMaterialization?.takeIf { it.first == nodeId }?.let { return it.second }
        currentMaterialization?.takeIf { it.currentNodeId == nodeId }?.let { return it }
        // Annotation overlays never change entity existence. Reconstruct the actual target board
        // iteratively without following annotations' references (which may name later offshoots).
        // Full annotation tuples are still checked on the main replay path, not elided there.
        var state = initial
        for (node in path(nodeId) ?: return null) {
            state = if (node.transition.annotation != null) state.copy(currentNodeId = node.id,
                currentGroup = GameplayHistory.append(state.currentGroup, node, state.board.containers), currentBranchId = node.branchId)
            else GameplayHistory.replay(state, node).state ?: return null
        }
        return state
    }
}

data class GameplayTreeChange(val tree: GameplayTree? = null, val reason: String? = null,
    val targetId: String? = null) {
    val outcome: String get() = if (reason != null) "rejected" else if (tree == null) "noOp" else "accepted"
}
