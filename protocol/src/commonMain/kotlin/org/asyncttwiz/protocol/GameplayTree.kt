package org.asyncttwiz.protocol

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.Transient
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.AbstractEncoder
import kotlinx.serialization.encoding.CompositeEncoder
import kotlinx.serialization.modules.EmptySerializersModule
import kotlin.concurrent.Volatile

/** The boundary is a stable parent plus the removed continuation, not a captured shared head. */
@Serializable data class GameplayRedoEntry(val mutation: GameplayMutation, val successorId: String?,
    val otherChildIds: Set<String>)

/** One immutable retained tree. Root data is immutable; materialized boards are derived, not per-node snapshots.
 * Queue order is oldest to newest (last entry is next). These queues are current account control state.
 * Relational adapters must commit the returned tree/delta with their inbox outcome atomically.
 */
@Serializable data class GameplayTree(val initial: PlayState,
    val nodes: Map<String, GameplayMutation> = emptyMap(), val currentNodeId: String = initial.root.id,
    val redoByAccount: Map<String, List<GameplayRedoEntry>> = emptyMap(),
    val removedParents: Map<String, String> = emptyMap(),
    val branches: Map<String, GameplayBranch> = emptyMap(),
    val currentBranchId: String = nodes[currentNodeId]?.branchId ?: initial.root.branchId,
    val branchArchives: BranchArchiveState = BranchArchiveState(),
    val branchHeads: Map<String, String> = mapOf(initial.root.branchId to initial.root.id),
    val selectionControlId: String? = null) {
    init { require(initial.currentNodeId == initial.root.id) }

    // Disposable current-only checkpoint, populated solely by replay or the shared reducer below.
    // It belongs to this exact immutable tree instance: copy/decoding (including same-ID topology,
    // root or scope changes) starts cold. It is neither wire state nor caller-supplied authority.
    @Transient private var currentMaterialization: PlayState? =
        if (currentNodeId == initial.root.id) initial.copy(currentBranchId = currentBranchId) else null
    // One disposable worker-prepared review endpoint, not a per-node snapshot map or accepted data.
    // Tree edits/copy/decoding discard it. Selective work still reconstructs/charges its own tuples.
    @Transient @Volatile private var reviewMaterialization: Pair<String, PlayState>? = null

    /** Prepare the exact personal cursor on a worker before enabling historical mutation callbacks.
     * The UI cannot inject a board; only this tree's shared replay can populate the disposable slot. */
    fun prepareReview(nodeId: String): PlayState? = stateAt(nodeId)?.also { reviewMaterialization = nodeId to it }

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

    /** Ordinary retained review has no selective-Undo eligibility ceiling. Cold reads replay the
     * complete ancestry and references; callers must keep cold reconstruction off latency-sensitive UI work.
     */
    fun stateAt(nodeId: String): PlayState? {
        if (nodeId == currentNodeId) currentMaterialization?.let { return it }
        reviewMaterialization?.takeIf { it.first == nodeId }?.let { return it.second }
        val reconstructed = materialize(nodeId) ?: return null
        val state = if (nodeId == currentNodeId) reconstructed.copy(currentBranchId = currentBranchId) else reconstructed
        if (nodeId == currentNodeId) currentMaterialization = state
        return state
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
        val mutation = change.mutation ?: return GameplayTreeChange(reason = change.reason)
        if (mutation.id in nodes || mutation.id in removedParents) return GameplayTreeChange(reason = "duplicateNode")
        val next = copy(initial = initial.copy(definitions = change.state!!.definitions),
            nodes = nodes + (mutation.id to mutation), currentNodeId = mutation.id,
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

    /** I24/I25 seam: call only after a successful deliberate shared selection change, not on retry.
     * Same-current activation is a no-op. Mere archive metadata/review must not call this method.
     */
    fun activate(nodeId: String): GameplayTreeChange = when {
        nodeId != initial.root.id && nodeId !in nodes -> GameplayTreeChange(reason = "missingReference")
        nodeId == currentNodeId -> GameplayTreeChange()
        (nodes[nodeId]?.branchId ?: initial.root.branchId) in branchArchives.archivedBranchIds ->
            GameplayTreeChange(reason = "restoreReturn")
        else -> GameplayTreeChange(copy(currentNodeId = nodeId,
            currentBranchId = nodes[nodeId]?.branchId ?: initial.root.branchId, redoByAccount = emptyMap()), targetId = nodeId)
    }

    internal fun checkMetadata(work: HistoryWork) {
        work.metadata(nodes.size.toLong() + removedParents.size + redoByAccount.size + branchHeads.size + branches.size +
            branchArchives.archivedBranchIds.size + branchArchives.lastControlByBranch.size + branchArchives.records.size)
        work.value(BranchArchiveState.serializer(), branchArchives)
        branches.values.forEach { work.value(GameplayBranch.serializer(), it) }
        for (queue in redoByAccount.values) {
            work.metadata(queue.size.toLong())
            for (entry in queue) {
                work.metadata(entry.otherChildIds.size.toLong())
                work.value(GameplayRedoEntry.serializer(), entry)
            }
        }
    }

    /** The linear replay suffix is not the whole reference domain: an inactive offshoot may
     * annotate the target or a re-materialized successor. Protect it without replaying branches or
     * widening N. The engine already bounded retained topology; this additional index scan is
     * bounded by the SAME metadata-entry ceiling, including every explicit reference edge. */
    internal fun selectiveReferenceFailure(candidate: GameplayTree): String? {
        var entries = nodes.size.toLong() + removedParents.size + branchHeads.size + branches.size
        for (node in nodes.values) {
            entries += node.references.size + if (node.transition.annotation == null) 0 else 1
            if (entries > SelectiveUndoLimits().metadataEntries) return "workLimit"
        }
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

    internal fun path(nodeId: String, work: HistoryWork? = null): List<GameplayMutation>? {
        val reverse = mutableListOf<GameplayMutation>()
        val seen = mutableSetOf<String>()
        var cursor = nodeId
        while (cursor != initial.root.id) {
            work?.loadNode()
            if (!seen.add(cursor)) return null
            val node = nodes[cursor] ?: return null
            if (node.id != cursor) return null
            reverse.add(node); cursor = node.parentId
        }
        return reverse.asReversed()
    }

    // SelectiveUndo always supplies its work meter and never consumes the ordinary-play checkpoint.
    internal fun materialize(nodeId: String, work: HistoryWork? = null): PlayState? {
        val path = path(nodeId, work) ?: return null
        var state = initial
        work?.value(PlayState.serializer(), state)
        for (node in path) {
            if (!referencesMatch(node, work)) return null
            state = replay(state, node, work) ?: return null
        }
        return state
    }

    internal fun referencesMatch(node: GameplayMutation, work: HistoryWork? = null): Boolean {
        work?.value(GameplayMutation.serializer(), node)
        return node.references.all {
            val referenced = nodes[it.nodeId] ?: return@all false
            work?.value(GameplayTransition.serializer(), referenced.transition)
            referenced.transition == it.transition
        }
    }

    internal fun replay(state: PlayState, node: GameplayMutation, work: HistoryWork? = null): PlayState? {
        work?.value(GameplayMutation.serializer(), node)
        // Loading and reducer reconstruction both traverse board data. Charge each invocation, not only N.
        work?.value(PlayState.serializer(), state)
        work?.effects(node.transition.effectUnits())
        work?.metadata(GameplayHistory.projectionLinkReferences(node.transition).size.toLong())
        return GameplayHistory.replay(state, node, referenceResolver(work)).state
    }

    /** Opaque public topology identity, never a hash of hidden faces/order/board values. Mutations
     * are immutable except parent pointers; the complete ancestry identifies the materialization. */
    fun materializationId(nodeId: String): String? = materializationId(nodeId, null)
    internal fun materializationId(nodeId: String, work: HistoryWork?): String? {
        val path = path(nodeId, work) ?: return null
        var identity = initial.root.id
        for (node in path) identity = CommandIds.uuidV5(identity, "retained/${node.id}")
        return identity
    }

    fun contextVersion(nodeId: String): String? = materializationId(nodeId)?.replace("-", "")
        ?.take(15)?.toLong(16)?.toString()

    private fun referenceResolver(work: HistoryWork? = null) = EventReferenceResolver { _, actor, reference ->
        if (actor !in initial.board.players.map { it.accountId } || reference.playId != initial.id) null else {
            val identity = materializationId(reference.nodeId, work)
            val version = identity?.replace("-", "")?.take(15)?.toLong(16)?.toString()
            if (identity == null || (reference.contextVersion != null && reference.contextVersion != version)) null else {
                // Targets require a real retained read, including inactive offshoots. Break malformed
                // cyclic annotation dependencies rather than recursively consuming the worker stack.
                val state = referenceState(reference.nodeId, work)
                state?.let { RetainedAnnotationContext.from(it, identity, reference.contextVersion) }
            }
        }
    }

    private fun referenceState(nodeId: String, work: HistoryWork?): PlayState? {
        if (work == null) reviewMaterialization?.takeIf { it.first == nodeId }?.let { return it.second }
        // Annotation overlays never change entity existence. Reconstruct the actual target board
        // iteratively without following annotations' references (which may name later offshoots).
        // Full annotation tuples are still checked on the main replay path, not elided there.
        var state = initial
        for (node in path(nodeId, work) ?: return null) {
            work?.value(GameplayMutation.serializer(), node)
            work?.value(PlayState.serializer(), state)
            work?.effects(node.transition.effectUnits())
            work?.metadata(GameplayHistory.projectionLinkReferences(node.transition).size.toLong())
            state = if (node.transition.annotation != null) state.copy(currentNodeId = node.id,
                currentGroup = GameplayHistory.append(state.currentGroup, node), currentBranchId = node.branchId)
            else GameplayHistory.replay(state, node).state ?: return null
        }
        return state
    }

    /** Catalog uses explicitly retained heads, never branchId filtering of a subtree. Internal
     * shared-prefix nodes are valid selections on each continuation that actually contains them. */
    fun archiveCatalog(): BranchArchiveCatalog = object : BranchArchiveCatalog {
        override val selectionControlId get() = this@GameplayTree.selectionControlId
        override val selected get() = requireNotNull(resolve(currentBranchId, currentNodeId))
        override fun resolve(branchId: String, nodeId: String): BranchArchiveTarget? {
            val head = branchHeads[branchId] ?: return null
            if (nodeId != initial.root.id && path(head)?.none { it.id == nodeId } != false) return null
            val version = materializationId(nodeId) ?: return null
            return BranchArchiveTarget(branchId, nodeId, version)
        }
        override fun head(branchId: String) = branchHeads[branchId]?.let { resolve(branchId, it) }
        override fun displacedReturn(branchId: String): BranchArchiveTarget? = branches[branchId]?.let {
            resolve(it.displacedBranchId, it.displacedNodeId)?.takeIf { target ->
                it.displacedVersion == null || target.version == it.displacedVersion }
        }
    }
}

data class GameplayTreeChange(val tree: GameplayTree? = null, val reason: String? = null,
    val targetId: String? = null) {
    val outcome: String get() = if (reason != null) "rejected" else if (tree == null) "noOp" else "accepted"
}

/** Selective Undo/Redo eligibility work limits ONLY, not ordinary play/read or retention limits.
 * Tests may lower them.
 * N counts individual successor records, not invocations/groups. No unchecked tail is ever accepted.
 */
data class SelectiveUndoLimits(val suffixNodes: Int = 128, val loadNodes: Int = 2048,
    val payloadBytes: Long = 2L * 1024 * 1024, val effectUnits: Long = 32768,
    val workUnits: Long = 250000, val metadataEntries: Long = 16384) {
    init {
        require(suffixNodes in 0..128 && loadNodes in 0..2048 && payloadBytes in 0..2L * 1024 * 1024 &&
            effectUnits in 0..32768 && workUnits in 0..250000 && metadataEntries in 0..16384)
    }
}

private class HistoryLimit : RuntimeException()
internal fun <T> boundedHistory(limits: SelectiveUndoLimits, block: (HistoryWork) -> T): T? =
    try { block(HistoryWork(limits)) } catch (_: HistoryLimit) { null }

/** Streaming serialization visitor: counts without allocating encoded payloads or full snapshot copies.
 * Payload size bounds each value, while work units bound ALL visits across reconstruction/replay.
 * Repeatedly reading the same board is CPU work, not a growing retained/request payload: accumulating
 * its bytes made even a latest-head tap exceed 2 MiB after drawing seven from ordinary 60-card decks.
 * String UTF-8 cost is conservatively <= three bytes per UTF-16 unit plus quoting/escaping allowance.
 * Element visits bound nested collections/mandatory payloads even with empty strings/zero suffix.
 * Replay structurally shares unchanged immutable cards/definitions. Memoize their measured size
 * within this ONE check, so measuring an ordinary deck at every ancestor does not itself exhaust
 * the CPU budget. Hits still charge a visit and their FULL bytes in the enclosing snapshot. Replaced
 * objects (even with the same ID/digest) are measured again. No replay or tuple check is cached.
 */
@OptIn(ExperimentalSerializationApi::class)
internal class HistoryWork(private val limits: SelectiveUndoLimits) : AbstractEncoder() {
    override val serializersModule = EmptySerializersModule()
    private var bytes = 0L
    private var units = 0L
    private var effects = 0L
    private var metadata = 0L
    private var loads = 0
    private class Measured(val value: Any, val bytes: Long)
    private val cards = mutableMapOf<String, Measured>()
    private val definitions = mutableMapOf<String, Measured>()
    fun loadNode() { if (++loads > limits.loadNodes) throw HistoryLimit() }
    fun metadata(count: Long) { metadata += count; if (metadata > limits.metadataEntries) throw HistoryLimit() }
    fun effects(count: Long) { effects += count; if (effects > limits.effectUnits) throw HistoryLimit() }
    fun <T> value(serializer: SerializationStrategy<T>, value: T) {
        bytes = 0
        encodeSerializableValue(serializer, value)
    }
    override fun <T> encodeSerializableValue(serializer: SerializationStrategy<T>, value: T) {
        val (cache, key) = when {
            value is PlayCard && serializer === PlayCard.serializer() -> cards to value.id
            value is SubmittedDefinition && serializer === SubmittedDefinition.serializer() -> definitions to value.digest
            else -> { super.encodeSerializableValue(serializer, value); return }
        }
        val measured = cache[key]
        if (measured != null && measured.value === value) {
            charge(measured.bytes)
            return
        }
        if (measured == null) metadata(1) // Bound disposable meter storage as well as its traversal.
        val start = bytes
        super.encodeSerializableValue(serializer, value)
        cache[key] = Measured(value as Any, bytes - start)
    }
    private fun charge(count: Long = 16) {
        bytes += count; units++
        if (bytes > limits.payloadBytes || units > limits.workUnits) throw HistoryLimit()
    }
    override fun beginStructure(descriptor: SerialDescriptor): CompositeEncoder { charge(); return this }
    override fun encodeElement(descriptor: SerialDescriptor, index: Int): Boolean { charge(); return true }
    override fun encodeValue(value: Any) { charge() }
    override fun encodeString(value: String) { charge(6L * value.length + 2) }
    override fun encodeEnum(enumDescriptor: SerialDescriptor, index: Int) { charge() }
    override fun encodeNull() { charge() }
}
