package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import org.asyncttwiz.android.ShellDestination
import org.asyncttwiz.protocol.*
import org.junit.Test
import java.util.UUID
import kotlin.test.assertEquals

/**
 * Custom booster (owner, 2026-11-19): Booster with a list of passes, "Pick n, Burn k", set by the pod's creator; the
 * cards left when the passes run out are thrown away.
 */
class CustomBoosterTest : E2e() {
    // DRF-260, DRF-261, DRF-262, DRF-263, DRF-264: Alice edits her Custom booster's passes in pod setup to "pick 2, burn 1" then
    // "pick 1"; Bob's invitation reads them; in the draft Alice's pick screen asks each pass, she keeps two and burns one of
    // her 6-card pack, Bob takes one of its last 3 and the other two are thrown away; her history marks her burn
    @Test fun aCustomBoosterPodDraftsItsPassesWithABurnAndADiscardAtTheEnd() {
        launchPlayers()
        val name = "E2E Custom booster ${UUID.randomUUID().toString().take(6)}"
        val pod = ServerSetup.setupPod(alice.account, name, settings = DraftSettings.customBooster().copy(
            packsPerPlayer = 1, cardsPerPack = 6, passes = listOf(DraftPass(1, 1), DraftPass(1, 0))))
        alice.auth.navigate(ShellDestination.PODS)
        alice.pods.open(pod)
        waitUntil("Alice's pod setup with its passes") { alice.shows(hasTestTag("pod-passes")) && alice.showsText("Passes (2)") }
        waitUntil("the mode reads Custom booster") { alice.showsText("Custom booster") }

        // Pass 1 picks 2: the number dialog
        val field = hasSetTextAction() and hasAnyAncestor(isDialog())
        alice.tagged("pod-pass-0-pick").performScrollTo().performClick()
        waitUntil("the Pick dialog") { alice.shows(field) && alice.showsText("Pass 1 · Pick") }
        alice.node(field).performTextReplacement("2")
        alice.node(hasClickAction() and hasAnyDescendant(hasText("Save")) and hasAnyAncestor(isDialog())).performClick()
        waitUntil("the dialog closed") { !alice.shows(field) }
        // Add pass repeats the last; a pass can be removed again
        alice.tagged("pod-pass-add").performScrollTo().performClick()
        waitUntil("a third pass") { alice.showsText("Passes (3)") && alice.shows(hasTestTag("pod-pass-2")) }
        alice.tagged("pod-pass-2-remove").performScrollTo().performClick()
        waitUntil("two passes again") { alice.showsText("Passes (2)") && !alice.shows(hasTestTag("pod-pass-2")) }
        val passes = listOf(DraftPass(2, 1), DraftPass(1, 0))
        waitUntil("the passes saved") {
            alice.pods.state.setup == null && alice.pods.state.pending == 0 && ServerSetup.pod(alice.account, pod).settings.passes == passes
        }

        // Bob's invitation tells the passes; he accepts on his phone
        ServerSetup.invitePod(alice.account, bob.account, pod)
        bob.auth.navigate(ShellDestination.PODS)
        bob.pods.refresh()
        waitUntil("Bob's invitation with the passes") {
            bob.shows(hasTestTag("pod-invitation-passes") and hasText("Custom booster: Pick 2 · Burn 1, Pick 1"))
        }
        bob.node(hasClickAction() and hasAnyDescendant(hasText("Accept"))).performClick()
        waitUntil("Bob a member") { ServerSetup.pod(alice.account, pod).members.size == 2 }
        ServerSetup.startPod(alice.account, pod)

        // Alice's own pack, pass 1: pick 2 of 6, burn 1
        alice.pods.refresh()
        waitUntil("Alice's first pack") { alice.pods.state.detail?.draft?.head?.cards?.size == 6 }
        waitUntil("the pass line") { alice.shows(hasTestTag("pod-pick-pass") and hasText("Pass 1/2 · Pick 2 of 6 · Burn 1")) }
        val own = alice.pods.state.detail!!.draft!!.head!!
        val (keep1, keep2, burn) = own.cards.map { it.id }
        fun mark(id: String, how: String) {
            alice.tagged("pod-pick-card-$id").performScrollTo().performClick()
            waitUntil("the card menu") { alice.shows(hasText(how) and hasAnyAncestor(isPopup())) }
            alice.node(hasText(how) and hasAnyAncestor(isPopup())).performClick()
            waitUntil("$id marked $how") { alice.shows(hasTestTag("pod-pick-card-$id") and hasStateDescription(how)) }
        }
        mark(keep1, "Keep"); mark(keep2, "Keep"); mark(burn, "Burn")
        alice.tagged("pod-pick-confirm").performClick()
        waitUntil("the server took the pick") {
            ServerSetup.pod(alice.account, pod).draft!!.pool.map { it.id }.toSet() == setOf(keep1, keep2)
        }

        // Bob picks his own pack (pass 1), then Alice's on its last pass: one of its 3 cards, the other 2 thrown away
        ServerSetup.pickUntil(listOf(bob.account), pod) { ServerSetup.pod(bob.account, pod).draft?.head == null }
        assertEquals(3, ServerSetup.pod(bob.account, pod).draft!!.pool.size)

        // Bob's pack comes to Alice on its last pass: 3 cards left, she picks 1 and the rest are thrown away
        alice.pods.refresh()
        waitUntil("Bob's pack at Alice") { alice.pods.state.detail?.draft?.head?.pick == 2 }
        waitUntil("the last pass's line") {
            alice.shows(hasTestTag("pod-pick-pass") and hasText("Pass 2/2 · Pick 1 of 3 · the rest are thrown away"))
        }
        val last = alice.pods.state.detail!!.draft!!.head!!.cards.first().id
        mark(last, "Keep")
        alice.tagged("pod-pick-confirm").performClick()
        waitUntil("the draft over") { ServerSetup.pod(alice.account, pod).phase == PodPhase.DRAFTED }
        assertEquals(3, ServerSetup.pod(alice.account, pod).draft!!.pool.size)

        // Her history: the burn of pass 1 marked as burned
        alice.pods.refresh()
        waitUntil("Alice's History button") { alice.shows(hasTestTag("pod-history")) }
        alice.tagged("pod-history").performClick()
        waitUntil("the burn in Alice's history") {
            alice.pods.history?.picks?.firstOrNull()?.burned == listOf(burn) &&
                alice.shows(hasTestTag("pod-history-0-$burn") and hasContentDescription("burned", substring = true))
        }
        assertEquals(listOf(2 to 1, 1 to 0), alice.pods.history!!.picks.map { it.kept.size to it.burned.size })
    }

    private fun hasStateDescription(value: String) = androidx.compose.ui.test.SemanticsMatcher.expectValue(
        androidx.compose.ui.semantics.SemanticsProperties.StateDescription, value)
}
