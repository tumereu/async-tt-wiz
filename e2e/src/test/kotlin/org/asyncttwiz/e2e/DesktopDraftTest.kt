package org.asyncttwiz.e2e

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import org.asyncttwiz.protocol.DraftSettings
import org.asyncttwiz.protocol.PodDeckCategory
import org.junit.Test
import org.robolectric.annotation.Config
import java.util.UUID
import kotlin.test.assertTrue

/**
 * A pod on the desktop layout (docs/specs/web-client.md §5, §7.6, step 6b; wireframe D9): Alice, on a computer at 1440 × 900,
 * picks with clicks and keys and moves her Build cards by their edge zones; Bob on his phone's layout.
 */
@Config(qualifiers = "w1440dp-h900dp-land-mdpi")
class DesktopDraftTest : E2e() {
    private fun openPod(pod: String) {
        waitUntil("Alice's side menu with the pod") { alice.shows(hasTestTag("navigation-pod-$pod")) }
        alice.tagged("navigation-pod-$pod").performClick()
    }

    private fun card(id: String) = alice.tagged("pod-pick-card-$id")
    private fun marked(id: String, mark: String) =
        alice.shows(hasTestTag("pod-pick-card-$id") and SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, mark))

    // WEB-044, WEB-028, WEB-036, DRF-060: a right-click burns a card, a click keeps another, K over a third keeps it instead,
    // Enter confirms: the server takes the third card into Alice's pool
    @Test fun aPickByClicksAndKeys() {
        launchPlayers(aliceDesktop = true)
        val name = "E2E desktop pick ${UUID.randomUUID().toString().take(6)}"
        val pod = ServerSetup.draftingPod(alice.account, bob.account, name,
            settings = DraftSettings(packsPerPlayer = 3, cardsPerPack = 3, keep = 1, burn = 1))
        openPod(pod)
        waitUntil("Alice's pack") { alice.pods.state.detail?.takeIf { it.id == pod }?.draft?.head != null }
        val (burned, clicked, keyed) = alice.pods.state.detail!!.draft!!.head!!.cards.map { it.id }
        waitUntil("Alice's pick screen") { alice.shows(hasTestTag("pod-pick-card-$keyed")) }
        card(burned).performMouseInput { moveTo(center); press(androidx.compose.ui.test.MouseButton.Secondary); release(androidx.compose.ui.test.MouseButton.Secondary) }
        waitUntil("a right-click burned the first card") { marked(burned, "Burn") }
        card(clicked).performMouseInput { click() }
        waitUntil("a click kept the second") { marked(clicked, "Keep") }
        card(keyed).performMouseInput { moveTo(center) }
        run()
        assertTrue(alice.key("k", true), "K taken over the third card")
        alice.key("k", false)
        waitUntil("K kept the third instead") { marked(keyed, "Keep") && marked(clicked, "Unmarked") && marked(burned, "Burn") }
        assertTrue(alice.key("Enter", true), "Enter taken")
        alice.key("Enter", false)
        waitUntil("the server took the third card into Alice's pool") {
            ServerSetup.pod(alice.account, pod).draft?.pool.orEmpty().any { it.id == keyed }
        }
        assertTrue(ServerSetup.pod(alice.account, pod).draft?.pool.orEmpty().none { it.id == clicked || it.id == burned }, "only the kept card")
    }

    // WEB-045, WEB-046, DRF-099: a click on a Main card's left edge zone moves it to Side; → over another Main card moves it to Maybe
    @Test fun buildCardsMoveByTheirEdgeZones() {
        launchPlayers(aliceDesktop = true)
        val name = "E2E desktop build ${UUID.randomUUID().toString().take(6)}"
        val pod = ServerSetup.draftedPod(alice.account, bob.account, name)
        openPod(pod)
        waitUntil("Alice's Build") { alice.pods.state.detail?.takeIf { it.id == pod }?.build != null && alice.shows(hasTestTag("pod-build")) }
        fun count(category: PodDeckCategory) = alice.pods.state.detail!!.build!!.copies.count { it.category == category }
        fun mainCard() = alice.nodes(hasContentDescription(", Main", substring = true)).onFirst()
        val main = count(PodDeckCategory.MAIN)
        mainCard().performMouseInput { moveTo(Offset(width * .1f, height / 2f)) }
        waitUntil("the left edge zone shown") { alice.shows(hasTestTag("edge-zone-left-active")) && alice.showsText("◂ Side") }
        mainCard().performMouseInput { click(Offset(width * .1f, height / 2f)) }
        waitUntil("one card in Side") { count(PodDeckCategory.SIDE) == 1 && count(PodDeckCategory.MAIN) == main - 1 && alice.pods.state.pending == 0 }
        mainCard().performMouseInput { moveTo(center) }
        run()
        assertTrue(alice.key("ArrowRight", true), "→ taken over a Main card")
        alice.key("ArrowRight", false)
        waitUntil("one card in Maybe") { count(PodDeckCategory.MAYBE) == 1 && count(PodDeckCategory.MAIN) == main - 2 && alice.pods.state.pending == 0 }
        assertTrue(!alice.showsText("Review ›"), "no one-by-one review on the desktop layout")
    }
}
