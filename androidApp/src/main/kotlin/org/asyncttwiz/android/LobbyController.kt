package org.asyncttwiz.android

import androidx.compose.runtime.*
import kotlinx.coroutines.runBlocking
import org.asyncttwiz.protocol.*
import java.util.UUID
import java.util.concurrent.ExecutorService

data class GameNameDraft(val recipient: AccountIdentity, val name: String)
data class DeckPickerState(val game: GameSummary, val decks: List<DeckSummary> = emptyList(),
    val draft: DeckSummary? = null, val loading: Boolean = true, val confirming: Boolean = false,
    val importing: Boolean = false, val error: String? = null)
data class LobbyState(val contacts: List<AccountIdentity> = emptyList(), val query: String = "",
    val results: List<AccountIdentity> = emptyList(), val searched: Boolean = false, val searching: Boolean = false,
    val searchError: String? = null, val games: List<GameSummary> = emptyList(), val invitations: List<GameSummary> = emptyList(),
    val selected: String? = null, val detail: GameSummary? = null, val draft: GameNameDraft? = null,
    val busy: Boolean = false, val mutating: Boolean = false, val uncertain: Boolean = false,
    val error: String? = null, val loaded: Boolean = false, val connectionMessage: String? = null,
    val connectedGames: Set<String> = emptySet(), val pendingGames: Set<String> = emptySet(),
    val picker: DeckPickerState? = null, val acceptedPreparation: LobbyPreparation? = null,
    val preparation: LobbyPreparation? = null, val queuedTransferCards: Long = 0)

