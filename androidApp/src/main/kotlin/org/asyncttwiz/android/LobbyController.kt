package org.asyncttwiz.android

import androidx.compose.runtime.*
import org.asyncttwiz.protocol.*
import java.util.UUID
import java.util.concurrent.ExecutorService

/** Accepted account data plus personal volatile input. Never an independently authoritative local game. */
class LobbyController(private val api: Lobbies, private val credential: () -> String?,
    private val worker: ExecutorService, private val post: (() -> Unit) -> Unit,
    private val openGame: () -> Unit = {}, private val suggest: () -> String = ::gameNameSuggestion,
    gameConnection: GameConnection? = null, private val accountId: () -> String? = { null },
    authenticationRequired: (String) -> Unit = {}, installationId: String = UUID.randomUUID().toString(),
    private val deckApi: Decks? = null,
    private val activityTarget: (suspend (String, ActivityRoute) -> ActivityTarget)? = null,
    treeCache: ((String, String, String) -> org.asyncttwiz.storage.GameplayTreeStore?)? = null,
    private val basicLandDefinitions: (() -> List<SubmittedDefinition>)? = null,
    clock: () -> Long = ::monotonicMillis) : AutoCloseable {
    private val holder = LobbyStateHolder()
    var state by holder
        private set
    /** Narrow read of [state] for table composition; see [LobbyTableView]. */
    internal val table: State<LobbyTableView> get() = holder.table
    @Volatile private var epoch = 0L
    private var closed = false
    private val latest = mutableMapOf<String, GameSummary>()
    val inspection = CardInspectionController()
    internal val setupCancellation = SetupCancellation()
    private val personalChat = GameChat()
    private val session: GameSession? = gameConnection?.let { GameSession(it, credential, post, authenticationRequired,
        changed = ::accepted, settled = { command, result ->
            tableActions.historyOwner?.settled(command, result)
            if (command.action == GameAction.AcceptInvitation && result.outcome == "accepted" &&
                latest[command.scope.gameId]?.lifecycle == GameLifecycle.LOBBY && sessionAvailable(command.scope.gameId)) open(command.scope.gameId)
            refresh(background = true)
        }, statusChanged = ::connectionState, installationId = installationId, accountId = accountId, treeCache = treeCache,
        tableChanges = { gameId, batch -> if (state.selected == gameId) tableActions.historyOwner?.liveChanges(batch) }, personalChat = personalChat,
        openedGame = { state.selected }) }
    private fun sessionAvailable(id: String) = session?.available(id) != false
    private val scope = object : LobbyScope {
        override var state: LobbyState
            get() = this@LobbyController.state
            set(value) { this@LobbyController.state = value }
        override val closed get() = this@LobbyController.closed
        override val epoch get() = this@LobbyController.epoch
        override val latest: Map<String, GameSummary> get() = this@LobbyController.latest
        override fun sessionAvailable(id: String) = this@LobbyController.sessionAvailable(id)
        override fun open(id: String) = this@LobbyController.open(id)
        override fun refresh(background: Boolean) = this@LobbyController.refresh(background)
        override fun leaveUnavailable() = this@LobbyController.leaveUnavailable()
        override fun connectionState() = this@LobbyController.connectionState()
    }
    private val queue: LobbyActionQueue = LobbyActionQueue(scope, session)
    private val refresher: LobbyRefresh = LobbyRefresh(scope, api, credential, worker, post, session, clock)
    private val contactBook: LobbyContacts = LobbyContacts(scope, api, credential, worker, post, refresher::fence, ::begin)
    private val invitations: LobbyInvitations = LobbyInvitations(scope, api, deckApi, credential, accountId, worker, post, session, queue,
        suggest, basicLandDefinitions)
    private val deckPicker: LobbyDeckPicker = LobbyDeckPicker(scope, deckApi, credential, accountId, worker, post, session, queue)
    private val preparationActions: LobbyPreparationActions = LobbyPreparationActions(scope, accountId, session, queue, inspection)
    private val removal: LobbyGameRemoval = LobbyGameRemoval(scope, accountId, session, setupCancellation)
    private val routes: LobbyActivityRoutes = LobbyActivityRoutes(scope, api, credential, accountId, worker, post, session, openGame,
        activityTarget)
    private val tableActions: LobbyTableActions = LobbyTableActions(scope, table, accountId, worker, post, session)
    val history: TableHistoryController get() = tableActions.history
    fun clear() { queue.clear(); tableActions.resetHistory(); epoch++; contactBook.clear(); deckPicker.clear(); routes.fence(); refresher.clear(); inspection.close(); setupCancellation.clear(); personalChat.clear(); invitations.clear(); latest.clear(); session?.clear(); state = LobbyState() }
    fun openChat() {
        if (state.detail?.lifecycle == GameLifecycle.ACTIVE) { state = state.copy(chatOpen = true); connectionState() }
    }
    fun closeChat() { state = state.copy(chatOpen = false) }
    var gameInput: (() -> Unit)? = null
    fun composeChat(text: String) { gameInput?.invoke(); state.detail?.let { personalChat.compose(it, text); connectionState() } }
    fun sendChat() {
        val game = state.detail ?: return
        val draft = state.chat.draft ?: return
        if (!closed && state.chat.canSend(game, true) && session?.sendChat(game, draft.text) == true) {
            personalChat.sent(game.id); connectionState()
        }
    }
    val chatViewEpoch get() = epoch
    fun presentedChat(gameId: String, viewEpoch: Long, ids: Set<String>) {
        // Fence stale layout callbacks after navigation/Logout or a changed account's panel.
        if (!closed && viewEpoch == epoch && state.chatOpen && state.selected == gameId) {
            session?.presentedChat(gameId, ids); connectionState()
        }
    }
    private fun connectionState() {
        val wire = session ?: return
        val ids = (state.games + state.invitations + listOfNotNull(state.detail)).map { it.id }.toSet()
        val next = state.detail?.let(wire::preparation)
        val feedback = preparationFeedback(state.preparation, next, accountId(), state.transferFeedback)
        state = state.copy(connectionMessage = wire.error, connectedGames = ids.filter(wire::ready).toSet(),
            syncedGames = ids.filter(wire::caughtUp).toSet(), pendingGames = ids.filter(wire::pending).toSet(),
            reconnectingGames = ids.filter(wire::reconnecting).toSet(),
            acceptedPreparation = state.selected?.let(wire::acceptedPreparation), preparation = next, transferFeedback = feedback,
            queuedTransferCards = state.selected?.let(wire::queuedTransfers) ?: 0, play = state.detail?.let(wire::play),
            acceptedPlay = state.detail?.let(wire::acceptedPlay),
            ownPodDeckName = state.selected?.let(wire::ownPodDeckName),
            opponentPresence = state.selected?.let(wire::opponentPresence),
            chat = state.selected?.let { personalChat.presentation(it, accountId(), wire.chat(it), wire.outgoingChat(it)) } ?: ChatPresentation(),
            chatLoaded = state.selected?.let(wire::chatLoaded) == true)
        tableActions.showTrees(state.selected?.let { listOf(it, wire.retainedTree(it), wire.displayTree(it), wire.retainedHistory(it)) }.orEmpty())
        // Never re-enter the session from its own status callback; run waiting taps right after it.
        if (queue.anyReady()) post(queue::runQueued)
    }
    fun dismissSessionError() { session?.dismissError(); state = state.copy(error = null, searchError = null) }
    private fun accepted(game: GameSummary) {
        val old = latest[game.id]
        if (old != null && !GameSession.newerOrEqual(game.revision, old.revision)) return
        if (state.selected == game.id && state.detail?.playId != game.playId) tableActions.resetHistory()
        latest[game.id] = game
        setupCancellation.accepted(game)
        if (game.lifecycle in listOf(GameLifecycle.CANCELLED, GameLifecycle.DELETED)) state = state.copy(
            gameContext = state.gameContext?.takeUnless { it.id == game.id },
            deletion = state.deletion?.takeUnless { it.game.id == game.id })
        val mine = accountId()
        val listed = (game.lifecycle == GameLifecycle.LOBBY || game.lifecycle == GameLifecycle.ACTIVE) && sessionAvailable(game.id)
        val inGames = listed && (game.inviter.accountId == mine || game.invitationState == InvitationState.ACCEPTED)
        val inInvites = listed && game.recipient.accountId == mine && game.invitationState == InvitationState.PENDING
        state = state.copy(games = updatedGameRows(state.games, game, inGames),
            invitations = updatedGameRows(state.invitations, game, inInvites),
            detail = if (state.selected == game.id) game else state.detail)
        if (state.selected == game.id && game.lifecycle != GameLifecycle.LOBBY) {
            closePicker(); inspection.close(); state = state.copy(preparing = false, transferFeedback = null)
        }
        if (state.selected == game.id) routes.reconcileActivityTarget(game)
        if (!listed && state.selected == game.id) leaveUnavailable()
    }
    private fun leaveUnavailable() {
        tableActions.resetHistory()
        routes.fence()
        closePicker(); inspection.close(); setupCancellation.clear()
        state = state.copy(selected = null, detail = null, preparing = false, acceptedPreparation = null,
            preparation = null, play = null, acceptedPlay = null, transferFeedback = null, queuedTransferCards = 0,
            chatOpen = false, chat = ChatPresentation(), gameContext = null, deletion = null, activityTarget = null,
            error = "This game is no longer available.")
    }
    // Game menu, deletion and setup cancellation: LobbyGameRemoval.
    fun gameContext(game: GameSummary) = removal.gameContext(game)
    fun dismissGameContext() = removal.dismissGameContext()
    internal fun offersGameDeletion(game: GameSummary): Boolean = removal.offersGameDeletion(game)
    fun requestGameDeletion(game: GameSummary) = removal.requestGameDeletion(game)
    fun dismissGameDeletion() = removal.dismissGameDeletion()
    fun confirmGameDeletion(): Boolean = removal.confirmGameDeletion()
    fun deleteGame(game: GameSummary): Boolean = removal.deleteGame(game)
    internal fun offersSetupCancellation(game: GameSummary): Boolean = removal.offersSetupCancellation(game)
    internal fun showGameContext(game: GameSummary) = removal.showGameContext(game)
    internal fun requestCancelSetup(game: GameSummary) = removal.requestCancelSetup(game)
    internal fun confirmCancelSetup() = removal.confirmCancelSetup()
    fun cancelSetup(game: GameSummary): Boolean = removal.cancelSetup(game)
    // The open table's requests, history and trees: LobbyTableActions.
    fun resultLocked(): Boolean = tableActions.resultLocked()
    fun captureResult(): ResultTarget? = tableActions.captureResult()
    fun suggestResult(target: ResultTarget, outcome: SuggestedResult, note: String = ""): Boolean =
        tableActions.suggestResult(target, outcome, note)
    fun acceptResult(target: ResultAcceptanceTarget): Boolean = tableActions.acceptResult(target)
    fun rejectResult(target: ResultAcceptanceTarget): Boolean = tableActions.rejectResult(target)
    fun gameplay(action: PlayAction, invocationId: String? = null, continuation: Continuation? = null): Boolean =
        tableActions.gameplay(action, invocationId, continuation)
    fun historyControl(action: GameAction): Boolean = tableActions.historyControl(action)
    fun gameplayBatch(actions: List<PlayAction>, requestId: String): Boolean = tableActions.gameplayBatch(actions, requestId)
    fun annotate(action: PlayAction): Boolean = tableActions.annotate(action)
    fun annotateBatch(actions: List<PlayAction>): Boolean = tableActions.annotateBatch(actions)
    fun inspectFace(action: PlayAction.InspectFace, lifetimeId: String, isCurrent: () -> Boolean,
        observed: (InspectionEvent) -> Unit): InspectionSubscription? = tableActions.inspectFace(action, lifetimeId, isCurrent, observed)
    fun retainedTree() = tableActions.retainedTree()
    fun retainedHistory() = tableActions.retainedHistory()
    fun displayTree() = tableActions.displayTree()
    fun reviewHistory(nodeId: String?, complete: (DownloadedHistoryState) -> Unit): Boolean = tableActions.reviewHistory(nodeId, complete)
    fun reviewHistoryTarget(target: CapturedHistoryTarget, isCurrent: () -> Boolean,
        complete: (CapturedHistoryReview) -> Unit): Boolean = tableActions.reviewHistoryTarget(target, isCurrent, complete)
    fun leaveActivityReview() { state = state.copy(activityTarget = null, error = null); tableActions.historyOwner?.current() }
    // Start settings, readiness and deck preparation: LobbyPreparationActions.
    fun starter(choice: StarterChoice) = preparationActions.starter(choice)
    fun alternateStarter(enabled: Boolean) = preparationActions.alternateStarter(enabled)
    fun showDeck(shown: Boolean) = preparationActions.showDeck(shown)
    fun ready() = preparationActions.ready()
    fun withdrawReady() = preparationActions.withdrawReady()
    fun changePodBasic(land: BasicLand, delta: Int, section: SavedDeckSection = SavedDeckSection.MAINBOARD): Boolean =
        preparationActions.changePodBasic(land, delta, section)
    internal val preparationUndo: PreparationUndo get() = preparationActions.preparationUndo
    internal fun preparationUndoStep(): PreparationStep? = preparationActions.preparationUndoStep()
    fun undoPreparation(): Boolean = preparationActions.undoPreparation()
    fun prepareDeck() = preparationActions.prepareDeck()
    fun inspectPrepared(definition: String) = preparationActions.inspectPrepared(definition)
    fun captureTransfer(definition: String, source: SavedDeckSection, all: Boolean): PreparedTransfer? =
        preparationActions.captureTransfer(definition, source, all)
    fun transfer(captured: PreparedTransfer): Boolean = preparationActions.transfer(captured)
    // Deck picker: LobbyDeckPicker.
    fun chooseDeck() = deckPicker.chooseDeck()
    fun closePicker() = deckPicker.closePicker()
    fun dismissPickerError() = deckPicker.dismissPickerError()
    fun highlightDeck(deck: DeckSummary) = deckPicker.highlightDeck(deck)
    fun importForPicker() = deckPicker.importForPicker()
    fun importedForPicker(saved: DeckSummary? = null): Boolean = deckPicker.importedForPicker(saved)
    fun refreshPicker() = deckPicker.refreshPicker()
    fun confirmDeck() = deckPicker.confirmDeck()
    // List refresh and foreground/background: LobbyRefresh.
    fun reconnect() { session?.tick(); refresh() }
    fun background() = refresher.background()
    fun foreground() = refresher.foreground()
    // Contacts (LobbyContacts) and invitations (LobbyInvitations).
    fun respond(game: GameSummary, accept: Boolean) = invitations.respond(game, accept)
    fun reinvite(game: GameSummary) = invitations.reinvite(game)
    fun query(value: String) = contactBook.query(value)
    fun search() = contactBook.search()
    fun contacts(chooseOpponent: Boolean) = contactBook.contacts(chooseOpponent)
    fun contactsTab(tab: ContactsTab) = contactBook.contactsTab(tab)
    fun filterContacts(value: String) = contactBook.filterContacts(value)
    fun requestRemoveContact(contact: AccountIdentity) = contactBook.requestRemoveContact(contact)
    fun dismissRemoveContact() = contactBook.dismissRemoveContact()
    fun removeContact() = contactBook.removeContact()
    fun invite(contact: AccountIdentity) {
        if (state.contacts.any { it.accountId == contact.accountId }) begin(contact)
        else add(contact, inviteAfter = true)
    }
    fun add(contact: AccountIdentity, inviteAfter: Boolean = false) = contactBook.add(contact, inviteAfter)
    fun begin(contact: AccountIdentity) = invitations.begin(contact)
    fun name(value: String) = invitations.name(value)
    fun reroll() = invitations.reroll()
    fun dismiss() = invitations.dismiss()
    fun create() = invitations.create()
    fun retryPodChallenge() = invitations.retryPodChallenge()
    fun challengeFromPod(podId: String, opponentId: String, gameName: String) = invitations.challengeFromPod(podId, opponentId, gameName)
    // Notification hand-off: LobbyActivityRoutes.
    fun activityUnavailable(message: String = "This activity is no longer available. Showing your current Games.") =
        routes.activityUnavailable(message)
    fun openActivity(route: ActivityRoute, current: () -> Boolean) = routes.openActivity(route, current)
    fun open(id: String) { if (!state.mutating) { tableActions.resetHistory(); routes.fence(); setupCancellation.clear(); queue.dropQueued(); closePicker(); inspection.close(); state = state.copy(selected = id, detail = null, activityTarget = null, acceptedPreparation = null, preparation = null, play = null, acceptedPlay = null, preparing = false, transferFeedback = null, chatOpen = false, chat = ChatPresentation(), gameContext = null, deletion = null, error = null); openGame(); refresh() } }
    fun back() { if (state.deletion != null) dismissGameDeletion() else if (state.gameContext != null) dismissGameContext()
        else if (setupCancellation.confirmation != null) setupCancellation.dismiss() else if (setupCancellation.context != null) setupCancellation.dismissContext()
        else if (state.chatOpen) closeChat() else if (inspection.state != null) inspection.close() else if (state.preparing) state = state.copy(preparing = false, transferFeedback = null)
        else if (state.picker != null) closePicker() else if (state.draft != null) dismiss() else { queue.dropQueued(); tableActions.resetHistory(); routes.fence(); state = state.copy(selected = null, detail = null, activityTarget = null, acceptedPreparation = null, preparation = null, play = null, acceptedPlay = null, transferFeedback = null, error = null); refresh() } }
    fun tick() { session?.tick() }
    fun heartbeat(presence: GamePresence): Boolean = session?.heartbeat(presence) == true
    fun sync() { tick(); if (refresher.inForeground && !state.busy && !state.mutating) refresh(background = true) }
    fun refresh(background: Boolean = false) = refresher.refresh(background)
    override fun close() { closed = true; clear(); session?.close(); worker.execute { api.close(); deckApi?.close() }; worker.shutdown() }
}
