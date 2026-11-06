package org.asyncttwiz.e2e

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.performClick
import org.asyncttwiz.android.CHOICE_SOURCE_TAG
import org.junit.Test
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Owner playtest 2026-11-06, Automatum panels: holding a revealed card in a panel to inspect it made the panel under it jump
 * upwards; inspecting the ability at a panel's corner didn't scroll when it was taller than the screen. Alice plays a
 * Cinder Trail and activates it: its search panel shows her library's Mountains to pick, with the Trail's ability at the
 * panel's right.
 */
class PanelInspectTest : E2e() {
    private val inPanel = hasAnyAncestor(hasContentDescription("Automatum choice"))

    /** Alice's search panel open, from her Cinder Trail's ability. */
    private fun aliceSearches() {
        startGame(TestDeck.trails(), deal = { a, _ -> "Cinder Trail" in hand(a) })
        bothOpen()
        bothKeep(bobAway = false)
        alice.playFromHand("Cinder Trail", this)
        waitUntil("the Trail on the battlefield", along = listOf(bob)) { alice.field("Cinder Trail").isNotEmpty() && alice.inOwnMain() }
        // Its one card on the screen (on a low screen the battlefield's drawing reaches past its bounds)
        alice.nodes(hasContentDescription("Cinder Trail, owner ${alice.name},", substring = true))[0].performClick()
        waitUntil("Alice's search", along = listOf(bob)) {
            if (alice.stepArrow() == "Resolve" || alice.stepArrow() == "Pass") alice.once { alice.tap(alice.stepArrow()!!) }
            alice.shows(hasText("Search your library for a basic land card") and isHeading())
        }
        run(skip = java.time.Duration.ofMillis(500))
    }

    // TBL-104, STK-056 (owner playtest 2026-11-06): holding a Mountain in the search panel inspects it and nothing in the
    // panel moves meanwhile: the card held and the panel's title stay where they were
    @Test fun holdingACardInAPanelLeavesThePanelStill() {
        aliceSearches()
        val mountain = alice.nodes(hasContentDescription("Mountain") and inPanel)[0]
        val title = alice.node(hasText("Search your library for a basic land card") and isHeading())
        val card = mountain.fetchSemanticsNode().boundsInRoot
        val heading = title.fetchSemanticsNode().boundsInRoot
        holding(mountain) {
            assertTrue(alice.shows(hasContentDescription("Card inspection content")), "Inspect open: ${alice.screen()}")
            assertEquals(card, mountain.fetchSemanticsNode().boundsInRoot, "the held card stays put")
            assertEquals(heading, title.fetchSemanticsNode().boundsInRoot, "the panel stays put")
        }
        assertTrue(!alice.shows(hasContentDescription("Card inspection content")), "closed on release")
        assertEquals(card, mountain.fetchSemanticsNode().boundsInRoot, "still put after release")
    }

    // TBL-127, STK-044, STK-050 (owner playtest 2026-11-06): on a phone too low for the Trail ability's Inspect (a small
    // phone with the largest system text), holding it at the panel's right and moving the finger up scrolls the Inspect,
    // as on the table
    @Config(qualifiers = "w360dp-h640dp-port-mdpi", fontScale = 2f)
    @Test fun inspectingThePanelsSourceScrollsWhenTallerThanTheScreen() {
        aliceSearches()
        val source = alice.node(hasTestTag(CHOICE_SOURCE_TAG) and hasContentDescription("Cinder Trail"))
        val tile = hasContentDescription("Activated ability of Cinder Trail")
        // Where the tile is drawn, past the edges the view clips it to
        fun top() = alice.node(tile).fetchSemanticsNode().positionInRoot.y
        holding(source) {
            val before = top()
            val height = alice.node(tile).fetchSemanticsNode().size.height
            val screen = alice.node(hasContentDescription("Card inspection content")).fetchSemanticsNode().boundsInRoot
            assertTrue(before + height > screen.bottom + 10f, "taller than the screen: tile to ${before + height}, view to ${screen.bottom}")
            moveHeldFinger(source, Offset(0f, -120f))
            val scrolled = top()
            assertTrue(scrolled < before - 10f, "scrolled up with the finger: $before → $scrolled")
        }
        assertTrue(!alice.shows(hasContentDescription("Card inspection content")), "closed on release")
    }
}
