package org.asyncttwiz.android

import androidx.compose.runtime.*
import kotlinx.coroutines.runBlocking
import org.asyncttwiz.protocol.*
import java.util.UUID
import java.util.concurrent.ExecutorService

data class GameNameDraft(val recipient: AccountIdentity, val name: String)
data class LobbyState(val contacts: List<AccountIdentity> = emptyList(), val query: String = "",
    val results: List<AccountIdentity> = emptyList(), val searched: Boolean = false, val searching: Boolean = false,
    val searchError: String? = null, val games: List<GameSummary> = emptyList(), val invitations: List<GameSummary> = emptyList(),
    val selected: String? = null, val detail: GameSummary? = null, val draft: GameNameDraft? = null,
    val busy: Boolean = false, val mutating: Boolean = false, val uncertain: Boolean = false,
    val error: String? = null, val loaded: Boolean = false)

/** Accepted account data plus personal volatile input. Never an independently authoritative local game. */
class LobbyController(private val api: Lobbies, private val credential: () -> String?,
    private val worker: ExecutorService, private val post: (() -> Unit) -> Unit,
    private val openGame: () -> Unit = {}, private val suggest: () -> String = ::gameNameSuggestion) : AutoCloseable {
    var state by mutableStateOf(LobbyState())
        private set
    @Volatile private var epoch = 0L
    private var closed = false
    private var searchVersion = 0L
    private var pendingCreate: CreateInvitation? = null
    private var pendingRecipient: AccountIdentity? = null
    private val pendingAdds = mutableMapOf<String, AddContact>()
    fun clear() { epoch++; searchVersion++; pendingCreate = null; pendingRecipient = null; pendingAdds.clear(); state = LobbyState() }
    fun query(value: String) { searchVersion++; state = state.copy(query = value, results = emptyList(), searching = false, searched = false, searchError = null) }
    fun search() {
        if (closed) return
        val token = credential() ?: return
        if (state.query.isBlank()) { state = state.copy(searchError = "Enter a username."); return }
        val query = state.query; val generation = epoch; val version = ++searchVersion
        state = state.copy(searching = true, searchError = null)
        worker.execute {
            if (generation != epoch) return@execute
            val result = runCatching { runBlocking { api.search(token, query) } }
            post { if (!closed && generation == epoch && version == searchVersion) state = state.copy(searching = false,
                searched = true, results = result.getOrNull()?.users.orEmpty(), searchError = result.exceptionOrNull()?.let(::lobbyError)) }
        }
    }
    fun add(contact: AccountIdentity) {
        if (closed || state.mutating || state.contacts.any { it.accountId == contact.accountId }) return
        val token = credential() ?: return
        val input = pendingAdds.getOrPut(contact.accountId) { AddContact(UUID.randomUUID().toString(), contact.accountId) }
        val generation = epoch
        state = state.copy(mutating = true, error = null)
        worker.execute {
            if (generation != epoch) return@execute
            val result = runCatching { runBlocking { api.add(token, input) } }
            post { if (!closed && generation == epoch) {
                val saved = result.getOrNull()
                if (saved != null) pendingAdds.remove(contact.accountId)
                state = state.copy(mutating = false, contacts = if (saved == null) state.contacts else
                    (state.contacts.filterNot { it.accountId == saved.accountId } + saved), error = result.exceptionOrNull()?.let(::lobbyError))
            } }
        }
    }
    fun begin(contact: AccountIdentity) {
        if (closed || state.mutating || state.contacts.none { it.accountId == contact.accountId }) return
        val pending = pendingCreate
        state = state.copy(draft = if (pending == null) GameNameDraft(contact, suggest())
            else GameNameDraft(pendingRecipient!!, pending.gameName), uncertain = pending != null, error = null)
    }
    fun name(value: String) { if (!state.mutating && pendingCreate == null) state = state.copy(draft = state.draft?.copy(name = value), error = null) }
    fun reroll() { if (!state.mutating && pendingCreate == null) name(suggest()) }
    fun dismiss() { if (!state.mutating) state = state.copy(draft = null, error = null) }
    fun create() {
        if (closed || state.mutating) return
        val draft = state.draft ?: return
        if (!LobbyValidation.name(draft.name)) { state = state.copy(error = "Enter a game name."); return }
        val token = credential() ?: return
        val input = pendingCreate ?: CreateInvitation(UUID.randomUUID().toString(), draft.recipient.accountId, draft.name)
        pendingCreate = input; pendingRecipient = draft.recipient
        val generation = epoch
        state = state.copy(mutating = true, error = null)
        worker.execute {
            if (generation != epoch) return@execute
            val result = runCatching { runBlocking { api.create(token, input) } }
            post { if (!closed && generation == epoch) {
                val game = result.getOrNull()
                if (game != null) {
                    pendingCreate = null; pendingRecipient = null
                    state = state.copy(mutating = false, draft = null, uncertain = false, selected = game.id, detail = game,
                        games = state.games.filterNot { it.id == game.id } + game)
                    openGame(); refresh()
                } else {
                    val failure = result.exceptionOrNull()
                    val terminal = (failure as? LobbyFailure)?.code in listOf("invalid_name", "contact_required", "invalid_request", "request_conflict")
                    if (terminal) { pendingCreate = null; pendingRecipient = null }
                    state = state.copy(mutating = false, uncertain = !terminal, error = lobbyError(failure!!))
                }
            } }
        }
    }
    fun open(id: String) { if (!state.mutating) { state = state.copy(selected = id, detail = null, error = null); openGame(); refresh() } }
    fun back() { if (state.draft != null) dismiss() else { state = state.copy(selected = null, detail = null, error = null); refresh() } }
    fun sync() { if (!state.busy && !state.mutating) refresh(background = true) }
    fun refresh(background: Boolean = false) {
        if (closed || state.busy || state.mutating) return
        val token = credential() ?: return
        val generation = epoch; val selected = state.selected
        state = state.copy(busy = true, error = if (background || state.draft != null) state.error else null)
        worker.execute {
            if (generation != epoch) return@execute
            val result = runCatching { runBlocking {
                val contacts = mutableListOf<AccountIdentity>(); val games = mutableListOf<GameSummary>(); val invites = mutableListOf<GameSummary>()
                suspend fun pages(read: suspend (String?) -> String?) {
                    var after: String? = null; val seen = mutableSetOf<String>()
                    do { check(generation == epoch); after = read(after); check(after == null || seen.add(after!!)) } while (after != null)
                }
                pages { cursor -> api.contacts(token, cursor).let { contacts.addAll(it.contacts); it.next } }
                pages { cursor -> api.games(token, false, cursor).let { games.addAll(it.games); it.next } }
                pages { cursor -> api.games(token, true, cursor).let { invites.addAll(it.games); it.next } }
                val detail = selected?.let { api.head(token, it) }
                LobbyState(contacts = contacts, games = games, invitations = invites, detail = detail)
            } }
            post { if (!closed && generation == epoch) {
                val data = result.getOrNull()
                state = if (data != null) state.copy(contacts = data.contacts, games = data.games, invitations = data.invitations,
                    detail = if (state.selected == selected) data.detail else state.detail, busy = false, loaded = true)
                else if ((result.exceptionOrNull() as? LobbyFailure)?.code == "not_found" && state.selected == selected)
                    state.copy(selected = null, detail = null, busy = false, error = "This game is no longer available.")
                else state.copy(busy = false, error = if (background && state.draft != null) state.error else lobbyError(result.exceptionOrNull()!!))
                // A user may choose another row while a list refresh is in flight.
                if (state.selected != selected) refresh()
            } }
        }
    }
    override fun close() { closed = true; clear(); worker.execute { api.close() }; worker.shutdown() }
}

internal fun gameNameSuggestion(): String = listOf("Azure", "Crackling", "Moonlit", "Silver", "Arcane", "Velvet", "Ember", "Wandering").random() +
    " " + listOf("Spark", "Mist", "Wand", "Grove", "Rune", "Prism", "Dragon", "Lantern").random()
internal fun lobbyError(failure: Throwable): String = when ((failure as? LobbyFailure)?.code) {
    "invalid_search" -> "Use 1–64 username characters without control characters."
    "invalid_name" -> "Enter a game name."
    "contact_required" -> "Add this player to your contacts first."
    "authentication_required" -> "Please sign in again. Your unfinished request is kept."
    "try_later" -> "Too many requests. Wait a moment and retry."
    "request_conflict" -> "This request could not be reused. Close the dialog and try again."
    "not_configured" -> "The game service is not configured in this build."
    else -> "Could not reach the game service. Your input is kept. Check your connection and retry."
}
