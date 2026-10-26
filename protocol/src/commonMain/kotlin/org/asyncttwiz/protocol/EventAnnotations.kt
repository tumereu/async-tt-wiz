package org.asyncttwiz.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/** Stable predicted/accepted node UUIDs, scoped to their original play. No client-assigned group. */
@Serializable data class EventReference(val playId: String, val nodeId: String, val contextVersion: String? = null)
@Serializable enum class PingTargetKind { CARD, PLAYER, AREA, OCCURRENCE, LOCATION }
@Serializable data class PingTarget(val kind: PingTargetKind, val id: String? = null, val position: BoardPosition? = null)
@Serializable data class ReferencedEventContext(val reference: EventReference, val materializationId: String)
@Serializable data class AnnotationRecording(val playId: String, val branchId: String, val parentId: String,
    val nodeId: String, val groupId: String, val requestId: String, val author: String)
@Serializable data class RecordedEventComment(val recording: AnnotationRecording, val reference: ReferencedEventContext, val text: String)
@Serializable data class PingPresence(val target: PingTarget, val ordinal: String, val additionNodeId: String)
@Serializable data class RecordedPingChange(val recording: AnnotationRecording, val reference: ReferencedEventContext,
    val target: PingTarget, val from: PingPresence?, val to: PingPresence?)
/** Relevant-state tuple: no unrelated current-board fields, incidental parent pointer or whole annotation-list lock. */
@Serializable data class AnnotationTransition(val playId: String, val branchId: String, val groupId: String,
    val reference: ReferencedEventContext, val target: PingTarget? = null, val from: PingPresence? = null,
    val to: PingPresence? = null, val comment: String? = null)
@Serializable data class EventAnnotationState(@Serializable(with = SharedMapSerializer::class) val commentsById: Map<String, RecordedEventComment>,
    @Serializable(with = SharedMapSerializer::class) val pingsById: Map<String, RecordedPingChange>,
    @Serializable(with = SharedMapSerializer::class) val lastOrdinals: Map<String, Long> = emptyMap()) {
    constructor(comments: List<RecordedEventComment> = emptyList(), pings: List<RecordedPingChange> = emptyList()) :
        this(comments.associateBy { it.recording.nodeId }, pings.associateBy { it.recording.nodeId },
            pings.groupBy { it.recording.groupId }.mapValues { (_, records) -> records.maxOf { it.to?.ordinal?.toLong() ?: 0L } })
    val comments: List<RecordedEventComment> get() = commentsById.values.toList()
    val pings: List<RecordedPingChange> get() = pingsById.values.toList()
    fun copy(comments: List<RecordedEventComment> = this.comments, pings: List<RecordedPingChange> = this.pings) = EventAnnotationState(comments, pings)

    @Transient private var activeIndex: Map<String, Map<Pair<ReferencedEventContext, PingTarget>, RecordedPingChange>>? = null
    /** Transaction-scoped indexed presentation state, separate from historical records. */
    fun restoreActive(index: Map<String, Map<Pair<ReferencedEventContext, PingTarget>, RecordedPingChange>>): EventAnnotationState =
        also { activeIndex = index }
    private fun active(): Map<String, Map<Pair<ReferencedEventContext, PingTarget>, RecordedPingChange>> = activeIndex ?: run {
        // A materialized projection's maps have no ancestry/insertion order. Pick the newest
        // ordinal per target; an Unping wins the tie with the addition it removes.
        val latest = mutableMapOf<Pair<String, Pair<ReferencedEventContext, PingTarget>>, RecordedPingChange>()
        for (record in pingsById.values) {
            val key = record.recording.groupId to (record.reference to record.target)
            val old = latest[key]
            val ordinal = (record.to ?: record.from)?.ordinal?.toLong() ?: 0L
            val previous = (old?.to ?: old?.from)?.ordinal?.toLong() ?: -1L
            if (ordinal > previous || (ordinal == previous && record.to == null)) latest[key] = record
        }
        var groups: Map<String, Map<Pair<ReferencedEventContext, PingTarget>, RecordedPingChange>> = emptyMap()
        for (record in latest.values) {
            val group = record.recording.groupId; val key = record.reference to record.target
            val members = groups[group].orEmpty().let { if (record.to == null) it.removeShared(key) else it.putShared(key, record) }
            groups = groups.putShared(group, members)
        }
        groups.also { activeIndex = it }
    }
    internal fun active(group: String) = active()[group].orEmpty()
    fun activePing(group: String, reference: ReferencedEventContext, target: PingTarget): RecordedPingChange? = active(group)[reference to target]
    internal fun comment(id: String, next: RecordedEventComment?): EventAnnotationState =
        copy(commentsById = if (next == null) commentsById.removeShared(id) else commentsById.putShared(id, next))
            .also { it.activeIndex = activeIndex }

    internal fun ping(id: String, next: RecordedPingChange?): EventAnnotationState {
        val old = pingsById[id]
        val record = next ?: old ?: return this
        val group = record.recording.groupId; val key = record.reference to record.target
        val presence = if (next == null) old?.from else next.to
        val restored = presence?.let { if (next != null) next else pingsById[it.additionNodeId] }
        val members = active(group).let { if (restored == null) it.removeShared(key) else it.putShared(key, restored) }
        return copy(pingsById = if (next == null) pingsById.removeShared(id) else pingsById.putShared(id, next))
            .also { it.activeIndex = active().putShared(group, members) }
    }
    internal fun ordinal(group: String, value: Long?): EventAnnotationState = copy(lastOrdinals =
        if (value == null) lastOrdinals.removeShared(group) else lastOrdinals.putShared(group, value)).also { it.activeIndex = activeIndex }
}

