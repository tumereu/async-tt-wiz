package org.asyncttwiz.protocol

import kotlinx.serialization.Serializable

/** Pod HTTP v1. DraftState is deliberately not part of any response. */
@Serializable enum class PodPhase { SETUP, DRAFTING, DRAFTED }
@Serializable enum class PodAttention { YOUR_PICK, BUILD, NONE }
@Serializable data class PodSummary(
    val id: String, val name: String, val phase: PodPhase, val creator: AccountIdentity,
    val members: List<AccountIdentity>, val attention: PodAttention, val packsWaiting: Int,
    val updatedAt: String,
)
@Serializable data class PodInvitation(val podId: String, val name: String, val creator: AccountIdentity, val invitedAt: String)
@Serializable data class PodList(val pods: List<PodSummary>, val invitations: List<PodInvitation>)
@Serializable data class PodSource(val cubeName: String, val totalCopies: Long)
@Serializable data class PodMember(
    val account: AccountIdentity, val packsWaiting: Int = 0, val picksMade: Int = 0,
    val building: Boolean = false, val submitted: Boolean = false,
)
@Serializable data class PodDetail(
    val id: String, val name: String, val phase: PodPhase, val creator: AccountIdentity,
    val settings: DraftSettings, val source: PodSource, val supply: DraftSupply,
    val members: List<PodMember>, val pendingInvitations: List<AccountIdentity>,
    val draftAttemptId: String? = null, val draft: DraftPrivateView? = null,
    val build: PodDeckBuild? = null, val submitted: Boolean = false,
    val deck: PodDeckBuild? = null, val definitions: List<SubmittedDefinition> = emptyList(),
    val updatedAt: String,
)
/** POST /v1/pods; GET /v1/pods returns PodList, GET /v1/pods/{id} returns PodDetail. */
@Serializable data class CreatePod(val requestId: String, val name: String, val cubeId: String, val mode: DraftMode = DraftMode.BOOSTER)
/** PUT /v1/pods/{id}. Omitted fields are unchanged; settings replace the complete settings object. */
@Serializable data class UpdatePod(val requestId: String, val name: String? = null, val cubeId: String? = null, val settings: DraftSettings? = null)
/** POST /v1/pods/{id}/invitations. */
@Serializable data class InviteToPod(val requestId: String, val accountId: String)
/** POST /v1/pods/{id}/{start,accept,decline,leave}; DELETE /v1/pods/{id} destroys.
 * Leave is membership-scoped, even while drafting: it cancels whichever draft is current. */
@Serializable data class PodRequest(val requestId: String)
/** POST /v1/pods/{id}/{submit,cancel}. The attempt must still be current, including on retries. */
@Serializable data class PodDraftRequest(val requestId: String, val draftAttemptId: String)
/** POST /v1/pods/{id}/pick. Counts and ownership are checked by the server engine. */
@Serializable data class PickPod(val requestId: String, val draftAttemptId: String, val packId: String, val keep: List<String>, val burn: List<String> = emptyList())
/** PUT /v1/pods/{id}/build/move. */
@Serializable data class MovePodCopy(val requestId: String, val draftAttemptId: String, val copyId: String, val category: PodDeckCategory)
/** PUT /v1/pods/{id}/build/basics. */
@Serializable data class SetPodBasics(val requestId: String, val draftAttemptId: String, val basics: PodDeckBasics)
@Serializable data class PodAcknowledged(val podId: String)
@Serializable data class PodError(val code: String)
