package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.doubleClick
import org.junit.Test
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Owner notes after beta5 (2026-11-08): in landscape the hand was a full-width strip above the bottom bar; it moves into
 * the bottom bar, between your life square and piles and the controls at its right end, and the battlefields take the
 * strip's height. Everything the hand does stays.
 */
class LandscapeHandTest : E2e() {
    // TBL-009, ZON-079, ZON-004 (owner, 2026-11-08): in landscape Alice's hand sits in the bottom bar, after her piles and
    // before Undo and the quick actions, the battlefield reaching down to the bar; a tap raises a card there and a second
    // plays it, a double tap plays a land
    @Config(qualifiers = "w892dp-h412dp-land-mdpi")
    @Test fun theHandInTheBottomBarPlaysAsInPortrait() {
        startGame(deal = { a, _ -> hand(a).count { it == "Mountain" } >= 2 })
        bothOpen()
        alice.tap("Keep")
        bob.tap("Keep")
        waitUntil("Alice's Main 1", along = listOf(bob)) { alice.inOwnMain() }
        val bar = 412f - 56f
        fun handBounds() = alice.nodes(hasContentDescription(", owner ${alice.name}, ", substring = true)).fetchSemanticsNodes()
            .map { it.boundsInRoot }.filter { it.center.y > bar }
        val cards = handBounds()
        assertEquals(alice.state.player(alice.me).hand.size, cards.size, "her whole hand in the bottom bar: ${alice.screen()}")
        val undo = alice.control("Undo").fetchSemanticsNode().boundsInRoot
        val library = alice.node(hasContentDescription("${alice.name} library,", substring = true)).fetchSemanticsNode().boundsInRoot
        assertTrue(cards.all { it.top >= bar && it.bottom <= 412f }, "inside the bar: $cards")
        assertTrue(cards.all { it.left > library.right && it.right < undo.left }, "between her piles and the controls: $cards")
        assertTrue(alice.battlefieldBounds().bottom > bar - 12f, "the battlefield reaches the bar: ${alice.battlefieldBounds()}")
        fun centre(row: List<androidx.compose.ui.geometry.Rect>) = (row.minOf { it.left } + row.maxOf { it.right }) / 2
        val handCentre = centre(cards)

        // A tap raises a Mountain in the bar; a second plays it
        alice.playFromHand("Mountain", this)
        waitUntil("the Mountain on the battlefield") { alice.field("Mountain").isNotEmpty() && alice.inOwnMain() }
        // The hand's row stays where it was (the cards left centred in it)
        assertEquals(handCentre, centre(handBounds()), .5f)
        // In her next turn a double tap plays the other Mountain
        alice.hold("To combat")
        waitUntil("Alice's next Main 1", along = listOf(bob)) {
            // Her playback stops in Bob's turn: she goes on with the main button
            if (alice.status is org.asyncttwiz.protocol.game.GameDevice.Status.Stopped ||
                alice.status == org.asyncttwiz.protocol.game.GameDevice.Status.YourMove && alice.state.turn.active != alice.me)
                alice.once { alice.stepArrow()?.let(alice::tap) }
            alice.inOwnMain() && alice.state.turn.number >= 3
        }
        alice.handCard("Mountain").performTouchInput { doubleClick() }
        waitUntil("the second Mountain on the battlefield") { alice.field("Mountain").size == 2 }
        assertTrue(alice.leaks.leaks.isEmpty() && bob.leaks.leaks.isEmpty())
    }
}
