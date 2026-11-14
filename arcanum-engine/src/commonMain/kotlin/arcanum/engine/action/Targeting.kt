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
        chosen: Map<String, List<TargetRef>> = emptyMap(), self: ObjectId? = null, item: arcanum.engine.state.StackItem? = null,
        event: arcanum.engine.state.TriggerEvent? = item?.event,
        // fra: the player a per-player slot's target is for ("for each opponent, … that player controls", CR 601.2c)
        slotPlayer: PlayerId? = null,
        // mh3: the kind of an ability not yet on the Stack whose targets are chosen (a trigger being put there, CR 603.3d)
        pendingKind: arcanum.api.AbilityKind? = null,
    ): List<TargetRef> {
        val e = act.evaluator(Context(you = controller, self = source, names = names, stackItem = item, event = event, slotPlayer = slotPlayer))
        val taken = slot.distinctFrom.flatMap { chosen[it].orEmpty() }.toSet()
        val objects = objectFilter(slot.what)?.let { f -> e.each(f) }.orEmpty()
            // CR 115.5: a spell or ability on the Stack can't target itself
            .filter { it != self }
            .map { TargetRef.Object(it) }
        val players = playerFilter(slot.what)?.let { f -> act.state.apnap().filter { e.matches(f, it) } }.orEmpty()
            .map { TargetRef.Player(it) }
        return (objects + players).filter { it !in taken && targetable(act, it, controller, source, item?.id ?: self, pendingKind) }
    }

    /** Whether [slot]'s targets limit each other: one controller or owner, a shared or a different quality, one for each
     * value, a total (CR 601.2c). */
    fun limitsEachOther(slot: TargetSlot): Boolean = slot.sameController || slot.sameOwner || slot.sharing != null ||
        slot.distinct != null || slot.onePer != null || slot.totalAtMost != null

    /**
     * Why [chosen] breaks the limits [slot]'s targets put on each other, or null when it doesn't: the one check for spells,
     * activated and triggered abilities (CR 601.2c, 602.2b, 603.3d) and new targets of a copy or a change (CR 707.10c,
     * 115.7). Each limit holds for any part of a group that keeps it (a smaller group is never refused where a larger is
     * accepted), which [possible] relies on.
     */
    fun groupRefusal(slot: TargetSlot, chosen: List<TargetRef>, e: Evaluator): String? {
        if (!limitsEachOther(slot)) return null
        val ids = chosen.mapNotNull { (it as? TargetRef.Object)?.id }
        // "target creatures controlled by the same opponent"
        if (slot.sameController && ids.mapNotNull { e.controllerOf(it) }.distinct().size > 1) return "The targets must have the same controller"
        // gtc chunk 10: "from a single graveyard": one owner
        if (slot.sameOwner && ids.mapNotNull { e.ownerOf(it) }.distinct().size > 1) return "The targets must have the same owner"
        // ecl: "two target creature cards that share a creature type"
        slot.sharing?.let { q -> if (ids.any { a -> ids.any { b -> a != b && !e.shares(q, a, b) } }) return "The targets must share $q" }
        // spm: "up to six target creature cards with different names"
        slot.distinct?.let { q -> if (ids.any { a -> ids.any { b -> a != b && e.shares(q, a, b) } }) return "The targets must differ in $q" }
        // fra: "up to one target … of each card type": each target stands for a value of its own
        slot.onePer?.let { q -> if (!e.eachStandsForOne(q, ids)) return "Each target must stand for a different $q" }
        // msh: "with total mana value 6 or less"
        slot.totalAtMost?.let { limit -> if (ids.sumOf { e.property(limit.property, it) } > e.value(limit.atMost)) return "The targets' total is more than allowed" }
        return null
    }

    /** The most targets [slot] may have: its printed maximum, and no more than an amount divided among them, since each
     * target of a division gets at least one of it (CR 601.2d). Null for any number. */
    fun maxTargets(slot: TargetSlot, e: Evaluator): Int? {
        val printed = slot.max?.let { e.value(it) }
        val amount = slot.divided?.let { maxOf(0, e.value(it)) } ?: return printed
        return printed?.let { minOf(it, amount) } ?: amount
    }

    /** Whether [min] of [options] can be chosen together within [slot]'s limits on each other (CR 603.3d, 700.2b: a
     * triggered ability or mode without such a group can't be put on the Stack or chosen). A search too long to finish
     * takes it as possible (the choice is checked exactly). */
    fun possible(slot: TargetSlot, options: List<TargetRef>, min: Int, e: Evaluator): Boolean {
        if (options.size < min) return false
        if (min < 1 || !limitsEachOther(slot)) return true
        var budget = SEARCH
        fun extend(from: Int, group: List<TargetRef>): Boolean {
            if (group.size == min) return true
            for (i in from..options.size - (min - group.size)) {
                if (budget-- <= 0) return true
                val next = group + options[i]
                if (groupRefusal(slot, next, e) == null && extend(i + 1, next)) return true
            }
            return false
        }
        return extend(0, emptyList())
    }

    /** [chooser] chooses [min] to [max] of [options] for [slot] while a spell or ability is put on the Stack or copied, the
     * group refused when it breaks the slot's limits on each other ([groupRefusal]). */
    fun choose(act: Act, chooser: PlayerId, source: ObjectId?, slot: TargetSlot, options: List<TargetRef>, min: Int, max: Int?, e: Evaluator): List<TargetRef> =
        act.chooseTargets(chooser, source, slot.name, options, min, max).also { chosen -> groupRefusal(slot, chosen, e)?.let { throw IllegalArgumentException(it) } }

    private const val SEARCH = 10_000

    /** CR 608.2b: [ref] is still a legal target of [slot]. */
    fun legal(
        act: Act, slot: TargetSlot, ref: TargetRef, controller: PlayerId, source: ObjectId?, names: Bindings,
        item: arcanum.engine.state.StackItem? = null,
    ): Boolean {
        val e = act.evaluator(Context(you = controller, self = source, names = names, stackItem = item, event = item?.event))
        val matches = when (ref) {
            is TargetRef.Object -> {
                val exists = act.state.obj(ref.id)?.let { o -> !o.phasedOut } ?: (ref.id in act.state.stackItems)
                // fra: a per-player slot's target matches for one of its players (CR 601.2c, 608.2b)
                val slotPlayers = slot.perPlayer?.let { f -> act.state.apnap().filter { e.matches(f, it) } }
                exists && objectFilter(slot.what)?.let { f -> zoneOk(act, f, ref.id) &&
                    (slotPlayers?.any { p -> e.with(e.ctx.copy(slotPlayer = p)).matches(f, ref.id) } ?: e.matches(f, ref.id)) } == true
            }
            is TargetRef.Player -> !act.state.player(ref.id).lost && playerFilter(slot.what)?.let { e.matches(it, ref.id) } == true
        }
        return matches && targetable(act, ref, controller, source, item?.id)
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
    fun targetable(act: Act, ref: TargetRef, controller: PlayerId, source: ObjectId?, item: ObjectId? = null, pendingKind: arcanum.api.AbilityKind? = null): Boolean {
        val view = act.view()
        val mechanics = when (ref) {
            is TargetRef.Object -> view[ref.id]?.mechanics.orEmpty()
            is TargetRef.Player -> view.players[ref.id]?.mechanics.orEmpty()
        }
        // CR 702.11b: "as though they didn't have hexproof"
        val ignoringHexproof = mechanics.any { applied ->
            val m = applied.mechanic as? Mechanic.TargetableDespiteHexproof ?: return@any false
            m.by == null || spellMatches(act.evaluator(Context(you = applied.controller, self = applied.source, names = applied.names)), m.by!!, controller, source, item, pendingKind)
        }
        return mechanics.none { applied ->
            !(ignoringHexproof && arcanum.engine.casting.Targets.fromHexproof(view, applied)) && forbids(act, applied, controller, source, item, pendingKind)
        }
    }

    private fun forbids(act: Act, applied: AppliedMechanic, controller: PlayerId, source: ObjectId?, item: ObjectId?, pendingKind: arcanum.api.AbilityKind?): Boolean {
        val e = act.evaluator(Context(you = applied.controller, self = applied.source, names = applied.names))
        return when (val m = applied.mechanic) {
            is Mechanic.CantBeTargeted -> spellMatches(e, m.by, controller, source, item, pendingKind) &&
                (m.fromSource == null || (source != null && e.matches(m.fromSource!!, source)))
            is Mechanic.Protection -> source != null && e.matches(m.from, source)
            else -> false
        }
    }

    /** Whether the targeting spell or ability (controlled by [controller], from [source]; [item] on the Stack, or not there yet and of
     * [pendingKind]) matches [filter]. */
    private fun spellMatches(e: Evaluator, filter: ObjectFilter, controller: PlayerId, source: ObjectId?, item: ObjectId?, pendingKind: arcanum.api.AbilityKind?): Boolean = when (filter) {
        ObjectFilter.Any -> true
        is ObjectFilter.ControlledBy -> e.matches(filter.players, controller)
        is ObjectFilter.And -> filter.all.all { spellMatches(e, it, controller, source, item, pendingKind) }
        is ObjectFilter.Or -> filter.any.any { spellMatches(e, it, controller, source, item, pendingKind) }
        is ObjectFilter.Not -> !spellMatches(e, filter.filter, controller, source, item, pendingKind)
        // mh3: a quality of the ability itself ("hexproof from activated and triggered abilities", CR 702.11d)
        is ObjectFilter.AbilityOf -> abilityMatches(e, filter, source, item, pendingKind)
        ObjectFilter.Category(arcanum.api.ObjectCategory.StackAbility) -> abilityMatches(e, filter, source, item, pendingKind)
        else -> source != null && e.matches(filter, source)
    }

    private fun abilityMatches(e: Evaluator, filter: ObjectFilter, source: ObjectId?, item: ObjectId?, pendingKind: arcanum.api.AbilityKind?): Boolean = when {
        item != null && item in e.board.state.stackItems -> e.matches(filter, item)
        pendingKind == null -> false
        filter is ObjectFilter.AbilityOf -> (filter.kind == null || filter.kind == pendingKind) && (filter.source == null || (source != null && e.matches(filter.source!!, source)))
        else -> true
    }
}
