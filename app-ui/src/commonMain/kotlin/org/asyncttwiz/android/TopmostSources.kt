package org.asyncttwiz.android

import arcanum.engine.decision.ManaUse
import arcanum.engine.state.ObjectId

/**
 * TBL-130 (owner playtest 2026-11-07): wherever the table acts on one member of a stack for the player, it takes the
 * topmost (front-drawn) member able to. The engine's mana suggestion (Autotap, STK-024) picks among identical sources by
 * its own order; these swap each pick for the topmost member of its stack that can make the same use.
 */
object TopmostSources {
    /**
     * [uses] (Autotap's suggestion for a payment offering [options]) with each mana ability of a card in a stack of
     * [groups] made by the topmost members of that stack able to make the same use (the same ability and colour, offered in
     * [options]); [card] tells the table card of an engine object. Other uses stay as they are.
     */
    fun of(uses: List<ManaUse>, options: List<ManaUse>, groups: List<BattlefieldGroup>, card: (ObjectId) -> String?): List<ManaUse> =
        TopmostMana.of(uses, options, stacks(groups), card)

    /** The members of each stack of [groups] (topmost last), as [TopmostMana] takes them. */
    fun stacks(groups: List<BattlefieldGroup>): List<List<String>> =
        groups.filter { it.kind == BattlefieldGroup.Kind.Stack }.map { it.members }

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
