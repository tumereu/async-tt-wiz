package org.asyncttwiz.protocol

import kotlinx.serialization.Serializable

/** Fixed manual positions. Main phases have one position, not an invented additional step. */
@Serializable enum class TurnPosition(val phase: String, val step: String, val label: String, val glyph: String) {
    UNTAP("BEGINNING", "UNTAP", "Untap", "↶"),
    UPKEEP("BEGINNING", "UPKEEP", "Upkeep", "⌛"),
    DRAW("BEGINNING", "DRAW", "Draw", "↓"),
    PRECOMBAT_MAIN("PRECOMBAT_MAIN", "MAIN", "Precombat main", "①"),
    BEGIN_COMBAT("COMBAT", "BEGIN_COMBAT", "Beginning of combat", "⚑"),
    DECLARE_ATTACKERS("COMBAT", "DECLARE_ATTACKERS", "Declare attackers", "⚔"),
    DECLARE_BLOCKERS("COMBAT", "DECLARE_BLOCKERS", "Declare blockers", "⛨"),
    COMBAT_DAMAGE("COMBAT", "COMBAT_DAMAGE", "Combat damage", "♨"),
    END_COMBAT("COMBAT", "END_COMBAT", "End of combat", "⚐"),
    POSTCOMBAT_MAIN("POSTCOMBAT_MAIN", "MAIN", "Postcombat main", "②"),
    END_STEP("ENDING", "END_STEP", "End step", "☾"),
    CLEANUP("ENDING", "CLEANUP", "Cleanup", "◇");

    companion object {
        fun of(turn: TurnState) = entries.singleOrNull { it.phase == turn.phase && it.step == turn.step }
    }
}

@Serializable enum class CoinFace { HEADS, TAILS }

/** Extensions of the sole GameplayRules processor. No entropy, queue or rules engine lives here. */
object TableActions {
    fun numeric(value: String) = DeckValidation.text(value) && value.matches(Regex("[+-]?[0-9]+"))

    /** Unicode 15.1 White_Space, not platform-dependent trim or expression parsing. */
    fun trimText(value: String) = value.trim { it.code in 9..13 || it.code in listOf(32, 133, 160, 5760,
        8232, 8233, 8239, 8287, 12288) || it.code in 8192..8202 }

    /** Shared scalar arithmetic; exact signed decimal strings, bounded only by the existing text limit. */
    fun addNumber(from: String, delta: String): String? {
        if (!numeric(from) || !numeric(delta)) return null
        return GameplayNumbers.add(from, delta).takeIf(DeckValidation::text)
    }

