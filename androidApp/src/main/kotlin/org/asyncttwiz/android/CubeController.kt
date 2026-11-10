package org.asyncttwiz.android

import androidx.compose.runtime.*
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.runBlocking
import org.asyncttwiz.protocol.*
import org.asyncttwiz.storage.*

data class CubeDetail(val saved: SavedCube, val definitions: Map<String, CardDefinition>)
enum class CubeDialog { RENAME, DELETE }
data class CubeState(val cubes: List<CubeSummary> = emptyList(), val loaded: Boolean = false,
    val selected: String? = null, val detail: CubeDetail? = null, val busy: Boolean = false,
    val error: String? = null, val editing: Boolean = false, val target: String? = null,
    val name: String = "", val text: String = "",
    val cobraInput: String = "", val provenance: String? = null, val result: DeckImportResult? = null,
    val dialog: CubeDialog? = null, val linkDialog: Boolean = false, val clipboardRead: Boolean = false,
    val saving: Boolean = false, val fetching: Boolean = false, val linkError: String? = null,
    val destination: DeckSection = DeckSection.MAINBOARD, val discard: Boolean = false)

/** A cube has Main and Maybe only. */
internal fun ResolvedDeckEntry.cubeSection() =
    if (section == DeckSection.MAINBOARD) SavedDeckSection.MAINBOARD else SavedDeckSection.MAYBEBOARD

