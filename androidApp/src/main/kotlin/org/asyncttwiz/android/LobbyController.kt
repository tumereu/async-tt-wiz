package org.asyncttwiz.android

import androidx.compose.runtime.*
import kotlinx.coroutines.runBlocking
import org.asyncttwiz.protocol.*
import java.util.UUID
import java.util.concurrent.ExecutorService

data class GameNameDraft(val recipient: AccountIdentity, val name: String)
enum class ContactsTab { CONTACTS, SEARCH }
data class DeckPickerState(val game: GameSummary, val decks: List<DeckSummary> = emptyList(),
    val draft: DeckSummary? = null, val loading: Boolean = true, val confirming: Boolean = false,
    val importing: Boolean = false, val error: String? = null, val loaded: Boolean = false)
data class LobbyState(val contacts: List<AccountIdentity> = emptyList(), val query: String = "",
    val results: List<AccountIdentity> = emptyList(), val searched: Boolean = false, val searching: Boolean = false,
    val searchError: String? = null, val games: List<GameSummary> = emptyList(), val invitations: List<GameSummary> = emptyList(),
    val selected: String? = null, val detail: GameSummary? = null, val draft: GameNameDraft? = null,
    val busy: Boolean = false, val mutating: Boolean = false, val uncertain: Boolean = false,
    val error: String? = null, val loaded: Boolean = false, val connectionMessage: String? = null,
    val connectedGames: Set<String> = emptySet(), val pendingGames: Set<String> = emptySet(),
    /** Games whose events missed while in the background have all been received (see GameSession.caughtUp). */
    val syncedGames: Set<String> = emptySet(),
    val picker: DeckPickerState? = null, val acceptedPreparation: LobbyPreparation? = null,
    val preparation: LobbyPreparation? = null, val queuedTransferCards: Long = 0,
    val preparing: Boolean = false, val transferFeedback: TransferFeedback? = null, val play: PlayState? = null,
    val acceptedPlay: PlayState? = null, val activityTarget: ActivityTarget? = null,
    val gameContext: GameSummary? = null, val deletion: GameDeletion? = null,
    val chatOpen: Boolean = false, val chat: ChatPresentation = ChatPresentation(),
    val reconnectingGames: Set<String> = emptySet(), val backgroundLoading: Boolean = false,
    val choosingOpponent: Boolean = false, val contactsTab: ContactsTab = ContactsTab.CONTACTS,
    val contactFilter: String = "", val removingContact: AccountIdentity? = null, val chatLoaded: Boolean = false,
    val retryPodChallengeName: String? = null, val ownPodDeckName: String? = null,
    /** The selected game's opponent presence, when the server sends it. */
    val opponentPresence: ReceivedPresence? = null,
    /** Games with lobby taps waiting for the game connection or an earlier request; each runs once. */
    val queuedGames: Set<String> = emptySet(),
    /** Background refreshes have kept failing for a while; the shown lists may be stale. */
    val unreachable: Boolean = false) {
    val filteredContacts get() = contacts.filter { it.username.contains(contactFilter.trim(), ignoreCase = true) }
    internal fun tableView() = LobbyTableView(selected, detail, play, acceptedPlay, connectedGames, reconnectingGames,
        syncedGames, activityTarget, chatOpen, chat.unread, connectionMessage, error, ownPodDeckName, opponentPresence)
}

/** The [LobbyState] fields an open table reads. Games-list refreshes flip busy/loading flags in the whole
 * state every few seconds; the table reads this narrow view ([LobbyController.table]) instead, so only a
 * change to one of these fields recomposes it. */
internal data class LobbyTableView(val selected: String?, val detail: GameSummary?, val play: PlayState?,
    val acceptedPlay: PlayState?, val connectedGames: Set<String>, val reconnectingGames: Set<String>,
    val syncedGames: Set<String>, val activityTarget: ActivityTarget?, val chatOpen: Boolean, val chatUnread: Int,
    val connectionMessage: String?, val error: String?, val ownPodDeckName: String?, val opponentPresence: ReceivedPresence?)

/** [LobbyController.state] and its narrow table view, written together. An equal view is not written, so
 * the busy/loading flips of a list refresh never invalidate the table. (Not derivedStateOf: a pending
 * derived invalidation still recomposes a child whose parent recomposes in the same frame.) */
