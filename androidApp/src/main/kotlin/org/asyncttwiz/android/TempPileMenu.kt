package org.asyncttwiz.android

import org.asyncttwiz.protocol.*

/** The four Temp pile cells of the card layouts. Only Temp 1 exists for now; the others are disabled stubs. */
internal fun tempPileTools(toolkit: TableToolkitController, play: PlayState, sourceIds: List<String>, enabled: Boolean): List<MenuControl> {
    val movable = sourceIds.any { id -> play.board.cards.any { card -> card.id == id && !card.token &&
        play.board.containers.none { it.id == card.containerId && it.temp } } }
    return listOf(MenuControl("Temp 1", enabled && movable) { toolkit.toTemp(sourceIds) }) +
        (2..4).map { MenuControl("Temp $it", false) {} }
}
