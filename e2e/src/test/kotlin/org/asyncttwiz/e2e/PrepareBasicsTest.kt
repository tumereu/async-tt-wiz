package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.asyncttwiz.protocol.PodGamePreparation
import org.asyncttwiz.protocol.SavedDeckSection
import org.junit.Test
import java.util.UUID
import kotlin.test.assertFalse

/**
 * Owner playtest 2026-11-06: preparing a pod deck for a game has no basic land −/+ steppers; the basics are cards like any
 * other, moved between Main and Side. Alice drafted with Bob, added 40 Plains to her Main (an unlimited sideboard gives her
 * Side 17 of each basic), challenged him and opens Prepare deck.
 */
class PrepareBasicsTest : E2e() {
    /** Alice's own prepared deck as the server accepted it. */
    private fun acceptedPlains(section: SavedDeckSection) = alice.lobby.state.acceptedPreparation?.players
        ?.firstOrNull { it.accountId == alice.id }?.deck?.let { PodGamePreparation.basics(it, section).plains }

    // LOB-052, DRF-124, LOB-049: no steppers; a Plains moves to Side as any card does (tap → Move one), on the server too,
    // and Undo brings it back
    @Test fun basicsMoveBetweenMainAndSideLikeAnyCard() {
        launchPlayers()
        val name = "E2E pod game ${UUID.randomUUID().toString().take(6)}"
        ServerSetup.podGame(alice.account, bob.account, name)
        waitUntil("Alice's menu") { alice.shows(hasTestTag("menu-GAMES")) }
        alice.tagged("menu-GAMES").performClick()
        waitUntil("the game in Alice's list") { alice.showsText(name) }
        alice.text(name).performScrollTo().performClick()
        waitUntil("Alice's pod deck in the lobby") {
            alice.lobby.state.preparation?.players?.firstOrNull { it.accountId == alice.id }?.deck != null && alice.showsText("Prepare deck")
        }
        alice.text("Prepare deck").performScrollTo().performClick()
        waitUntil("Alice's Plains in her Main") { alice.shows(hasContentDescription("Plains, 40 copies, Mainboard")) }
        assertFalse(alice.shows(hasContentDescription("Add Plains", substring = true)), "no basic land steppers")
        assertFalse(alice.shows(hasContentDescription("Remove Plains", substring = true)), "no basic land steppers")

        alice.described("Plains, 40 copies, Mainboard").performClick()
        waitUntil("the Plains' menu") { alice.showsText("Move one to Sideboard") }
        alice.text("Move one to Sideboard").performClick()
        waitUntil("a Plains moved to Side, on the server") {
            alice.shows(hasContentDescription("Plains, 39 copies, Mainboard")) &&
                acceptedPlains(SavedDeckSection.MAINBOARD) == 39 && acceptedPlains(SavedDeckSection.SIDEBOARD) == 18
        }

        alice.described("Undo").performClick()
        waitUntil("the Plains back in Main, on the server") {
            alice.shows(hasContentDescription("Plains, 40 copies, Mainboard")) &&
                acceptedPlains(SavedDeckSection.MAINBOARD) == 40 && acceptedPlains(SavedDeckSection.SIDEBOARD) == 17
        }
    }
}
