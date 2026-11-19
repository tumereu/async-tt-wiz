package org.asyncttwiz.android

import androidx.compose.runtime.*
import org.asyncttwiz.appui.executeSuspending
import org.asyncttwiz.protocol.*
import org.asyncttwiz.appui.ExecutorService
import kotlin.concurrent.Volatile
import org.asyncttwiz.appui.randomUuid

/** A saved deck with its cards as the phone shows them ([definitions], by [SavedDeckEntry.card]), built from its card
 * records (owner 2026-11-10: card data from the server's catalog). */
data class DeckDetail(val saved: SavedDeck, val definitions: Map<String, CardDefinition>)
enum class DeckDialog { EDIT, RENAME, COVER, DELETE }
/** LOB-245, LOB-246: a deck category dialog over Manage decks' Decks tab. */
sealed interface CategoryDialog {
    /** A new category in [parent] (null: the top level). */
    data class Create(val parent: String?) : CategoryDialog
    data class Rename(val category: DeckCategory) : CategoryDialog
    data class Move(val category: DeckCategory) : CategoryDialog
    data class Delete(val category: DeckCategory) : CategoryDialog
}
/** [draftDecks]: kept automatically from submitted pod decks, newest first, read-only until saved as an ordinary deck. */
data class DeckLibraryState(val decks: List<DeckSummary> = emptyList(), val draftDecks: List<DeckSummary> = emptyList(), val selected: String? = null,
    val detail: DeckDetail? = null, val busy: Boolean = false, val error: String? = null, val loaded: Boolean = false,
    val dialog: DeckDialog? = null, val draftName: String = "", val draftCover: String? = null,
    val section: SavedDeckSection = SavedDeckSection.MAINBOARD, val mutating: Boolean = false,
    /** LOB-244: the account's deck categories, and the ones folded open on this device (personal UI). */
    val categories: List<DeckCategory> = emptyList(), val expanded: Set<String> = emptySet(),
    val categoryDialog: CategoryDialog? = null, val categoryName: String = "", val categoryBusy: Boolean = false)

