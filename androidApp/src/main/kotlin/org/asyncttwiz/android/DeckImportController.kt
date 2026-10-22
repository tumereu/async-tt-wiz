package org.asyncttwiz.android

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.asyncttwiz.storage.DeckImportResult
import org.asyncttwiz.storage.ImportIssue
import java.util.concurrent.ExecutorService

data class DeckImportState(val active: Boolean = false, val text: String = "", val busy: Boolean = false,
    val result: DeckImportResult? = null, val preview: Boolean = false, val name: String = "")

/** Personal volatile draft, retained by the Activity ViewModel across rotation, never a saved deck. */
class DeckImportController(private val resolve: (String) -> DeckImportResult,
    private val worker: ExecutorService, private val post: (() -> Unit) -> Unit) : AutoCloseable {
    var state by mutableStateOf(DeckImportState())
        private set
    private var epoch = 0L
    private var closed = false
    fun open() { if (!closed) { epoch++; state = DeckImportState(active = true) } }
    fun exit() { epoch++; state = DeckImportState() }
    fun edit(text: String) {
        if (!closed && state.active) { epoch++; state = state.copy(text = text, busy = false, result = null, preview = false) }
    }
    fun lookup() {
        if (closed || !state.active || state.busy || state.preview) return
        val generation = ++epoch
        val text = state.text
        state = state.copy(busy = true, result = null)
        worker.execute {
            val result = try { resolve(text) } catch (_: Exception) {
                DeckImportResult(text, emptyList(), listOf(ImportIssue(0,
                    "Card records could not be read. Check storage or update card records, then retry lookup.")), null)
            }
            post { if (!closed && generation == epoch && state.active)
                state = state.copy(busy = false, result = result) }
        }
    }
    fun review() { if (state.result?.resolved == true && !state.busy) state = state.copy(preview = true) }
    fun editList() { state = state.copy(preview = false) }
    fun name(value: String) { state = state.copy(name = value) }
    fun back() { if (state.preview) editList() else exit() }
    override fun close() { closed = true; exit(); worker.shutdownNow() }
}
