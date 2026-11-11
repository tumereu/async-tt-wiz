package org.asyncttwiz.android

import org.asyncttwiz.appui.BackHandler
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.*
import org.asyncttwiz.protocol.*

/**
 * Inspect in a game (TBL-104): what holding a card (or an ability's entry on the Stack) shows while the finger stays: an
 * ability's tile alone (STK-050), else every face the viewer may see of the card (TBL-106) with what changed on it, then
 * the other members of its battlefield group held with it (TBL-111), each with its own changes. A face-down card stays
 * hidden from the opponent (TBL-109). [swipe]: its pinned mode, when the hold can pin it.
 */
@Composable
internal fun GameplayCardInspection(state: TableBoard, viewer: String, inspection: ManualInspection, close: () -> Unit,
    names: Map<String, String> = emptyMap(), scroll: ScrollState? = null, swipe: InspectSwipe? = null) {
    if (inspection.gameId != state.gameId || inspection.playId != state.playId) return
    BackHandler(onBack = { if (swipe?.pinned == true) swipe.close() else close() })
    val card = inspection.snapshot ?: CombatStackRules.inspection(state.board, inspection.cardId) ?: run {
        // A pinned Inspect whose card is gone closes
        if (swipe?.pinned == true) LaunchedEffect(swipe) { swipe.close() }
        return
    }
    val groups = remember(state, viewer, inspection, names) { gameInspectGroups(state, viewer, inspection, card, names) }
    InspectPopup(groups, scroll ?: rememberScrollState(), swipe)
}

/** The pictures [GameplayCardInspection] shows of [card], held as [inspection] says. */
internal fun gameInspectTiles(state: TableBoard, viewer: String, inspection: ManualInspection, card: PlayCard,
    names: Map<String, String>): List<InspectTile> = gameInspectGroups(state, viewer, inspection, card, names).flatMap { it.tiles }

/** What [GameplayCardInspection] shows of [card], held as [inspection] says: one group per card, with its changes. */
internal fun gameInspectGroups(state: TableBoard, viewer: String, inspection: ManualInspection, card: PlayCard,
    names: Map<String, String>): List<InspectGroup> {
    val entry = inspection.stackEntryId ?: inspection.cardId
    // STK-050 (owner playtest 2026-11-06): an ability on the Stack is its panel alone (the source's art crop, the
    // ability's text and what was chosen), not its source card's picture
    StackAbilities.of(state, entry, viewer)?.let { shown ->
        val definition = state.definitions.firstOrNull { it.digest == card.definition }?.definition?.takeIf { it.faces.isNotEmpty() }
        val face = definition?.faces?.indexOfFirst { it.faceId == card.faceId }?.takeIf { it >= 0 } ?: 0
        val art = definition?.let { InspectedCard.retained(it).artwork.getOrNull(face) }?.forTable()
        // (owner 2026-11-07) its text names its targets in place; the details list only the targets it doesn't name
        val text = StackAbilities.text(state, entry, viewer, names)
        return listOf(InspectGroup("ability/$entry", listOf(InspectTile.Ability("ability/$entry", shown,
            StackAbilities.details(state, entry, viewer, names, text), CombatStackRules.controller(state.board, entry) == viewer, art,
            text?.pieces?.tinted(viewer)))))
    }
    // TBL-111: holding a battlefield group shows each of its members in turn
    val below = if (inspection.snapshot == null) inspection.group.mapNotNull { id -> state.board.cards.firstOrNull { it.id == id } }
        else emptyList()
    val faceDown = CombatStackRules.faceDownEntry(state.board, inspection.cardId)
    val held = cardTiles(state, viewer, card, inspection, faceDown, inspection.cardId in CombatStackRules.projectionIds(state.board))
    val others = below.map { it to cardTiles(state, viewer, it, null, false, false) }
    val changes = InspectChanges.of(state, viewer, (listOf(card to held) + others).filter { (_, tiles) -> tiles.none { it is InspectTile.Covered } }
        .map { it.first }, names)
    // STK-050 (owner 2026-11-06): a spell on the Stack shows what was chosen for it (modes, X, targets), as abilities do
    val chosen = if (state.board.stackEffects.any { it.id == entry } && held.none { it is InspectTile.Covered })
        StackAbilities.details(state, entry, viewer, names).map { InspectChange(it) } else emptyList()
    return listOf(InspectGroup(card.id, held, chosen + changes[card.id].orEmpty())) +
        others.map { (member, tiles) -> InspectGroup(member.id, tiles, changes[member.id].orEmpty()) }
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
    val full = InspectedCard.retained(definition)
    val shown = InspectedCard(filtered, faces.map { face -> full.artwork[definition.faces.indexOf(face)] })
    // The holder's own face-down card (or face-down entry, STK-005) shows its front marked "Face down"
    val faceDown = ManualDisclosure.faceDownOutsideHiddenZones(state.board, card) || faceDownEntry
    return inspectFaces(shown, faces.indexOfFirst { it.faceId == card.faceId }, flippedOnTable(definition, card.faceId), card.id,
        if (card.owner == viewer) OwnTableColor else OpponentTableColor, card.token, projection, faceDown)
}
