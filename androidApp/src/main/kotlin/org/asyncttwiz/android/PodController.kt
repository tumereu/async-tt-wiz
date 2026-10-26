package org.asyncttwiz.android

import androidx.compose.runtime.*
import kotlinx.coroutines.runBlocking
import org.asyncttwiz.protocol.*
import java.util.UUID
import java.util.concurrent.ExecutorService

enum class PodTab { DRAFT, BUILD, STATS, GAMES }
enum class PodConfirmation { DESTROY, LEAVE, CANCEL, SUBMIT }
data class NewPodDraft(val name: String, val cubeId: String? = null, val mode: DraftMode = DraftMode.BOOSTER)
data class PodSetupEdit(val name: String, val settings: DraftSettings, val cubeId: String? = null, val revision: Long = 0)
data class PodState(
    val pods: List<PodSummary> = emptyList(), val invitations: List<PodInvitation> = emptyList(),
    val loaded: Boolean = false, val selected: String? = null, val detail: PodDetail? = null,
    val cubes: List<CubeSummary> = emptyList(), val cubesLoaded: Boolean = false,
    val creating: NewPodDraft? = null, val setup: PodSetupEdit? = null,
    val tab: PodTab = PodTab.DRAFT, val choosingMember: Boolean = false,
    val confirmation: PodConfirmation? = null, val loading: Boolean = false,
    val pending: Int = 0, val picking: Boolean = false, val uncertain: Boolean = false,
    val error: String? = null, val notice: String? = null,
)

/** All operations are serialized, including a read after each write. Only build edits predict.
 * An ambiguous write remains at the head with its original request id until retried. */
