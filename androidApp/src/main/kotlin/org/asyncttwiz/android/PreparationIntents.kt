package org.asyncttwiz.android

import org.asyncttwiz.protocol.*

/** GameSession's lobby preparation: the accepted preparation per game plus this device's own
 * unsettled intents (deck selection, start settings/readiness, captured and sent card transfers),
 * overlaid on it for display until an accepted revision covers them. Main thread. */
internal class PreparationIntents {
    private data class PreparationReplica(val revision: String, val state: LobbyPreparation)
    private data class Selection(val command: GameCommand, val actor: String, val source: SavedDeck,
        val definitions: List<SubmittedDefinition>, var commitRevision: String? = null)
    private val preparations = mutableMapOf<String, PreparationReplica>()
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

    fun pending(gameId: String) = selections.values.any { it.command.scope.gameId == gameId } ||
        transfers.values.any { it.command.scope.gameId == gameId } || readiness.values.any { it.command.scope.gameId == gameId } || captured.any { it.intent.gameId == gameId }
    fun queuedTransfers(gameId: String) = captured.filter { it.intent.gameId == gameId }.sumOf { it.remaining().count }
    fun acceptedPreparation(gameId: String) = preparations[gameId]?.state
    /** [current] is the game's newest known summary. */
    fun preparation(current: GameSummary, gameId: String): LobbyPreparation? {
        if (current.lifecycle != GameLifecycle.LOBBY) return null
        var display = preparations[gameId]?.state?.takeIf { it.roundId == current.preparationRound } ?: return null
        for (selection in selections.values.filter { it.command.scope.gameId == gameId }) {
            display = PreparationRules.select(current, display, selection.actor, selection.command.requestId,
                selection.command.scope, selection.command.action as GameAction.SelectDeck, selection.source,
                selection.definitions).state ?: display
        }
        // Ready is submitted only after earlier predictions settle. Later per-card edits follow it.
        for (intent in readiness.values.filter { it.command.scope.gameId == gameId }) {
            display = preparationIntent(current, display, intent.actor, intent.command.scope, intent.command.action).state ?: display
        }
        for (transfer in transfers.values.filter { it.command.scope.gameId == gameId && !it.withdrawn }) {
            val next = PreparationRules.transfer(current, display, transfer.actor, transfer.command.scope,
                transfer.command.action as GameAction.TransferPreparedCard).state
            if (next == null) transfer.withdrawn = true else display = next
        }
        if (current.lifecycle == GameLifecycle.LOBBY) for (capture in captured.filter { it.intent.gameId == gameId }) {
            display = PreparationRules.previewTransfer(display, capture.actor, capture.remaining())
        }
        return display
    }
    fun preparationIntent(game: GameSummary, base: LobbyPreparation, actor: String, scope: GameScope, action: GameAction) =
        if (action is GameAction.SetPodGameBasics) PodGamePreparation.setBasics(game, base, actor, scope, action)
        else StartRules.change(game, base, actor, scope, action)

    fun readied(command: GameCommand, actor: String) { readiness[command.requestId] = Readiness(command, actor) }
    fun selected(command: GameCommand, actor: String, source: SavedDeck, definitions: List<SubmittedDefinition>) {
        selections[command.requestId] = Selection(command, actor, source, definitions)
    }
    fun capture(intent: PreparedTransfer, actor: String) { captured.add(Captured(intent, actor)) }
    fun withdrawTransfer(requestId: String) { transfers[requestId]?.withdrawn = true }

    /** Turns captured rows into individual transfer originals while [capacity] allows. [command]
     * builds and queues the next card's command, or returns null while its stream cannot send. */
    fun drainCaptured(capacity: () -> Boolean,
        command: (gameId: String, roundId: String, next: () -> GameAction.TransferPreparedCard) -> GameCommand?) {
        while (capacity() && transfers.size + selections.size < 256 && captured.isNotEmpty()) {
            val next = captured.first()
            val queued = command(next.intent.gameId, next.intent.roundId, next::next) ?: break
            transfers[queued.requestId] = Transfer(queued, next.actor)
            if (next.finished) captured.removeFirst()
        }
    }

    /** The game left the lobby: no accepted preparation or captured rows remain. */
    fun leftLobby(gameId: String) {
        preparations.remove(gameId)
        captured.removeAll { it.intent.gameId == gameId }
    }
    fun accept(game: GameSummary, revision: String, preparation: LobbyPreparation) {
        require(preparation.gameId == game.id && preparation.roundId == game.preparationRound)
        val old = preparations[game.id]
        if (old == null || GameSession.newerOrEqual(revision, old.revision)) {
            preparations[game.id] = PreparationReplica(revision, preparation)
            val applied = preparation.players.mapNotNull { it.deck?.selectionRequestId }.toSet()
            selections.entries.removeAll { it.key in applied }
        }
    }
    /** A final result settles the intent: accepted ones wait for their commit revision, others are dropped. */
    fun settle(result: GameResult, retire: () -> Unit) {
        selections[result.requestId]?.let { selection ->
            if (result.outcome == "accepted") selection.commitRevision = requireNotNull(result.commitRevision)
            else selections.remove(result.requestId)
            retire()
        }
        transfers[result.requestId]?.let { transfer ->
            if (result.outcome == "accepted") transfer.commitRevision = requireNotNull(result.commitRevision)
            else transfers.remove(result.requestId)
            retire()
        }
        readiness[result.requestId]?.let { intent ->
            if (result.outcome == "accepted") intent.commitRevision = requireNotNull(result.commitRevision)
            else readiness.remove(result.requestId)
            retire()
        }
    }
    fun correlate(requestId: String, revision: String) {
        selections[requestId]?.commitRevision = revision
        transfers[requestId]?.commitRevision = revision
        readiness[requestId]?.commitRevision = revision
    }
    /** Drops intents the accepted [revision] already contains. */
    fun retire(gameId: String, revision: String) {
        readiness.entries.removeAll { (_, intent) -> intent.command.scope.gameId == gameId &&
            intent.commitRevision?.let { GameSession.newerOrEqual(revision, it) } == true }
        selections.entries.removeAll { (_, selection) -> selection.command.scope.gameId == gameId &&
            selection.commitRevision?.let { GameSession.newerOrEqual(revision, it) } == true }
        transfers.entries.removeAll { (_, transfer) -> transfer.command.scope.gameId == gameId &&
            transfer.commitRevision?.let { GameSession.newerOrEqual(revision, it) } == true }
    }
    fun forget(requestId: String) { selections.remove(requestId); transfers.remove(requestId); readiness.remove(requestId) }
    fun remove(gameId: String) {
        preparations.remove(gameId)
        selections.entries.removeAll { it.value.command.scope.gameId == gameId }
        transfers.entries.removeAll { it.value.command.scope.gameId == gameId }
        readiness.entries.removeAll { it.value.command.scope.gameId == gameId }
        captured.removeAll { it.intent.gameId == gameId }
    }
    fun clear() { selections.clear(); transfers.clear(); captured.clear(); readiness.clear(); preparations.clear() }
}
