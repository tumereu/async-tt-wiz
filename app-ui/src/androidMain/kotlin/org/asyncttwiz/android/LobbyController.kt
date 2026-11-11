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
    clock: () -> Long = ::monotonicMillis,
    /** Where engine games do their work: the app's engine thread (I-D56 step D2); tests pass [EngineWorker.Direct]. */
    engineWorker: EngineWorker = EngineWorker.Background,
    /** The main thread's clock [engineTicks] run on: by default the main looper's handler. */
    private val delayed: MainDelayed = mainLooperDelayed) : AutoCloseable {
    private val holder = LobbyStateHolder()
    var state by holder
        private set
    /** Narrow read of [state] for table composition; see [LobbyTableView]. */
    val table: State<LobbyTableView> get() = holder.table
    @Volatile private var epoch = 0L
    private var closed = false
    private val latest = mutableMapOf<String, GameSummary>()
    val setupCancellation = SetupCancellation()
    private val personalChat = GameChat()
    private val session: GameSession? = gameConnection?.let { GameSession(it, credential, post, authenticationRequired,
        changed = ::accepted, settled = { command, result ->
            if (command.action == GameAction.AcceptInvitation && result.outcome == "accepted" &&
                latest[command.scope.gameId]?.lifecycle == GameLifecycle.LOBBY && sessionAvailable(command.scope.gameId)) open(command.scope.gameId)
            // A move or a seen mark changes no list by itself: the game's stream brings its summary, and the regular refresh the rest
            if (command.action !is GameAction.Submit && !command.action.isEngine) refresh(background = true)
        }, statusChanged = ::connectionState, installationId = installationId, accountId = accountId, now = clock,
        personalChat = personalChat,
        openedGame = { state.selected }, engineWorker = engineWorker, engineLoad = { it == state.selected }) }
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
    private val invitations: LobbyInvitations = LobbyInvitations(scope, api, credential, accountId, worker, post, session, queue, suggest)
    /** The phone's card records, which a chosen deck's cards are built from (owner 2026-11-10). */
    var cardRecords: SavedCardRecords = SavedCardRecords.NONE
    private val deckPicker: LobbyDeckPicker = LobbyDeckPicker(scope, deckApi, credential, accountId, worker, post, session, queue) { cardRecords }
    private val preparationActions: LobbyPreparationActions = LobbyPreparationActions(scope, accountId, session, queue)
    private val removal: LobbyGameRemoval = LobbyGameRemoval(scope, accountId, session, setupCancellation)
    /** Game history: records of the account, a game and a pod (read on demand). */
    val history = HistoryController(api, credential, worker, post)
    private val routes: LobbyActivityRoutes = LobbyActivityRoutes(scope, api, credential, accountId, worker, post, session, openGame,
        activityTarget)
    /** LOB-145 (owner, 2026-11-10): the chat tab last shown in each game, by game id, while the app runs (personal UI). */
    private val lastChatTabs = mutableMapOf<String, ChatTab>()
    /** The chat tab last shown in [gameId]'s chat, null before any. */
    fun lastChatTab(gameId: String): ChatTab? = lastChatTabs[gameId]
    fun clear() { lastChatTabs.clear(); queue.clear(); epoch++; contactBook.clear(); deckPicker.clear(); routes.fence(); refresher.clear(); setupCancellation.clear(); personalChat.clear(); invitations.clear(); latest.clear(); session?.clear(); history.clear(); state = LobbyState() }
    /** Opens the game's chat; [focus]: at that message (holding its bubble on the table, LOB-136); [tab]: on that tab in a pod
     * game (LOB-145). */
    fun openChat(focus: String? = null, tab: ChatTab = ChatTab.GAME) {
        if (state.detail?.lifecycle == GameLifecycle.ACTIVE) {
            AppLog.info("chat", "Chat opened on ${tab.name.lowercase()}" + (focus?.let { " at message $it" } ?: "") +
                (state.detail?.id?.let { id -> lastChatTabs[id]?.let { " (last used here: ${it.name.lowercase()})" } } ?: ""))
            state.detail?.id?.let { lastChatTabs[it] = tab }
            state = state.copy(chatOpen = true, chatFocus = focus, chatTab = tab); connectionState()
        }
    }
    /** LOB-145: shows the Game or Pod tab of a pod game's chat. */
    fun chatTab(tab: ChatTab) {
        if (state.chatOpen && tab != state.chatTab) {
            AppLog.info("chat", "Chat tab ${tab.name.lowercase()}")
            state.detail?.id?.let { lastChatTabs[it] = tab }
            state = state.copy(chatTab = tab, chatFocus = null)
        }
    }
    /** LOB-139: Write in the Life panel: the chat open with its input focused and the soft keyboard up. */
    fun writeChat() { openChat(); if (state.chatOpen) state = state.copy(chatWrite = true) }
    fun closeChat() { state = state.copy(chatOpen = false, chatFocus = null, chatWrite = false) }
    var gameInput: (() -> Unit)? = null
    fun composeChat(text: String) { gameInput?.invoke(); state.detail?.let { personalChat.compose(it, text); connectionState() } }
    fun sendChat() {
        val game = state.detail ?: return
        val draft = state.chat.draft ?: return
        if (!closed && state.chat.canSend(game, true) && session?.sendChat(game, draft.text) == true) {
            personalChat.sent(game.id, draft.text); connectionState()
        }
    }
    /** LOB-139: sends a quick message with one tap; a typed draft stays as it is. */
    fun sendQuickChat(text: String) {
        val game = state.detail ?: return
        gameInput?.invoke()
        if (!closed && state.chat.canSendQuick(game, true) && session?.sendChat(game, text) == true) {
            personalChat.sent(game.id, text, fromDraft = false); connectionState()
        }
    }
    /** LOB-137: the own sent message's bubble [key] has faded; reopening the game doesn't show it again. */
    fun chatSentShown(gameId: String, key: Long) { personalChat.sentShown(gameId, key); connectionState() }
    /** LOB-136: a tap on the other player's chat bubble reads its message (the next unread one shows next). */
    fun readChatBubble(gameId: String, messageId: String) {
        if (!closed && state.selected == gameId) {
            gameInput?.invoke()
            session?.presentedChat(gameId, setOf(messageId)); connectionState()
        }
    }
    val chatViewEpoch get() = epoch
    fun presentedChat(gameId: String, viewEpoch: Long, ids: Set<String>) {
        // Fence stale layout callbacks after navigation/Logout or a changed account's panel.
        if (!closed && viewEpoch == epoch && state.chatOpen && state.chatTab == ChatTab.GAME && state.selected == gameId) {
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
            queuedTransferCards = state.selected?.let(wire::queuedTransfers) ?: 0, board = state.detail?.let(wire::board),
            ownPodDeckName = state.selected?.let(wire::ownPodDeckName),
            opponentPresence = state.selected?.let(wire::opponentPresence),
            chat = state.selected?.let { personalChat.presentation(it, accountId(), wire.chat(it), wire.outgoingChat(it)) } ?: ChatPresentation(),
            chatLoaded = state.selected?.let(wire::chatLoaded) == true)
        // Never re-enter the session from its own status callback; run waiting taps right after it.
        if (queue.anyReady()) post(queue::runQueued)
    }
    fun dismissSessionError() { session?.dismissError(); state = state.copy(error = null, searchError = null) }
    private fun accepted(game: GameSummary) {
        val old = latest[game.id]
        if (old != null && !GameSession.newerOrEqual(game.revision, old.revision)) return
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
            closePicker(); state = state.copy(preparing = false, transferFeedback = null)
        }
        if (state.selected == game.id) routes.reconcileActivityTarget(game)
        if (!listed && state.selected == game.id) leaveUnavailable()
    }
    private fun leaveUnavailable() {
        state.selected?.let { AppLog.info("lobby", "Game ${it.take(8)} no longer available: back to the list") }
        routes.fence()
        closePicker(); setupCancellation.clear()
        state = state.copy(selected = null, detail = null, preparing = false, acceptedPreparation = null,
            preparation = null, board = null, transferFeedback = null, queuedTransferCards = 0,
            chatOpen = false, chat = ChatPresentation(), gameContext = null, deletion = null, activityTarget = null,
            error = "This game is no longer available.")
    }
    // Game menu, deletion and setup cancellation: LobbyGameRemoval.
    fun gameContext(game: GameSummary) = removal.gameContext(game)
    fun dismissGameContext() = removal.dismissGameContext()
    fun offersGameDeletion(game: GameSummary): Boolean = removal.offersGameDeletion(game)
    fun requestGameDeletion(game: GameSummary) = removal.requestGameDeletion(game)
    fun dismissGameDeletion() = removal.dismissGameDeletion()
    fun confirmGameDeletion(): Boolean = removal.confirmGameDeletion()
    fun deleteGame(game: GameSummary): Boolean = removal.deleteGame(game)
    fun offersSetupCancellation(game: GameSummary): Boolean = removal.offersSetupCancellation(game)
    fun showGameContext(game: GameSummary) = removal.showGameContext(game)
    fun requestCancelSetup(game: GameSummary) = removal.requestCancelSetup(game)
    fun confirmCancelSetup() = removal.confirmCancelSetup()
    fun cancelSetup(game: GameSummary): Boolean = removal.cancelSetup(game)
    /** I-D56: the open game, when it is played on engine state. */
    fun engineGame(): EngineGame? = state.detail?.let { session?.engineGame(it) }
    /** The open engine game's own steps and what it sends ([GameSession.engineTick]); [connectionState] republishes. */
    fun engineTick() { state.selected?.let { session?.engineTick(it) } }
    /** [engineTick] every second on the main thread's clock while [gameId]'s table is shown, until the returned closer is
     * called: not on a composition's frame clock, which stands still in the e2e harness while nothing animates. */
    fun engineTicks(gameId: String): () -> Unit {
        var active = true
        AppLog.info("game", "Game ${gameId.take(8)}: engine ticks every 1 s")
        val cancel = repeatEvery(1_000, delayed) { if (!closed) engineTick() }
        return { if (active) { active = false; cancel(); AppLog.info("game", "Game ${gameId.take(8)}: engine ticks stopped") } }
    }
    /** After an action on the open engine game: what it sends goes out and the table shows its new board. */
    fun engineSent() { state.selected?.let { session?.pumpEngine(it) }; connectionState() }
    // Start settings, readiness and deck preparation: LobbyPreparationActions.
    fun starter(choice: StarterChoice) = preparationActions.starter(choice)
    fun alternateStarter(enabled: Boolean) = preparationActions.alternateStarter(enabled)
    fun showDeck(shown: Boolean) = preparationActions.showDeck(shown)
    /** LOB-135: the open game's assume reveal settings, changed by its host: in the lobby once the game can send (the
     * latest tap wins), in play at once beside the game's own requests. */
    fun assumeReveals(reveals: AssumeReveals) {
        val game = state.detail ?: return
        if (closed || game.inviter.accountId != accountId()) return
        val send = { state.detail?.takeIf { it.id == game.id }?.let { current -> session?.setAssumeReveals(current, reveals) }; connectionState() }
        if (game.lifecycle == GameLifecycle.LOBBY) queue.whenReady(game.id, "assumeReveals") { send() } else send()
    }
    /** TRN-065 (owner 2026-11-07): the viewer tapped OK on the open game's result: off the screen at once, sent once the
     * game can send. */
    fun acknowledgeResult() {
        val game = state.detail ?: return
        if (closed) return
        val atTable = session?.tableHeldAtEnd(game.id) == true
        val last = !resultWaiting(game)
        AppLog.info("game", "Result OK pressed on ${resultKey(game)} ${if (atTable) "at the table" else "over the lobby"}: " +
            "${game.resultNotice?.ending}, ${if (last) "the last one: to the lobby" else "the other player's still to come"}")
        if (last) session?.releaseEnd(game.id, "own OK, the last one")
        state = state.copy(resultsOk = state.resultsOk + resultKey(game), board = if (last) null else state.board)
        queue.whenReady(game.id, "acknowledgeResult") {
            state.detail?.takeIf { it.id == game.id }?.let { current -> session?.acknowledgeResult(current) }; connectionState()
        }
    }
    /** TRN-065 (owner, 2026-11-09): whether [game]'s table stays on after its play ended, until both OKs stand. */
    fun tableHeldAtEnd(game: GameSummary?): Boolean = game != null && session?.tableHeldAtEnd(game.id) == true && resultOpen(game)
    /** TRN-065: whether the viewer still has to tap OK on [game]'s last result (the server's word, unless OK'd here). */
    fun resultUnseen(game: GameSummary): Boolean =
        game.resultNotice?.unseen?.contains(accountId()) == true && resultKey(game) !in state.resultsOk
    /** TRN-065 (owner, 2026-11-09): whether the other player still has to tap OK on [game]'s last result: the viewer waits
     * for it at the table (or with the dialog over the lobby), and nobody acts in the lobby meanwhile. */
    fun resultWaiting(game: GameSummary): Boolean = game.lifecycle == GameLifecycle.LOBBY &&
        game.resultNotice?.unseen?.any { it != accountId() } == true
    /** TRN-065: whether an OK on [game]'s last result is still owed by anyone: the lobby can't be used until both stand. */
    fun resultOpen(game: GameSummary): Boolean = game.lifecycle == GameLifecycle.LOBBY && (resultUnseen(game) || resultWaiting(game))
    /** For the log: who owes an OK, as this player sees it. */
    private fun owedBy(unseen: Collection<String>): String =
        unseen.joinToString { if (it == accountId()) "this player" else "the other player" }.ifEmpty { "nobody" }
    fun ready() = preparationActions.ready()
    fun withdrawReady() = preparationActions.withdrawReady()
    val preparationUndo: PreparationUndo get() = preparationActions.preparationUndo
    fun preparationUndoStep(): PreparationStep? = preparationActions.preparationUndoStep()
    fun undoPreparation(): Boolean = preparationActions.undoPreparation()
    fun prepareDeck() = preparationActions.prepareDeck()
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
    /** A game's notification (LOB-078): opens the game; when it is open already its table stays as it is, so a take-back
     * that reached it in the background still plays on the return (HIS-106, HIS-108) instead of the table opening anew
     * at the board after it. */
    fun openNotified(id: String) { if (state.selected == id && state.detail?.id == id) openGame() else open(id) }
    fun open(id: String) {
        // TRN-065 (e2e GameEndDialogTest, 2026-11-10): which game opens, and what of its last result stands
        AppLog.info("lobby", "Game ${id.take(8)} opened" + (if (state.mutating) ": ignored, a change is being saved" else
            latest[id]?.let { game -> " (${game.lifecycle}${game.resultNotice?.let { ", result OK owed by ${owedBy(it.unseen)}" }.orEmpty()})" }.orEmpty()))
        if (!state.mutating) { routes.fence(); setupCancellation.clear(); queue.dropQueued(); closePicker(); state = state.copy(selected = id, detail = null, activityTarget = null, acceptedPreparation = null, preparation = null, board = null, preparing = false, transferFeedback = null, chatOpen = false, chat = ChatPresentation(), gameContext = null, deletion = null, error = null); session?.openedChanged(); openGame(); refresh() } }
    fun back() { if (state.deletion != null) dismissGameDeletion() else if (state.gameContext != null) dismissGameContext()
        else if (setupCancellation.confirmation != null) setupCancellation.dismiss() else if (setupCancellation.context != null) setupCancellation.dismissContext()
        else if (state.chatOpen) closeChat() else if (state.preparing) state = state.copy(preparing = false, transferFeedback = null)
        else if (state.picker != null) closePicker() else if (state.draft != null) dismiss() else {
            state.selected?.let { AppLog.info("lobby", "Game ${it.take(8)} left for the list") }
            queue.dropQueued(); routes.fence(); state = state.copy(selected = null, detail = null, activityTarget = null, acceptedPreparation = null, preparation = null, board = null, transferFeedback = null, error = null); session?.openedChanged(); refresh() } }
    /** The background maintenance's tick: the socket's background attempts back off after failures ([GameSession.poll]). */
    fun tick() { session?.poll() }
    fun heartbeat(presence: GamePresence): Boolean = session?.heartbeat(presence) == true
    fun sync() { tick(); if (refresher.inForeground && !state.busy && !state.mutating) refresh(background = true) }
    fun refresh(background: Boolean = false) = refresher.refresh(background)
    override fun close() { closed = true; clear(); session?.close(); worker.execute { api.close(); deckApi?.close() }; worker.shutdown() }
}
