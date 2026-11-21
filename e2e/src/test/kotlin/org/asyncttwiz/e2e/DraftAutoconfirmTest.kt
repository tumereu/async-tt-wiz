package org.asyncttwiz.e2e

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isEnabled
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
 * are all marked, or in Grid the moment a line's arrow is tapped.
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
        // Auto is the tip at Confirm's right end, one shape with it
        val confirmBounds = alice.tagged("pod-pick-confirm").fetchSemanticsNode().boundsInRoot
        val autoBounds = alice.tagged("pod-pick-autoconfirm").fetchSemanticsNode().boundsInRoot
        assertTrue(confirmBounds.right <= autoBounds.left && autoBounds.left - confirmBounds.right < 8f &&
            autoBounds.center.y in confirmBounds.top..confirmBounds.bottom, "Auto right after Confirm: $confirmBounds, $autoBounds")
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

    // DRF-293, DRF-083 (owner, 2026-11-21): a Grid pod for Alice and Bob; Alice's draft starts with Auto off, and a tap on a row's
    // arrow only chooses it (row 2: Bob may have taken row 1 first); she turns Auto on (nothing is sent over the chosen row),
    // then a tap on a column's arrow takes that column at once, without Confirm
    @Test fun autoTakesTheGridLineWhoseArrowIsTapped() {
        launchPlayers()
        val name = "E2E Grid autoconfirm ${UUID.randomUUID().toString().take(6)}"
        val pod = ServerSetup.draftingPod(alice.account, bob.account, name,
            cards = listOf(TestCards.mountain, TestCards.cub, TestCards.spark, TestCards.herald), settings = DraftSettings.grid().copy(grids = 2))
        // Seating is shuffled: Bob may pick from the first grid before Alice
        fun aliceToPick() = ServerSetup.pod(alice.account, pod).grid!!.let { grid ->
            grid.publicView.grids.any { it.id == grid.mine.headGridId && it.currentPlayer == alice.account.id }
        }
        if (!aliceToPick()) ServerSetup.gridPick(bob.account, pod)
        assertTrue(aliceToPick(), "Alice picks next")
        alice.auth.navigate(ShellDestination.PODS)
        alice.pods.open(pod)
        waitUntil("Alice's grid") { alice.shows(hasContentDescription("Select row 2") and isEnabled()) }
        waitUntil("Auto off at the start of the draft") { alice.shows(toggle(on = false)) }
        fun pool() = ServerSetup.pod(alice.account, pod).grid!!.mine.pool
        alice.node(hasContentDescription("Select row 2")).performClick()
        waitUntil("row 2 chosen") { alice.shows(hasTestTag("pod-grid-confirm") and isEnabled()) }
        assertTrue(pool().isEmpty(), "Auto off: the tap only chose the row")
        alice.tagged("pod-pick-autoconfirm").performClick()
        waitUntil("Auto on") { alice.shows(toggle(on = true)) }
        assertTrue(pool().isEmpty(), "turning Auto on over the chosen row sends nothing")
        val board = ServerSetup.pod(alice.account, pod).grid!!.let { grid -> grid.publicView.grids.single { it.id == grid.mine.headGridId } }
        val column = listOf(0, 3, 6).mapNotNull { board.positions[it]?.id }
        alice.node(hasContentDescription("Select column 1")).performClick()
        waitUntil("column 1 on the server without Confirm") { pool().map { it.id }.toSet() == column.toSet() }
    }
}
