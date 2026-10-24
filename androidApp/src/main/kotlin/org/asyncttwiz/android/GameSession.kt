package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import org.asyncttwiz.storage.GameplayTreeStore

/** Captured original identity, never a mutable selection or enqueue receipt. */
data class InspectionRequest(val requestId: String, val scope: GameScope, val accountId: String,
    val lifetimeId: String, val action: PlayAction.InspectFace)

sealed interface InspectionEvent {
    val request: InspectionRequest
    /** Both accepted tree proof and the still-applicable exact card/face/region are present. */
    data class Accepted(override val request: InspectionRequest, val disclosure: GameplayDisclosure,
        val mutation: GameplayMutation, val throughRevision: String, val state: PlayState) : InspectionEvent
    /** Original final no-op/rejection, or a definitive non-retryable admission refusal. */
    data class NotAccepted(override val request: InspectionRequest, val result: GameResult? = null,
        val admissionError: GameAdmissionError? = null) : InspectionEvent
}

/** Closing cancels presentation only. It neither withdraws/retries an original nor erases exposure. */
class InspectionSubscription internal constructor(val request: InspectionRequest) : AutoCloseable {
    @Volatile internal var active = true
        private set
    override fun close() { active = false }
}

/** Adapter for an ALREADY authorized ActivityTarget.GROUP (never a raw push hint). Group metadata
 * labels the captured target; the exact boundary alone selects review, including parent fallback. */
data class CapturedHistoryTarget(val accountId: String, val gameId: String, val playId: String,
    val groupId: String?, val boundaryNodeId: String)
data class CapturedHistoryReview(val target: CapturedHistoryTarget, val view: DownloadedHistoryState)

