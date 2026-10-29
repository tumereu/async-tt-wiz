package org.asyncttwiz.android

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import org.asyncttwiz.protocol.*

/** Graveyard and Exile: public piles whose tap opens a pile menu and whose double-tap opens the search panel. */
internal object PublicPiles {
    val zones = setOf(PlayZone.GRAVEYARD, PlayZone.EXILE)
    /** Real cards of [ids] that can go into [zone]: tokens never enter a Hand or Library. */
    fun movable(board: BoardState, ids: List<String>, zone: PlayZone): List<String> = ids.distinct().filter { id ->
        board.cards.any { it.id == id && !(it.token && zone in setOf(PlayZone.HAND, PlayZone.LIBRARY)) }
    }
}

/** The pile menu of a Graveyard or Exile [pile] (either player's). Every button is always shown, disabled
 * when it does not apply. Each mass move is one invocation into the cards' owners' zones; no "All to hand". */
@Composable
internal fun PublicPileActions(toolkit: TableToolkitController, play: PlayState, pile: PlayContainer) {
    val enabled = toolkit.enabled()
    val cards = pile.cards
    val toLibrary = enabled && PublicPiles.movable(play.board, cards, PlayZone.LIBRARY).isNotEmpty()
    val other = if (pile.zone == PlayZone.GRAVEYARD) PlayZone.EXILE else PlayZone.GRAVEYARD
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            // Looking is personal: also available while reviewing or offline.
            InlinePopupCell(MenuControl("Search", cards.isNotEmpty()) { toolkit.openPublicPile(pile.id, toggle = false) })
            InlinePopupCell(MenuControl("All to library top", toLibrary) { toolkit.pileToZone(pile.id, PlayZone.LIBRARY) },
                label = "All top")
            InlinePopupCell(MenuControl("All to library bottom", toLibrary) { toolkit.pileToZone(pile.id, PlayZone.LIBRARY, bottom = true) },
                label = "All bottom")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            InlinePopupCell(MenuControl("Shuffle all into library", toLibrary) {
                toolkit.pileToZone(pile.id, PlayZone.LIBRARY, shuffle = true)
            }, label = "Shuffle\nall")
            InlinePopupCell(MenuControl("All to ${moveZoneLabel(other).lowercase()}", enabled && cards.isNotEmpty()) {
                toolkit.pileToZone(pile.id, other)
            }, label = "All →\n${moveZoneLabel(other)}")
            InlinePopupCell(MenuControl("All to Temp 1", enabled && tempPileMovable(play, cards, 1).isNotEmpty()) {
                toolkit.toTemp(cards, 1)
            }, label = "All →\nTemp 1")
        }
    }
}
