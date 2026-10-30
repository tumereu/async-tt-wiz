package org.asyncttwiz.android

import androidx.compose.runtime.*
import kotlinx.coroutines.runBlocking
import org.asyncttwiz.protocol.*
import java.util.UUID
import java.util.concurrent.ExecutorService

enum class PodTab { DRAFT, BUILD, STATS, GAMES }
internal fun podTabs(phase: PodPhase): List<PodTab> = if (phase == PodPhase.DRAFTING)
    listOf(PodTab.DRAFT, PodTab.BUILD, PodTab.STATS) else listOf(PodTab.BUILD, PodTab.STATS, PodTab.GAMES)
internal fun podDefaultTab(phase: PodPhase, submitted: Boolean): PodTab = when {
    phase == PodPhase.DRAFTING -> PodTab.DRAFT
    submitted -> PodTab.GAMES
    else -> PodTab.BUILD
}
enum class PodConfirmation { DESTROY, LEAVE, CANCEL, SUBMIT, START }
data class NewPodDraft(val name: String, val cubeId: String? = null, val mode: DraftMode = DraftMode.BOOSTER,
    val setSource: PodSetSource? = null, val settings: DraftSettings = DraftSettings())
data class PodSetupEdit(val name: String, val settings: DraftSettings, val cubeId: String? = null, val revision: Long = 0,
    val setSource: PodSetSource? = null)
data class PodState(
    val pods: List<PodSummary> = emptyList(), val invitations: List<PodInvitation> = emptyList(),
    val loaded: Boolean = false, val selected: String? = null, val detail: PodDetail? = null,
    val cubes: List<CubeSummary> = emptyList(), val cubesLoaded: Boolean = false,
    val creating: NewPodDraft? = null, val setup: PodSetupEdit? = null,
    val tab: PodTab = PodTab.DRAFT, val choosingMember: Boolean = false,
    val confirmation: PodConfirmation? = null, val loading: Boolean = false,
    val pending: Int = 0, val picking: Boolean = false, val uncertain: Boolean = false,
    val error: String? = null, val notice: String? = null,
    val sourcePreview: PodSource? = null,
    /** Shown in the source picker: why the player is asked to choose a source (a preset's cube is gone). */
    val sourceHint: String? = null,
    /** Automatic refreshes have kept failing for a while; the shown pod data may be stale. */
    val unreachable: Boolean = false,
)

/** Runs [task] after [delayMillis] off the main thread; the returned function cancels it if it has not started. */
fun interface PodMarksScheduler { fun schedule(delayMillis: Long, task: () -> Unit): () -> Unit }
/** Local pick marks belong to one visit of a pack: the same pack can come back later with fewer cards. */
internal data class PodMarksKey(val pod: String, val attempt: String, val pack: String, val pick: Int)
internal const val PodMarksDelayMillis = 500L
/** Private deck notes belong to one draft attempt. */
internal data class PodNotesKey(val pod: String, val attempt: String)

/** One reversible Build change: where a copy was before a move, or the basics before a tap. */
internal sealed interface PodBuildStep {
    data class Move(val copyId: String, val from: PodDeckCategory, val to: PodDeckCategory) : PodBuildStep
    data class Basics(val previous: PodDeckBasics, val side: Boolean = false) : PodBuildStep
}
internal const val PodBuildUndoLimit = 50
private data class PodBuildHistory(val pod: String, val attempt: String, val steps: List<PodBuildStep>)

/** All operations are serialized, including a read after each write. Setup edits, build edits and queued picks predict.
 * An ambiguous write remains at the head with its original request id until retried. */
