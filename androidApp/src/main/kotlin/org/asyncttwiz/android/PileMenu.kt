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