    internal fun change(state: PlayState, actor: String, command: GameCommand, action: PlayAction): GameplayChange {
        fun reject(reason: String) = GameplayChange(reason = reason)
        val board = state.board
        if (action is PlayAction.RollDie || action is PlayAction.FlipCoin) {
            val result = when (action) {
                is PlayAction.RollDie -> GameplayRandomResult(RandomTool.DIE, action.value.toString(), action.sides)
                is PlayAction.FlipCoin -> GameplayRandomResult(RandomTool.COIN, action.value.name)
                else -> error("unreachable")
            }
            // Even a repeated result is a distinct information-gaining event; no board marker is added.
            return GameplayRules.record(state, actor, command, board, GameplayTransition(random = result))
        }
        if (action is PlayAction.SetPlayerCounter || action is PlayAction.AdjustPlayerCounter) {
            val account = when (action) {
                is PlayAction.SetPlayerCounter -> action.accountId
                is PlayAction.AdjustPlayerCounter -> action.accountId
                else -> error("unreachable")
            }
            val color = when (action) {
                is PlayAction.SetPlayerCounter -> action.color
                is PlayAction.AdjustPlayerCounter -> action.color
                else -> error("unreachable")
            }
            val player = board.players.singleOrNull { it.accountId == account } ?: return reject("missingReference")
            val from = player.counters[color]
            val to = if (action is PlayAction.SetPlayerCounter) trimText(action.value).takeIf { it.isNotEmpty() } else {
                val number = from ?: "0"
                if (!numeric(number)) return reject("inapplicable")
                val delta = (action as PlayAction.AdjustPlayerCounter).delta
                if (delta.removePrefix("+").removePrefix("-").all { it == '0' }) return GameplayChange(state)
                addNumber(number, delta) ?: return reject("invalidInput")
            }
            if (from == to) return GameplayChange(state)
            val counters = if (to == null) player.counters - color else player.counters + (color to to)
            return GameplayRules.record(state, actor, command,
                board.copy(players = board.players.map { if (it.accountId == account) it.copy(counters = counters) else it }),
                GameplayTransition(listOf(GameplayFieldChange(account, GameplayField.COUNTER, from, to, color))))
        }
        val from = board.turn
        val position = TurnPosition.of(from) ?: return reject("inapplicable")
        val nextTurn = action == PlayAction.EndTurn || (action == PlayAction.NextStep && position == TurnPosition.CLEANUP)
        val to = if (nextTurn) {
            val other = board.players.singleOrNull { it.accountId != from.accountId } ?: return reject("inapplicable")
            if (board.players.none { it.accountId == from.accountId }) return reject("inapplicable")
            if (!from.number.matches(Regex("[1-9][0-9]*"))) return reject("inapplicable")
            val number = addNumber(from.number, "1") ?: return reject("invalidInput")
            TurnState(other.accountId, number)
        } else {
            val chosen = when (action) {
                PlayAction.NextStep -> TurnPosition.entries[position.ordinal + 1]
                is PlayAction.ChooseStep -> action.position
                else -> return reject("invalidInput")
            }
            // A passed step only moves forward inside this turn.
            if ((action as? PlayAction.ChooseStep)?.through == true && chosen.ordinal < position.ordinal) return reject("inapplicable")
            from.copy(phase = chosen.phase, step = chosen.step)
        }
        if (from == to) return GameplayChange(state)
        // Direct selection/ordinary advancement only depend on phase and step, not unchanged values.
        val fields = mutableListOf(
            GameplayFieldChange(state.id, GameplayField.PHASE, from.phase, to.phase),
            GameplayFieldChange(state.id, GameplayField.STEP, from.step, to.step))
        if (nextTurn) {
            fields += GameplayFieldChange(state.id, GameplayField.TURN_PLAYER, from.accountId, to.accountId)
            fields += GameplayFieldChange(state.id, GameplayField.TURN_NUMBER, from.number, to.number)
        }
        // Rules v32: a step passed inside a multi-step advance leaves the move where it is.
        if ((action as? PlayAction.ChooseStep)?.through == true)
            return GameplayRules.record(state, actor, command, board.copy(turn = to), GameplayTransition(fields))
        val holder = if (TurnPosition.of(to) == TurnPosition.DECLARE_BLOCKERS)
            board.players.single { it.accountId != to.accountId }.accountId else to.accountId
        return GameplayRules.record(state, actor, command, board.copy(turn = to, attentionHolder = holder),
            GameplayTransition(fields, attention = AttentionChange(board.attentionHolder, holder)))
    }
}

/** Rules v32: an action that moves the turn on by more than one step records every step it passes (CR 500.1)
 * as its own [PlayAction.ChooseStep] with `through = true`, sent with the final step change as one invocation
 * (one Undo, one history group). The final event is exactly the single event used before, so the end state
 * (step, turn, move holder) is unchanged; passed steps are only points a player can go back to. */
object StepRun {
    const val RULES_VERSION = 32

    /** The positions strictly after the board's position and before [target] (null: the end of the turn, through
     * Cleanup) that the turn passes, in order. Empty when [target] is not later in this turn.
     * CR 103.8a: the starting player skips the Draw step of their first turn (turn "1", as turns count both players').
     * CR 508.8: with no attackers declared, Declare blockers and Combat damage are skipped. */
    fun between(board: BoardState, target: TurnPosition?): List<TurnPosition> {
        val from = TurnPosition.of(board.turn) ?: return emptyList()
        val end = target?.ordinal ?: TurnPosition.entries.size
        if (end <= from.ordinal) return emptyList()
        val attackers = board.combat?.pairs?.any { it.attackers.isNotEmpty() } == true
        return TurnPosition.entries.subList(from.ordinal + 1, end).filterNot { position ->
            position == TurnPosition.DRAW && board.turn.number == "1" ||
                !attackers && (position == TurnPosition.DECLARE_BLOCKERS || position == TurnPosition.COMBAT_DAMAGE)
        }
    }

    private fun passed(positions: List<TurnPosition>) = positions.map { PlayAction.ChooseStep(it, through = true) }

    /** End turn: each remaining step of this turn, then the turn change. */
    fun endTurn(board: BoardState): List<PlayAction> = passed(between(board, null)) + PlayAction.EndTurn

    /** Go to [target]: the steps passed on the way when it is later in this turn, then the ordinary step change.
     * An earlier (or the same) step is a correction: one event, as before. */
    fun choose(board: BoardState, target: TurnPosition): List<PlayAction> =
        passed(between(board, target)) + PlayAction.ChooseStep(target)

    /** Pass on to [target] itself as a passed step, for a combined action that does something inside it (for example
     * the draw in the Draw step) before its own final step change. Nothing when [target] is not later in this turn. */
    fun passTo(board: BoardState, target: TurnPosition): List<PlayAction> {
        val from = TurnPosition.of(board.turn) ?: return emptyList()
        if (target.ordinal <= from.ordinal) return emptyList()
        return passed(between(board, target) + target)
    }
}
