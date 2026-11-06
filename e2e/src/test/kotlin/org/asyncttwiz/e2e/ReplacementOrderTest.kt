package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.performClick
import arcanum.engine.decision.Decision
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Owner gallery review, 2026-11-06: which replacement effect applies first is a drag-to-reorder preference list. Alice has
 * an Ember Amplifier (her sources deal double damage) and an Ember Mindtrap (her damage to an opponent is prevented and
 * they mill that many cards) and casts Storm Spark at Bob: Bob, the affected player, is asked; he drags the Mindtrap to the
 * top and OK: the 3 damage is prevented and he mills 3 (doubling first would have been 6).
 */
class ReplacementOrderTest : E2e() {
    private val mindtrap = "Replacement: If a source you control would deal damage to an opponent, prevent that damage and each opponent mills that many cards."
    private val amplifier = "Replacement: If a source you control would deal damage to a permanent or player, it deals double that damage instead."

    // STK-030, CR 616.1, 616.1e: the affected player orders the effects; the order taken is the list's
    @Test fun theAffectedPlayerOrdersTheReplacements() {
        val deck = TestDeck("Traps", listOf(TestCards.mountain to 16, TestCards.amplifier to 8, TestCards.mindtrap to 8, TestCards.spark to 8))
        startGame(deck, deal = { a, _ -> listOf("Mountain", "Ember Amplifier", "Ember Mindtrap", "Storm Spark").all { it in hand(a) } })
        bothOpen()
        bothKeep(bobAway = false)
        alice.playFromHand("Mountain", this)
        waitUntil("the Mountain on the battlefield", along = listOf(bob)) { alice.field("Mountain").isNotEmpty() && alice.inOwnMain() }
        for (name in listOf("Ember Amplifier", "Ember Mindtrap")) {
            alice.playFromHand(name, this)
            waitUntil("$name on the battlefield", along = listOf(bob)) { alice.field(name).isNotEmpty() && alice.inOwnMain() }
        }
        val library = alice.state.player(bob.me).library.size
        alice.playFromHand("Storm Spark", this)
        waitUntil("Alice's target bar") { alice.shows(hasText("Choose any target") and isHeading()) }
        alice.node(hasContentDescription("${bob.name}, life 20", substring = true)).performClick()
        waitUntil("Bob choosing which applies first", along = listOf(bob)) {
            bob.state.pending?.decision is Decision.ChooseReplacement && bob.shows(hasText("Which applies first") and isHeading())
        }
        assertEquals(2, bob.nodes(hasContentDescription("Drag to reorder")).fetchSemanticsNodes().size, "a handle per effect")
        if (bob.rowTop(mindtrap) > bob.rowTop(amplifier)) {
            inputClockCaughtUp()
            dragRowUp(bob, mindtrap)
        }
        waitUntil("the Mindtrap at the top") { bob.rowTop(mindtrap) < bob.rowTop(amplifier) }
        bob.text("OK").performClick()
        waitUntil("the Spark resolved", along = listOf(bob)) {
            alice.state.stack.isEmpty() && alice.state.pending == null && alice.state.player(bob.me).library.size < library
        }
        // Prevented first: 3 milled, no damage (doubled first: 6 milled)
        assertEquals(20, alice.state.player(bob.me).life)
        assertEquals(library - 3, alice.state.player(bob.me).library.size)
        assertFalse(bob.refusalSeen, "no refusal: ${bob.shownTexts}")
    }
}
