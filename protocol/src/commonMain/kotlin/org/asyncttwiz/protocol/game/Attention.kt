package org.asyncttwiz.protocol.game

import arcanum.engine.Engine
import arcanum.engine.Host
import arcanum.engine.input.Input
import arcanum.engine.setup.started
import arcanum.engine.state.GameState
import arcanum.engine.state.PlayerId

/** Whose move a game is (I-D54: from the engine's state, not ATW's model): the lobby's "your move" and notifications. */
object Attention {
    /**
     * The player the game waits for in [state]: who makes the open decision, holds priority, or declares attackers or
     * blockers ([Host.decider] of them, CR 723); null once the game is over or before it has started.
     */
    fun of(state: GameState): PlayerId? {
        if (Engine.result(state) != null) return null
        state.pending?.let { return Host.decider(state, it.decision.player) }
        state.priority.holder?.let { return Host.decider(state, it) }
        if (!state.started) return null
        return state.players.firstOrNull { p ->
            !p.lost && Engine.options(state, p.id).any { it is Input.DeclareAttackers || it is Input.DeclareBlockers }
        }?.id
    }
}
