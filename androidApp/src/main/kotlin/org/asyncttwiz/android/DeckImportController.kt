package org.asyncttwiz.android

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.asyncttwiz.storage.DeckImportResult
import org.asyncttwiz.storage.ImportIssue
import org.asyncttwiz.protocol.SavedDeck
import org.asyncttwiz.protocol.DeckSummary
import org.asyncttwiz.protocol.DeckImportMode
import java.util.concurrent.ExecutorService

data class DeckImportState(val active: Boolean = false, val text: String = "", val busy: Boolean = false,
    val result: DeckImportResult? = null, val preview: Boolean = false, val name: String = "",
    val saving: Boolean = false, val error: String? = null, val target: String? = null,
    val mode: DeckImportMode = DeckImportMode.OVERWRITE)

/** Personal volatile draft, retained by the Activity ViewModel across rotation, never a saved deck. */
class DeckImportController(private val resolve: (String) -> DeckImportResult,
    private val worker: ExecutorService, private val post: (() -> Unit) -> Unit) : AutoCloseable {
    var state by mutableStateOf(DeckImportState())
        private set
    @Volatile private var epoch = 0L
    private var closed = false
    private var retryPlan: DeckSavePlan? = null
    private var saveAction: (() -> ((DeckSavePlan) -> SavedDeck)?)? = null
    private var saved: (SavedDeck) -> Unit = {}
    val inspection = CardInspectionController()
    fun inspect(entry: org.asyncttwiz.storage.ResolvedDeckEntry) {
        val result = state.result ?: return
        if (!closed && state.preview && entry in result.entries) inspection.open(InspectedCard.preview(entry, result.catalog))
    }
    fun connectSave(action: () -> ((DeckSavePlan) -> SavedDeck)?, saved: (SavedDeck) -> Unit) {
        saveAction = action; this.saved = saved
    }
    fun open() { if (!closed) { epoch++; inspection.close(); retryPlan = null; state = DeckImportState(active = true) } }
    fun openExisting(deck: DeckSummary) {
        open()
        if (!closed) state = state.copy(target = deck.id, name = deck.name)
    }
    fun mode(value: DeckImportMode) {
        if (!closed && state.target != null && !state.saving && state.mode != value) {
            retryPlan = null; state = state.copy(mode = value, error = null)
        }
    }
    fun exit() { epoch++; inspection.close(); retryPlan = null; state = DeckImportState() }
    fun edit(text: String) {
        if (!closed && state.active && !state.saving) { epoch++; retryPlan = null
            state = state.copy(text = text, busy = false, result = null, preview = false, error = null) }
    }
    fun lookup() {
        if (closed || !state.active || state.busy || state.preview) return
        val generation = ++epoch
        val text = state.text
        state = state.copy(busy = true, result = null)
        worker.execute {
            if (generation != epoch) return@execute
            val result = try { resolve(text) } catch (_: Exception) {
                DeckImportResult(text, emptyList(), listOf(ImportIssue(0,
                    "Card records could not be read. Check storage or update card records, then retry lookup.")), null)
            }
            post { if (!closed && generation == epoch && state.active)
                state = state.copy(busy = false, result = result) }
        }
    }
    fun review() { if (state.result?.resolved == true && !state.busy) state = state.copy(preview = true) }
    fun editList() { if (!state.saving) { inspection.close(); state = state.copy(preview = false, error = null) } }
    fun name(value: String) { if (!state.saving && value != state.name) {
        retryPlan = null; state = state.copy(name = value, error = null)
    } }
    fun save() {
        if (closed || !state.active || !state.preview || state.saving) return
        val input = state
        if (input.name.isBlank()) { state = state.copy(error = "Enter a deck name."); return }
        val action = saveAction?.invoke() ?: run {
            state = state.copy(error = "Sign in to save this deck."); return
        }
        val generation = ++epoch
        val previous = retryPlan
        state = state.copy(saving = true, error = null)
        worker.execute {
            if (generation != epoch) return@execute
            var plan = previous
            try {
                 if (plan == null) plan = DeckSavePlan.from(checkNotNull(input.result), input.name, input.target, input.mode)
                val result = action(checkNotNull(plan))
                post { if (!closed && generation == epoch && state.active) { exit(); saved(result) } }
            } catch (failure: Exception) {
                val retained = plan
                post { if (!closed && generation == epoch && state.active) {
                    retryPlan = retained; state = state.copy(saving = false, error = deckError(failure))
                } }
            }
        }
    }
    fun back() { if (inspection.state != null) inspection.close() else if (state.preview) editList() else exit() }
    override fun close() { closed = true; exit(); worker.shutdownNow() }
}
