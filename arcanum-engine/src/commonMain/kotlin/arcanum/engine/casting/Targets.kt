package arcanum.engine.casting

import arcanum.api.Instructions
import arcanum.api.Mechanic
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.engine.query.Evaluator
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import arcanum.engine.state.TargetRef

/** One target slot of a spell or ability as cast: [key] is where its targets are kept in the Stack item. */
internal data class SlotOf(val key: String, val slot: TargetSlot, val modePosition: Int?)

/**
 * Choosing targets (CR 115, 601.2c): the slots of a spell or ability with its chosen modes, and which objects and
 * players are legal for a slot: matching its filter, not the spell itself (CR 115.5), different from the slots it
 * must differ from, and not protected from it (hexproof, shroud, protection; CR 702.11, 702.18, 702.16).
 */
internal object Targets {
    /** The slots of [instructions] with [modes] chosen: its own first, then each chosen mode's, in the order chosen. */
    fun slots(instructions: Instructions, modes: List<Int>): List<SlotOf> {
        val own = instructions.targets.map { SlotOf(it.name, it, null) }
        val options = instructions.modes?.options.orEmpty()
        val fromModes = modes.flatMapIndexed { position, index ->
            options.getOrNull(index)?.instructions?.targets.orEmpty().map { slot ->
                SlotOf(targetKey(instructions, modes, position, slot.name), slot, position)
            }
        }
        return own + fromModes
    }

    /** Whether [ref] is a legal target for [slot] of the spell or ability [item] (from [source]) controlled by [player]. */
    fun isLegal(snap: Snapshot, eval: Evaluator, item: ObjectId, source: ObjectId?, slot: TargetSlot, ref: TargetRef): Boolean = when (ref) {
        is TargetRef.Object -> {
            val filter = when (val what = slot.what) {
                is TargetKind.Objects -> what.filter
                is TargetKind.Either -> what.objects
                is TargetKind.Players -> null
            }
            filter != null && ref.id != item && exists(snap, ref.id) && eval.matches(filter, ref.id) && !protectedFrom(snap, ref, item, source)
        }
        is TargetRef.Player -> {
            val filter = when (val what = slot.what) {
                is TargetKind.Players -> what.filter
                is TargetKind.Either -> what.players
                is TargetKind.Objects -> null
            }
            filter != null && !snap.state.player(ref.id).lost && eval.matches(filter, ref.id) && !protectedFrom(snap, ref, item, source)
        }
    }

    private fun exists(snap: Snapshot, id: ObjectId): Boolean =
        snap.state.obj(id)?.let { !it.phasedOut } ?: (id in snap.state.stackItems)

    /** Every legal target of [slot] now (CR 601.2c). */
    fun options(snap: Snapshot, eval: Evaluator, item: ObjectId, source: ObjectId?, slot: TargetSlot): List<TargetRef> {
        val objects = when (val what = slot.what) {
            is TargetKind.Objects -> eval.each(what.filter)
            is TargetKind.Either -> eval.each(what.objects)
            is TargetKind.Players -> emptyList()
        }.map { TargetRef.Object(it) }
        val players = snap.state.players.filterNot { it.lost }.map { TargetRef.Player(it.id) }
        return (objects + players).filter { isLegal(snap, eval, item, source, slot, it) }
    }

    /**
     * Hexproof, shroud (CR 702.11b, 702.18a: "can't be the target of spells or abilities [matching]", checked against the
     * spell or ability itself and, for "hexproof from", its source) and protection (CR 702.16b: against the spell or the
     * ability's source).
     */
    /** [applied] comes from a hexproof keyword's expansion (CR 702.11). */
    fun fromHexproof(view: arcanum.engine.view.View, applied: arcanum.engine.view.AppliedMechanic): Boolean {
        val expansion = applied.origin as? arcanum.engine.view.AbilityOrigin.Expansion ?: return false
        val holder = applied.source ?: return false
        return view[holder]?.characteristics?.abilities.orEmpty().any {
            it.origin == expansion.keyword && (it.ability as? arcanum.api.Keyword)?.kind == arcanum.api.Keyword.Hexproof
        }
    }

    fun protectedFrom(snap: Snapshot, ref: TargetRef, item: ObjectId, source: ObjectId?): Boolean {
        val mechanics = when (ref) {
            is TargetRef.Object -> snap.view[ref.id]?.mechanics.orEmpty()
            is TargetRef.Player -> snap.view.players[ref.id]?.mechanics.orEmpty()
        }
        val sourceObject = source ?: item
        // CR 702.11b: "as though they didn't have hexproof": the restriction of their hexproof keywords doesn't apply
        val ignoringHexproof = mechanics.any { it.mechanic == Mechanic.TargetableDespiteHexproof }
        return mechanics.any { applied ->
            if (ignoringHexproof && fromHexproof(snap.view, applied)) return@any false
            val e = snap.evaluator(applied.controller, self = applied.source ?: (ref as? TargetRef.Object)?.id, names = applied.names)
            when (val m = applied.mechanic) {
                is Mechanic.CantBeTargeted -> e.matches(m.by, item) && (m.fromSource == null || e.matches(m.fromSource!!, sourceObject))
                is Mechanic.Protection -> e.matches(m.from, sourceObject)
                else -> false
            }
        }
    }
}

/**
 * Where the targets of a slot are kept in a Stack item ([arcanum.engine.state.StackItem.targets]): the slot's name,
 * except that a mode chosen at [position] whose slot name an earlier chosen mode (or an earlier time of a repeated
 * mode) already used keeps them under "name@position" (CR 700.2, 700.2d). Resolution reads a mode's slots the same way.
 */
internal fun targetKey(instructions: Instructions, modes: List<Int>, position: Int, slotName: String): String {
    val options = instructions.modes?.options.orEmpty()
    val usedBefore = instructions.targets.any { it.name == slotName } ||
        modes.take(position).any { index -> options.getOrNull(index)?.instructions?.targets.orEmpty().any { it.name == slotName } }
    return if (usedBefore) "$slotName@$position" else slotName
}

/** For decisions: the players of [ids]. */
internal fun players(ids: List<PlayerId>) = ids.map { TargetRef.Player(it) }
