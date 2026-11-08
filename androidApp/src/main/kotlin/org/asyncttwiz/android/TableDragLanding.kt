package org.asyncttwiz.android

import org.asyncttwiz.protocol.PlayZone
import org.asyncttwiz.protocol.CombatInsertion
import org.asyncttwiz.protocol.CombatRole

data class TableDragLanding(val outlines: List<TableBounds> = emptyList(),
    val hand: TableBounds? = null, val insertionX: Float? = null,
    val additionalHands: List<Pair<TableBounds, Float?>> = emptyList())

/** Uses the same snapped destination and captured relative positions as the move adapter. */
fun tableDragLanding(scene: TableScene, preview: TableGesturePreview): TableDragLanding {
    val scope = preview.scope ?: return TableDragLanding()
    val destination = preview.destination ?: return TableDragLanding()
    val destinations = scope.cardIds.groupBy { scene.ownerMoveDestination(it, destination) }
    if (destinations.size > 1 || destinations.keys.singleOrNull()?.let { it != destination } == true) {
        val landings = destinations.map { (target, ids) -> tableDragLanding(scene, preview.copy(
            scope = TableScope(scope.contextId.takeIf { it in ids } ?: ids.first(), ids), destination = target)) }
        val hands = landings.flatMap { landing -> listOfNotNull(landing.hand?.let { it to landing.insertionX }) + landing.additionalHands }
        return TableDragLanding(landings.flatMap { it.outlines }, hands.firstOrNull()?.first, hands.firstOrNull()?.second, hands.drop(1))
    }
    val ids = scope.cardIds
    return when (destination) {
        // TBL-052, TBL-004: the battlefield lays itself out, so a card dropped on it lights up the battlefield it lands on
        is TableDestination.Grid -> TableDragLanding(listOfNotNull(scene.fields.firstOrNull { it.containerId == destination.containerId }?.bounds))
        is TableDestination.CardPile -> TableDragLanding(listOfNotNull(scene.items.firstOrNull { it.cardId == destination.cardId }
            ?.let { card -> scene.fields.firstOrNull { it.containerId == card.viewport } }?.bounds))
        is TableDestination.Container -> {
            val item = scene.items.firstOrNull { it.id == destination.id } ?: return TableDragLanding()
            if (destination.zone == PlayZone.HAND) {
                val right = destination.handGap?.beforeId?.let { id -> scene.items.firstOrNull { it.cardId == id } }
                val left = destination.handGap?.afterId?.let { id -> scene.items.firstOrNull { it.cardId == id } }
                val x = right?.bounds?.x?.minus(2) ?: left?.bounds?.right?.plus(2) ?: item.bounds.center.x
                TableDragLanding(hand = item.bounds, insertionX = x.coerceIn(item.bounds.x + 2, item.bounds.right - 2))
            } else {
                val step = if (destination.zone == PlayZone.STACK) item.bounds.width * .12f else 4f
                val x = item.bounds.x + if (destination.zone == PlayZone.STACK) 2 * step else 8f
                val y = item.bounds.y + if (destination.zone == PlayZone.STACK) 2 * step else 8f
                val port = scene.viewports.firstOrNull { it.id == item.viewport }?.bounds ?: item.bounds
                TableDragLanding(ids.take(3).mapIndexedNotNull { n, _ -> TableBounds(x - n * step, y - n * step,
                    item.bounds.width, item.bounds.height).intersection(port) })
            }
        }
        is TableDestination.Combat -> {
            val target = destination.destination
            val region = scene.combatDrops.firstOrNull { it.destination == target } ?: scene.combatDrops.firstOrNull {
                it.destination.pairId == target.pairId && it.destination.role == target.role &&
                    (target.pairId == null && it.destination.pairGap == target.pairGap ||
                        target.pairId != null && it.destination.insertion == CombatInsertion.APPEND)
            } ?: scene.combatDrops.firstOrNull { it.destination.role == target.role } ?: return TableDragLanding()
            val margin = CombatStackPresentation.cardGap(scene.combatCardWidth)
            val sources = ids.map { scene.sources[it]?.sourceId ?: it }.toSet()
            val rows = scene.combatDrops.filter { it.destination.pairId != null &&
                it.destination.insertion == CombatInsertion.APPEND }
            val groups = rows.groupBy { it.destination.pairId }
            fun remaining(row: CombatDropRegion) = scene.items.filter { it.viewport == "combat" &&
                it.sourceId !in sources && row.bounds.contains(it.bounds.center) }.sortedBy { it.bounds.x }
            val moved = ids.distinctBy { scene.sources[it]?.sourceId ?: it }
            val pairIds = groups.keys.toList()
            val order = pairIds.toMutableList().apply { if (target.pairId == null) {
                val index = target.pairGap.beforeId?.let(::indexOf)?.takeIf { it >= 0 } ?: size
                add(index, null)
            } }
            fun cards(pair: String?, role: CombatRole) = groups[pair]?.firstOrNull { it.destination.role == role }
                ?.let(::remaining).orEmpty()
            fun count(pair: String?, role: CombatRole) = cards(pair, role).size +
                if (pair == target.pairId && role == target.role) moved.size else 0
            fun width(pair: String?): Float {
                val blockers = count(pair, CombatRole.BLOCKER); val attackers = count(pair, CombatRole.ATTACKER)
                return if (blockers + attackers == 0) 0f else CombatStackPresentation.slotWidth(blockers, attackers, scene.combatCardWidth)
            }
            // Recenter the final group after removing the dragged sources and inserting their new row.
            val center = if (rows.isEmpty()) region.landing.x else
                (rows.minOf { it.bounds.x } + rows.maxOf { it.bounds.right }) / 2
            val total = order.sumOf { width(it).toDouble() }.toFloat()
            val start = center - total / 2 + order.takeWhile { it != target.pairId }.sumOf { width(it).toDouble() }.toFloat()
            val remaining = cards(target.pairId, target.role)
            val index = if (target.insertion == CombatInsertion.APPEND) remaining.size else
                target.cardGap.beforeId?.let { id -> remaining.indexOfFirst { it.cardId == id }.takeIf { it >= 0 } } ?: remaining.size
            var x = start + (width(target.pairId) - CombatStackPresentation.rowWidth(count(target.pairId, target.role), scene.combatCardWidth)) / 2 +
                index * (scene.combatCardWidth + margin)
            val slots = moved.map {
                val b = TableBounds(x, region.landing.y - scene.combatCardHeight / 2, scene.combatCardWidth, scene.combatCardHeight)
                x += b.width + margin
                b
            }
            val port = scene.world?.viewport
            TableDragLanding(slots.mapNotNull { if (port == null) it else it.intersection(port) })
        }
    }
}
