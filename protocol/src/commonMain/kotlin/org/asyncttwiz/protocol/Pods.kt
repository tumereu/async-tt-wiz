package org.asyncttwiz.protocol

import kotlinx.serialization.Serializable

/** Pod HTTP v1. DraftState is deliberately not part of any response. */
@Serializable enum class PodPhase { SETUP, DRAFTING, DRAFTED }
@Serializable enum class PodAttention { YOUR_PICK, BUILD, NONE }
/** [packsWaiting] is the caller's [PodMember.packsWaiting]. */
@Serializable data class PodSummary(
    val id: String, val name: String, val phase: PodPhase, val creator: AccountIdentity,
    val members: List<AccountIdentity>, val attention: PodAttention, val packsWaiting: Int,
    val updatedAt: String,
    /** The pod's engine; turn-based engines read "Your pick" without a pack count. */
    val mode: DraftMode = DraftMode.BOOSTER,
    /** The other members' messages in the pod's chat the caller hasn't read (DRF-012). */
    val chatUnread: Int = 0,
)
@Serializable data class PodInvitation(val podId: String, val name: String, val creator: AccountIdentity, val invitedAt: String)
@Serializable data class PodList(val pods: List<PodSummary>, val invitations: List<PodInvitation>)
@Serializable enum class PodSourceKind { CUBE, SET }
/** A cube source's [totalCopies] counts the pod's prepared Main (what gets drafted); [maybeCopies] its Maybe. A set
 * source's [packSets] is its pack grid read row by row (DRF-177, [PodPackGrid], normalized: `[setCode]` for one set);
 * [setCode], [setName] and [boosterName] are its first pack's set's, and [boosterKind] is approximate when any of its sets
 * has no booster recipe. */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable data class PodSource(
    val cubeName: String = "", val totalCopies: Long = 0,
    val kind: PodSourceKind = PodSourceKind.CUBE,
    val setCode: String? = null, val setName: String? = null, val boosterKind: SetBoosterKind? = null,
    val boosterName: String? = null,
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val maybeCopies: Long = 0,
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val packSets: List<String> = emptyList(),
)
/** Boards/log of the grids the caller has seen, plus only the caller's pool and pickable grids. */
@Serializable data class PodGridView(val publicView: GridPublicView, val mine: GridPrivateView)
@Serializable data class PodMember(
    /**
     * packsWaiting: picks the member can make in a row if nobody else does anything (DraftEngine/GridEngine
     * consecutivePicks): their queue plus next-round packs (GRID: grids) they would open themselves. Positive exactly
     * when their queue is not empty.
     */
    val account: AccountIdentity, val packsWaiting: Int = 0, val picksMade: Int = 0,
    val building: Boolean = false, val submitted: Boolean = false,
    val picking: Boolean = false,
)
@Serializable data class PodDetail(
    val id: String, val name: String, val phase: PodPhase, val creator: AccountIdentity,
    val settings: DraftSettings, val source: PodSource,
    /** SET sources have unlimited supply, represented by available == Long.MAX_VALUE. */
    val supply: DraftSupply,
    val members: List<PodMember>, val pendingInvitations: List<AccountIdentity>,
    val draftAttemptId: String? = null, val draft: DraftPrivateView? = null,
    val build: PodDeckBuild? = null, val submitted: Boolean = false,
    val deck: PodDeckBuild? = null, val definitions: List<SubmittedDefinition> = emptyList(),
    val updatedAt: String, val grid: PodGridView? = null,
    /** Caller-only; never included in member projections or shared game state. */
    val deckName: String = "$name deck",
    /** Setup only: the server starts the draft when the last pending invitation is accepted. */
    val autoStart: Boolean = false,
    /** Creator-only: why auto-start last switched itself off, until it is armed again or the draft starts. */
    val autoStartOff: PodAutoStartOff? = null,
    /** Caller-only notes for the current draft attempt; null until first saved. */
    val notes: PodDeckNotes? = null,
    /** Rochester, Rotisserie, Winston, Winchester and Sealed: what the caller may see of the draft. */
    val limited: LimitedView? = null,
    /** Caller-only: the definition chosen as this deck's cover (DRF-166); null until chosen. */
    val deckCover: String? = null,
)
/** A member's private deck notes and tally counters (W, U, B, R, G, C, gold, neutral) for one draft attempt. */
@Serializable data class PodDeckNotes(val text: String = "", val counters: List<Int> = List(COUNTERS) { 0 }) {
    val valid: Boolean get() = text.length <= MAX_TEXT && DeckValidation.text(text) &&
        text.none { it.isISOControl() && it != '\n' } && counters.size == COUNTERS && counters.all { it in 0..MAX_COUNTER }
    companion object { const val MAX_TEXT = 2000; const val COUNTERS = 8; const val MAX_COUNTER = 99 }
}
/** reason is "declined" (account = who declined) or the start error code that prevented the automatic start. */
@Serializable data class PodAutoStartOff(val reason: String, val account: AccountIdentity? = null)
/** POST /v1/pods: exactly one of cubeId/setCode/packSets is required. [setCode]: a draft set the server builds the packs
 * of from its card catalog (docs/specs/web-client.md section 3.7), for every pack. [packSets]: the pack grid read row by
 * row (DRF-177, [PodPackGrid]), each pack's set by its code; every set must be draftable. GET /v1/pods returns PodList,
 * GET /v1/pods/{id} returns PodDetail. */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable data class CreatePod(val requestId: String, val name: String, val cubeId: String? = null,
    val mode: DraftMode = DraftMode.BOOSTER,
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val settings: DraftSettings? = null,
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val setCode: String? = null,
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val packSets: List<String>? = null)
/** PUT /v1/pods/{id}. Omitted fields are unchanged; settings replace the complete settings object (a set pod's pack grid
 * is then resized to them). At most one of cubeId/setCode/packSets may be supplied, replacing the entire source snapshot
 * in setup. */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable data class UpdatePod(val requestId: String, val name: String? = null, val cubeId: String? = null,
    val settings: DraftSettings? = null,
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val setCode: String? = null,
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val packSets: List<String>? = null)
/** POST /v1/pods/{id}/invitations. */
@Serializable data class InviteToPod(val requestId: String, val accountId: String)
/** POST /v1/pods/{id}/{start,accept,decline,leave}; DELETE /v1/pods/{id} destroys.
 * Leave is membership-scoped, even while drafting: it cancels whichever draft is current. */
