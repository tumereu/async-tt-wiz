package org.asyncttwiz.android

import org.asyncttwiz.protocol.CombatDestination
import org.asyncttwiz.protocol.CombatInsertion
import org.asyncttwiz.protocol.IdentityGap

/** At an exact midpoint, crossing onto another slot counts in either direction. */
internal fun combatInsertionIndex(midpoints: List<Float>, x: Float, originX: Float?): Int =
    midpoints.indexOfFirst { midpoint -> x < midpoint ||
        (x == midpoint && (originX == null || originX >= midpoint)) }.let { if (it < 0) midpoints.size else it }

internal fun TableScene.combatDestination(point: TablePoint, scope: TableScope?): CombatDestination? {
    // End extensions and the screen-sized divider tolerance always win over occupied rows.
    combatDrops.asReversed().firstOrNull { it.destination.pairId == null && it.contains(point) }
        ?.let { return it.destination }
    val rows = combatDrops.filter { it.destination.pairId != null && it.destination.insertion == CombatInsertion.APPEND }
    val row = rows.firstOrNull { it.contains(point) } ?: return null
    val origin = items.firstOrNull { it.viewport == "combat" && it.cardId == scope?.contextId }
    val originRow = origin?.let { card -> rows.firstOrNull { it.bounds.contains(card.bounds.center) } }
    val sources = scope?.cardIds.orEmpty().map { this.sources[it]?.sourceId ?: it }.toSet()
    val cards = items.filter { it.viewport == "combat" && row.bounds.contains(it.bounds.center) }
        .sortedBy { it.bounds.x }
    // Moving a combat card onto another occupied slot of the same row reorders slots, rather
    // than silently merging them. Empty opposing rows still accept attacker/blocker placement.
    if (originRow != null && originRow.destination.role == row.destination.role &&
        originRow.destination.pairId != row.destination.pairId && cards.isNotEmpty()) {
        val slots = rows.filter { it.destination.role == row.destination.role }
        val index = combatInsertionIndex(slots.map { it.bounds.center.x }, point.x, origin.bounds.center.x)
        return row.destination.copy(pairId = null, pairGap = IdentityGap(
            slots.getOrNull(index - 1)?.destination?.pairId, slots.getOrNull(index)?.destination?.pairId))
    }
    val remaining = cards.filter { it.sourceId !in sources }
    val index = combatInsertionIndex(remaining.map { it.bounds.center.x }, point.x, origin?.bounds?.center?.x)
    return row.destination.copy(insertion = CombatInsertion.GAP, cardGap = IdentityGap(
        remaining.getOrNull(index - 1)?.cardId, remaining.getOrNull(index)?.cardId))
}
