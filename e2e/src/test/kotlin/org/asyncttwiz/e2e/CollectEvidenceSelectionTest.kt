package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.isNotEnabled
import androidx.compose.ui.test.performClick
import arcanum.engine.decision.Decision
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Owner, 2026-11-09: "You select at least up to the required amount, which if other conditions are fulfilled enables the button
 * to cast etc, but you may make more selections." Alice mills Storm Sparks ({R}) with free Ember Sifters and casts an Ember
 * Witness ("you may collect evidence 2"): she picks the cards in one panel, whose OK waits for the total.
 */
class CollectEvidenceSelectionTest : E2e() {
    private val inPanel = hasAnyAncestor(hasContentDescription("Automatum choice"))
    private val spark = hasContentDescription("Storm Spark") and inPanel
    private val ok = hasClickAction() and hasAnyDescendant(hasText("OK"))

    /** Alice plays the free [name] and it and its trigger resolve, as far as [until]. */
    private fun play(name: String, until: () -> Boolean) {
        alice.playFromHand(name, this)
        waitUntil("$name played on", along = listOf(bob)) {
            // Alice passes her own spell and trigger on (STK-026) as the main button says
            if (alice.stepArrow() == "Resolve" || alice.stepArrow() == "Pass") alice.once { alice.tap(alice.stepArrow()!!) }
            until()
        }
    }

    private fun graveyard() = alice.state.player(alice.me).graveyard

    /** Alice has [milled] cards in her graveyard and is asked to pick the evidence, having said yes. */
    private fun toTheSelection(sifters: Int, milled: Int): Decision.ChooseObjects {
        bothOpen()
        bothKeep(bobAway = false)
        repeat(sifters) { n ->
            play("Ember Sifter") { graveyard().size == 2 * (n + 1) && alice.state.stack.isEmpty() && alice.state.pending == null && alice.inOwnMain() }
        }
        assertEquals(milled, graveyard().size)
        play("Ember Witness") { alice.state.pending?.decision is Decision.May && alice.showsText("Yes") }
        alice.text("Yes").performClick()
        waitUntil("the evidence's panel") { alice.state.pending?.decision is Decision.ChooseObjects && alice.shows(spark) }
        return assertIs<Decision.ChooseObjects>(alice.state.pending?.decision)
    }

    private fun pick(index: Int, chosen: Int) {
        alice.nodes(spark)[index].performClick()
        waitUntil("$chosen cards picked") { alice.nodes(hasContentDescription("Storm Spark, chosen") and inPanel).fetchSemanticsNodes().size == chosen }
    }

    // STK-061, CR 701.59a: the panel takes any number of the cards; OK is greyed below the total and enabled from it on, for more cards too;
    // they are exiled together and the card is drawn
    @Test fun okWaitsForTheTotalAndMoreCardsMayBePicked() {
        startGame(TestDeck.witnesses(), deal = { a, _ ->
            hand(a).count { it == "Ember Sifter" } >= 2 && "Ember Witness" in hand(a) && library(a).take(4).all { it == "Storm Spark" }
        })
        val choice = toTheSelection(sifters = 2, milled = 4)
        assertNull(choice.max, "any number")
        assertTrue(alice.shows(ok and isNotEnabled()), "nothing picked: ${alice.screen()}")

        pick(0, 1)
        run()
        assertTrue(alice.shows(ok and isNotEnabled()), "1 of 2: ${alice.screen()}")
        // pick() leaves a chosen card under another description: the next unpicked one is first again
        pick(0, 2)
        waitUntil("OK enabled at the total") { alice.shows(ok and isEnabled()) }
        pick(0, 3)
        waitUntil("OK still enabled with more than needed") { alice.shows(ok and isEnabled()) }

        val hand = alice.state.player(alice.me).hand.size
        alice.text("OK").performClick()
        waitUntil("the cards exiled and a card drawn, on the server", along = listOf(bob)) {
            alice.state.player(alice.me).hand.size == hand + 1 && alice.engine { device.session.pending.isEmpty() }
        }
        assertEquals(3, alice.state.exile.size)
        assertEquals(1, graveyard().size)
        assertFalse(alice.refusalSeen, "no refusal: ${alice.shownTexts}")
    }

    // STK-061, SYS-063, CR 701.59a: a total only all the cards reach isn't skipped, in Quick too (its one answer is no reason here): she
    // still picks them and confirms
    @Test fun aTotalOnlyAllTheCardsReachIsStillConfirmed() {
        startGame(TestDeck.witnesses(), deal = { a, _ ->
            "Ember Sifter" in hand(a) && "Ember Witness" in hand(a) && library(a).take(2).all { it == "Storm Spark" }
        })
        toTheSelection(sifters = 1, milled = 2)
        repeat(5) { run() }
        assertIs<Decision.ChooseObjects>(alice.state.pending?.decision, "still asked")
        assertTrue(alice.shows(ok and isNotEnabled()), "nothing picked: ${alice.screen()}")
        pick(0, 1)
        run()
        assertTrue(alice.shows(ok and isNotEnabled()), "1 of 2: ${alice.screen()}")
        pick(0, 2)
        waitUntil("OK enabled at the total") { alice.shows(ok and isEnabled()) }

        val hand = alice.state.player(alice.me).hand.size
        alice.text("OK").performClick()
        waitUntil("the cards exiled and a card drawn, on the server", along = listOf(bob)) {
            alice.state.player(alice.me).hand.size == hand + 1 && alice.engine { device.session.pending.isEmpty() }
        }
        assertEquals(2, alice.state.exile.size)
        assertFalse(alice.refusalSeen, "no refusal: ${alice.shownTexts}")
    }
}
