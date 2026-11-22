package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasContentDescription
import org.junit.Test

/**
 * Owner playtest, 2026-11-22 (TBL-239, TBL-233): hand cards show the battlefield's bottom row, their type icons and the P/T
 * Automatum works out, and read them aloud.
 */
class HandCardTypesTest : E2e() {
    // TBL-239, TBL-233: Alice's hand cards read their types and a creature's P/T aloud; Bob's table shows none of her hand
    @Test fun handCardsShowTheirTypesAndPowerAndToughness() {
        startGame(deal = { a, _ -> "Ember Cub" in hand(a) && "Storm Spark" in hand(a) && "Mountain" in hand(a) })
        bothOpen()
        waitUntil("Alice's hand cards with their types and P/T") {
            alice.shows(hasContentDescription("Ember Cub, owner ${alice.name}, untapped, Creature, 2/1")) &&
                alice.shows(hasContentDescription("Storm Spark, owner ${alice.name}, untapped, Instant")) &&
                alice.shows(hasContentDescription("Mountain, owner ${alice.name}, untapped, Land"))
        }
        kotlin.test.assertFalse(bob.shows(hasContentDescription("Ember Cub, owner ${alice.name}", substring = true)), "her hand is hidden from Bob")
    }
}