class PodController(private val api: Pods, private val cubes: Cubes, private val credential: () -> String?,
    private val worker: ExecutorService, private val post: (() -> Unit) -> Unit,
    private val suggest: () -> String = ::gameNameSuggestion,
    private val wait: (suspend (Long) -> Unit)? = null) : AutoCloseable {
    var state by mutableStateOf(PodState())
        private set
    @Volatile private var epoch = 0L
    @Volatile private var closed = false
    private var active = false
    private var lastCredential = credential()
    private var refreshPending = false
    private var cubesPending = false
    private var accepted: PodDetail? = null
    private var editRevision = 0L
    private val notices = mutableMapOf<String, String>()
    private data class Write(val pod: String?, val attempt: String? = null, val pick: Boolean = false,
        val predict: ((PodDeckBuild) -> PodDeckBuild)? = null, val exit: Boolean = false,
        val select: Boolean = false, val setupRevision: Long? = null,
        val send: suspend (String) -> PodDetail?)
    private val writes = mutableListOf<Write>()

    fun clear() {
        epoch++; active = false; refreshPending = false; cubesPending = false
        lastCredential = credential()
        writes.clear(); notices.clear(); accepted = null; state = PodState()
    }
    fun dismissError() { state = state.copy(error = null, notice = null) }
    fun open(id: String) {
        if (state.uncertain) return
        flushSetup()
        accepted = null
        state = state.copy(selected = id, detail = null, creating = null, setup = null,
            confirmation = null, choosingMember = false, error = null, notice = notices.remove(id))
        refresh()
    }
    fun back() {
        if (state.confirmation != null) { dismissConfirmation(); return }
        if (state.choosingMember) { chooseMember(false); return }
        showList()
    }
    fun showList() {
        flushSetup()
        // Keep pending writes attached to their pod; navigation does not discard them.
        accepted = null
        state = state.copy(selected = null, detail = null, creating = null, setup = null,
            confirmation = null, choosingMember = false, error = null)
        refresh()
    }
    fun refresh() { if (!closed) { refreshPending = true; pump() } }
    fun hint(hint: ActivityHint) {
        if (!hint.kind.isPodActivity()) return
        if (hint.kind == ActivityKind.POD_DRAFT_CANCELLED || hint.kind == ActivityKind.POD_DESTROYED) {
            val text = podActivityText(hint.kind)
            if (state.selected == hint.gameId) state = state.copy(notice = text) else notices[hint.gameId] = text
        }
        // Always refresh the list (Quick continue too); refresh the open detail in the same pass.
        refresh()
    }
    fun retry() { syncCredential(); state = state.copy(uncertain = false, error = null); refreshPending = true; pump() }
    fun tab(tab: PodTab) { state = state.copy(tab = tab) }
    fun newPod(cubeId: String? = null) {
        if (writes.isNotEmpty()) return
        accepted = null
        state = state.copy(selected = null, detail = null, creating = NewPodDraft(suggest(), cubeId), setup = null,
            confirmation = null, choosingMember = false, error = null, notice = null)
        loadCubes()
    }
    fun newName(name: String) { if (!state.uncertain && state.pending == 0) state = state.copy(creating = state.creating?.copy(name = name)) }
    fun reroll() = newName(suggest())
    fun newCube(id: String) { if (!state.uncertain && state.pending == 0) state = state.copy(creating = state.creating?.copy(cubeId = id)) }
    fun newMode(mode: DraftMode) { if (!state.uncertain && state.pending == 0) state = state.copy(creating = state.creating?.copy(mode = mode)) }
    fun loadCubes() { cubesPending = true; pump() }
    /** Called whenever this destination returns to composition, including after managing cubes. */
    fun shown() {
        if (state.creating != null) cubesPending = true
        refresh()
    }
    fun create() {
        val draft = state.creating ?: return
        if (state.pending > 0 || state.uncertain) return
        if (draft.name.isBlank() || !DeckValidation.text(draft.name) || draft.name.length > 120 ||
            draft.name.any { it.isISOControl() } || draft.cubeId == null) {
            state = state.copy(error = "Enter a name and select a cube."); return
        }
        val input = CreatePod(requestId(), draft.name, draft.cubeId, draft.mode)
        enqueue(Write(null, select = true, send = { api.create(it, input) }))
    }
    fun respond(id: String, accept: Boolean) {
        if (state.pending > 0) return
        val input = PodRequest(requestId())
        enqueue(Write(id, select = accept, send = {
            if (accept) api.accept(it, id, input) else { api.decline(it, id, input); null }
        }))
    }
    fun editSetup(name: String? = null, settings: DraftSettings? = null, cubeId: String? = null) {
        val detail = state.detail?.takeIf { it.phase == PodPhase.SETUP } ?: return
        if (state.uncertain) return
        val old = state.setup ?: PodSetupEdit(detail.name, detail.settings)
        state = state.copy(setup = old.copy(name = name ?: old.name, settings = settings ?: old.settings,
            cubeId = cubeId ?: old.cubeId, revision = ++editRevision), error = null)
    }
    fun flushSetup() {
        val detail = state.detail ?: return
        val edit = state.setup ?: return
        if (state.uncertain || writes.any { it.setupRevision == edit.revision }) return
        val input = UpdatePod(requestId(), edit.name, edit.cubeId, edit.settings)
        enqueue(Write(detail.id, setupRevision = edit.revision, send = { api.update(it, detail.id, input) }))
    }
    fun chooseMember(value: Boolean) { state = state.copy(choosingMember = value) }
    fun invite(account: AccountIdentity) {
        val detail = state.detail ?: return
        if (state.pending > 0) return
        val input = InviteToPod(requestId(), account.accountId)
        state = state.copy(choosingMember = false)
        enqueue(Write(detail.id, send = { api.invite(it, detail.id, input) }))
    }
    fun start() {
        val detail = state.detail ?: return
        if (state.pending > 0 || state.setup != null || !podCanStart(detail)) return
        val input = PodRequest(requestId())
        enqueue(Write(detail.id, send = { api.start(it, detail.id, input) }))
    }
    fun pick(keep: List<String>, burn: List<String>) {
        val detail = state.detail?.takeIf { it.phase == PodPhase.DRAFTING } ?: return
        val pack = detail.draft?.head ?: return
        val attempt = detail.draftAttemptId ?: return
        if (state.picking || state.uncertain) return
        val input = PickPod(requestId(), attempt, pack.id, keep.toList(), burn.toList())
        enqueue(Write(detail.id, attempt, pick = true, send = { api.pick(it, detail.id, input) }))
    }
    fun move(copyId: String, category: PodDeckCategory) {
        val detail = editable() ?: return
        if (detail.build?.copies?.none { it.copyId == copyId } != false) return
        val input = MovePodCopy(requestId(), detail.draftAttemptId!!, copyId, category)
        enqueue(Write(detail.id, input.draftAttemptId, predict = { build -> build.copy(copies = build.copies.map {
            if (it.copyId == copyId) it.copy(category = category) else it
        }) }, send = { api.move(it, detail.id, input) }))
    }
    fun basics(basics: PodDeckBasics) {
        val detail = editable() ?: return
        if (!basics.valid) return
        val input = SetPodBasics(requestId(), detail.draftAttemptId!!, basics)
        enqueue(Write(detail.id, input.draftAttemptId, predict = { it.copy(basics = basics) },
            send = { api.basics(it, detail.id, input) }))
    }
    /** Relative so taps faster than recomposition each count against the latest predicted build. */
    fun adjustBasic(index: Int, delta: Int) {
        val current = state.detail?.build?.basics ?: return
        basics(current.podBuildAdjust(index, delta))
    }
    private fun editable() = state.detail?.takeIf { it.draftAttemptId != null && !it.submitted &&
        it.phase != PodPhase.SETUP && !state.uncertain && state.confirmation == null }
    fun requestConfirmation(action: PodConfirmation) {
        if (state.detail != null && !state.uncertain) state = state.copy(confirmation = action)
    }
    fun dismissConfirmation() { if (!state.uncertain) state = state.copy(confirmation = null) }
    fun confirm() { perform(state.confirmation ?: return) }
    /** The Build view already asked for confirmation; submit without a second dialog. */
    fun submit() { perform(PodConfirmation.SUBMIT) }
    private fun perform(action: PodConfirmation) {
        val detail = state.detail ?: return
        if (state.pending > 0 || state.uncertain) return
        val input = PodRequest(requestId())
        val submit = detail.draftAttemptId?.let { PodDraftRequest(input.requestId, it) }
        if (action in listOf(PodConfirmation.SUBMIT, PodConfirmation.CANCEL) && submit == null) return
        enqueue(Write(detail.id, exit = action in listOf(PodConfirmation.LEAVE, PodConfirmation.DESTROY), send = { token ->
            when (action) {
                PodConfirmation.DESTROY -> { api.destroy(token, detail.id, input); null }
                PodConfirmation.LEAVE -> { api.leave(token, detail.id, input); null }
                PodConfirmation.CANCEL -> api.cancel(token, detail.id, submit!!)
                PodConfirmation.SUBMIT -> api.submit(token, detail.id, submit!!)
            }
        }))
    }
    private fun requestId() = UUID.randomUUID().toString()
    private fun enqueue(write: Write) {
        syncCredential()
        if (closed || state.uncertain || credential() == null) return
        writes += write; state = state.copy(error = null); render(); pump()
    }
    private fun render() {
        var detail = accepted?.takeIf { it.id == state.selected }
        if (detail != null && detail.build != null && !detail.submitted) {
            val base = detail
            val predicted = writes.filter { it.pod == base.id && it.attempt == base.draftAttemptId }
                .fold(base.build!!) { build, write -> write.predict?.invoke(build) ?: build }
            detail = base.copy(build = predicted)
        }
        state = state.copy(detail = detail, pending = writes.size, picking = writes.any { it.pick })
    }
    private fun applyDetail(detail: PodDetail) {
        if (state.selected != detail.id) return
        val changed = accepted?.phase != detail.phase
        accepted = detail
        if (changed) state = state.copy(tab = if (detail.phase == PodPhase.DRAFTING) PodTab.DRAFT else PodTab.BUILD)
        if (detail.phase != PodPhase.SETUP) state = state.copy(setup = null)
    }
    private fun syncCredential(): String? {
        val token = credential()
        if (token != lastCredential) {
            lastCredential = token
            epoch++
            // A rejected credential can disappear before the request's posted completion.
            // Fence its result, but retain ambiguous writes and their original request IDs.
            if (active) {
                refreshPending = true
                cubesPending = cubesPending || state.creating != null
                state = state.copy(loading = false, uncertain = writes.isNotEmpty())
            }
            active = false
        }
        return token
    }
    private fun pump() {
        val token = syncCredential()
        if (closed || active || state.uncertain || token == null) return
        val write = writes.firstOrNull()
        val read = write == null && refreshPending
        val readCubes = cubesPending
        if (write == null && !read && !readCubes) return
        active = true
        if (read) refreshPending = false
        cubesPending = false
        val generation = epoch; val selected = state.selected
        state = state.copy(loading = true)
        worker.execute {
            fun current() = !closed && generation == epoch && credential() == token
            fun complete(): Boolean {
                // Never reset a newer request when an older completion arrives late.
                if (closed || generation != epoch) return false
                syncCredential()
                if (generation != epoch) return false
                active = false
                state = state.copy(loading = false)
                return true
            }
            try {
                val result = runBlocking {
                    var detail: PodDetail? = null
                    if (write != null) {
                        detail = serviceRetry(wait, ::current) { write.send(token) }
                        // Even mutation responses are followed by a fresh authorized read.
                        val id = detail?.id ?: write.pod
                        if (id != null) try { detail = serviceRetry(wait, ::current) { api.detail(token, id) } }
                        catch (failure: PodFailure) { if (failure.code != "not_found") throw failure else detail = null }
                    } else if (read && selected != null) {
                        try { detail = serviceRetry(wait, ::current) { api.detail(token, selected) } }
                        catch (failure: PodFailure) { if (failure.code != "not_found") throw failure }
                    }
                    val list = if (read || write != null) serviceRetry(wait, ::current) { api.list(token) } else null
                    val sources = if (readCubes) {
                        val all = linkedMapOf<String, CubeSummary>(); val cursors = mutableSetOf<String>(); var after: String? = null
                        do {
                            val page = serviceRetry(wait, ::current) { cubes.listCubes(token, after) }
                            page.cubes.forEach { all[it.id] = it }; after = page.next
                            if (after != null && !cursors.add(after)) throw PodFailure("invalid_response")
                        } while (after != null)
                        all.values.toList()
                    } else null
                    Triple(detail, list, sources)
                }
                post { if (complete()) {
                    state = state.copy(loading = false, error = null)
                    if (write != null) {
                        writes.removeAt(0)
                        if (write.setupRevision == state.setup?.revision) state = state.copy(setup = null)
                        state = state.copy(confirmation = null)
                        if (write.exit) { accepted = null; state = state.copy(selected = null, detail = null, setup = null) }
                        if (write.select && result.first != null) state = state.copy(selected = result.first!!.id, creating = null)
                    }
                    result.second?.let { state = state.copy(pods = it.pods, invitations = it.invitations, loaded = true) }
                    result.third?.let { state = state.copy(cubes = it, cubesLoaded = true) }
                    result.first?.let(::applyDetail)
                    if ((read || write != null && !write.exit) && selected != null && result.first == null && state.selected == selected) {
                        accepted = null; state = state.copy(selected = null, setup = null, notice = state.notice ?: "This pod is no longer available.")
                    }
                    render(); pump()
                } }
            } catch (failure: Exception) {
                post { if (complete()) {
                    val uncertain = write != null && (transientServiceFailure(failure) || (failure as? PodFailure)?.code == "invalid_response")
                    if (write != null && !uncertain) writes.removeAt(0)
                    if ((failure as? PodFailure)?.code in listOf("draft_attempt_expired", "not_drafting", "not_found", "deck_submitted")) {
                        writes.clear(); refreshPending = true
                    }
                    state = state.copy(loading = false, uncertain = uncertain, error = podError(failure))
                    if (readCubes) cubesPending = true
                    render()
                    // Do not spin on failing reads. Explicit retry or the next visibility tick retries.
                    if (write != null && !uncertain) pump()
                } }
            }
        }
    }
    override fun close() { closed = true; clear(); worker.execute { api.close(); cubes.close() }; worker.shutdown() }
}