/** Only accepted server data is collection state; editor, lookup and retry intent are volatile. */
class CubeController(private val api: Cubes, private val credential: () -> String?,
    private val resolve: (String) -> DeckImportResult,
    private val resolveCobra: (ParsedDeck) -> DeckImportResult,
    private val worker: ExecutorService, private val post: (() -> Unit) -> Unit,
    private val fetchCobra: (String) -> ParsedDeck = ::fetchCubeCobra) : AutoCloseable, CardListEditor {
    var state by mutableStateOf(CubeState())
        private set
    /** The editor's catalog search page (a section header's +). */
    val search = CardListSearch(worker, post)
    /** Name and text the editor opened with, to ask before discarding changes. */
    private var initial: Pair<String, String> = "" to ""
    override val kind get() = CardListKind.CUBE
    override val entries: List<ResolvedDeckEntry>? get() = state.result?.takeIf { it.issues.isEmpty() }?.entries
        ?: if (state.text.isBlank() && !state.busy) emptyList() else null
    override val listCatalog get() = state.result?.catalog
    override val editable get() = state.editing && !state.busy
    override val destination get() = state.destination
    override fun destination(section: DeckSection) { if (section in kind.sections) state = state.copy(destination = section) }
    override fun retainedDefinition(printingId: String) = source?.definitions?.get(printingId)
    override val cardsError get() = state.error
    override fun cardsMessage(message: String?) { state = state.copy(error = message) }
    /** A Cards tab or search edit: the list is replaced and its text rewritten, already resolved, so no lookup runs. */
    override fun applyCards(entries: List<ResolvedDeckEntry>, catalog: CatalogMetadata?) {
        if (closed || !editable) return
        undo.push(state.text to state.result)
        editInput()
        val text = CardListEdits.text(kind, entries)
        state = state.copy(text = text, result = DeckImportResult(text, entries, emptyList(), catalog ?: state.result?.catalog), error = null)
    }
    /** The text and list before each Cards tab or search change. */
    private val undo = CardListUndo<Pair<String, DeckImportResult?>>()
    override val canUndo get() = undo.steps.isNotEmpty()
    override fun undoCards() {
        if (closed || !editable) return
        val (text, result) = undo.pop() ?: return
        editInput()
        state = state.copy(text = text, result = result, error = null)
    }
    /** Leaving the editor: asks "Discard unsaved changes?" when the name or list changed. */
    fun cancelEdit() {
        if (state.name != initial.first || state.text != initial.second) state = state.copy(discard = true) else discardEdit()
    }
    fun keepEditing() { state = state.copy(discard = false) }
    fun discardEdit() {
        if (state.busy) { epoch++; state = state.copy(busy = false, saving = false, fetching = false) }
        search.reset(); undo.clear(); source = null; cobraSource = null; pendingSave = null
        state = state.copy(editing = false, result = null, error = null, discard = false)
    }
    @Volatile private var epoch = 0L
    private var closed = false
    private var source: CubeEditorSource? = null
    private var cobraSource: ParsedDeck? = null
    /** One cube save, a single request (owner 2026-11-10: card data from the server's catalog), kept while uncertain so
     * a retry sends the same request; [cards]: its cards as the phone makes them (the server builds the same). */
    private data class CubeSave(val cube: CreateCube, val target: String?, val cards: List<SubmittedDefinition>)
    private var pendingSave: CubeSave? = null
    private var pendingUpdate: UpdateCube? = null
    private var pendingDelete: DeleteCube? = null
    // Replace on logout so an in-flight old request cannot populate the new session's cache.
    @Volatile private var definitionCache = ConcurrentHashMap<String, CardDefinition>()
    fun clear() { search.reset(); undo.clear(); epoch++; definitionCache = ConcurrentHashMap(); source = null; cobraSource = null; pendingSave = null; pendingUpdate = null; pendingDelete = null; state = CubeState() }
    fun dismissError() { state = state.copy(error = null) }
    fun back() {
        if (state.busy) { epoch++; state = state.copy(busy = false, saving = false, fetching = false) }
        if (state.dialog != null) { dismiss(); return }
        if (search.state.open) { search.close(); return }
        if (state.linkDialog) { closeLink(); return }
        if (state.discard) { keepEditing(); return }
        if (state.editing) { cancelEdit(); return }
        state = state.copy(selected = null, detail = null, error = null)
        refresh()
    }
    fun refresh() = load(null)
    fun open(id: String) { load(id) }
    fun retry() = load(state.selected)
    private fun load(id: String?) {
        if (closed || state.busy) return
        val token = credential() ?: return
        val cache = definitionCache
        state = state.copy(selected = id, detail = state.detail?.takeIf { it.saved.cube.id == id }, error = null)
        work { current ->
            if (id == null) {
                val cubes = linkedMapOf<String, CubeSummary>(); val cursors = mutableSetOf<String>(); var after: String? = null
                do {
                    val page = serviceRetry(current = current) { api.listCubes(token, after) }
                    page.cubes.forEach { cubes[it.id] = it }; after = page.next
                    if (after != null && !cursors.add(after)) throw DeckFailure("invalid_response")
                } while (after != null)
                return@work { state = state.copy(cubes = cubes.values.toList(), loaded = true) }
            } else {
                val saved = serviceRetry(current = current) { api.readCube(token, id) }
                val digests = saved.entries.map { it.definition }.distinct()
                for (batch in digests.filterNot(cache::containsKey).chunked(CubeValidation.MAX_DEFINITIONS_PER_READ)) {
                    val received = serviceRetry(current = current) { api.cubeDefinitions(token, id, ReadCubeDefinitions(batch)) }.definitions
                    if (received.size != batch.size || received.map { it.digest }.toSet() != batch.toSet()) throw DeckFailure("invalid_response")
                    received.forEach { cache[it.digest] = it.definition }
                }
                val definitions = digests.associateWith(cache::getValue)
                return@work { state = state.copy(detail = CubeDetail(saved, definitions)) }
            }
        }
    }
    fun newCube() {
        if (state.busy) return
        source = null; cobraSource = null; pendingSave = null; search.reset(); undo.clear()
        state = state.copy(editing = true, target = null, name = "", text = "", cobraInput = "",
            provenance = null, result = null, error = null, linkDialog = false, clipboardRead = false,
            linkError = null, destination = DeckSection.MAINBOARD, discard = false)
        initial = "" to ""
    }
    fun edit() {
        if (state.busy) return
        val detail = state.detail ?: return
        val retained = CubeEditorSource(detail); source = retained; cobraSource = null; pendingSave = null; search.reset(); undo.clear()
        state = state.copy(editing = true, target = detail.saved.cube.id, name = detail.saved.cube.name,
            text = retained.text, cobraInput = "", provenance = detail.saved.cube.cubeCobraId,
            result = null, error = null, linkDialog = false, clipboardRead = false, linkError = null,
            destination = DeckSection.MAINBOARD, discard = false)
        initial = state.name to state.text
        lookup()
    }
    private fun editInput() {
        if (state.busy && !state.saving) { epoch++; state = state.copy(busy = false, fetching = false) }
        pendingSave = null
    }
    fun name(value: String) { editInput(); pendingUpdate = null; state = state.copy(name = value, error = null) }
    fun text(value: String) { editInput(); undo.clear(); state = state.copy(text = value, result = null, error = null) }
    fun cobraInput(value: String) {
        if (state.fetching) { epoch++; state = state.copy(busy = false, fetching = false) }
        state = state.copy(cobraInput = value, linkError = null)
    }
    /** The top bar's "Import from link": a Cube Cobra dialog over the unchanged editor. */
    fun openLink() { if (state.editing && !state.saving) state = state.copy(linkDialog = true, linkError = null) }
    /** Cancel drops a pending fetch; the editor stays as it was. */
    fun closeLink() {
        if (state.fetching) { epoch++; state = state.copy(busy = false, fetching = false) }
        state = state.copy(linkDialog = false, linkError = null)
    }
    fun prefillCobra(read: () -> String?) {
        if (state.clipboardRead || !state.editing || !state.linkDialog) return
        val match = ImportClipboard.cube(read())
        state = state.copy(clipboardRead = true, cobraInput = if (state.cobraInput.isBlank()) match.orEmpty() else state.cobraInput)
    }
    /** The Link field's paste icon: paste the clipboard and import it at once (typed text waits for Import). */
    fun pasteCobra(clip: String?) {
        if (!state.editing || state.saving) return
        val text = clip?.trim().orEmpty()
        if (text.isEmpty()) { state = state.copy(linkError = "Clipboard is empty."); return }
        cobraInput(ImportClipboard.cube(text) ?: text)
        importCobra()
    }
    /** Something valid to save: a resolved list with a valid name, and no lookup, fetch or save in progress. */
    val canSave: Boolean get() = state.editing && state.result?.resolved == true && CubeValidation.name(state.name) && !state.busy
    fun lookup() {
        if (!state.editing || state.busy) return
        val input = state; val retained = source; val cobra = cobraSource
        work { _ ->
            val result = if (cobra != null) {
                val parsed = CubeImportResolver.parse(input.text)
                val occurrences = mutableMapOf<Triple<String, String?, String?>, Int>()
                val lines = parsed.lines.map { line ->
                    val key = Triple(line.name, line.set, line.number)
                    val candidates = cobra.lines.filter { Triple(it.name, it.set, it.number) == key }
                    val occurrence = occurrences.getOrDefault(key, 0); occurrences[key] = occurrence + 1
                    val original = candidates.singleOrNull() ?: candidates.getOrNull(occurrence)
                    line.copy(scryfallId = original?.scryfallId)
                }
                resolveCobra(parsed.copy(lines = lines))
            } else retained?.resolve(input.text, resolve) ?: resolve(input.text)
            return@work { state = state.copy(result = result) }
        }
    }
    fun importCobra() {
        if (!state.editing || state.busy) return
        val id = try { CubeCobraImport.cubeId(state.cobraInput) } catch (_: Exception) {
            state = state.copy(linkError = "Enter a Cube Cobra URL or cube id."); return
        }
        work(link = true) { _ ->
            val parsed = fetchCobra(id)
            val result = resolveCobra(parsed)
            return@work {
                source = null; cobraSource = parsed; pendingSave = null; undo.clear()
                // An ordinary editable paste list: the list is replaced and a typed name is kept.
                state = state.copy(text = parsed.text, name = state.name.ifBlank { id }, provenance = id,
                    result = result, linkDialog = false, linkError = null, error = null)
            }
        }
    }
    fun save() {
        if (state.busy || !state.editing || state.result?.resolved != true) return
        if (!CubeValidation.name(state.name)) { state = state.copy(error = "Enter a cube name."); return }
        val token = credential() ?: return
        val input = state; val retained = source?.definitions.orEmpty()
        val cache = definitionCache
        var attempted = pendingSave
        state = state.copy(saving = true)
        work(onFailure = { pendingSave = attempted.takeIf { state.text == input.text && state.name == input.name } }) { current ->
            val plan = attempted ?: run {
                val cards = linkedMapOf<String, SubmittedDefinition>()
                val copies = linkedMapOf<Pair<SavedDeckSection, String>, Int>()
                for (entry in input.result!!.entries) {
                    // A card the cube holds from before is named by its digest (its printing id here) and kept as stored
                    val kept = retained[entry.printing.id]
                    val definition = kept ?: PhysicalCardFaces.normalize(entry.definition(input.result.catalog))
                    val digest = if (kept != null) entry.printing.id else DeckJson.digest(definition)
                    cards[digest] = SubmittedDefinition(digest, definition)
                    copies.merge(entry.cubeSection() to entry.printing.id, entry.quantity, Int::plus)
                }
                val entries = copies.map { (key, quantity) -> CardListEntry(key.first, key.second, quantity) }
                if (!CubeValidation.input(entries)) throw DeckFailure("invalid_cube")
                CubeSave(CreateCube(UUID.randomUUID().toString(), input.name, entries, input.provenance), input.target, cards.values.toList())
            }
            attempted = plan
            AppLog.info("cubes", "save ${if (plan.target == null) "new cube" else "cube ${plan.target}"}: ${plan.cube.entries.size} entries, " +
                "${plan.cube.entries.count { DeckValidation.digest(it.printingId) }} kept from before, request ${plan.cube.requestId}")
            plan.cards.forEach { cache[it.digest] = it.definition }
            val saved = serviceRetry(current = current) {
                if (plan.target == null) api.create(token, plan.cube) else api.update(token, plan.target,
                    UpdateCube(plan.cube.requestId, plan.cube.name, plan.cube.entries, plan.cube.cubeCobraId))
            }
            return@work {
                pendingSave = null
                if (state.text == input.text && state.name == input.name) {
                    source = null; cobraSource = null; search.reset(); undo.clear(); state = state.copy(editing = false, result = null)
                    open(saved.cube.id)
                } else { initial = input.name to input.text; state = state.copy(target = saved.cube.id) }
            }
        }
    }
    fun dialog(value: CubeDialog) {
        if (!state.busy && state.detail != null) {
            pendingUpdate = null; pendingDelete = null
            state = state.copy(dialog = value, name = state.detail!!.saved.cube.name, error = null)
        }
    }
    /** LOB-057: the editor of a saved cube offers "Delete cube", confirmed first; the name field keeps what was typed. */
    val canDelete: Boolean get() = state.editing && state.target != null && !state.busy
    fun requestDelete() {
        if (canDelete) { pendingUpdate = null; pendingDelete = null; state = state.copy(dialog = CubeDialog.DELETE, error = null) }
    }
    fun dismiss() { if (!state.busy) { pendingUpdate = null; pendingDelete = null; state = state.copy(dialog = null, error = null) } }
    fun confirm() {
        if (state.busy) return
        val deleting = state.dialog == CubeDialog.DELETE
        val id = (if (deleting && state.editing) state.target else state.selected) ?: return
        val token = credential() ?: return
        if (!deleting && (state.dialog != CubeDialog.RENAME || !CubeValidation.name(state.name))) {
            state = state.copy(error = "Enter a cube name."); return
        }
        val delete = if (deleting) pendingDelete ?: DeleteCube(UUID.randomUUID().toString()).also { pendingDelete = it } else null
        val update = if (!deleting) pendingUpdate ?: UpdateCube(UUID.randomUUID().toString(), name = state.name).also { pendingUpdate = it } else null
        work { current ->
            serviceRetry(current = current) { if (delete != null) api.delete(token, id, delete) else api.update(token, id, update!!) }
            return@work {
                pendingUpdate = null; pendingDelete = null; state = state.copy(dialog = null)
                if (deleting) { if (state.editing) discardEdit(); back() } else open(id)
            }
        }
    }
    /** A [link] request is the link dialog's fetch: its progress and failure show in the dialog. */
    private fun work(onFailure: () -> Unit = {}, link: Boolean = false, action: suspend (() -> Boolean) -> (() -> Unit)?) {
        if (closed || state.busy) return
        val generation = ++epoch
        val token = credential()
        state = if (link) state.copy(busy = true, fetching = true, linkError = null) else state.copy(busy = true, error = null)
        worker.execute {
            if (generation != epoch) return@execute
            try {
                val apply = runBlocking { action { !closed && generation == epoch && credential() == token } }
                post { if (!closed && generation == epoch) {
                    // Credential expiry fences data, not completion. Keep ambiguous save intent for retry.
                    state = state.copy(busy = false, saving = false, fetching = false)
                    if (credential() == token) apply?.invoke() else onFailure()
                } }
            } catch (failure: Exception) {
                post { if (!closed && generation == epoch) {
                    onFailure()
                    state = state.copy(busy = false, saving = false, fetching = false)
                    val message = if (failure is CubeCobraFailure) failure.message else when ((failure as? DeckFailure)?.code) {
                        "invalid_cube" -> "Check the cube name and quantities (maximum 3000 copies)."
                        "not_found" -> "Cube is no longer available."
                        else -> "Cube request failed. Try again."
                    }
                    if (credential() == token) state = if (link) state.copy(linkError = message) else state.copy(error = message)
                } }
            }
        }
    }
    override fun close() { closed = true; clear(); worker.execute { api.close() }; worker.shutdown() }
}
