package org.asyncttwiz.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonIgnoreUnknownKeys
import kotlin.concurrent.Volatile

/** The boundary is a stable parent plus the removed continuation, not a captured shared head. */
@Serializable data class GameplayRedoEntry(val mutation: GameplayMutation, val successorId: String?,
    val otherChildIds: Set<String>, val otherChildCount: Int? = null)

/** One immutable retained tree. Root data is immutable; materialized boards are derived, not per-node snapshots.
 * Queue order is oldest to newest (last entry is next). These queues are current account control state.
 * Relational adapters must commit the returned tree/delta with their inbox outcome atomically.
 */
// Stored/cached trees from older releases contain removed control metadata. It has no effect on play.
@JsonIgnoreUnknownKeys
@Serializable data class GameplayTree(val initial: PlayState,
    @Serializable(with = SharedMapSerializer::class) val nodes: Map<String, GameplayMutation> = emptyMap(), val currentNodeId: String = initial.root.id,
    @Serializable(with = RedoQueuesSerializer::class) val redoByAccount: Map<String, List<GameplayRedoEntry>> = emptyMap(),
    @Serializable(with = SharedMapSerializer::class) val removedParents: Map<String, String> = emptyMap(),
    @Serializable(with = SharedMapSerializer::class) val branches: Map<String, GameplayBranch> = emptyMap(),
    val currentBranchId: String = nodes[currentNodeId]?.branchId ?: initial.root.branchId,
    @Serializable(with = SharedMapSerializer::class) val branchHeads: Map<String, String> = mapOf(initial.root.branchId to initial.root.id),
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
    @Transient private var retainedIndex: RetainedIndex? = null
    @Transient internal val generationToken: Any = Any()
    @Transient internal var editBase: Any? = null
    @Transient internal var editKeys: Set<GameplayTreeKey> = emptySet()
    // Keys only: never retain old trees/boards. Presentation consumers can cross coalesced updates
    // and prediction/confirmation forks in O(delta), without comparing every retained mutation.
    private class Edits(val parent: Edits?, val keys: Set<GameplayTreeKey>) {
        val depth: Int = (parent?.depth ?: -1) + 1
        val origin: Any = parent?.origin ?: Any()
    }
    @Transient private var edits = Edits(null, emptySet())

    /** Null denotes an unrelated cold generation, not permission to diff on the UI thread. */
    fun changedNodeIdsSince(previous: GameplayTree): Set<String>? {
        if (nodes === previous.nodes) return emptySet()
        var a = edits; var b = previous.edits
        if (a.origin !== b.origin) return null
        val ids = mutableSetOf<String>()
        fun collect(edit: Edits) { for (key in edit.keys) if (key.kind == "node") ids.add(key.id) }
        while (a !== b) {
            if (a.depth >= b.depth) { collect(a); a = a.parent ?: return null }
            else { collect(b); b = b.parent ?: return null }
        }
        return ids
    }
    @Transient var work: GameplayWork = GameplayWork()
        private set

    internal fun index(): RetainedIndex = retainedIndex ?: RetainedIndex.build(this).also {
        work.fullHistoryScans++; work.indexedNodes += nodes.size; retainedIndex = it
    }

    /** Trusted persistence adapter only: the endpoint and indices must be committed atomically with
     * the accepted records. No replay/validation bypass is exposed on the client wire codec. */
    fun restoreEndpoint(state: PlayState, index: RetainedIndex): GameplayTree {
        require(state.id == initial.id && state.gameId == initial.gameId && state.currentNodeId == currentNodeId)
        require(state.engineVersion == initial.engineVersion && state.readFormat == initial.readFormat)
        require(index.positions[currentNodeId] != null)
        retainedIndex = index
        return checkpoint(state)
    }

    fun endpointIndex(): RetainedIndex = index()

    /** Reducers publish their write set, not a diff of the retained history. */
    internal fun changedFrom(previous: GameplayTree, keys: Set<GameplayTreeKey>): GameplayTree {
        editBase = previous.generationToken
        editKeys = keys
        edits = Edits(previous.edits, keys)
        work = previous.work
        val ids = keys.filter { it.kind == "node" }.map { it.id }.toSet()
        retainedIndex = if (nodes === previous.nodes) previous.index() else previous.index().edited(previous.nodes, nodes, ids)
        work.indexedNodes += ids.size
        return this
    }

    internal fun unapply(state: PlayState, node: GameplayMutation): PlayState? {
        work.unappliedNodes++
        return GameplayHistory.unapply(state, node)
    }

    /** A reducer's already-materialized boundary while it validates a sparse edited suffix. */
    internal fun nearby(state: PlayState) { reviewMaterialization = state.currentNodeId to state }

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
        it.retainedIndex = retainedIndex
        it.edits = edits
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

    /** Walk from the nearest known endpoint. LCA work is proportional to distance, not root depth. */
    fun stateAt(nodeId: String): PlayState? {
        if (nodeId == currentNodeId) currentMaterialization?.let { return it }
        reviewMaterialization?.takeIf { it.first == nodeId }?.let { return it.second }
        val candidates = listOfNotNull(currentMaterialization, reviewMaterialization?.second)
        for (source in candidates) {
            val node = nodes[source.currentNodeId]
            val adjacent = when {
                node?.parentId == nodeId -> unapply(source, node)
                nodes[nodeId]?.parentId == source.currentNodeId -> nodes[nodeId]?.takeIf(::referencesMatch)?.let { replay(source, it) }
                else -> null
            }
            if (adjacent != null) {
                val state = if (nodeId == currentNodeId) adjacent.copy(currentBranchId = currentBranchId) else adjacent
                if (nodeId == currentNodeId) currentMaterialization = state else reviewMaterialization = nodeId to state
                return state
            }
        }
        val nearest = nearest(candidates, nodeId)
        // A failed inverse/delta is a rejection, never an excuse to replay the entire root on
        // a normal path. Root reconstruction is reserved for a genuinely cold tree.
        val reconstructed = (if (candidates.isEmpty()) materialize(nodeId)
            else nearest?.let { traverse(it.first, it.second) }) ?: return null
        val state = if (nodeId == currentNodeId) reconstructed.copy(currentBranchId = currentBranchId) else reconstructed
        if (nodeId == currentNodeId) currentMaterialization = state else reviewMaterialization = nodeId to state
        return state
    }

    /** Race endpoint walks one edge at a time: even a distant cached endpoint cannot turn a
     * nearby review step/reference read into a walk to the root. */
    private fun nearest(candidates: List<PlayState>, target: String): Pair<PlayState, Pair<List<GameplayMutation>, List<GameplayMutation>>>? {
        val positions = index().positions
        val targetDepth = positions[target]?.depth ?: return null
        class Walk(val source: PlayState) {
            var left = source.currentNodeId; var right = target
            var a = positions[left]?.depth ?: -1; var b = targetDepth
            val up = mutableListOf<GameplayMutation>(); val down = mutableListOf<GameplayMutation>()
            fun step(): Boolean {
                if (left == right || a < 0) return false
                work.ancestrySteps++
                if (a >= b) { val node = nodes[left] ?: return false; up += node; left = node.parentId; a-- }
                else { val node = nodes[right] ?: return false; down += node; right = node.parentId; b-- }
                return true
            }
        }
        val walks = candidates.map(::Walk).filter { it.a >= 0 }
        while (walks.isNotEmpty()) {
            var progressed = false
            for (walk in walks) {
                if (walk.left == walk.right) return walk.source to (walk.up to walk.down.asReversed())
                progressed = walk.step() || progressed
            }
            if (!progressed) return null
        }
        return null
    }

    private fun traverse(source: PlayState, route: Pair<List<GameplayMutation>, List<GameplayMutation>>): PlayState? {
        val (up, down) = route
        var state = source
        for (node in up) state = unapply(state, node) ?: return null
        for (node in down) {
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
        if (next.initial === initial && next.nodes === nodes && next.currentNodeId == currentNodeId)
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
        if (!index().incremental) return GameplayTreeChange(reason = "historyUnavailable")
        val state = stateAt(target) ?: return GameplayTreeChange(reason = "historyUnavailable")
        // A retained future also exists beyond an explicitly activated internal current pointer.
        // Merely viewing/arming never reaches this code. Allocate metadata only after real success.
        val forks = target != currentNodeId || index().children[target].orEmpty().isNotEmpty()
        val branchId = if (forks) CommandIds.uuidV5(command.requestId, "history-branch") else currentBranchId
        val change = GameplayRules.change(state.copy(currentBranchId = branchId), actor, command, referenceResolver())
        val mutation = change.mutation?.let { GameplayHistory.recontextualize(state, it) }
            ?: return GameplayTreeChange(reason = change.reason)
        if (mutation.id in nodes || mutation.id in removedParents) return GameplayTreeChange(reason = "duplicateNode")
        val next = copy(nodes = nodes.putShared(mutation.id, mutation), currentNodeId = mutation.id,
            currentBranchId = branchId,
            branchHeads = branchHeads.putShared(branchId, mutation.id),
            selectionControlId = if (forks) command.requestId else selectionControlId,
            branches = if (!forks) branches else branches.putShared(branchId, GameplayBranch(branchId,
                target, mutation.id, command.requestId, actor, currentNodeId, currentBranchId,
                materializationId(currentNodeId))),
            redoByAccount = if (forks) emptyMap() else redoByAccount.removeShared(actor))
        next.currentMaterialization = change.state
        next.reviewMaterialization = reviewMaterialization
        val keys = mutableSetOf(GameplayTreeKey("node", mutation.id), GameplayTreeKey("current", "current"),
            GameplayTreeKey("selected-branch", "current"), GameplayTreeKey("branch-head", branchId))
        (if (forks) redoByAccount.keys else setOf(actor)).forEach { keys += GameplayTreeKey("redo", it) }
        if (forks) keys += setOf(GameplayTreeKey("branch", branchId), GameplayTreeKey("branch-lineage", branchId),
            GameplayTreeKey("selection-control", "current"))
        next.changedFrom(this, keys)
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
     * widening the 50-event suffix. Reverse indices avoid scanning unrelated retained references. */
    internal fun selectiveReferenceFailure(candidate: GameplayTree): String? {
        val edited = candidate.editKeys.filter { it.kind == "node" }.map { it.id }
        val changed = index().descendants(edited) + candidate.index().descendants(edited)
        for (id in changed) {
            // An unchanged transition does not invalidate event dependencies. An ancestry change
            // does invalidate annotation contexts; a nonempty reverse index is sufficient to block,
            // even if ten thousand inactive comments name that one event.
            if (index().positions[id]?.identity != candidate.index().positions[id]?.identity &&
                candidate.index().annotationReaders[id].orEmpty().isNotEmpty()) return "referenceBlocked"
            if (nodes[id]?.transition != candidate.nodes[id]?.transition) {
                for (reader in index().readers[id].orEmpty()) {
                    val node = candidate.nodes[reader] ?: continue
                    if (!candidate.referencesMatch(node)) return "referenceBlocked"
                }
            }
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
        work.rootMaterializations++
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
        work.replayedNodes++
        nearby(state)
        return GameplayHistory.replay(state, node, referenceResolver()).state
    }

    /** Opaque public topology identity, never a hash of hidden faces/order/board values. Mutations
     * are immutable except parent pointers; the complete ancestry identifies the materialization. */
    fun materializationId(nodeId: String): String? {
        return index().positions[nodeId]?.identity
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
        // Stored deltas are self-contained, including overlays. Do not recursively resolve annotation
        // dependencies while deriving a target's board; the enclosing replay validates its reference.
        val endpoints = listOfNotNull(currentMaterialization, reviewMaterialization?.second).ifEmpty { listOf(initial) }
        val (source, route) = nearest(endpoints, nodeId) ?: return null
        val (up, down) = route
        var state = source
        for (node in up) state = unapply(state, node) ?: return null
        for (node in down) { work.replayedNodes++; state = GameplayHistory.apply(state, node) ?: return null }
        return state
    }
}

data class GameplayTreeChange(val tree: GameplayTree? = null, val reason: String? = null,
    val targetId: String? = null) {
    val outcome: String get() = if (reason != null) "rejected" else if (tree == null) "noOp" else "accepted"
}
