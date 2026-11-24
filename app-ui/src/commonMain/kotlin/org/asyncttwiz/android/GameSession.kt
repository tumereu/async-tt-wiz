package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import kotlin.concurrent.Volatile
import org.asyncttwiz.appui.currentThread
import org.asyncttwiz.appui.nanoTime
import org.asyncttwiz.appui.randomUuid
import org.asyncttwiz.appui.simpleClassName

/** One watched game's server stream: its open/resume state and the authorized generation. */
data class SessionStream(val id: String = randomUuid(), var generation: String? = null,
    var head: Boolean = false, var opening: Boolean = false, var opened: Boolean = false,
    var lastGeneration: String? = null, var recoveryIds: List<String> = emptyList(), var awaitingReady: Boolean = false)

/** Main-thread, process-memory queue. Results settle intent; only revisioned reads change accepted state. */
class GameSession(private val connection: GameConnection, private val credential: () -> String?,
    private val post: (() -> Unit) -> Unit, private val authenticationRequired: (String) -> Unit,
    private val changed: (GameSummary) -> Unit, private val settled: (GameCommand, GameResult) -> Unit,
    private val statusChanged: () -> Unit, private val installationId: String = randomUuid(),
    private val accountId: () -> String? = { null },
    private val now: () -> Long = { nanoTime() / 1_000_000 },
    reconnectTelemetry: (ReconnectEpisode) -> Unit = { if (Diagnostics.enabled) DebugLatency.reconnect(it) },
    private val personalChat: GameChat = GameChat(),
    private val openedGame: () -> String? = { null },
    /** Where engine games do their work (I-D56 step D2): the app's engine thread, or inline in tests. */
    private val engineWorker: EngineWorker = EngineWorker.Direct,
    /** Which engine games to load on the engine thread (the app: only the open one; switchreview #11). */
    private val engineLoad: (String) -> Boolean = { true },
    /** CR 707.12: the cards engine games name for the engine ([EngineGame]'s): the phone's card records. */
    private val namedCards: org.asyncttwiz.protocol.game.NamedCards = org.asyncttwiz.protocol.game.NamedCards.NONE) : AutoCloseable {
    // The engine games' own-action probe reports to the app's slow-action recorder (it is app-engine's, without it)
    init { installEngineDiagnostics() }
    // Always traced: reports can be turned on mid-game; the default sink sends only while they are on.
    private val reconnectTrace = ReconnectRecorder(true, now, record = reconnectTelemetry)
    private var backgrounded = false
    private var resumePending = false
    private var resumePing: String? = null
    private fun recoveryReady() { streams.keys.filter(::ready).forEach {
        DebugJank.catchUp(it, false); reconnectTrace.step(it, "ready", complete = true)
    } }
    private val uiThread = currentThread()
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
    private val processId = randomUuid()
    private val streams = linkedMapOf<String, SessionStream>()
    private val summaries = mutableMapOf<String, GameSummary>()
    private val unavailableGames = mutableSetOf<String>()
    private val pending = linkedMapOf<String, GameCommand>()
    private val preparations = PreparationIntents()
    private val ownPodDeckNames = mutableMapOf<String, String>()
    fun ownPodDeckName(gameId: String): String? = ownPodDeckNames[gameId]
    /** The server took the player's new private pod deck [name] for [gameId] (DRF-105, from its lobby): shown until the
     * next head says it again. */
    fun podDeckNamed(gameId: String, name: String) { if (gameId in streams) ownPodDeckNames[gameId] = name }
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
    /** LOB-148: asks [gameId]'s chat read state now, also for a game not open. */
    fun askChatRead(gameId: String) = chats.askRead(gameId)
    fun outgoingChat(gameId: String) = chats.outgoingChat(gameId)
    fun loadOlderChat(gameId: String): Boolean = chats.loadOlderChat(gameId)
    fun sendChat(game: GameSummary, text: String): Boolean = measureLatency(game, GameAction.SendChat(text)) {
        val current = summary(game)
        // LOB-271 (owner, 2026-11-22): in play and before the game starts, in the play (or the lobby) it was written for
        if (!gameChatOpen(game) || !gameChatOpen(current) || game.lifecycle != current.lifecycle ||
            game.playId != current.playId || !ChatValidation.text(text)) return false
        val command = command(current, GameAction.SendChat(text)) ?: return false
        chats.submitted(command)
        pending[command.requestId] = command
        latencySubmitted(command, true) // Outgoing chat is immediately visible in the local UI.
        error = null; flush(); statusChanged(); return true
    }
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
    /** Problem report VSVLSK: the connection's opens, closes, failed sends, hello and welcome, stream opens and fences, each
     * with its cause and epoch; repeats summarised ([ConnectionLog]). */
    private val connectionLog = ConnectionLog(now)
    /** The one ordering rule for each game's server state: the received revision, and the revisions held until the ones
     * before them come ([RevisionOrder]). */
    private val order = RevisionOrder(connectionLog)
    /** Held revisions that follow what was just received, applied once the message that let them go is done ([drainHeld]). */
    private val released = ArrayDeque<GameRevision>()
    private var draining = false
    /** Why the next socket is opened: the cause the last one ended with (logged with the attempt). */
    private var connectReason = "the games need the connection"
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
        if (engineGames.isNotEmpty()) AppLog.info("game", "App away: ${engineGames.size} open game(s) pause: " +
            engineGames.values.joinToString { "${it.gameId.take(8)} at revision ${it.revision}" })
        engineGames.values.forEach { it.away() }
    }
    fun foreground() {
        reconnectTrace.stepAll("lifecycleForeground"); connectionDemand.clear()
        // A socket kept open while away may still hold undelivered events (a frozen process reads
        // them only now): its pong, answered in order, marks the end of that backlog. A new socket's
        // stream heads mark it instead.
        if (backgrounded || resumePending) {
            resumePending = true; resumePing = null
            if (connected && welcome) randomUuid().let { resumePing = it; send(GamePing(it)) }
            // HIS-035, HIS-205: the open tables stay away until everything the server had is in (the ping's answer, or a new
            // socket's welcome and the game's head)
            if (engineGames.isNotEmpty()) AppLog.info("game", "App back: the open game(s) stay away until the server's backlog is in (" +
                "${if (connected && welcome) "ping sent on the open socket" else "waiting for a new connection"}): " +
                engineGames.values.joinToString { "${it.gameId.take(8)} at revision ${it.revision}" })
        }
        backgrounded = false
        tick()
    }
    /** Everything the server had when the app returned to the foreground has been received and
     * published for this game, so a resumed table may decide live vs. timeline mode. */
    fun caughtUp(gameId: String): Boolean = !backgrounded && !resumePending && ready(gameId) &&
        // A head or list read ahead of the open play asked for the revisions in between: they are part of what the server had
        gameId !in headAsked && !order.holding(gameId)
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
        endTableUnavailable.remove(gameId); endTableAsked.remove(gameId)
        preparations.remove(gameId); engineGames.remove(gameId)?.close(); engineBacklog.remove(gameId); reloadAsked.remove(gameId)
        engineHeads.remove(gameId); headAsked.remove(gameId); unopenable.remove(gameId)
        order.forget(gameId); released.removeAll { it.gameId == gameId }
        chats.remove(gameId)
        streams[gameId]?.head = false
    }
    fun pending(gameId: String) = pending.values.any { it.scope.gameId == gameId } || preparations.pending(gameId)
    fun queuedTransfers(gameId: String) = preparations.queuedTransfers(gameId)
    fun acceptedPreparation(gameId: String) = preparations.acceptedPreparation(gameId)
    /** The board the table draws of [game] played on engine state, once it first loaded. */
    fun board(game: GameSummary): TableBoard? = engineGame(game)?.play()

    // ---- I-D56: games played on engine state
    private val engineGames = mutableMapOf<String, EngineGame>()
    /** What an engine game had to send while its stream wasn't ready (sent once it is). */
    private val engineBacklog = mutableMapOf<String, MutableList<GameAction>>()
    /** The revision an engine game that can't follow last asked to be loaded again at. */
    private val reloadAsked = mutableMapOf<String, String?>()
    /** The game [game] played on engine state on this device, while it is its current active play (once it first loaded
     * on the engine thread). */
    fun engineGame(game: GameSummary): EngineGame? = engineGames[game.id]?.takeIf { summary(game).let { current ->
        game.id in endHeld || current.lifecycle == GameLifecycle.ACTIVE && current.playId == it.base.id } && it.observedLoaded }
    /**
     * TRN-065 (owner, 2026-11-09): the games whose play a concession or an accepted suggestion ended while this player
     * stood at the table: the result is final, but nobody goes to the lobby until both players have pressed OK (the one
     * whose own action ended it has given theirs), so the ended play's last board stays on this device, showing the dialog
     * ([EngineGame.holdEnd]). Nothing it makes is sent any more.
     */
    private val endHeld = mutableSetOf<String>()
    /** TRN-065: whether [gameId]'s table stays on after its play ended ([endHeld]). */
    fun tableHeldAtEnd(gameId: String): Boolean = gameId in endHeld
    /**
     * TRN-142 (owner, 2026-11-22): the games opened here while an OK on their last result is still owed whose ended play's
     * table can't show on this device (it failed to load or to show its end): their result shows over the
     * lobby instead. Forgotten when the game closes.
     */
    private val endTableUnavailable = mutableSetOf<String>()
    /** TRN-142: whether [gameId], opened while an OK on its last result is owed, shows its ended play's table (loading until its
     * head comes) rather than the lobby. */
    fun endTableShown(gameId: String): Boolean = gameId !in endTableUnavailable
    /** TRN-142: the summary revision of each game whose ended play's head was asked for at it (asked once per revision). */
    private val endTableAsked = mutableMapOf<String, String>()
    /** TRN-065: both OKs stand on [gameId]'s result (the player's own last, here or on another device, or the other player's):
     * the held table goes, and the lobby shows. */
    fun releaseEnd(gameId: String, why: String) {
        endTableAsked.remove(gameId)
        if (!endHeld.remove(gameId)) return
        AppLog.info("game", "End of game: table let go, to the lobby: $why")
        engineGames.remove(gameId)?.close(); engineBacklog.remove(gameId); reloadAsked.remove(gameId)
        released += order.letGo(gameId)
        summaries[gameId]?.let { changed(summary(it)) }
        drainHeld()
        statusChanged()
    }
    /** TRN-142 (owner, 2026-11-23): a game whose result waits for an OK shows it over the lobby instead of over its final board
     * ([how]): a fault, logged as an error and reported (the device's own line says why it couldn't show the end). */
    @OptIn(UnobservedEngineState::class) // the server stream's handling, outside composition
    private fun endUnshown(gameId: String, how: String, revision: String?) {
        val summary = summaries[gameId]
        Faults.report("GameSession", "ended play's board can't be shown: the result over the lobby", mapOf("game" to gameId,
            "how" to how, "revision" to "$revision", "play" to "${summary?.playId ?: engineGames[gameId]?.base?.id}",
            "ending" to "${summary?.resultNotice?.ending}", "owed" to "${summary?.resultNotice?.unseen?.size}",
            "loadFailed" to "${engineGames[gameId]?.loadFailed}"))
    }

    /** TRN-065: [message] ended [game]'s play: whether its table stays on until both OKs stand ([endHeld]). Only when the
     * game is open here with its board on the table and an OK is still owed (this player's or the other's); else, the lobby
     * at once, with the dialog over it while an OK is owed (a device that wasn't at the table, a device that can't follow
     * the line). */
    @OptIn(UnobservedEngineState::class) // the server stream's handling, outside composition
    private fun holdEnd(game: EngineGame, message: GameRevision): Boolean {
        val notice = message.summary.resultNotice?.takeIf { message.summary.lifecycle == GameLifecycle.LOBBY }
        val owed = notice?.unseen.orEmpty()
        // TRN-142: a table already held at the end (one opened while the ended play still loads too) stays
        if (notice != null && message.gameId in endHeld && engineLoad(message.gameId) && !game.loadFailed) {
            install(message.summary, message.resultRevision, message.preparation, "a revision while the table is held at the end")
            return true
        }
        if (notice == null || !engineLoad(message.gameId) || game.shown == null || game.loadFailed) {
            val why = if (notice == null) "both OKs stand (revision ${message.resultRevision}, " +
                "${if (message.author == (account ?: accountId())) "own request" else "the other player's request"})"
                else "the device can't hold the table (open ${engineLoad(message.gameId)}, shown ${game.shown != null}, failed ${game.loadFailed})"
            // TRN-065 (owner, prod 0.2.0): a play ended here without its table held is said too, not only a held one let go
            if (message.gameId !in endHeld) AppLog.info("game", "End of game at revision ${message.resultRevision}: to the lobby: $why")
            releaseEnd(message.gameId, why)
            return false
        }
        if (endHeld.add(message.gameId)) {
            AppLog.info("game", "End of game: ${notice.ending} while at the table, staying until both OKs; owed by " +
                owed.joinToString { if (it == (account ?: accountId())) "this player" else "the other player" })
            game.holdEnd(message) {
                endUnshown(message.gameId, "at the table when it ended", message.resultRevision)
                releaseEnd(message.gameId, "the device can't show the end")
            }
        }
        install(message.summary, message.resultRevision, message.preparation, "the revision that ended the play")
        return true
    }
    fun engineGame(gameId: String): EngineGame? = summaries[gameId]?.let(::engineGame)

    /**
     * Whether [gameId] is the open play at the table, whose state is built from its revisions' events ([RevisionOrder]'s
     * stepped game: a head or list read ahead of it waits for the revisions, a revision ahead is held): its engine game is
     * loaded here and plays on, not a table held at the end (TRN-065) nor a game that failed to load. TRN-065, LOB-046
     * (owner, prod 0.2.0): so a head or list read saying the play ended never takes the player to the lobby before the
     * revision that ended it plays the end at the table ([holdEnd]).
     */
    private fun stepped(gameId: String): Boolean {
        val game = engineGames[gameId] ?: return false
        return gameId !in endHeld && engineLoad(gameId) && !game.loadFailed
    }

    /** [gameId]'s received revision moves to [revision] ([RevisionOrder.applied]); the held revisions that now follow apply
     * once the message being handled is done ([drainHeld]). */
    private fun received(gameId: String, revision: String) { released += order.applied(gameId, revision) }

    /** Applies the held revisions [received] and [RevisionOrder.loaded] let go, in order (each may let more go). */
    private fun drainHeld() {
        if (draining) return
        draining = true
        try {
            while (released.isNotEmpty()) {
                val message = released.removeFirst()
                if (message.gameId in streams) applyRevision(message)
            }
        } finally { draining = false }
    }

    /** The newest engine head of a game that isn't loaded, kept until it opens (switchreview #11: only the open game loads
     * on the engine thread; the list needs only the summary). */
    private val engineHeads = mutableMapOf<String, GameHead>()
    /** Games whose asked-for head hasn't arrived yet (asked once until it does). */
    private val headAsked = mutableSetOf<String>()
    /** Games this device can't open (a head without engine state, or a load that failed): an error, never a reconnect loop. */
    private val unopenable = mutableSetOf<String>()

    /** Loads or reloads the engine game of [head] (a head of an active play whose state is the engine's). */
    @OptIn(UnobservedEngineState::class) // the server stream's handling, outside composition
    private fun engineHead(head: GameHead) {
        val snapshot = head.engine ?: return
        val play = head.play ?: return
        val me = account ?: accountId() ?: return
        val existing = engineGames[head.gameId]?.takeIf { it.base.id == play.id }
        var following = false
        if (existing == null && !engineLoad(head.gameId)) {
            // A list game: its summary is all the list needs; it loads when it opens
            engineHeads[head.gameId] = head
        } else if (existing != null && !existing.needsReload && behind(existing, head) && asksPast(existing, head)) {
            // The revisions up to it are asked for again instead ([asksPast]): the game follows them
            following = true
        } else if (existing == null || existing.needsReload || behind(existing, head)) {
            headsAhead.remove(head.gameId)
            // A head while the device follows the server exactly changes nothing; a new play, a device that asked to load
            // again, or a head past the revisions the device received (a resubscription replays at most 100,
            // switchreview #10) loads from it
            engineHeads.remove(head.gameId); unopenable.remove(head.gameId)
            if (existing == null) engineGames[head.gameId] = EngineGame(head.gameId, me, play, snapshot, worker = engineWorker,
                onShown = { engineShown(head.gameId) },
                pages = { through, done -> connection.historyPage(head.gameId, through, done) }, namedCards = namedCards,
                device = EngineBoundary.devices)
            else {
                // Why an open game loads again (e2e CombatHoldTest, 2026-11-10: a load in the middle of play said nothing of it)
                AppLog.info("engine", "Game ${head.gameId.take(8)} loads again from head ${head.engine?.headId?.take(8)} at revision " +
                    "${head.revision}: ${if (existing.needsReload) "the device can't follow" else "the head is past revision ${existing.revision}"}")
                // HIS-006: the open game loading again doesn't make a live table pause (unless the app is away or the
                // table already paused: [EngineGame.load] decides and logs)
                existing.load(snapshot, play, live = true)
            }
            reloadAsked.remove(head.gameId)
        } else { following = true; headsAhead.remove(head.gameId) }
        // HIS-205, HIS-035: every engine head of an open game, from where to where and what it did (a head past the revisions
        // is how a take-back can reach a phone back from sleep)
        if (existing != null) AppLog.info("engine", "Head of game ${head.gameId.take(8)}: revision ${existing.revision} → ${head.revision}, " +
            "head ${snapshot.headId.take(8)}${snapshot.undoneBy?.let { ", an undo by $it took back ${snapshot.takenBack.size} seen events" } ?: ""}" +
            ", app ${if (existing.awayPaused) "away" else "here"}: ${if (following) "nothing new, the game follows the revisions" else "the game loaded again from it"}")
        // A head the game didn't load from says nothing of the revisions up to it that are still on their way (it can be
        // read before they are published): they carry the confirmations of the device's own events and the local heads,
        // so only a head the game loads from is received ([RevisionOrder.loaded])
        if (!following) {
            engineGames[head.gameId]?.revision = head.revision
            released += order.loaded(head.gameId, head.revision)
        }
        // LOB-135: the game's assume reveal settings, as its host last set them
        engineGames[head.gameId]?.assumeReveals(head.summary.assumeReveals)
        if (summaries[head.gameId]?.let { newerOrEqual(head.revision, it.revision) } != false) summaries[head.gameId] = head.summary
        streams[head.gameId]?.head = true
        changed(summary(head.summary))
        // The revisions held after the head the game loaded from apply now ([RevisionOrder.loaded])
        drainHeld()
        pumpEngine(head.gameId)
    }
    /**
     * TRN-142 (owner, 2026-11-22): [head] is the open game's lobby head while an OK on its last result is still owed: the
     * ended play it carries (with its engine state) loads and its table holds the end until both OKs stand, as when the play
     * ended at the table ([holdEnd]): its result's dialog over its last board. One that can't load or show it leaves the
     * result over the lobby.
     */
    private fun endTable(head: GameHead) {
        val notice = head.summary.resultNotice
        if (head.summary.lifecycle != GameLifecycle.LOBBY || notice == null || !engineLoad(head.gameId) || head.gameId in endHeld) return
        val play = head.play
        val snapshot = head.engine
        // A summary head (the list's subscription, a heartbeat) carries neither: the one asked for comes ([loadOpened])
        if (play == null || snapshot == null) return
        val me = account ?: accountId() ?: return
        engineGames.remove(head.gameId)?.close(); engineBacklog.remove(head.gameId); reloadAsked.remove(head.gameId)
        val game = EngineGame(head.gameId, me, play, snapshot, worker = engineWorker, onShown = { engineShown(head.gameId) },
            pages = { through, done -> connection.historyPage(head.gameId, through, done) }, namedCards = namedCards,
            device = EngineBoundary.devices)
        engineGames[head.gameId] = game
        endHeld += head.gameId
        game.assumeReveals(head.summary.assumeReveals)
        AppLog.info("game", "End of game: ${notice.ending}, opened at its ended play's table (play ${play.id.take(8)}, head " +
            "${snapshot.headId.take(8)}, revision ${head.revision}), staying until both OKs; owed by " +
            notice.unseen.joinToString { if (it == me) "this player" else "the other player" })
        // What the player hadn't watched shows at once, as when the play ended at the table
        game.holdEnd(GameRevision(head.gameId, head.revision, head.revision, "", "", GameScope(head.gameId), head.summary)) {
            endTableUnavailable += head.gameId
            endUnshown(head.gameId, "opened again, its end couldn't be shown", head.revision)
            releaseEnd(head.gameId, "the device can't show the ended play's end: the result over the lobby")
        }
        changed(summary(head.summary))
        statusChanged()
    }

    /** A head of an open game past the revisions it received that it didn't load from ([asksPast]): its revision, and how many
     * such heads in a row the revisions asked for again caught up with. By game. */
    private class HeadAhead(val revision: String, val asked: Int)
    private val headsAhead = mutableMapOf<String, HeadAhead>()

    /**
     * Merge gate 2026-11-24 (CoreLoopTest.theStepArrowGoesToCombatAndDeclares): [head] of the open game [game] is past the
     * revisions it received, read right after a revision was committed whose publication is still on its way (the server
     * registers the subscription before it replays, so what is committed after that reaches the device as a publication, but
     * the head read after the replay can come first). Loading from such a head took back the board the player had just been
     * shown (their own events, the other player's equivalent ones about to be adopted) and refused the press they made on it
     * as stale. So the revisions after the received one are asked for again first (the server replays them, then sends its head
     * again): true while it does. The game loads from a head that stays past them only when the revisions asked for didn't
     * come (a replay can't bring them: more than it carries), after [MAX_HEADS_AHEAD] such heads in a row, or when the head is
     * further ahead than one replay carries ([REPLAY_REVISIONS]). Main thread.
     */
    @OptIn(UnobservedEngineState::class) // the server stream's handling, outside composition
    private fun asksPast(game: EngineGame, head: GameHead): Boolean {
        val received = game.revision ?: return false
        val gap = GameWire.ordinal(head.revision) - GameWire.ordinal(received)
        val last = headsAhead[head.gameId]
        val caughtUp = last == null || GameWire.ordinal(received) >= GameWire.ordinal(last.revision)
        val asked = (last?.asked ?: 0) + 1
        val why = when {
            gap > REPLAY_REVISIONS -> "it is $gap revisions ahead, more than one replay carries"
            !caughtUp -> "the revisions up to ${last?.revision} asked for didn't come"
            asked > MAX_HEADS_AHEAD -> "the ${asked}th head in a row past the revisions received"
            streams[head.gameId]?.generation == null || !ready || !available(head.gameId) -> "the stream isn't ready to ask"
            else -> null
        }
        if (why != null) {
            AppLog.info("engine", "Game ${head.gameId.take(8)}: head ${head.engine?.headId?.take(8)} at revision ${head.revision} is " +
                "past revision $received: it loads from it ($why)")
            return false
        }
        headsAhead[head.gameId] = HeadAhead(head.revision, asked)
        AppLog.info("engine", "Game ${head.gameId.take(8)}: head ${head.engine?.headId?.take(8)} at revision ${head.revision} is " +
            "past revision $received (shown tip ${game.shown?.tip?.take(8)}): not loaded from; the revisions after $received asked " +
            "for again (committed after the replay was read, their publication on its way; head $asked in a row)")
        askHead(head.gameId)
        return true
    }

    /** [head] is past the revisions [game] received, and its last event isn't among those the device shows. */
    @OptIn(UnobservedEngineState::class) // the server stream's handling, outside composition
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
        install(message.summary, message.resultRevision, message.preparation, "a revision before the engine head")
        received(message.gameId, message.resultRevision)
        if (engineLoad(message.gameId)) askHead(message.gameId)
    }
    /** Asks for [gameId]'s head, once until it arrives: the server sends the revisions after the device's, then the head. */
    private fun askHead(gameId: String) {
        val stream = streams[gameId] ?: return
        if (!ready || stream.generation == null || gameId in headAsked || !available(gameId)) return
        headAsked.add(gameId)
        send(SubscribeGame(gameId, order.received(gameId) ?: "0", engine = true))
    }
    /**
     * The game open on the device changed ([engineLoad]): a game no longer open is let go at once, not at the next tick
     * (TRN-065, e2e GameEndDialogTest: a table held at the game's end showed again when the game was opened right after
     * leaving it, instead of the dialog over the lobby).
     */
    fun openedChanged() { if (!closed) letGoClosed() }

    /** Each game that should be loaded but isn't (an active engine play): loads it from its kept head, or asks for one. */
    private fun loadOpened() {
        letGoClosed()
        for ((gameId, stream) in streams) {
            if (stream.generation == null || !engineLoad(gameId) || gameId in unopenable) continue
            val current = summaries[gameId] ?: continue
            // TRN-142 (owner, 2026-11-22): a game whose result still waits for an OK opens at its ended play's table
            if (current.lifecycle == GameLifecycle.LOBBY && current.resultNotice != null) {
                // (a head already asked for, say the stream's own summary one, comes first: it may not carry the play)
                if (engineGames[gameId] == null && gameId !in endTableUnavailable && gameId !in headAsked &&
                    endTableAsked[gameId] != current.revision) {
                    askHead(gameId)
                    if (gameId in headAsked) {
                        endTableAsked[gameId] = current.revision
                        AppLog.info("game", "End of game: ${gameId.take(8)} opened while an OK on its result is owed: its ended play's " +
                            "head asked for, for its table (revision ${current.revision})")
                    }
                }
                continue
            }
            if (current.lifecycle != GameLifecycle.ACTIVE || current.playId == null) continue
            if (engineGames[gameId]?.base?.id == current.playId) continue
            val kept = engineHeads[gameId]
            if (kept != null && kept.summary.playId == current.playId) engineHead(kept) else askHead(gameId)
        }
    }

    /** Switch-review #11: only the open game has an engine state on the device; one that is no longer open is let go (what
     * it still has to send goes first) and its subscription becomes a summary one. */
    @OptIn(UnobservedEngineState::class) // the server stream's handling, outside composition
    private fun letGoClosed() {
        // TRN-142: a game opened again tries its ended play's table again
        endTableUnavailable.removeAll { !engineLoad(it) }; endTableAsked.keys.removeAll { !engineLoad(it) }
        headsAhead.keys.removeAll { !engineLoad(it) }
        for (gameId in engineGames.keys.filter { !engineLoad(it) }) {
            if (endHeld.remove(gameId)) AppLog.info("game", "End of game: table let go, the game closed (an OK still owed)")
            val game = engineGames[gameId] ?: continue // let go already by an earlier one's delivery
            if (game.shown != null) game.takeNow(Long.MAX_VALUE) { actions -> deliverEngine(gameId, game, actions) }
            engineGames.remove(gameId)?.close(); engineBacklog.remove(gameId); reloadAsked.remove(gameId)
            released += order.letGo(gameId); drainHeld()
            val stream = streams[gameId]
            if (ready && stream?.generation != null && available(gameId))
                send(SubscribeGame(gameId, order.received(gameId) ?: "0", engine = false))
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
            engineGames.remove(message.gameId)?.close()
            if (message.summary.lifecycle == GameLifecycle.ACTIVE && message.summary.playId != null) awaitEngineHead(message)
            else install(message.summary, message.resultRevision, message.preparation, "a revision")
            return
        }
        game.assumeReveals(message.summary.assumeReveals)
        game.revision(message)
        received(message.gameId, message.resultRevision)
        if (summaries[message.gameId]?.let { newerOrEqual(message.resultRevision, it.revision) } != false) summaries[message.gameId] = message.summary
        changed(summary(message.summary))
        pumpEngine(message.gameId)
    }

    /** [gameId]'s engine game published a new board (main thread): the table shows it, what it sends goes out, and a
     * device that can't follow asks to load the game again (once per revision). */
    @OptIn(UnobservedEngineState::class) // the server stream's handling, outside composition
    private fun engineShown(gameId: String) {
        val game = engineGames[gameId] ?: return
        // TRN-142: an ended play's table that fails to load leaves the result over the lobby
        if (game.loadFailed && gameId in endHeld) {
            endTableUnavailable += gameId
            endUnshown(gameId, "opened again, its ended play failed to load", order.received(gameId))
            releaseEnd(gameId, "the ended play's table failed to load: the result over the lobby")
            return
        }
        if (game.loadFailed) { engineGames.remove(gameId)?.close(); cannotOpen(gameId, "engineLoadFailed"); return }
        if (game.needsReload && gameId in streams && reloadAsked[gameId] != order.received(gameId)) {
            reloadAsked[gameId] = order.received(gameId)
            send(SubscribeGame(gameId, order.received(gameId) ?: "0"))
        }
        summaries[gameId]?.let { changed(summary(it)) }
        pumpEngine(gameId)
    }

    /** The time for [gameId]'s engine game: its own steps (passes, assumptions), then what it sends. Called by the open
     * table every second and after every action. */
    fun engineTick(gameId: String) {
        val game = engineGame(gameId) ?: return
        // HIS-035, HIS-006: back, with everything the server had: live again unless there is something to catch up on
        if (game.awayPaused && caughtUp(gameId)) {
            AppLog.info("game", "Game ${gameId.take(8)} back: the server's backlog is in (stream ready with its head, no revisions asked for or held), revision ${game.revision}")
            game.returned()
        }
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
            val command = GameCommand(randomUuid(), GameScope(gameId, playId = game.base.id), action, stream.id, generation)
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
            // A list read is read whenever it was asked for: one ahead of the open play at the table waits for its revisions
            when (order.snapshot(game.id, game.revision, "a list read", stepped(game.id), whole = ended(game))) {
                RevisionOrder.Snapshot.Waits -> askHead(game.id)
                RevisionOrder.Snapshot.Behind -> Unit
                RevisionOrder.Snapshot.Applies -> install(game, game.revision, null, "a list read")
            }
            streams.getOrPut(game.id) { SessionStream() }
        }
        drainHeld()
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
            if (connected) {
                val cause = if (current == null) "credentialUnavailable" else "credentialChanged"
                streams.keys.forEach { reconnectTrace.start(it, cause) }
                connectionLog.info("credential", "Connection ends (epoch $epoch): $cause")
                connectReason = cause
            }
            disconnect()
        }
        if (current == null || streams.isEmpty()) return
        if (!connected) {
            if (compatibilityError != null) compatibilityRetryAt = now() + 30_000
            token = current; lastCredential = current; connected = true; lastPing = now(); val generation = ++epoch
            connectionLog.info("connect", "Connecting (epoch $generation): $connectReason; ${pending.size} commands waiting")
            reconnectTrace.stepAll("socketConnectStart")
            statusChanged()
            connection.connect(current, object : GameConnection.Listener {
                private fun deliver(action: () -> Unit) = post {
                    if (!closed && epoch == generation && credential() == current && sameAccount()) action()
                }
                override fun opened() = deliver {
                    connectionLog.info("opened", "Connected (epoch $generation): hello sent")
                    reconnectTrace.stepAll("socketConnectedHttpAuthenticated")
                    send(GameHello(processId, installationId, maxMinor = GAME_PROTOCOL_MINOR, gameplayRulesVersion = CURRENT_GAMEPLAY_RULES_VERSION))
                }
                override fun message(message: GameServerMessage) = deliver {
                    val failure = runCatching { receive(message) }.exceptionOrNull()
                    if (failure != null) {
                        AppLog.warn("game", "Could not apply ${message::class.simpleName}", failure)
                        connectionUnavailable("streamError:${message::class.simpleName}:${simpleClassName(failure)}")
                    }
                }
                override fun disconnected(unauthorized: Boolean) = disconnected(unauthorized, "socketDisconnected")
                override fun disconnected(unauthorized: Boolean, cause: String) = deliver {
                    // A socket that never got the server's welcome failed: the next background attempt waits longer
                    failedConnects = if (welcome) 0 else failedConnects + 1
                    nextConnectAt = now() + reconnectDelay(failedConnects)
                    connectionLog.warn("lost", "Connection lost (epoch $generation, ${if (welcome) "after" else "before"} the welcome): " +
                        "$cause; ${pending.size} commands waiting, next attempt in ${reconnectDelay(failedConnects)} ms or at the next action")
                    connectionUnavailable(cause, logged = true)
                    if (unauthorized) authenticationRequired(current)
                }
            })
        } else if (welcome) {
            openStreams()
            streams.keys.forEach(chats::syncChatRead)
            loadOpened()
            // The server's traffic lease counts protocol messages, not WebSocket control pings.
            if (now() - lastPing >= 25_000) { lastPing = now(); send(GamePing(randomUuid())) }
        }
        connectionDemand.keys.removeAll { ready(it) }
        uncertainGames.removeAll { ready(it) }
        recoveryReady()
        statusChanged()
    }
    fun submit(game: GameSummary, action: GameAction): Boolean {
        if (action == GameAction.DeleteGame) return deleteGame(game)
        if (action is GameAction.SendChat || action is GameAction.SelectDeck || action is GameAction.TransferPreparedCard ||
            action is GameAction.SetStarter || action is GameAction.SetAlternateStarter || action is GameAction.ShowDeck ||
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
    fun readiness(game: GameSummary, actor: String, action: GameAction): Boolean = measureLatency(game, action) {
        if (action !is GameAction.SetStarter && action !is GameAction.SetAlternateStarter && action !is GameAction.ShowDeck &&
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
    /** [lender]: the game creator's account id when [source] is one of the decks they lend (LOB-256). */
    fun selectDeck(game: GameSummary, actor: String, source: SavedDeck, definitions: List<SubmittedDefinition>, lender: String? = null): Boolean =
        measureLatency(game, GameAction.SelectDeck(source.deck.id, source.deck.revision)) {
        val current = summary(game)
        val base = preparation(current) ?: return false
        val action = GameAction.SelectDeck(source.deck.id, source.deck.revision)
        val command = command(current, action) ?: return false
        if (PreparationRules.select(current, base, actor, command.requestId, command.scope, action, source, definitions, lender).state == null) return false
        preparations.selected(command, actor, source, definitions, lender)
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
    /** LOB-247: the game's deck settings, changed by its creator in the lobby (the server refuses anyone else). */
    fun setDeckSettings(game: GameSummary, settings: DeckSettings): Boolean = measureLatency(game, GameAction.SetDeckSettings(settings)) {
        val current = summary(game)
        if (current.lifecycle != GameLifecycle.LOBBY || current.deckSettings == settings) return false
        val command = command(current, GameAction.SetDeckSettings(settings)) ?: return false
        AppLog.info("lobby", "Deck settings of ${current.id.take(8)} asked: ${current.deckSettings} -> $settings")
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
        val stream = streams[game.id] ?: return null.also { Faults.report("GameSession", "command for a game without a stream", mapOf("game" to game.id)) }
        val generation = stream.generation ?: stream.lastGeneration
            ?: return null.also { Faults.report("GameSession", "command for a stream without a generation", mapOf("game" to game.id)) }
        val scope = if (action is GameAction.SetAssumeReveals || action == GameAction.AcknowledgeResult) GameScope(game.id)
            else if (action == GameAction.DeleteGame || action is GameAction.SendChat) GameScope(game.id, playId = game.playId)
            else GameScope(game.id, game.preparationRound,
                if (action == GameAction.AcceptInvitation || action == GameAction.DeclineInvitation || action == GameAction.Reinvite) game.invitationId else null)
        return GameCommand(randomUuid(), scope,
            action, stream.id, generation)
    }
    private fun enqueue(game: GameSummary, action: GameAction): GameCommand? = measureLatency(game, action) {
        val command = command(game, action) ?: return null
        pending[command.requestId] = command
        latencySubmitted(command, false)
        error = null; flush(); statusChanged(); return command
    }
    private fun openStreams() {
        val asked = mutableListOf<String>()
        try {
            for ((id, stream) in streams) if (!stream.opening) {
                if (!available(id) && pending.values.none { it.scope.gameId == id }) continue
                stream.opening = true
                // Only a resume recovers the outcomes of what was sent before (SYS-014); an open starts the stream afresh, and
                // what is still waiting goes again under its own request ids (the server answers a repeat with its outcome)
                val ids = if (stream.opened) pending.values.filter { it.scope.gameId == id }.map { it.requestId } else emptyList()
                if (!stream.opened) sent.removeAll { pending[it]?.scope?.gameId == id }
                stream.recoveryIds = ids
                stream.awaitingReady = true
                reconnectTrace.step(id, if (stream.opened) "streamResumeStart" else "streamOpenStart")
                asked += "${if (stream.opened) "resume" else "open"} ${id.take(8)}" + if (ids.isEmpty()) "" else " (${ids.size} to recover)"
                if (!send(if (stream.opened) ResumeGameStream(id, stream.id, ids) else OpenGameStream(id, stream.id))) return
            }
        } finally {
            if (asked.isNotEmpty()) connectionLog.info("streams", "Game streams asked (epoch $epoch): ${asked.joinToString()}")
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
                connectionLog.info("welcome", "Welcome (epoch $epoch): ${streams.size} game streams, ${pending.size} commands waiting")
                connectReason = "the games need the connection"
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
                if (message.statuses.isNotEmpty() || engineLoad(message.gameId)) connectionLog.info("ready",
                    "Game ${message.gameId.take(8)} stream ready (epoch $epoch, generation ${message.generation})" +
                    message.statuses.groupBy { it.status }.entries.joinToString("") { (status, list) -> ", ${list.size} $status" })
                reconnectTrace.step(message.gameId, "streamAuthorized")
                require(message.statuses.size == stream.recoveryIds.size &&
                    message.statuses.map { it.requestId }.toSet() == stream.recoveryIds.toSet())
                message.ended?.let {
                    require(it.id == message.gameId && ended(it))
                    install(it, it.revision, null, "a stream ready")
                }
                // Successful ordinary stream authorization is fresh evidence of availability;
                // a list/head omission alone was never a fabricated cancellation revision.
                if (message.ended == null) unavailableGames.remove(message.gameId)
                for (status in message.statuses) when (status.status) {
                    "final" -> { val result = requireNotNull(status.result); require(result.requestId == status.requestId); result(result) }
                    "pending" -> sent.add(status.requestId)
                    "notAdmitted" -> if (available(message.gameId)) sent.remove(status.requestId) else {
                        // A lobby action that will never be taken: what it showed is taken back
                        pending.remove(status.requestId); sent.remove(status.requestId); preparations.forget(status.requestId, "game unavailable")
                    }
                    else -> error("Invalid recovery status")
                }
                if (available(message.gameId)) {
                    headAsked.add(message.gameId)
                    // Switch-review #11: the open game's head and revisions carry its engine state; the list's are summaries
                    send(SubscribeGame(message.gameId, order.received(message.gameId) ?: "0",
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
                // Fence old-incarnation reads before they can create a replica or trigger recovery ([RevisionOrder]); a head
                // ahead of the open play at the table waits for the revisions in between
                val place = order.snapshot(message.gameId, message.revision, if (message.engine != null) "an engine head" else "a head",
                    stepped(message.gameId), whole = message.engine != null || ended(message.summary))
                if (place == RevisionOrder.Snapshot.Behind) return
                if (summaries[message.gameId]?.let { ended(it) && !ended(message.summary) } == true) return
                message.ownPodDeckName?.let { ownPodDeckNames[message.gameId] = it }
                chats.installChatHead(message)
                // I-D56: an active play is engine state, played on its engine game
                if (message.engine != null && message.play != null &&
                    message.summary.lifecycle == GameLifecycle.ACTIVE) engineHead(message)
                else if (place == RevisionOrder.Snapshot.Waits) {
                    // A summary head (switch-review #11: every heartbeat) past the revisions the open play received: they are
                    // asked for (the server replays them, then sends the engine head, switch-review #10); TRN-065: one saying
                    // the play ended leaves the table to the revision that ends it
                    streams[message.gameId]?.head = true
                    askHead(message.gameId)
                }
                else {
                    install(message.summary, message.revision, message.preparation, "a head")
                    streams[message.gameId]?.head = true
                    // A play without engine state can't be played on this device (no history is downloaded any more)
                    if (message.play != null && message.summary.lifecycle == GameLifecycle.ACTIVE)
                        cannotOpen(message.gameId, "headWithoutEngine")
                    else endTable(message)
                }
                flush()
            }
            is GameRevision -> {
                require(message.gameId == message.summary.id && message.scope.gameId == message.gameId)
                if (message.gameId !in streams) return
                // Fence BEFORE observePlay can invalidate a newer incarnation's durable cache ([RevisionOrder]).
                // Duplicate accepted deliveries can still correlate exact originals, but cannot
                // advance/replace storage or start recovery against an obsolete through-revision.
                when (order.revision(message, stepped(message.gameId))) {
                    RevisionOrder.Revision.Behind -> {
                        // TRN-065 (owner, prod 0.2.0): the revision that ended the open play, come after its revision was accepted
                        if (message.summary.lifecycle != GameLifecycle.ACTIVE && engineGames[message.gameId] != null && message.gameId !in endHeld)
                            AppLog.info("game", "End of game: revision ${message.resultRevision} came after revision " +
                                "${order.received(message.gameId)} was accepted: the table isn't held")
                        correlateSelections(message)
                        flush(); statusChanged(); return
                    }
                    // The open play's revisions in between are on their way or missing: asked for (replayed, then the head)
                    RevisionOrder.Revision.Held -> { askHead(message.gameId); flush(); statusChanged(); return }
                    RevisionOrder.Revision.InOrder -> {
                        val engine = applyRevision(message)
                        drainHeld()
                        if (engine) { flush(); statusChanged(); return }
                    }
                }
            }
            is GameResult -> result(message)
            is GameReceipt -> Unit // Receipt is not acceptance and never removes an uncertain original.
            is GameAdmissionError -> {
                if (Diagnostics.enabled) message.requestId?.let(DebugLatency::result)
                val fenced = message.gameId?.takeIf { message.code == "streamFenced" }?.let { id -> streams[id]?.let { id to it } }
                if (message.code == "gameplayRulesUpgradeRequired" || message.code == "serverUpdatePending" ||
                    (!welcome && message.code == "invalidInput" && message.requestId == null)) {
                    // A server older than the app's Hello rejects it as unknown input: wait for the server update.
                    incompatibleRules(clientOlder = message.code == "gameplayRulesUpgradeRequired")
                }
                else if (message.code == "unauthorized") { val rejected = token; connectionUnavailable("admission:unauthorized"); rejected?.let(authenticationRequired) }
                else if (fenced != null) streamTakenOver(fenced.first, fenced.second, message.requestId)
                else if (message.retryable || message.code == "streamFenced") connectionUnavailable("admission:${message.code}")
                else {
                    connectionLog.warn("admission", "Refused at admission (epoch $epoch): ${message.code}" +
                        (message.requestId?.let { " for request ${it.take(8)} (${pending[it]?.action?.let(::simpleClassName)})" } ?: "") +
                        (message.gameId?.let { " of game ${it.take(8)}" } ?: ""))
                    val original = message.requestId?.let(pending::get)
                    if (message.code == "accessDenied") message.gameId?.let(::unavailable)
                    message.requestId?.let { id ->
                        chats.failed(id, "Not sent. This request could not be used.")
                        pending.remove(id); preparations.forget(id, "admission ${message.code}"); sent.remove(id)
                    }
                    if (original != null) error = "This game changed or the request could not be used. Refresh and try again."
                }
            }
            is GameUpgradeRequired -> incompatibleRules(clientOlder = true)
            is GamePresenceUpdate -> if (message.gameId in streams && available(message.gameId))
                presence(message.gameId, message.opponent)
            is GameChatReadUpdate -> if (message.gameId in streams && available(message.gameId)) chats.readChanged(message.gameId)
            is GamePong -> if (message.correlationId == resumePing) {
                resumePing = null; resumePending = false
                if (engineGames.isNotEmpty()) AppLog.info("game", "App back: the ping's answer came, what the open socket held is in")
            }
        }
        drainHeld()
        flush()
        recoveryReady()
        statusChanged()
    }
    /** A revision of [message]'s game that follows what was received ([RevisionOrder.Revision.InOrder], or held and let go):
     * applied to its engine game, or to the summary and lobby. True when it went to the engine game. */
    private fun applyRevision(message: GameRevision): Boolean {
        if (summaries[message.gameId]?.let { ended(it) && !ended(message.summary) } == true) return false
        message.chatMessage?.let { chats.installChat(message.gameId, message.resultRevision, listOf(it), snapshot = false) }
        val engine = engineGames[message.gameId]
        if (engine != null) {
            engineRevision(engine, message)
            correlateSelections(message)
            return true
        }
        // I-D56: every play is engine state; one this device hasn't loaded waits for its engine head
        if (message.summary.lifecycle == GameLifecycle.ACTIVE && message.summary.playId != null) awaitEngineHead(message)
        else install(message.summary, message.resultRevision, message.preparation, "a revision")
        correlateSelections(message)
        return false
    }
    private fun install(game: GameSummary, revision: String, preparation: LobbyPreparation?, source: String = "") {
        require(game.revision == revision); GameWire.ordinal(revision)
        // An HTTP summary can lead the actual replica. It must not prevent catching that replica up ([RevisionOrder]: a
        // summary before the received revision is old).
        if (order.received(game.id)?.let { !newerOrEqual(revision, it) } == true) return
        val old = summaries[game.id]
        if (old != null && ended(old) && !ended(game)) return
        if (ended(game)) require(preparation == null)
        if (game.id in unavailableGames && old != null && revision == old.revision &&
            game.lifecycle in listOf(GameLifecycle.LOBBY, GameLifecycle.ACTIVE)) return
        if (old != null && GameWire.ordinal(revision) > GameWire.ordinal(old.revision)) unavailableGames.remove(game.id)
        // TRN-065 (owner, 2026-11-09): logged fully: when an OK on a result becomes owed, and when both stand (the lobby free)
        if (old != null && old.resultNotice != game.resultNotice && GameWire.ordinal(revision) > GameWire.ordinal(old.revision)) {
            val me = account ?: accountId()
            AppLog.info("game", "Result notice at revision $revision${if (source.isEmpty()) "" else " from $source"}: " + (game.resultNotice?.let { notice ->
                "${notice.ending}, OK owed by ${notice.unseen.joinToString { if (it == me) "this player" else "the other player" }}" }
                ?: "both OKs stand, to the lobby${if (endHeld.contains(game.id)) " (from the table)" else ""}"))
        }
        if (game.lifecycle != GameLifecycle.LOBBY) preparations.leftLobby(game.id)
        if (game.lifecycle == GameLifecycle.CANCELLED || game.lifecycle == GameLifecycle.DELETED) clearContext(game.id)
        if (preparation != null && game.lifecycle == GameLifecycle.LOBBY) preparations.accept(game, revision, preparation)
        if ((preparation != null && game.lifecycle == GameLifecycle.LOBBY) || ended(game)) received(game.id, revision)
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
                // Problem report JVPJ8Y: what was refused and why, in the engine's words when the server gave them, and for a
                // submission what it held (each input, marked when assumed)
                // (a stale race and a seen report with nothing new are routine: the take-back line tells the first)
                if (result.reason != "stale" && result.reason != "noChange") AppLog.info("engine", "Server refused ${original.action::class.simpleName}: ${result.reason}" +
                    (result.detail?.let { " ($it)" } ?: "") + ((original.action as? GameAction.Submit)?.submission?.let { s ->
                        " ${s.id} on ${s.parentId}, acts " + s.acts.joinToString(prefix = "[", postfix = "]") { act ->
                            when (act) {
                                is org.asyncttwiz.protocol.game.Act.Perform -> (if (act.assumed) "assumed " else "") + act.input
                                is org.asyncttwiz.protocol.game.Act.NewLine -> "new line ${act.number}"
                            }
                        }.take(1200)
                    } ?: ""))
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
        val revision = order.received(gameId) ?: return
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
            val generation = stream.generation
            if (generation == null || !stream.head) return@drainCaptured null
            val command = GameCommand(randomUuid(), GameScope(gameId, roundId),
                next(), stream.id, generation)
            pending[command.requestId] = command
            command
        }
        for ((id, command) in pending) {
            val stream = streams[command.scope.gameId] ?: run { Faults.report("GameSession", "pending command without a stream", mapOf("game" to command.scope.gameId)); continue }
            val generation = stream.generation
            if (id !in sent && available(command.scope.gameId) && generation != null && stream.head) {
                if (!send(command.copy(generation = generation))) return
                sent.add(id)
            }
        }
    }
    fun heartbeat(presence: GamePresence): Boolean = connected && welcome && send(GamePing(randomUuid(), presence))
    private fun send(message: GameClientMessage): Boolean {
        if (!connection.send(message)) {
            connectionLog.warn("sendFailed", "Couldn't send ${simpleClassName(message)} (epoch $epoch): the socket didn't take it")
            connectionUnavailable("socketSendFailed:${simpleClassName(message)}"); return false
        }
        return true
    }
    /**
     * Problem report VSVLSK: the server fenced [gameId]'s stream on this connection: another tab of this browser (one
     * installation to the server) or another process opened it since. The stream is opened again here, on the same socket,
     * and what waits goes again under its own request ids once it is ready ([openStreams]); a fence of a command sent before
     * an open or resume still on its way is left to that one's answer. Before, the whole connection was dropped and every
     * new one asked to resume the stream the other tab held, was fenced again and started over, silently, forever.
     */
    private fun streamTakenOver(gameId: String, stream: SessionStream, requestId: String?) {
        val what = requestId?.let { "request ${it.take(8)} (${pending[it]?.action?.let(::simpleClassName)})" }
            ?: if (stream.opened) "its resume" else "its open"
        if (requestId != null && stream.awaitingReady) {
            connectionLog.info("fenced", "Game ${gameId.take(8)} stream fenced on $what (epoch $epoch), sent before the open or resume on its way")
            return
        }
        connectionLog.warn("fenced", "Game ${gameId.take(8)} stream fenced on $what (epoch $epoch): another tab or process of " +
            "this installation opened it; opening it again here, ${pending.values.count { it.scope.gameId == gameId }} commands to send again")
        stream.opened = false; stream.opening = false; stream.awaitingReady = false; stream.generation = null
        headAsked.remove(gameId)
        reconnectTrace.start(gameId, "streamFenced")
        openStreams()
    }
    private fun connectionUnavailable(cause: String, logged: Boolean = false) {
        if (!logged && connected) connectionLog.warn("dropped", "Connection dropped (epoch $epoch): $cause; ${pending.size} commands waiting")
        connectReason = "after $cause"
        streams.keys.forEach { reconnectTrace.start(it, if (backgrounded) "lifecycle:$cause" else cause) }
        disconnect(); statusChanged()
    }
    private fun incompatibleRules(clientOlder: Boolean) {
        connectionLog.warn("incompatible", "Connection ends (epoch $epoch): " +
            if (clientOlder) "the app is older than the server" else "the server is older than the app")
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
        ownPodDeckNames.clear(); chats.clear(); order.clear(); released.clear(); summaries.clear(); unavailableGames.clear(); streams.clear(); connectionDemand.clear(); uncertainGames.clear(); reconnectTrace.clear(); lastCredential = null; account = null; compatibilityError = null; compatibilityRetryAt = Long.MAX_VALUE; error = null; statusChanged() }
    override fun close() { closed = true; clear(); connection.close() }
    companion object {
        /** The table's line for a game this device can't open (SYS-052): never a reconnect loop. */
        const val CANNOT_OPEN = "This game can't be opened on this device. Send a problem report from the menu."
        /** The longest wait between background socket attempts after failures ([poll]). */
        const val MAX_RECONNECT_DELAY_MS = 10_000L
        /** The most revisions one replay of a subscription carries (the server's revisions page). */
        const val REPLAY_REVISIONS = 100
        /** How many heads in a row past the revisions received an open game asks past before it loads from one ([asksPast]). */
        const val MAX_HEADS_AHEAD = 3
        fun newerOrEqual(a: String, b: String) = GameWire.ordinal(a) >= GameWire.ordinal(b)
        fun ended(game: GameSummary) = game.lifecycle !in listOf(GameLifecycle.LOBBY, GameLifecycle.ACTIVE)
    }
}
