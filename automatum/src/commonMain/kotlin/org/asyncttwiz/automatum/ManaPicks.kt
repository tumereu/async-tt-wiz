package org.asyncttwiz.automatum

import arcanum.engine.decision.Decision
import arcanum.engine.decision.ManaUse

/**
 * The mana section's sources as the player taps them (STK-024, I-D6): each source is one chip, whatever colours it can
 * make, and the chips tapped are a set. Which colour each tapped source makes is worked out from the whole set, so the
 * order the player taps them in never matters (a land making {W} or {U} beside a Plains pays {W}{U}).
 */
object ManaPicks {
    /** [decision]'s uses grouped by what the player taps: one group per source (its abilities and colours), pool mana,
     * life or another way, in the engine's order. */
    fun groups(decision: Decision.ManaPayment): List<List<ManaUse>> = decision.options.groupBy { use -> when (use) {
        is ManaUse.Ability -> use.source.toString()
        is ManaUse.Pool -> "pool/${use.index}"
        is ManaUse.Life -> "life/${use.symbol}"
        is ManaUse.Special -> "special/${use.way}/${use.objects}"
    } }.values.toList()

    /**
     * One use of each of [tapped] (groups of [groups]) that pays ([pays], the engine's check), preferring the uses in
     * [preferred] (the colours shown so far): null when no combination among the first [limit] tried pays. A source with
     * one use has nothing to choose.
     */
    fun assign(tapped: List<List<ManaUse>>, preferred: List<ManaUse>, pays: (List<ManaUse>) -> Boolean, limit: Int = 64): List<ManaUse>? {
        val ordered = tapped.map { group -> group.sortedBy { if (it in preferred) 0 else 1 } }
        var tries = 0
        val chosen = ArrayList<ManaUse>(ordered.size)
        fun go(i: Int): Boolean {
            if (i == ordered.size) return (++tries <= limit) && pays(chosen.toList())
            for (use in ordered[i]) {
                chosen += use
                if (go(i + 1)) return true
                chosen.removeAt(chosen.lastIndex)
                if (tries >= limit) return false
            }
            return false
        }
        return if (go(0)) chosen.toList() else null
    }

    /**
     * The uses after tapping or untapping [group] with [current] tapped: untapping drops its use; tapping adds it. Either
     * way the colours of all tapped sources are worked out again ([assign]) so the set pays when it can; when it can't,
     * the kept uses stay as they were and a newly tapped source makes its first colour.
     */
    fun toggle(decision: Decision.ManaPayment, current: List<ManaUse>, group: List<ManaUse>, pays: (List<ManaUse>) -> Boolean): List<ManaUse> {
        val all = groups(decision)
        val untapping = current.any { it in group }
        val kept = if (untapping) current.filterNot { it in group } else current
        val tapped = all.filter { g -> g.any { it in kept } || (!untapping && g == group) }
        assign(tapped, kept, pays)?.let { return it }
        return if (untapping) kept else kept + group.first()
    }
}
