package org.asyncttwiz.android

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.*
import org.asyncttwiz.protocol.*

/**
 * Inspect in a game (TBL-104): what holding a card (or an ability's entry on the Stack) shows while the finger stays, only
 * pictures: an ability's tile first (STK-046), then every face the viewer may see of the card (TBL-106), then each card
 * under it in its battlefield pile (TBL-111). A face-down card stays hidden from the opponent (TBL-109).
 */
@Composable
internal fun GameplayCardInspection(state: TableBoard, viewer: String, inspection: ManualInspection, close: () -> Unit,
    names: Map<String, String> = emptyMap(), scroll: ScrollState? = null) {
    if (inspection.gameId != state.gameId || inspection.playId != state.playId) return
    BackHandler(onBack = close)
    val card = inspection.snapshot ?: CombatStackRules.inspection(state.board, inspection.cardId) ?: return
    val tiles = remember(state, viewer, inspection, names) { gameInspectTiles(state, viewer, inspection, card, names) }
    InspectPopup(tiles, scroll ?: rememberScrollState())
}

/** The pictures [GameplayCardInspection] shows of [card], held as [inspection] says. */
internal fun gameInspectTiles(state: TableBoard, viewer: String, inspection: ManualInspection, card: PlayCard,
    names: Map<String, String>): List<InspectTile> {
    val entry = inspection.stackEntryId ?: inspection.cardId
    // STK-050: an ability on the Stack is its panel first (the source's art, the ability's text), then its source card
    val ability = StackAbilities.of(state, entry, viewer)?.let { shown ->
        val definition = state.definitions.firstOrNull { it.digest == card.definition }?.definition?.takeIf { it.faces.isNotEmpty() }
        val face = definition?.faces?.indexOfFirst { it.faceId == card.faceId }?.takeIf { it >= 0 } ?: 0
        val art = definition?.let { InspectedCard.retained(card.definition, it).artwork.getOrNull(face) }
        InspectTile.Ability("ability/$entry", shown, StackAbilities.details(state, entry, viewer, names),
            CombatStackRules.controller(state.board, entry) == viewer, art)
    }
    // TBL-111: holding a battlefield card that lies on others shows it and every card under it
    val below = if (inspection.snapshot == null) battlefieldPileBelow(state.board, inspection.cardId) else emptyList()
    val faceDown = CombatStackRules.faceDownEntry(state.board, inspection.cardId)
    return listOfNotNull(ability) + cardTiles(state, viewer, card, inspection, faceDown, inspection.cardId in CombatStackRules.projectionIds(state.board)) +
        below.flatMap { cardTiles(state, viewer, it, null, false, false) }
}

/** [card]'s faces as its inspection shows them to [viewer], or one covered tile when they may see none of it. */
private fun cardTiles(state: TableBoard, viewer: String, card: PlayCard, inspection: ManualInspection?, faceDownEntry: Boolean,
    projection: Boolean): List<InspectTile> {
    val definition = state.definitions.singleOrNull { it.digest == card.definition }?.definition
        ?: return listOf(InspectTile.Covered(card.id, "Card data unavailable"))
    // Only a deliberate inspection of a choice's card ([ManualInspection.explicit]) names its faces itself; an ordinary one
    // never shows an opponent a face-down front
    val allowed = if (inspection?.explicit == true) inspection.faceIds
        else if (ManualDisclosure.faceDownFrontHidden(state.board, card, viewer)) emptyList()
        else ManualDisclosure.permittedFaces(state.board, state.definitions, card, viewer, inspecting = true).map { it.faceId }
            .filter { inspection == null || it in inspection.faceIds }
    val faces = definition.faces.filter { it.faceId in allowed }
    if (faces.isEmpty()) return listOf(InspectTile.Covered(card.id, "Covered card"))
    // The full name only when every face is permitted: a partial view never names a face it hides.
    val filtered = definition.copy(name = if (faces.size == definition.faces.size) definition.name else faces.first().name, faces = faces)
    val full = InspectedCard.retained(card.definition, definition)
    val shown = InspectedCard(filtered, faces.map { face -> full.artwork[definition.faces.indexOf(face)] })
    // The holder's own face-down card (or face-down entry, STK-005) shows its front marked "Face down"
    val faceDown = ManualDisclosure.faceDownOutsideHiddenZones(state.board, card) || faceDownEntry
    return inspectFaces(shown, faces.indexOfFirst { it.faceId == card.faceId }, flippedOnTable(definition, card.faceId), card.id,
        if (card.owner == viewer) OwnTableColor else OpponentTableColor, card.token, projection, faceDown)
}

/** TBL-111: the cards under [cardId] in its battlefield pile (TBL-053), top first; empty when nothing lies under it. */
internal fun battlefieldPileBelow(board: BoardState, cardId: String): List<PlayCard> {
    val card = board.cards.singleOrNull { it.id == cardId } ?: return emptyList()
    val placement = card.placement ?: return emptyList()
    val field = board.containers.singleOrNull { it.id == card.containerId && it.zone == PlayZone.BATTLEFIELD } ?: return emptyList()
    val byId = board.cards.associateBy { it.id }
    // The container order is the pile order, top first (TablePresentation.logicalPlacements).
    val pile = field.cards.mapNotNull { byId[it] }.filter { it.placement == placement }
    return pile.drop(pile.indexOfFirst { it.id == cardId } + 1)
}
