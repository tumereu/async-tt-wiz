package org.asyncttwiz.protocol

data class StackTextOption(val text: String, val faceId: String, val paragraph: Int? = null)

/** Literal definition text only: no ability classification or game-rule interpretation. */
object StackText {
    fun options(definition: CardDefinition, faceId: String): List<StackTextOption> {
        val faces = if (definition.faces.size > 1) definition.faces else definition.faces.filter { it.faceId == faceId }
        val titles = if (definition.faces.size > 1) faces.map { StackTextOption(it.name, it.faceId) } else emptyList()
        return titles + faces.flatMap { face -> face.rulesText.lines().map(String::trim).filter(String::isNotEmpty)
            .mapIndexed { index, text -> StackTextOption(text, face.faceId, index) } }
    }
    fun options(state: PlayState, entryId: String): List<StackTextOption> {
        val card = CombatStackRules.inspection(state.board, entryId) ?: return emptyList()
        val definition = state.definitions.find { it.digest == card.definition }?.definition ?: return emptyList()
        return options(definition, card.faceId)
    }
}

/** Ordinary invocation constituents. Each request retains its own guards, outcome and Undo boundary. */
object StackQuickActions {
    fun actions(state: PlayState, entryId: String, zone: PlayZone): List<PlayAction> {
        require(zone in setOf(PlayZone.BATTLEFIELD, PlayZone.GRAVEYARD))
        require(entryId in CombatStackRules.stack(state.board).cards)
        val remove = if (state.board.stackEffects.any { it.id == entryId })
            listOf(PlayAction.Stack(StackAction.RemoveEffect(entryId))) else emptyList()
        val card = CombatStackRules.liveSource(state.board, entryId) ?: return remove
        val source = state.board.containers.single { it.id == card.containerId }
        if (zone == PlayZone.BATTLEFIELD && source.zone == PlayZone.BATTLEFIELD) return remove
        val target = state.board.containers.single { it.accountId == card.owner && it.zone == zone }
        // Coordinates run outwards-to-inwards for BOTH players. Row 0 is lands, row 1 the nearest main row.
        val destination = if (zone == PlayZone.BATTLEFIELD) {
            val rightmost = state.board.cards.filter { it.containerId == target.id && it.placement?.position?.y == 1 }
                .maxOfOrNull { it.placement!!.position.x } ?: -1
            MoveDestination(target.id, MoveInsertion.EMPTY, BoardPosition(rightmost + 1, 1))
        } else MoveDestination(target.id, MoveInsertion.UNORDERED)
        return remove + GameplayRules.moveActions(state, PlayAction.MoveCard(card.id,
            MoveSource(card.containerId, card.placement), destination))
    }
}