private class LobbyStateHolder : MutableState<LobbyState> {
    private val whole = mutableStateOf(LobbyState())
    val table = mutableStateOf(LobbyState().tableView(), structuralEqualityPolicy())
    override var value: LobbyState
        get() = whole.value
        set(next) { whole.value = next; table.value = next.tableView() }
    override fun component1() = value
    override fun component2(): (LobbyState) -> Unit = { value = it }
}

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
    /** The selected game's trees live in the session, outside snapshot state. Bumped whenever one of them
     * is replaced, and read by [retainedTree]/[displayTree]/[retainedHistory], so composition that reads a
     * tree recomposes when it changes (and not merely when some unrelated lobby field happens to). */
    private val treeRevision = mutableLongStateOf(0L)
    private var shownTrees: List<Any?> = emptyList()
    @Volatile private var epoch = 0L
    @Volatile private var refreshVersion = 0L
    @Volatile private var lifecycleVersion = 0L
    @Volatile private var inForeground = true
    private var refreshError: String? = null
    private val quietFailures = QuietRefreshFailures(clock)
    private var closed = false
    private var searchVersion = 0L
    private var pendingCreate: CreateInvitation? = null
    private data class PodChallenge(val podId: String, val input: ChallengeFromPod, val submission: SubmitDefinitions)
    private var pendingChallenge: PodChallenge? = null
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
        }).also { historyOwner = it }
    val inspection = CardInspectionController()
    internal val setupCancellation = SetupCancellation()
    private val personalChat = GameChat()
    private val session: GameSession? = gameConnection?.let { GameSession(it, credential, post, authenticationRequired,
        changed = ::accepted, settled = { command, result ->
            historyOwner?.settled(command, result)
            if (command.action == GameAction.AcceptInvitation && result.outcome == "accepted" &&
                latest[command.scope.gameId]?.lifecycle == GameLifecycle.LOBBY && sessionAvailable(command.scope.gameId)) open(command.scope.gameId)
            refresh(background = true)
        }, statusChanged = ::connectionState, installationId = installationId, accountId = accountId, treeCache = treeCache,
        tableChanges = { gameId, batch -> if (state.selected == gameId) historyOwner?.liveChanges(batch) }, personalChat = personalChat,
        openedGame = { state.selected }) }
    private fun sessionAvailable(id: String) = session?.available(id) != false
    /** A lobby tap made while the game connection (re)opens, or while an earlier request is in flight. */
    private class QueuedLobbyAction(val gameId: String, val kind: String, val run: () -> Unit)
    private val queuedActions = mutableListOf<QueuedLobbyAction>()
    private var runningQueued = false
    private fun canSendNow(gameId: String) = gameId in state.connectedGames && gameId !in state.pendingGames
    /** Runs [run] now when the game connection is ready, otherwise once it is. A newer tap of the same
     * control ([kind]) replaces an older waiting one, so nothing is submitted twice. */
    private fun whenReady(gameId: String, kind: String, run: () -> Unit) {
        if (closed) return
        if (canSendNow(gameId) && queuedActions.none { it.gameId == gameId }) { run(); return }
        queuedActions.removeAll { it.gameId == gameId && it.kind == kind }
        queuedActions.add(QueuedLobbyAction(gameId, kind, run))
        queuedChanged()
        session?.tick()
    }
    private fun dropQueued(kind: String? = null) {
        if (queuedActions.removeAll { kind == null || it.kind == kind }) queuedChanged()
    }
    private fun queuedChanged() {
        val games = queuedActions.map { it.gameId }.toSet()
        if (games != state.queuedGames) state = state.copy(queuedGames = games)
    }
    private fun runQueued() {
        if (runningQueued) return
        runningQueued = true
        try {
            while (!closed) {
                val next = queuedActions.firstOrNull { canSendNow(it.gameId) } ?: break
                queuedActions.remove(next); queuedChanged(); next.run()
            }
        } finally { runningQueued = false }
    }
    fun clear() { queuedActions.clear(); historyOwner?.close(); historyOwner = null; epoch++; searchVersion++; pickerVersion++; routeVersion++; refreshError = null; quietFailures.succeeded(); inspection.close(); setupCancellation.clear(); personalChat.clear(); pendingCreate = null; pendingChallenge = null; pendingRecipient = null; pendingAdds.clear(); latest.clear(); session?.clear(); state = LobbyState() }
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
        val trees = state.selected?.let { listOf(it, wire.retainedTree(it), wire.displayTree(it), wire.retainedHistory(it)) }.orEmpty()
        if (trees.size != shownTrees.size || trees.indices.any { trees[it] !== shownTrees[it] }) {
            shownTrees = trees; treeRevision.longValue++
        }
        // Never re-enter the session from its own status callback; run waiting taps right after it.
        if (queuedActions.any { canSendNow(it.gameId) }) post(::runQueued)
    }
    fun dismissSessionError() { session?.dismissError(); state = state.copy(error = null, searchError = null) }
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
        state = state.copy(games = updatedGameRows(state.games, game, inGames),
            invitations = updatedGameRows(state.invitations, game, inInvites),
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
        // A held pod challenge can be declined at once; accepting waits for the player's submitted deck.
        if (accept && game.invitationHeld) return
        session?.submit(game, if (accept) GameAction.AcceptInvitation else GameAction.DeclineInvitation)
    }
    fun reinvite(game: GameSummary) {
        if (game.inviter.accountId != accountId() || game.invitationState != InvitationState.DECLINED || game.lifecycle != GameLifecycle.LOBBY) return
        whenReady(game.id, "reinvite") { session?.submit(game, GameAction.Reinvite) }
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
    fun alternateStarter(enabled: Boolean) = readiness(GameAction.SetAlternateStarter(enabled))
    /** The result lock for the table right now, the one gate every table entry point consults (see
     * [ResultActions.blocksAt]). Reviewing: the shown point. Continuing: the armed point, then its predicted
     * new branch. Otherwise: the live endpoint. Branching away from a suggestion leaves it on the old branch. */
    fun resultLocked(): Boolean {
        // Read through the narrow view: the table calls this during composition.
        val shown = table.value
        val game = shown.detail ?: return false
        val personal = historyOwner
        val continuing = personal?.armed != null
        val reviewing = personal?.playback?.position?.mode == HistoryMode.PLAYBACK && !personal.provisional
        val board = if (reviewing) personal?.view?.state else if (continuing) shown.play else null
        return ResultActions.blocksAt(game, shown.acceptedPlay, shown.play, board, inTimeline = reviewing || continuing)
    }
    fun captureResult(): ResultTarget? = state.detail?.let { ResultActions.capture(it, state.play) }
    fun suggestResult(target: ResultTarget, outcome: SuggestedResult, note: String = ""): Boolean {
        val game = state.detail ?: return false
        val actor = accountId() ?: return false
        if (ResultActions.capture(game, state.play) != target) return false
        return !closed && gameplay(PlayAction.SuggestResult(outcome, ResultNoteInput.clean(note)))
    }
    fun acceptResult(target: ResultAcceptanceTarget): Boolean {
        val game = state.detail ?: return false
        val actor = accountId() ?: return false
        return !closed && session?.acceptResult(game, actor, target) == true
    }
    fun rejectResult(target: ResultAcceptanceTarget): Boolean {
        val game = state.detail ?: return false
        val actor = accountId() ?: return false
        return !closed && session?.rejectResult(game, actor, target) == true
    }
    fun ready() = readiness(GameAction.Ready)
    fun withdrawReady() = readiness(GameAction.WithdrawReady)
    fun changePodBasic(land: BasicLand, delta: Int, section: SavedDeckSection = SavedDeckSection.MAINBOARD): Boolean {
        val actor = accountId() ?: return false
        val preparation = state.preparation ?: return false
        val deck = preparation.players.find { it.accountId == actor }?.deck ?: return false
        if (delta !in listOf(-1, 1)) return false
        val counts = PodGamePreparation.basics(deck, section)
        val count = land.count(counts).toLong() + delta
        if (count !in 0..DeckValidation.MAX_CARDS.toLong() || !setPodBasics(land.withCount(counts, count.toInt()), section)) return false
        preparationUndo = preparationUndo.push(preparation.gameId, preparation.roundId, deck.id, PreparationStep.Basics(section, counts))
        return true
    }
    private fun setPodBasics(basics: PodDeckBasics, section: SavedDeckSection): Boolean {
        val game = state.detail ?: return false
        val actor = accountId() ?: return false
        val preparation = state.preparation ?: return false
        val deck = preparation.players.find { it.accountId == actor }?.deck ?: return false
        if (closed || preparation.podRules == null) return false
        val definitions = BasicLand.entries.map { basic -> preparation.definitions.find { it.digest == deck.basicDefinitions[basic] } ?: return false }
        return session?.readiness(game, actor, GameAction.SetPodGameBasics(deck.id, basics, definitions, section)) == true
    }
    /** Personal, last 50 transfers and basic taps of the shown preparation round; each undo is an ordinary request. */
    internal var preparationUndo by mutableStateOf(PreparationUndo())
        private set
    internal fun preparationUndoStep(): PreparationStep? = preparationUndo.last(state.preparation, accountId())
    fun undoPreparation(): Boolean {
        val game = state.detail ?: return false
        val actor = accountId() ?: return false
        if (closed || game.lifecycle != GameLifecycle.LOBBY) return false
        val done = when (val step = preparationUndoStep() ?: return false) {
            is PreparationStep.Moved -> session?.transfer(game, actor, step.transfer.inverse()) == true
            is PreparationStep.Basics -> setPodBasics(step.previous, step.section)
        }
        if (done) preparationUndo = preparationUndo.pop()
        return done
    }
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
    fun gameplayBatch(actions: List<PlayAction>, requestId: String): Boolean {
        val game = state.detail ?: return false
        val actor = accountId() ?: return false
        if (closed) return false
        val personal = historyOwner
        if (personal != null && personal.armed == null && personal.playback?.position?.mode == HistoryMode.PLAYBACK) return false
        fun send(continuation: Continuation?) = session?.gameplayBatch(game, actor, actions, requestId, continuation,
            captured = { historyOwner?.captured(it) }) == true
        return if (personal != null) personal.gameplay({ session?.play(game) }, ::send) else send(null)
    }
    /** Annotating a viewed boundary appends to ordinary current history; it does not activate that
     * boundary or silently acquire historical continuation intent. */
    fun annotate(action: PlayAction): Boolean {
        if (action !is PlayAction.EventComment && action !is PlayAction.Ping && action !is PlayAction.Unping) return false
        val game = state.detail ?: return false
        val actor = accountId() ?: return false
        return !closed && session?.gameplay(game, actor, action) == true
    }
    fun annotateBatch(actions: List<PlayAction>): Boolean {
        if (actions.isEmpty() || actions.any { it !is PlayAction.EventComment && it !is PlayAction.Ping && it !is PlayAction.Unping }) return false
        if (actions.size == 1) return annotate(actions.single())
        val game = state.detail ?: return false
        val actor = accountId() ?: return false
        return !closed && session?.gameplayBatch(game, actor, actions, java.util.UUID.randomUUID().toString()) == true
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
    fun retainedTree() = selectedTree(GameSession::retainedTree)
    fun retainedHistory() = selectedTree(GameSession::retainedHistory)
    fun displayTree() = selectedTree(GameSession::displayTree)
    private fun <T> selectedTree(read: GameSession.(String) -> T?): T? {
        treeRevision.longValue // Observed: the session's trees are not snapshot state.
        val selected = table.value.selected ?: return null
        return session?.read(selected)
    }
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
    fun leaveActivityReview() { state = state.copy(activityTarget = null, error = null); historyOwner?.current() }
    private fun readiness(action: GameAction) {
        val game = state.detail ?: return
        val actor = accountId() ?: return
        if (closed) return
        if (game.lifecycle != GameLifecycle.LOBBY) { session?.readiness(game, actor, action); return }
        // Ready/Withdraw toggle one control; the latest waiting tap of each control wins.
        whenReady(game.id, when (action) { is GameAction.SetStarter -> "starter"; is GameAction.SetAlternateStarter -> "alternate"; else -> "ready" }) {
            state.detail?.takeIf { it.id == game.id }?.let { current -> session?.readiness(current, actor, action) }
        }
    }
    /** The picker is personal and lists saved decks over HTTP, so it opens without the game connection. */
    fun chooseDeck() {
        val game = state.detail ?: return
        if (closed || deckApi == null || game.podId != null || state.preparation?.podRules != null || game.lifecycle != GameLifecycle.LOBBY ||
            (game.inviter.accountId != accountId() && game.invitationState != InvitationState.ACCEPTED)) return
        state = state.copy(picker = DeckPickerState(game))
        refreshPicker()
    }
    fun closePicker() { pickerVersion++; dropQueued("deck"); state = state.copy(picker = null) }
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
                else whenReady(picker.game.id, "deck") {
                    if (current()) {
                        if (state.detail?.preparationRound == picker.game.preparationRound &&
                            session?.selectDeck(picker.game, actor, data.first, data.second) == true) closePicker()
                        else failed(null)
                    }
                }
            } }
        }
    }
    fun reconnect() { session?.tick(); refresh() }
    fun background() {
        inForeground = false
        session?.background()
        lifecycleVersion++
        state = state.copy(backgroundLoading = state.busy, syncedGames = emptySet())
    }
    fun foreground() {
        if (closed) return
        inForeground = true
        // Like GameSession.foreground's action-grace reset, an old interrupted read must not
        // present a reconnect error or prevent the silent resume attempt from starting.
        refreshVersion++
        state = state.copy(busy = false, backgroundLoading = false,
            error = state.error.takeUnless { it == refreshError })
        refreshError = null
        session?.foreground()
        // Refresh account lists silently, retaining the table and existing stream generations.
        refresh(background = true)
    }
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
        if (closed || session?.transfer(game, actor, captured) != true) return false
        preparationUndo = preparationUndo.push(captured.gameId, captured.roundId, captured.deckId, PreparationStep.Moved(captured))
        return true
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
            val result = runCatching { runBlocking { serviceRetry(current = { generation == epoch && version == searchVersion }) { api.search(token, query) } } }
            post { if (!closed && generation == epoch && version == searchVersion) state = state.copy(searching = false,
                searched = true, results = result.getOrNull()?.users ?: state.results, searchError = result.exceptionOrNull()?.let(::lobbyError)) }
        }
    }
    fun contacts(chooseOpponent: Boolean) {
        state = state.copy(choosingOpponent = chooseOpponent, contactsTab = ContactsTab.CONTACTS, contactFilter = "")
    }
    fun contactsTab(tab: ContactsTab) { state = state.copy(contactsTab = tab) }
    fun filterContacts(value: String) { state = state.copy(contactFilter = value) }
    fun requestRemoveContact(contact: AccountIdentity) { if (!state.mutating) state = state.copy(removingContact = contact) }
    fun dismissRemoveContact() { state = state.copy(removingContact = null) }
    fun removeContact() {
        val contact = state.removingContact ?: return
        val token = credential() ?: return
        if (closed || state.mutating) return
        val generation = epoch
        // Fence a list read started before this removal so it cannot resurrect the contact.
        refreshVersion++
        state = state.copy(mutating = true, busy = false, removingContact = null, error = null)
        worker.execute {
            if (generation != epoch) return@execute
            val result = runCatching { runBlocking { serviceRetry(current = { generation == epoch }) { api.remove(token, contact.accountId) } } }
            post { if (!closed && generation == epoch) {
                state = state.copy(mutating = false, contacts = if (result.isSuccess) state.contacts.filterNot { it.accountId == contact.accountId } else state.contacts,
                    error = result.exceptionOrNull()?.let(::lobbyError))
            } }
        }
    }
    fun invite(contact: AccountIdentity) {
        if (state.contacts.any { it.accountId == contact.accountId }) begin(contact)
        else add(contact, inviteAfter = true)
    }
    fun add(contact: AccountIdentity, inviteAfter: Boolean = false) {
        if (closed || state.mutating || state.contacts.any { it.accountId == contact.accountId }) return
        val token = credential() ?: return
        val input = pendingAdds.getOrPut(contact.accountId) { AddContact(UUID.randomUUID().toString(), contact.accountId) }
        val generation = epoch
        refreshVersion++
        state = state.copy(mutating = true, busy = false, error = null)
        worker.execute {
            if (generation != epoch) return@execute
            val result = runCatching { runBlocking { serviceRetry(current = { generation == epoch }) { api.add(token, input) } } }
            post { if (!closed && generation == epoch) {
                val saved = result.getOrNull()
                if (saved != null) pendingAdds.remove(contact.accountId)
                state = state.copy(mutating = false, contacts = if (saved == null) state.contacts else
                    (state.contacts.filterNot { it.accountId == saved.accountId } + saved), error = result.exceptionOrNull()?.let(::lobbyError))
                if (saved != null && inviteAfter) begin(saved)
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
            val result = runCatching { runBlocking { serviceRetry(current = { generation == epoch }) { api.create(token, input) } } }
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
    fun retryPodChallenge() {
        val request = pendingChallenge ?: return
        challengeFromPod(request.podId, request.input.opponentId, request.input.gameName)
    }
    /** Pod UI supplies a suggested name; retries retain the original name and request identities. */
    fun challengeFromPod(podId: String, opponentId: String, gameName: String) {
        if (closed || state.mutating) return
        val token = credential() ?: return
        val decks = deckApi ?: return
        val resolve = basicLandDefinitions ?: return
        val previous = pendingChallenge
        if (previous != null && (previous.podId != podId || previous.input.opponentId != opponentId)) {
            state = state.copy(error = "Retry the original pod challenge before creating another."); return
        }
        if (previous == null && !LobbyValidation.name(gameName)) { state = state.copy(error = "Enter a game name."); return }
        val generation = epoch
        state = state.copy(mutating = true, error = null)
        worker.execute {
            if (generation != epoch) return@execute
            var captured = previous
            val result = runCatching { runBlocking {
                val request = captured ?: resolve().let { definitions ->
                    PodChallenge(podId, ChallengeFromPod(UUID.randomUUID().toString(), opponentId, gameName, definitions),
                        SubmitDefinitions(UUID.randomUUID().toString(), definitions))
                }.also { captured = it }
                serviceRetry(current = { generation == epoch }) { decks.submit(token, request.submission) }
                serviceRetry(current = { generation == epoch }) { api.challengeFromPod(token, request.podId, request.input) }
            } }
            post { if (!closed && generation == epoch) {
                val game = result.getOrNull()
                val failure = result.exceptionOrNull()
                val code = when (failure) { is LobbyFailure -> failure.code; is DeckFailure -> failure.code; else -> null }
                val terminal = code in listOf("not_found", "opponent_not_member", "invalid_opponent",
                    "deck_not_submitted", "invalid_name", "invalid_request", "request_conflict", "invalid_basic_definitions", "invalid_deck", "invalid_definition")
                pendingChallenge = if (game != null || terminal) null else captured
                state = state.copy(mutating = false, error = failure?.let(::lobbyError),
                    retryPodChallengeName = pendingChallenge?.input?.gameName)
                if (game != null) {
                    if (latest[game.id]?.lifecycle !in listOf(GameLifecycle.CANCELLED, GameLifecycle.DELETED) && sessionAvailable(game.id)) open(game.id)
                    else refresh()
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
        if (route.kind.isPodActivity()) return
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
    fun open(id: String) { if (!state.mutating) { resetHistory(); routeVersion++; setupCancellation.clear(); dropQueued(); closePicker(); inspection.close(); state = state.copy(selected = id, detail = null, activityTarget = null, acceptedPreparation = null, preparation = null, play = null, acceptedPlay = null, preparing = false, transferFeedback = null, chatOpen = false, chat = ChatPresentation(), gameContext = null, deletion = null, error = null); openGame(); refresh() } }
    fun back() { if (state.deletion != null) dismissGameDeletion() else if (state.gameContext != null) dismissGameContext()
        else if (setupCancellation.confirmation != null) setupCancellation.dismiss() else if (setupCancellation.context != null) setupCancellation.dismissContext()
        else if (state.chatOpen) closeChat() else if (inspection.state != null) inspection.close() else if (state.preparing) state = state.copy(preparing = false, transferFeedback = null)
        else if (state.picker != null) closePicker() else if (state.draft != null) dismiss() else { dropQueued(); resetHistory(); routeVersion++; state = state.copy(selected = null, detail = null, activityTarget = null, acceptedPreparation = null, preparation = null, play = null, acceptedPlay = null, transferFeedback = null, error = null); refresh() } }
    fun tick() { session?.tick() }
    fun heartbeat(presence: GamePresence): Boolean = session?.heartbeat(presence) == true
    fun sync() { tick(); if (inForeground && !state.busy && !state.mutating) refresh(background = true) }
    fun refresh(background: Boolean = false) {
        if (closed || !inForeground || state.busy || state.mutating) return
        val token = credential() ?: return
        val generation = epoch; val selected = state.selected
        val version = ++refreshVersion; val lifecycle = lifecycleVersion
        fun current() = generation == epoch && version == refreshVersion && inForeground
        val atStart = latest.toMap()
        // Nothing shown yet: ride out a short outage on the loading indicator instead of failing fast.
        val firstLoad = !state.loaded
        val delays = if (firstLoad) firstLoadRetryDelays else quickRetryDelays
        state = state.copy(busy = true, backgroundLoading = background,
            error = if (background || state.draft != null) state.error else null)
        worker.execute {
            if (!current()) return@execute
            val result = runCatching { runBlocking {
                val contacts = mutableListOf<AccountIdentity>(); val games = mutableListOf<GameSummary>(); val invites = mutableListOf<GameSummary>()
                suspend fun pages(read: suspend (String?) -> String?): Boolean {
                    var after: String? = null; val seen = mutableSetOf<String>()
                    do {
                        if (!current()) return false
                        after = read(after); check(after == null || seen.add(after!!))
                    } while (after != null)
                    return current()
                }
                if (!pages { cursor -> serviceRetry(current = ::current, delays = delays) { api.contacts(token, cursor) }.let { contacts.addAll(it.contacts); it.next } }) return@runBlocking null
                if (!pages { cursor -> serviceRetry(current = ::current, delays = delays) { api.games(token, false, cursor) }.let { games.addAll(it.games); it.next } }) return@runBlocking null
                if (!pages { cursor -> serviceRetry(current = ::current, delays = delays) { api.games(token, true, cursor) }.let { invites.addAll(it.games); it.next } }) return@runBlocking null
                val detail = selected?.let {
                    try { serviceRetry(current = ::current, delays = delays) { api.head(token, it) } } catch (failure: LobbyFailure) {
                        if (failure.code == "not_found") null else throw failure
                    }
                }
                LobbyState(contacts = contacts, games = games, invitations = invites, detail = detail)
            } }
            post { if (!closed && generation == epoch && version == refreshVersion) {
                val data = result.getOrNull()
                val failure = result.exceptionOrNull()
                if (data != null) {
                    quietFailures.succeeded()
                    state = state.copy(unreachable = false)
                    // Omission removes an old row. Only a revision learned since this read began
                    // can override omission; retained old lists must not resurrect cancelled games.
                    val newer = latest.values.filter { atStart[it.id] != it }
                    val games = (data.games + data.invitations + listOfNotNull(data.detail) + newer)
                        .groupBy { it.id }.values.map { versions -> versions.maxBy { GameWire.ordinal(it.revision) } }
                        .map { game -> latest[game.id]?.takeIf { GameSession.newerOrEqual(it.revision, game.revision) } ?: game }
                    val present = games.map { it.id }.toSet()
                    for (id in atStart.keys - present) session?.unavailable(id)
                    state = state.copy(contacts = data.contacts, games = state.games.filter { old -> games.any { it.id == old.id } },
                        invitations = state.invitations.filter { old -> games.any { it.id == old.id } }, busy = false,
                        backgroundLoading = false, loaded = true, error = state.error.takeUnless { it == refreshError })
                    refreshError = null
                    if (selected != null && state.selected == selected && data.detail == null && latest[selected] == atStart[selected]) {
                        session?.unavailable(selected); leaveUnavailable()
                    }
                    if (session == null) state = state.copy(games = stableGameRows(state.games, data.games), invitations = stableGameRows(state.invitations, data.invitations),
                        detail = if (state.selected == selected) data.detail else state.detail)
                    else {
                        session.watch(games); connectionState()
                    }
                } else if (background && !firstLoad && failure != null && transientServiceFailure(failure)) {
                    // A background poll of shown lists: keep them and let the next poll retry.
                    state = state.copy(busy = false, backgroundLoading = false, unreachable = quietFailures.failed())
                } else {
                    val silent = failure == null || lobbyLifecycleInterruption(failure,
                        interrupted = lifecycle != lifecycleVersion || !inForeground)
                    val previousError = refreshError
                    refreshError = if (silent) null else lobbyError(failure!!)
                    state = state.copy(busy = false, backgroundLoading = false,
                        error = refreshError ?: state.error.takeUnless { it == previousError })
                }
                // A user may choose another row while a list refresh is in flight.
                if (state.selected != selected) refresh()
            } }
        }
    }
    override fun close() { closed = true; clear(); session?.close(); worker.execute { api.close(); deckApi?.close() }; worker.shutdown() }
}

internal fun updatedGameRows(rows: List<GameSummary>, game: GameSummary, included: Boolean): List<GameSummary> =
    if (!included) rows.filterNot { it.id == game.id }
    else if (rows.any { it.id == game.id }) rows.map { if (it.id == game.id) game else it }
    else rows + game
internal fun stableGameRows(old: List<GameSummary>, incoming: List<GameSummary>): List<GameSummary> {
    val byId = incoming.associateBy { it.id }
    val retained = old.mapNotNull { byId[it.id] }
    val ids = old.map { it.id }.toSet()
    return retained + incoming.filterNot { it.id in ids }
}
internal val gameNameAdjectives = listOf("Azure", "Crackling", "Moonlit", "Silver", "Arcane", "Velvet", "Ember", "Wandering",
    "Ancient", "Astral", "Amber", "Bewitched", "Celestial", "Crimson", "Crystal", "Duskborn", "Eldritch", "Enchanted",
    "Fabled", "Frosted", "Gilded", "Glimmering", "Hallowed", "Hidden", "Iridescent", "Jade", "Luminous", "Misty",
    "Mystic", "Obsidian", "Opal", "Radiant", "Runic", "Sapphire", "Shimmering", "Silent", "Starlit", "Twilight", "Verdant", "Whispering")
internal val gameNameNouns = listOf("Spark", "Mist", "Wand", "Grove", "Rune", "Prism", "Dragon", "Lantern",
    "Amulet", "Beacon", "Chalice", "Citadel", "Comet", "Crown", "Dawn", "Echo", "Familiar", "Flame", "Gale", "Gate",
    "Grimoire", "Haven", "Hydra", "Isle", "Labyrinth", "Lotus", "Moon", "Oracle", "Phoenix", "Portal", "Relic", "Sanctum",
    "Scepter", "Sigil", "Spire", "Star", "Tempest", "Thicket", "Tower", "Wisp")
internal fun gameNameSuggestion(): String = gameNameAdjectives.random() + " " + gameNameNouns.random()
internal fun lobbyLifecycleInterruption(failure: Throwable, interrupted: Boolean): Boolean = interrupted &&
    (failure is kotlinx.coroutines.CancellationException || transientServiceFailure(failure))

internal fun lobbyError(failure: Throwable): String = when ((failure as? LobbyFailure)?.code) {
    "invalid_search" -> "Use 1–64 username characters without control characters."
    "invalid_name" -> "Enter a game name."
    "contact_required" -> "Add this player to your contacts first."
    "authentication_required" -> "Please sign in again. Your unfinished request is kept."
    "try_later" -> "Too many requests. Wait a moment and retry."
    "request_conflict" -> "This request could not be reused. Close the dialog and try again."
    "not_configured" -> "The game service is not configured in this build."
    "basic_definitions_unavailable" -> "The five English M21 basic lands are missing. Update card records and retry."
    "deck_not_submitted" -> "Submit your pod deck before challenging."
    "opponent_not_member", "not_found" -> "This pod or player is no longer available."
    "invalid_opponent" -> "Choose another member of this pod."
    "invalid_basic_definitions" -> "Basic land definitions were refused. Update card records and retry."
    "invalid_deck" -> "This deck exceeds the game's 1,000-card limit."
    else -> "Could not reach the game service. Your input is kept. Check your connection and retry."
}
