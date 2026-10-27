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
    val name: String = "", val text: String = "", val includeSideboard: Boolean = false,
    val cobraInput: String = "", val provenance: String? = null, val result: DeckImportResult? = null,
    val dialog: CubeDialog? = null, val cobraTab: Boolean = true, val clipboardRead: Boolean = false,
    val saving: Boolean = false, val imported: Boolean = false)

/** Only accepted server data is collection state; editor, lookup and retry intent are volatile. */
class CubeController(private val api: Cubes, private val credential: () -> String?,
    private val resolve: (String, Boolean) -> DeckImportResult,
    private val resolveCobra: (ParsedDeck) -> DeckImportResult,
    private val worker: ExecutorService, private val post: (() -> Unit) -> Unit,
    private val fetchCobra: (String) -> ParsedDeck = ::fetchCubeCobra) : AutoCloseable {
    var state by mutableStateOf(CubeState())
        private set
    @Volatile private var epoch = 0L
    private var closed = false
    private var source: CubeEditorSource? = null
    private var cobraSource: ParsedDeck? = null
    private data class SavePlan(val definitions: SubmitDefinitions, val cube: CreateCube, val target: String?)
    private var pendingSave: SavePlan? = null
    private var pendingUpdate: UpdateCube? = null
    private var pendingDelete: DeleteCube? = null
    // Replace on logout so an in-flight old request cannot populate the new session's cache.
    @Volatile private var definitionCache = ConcurrentHashMap<String, CardDefinition>()
    fun clear() { epoch++; definitionCache = ConcurrentHashMap(); source = null; cobraSource = null; pendingSave = null; pendingUpdate = null; pendingDelete = null; state = CubeState() }
    fun dismissError() { state = state.copy(error = null) }
    fun back() {
        if (state.busy) { epoch++; state = state.copy(busy = false, saving = false) }
        if (state.dialog != null) { dismiss(); return }
        if (state.editing) { source = null; cobraSource = null; pendingSave = null; state = state.copy(editing = false, result = null, error = null); return }
        state = state.copy(selected = null, detail = null, error = null)
        refresh()
    }
    fun refresh() = load(null)
    fun open(id: String) = load(id)
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
        source = null; cobraSource = null; pendingSave = null
        state = state.copy(editing = true, target = null, name = "", text = "", cobraInput = "",
            provenance = null, includeSideboard = false, result = null, error = null, cobraTab = true, clipboardRead = false,
            imported = false)
    }
    fun edit() {
        if (state.busy) return
        val detail = state.detail ?: return
        val retained = CubeEditorSource(detail); source = retained; cobraSource = null; pendingSave = null
        state = state.copy(editing = true, target = detail.saved.cube.id, name = detail.saved.cube.name,
            text = retained.text, includeSideboard = false, cobraInput = "", provenance = detail.saved.cube.cubeCobraId,
            result = null, error = null, cobraTab = false, clipboardRead = false, imported = false)
        lookup()
    }
    private fun editInput() {
        if (state.busy && !state.saving) { epoch++; state = state.copy(busy = false) }
        pendingSave = null
    }
    fun name(value: String) { editInput(); pendingUpdate = null; state = state.copy(name = value, error = null) }
    fun text(value: String) { editInput(); state = state.copy(text = value, result = null, error = null) }
    fun sideboard(value: Boolean) { editInput(); state = state.copy(includeSideboard = value, result = null) }
    fun cobraInput(value: String) { editInput(); state = state.copy(cobraInput = value, error = null) }
    fun cobraTab(value: Boolean) { state = state.copy(cobraTab = value, error = null) }
    fun prefillCobra(read: () -> String?) {
        if (state.clipboardRead || !state.editing || !state.cobraTab) return
        val match = ImportClipboard.cube(read())
        state = state.copy(clipboardRead = true, cobraInput = if (state.cobraInput.isBlank()) match.orEmpty() else state.cobraInput)
    }
    /** The Link field's paste icon: paste the clipboard and import it at once (typed text waits for Import). */
    fun pasteCobra(clip: String?) {
        if (!state.editing || state.saving) return
        val text = clip?.trim().orEmpty()
        if (text.isEmpty()) { state = state.copy(error = "Clipboard is empty."); return }
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
                val parsed = CubeImportResolver.parse(input.text, input.includeSideboard)
                val occurrences = mutableMapOf<Triple<String, String?, String?>, Int>()
                val lines = parsed.lines.map { line ->
                    val key = Triple(line.name, line.set, line.number)
                    val candidates = cobra.lines.filter { Triple(it.name, it.set, it.number) == key }
                    val occurrence = occurrences.getOrDefault(key, 0); occurrences[key] = occurrence + 1
                    val original = candidates.singleOrNull() ?: candidates.getOrNull(occurrence)
                    line.copy(scryfallId = original?.scryfallId)
                }
                resolveCobra(parsed.copy(lines = lines))
            } else retained?.resolve(input.text, input.includeSideboard, resolve) ?: resolve(input.text, input.includeSideboard)
            return@work { state = state.copy(result = result) }
        }
    }
    fun importCobra() {
        if (!state.editing || state.busy) return
        val id = try { CubeCobraImport.cubeId(state.cobraInput) } catch (_: Exception) {
            state = state.copy(error = "Enter a Cube Cobra URL or cube id."); return
        }
        work { _ ->
            val parsed = fetchCobra(id)
            val result = resolveCobra(parsed)
            return@work {
                source = null; cobraSource = parsed; pendingSave = null
                state = state.copy(text = parsed.text, name = state.name.ifBlank { id }, provenance = id,
                    includeSideboard = false, result = result, imported = true)
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
        work(onFailure = { pendingSave = attempted.takeIf { state.text == input.text && state.name == input.name && state.includeSideboard == input.includeSideboard } }) { current ->
            val plan = attempted ?: run {
                val definitions = linkedMapOf<String, SubmittedDefinition>()
                val entries = input.result!!.entries.map { entry ->
                    val definition = PhysicalCardFaces.normalize(retained[entry.printing.id] ?: entry.definition(input.result.catalog))
                    if (!DeckValidation.definition(definition)) throw DeckFailure("invalid_definition")
                    val digest = DeckJson.digest(definition)
                    definitions[digest] = SubmittedDefinition(digest, definition)
                    SavedCubeEntry(digest, entry.quantity)
                }.groupBy { it.definition }.map { (digest, rows) -> SavedCubeEntry(digest, rows.sumOf { it.quantity }) }
                if (!CubeValidation.entries(entries)) throw DeckFailure("invalid_cube")
                SavePlan(SubmitDefinitions(UUID.randomUUID().toString(), definitions.values.toList()),
                    CreateCube(UUID.randomUUID().toString(), input.name, entries, input.provenance), input.target)
            }
            attempted = plan
            val accepted = serviceRetry(current = current) { api.submit(token, plan.definitions) }
            if (accepted.digests.toSet() != plan.definitions.definitions.map { it.digest }.toSet()) throw DeckFailure("invalid_response")
            plan.definitions.definitions.forEach { cache[it.digest] = it.definition }
            val saved = serviceRetry(current = current) {
                if (plan.target == null) api.create(token, plan.cube) else api.update(token, plan.target,
                    UpdateCube(plan.cube.requestId, plan.cube.name, plan.cube.entries, plan.cube.cubeCobraId))
            }
            return@work {
                pendingSave = null
                if (state.text == input.text && state.name == input.name && state.includeSideboard == input.includeSideboard) {
                    source = null; cobraSource = null; state = state.copy(editing = false, result = null)
                    open(saved.cube.id)
                } else state = state.copy(target = saved.cube.id)
            }
        }
    }
    fun dialog(value: CubeDialog) {
        if (!state.busy && state.detail != null) {
            pendingUpdate = null; pendingDelete = null
            state = state.copy(dialog = value, name = state.detail!!.saved.cube.name, error = null)
        }
    }
    fun dismiss() { if (!state.busy) { pendingUpdate = null; pendingDelete = null; state = state.copy(dialog = null, error = null) } }
    fun confirm() {
        if (state.busy) return
        val id = state.selected ?: return
        val token = credential() ?: return
        val deleting = state.dialog == CubeDialog.DELETE
        if (!deleting && (state.dialog != CubeDialog.RENAME || !CubeValidation.name(state.name))) {
            state = state.copy(error = "Enter a cube name."); return
        }
        val delete = if (deleting) pendingDelete ?: DeleteCube(UUID.randomUUID().toString()).also { pendingDelete = it } else null
        val update = if (!deleting) pendingUpdate ?: UpdateCube(UUID.randomUUID().toString(), name = state.name).also { pendingUpdate = it } else null
        work { current ->
            serviceRetry(current = current) { if (delete != null) api.delete(token, id, delete) else api.update(token, id, update!!) }
            return@work {
                pendingUpdate = null; pendingDelete = null; state = state.copy(dialog = null)
                if (deleting) back() else open(id)
            }
        }
    }
    private fun work(onFailure: () -> Unit = {}, action: suspend (() -> Boolean) -> (() -> Unit)?) {
        if (closed || state.busy) return
        val generation = ++epoch
        val token = credential()
        state = state.copy(busy = true, error = null)
        worker.execute {
            if (generation != epoch) return@execute
            try {
                val apply = runBlocking { action { !closed && generation == epoch && credential() == token } }
                post { if (!closed && generation == epoch) {
                    // Credential expiry fences data, not completion. Keep ambiguous save intent for retry.
                    state = state.copy(busy = false, saving = false)
                    if (credential() == token) apply?.invoke() else onFailure()
                } }
            } catch (failure: Exception) {
                post { if (!closed && generation == epoch) {
                    onFailure()
                    state = state.copy(busy = false, saving = false)
                    if (credential() == token) state = state.copy(error = if (failure is CubeCobraFailure) failure.message else when ((failure as? DeckFailure)?.code) {
                        "invalid_cube" -> "Check the cube name and quantities (maximum 3000 copies)."
                        "not_found" -> "Cube is no longer available."
                        else -> "Cube request failed. Try again."
                    })
                } }
            }
        }
    }
    override fun close() { closed = true; clear(); worker.execute { api.close() }; worker.shutdown() }
}
