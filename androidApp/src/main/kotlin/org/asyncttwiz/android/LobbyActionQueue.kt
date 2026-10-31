package org.asyncttwiz.android

/** LobbyController's waiting lobby taps: a tap made while the game connection (re)opens, or while an
 * earlier request of that game is in flight, runs once the game can send. Main thread. */
internal class LobbyActionQueue(private val scope: LobbyScope, private val session: GameSession?) {
    private var state: LobbyState
        get() = scope.state
        set(value) { scope.state = value }
    private val closed get() = scope.closed
    /** A lobby tap made while the game connection (re)opens, or while an earlier request is in flight. */
    private class QueuedLobbyAction(val gameId: String, val kind: String, val run: () -> Unit)
    private val queuedActions = mutableListOf<QueuedLobbyAction>()
    private var runningQueued = false
    private fun canSendNow(gameId: String) = gameId in state.connectedGames && gameId !in state.pendingGames
    /** Runs [run] now when the game connection is ready, otherwise once it is. A newer tap of the same
     * control ([kind]) replaces an older waiting one, so nothing is submitted twice. */
    fun whenReady(gameId: String, kind: String, run: () -> Unit) {
        if (closed) return
        if (canSendNow(gameId) && queuedActions.none { it.gameId == gameId }) { run(); return }
        queuedActions.removeAll { it.gameId == gameId && it.kind == kind }
        queuedActions.add(QueuedLobbyAction(gameId, kind, run))
        queuedChanged()
        session?.tick()
    }
    fun dropQueued(kind: String? = null) {
        if (queuedActions.removeAll { kind == null || it.kind == kind }) queuedChanged()
    }
    private fun queuedChanged() {
        val games = queuedActions.map { it.gameId }.toSet()
        if (games != state.queuedGames) state = state.copy(queuedGames = games)
    }
    fun runQueued() {
        if (runningQueued) return
        runningQueued = true
        try {
            while (!closed) {
                val next = queuedActions.firstOrNull { canSendNow(it.gameId) } ?: break
                queuedActions.remove(next); queuedChanged(); next.run()
            }
        } finally { runningQueued = false }
    }
    fun clear() { queuedActions.clear() }
    /** Some waiting tap's game can send now. */
    fun anyReady() = queuedActions.any { canSendNow(it.gameId) }
}
