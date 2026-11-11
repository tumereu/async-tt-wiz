package org.asyncttwiz.automatum

import arcanum.api.Zone
import arcanum.engine.Engine
import arcanum.engine.change.Change
import arcanum.engine.state.GameState
import arcanum.engine.state.ObjectId

/**
 * What an object that no longer exists may be shown as (I-D27; CR 608.2h last known information): a card that left a public
 * zone face up (a creature that died, its death trigger still to order: problem report 3UE2NW) was seen by everyone as it
 * was there, so it stays shown by what it was; one that left a hidden zone, or face down, stays hidden.
 */
object LastKnown {
    private val PUBLIC = setOf(Zone.Battlefield, Zone.Graveyard, Zone.Exile, Zone.Stack, Zone.Command)

    /** Whether [id], gone from [state], was last in a public zone face up: what it was is public. False while it exists. */
    fun public(state: GameState, id: ObjectId): Boolean {
        if (state.obj(id) != null) return false
        for (i in state.log.indices.reversed()) {
            val move = (state.log[i].change as? Change.ZoneChange)?.moves?.firstOrNull { it.before.id == id } ?: continue
            return move.before.zone in PUBLIC && !move.before.faceDown
        }
        // Removed rather than moved (a token or copy that ceased to exist), or known only from a Stack item's snapshot
        val known = Engine.lastKnown(state, id) ?: return false
        return known.zone in PUBLIC && known.characteristics?.name != null
    }
}
