package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import java.util.UUID

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
    private val now: () -> Long = { System.nanoTime() / 1_000_000 },
    reconnectTelemetry: (ReconnectEpisode) -> Unit = { if (Diagnostics.enabled) DebugLatency.reconnect(it) },
    private val personalChat: GameChat = GameChat(),
    private val openedGame: () -> String? = { null },
    /** Where engine games do their work (I-D56 step D2): the app's engine thread, or inline in tests. */
    private val engineWorker: EngineWorker = EngineWorker.Direct,
    /** Which engine games to load on the engine thread (the app: only the open one; switchreview #11). */
    private val engineLoad: (String) -> Boolean = { true }) : AutoCloseable {
    // Always traced: reports can be turned on mid-game; the default sink sends only while they are on.
    private val reconnectTrace = ReconnectRecorder(true, now, record = reconnectTelemetry)
    private var backgrounded = false
    private var resumePending = false
    private var resumePing: String? = null
    private fun recoveryReady() { streams.keys.filter(::ready).forEach {
        DebugJank.catchUp(it, false); reconnectTrace.step(it, "ready", complete = true)
    } }
    private val uiThread = Thread.currentThread()
    private var latencyTrace: LatencyRecorder.Trace? = null
    private inline fun <T> measureLatency(game: GameSummary, action: GameAction, block: () -> T): T {
        if (!Diagnostics.enabled) return block()
        val previous = latencyTrace
        val trace = DebugLatency.begin(game.id, action.latencyType(), 0)
        latencyTrace = trace
        try { return block() } finally { latencyTrace = previous; trace?.finish() }
    }
    private fun latencySubmitted(command: GameCommand, predicted: Boolean) {
        if (Diagnostics.enabled) latencyTrace?.submitted(command.requestId, predicted)
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
    @Volatile private var account: String? = null
    private val scope = object : SessionScope {
        override val closed get() = this@GameSession.closed
        override val epoch get() = this@GameSession.epoch
        override val account get() = this@GameSession.account
        override fun sameAccount() = this@GameSession.sameAccount()
        override fun post(action: () -> Unit) = this@GameSession.post(action)
        override fun statusChanged() = this@GameSession.statusChanged()
    }
    private val chats = SessionChat(scope, connection, personalChat, now, openedGame,
        syncable = { ready && available(it) && it in streams }, available = ::available)
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
    private var compatibilityError: String? = null
    private var compatibilityRetryAt = Long.MAX_VALUE
    private var lastCredential: String? = null
    private var lastPing = 0L
    private val connectionDemand = mutableMapOf<String, Long>()
    /** Socket attempts in a row that failed before the server's welcome, and when [poll] may try the next one. */
    private var failedConnects = 0
    private var nextConnectAt = 0L
    private fun reconnectDelay(failures: Int): Long =
        if (failures <= 0) 0L else minOf(1_000L shl minOf(failures - 1, 10), MAX_RECONNECT_DELAY_MS)
    private val uncertainGames = mutableSetOf<String>()
    @Volatile private var closed = false
    var error: String? = null
        private set
    val ready get() = connected && welcome
    fun reconnecting(gameId: String) = compatibilityError == null && !ready(gameId) && connectionDemand[gameId]?.let { now() - it >= 3_000 } == true
    fun dismissError() { error = compatibilityError; statusChanged() }
    fun background() {
        backgrounded = true; resumePending = true; resumePing = null; reconnectTrace.stepAll("lifecycleBackground")
        // HIS-035: leaving the app pauses every open table's playback at once
        engineGames.values.forEach { it.away() }
    }
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
    fun caughtUp(gameId: String): Boolean = !backgrounded && !resumePending && ready(gameId)
    /** Only a previously authorized, loaded active play may capture originals during a short
     * reconnect wait. Nothing is sent until stream recovery and its accepted head are complete. */
    private fun activeRequest(game: GameSummary): Boolean {
        checkAccount()
        if (compatibilityError != null) return false
        if (ready(game.id)) { connectionDemand.remove(game.id); uncertainGames.remove(game.id); return true }
        if (closed || !available(game.id) || credential() == null || credential() != lastCredential ||
            streams[game.id]?.lastGeneration == null || engineGame(game) == null) return false
        val started = connectionDemand.getOrPut(game.id, now)
        tick()
        // Do not extend an uncertain old socket's chain before its outcomes are known.
        if (game.id in uncertainGames) return false
        return now() - started < 3_000
    }
    fun available(gameId: String) = gameId !in unavailableGames && summaries[gameId]?.lifecycle !in listOf(GameLifecycle.CANCELLED, GameLifecycle.DELETED)
    fun ready(gameId: String) = ready && available(gameId) && streams[gameId]?.let { it.generation != null && it.head } == true
    /** A negative ordinary read fences personal context, not uncertain admitted originals. */
    fun unavailable(gameId: String) {
        unavailableGames.add(gameId); clearContext(gameId)
        streams[gameId]?.let { it.opening = false; it.generation = null }
        statusChanged()
    }
    private fun clearContext(gameId: String) {
        ownPodDeckNames.remove(gameId); presences.remove(gameId); endHeld.remove(gameId)
        preparations.remove(gameId); engineGames.remove(gameId); engineBacklog.remove(gameId); reloadAsked.remove(gameId)
        engineHeads.remove(gameId); headAsked.remove(gameId); unopenable.remove(gameId)
        acceptedRevisions.remove(gameId)
        chats.remove(gameId)
        streams[gameId]?.head = false
    }
    fun pending(gameId: String) = pending.values.any { it.scope.gameId == gameId } || preparations.pending(gameId)
    fun queuedTransfers(gameId: String) = preparations.queuedTransfers(gameId)
    fun acceptedPreparation(gameId: String) = preparations.acceptedPreparation(gameId)
    /** The board the table draws of [game] played on engine state (engine-responsiveness R4a), once it first loaded. */
    internal fun board(game: GameSummary): TableBoard? = engineGame(game)?.play()

    // ---- I-D56: games played on engine state
    private val engineGames = mutableMapOf<String, EngineGame>()
    /** What an engine game had to send while its stream wasn't ready (sent once it is). */
    private val engineBacklog = mutableMapOf<String, MutableList<GameAction>>()
    /** The revision an engine game that can't follow last asked to be loaded again at. */
    private val reloadAsked = mutableMapOf<String, String?>()
    /** The game [game] played on engine state on this device, while it is its current active play (once it first loaded
     * on the engine thread). */
    internal fun engineGame(game: GameSummary): EngineGame? = engineGames[game.id]?.takeIf { summary(game).let { current ->
        game.id in endHeld || current.lifecycle == GameLifecycle.ACTIVE && current.playId == it.base.id } && it.shown != null }
    /**
     * TRN-065 (owner, 2026-11-09): the games whose play the other player's concession or accepted suggestion ended while
     * this player stood at the table: the result is final, but they go to the lobby only after their own OK, so the ended
     * play's last board stays on this device, showing the dialog ([EngineGame.holdEnd]). Nothing it makes is sent any more.
     */
    private val endHeld = mutableSetOf<String>()
    /** TRN-065: whether [gameId]'s table stays on after its play ended ([endHeld]). */
    internal fun tableHeldAtEnd(gameId: String): Boolean = gameId in endHeld
    /** TRN-065: the player's own OK on [gameId]'s result (here or on another device): the held table goes, and the lobby shows. */
    internal fun releaseEnd(gameId: String, why: String) {
        if (!endHeld.remove(gameId)) return
        AppLog.info("game", "End of game: table let go, $why")
        engineGames.remove(gameId); engineBacklog.remove(gameId); reloadAsked.remove(gameId)
        summaries[gameId]?.let { changed(summary(it)) }
        statusChanged()
    }
    /** TRN-065: [message] ended [game]'s play: whether its table stays on until the player's own OK ([endHeld]). Only when
     * the game is open here with its board on the table and the OK is still theirs; else, the lobby at once with the dialog
     * over it (a device that wasn't at the table, a device that can't follow the line). */
    private fun holdEnd(game: EngineGame, message: GameRevision): Boolean {
        val owes = message.summary.lifecycle == GameLifecycle.LOBBY &&
            message.summary.resultNotice?.unseen?.contains(account ?: accountId()) == true
        if (!owes || !engineLoad(message.gameId) || game.shown == null || game.loadFailed) {
            releaseEnd(message.gameId, "no OK owed here")
            return false
        }
        if (endHeld.add(message.gameId)) {
            AppLog.info("game", "End of game: ${message.summary.resultNotice?.ending} while at the table, staying until the OK")
            game.holdEnd(message) { releaseEnd(message.gameId, "the device can't show the end") }
        }
        install(message.summary, message.resultRevision, message.preparation)
        return true
    }
    internal fun engineGame(gameId: String): EngineGame? = summaries[gameId]?.let(::engineGame)

    /** The newest engine head of a game that isn't loaded, kept until it opens (switchreview #11: only the open game loads
     * on the engine thread; the list needs only the summary). */
    private val engineHeads = mutableMapOf<String, GameHead>()
    /** Games whose asked-for head hasn't arrived yet (asked once until it does). */
    private val headAsked = mutableSetOf<String>()
    /** Games this device can't open (a head without engine state, or a load that failed): an error, never a reconnect loop. */
    private val unopenable = mutableSetOf<String>()

    /** Loads or reloads the engine game of [head] (a head of an active play whose state is the engine's). */
    private fun engineHead(head: GameHead) {
        val snapshot = head.engine ?: return
        val play = head.play ?: return
        val me = account ?: accountId() ?: return
        val existing = engineGames[head.gameId]?.takeIf { it.base.id == play.id }
        var following = false
        if (existing == null && !engineLoad(head.gameId)) {
            // A list game: its summary is all the list needs; it loads when it opens
            engineHeads[head.gameId] = head
        } else if (existing == null || existing.needsReload || behind(existing, head)) {
            // A head while the device follows the server exactly changes nothing; a new play, a device that asked to load
            // again, or a head past the revisions the device received (a resubscription replays at most 100,
            // switchreview #10) loads from it
            engineHeads.remove(head.gameId); unopenable.remove(head.gameId)
            if (existing == null) engineGames[head.gameId] = EngineGame(head.gameId, me, play, snapshot, worker = engineWorker,
                onShown = { engineShown(head.gameId) },
                pages = { through, done -> connection.historyPage(head.gameId, through, done) })
            else existing.load(snapshot, play)
            reloadAsked.remove(head.gameId)
        } else following = true
        // A head the game didn't load from says nothing of the revisions up to it that are still on their way (it can be
        // read before they are published): they carry the confirmations of the device's own events and the local heads,
        // and must not be fenced off as already received
        if (!following) {
            engineGames[head.gameId]?.revision = head.revision
            acceptedRevisions[head.gameId] = head.revision
        }
        // LOB-135: the game's assume reveal settings, as its host last set them
        engineGames[head.gameId]?.assumeReveals(head.summary.assumeReveals)
        if (summaries[head.gameId]?.let { newerOrEqual(head.revision, it.revision) } != false) summaries[head.gameId] = head.summary
        streams[head.gameId]?.head = true
        changed(summary(head.summary))
        pumpEngine(head.gameId)
    }
    /** [head] is past the revisions [game] received, and its last event isn't among those the device shows. */
    private fun behind(game: EngineGame, head: GameHead): Boolean {
        val received = game.revision ?: return true
        if (GameWire.ordinal(head.revision) <= GameWire.ordinal(received)) return false
        val shown = game.shown ?: return false
        val headId = head.engine?.headId ?: return false
        return shown.tip != headId && shown.line.none { it.id == headId }
    }

    /** A revision of an active play this device has no engine game for (a game's start, or what a resubscription replays
     * before the head): every play is engine state, so the game waits for its engine head; nothing goes through the old
     * history catch-up, whose server routes are gone (switchreview #1). */
    private fun awaitEngineHead(message: GameRevision) {
        engineHeads.remove(message.gameId)
        install(message.summary, message.resultRevision, message.preparation)
        if (acceptedRevisions[message.gameId]?.let { newerOrEqual(it, message.resultRevision) } != true)
            acceptedRevisions[message.gameId] = message.resultRevision
        if (engineLoad(message.gameId)) askHead(message.gameId)
    }
    /** Asks for [gameId]'s head, once until it arrives: the server sends the revisions after the device's, then the head. */
    private fun askHead(gameId: String) {
        val stream = streams[gameId] ?: return
        if (!ready || stream.generation == null || gameId in headAsked || !available(gameId)) return
        headAsked.add(gameId)
        send(SubscribeGame(gameId, acceptedRevisions[gameId] ?: "0", engine = true))
    }
    /** Each game that should be loaded but isn't (an active engine play): loads it from its kept head, or asks for one. */
    private fun loadOpened() {
        // Switch-review #11: only the open game has an engine state on the device; one that is no longer open is let go
        // (what it still has to send goes first) and its subscription becomes a summary one
        for (gameId in engineGames.keys.filter { !engineLoad(it) }) {
            endHeld.remove(gameId)
            val game = engineGames.getValue(gameId)
            if (game.shown != null) deliverEngine(gameId, game, game.take(Long.MAX_VALUE))
            engineGames.remove(gameId); engineBacklog.remove(gameId); reloadAsked.remove(gameId)
            val stream = streams[gameId]
            if (ready && stream?.generation != null && available(gameId))
                send(SubscribeGame(gameId, acceptedRevisions[gameId] ?: "0", engine = false))
        }
        for ((gameId, stream) in streams) {
            if (stream.generation == null || !engineLoad(gameId) || gameId in unopenable) continue
            val current = summaries[gameId] ?: continue
            if (current.lifecycle != GameLifecycle.ACTIVE || current.playId == null) continue
            if (engineGames[gameId]?.base?.id == current.playId) continue
            val kept = engineHeads[gameId]
            if (kept != null && kept.summary.playId == current.playId) engineHead(kept) else askHead(gameId)
        }
    }
    /** [gameId] can't be opened on this device: a clear error instead of reconnecting. */
    private fun cannotOpen(gameId: String, cause: String) {
        AppLog.warn("game", "Game can't be opened: $cause")
        unopenable.add(gameId)
        streams[gameId]?.head = true
        error = CANNOT_OPEN
        statusChanged()
    }

    /** An accepted revision of an engine game's play. */
    private fun engineRevision(game: EngineGame, message: GameRevision) {
        if (message.summary.lifecycle != GameLifecycle.ACTIVE || message.summary.playId != game.base.id) {
            if (holdEnd(game, message)) return
            engineGames.remove(message.gameId)
            if (message.summary.lifecycle == GameLifecycle.ACTIVE && message.summary.playId != null) awaitEngineHead(message)
            else install(message.summary, message.resultRevision, message.preparation)
            return
        }
        game.assumeReveals(message.summary.assumeReveals)
        game.revision(message)
        acceptedRevisions[message.gameId] = message.resultRevision
        if (summaries[message.gameId]?.let { newerOrEqual(message.resultRevision, it.revision) } != false) summaries[message.gameId] = message.summary
        changed(summary(message.summary))
        pumpEngine(message.gameId)
    }

    /** [gameId]'s engine game published a new board (main thread): the table shows it, what it sends goes out, and a
     * device that can't follow asks to load the game again (once per revision). */
    private fun engineShown(gameId: String) {
        val game = engineGames[gameId] ?: return
        if (game.loadFailed) { engineGames.remove(gameId); cannotOpen(gameId, "engineLoadFailed"); return }
        if (game.needsReload && gameId in streams && reloadAsked[gameId] != acceptedRevisions[gameId]) {
            reloadAsked[gameId] = acceptedRevisions[gameId]
            send(SubscribeGame(gameId, acceptedRevisions[gameId] ?: "0"))
        }
        summaries[gameId]?.let { changed(summary(it)) }
        pumpEngine(gameId)
    }

    /** The time for [gameId]'s engine game: its own steps (passes, assumptions), then what it sends. Called by the open
     * table every second and after every action. */
    fun engineTick(gameId: String) {
        val game = engineGame(gameId) ?: return
        // HIS-035, HIS-006: back, with everything the server had: live again unless there is something to catch up on
        if (game.awayPaused && caughtUp(gameId)) game.returned()
        game.tick(now())
        pumpEngine(gameId)
        statusChanged()
    }

    /** Sends what [gameId]'s engine game has to send, in order, on the ordinary queue. */
    fun pumpEngine(gameId: String) {
        val game = engineGames[gameId] ?: return
        val stream = streams[gameId] ?: return
        if ((stream.generation ?: stream.lastGeneration) == null) return
        engineBacklog.remove(gameId)?.let { deliverEngine(gameId, game, it) }
        // Taken on the engine thread, sent from here once it has them
        game.take(now()) { actions -> deliverEngine(gameId, game, actions) }
        flush()
        statusChanged()
    }
    private fun deliverEngine(gameId: String, game: EngineGame, actions: List<GameAction>) {
        if (engineGames[gameId] !== game || gameId in endHeld) return
        // SYS-013: the player's own game action waits for the connection: the next background attempt doesn't wait longer
        if (!ready && actions.any { it !is GameAction.EngineSeen }) nextConnectAt = 0L
        // SYS-016: a game action waiting for the server; after 3 s the table says "Reconnecting…" and refuses actions
        // until the connection is back ([reconnecting])
        if (!ready(gameId) && actions.any { it !is GameAction.EngineSeen }) connectionDemand.getOrPut(gameId, now)
        val stream = streams[gameId]
        val generation = stream?.generation ?: stream?.lastGeneration
        if (stream == null || generation == null) { engineBacklog.getOrPut(gameId) { mutableListOf() } += actions; return }
        for (action in actions) {
            val command = GameCommand(UUID.randomUUID().toString(), GameScope(gameId, playId = game.base.id), action, stream.id, generation)
            if (!GameWire.valid(command)) { AppLog.warn("engine", "Invalid engine command ${action::class.simpleName}"); continue }
            pending[command.requestId] = command
        }
        flush()
        statusChanged()
    }
    fun preparation(game: GameSummary): LobbyPreparation? = preparations.preparation(summary(game), game.id)
    fun summary(game: GameSummary): GameSummary = summaries[game.id]?.takeIf {
        ended(it) || newerOrEqual(it.revision, game.revision) } ?: game
    fun watch(games: List<GameSummary>) {
        checkAccount()
        for (game in games) {
            install(game, game.revision, null)
            streams.getOrPut(game.id) { SessionStream() }
        }
        tick()
    }
    /**
     * The background maintenance's tick (every second): [tick], except that while the socket is down after failed
     * attempts the next attempt waits ([reconnectDelay]: 1 s, doubling, at most [MAX_RECONNECT_DELAY_MS]) instead of
     * retrying every second (analysis 2026-11-05: a phone without a connection retried, and logged, without a pause).
     * Everything else that asks for the connection ([tick]: a tap, coming back to the app, a lobby read that worked, an
     * own game action to send) tries at once.
     */
    fun poll() {
        if (!closed && !connected && compatibilityError == null && now() < nextConnectAt) { statusChanged(); return }
        tick()
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
                    // A socket that never got the server's welcome failed: the next background attempt waits longer
                    failedConnects = if (welcome) 0 else failedConnects + 1
                    nextConnectAt = now() + reconnectDelay(failedConnects)
                    AppLog.warn("game", "Connection lost: $cause")
                    connectionUnavailable(cause)
                    if (unauthorized) authenticationRequired(current)
                }
            })
        } else if (welcome) {
            openStreams()
            streams.keys.forEach(chats::syncChatRead)
            loadOpened()
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
        if (action is GameAction.SendChat || action is GameAction.SelectDeck || action is GameAction.TransferPreparedCard ||
            action is GameAction.SetStarter || action is GameAction.SetAlternateStarter || action is GameAction.SetPodGameBasics || action is GameAction.ShowDeck ||
            action == GameAction.Ready || action == GameAction.WithdrawReady) return false
        val current = summary(game)
        if (current.lifecycle != GameLifecycle.LOBBY || current.preparationRound != game.preparationRound) return false
        return enqueue(game, action) != null
    }
    fun deleteGame(game: GameSummary): Boolean {
        val current = summary(game)
        if (game.lifecycle != GameLifecycle.ACTIVE || game.playId == null ||
            current.lifecycle != GameLifecycle.ACTIVE || current.playId != game.playId) return false
        return enqueue(game, GameAction.DeleteGame) != null
    }
    /** [outside]: with Ready, the player's dungeon cards outside the game (OutsideCards), sent with it. */
    fun readiness(game: GameSummary, actor: String, action: GameAction, outside: List<SubmittedDefinition> = emptyList()): Boolean = measureLatency(game, action) {
        if (action !is GameAction.SetStarter && action !is GameAction.SetAlternateStarter && action !is GameAction.SetPodGameBasics && action !is GameAction.ShowDeck &&
            action != GameAction.Ready && action != GameAction.WithdrawReady) return false
        val current = summary(game)
        val base = preparation(current) ?: return false
        val command = command(current, action)?.let { if (action == GameAction.Ready) it.copy(inlineDefinitions = outside) else it } ?: return false
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
        val timing = if (Diagnostics.enabled) DebugLatency.begin(game.id, "TransferPreparedCard", 0) else null
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
    /** LOB-135: the game's assume reveal settings, changed by its host, in the lobby or in play (the server refuses anyone
     * else); sent alongside whatever else of the game is on its way, as chat is. */
    fun setAssumeReveals(game: GameSummary, reveals: AssumeReveals): Boolean = measureLatency(game, GameAction.SetAssumeReveals(reveals)) {
        val current = summary(game)
        if (current.lifecycle !in listOf(GameLifecycle.LOBBY, GameLifecycle.ACTIVE) || current.assumeReveals == reveals) return false
        val command = command(current, GameAction.SetAssumeReveals(reveals)) ?: return false
        pending[command.requestId] = command
        latencySubmitted(command, false)
        error = null; flush(); statusChanged(); return true
    }
    /** TRN-065: the player tapped OK on [game]'s last result. */
    fun acknowledgeResult(game: GameSummary): Boolean = measureLatency(game, GameAction.AcknowledgeResult) {
        val current = summary(game)
        if (current.lifecycle != GameLifecycle.LOBBY || current.resultNotice == null) return false
        val command = command(current, GameAction.AcknowledgeResult) ?: return false
        pending[command.requestId] = command
        latencySubmitted(command, false)
        error = null; flush(); statusChanged(); return true
    }
    private fun command(game: GameSummary, action: GameAction): GameCommand? {
        if (action is GameAction.SendChat) { if (!activeRequest(game)) return null }
        else if (!ready(game.id) || credential() != token) return null
        if ((action !is GameAction.SendChat && action !is GameAction.SetAssumeReveals && pending(game.id)) || pending.size >= 256) return null
        val stream = streams.getValue(game.id)
        val scope = if (action is GameAction.SetAssumeReveals || action == GameAction.AcknowledgeResult) GameScope(game.id)
            else if (action == GameAction.DeleteGame || action is GameAction.SendChat) GameScope(game.id, playId = game.playId)
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
                require(message.protocolMajor == 1 && message.engineVersion == 1 && message.readFormat == 1)
                // SYS-033: app and server must speak the same protocol and rules; the older side waits for its update.
                if (message.minor != GAME_PROTOCOL_MINOR) {
                    incompatibleRules(clientOlder = message.minor > GAME_PROTOCOL_MINOR)
                    return
                }
                if (message.gameplayRulesVersion != CURRENT_GAMEPLAY_RULES_VERSION) {
                    incompatibleRules(clientOlder = message.gameplayRulesVersion > CURRENT_GAMEPLAY_RULES_VERSION)
                    return
                }
                compatibilityError = null; compatibilityRetryAt = Long.MAX_VALUE
                reconnectTrace.stepAll("protocolWelcome")
                welcome = true; error = null; resumePending = false; resumePing = null; failedConnects = 0; openStreams()
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
                    install(it, it.revision, null)
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
                if (available(message.gameId)) {
                    headAsked.add(message.gameId)
                    // Switch-review #11: the open game's head and revisions carry its engine state; the list's are summaries
                    send(SubscribeGame(message.gameId, acceptedRevisions[message.gameId] ?: "0",
                        engine = engineLoad(message.gameId)))
                }
                flush()
            }
            is GameHead -> {
                require(message.gameId == message.summary.id && message.revision == message.summary.revision)
                if (message.gameId !in streams) return
                // Presence is read fresh with every head; it is not revisioned state.
                message.opponentPresence?.let { presence(message.gameId, it) }
                headAsked.remove(message.gameId)
                // Fence old-incarnation reads before they can create a replica or trigger recovery.
                // A leading HTTP summary is deliberately not this accepted-state boundary.
                if (acceptedRevisions[message.gameId]?.let { !newerOrEqual(message.revision, it) } == true) return
                if (summaries[message.gameId]?.let { ended(it) && !ended(message.summary) } == true) return
                message.ownPodDeckName?.let { ownPodDeckNames[message.gameId] = it }
                chats.installChatHead(message)
                // I-D56: an active play is engine state, played on its engine game
                if (message.engine != null && message.play != null &&
                    message.summary.lifecycle == GameLifecycle.ACTIVE) engineHead(message)
                else if (message.summary.lifecycle == GameLifecycle.ACTIVE && message.play == null) {
                    // A summary head (switch-review #11: every heartbeat, and a game in the list): the summary and presence;
                    // the open game asks for its engine head when this one is past what it received (switch-review #10)
                    install(message.summary, message.revision, message.preparation)
                    streams[message.gameId]?.head = true
                    val loaded = engineGames[message.gameId]?.takeIf { it.base.id == message.summary.playId }
                    if (engineLoad(message.gameId) && loaded?.revision?.let { GameWire.ordinal(message.revision) > GameWire.ordinal(it) } == true)
                        askHead(message.gameId)
                }
                else {
                    install(message.summary, message.revision, message.preparation)
                    streams[message.gameId]?.head = true
                    // A play without engine state can't be played on this device (no history is downloaded any more)
                    if (message.play != null && message.summary.lifecycle == GameLifecycle.ACTIVE)
                        cannotOpen(message.gameId, "headWithoutEngine")
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
                    correlateSelections(message)
                    flush(); statusChanged(); return
                }
                if (summaries[message.gameId]?.let { ended(it) && !ended(message.summary) } == true) return
                message.chatMessage?.let { chats.installChat(message.gameId, message.resultRevision, listOf(it), snapshot = false) }
                val engine = engineGames[message.gameId]
                if (engine != null) {
                    engineRevision(engine, message)
                    correlateSelections(message)
                    flush(); statusChanged(); return
                }
                // I-D56: every play is engine state; one this device hasn't loaded waits for its engine head
                if (message.summary.lifecycle == GameLifecycle.ACTIVE && message.summary.playId != null) awaitEngineHead(message)
                else install(message.summary, message.resultRevision, message.preparation)
                correlateSelections(message)
            }
            is GameResult -> result(message)
            is GameReceipt -> Unit // Receipt is not acceptance and never removes an uncertain original.
            is GameAdmissionError -> {
                if (Diagnostics.enabled) message.requestId?.let(DebugLatency::result)
                if (message.code == "gameplayRulesUpgradeRequired" || message.code == "serverUpdatePending" ||
                    (!welcome && message.code == "invalidInput" && message.requestId == null)) {
                    // A server older than the app's Hello rejects it as unknown input: wait for the server update.
                    incompatibleRules(clientOlder = message.code == "gameplayRulesUpgradeRequired")
                }
                else if (message.code == "unauthorized") { val rejected = token; connectionUnavailable("admission:unauthorized"); rejected?.let(authenticationRequired) }
                else if (message.retryable || message.code == "streamFenced") connectionUnavailable("admission:${message.code}")
                else {
                    val original = message.requestId?.let(pending::get)
                    if (message.code == "accessDenied") message.gameId?.let(::unavailable)
                    message.requestId?.let { id ->
                        chats.failed(id, "Not sent. This request could not be used.")
                        pending.remove(id); preparations.forget(id); sent.remove(id)
                    }
                    if (original != null) error = "This game changed or the request could not be used. Refresh and try again."
                }
            }
            is GameUpgradeRequired -> incompatibleRules(clientOlder = true)
            is GamePresenceUpdate -> if (message.gameId in streams && available(message.gameId))
                presence(message.gameId, message.opponent)
            is GameChatReadUpdate -> if (message.gameId in streams && available(message.gameId)) chats.readChanged(message.gameId)
            is GamePong -> if (message.correlationId == resumePing) { resumePing = null; resumePending = false }
        }
        flush()
        recoveryReady()
        statusChanged()
    }
    private fun install(game: GameSummary, revision: String, preparation: LobbyPreparation?) {
        require(game.revision == revision); GameWire.ordinal(revision)
        // An HTTP summary can lead the actual replica. It must not prevent catching that replica up.
        if (acceptedRevisions[game.id]?.let { !newerOrEqual(revision, it) } == true) return
        val old = summaries[game.id]
        if (old != null && ended(old) && !ended(game)) return
        if (ended(game)) require(preparation == null)
        if (game.id in unavailableGames && old != null && revision == old.revision &&
            game.lifecycle in listOf(GameLifecycle.LOBBY, GameLifecycle.ACTIVE)) return
        if (old != null && GameWire.ordinal(revision) > GameWire.ordinal(old.revision)) unavailableGames.remove(game.id)
        if (game.lifecycle != GameLifecycle.LOBBY) preparations.leftLobby(game.id)
        if (game.lifecycle == GameLifecycle.CANCELLED || game.lifecycle == GameLifecycle.DELETED) clearContext(game.id)
        if (preparation != null && game.lifecycle == GameLifecycle.LOBBY) preparations.accept(game, revision, preparation)
        if ((preparation != null && game.lifecycle == GameLifecycle.LOBBY) || ended(game)) acceptedRevisions[game.id] = revision
        if (summaries[game.id]?.let { newerOrEqual(revision, it.revision) } != false) summaries[game.id] = game
        retireSelections(game.id)
        changed(summary(game))
    }
    private fun result(result: GameResult) {
        if (Diagnostics.enabled) DebugLatency.result(result.requestId)
        val original = pending[result.requestId] ?: return
        require(result.scope == original.scope)
        // I-D56 step C: the engine game hears of its refused actions (a stale submission, a refused rewind)
        engineGames[original.scope.gameId]?.let { engine ->
            if (result.outcome != "accepted" && (original.action is GameAction.Submit || original.action.isEngine)) {
                engine.refused(original.action, result.reason)
                post { pumpEngine(original.scope.gameId) }
            }
        }
        pending.remove(result.requestId); sent.remove(result.requestId)
        if (result.outcome != "accepted") chats.failed(result.requestId,
            "Not sent. The original play is no longer available or this request was refused.")
        preparations.settle(result) { retireSelections(original.scope.gameId) }
        // An engine game's refusals are its device's notes (I-D56 §8: a stale race is never shown as a refusal)
        error = if (result.outcome in setOf("accepted", "noOp") || original.action is GameAction.Submit || original.action.isEngine) null
            else refusalText(result.reason)
        if (original.action == GameAction.AcceptInvitation && result.outcome == "accepted" &&
            summaries[original.scope.gameId]?.podId != null) {
            send(SubscribeGame(original.scope.gameId, result.commitRevision ?: "0"))
        }
        settled(original, result)
    }

    private fun retireSelections(gameId: String) {
        // A newer HTTP summary is not a board/preparation replica and cannot settle its overlay.
        val revision = acceptedRevisions[gameId] ?: return
        preparations.retire(gameId, revision)
    }
    private fun correlateSelections(message: GameRevision) {
        preparations.correlate(message.requestId, message.resultRevision)
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
        epoch++; connected = false; welcome = false; resumePing = null; token = null; connection.disconnect(); sent.clear(); chats.disconnected()
        presences.clear(); headAsked.clear()
        streams.values.forEach { it.generation = null; it.head = false; it.opening = false; it.awaitingReady = false }
    }
    fun clear() { disconnect(); pending.clear(); preparations.clear();
        ownPodDeckNames.clear(); chats.clear(); acceptedRevisions.clear(); summaries.clear(); unavailableGames.clear(); streams.clear(); connectionDemand.clear(); uncertainGames.clear(); reconnectTrace.clear(); lastCredential = null; account = null; compatibilityError = null; compatibilityRetryAt = Long.MAX_VALUE; error = null; statusChanged() }
    override fun close() { closed = true; clear(); connection.close() }
    companion object {
        /** The table's line for a game this device can't open (SYS-052): never a reconnect loop. */
        const val CANNOT_OPEN = "This game can't be opened on this device. Send a problem report from the menu."
        /** The longest wait between background socket attempts after failures ([poll]). */
        const val MAX_RECONNECT_DELAY_MS = 10_000L
        fun newerOrEqual(a: String, b: String) = GameWire.ordinal(a) >= GameWire.ordinal(b)
        internal fun ended(game: GameSummary) = game.lifecycle !in listOf(GameLifecycle.LOBBY, GameLifecycle.ACTIVE)
    }
}
