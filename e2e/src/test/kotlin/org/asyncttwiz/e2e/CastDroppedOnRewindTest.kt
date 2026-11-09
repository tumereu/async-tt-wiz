package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performClick
import org.junit.Test
import java.time.Duration
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Owner problem report RC3J4K (beta11): "cast panel did not disappear when i accepted opponent undo". Alice had begun a
 * cast when Bob asked to undo; her Accept took the table back behind where the cast began, and the cast stayed open on the
 * rewound board until a later board dropped it.
 */
class CastDroppedOnRewindTest : E2e() {
    private val deck = TestDeck("Flicks", listOf(TestCards.mountain to 20, TestCards.flick to 20))
    private val targeting = hasContentDescription("Choose targets")

    // STK-055, STK-025, HIS-099 (problem report RC3J4K): Alice plays a Mountain, then begins casting Ember Flick (its target
    // bar open); Bob asks to go back to before her Mountain and she accepts: the table goes back and her open cast is dropped
    // at once, though Ember Flick could be cast there again
    @Test fun acceptingTheOtherPlayersUndoDropsTheOpenCast() {
        startGame(deck, deck, deal = { a, _ -> "Mountain" in hand(a) && "Ember Flick" in hand(a) })
        bothOpen()
        bothKeep(bobAway = false)
        val before = alice.engine { device.session.tip }
        alice.playFromHand("Mountain", this)
        waitUntil("Alice's Mountain on the server", along = listOf(bob)) {
            alice.field("Mountain").isNotEmpty() && quiet() && alice.inOwnMain()
        }
        alice.playFromHand("Ember Flick", this)
        waitUntil("Alice's cast of Ember Flick open, asking for its target") { alice.shows(targeting) }
        bob.game!!.requestUndo(before, "")
        waitUntil("Alice asked to accept the undo") { alice.shows(hasTestTag("request-accept")) }
        alice.node(hasTestTag("request-accept")).performClick()
        waitUntil("Alice's table back before her Mountain") {
            alice.engine { device.session.tip == before } && alice.hand("Mountain").isNotEmpty() && alice.field("Mountain").isEmpty()
        }
        run(skip = Duration.ofMillis(500))
        assertFalse(alice.shows(targeting), "the open cast is dropped: ${alice.screen()}")
        assertTrue(alice.hand("Ember Flick").isNotEmpty(), "Ember Flick still in her hand")
    }
}
