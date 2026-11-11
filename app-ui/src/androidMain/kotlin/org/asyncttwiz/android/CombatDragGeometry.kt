package org.asyncttwiz.android

import org.asyncttwiz.protocol.CombatDestination

/**
 * CMB-028, CMB-034: what a drop at [point] in the combat band means: a lane. Over a pair, that pair (its attacker a
 * dropped blocker blocks, its target a dropped attacker attacks); elsewhere in a lane (an empty lane, the collapsed band's
 * chips, beside the lanes at the band's ends, CMB-007), that lane's target.
 */
fun TableScene.combatDestination(point: TablePoint): CombatDestination? =
    combatDrops.firstOrNull { it.destination.pairId != null && it.contains(point) }?.destination
        ?: combatDrops.asReversed().firstOrNull { it.destination.pairId == null && it.contains(point) }?.destination
