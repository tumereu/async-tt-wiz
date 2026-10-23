package org.asyncttwiz.protocol

import kotlinx.serialization.Serializable

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
@Serializable data class EventAnnotationState(val comments: List<RecordedEventComment> = emptyList(),
    val pings: List<RecordedPingChange> = emptyList())

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
        val active = linkedMapOf<Pair<ReferencedEventContext, PingTarget>, RecordedPingChange>()
        for (record in annotations.pings) if (record.recording.groupId == groupId) {
            val key = record.reference to record.target
            if (record.to == null) active.remove(key) else active[key] = record
        }
        return active.values.sortedBy { it.to!!.ordinal.toLong() }
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
        if (state.annotations.comments.any { it.recording.requestId == command.requestId } ||
            state.annotations.pings.any { it.recording.requestId == command.requestId }) return GameplayChange(state)
        if (reference.playId != state.id) return GameplayChange(reason = "missingReference")
        val retained = references.resolve(state, actor, reference)
            ?.takeIf { it.context.reference == reference } ?: return GameplayChange(reason = "missingReference")
        val target = when (action) { is PlayAction.Ping -> action.target; is PlayAction.Unping -> action.target; else -> null }
        if (target != null && target.kind != PingTargetKind.LOCATION && target !in retained.targets)
            return GameplayChange(reason = "missingReference")
        val nodeId = CommandIds.uuidV5(command.requestId, "board-node")
        val semantic = HistoryGrouping.classify(action)
        val group = HistoryGrouping.append(state.currentGroup, nodeId, command.requestId, actor, semantic)
        val recording = AnnotationRecording(state.id, state.currentBranchId, state.currentNodeId, nodeId, group.id, command.requestId, actor)
        val from = if (target == null) null else activePings(state.annotations, group.id)
            .singleOrNull { it.reference == retained.context && it.target == target }?.to
        val to: PingPresence?
        val annotations: EventAnnotationState
        if (action is PlayAction.EventComment) {
            to = null
            annotations = state.annotations.copy(comments = state.annotations.comments + RecordedEventComment(recording, retained.context, action.text))
        } else {
            if ((action is PlayAction.Unping && from == null) || (action is PlayAction.Ping && from != null)) return GameplayChange(state)
            val lastOrdinal = state.annotations.pings.filter { it.recording.groupId == group.id }
                .mapNotNull { it.to?.ordinal?.toLong() }.maxOrNull() ?: 0L
            if (action is PlayAction.Ping && lastOrdinal == Long.MAX_VALUE) return GameplayChange(reason = "inapplicable")
            to = if (action is PlayAction.Unping) null else PingPresence(requireNotNull(target), (lastOrdinal + 1).toString(), nodeId)
            annotations = state.annotations.copy(pings = state.annotations.pings + RecordedPingChange(recording, retained.context, requireNotNull(target), from, to))
        }
        val transition = GameplayTransition(annotation = AnnotationTransition(state.id, state.currentBranchId, group.id,
            retained.context, target, from, to, (action as? PlayAction.EventComment)?.text))
        val mutation = GameplayMutation(nodeId, state.currentNodeId, state.currentBranchId, command.requestId,
            actor, command.invocationId, action, transition, grouping = semantic)
        return GameplayChange(state.copy(currentNodeId = nodeId, currentGroup = group, annotations = annotations), mutation)
    }
}
