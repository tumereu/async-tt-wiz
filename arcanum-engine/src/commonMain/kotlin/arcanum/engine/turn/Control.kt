package arcanum.engine.turn

import arcanum.api.TurnStep
import arcanum.engine.state.GameState
import arcanum.engine.state.PlayerControl
import arcanum.engine.state.PlayerId

/**
 * Controlling another player (CR 723): who makes a player's choices and decisions now. The engine keeps each decision and
 * input the controlled player's own ([arcanum.engine.decision.Decision.player], the acting player of an input), so they
 * use only that player's resources (723.5a) and they stay the active player (723.3); the host lets [decider] make them.
 */
internal object Control {
    private val COMBAT = setOf(TurnStep.BeginningOfCombat, TurnStep.DeclareAttackers, TurnStep.DeclareBlockers, TurnStep.CombatDamage,
        TurnStep.EndOfCombat)

    /** The control in force now: this turn's (CR 723.1), only in its combat phase when limited to it (723.2), while both
     * players are in the game (CR 800.4a). A player controlling themselves decides as usual (723.9). */
    fun inForce(state: GameState): PlayerControl? = state.turn.controlled?.takeIf { c ->
        (!c.combatOnly || state.turn.step in COMBAT) && c.player != c.controller &&
            state.players.any { it.id == c.player && !it.lost } && state.players.any { it.id == c.controller && !it.lost }
    }

    /** Who makes [player]'s choices and decisions now (CR 723.5): their controller while controlled, else themselves; a
     * controller goes on making their own too (723.8). */
    fun decider(state: GameState, player: PlayerId): PlayerId = inForce(state)?.takeIf { it.player == player }?.controller ?: player

    /** The player [controller] controls now, if any. */
    fun controlledBy(state: GameState, controller: PlayerId): PlayerId? = inForce(state)?.takeIf { it.controller == controller }?.player
}
