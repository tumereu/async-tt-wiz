package org.asyncttwiz.android

import androidx.compose.runtime.*
import org.asyncttwiz.protocol.*
import org.asyncttwiz.storage.*
import java.time.LocalDate
import java.util.concurrent.ExecutorService

/** Blocking methods: the picker calls these only on its worker. */
interface PodSetRepository {
    fun sets(): List<DraftableSet>
    fun isCached(code: String): Boolean
    fun load(code: String): SetSourceUpload
    fun redownload(code: String): SetSourceUpload
}

internal class StoredPodSets(factory: () -> SetSourceRepository) : PodSetRepository {
    // Repository construction also touches the filesystem; defer it to the worker.
    private val repository by lazy(factory)
    override fun sets() = repository.sets()
    override fun isCached(code: String) = repository.isCached(code)
    override fun load(code: String) = repository.load(code)
    override fun redownload(code: String) = repository.redownload(code)
}

internal data class PodSetRow(val set: DraftableSet, val cached: Boolean)
internal data class PodSetPickerState(
    val open: Boolean = false, val rows: List<PodSetRow> = emptyList(), val query: String = "",
    val loaded: Boolean = false, val busy: Boolean = false, val status: String? = null,
    val error: String? = null, val warnings: List<String> = emptyList(),
) {
    val filtered get() = rows.filter { row ->
        row.set.draftable && row.set.releaseDate <= LocalDate.now().toString() && query.trim().let { it.isEmpty() || row.set.name.contains(it, true) ||
            row.set.code.contains(it, true) || row.set.releaseDate.contains(it, true) }
    }.sortedWith(compareByDescending<PodSetRow> { it.set.releaseDate }.thenBy { it.set.name }.thenBy { it.set.code })
}

internal class PodSetPickerController(private val repository: PodSetRepository?, private val worker: ExecutorService,
    private val post: (() -> Unit) -> Unit, private val choosing: (DraftableSet) -> Unit = {},
    private val failed: () -> Unit = {}, private val selected: (SetSourceUpload) -> Unit) {
    var state by mutableStateOf(PodSetPickerState())
        private set
    private var generation = 0L
    private var retryAction: (() -> Unit)? = null

    fun open() {
        if (state.open) return
        generation++
        state = state.copy(open = true, busy = false, query = "", error = null, warnings = emptyList())
        list()
    }
    fun dismiss() { generation++; retryAction = null; state = state.copy(open = false, busy = false, status = null, error = null) }
    fun clear() { dismiss(); state = PodSetPickerState() }
    fun query(value: String) { state = state.copy(query = value) }
    fun retry() { if (!state.busy) retryAction?.invoke() }
    private fun list() = work("Loading installed sets…", ::list, action = {
        checkNotNull(repository).sets().map { PodSetRow(it, repository.isCached(it.code)) }
    }) { state = state.copy(rows = it, loaded = true) }

    fun choose(code: String) = acquire(code, false)
    fun redownload(code: String) = acquire(code, true)
    private fun acquire(code: String, force: Boolean) {
        val set = state.rows.firstOrNull { it.set.code == code && it.set.draftable }?.set ?: return
        if (!state.open || force && state.busy) return
        if (!force) {
            // Displayed rows remain selectable during refresh. Fence its queued result before choosing.
            generation++
            state = state.copy(busy = false)
        }
        work(if (force) "Redownloading $code and preparing card definitions…" else "Loading $code: downloading if needed and preparing card definitions…",
            { acquire(code, force) }, detached = !force, action = {
                val source = checkNotNull(repository)
                if (force) source.redownload(code) else source.load(code)
            }) { upload ->
            state = state.copy(rows = state.rows.map { if (it.set.code == code) it.copy(cached = true) else it }, warnings = upload.warnings)
            if (!force) { dismiss(); selected(upload) }
        }
        if (!force) { state = state.copy(open = false); choosing(set) }
    }

    private fun <T> work(status: String, retry: () -> Unit, detached: Boolean = false, action: () -> T, apply: (T) -> Unit) {
        if (!state.open || state.busy) return
        val current = generation
        retryAction = retry
        state = state.copy(busy = true, status = status, error = null, warnings = emptyList())
        worker.execute {
            try {
                val result = action()
                post { if (current == generation && (state.open || detached)) {
                    retryAction = null
                    state = state.copy(busy = false, status = null, error = null)
                    apply(result)
                } }
            } catch (_: Exception) {
                post { if (current == generation && (state.open || detached)) {
                    state = state.copy(busy = false, status = null, error = "Could not load source.")
                    if (detached) failed()
                } }
            }
        }
    }
}

internal fun PodSetSource.podSource() = PodSource(kind = PodSourceKind.SET, setCode = snapshot.setCode,
    setName = snapshot.setName, boosterKind = if (snapshot.recipe == null) SetBoosterKind.APPROXIMATE else SetBoosterKind.RECIPE_BASED,
    boosterName = snapshot.recipe?.name)
