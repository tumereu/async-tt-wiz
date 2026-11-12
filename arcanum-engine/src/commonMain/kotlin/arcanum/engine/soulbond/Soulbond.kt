package arcanum.engine.soulbond

import arcanum.engine.state.status
import arcanum.api.CardType
import arcanum.api.Zone
import arcanum.engine.action.Act
import arcanum.engine.change.Change
import arcanum.engine.state.GameState
import arcanum.engine.state.NamedValue
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import arcanum.engine.view.View

/**
 * Pairing (soulbond, CR 702.95): two creatures are paired while both stay creatures on the battlefield under the control of the
 * player who paired them. Each keeps its partner and that player as its [arcanum.engine.state.ObjectStatus.PairedWith]; [check]
 * unpairs them, with the state-based actions, as soon as that stops being true (702.95e).
 */
internal object Soulbond {
    /** The creature [id] is paired with, while the pairing lasts. */
    fun partner(state: GameState, id: ObjectId): ObjectId? {
        val o = state.obj(id) ?: return null
        if (o.zone != Zone.Battlefield) return null
        val other = o.statuses.status<arcanum.engine.state.ObjectStatus.PairedWith>()?.other ?: return null
        val p = state.obj(other)?.takeIf { it.zone == Zone.Battlefield } ?: return null
        return other.takeIf { p.statuses.status<arcanum.engine.state.ObjectStatus.PairedWith>()?.other == id }
    }

    /** CR 702.95a, c, d: [player] pairs [first] with one of [candidates] (chosen when there are several); nothing when either isn't a
     * creature on the battlefield under [player]'s control or is paired already. */
    fun pair(act: Act, player: PlayerId, source: ObjectId?, first: ObjectId, candidates: List<ObjectId>) {
        val view = act.view()
        fun pairable(id: ObjectId) = act.state.obj(id)?.zone == Zone.Battlefield && view[id]?.controller == player &&
            view[id]?.characteristics?.isType(CardType.Creature) == true && partner(act.state, id) == null
        if (!pairable(first)) return
        val options = candidates.filter { it != first && pairable(it) }
        val second = act.chooseObjects(player, source, options, 1, 1, "Pair with").firstOrNull() ?: return
        listOf(first to second, second to first).forEach { (a, b) ->
            val before = act.state.objOrThrow(a).statuses.status<arcanum.engine.state.ObjectStatus.PairedWith>()
            val after = arcanum.engine.state.ObjectStatus.PairedWith(b, player)
            if (before != after) act.record(Change.ObjectStatusChanged(a, before, after))
        }
    }

    /** CR 702.95e: a pair whose creatures aren't both creatures on the battlefield under the pairing player's control is unpaired. */
    fun check(act: Act, view: View) {
        act.state.battlefield.forEach { id ->
            val o = act.state.obj(id) ?: return@forEach
            val by = (o.statuses.status<arcanum.engine.state.ObjectStatus.PairedWith>() ?: return@forEach).by
            val other = partner(act.state, id)
            val holds = other != null && listOf(id, other).all { view[it]?.controller == by && view[it]?.characteristics?.isType(CardType.Creature) == true }
            if (!holds) {
                clear(act, id)
                other?.let { clear(act, it) }
            }
        }
    }

    private fun clear(act: Act, id: ObjectId) {
        val o = act.state.obj(id) ?: return
        o.statuses.status<arcanum.engine.state.ObjectStatus.PairedWith>()?.let { act.record(Change.ObjectStatusChanged(id, it, null)) }
    }
}
