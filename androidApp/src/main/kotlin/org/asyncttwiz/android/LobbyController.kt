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
    val preparation: LobbyPreparation? = null, val queuedTransferCards: Long = 0,
    val preparing: Boolean = false, val transferFeedback: TransferFeedback? = null, val play: PlayState? = null,
    val acceptedPlay: PlayState? = null, val activityTarget: ActivityTarget? = null,
    val gameContext: GameSummary? = null, val deletion: GameDeletion? = null,
    val chatOpen: Boolean = false, val chat: ChatPresentation = ChatPresentation())

/** Accepted account data plus personal volatile input. Never an independently authoritative local game. */
class LobbyController(private val api: Lobbies, private val credential: () -> String?,
    private val worker: ExecutorService, private val post: (() -> Unit) -> Unit,
    private val openGame: () -> Unit = {}, private val suggest: () -> String = ::gameNameSuggestion,
    gameConnection: GameConnection? = null, private val accountId: () -> String? = { null },
    authenticationRequired: (String) -> Unit = {}, installationId: String = UUID.randomUUID().toString(),
    private val deckApi: Decks? = null,
    private val activityTarget: (suspend (String, ActivityRoute) -> ActivityTarget)? = null,
    treeCache: ((String, String, String) -> org.asyncttwiz.storage.GameplayTreeStore)? = null) : AutoCloseable {
    var state by mutableStateOf(LobbyState())
        private set
    @Volatile private var epoch = 0L
    private var closed = false
    private var searchVersion = 0L
    private var pendingCreate: CreateInvitation? = null
    private var pendingRecipient: AccountIdentity? = null
    private val pendingAdds = mutableMapOf<String, AddContact>()
    private var pickerVersion = 0L
    private val latest = mutableMapOf<String, GameSummary>()
    private var routeVersion = 0L
    private var historyOwner: TableHistoryController? = null
    private fun resetHistory() { historyOwner?.close(); historyOwner = null }
    val history: TableHistoryController get() = historyOwner ?: TableHistoryController(worker, post, ::reviewHistory,
        submit = { action, invocation, continuation ->
            val game = state.detail
            val actor = accountId()
            game != null && actor != null && !closed && session?.gameplay(game, actor, action, invocation, continuation,
                captured = { historyOwner?.captured(it) }) == true
        }, control = ::historyControl).also { historyOwner = it }
    val inspection = CardInspectionController()
    internal val setupCancellation = SetupCancellation()
    private val personalChat = GameChat()
    private val session: GameSession? = gameConnection?.let { GameSession(it, credential, post, authenticationRequired,
        changed = ::accepted, settled = { command, result ->
            historyOwner?.settled(command, result)
            if (command.action == GameAction.AcceptInvitation && result.outcome == "accepted" &&
                latest[command.scope.gameId]?.lifecycle == GameLifecycle.LOBBY && sessionAvailable(command.scope.gameId)) open(command.scope.gameId)
            refresh(background = true)
        }, statusChanged = ::connectionState, installationId = installationId, accountId = accountId, treeCache = treeCache) }
    private fun sessionAvailable(id: String) = session?.available(id) != false
    fun clear() { historyOwner?.close(); historyOwner = null; epoch++; searchVersion++; pickerVersion++; routeVersion++; inspection.close(); setupCancellation.clear(); personalChat.clear(); pendingCreate = null; pendingRecipient = null; pendingAdds.clear(); latest.clear(); session?.clear(); state = LobbyState() }
    fun openChat() {
        if (state.detail?.lifecycle == GameLifecycle.ACTIVE) { state = state.copy(chatOpen = true); connectionState() }
    }
    fun closeChat() { state = state.copy(chatOpen = false) }
    fun composeChat(text: String) { state.detail?.let { personalChat.compose(it, text); connectionState() } }
    fun sendChat() {
        val game = state.detail ?: return
        val draft = state.chat.draft ?: return
        if (!closed && state.chat.canSend(game, game.id in state.connectedGames) && session?.sendChat(game, draft.text) == true) {
            personalChat.sent(game.id); connectionState()
        }
    }
    val chatViewEpoch get() = epoch
    fun presentedChat(gameId: String, viewEpoch: Long, ids: Set<String>) {
        // Fence stale layout callbacks after navigation/Logout or a changed account's panel.
        if (!closed && viewEpoch == epoch && state.chatOpen && state.selected == gameId) {
            personalChat.presented(state.chat.messages, ids); connectionState()
        }
    }
    private fun connectionState() {
        val wire = session ?: return
        val ids = (state.games + state.invitations + listOfNotNull(state.detail)).map { it.id }.toSet()
        val next = state.detail?.let(wire::preparation)
        val feedback = preparationFeedback(state.preparation, next, accountId(), state.transferFeedback)
        state = state.copy(connectionMessage = wire.error, connectedGames = ids.filter(wire::ready).toSet(), pendingGames = ids.filter(wire::pending).toSet(),
            acceptedPreparation = state.selected?.let(wire::acceptedPreparation), preparation = next, transferFeedback = feedback,
            queuedTransferCards = state.selected?.let(wire::queuedTransfers) ?: 0, play = state.detail?.let(wire::play),
            acceptedPlay = state.detail?.let(wire::acceptedPlay),
            chat = state.selected?.let { personalChat.presentation(it, accountId(), wire.chat(it), wire.outgoingChat(it)) } ?: ChatPresentation())
    }
    private fun accepted(game: GameSummary) {
        val old = latest[game.id]
        if (old != null && !GameSession.newerOrEqual(game.revision, old.revision)) return
        if (state.selected == game.id && state.detail?.playId != game.playId) resetHistory()
        latest[game.id] = game
        setupCancellation.accepted(game)
        if (game.lifecycle in listOf(GameLifecycle.CANCELLED, GameLifecycle.DELETED)) state = state.copy(
            gameContext = state.gameContext?.takeUnless { it.id == game.id },
            deletion = state.deletion?.takeUnless { it.game.id == game.id })
        val mine = accountId()
        val listed = (game.lifecycle == GameLifecycle.LOBBY || game.lifecycle == GameLifecycle.ACTIVE) && sessionAvailable(game.id)
        val inGames = listed && (game.inviter.accountId == mine || game.invitationState == InvitationState.ACCEPTED)
        val inInvites = listed && game.recipient.accountId == mine && game.invitationState == InvitationState.PENDING
        state = state.copy(games = state.games.filterNot { it.id == game.id } + if (inGames) listOf(game) else emptyList(),
            invitations = state.invitations.filterNot { it.id == game.id } + if (inInvites) listOf(game) else emptyList(),
            detail = if (state.selected == game.id) game else state.detail)
        if (state.selected == game.id && game.lifecycle != GameLifecycle.LOBBY) {
            closePicker(); inspection.close(); state = state.copy(preparing = false, transferFeedback = null)
        }
        if (state.selected == game.id) reconcileActivityTarget(game)
        if (!listed && state.selected == game.id) leaveUnavailable()
    }
    private fun leaveUnavailable() {
        resetHistory()
        routeVersion++
        closePicker(); inspection.close(); setupCancellation.clear()
        state = state.copy(selected = null, detail = null, preparing = false, acceptedPreparation = null,
            preparation = null, play = null, acceptedPlay = null, transferFeedback = null, queuedTransferCards = 0,
            chatOpen = false, chat = ChatPresentation(), gameContext = null, deletion = null, activityTarget = null,
            error = "This game is no longer available.")
    }
    fun gameContext(game: GameSummary) {
        if (offersGameDeletion(game)) {
            setupCancellation.clear()
            state = state.copy(gameContext = game)
        }
    }
    fun dismissGameContext() { state = state.copy(gameContext = null) }
    internal fun offersGameDeletion(game: GameSummary): Boolean {
        val actor = accountId() ?: return false
        val current = session?.summary(game) ?: latest[game.id] ?: game
        return !closed && sessionAvailable(game.id) && GameDeletion(game, actor).matches(current, actor)
    }
    fun requestGameDeletion(game: GameSummary) {
        val actor = accountId() ?: return
        if (!offersGameDeletion(game)) return
        setupCancellation.clear()
        state = state.copy(gameContext = null, deletion = GameDeletion(game, actor))
    }
    fun dismissGameDeletion() { state = state.copy(deletion = null) }
    fun confirmGameDeletion(): Boolean {
        val capture = state.deletion ?: return false
        val dispatched = capture.actorId == accountId() && deleteGame(capture.game)
        state = state.copy(deletion = null, error = if (dispatched) null else "This game changed or is disconnected. Reconnect and choose Delete game again.")
        return dispatched
    }
    fun deleteGame(game: GameSummary): Boolean {
        val current = (listOfNotNull(state.detail) + state.games).find { it.id == game.id }?.let { session?.summary(it) ?: it }
        val capture = accountId()?.let { GameDeletion(game, it) } ?: return false
        return !closed && capture.matches(current, accountId()) && session?.deleteGame(game) == true
    }
    fun respond(game: GameSummary, accept: Boolean) {
        if (game.recipient.accountId != accountId() || game.invitationState != InvitationState.PENDING || game.lifecycle != GameLifecycle.LOBBY) return
        session?.submit(game, if (accept) GameAction.AcceptInvitation else GameAction.DeclineInvitation)
    }
    fun reinvite(game: GameSummary) {
        if (game.inviter.accountId != accountId() || game.invitationState != InvitationState.DECLINED || game.lifecycle != GameLifecycle.LOBBY) return
        session?.submit(game, GameAction.Reinvite)
    }
    internal fun offersSetupCancellation(game: GameSummary): Boolean {
        val current = session?.summary(game) ?: game
        return !closed && sessionAvailable(game.id) && setupCancellationAllowed(game, accountId()) && setupCancellationAllowed(current, accountId()) &&
            current.preparationRound == game.preparationRound
    }
    internal fun showGameContext(game: GameSummary) {
        if (offersSetupCancellation(game)) {
            state = state.copy(gameContext = null, deletion = null)
            setupCancellation.context(game)
        }
    }
    internal fun requestCancelSetup(game: GameSummary) {
        if (offersSetupCancellation(game)) {
            state = state.copy(gameContext = null, deletion = null)
            setupCancellation.request(game)
        }
    }
    internal fun confirmCancelSetup() = setupCancellation.confirm(::cancelSetup)
    /** Always submit the captured game/round, never replace it with the currently selected row. */
    fun cancelSetup(game: GameSummary): Boolean {
        val current = session?.summary(game) ?: return false
        if (!offersSetupCancellation(game) || !setupCancellationAllowed(current, accountId()) ||
            current.preparationRound != game.preparationRound) return false
        return session.submit(game, GameAction.CancelSetup)
    }
    fun starter(choice: StarterChoice) = readiness(GameAction.SetStarter(choice))
    fun captureResult(): ResultTarget? = state.detail?.let { ResultActions.capture(it, state.play) }
    fun suggestResult(target: ResultTarget, outcome: SuggestedResult): Boolean {
        val game = state.detail ?: return false
        val actor = accountId() ?: return false
        if (ResultActions.capture(game, state.play) != target) return false
        return !closed && gameplay(PlayAction.SuggestResult(outcome))
    }
    fun acceptResult(target: ResultAcceptanceTarget): Boolean {
        val game = state.detail ?: return false
        val actor = accountId() ?: return false
        return !closed && session?.acceptResult(game, actor, target) == true
    }
    fun ready() = readiness(GameAction.Ready)
    fun withdrawReady() = readiness(GameAction.WithdrawReady)
    fun gameplay(action: PlayAction, invocationId: String? = null, continuation: Continuation? = null): Boolean {
        val game = state.detail ?: return false
        val actor = accountId() ?: return false
        val personal = historyOwner
        if (continuation == null && personal != null) {
            if (personal.armed == null && personal.playback?.position?.mode == HistoryMode.PLAYBACK) return false
            return personal.gameplay(action, invocationId) { session?.play(game) }
        }
        return !closed && session?.gameplay(game, actor, action, invocationId, continuation) == true
    }
    fun historyControl(action: GameAction): Boolean {
        val game = state.detail ?: return false
        val actor = accountId() ?: return false
        return !closed && session?.historyControl(game, actor, action) == true
    }
    /** Annotating a viewed boundary appends to ordinary current history; it does not activate that
     * boundary or silently acquire historical continuation intent. */
    fun annotate(action: PlayAction): Boolean {
        if (action !is PlayAction.EventComment && action !is PlayAction.Ping && action !is PlayAction.Unping) return false
        val game = state.detail ?: return false
        val actor = accountId() ?: return false
        return !closed && session?.gameplay(game, actor, action) == true
    }
    /** Accepted disclosure observer, not Boolean enqueue -> inspection. The UI supplies its exact
     * inspector lifetime and closes the returned subscription on Back/dismissal/replacement. */
    fun inspectFace(action: PlayAction.InspectFace, lifetimeId: String, isCurrent: () -> Boolean,
        observed: (InspectionEvent) -> Unit): InspectionSubscription? {
        val game = state.detail ?: return null
        val actor = accountId() ?: return null
        val generation = epoch
        return session?.inspectFace(game, actor, action, lifetimeId, isCurrent = {
            !closed && generation == epoch && accountId() == actor && state.selected == game.id &&
                state.detail?.playId == game.playId && isCurrent()
        }, observed = observed, continuation = historyOwner?.armed?.let { Continuation(it, true) },
            captured = { historyOwner?.captured(it) }).also { if (it != null) historyOwner?.inspectionSubmitted(state.play) }
    }
    fun retainedTree() = state.selected?.let { session?.retainedTree(it) }
    fun reviewHistory(nodeId: String?, complete: (DownloadedHistoryState) -> Unit): Boolean =
        state.selected?.let { session?.review(it, nodeId, complete) } == true
    fun reviewHistoryTarget(target: CapturedHistoryTarget, isCurrent: () -> Boolean,
        complete: (CapturedHistoryReview) -> Unit): Boolean {
        if (closed || state.selected != target.gameId || state.detail?.playId != target.playId || accountId() != target.accountId) return false
        val generation = epoch
        return session?.reviewTarget(target, isCurrent = {
            !closed && generation == epoch && accountId() == target.accountId && state.selected == target.gameId &&
                state.detail?.playId == target.playId && isCurrent()
        }, complete = complete) == true
    }
    fun downloadHistory(): Boolean = state.detail?.let { session?.downloadHistory(it) } == true
    fun leaveActivityReview() { state = state.copy(activityTarget = null, error = null); historyOwner?.current() }
    private fun readiness(action: GameAction) {
        val game = state.detail ?: return
        val actor = accountId() ?: return
        if (!closed) session?.readiness(game, actor, action)
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
    fun prepareDeck() {
        if (state.detail?.lifecycle == GameLifecycle.LOBBY && state.preparation?.players?.any { it.accountId == accountId() && it.deck != null } == true)
            state = state.copy(preparing = true, transferFeedback = null)
    }
    fun inspectPrepared(definition: String) {
        if (state.preparing) state.preparation?.definitions?.find { it.digest == definition }?.let {
            inspection.open(InspectedCard.retained(it.digest, it.definition))
        }
    }
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
                    state = state.copy(mutating = false, draft = null, uncertain = false)
                    // An idempotent create response is an original outcome, not current lifecycle state.
                    if (latest[game.id]?.lifecycle !in listOf(GameLifecycle.CANCELLED, GameLifecycle.DELETED) && sessionAvailable(game.id)) open(game.id)
                    else refresh()
                } else {
                    val failure = result.exceptionOrNull()
                    val terminal = (failure as? LobbyFailure)?.code in listOf("invalid_name", "contact_required", "invalid_request", "request_conflict")
                    if (terminal) { pendingCreate = null; pendingRecipient = null }
                    state = state.copy(mutating = false, uncertain = !terminal, error = lobbyError(failure!!))
                }
            } }
        }
    }
    fun activityUnavailable(message: String = "This activity is no longer available. Showing your current Games.") {
        leaveUnavailable()
        state = state.copy(error = message)
        openGame(); refresh(background = true)
    }
    fun openActivity(route: ActivityRoute, current: () -> Boolean) {
        val read = activityTarget ?: return activityUnavailable()
        val token = credential() ?: return activityUnavailable()
        if (closed || state.mutating) return
        if (route.accountId != accountId() || !current()) return activityUnavailable()
        val generation = epoch; val version = ++routeVersion
        worker.execute {
            if (generation != epoch) return@execute
            val result = runCatching { runBlocking {
                val target = read(token, route)
                require(target.gameId == route.gameId)
                target to if (target.destination == ActivityDestination.UNAVAILABLE) null else api.head(token, route.gameId)
            } }
            post {
                if (closed || generation != epoch || version != routeVersion || credential() != token || !current()) return@post
                val resolved = result.getOrNull()
                if (resolved?.second == null) {
                    activityUnavailable(if (result.isFailure) "Could not open this notification. Refresh Games and try again."
                        else "This activity is no longer available. Showing your current Games.")
                } else {
                    if (state.mutating) return@post
                    val detail = session?.summary(resolved.second!!) ?: latest[route.gameId]?.takeIf {
                        GameSession.newerOrEqual(it.revision, resolved.second!!.revision) } ?: resolved.second!!
                    if (!sessionAvailable(route.gameId) || detail.lifecycle !in listOf(GameLifecycle.LOBBY, GameLifecycle.ACTIVE)) {
                        activityUnavailable(); return@post
                    }
                    open(route.gameId)
                    state = state.copy(detail = detail, activityTarget = resolved.first,
                        error = if (resolved.first.destination == ActivityDestination.CURRENT)
                            "This notification is no longer current. Showing the current game." else null)
                    reconcileActivityTarget(detail)
                }
            }
        }
    }
    private fun reconcileActivityTarget(game: GameSummary) {
        val target = state.activityTarget ?: return
        val valid = when (target.destination) {
            ActivityDestination.INVITATION -> game.lifecycle == GameLifecycle.LOBBY && game.invitationState == InvitationState.PENDING && game.invitationId == target.invitationId
            ActivityDestination.GROUP -> game.lifecycle == GameLifecycle.ACTIVE && game.playId == target.playId
            else -> true
        }
        if (!valid) state = state.copy(activityTarget = ActivityTarget(game.id, ActivityDestination.CURRENT, playId = game.playId),
            error = "This notification is no longer current. Showing the current game.")
        else if (target.destination == ActivityDestination.GROUP) state = state.copy(error = null)
    }
    fun open(id: String) { if (!state.mutating) { resetHistory(); routeVersion++; setupCancellation.clear(); closePicker(); inspection.close(); state = state.copy(selected = id, detail = null, activityTarget = null, acceptedPreparation = null, preparation = null, play = null, acceptedPlay = null, preparing = false, transferFeedback = null, chatOpen = false, chat = ChatPresentation(), gameContext = null, deletion = null, error = null); openGame(); refresh() } }
    fun back() { if (state.deletion != null) dismissGameDeletion() else if (state.gameContext != null) dismissGameContext()
        else if (setupCancellation.confirmation != null) setupCancellation.dismiss() else if (setupCancellation.context != null) setupCancellation.dismissContext()
        else if (state.chatOpen) closeChat() else if (inspection.state != null) inspection.close() else if (state.preparing) state = state.copy(preparing = false, transferFeedback = null)
        else if (state.picker != null) closePicker() else if (state.draft != null) dismiss() else { resetHistory(); routeVersion++; state = state.copy(selected = null, detail = null, activityTarget = null, acceptedPreparation = null, preparation = null, play = null, acceptedPlay = null, transferFeedback = null, error = null); refresh() } }
    fun sync() { session?.tick(); if (!state.busy && !state.mutating) refresh(background = true) }
    fun refresh(background: Boolean = false) {
        if (closed || state.busy || state.mutating) return
        val token = credential() ?: return
        val generation = epoch; val selected = state.selected
        val atStart = latest.toMap()
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
                val detail = selected?.let {
                    try { api.head(token, it) } catch (failure: LobbyFailure) {
                        if (failure.code == "not_found") null else throw failure
                    }
                }
                LobbyState(contacts = contacts, games = games, invitations = invites, detail = detail)
            } }
            post { if (!closed && generation == epoch) {
                val data = result.getOrNull()
                if (data != null) {
                    // Omission removes an old row. Only a revision learned since this read began
                    // can override omission; retained old lists must not resurrect cancelled games.
                    val newer = latest.values.filter { atStart[it.id] != it }
                    val games = (data.games + data.invitations + listOfNotNull(data.detail) + newer)
                        .groupBy { it.id }.values.map { versions -> versions.maxBy { GameWire.ordinal(it.revision) } }
                        .map { game -> latest[game.id]?.takeIf { GameSession.newerOrEqual(it.revision, game.revision) } ?: game }
                    val present = games.map { it.id }.toSet()
                    for (id in atStart.keys - present) session?.unavailable(id)
                    state = state.copy(contacts = data.contacts, games = emptyList(), invitations = emptyList(), busy = false, loaded = true)
                    if (selected != null && state.selected == selected && data.detail == null && latest[selected] == atStart[selected]) {
                        session?.unavailable(selected); leaveUnavailable()
                    }
                    if (session == null) state = state.copy(games = data.games, invitations = data.invitations,
                        detail = if (state.selected == selected) data.detail else state.detail)
                    else {
                        session.watch(games); connectionState()
                    }
                } else {
                    state = state.copy(busy = false, error = if (background && state.draft != null) state.error else lobbyError(result.exceptionOrNull()!!))
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
