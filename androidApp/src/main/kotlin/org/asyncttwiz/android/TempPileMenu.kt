package org.asyncttwiz.android

import org.asyncttwiz.protocol.*

/** Cards of [sourceIds] that can go into their owner's Temp pile [pile]: real cards, not already there. */
internal fun tempPileMovable(play: PlayState, sourceIds: List<String>, pile: Int): List<PlayCard> =
    sourceIds.distinct().mapNotNull { id -> play.board.cards.singleOrNull { it.id == id } }.filter { card ->
        !card.token && play.board.containers.none { it.id == card.containerId && it.temp && it.tempNumber == pile }
    }

/** "Temp 1".."Temp 4": always all four; each moves the cards into their owners' pile of that number,
 * creating (turning on) the pile if needed. Disabled only when no source card can move there. */
internal fun tempPileTools(toolkit: TableToolkitController, play: PlayState, sourceIds: List<String>, enabled: Boolean): List<MenuControl> =
    TempPiles.NUMBERS.map { pile ->
        MenuControl("Temp $pile", enabled && tempPileMovable(play, sourceIds, pile).isNotEmpty()) { toolkit.toTemp(sourceIds, pile) }
    }
