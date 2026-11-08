package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.performClick
import org.junit.Test
import kotlin.test.assertTrue

/** Owner, beta9 notes: a sentence too long for the target bar can be read whole, and that leaves the rest of the bar alone. */
class TargetBarSentenceTest : E2e() {
    // STK-047: the sentence stops at 4 lines; a tap shows it whole, scrolling past half the table's height, another tap
    // goes back, and meanwhile the table's targets take their taps as usual
    @Test fun aLongSentenceOpensWholeOnATapAndTheCastGoesOn() {
        startGame(TestDeck.thirds(TestCards.cascade), deal = { a, _ -> "Mountain" in hand(a) && "Ember Cascade" in hand(a) })
        bothOpen()
        bothKeep(bobAway = false)
        alice.playFromHand("Mountain", this)
        waitUntil("the Mountain on the battlefield", along = listOf(bob)) { alice.field("Mountain").isNotEmpty() && alice.inOwnMain() }
        alice.playFromHand("Ember Cascade", this)
        waitUntil("Alice's target bar") { alice.shows(hasText("Choose any target") and isHeading()) }
        val sentence = alice.node(hasContentDescription("Question: Ember Cascade deals 3 damage", substring = true))
        fun height() = sentence.fetchSemanticsNode().boundsInRoot.height
        val scrolling = hasScrollAction() and hasAnyDescendant(hasContentDescription("Question: ", substring = true))
        val fourLines = height()
        assertTrue(fourLines > 0f && !alice.shows(scrolling), "cut at 4 lines: $fourLines")

        sentence.performClick()
        waitUntil("the whole sentence") { height() > fourLines }
        // Taller than half the table it scrolls there. The test screen is 892 dp at 1 px a dp; the bar adds its title's line.
        val shown = sentence.fetchSemanticsNode().boundsInRoot.height
        assertTrue(shown in (446f - 1f)..(446f + 1f), "half the table's height: $shown")
        assertTrue(alice.shows(scrolling), "it scrolls")
        val bar = alice.described("Choose targets").fetchSemanticsNode().boundsInRoot
        assertTrue(bar.height < 446f + 40f, "the bar is that with its title: $bar")

        sentence.performClick()
        waitUntil("back at 4 lines") { height() == fourLines }
        sentence.performClick()
        waitUntil("whole again") { height() > fourLines }

        // Open or not, the cast goes on: Bob's life square is the target
        alice.node(hasContentDescription("${bob.name}, life 20", substring = true)).performClick()
        waitUntil("the Cascade resolving at Bob", along = listOf(bob)) { alice.state.player(bob.me).life == 17 }
    }
}