/** Main-thread, process-memory queue. Results settle intent; only revisioned reads change accepted state. */
class GameSession(private val connection: GameConnection, private val credential: () -> String?,
    private val post: (() -> Unit) -> Unit, private val authenticationRequired: (String) -> Unit,
    private val changed: (GameSummary) -> Unit, private val settled: (GameCommand, GameResult) -> Unit,
    private val statusChanged: () -> Unit, private val installationId: String = UUID.randomUUID().toString(),
    private val accountId: () -> String? = { null },
    private val treeCache: ((String, String, String) -> GameplayTreeStore?)? = null,
    private val treeWorker: ExecutorService = Executors.newSingleThreadExecutor(),
    private val now: () -> Long = { System.nanoTime() / 1_000_000 }) : AutoCloseable {
    private val uiThread = Thread.currentThread()
    private val caches = ConcurrentHashMap<String, GameplayTreeStore>()
    private val observedPlays = ConcurrentHashMap<String, String>()
    private val restoring = mutableSetOf<String>()
    private data class InspectionObserver(val subscription: InspectionSubscription, val current: () -> Boolean,
        val observed: (InspectionEvent) -> Unit)
    private val inspections = linkedMapOf<String, InspectionObserver>()
    private data class Stream(val id: String = UUID.randomUUID().toString(), var generation: String? = null,
        var head: Boolean = false, var opening: Boolean = false, var opened: Boolean = false,
        var lastGeneration: String? = null, var recoveryIds: List<String> = emptyList(), var awaitingReady: Boolean = false)
    private val processId = UUID.randomUUID().toString()
    private val streams = linkedMapOf<String, Stream>()
    private val summaries = mutableMapOf<String, GameSummary>()
    private val unavailableGames = mutableSetOf<String>()
    private val pending = linkedMapOf<String, GameCommand>()
    private data class PreparationReplica(val revision: String, val state: LobbyPreparation)
    private data class Selection(val command: GameCommand, val actor: String, val source: SavedDeck,
        val definitions: List<SubmittedDefinition>, var commitRevision: String? = null)
    private val preparations = mutableMapOf<String, PreparationReplica>()
    private val plays = mutableMapOf<String, GameplayReplica>()
    private data class Recovery(val ids: List<String>, val notAdmitted: Set<String>, var through: String,
        val tail: MutableList<GameRevision> = mutableListOf())
    private val recoveries = mutableMapOf<String, Recovery>()
    @Volatile private var account: String? = null
    private data class ResultIntent(val command: GameCommand, val actor: String, var commitRevision: String? = null)
    private val resultIntents = linkedMapOf<String, ResultIntent>()
    private val messages = mutableMapOf<String, List<ChatMessage>>()
    private val chatRevisions = mutableMapOf<String, String>()
    private val chatSubmissions = linkedMapOf<String, ChatSubmission>()
    fun chat(gameId: String) = messages[gameId].orEmpty()
    fun outgoingChat(gameId: String) = chatSubmissions.values.filter { it.command.scope.gameId == gameId }
    fun sendChat(game: GameSummary, text: String): Boolean {
        val current = summary(game)
        if (game.lifecycle != GameLifecycle.ACTIVE || current.lifecycle != GameLifecycle.ACTIVE || current.playId == null ||
            game.playId != current.playId || !ChatValidation.text(text)) return false
        val command = command(current, GameAction.SendChat(text)) ?: return false
        chatSubmissions[command.requestId] = ChatSubmission(command)
        pending[command.requestId] = command
        error = null; flush(); statusChanged(); return true
    }
    private val acceptedRevisions = mutableMapOf<String, String>()
    private data class Readiness(val command: GameCommand, val actor: String, var commitRevision: String? = null)
    private val readiness = linkedMapOf<String, Readiness>()
    private val selections = linkedMapOf<String, Selection>()
    private data class Transfer(val command: GameCommand, val actor: String,
        var commitRevision: String? = null, var withdrawn: Boolean = false)
    private data class Captured(val intent: PreparedTransfer, val actor: String, var entry: Int = 0, var offset: Int = 0) {
        fun remaining() = intent.copy(entries = intent.entries.drop(entry).mapIndexed { index, range ->
            if (index == 0) range.copy(firstIndex = (GameWire.ordinal(range.firstIndex) + offset).toString(), quantity = range.quantity - offset) else range })
        fun next(): GameAction.TransferPreparedCard {
            val range = intent.entries[entry]; val action = intent.action(range, offset++)
            if (offset == range.quantity) { entry++; offset = 0 }
            return action
        }
        val finished get() = entry == intent.entries.size
    }
    private val transfers = linkedMapOf<String, Transfer>()
    private val captured = ArrayDeque<Captured>()
    private val sent = mutableSetOf<String>()
    @Volatile private var token: String? = null
    @Volatile private var epoch = 0L
    private var connected = false
    private var welcome = false
    private var lastCredential: String? = null
    private var lastPing = 0L
    private val connectionDemand = mutableMapOf<String, Long>()
    private val historyDownloads = mutableSetOf<String>()
    private val uncertainGames = mutableSetOf<String>()
    @Volatile private var closed = false
    var error: String? = null
        private set
    val ready get() = connected && welcome
    fun reconnecting(gameId: String) = !ready(gameId) && connectionDemand[gameId]?.let { now() - it >= 3_000 } == true
    fun dismissError() { error = null; statusChanged() }
    fun foreground() { connectionDemand.clear(); tick() }
    /** Only a previously authorized, retained active play may capture originals during a short
     * reconnect wait. Nothing is sent until stream recovery and its accepted head are complete. */
    private fun activeRequest(game: GameSummary): Boolean {
        checkAccount()
        if (ready(game.id)) { connectionDemand.remove(game.id); uncertainGames.remove(game.id); return true }
        if (closed || !available(game.id) || credential() == null || credential() != lastCredential ||
            streams[game.id]?.lastGeneration == null || acceptedPlay(game) == null) return false
        val started = connectionDemand.getOrPut(game.id, now)
        tick()
        // Do not extend an uncertain old socket's predicted chain before its outcomes are known.
        if (game.id in uncertainGames || game.id in recoveries) return false
        return now() - started < 3_000
    }
    fun available(gameId: String) = gameId !in unavailableGames && summaries[gameId]?.lifecycle !in listOf(GameLifecycle.CANCELLED, GameLifecycle.DELETED)
    fun ready(gameId: String) = ready && available(gameId) && gameId !in recoveries && streams[gameId]?.let { it.generation != null && it.head } == true
    /** A negative ordinary read fences personal context, not uncertain admitted originals. */
    fun unavailable(gameId: String) {
        unavailableGames.add(gameId); clearContext(gameId)
        streams[gameId]?.let { it.opening = false; it.generation = null }
        statusChanged()
    }
    private fun clearContext(gameId: String) {
        observedPlays.remove(gameId)
        caches.remove(gameId)?.invalidate()
        inspections.entries.removeAll { (_, observer) ->
            (observer.subscription.request.scope.gameId == gameId).also { if (it) observer.subscription.close() }
        }
        preparations.remove(gameId); plays.remove(gameId); acceptedRevisions.remove(gameId)
        recoveries.remove(gameId)
        messages.remove(gameId); chatRevisions.remove(gameId)
        chatSubmissions.entries.removeAll { it.value.command.scope.gameId == gameId }
        selections.entries.removeAll { it.value.command.scope.gameId == gameId }
        transfers.entries.removeAll { it.value.command.scope.gameId == gameId }
        readiness.entries.removeAll { it.value.command.scope.gameId == gameId }
        resultIntents.entries.removeAll { it.value.command.scope.gameId == gameId }
        captured.removeAll { it.intent.gameId == gameId }
        streams[gameId]?.head = false
    }
    fun pending(gameId: String) = pending.values.any { it.scope.gameId == gameId } || selections.values.any { it.command.scope.gameId == gameId } ||
        transfers.values.any { it.command.scope.gameId == gameId } || readiness.values.any { it.command.scope.gameId == gameId } || captured.any { it.intent.gameId == gameId } ||
        plays[gameId]?.pendingIds?.isNotEmpty() == true || resultIntents.values.any { it.command.scope.gameId == gameId }
    fun queuedTransfers(gameId: String) = captured.filter { it.intent.gameId == gameId }.sumOf { it.remaining().count }
    fun acceptedPreparation(gameId: String) = preparations[gameId]?.state
    fun acceptedPlay(game: GameSummary) = plays[game.id]?.accepted?.takeIf { summary(game).let { current ->
        current.lifecycle == GameLifecycle.ACTIVE && current.playId == it.id } }
    fun play(game: GameSummary) = plays[game.id]?.display?.takeIf { acceptedPlay(game)?.id == it.id }
    fun retainedTree(gameId: String) = plays[gameId]?.tree?.tree
    /** Personal read-only review, including offline. Cold replay and cursor persistence are worker
     * work; account/incarnation/clear fences are checked before disk and again before UI delivery. */
    fun review(gameId: String, nodeId: String?, complete: (DownloadedHistoryState) -> Unit): Boolean {
        checkAccount()
        val retained = plays[gameId]?.tree?.tree ?: return false
        val revision = plays[gameId]?.revision?.let(GameWire::ordinal) ?: return false
        val cache = caches[gameId]
        val generation = epoch; val owner = account
        fun valid() = !closed && generation == epoch && owner == account && sameAccount() &&
            observedPlays[gameId] == retained.initial.id
        treeWorker.execute {
            if (!valid()) return@execute
            val cursor = if (nodeId == null) retained.currentNodeId else retained.resolveCursor(nodeId)
            val state = cursor?.let(retained::prepareReview)
            if (!valid()) return@execute
            cache?.reviewCursor(if (nodeId == null) null else cursor)
            val view = DownloadedHistoryState(revision, cursor, nodeId != null, state,
                error = if (state == null) "Downloaded history is unavailable." else null,
                contextVersion = cursor?.let(retained::contextVersion))
            post { if (valid()) complete(view) }
        }
        return true
    }

    /** Personal exact-boundary route for an authorized activity/history consumer. Stale scope/access
     * or route lifetime is refused, never replaced by current or converted to a shared control. */
    fun reviewTarget(target: CapturedHistoryTarget, isCurrent: () -> Boolean,
        complete: (CapturedHistoryReview) -> Unit): Boolean {
        checkAccount()
        val summary = summaries[target.gameId] ?: return false
        val retained = plays[target.gameId]?.tree?.tree ?: return false
        val revision = plays[target.gameId]?.revision?.let(GameWire::ordinal) ?: return false
        val cache = caches[target.gameId]
        if (!listOfNotNull(target.accountId, target.gameId, target.playId, target.groupId, target.boundaryNodeId).all(DeckValidation::uuid) ||
            account != target.accountId || summary.lifecycle != GameLifecycle.ACTIVE || summary.playId != target.playId ||
            retained.initial.id != target.playId || retained.initial.gameId != target.gameId ||
            target.accountId !in retained.initial.board.players.map { it.accountId } ||
            target.accountId !in listOf(summary.inviter.accountId, summary.recipient.accountId) || !isCurrent()) return false
        val generation = epoch
        fun valid() = !closed && generation == epoch && account == target.accountId && sameAccount() &&
            observedPlays[target.gameId] == target.playId && runCatching(isCurrent).getOrDefault(false)
        treeWorker.execute {
            if (!valid()) return@execute
            val resolved = retained.resolveCursor(target.boundaryNodeId)
            val state = resolved?.let(retained::prepareReview)
            if (!valid()) return@execute
            if (state != null) cache?.reviewCursor(resolved)
            val review = CapturedHistoryReview(target, DownloadedHistoryState(revision, resolved, reviewing = true,
                state = state, error = if (state == null) "This retained history boundary is unavailable." else null,
                contextVersion = resolved?.let(retained::contextVersion)))
            post { if (valid()) complete(review) }
        }
        return true
    }

    /** Explicit read-only refresh uses the existing baseline/recovery operation, never a lazy branch API. */
    fun downloadHistory(game: GameSummary): Boolean {
        checkAccount()
        if (closed || !activeRequest(game)) return false
        if (!ready(game.id)) { historyDownloads.add(game.id); statusChanged(); return true }
        recover(game.id, summary(game).revision)
        return true
    }
    fun withdrawGameplayPrediction(gameId: String, requestId: String) { plays[gameId]?.withdraw(requestId); statusChanged() }
    fun preparation(game: GameSummary): LobbyPreparation? {
        val current = summary(game)
        if (current.lifecycle != GameLifecycle.LOBBY) return null
        var display = preparations[game.id]?.state?.takeIf { it.roundId == current.preparationRound } ?: return null
        for (selection in selections.values.filter { it.command.scope.gameId == game.id }) {
            display = PreparationRules.select(current, display, selection.actor, selection.command.requestId,
                selection.command.scope, selection.command.action as GameAction.SelectDeck, selection.source,
                selection.definitions).state ?: display
        }
        // Ready is submitted only after earlier predictions settle. Later per-card edits follow it.
        for (intent in readiness.values.filter { it.command.scope.gameId == game.id }) {
            display = StartRules.change(current, display, intent.actor, intent.command.scope, intent.command.action).state ?: display
        }
        for (transfer in transfers.values.filter { it.command.scope.gameId == game.id && !it.withdrawn }) {
            val next = PreparationRules.transfer(current, display, transfer.actor, transfer.command.scope,
                transfer.command.action as GameAction.TransferPreparedCard).state
            if (next == null) transfer.withdrawn = true else display = next
        }
        if (current.lifecycle == GameLifecycle.LOBBY) for (capture in captured.filter { it.intent.gameId == game.id }) {
            display = PreparationRules.previewTransfer(display, capture.actor, capture.remaining())
        }
        return display
    }
    fun summary(game: GameSummary): GameSummary = summaries[game.id]?.takeIf {
        ended(it) || newerOrEqual(it.revision, game.revision) } ?: game
    private fun ended(game: GameSummary) = game.lifecycle !in listOf(GameLifecycle.LOBBY, GameLifecycle.ACTIVE)
    fun watch(games: List<GameSummary>) {
        checkAccount()
        for (game in games) {
            install(game, game.revision, null, null)
            streams.getOrPut(game.id) { Stream() }
        }
        tick()
        if (treeCache != null) for (game in games) {
            val current = summary(game)
            observePlay(current)
            if (current.lifecycle == GameLifecycle.ACTIVE && current.playId != null &&
                plays[game.id]?.accepted == null && restoring.add(game.id)) loadCached(current)
        }
    }
    fun tick() {
        if (closed) return
        checkAccount()
        val current = credential()
        if (current != token) disconnect()
        if (current == null || streams.isEmpty()) return
        if (!connected) {
            token = current; lastCredential = current; connected = true; lastPing = now(); val generation = ++epoch
            statusChanged()
            connection.connect(current, object : GameConnection.Listener {
                private fun deliver(action: () -> Unit) = post {
                    if (!closed && epoch == generation && credential() == current && sameAccount()) action()
                }
                override fun opened() = deliver { send(GameHello(processId, installationId)) }
                override fun message(message: GameServerMessage) = deliver {
                    if (runCatching { receive(message) }.isFailure) unavailable()
                }
                override fun disconnected(unauthorized: Boolean) = deliver {
                    unavailable()
                    if (unauthorized) authenticationRequired(current)
                }
            })
        } else if (welcome) {
            openStreams()
            // The server's traffic lease counts protocol messages, not WebSocket control pings.
            if (now() - lastPing >= 25_000) { lastPing = now(); send(GamePing(UUID.randomUUID().toString())) }
        }
        connectionDemand.keys.removeAll { ready(it) }
        uncertainGames.removeAll { ready(it) }
        statusChanged()
    }
    fun submit(game: GameSummary, action: GameAction): Boolean {
        if (action == GameAction.DeleteGame) return deleteGame(game)
        if (action is GameAction.Play || action is GameAction.AcceptResult || action is GameAction.SendChat || action is GameAction.MakeCurrent || action is GameAction.BranchArchive ||
            action == GameAction.Undo || action == GameAction.Redo || action is GameAction.SelectDeck || action is GameAction.TransferPreparedCard ||
            action is GameAction.SetStarter || action == GameAction.Ready || action == GameAction.WithdrawReady) return false
        val current = summary(game)
        if (current.lifecycle != GameLifecycle.LOBBY || current.preparationRound != game.preparationRound) return false
        return enqueue(game, action) != null
    }
    /** Capture one original in memory; send now or after authorized reconnect recovery. */
    fun gameplay(game: GameSummary, actor: String, action: PlayAction, invocationId: String? = null,
        continuation: Continuation? = null, captured: (GameCommand) -> Unit = {}): Boolean =
        gameplayCommand(game, actor, GameAction.Play(action), invocationId, continuation, captured) != null

    /** Explicit logged inspection on the same original queue. Callers supply a UI lifetime guard
     * and close the subscription on dismissal. Only Accepted may drive ManualActions.inspected. */
    fun inspectFace(game: GameSummary, actor: String, action: PlayAction.InspectFace, lifetimeId: String,
        isCurrent: () -> Boolean, observed: (InspectionEvent) -> Unit,
        continuation: Continuation? = null, captured: (GameCommand) -> Unit = {}): InspectionSubscription? {
        if (lifetimeId.isBlank() || lifetimeId.length > 200 || !isCurrent()) return null
        var subscription: InspectionSubscription? = null
        gameplayCommand(game, actor, GameAction.Play(action), null, continuation) { command ->
            captured(command)
            inspections.entries.removeAll { !it.value.subscription.active }
            val bound = InspectionSubscription(InspectionRequest(command.requestId, command.scope, actor, lifetimeId, action))
            subscription = bound
            inspections[command.requestId] = InspectionObserver(bound, isCurrent, observed)
        } ?: return null
        return subscription
    }

    /** Exact typed shared controls use the SAME original queue and processing-time target.
     * No fixed Undo target, alternate endpoint, or durable offline command queue. */
    fun historyControl(game: GameSummary, actor: String, action: GameAction): Boolean {
        if (action != GameAction.Undo && action != GameAction.Redo && action !is GameAction.MakeCurrent &&
            action !is GameAction.BranchArchive) return false
        return gameplayCommand(game, actor, action, null, null) != null
    }

    private fun gameplayCommand(game: GameSummary, actor: String, action: GameAction, invocationId: String?,
        continuation: Continuation?, captured: (GameCommand) -> Unit = {}): GameCommand? {
        checkAccount()
        val current = summary(game)
        if (closed || !activeRequest(current) || pending.size >= 256 ||
            current.lifecycle != GameLifecycle.ACTIVE || current.playId == null ||
            (account != null && account != actor)) return null
        val replica = plays[game.id] ?: return null
        // Controls are processing-time operations, not predicted boards. A drag captured from the
        // pre-Undo display must not follow that Undo with a now-invalid source/instance reference.
        if (action is GameAction.Play && resultIntents.values.any { it.command.scope.gameId == game.id &&
                it.command.action !is GameAction.AcceptResult }) return null
        if (replica.pendingIds.size >= 256 || replica.accepted?.id != current.playId) return null
        val stream = streams.getValue(game.id)
        val command = GameCommand(UUID.randomUUID().toString(), GameScope(game.id, playId = current.playId),
            action, stream.id, stream.generation ?: stream.lastGeneration!!, invocationId = invocationId, continuation = continuation)
        if (!GameWire.valid(command)) return null
        // A control can become eligible after an already-inflight original. Failure to predict is
        // not authority to suppress the server's processing-time candidate.
        // Selective controls can require a cold suffix replay. Do not run that server eligibility
        // calculation on the UI thread or block a following original on its acknowledgement.
        // The accepted delta is prepared by the serial tree worker before publication.
        if (action is GameAction.Play && !replica.predict(command, actor)) return null
        pending[command.requestId] = command
        if (action !is GameAction.Play) resultIntents[command.requestId] = ResultIntent(command, actor)
        captured(command)
        error = null; flush(); statusChanged(); return command
    }
    fun suggestResult(game: GameSummary, actor: String, target: ResultTarget, outcome: SuggestedResult): Boolean {
        val current = summary(game)
        val play = play(current) ?: return false
        if (ResultActions.capture(current, play) != target) return false
        return gameplay(current, actor, PlayAction.SuggestResult(outcome))
    }
    fun acceptResult(game: GameSummary, actor: String, target: ResultAcceptanceTarget): Boolean {
        val current = summary(game)
        // Only an accepted current suggestion is actionable; a local prediction is not acceptance authority.
        val play = acceptedPlay(current) ?: return false
        if (ResultActions.capture(current, play) != target.play || play.board.resultSuggestion?.id != target.suggestionId ||
            play.board.resultSuggestion?.author == actor || actor !in play.board.players.map { it.accountId }) return false
        if (resultIntents.values.any { it.command.scope.playId == target.play.playId && it.command.action == GameAction.AcceptResult(target.suggestionId) }) return false
        val command = resultCommand(target.play, GameAction.AcceptResult(target.suggestionId)) ?: return false
        return enqueueResult(command, actor)
    }
    private fun resultCommand(target: ResultTarget, action: GameAction): GameCommand? {
        val game = summaries[target.gameId] ?: return null
        if (!activeRequest(game) || pending.size >= 256) return null
        val stream = streams.getValue(target.gameId)
        return GameCommand(UUID.randomUUID().toString(), GameScope(target.gameId, playId = target.playId),
            action, stream.id, stream.generation ?: stream.lastGeneration!!).takeIf(GameWire::valid)
    }
    private fun enqueueResult(command: GameCommand, actor: String): Boolean {
        resultIntents[command.requestId] = ResultIntent(command, actor)
        pending[command.requestId] = command
        error = null; flush(); statusChanged(); return true
    }
    fun deleteGame(game: GameSummary): Boolean {
        val current = summary(game)
        if (game.lifecycle != GameLifecycle.ACTIVE || game.playId == null ||
            current.lifecycle != GameLifecycle.ACTIVE || current.playId != game.playId) return false
        return enqueue(game, GameAction.DeleteGame) != null
    }
    fun readiness(game: GameSummary, actor: String, action: GameAction): Boolean {
        if (action !is GameAction.SetStarter && action != GameAction.Ready && action != GameAction.WithdrawReady) return false
        val current = summary(game)
        val base = preparation(current) ?: return false
        val command = command(current, action) ?: return false
        if (StartRules.change(current, base, actor, command.scope, action).state == null) return false
        readiness[command.requestId] = Readiness(command, actor)
        pending[command.requestId] = command
        error = null; flush(); statusChanged(); return true
    }
    fun selectDeck(game: GameSummary, actor: String, source: SavedDeck, definitions: List<SubmittedDefinition>): Boolean {
        val current = summary(game)
        val base = preparation(current) ?: return false
        val action = GameAction.SelectDeck(source.deck.id, source.deck.revision)
        val command = command(current, action) ?: return false
        if (PreparationRules.select(current, base, actor, command.requestId, command.scope, action, source, definitions).state == null) return false
        selections[command.requestId] = Selection(command, actor, source, definitions)
        pending[command.requestId] = command
        error = null; flush(); statusChanged(); return true
    }
    /** One release captures a row, but produces ordinary individual commands, never an atomic bulk. */
    fun transfer(game: GameSummary, actor: String, intent: PreparedTransfer): Boolean {
        val current = summary(game)
        val deck = preparation(current)?.players?.find { it.accountId == actor }?.deck ?: return false
        if (!ready(game.id) || credential() != token || current.lifecycle != GameLifecycle.LOBBY ||
            intent.gameId != current.id || intent.roundId != current.preparationRound || intent.deckId != deck.id ||
            intent.selectionRequestId != deck.selectionRequestId || intent.entries.isEmpty() ||
            intent.entries.any { it.quantity <= 0 || it.section != intent.source } ||
            (actor != current.inviter.accountId && (actor != current.recipient.accountId || current.invitationState != InvitationState.ACCEPTED))) return false
        captured.add(Captured(intent, actor))
        error = null; flush(); statusChanged(); return true
    }
    /** Withdraw display only. Already sent originals still recover and can be accepted normally. */
    fun withdrawTransferPrediction(requestId: String) { transfers[requestId]?.withdrawn = true; statusChanged() }
    private fun command(game: GameSummary, action: GameAction): GameCommand? {
        if (action is GameAction.SendChat) { if (!activeRequest(game)) return null }
        else if (!ready(game.id) || credential() != token) return null
        if ((action !is GameAction.SendChat && pending(game.id)) || pending.size >= 256) return null
        val stream = streams.getValue(game.id)
        val scope = if (action == GameAction.DeleteGame || action is GameAction.SendChat) GameScope(game.id, playId = game.playId)
            else GameScope(game.id, game.preparationRound,
                if (action == GameAction.AcceptInvitation || action == GameAction.DeclineInvitation || action == GameAction.Reinvite) game.invitationId else null)
        return GameCommand(UUID.randomUUID().toString(), scope,
            action, stream.id, stream.generation ?: stream.lastGeneration!!)
    }
    private fun enqueue(game: GameSummary, action: GameAction): GameCommand? {
        val command = command(game, action) ?: return null
        pending[command.requestId] = command
        error = null; flush(); statusChanged(); return command
    }
    private fun openStreams() {
        for ((id, stream) in streams) if (!stream.opening) {
            if (!available(id) && pending.values.none { it.scope.gameId == id }) continue
            stream.opening = true
            val ids = pending.values.filter { it.scope.gameId == id }.map { it.requestId }
            stream.recoveryIds = ids
            stream.awaitingReady = true
            if (!send(if (stream.opened) ResumeGameStream(id, stream.id, ids) else OpenGameStream(id, stream.id))) return
        }
    }
    private fun receive(message: GameServerMessage) {
        when (message) {
            is GameWelcome -> {
                require(message.protocolMajor == 1 && message.minor == 0 && message.engineVersion == 1 && message.readFormat == 1)
                welcome = true; error = null; openStreams()
            }
            is GameStreamReady -> {
                val stream = streams[message.gameId] ?: return
                if (!stream.awaitingReady) return
                require(stream.id == message.streamId); GameWire.ordinal(message.generation)
                if (stream.lastGeneration?.let { !newerOrEqual(message.generation, it) || message.generation == it } == true) return
                stream.generation = message.generation; stream.opened = true
                stream.lastGeneration = message.generation
                stream.awaitingReady = false
                require(message.statuses.size == stream.recoveryIds.size &&
                    message.statuses.map { it.requestId }.toSet() == stream.recoveryIds.toSet())
                message.ended?.let {
                    require(it.id == message.gameId && ended(it))
                    install(it, it.revision, null, null)
                }
                // Successful ordinary stream authorization is fresh evidence of availability;
                // a list/head omission alone was never a fabricated cancellation revision.
                if (message.ended == null) unavailableGames.remove(message.gameId)
                for (status in message.statuses) when (status.status) {
                    "final" -> { val result = requireNotNull(status.result); require(result.requestId == status.requestId); result(result) }
                    "pending" -> sent.add(status.requestId)
                    "notAdmitted" -> if (available(message.gameId)) sent.remove(status.requestId) else {
                        pending.remove(status.requestId); sent.remove(status.requestId)
                    }
                    else -> error("Invalid recovery status")
                }
                if (available(message.gameId)) send(SubscribeGame(message.gameId, summaries[message.gameId]?.revision ?: "0"))
                flush()
            }
            is GameHead -> {
                require(message.gameId == message.summary.id && message.revision == message.summary.revision)
                if (message.gameId !in streams) return
                // Fence old-incarnation reads before they can create a replica or trigger recovery.
                // A leading HTTP summary is deliberately not this accepted-state boundary.
                if (acceptedRevisions[message.gameId]?.let { !newerOrEqual(message.revision, it) } == true) return
                if (summaries[message.gameId]?.let { ended(it) && !ended(message.summary) } == true) return
                installChat(message.gameId, message.revision, message.chat, snapshot = true)
                observePlay(message.summary)
                recoveries[message.gameId]?.let { if (newerOrEqual(message.revision, it.through)) it.through = message.revision }
                if (message.play != null) {
                    val replica = plays.getOrPut(message.gameId) { GameplayReplica() }
                    if (replica.revision == message.revision) {
                        streams[message.gameId]?.head = true
                    } else if (treeCache == null && !message.retainedTreeRequired && replica.accepted == null && replica.root(message)) {
                        install(message.summary, message.revision, null, message.play)
                        streams[message.gameId]?.head = true
                    } else if (replica.revision?.let { newerOrEqual(it, message.revision) } != true) recover(message.gameId, message.revision)
                } else {
                    install(message.summary, message.revision, message.preparation, null)
                    streams[message.gameId]?.head = true
                }
                flush()
            }
            is GameRevision -> {
                require(message.gameId == message.summary.id && message.scope.gameId == message.gameId)
                if (message.gameId !in streams) return
                // Fence BEFORE observePlay can invalidate a newer incarnation's durable cache.
                // Duplicate accepted deliveries can still correlate exact originals, but cannot
                // advance/replace storage or start recovery against an obsolete through-revision.
                if (acceptedRevisions[message.gameId]?.let { GameWire.ordinal(message.resultRevision) <= GameWire.ordinal(it) } == true) {
                    plays[message.gameId]?.takeIf { it.accepted?.id == message.summary.playId }?.apply(message)
                    correlateSelections(message)
                    notifyInspections(); flush(); statusChanged(); return
                }
                if (summaries[message.gameId]?.let { ended(it) && !ended(message.summary) } == true) return
                message.chatMessage?.let { installChat(message.gameId, message.resultRevision, listOf(it), snapshot = false) }
                observePlay(message.summary)
                if (message.summary.lifecycle == GameLifecycle.ACTIVE && message.summary.playId != null) {
                    recoveries[message.gameId]?.let {
                        it.tail.add(message)
                        if (newerOrEqual(message.resultRevision, it.through)) it.through = message.resultRevision
                    }
                    val replica = plays.getOrPut(message.gameId) { GameplayReplica() }
                    if (treeCache != null && message.gameId !in recoveries) {
                        applyTree(message)
                    } else if (treeCache != null && message.gameId in recoveries) {
                        // Recovery owns one isolated generation and its interleaved tail.
                    } else if (replica.accepted == null && message.play != null && message.play!!.currentNodeId == message.play!!.root.id) {
                        replica.root(GameHead(message.gameId, message.resultRevision, message.summary, play = message.play))
                        install(message.summary, message.resultRevision, null, message.play)
                    } else if (replica.apply(message)) install(message.summary, message.resultRevision, null, replica.accepted)
                    else {
                        if (message.gameId !in recoveries) { recover(message.gameId, message.resultRevision); recoveries[message.gameId]?.tail?.add(message) }
                    }
                } else install(message.summary, message.resultRevision, message.preparation, null)
                // A correlated accepted revision is enough even when its result has not arrived yet.
                correlateSelections(message)
            }
            is GameResult -> result(message)
            is GameReceipt -> Unit // Receipt is not acceptance and never removes an uncertain original.
            is GameAdmissionError -> {
                if (message.code == "unauthorized") { val rejected = token; unavailable(); rejected?.let(authenticationRequired) }
                else if (message.retryable || message.code == "streamFenced") unavailable()
                else {
                    val original = message.requestId?.let(pending::get)
                    if (message.code == "accessDenied") message.gameId?.let(::unavailable)
                    message.requestId?.let { id -> inspections.remove(id)?.let { observer ->
                        presentInspection(observer, InspectionEvent.NotAccepted(observer.subscription.request, admissionError = message)) } }
                    message.requestId?.let { id ->
                        chatSubmissions[id]?.let { chatSubmissions[id] = it.copy(failure = "Not sent. This request could not be used.") }
                        pending.remove(id)?.let { plays[it.scope.gameId]?.retire(id) }; selections.remove(id); transfers.remove(id); readiness.remove(id); resultIntents.remove(id); sent.remove(id)
                    }
                    if (original != null) error = historyControlFailure(original.action, message.code)
                        ?: "This game changed or the request could not be used. Refresh and try again."
                }
            }
            is GameUpgradeRequired -> { unavailable(); error = "Update the app to connect to this game service." }
            is GamePong -> Unit
        }
        flush()
        statusChanged()
    }
    private fun install(game: GameSummary, revision: String, preparation: LobbyPreparation?, play: PlayState?) {
        require(game.revision == revision); GameWire.ordinal(revision)
        // An HTTP summary can lead the actual replica. It must not prevent catching that replica up.
        if (acceptedRevisions[game.id]?.let { !newerOrEqual(revision, it) } == true) return
        val old = summaries[game.id]
        if (old != null && ended(old) && !ended(game)) return
        if (game.lifecycle != GameLifecycle.ACTIVE) plays.remove(game.id)
        if (ended(game)) require(preparation == null && play == null)
        if (game.id in unavailableGames && old != null && revision == old.revision &&
            game.lifecycle in listOf(GameLifecycle.LOBBY, GameLifecycle.ACTIVE)) return
        if (old != null && GameWire.ordinal(revision) > GameWire.ordinal(old.revision)) unavailableGames.remove(game.id)
        observePlay(game)
        if (play != null) {
            require(game.lifecycle == GameLifecycle.ACTIVE && play.gameId == game.id && play.id == game.playId &&
                play.preparationRound == game.preparationRound && play.engineVersion == 1 && play.readFormat == 1)
        }
        if (game.lifecycle != GameLifecycle.LOBBY) {
            preparations.remove(game.id)
            captured.removeAll { it.intent.gameId == game.id }
        }
        if (game.lifecycle == GameLifecycle.CANCELLED || game.lifecycle == GameLifecycle.DELETED) clearContext(game.id)
        if (preparation != null && game.lifecycle == GameLifecycle.LOBBY) {
            require(preparation.gameId == game.id && preparation.roundId == game.preparationRound)
            val old = preparations[game.id]
            if (old == null || newerOrEqual(revision, old.revision)) {
                preparations[game.id] = PreparationReplica(revision, preparation)
                val applied = preparation.players.mapNotNull { it.deck?.selectionRequestId }.toSet()
                selections.entries.removeAll { it.key in applied }
            }
        }
        if ((preparation != null && game.lifecycle == GameLifecycle.LOBBY) || play != null || ended(game)) acceptedRevisions[game.id] = revision
        if (summaries[game.id]?.let { newerOrEqual(revision, it.revision) } != false) summaries[game.id] = game
        retireSelections(game.id)
        changed(summary(game))
        notifyInspections()
    }
    private fun installChat(gameId: String, revision: String, incoming: List<ChatMessage>, snapshot: Boolean) {
        if (!available(gameId)) return
        require(incoming.all { it.gameId == gameId && GameWire.ordinal(it.revision) <= GameWire.ordinal(revision) })
        val oldRevision = chatRevisions[gameId]
        if (snapshot && oldRevision != null && !newerOrEqual(revision, oldRevision)) return
        val retained = if (snapshot) incoming else chat(gameId) + incoming
        messages[gameId] = retained.distinctBy { it.id }.sortedBy { GameWire.ordinal(it.revision) }
        if (oldRevision == null || newerOrEqual(revision, oldRevision)) chatRevisions[gameId] = revision
        incoming.forEach { chatSubmissions.remove(it.id) }
    }
    private fun result(result: GameResult) {
        val original = pending[result.requestId] ?: return
        require(result.scope == original.scope)
        plays[original.scope.gameId]?.result(result)
        pending.remove(result.requestId); sent.remove(result.requestId)
        if (result.outcome != "accepted") chatSubmissions[result.requestId]?.let {
            chatSubmissions[result.requestId] = it.copy(failure = "Not sent. The original play is no longer available or this request was refused.")
        }
        selections[result.requestId]?.let { selection ->
            if (result.outcome == "accepted") selection.commitRevision = requireNotNull(result.commitRevision)
            else selections.remove(result.requestId)
            retireSelections(original.scope.gameId)
        }
        transfers[result.requestId]?.let { transfer ->
            if (result.outcome == "accepted") transfer.commitRevision = requireNotNull(result.commitRevision)
            else transfers.remove(result.requestId)
            retireSelections(original.scope.gameId)
        }
        readiness[result.requestId]?.let { intent ->
            if (result.outcome == "accepted") intent.commitRevision = requireNotNull(result.commitRevision)
            else readiness.remove(result.requestId)
            retireSelections(original.scope.gameId)
        }
        resultIntents[result.requestId]?.let { intent ->
            if (result.outcome == "accepted") intent.commitRevision = requireNotNull(result.commitRevision)
            else resultIntents.remove(result.requestId)
            retireSelections(original.scope.gameId)
        }
        error = if (result.outcome in setOf("accepted", "noOp")) null else historyControlFailure(original.action, result.reason) ?: when (result.reason) {
            "sourceChanged" -> "This saved deck changed. Choose it again to use the current version."
            "sourceUnavailable" -> "This saved deck is no longer available. Choose another deck."
            "deckRequired" -> "Select your deck to get ready."
            "starterRequired" -> "Choose the starting player to get ready."
            else -> "This game has changed. Refresh and try again."
        }
        if (result.outcome != "accepted") inspections.remove(result.requestId)?.let { observer ->
            presentInspection(observer, InspectionEvent.NotAccepted(observer.subscription.request, result = result)) }
        notifyInspections()
        settled(original, result)
    }

    private fun historyControlFailure(action: GameAction?, reason: String?): String? {
        val verb = when (action) { GameAction.Undo -> "undo"; GameAction.Redo -> "redo"; else -> return null }
        val explanation = when (reason) {
            "revealingTarget" -> "that action revealed hidden information. Use History to review it, or keep playing."
            "noCandidate" -> "there is no action to $verb. Keep playing."
            "forkBoundary" -> "that would change an existing branch. Use History to choose a continuation."
            "suffixUnavailable", "workLimit" -> "that history is too large or branched to check safely. Use History to review it, or keep playing."
            "referenceBlocked" -> "a later event refers to that action. Use History to review it, or keep playing."
            "replayBlocked", "targetTransitionBlocked" -> "that would change another recorded transition. Use History to review it, or keep playing."
            "missingAnchor" -> "the original place for that action is no longer available. Keep playing or open History."
            "lifecycleEnded", "inapplicable" -> "the original play is no longer active. Return to the games list."
            "accessDenied" -> "this game is no longer available to this account. Return to the games list."
            "historyUnavailable", "missingReference" -> "the required history is unavailable. Reconnect to synchronize it."
            else -> "the request could not be used. Keep playing or open History."
        }
        return "Can't $verb: $explanation"
    }

    private fun currentInspection(observer: InspectionObserver): Boolean {
        val request = observer.subscription.request
        return observer.subscription.active && !closed && sameAccount() &&
            (account == null || account == request.accountId) &&
            observedPlays[request.scope.gameId] == request.scope.playId && runCatching(observer.current).getOrDefault(false)
    }

    private fun presentInspection(observer: InspectionObserver, event: InspectionEvent) {
        if (!currentInspection(observer)) { observer.subscription.close(); return }
        observer.subscription.close() // consume before callback: duplicate state/results cannot reopen it.
        observer.observed(event)
    }

    private fun notifyInspections() {
        for ((id, observer) in inspections.toMap()) {
            if (!currentInspection(observer)) { inspections.remove(id); observer.subscription.close(); continue }
            val request = observer.subscription.request
            val replica = plays[request.scope.gameId] ?: continue
            val state = replica.accepted ?: continue
            val mutation = replica.tree?.tree?.nodes?.get(CommandIds.uuidV5(id, "board-node")) ?: continue
            if (mutation.requestId != id || mutation.author != request.accountId || mutation.action != request.action) continue
            val action = request.action
            val disclosure = mutation.transition.disclosures.singleOrNull { it.cardId == action.cardId &&
                it.faceId == action.faceId && it.containerId == action.containerId && request.accountId in it.viewers } ?: continue
            // Original acceptance never retargets a moved/deleted card, another face/region, or a
            // closed/replaced UI lifetime. Losing presentation applicability does not erase history.
            val card = state.board.cards.singleOrNull { it.id == action.cardId && it.containerId == action.containerId }
            inspections.remove(id)
            if (card == null || state.id != request.scope.playId || state.definitions.none { definition ->
                    definition.digest == card.definition && definition.definition.faces.any { it.faceId == action.faceId } }) {
                observer.subscription.close(); continue
            }
            presentInspection(observer, InspectionEvent.Accepted(request, disclosure, mutation, replica.revision!!, state))
        }
    }
    private fun retireSelections(gameId: String) {
        // A newer HTTP summary is not a board/preparation replica and cannot settle its overlay.
        val revision = acceptedRevisions[gameId] ?: return
        resultIntents.entries.removeAll { (_, intent) -> intent.command.scope.gameId == gameId &&
            intent.commitRevision?.let { newerOrEqual(revision, it) } == true }
        readiness.entries.removeAll { (_, intent) -> intent.command.scope.gameId == gameId &&
            intent.commitRevision?.let { newerOrEqual(revision, it) } == true }
        selections.entries.removeAll { (_, selection) -> selection.command.scope.gameId == gameId &&
            selection.commitRevision?.let { newerOrEqual(revision, it) } == true }
        transfers.entries.removeAll { (_, transfer) -> transfer.command.scope.gameId == gameId &&
            transfer.commitRevision?.let { newerOrEqual(revision, it) } == true }
    }
    private fun correlateSelections(message: GameRevision) {
        selections[message.requestId]?.commitRevision = message.resultRevision
        transfers[message.requestId]?.commitRevision = message.resultRevision
        readiness[message.requestId]?.commitRevision = message.resultRevision
        resultIntents[message.requestId]?.commitRevision = message.resultRevision
        retireSelections(message.gameId)
    }
    private fun flush() {
        if (!ready || credential() != token) return
        for (id in historyDownloads.toList()) if (ready(id)) {
            historyDownloads.remove(id)
            summaries[id]?.let { recover(id, it.revision) }
        }
        // Keep the original protocol's 256-ID recovery window. Retain the entire captured tail
        // compactly in memory, preview it immediately and drain it as capacity becomes available.
        // No card is silently dropped and an acknowledgement is not a per-card sending barrier.
        while (ready && credential() == token && pending.size < 256 && transfers.size + selections.size < 256 && captured.isNotEmpty()) {
            val next = captured.first()
            val stream = streams[next.intent.gameId] ?: break
            if (stream.generation == null || !stream.head) break
            val command = GameCommand(UUID.randomUUID().toString(), GameScope(next.intent.gameId, next.intent.roundId),
                next.next(), stream.id, stream.generation!!)
            pending[command.requestId] = command
            transfers[command.requestId] = Transfer(command, next.actor)
            if (next.finished) captured.removeFirst()
        }
        for ((id, command) in pending) {
            val stream = streams.getValue(command.scope.gameId)
            if (id !in sent && available(command.scope.gameId) && stream.generation != null && stream.head) {
                if (command.scope.gameId in recoveries) continue
                if (!send(command.copy(generation = stream.generation!!))) return
                sent.add(id)
            }
        }
    }
    private fun send(message: GameClientMessage): Boolean {
        if (!connection.send(message)) { unavailable(); return false }; return true
    }
    private fun unavailable() { disconnect(); statusChanged() }
    private fun recover(gameId: String, through: String) {
        if (gameId in recoveries) return
        val stream = streams[gameId] ?: return
        stream.head = false
        val ids = pending.values.filter { it.scope.gameId == gameId }.map { it.requestId }
        val recovery = Recovery(ids, ids.filter { it !in sent }.toSet(), through)
        recoveries[gameId] = recovery
        val generation = epoch; val current = token
        if (treeCache != null && connection.treeTransfer(gameId, recovery.ids) { transfer -> post {
            if (!closed && generation == epoch && current == credential() && sameAccount() && recoveries[gameId] === recovery) {
                if (transfer == null) { recoveries.remove(gameId); unavailable() }
                else stageTree(gameId, recovery, transfer, generation, current)
            }
        } }) return
        if (!connection.baseline(gameId, recovery.ids) { baseline -> post {
            if (!closed && generation == epoch && current == credential() && sameAccount() && recoveries[gameId] === recovery) {
                recoveries.remove(gameId)
                val installed = runCatching {
                    requireNotNull(baseline)
                    require(baseline.snapshot.head.gameId == gameId)
                    require(baseline.statuses.map { it.requestId }.toSet() == recovery.ids.toSet())
                    val replica = plays.getOrPut(gameId) { GameplayReplica() }
                    val correlated = baseline.copy(statuses = baseline.statuses.map {
                        if (it.status == "unknown" && it.requestId in recovery.notAdmitted) it.copy(status = "notAdmitted") else it })
                    if (!replica.baseline(correlated, recovery.tail, recovery.through)) return@runCatching false
                    val latest = recovery.tail.maxByOrNull { GameWire.ordinal(it.resultRevision) }
                        ?.takeIf { it.resultRevision == replica.revision }
                    val head = baseline.snapshot.head
                    install(latest?.summary ?: head.summary, replica.revision!!, null, replica.accepted)
                    installChat(gameId, head.revision, head.chat, snapshot = true)
                    recovery.tail.forEach { update -> update.chatMessage?.let {
                        installChat(gameId, update.resultRevision, listOf(it), snapshot = false)
                    } }
                    for (status in baseline.statuses) if (status.status == "final") result(status.result!!)
                    true
                }.getOrDefault(false)
                if (installed) { stream.head = true; error = null; flush(); statusChanged() } else unavailable()
            }
        } }) { recoveries.remove(gameId); unavailable() }
    }

    private fun observePlay(game: GameSummary) {
        val play = game.playId.takeIf { game.lifecycle == GameLifecycle.ACTIVE }
        if (play == null) observedPlays.remove(game.id) else observedPlays.put(game.id, play)
        caches[game.id]?.takeIf { it.playId != play }?.let { it.invalidate(); caches.remove(game.id, it) }
    }

    private fun loadCached(game: GameSummary) {
        val playId = game.playId ?: return; val owner = account ?: return
        val createCache = treeCache ?: return
        val generation = epoch
        fun valid() = !closed && generation == epoch && account == owner && sameAccount() && observedPlays[game.id] == playId
        treeWorker.execute {
            val published = runCatching {
                if (!valid()) return@runCatching null
                val cache = caches[game.id]?.takeIf { it.isCurrent() } ?: (createCache(owner, game.id, playId) ?: return@runCatching null).also {
                    if (!valid()) it.invalidate()
                    caches[game.id] = it
                }
                cache.guard(::valid)
                TreeDownload(cache, uiThread).accepted()?.takeIf {
                    it.generation.tree.stateAt(it.generation.tree.currentNodeId) != null }
            }.getOrNull()
            post {
                restoring.remove(game.id)
                if (!valid() || published == null || plays[game.id]?.revision?.let {
                        GameWire.ordinal(it) >= published.version.revision } == true) return@post
                val at = published.version.revision.toString()
                val endpoint = published.generation.tree.stateAt(published.generation.tree.currentNodeId)
                val head = GameHead(game.id, at, game.copy(revision = at), play = endpoint)
                val replica = plays.getOrPut(game.id) { GameplayReplica() }
                if (replica.published(head, published.generation, at, emptyList(), emptyList())) {
                    install(head.summary, at, null, replica.accepted); statusChanged()
                }
            }
        }
    }

    private fun delta(update: GameRevision): TreeRecordDelta {
        // Shared metadata/communication owners can advance the game revision without changing the
        // tree. They still advance the SAME accepted cursor; never erase history or force a second
        // cache/journal. An unmatched full board without tree records fails closed to baseline.
        require(update.treeDelta != null || update.play == null)
        return TreeRecordDelta(update.gameId, requireNotNull(update.summary.playId),
            GameWire.ordinal(update.baseRevision), GameWire.ordinal(update.resultRevision), update.treeDelta?.upserts.orEmpty(),
            update.treeDelta?.removals.orEmpty().map { it.kind to it.id })
    }

    private fun stageTree(gameId: String, recovery: Recovery, transfer: GameplayTreeTransfer, generation: Long, current: String?) {
        val playId = transfer.manifest.playId
        val owner = account ?: return unavailable()
        val createCache = treeCache ?: return
        val tail = recovery.tail.toList().sortedBy { GameWire.ordinal(it.resultRevision) }
        val through = recovery.through
        fun valid() = !closed && generation == epoch && current == credential() && sameAccount() && observedPlays[gameId] == playId
        treeWorker.execute {
            if (!valid()) return@execute
            val published = runCatching {
                require(transfer.manifest.statuses.map { it.requestId }.toSet() == recovery.ids.toSet())
                transfer.manifest.statuses.forEach { status ->
                    require(status.status in setOf("final", "pending", "unknown"))
                    if (status.status == "final") require(status.result?.requestId == status.requestId &&
                        status.result?.scope == GameScope(gameId, playId = playId))
                }
                val cache = caches[gameId]?.takeIf { it.playId == playId && it.isCurrent() }
                    ?: (createCache(owner, gameId, playId) ?: return@runCatching null).also {
                    if (!valid()) it.invalidate()
                    caches[gameId] = it
                }
                cache.guard(::valid)
                if (!valid()) return@runCatching null
                val download = TreeDownload(cache, uiThread)
                val staged = download.stage(transfer.plan) { index -> if (valid()) transfer.openChunk(index) else null }
                    ?: return@runCatching null
                if (!valid()) return@runCatching null
                val result = download.publish(staged, tail.filter { GameWire.ordinal(it.resultRevision) > transfer.plan.revision }
                    .map(::delta), GameWire.ordinal(through))
                (result as? TreePublication.Published)?.value?.takeIf { value ->
                    value.generation.tree.stateAt(value.generation.tree.currentNodeId) != null }
            }.getOrNull()
            post {
                if (!valid() || recoveries[gameId] !== recovery) return@post
                recoveries.remove(gameId)
                if (published == null || GameWire.ordinal(recovery.through) > published.version.revision) {
                    recover(gameId, recovery.through); return@post
                }
                val replica = plays.getOrPut(gameId) { GameplayReplica() }
                if (!replica.published(requireNotNull(transfer.manifest.head), published.generation,
                    published.version.revision.toString(), transfer.manifest.statuses, tail)) { unavailable(); return@post }
                val summary = tail.lastOrNull { it.resultRevision == replica.revision }?.summary ?: transfer.manifest.head!!.summary
                install(summary, replica.revision!!, null, replica.accepted)
                val head = requireNotNull(transfer.manifest.head)
                installChat(gameId, head.revision, head.chat, snapshot = true)
                tail.forEach { update -> update.chatMessage?.let { installChat(gameId, update.resultRevision, listOf(it), snapshot = false) } }
                for (status in transfer.manifest.statuses) if (status.status == "final") result(requireNotNull(status.result))
                streams[gameId]?.head = true; error = null; flush(); statusChanged()
            }
        }
    }

    private fun applyTree(update: GameRevision) {
        val generation = epoch; val current = token; val playId = update.summary.playId
        fun valid() = !closed && generation == epoch && current == credential() && sameAccount() && observedPlays[update.gameId] == playId
        treeWorker.execute {
            if (!valid()) return@execute
            val published = runCatching { caches[update.gameId]?.let { TreeDownload(it, uiThread).apply(delta(update)) }
                as? TreePublication.Published }.getOrNull()?.value?.takeIf {
                    it.generation.tree.stateAt(it.generation.tree.currentNodeId) != null }
            post {
                if (!valid()) return@post
                if (published == null) { recover(update.gameId, update.resultRevision); return@post }
                val replica = plays.getOrPut(update.gameId) { GameplayReplica() }
                val endpoint = published.generation.tree.stateAt(published.generation.tree.currentNodeId)
                val head = GameHead(update.gameId, update.resultRevision, update.summary, play = endpoint)
                if (replica.published(head, published.generation, published.version.revision.toString(), emptyList(), listOf(update))) {
                    install(update.summary, replica.revision!!, null, replica.accepted); statusChanged()
                }
            }
        }
    }
    private fun checkAccount() {
        val current = accountId() ?: return
        if (account != null && current != account) clear()
        account = current
    }
    private fun sameAccount() = account == null || accountId() == null || accountId() == account
    private fun disconnect() {
        uncertainGames.addAll(pending.values.map { it.scope.gameId })
        uncertainGames.addAll(resultIntents.values.map { it.command.scope.gameId })
        epoch++; connected = false; welcome = false; token = null; connection.disconnect(); sent.clear()
        recoveries.clear()
        streams.values.forEach { it.generation = null; it.head = false; it.opening = false; it.awaitingReady = false }
    }
    fun clear() { inspections.values.forEach { it.subscription.close() }; inspections.clear(); caches.values.forEach { it.invalidate() }; caches.clear(); observedPlays.clear(); restoring.clear(); disconnect(); pending.clear(); resultIntents.clear(); selections.clear(); transfers.clear(); captured.clear(); readiness.clear(); preparations.clear(); plays.clear();
        messages.clear(); chatRevisions.clear(); chatSubmissions.clear(); acceptedRevisions.clear(); summaries.clear(); unavailableGames.clear(); streams.clear(); connectionDemand.clear(); historyDownloads.clear(); uncertainGames.clear(); lastCredential = null; account = null; error = null; statusChanged() }
    override fun close() { closed = true; clear(); treeWorker.shutdown(); connection.close() }
    companion object {
        fun newerOrEqual(a: String, b: String) = GameWire.ordinal(a) >= GameWire.ordinal(b)
    }
}
