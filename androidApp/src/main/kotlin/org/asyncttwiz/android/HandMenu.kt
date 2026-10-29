package org.asyncttwiz.android

import org.asyncttwiz.protocol.*

internal fun handMenuTools(toolkit: TableToolkitController, play: PlayState, bound: ToolkitCapture,
    selection: TableSelection, names: Map<String, String>, enabled: Boolean): List<MenuControl> {
    val hand = play.board.containers.singleOrNull { it.zone == PlayZone.HAND && it.accountId == toolkit.viewer &&
        play.board.cards.any { card -> card.id == bound.contextViewId && card.containerId == it.id } } ?: return emptyList()
    val ids = bound.sourceIds.filter { it in hand.cards }
    val scope = TableScope(bound.contextViewId, ids)
    val moves = basicMoveTools(play, names, enabled, scope, bound.sources.filterKeys { it in ids }, toolkit::handle, toolkit::dismiss)
    return listOf(MenuControl("Play", enabled) { if (toolkit.playHand(ids)) toolkit.dismiss() },
        MenuControl("Face down", enabled) { if (toolkit.castFaceDown(ids)) toolkit.dismiss() },
        MenuControl("Reveal", enabled && play.board.contentsDisplays.isEmpty()) {
            if (toolkit.submit(PlayAction.RevealHand(hand.id, ids))) toolkit.dismiss()
        }) + moves.filter { it.name in listOf("Battlefield", "Graveyard", "Exile", "Library top", "Library bottom", "Shuffle into library") }
        .map { if (it.name == "Graveyard") it.copy(name = "Discard") else it } +
        tempPileTools(toolkit, play, ids, enabled) +
        HandRandomResult.entries.map { result ->
            val label = when (result) {
                HandRandomResult.DISCARD -> "Discard random"
                HandRandomResult.REVEAL -> "Reveal random"
                HandRandomResult.EXILE -> "Exile random"
                HandRandomResult.LIBRARY_BOTTOM -> "Random to library bottom"
            }
            MenuControl(label, enabled && (result != HandRandomResult.REVEAL || play.board.contentsDisplays.isEmpty())) {
                if (toolkit.randomHand(hand.id, selection.ids.filter { it in hand.cards }, result)) toolkit.dismiss()
            }
        }
}

/** Same destination and auto-resolution path as a hand double tap, evaluated per card in the batch. */
internal fun handPlayDestination(play: PlayState, card: PlayCard): TableDestination? {
    val hand = play.board.containers.singleOrNull { it.id == card.containerId && it.zone == PlayZone.HAND } ?: return null
    val front = play.definitions.singleOrNull { it.digest == card.definition }?.definition?.faces?.firstOrNull()
    if ("Land" !in StackContexts.types(front?.typeLine.orEmpty()))
        return TableDestination.Container(CombatStackRules.stack(play.board).id, PlayZone.STACK)
    val field = play.board.containers.singleOrNull { it.zone == PlayZone.BATTLEFIELD && it.accountId == hand.accountId } ?: return null
    val occupied = play.board.cards.filter { it.containerId == field.id }.mapNotNull { it.placement?.position }.toSet()
    val column = generateSequence(0) { it + 1 }.first { BoardPosition(it, 0) !in occupied }
    return TableDestination.Grid(field.id, column, 0)
}
