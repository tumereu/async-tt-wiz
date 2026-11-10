package org.asyncttwiz.e2e

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Test
import java.util.UUID
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Owner playtest 2026-11-09 (DRF-063): on the draft pick screen, holding a card to inspect it and moving the finger up and
 * back down past where the hold began closed Inspect. Once it is open, only lifting the finger closes it.
 */
class PickInspectHoldTest : E2e() {
    private val inspection = hasTestTag("card-inspection")

    // DRF-063 (owner playtest 2026-11-09): Alice holds a card of her pack until Inspect has settled, moves her finger up (a
    // little sideways), then down past where the hold began: Inspect stays open all the while; lifting the finger closes
    // it and the card is left unmarked
    @Test fun inspectStaysOpenUntilTheFingerLifts() {
        launchPlayers()
        val name = "E2E pick ${UUID.randomUUID().toString().take(6)}"
        val pod = ServerSetup.draftingPod(alice.account, bob.account, name)
        waitUntil("Alice's menu") { alice.shows(hasTestTag("menu-PODS")) }
        alice.tagged("menu-PODS").performClick()
        waitUntil("the pod in Alice's list") { alice.showsText(name) }
        alice.text(name).performScrollTo().performClick()
        waitUntil("Alice's pack") { alice.pods.state.detail?.takeIf { it.id == pod }?.draft?.head != null }
        val copy = alice.pods.state.detail!!.draft!!.head!!.cards.first().id
        val card = hasTestTag("pod-pick-card-$copy")
        waitUntil("the card on Alice's pick screen") { alice.shows(card) }
        val node = alice.node(card)
        holding(node) {
            assertTrue(alice.shows(inspection), "Inspect open after the hold: ${alice.screen()}")
            moveHeldFinger(node, Offset(30f, -200f))
            assertTrue(alice.shows(inspection), "still open with the finger moved up")
            moveHeldFinger(node, Offset(0f, 400f), steps = 20)
            assertTrue(alice.shows(inspection), "still open with the finger back down past where the hold began")
        }
        assertFalse(alice.shows(inspection), "closed once the finger lifted")
        assertTrue(alice.shows(card and SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Unmarked")), "the card unmarked")
    }
}
