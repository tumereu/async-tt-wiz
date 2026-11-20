package arcanum.engine.trigger

import arcanum.api.Zone
import arcanum.engine.change.Change
import arcanum.engine.change.Meaning
import arcanum.engine.state.GameState
import arcanum.engine.state.PlayerId
import arcanum.engine.state.TargetRef

/** Crimes (CR 700.13): which spells and abilities put on the Stack commit one. */
internal object Crimes {
    /**
     * Whether [added], about to go on the Stack in [state], commits a crime (CR 700.13): it is cast, activated or a
     * triggered ability put on the Stack, and one of its targets is another player, a permanent, spell or ability another
     * player controls, or a card in another player's graveyard (not a card in exile or another zone). A copy put on the
     * Stack isn't cast (CR 707.10), and new targets chosen later aren't put on the Stack (CR 115.7): neither is a crime.
     */
    fun committed(state: GameState, added: Change.StackItemAdded): Boolean {
        if (added.meanings.none { it == Meaning.Cast || it == Meaning.Activated || it == Meaning.Triggered }) return false
        val player = added.item.controller
        val targets = added.item.targets.values.flatten()
        if (targets.isEmpty()) return false
        val view by lazy { arcanum.engine.view.Layers.view(state) }
        return targets.any { ref -> against(state, { view[it]?.controller }, ref).let { it != null && it != player } }
    }

    /** The player targeting [ref] is a crime against: the player, the controller of a permanent, spell or ability, the owner of
     * a card in a graveyard; null for a card elsewhere. */
    private fun against(state: GameState, viewController: (arcanum.engine.state.ObjectId) -> PlayerId?, ref: TargetRef): PlayerId? = when (ref) {
        is TargetRef.Player -> ref.id
        is TargetRef.Object -> {
            val o = state.obj(ref.id)
            when {
                o == null -> state.stackItems[ref.id]?.controller
                o.zone == Zone.Battlefield || o.zone == Zone.Stack -> viewController(o.id) ?: o.controller ?: o.owner
                o.zone == Zone.Graveyard -> o.owner
                else -> null
            }
        }
    }
}
