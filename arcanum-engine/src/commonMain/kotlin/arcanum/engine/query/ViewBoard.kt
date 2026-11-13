package arcanum.engine.query

import arcanum.engine.change.Change
import arcanum.engine.state.GameState
import arcanum.engine.state.ObjectId
import arcanum.engine.state.ObjectSnapshot
import arcanum.engine.state.PlayerId
import arcanum.engine.view.Characteristics
import arcanum.engine.view.View

/**
 * The board as the final [View] sees it, for flows that read the game after continuous effects (resolution,
 * triggers, state-based actions). An object that has left its zone reads as its last known information (CR 608.2h).
 */
internal class ViewBoard(override val state: GameState, val view: View) : Board {
    override fun characteristics(id: ObjectId): Characteristics? =
        view[id]?.characteristics ?: if (state.obj(id) == null) snapshot(id)?.characteristics else null

    override fun controller(id: ObjectId): PlayerId? {
        view[id]?.let { return it.controller ?: it.owner }
        state.stackItems[id]?.let { return it.controller }
        return snapshot(id)?.let { it.controller ?: it.owner }
    }
    override fun createdBy(id: ObjectId): ObjectId? = view[id]?.copiable?.createdBy ?: super.createdBy(id)
    override fun hasMechanic(id: ObjectId, mechanic: (arcanum.api.Mechanic) -> Boolean): Boolean = view[id]?.has(mechanic) == true
    override fun mechanicsOf(id: ObjectId): List<arcanum.api.Mechanic> = view[id]?.mechanics.orEmpty().map { it.mechanic }
}

/** Last known information (CR 608.2h, 603.10): what an object was as it left its zone, read from the tagged events
 * (D33) and the snapshots Stack items, effect objects and delayed triggers keep (D21). */
internal object Lki {
    fun find(state: GameState, id: ObjectId): ObjectSnapshot? {
        if (state.obj(id) != null) return null
        for (i in state.log.indices.reversed()) {
            when (val change = state.log[i].change) {
                is Change.ZoneChange -> change.moves.firstOrNull { it.before.id == id }?.let { m ->
                    return m.seen ?: ObjectSnapshot(m.before.id, m.before.owner, m.before.controller, m.before.zone, printed(m.before),
                        m.before.counters, m.before.damage, m.before.tapped, m.before.token, m.before.attachedTo, m.before.cast, m.before.linked,
                        arcanum.engine.state.snapshotStatuses(m.before))
                }
                is Change.Removed -> change.objects.firstOrNull { it.obj.id == id }?.let { p ->
                    val o = p.obj
                    return ObjectSnapshot(o.id, o.owner, o.controller, o.zone, printed(o), o.counters, o.damage, o.tapped, o.token,
                        o.attachedTo, o.cast, o.linked, arcanum.engine.state.snapshotStatuses(o))
                }
                else -> Unit
            }
        }
        state.stackItems.values.forEach { item -> item.snapshots.firstOrNull { it.id == id }?.let { return it } }
        state.delayedTriggers.values.forEach { d -> d.snapshots.firstOrNull { it.id == id }?.let { return it } }
        return null
    }

    /**
     * The new object [id] became by its last zone change (CR 400.7), when it went to a public zone and is still there
     * ("this card" in the graveyard for an ability that triggered as it died, CR 603.6e); null otherwise.
     */
    fun newObject(state: GameState, id: ObjectId): ObjectId? {
        if (state.obj(id) != null) return null
        for (i in state.log.indices.reversed()) {
            val change = state.log[i].change as? Change.ZoneChange ?: continue
            val m = change.moves.firstOrNull { it.before.id == id } ?: continue
            val public = m.to != arcanum.api.Zone.Hand && m.to != arcanum.api.Zone.Library
            return m.after.id.takeIf { public && state.obj(it)?.zone == m.to }
        }
        return null
    }

    /** What [id] remembered (D41) as it last existed, once it has left its zone. */
    fun memory(state: GameState, id: ObjectId): arcanum.engine.state.Bindings? {
        for (i in state.log.indices.reversed()) {
            val change = state.log[i].change as? Change.ZoneChange ?: continue
            change.moves.firstOrNull { it.before.id == id }?.let { return it.before.linked }
        }
        return null
    }

    /** The printed characteristics of [o] (no continuous effects): the fallback when a move kept no snapshot. */
    fun printed(o: arcanum.engine.state.GameObject): Characteristics? {
        val face = o.definition.faces.getOrNull(o.face) ?: return null
        return arcanum.engine.view.Copiable.initial(o, listOf(arcanum.engine.view.IndexedFace(o.face, face)), null)
    }
}
