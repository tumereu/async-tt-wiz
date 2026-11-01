package org.asyncttwiz.android

import org.asyncttwiz.protocol.CombatDestination
import org.asyncttwiz.protocol.CombatInsertion
import org.asyncttwiz.protocol.IdentityGap

/** At an exact midpoint, crossing onto another slot counts in either direction. */
internal fun combatInsertionIndex(midpoints: List<Float>, x: Float, originX: Float?): Int =
    midpoints.indexOfFirst { midpoint -> x < midpoint ||
        (x == midpoint && (originX == null || originX >= midpoint)) }.let { if (it < 0) midpoints.size else it }

/** CMB-027: the combat card in the row under [point] that the dragged card (drawn at card size, centred on
 * [point]) covers by at least half its width, the most covered first; the dragged cards themselves don't count. */
private fun TableScene.overlappedCombatCard(point: TablePoint, sources: Set<String>, rows: List<CombatDropRegion>): TableItem? {
    val row = rows.firstOrNull { point.y >= it.bounds.y && point.y < it.bounds.bottom } ?: return null
    val left = point.x - cardWidth / 2
    val right = point.x + cardWidth / 2
    return items.filter { it.viewport == "combat" && it.cardId != null && it.sourceId !in sources &&
            it.bounds.center.y >= row.bounds.y && it.bounds.center.y < row.bounds.bottom }
        .map { it to minOf(right, it.bounds.right) - maxOf(left, it.bounds.x) }
        .filter { (card, overlap) -> overlap >= card.bounds.width / 2 }
        .maxByOrNull { it.second }?.first
}

internal fun TableScene.combatDestination(point: TablePoint, scope: TableScope?): CombatDestination? {
    val rows = combatDrops.filter { it.destination.pairId != null && it.destination.insertion == CombatInsertion.APPEND }
    val origin = items.firstOrNull { it.viewport == "combat" && it.cardId == scope?.contextId }
    val sources = scope?.cardIds.orEmpty().map { this.sources[it]?.sourceId ?: it }.toSet()
    // CMB-027: covering at least half of a card goes beside it, in its pair (also across lanes), before
    // dividers, end gaps and pair reordering. The side follows the dragged card's centre.
    overlappedCombatCard(point, sources, rows)?.let { covered ->
        val pairRow = rows.firstOrNull { it.bounds.contains(covered.bounds.center) } ?: return@let
        val remaining = items.filter { it.viewport == "combat" && it.sourceId !in sources &&
            pairRow.bounds.contains(it.bounds.center) }.sortedBy { it.bounds.x }
        val index = remaining.indexOf(covered) +
            combatInsertionIndex(listOf(covered.bounds.center.x), point.x, origin?.bounds?.center?.x)
        return pairRow.destination.copy(insertion = CombatInsertion.GAP, cardGap = IdentityGap(
            remaining.getOrNull(index - 1)?.cardId, remaining.getOrNull(index)?.cardId))
    }
    // End extensions and the screen-sized divider tolerance always win over occupied rows.
    combatDrops.asReversed().firstOrNull { it.destination.pairId == null && it.contains(point) }
        ?.let { return it.destination }
    val row = rows.firstOrNull { it.contains(point) } ?: return null
    val originRow = origin?.let { card -> rows.firstOrNull { it.bounds.contains(card.bounds.center) } }
    val cards = items.filter { it.viewport == "combat" && row.bounds.contains(it.bounds.center) }
        .sortedBy { it.bounds.x }
    // Moving a combat card onto another occupied slot of the same row reorders slots, rather
    // than silently merging them. Empty opposing rows still accept attacker/blocker placement.
    if (originRow != null && originRow.destination.role == row.destination.role &&
        originRow.destination.pairId != row.destination.pairId && cards.isNotEmpty()) {
        if (originRow.destination.attackTarget != row.destination.attackTarget) return row.destination
        val slots = rows.filter { it.destination.role == row.destination.role }
        val index = combatInsertionIndex(slots.map { it.bounds.center.x }, point.x, origin.bounds.center.x)
        val boundary = if (index == 0) slots.first().bounds.x else slots[index - 1].bounds.right
        return (combatDrops.firstOrNull { it.destination.pairId == null && it.destination.pairGap != null &&
            it.destination.role == row.destination.role && kotlin.math.abs(it.landing.x - boundary) < .1f }?.destination ?: row.destination)
            .copy(attackTarget = row.destination.attackTarget)
    }
    val remaining = cards.filter { it.sourceId !in sources }
    val index = combatInsertionIndex(remaining.map { it.bounds.center.x }, point.x, origin?.bounds?.center?.x)
    return row.destination.copy(insertion = CombatInsertion.GAP, cardGap = IdentityGap(
        remaining.getOrNull(index - 1)?.cardId, remaining.getOrNull(index)?.cardId))
}