class PodController(private val api: Pods, private val cubes: Cubes, private val credential: () -> String?,
    private val worker: ExecutorService, private val post: (() -> Unit) -> Unit,
    private val suggest: () -> String = ::gameNameSuggestion,
    private val wait: (suspend (Long) -> Unit)? = null,
    setRepository: PodSetRepository? = null,
    private val librarianDefinition: () -> SubmittedDefinition = { throw PodFailure("librarian_definition_unavailable") },
    clock: () -> Long = ::monotonicMillis,
    /** Saves head-pack marks on the server, outside the serialized queue so a pick never waits for them. */
    private val marksScheduler: PodMarksScheduler? = null) : AutoCloseable {
    var state by mutableStateOf(PodState())
        private set
    @Volatile private var epoch = 0L
    @Volatile private var closed = false
    private var active = false
    private var lastCredential = credential()
    private var refreshPending = false
    /** The pending refresh was asked for by the player (Retry), so its failure is reported at once. */
    private var refreshRequested = false
    private val quietFailures = QuietRefreshFailures(clock)
    private var cubesPending = false
    private var accepted: PodDetail? = null
    private var editRevision = 0L
    private val notices = mutableMapOf<String, String>()
    private data class Write(val pod: String?, val attempt: String? = null, val pick: Boolean = false,
        val draftPick: PickPod? = null,
        val predict: ((PodDeckBuild) -> PodDeckBuild)? = null, val exit: Boolean = false,
        val select: Boolean = false, val setupRevision: Long? = null,
        val createdFrom: NewPodDraft? = null,
        val deckName: String? = null, val submit: Boolean = false,
        val send: suspend (String) -> PodDetail?)
    private val writes = mutableListOf<Write>()
    /** Keep/burn/Librarian marks survive tab switches here, and app restarts through the server copy. */
    private val pickMarks = mutableStateMapOf<PodMarksKey, PodPickMarks>()
    private data class MarksWrite(val key: PodMarksKey, val marks: PodPickMarks, val token: String, val retries: Int = 0)
    private val marksLock = Any()
    /** Latest unsent marks (coalesced) and packs already picked locally; shared with the scheduler thread. */
    private var marksPending: MarksWrite? = null
    private val pickedPacks = mutableSetOf<PodMarksKey>()
    private var marksCancel: (() -> Unit)? = null
    /** Deck notes, saved like marks: debounced, latest wins, off the serialized queue; adopted from the server when absent. */
    private val deckNotes = mutableStateMapOf<PodNotesKey, PodDeckNotes>()
    private data class NotesWrite(val key: PodNotesKey, val notes: PodDeckNotes, val token: String, val retries: Int = 0)
    private var notesPending: NotesWrite? = null
    private var notesCancel: (() -> Unit)? = null
    /** Build undo for the open pod's current attempt; survives tab switches, not restarts. */
    private var buildHistory by mutableStateOf<PodBuildHistory?>(null)
    /** The pick/grid/Build card inspector (Next face, Enlarge artwork); personal, closed on pod change. */
    val inspection = CardInspectionController()
    internal val setPicker = PodSetPickerController(setRepository, worker, post,
        choosing = { set -> state = state.copy(sourcePreview = PodSource(kind = PodSourceKind.SET, setCode = set.code, setName = set.name)) },
        failed = { state = state.copy(sourcePreview = null, error = "Could not load source.") }) { upload ->
        val source = PodSetSource(upload.snapshot, upload.definitions.definitions)
        state = state.copy(sourcePreview = null, sourceHint = null)
        if (state.creating != null) state = state.copy(creating = state.creating?.copy(cubeId = null, setSource = source))
        else editSetup(setSource = source)
        state = state.copy(notice = upload.warnings.takeIf { it.isNotEmpty() }?.joinToString("\n"))
    }
    /** Draft table presets for the New pod form and the creator's setup form. */
    internal val presets = PodPresetController(api, credential, worker, post, ::presetForm, ::applyPreset)

    fun clear() {
        epoch++; active = false; refreshPending = false; refreshRequested = false; cubesPending = false
        quietFailures.succeeded()
        inspection.close()
        setPicker.clear()
        presets.clear()
        lastCredential = credential()
        writes.clear(); notices.clear(); accepted = null; state = PodState()
        pickMarks.clear(); marksCancel?.invoke(); marksCancel = null
        deckNotes.clear(); notesCancel?.invoke(); notesCancel = null; buildHistory = null
        synchronized(marksLock) { marksPending = null; pickedPacks.clear(); notesPending = null }
    }
    fun dismissError() { state = state.copy(error = null, notice = null) }
    fun open(id: String) {
        if (state.uncertain) return
        flushSetup()
        setPicker.dismiss()
        presets.dismiss()
        inspection.close()
        accepted = null
        if (buildHistory?.pod != id) buildHistory = null
        state = state.copy(selected = id, detail = null, creating = null, setup = null,
            confirmation = null, choosingMember = false, error = null, notice = notices.remove(id), sourcePreview = null)
        refresh()
    }
    fun back() {
        if (inspection.state != null) { inspection.close(); return }
        if (setPicker.state.open) { setPicker.dismiss(); return }
        if (state.confirmation != null) { dismissConfirmation(); return }
        if (state.choosingMember) { chooseMember(false); return }
        showList()
    }
    fun showList() {
        flushSetup()
        setPicker.dismiss()
        presets.dismiss()
        inspection.close()
        // Keep pending writes attached to their pod; navigation does not discard them.
        accepted = null
        state = state.copy(selected = null, detail = null, creating = null, setup = null,
            confirmation = null, choosingMember = false, error = null, sourcePreview = null)
        refresh()
    }
    fun refresh(requested: Boolean = false) {
        if (!closed) { refreshPending = true; refreshRequested = refreshRequested || requested; pump() }
    }
    fun hint(hint: ActivityHint) {
        if (!hint.kind.isPodActivity()) return
        if (hint.kind == ActivityKind.POD_DRAFT_CANCELLED || hint.kind == ActivityKind.POD_DESTROYED) {
            val text = podActivityText(hint.kind)
            if (state.selected == hint.gameId) state = state.copy(notice = text) else notices[hint.gameId] = text
        }
        // Always refresh the list (Quick continue too); refresh the open detail in the same pass.
        refresh()
    }
    fun retry() { syncCredential(); state = state.copy(uncertain = false, error = null); refreshPending = true; refreshRequested = true; pump() }
    fun tab(tab: PodTab) { state = state.copy(tab = tab) }
    fun newPod(cubeId: String? = null) {
        if (writes.isNotEmpty()) return
        accepted = null
        setPicker.dismiss()
        state = state.copy(selected = null, detail = null, creating = NewPodDraft(suggest(), cubeId), setup = null,
            confirmation = null, choosingMember = false, error = null, notice = null, sourcePreview = null)
        loadCubes()
    }
    fun newName(name: String) { state = state.copy(creating = state.creating?.copy(name = name)) }
    fun reroll() = newName(suggest())
    fun newCube(id: String) {
        setPicker.dismiss()
        state = state.copy(sourcePreview = null, sourceHint = null, creating = state.creating?.copy(cubeId = id, setSource = null))
    }
    fun chooseSet() {
        if (state.creating != null || state.detail?.phase == PodPhase.SETUP) {
            state = state.copy(sourcePreview = null, sourceHint = null)
            loadCubes(); setPicker.open()
        }
    }
    /** The open form's preset contents: the New pod form, or the setup form of a pod still in setup. */
    private fun presetForm(): PodPresetForm? {
        fun cubeName(id: String?) = state.cubes.firstOrNull { it.id == id }?.name
        state.creating?.let { draft ->
            val set = draft.setSource?.snapshot
            return PodPresetForm(draft.settings, draft.cubeId, set?.setCode, set?.setName, set?.setName ?: cubeName(draft.cubeId))
        }
        val detail = state.detail?.takeIf { it.phase == PodPhase.SETUP } ?: return null
        val edit = state.setup
        val settings = edit?.settings ?: detail.settings
        edit?.setSource?.snapshot?.let { return PodPresetForm(settings, setCode = it.setCode, setName = it.setName, sourceName = it.setName) }
        edit?.cubeId?.let { return PodPresetForm(settings, cubeId = it, sourceName = cubeName(it)) }
        val source = detail.source
        if (source.kind == PodSourceKind.SET) return PodPresetForm(settings, setCode = source.setCode,
            setName = source.setName ?: source.setCode, sourceName = source.setName ?: source.setCode)
        // A pod keeps a copy of its cube, not a link: find the player's cube by its name, if exactly one matches.
        val cube = state.cubes.singleOrNull { it.name == source.cubeName }
        return PodPresetForm(settings, cubeId = cube?.id, sourceName = cube?.name)
    }
    /** Fills the form from [preset]. A set is loaded from the catalog; a deleted cube leaves the source to choose. */
    internal fun applyPreset(preset: PodPreset) {
        val settings = preset.settings.copy(librarianDefinition = null)
        val cube = preset.cubeId?.takeUnless { preset.cubeMissing }
        val draft = state.creating
        if (draft != null) {
            setPicker.dismiss()
            // A preset saved without a source keeps the source already chosen.
            val keep = preset.cubeId == null && preset.setCode == null
            state = state.copy(sourcePreview = null, sourceHint = null, error = null, notice = null,
                creating = draft.copy(settings = settings, mode = settings.mode, cubeId = if (keep) draft.cubeId else cube,
                    setSource = if (keep) draft.setSource else null))
        } else {
            if (state.detail?.phase != PodPhase.SETUP || state.uncertain) return
            setPicker.dismiss()
            state = state.copy(sourcePreview = null, sourceHint = null)
            editSetup(settings = settings, cubeId = cube)
        }
        when {
            preset.setCode != null -> setPicker.load(preset.setCode!!, preset.setName ?: preset.setCode!!)
            preset.cubeMissing -> {
                chooseSet()
                state = state.copy(sourceHint = "The cube of “${preset.name}” no longer exists. Choose a set or cube.")
            }
        }
    }
    fun newMode(mode: DraftMode) = newSettings { podModeSettings(mode, it) }
    fun newSettings(change: (DraftSettings) -> DraftSettings) {
        state.creating?.let { draft -> val settings = change(draft.settings)
            state = state.copy(creating = draft.copy(settings = settings, mode = settings.mode)) }
    }
    fun chooseCube(id: String) {
        if (state.creating != null) newCube(id) else {
            setPicker.dismiss(); state = state.copy(sourcePreview = null, sourceHint = null); editSetup(cubeId = id)
        }
    }
    fun loadCubes() { cubesPending = true; pump() }
    /** Called whenever this destination returns to composition, including after managing cubes. */
    fun shown() {
        if (state.creating != null) cubesPending = true
        refresh()
    }
    fun create() {
        val draft = state.creating ?: return
        if (state.pending > 0 || state.uncertain || state.sourcePreview != null) return
        if (draft.name.isBlank() || !DeckValidation.text(draft.name) || draft.name.length > 120 ||
            draft.name.any { it.isISOControl() } || (draft.cubeId == null && draft.setSource == null)) {
            state = state.copy(error = "Enter a name and select a cube or MTG set."); return
        }
        if (draft.settings.validate(2).isNotEmpty()) { state = state.copy(error = "Invalid settings."); return }
        val input = CreatePod(requestId(), draft.name, draft.cubeId, draft.mode, draft.setSource, draft.settings)
        val upload by lazy { input.copy(settings = withLibrarian(draft.settings)) }
        enqueue(Write(null, select = true, createdFrom = draft, send = { api.create(it, upload) }))
    }
    fun respond(id: String, accept: Boolean) {
        if (state.pending > 0) return
        val input = PodRequest(requestId())
        enqueue(Write(id, select = accept, send = {
            if (accept) api.accept(it, id, input) else { api.decline(it, id, input); null }
        }))
    }
    fun editSetup(name: String? = null, settings: DraftSettings? = null, cubeId: String? = null, setSource: PodSetSource? = null) {
        val detail = state.detail?.takeIf { it.phase == PodPhase.SETUP } ?: return
        val base = accepted ?: detail
        val old = state.setup ?: PodSetupEdit(base.name, base.settings)
        state = state.copy(setup = old.copy(name = name ?: old.name, settings = settings ?: old.settings,
            cubeId = if (setSource != null) null else cubeId ?: old.cubeId,
            setSource = if (cubeId != null) null else setSource ?: old.setSource, revision = ++editRevision), error = null)
        render()
    }
    /** Apply field edits to the latest pending settings, not a UI snapshot awaiting recomposition. */
    fun editSettings(change: (DraftSettings) -> DraftSettings) {
        val settings = state.setup?.settings ?: state.detail?.settings ?: return
        editSetup(settings = change(settings))
    }
    fun flushSetup() {
        val detail = state.detail ?: return
        val edit = state.setup ?: return
        if (state.uncertain || writes.any { it.setupRevision == edit.revision }) return
        val input = UpdatePod(requestId(), edit.name, edit.cubeId, edit.settings, edit.setSource)
        val upload by lazy { input.copy(settings = withLibrarian(edit.settings)) }
        enqueue(Write(detail.id, setupRevision = edit.revision, send = { api.update(it, detail.id, upload) }))
    }
    fun chooseMember(value: Boolean) { state = state.copy(choosingMember = value) }
    fun invite(account: AccountIdentity) {
        val detail = state.detail ?: return
        if (state.uncertain) return
        val input = InviteToPod(requestId(), account.accountId)
        state = state.copy(choosingMember = false)
        enqueue(Write(detail.id, send = { api.invite(it, detail.id, input) }))
    }
    fun start() {
        val detail = state.detail ?: return
        if (state.pending > 0 || state.setup != null || state.sourcePreview != null || !podCanStart(detail)) return
        val input = PodRequest(requestId())
        enqueue(Write(detail.id, send = { api.start(it, detail.id, input) }))
    }
    /** Arms (or cancels) the server-side start that fires when the last pending invitation is accepted. */
    fun autoStart(enabled: Boolean) {
        val detail = state.detail?.takeIf { it.phase == PodPhase.SETUP } ?: return
        if (state.pending > 0 || state.uncertain || state.setup != null || state.sourcePreview != null) return
        if (enabled && detail.pendingInvitations.isEmpty()) return
        val input = SetPodAutoStart(requestId(), enabled)
        enqueue(Write(detail.id, send = { api.autoStart(it, detail.id, input) }))
    }
    fun pick(keep: List<String>, burn: List<String>, librarians: List<String> = emptyList()) {
        val detail = state.detail?.takeIf { it.phase == PodPhase.DRAFTING } ?: return
        val pack = detail.draft?.head ?: return
        val attempt = detail.draftAttemptId ?: return
        val owned = detail.draft!!.pool.filter { it.librarian }.map { it.id }
        if (librarians.distinct().size != librarians.size || librarians.any { it !in owned }) return
        if (state.uncertain || !PodPickRules(pack, detail.settings, librarians)
                .canConfirm(PodPickMarks(keeps = keep, burns = burn, librarians = librarians), false)) return
        val input = PickPod(requestId(), attempt, pack.id, keep.toList(), burn.toList(), librarians.toList())
        // The pick supersedes any unsent marks for this pack; a marks write already in flight is refused by the server.
        val key = PodMarksKey(detail.id, attempt, pack.id, pack.pick)
        synchronized(marksLock) { pickedPacks += key; if (marksPending?.key == key) marksPending = null }
        enqueue(Write(detail.id, attempt, pick = true, draftPick = input, send = { api.pick(it, detail.id, input) }))
    }
    private fun marksKey(detail: PodDetail?, pack: DraftPrivatePack?): PodMarksKey? {
        val attempt = detail?.draftAttemptId ?: return null
        return pack?.let { PodMarksKey(detail.id, attempt, it.id, it.pick) }
    }
    /** The marks shown for [pack] in the open pod. */
    internal fun marks(pack: DraftPrivatePack?): PodPickMarks =
        marksKey(state.detail, pack)?.let { pickMarks[it] } ?: PodPickMarks(pack?.id)
    /** Local intent for the head pack; saved to the server ~[PodMarksDelayMillis] after the last change, latest wins. */
    internal fun mark(pack: DraftPrivatePack?, marks: PodPickMarks) {
        val detail = state.detail ?: return
        if (pack == null || detail.draft?.head?.id != pack.id) return
        val key = marksKey(detail, pack) ?: return
        val value = marks.copy(packId = pack.id)
        if (pickMarks[key] == value) return
        pickMarks[key] = value
        val scheduler = marksScheduler ?: return
        val token = credential() ?: return
        synchronized(marksLock) {
            if (key in pickedPacks) return
            marksPending = MarksWrite(key, value, token)
        }
        scheduleMarks(scheduler, PodMarksDelayMillis)
    }
    private fun scheduleMarks(scheduler: PodMarksScheduler, delay: Long) {
        marksCancel?.invoke()
        marksCancel = scheduler.schedule(delay, ::sendMarks)
    }
    /** Runs on the scheduler thread; never touches Compose state directly. */
    private fun sendMarks() {
        val write = synchronized(marksLock) { marksPending.also { marksPending = null } } ?: return
        val input = SetPodDraftMarks(write.key.attempt, write.key.pack, write.marks.keeps, write.marks.burns, write.marks.librarians)
        try { runBlocking { api.marks(write.token, write.key.pod, input) } }
        catch (failure: Exception) {
            // Marks are a convenience: a refusal (pack already picked, attempt over) is dropped; a lost connection retries a little.
            if (!transientServiceFailure(failure) || write.retries >= 3) return
            post {
                if (closed) return@post
                val scheduler = marksScheduler ?: return@post
                val retry = synchronized(marksLock) {
                    if (marksPending != null || write.key in pickedPacks) false
                    else { marksPending = write.copy(retries = write.retries + 1); true }
                }
                if (retry) scheduleMarks(scheduler, PodMarksDelayMillis * 10)
            }
        }
    }
    /** Keeps local marks only for the pod's current packs and adopts saved head marks when there are none locally. */
    private fun syncMarks(detail: PodDetail) {
        val draft = detail.draft
        val current = (listOfNotNull(draft?.head) + draft?.queuedPacks.orEmpty()).mapNotNull { marksKey(detail, it) }.toSet()
        pickMarks.keys.filter { it.pod == detail.id && it !in current }.forEach { pickMarks.remove(it) }
        val head = draft?.head ?: return
        val key = marksKey(detail, head) ?: return
        synchronized(marksLock) { pickedPacks.removeAll { it.pod == detail.id && it !in current }; if (key in pickedPacks) return }
        val saved = draft.marks?.takeIf { it.packId == head.id } ?: return
        if (key in pickMarks) return
        val owned = draft.pool.filter { it.librarian }.map { it.id }.toSet()
        val librarians = saved.librarians.filter { it in owned }
        // Spent Librarians are cards of the pack for this pick, so their keep/burn marks come back with them.
        val cards = head.cards.map { it.id }.toSet() + librarians
        pickMarks[key] = PodPickMarks(head.id, saved.keeps.filter { it in cards }, saved.burns.filter { it in cards }, librarians)
    }
    private fun notesKey(detail: PodDetail?): PodNotesKey? =
        detail?.takeIf { it.phase != PodPhase.SETUP }?.draftAttemptId?.let { PodNotesKey(detail.id, it) }
    /** The open pod's notes for its current attempt; null when there is no attempt to keep notes for. */
    internal fun notes(): PodDeckNotes? = notesKey(state.detail)?.let { deckNotes[it] ?: PodDeckNotes() }
    /** Relative, like basics taps; saved ~[PodMarksDelayMillis] after the last change, also after submitting. */
    internal fun editNotes(change: (PodDeckNotes) -> PodDeckNotes) {
        val key = notesKey(state.detail) ?: return
        val current = deckNotes[key] ?: PodDeckNotes()
        val next = change(current).podNotesSanitized()
        if (next == current || !next.valid) return
        deckNotes[key] = next
        val scheduler = marksScheduler ?: return
        val token = credential() ?: return
        synchronized(marksLock) { notesPending = NotesWrite(key, next, token) }
        scheduleNotes(scheduler, PodMarksDelayMillis)
    }
    private fun scheduleNotes(scheduler: PodMarksScheduler, delay: Long) {
        notesCancel?.invoke()
        notesCancel = scheduler.schedule(delay, ::sendNotes)
    }
    private fun sendNotes() {
        val write = synchronized(marksLock) { notesPending.also { notesPending = null } } ?: return
        try { runBlocking { api.notes(write.token, write.key.pod, SetPodDeckNotes(write.key.attempt, write.notes)) } }
        catch (failure: Exception) {
            if (!transientServiceFailure(failure) || write.retries >= 3) return
            post {
                if (closed) return@post
                val scheduler = marksScheduler ?: return@post
                val retry = synchronized(marksLock) {
                    if (notesPending != null) false else { notesPending = write.copy(retries = write.retries + 1); true }
                }
                if (retry) scheduleNotes(scheduler, PodMarksDelayMillis * 10)
            }
        }
    }
    private fun syncNotes(detail: PodDetail) {
        val key = notesKey(detail)
        deckNotes.keys.filter { it.pod == detail.id && it != key }.forEach { deckNotes.remove(it) }
        if (key == null || key in deckNotes) return
        detail.notes?.let { deckNotes[key] = it.podNotesSanitized() }
    }
    fun gridSelect(gridId: String, line: GridLineType, index: Int) {
        val detail = state.detail?.takeIf { it.phase == PodPhase.DRAFTING && it.settings.mode == DraftMode.GRID } ?: return
        val grid = detail.grid ?: return
        val board = grid.publicView.grids.firstOrNull { it.id == gridId } ?: return
        val attempt = detail.draftAttemptId ?: return
        if (state.picking || state.uncertain || !PodGridRules(board, grid.mine).canSelect(GridLine(line, index))) return
        val input = SelectPodGrid(requestId(), attempt, gridId, line, index)
        enqueue(Write(detail.id, attempt, pick = true, send = { api.gridSelect(it, detail.id, input) }))
    }
    fun move(copyId: String, category: PodDeckCategory) {
        val detail = editable() ?: return
        val from = detail.build?.copies?.firstOrNull { it.copyId == copyId }?.category ?: return
        if (from != category) record(detail, PodBuildStep.Move(copyId, from, category))
        sendMove(detail, copyId, category)
    }
    /** The review feed's own Undo: moves the copy back and drops that move from the Build undo stack. */
    fun revertMove(copyId: String, category: PodDeckCategory) {
        val detail = editable() ?: return
        if (detail.build?.copies?.none { it.copyId == copyId } != false) return
        val steps = buildSteps(detail)
        val index = steps.indexOfLast { it is PodBuildStep.Move && it.copyId == copyId && it.from == category }
        if (index >= 0) buildHistory = buildHistory?.copy(steps = steps.filterIndexed { i, _ -> i != index })
        sendMove(detail, copyId, category)
    }
    private fun sendMove(detail: PodDetail, copyId: String, category: PodDeckCategory) {
        val input = MovePodCopy(requestId(), detail.draftAttemptId!!, copyId, category)
        enqueue(Write(detail.id, input.draftAttemptId, predict = { build -> build.copy(copies = build.copies.map {
            if (it.copyId == copyId) it.copy(category = category) else it
        }) }, send = { api.move(it, detail.id, input) }))
    }
    fun basics(basics: PodDeckBasics, side: Boolean = false) {
        val detail = editable() ?: return
        if (!basics.valid) return
        detail.build?.let { if (side) it.sideBasics else it.basics }?.takeIf { it != basics }?.let {
            record(detail, PodBuildStep.Basics(it, side))
        }
        sendBasics(detail, basics, side)
    }
    private fun sendBasics(detail: PodDetail, basics: PodDeckBasics, side: Boolean) {
        val input = SetPodBasics(requestId(), detail.draftAttemptId!!, basics, side)
        enqueue(Write(detail.id, input.draftAttemptId, predict = { if (side) it.copy(sideBasics = basics) else it.copy(basics = basics) },
            send = { api.basics(it, detail.id, input) }))
    }
    private fun buildSteps(detail: PodDetail?): List<PodBuildStep> =
        buildHistory?.takeIf { detail != null && it.pod == detail.id && it.attempt == detail.draftAttemptId }?.steps.orEmpty()
    private fun record(detail: PodDetail, step: PodBuildStep) {
        buildHistory = PodBuildHistory(detail.id, detail.draftAttemptId!!, (buildSteps(detail) + step).takeLast(PodBuildUndoLimit))
    }
    val canUndoBuild: Boolean get() = editable()?.let { buildSteps(it).isNotEmpty() } == true
    /** Sends the inverse of the latest Build change as an ordinary edit. Returns the moved copy and its destination. */
    fun undoBuild(): Pair<String, PodDeckCategory>? {
        val detail = editable() ?: return null
        val steps = buildSteps(detail)
        val step = steps.lastOrNull() ?: return null
        buildHistory = buildHistory?.copy(steps = steps.dropLast(1))
        return when (step) {
            is PodBuildStep.Move -> { sendMove(detail, step.copyId, step.from); step.copyId to step.from }
            is PodBuildStep.Basics -> { sendBasics(detail, step.previous, step.side); null }
        }
    }
    /** Relative so taps faster than recomposition each count against the latest predicted build. */
    fun adjustBasic(index: Int, delta: Int, side: Boolean = false) {
        val current = state.detail?.build?.let { if (side) it.sideBasics else it.basics } ?: return
        basics(current.podBuildAdjust(index, delta), side)
    }
    fun deckName(name: String) {
        val detail = state.detail?.takeIf { it.draftAttemptId != null && it.phase != PodPhase.SETUP } ?: return
        if (name.isBlank() || name.length > 120 || !DeckValidation.text(name) || name.any { it.isISOControl() }) return
        val input = SetPodDeckName(requestId(), detail.draftAttemptId!!, name)
        enqueue(Write(detail.id, input.draftAttemptId, deckName = name, send = { api.deckName(it, detail.id, input) }))
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
        if ((state.pending > 0 && action != PodConfirmation.SUBMIT) || state.uncertain) return
        val input = PodRequest(requestId())
        val submit = detail.draftAttemptId?.let { PodDraftRequest(input.requestId, it) }
        if (action in listOf(PodConfirmation.SUBMIT, PodConfirmation.CANCEL) && submit == null) return
        if (action == PodConfirmation.START && (state.setup != null || state.sourcePreview != null || !podCanStart(detail))) return
        enqueue(Write(detail.id, attempt = detail.draftAttemptId, submit = action == PodConfirmation.SUBMIT,
            exit = action in listOf(PodConfirmation.LEAVE, PodConfirmation.DESTROY), send = { token ->
            when (action) {
                PodConfirmation.DESTROY -> { api.destroy(token, detail.id, input); null }
                PodConfirmation.LEAVE -> { api.leave(token, detail.id, input); null }
                PodConfirmation.CANCEL -> api.cancel(token, detail.id, submit!!)
                PodConfirmation.SUBMIT -> api.submit(token, detail.id, submit!!)
                PodConfirmation.START -> api.start(token, detail.id, input)
            }
        }))
        if (action == PodConfirmation.SUBMIT) { buildHistory = null; state = state.copy(tab = PodTab.GAMES) }
    }
    private fun requestId() = UUID.randomUUID().toString()

    private fun withLibrarian(settings: DraftSettings): DraftSettings =
        if (settings.librarians == 0 || settings.librarianDefinition != null) settings
        else settings.copy(librarianDefinition = librarianDefinition())
    private fun enqueue(write: Write) {
        syncCredential()
        if (closed || state.uncertain || credential() == null) return
        writes += write; state = state.copy(error = null); render(); pump()
    }
    private fun render() {
        var detail = accepted?.takeIf { it.id == state.selected }
        for (write in writes) {
            val input = write.draftPick ?: continue
            val base = detail?.takeIf { it.id == write.pod && it.draftAttemptId == write.attempt } ?: continue
            val draft = base.draft ?: continue
            val pack = draft.head?.takeIf { it.id == input.packId } ?: continue
            // A spent Librarian kept back from the pack returns to the pool.
            val kept = pack.withSpentLibrarians(input.librarians, draft.pool).cards.filter { it.id in input.keep }
            val pool = draft.pool.filterNot { it.id in input.librarians } + kept
            detail = base.copy(
                draft = draft.copy(head = draft.queuedPacks.firstOrNull(), queuedPacks = draft.queuedPacks.drop(1),
                    packsBehind = (draft.packsBehind - 1).coerceAtLeast(0), pool = pool),
                build = base.build?.let { PodDeckRules.withDraftPool(it, pool.map { copy -> copy.id }) },
                members = base.members.map { if (it.account.accountId == draft.player)
                    it.copy(packsWaiting = (it.packsWaiting - 1).coerceAtLeast(0), picksMade = it.picksMade + 1) else it })
        }
        if (detail != null && detail.build != null && !detail.submitted) {
            val base = detail
            val predicted = writes.filter { it.pod == base.id && it.attempt == base.draftAttemptId }
                .fold(base.build!!) { build, write -> write.predict?.invoke(build) ?: build }
            detail = base.copy(build = predicted)
        }
        val edit = state.setup
        if (detail?.phase == PodPhase.SETUP && edit != null) {
            val source = edit.setSource?.podSource() ?: state.cubes.find { it.id == edit.cubeId }
                ?.let { PodSource(it.name, it.totalCopies) } ?: detail.source
            val needed = edit.settings.supply(detail.members.size, emptyList()).needed
            detail = detail.copy(name = edit.name, settings = edit.settings, source = source,
                supply = DraftSupply(needed, if (source.kind == PodSourceKind.SET) Long.MAX_VALUE else source.totalCopies))
        }
        detail?.let { base ->
            val pending = writes.filter { it.pod == base.id && it.attempt == base.draftAttemptId }
            detail = base.copy(deckName = pending.lastOrNull { it.deckName != null }?.deckName ?: base.deckName,
                submitted = base.submitted || pending.any { it.submit })
        }
        state = state.copy(detail = detail, pending = writes.size, picking = writes.any { it.pick })
    }
    private fun applyDetail(detail: PodDetail) {
        if (state.selected != detail.id) return
        val changed = accepted?.phase != detail.phase || accepted?.submitted != detail.submitted
        accepted = detail
        syncMarks(detail)
        syncNotes(detail)
        if (buildHistory?.pod == detail.id && (detail.submitted || buildHistory?.attempt != detail.draftAttemptId)) buildHistory = null
        if (changed) state = state.copy(tab = podDefaultTab(detail.phase, detail.submitted))
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
        val requested = read && refreshRequested
        if (read) { refreshPending = false; refreshRequested = false }
        cubesPending = false
        val generation = epoch; val selected = state.selected
        // Nothing shown yet: ride out a short outage on the loading indicator instead of failing fast.
        val firstLoad = read && (if (selected != null) state.detail == null else !state.loaded)
        val readDelays = if (firstLoad) firstLoadRetryDelays else quickRetryDelays
        val previousError = state.error
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
                        try { detail = serviceRetry(wait, ::current, readDelays) { api.detail(token, selected) } }
                        catch (failure: PodFailure) { if (failure.code != "not_found") throw failure }
                    }
                    val list = if (read || write != null) serviceRetry(wait, ::current, readDelays) { api.list(token) } else null
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
                    quietFailures.succeeded()
                    // A queued refresh must not erase a newer source-preparation failure.
                    state = state.copy(loading = false, unreachable = false, error = state.error.takeUnless { it == previousError })
                    if (write != null) {
                        writes.removeAt(0)
                        if (write.setupRevision == state.setup?.revision) state = state.copy(setup = null)
                        state = state.copy(confirmation = null)
                        if (write.exit) { accepted = null; state = state.copy(selected = null, detail = null, setup = null) }
                        if (write.select && result.first != null) {
                            // Creation leaves the form live too. Carry edits made in flight into setup.
                            val newer = write.createdFrom?.let { sent -> state.creating?.takeIf { it != sent } }
                            state = state.copy(selected = result.first!!.id, creating = null, setup = newer?.let {
                                PodSetupEdit(it.name, it.settings,
                                    cubeId = it.cubeId.takeIf { id -> id != write.createdFrom?.cubeId },
                                    setSource = it.setSource.takeIf { source -> source != write.createdFrom?.setSource },
                                    revision = ++editRevision)
                            })
                        }
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
                    if (write == null && !requested && !firstLoad && transientServiceFailure(failure)) {
                        // An automatic refresh of shown data: keep it and let the next poll retry.
                        state = state.copy(unreachable = quietFailures.failed())
                        if (readCubes) cubesPending = true
                        render()
                        return@post
                    }
                    val uncertain = write != null && (transientServiceFailure(failure) || (failure as? PodFailure)?.code == "invalid_response")
                    if (write != null && !uncertain) {
                        writes.removeAt(0)
                        // Later queued picks depend on this head having been accepted. Restore the
                        // authoritative queue instead of sending them out of order after a rejection.
                        if (write.draftPick != null) {
                            writes.removeAll { it.draftPick != null && it.pod == write.pod && it.attempt == write.attempt }
                            // The head comes back; its marks may be saved again.
                            synchronized(marksLock) { pickedPacks.removeAll { it.pod == write.pod && it.attempt == write.attempt } }
                        }
                        if (write.setupRevision != null) {
                            // Later full-settings snapshots depend on this rejected edit; drop them too.
                            writes.removeAll { it.pod == write.pod && it.setupRevision != null }
                            if (state.selected == write.pod) state = state.copy(setup = null)
                        }
                    }
                    if ((failure as? PodFailure)?.code in listOf("draft_attempt_expired", "not_drafting", "not_found", "deck_submitted")) {
                        writes.clear(); refreshPending = true
                    }
                    state = state.copy(loading = false, uncertain = uncertain,
                        error = if (write?.draftPick != null && !uncertain) "Pick rejected."
                            else if (write?.setupRevision != null && !uncertain) "Settings not saved." else podError(failure))
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
internal fun podMemberStatus(member: PodMember, phase: PodPhase, mode: DraftMode = DraftMode.BOOSTER): String = when {
    member.submitted -> "submitted"
    phase == PodPhase.SETUP -> "accepted"
    mode == DraftMode.GRID && phase == PodPhase.DRAFTING -> if (member.picking) "picking" else "waiting"
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
    PodConfirmation.START -> "Start the draft now with the ${detail.members.size} accepted members? " +
        "${podPendingCount(detail.pendingInvitations.size)} will be cancelled."
}
private fun podPendingCount(count: Int) = if (count == 1) "The pending invitation" else "The $count pending invitations"
/** The creator's Start control while invitations are pending: arm, or show progress and cancel. */
internal fun podAutoStartLabel(detail: PodDetail): String =
    if (detail.autoStart) "Starts when ${detail.pendingInvitations.size} more accept · Cancel" else "Start when everyone accepts"
internal fun podAutoStartOffText(off: PodAutoStartOff): String = when (off.reason) {
    "declined" -> "${off.account?.username ?: "An invitee"} declined; auto-start is off"
    "not_enough_members" -> "Could not start automatically: not enough members. Auto-start is off"
    "invalid_settings" -> "Could not start automatically: the settings do not fit the members. Auto-start is off"
    "insufficient_supply" -> "Could not start automatically: the source does not have enough cards. Auto-start is off"
    "librarian_definition_unavailable", "invalid_definition" -> "Could not start automatically: Librarian unavailable. Auto-start is off"
    else -> "Could not start automatically (${off.reason}). Auto-start is off"
}
private fun podError(failure: Exception): String = when ((failure as? PodFailure)?.code) {
    "invalid_settings" -> "Invalid settings."
    "librarian_definition_unavailable" -> "Librarian unavailable."
    "insufficient_supply" -> "The source cube does not have enough cards."
    "main_too_small" -> "Main must contain at least 40 cards including basics."
    "sideboard_limit" -> "Move surplus sideboard cards to Trash before submitting."
    "not_found" -> "This pod is no longer available."
    else -> "Could not complete the pod request (${(failure as? PodFailure)?.code ?: "connection"}). Retry to check the outcome."
}
