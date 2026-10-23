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
        return GameplayRules.record(state, actor, command, board.copy(turn = to), GameplayTransition(fields))
    }
}
