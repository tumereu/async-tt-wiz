package org.asyncttwiz.android

import org.asyncttwiz.protocol.PlayZone

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
    val zoom = scene.camera.zoom
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
                val x = field.gridBounds.x + p.x * TablePresentation.GRID * zoom + 16 * zoom
                val dy = p.y * field.rowHeight
                val rowY = if (field == scene.fields.last()) field.gridBounds.bottom - field.rowHeight - dy else field.gridBounds.y + dy
                val baseY = rowY + (field.rowHeight - scene.footprint) / 2
                val b = outline(id, 0f, 0f)
                battlefieldCardBounds(TableBounds(x, baseY, scene.footprint, scene.footprint), b.width, b.height, n, zoom)
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
                val step = if (destination.zone == PlayZone.STACK) 28f * .12f else 4f
                val x = item.bounds.x + if (destination.zone == PlayZone.STACK) 2 * step else 8f
                val y = item.bounds.y + if (destination.zone == PlayZone.STACK) 2 * step else 8f
                val port = scene.viewports.firstOrNull { it.id == item.viewport }?.bounds ?: item.bounds
                TableDragLanding(ids.take(3).mapIndexedNotNull { n, _ -> TableBounds(x - n * step, y - n * step,
                    28f, 40f).intersection(port) })
            }
        }
        is TableDestination.CardPile -> {
            val target = scene.items.firstOrNull { it.cardId == destination.cardId } ?: return TableDragLanding()
            TableDragLanding(ids.mapIndexed { n, id ->
                val b = outline(id, 0f, 0f)
                battlefieldCardBounds(target.footprint, b.width, b.height, n, zoom)
            })
        }
        is TableDestination.Combat -> {
            val region = scene.combatDrops.firstOrNull { it.destination == destination.destination } ?: return TableDragLanding()
            val margin = scene.cardWidth * .04f
            val sources = ids.map { scene.sources[it]?.sourceId ?: it }.toSet()
            val rows = scene.combatDrops.filter { it.destination.pairId != null &&
                it.destination.insertion == org.asyncttwiz.protocol.CombatInsertion.APPEND }
            val groups = rows.groupBy { it.destination.pairId }
            fun remaining(row: CombatDropRegion) = scene.items.filter { it.viewport == "combat" &&
                it.sourceId !in sources && row.bounds.contains(it.bounds.center) }.sortedBy { it.bounds.x }
            fun width(rows: List<CombatDropRegion>): Float {
                val count = rows.maxOf { remaining(it).size }
                return if (count == 0) 0f else count * (scene.cardWidth + margin) + margin
            }
            val target = destination.destination
            val pairIds = groups.keys.toList()
            val index = target.pairId?.let(pairIds::indexOf) ?: target.pairGap.beforeId?.let(pairIds::indexOf) ?: pairIds.size
            // Repack after removing the dragged sources, including groups that disappear entirely.
            val start = rows.firstOrNull()?.bounds?.x ?: (region.landing.x - margin)
            var x = start + groups.values.take(index.coerceAtLeast(0)).sumOf { width(it).toDouble() }.toFloat() + margin
            if (target.pairId != null) {
                val row = rows.first { it.destination.pairId == target.pairId && it.destination.role == target.role }
                val cards = remaining(row)
                val before = target.cardGap.beforeId?.let { id -> cards.indexOfFirst { it.cardId == id }.takeIf { it >= 0 } }
                val count = if (target.insertion == org.asyncttwiz.protocol.CombatInsertion.APPEND) cards.size else before ?: cards.size
                x += count * (scene.cardWidth + margin)
            }
            val slots = ids.distinctBy { scene.sources[it]?.sourceId ?: it }.map {
                val b = TableBounds(x, region.landing.y - scene.cardHeight / 2, scene.cardWidth, scene.cardHeight)
                x += b.width + margin
                b
            }
            val port = scene.world?.viewport
            TableDragLanding(slots.mapNotNull { if (port == null) it else it.intersection(port) })
        }
    }
}
