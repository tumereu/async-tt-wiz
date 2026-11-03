package org.asyncttwiz.android

import org.asyncttwiz.protocol.PlayZone

/** Graveyard and Exile: public piles whose tap opens a pile menu (Search) and whose double-tap opens the search panel. */
internal object PublicPiles {
    val zones = setOf(PlayZone.GRAVEYARD, PlayZone.EXILE)
}
