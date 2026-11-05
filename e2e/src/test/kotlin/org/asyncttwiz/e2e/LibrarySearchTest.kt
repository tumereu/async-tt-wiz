package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.performClick
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Owner playtest 2026-11-05 evening (a fetch land): the library search didn't show the effect causing it, said nothing of
 * what was searched, lost its pick when hidden with the eye, and didn't show the library's other cards. Alice plays a
 * Cinder Trail and activates it ({T}, pay 1 life, sacrifice it: search for a basic land card).
 */
class LibrarySearchTest : E2e() {
    private val inPanel = hasAnyAncestor(hasContentDescription("Automatum choice"))

    // STK-056, STK-044: the search panel: the Trail's ability at its right, "Search your library for a basic land card",
    // the Mountains to pick, the other cards under "Other cards"; the pick survives hiding the panel; OK finds the Mountain
    @Test fun aFetchLandsSearchShowsItsSourceAndKeepsItsPick() {
        startGame(TestDeck.trails(), deal = { a, _ -> "Cinder Trail" in hand(a) })
        bothOpen()
        bothKeep(bobAway = false)
        alice.playFromHand("Cinder Trail", this)
        waitUntil("the Trail on the battlefield", along = listOf(bob)) { alice.field("Cinder Trail").isNotEmpty() && alice.inOwnMain() }
        val mountains = alice.field("Mountain").size
        alice.battlefieldCard("Cinder Trail").performClick()
        waitUntil("Alice's search", along = listOf(bob)) {
            // Alice passes her own ability on (STK-026) as the main button says
            if (alice.stepArrow() == "Resolve" || alice.stepArrow() == "Pass") alice.once { alice.tap(alice.stepArrow()!!) }
            alice.shows(hasText("Search your library for a basic land card") and isHeading())
        }
        assertTrue(alice.shows(hasTestTag("choice-source") and hasContentDescription("Cinder Trail")), "the Trail at the right: ${alice.screen()}")
        assertTrue(alice.shows(hasText("Other cards") and isHeading()), "Other cards: ${alice.screen()}")
        assertTrue(alice.shows(hasContentDescription("Storm Spark") and inPanel), "a Storm Spark among the other cards")
        // A Storm Spark can't be picked; a Mountain can, and stays picked while the panel is hidden and shown again
        alice.nodes(hasContentDescription("Storm Spark") and inPanel)[0].performClick()
        alice.nodes(hasContentDescription("Mountain") and inPanel)[0].performClick()
        waitUntil("the Mountain picked") { alice.shows(hasContentDescription("Mountain, chosen") and inPanel) }
        assertFalse(alice.shows(hasContentDescription("Storm Spark, chosen")), "the Spark isn't picked")
        alice.tap("Hide Automatum panel")
        waitUntil("the panel hidden") { !alice.shows(hasContentDescription("Mountain, chosen") and inPanel) && alice.showsControl("Show Automatum panel") }
        alice.tap("Show Automatum panel")
        waitUntil("the pick still there") { alice.shows(hasContentDescription("Mountain, chosen") and inPanel) }
        alice.text("OK").performClick()
        waitUntil("the Mountain found, on the server", along = listOf(bob)) {
            alice.field("Mountain").size == mountains + 1 && alice.field("Cinder Trail").isEmpty() &&
                alice.engine { device.session.pending.isEmpty() }
        }
        assertFalse(alice.refusalSeen, "no refusal: ${alice.shownTexts}")
    }
}
