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
