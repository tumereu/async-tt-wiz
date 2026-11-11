package org.asyncttwiz.android

import org.asyncttwiz.protocol.*

/** LobbyController's ways to end a game from the Games list: the game menu, deleting a game and
 * cancelling a game's setup, each captured against the game it was offered for. Main thread. */
internal class LobbyGameRemoval(private val scope: LobbyScope, private val accountId: () -> String?,
    private val session: GameSession?, private val setupCancellation: SetupCancellation) {
    private var state: LobbyState
        get() = scope.state
        set(value) { scope.state = value }
    private val closed get() = scope.closed
    private val latest get() = scope.latest
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
    private fun sessionAvailable(id: String) = scope.sessionAvailable(id)
}
