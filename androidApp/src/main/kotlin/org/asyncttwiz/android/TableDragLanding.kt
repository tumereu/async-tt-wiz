package org.asyncttwiz.android

import org.asyncttwiz.protocol.PlayZone
import org.asyncttwiz.protocol.CombatInsertion
import org.asyncttwiz.protocol.CombatRole

data class TableDragLanding(val outlines: List<TableBounds> = emptyList(),
    val hand: TableBounds? = null, val insertionX: Float? = null,
    val additionalHands: List<Pair<TableBounds, Float?>> = emptyList(),
    val combatInsertion: Pair<TablePoint, TablePoint>? = null)

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
    val zoom = scene.camera.zoom
    val fieldScale = scene.cardWidth / 28f
    val ids = scope.cardIds
    fun outline(id: String, x: Float, y: Float): TableBounds {
        val item = scene.items.firstOrNull { it.cardId == id }
        val source = if (item?.viewport == "combat") scene.items.firstOrNull { it.cardId == item.sourceId && !it.projection } else item
        val tapped = source?.tapped == true
        return TableBounds(x, y, if (tapped) scene.cardHeight else scene.cardWidth,
            if (tapped) scene.cardWidth else scene.cardHeight)
    }
    return when (destination) {
        is TableDestination.Grid -> {
            val field = scene.fields.firstOrNull { it.containerId == destination.containerId } ?: return TableDragLanding()
            val context = scene.sources[scope.contextId]?.placement
            val positions = ids.mapIndexed { index, id ->
                val old = scene.sources[id]?.placement
                id to if (context != null && old != null) TablePoint(destination.column + old.column - context.column,
                    destination.row + old.row - context.row) else TablePoint((destination.column + index).toFloat(), destination.row.toFloat())
            }
            if (positions.groupBy { it.second }.any { (p, members) -> members.size > 3 || scene.items.any {
                it.cardId !in ids && it.viewport == field.containerId && scene.sources[it.cardId]?.placement?.let { old ->
                    old.column == p.x && old.row == p.y } == true } }) return TableDragLanding()
            val slots = positions.groupBy { it.second }.flatMap { (p, members) -> members.mapIndexed { n, (id, _) ->
                val x = field.originX + p.x * scene.columnPitch + 16 * fieldScale
                val dy = p.y * field.rowHeight
                val rowY = if (field == scene.fields.last()) field.gridBounds.bottom - field.rowHeight - dy else field.gridBounds.y + dy
                val baseY = rowY + (field.rowHeight - scene.footprint) / 2
                val b = outline(id, 0f, 0f)
                battlefieldCardBounds(TableBounds(x, baseY, scene.columnPitch, scene.footprint), b.width, b.height, n, fieldScale)
                    .intersection(field.bounds)
            } }.filterNotNull()
            TableDragLanding(slots)
        }
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
        is TableDestination.CardPile -> {
            val target = scene.items.firstOrNull { it.cardId == destination.cardId } ?: return TableDragLanding()
            TableDragLanding(ids.mapIndexed { n, id ->
                val b = outline(id, 0f, 0f)
                battlefieldCardBounds(target.footprint, b.width, b.height, n, fieldScale)
            })
        }
        is TableDestination.Combat -> {
            val target = destination.destination
            val region = scene.combatDrops.firstOrNull { it.destination == target } ?: scene.combatDrops.firstOrNull {
                it.destination.pairId == target.pairId && it.destination.role == target.role &&
                    (target.pairId == null && it.destination.pairGap == target.pairGap ||
                        target.pairId != null && it.destination.insertion == CombatInsertion.APPEND)
            } ?: scene.combatDrops.firstOrNull { it.destination.role == target.role } ?: return TableDragLanding()
            val margin = CombatStackPresentation.cardGap(scene.cardWidth)
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
                return if (blockers + attackers == 0) 0f else CombatStackPresentation.slotWidth(blockers, attackers, scene.cardWidth)
            }
            // Recenter the final group after removing the dragged sources and inserting their new row.
            val center = if (rows.isEmpty()) region.landing.x else
                (rows.minOf { it.bounds.x } + rows.maxOf { it.bounds.right }) / 2
            val total = order.sumOf { width(it).toDouble() }.toFloat()
            val start = center - total / 2 + order.takeWhile { it != target.pairId }.sumOf { width(it).toDouble() }.toFloat()
            val remaining = cards(target.pairId, target.role)
            val index = if (target.insertion == CombatInsertion.APPEND) remaining.size else
                target.cardGap.beforeId?.let { id -> remaining.indexOfFirst { it.cardId == id }.takeIf { it >= 0 } } ?: remaining.size
            var x = start + (width(target.pairId) - CombatStackPresentation.rowWidth(count(target.pairId, target.role), scene.cardWidth)) / 2 +
                index * (scene.cardWidth + margin)
            val firstX = x
            val slots = moved.map {
                val b = TableBounds(x, region.landing.y - scene.cardHeight / 2, scene.cardWidth, scene.cardHeight)
                x += b.width + margin
                b
            }
            val port = scene.world?.viewport
            val indicatorX = firstX - if (target.pairId == null) CombatStackPresentation.slotMargin(scene.cardWidth) else margin / 2
            val indicator = TablePoint(indicatorX, region.landing.y - scene.cardHeight * .3f) to
                TablePoint(indicatorX, region.landing.y + scene.cardHeight * .3f)
            TableDragLanding(slots.mapNotNull { if (port == null) it else it.intersection(port) }, combatInsertion = indicator)
        }
    }
}
