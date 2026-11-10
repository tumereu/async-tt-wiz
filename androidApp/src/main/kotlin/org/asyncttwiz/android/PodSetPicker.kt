package org.asyncttwiz.android

import androidx.compose.runtime.*
import org.asyncttwiz.protocol.*
import org.asyncttwiz.storage.*
import java.time.LocalDate
import java.util.concurrent.ExecutorService

/** The draft sets of the card records with their support marks (section 3.8). Blocking: the picker's worker only. */
fun interface PodSetRepository {
    fun sets(): List<DraftableSet>
}

/** The device's card records as the pickers' set list. */
internal class StoredPodSets(private val repository: () -> CatalogRepository) : PodSetRepository {
    override fun sets() = repository().draftableSets()
}

/** A draft set chosen as a pod's source: the server builds its packs from its card catalog (DRF-169, section 3.7). */
data class PodSetChoice(val code: String, val name: String)

internal fun PodSetChoice.podSource() = PodSource(kind = PodSourceKind.SET, setCode = code, setName = name)

/** A set's support as a list's (LOB-115's icon and hold): its unsupported cards (DRF-161), from the card catalog's marks. */
internal fun DraftableSet.support(): CardListSupport =
    CardListSupport(CardDefinitions.parserVersion, unsupported, arcanum.engine.Engine.SUPPORT_VERSION)

internal data class PodSetPickerState(
    val open: Boolean = false, val sets: List<DraftableSet> = emptyList(), val query: String = "",
    val loaded: Boolean = false, val busy: Boolean = false, val error: String? = null,
) {
    /** A set's Automatum support (DRF-161), null when the card records don't list it. */
    fun support(code: String?): CardListSupport? = sets.firstOrNull { it.code == code }?.support()

    val filtered get() = sets.filter { set ->
        set.releaseDate <= LocalDate.now(AppClock.clock()).toString() && query.trim().let { it.isEmpty() || set.name.contains(it, true) ||
            set.code.contains(it, true) || set.releaseDate.contains(it, true) }
    }.sortedWith(compareByDescending<DraftableSet> { it.releaseDate }.thenBy { it.name }.thenBy { it.code })
}

/**
 * The Sets tab of a pod's source picker (DRF-088): the draft sets of the card records, newest first. A set Automatum
 * can't play every card of (the card catalog's marks, section 3.9) is greyed and can't be chosen; a chosen set goes to
 * the pod by its code ([selected]).
 */
internal class PodSetPickerController(private val repository: PodSetRepository?, private val worker: ExecutorService,
    private val post: (() -> Unit) -> Unit, private val selected: (PodSetChoice) -> Unit) {
    var state by mutableStateOf(PodSetPickerState())
        private set
    private var generation = 0L

    fun open() {
        if (state.open) return
        state = state.copy(open = true, query = "", error = null)
        load()
    }
    fun dismiss() { generation++; state = state.copy(open = false, busy = false, error = null) }
    fun clear() { dismiss(); state = PodSetPickerState() }
    fun query(value: String) { state = state.copy(query = value) }
    fun retry() { if (!state.busy) load() }

    /** Reads the sets (for a chosen source's warning sign too, DRF-161) when they aren't read yet. */
    fun loadSupport() { if (!state.loaded && !state.busy) load() }

    private fun load() {
        val source = repository ?: return
        val current = ++generation
        state = state.copy(busy = true, error = null)
        worker.execute {
            val sets = runCatching { source.sets() }
                .onSuccess { sets -> AppLog.info("sets", "${sets.size} draft sets, ${sets.count { it.automatum }} Automatum can play") }
                .onFailure { AppLog.warn("sets", "Reading the draft sets failed", it) }
            post {
                if (current != generation) return@post
                state = sets.fold({ state.copy(sets = it, loaded = true, busy = false) },
                    { state.copy(busy = false, error = "Could not load the sets.") })
            }
        }
    }

    /** Chooses set [code] when Automatum can play every card of it; an unsupported set can't be chosen (section 3.9). */
    fun choose(code: String) {
        val set = state.sets.firstOrNull { it.code == code } ?: return
        if (!set.automatum) { AppLog.info("sets", "$code not chosen: Automatum can't play ${set.unsupported.size} of its cards"); return }
        AppLog.info("sets", "$code (${set.name}) chosen as the source")
        dismiss()
        selected(PodSetChoice(set.code, set.name))
    }

    /** A preset's set, by its code: chosen without showing the list, reported like a picked set. */
    fun load(code: String, name: String) {
        dismiss()
        selected(PodSetChoice(code, name))
    }
}