/** Accepted account data plus personal volatile input. Never an independently authoritative local game. */
class LobbyController(private val api: Lobbies, private val credential: () -> String?,
    private val worker: ExecutorService, private val post: (() -> Unit) -> Unit,
    private val openGame: () -> Unit = {}, private val suggest: () -> String = ::gameNameSuggestion,
    gameConnection: GameConnection? = null, private val accountId: () -> String? = { null },
    authenticationRequired: (String) -> Unit = {}, installationId: String = UUID.randomUUID().toString(),
    private val deckApi: Decks? = null) : AutoCloseable {
    var state by mutableStateOf(LobbyState())
        private set
    @Volatile private var epoch = 0L
    private var closed = false
    private var searchVersion = 0L
    private var pendingCreate: CreateInvitation? = null
    private var pendingRecipient: AccountIdentity? = null
    private val pendingAdds = mutableMapOf<String, AddContact>()
    private var pickerVersion = 0L
    private val session: GameSession? = gameConnection?.let { GameSession(it, credential, post, authenticationRequired,
        changed = ::accepted, settled = { command, result ->
            if (command.action == GameAction.AcceptInvitation && result.outcome == "accepted") open(command.scope.gameId)
            refresh(background = true)
        }, statusChanged = ::connectionState, installationId = installationId) }
    fun clear() { epoch++; searchVersion++; pickerVersion++; pendingCreate = null; pendingRecipient = null; pendingAdds.clear(); session?.clear(); state = LobbyState() }
    private fun connectionState() {
        val wire = session ?: return
        val ids = (state.games + state.invitations + listOfNotNull(state.detail)).map { it.id }.toSet()
        state = state.copy(connectionMessage = wire.error, connectedGames = ids.filter(wire::ready).toSet(), pendingGames = ids.filter(wire::pending).toSet(),
            acceptedPreparation = state.selected?.let(wire::acceptedPreparation), preparation = state.detail?.let(wire::preparation),
            queuedTransferCards = state.selected?.let(wire::queuedTransfers) ?: 0)
    }
    private fun accepted(game: GameSummary) {
        val mine = accountId()
        val listed = game.lifecycle == GameLifecycle.LOBBY || game.lifecycle == GameLifecycle.ACTIVE
        val inGames = listed && (game.inviter.accountId == mine || game.invitationState == InvitationState.ACCEPTED)
        val inInvites = listed && game.recipient.accountId == mine && game.invitationState == InvitationState.PENDING
        state = state.copy(games = state.games.filterNot { it.id == game.id } + if (inGames) listOf(game) else emptyList(),
            invitations = state.invitations.filterNot { it.id == game.id } + if (inInvites) listOf(game) else emptyList(),
            detail = if (state.selected == game.id) game else state.detail)
    }
    fun respond(game: GameSummary, accept: Boolean) {
        if (game.recipient.accountId != accountId() || game.invitationState != InvitationState.PENDING || game.lifecycle != GameLifecycle.LOBBY) return
        session?.submit(game, if (accept) GameAction.AcceptInvitation else GameAction.DeclineInvitation)
    }
    fun reinvite(game: GameSummary) {
        if (game.inviter.accountId != accountId() || game.invitationState != InvitationState.DECLINED || game.lifecycle != GameLifecycle.LOBBY) return
        session?.submit(game, GameAction.Reinvite)
    }
    fun chooseDeck() {
        val game = state.detail ?: return
        if (closed || deckApi == null || game.lifecycle != GameLifecycle.LOBBY || game.id !in state.connectedGames ||
            game.id in state.pendingGames || (game.inviter.accountId != accountId() && game.invitationState != InvitationState.ACCEPTED)) return
        state = state.copy(picker = DeckPickerState(game))
        refreshPicker()
    }
    fun closePicker() { pickerVersion++; state = state.copy(picker = null) }
    fun highlightDeck(deck: DeckSummary) {
        val picker = state.picker ?: return
        if (!picker.confirming && deck in picker.decks) state = state.copy(picker = picker.copy(draft = deck, error = null))
    }
    fun importForPicker() { state.picker?.takeIf { !it.confirming }?.let { state = state.copy(picker = it.copy(importing = true)) } }
    fun importedForPicker(): Boolean {
        if (state.picker?.importing != true) return false
        state = state.copy(picker = state.picker!!.copy(importing = false)); refreshPicker(); return true
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
                    check(generation == epoch)
                    val page = api.list(token, next); page.decks.forEach { decks[it.id] = it }; next = page.next
                    check(next == null || seen.add(next!!))
                } while (next != null)
                decks.values.toList()
            } }
            post { if (!closed && generation == epoch && version == pickerVersion) state.picker?.let {
                // A background collection read never silently substitutes or updates the captured draft.
                state = state.copy(picker = it.copy(decks = result.getOrDefault(it.decks), loading = false,
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
        if (closed || picker.confirming || picker.loading || picker.game.id !in state.connectedGames || picker.game.id in state.pendingGames) return
        val generation = epoch; val version = ++pickerVersion
        state = state.copy(picker = picker.copy(confirming = true, error = null))
        worker.execute {
            val result = runCatching { runBlocking {
                check(generation == epoch)
                val saved = api.read(token, source.id)
                if (saved.deck.revision != source.revision) throw DeckFailure("source_changed")
                val definitions = saved.entries.map { it.definition }.distinct().map { digest ->
                    check(generation == epoch); api.definition(token, source.id, digest)
                }
                saved to definitions
            } }
            post { if (!closed && generation == epoch && version == pickerVersion) state.picker?.let { current ->
                val data = result.getOrNull()
                if (data != null && state.detail?.preparationRound == picker.game.preparationRound &&
                    session?.selectDeck(picker.game, actor, data.first, data.second) == true) closePicker()
                else state = state.copy(picker = current.copy(confirming = false, error = when ((result.exceptionOrNull() as? DeckFailure)?.code) {
                    "source_changed" -> "This saved deck changed. Refresh and choose it again."
                    "not_found" -> "This saved deck was deleted. Refresh and choose another deck."
                    null -> "This game changed or is disconnected. Reconnect and choose again."
                    else -> deckError(result.exceptionOrNull() as? Exception ?: DeckFailure("network"))
                }))
            } }
        }
    }
    fun reconnect() { session?.tick(); refresh() }
    /** Capture on pointer/accessibility start; release must use this exact object, not a fresh row count. */
    fun captureTransfer(definition: String, source: SavedDeckSection, all: Boolean): PreparedTransfer? {
        val game = state.detail ?: return null
        val actor = accountId() ?: return null
        if (closed || game.lifecycle != GameLifecycle.LOBBY || game.id !in state.connectedGames) return null
        val preparation = state.preparation ?: return null
        return PreparationRules.capture(preparation, actor, definition, source, all)
    }
    fun transfer(captured: PreparedTransfer): Boolean {
        val game = state.detail ?: return false
        val actor = accountId() ?: return false
        return !closed && session?.transfer(game, actor, captured) == true
    }
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
    fun open(id: String) { if (!state.mutating) { closePicker(); state = state.copy(selected = id, detail = null, acceptedPreparation = null, preparation = null, error = null); openGame(); refresh() } }
    fun back() { if (state.picker != null) closePicker() else if (state.draft != null) dismiss() else { state = state.copy(selected = null, detail = null, acceptedPreparation = null, preparation = null, error = null); refresh() } }
    fun sync() { session?.tick(); if (!state.busy && !state.mutating) refresh(background = true) }
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
                val priorGames = state.games + state.invitations
                state = if (data != null) state.copy(contacts = data.contacts, games = data.games.map { session?.summary(it) ?: it },
                    invitations = data.invitations.map { session?.summary(it) ?: it },
                    detail = if (state.selected == selected) data.detail?.let { session?.summary(it) ?: it } else state.detail, busy = false, loaded = true)
                else if ((result.exceptionOrNull() as? LobbyFailure)?.code == "not_found" && state.selected == selected)
                    state.copy(selected = null, detail = null, busy = false, error = "This game is no longer available.")
                else state.copy(busy = false, error = if (background && state.draft != null) state.error else lobbyError(result.exceptionOrNull()!!))
                if (data != null && session != null) {
                    val games = (data.games + data.invitations + priorGames + listOfNotNull(state.detail))
                        .groupBy { it.id }.values.map { versions -> versions.maxBy { GameWire.ordinal(it.revision) } }.map(session::summary)
                    for (game in games) accepted(game)
                    session.watch(games); connectionState()
                }
                // A user may choose another row while a list refresh is in flight.
                if (state.selected != selected) refresh()
            } }
        }
    }
    override fun close() { closed = true; clear(); session?.close(); worker.execute { api.close(); deckApi?.close() }; worker.shutdown() }
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
