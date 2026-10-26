package org.asyncttwiz.android

import androidx.compose.runtime.*
import org.asyncttwiz.protocol.*
import org.asyncttwiz.storage.*
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
        query.trim().let { it.isEmpty() || row.set.name.contains(it, true) ||
            row.set.code.contains(it, true) || row.set.releaseDate.contains(it, true) }
    }.sortedWith(compareByDescending<PodSetRow> { it.set.releaseDate }.thenBy { it.set.name }.thenBy { it.set.code })
}

internal class PodSetPickerController(private val repository: PodSetRepository?, private val worker: ExecutorService,
    private val post: (() -> Unit) -> Unit, private val selected: (SetSourceUpload) -> Unit) {
    var state by mutableStateOf(PodSetPickerState())
        private set
    private var generation = 0L
    private var retryAction: (() -> Unit)? = null

    fun open() {
        if (state.open) return
        state = state.copy(open = true, query = "", error = null, warnings = emptyList())
        list()
    }
    fun dismiss() { generation++; retryAction = null; state = state.copy(open = false, busy = false, status = null, error = null) }
    fun clear() { dismiss(); state = PodSetPickerState() }
    fun query(value: String) { state = state.copy(query = value) }
    fun retry() { if (!state.busy) retryAction?.invoke() }
    private fun list() = work("Loading installed sets…", ::list, {
        checkNotNull(repository).sets().map { PodSetRow(it, repository.isCached(it.code)) }
    }) { state = state.copy(rows = it, loaded = true) }

    fun choose(code: String) = acquire(code, false)
    fun redownload(code: String) = acquire(code, true)
    private fun acquire(code: String, force: Boolean) {
        if (state.rows.none { it.set.code == code }) return
        work(if (force) "Redownloading $code and preparing card definitions…" else "Loading $code: downloading if needed and preparing card definitions…",
            { acquire(code, force) }, {
                val source = checkNotNull(repository)
                if (force) source.redownload(code) else source.load(code)
            }) { upload ->
            state = state.copy(rows = state.rows.map { if (it.set.code == code) it.copy(cached = true) else it }, warnings = upload.warnings)
            if (!force) { dismiss(); selected(upload) }
        }
    }

    private fun <T> work(status: String, retry: () -> Unit, action: () -> T, apply: (T) -> Unit) {
        if (!state.open || state.busy) return
        val current = generation
        retryAction = retry
        state = state.copy(busy = true, status = status, error = null, warnings = emptyList())
        worker.execute {
            try {
                val result = action()
                post { if (current == generation && state.open) {
                    retryAction = null
                    state = state.copy(busy = false, status = null, error = null)
                    apply(result)
                } }
            } catch (_: Exception) {
                post { if (current == generation && state.open) state = state.copy(busy = false, status = null,
                    error = "Could not load the set source. Check your connection and installed card records, then Retry.") }
            }
        }
    }
}

internal fun PodSetSource.podSource() = PodSource(kind = PodSourceKind.SET, setCode = snapshot.setCode,
    setName = snapshot.setName, boosterKind = if (snapshot.recipe == null) SetBoosterKind.APPROXIMATE else SetBoosterKind.RECIPE_BASED,
    boosterName = snapshot.recipe?.name)

internal fun podSourceLabel(source: PodSource): String = if (source.kind == PodSourceKind.CUBE)
    "${source.cubeName} · ${source.totalCopies} copies"
else "${source.setName ?: source.setCode} (${source.setCode}) · ${podBoosterLabel(source)}"

internal fun podBoosterLabel(source: PodSource): String = when {
    source.boosterKind == SetBoosterKind.APPROXIMATE -> "Approximate packs"
    source.boosterName == "play" -> "Play booster"
    source.boosterName == "draft" -> "Draft booster"
    else -> "Recipe-based boosters"
}

internal fun podSupplyLabel(source: PodSource, needed: Long): String = if (source.kind == PodSourceKind.SET)
    "Unlimited (set boosters)" else "$needed / ${source.totalCopies} cards used"
