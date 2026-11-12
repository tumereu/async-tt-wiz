package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import org.junit.Test
import java.util.UUID

/** The Build and Stats flow of a drafted deck: the basics dialog's count, the mana table and the curve's views. */
class DraftStatsFlowTest : E2e() {
    // DRF-103, DRF-111, DRF-114, DRF-116, DRF-180: Alice drafts a mana rock (Ember Shard, "{T}: Add {R}.", red by its colour as in the donut), adds a Mountain
    // in the basics dialog and sees its count follow, then reads her Stats and switches the curve to Color and back
    @Test fun basicsCountThenStatsTableAndCurveToggle() {
        launchPlayers()
        val name = "E2E pod ${UUID.randomUUID().toString().take(6)}"
        val pod = ServerSetup.draftingPod(alice.account, bob.account, name, cards = listOf(TestCards.shard))
        waitUntil("Alice's menu") { alice.shows(hasTestTag("menu-PODS")) }
        alice.tagged("menu-PODS").performClick()
        waitUntil("the pod in Alice's list") { alice.showsText(name) }
        alice.text(name).performScrollTo().performClick()
        waitUntil("Alice's pack") { alice.pods.state.detail?.takeIf { it.id == pod }?.draft?.head != null }
        val pack = alice.pods.state.detail!!.draft!!.head!!
        alice.pods.pick(listOf(pack.cards.first().id), emptyList())
        waitUntil("Ember Shard in Alice's pool") { alice.pods.state.detail?.draft?.pool?.size == 1 && !alice.pods.state.uncertain }

        alice.text("Build").performClick()
        waitUntil("Alice's Build") { alice.shows(hasTestTag("pod-build-basics")) }
        alice.tagged("pod-build-basics").performClick()
        waitUntil("the basics count") { alice.shows(hasTestTag("pod-build-basics-count") and hasText("Basics 0 · Lands 0")) }
        alice.described("Add Mountain").performClick()
        waitUntil("the count following the stepper") { alice.shows(hasTestTag("pod-build-basics-count") and hasText("Basics 1 · Lands 1")) }
        alice.text("Done").performClick()

        alice.text("Stats").performClick()
        waitUntil("Alice's Stats") { alice.shows(hasTestTag("pod-stats")) }
        // DRF-180: the subtype row under the types counts the Mountain's land type
        alice.tagged("pod-stats").performScrollToNode(hasTestTag("pod-stats-subtypes"))
        waitUntil("the Mountain in the subtype row") { alice.shows(hasTestTag("pod-stats-subtype-Mountain") and hasText("Mountain 1")) }
        alice.tagged("pod-stats").performScrollToNode(hasTestTag("pod-stats-sources"))
        waitUntil("the Mountain and the rock as red sources, with no pips") {
            alice.shows(hasTestTag("pod-stats-mana-R") and hasAnyDescendant(hasText("2 sources · 100%"))) &&
                alice.shows(hasTestTag("pod-stats-mana-R") and hasAnyDescendant(hasContentDescription("R: 0% of pips, 100% of sources"))) &&
                alice.shows(hasTestTag("pod-stats-sources") and hasText("Sources: 1 land, 1 other"))
        }
        alice.tagged("pod-stats").performScrollToNode(hasTestTag("pod-stats-patterns"))
        waitUntil("the rock as a chip without pips") { alice.shows(hasTestTag("pod-stats-no-pips") and hasText("1× ∅")) }

        alice.tagged("pod-stats").performScrollToNode(hasTestTag("pod-stats-curve"))
        waitUntil("the curve on Type at first") {
            alice.shows(hasTestTag("pod-stats-curve") and hasContentDescription("0: 0 creature, 1 noncreature", substring = true))
        }
        alice.tagged("pod-stats-curve-color").performClick()
        waitUntil("the curve by colour, with its legend") {
            alice.shows(hasTestTag("pod-stats-curve") and hasContentDescription("0: 1 R,", substring = true)) &&
                alice.shows(hasTestTag("pod-stats-curve-legend"))
        }
        alice.tagged("pod-stats-curve-type").performClick()
        waitUntil("the curve back on Type") {
            alice.shows(hasTestTag("pod-stats-curve") and hasContentDescription("0: 0 creature, 1 noncreature", substring = true)) &&
                !alice.shows(hasTestTag("pod-stats-curve-legend"))
        }
    }
}
