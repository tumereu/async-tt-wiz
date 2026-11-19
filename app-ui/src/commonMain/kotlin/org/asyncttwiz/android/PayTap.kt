package org.asyncttwiz.android

import arcanum.engine.decision.ManaUse
import org.asyncttwiz.automatum.ManaWays

/**
 * STK-024, STK-049 (review 147a9c5af6): one tap on an open payment, as the player made it: kept while the tap before it is still
 * worked out and taken once that one is done, on the payment shown then (never a tap that silently does nothing).
 */
sealed interface PayTap {
    /** What was tapped, for the app log. */
    val what: String
    /** The table card that shakes when the tap no longer pays the payment (TBL-118); null for a chip in the panel. */
    val card: String?

    /** The battlefield card [cardId] tapped as a source. */
    data class Source(val cardId: String) : PayTap {
        override val what get() = "source $cardId"
        override val card get() = cardId
    }

    /** A chip in the panel or bar (pool mana, life, a source used with other objects) for the uses [group]. */
    data class Chip(val group: List<ManaUse>) : PayTap {
        override val what get() = "chip ${ManaWays.label(group.first())}"
        override val card: String? get() = null
    }

    /** [way] chosen with the corner chip of the table card [cardId]. */
    data class Way(val cardId: String, val way: ManaUse) : PayTap {
        override val what get() = "way ${ManaWays.label(way)} of $cardId"
        override val card get() = cardId
    }
}
