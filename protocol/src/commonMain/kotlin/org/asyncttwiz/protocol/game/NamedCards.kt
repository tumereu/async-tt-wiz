package org.asyncttwiz.protocol.game

import arcanum.api.CardDefinition

/**
 * A card's engine definition by its name, for "create a copy of the card with the chosen name" (CR 707.12): the engine has no
 * cards of its own and asks the host ([arcanum.engine.decision.Decision.NamedCard]); the player's device answers it from its
 * card records, the card built as a deck's cards are. Null when there is no such card (the records not installed).
 */
fun interface NamedCards {
    fun definition(name: String): CardDefinition?

    companion object {
        /** No card records: nothing is found. */
        val NONE: NamedCards = NamedCards { null }
    }
}