/** Server-owned accepted collection. Dialogs/retries are personal volatile memory, never local authority. */
class DeckLibraryController(private val api: Decks, private val credential: () -> String?,
    private val worker: ExecutorService, private val post: (() -> Unit) -> Unit) : AutoCloseable {
    var state by mutableStateOf(DeckLibraryState())
        private set
    @Volatile private var epoch = 0L
    private var closed = false
    private data class Pending(val id: String, val update: UpdateDeck? = null, val delete: DeleteDeck? = null, val copy: CopyDeck? = null)
    private var pending: Pending? = null
    /** The phone's card records, which a saved deck's cards are built from (owner 2026-11-10). */
    var cardRecords: SavedCardRecords = SavedCardRecords.NONE
    fun clear() { epoch++; pending = null; state = DeckLibraryState() }
    fun back() {
        if (state.dialog != null) { dismiss(); return }
        epoch++; pending = null
        state = state.copy(selected = null, detail = null, busy = false, mutating = false, error = null)
        refresh()
    }
    fun refresh() = load(null)
    fun open(id: String) { pending = null; state = state.copy(dialog = null, section = SavedDeckSection.MAINBOARD); load(id) }
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
        if (!state.mutating && state.detail?.saved?.entries?.any { it.printingId == value } == true) {
            pending = null; state = state.copy(draftCover = value, error = null)
        }
    }
    fun dismiss() { pending = null; state = state.copy(dialog = null, error = null) }

    /** LOB-244: folds category [id] open or shut (personal, kept while the app runs). */
    fun toggleCategory(id: String) {
        state = state.copy(expanded = if (id in state.expanded) state.expanded - id else state.expanded + id)
    }
    /** LOB-245, LOB-246: opens a category dialog; a name dialog starts from the category's name. */
    fun categoryDialog(dialog: CategoryDialog) {
        if (state.categoryBusy) return
        categoryRequest = null
        state = state.copy(categoryDialog = dialog, error = null,
            categoryName = (dialog as? CategoryDialog.Rename)?.category?.name.orEmpty())
    }
    fun categoryName(value: String) { if (!state.categoryBusy) { categoryRequest = null; state = state.copy(categoryName = value) } }
    fun dismissCategory() { if (!state.categoryBusy) { categoryRequest = null; state = state.copy(categoryDialog = null) } }
    /** Creates or renames as the open dialog says (its name trimmed), or deletes its category. */
    fun confirmCategory() {
        val name = state.categoryName.trim()
        when (val dialog = state.categoryDialog ?: return) {
            is CategoryDialog.Create -> if (DeckCategories.name(name)) categoryWrite("create in ${dialog.parent ?: "the top level"}") { token, request ->
                api.createCategory(token, CreateDeckCategory(request, name, dialog.parent)) }
            is CategoryDialog.Rename -> if (DeckCategories.name(name)) categoryWrite("rename ${dialog.category.id}") { token, request ->
                api.updateCategory(token, dialog.category.id, UpdateDeckCategory(request, name = name)) }
            is CategoryDialog.Delete -> categoryWrite("delete ${dialog.category.id}") { token, request ->
                api.deleteCategory(token, dialog.category.id, DeleteDeckCategory(request)) }
            is CategoryDialog.Move -> Unit
        }
    }
    /** LOB-245: moves the open Move dialog's category into [parent] (null: the top level). */
    fun moveCategory(parent: String?) {
        val dialog = state.categoryDialog as? CategoryDialog.Move ?: return
        if (!DeckCategories.canMove(state.categories, dialog.category.id, parent)) return
        categoryWrite("move ${dialog.category.id} into ${parent ?: "the top level"}") { token, request ->
            api.updateCategory(token, dialog.category.id, UpdateDeckCategory(request, parent = CategoryChoice(parent))) }
    }
    /** The open dialog's request id, kept while the same change is retried (the server answers a repeat with its result). */
    private var categoryRequest: String? = null
    private fun categoryWrite(what: String, write: suspend (String, String) -> DeckCategoryList) {
        if (closed || state.categoryBusy) return
        val token = credential() ?: return
        val request = categoryRequest ?: randomUuid().also { categoryRequest = it }
        state = state.copy(categoryBusy = true, error = null)
        AppLog.info("decks", "Deck category $what asked")
        worker.executeSuspending {
            try {
                val list = serviceRetry(current = { !closed }) { write(token, request) }
                post { if (!closed) {
                    categoryRequest = null
                    state = state.copy(categories = list.categories, categoryBusy = false, categoryDialog = null)
                    // Decks moved with a deleted category: their categories come with the decks
                    refresh()
                } }
            } catch (failure: Exception) {
                AppLog.warn("decks", "Deck category $what failed", failure)
                post { if (!closed) state = state.copy(categoryBusy = false, error = deckError(failure)) }
            }
        }
    }

    private fun load(id: String?) {
        if (closed || state.mutating) return
        val token = credential() ?: return
        val generation = ++epoch
        state = state.copy(selected = id, detail = state.detail?.takeIf { it.saved.deck.id == id },
            busy = true, error = null)
        worker.executeSuspending {
            if (generation != epoch) return@executeSuspending
            try {
                val result = serviceRetry(current = { generation == epoch }) { if (id == null) null else detail(token, id, generation) }
                if (id != null && result == null) return@executeSuspending
                val decks = if (id == null) run { serviceRetry(current = { generation == epoch }) { collection(token, generation) } } ?: return@executeSuspending else null
                val drafts = if (id == null) run { serviceRetry(current = { generation == epoch }) { api.drafts(token).decks } } else null
                if (generation != epoch) return@executeSuspending
                post { if (!closed && generation == epoch) state = state.copy(detail = result,
                    categories = if (id == null) loadedCategories else state.categories,
                    decks = decks ?: state.decks.map { if (it.id == id && result != null) result.saved.deck else it },
                    draftDecks = drafts ?: state.draftDecks.map { if (it.id == id && result != null) result.saved.deck else it },
                    busy = false, loaded = state.loaded || id == null) }
            } catch (failure: Exception) {
                post { if (!closed && generation == epoch) {
                    if (id != null && (failure as? DeckFailure)?.code == "not_found") {
                        pending = null
                        state = state.copy(selected = null, detail = null, dialog = null, busy = false,
                            decks = state.decks.filterNot { it.id == id }, draftDecks = state.draftDecks.filterNot { it.id == id },
                            error = "This saved deck was deleted on another device.")
                    } else state = state.copy(busy = false, error = deckError(failure))
                } }
            }
        }
    }

    /** Copies the open draft deck into the ordinary decks, then opens the copy. */
    fun saveAsDeck() {
        if (closed || state.mutating || state.dialog != null) return
        val id = state.selected ?: return
        if (state.detail?.saved?.deck?.takeIf { it.id == id }?.draft == null) return
        if (pending?.copy == null || pending?.id != id) pending = Pending(id, copy = CopyDeck(randomUuid()))
        mutate()
    }

    fun confirm() {
        if (closed || state.mutating) return
        if (state.selected == null) return
        mutate()
    }

    private fun mutate() {
        val id = state.selected ?: return
        val token = credential() ?: return
        val operation = pending ?: when (state.dialog) {
            DeckDialog.RENAME -> {
                if (!DeckValidation.text(state.draftName) || state.draftName.isBlank()) {
                    state = state.copy(error = "Enter a deck name."); return
                }
                Pending(id, update = UpdateDeck(randomUuid(), name = state.draftName))
            }
            DeckDialog.COVER -> Pending(id, update = UpdateDeck(randomUuid(), cover = state.draftCover ?: return))
            DeckDialog.DELETE -> Pending(id, delete = DeleteDeck(randomUuid()))
            else -> return
        }
        pending = operation
        val generation = ++epoch
        state = state.copy(mutating = true, busy = false, error = null)
        worker.executeSuspending {
            if (generation != epoch) return@executeSuspending
            try {
                val copy = run { serviceRetry(current = { generation == epoch }) {
                    if (operation.delete != null) { api.delete(token, operation.id, operation.delete); null }
                    else if (operation.copy != null) api.copy(token, operation.id, operation.copy)
                    else { operation.update?.let { api.update(token, operation.id, it) }
                        ?: Faults.report("DeckLibraryController", "deck write without a change", mapOf("deck" to operation.id)); null }
                } }
                // The original retry outcome may precede another device's newer edit. Read current state,
                // never install an old outcome over that state or reapply the mutation locally.
                post { if (!closed && generation == epoch) {
                    pending = null
                    state = state.copy(mutating = false, dialog = null)
                    if (copy != null) {
                        state = state.copy(decks = state.decks.filterNot { it.id == copy.deck.id } + copy.deck)
                        open(copy.deck.id)
                    } else if (operation.delete != null) back() else retry()
                } }
            } catch (failure: Exception) {
                post { if (!closed && generation == epoch) state = state.copy(mutating = false, error = deckError(failure)) }
            }
        }
    }

    /** LOB-244: the categories the last whole collection read brought (its first page's). */
    private var loadedCategories: List<DeckCategory> = emptyList()
    private suspend fun collection(token: String, generation: Long): List<DeckSummary>? {
        val decks = linkedMapOf<String, DeckSummary>(); val cursors = mutableSetOf<String>(); var after: String? = null
        do {
            if (generation != epoch) return null
            val page = api.list(token, after); page.decks.forEach { decks[it.id] = it }
            if (after == null) loadedCategories = page.categories
            after = page.next
            if (after != null && !cursors.add(after)) throw DeckFailure("invalid_response")
        } while (after != null)
        return decks.values.toList()
    }
    /** Deck [id] with its cards built from the card records by printing (owner 2026-11-10). */
    private suspend fun detail(token: String, id: String, generation: Long): DeckDetail? {
        val saved = api.read(token, id)
        if (generation != epoch) return null
        val cards = saved.cards()
        return DeckDetail(saved, cardRecords.read(cards.mapNotNull { it.printingId }).definitions(cards))
    }
    override fun close() { closed = true; clear(); worker.execute { api.close() }; worker.shutdown() }
}
