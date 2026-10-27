package org.asyncttwiz.server.game

import jakarta.websocket.*
import java.util.ArrayDeque
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import org.asyncttwiz.protocol.*
import org.asyncttwiz.server.ServiceRuntime
import org.asyncttwiz.server.ServiceRuntime.UnavailabilityBoundary
import org.asyncttwiz.server.accounts.AccountService
import org.asyncttwiz.server.database.OwnershipFailure

/** Running admission, execution and publication workers, all included in readiness/recovery. */
class GameHub @JvmOverloads constructor(private val runtime: ServiceRuntime,
    private val workers: ScheduledExecutorService = Executors.newScheduledThreadPool(5) {
        Thread(it, "game-worker").apply { isDaemon = true }
    }) : AutoCloseable {
    val games = GameService(runtime)
    val dispatcher = GameDispatcher()
    private val accounts = AccountService(runtime)
    private val handlerTiming = GameHandlerTiming()
    private val sockets = ConcurrentHashMap.newKeySet<GameSocket>()
    private val tasks = CopyOnWriteArrayList<ScheduledFuture<*>>()
    fun start() {
        repeat(4) {
            tasks.add(workers.scheduleWithFixedDelay({ guarded(UnavailabilityBoundary.GAME_EXECUTION) { repeat(32) { if (!games.executeOne()) return@guarded } } }, 0, 100, TimeUnit.MILLISECONDS))
        }
        tasks.add(workers.scheduleWithFixedDelay({ guarded(UnavailabilityBoundary.GAME_PUBLICATION) {
            for (publication in games.publications()) {
                // Only committed rows are visible; delivery is retryable/duplicate-safe and outside SQL.
                for (socket in sockets) socket.publish(publication)
                games.published(publication.id)
            }
        } }, 0, 100, TimeUnit.MILLISECONDS))
        tasks.add(workers.scheduleWithFixedDelay({ guarded(UnavailabilityBoundary.GAME_PUBLICATION) {
            sockets.forEach { it.heartbeat() }
        } }, 30, 30, TimeUnit.SECONDS))
        tasks.add(workers.scheduleWithFixedDelay({ games.evictExpiredBaselines() }, 1, 1, TimeUnit.SECONDS))
    }
    private fun guarded(boundary: UnavailabilityBoundary, action: () -> Unit) {
        try { if (runtime.ready()) action() }
        catch (failure: OwnershipFailure) {
            // Ownership recovery still stops all workers until validation succeeds.
            runtime.recordUnavailability(boundary, failure)
        }
        catch (failure: Throwable) {
            runtime.invalidateWorkerRecovery()
            runtime.recordUnavailability(boundary, failure)
        }
    }
    fun available() = !workers.isShutdown && tasks.none { it.isDone } && dispatcher.available()
    fun validatePending() = games.validatePending()
    fun endpoint(token: String, accountId: String): Endpoint = GameSocket(token, accountId)
    override fun close() {
        workers.shutdownNow()
        // toArray is weakly consistent; toList() can throw when a socket leaves between size() and next().
        sockets.toTypedArray().forEach { it.stop() }
        dispatcher.close()
        try { workers.awaitTermination(5, TimeUnit.SECONDS) } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
    }

    private inner class GameSocket(private val token: String, val accountId: String) : Endpoint() {
        private val connectionId = UUID.randomUUID()
        private lateinit var session: Session
        private var hello: GameHello? = null
        private val subscriptions = ConcurrentHashMap.newKeySet<String>()
        private val streams = ConcurrentHashMap.newKeySet<String>()
        private val outgoing = ArrayDeque<String>()
        private var bytes = 0
        private var sending = false
        private var stopped = false
        @Volatile private var lastTraffic = System.nanoTime()
        override fun onOpen(session: Session, config: EndpointConfig) {
            this.session = session
            session.maxTextMessageBufferSize = 1048576
            session.maxIdleTimeout = 90000
            session.asyncRemote.sendTimeout = 5000
            sockets.add(this)
            session.addMessageHandler(String::class.java, MessageHandler.Whole<String> { receive(it) })
        }
        private fun principal(): AccountService.Principal {
            if (!runtime.ready()) throw GameProtocolFailure("storageUnavailable", true)
            return accounts.authenticate(token) ?: throw GameProtocolFailure("unauthorized")
        }
        private fun receive(text: String) {
            lastTraffic = System.nanoTime()
            val message = try { GameWire.decodeClient(text) } catch (_: Exception) {
                send(GameAdmissionError("invalidInput", false)); return
            }
            if (message is GameHello) {
                if (hello != null) { send(GameAdmissionError("invalidInput", false)); return }
                if (message.protocolMajor != 1 || message.minMinor > 0 || message.maxMinor < 0 ||
                    1 !in message.engineVersions || 1 !in message.readFormats) {
                    send(GameUpgradeRequired()); return
                }
                if (!DeckValidation.uuid(message.installationId) || !DeckValidation.uuid(message.processId)) {
                    send(GameAdmissionError("invalidInput", false)); return
                }
                if (message.gameplayRulesVersion < CURRENT_GAMEPLAY_RULES_VERSION) {
                    send(GameAdmissionError("gameplayRulesUpgradeRequired", false)); return
                }
                hello = message; send(GameWelcome(gameplayRulesVersion = CURRENT_GAMEPLAY_RULES_VERSION)); return
            }
            val negotiated = hello ?: run { send(GameAdmissionError("upgradeRequired", false)); return }
            if (message is GamePing) { send(GamePong(message.correlationId)); return }
            if (negotiated.gameplayRulesVersion != CURRENT_GAMEPLAY_RULES_VERSION) {
                send(GameAdmissionError("serverUpdatePending", false)); return
            }
            val gameId = when (message) {
                is GameCommand -> message.scope.gameId
                is OpenGameStream -> message.gameId
                is ResumeGameStream -> message.gameId
                is SubscribeGame -> message.gameId
                else -> return
            }
            val requestId = (message as? GameCommand)?.requestId
            if (!DeckValidation.uuid(gameId)) { send(GameAdmissionError("invalidInput", false, requestId)); return }
            // This call enqueues before *any* asynchronous auth/admission DB work.
            val timed = handlerTiming.queued(gameId, message::class.simpleName ?: "Unknown")
            dispatcher.submit(gameId, text.encodeToByteArray().size) {
                timed {
                val principal = principal()
                when (message) {
                    is GameCommand -> send(games.admit(principal, negotiated, connectionId, message))
                    is OpenGameStream -> {
                        send(games.open(principal, negotiated, connectionId, gameId, message.streamId)); streams.add(gameId)
                    }
                    is ResumeGameStream -> {
                        send(games.open(principal, negotiated, connectionId, gameId, message.streamId, message.requestIds)); streams.add(gameId)
                    }
                    is SubscribeGame -> {
                        if (1 !in message.readFormats) throw GameProtocolFailure("upgradeRequired")
                        games.subscriptionHead(principal, gameId) // Authorize before enabling any publication delivery.
                        // Register before snapshot selection; duplicates/races are reconciled by revision IDs.
                        subscriptions.add(gameId)
                        try {
                            // A fresh subscription to a tombstone gets lifecycle only, no old board/history.
                            val head = games.subscriptionHead(principal, gameId)
                            if (head.summary.lifecycle in listOf(GameLifecycle.LOBBY, GameLifecycle.ACTIVE)) {
                                try {
                                    games.revisions(principal, gameId, message.revision).revisions.forEach {
                                        send(games.publicationState(principal, it))
                                    }
                                } catch (failure: GameProtocolFailure) {
                                    if (failure.code != "accessDenied") throw failure
                                }
                            }
                            send(games.subscriptionHead(principal, gameId))
                        } catch (failure: Exception) { subscriptions.remove(gameId); throw failure }
                    }
                    else -> Unit
                }
                }
            }.whenComplete { _, failure ->
                if (failure != null) {
                    val cause = (failure as? java.util.concurrent.CompletionException)?.cause ?: failure
                    val error = cause as? GameProtocolFailure
                    send(GameAdmissionError(error?.code ?: "storageUnavailable", error?.retryable ?: true, requestId, gameId))
                    if (error?.code == "unauthorized") stop()
                }
            }
        }
        fun publish(publication: GameService.Publication) {
            if (publication.gameId !in subscriptions && publication.gameId !in streams) return
            try {
                val principal = principal() // A revoked socket is not a permanent authorization subscription.
                if (publication.revision != null && publication.gameId in subscriptions) {
                    val revision = GameWire.json.decodeFromString<GameServerMessage>(publication.revision) as GameRevision
                    send(games.publicationState(principal, revision))
                }
                if (publication.accountId == accountId) sendText(publication.result)
            } catch (failure: GameProtocolFailure) { if (failure.code == "unauthorized") stop() }
        }
        fun heartbeat() {
            if (System.nanoTime() - lastTraffic > TimeUnit.SECONDS.toNanos(90)) { stop(); return }
            for (gameId in subscriptions) dispatcher.submit(gameId) {
                send(games.subscriptionHead(principal(), gameId))
            }.whenComplete { _, failure -> if (failure != null) stop() }
            if (subscriptions.isEmpty()) send(GamePong("heartbeat"))
        }
        private fun send(message: GameServerMessage) = sendText(GameWire.encode(message))
        @Synchronized private fun sendText(text: String) {
            if (stopped) return
            val size = text.encodeToByteArray().size
            if (outgoing.size >= 256 || bytes + size > 4 * 1024 * 1024) { stop(); return }
            outgoing.addLast(text); bytes += size
            if (!sending) { sending = true; sendNext() }
        }
        @Synchronized private fun sendNext() {
            val text = outgoing.peekFirst() ?: run { sending = false; return }
            if (stopped) return
            session.asyncRemote.sendText(text) { result ->
                synchronized(this) {
                    if (!result.isOK) { stop(); return@sendText }
                    if (stopped) return@sendText
                    outgoing.removeFirst(); bytes -= text.encodeToByteArray().size
                    sendNext()
                }
            }
        }
        @Synchronized fun stop() {
            if (stopped) return
            stopped = true; sockets.remove(this); outgoing.clear(); bytes = 0
            try { if (::session.isInitialized && session.isOpen) session.close(CloseReason(CloseReason.CloseCodes.GOING_AWAY, "Reconnect")) }
            catch (_: Exception) { /* Delivery never affects committed game state. */ }
        }
        override fun onClose(session: Session, reason: CloseReason) {
            org.slf4j.LoggerFactory.getLogger(GameHub::class.java).info(
                "Game socket closed connection={} code={} serverStopped={}", connectionId, reason.closeCode.code, stopped)
            stop()
        }
        override fun onError(session: Session?, failure: Throwable?) {
            org.slf4j.LoggerFactory.getLogger(GameHub::class.java).warn(
                "Game socket error connection={} type={}", connectionId, failure?.javaClass?.simpleName)
            stop()
        }
    }
}
