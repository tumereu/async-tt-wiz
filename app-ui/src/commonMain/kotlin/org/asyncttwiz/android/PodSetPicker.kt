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
class StoredPodSets(private val repository: () -> CatalogRepository) : PodSetRepository {
    override fun sets() = repository().draftableSets()
}

/** A draft set chosen as a pod's source: the server builds its packs from its card catalog (DRF-169, section 3.7). */
data class PodSetChoice(val code: String, val name: String)

fun PodSetChoice.podSource() = PodSource(kind = PodSourceKind.SET, setCode = code, setName = name, packSets = listOf(code))

/** A set's support as a list's (LOB-115's icon and hold): its unsupported cards (DRF-161), from the card catalog's marks. */
fun DraftableSet.support(): CardListSupport =
    CardListSupport(CardDefinitions.parserVersion, unsupported, arcanum.engine.Engine.SUPPORT_VERSION)

/** Which packs of the pack grid (DRF-178) a set is being chosen for: one cell, a round's row or a pack's column. */
sealed interface PodPackTarget {
    /** The cell of round [row]'s pack [column] (zero-based), [index] in the grid read row by row. */
    data class Cell(val index: Int, val row: Int, val column: Int) : PodPackTarget
    /** Every pack of round [row] (a row label). */
    data class Row(val row: Int) : PodPackTarget
    /** Pack [column] of every round (a column label). */
    data class Column(val column: Int) : PodPackTarget
}

/** The packs [this] target names, as the set picker's title says them: "Round 2 · Pack 1", "Round 2", "Pack 1". */
fun PodPackTarget.label(settings: DraftSettings): String = when (this) {
    is PodPackTarget.Cell -> if (PodPackGrid.rows(settings)?.size == 1) podPackColumnLabel(column)
        else "${podPackRowLabel(settings, row)} · ${podPackColumnLabel(column)}"
    is PodPackTarget.Row -> podPackRowLabel(settings, row)
    is PodPackTarget.Column -> podPackColumnLabel(column)
}
/** A row label: "Round 2"; a grid of one row (Sealed, Winston, Winchester) reads "All packs". */
fun podPackRowLabel(settings: DraftSettings, row: Int) =
    if (PodPackGrid.rows(settings)?.size == 1) "All packs" else "Round ${row + 1}"
/** A column label: "Pack 1". */
fun podPackColumnLabel(column: Int) = "Pack ${column + 1}"

/** [packSets] with [target]'s packs set to [code] (DRF-178). */
fun PodPackTarget.apply(packSets: List<String>, settings: DraftSettings, code: String): List<String> = when (this) {
    is PodPackTarget.Cell -> PodPackGrid.setCell(packSets, settings, index, code)
    is PodPackTarget.Row -> PodPackGrid.setRow(packSets, settings, row, code)
    is PodPackTarget.Column -> PodPackGrid.setColumn(packSets, settings, column, code)
}

data class PodSetPickerState(
    val open: Boolean = false, val sets: List<DraftableSet> = emptyList(), val query: String = "",
    val loaded: Boolean = false, val busy: Boolean = false, val error: String? = null,
    /** The pack grid's packs the set is chosen for (DRF-178); null: the whole pod's source. */
    val target: PodPackTarget? = null,
) {
    /** A set's Automatum support (DRF-161), null when the card records don't list it. */
    fun support(code: String?): CardListSupport? = sets.firstOrNull { it.code == code }?.support()

    /** Set [code] of the card records, if they list it. */
    fun set(code: String): DraftableSet? = sets.firstOrNull { it.code == code }

    val filtered get() = sets.filter { set ->
        set.releaseDate <= LocalDate.now(AppClock.clock()).toString() && query.trim().let { it.isEmpty() || set.name.contains(it, true) ||
            set.code.contains(it, true) || set.releaseDate.contains(it, true) }
    }.sortedWith(compareByDescending<DraftableSet> { it.releaseDate }.thenBy { it.name }.thenBy { it.code })
}

/**
 * The Sets tab of a pod's source picker (DRF-088): the draft sets of the card records, newest first, each with its symbol
 * (DRF-176). A set Automatum can't play every card of (the card catalog's marks, section 3.9) is greyed and can't be
 * chosen; a chosen set goes to the pod by its code ([selected], with the pack grid's packs it was chosen for, DRF-178).
 */
class PodSetPickerController(private val repository: PodSetRepository?, private val worker: ExecutorService,
    private val post: (() -> Unit) -> Unit, private val selected: (PodSetChoice, PodPackTarget?) -> Unit) {
    var state by mutableStateOf(PodSetPickerState())
        private set
    private var generation = 0L

    /** Opens the list for the whole pod's source, or for [target]'s packs of the pack grid. */
    fun open(target: PodPackTarget? = null) {
        if (state.open) return
        AppLog.info("sets", "Set list opened for ${target ?: "the source"}")
        state = state.copy(open = true, query = "", error = null, target = target)
        load()
    }
    fun dismiss() { generation++; state = state.copy(open = false, busy = false, error = null, target = null) }
    fun clear() { dismiss(); state = PodSetPickerState() }
    fun query(value: String) { state = state.copy(query = value) }
    fun retry() { if (!state.busy) load() }

    /** Reads the sets (for a chosen source's warning sign and the pack grid's symbols too, DRF-161) when they aren't read yet. */
    fun loadSupport() { if (!state.loaded && !state.busy) load() }

    private fun load() {
        val source = repository ?: return
        val current = ++generation
        state = state.copy(busy = true, error = null)
        worker.execute {
            val sets = runCatching { source.sets() }
                .onSuccess { sets -> AppLog.info("sets", "${sets.size} draft sets, ${sets.count { it.automatum }} Automatum can play, " +
                    "${sets.count { it.keyrune.isNotEmpty() }} with a symbol code") }
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
        val target = state.target
        AppLog.info("sets", "$code (${set.name}) chosen for ${target ?: "the source"}")
        dismiss()
        selected(PodSetChoice(set.code, set.name), target)
    }

    /** A preset's set, by its code: chosen without showing the list, reported like a picked set. */
    fun load(code: String, name: String) {
        dismiss()
        selected(PodSetChoice(code, name), null)
    }
}
