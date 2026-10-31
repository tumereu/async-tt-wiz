package org.asyncttwiz.android

import kotlinx.coroutines.runBlocking
import org.asyncttwiz.protocol.*
import java.util.concurrent.ExecutorService

data class DeckPickerState(val game: GameSummary, val decks: List<DeckSummary> = emptyList(),
    val draft: DeckSummary? = null, val loading: Boolean = true, val confirming: Boolean = false,
    val importing: Boolean = false, val error: String? = null, val loaded: Boolean = false)

/** LobbyController's deck picker: lists saved and draft decks over HTTP, keeps the highlighted deck and
 * a deck import started from it, and selects the confirmed deck for the game once its connection is
 * ready. Main thread; reads run on [worker]. */
internal class LobbyDeckPicker(private val scope: LobbyScope, private val deckApi: Decks?, private val credential: () -> String?,
    private val accountId: () -> String?, private val worker: ExecutorService, private val post: (() -> Unit) -> Unit,
    private val session: GameSession?, private val queue: LobbyActionQueue) {
    private var state: LobbyState
        get() = scope.state
        set(value) { scope.state = value }
    private val closed get() = scope.closed
    private val epoch get() = scope.epoch
    private var pickerVersion = 0L
    fun clear() { pickerVersion++ }
    /** The picker is personal and lists saved decks over HTTP, so it opens without the game connection. */
    fun chooseDeck() {
        val game = state.detail ?: return
        if (closed || deckApi == null || game.podId != null || state.preparation?.podRules != null || game.lifecycle != GameLifecycle.LOBBY ||
            (game.inviter.accountId != accountId() && game.invitationState != InvitationState.ACCEPTED)) return
        state = state.copy(picker = DeckPickerState(game))
        refreshPicker()
    }
    fun closePicker() { pickerVersion++; queue.dropQueued("deck"); state = state.copy(picker = null) }
    fun dismissPickerError() { state = state.copy(picker = state.picker?.copy(error = null)) }
    fun highlightDeck(deck: DeckSummary) {
        val picker = state.picker ?: return
        if (!picker.confirming && deck in picker.decks) state = state.copy(picker = picker.copy(draft = deck, error = null))
    }
    fun importForPicker() { state.picker?.takeIf { !it.confirming }?.let { state = state.copy(picker = it.copy(importing = true)) } }
    /** Back from an import started in the picker; a just-saved [saved] deck comes back highlighted, not confirmed. */
    fun importedForPicker(saved: DeckSummary? = null): Boolean {
        val picker = state.picker?.takeIf { it.importing } ?: return false
        state = state.copy(picker = picker.copy(importing = false, draft = saved ?: picker.draft,
            decks = if (saved == null) picker.decks else listOf(saved) + picker.decks.filter { it.id != saved.id }))
        refreshPicker(); return true
    }
    fun refreshPicker() {
        val picker = state.picker ?: return
        val api = deckApi ?: return
        val token = credential() ?: return
        if (picker.confirming) return
        val generation = epoch; val version = ++pickerVersion
        state = state.copy(picker = picker.copy(loading = true, error = null))
        worker.execute {
            val result = runCatching { runBlocking {
                val decks = linkedMapOf<String, DeckSummary>(); val seen = mutableSetOf<String>(); var next: String? = null
                do {
                    if (generation != epoch || version != pickerVersion) return@runBlocking emptyList()
                    val page = serviceRetry(current = { generation == epoch && version == pickerVersion }) { api.list(token, next) }; page.decks.forEach { decks[it.id] = it }; next = page.next
                    check(next == null || seen.add(next!!))
                } while (next != null)
                // Draft decks are playable too; the picker lists them under their own heading.
                if (generation != epoch || version != pickerVersion) return@runBlocking emptyList()
                serviceRetry(current = { generation == epoch && version == pickerVersion }) { api.drafts(token) }.decks.forEach { decks[it.id] = it }
                decks.values.toList()
            } }
            post { if (!closed && generation == epoch && version == pickerVersion) state.picker?.let {
                // A background collection read never silently substitutes or updates the captured draft.
                state = state.copy(picker = it.copy(decks = result.getOrDefault(it.decks), loading = false, loaded = it.loaded || result.isSuccess,
                    error = result.exceptionOrNull()?.let { deckError(it as? Exception ?: DeckFailure("network")) }))
            } }
        }
    }
    fun confirmDeck() {
        val picker = state.picker ?: return
        val source = picker.draft ?: return
        val api = deckApi ?: return
        val token = credential() ?: return
        val actor = accountId() ?: return
        if (closed || picker.confirming || picker.loading) return
        val generation = epoch; val version = ++pickerVersion
        state = state.copy(picker = picker.copy(confirming = true, error = null))
        worker.execute {
            val result = runCatching { runBlocking {
                if (generation != epoch || version != pickerVersion) return@runBlocking null
                val saved = serviceRetry(current = { generation == epoch && version == pickerVersion }) { api.read(token, source.id) }
                if (saved.deck.revision != source.revision) throw DeckFailure("source_changed")
                val definitions = saved.entries.map { it.definition }.distinct().map { digest ->
                    if (generation != epoch || version != pickerVersion) return@runBlocking null
                    serviceRetry(current = { generation == epoch && version == pickerVersion }) { api.definition(token, source.id, digest) }
                }
                saved to definitions
            } }
            fun current() = !closed && generation == epoch && version == pickerVersion && state.picker != null
            fun failed(failure: Throwable?) = state.picker?.let { current ->
                state = state.copy(picker = current.copy(confirming = false, error = when ((failure as? DeckFailure)?.code) {
                    "source_changed" -> "This saved deck changed. Refresh and choose it again."
                    "not_found" -> "This saved deck was deleted. Refresh and choose another deck."
                    null -> "This game changed or is disconnected. Reconnect and choose again."
                    else -> deckError(failure as? Exception ?: DeckFailure("network"))
                }))
            }
            post { if (current()) {
                val data = result.getOrNull()
                // The deck read needs no game connection; only the selection waits for it (still "Loading deck…").
                if (data == null) failed(result.exceptionOrNull())
                else queue.whenReady(picker.game.id, "deck") {
                    if (current()) {
                        if (state.detail?.preparationRound == picker.game.preparationRound &&
                            session?.selectDeck(picker.game, actor, data.first, data.second) == true) closePicker()
                        else failed(null)
                    }
                }
            } }
        }
    }
}
