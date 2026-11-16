package org.asyncttwiz.e2e

import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import org.junit.Test
import org.robolectric.annotation.Config
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Owner, prod 0.2.0 (desktop, 2026-11-16): casting a gift sorcery with two targets in one sentence ("Choose target creature you
 * control and target creature you don't control. …"), the target bar showed its text with no phrase highlighted, though other
 * spells' phrases shone. Gift adds a wordless spell ability of its own before the spell's, and that one was read as the
 * spell's text: no phrase to highlight, on the phone as on the desktop (both draw the same bar, STK-047, WEB-019).
 */
abstract class GiftTargetPhraseFlow(private val desktop: Boolean) : E2e() {
    private val rest = ". Put a +1/+1 counter on the creature you control if the gift was promised. Then those creatures fight each other."

    private fun E2ePlayer.press(node: androidx.compose.ui.test.SemanticsNodeInteraction) =
        if (desktop) node.performMouseInput { click() } else node.performClick()

    // STK-047, WEB-019, CR 702.174j, 601.2c: Bob has an Ash Pup, Alice an Ash Pup; she casts Ember Quarrel without the gift: its
    // bar shows the spell's sentence with "target creature you control" shining, then, her Pup picked, her Pup named in its
    // place and "target creature you don't control" shining; Bob's Pup picked, the Quarrel resolves (the Pups fight)
    @Test fun aGiftSpellsTargetsShowWhichPickIsWhich() {
        startGame(TestDeck("Quarrels", listOf(TestCards.mountain to 10, TestCards.pup to 15, TestCards.quarrel to 15)),
            TestDeck("Pups", listOf(TestCards.mountain to 20, TestCards.pup to 20)), bobStarts = true, aliceDesktop = desktop,
            deal = { a, b -> "Ash Pup" in hand(a) && "Ember Quarrel" in hand(a) && "Ash Pup" in hand(b) })
        bothOpen()
        alice.press(alice.control("Keep"))
        bob.tap("Keep")
        waitUntil("Bob's Main 1", along = listOf(alice)) { bob.inOwnMain() }
        bob.playFromHand("Ash Pup", this)
        waitUntil("Bob's Pup", along = listOf(alice)) { bob.field("Ash Pup").isNotEmpty() && bob.inOwnMain() }
        bob.hold("To combat")
        waitUntil("Alice's Main 1", along = listOf(bob)) { alice.inOwnMain() }
        for (name in listOf("Ash Pup", "Ember Quarrel")) {
            waitUntil("Alice's game settled") { alice.settled(this) }
            // WEB-015: on the desktop's fan the mouse first rests on the card, which rises whole out of the fan (a click on a
            // card half under its neighbour would be its neighbour's)
            if (desktop) {
                alice.handCard(name).performMouseInput { moveTo(center) }
                waitUntil("$name risen out of Alice's fan") {
                    alice.settled(this) && alice.handCard(name).fetchSemanticsNode().boundsInRoot.let { it.bottom <= 900f && it.height > it.width }
                }
            }
            alice.press(alice.handCard(name))
            waitUntil("$name raised in Alice's hand") { alice.raisedInHand() == name && alice.settled(this) }
            if (name == "Ash Pup") {
                alice.press(alice.handCard(name))
                waitUntil("Alice's Pup", along = listOf(bob)) { alice.field("Ash Pup").isNotEmpty() && alice.inOwnMain() }
            }
        }
        // The Quarrel's chips: cast without the gift
        waitUntil("the Quarrel's chips") { alice.shows(hasContentDescription("Way Cast")) }
        alice.press(alice.node(hasContentDescription("Way Cast")))
        waitUntil("Alice's target bar") { alice.shows(hasContentDescription("Choose targets")) }
        assertTrue(alice.shows(hasContentDescription("Question: Choose target creature you control and target creature you don't control$rest")),
            "the spell's sentence: ${alice.screen()}")
        assertTrue(alice.shows(hasContentDescription("Choosing now: target creature you control")), "the first phrase shining: ${alice.screen()}")
        alice.press(alice.battlefieldCard("Ash Pup"))
        waitUntil("the second target asked: ${alice.screen()}") {
            alice.shows(hasContentDescription("Choosing now: target creature you don't control"))
        }
        assertTrue(alice.shows(hasContentDescription("Question: Choose Ash Pup and target creature you don't control$rest")),
            "her Pup named for the first phrase: ${alice.screen()}")
        alice.press(alice.battlefieldCard("Ash Pup", owner = bob))
        waitUntil("the Pups fought", along = listOf(bob)) { alice.field("Ash Pup").isEmpty() && bob.field("Ash Pup").isEmpty() }
        assertFalse(alice.refusalSeen, "no refusal: ${alice.shownTexts}")
    }
}

/** On her phone. */
class GiftTargetPhraseTest : GiftTargetPhraseFlow(desktop = false)

/** In the desktop layout (WEB-019's strip). */
@Config(qualifiers = "w1440dp-h900dp-land-mdpi")
class GiftTargetPhraseDesktopTest : GiftTargetPhraseFlow(desktop = true)
