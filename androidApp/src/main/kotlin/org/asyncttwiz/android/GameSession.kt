package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import org.asyncttwiz.storage.GameplayTreeStore

/** One watched game's server stream: its open/resume state and the authorized generation. */
internal data class SessionStream(val id: String = UUID.randomUUID().toString(), var generation: String? = null,
    var head: Boolean = false, var opening: Boolean = false, var opened: Boolean = false,
    var lastGeneration: String? = null, var recoveryIds: List<String> = emptyList(), var awaitingReady: Boolean = false)

/** Main-thread, process-memory queue. Results settle intent; only revisioned reads change accepted state. */
class GameSession(private val connection: GameConnection, private val credential: () -> String?,
    private val post: (() -> Unit) -> Unit, private val authenticationRequired: (String) -> Unit,
    private val changed: (GameSummary) -> Unit, private val settled: (GameCommand, GameResult) -> Unit,
    private val statusChanged: () -> Unit, private val installationId: String = UUID.randomUUID().toString(),
    private val accountId: () -> String? = { null },
    private val treeCache: ((String, String, String) -> GameplayTreeStore?)? = null,
    private val treeWorker: ExecutorService = DebugJank.treeExecutor(Executors.newSingleThreadExecutor()),
    private val now: () -> Long = { System.nanoTime() / 1_000_000 },
    reconnectTelemetry: (ReconnectEpisode) -> Unit = { if (BuildConfig.DIAGNOSTICS) DebugLatency.reconnect(it) },
    private val reviewWorker: ExecutorService = DebugJank.treeExecutor(Executors.newSingleThreadExecutor()),
    private val tableChanges: (String, TableChangeBatch) -> Unit = { _, _ -> },
    private val personalChat: GameChat = GameChat(),
    private val openedGame: () -> String? = { null }) : AutoCloseable {
    private fun replica(gameId: String) = plays.getOrPut(gameId) { GameplayReplica { tableChanges(gameId, it) } }
    private val reconnectTrace = ReconnectRecorder(BuildConfig.DIAGNOSTICS, now, record = reconnectTelemetry)
    private var backgrounded = false
    private var resumePending = false
    private var resumePing: String? = null
    private fun recoveryReady() { streams.keys.filter(::ready).forEach {
        DebugJank.catchUp(it, false); reconnectTrace.step(it, "ready", complete = true)
    } }
    private val uiThread = Thread.currentThread()
    private var latencyTrace: LatencyRecorder.Trace? = null
    private inline fun <T> measureLatency(game: GameSummary, action: GameAction, block: () -> T): T {
        if (!BuildConfig.DIAGNOSTICS) return block()
        val previous = latencyTrace
        val trace = DebugLatency.begin(game.id, action.latencyType(), (plays[game.id]?.tree?.tree?.nodes?.size ?: 0) +
            if (plays[game.id]?.accepted != null) 1 else 0)
        latencyTrace = trace
        try { return block() } finally { latencyTrace = previous; trace?.finish() }
    }
    private fun latencySubmitted(command: GameCommand, predicted: Boolean) {
        if (BuildConfig.DIAGNOSTICS) latencyTrace?.submitted(command.requestId, predicted)
    }
    private val processId = UUID.randomUUID().toString()
    private val streams = linkedMapOf<String, SessionStream>()
    private val summaries = mutableMapOf<String, GameSummary>()
    private val unavailableGames = mutableSetOf<String>()
    private val pending = linkedMapOf<String, GameCommand>()
    private val preparations = PreparationIntents()
    private val ownPodDeckNames = mutableMapOf<String, String>()
    fun ownPodDeckName(gameId: String): String? = ownPodDeckNames[gameId]
    /** The opponent's presence per game while this connection is up; dropped on disconnect so it never goes stale. */
    private val presences = mutableMapOf<String, ReceivedPresence>()
    fun opponentPresence(gameId: String): ReceivedPresence? = presences[gameId]
    private fun presence(gameId: String, value: OpponentPresence) {
        val next = ReceivedPresence(value, now())
        if (presences[gameId]?.let { PresenceText.same(it, next) } != true) presences[gameId] = next
    }
    private val plays = mutableMapOf<String, GameplayReplica>()
    @Volatile private var account: String? = null
    private data class ResultIntent(val command: GameCommand, val actor: String, var commitRevision: String? = null)
    private val resultIntents = linkedMapOf<String, ResultIntent>()
    private val scope = object : TreeCatchUp.Session {
        override val closed get() = this@GameSession.closed
        override val epoch get() = this@GameSession.epoch
        override val account get() = this@GameSession.account
        override fun sameAccount() = this@GameSession.sameAccount()
        override fun post(action: () -> Unit) = this@GameSession.post(action)
        override fun statusChanged() = this@GameSession.statusChanged()
        override val token get() = this@GameSession.token
        override fun credential() = this@GameSession.credential()
        override fun stream(gameId: String) = streams[gameId]
        override fun replica(gameId: String) = this@GameSession.replica(gameId)
        override fun retained(gameId: String) = plays[gameId]
        override fun summary(gameId: String) = summaries[gameId]
        override fun pending(gameId: String) = pending.values.filter { it.scope.gameId == gameId }
        override fun sent(requestId: String) = requestId in sent
        override fun install(game: GameSummary, revision: String, preparation: LobbyPreparation?, play: PlayState?) =
            this@GameSession.install(game, revision, preparation, play)
        override fun result(result: GameResult) = this@GameSession.result(result)
        override fun clearError() { error = null }
        override fun flush() = this@GameSession.flush()
        override fun recoveryReady() = this@GameSession.recoveryReady()
        override fun connectionUnavailable(cause: String) = this@GameSession.connectionUnavailable(cause)
        override fun send(message: GameClientMessage) = this@GameSession.send(message)
    }
    private val chats = SessionChat(scope, connection, personalChat, now, openedGame,
        syncable = { ready && available(it) && it in streams }, available = ::available)
    private val catchUp = TreeCatchUp(scope, connection, treeCache, treeWorker, uiThread, now, reconnectTrace, chats)
    private val reviews = SessionHistoryReview(scope, reviewWorker, ::checkAccount, plays::get, summaries::get, catchUp::cache, catchUp::observedPlay)
    private val inspections = InspectionObservers(scope, plays::get, catchUp::observedPlay)
    fun chat(gameId: String) = chats.chat(gameId)
    fun chatLoaded(gameId: String) = chats.chatLoaded(gameId)
    fun presentedChat(gameId: String, ids: Set<String>) = chats.presentedChat(gameId, ids)
    fun outgoingChat(gameId: String) = chats.outgoingChat(gameId)
    fun loadOlderChat(gameId: String): Boolean = chats.loadOlderChat(gameId)
    fun sendChat(game: GameSummary, text: String): Boolean = measureLatency(game, GameAction.SendChat(text)) {
        val current = summary(game)
        if (game.lifecycle != GameLifecycle.ACTIVE || current.lifecycle != GameLifecycle.ACTIVE || current.playId == null ||
            game.playId != current.playId || !ChatValidation.text(text)) return false
        val command = command(current, GameAction.SendChat(text)) ?: return false
        chats.submitted(command)
        pending[command.requestId] = command
        latencySubmitted(command, true) // Outgoing chat is immediately visible in the local UI.
        error = null; flush(); statusChanged(); return true
    }
    private val acceptedRevisions = mutableMapOf<String, String>()
    private val sent = mutableSetOf<String>()
    @Volatile private var token: String? = null
    @Volatile private var epoch = 0L
    private var connected = false
    private var welcome = false
    /** The last welcome's protocol minor: [GameCommand.expectedSelection] is sent only to servers that understand it. */
    private var serverMinor = 0
    private var compatibilityError: String? = null
    private var compatibilityRetryAt = Long.MAX_VALUE
    private var lastCredential: String? = null
    private var lastPing = 0L
    private val connectionDemand = mutableMapOf<String, Long>()
    private val uncertainGames = mutableSetOf<String>()
    @Volatile private var closed = false
    var error: String? = null
        private set
    val ready get() = connected && welcome
    fun reconnecting(gameId: String) = compatibilityError == null && !ready(gameId) && connectionDemand[gameId]?.let { now() - it >= 3_000 } == true
    fun dismissError() { error = compatibilityError; statusChanged() }
    fun background() { backgrounded = true; resumePending = true; resumePing = null; reconnectTrace.stepAll("lifecycleBackground") }
    fun foreground() {
        reconnectTrace.stepAll("lifecycleForeground"); connectionDemand.clear()
        // A socket kept open while away may still hold undelivered events (a frozen process reads
        // them only now): its pong, answered in order, marks the end of that backlog. A new socket's
        // stream heads mark it instead.
        if (backgrounded || resumePending) {
            resumePending = true; resumePing = null
            if (connected && welcome) UUID.randomUUID().toString().let { resumePing = it; send(GamePing(it)) }
        }
        backgrounded = false
        tick()
    }
    /** Everything the server had when the app returned to the foreground has been received and
     * published for this game, so a resumed table may decide live vs. timeline mode. */
    fun caughtUp(gameId: String): Boolean = !backgrounded && !resumePending && ready(gameId) && catchUp.caughtUp(gameId)
    /** Only a previously authorized, retained active play may capture originals during a short
     * reconnect wait. Nothing is sent until stream recovery and its accepted head are complete. */
    private fun activeRequest(game: GameSummary, captureDuringRecovery: Boolean = false): Boolean {
        checkAccount()
        if (compatibilityError != null) return false
        if (ready(game.id)) { connectionDemand.remove(game.id); uncertainGames.remove(game.id); return true }
        if (closed || !available(game.id) || credential() == null || credential() != lastCredential ||
            streams[game.id]?.lastGeneration == null || acceptedPlay(game) == null) return false
        val started = connectionDemand.getOrPut(game.id, now)
        tick()
        // Do not extend an uncertain old socket's predicted chain before its outcomes are known.
        if (!captureDuringRecovery && (game.id in uncertainGames || catchUp.recovering(game.id))) return false
        return now() - started < 3_000
    }
    fun available(gameId: String) = gameId !in unavailableGames && summaries[gameId]?.lifecycle !in listOf(GameLifecycle.CANCELLED, GameLifecycle.DELETED)
    fun ready(gameId: String) = ready && available(gameId) && !catchUp.recovering(gameId) && streams[gameId]?.let { it.generation != null && it.head } == true
    /** A negative ordinary read fences personal context, not uncertain admitted originals. */
    fun unavailable(gameId: String) {
        unavailableGames.add(gameId); clearContext(gameId)
        streams[gameId]?.let { it.opening = false; it.generation = null }
        statusChanged()
    }
    private fun clearContext(gameId: String) {
        ownPodDeckNames.remove(gameId); presences.remove(gameId)
        catchUp.remove(gameId)
        inspections.remove(gameId)
        preparations.remove(gameId); plays.remove(gameId); acceptedRevisions.remove(gameId)
        chats.remove(gameId)
        resultIntents.entries.removeAll { it.value.command.scope.gameId == gameId }
        streams[gameId]?.head = false
    }
    fun pending(gameId: String) = pending.values.any { it.scope.gameId == gameId } || preparations.pending(gameId) ||
        plays[gameId]?.pendingIds?.isNotEmpty() == true || resultIntents.values.any { it.command.scope.gameId == gameId }
    fun queuedTransfers(gameId: String) = preparations.queuedTransfers(gameId)
    fun acceptedPreparation(gameId: String) = preparations.acceptedPreparation(gameId)
    fun acceptedPlay(game: GameSummary) = plays[game.id]?.accepted?.takeIf { summary(game).let { current ->
        current.lifecycle == GameLifecycle.ACTIVE && current.playId == it.id } }
    fun play(game: GameSummary) = plays[game.id]?.display?.takeIf { acceptedPlay(game)?.id == it.id }
    fun retainedTree(gameId: String) = plays[gameId]?.tree?.tree
    fun retainedHistory(gameId: String) = plays[gameId]?.history
    fun displayTree(gameId: String) = plays[gameId]?.displayTree
    /** Personal read-only review, including offline. */
    fun review(gameId: String, nodeId: String?, complete: (DownloadedHistoryState) -> Unit): Boolean =
        reviews.review(gameId, nodeId, complete)
    /** Personal exact-boundary route for an authorized activity/history consumer. */
    fun reviewTarget(target: CapturedHistoryTarget, isCurrent: () -> Boolean,
        complete: (CapturedHistoryReview) -> Unit): Boolean = reviews.reviewTarget(target, isCurrent, complete)

    fun withdrawGameplayPrediction(gameId: String, requestId: String) { plays[gameId]?.withdraw(requestId); statusChanged() }
    fun preparation(game: GameSummary): LobbyPreparation? = preparations.preparation(summary(game), game.id)
    fun summary(game: GameSummary): GameSummary = summaries[game.id]?.takeIf {
        ended(it) || newerOrEqual(it.revision, game.revision) } ?: game
    fun watch(games: List<GameSummary>) {
        checkAccount()
        for (game in games) {
            install(game, game.revision, null, null)
            streams.getOrPut(game.id) { SessionStream() }
        }
        tick()
        if (treeCache != null) for (game in games) catchUp.watch(summary(game))
    }
    fun tick() {
        if (closed) return
        checkAccount()
        if (compatibilityError != null && now() < compatibilityRetryAt) return
        val current = credential()
        if (current != token) {
            if (connected) streams.keys.forEach { reconnectTrace.start(it,
                if (current == null) "credentialUnavailable" else "credentialChanged") }
            disconnect()
        }
        if (current == null || streams.isEmpty()) return
        if (!connected) {
            if (compatibilityError != null) compatibilityRetryAt = now() + 30_000
            token = current; lastCredential = current; connected = true; lastPing = now(); val generation = ++epoch
            reconnectTrace.stepAll("socketConnectStart")
            statusChanged()
            connection.connect(current, object : GameConnection.Listener {
                private fun deliver(action: () -> Unit) = post {
                    if (!closed && epoch == generation && credential() == current && sameAccount()) action()
                }
                override fun opened() = deliver {
                    AppLog.info("game", "Connected")
                    reconnectTrace.stepAll("socketConnectedHttpAuthenticated")
                    send(GameHello(processId, installationId, maxMinor = GAME_PROTOCOL_MINOR, gameplayRulesVersion = CURRENT_GAMEPLAY_RULES_VERSION))
                }
                override fun message(message: GameServerMessage) = deliver {
                    val failure = runCatching { receive(message) }.exceptionOrNull()
                    if (failure != null) {
                        AppLog.warn("game", "Could not apply ${message::class.simpleName}", failure)
                        connectionUnavailable("streamError:${message::class.simpleName}:${failure.javaClass.simpleName}")
                    }
                }
                override fun disconnected(unauthorized: Boolean) = disconnected(unauthorized, "socketDisconnected")
                override fun disconnected(unauthorized: Boolean, cause: String) = deliver {
                    AppLog.warn("game", "Connection lost: $cause")
                    connectionUnavailable(cause)
                    if (unauthorized) authenticationRequired(current)
                }
            })
        } else if (welcome) {
            openStreams()
            streams.keys.forEach(chats::syncChatRead)
            catchUp.checkHeadGaps()
            // The server's traffic lease counts protocol messages, not WebSocket control pings.
            if (now() - lastPing >= 25_000) { lastPing = now(); send(GamePing(UUID.randomUUID().toString())) }
        }
        connectionDemand.keys.removeAll { ready(it) }
        uncertainGames.removeAll { ready(it) }
        recoveryReady()
        statusChanged()
    }
    fun submit(game: GameSummary, action: GameAction): Boolean {
        if (action == GameAction.DeleteGame) return deleteGame(game)
        if (action.isPlay || action is GameAction.AcceptResult || action is GameAction.SendChat ||
            action == GameAction.Undo || action == GameAction.Redo || action is GameAction.SelectDeck || action is GameAction.TransferPreparedCard ||
            action is GameAction.SetStarter || action is GameAction.SetAlternateStarter || action is GameAction.SetPodGameBasics || action is GameAction.ShowDeck ||
            action == GameAction.Ready || action == GameAction.WithdrawReady) return false
        val current = summary(game)
        if (current.lifecycle != GameLifecycle.LOBBY || current.preparationRound != game.preparationRound) return false
        return enqueue(game, action) != null
    }
    /** Capture one original in memory; send now or after authorized reconnect recovery. */
    fun gameplay(game: GameSummary, actor: String, action: PlayAction, invocationId: String? = null,
        continuation: Continuation? = null, captured: (GameCommand) -> Unit = {}): Boolean =
        gameplayCommand(game, actor, GameAction.Play(action), invocationId, continuation, captured) != null

    fun gameplayBatch(game: GameSummary, actor: String, actions: List<PlayAction>, requestId: String,
        continuation: Continuation? = null, captured: (GameCommand) -> Unit = {}): Boolean =
        gameplayCommand(game, actor, GameAction.PlayBatch(actions), requestId, continuation, captured, requestId) != null

    /** Explicit logged inspection on the same original queue. Callers supply a UI lifetime guard
     * and close the subscription on dismissal. Only Accepted may drive ManualActions.inspected. */
    fun inspectFace(game: GameSummary, actor: String, action: PlayAction.InspectFace, lifetimeId: String,
        isCurrent: () -> Boolean, observed: (InspectionEvent) -> Unit,
        continuation: Continuation? = null, captured: (GameCommand) -> Unit = {}): InspectionSubscription? {
        if (lifetimeId.isBlank() || lifetimeId.length > 200 || !isCurrent()) return null
        var subscription: InspectionSubscription? = null
        gameplayCommand(game, actor, GameAction.Play(action), null, continuation, captured = { command ->
            captured(command)
            subscription = inspections.observe(command, actor, lifetimeId, action, isCurrent, observed)
        }) ?: return null
        return subscription
    }

    /** Exact typed shared controls use the SAME original queue and processing-time target.
     * No fixed Undo target, alternate endpoint, or durable offline command queue. */
    fun historyControl(game: GameSummary, actor: String, action: GameAction): Boolean {
        if (action != GameAction.Undo && action != GameAction.Redo) return false
        return gameplayCommand(game, actor, action, null, null) != null
    }

    private fun gameplayCommand(game: GameSummary, actor: String, action: GameAction, invocationId: String?,
        continuation: Continuation?, captured: (GameCommand) -> Unit = {}, requestId: String = UUID.randomUUID().toString()): GameCommand? = measureLatency(game, action) {
        checkAccount()
        val current = summary(game)
        fun refused(gate: String): GameCommand? {
            DebugLatency.tap(game.id, "dropped:$gate")
            error = compatibilityError ?: "Action not queued ($gate). Reconnect and try again."
            statusChanged()
            return null
        }
        if (closed || !activeRequest(current, captureDuringRecovery = (action as? GameAction.Play)?.action is PlayAction.SetTapped ||
                (action as? GameAction.PlayBatch)?.actions?.all { it is PlayAction.SetTapped } == true) || pending.size >= 256 ||
            current.lifecycle != GameLifecycle.ACTIVE || current.playId == null ||
            (account != null && account != actor)) return refused("sessionUnavailable")
        val replica = plays[game.id] ?: return refused("replicaMissing")
        // The one result-lock rule: a continuation is gated by the board it continues from (an earlier timeline
        // point, or its own predicted new branch), so a suggestion further along the old line does not block it.
        // This is the same board the prediction below materializes; an unknown board falls back to the live lock.
        val continuationBoard = continuation?.let { replica.displayTree?.stateAt(it.nodeId) }
        if (ResultActions.blocksAt(current, replica.accepted, play(current), continuationBoard, inTimeline = continuation != null) &&
            (action as? GameAction.Play)?.action !is PlayAction.DeclineResult &&
            (action as? GameAction.Play)?.action !is PlayAction.WithdrawResult) return refused("pendingResult")
        // Unpredicted controls remain a capture barrier: never drag from a pre-Undo board. Exact
        // latest-head predictions allow rapid play/control sequences on the same original FIFO.
        if (action.isPlay && resultIntents.values.any { it.command.scope.gameId == game.id &&
                it.command.action !is GameAction.AcceptResult && !replica.predicted(it.command.requestId) }) return refused("controlBarrier")
        if (replica.pendingIds.size >= 256 || replica.accepted?.id != current.playId) return refused("predictionCapacityOrPlayChanged")
        val stream = streams.getValue(game.id)
        // Ordinary play names the active line it was made on (including an own pending branch): one that races
        // with the opponent's deliberate branch is refused instead of landing in their new line.
        val expectedSelection = replica.displayTree?.selectionKey?.takeIf { action.isPlay && continuation == null && serverMinor >= 2 }
        val command = GameCommand(requestId, GameScope(game.id, playId = current.playId),
            action, stream.id, stream.generation ?: stream.lastGeneration!!, invocationId = invocationId, continuation = continuation,
            expectedSelection = expectedSelection)
        if (!GameWire.valid(command)) return refused("invalidCommand")
        // A control can become eligible after an already-inflight original. Failure to predict is
        // not authority to suppress the server's processing-time candidate.
        // Replica prediction is limited to exact terminal controls; selective suffixes stay server-only.
        // The accepted delta is prepared by the serial tree worker before publication.
        if (action.isPlay && !replica.predict(command, actor)) return refused("predictionRefused")
        if (!action.isPlay && resultIntents.values.none { it.command.scope.gameId == game.id &&
                !replica.predicted(it.command.requestId) }) replica.predict(command, actor)
        pending[command.requestId] = command
        latencySubmitted(command, replica.predicted(command.requestId))
        if (!action.isPlay) resultIntents[command.requestId] = ResultIntent(command, actor)
        captured(command)
        error = null; flush(); statusChanged(); return command
    }
    fun suggestResult(game: GameSummary, actor: String, target: ResultTarget, outcome: SuggestedResult,
        note: String = ""): Boolean {
        val current = summary(game)
        val play = play(current) ?: return false
        if (ResultActions.capture(current, play) != target) return false
        return gameplay(current, actor, PlayAction.SuggestResult(outcome, ResultNoteInput.clean(note)))
    }
    fun acceptResult(game: GameSummary, actor: String, target: ResultAcceptanceTarget): Boolean =
        measureLatency(game, GameAction.AcceptResult(target.suggestionId)) {
        val current = summary(game)
        // Only an accepted current suggestion is actionable; a local prediction is not acceptance authority.
        val play = acceptedPlay(current) ?: return false
        if (ResultActions.capture(current, play) != target.play || play.board.resultSuggestion?.id != target.suggestionId ||
            play.board.resultSuggestion?.author == actor || actor !in play.board.players.map { it.accountId }) return false
        if (resultIntents.values.any { it.command.scope.playId == target.play.playId && it.command.action == GameAction.AcceptResult(target.suggestionId) }) return false
        val command = resultCommand(target.play, GameAction.AcceptResult(target.suggestionId)) ?: return false
        return enqueueResult(command, actor)
    }
    fun rejectResult(game: GameSummary, actor: String, target: ResultAcceptanceTarget): Boolean {
        val current = summary(game)
        val accepted = acceptedPlay(current) ?: return false
        val suggestion = accepted.board.resultSuggestion ?: return false
        if (ResultActions.capture(current, accepted) != target.play || suggestion.id != target.suggestionId) return false
        return if (suggestion.author == actor) {
            gameplay(current, actor, PlayAction.WithdrawResult(target.suggestionId))
        } else gameplay(current, actor, PlayAction.DeclineResult(target.suggestionId))
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
        latencySubmitted(command, false)
        error = null; flush(); statusChanged(); return true
    }
    fun deleteGame(game: GameSummary): Boolean {
        val current = summary(game)
        if (game.lifecycle != GameLifecycle.ACTIVE || game.playId == null ||
            current.lifecycle != GameLifecycle.ACTIVE || current.playId != game.playId) return false
        return enqueue(game, GameAction.DeleteGame) != null
    }
    fun readiness(game: GameSummary, actor: String, action: GameAction): Boolean = measureLatency(game, action) {
        if (action !is GameAction.SetStarter && action !is GameAction.SetAlternateStarter && action !is GameAction.SetPodGameBasics && action !is GameAction.ShowDeck &&
            action != GameAction.Ready && action != GameAction.WithdrawReady) return false
        val current = summary(game)
        val base = preparation(current) ?: return false
        val command = command(current, action) ?: return false
        if (preparations.preparationIntent(current, base, actor, command.scope, action).state == null) return false
        preparations.readied(command, actor)
        pending[command.requestId] = command
        latencySubmitted(command, true)
        error = null; flush(); statusChanged(); return true
    }
    fun selectDeck(game: GameSummary, actor: String, source: SavedDeck, definitions: List<SubmittedDefinition>): Boolean =
        measureLatency(game, GameAction.SelectDeck(source.deck.id, source.deck.revision)) {
        val current = summary(game)
        val base = preparation(current) ?: return false
        val action = GameAction.SelectDeck(source.deck.id, source.deck.revision)
        val command = command(current, action) ?: return false
        if (PreparationRules.select(current, base, actor, command.requestId, command.scope, action, source, definitions).state == null) return false
        preparations.selected(command, actor, source, definitions)
        pending[command.requestId] = command
        latencySubmitted(command, true)
        error = null; flush(); statusChanged(); return true
    }
    /** One release captures a row, but produces ordinary individual commands, never an atomic bulk. */
    fun transfer(game: GameSummary, actor: String, intent: PreparedTransfer): Boolean {
        val timing = if (BuildConfig.DIAGNOSTICS) DebugLatency.begin(game.id, "TransferPreparedCard", 0) else null
        try {
        val current = summary(game)
        val deck = preparation(current)?.players?.find { it.accountId == actor }?.deck ?: return false
        if (!ready(game.id) || credential() != token || current.lifecycle != GameLifecycle.LOBBY ||
            intent.gameId != current.id || intent.roundId != current.preparationRound || intent.deckId != deck.id ||
            intent.selectionRequestId != deck.selectionRequestId || intent.entries.isEmpty() ||
            intent.entries.any { it.quantity <= 0 || it.section != intent.source } ||
            (actor != current.inviter.accountId && (actor != current.recipient.accountId || current.invitationState != InvitationState.ACCEPTED))) return false
        preparations.capture(intent, actor)
        timing?.submitted(null, true)
        error = null; flush(); statusChanged(); return true
        } finally { timing?.finish() }
    }
    /** Withdraw display only. Already sent originals still recover and can be accepted normally. */
    fun withdrawTransferPrediction(requestId: String) { preparations.withdrawTransfer(requestId); statusChanged() }
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
    private fun enqueue(game: GameSummary, action: GameAction): GameCommand? = measureLatency(game, action) {
        val command = command(game, action) ?: return null
        pending[command.requestId] = command
        latencySubmitted(command, false)
        error = null; flush(); statusChanged(); return command
    }
    private fun openStreams() {
        for ((id, stream) in streams) if (!stream.opening) {
            if (!available(id) && pending.values.none { it.scope.gameId == id }) continue
            stream.opening = true
            val ids = pending.values.filter { it.scope.gameId == id }.map { it.requestId }
            stream.recoveryIds = ids
            stream.awaitingReady = true
            reconnectTrace.step(id, if (stream.opened) "streamResumeStart" else "streamOpenStart")
            if (!send(if (stream.opened) ResumeGameStream(id, stream.id, ids) else OpenGameStream(id, stream.id))) return
        }
    }
    private fun receive(message: GameServerMessage) {
        when (message) {
            is GameWelcome -> {
                require(message.protocolMajor == 1 && message.minor in 0..GAME_PROTOCOL_MINOR && message.engineVersion == 1 && message.readFormat == 1)
                if (message.gameplayRulesVersion != CURRENT_GAMEPLAY_RULES_VERSION) {
                    incompatibleRules(clientOlder = message.gameplayRulesVersion > CURRENT_GAMEPLAY_RULES_VERSION)
                    return
                }
                compatibilityError = null; compatibilityRetryAt = Long.MAX_VALUE
                serverMinor = message.minor
                reconnectTrace.stepAll("protocolWelcome")
                welcome = true; error = null; resumePending = false; resumePing = null; openStreams()
            }
            is GameStreamReady -> {
                val stream = streams[message.gameId] ?: return
                if (!stream.awaitingReady) return
                require(stream.id == message.streamId); GameWire.ordinal(message.generation)
                if (stream.lastGeneration?.let { !newerOrEqual(message.generation, it) || message.generation == it } == true) return
                stream.generation = message.generation; stream.opened = true
                stream.lastGeneration = message.generation
                stream.awaitingReady = false
                reconnectTrace.step(message.gameId, "streamAuthorized")
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
                if (available(message.gameId)) send(SubscribeGame(message.gameId,
                    plays[message.gameId]?.revision ?: acceptedRevisions[message.gameId] ?: "0"))
                flush()
            }
            is GameHead -> {
                require(message.gameId == message.summary.id && message.revision == message.summary.revision)
                if (message.gameId !in streams) return
                // Presence is read fresh with every head; it is not revisioned state.
                message.opponentPresence?.let { presence(message.gameId, it) }
                // Fence old-incarnation reads before they can create a replica or trigger recovery.
                // A leading HTTP summary is deliberately not this accepted-state boundary.
                if (acceptedRevisions[message.gameId]?.let { !newerOrEqual(message.revision, it) } == true) return
                if (summaries[message.gameId]?.let { ended(it) && !ended(message.summary) } == true) return
                message.ownPodDeckName?.let { ownPodDeckNames[message.gameId] = it }
                catchUp.headArrived(message.summary)
                chats.installChatHead(message)
                if (!catchUp.head(message)) {
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
                    inspections.notifyInspections(); flush(); statusChanged(); return
                }
                if (summaries[message.gameId]?.let { ended(it) && !ended(message.summary) } == true) return
                message.chatMessage?.let { chats.installChat(message.gameId, message.resultRevision, listOf(it), snapshot = false) }
                if (catchUp.superseded(message)) {
                    // History of a play that is no longer current: nothing to catch up or observe.
                    correlateSelections(message); flush(); statusChanged(); return
                }
                catchUp.observePlay(message.summary)
                if (message.summary.lifecycle == GameLifecycle.ACTIVE && message.summary.playId != null) {
                    catchUp.revision(message)
                } else install(message.summary, message.resultRevision, message.preparation, null)
                // A correlated accepted revision is enough even when its result has not arrived yet.
                correlateSelections(message)
            }
            is GameResult -> result(message)
            is GameReceipt -> Unit // Receipt is not acceptance and never removes an uncertain original.
            is GameAdmissionError -> {
                if (BuildConfig.DIAGNOSTICS) message.requestId?.let(DebugLatency::result)
                if (message.code == "gameplayRulesUpgradeRequired" || message.code == "serverUpdatePending" ||
                    (!welcome && message.code == "invalidInput" && message.requestId == null)) {
                    // Pre-handshake-field servers reject the new Hello as unknown input.
                    incompatibleRules(clientOlder = message.code == "gameplayRulesUpgradeRequired")
                }
                else if (message.code == "unauthorized") { val rejected = token; connectionUnavailable("admission:unauthorized"); rejected?.let(authenticationRequired) }
                else if (message.retryable || message.code == "streamFenced") connectionUnavailable("admission:${message.code}")
                else {
                    val original = message.requestId?.let(pending::get)
                    if (message.code == "accessDenied") message.gameId?.let(::unavailable)
                    message.requestId?.let { id -> inspections.notAccepted(id) { request ->
                        InspectionEvent.NotAccepted(request, admissionError = message) } }
                    message.requestId?.let { id ->
                        chats.failed(id, "Not sent. This request could not be used.")
                        pending.remove(id)?.let { plays[it.scope.gameId]?.retire(id) }; preparations.forget(id); resultIntents.remove(id); sent.remove(id)
                    }
                    if (original != null) error = historyControlFailure(original.action, message.code)
                        ?: "This game changed or the request could not be used. Refresh and try again."
                }
            }
            is GameUpgradeRequired -> incompatibleRules(clientOlder = true)
            is GamePresenceUpdate -> if (message.gameId in streams && available(message.gameId))
                presence(message.gameId, message.opponent)
            is GamePong -> if (message.correlationId == resumePing) { resumePing = null; resumePending = false }
        }
        catchUp.checkHeadGaps()
        flush()
        recoveryReady()
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
        catchUp.observePlay(game)
        if (play != null) {
            require(game.lifecycle == GameLifecycle.ACTIVE && play.gameId == game.id && play.id == game.playId &&
                play.preparationRound == game.preparationRound && play.engineVersion == 1 && play.readFormat == 1)
        }
        if (game.lifecycle != GameLifecycle.LOBBY) preparations.leftLobby(game.id)
        if (game.lifecycle == GameLifecycle.CANCELLED || game.lifecycle == GameLifecycle.DELETED) clearContext(game.id)
        if (preparation != null && game.lifecycle == GameLifecycle.LOBBY) preparations.accept(game, revision, preparation)
        if ((preparation != null && game.lifecycle == GameLifecycle.LOBBY) || play != null || ended(game)) acceptedRevisions[game.id] = revision
        if (play != null && catchUp.reachesHead(game.id, revision))
            streams[game.id]?.head = true
        if (summaries[game.id]?.let { newerOrEqual(revision, it.revision) } != false) summaries[game.id] = game
        retireSelections(game.id)
        changed(summary(game))
        inspections.notifyInspections()
    }
    private fun result(result: GameResult) {
        if (BuildConfig.DIAGNOSTICS) DebugLatency.result(result.requestId)
        val original = pending[result.requestId] ?: return
        require(result.scope == original.scope)
        plays[original.scope.gameId]?.result(result)
        pending.remove(result.requestId); sent.remove(result.requestId)
        if (result.outcome != "accepted") chats.failed(result.requestId,
            "Not sent. The original play is no longer available or this request was refused.")
        preparations.settle(result) { retireSelections(original.scope.gameId) }
        resultIntents[result.requestId]?.let { intent ->
            if (result.outcome == "accepted") intent.commitRevision = requireNotNull(result.commitRevision)
            else resultIntents.remove(result.requestId)
            retireSelections(original.scope.gameId)
        }
        error = if (result.outcome in setOf("accepted", "noOp")) null else historyControlFailure(original.action, result.reason) ?: refusalText(result.reason)
        if (result.outcome != "accepted") inspections.notAccepted(result.requestId) { request ->
            InspectionEvent.NotAccepted(request, result = result) }
        inspections.notifyInspections()
        if (original.action == GameAction.AcceptInvitation && result.outcome == "accepted" &&
            summaries[original.scope.gameId]?.podId != null) {
            send(SubscribeGame(original.scope.gameId, result.commitRevision ?: "0"))
        }
        settled(original, result)
    }

    private fun retireSelections(gameId: String) {
        // A newer HTTP summary is not a board/preparation replica and cannot settle its overlay.
        val revision = acceptedRevisions[gameId] ?: return
        resultIntents.entries.removeAll { (_, intent) -> intent.command.scope.gameId == gameId &&
            intent.commitRevision?.let { newerOrEqual(revision, it) } == true }
        preparations.retire(gameId, revision)
    }
    private fun correlateSelections(message: GameRevision) {
        preparations.correlate(message.requestId, message.resultRevision)
        resultIntents[message.requestId]?.commitRevision = message.resultRevision
        retireSelections(message.gameId)
    }
    private fun flush() {
        if (!ready || credential() != token) return
        // Keep the original protocol's 256-ID recovery window. Retain the entire captured tail
        // compactly in memory, preview it immediately and drain it as capacity becomes available.
        // No card is silently dropped and an acknowledgement is not a per-card sending barrier.
        preparations.drainCaptured({ ready && credential() == token && pending.size < 256 }) { gameId, roundId, next ->
            val stream = streams[gameId] ?: return@drainCaptured null
            if (stream.generation == null || !stream.head) return@drainCaptured null
            val command = GameCommand(UUID.randomUUID().toString(), GameScope(gameId, roundId),
                next(), stream.id, stream.generation!!)
            pending[command.requestId] = command
            command
        }
        for ((id, command) in pending) {
            val stream = streams.getValue(command.scope.gameId)
            if (id !in sent && available(command.scope.gameId) && stream.generation != null && stream.head) {
                if (catchUp.recovering(command.scope.gameId)) continue
                if (!send(command.copy(generation = stream.generation!!))) return
                sent.add(id)
            }
        }
    }
    fun heartbeat(presence: GamePresence): Boolean = connected && welcome && send(GamePing(UUID.randomUUID().toString(), presence))
    private fun send(message: GameClientMessage): Boolean {
        if (!connection.send(message)) { connectionUnavailable("socketSendFailed"); return false }; return true
    }
    private fun connectionUnavailable(cause: String) {
        streams.keys.forEach { reconnectTrace.start(it, if (backgrounded) "lifecycle:$cause" else cause) }
        disconnect(); statusChanged()
    }
    private fun incompatibleRules(clientOlder: Boolean) {
        compatibilityError = if (clientOlder) "Update the app to continue this game" else "Server update pending"
        compatibilityRetryAt = if (clientOlder) Long.MAX_VALUE else now() + 30_000
        disconnect()
        error = compatibilityError
        statusChanged()
    }
    private fun checkAccount() {
        val current = accountId() ?: return
        if (account != null && current != account) clear()
        account = current
    }
    private fun sameAccount() = account == null || accountId() == null || accountId() == account
    private fun disconnect() {
        streams.keys.forEach { DebugJank.catchUp(it, false) }
        uncertainGames.addAll(pending.values.map { it.scope.gameId })
        uncertainGames.addAll(resultIntents.values.map { it.command.scope.gameId })
        epoch++; connected = false; welcome = false; resumePing = null; token = null; connection.disconnect(); sent.clear(); chats.disconnected()
        catchUp.disconnected(); presences.clear()
        streams.values.forEach { it.generation = null; it.head = false; it.opening = false; it.awaitingReady = false }
    }
    fun clear() { inspections.clear(); catchUp.clear(); disconnect(); reviews.clear(); pending.clear(); resultIntents.clear(); preparations.clear(); plays.clear();
        ownPodDeckNames.clear(); chats.clear(); acceptedRevisions.clear(); summaries.clear(); unavailableGames.clear(); streams.clear(); connectionDemand.clear(); uncertainGames.clear(); reconnectTrace.clear(); lastCredential = null; account = null; compatibilityError = null; compatibilityRetryAt = Long.MAX_VALUE; error = null; statusChanged() }
    override fun close() { closed = true; clear(); treeWorker.shutdown(); reviewWorker.shutdown(); connection.close() }
    companion object {
        fun newerOrEqual(a: String, b: String) = GameWire.ordinal(a) >= GameWire.ordinal(b)
        internal fun ended(game: GameSummary) = game.lifecycle !in listOf(GameLifecycle.LOBBY, GameLifecycle.ACTIVE)
    }
}
