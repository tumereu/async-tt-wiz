package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import org.junit.Test
import java.util.UUID

/** Stats' mana sources from the cards' Automatum definitions (DRF-114, DRF-115). */
class ManaSourcesTest : E2e() {
    // DRF-114, DRF-115: Alice drafts a mana rock (Ember Shard, "{T}: Add {R}."); her Stats count it as another red source
    // beside the (no) lands, and the total line says so
    @Test fun aDraftedManaRockCountsAsAnotherSource() {
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
        alice.text("Stats").performClick()
        waitUntil("Alice's Stats") { alice.shows(hasTestTag("pod-stats")) }
        alice.tagged("pod-stats").performScrollToNode(hasTestTag("pod-stats-sources"))
        waitUntil("the rock as a red source in Alice's Stats") {
            alice.shows(hasTestTag("pod-stats-mana-R") and hasAnyDescendant(hasText("0 + 1"))) &&
                alice.shows(hasTestTag("pod-stats-sources") and hasText("Sources: 0 lands, 1 other"))
        }
    }
}