/** A trusted retained-tree read, after account access checking. Never accept a context/version supplied
 * by a request as evidence. Inactive retained nodes are resolved here without activating them or exposing faces. */
data class RetainedAnnotationContext(val context: ReferencedEventContext, val targets: Set<PingTarget>) {
    companion object {
        /** materializationId is a retained read's opaque identity, changed on tree re-materialization.
         * It is NOT a content hash: hashing covered faces/library order could create a disclosure oracle. */
        fun from(state: PlayState, materializationId: String, contextVersion: String? = null): RetainedAnnotationContext = RetainedAnnotationContext(
            ReferencedEventContext(EventReference(state.id, state.currentNodeId, contextVersion), materializationId),
            (state.board.cards.map { PingTarget(PingTargetKind.CARD, it.id) } +
                state.board.players.map { PingTarget(PingTargetKind.PLAYER, it.accountId) } +
                 state.board.containers.map { PingTarget(PingTargetKind.AREA, it.id) } +
                 listOfNotNull(state.board.combat?.let { PingTarget(PingTargetKind.AREA, it.id) }) +
                 CombatStackRules.occurrences(state.board.combat).map { PingTarget(PingTargetKind.OCCURRENCE, it.id) } +
                 state.board.stackEffects.map { PingTarget(PingTargetKind.OCCURRENCE, it.id) }).toSet())
    }
}
fun interface EventReferenceResolver {
    fun resolve(state: PlayState, actor: String, reference: EventReference): RetainedAnnotationContext?
    fun context(state: PlayState, actor: String, reference: EventReference): ReferencedEventContext? = resolve(state, actor, reference)?.context
    fun targetPresent(state: PlayState, actor: String, reference: EventReference, target: PingTarget): Boolean =
        target.kind == PingTargetKind.LOCATION || resolve(state, actor, reference)?.targets?.contains(target) == true
    companion object {
        /** Fail closed until a trusted retained-tree read is supplied, including for current-node references.
         * A node UUID alone cannot identify its re-materialized state after selective Undo/Redo. */
        val UNAVAILABLE = EventReferenceResolver { _, _, _ -> null }
    }
}

