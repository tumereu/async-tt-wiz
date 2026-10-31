package org.asyncttwiz.android

import org.asyncttwiz.protocol.*

/** Captured original identity, never a mutable selection or enqueue receipt. */
data class InspectionRequest(val requestId: String, val scope: GameScope, val accountId: String,
    val lifetimeId: String, val action: PlayAction.InspectFace)

sealed interface InspectionEvent {
    val request: InspectionRequest
    /** Both accepted tree proof and the still-applicable exact card/face/region are present. */
    data class Accepted(override val request: InspectionRequest, val disclosure: GameplayDisclosure,
        val mutation: GameplayMutation, val throughRevision: String, val state: PlayState) : InspectionEvent
    /** Original final no-op/rejection, or a definitive non-retryable admission refusal. */
    data class NotAccepted(override val request: InspectionRequest, val result: GameResult? = null,
        val admissionError: GameAdmissionError? = null) : InspectionEvent
}

/** Closing cancels presentation only. It neither withdraws/retries an original nor erases exposure. */
class InspectionSubscription internal constructor(val request: InspectionRequest) : AutoCloseable {
    @Volatile internal var active = true
        private set
    override fun close() { active = false }
}

/** GameSession's open logged inspections. Each captured original gets one outcome for its UI
 * lifetime: Accepted once the accepted tree proves the exact disclosure, otherwise NotAccepted. */
internal class InspectionObservers(private val scope: SessionScope, private val replicaOf: (String) -> GameplayReplica?,
    private val observedPlay: (String) -> String?) {
    private data class InspectionObserver(val subscription: InspectionSubscription, val current: () -> Boolean,
        val observed: (InspectionEvent) -> Unit)
    private val inspections = linkedMapOf<String, InspectionObserver>()

    fun observe(command: GameCommand, actor: String, lifetimeId: String, action: PlayAction.InspectFace,
        isCurrent: () -> Boolean, observed: (InspectionEvent) -> Unit): InspectionSubscription {
        inspections.entries.removeAll { !it.value.subscription.active }
        val bound = InspectionSubscription(InspectionRequest(command.requestId, command.scope, actor, lifetimeId, action))
        inspections[command.requestId] = InspectionObserver(bound, isCurrent, observed)
        return bound
    }

    /** The original was finally refused or not admitted. */
    fun notAccepted(requestId: String, event: (InspectionRequest) -> InspectionEvent) {
        inspections.remove(requestId)?.let { observer -> presentInspection(observer, event(observer.subscription.request)) }
    }

    private fun currentInspection(observer: InspectionObserver): Boolean {
        val request = observer.subscription.request
        return observer.subscription.active && !scope.closed && scope.sameAccount() &&
            (scope.account == null || scope.account == request.accountId) &&
            observedPlay(request.scope.gameId) == request.scope.playId && runCatching(observer.current).getOrDefault(false)
    }

    private fun presentInspection(observer: InspectionObserver, event: InspectionEvent) {
        if (!currentInspection(observer)) { observer.subscription.close(); return }
        observer.subscription.close() // consume before callback: duplicate state/results cannot reopen it.
        observer.observed(event)
    }

    fun notifyInspections() {
        for ((id, observer) in inspections.toMap()) {
            if (!currentInspection(observer)) { inspections.remove(id); observer.subscription.close(); continue }
            val request = observer.subscription.request
            val replica = replicaOf(request.scope.gameId) ?: continue
            val state = replica.accepted ?: continue
            val mutation = replica.tree?.tree?.nodes?.get(CommandIds.uuidV5(id, "board-node")) ?: continue
            if (mutation.requestId != id || mutation.author != request.accountId || mutation.action != request.action) continue
            val action = request.action
            val disclosure = mutation.transition.disclosures.singleOrNull { it.cardId == action.cardId &&
                it.faceId == action.faceId && it.containerId == action.containerId && request.accountId in it.viewers } ?: continue
            // Original acceptance never retargets a moved/deleted card, another face/region, or a
            // closed/replaced UI lifetime. Losing presentation applicability does not erase history.
            val card = state.board.cards.singleOrNull { it.id == action.cardId && it.containerId == action.containerId }
            inspections.remove(id)
            if (card == null || state.id != request.scope.playId || state.definitions.none { definition ->
                    definition.digest == card.definition && definition.definition.faces.any { it.faceId == action.faceId } }) {
                observer.subscription.close(); continue
            }
            presentInspection(observer, InspectionEvent.Accepted(request, disclosure, mutation, replica.revision!!, state))
        }
    }

    fun remove(gameId: String) {
        inspections.entries.removeAll { (_, observer) ->
            (observer.subscription.request.scope.gameId == gameId).also { if (it) observer.subscription.close() }
        }
    }
    fun clear() { inspections.values.forEach { it.subscription.close() }; inspections.clear() }
}
