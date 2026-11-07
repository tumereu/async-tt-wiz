package org.asyncttwiz.android

import arcanum.engine.decision.ManaUse
import arcanum.engine.state.ObjectId

/**
 * TBL-130 (owner playtest 2026-11-07): wherever the table acts on one member of a stack for the player, it takes the
 * topmost (front-drawn) member able to. The engine's mana suggestion (Autotap, STK-024) picks among identical sources by
 * its own order; these swap each pick for the topmost member of its stack that can make the same use.
 */
internal object TopmostSources {
    /**
     * [uses] (Autotap's suggestion for a payment offering [options]) with each mana ability of a card in a stack of
     * [groups] made by the topmost members of that stack able to make the same use (the same ability and colour, offered in
     * [options]); [card] tells the table card of an engine object. Other uses stay as they are.
     */
    fun of(uses: List<ManaUse>, options: List<ManaUse>, groups: List<BattlefieldGroup>, card: (ObjectId) -> String?): List<ManaUse> {
        val stacks = groups.filter { it.kind == BattlefieldGroup.Kind.Stack }
        if (stacks.isEmpty() || uses.none { it is ManaUse.Ability }) return uses
        val stackOf = HashMap<String, BattlefieldGroup>()
        for (stack in stacks) for (member in stack.members) stackOf[member] = stack
        val offered = options.filterIsInstance<ManaUse.Ability>().toSet()
        // The engine objects of the table cards the options name
        val objects = HashMap<String, ObjectId>()
        for (use in offered) card(use.source)?.let { objects.putIfAbsent(it, use.source) }
        val taken = HashSet<String>()
        // Cards another way of paying uses (convoke, improvise) stay with it
        for (use in uses) if (use is ManaUse.Special) use.objects.mapNotNullTo(taken, card)
        return uses.map { use ->
            if (use !is ManaUse.Ability) return@map use
            val own = card(use.source)
            val stack = own?.let(stackOf::get)
            val swapped = stack?.members?.asReversed()?.firstNotNullOfOrNull { member ->
                if (member in taken) null
                else objects[member]?.let { use.copy(source = it) }?.takeIf { it in offered }
            }
            (swapped ?: use).also { chosen -> card((chosen as ManaUse.Ability).source)?.let(taken::add) }
        }
    }

    /**
     * ZON-079: the table cards [sources] (the sources Autotap would tap, outlined) with each stack of [groups] outlined on
     * its topmost untapped members ([tapped] telling), as many as Autotap takes from it.
     */
    fun cards(sources: Set<String>, groups: List<BattlefieldGroup>, tapped: (String) -> Boolean): Set<String> {
        if (sources.isEmpty()) return sources
        val result = sources.toMutableSet()
        for (stack in groups) {
            if (stack.kind != BattlefieldGroup.Kind.Stack) continue
            val picked = stack.members.filter { it in sources }
            if (picked.isEmpty()) continue
            val untapped = stack.members.asReversed().filter { !tapped(it) }
            if (picked.any(tapped) || untapped.size < picked.size) continue
            result -= picked.toSet()
            result += untapped.take(picked.size)
        }
        return result
    }
}