@Serializable data class PodRequest(val requestId: String)
/** POST /v1/pods/{id}/auto-start (creator, setup). Arming needs a pending invitation and members plus invitations within
 * the engine's player limit (409 too_many_players); settings edits keep it armed unless they put the pod over the new
 * engine's limit. A decline, going over the limit, or a start failure at the last acceptance switches it off and
 * notifies the creator. */
@Serializable data class SetPodAutoStart(val requestId: String, val enabled: Boolean)
/** POST /v1/pods/{id}/cancel (and submit, see [SubmitPodDeck]). The attempt must still be current, including on retries. */
@Serializable data class PodDraftRequest(val requestId: String, val draftAttemptId: String)
/** POST /v1/pods/{id}/submit. The server gives the submitter's draft deck its basics from its catalog (LOB-052). */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable data class SubmitPodDeck(val requestId: String, val draftAttemptId: String)
/** POST /v1/pods/{id}/pick. Counts and ownership are checked by the server engine. */
@Serializable data class PickPod(val requestId: String, val draftAttemptId: String, val packId: String, val keep: List<String>, val burn: List<String> = emptyList(), val librarians: List<String> = emptyList())
/** PUT /v1/pods/{id}/draft/marks. Saves the caller's marks for their current head pack only (409 otherwise); latest wins.
 * Not request-identified: the marks are local intent, cleared when that pack is picked, and the pick is validated as before. */
