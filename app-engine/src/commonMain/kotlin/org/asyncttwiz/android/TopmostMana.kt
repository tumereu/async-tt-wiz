package org.asyncttwiz.android

import arcanum.engine.decision.ManaUse
import arcanum.engine.state.ObjectId

/**
 * TBL-130 (owner playtest 2026-11-07): the engine's mana suggestion (Autotap, STK-024) picks among identical sources by its
 * own order; this swaps each pick for the topmost member of its stack that can make the same use. Worked out where the
 * engine is (web client step 4e: the browser's engine worker), from the stacks of identical battlefield cards as the table
 * lays them out ([stacks]: each stack's members, topmost last).
 */
object TopmostMana {
    /**
     * [uses] (Autotap's suggestion for a payment offering [options]) with each mana ability of a card in one of [stacks]
     * made by the topmost members of that stack able to make the same use (the same ability and colour, offered in
     * [options]); [card] tells the table card of an engine object. Other uses stay as they are.
     */
    fun of(uses: List<ManaUse>, options: List<ManaUse>, stacks: List<List<String>>, card: (ObjectId) -> String?): List<ManaUse> {
        if (stacks.isEmpty() || uses.none { it is ManaUse.Ability }) return uses
        val stackOf = HashMap<String, List<String>>()
        for (stack in stacks) for (member in stack) stackOf[member] = stack
        val offered = options.filterIsInstance<ManaUse.Ability>().toSet()
        // The engine objects of the table cards the options name
        val objects = HashMap<String, ObjectId>()
        for (use in offered) card(use.source)?.let { objects.getOrPut(it) { use.source } }
        val taken = HashSet<String>()
        // Cards another way of paying uses (convoke, improvise) stay with it
        for (use in uses) if (use is ManaUse.Special) use.objects.mapNotNullTo(taken, card)
        return uses.map { use ->
            if (use !is ManaUse.Ability) return@map use
            val own = card(use.source)
            val stack = own?.let(stackOf::get)
            val swapped = stack?.asReversed()?.firstNotNullOfOrNull { member ->
                if (member in taken) null
                else objects[member]?.let { use.copy(source = it) }?.takeIf { it in offered }
            }
            (swapped ?: use).also { chosen -> card((chosen as ManaUse.Ability).source)?.let(taken::add) }
        }
    }
}
