package org.asyncttwiz.android

import org.asyncttwiz.protocol.PlayZone

internal data class StackRemovalFlight(val entry: TableItem, val destination: TableItem)

/** Compare only the two already-materialized scenes, never the history tree. A plain effect
 * removal still uses the existing return-home animation; this plan is for accompanying moves. */
internal fun stackRemovalFlights(from: TableScene, to: TableScene): List<StackRemovalFlight> {
    val remaining = to.items.firstOrNull { it.zone == PlayZone.STACK && it.kind == TableItemKind.PILE }?.cards.orEmpty().toSet()
    val destinations = buildMap {
        for (item in to.items) if (!item.projection && item.zone in setOf(PlayZone.GRAVEYARD, PlayZone.BATTLEFIELD, PlayZone.EXILE)) {
            if (item.kind == TableItemKind.PILE || item.copies > 1) for (id in item.cards) put(id, item)
            if (item.kind == TableItemKind.CARD) item.cardId?.let { put(it, item) }
        }
    }
    return (from.stackProjections + from.items.filter { it.zone == PlayZone.STACK && it.kind == TableItemKind.CARD })
        .distinctBy { it.animationId }.mapNotNull { entry ->
            val source = entry.sourceId ?: return@mapNotNull null
            if (entry.cardId in remaining || from.sources[source]?.containerId == to.sources[source]?.containerId)
                return@mapNotNull null
            destinations[source]?.let { StackRemovalFlight(entry, it) }
        }
}
