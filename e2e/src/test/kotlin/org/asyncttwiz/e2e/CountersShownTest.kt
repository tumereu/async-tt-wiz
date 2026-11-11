package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasContentDescription
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Owner playtest, 2026-11-11: a planeswalker's loyalty showed only as a counter text too long for its number to be seen. Now a
 * permanent without P/T shows its primary counter as its corner number and other counters show as bubbles on the art, the
 * words in Inspect. Alice casts an Ember Sage (a planeswalker, loyalty 3) and a Charge Stone (enters with a charge counter):
 * both phones read their counters, and holding each lists them in words.
 */
class CountersShownTest : E2e() {
    private val deck = TestDeck("Counters", listOf(TestCards.mountain to 14, TestCards.sage to 13, TestCards.chargeStone to 13))

    // TBL-021, TBL-135, TBL-104, TBL-113 (owner, 2026-11-11)
    @Test fun countersShowOnThePermanentsAndInInspect() {
        startGame(deck, deck, deal = { a, _ -> "Ember Sage" in hand(a) && "Charge Stone" in hand(a) })
        bothOpen()
        bothKeep(bobAway = false)
        alice.playFromHand("Ember Sage", this)
        waitUntil("the Sage", along = listOf(bob)) { alice.field("Ember Sage").isNotEmpty() && alice.inOwnMain() }
        alice.playFromHand("Charge Stone", this)
        waitUntil("the Stone", along = listOf(bob)) { alice.field("Charge Stone").isNotEmpty() && alice.inOwnMain() }
        val sage = hasContentDescription("Ember Sage, owner ${alice.name}, ", substring = true) and
            hasContentDescription(", loyalty counter ×3", substring = true)
        val stone = hasContentDescription("Charge Stone, owner ${alice.name}, ", substring = true) and
            hasContentDescription(", charge counter", substring = true)
        // Its loyalty is its corner number, the Stone's charge counter a bubble; both read in words
        waitUntil("Alice's table reads the counters") { alice.shows(sage) && alice.shows(stone) }
        waitUntil("Bob's table reads them too", along = listOf(alice)) { bob.shows(sage) && bob.shows(stone) }
        holding(alice.battlefieldCard("Ember Sage")) {
            assertTrue(alice.showsText("loyalty counter ×3"), "Inspect lists the loyalty: ${alice.screen()}")
        }
        holding(alice.battlefieldCard("Charge Stone")) {
            assertTrue(alice.showsText("charge counter"), "Inspect lists the charge counter: ${alice.screen()}")
        }
        assertFalse(alice.refusalSeen || bob.refusalSeen, "no refusal")
    }
}
