package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performClick
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Owner, 2026-11-07 (View deck): your own Library's menu has View deck, a panel of your deck in type groups, All or
 * Still in library; the other player's Library has none.
 */
class ViewDeckTest : E2e() {
    /** Alice opens View deck from her Library's menu and switches to Still in library. */
    private fun openStillInLibrary(left: Int) {
        alice.node(hasContentDescription("${alice.name} library,", substring = true)).performClick()
        waitUntil("Alice's Library menu with View deck") { alice.showsControl("View deck") }
        pastPopupGuard()
        alice.tap("View deck")
        waitUntil("the Deck panel") { alice.shows(hasTestTag("deck-view")) }
        assertTrue(alice.showsText("All · 40"), alice.screen())
        pastPopupGuard()
        alice.text("Still in library · $left").performClick()
        waitUntil("Still in library chosen") { alice.showsText("Still in library · $left") && alice.showsText("Lands · ", substring = true) }
    }

    // ZON-093, ZON-078 (owner 2026-11-07): at the opening Alice's View deck shows her 40 cards, and Still in library
    // the 33 not in her opening hand; after she draws in her first turn (Bob starts) it shows 32. Nothing in the game changes.
    @Test fun viewDeckFollowsTheCardsLeavingTheLibrary() {
        startGame(bobStarts = true)
        bothOpen()
        val head = alice.engine { device.session.head }
        openStillInLibrary(33)
        assertEquals(head, alice.engine { device.session.head }, "looking only: no event")
        pastPopupGuard()
        alice.tap("Close")
        waitUntil("the panel closed") { !alice.shows(hasTestTag("deck-view")) }
        alice.tap("Keep")
        bob.tap("Keep")
        waitUntil("Bob's Main 1", along = listOf(alice)) { bob.inOwnMain() }
        bob.hold("To combat")
        waitUntil("Alice's Main 1 after her draw", along = listOf(bob)) {
            alice.inOwnMain() && alice.state.player(alice.me).library.size == 32
        }
        openStillInLibrary(32)
        assertFalse(alice.refusalSeen, "no refusal")
    }
}
