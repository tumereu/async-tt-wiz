package org.asyncttwiz.e2e

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.asyncttwiz.android.ShellDestination
import org.asyncttwiz.protocol.*
import org.junit.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Draft autoconfirm (owner, 2026-11-21): with Auto on beside Confirm, a pick goes by itself the moment its keeps and burns
 * are all marked.
 */
class DraftAutoconfirmTest : E2e() {
    private fun hasStateDescription(value: String) = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, value)
    private fun toggle(on: Boolean) = hasTestTag("pod-pick-autoconfirm") and
        SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, if (on) ToggleableState.On else ToggleableState.Off)

    // DRF-293, DRF-294, DRF-044: a Booster pod (keep 1, burn 1) for Alice and Bob; Alice's draft starts with Auto off; she turns
    // it on, marks a keep (nothing is sent) and then a burn: the pick reaches the server at once, without Confirm; her next pack
    // shows with Auto still on, and the keep that completes it (its last card burned by itself) sends that pick too
    @Test fun autoSendsThePickOnceItsMarksAreComplete() {
        launchPlayers()
        val name = "E2E Autoconfirm ${UUID.randomUUID().toString().take(6)}"
        val pod = ServerSetup.draftingPod(alice.account, bob.account, name,
            cards = listOf(TestCards.mountain, TestCards.cub, TestCards.spark, TestCards.herald),
            settings = DraftSettings(packsPerPlayer = 2, cardsPerPack = 4, keep = 1, burn = 1))
        alice.auth.navigate(ShellDestination.PODS)
        alice.pods.open(pod)
        waitUntil("Alice's first pack") { alice.pods.state.detail?.draft?.head?.cards?.size == 4 }
        waitUntil("Auto off at the start of the draft") { alice.shows(toggle(on = false)) }
        alice.tagged("pod-pick-autoconfirm").performClick()
        waitUntil("Auto on") { alice.shows(toggle(on = true)) }

        fun mark(id: String, how: String) {
            alice.tagged("pod-pick-card-$id").performScrollTo().performClick()
            waitUntil("the card menu") { alice.shows(hasText(how) and hasAnyAncestor(isPopup())) }
            alice.node(hasText(how) and hasAnyAncestor(isPopup())).performClick()
        }
        val first = alice.pods.state.detail!!.draft!!.head!!
        val (keep, burn) = first.cards.map { it.id }
        mark(keep, "Keep")
        waitUntil("the keep marked") { alice.shows(hasTestTag("pod-pick-card-$keep") and hasStateDescription("Keep")) }
        assertTrue(ServerSetup.pod(alice.account, pod).draft!!.pool.isEmpty(), "a keep alone sends nothing")
        mark(burn, "Burn")
        waitUntil("the pick on the server without Confirm") {
            ServerSetup.pod(alice.account, pod).draft!!.pool.map { it.id } == listOf(keep)
        }

        // Bob passes his pack to Alice; Auto is still on for her next pick, which goes by itself too
        ServerSetup.pickUntil(listOf(bob.account), pod) { ServerSetup.pod(bob.account, pod).draft!!.pool.isNotEmpty() }
        alice.pods.refresh()
        // Two cards left in it: the keep alone completes the pick, the other card burned by itself (DRF-045)
        waitUntil("Bob's pack at Alice") { alice.pods.state.detail?.draft?.head?.let { it.id != first.id && it.cards.size == 2 } == true }
        waitUntil("Auto still on") { alice.shows(toggle(on = true)) }
        val keep2 = alice.pods.state.detail!!.draft!!.head!!.cards.first().id
        mark(keep2, "Keep")
        waitUntil("the second pick on the server") {
            ServerSetup.pod(alice.account, pod).draft!!.pool.map { it.id }.toSet() == setOf(keep, keep2)
        }
        assertEquals(2, ServerSetup.pod(alice.account, pod).draft!!.pool.size)
    }
}
