package org.asyncttwiz.e2e

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Test
import kotlin.test.assertNull

/**
 * The card catalog's support marks (docs/specs/web-client.md sections 3.8, 3.9) decide which sets can be drafted, and the
 * phone shows them at once: in Alice's draft set search (her card records are the server's catalog, the test cards') the
 * Unlisted Set, whose packs hold a card Automatum doesn't support, is greyed and can't be chosen, and holding its warning
 * sign names that card; a supported set can be chosen.
 */
class SetSupportMarksTest : E2e() {
    // DRF-162, SYS-082, DRF-161
    @Test fun theDraftSetSearchRefusesASetTheCardCatalogSaysCantBeDrafted() {
        launchPlayers(aliceCatalog = true)
        waitUntil("Alice's card records installed") { alice.catalog!!.state.ready }
        waitUntil("Alice's menu") { alice.shows(hasTestTag("menu-PODS")) }
        alice.tagged("menu-PODS").performClick()
        waitUntil("Alice's Pods") { alice.shows(hasTestTag("new-pod")) }
        alice.tagged("new-pod").performClick()
        waitUntil("the New pod form") { alice.pods.state.creating != null }
        alice.pods.chooseSet()
        waitUntil("the Source dialog") { alice.showsText("Sets") }
        alice.node(hasText("Sets")).performClick()
        waitUntil("the sets in the list") { alice.showsText("Unlisted Set") }
        val unlisted = alice.node(hasTestTag("set-OFF"))
        unlisted.performScrollTo()
        alice.node(hasClickAction() and hasAnyDescendant(hasText("Unlisted Set"))).assertIsNotEnabled().performClick()
        assertNull(alice.pods.state.creating!!.set, "the unsupported set isn't chosen")
        alice.node(hasContentDescription("Automatum can't play 1 card yet: Plain Bear")).assertExists()
        // A set Automatum can play every card of is chosen
        alice.node(hasClickAction() and hasAnyDescendant(hasText("Test Set"))).performScrollTo().assertIsEnabled().performClick()
        waitUntil("the set chosen as the source") { alice.pods.state.creating?.set?.code == "TST" }
    }
}
