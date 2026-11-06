package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.isNotEnabled
import androidx.compose.ui.test.performClick
import org.asyncttwiz.protocol.game.GameDevice
import org.junit.Test
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Owner gallery review, 2026-11-06: where assuming the other player's answer would show you hidden information or a random
 * result, the wait's Assume shows greyed, and holding it says why. Bob is away; Alice casts Ember Glance ("Look at target
 * player's hand.") at him: assuming his pass would show her his hand, so "Assume resolves" is greyed, a tap does nothing
 * and holding it explains; when Bob comes back and passes, it resolves.
 */
class InformationRuleAssumeTest : E2e() {
    private val why get() = "Assuming this would show you hidden cards or a random result: wait for ${bob.name}."

    // TRN-067, TRN-070, TRN-071: the greyed Assume, its tap refused, its hold's reason; the real pass goes on
    @Test fun assumeIsGreyedWhereItWouldShowHiddenCards() {
        startGame(TestDeck("Glances", listOf(TestCards.mountain to 20, TestCards.glance to 20)), deal = { a, _ -> "Ember Glance" in hand(a) })
        bothOpen()
        bothKeep(bobAway = true)
        alice.playFromHand("Ember Glance", this)
        waitUntil("Alice's target bar") { alice.shows(hasText("Choose target player") and isHeading()) }
        alice.node(hasContentDescription("${bob.name}, life 20", substring = true)).performClick()
        waitUntil("Alice waiting for Bob: going on would show hidden cards") {
            (alice.status as? GameDevice.Status.Waiting)?.reason == GameDevice.Wait.HiddenCards
        }
        val assume = hasTestTag("engine-wait-action") and hasContentDescription("Assume resolves")
        waitUntil("the greyed Assume on the main button") { alice.shows(assume and isNotEnabled()) }
        alice.node(assume).performClick()
        run(skip = Duration.ofSeconds(2))
        assertEquals(1, alice.state.stack.size, "nothing assumed: the Glance waits on the Stack")
        inputClockCaughtUp()
        var said = false
        holding(alice.node(assume)) { said = alice.showsText(why) }
        assertTrue(said, "holding it says why: ${alice.screen()}")

        bob.back()
        waitUntil("Bob's pass and the Glance resolved", along = listOf(bob)) {
            alice.state.stack.isEmpty() && alice.engine { device.session.pending.isEmpty() } &&
                alice.state.player(alice.me).graveyard.any { cardName(alice.state, it) == "Ember Glance" }
        }
    }
}