internal fun podCanStart(detail: PodDetail) = detail.phase == PodPhase.SETUP && detail.members.size >= 2 &&
    detail.settings.validate(detail.members.size).isEmpty() && detail.supply.sufficient

internal fun podStatus(pod: PodSummary): String = when (pod.attention) {
    PodAttention.YOUR_PICK -> "Your pick · ${podPackCount(pod.packsWaiting)}"
    PodAttention.BUILD -> "Build your deck"
    PodAttention.NONE -> when (pod.phase) { PodPhase.SETUP -> "Setup"; PodPhase.DRAFTING -> "Drafting"; PodPhase.DRAFTED -> "Deck submitted" }
}
internal fun podMemberStatus(member: PodMember, phase: PodPhase): String = when {
    member.submitted -> "submitted"
    phase == PodPhase.SETUP -> "accepted"
    member.packsWaiting > 0 -> "picking · ${podPackCount(member.packsWaiting)}"
    phase == PodPhase.DRAFTED || member.building -> "building"
    else -> "waiting"
}
internal fun podPackCount(count: Int) = "$count ${if (count == 1) "pack" else "packs"}"
internal fun podConfirmationText(action: PodConfirmation, detail: PodDetail): String = when (action) {
    PodConfirmation.DESTROY -> "Destroy this pod for everyone?"
    PodConfirmation.LEAVE -> if (detail.phase == PodPhase.DRAFTING)
        "This cancels the draft for all ${detail.members.size} players and discards all picks." else "Leave this pod?"
    PodConfirmation.CANCEL -> "Discard all picks and return to setup?"
    PodConfirmation.SUBMIT -> "Submit and lock your deck? Main, Side and basics cannot be changed afterwards."
}
private fun podError(failure: Exception): String = when ((failure as? PodFailure)?.code) {
    "invalid_settings" -> "Invalid settings. Check the pack sizes, keep/burn counts and Advanced settings."
    "insufficient_supply" -> "The source cube does not have enough cards."
    "main_too_small" -> "Main must contain at least 40 cards including basics."
    "sideboard_limit" -> "Move surplus sideboard cards to Trash before submitting."
    "not_found" -> "This pod is no longer available."
    else -> "Could not complete the pod request (${(failure as? PodFailure)?.code ?: "connection"}). Retry to check the outcome."
}
