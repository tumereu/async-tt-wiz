package arcanum.engine.action

import arcanum.api.Mechanic
import arcanum.api.ObjectFilter
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.Zone
import arcanum.engine.query.Context
import arcanum.engine.query.Evaluator
import arcanum.engine.state.Bindings
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import arcanum.engine.state.TargetRef
import arcanum.engine.view.AppliedMechanic

/**
 * Targets (CR 115): the legal choices for a target slot of a spell or ability controlled by [controller] whose source
 * is [source], and whether a chosen target is still legal as it resolves (CR 608.2b). Hexproof, shroud and "can't be
 * the target" (CR 702.11, 702.18) look at the spell or ability and its source; protection at the source (CR 702.16b).
 */
internal object Targeting {
    fun options(
        act: Act, slot: TargetSlot, controller: PlayerId, source: ObjectId?, names: Bindings,
        chosen: Map<String, List<TargetRef>> = emptyMap(), self: ObjectId? = null,
    ): List<TargetRef> {
        val e = act.evaluator(Context(you = controller, self = source, names = names))
        val taken = slot.distinctFrom.flatMap { chosen[it].orEmpty() }.toSet()
        val objects = objectFilter(slot.what)?.let { f -> e.each(f) }.orEmpty()
            // CR 115.5: a spell or ability on the Stack can't target itself
            .filter { it != self }
            .map { TargetRef.Object(it) }
        val players = playerFilter(slot.what)?.let { f -> act.state.apnap().filter { e.matches(f, it) } }.orEmpty()
            .map { TargetRef.Player(it) }
        return (objects + players).filter { it !in taken && targetable(act, it, controller, source) }
    }

    /** CR 608.2b: [ref] is still a legal target of [slot]. */
    fun legal(act: Act, slot: TargetSlot, ref: TargetRef, controller: PlayerId, source: ObjectId?, names: Bindings): Boolean {
        val e = act.evaluator(Context(you = controller, self = source, names = names))
        val matches = when (ref) {
            is TargetRef.Object -> {
                val exists = act.state.obj(ref.id)?.let { o -> !o.phasedOut } ?: (ref.id in act.state.stackItems)
                exists && objectFilter(slot.what)?.let { f -> zoneOk(act, f, ref.id) && e.matches(f, ref.id) } == true
            }
            is TargetRef.Player -> !act.state.player(ref.id).lost && playerFilter(slot.what)?.let { e.matches(it, ref.id) } == true
        }
        return matches && targetable(act, ref, controller, source)
    }

    /** The zone a filter looks in (the battlefield unless it names one, the Stack for spells, CR 115.4). */
    private fun zoneOk(act: Act, filter: ObjectFilter, id: ObjectId): Boolean {
        val zones = Evaluator.zonesIn(filter)
        val zone = act.state.obj(id)?.zone ?: Zone.Stack
        return if (zones.isNotEmpty()) zone in zones else zone == Zone.Battlefield || zone == Zone.Stack
    }

    private fun objectFilter(kind: TargetKind): ObjectFilter? = when (kind) {
        is TargetKind.Objects -> kind.filter
        is TargetKind.Either -> kind.objects
        is TargetKind.Players -> null
    }

    private fun playerFilter(kind: TargetKind) = when (kind) {
        is TargetKind.Players -> kind.filter
        is TargetKind.Either -> kind.players
        is TargetKind.Objects -> null
    }

    /** Hexproof, shroud, "can't be the target" and protection (CR 702.11, 702.16, 702.18). */
    fun targetable(act: Act, ref: TargetRef, controller: PlayerId, source: ObjectId?): Boolean {
        val view = act.view()
        val mechanics = when (ref) {
            is TargetRef.Object -> view[ref.id]?.mechanics.orEmpty()
            is TargetRef.Player -> view.players[ref.id]?.mechanics.orEmpty()
        }
        return mechanics.none { applied -> forbids(act, applied, controller, source) }
    }

    private fun forbids(act: Act, applied: AppliedMechanic, controller: PlayerId, source: ObjectId?): Boolean {
        val e = act.evaluator(Context(you = applied.controller, self = applied.source, names = applied.names))
        return when (val m = applied.mechanic) {
            is Mechanic.CantBeTargeted -> spellMatches(e, m.by, controller, source) &&
                (m.fromSource == null || (source != null && e.matches(m.fromSource!!, source)))
            is Mechanic.Protection -> source != null && e.matches(m.from, source)
            else -> false
        }
    }

    /** Whether the targeting spell or ability (controlled by [controller], from [source]) matches [filter]. */
    private fun spellMatches(e: Evaluator, filter: ObjectFilter, controller: PlayerId, source: ObjectId?): Boolean = when (filter) {
        ObjectFilter.Any -> true
        is ObjectFilter.ControlledBy -> e.matches(filter.players, controller)
        is ObjectFilter.And -> filter.all.all { spellMatches(e, it, controller, source) }
        is ObjectFilter.Or -> filter.any.any { spellMatches(e, it, controller, source) }
        is ObjectFilter.Not -> !spellMatches(e, filter.filter, controller, source)
        else -> source != null && e.matches(filter, source)
    }
}
