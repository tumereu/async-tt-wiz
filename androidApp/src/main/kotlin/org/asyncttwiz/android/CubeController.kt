package org.asyncttwiz.android

import androidx.compose.runtime.*
import java.util.UUID
import java.util.concurrent.ExecutorService
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
    val dialog: CubeDialog? = null)

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
    fun clear() { epoch++; source = null; cobraSource = null; pendingSave = null; pendingUpdate = null; pendingDelete = null; state = CubeState() }
    fun dismissError() { state = state.copy(error = null) }
    fun back() {
        if (state.busy) return
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
                val definitions = saved.entries.map { it.definition }.distinct().associateWith { digest ->
                    serviceRetry(current = current) { api.cubeDefinition(token, id, digest) }.definition
                }
                return@work { state = state.copy(detail = CubeDetail(saved, definitions)) }
            }
        }
    }
    fun newCube() {
        if (state.busy) return
        source = null; cobraSource = null; pendingSave = null
        state = state.copy(editing = true, target = null, name = "", text = "", cobraInput = "",
            provenance = null, includeSideboard = false, result = null, error = null)
    }
    fun edit() {
        if (state.busy) return
        val detail = state.detail ?: return
        val retained = CubeEditorSource(detail); source = retained; cobraSource = null; pendingSave = null
        state = state.copy(editing = true, target = detail.saved.cube.id, name = detail.saved.cube.name,
            text = retained.text, includeSideboard = false, cobraInput = "", provenance = detail.saved.cube.cubeCobraId,
            result = null, error = null)
        lookup()
    }
    fun name(value: String) { if (!state.busy) { pendingSave = null; pendingUpdate = null; state = state.copy(name = value, error = null) } }
    fun text(value: String) { if (!state.busy) { pendingSave = null; state = state.copy(text = value, result = null, error = null) } }
    fun sideboard(value: Boolean) { if (!state.busy) { pendingSave = null; state = state.copy(includeSideboard = value, result = null) } }
    fun cobraInput(value: String) { if (!state.busy) state = state.copy(cobraInput = value, error = null) }
    fun lookup() {
        if (!state.editing || state.busy) return
        val input = state; val retained = source; val cobra = cobraSource
        work { _ ->
            val result = if (cobra != null) {
                val parsed = DeckTextParser.parse(input.text, CubeValidation.MAX_CARDS, input.includeSideboard)
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
                state = state.copy(text = parsed.text, provenance = id, includeSideboard = false, result = result)
            }
        }
    }
    fun save() {
        if (state.busy || !state.editing || state.result?.resolved != true) return
        if (!CubeValidation.name(state.name)) { state = state.copy(error = "Enter a cube name."); return }
        val token = credential() ?: return
        val input = state; val retained = source?.definitions.orEmpty()
        var attempted = pendingSave
        work(onFailure = { pendingSave = attempted }) { current ->
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
            val saved = serviceRetry(current = current) {
                if (plan.target == null) api.create(token, plan.cube) else api.update(token, plan.target,
                    UpdateCube(plan.cube.requestId, plan.cube.name, plan.cube.entries, plan.cube.cubeCobraId))
            }
            return@work {
                source = null; cobraSource = null; pendingSave = null; state = state.copy(editing = false, result = null)
                open(saved.cube.id)
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
                post { if (!closed && generation == epoch && credential() == token) {
                    state = state.copy(busy = false); apply?.invoke()
                } }
            } catch (failure: Exception) {
                post { if (!closed && generation == epoch && credential() == token) {
                    onFailure()
                    state = state.copy(busy = false,
                    error = when ((failure as? DeckFailure)?.code) {
                        "invalid_cube" -> "Check the cube name and quantities (maximum 3000 copies)."
                        "not_found" -> "This cube is no longer available. Return to your collection."
                        else -> "Could not complete the cube request. Your input is kept. Check card records or your connection and retry."
                    })
                } }
            }
        }
    }
    override fun close() { closed = true; clear(); worker.execute { api.close() }; worker.shutdown() }
}
