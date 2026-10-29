package org.asyncttwiz.android

import org.asyncttwiz.protocol.*

internal val ownerOnlyZones = setOf(PlayZone.HAND, PlayZone.LIBRARY, PlayZone.GRAVEYARD, PlayZone.EXILE)

internal fun moveZoneLabel(zone: PlayZone, bottom: Boolean = false): String = when (zone) {
    PlayZone.HAND -> "Hand"
    PlayZone.BATTLEFIELD -> "Battlefield"
    PlayZone.LIBRARY -> if (bottom) "Library bottom" else "Library top"
    PlayZone.GRAVEYARD -> "Graveyard"
    PlayZone.EXILE -> "Exile"
    PlayZone.STACK -> "Stack"
}

/** Client input policy only; historical moves remain valid in the shared processor. */
internal fun ownerMoveDestination(board: BoardState, id: String, destination: TableDestination, playId: String? = null): TableDestination {
    val owner = CombatStackRules.liveSource(board, id)?.owner ?: return destination
    return when (destination) {
        is TableDestination.Container -> if (destination.zone in ownerOnlyZones) {
            // A Temp pile redirects to the owner's Temp pile of the same number, which may not have been created yet.
            val temp = board.containers.firstOrNull { it.id == destination.id && it.temp }
            val target = if (temp != null) TempPiles.container(board, owner, temp.tempNumber)?.id
                    ?: playId?.takeIf(DeckValidation::uuid)?.let { TempPiles.containerId(it, owner, temp.tempNumber) } ?: return destination
                else board.zoneContainer(owner, destination.zone)?.id ?: return destination
            if (target == destination.id) destination else destination.copy(id = target, handGap = null)
        } else destination
        is TableDestination.Grid -> if (destination.ownerOnly) {
            val target = board.containers.singleOrNull { it.zone == PlayZone.BATTLEFIELD && it.accountId == owner } ?: return destination
            if (target.id == destination.containerId) destination else {
                val occupied = board.cards.filter { it.containerId == target.id }.mapNotNull { it.placement?.position }.toSet()
                destination.copy(containerId = target.id, column = generateSequence(0) { it + 1 }.first { BoardPosition(it, 0) !in occupied }, row = 0)
            }
        } else destination
        else -> destination
    }
}

internal fun ownerMoveDestination(board: BoardState, id: String, destination: MoveDestination, playId: String? = null): MoveDestination {
    val zone = board.containers.singleOrNull { it.id == destination.containerId }?.zone ?: return destination
    if (zone !in ownerOnlyZones) return destination
    val redirected = ownerMoveDestination(board, id, TableDestination.Container(destination.containerId, zone), playId) as TableDestination.Container
    return if (redirected.id == destination.containerId) destination else MoveDestination(redirected.id,
        if (zone == PlayZone.LIBRARY) {
            if (destination.insertion == MoveInsertion.BOTTOM) MoveInsertion.BOTTOM else MoveInsertion.TOP
        } else MoveInsertion.UNORDERED)
}

/** The scene carries owners even for a captured Library top that has no individual drawing. */
internal fun TableScene.ownerMoveDestination(id: String, destination: TableDestination): TableDestination {
    if (destination !is TableDestination.Container || destination.zone !in ownerOnlyZones) return destination
    val owner = cardOwners[id] ?: items.firstOrNull { it.cardId == id }?.owner ?: return destination
    val pile = tempPiles.values.firstNotNullOfOrNull { ids -> ids.indexOf(destination.id).takeIf { it >= 0 } }
    if (pile != null) {
        val target = tempPiles[owner]?.getOrNull(pile) ?: return destination
        return if (target == destination.id) destination else destination.copy(id = target, handGap = null)
    }
    val target = items.firstOrNull { it.zone == destination.zone && it.owner == owner && !it.temp &&
        it.kind in listOf(TableItemKind.HAND, TableItemKind.PILE) } ?: return destination
    return if (target.id == destination.id) destination else destination.copy(id = target.id, handGap = null)
}
