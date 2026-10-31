package org.asyncttwiz.android

import androidx.compose.runtime.*
import org.asyncttwiz.protocol.*

/** LobbyController's own deck preparation in a lobby game: start settings and readiness, the
 * preparation view's card transfers and pod basic lands, and their personal undo. Main thread. */
internal class LobbyPreparationActions(private val scope: LobbyScope, private val accountId: () -> String?,
    private val session: GameSession?, private val queue: LobbyActionQueue, private val inspection: CardInspectionController) {
    private var state: LobbyState
        get() = scope.state
        set(value) { scope.state = value }
    private val closed get() = scope.closed
    fun starter(choice: StarterChoice) = readiness(GameAction.SetStarter(choice))
    fun alternateStarter(enabled: Boolean) = readiness(GameAction.SetAlternateStarter(enabled))
    /** Show (or hide again) the own deck's name and cover to the opponent in this game. */
    fun showDeck(shown: Boolean) = readiness(GameAction.ShowDeck(shown))
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
    private fun readiness(action: GameAction) {
        val game = state.detail ?: return
        val actor = accountId() ?: return
        if (closed) return
        if (game.lifecycle != GameLifecycle.LOBBY) { session?.readiness(game, actor, action); return }
        // Ready/Withdraw toggle one control; the latest waiting tap of each control wins.
        queue.whenReady(game.id, when (action) { is GameAction.SetStarter -> "starter"; is GameAction.SetAlternateStarter -> "alternate"; is GameAction.ShowDeck -> "showDeck"; else -> "ready" }) {
            state.detail?.takeIf { it.id == game.id }?.let { current -> session?.readiness(current, actor, action) }
        }
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
}