object EventAnnotations {
    fun valid(reference: EventReference) = DeckValidation.uuid(reference.playId) && DeckValidation.uuid(reference.nodeId) &&
        (reference.contextVersion == null || runCatching { GameWire.ordinal(reference.contextVersion); true }.getOrDefault(false))
    fun valid(target: PingTarget) = if (target.kind == PingTargetKind.LOCATION) target.id == null && target.position != null
        else target.position == null && target.id?.let(DeckValidation::uuid) == true

    fun activePings(annotations: EventAnnotationState, groupId: String): List<RecordedPingChange> {
        return annotations.active(groupId).values.sortedBy { it.to!!.ordinal.toLong() }
    }

    /** Called only through GameplayRules, so validation/scope/access remain the common request boundary. */
    internal fun change(state: PlayState, actor: String, command: GameCommand, references: EventReferenceResolver): GameplayChange {
        val action = (command.action as GameAction.Play).action
        val reference = when (action) {
            is PlayAction.EventComment -> action.reference
            is PlayAction.Ping -> action.reference
            is PlayAction.Unping -> action.reference
            else -> error("Not an event annotation")
        }
        // The server's durable request outcome path remains the delivery authority; this also prevents
        // duplicate records when the shared reducer is presented an already-applied request.
        val nodeId = CommandIds.uuidV5(command.requestId, "board-node")
        if (nodeId in state.annotations.commentsById || nodeId in state.annotations.pingsById) return GameplayChange(state)
        if (reference.playId != state.id) return GameplayChange(reason = "missingReference")
        val retained = (if (action is PlayAction.EventComment) references.context(state, actor, reference)?.let {
            RetainedAnnotationContext(it, emptySet())
        } else references.resolve(state, actor, reference))
            ?.takeIf { it.context.reference == reference } ?: return GameplayChange(reason = "missingReference")
        val target = when (action) { is PlayAction.Ping -> action.target; is PlayAction.Unping -> action.target; else -> null }
        if (target != null && target.kind != PingTargetKind.LOCATION && target !in retained.targets)
            return GameplayChange(reason = "missingReference")
        val semantic = HistoryGrouping.classify(action)
        val group = HistoryGrouping.append(state.currentGroup, nodeId, command.requestId, actor, semantic)
        val recording = AnnotationRecording(state.id, state.currentBranchId, state.currentNodeId, nodeId, group.id, command.requestId, actor)
        val from = if (target == null) null else state.annotations.active(group.id)[retained.context to target]?.to
        val to: PingPresence?
        val annotations: EventAnnotationState
        if (action is PlayAction.EventComment) {
            to = null
            annotations = state.annotations.comment(nodeId, RecordedEventComment(recording, retained.context, action.text))
        } else {
            if ((action is PlayAction.Unping && from == null) || (action is PlayAction.Ping && from != null)) return GameplayChange(state)
            val lastOrdinal = state.annotations.lastOrdinals[group.id] ?: 0L
            if (action is PlayAction.Ping && lastOrdinal == Long.MAX_VALUE) return GameplayChange(reason = "inapplicable")
            to = if (action is PlayAction.Unping) null else PingPresence(requireNotNull(target), (lastOrdinal + 1).toString(), nodeId)
            annotations = state.annotations.ping(nodeId, RecordedPingChange(recording, retained.context, requireNotNull(target), from, to))
                .let { if (to == null) it else it.ordinal(group.id, lastOrdinal + 1) }
        }
        val transition = GameplayTransition(annotation = AnnotationTransition(state.id, state.currentBranchId, group.id,
            retained.context, target, from, to, (action as? PlayAction.EventComment)?.text))
        val mutation = GameplayMutation(nodeId, state.currentNodeId, state.currentBranchId, command.requestId,
            actor, command.invocationId, action, transition, grouping = semantic)
        return GameplayChange(state.copy(currentNodeId = nodeId, currentGroup = group, annotations = annotations), mutation)
    }
}
