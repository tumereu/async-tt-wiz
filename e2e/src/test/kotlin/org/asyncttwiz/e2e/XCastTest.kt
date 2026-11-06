package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.isNotEnabled
import androidx.compose.ui.test.performClick
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Owner playtest 2026-11-05 evening: an X cast has no separate OK before Cast, X can't be set past what the mana pays, and
 * the cast panel has no Cancel. Alice plays a Mountain and casts Ember Burst ({X}: X damage to any target): X goes from 0
 * to 1 and no further, Cast goes on to the target, and Bob takes 1.
 */
class XCastTest : E2e() {
    /** The cast panel's Cast button: the smallest clickable node holding the word "Cast". */
    private fun E2ePlayer.castButton(): androidx.compose.ui.semantics.SemanticsNode =
        nodes(hasClickAction() and hasAnyDescendant(hasText("Cast"))).fetchSemanticsNodes().minBy { it.size.width * it.size.height }

    // STK-023, STK-052: X bounded by the mana (one Mountain: at most 1), no OK and no Cancel; Cast answers X and asks the target
    @Test fun anXCastHasNoOkAndStopsAtWhatTheManaPays() {
        startGame(TestDeck.bursts(), deal = { a, _ -> "Mountain" in hand(a) && "Ember Burst" in hand(a) })
        bothOpen()
        bothKeep(bobAway = false)
        alice.playFromHand("Mountain", this)
        waitUntil("the Mountain on the battlefield", along = listOf(bob)) { alice.field("Mountain").isNotEmpty() && alice.inOwnMain() }
        alice.playFromHand("Ember Burst", this)
        waitUntil("Alice's cast panel with X at 0") { alice.shows(hasContentDescription("Cast panel")) && alice.shows(hasContentDescription("Value 0")) }
        assertTrue(alice.shows(hasText("Cast Ember Burst") and isHeading()), "the panel's title names the card (STK-023)")
        assertTrue(alice.shows(hasContentDescription("One less") and isNotEnabled()), "X at its least")
        alice.tap("One more")
        waitUntil("X at 1") { alice.shows(hasContentDescription("Value 1")) }
        assertTrue(alice.shows(hasContentDescription("One more") and isNotEnabled()), "+ stops at what one Mountain pays")
        assertFalse(alice.showsText("OK"), "no OK")
        assertFalse(alice.showsText("Cancel"), "no Cancel")
        val cast = alice.castButton()
        assertTrue(isEnabled().matches(cast), "Cast enabled")
        alice.node(androidx.compose.ui.test.SemanticsMatcher("Cast button") { it.id == cast.id }).performClick()
        waitUntil("Alice's target bar") { alice.shows(hasText("Choose any target") and isHeading()) }
        assertFalse(alice.showsText("Cancel"), "the target bar has no Cancel")
        alice.node(hasContentDescription("${bob.name}, life 20", substring = true)).performClick()
        waitUntil("the Burst dealing 1 to Bob, on the server", along = listOf(bob)) {
            alice.state.player(bob.me).life == 19 && alice.engine { device.session.pending.isEmpty() }
        }
        assertFalse(alice.refusalSeen, "no refusal: ${alice.shownTexts}")
    }
}
