package arcanum.engine.turn

import arcanum.api.Zone
import arcanum.engine.Options
import arcanum.engine.action.Knowledge
import arcanum.engine.casting.Legality
import arcanum.engine.casting.Snapshot
import arcanum.engine.input.Input
import arcanum.engine.setup.GameEnd
import arcanum.engine.setup.started
import arcanum.engine.state.GameState
import arcanum.engine.state.PlayerId

/**
 * [arcanum.engine.Engine.shouldPassFor] (I-D3, I-D18): whether a host passes priority for a player at once because they
 * can't do anything with it (CR 117.3). The engine itself never passes for anyone; a host asks this after every
 * outcome and performs [Input.PassPriority] while it is true.
 */
internal object AutoPass {
    fun shouldPass(state: GameState, player: PlayerId): Boolean {
        if (GameEnd.over(state) || !state.started || state.pending != null || state.priority.holder != player) return false
        val p = state.players.firstOrNull { it.id == player }?.takeUnless { it.lost } ?: return false
        val snap = Snapshot(state)
        // Nothing but passing: playing mana abilities alone does nothing at priority (the mana empties, CR 106.4)
        if (Options.any(state, player) { it !is Input.PassPriority && !manaAbility(snap, it) }) return false
        // Cards only they may see that could let them act: a card in hand, a face-down permanent to turn face up
        // (CR 702.37e), a card they may look at and play; not cards outside the game (a revealed companion is public)
        val hidden = Knowledge.privateTo(state, snap.view, player).filter { state.obj(it)?.zone != Zone.Outside }
        // The state can't tell what unknown cards would let them do (D60): their own view decides
        if (hidden.any { state.obj(it)?.unknown == true }) return false
        // CR 723.5: a controlled player's priority is passed the way their controller would have it
        val setting = state.player(Control.decider(state, player)).skipWhenNoChoice
        return setting || hidden.isEmpty()
    }

    private fun manaAbility(snap: Snapshot, input: Input): Boolean {
        val activate = input as? Input.Activate ?: return false
        return Legality.instance(snap, activate.source, activate.ability)?.ability?.let { Legality.isManaAbility(it) } == true
    }
}
