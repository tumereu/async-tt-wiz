package org.asyncttwiz.android

import androidx.compose.runtime.*
import kotlinx.coroutines.runBlocking
import org.asyncttwiz.protocol.*
import java.util.UUID
import java.util.concurrent.ExecutorService

data class DeckDetail(val saved: SavedDeck, val definitions: Map<String, CardDefinition>)
enum class DeckDialog { EDIT, RENAME, COVER, DELETE }
data class DeckLibraryState(val decks: List<DeckSummary> = emptyList(), val selected: String? = null,
    val detail: DeckDetail? = null, val busy: Boolean = false, val error: String? = null, val loaded: Boolean = false,
    val dialog: DeckDialog? = null, val draftName: String = "", val draftCover: String? = null,
    val section: SavedDeckSection = SavedDeckSection.MAINBOARD, val mutating: Boolean = false)

/** Server-owned accepted collection. Dialogs/retries are personal volatile memory, never local authority. */
class DeckLibraryController(private val api: Decks, private val credential: () -> String?,
    private val worker: ExecutorService, private val post: (() -> Unit) -> Unit) : AutoCloseable {
    var state by mutableStateOf(DeckLibraryState())
        private set
    @Volatile private var epoch = 0L
    private var closed = false
    private data class Pending(val id: String, val update: UpdateDeck? = null, val delete: DeleteDeck? = null)
    private var pending: Pending? = null
    val inspection = CardInspectionController()
    fun inspect(identity: String) {
        val detail = state.detail ?: return
        if (!closed && state.dialog == null && detail.saved.entries.any { it.definition == identity })
            detail.definitions[identity]?.let { inspection.open(InspectedCard.retained(identity, it)) }
    }
    fun clear() { epoch++; inspection.close(); pending = null; state = DeckLibraryState() }
    fun back() {
        if (inspection.state != null) { inspection.close(); return }
        if (state.dialog != null) { dismiss(); return }
        epoch++; pending = null
        state = state.copy(selected = null, detail = null, busy = false, mutating = false, error = null)
        refresh()
    }
    fun refresh() = load(null)
    fun open(id: String) { inspection.close(); pending = null; state = state.copy(dialog = null, section = SavedDeckSection.MAINBOARD); load(id) }
    fun retry() = load(state.selected)
    fun dismissError() { state = state.copy(error = null) }
    fun section(value: SavedDeckSection) { state = state.copy(section = value) }
    fun edit(dialog: DeckDialog) {
        if (state.mutating) return
        val saved = state.detail?.saved ?: return
        pending = null
        state = state.copy(dialog = dialog, draftName = saved.deck.name, draftCover = saved.deck.cover?.definition, error = null)
    }
    fun name(value: String) { if (!state.mutating) { pending = null; state = state.copy(draftName = value, error = null) } }
    fun cover(value: String) {
        if (!state.mutating && state.detail?.saved?.entries?.any { it.definition == value } == true) {
            pending = null; state = state.copy(draftCover = value, error = null)
        }
    }
    fun dismiss() { pending = null; state = state.copy(dialog = null, error = null) }

    private fun load(id: String?) {
        if (closed || state.mutating) return
        val token = credential() ?: return
        val generation = ++epoch
        val known = state.detail?.takeIf { it.saved.deck.id == id }?.definitions.orEmpty()
        state = state.copy(selected = id, detail = state.detail?.takeIf { it.saved.deck.id == id },
            busy = true, error = null)
        worker.execute {
            if (generation != epoch) return@execute
            try {
                val result = runBlocking { serviceRetry(current = { generation == epoch }) { if (id == null) null else detail(token, id, known, generation) } }
                if (id != null && result == null) return@execute
                val decks = if (id == null) runBlocking { serviceRetry(current = { generation == epoch }) { collection(token, generation) } } ?: return@execute else null
                post { if (!closed && generation == epoch) state = state.copy(detail = result,
                    decks = decks ?: state.decks.map { if (it.id == id) result!!.saved.deck else it }, busy = false, loaded = state.loaded || id == null) }
            } catch (failure: Exception) {
                post { if (!closed && generation == epoch) {
                    if (id != null && (failure as? DeckFailure)?.code == "not_found") {
                        inspection.close(); pending = null
                        state = state.copy(selected = null, detail = null, dialog = null, busy = false,
                            decks = state.decks.filterNot { it.id == id }, error = "This saved deck was deleted on another device.")
                    } else state = state.copy(busy = false, error = deckError(failure))
                } }
            }
        }
    }

    fun confirm() {
        if (closed || state.mutating) return
        val id = state.selected ?: return
        val token = credential() ?: return
        val operation = pending ?: when (state.dialog) {
            DeckDialog.RENAME -> {
                if (!DeckValidation.text(state.draftName) || state.draftName.isBlank()) {
                    state = state.copy(error = "Enter a deck name."); return
                }
                Pending(id, update = UpdateDeck(UUID.randomUUID().toString(), name = state.draftName))
            }
            DeckDialog.COVER -> Pending(id, update = UpdateDeck(UUID.randomUUID().toString(), cover = state.draftCover ?: return))
            DeckDialog.DELETE -> Pending(id, delete = DeleteDeck(UUID.randomUUID().toString()))
            else -> return
        }
        pending = operation
        val generation = ++epoch
        state = state.copy(mutating = true, busy = false, error = null)
        worker.execute {
            if (generation != epoch) return@execute
            try {
                runBlocking { serviceRetry(current = { generation == epoch }) {
                    if (operation.delete != null) api.delete(token, operation.id, operation.delete)
                    else api.update(token, operation.id, operation.update!!)
                } }
                // The original retry outcome may precede another device's newer edit. Read current state,
                // never install an old outcome over that state or reapply the mutation locally.
                post { if (!closed && generation == epoch) {
                    pending = null
                    state = state.copy(mutating = false, dialog = null)
                    if (operation.delete != null) back() else retry()
                } }
            } catch (failure: Exception) {
                post { if (!closed && generation == epoch) state = state.copy(mutating = false, error = deckError(failure)) }
            }
        }
    }

    private suspend fun collection(token: String, generation: Long): List<DeckSummary>? {
        val decks = linkedMapOf<String, DeckSummary>(); val cursors = mutableSetOf<String>(); var after: String? = null
        do {
            if (generation != epoch) return null
            val page = api.list(token, after); page.decks.forEach { decks[it.id] = it }; after = page.next
            if (after != null && !cursors.add(after)) throw DeckFailure("invalid_response")
        } while (after != null)
        return decks.values.toList()
    }
    private suspend fun detail(token: String, id: String, known: Map<String, CardDefinition>, generation: Long): DeckDetail? {
        val saved = api.read(token, id)
        val definitions = saved.entries.map { it.definition }.distinct().associateWith {
            if (generation != epoch) return null
            known[it] ?: api.definition(token, id, it).definition
        }
        return DeckDetail(saved, definitions)
    }
    override fun close() { closed = true; clear(); worker.execute { api.close() }; worker.shutdown() }
}
