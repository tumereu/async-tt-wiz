package arcanum.engine.soulbond

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
 * player who paired them. Each keeps its partner in its memory under [PAIRED_WITH] and that player under [PAIRED_BY]; [check]
 * unpairs them, with the state-based actions, as soon as that stops being true (702.95e).
 */
internal object Soulbond {
    const val PAIRED_WITH: String = "\$paired-with"
    const val PAIRED_BY: String = "\$paired-by"

    /** The creature [id] is paired with, while the pairing lasts. */
    fun partner(state: GameState, id: ObjectId): ObjectId? {
        val o = state.obj(id) ?: return null
        if (o.zone != Zone.Battlefield) return null
        val other = (o.memory[PAIRED_WITH] as? NamedValue.Objects)?.ids?.singleOrNull() ?: return null
        val p = state.obj(other)?.takeIf { it.zone == Zone.Battlefield } ?: return null
        return other.takeIf { (p.memory[PAIRED_WITH] as? NamedValue.Objects)?.ids?.singleOrNull() == id }
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
            set(act, a, PAIRED_WITH, NamedValue.Objects(listOf(b)))
            set(act, a, PAIRED_BY, NamedValue.Players(listOf(player)))
        }
    }

    /** CR 702.95e: a pair whose creatures aren't both creatures on the battlefield under the pairing player's control is unpaired. */
    fun check(act: Act, view: View) {
        act.state.battlefield.forEach { id ->
            val o = act.state.obj(id) ?: return@forEach
            if (o.memory[PAIRED_WITH] == null) return@forEach
            val by = (o.memory[PAIRED_BY] as? NamedValue.Players)?.ids?.singleOrNull()
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
        listOf(PAIRED_WITH, PAIRED_BY).forEach { key -> o.memory[key]?.let { act.record(Change.Memory(id, key, it, null)) } }
    }

    private fun set(act: Act, id: ObjectId, key: String, value: NamedValue) {
        val before = act.state.objOrThrow(id).memory[key]
        if (before != value) act.record(Change.Memory(id, key, before, value))
    }
}