@Serializable data class SetPodDraftMarks(val draftAttemptId: String, val packId: String, val keeps: List<String> = emptyList(),
    val burns: List<String> = emptyList(), val librarians: List<String> = emptyList())
/** PUT /v1/pods/{id}/build/notes. Caller-private, allowed while the attempt is current (also after submitting).
 * Not request-identified: latest wins, like draft marks. */
@Serializable data class SetPodDeckNotes(val draftAttemptId: String, val notes: PodDeckNotes)
/** POST /v1/pods/{id}/grid/select; index is zero-based (0..2). */
@Serializable data class SelectPodGrid(val requestId: String, val draftAttemptId: String, val gridId: String,
    val line: GridLineType, val index: Int)
/** POST /v1/pods/{id}/turn: a Rochester, Rotisserie, Winston or Winchester move ([LimitedMove] without the player). */
@Serializable data class PodTurn(val requestId: String, val draftAttemptId: String, val action: LimitedAction, val turn: Int,
    val card: String? = null, val packId: String? = null, val pile: Int? = null, val table: Int = 0)
/** GET /v1/pods/{id}/draft/history: the caller's Booster or Glimpse picks in order (DRF-165), the definitions of their
 * cards, and the draft's [seating] (account ids in seat order, DRF-043): a round's packs pass to the next seat, or the
 * previous one ([DraftEngine.direction]). */
@Serializable data class PodDraftHistory(val picks: List<DraftHistoryPick>, val definitions: List<SubmittedDefinition> = emptyList(),
    val seating: List<String> = emptyList())
/** PUT /v1/pods/{id}/rotisserie/queue: the caller's whole Rotisserie pick queue, in order (DRF-164); empty empties it. */
@Serializable data class PodPickQueue(val requestId: String, val draftAttemptId: String, val cards: List<String>)
/** PUT /v1/pods/{id}/build/move. */
@Serializable data class MovePodCopy(val requestId: String, val draftAttemptId: String, val copyId: String, val category: PodDeckCategory)
/** PUT /v1/pods/{id}/build/basics. [side] sets the Side lands instead of Main. */
@Serializable data class SetPodBasics(val requestId: String, val draftAttemptId: String, val basics: PodDeckBasics,
    val side: Boolean = false)
/** PUT /v1/pods/{id}/build/name. Changes only the caller's private deck name. */
@Serializable data class SetPodDeckName(val requestId: String, val draftAttemptId: String, val name: String)
/** PUT /v1/pods/{id}/build/cover. Changes only the caller's private deck cover: [definition] must be among their
 * [PodDeckRules.coverChoices] (DRF-166). */
@Serializable data class SetPodDeckCover(val requestId: String, val draftAttemptId: String, val definition: String)
/** GET /v1/pods/{id}/cube (creator): the pod's prepared cube, Main (drafted) and Maybe (not drafted). The saved cube
 * is never changed by preparing. */
@Serializable data class PodCube(val cubeName: String, val entries: List<PodCubeEntry>)
/** One card of a pod's prepared cube: [definition] is the card data the pod keeps (its digest, what moves name); the phone
 * shows it from its own card records by [printingId] (owner 2026-11-10), or by [name] alone without one. */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable data class PodCubeEntry(val definition: String, val printingId: String?, val name: String, val quantity: Int,
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER)
    val section: SavedDeckSection = SavedDeckSection.MAINBOARD)
/** PUT /v1/pods/{id}/cube/move (creator, setup, cube source): moves [quantity] copies of [definition] from [from] to the
 * other section (Main ↔ Maybe) of this pod's prepared cube. Answers the updated [PodCube]. */
@Serializable data class MovePodCubeCards(val requestId: String, val definition: String, val from: SavedDeckSection, val quantity: Int = 1)
@Serializable data class PodAcknowledged(val podId: String)
@Serializable data class PodError(val code: String)
