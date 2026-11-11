package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.performClick
import arcanum.engine.decision.Decision
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Owner problem report 3UE2NW (beta14, 2026-11-11): sacrificing his Blood Artist to an equip cost asked him to order its
 * death trigger and another creature's, and the panel called one of them only "Trigger 1". The Artist had left the
 * battlefield, so the panel took it for hidden: neither its name nor its trigger's text was shown. What it was as it last
 * existed on the battlefield is public (CR 608.2h, last known information), so its trigger shows its source and its text.
 */
class TriggerSourceLeftTest : E2e() {
    private val martyr = "Trigger: When this creature dies, you gain 2 life."
    private val mourner = "Trigger: Whenever another creature you control dies, you gain 1 life."

    // STK-030, CR 603.3b, CR 608.2h: a trigger whose source has left the battlefield is listed by that source's name and the
    // trigger's own text, as the other trigger is
    @Test fun aTriggerOfALeftSourceShowsItsSourceAndText() {
        startGame(TestDeck("Mourners", listOf(TestCards.mountain to 10, TestCards.altar to 10, TestCards.martyr to 10, TestCards.mourner to 10)),
            TestDeck.glimmers(),
            deal = { a, _ -> hand(a).let { h -> listOf("Ember Altar", "Ember Martyr", "Ember Mourner").all { it in h } } })
        bothOpen()
        bothKeep(bobAway = false)
        for (name in listOf("Ember Altar", "Ember Martyr", "Ember Mourner")) {
            alice.playFromHand(name, this)
            waitUntil("$name on Alice's battlefield", along = listOf(bob)) { alice.field(name).isNotEmpty() && alice.inOwnMain() }
        }
        alice.battlefieldCard("Ember Altar").performClick()
        waitUntil("Alice's sacrifice asked on the table") { alice.shows(hasContentDescription("Choose on the table")) }
        alice.battlefieldCard("Ember Martyr").performClick()
        waitUntil("Alice ordering the Martyr's and the Mourner's triggers", along = listOf(bob)) {
            alice.state.pending?.decision is Decision.OrderTriggers && alice.shows(hasText("Order triggers") and isHeading())
        }
        assertTrue(alice.field("Ember Martyr").isEmpty(), "the Martyr has left the battlefield")
        assertTrue(alice.shows(hasContentDescription(martyr)), "the Martyr's trigger by its text: ${alice.screen()}")
        assertTrue(alice.shows(hasContentDescription(mourner)), "the Mourner's trigger by its text: ${alice.screen()}")
        assertTrue(alice.shows(hasContentDescription("Ember Martyr") and hasAnyAncestor(hasContentDescription(martyr))),
            "the Martyr's trigger shows the Martyr: ${alice.screen()}")
        assertFalse(alice.shows(hasText("Trigger 1") or hasText("Trigger 2")),
            "no stand-in name: ${alice.screen()}")
        alice.text("OK").performClick()
        waitUntil("the triggers and the Altar's ability resolved", along = listOf(bob)) {
            alice.state.stack.isEmpty() && alice.state.pending == null && alice.inOwnMain()
        }
        // The Altar's 2, the Martyr's 2, the Mourner's 1
        assertEquals(25, alice.state.player(alice.me).life)
        assertFalse(alice.refusalSeen, "no refusal: ${alice.shownTexts}")
    }
}
