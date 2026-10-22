package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import java.util.UUID

/** Main-thread, process-memory queue. Results settle intent; only revisioned reads change accepted state. */
class GameSession(private val connection: GameConnection, private val credential: () -> String?,
    private val post: (() -> Unit) -> Unit, private val authenticationRequired: (String) -> Unit,
    private val changed: (GameSummary) -> Unit, private val settled: (GameCommand, GameResult) -> Unit,
    private val statusChanged: () -> Unit, private val installationId: String = UUID.randomUUID().toString()) : AutoCloseable {
    private data class Stream(val id: String = UUID.randomUUID().toString(), var generation: String? = null,
        var head: Boolean = false, var opening: Boolean = false, var opened: Boolean = false)
    private val processId = UUID.randomUUID().toString()
    private val streams = linkedMapOf<String, Stream>()
    private val summaries = mutableMapOf<String, GameSummary>()
    private val pending = linkedMapOf<String, GameCommand>()
    private data class PreparationReplica(val revision: String, val state: LobbyPreparation)
    private data class Selection(val command: GameCommand, val actor: String, val source: SavedDeck,
        val definitions: List<SubmittedDefinition>, var commitRevision: String? = null)
    private val preparations = mutableMapOf<String, PreparationReplica>()
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
    private var token: String? = null
    private var epoch = 0L
    private var connected = false
    private var welcome = false
    private var closed = false
    var error: String? = null
        private set
    val ready get() = connected && welcome
    fun ready(gameId: String) = ready && streams[gameId]?.let { it.generation != null && it.head } == true
    fun pending(gameId: String) = pending.values.any { it.scope.gameId == gameId } || selections.values.any { it.command.scope.gameId == gameId } ||
        transfers.values.any { it.command.scope.gameId == gameId } || captured.any { it.intent.gameId == gameId }
    fun queuedTransfers(gameId: String) = captured.filter { it.intent.gameId == gameId }.sumOf { it.remaining().count }
    fun acceptedPreparation(gameId: String) = preparations[gameId]?.state
    fun preparation(game: GameSummary): LobbyPreparation? {
        val current = summary(game)
        var display = preparations[game.id]?.state?.takeIf { it.roundId == current.preparationRound } ?: return null
        for (selection in selections.values.filter { it.command.scope.gameId == game.id }) {
            display = PreparationRules.select(current, display, selection.actor, selection.command.requestId,
                selection.command.scope, selection.command.action as GameAction.SelectDeck, selection.source,
                selection.definitions).state ?: display
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
    fun summary(game: GameSummary): GameSummary = summaries[game.id]?.takeIf { newerOrEqual(it.revision, game.revision) } ?: game
    fun watch(games: List<GameSummary>) {
        for (game in games) {
            val old = summaries[game.id]
            if (old == null || newerOrEqual(game.revision, old.revision)) summaries[game.id] = game
            streams.getOrPut(game.id) { Stream() }
        }
        tick()
    }
    fun tick() {
        if (closed) return
        val current = credential()
        if (current != token) disconnect()
        if (current == null || streams.isEmpty()) return
        if (!connected) {
            token = current; connected = true; val generation = ++epoch
            error = "Connecting…"; statusChanged()
            connection.connect(current, object : GameConnection.Listener {
                private fun deliver(action: () -> Unit) = post {
                    if (!closed && epoch == generation && credential() == current) action()
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
        } else if (welcome) openStreams()
    }
    fun submit(game: GameSummary, action: GameAction): Boolean {
        if (action is GameAction.SelectDeck || action is GameAction.TransferPreparedCard) return false // Preparation needs captured data for prediction.
        return enqueue(game, action) != null
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
        if (!ready(game.id) || pending(game.id) || credential() != token || pending.size >= 256) return null
        val stream = streams.getValue(game.id)
        return GameCommand(UUID.randomUUID().toString(), GameScope(game.id, game.preparationRound,
            if (action is GameAction.SelectDeck) null else game.invitationId),
            action, stream.id, stream.generation!!)
    }
    private fun enqueue(game: GameSummary, action: GameAction): GameCommand? {
        val command = command(game, action) ?: return null
        pending[command.requestId] = command
        error = null; flush(); statusChanged(); return command
    }
    private fun openStreams() {
        for ((id, stream) in streams) if (!stream.opening) {
            stream.opening = true
            val ids = pending.values.filter { it.scope.gameId == id }.map { it.requestId }
            if (!send(if (stream.opened) ResumeGameStream(id, stream.id, ids) else OpenGameStream(id, stream.id))) return
            if (!send(SubscribeGame(id, summaries[id]?.revision ?: "0"))) return
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
                require(stream.id == message.streamId); GameWire.ordinal(message.generation)
                stream.generation = message.generation; stream.opened = true
                val originals = pending.values.filter { it.scope.gameId == message.gameId }.toList()
                require(message.statuses.map { it.requestId }.toSet() == originals.map { it.requestId }.toSet())
                for (status in message.statuses) when (status.status) {
                    "final" -> result(requireNotNull(status.result))
                    "pending" -> sent.add(status.requestId)
                    "notAdmitted" -> sent.remove(status.requestId)
                    else -> error("Invalid recovery status")
                }
                flush()
            }
            is GameHead -> { install(message.summary, message.revision, message.preparation); streams[message.gameId]?.head = true; flush() }
            is GameRevision -> {
                install(message.summary, message.resultRevision, message.preparation)
                // A correlated accepted revision is enough even when its result has not arrived yet.
                transfers.remove(message.requestId)
            }
            is GameResult -> result(message)
            is GameReceipt -> Unit // Receipt is not acceptance and never removes an uncertain original.
            is GameAdmissionError -> {
                if (message.code == "unauthorized") { val rejected = token; unavailable(); rejected?.let(authenticationRequired) }
                else if (message.retryable || message.code == "streamFenced") unavailable()
                else {
                    message.requestId?.let { pending.remove(it); selections.remove(it); transfers.remove(it); sent.remove(it) }
                    error = "This game changed or the request could not be used. Refresh and try again."
                }
            }
            is GameUpgradeRequired -> { unavailable(); error = "Update the app to connect to this game service." }
            is GamePong -> Unit
        }
        flush()
        statusChanged()
    }
    private fun install(game: GameSummary, revision: String, preparation: LobbyPreparation?) {
        require(game.revision == revision); GameWire.ordinal(revision)
        if (preparation != null) {
            require(preparation.gameId == game.id && preparation.roundId == game.preparationRound)
            val old = preparations[game.id]
            if (old == null || newerOrEqual(revision, old.revision)) {
                preparations[game.id] = PreparationReplica(revision, preparation)
                val applied = preparation.players.mapNotNull { it.deck?.selectionRequestId }.toSet()
                selections.entries.removeAll { it.key in applied }
            }
        }
        retireSelections(game.id)
        val old = summaries[game.id]
        if (old == null || newerOrEqual(revision, old.revision)) { summaries[game.id] = game; changed(game) }
    }
    private fun result(result: GameResult) {
        val original = pending[result.requestId] ?: return
        require(result.scope == original.scope)
        pending.remove(result.requestId); sent.remove(result.requestId)
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
        error = if (result.outcome == "accepted") null else when (result.reason) {
            "sourceChanged" -> "This saved deck changed. Choose it again to use the current version."
            "sourceUnavailable" -> "This saved deck is no longer available. Choose another deck."
            else -> "This game has changed. Refresh and try again."
        }
        settled(original, result)
    }
    private fun retireSelections(gameId: String) {
        val revision = preparations[gameId]?.revision ?: return
        selections.entries.removeAll { (_, selection) -> selection.command.scope.gameId == gameId &&
            selection.commitRevision?.let { newerOrEqual(revision, it) } == true }
        transfers.entries.removeAll { (_, transfer) -> transfer.command.scope.gameId == gameId &&
            transfer.commitRevision?.let { newerOrEqual(revision, it) } == true }
    }
    private fun flush() {
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
            if (id !in sent && stream.generation != null && stream.head) {
                if (!send(command.copy(generation = stream.generation!!))) return
                sent.add(id)
            }
        }
    }
    private fun send(message: GameClientMessage): Boolean {
        if (!connection.send(message)) { unavailable(); return false }; return true
    }
    private fun unavailable() { disconnect(); error = "Disconnected. Requests are kept; reconnect to check their outcome."; statusChanged() }
    private fun disconnect() {
        epoch++; connected = false; welcome = false; token = null; connection.disconnect(); sent.clear()
        streams.values.forEach { it.generation = null; it.head = false; it.opening = false }
    }
    fun clear() { disconnect(); pending.clear(); selections.clear(); transfers.clear(); captured.clear(); preparations.clear(); summaries.clear(); streams.clear(); error = null; statusChanged() }
    override fun close() { closed = true; clear(); connection.close() }
    companion object {
        fun newerOrEqual(a: String, b: String) = GameWire.ordinal(a) >= GameWire.ordinal(b)
    }
}
