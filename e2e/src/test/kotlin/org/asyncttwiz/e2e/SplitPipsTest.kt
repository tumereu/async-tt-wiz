package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import org.junit.Test
import java.util.UUID

/**
 * Owner bug, playtest 2026-11-05 evening: a draft deck's Stats left out the red pips of Road // Ruin, a split card: only
 * the front half's cost was counted.
 */
class SplitPipsTest : E2e() {
    // DRF-114, DRF-116: Alice drafts a split card ({2}{G} // {1}{R}{R}); her Stats count both halves' pips, the red ones too
    @Test fun statsCountBothHalvesPipsOfASplitCard() {
        launchPlayers()
        val name = "E2E pod ${UUID.randomUUID().toString().take(6)}"
        val pod = ServerSetup.draftingPod(alice.account, bob.account, name, cards = listOf(TestCards.trailBlaze))
        waitUntil("Alice's menu") { alice.shows(hasTestTag("menu-PODS")) }
        alice.tagged("menu-PODS").performClick()
        waitUntil("the pod in Alice's list") { alice.showsText(name) }
        alice.text(name).performScrollTo().performClick()
        waitUntil("Alice's pack") { alice.pods.state.detail?.takeIf { it.id == pod }?.draft?.head != null }
        val pack = alice.pods.state.detail!!.draft!!.head!!
        alice.pods.pick(listOf(pack.cards.first().id), emptyList())
        waitUntil("Trail // Blaze in Alice's pool") { alice.pods.state.detail?.draft?.pool?.size == 1 && !alice.pods.state.uncertain }
        alice.text("Stats").performClick()
        waitUntil("Alice's Stats") { alice.shows(hasTestTag("pod-stats")) }
        alice.tagged("pod-stats").performScrollToNode(hasTestTag("pod-stats-mana"))
        waitUntil("two red pips and one green in Alice's Stats") {
            alice.shows(hasTestTag("pod-stats-mana-R") and hasAnyDescendant(hasText("2 / 0"))) &&
                alice.shows(hasTestTag("pod-stats-mana-G") and hasAnyDescendant(hasText("1 / 0")))
        }
    }
}
