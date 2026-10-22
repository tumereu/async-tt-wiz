package org.asyncttwiz.android

import androidx.compose.runtime.*
import kotlinx.coroutines.runBlocking
import org.asyncttwiz.protocol.*
import java.util.concurrent.ExecutorService

data class DeckDetail(val saved: SavedDeck, val definitions: Map<String, CardDefinition>)
data class DeckLibraryState(val decks: List<DeckSummary> = emptyList(), val selected: String? = null,
    val detail: DeckDetail? = null, val busy: Boolean = false, val error: String? = null, val loaded: Boolean = false)

/** Only server reads become visible saved records. Private memory is fenced/cleared on Logout. */
class DeckLibraryController(private val api: Decks, private val credential: () -> String?,
    private val worker: ExecutorService, private val post: (() -> Unit) -> Unit) : AutoCloseable {
    var state by mutableStateOf(DeckLibraryState())
        private set
    @Volatile private var epoch = 0L
    private var closed = false
    fun clear() { epoch++; state = DeckLibraryState() }
    fun back() { epoch++; state = state.copy(selected = null, detail = null, busy = false, error = null); refresh() }
    fun refresh() = load(null)
    fun open(id: String) = load(id)
    fun retry() = load(state.selected)
    private fun load(id: String?) {
        if (closed) return
        val token = credential() ?: return
        val generation = ++epoch
        val summaries = state.decks
        state = state.copy(selected = id, detail = null, busy = true, error = null)
        worker.execute {
            if (generation != epoch) return@execute
            val next = try { runBlocking {
                if (id != null) {
                    val saved = api.read(token, id)
                    val definitions = saved.entries.map { it.definition }.distinct().associateWith {
                        api.definition(token, id, it).definition
                    }
                    DeckLibraryState(decks = summaries, selected = id, detail = DeckDetail(saved, definitions), loaded = true)
                } else {
                    val decks = mutableListOf<DeckSummary>(); val cursors = mutableSetOf<String>(); var after: String? = null
                    do {
                        val page = api.list(token, after); decks += page.decks; after = page.next
                        if (after != null && !cursors.add(after)) throw DeckFailure("invalid_response")
                    } while (after != null)
                    DeckLibraryState(decks = decks, loaded = true)
                }
            } } catch (failure: Exception) { DeckLibraryState(selected = id, error = deckError(failure)) }
            post { if (!closed && generation == epoch) state = next }
        }
    }
    override fun close() { closed = true; clear(); worker.execute { api.close() }; worker.shutdown() }
}
