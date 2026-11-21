package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.asyncttwiz.android.PodTab
import org.asyncttwiz.android.ShellDestination
import org.asyncttwiz.protocol.*
import org.junit.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Draft bot seats (owner, 2026-11-21): a pod's creator adds bots in setup, and two people draft with them to the end. */
class DraftBotSeatsTest : E2e() {
    // DRF-296, DRF-297, DRF-298, DRF-301, DRF-030: Alice taps + twice on the Bots stepper beside her Booster pod's invite button
    // in setup (there is no Bots tile) and sees Bot 1 and Bot 2 listed as bots (Members 3/8); Bob joins and the draft starts;
    // the bots are in the header's avatars and pick at once on the server, so Alice (on her phone) and Bob (over the API) draft
    // to the end without ever waiting for a bot; the Games tab then lists only Bob
    @Test fun twoPeopleAndTwoBotsDraftAPodToItsEnd() {
        launchPlayers()
        val name = "E2E bots ${UUID.randomUUID().toString().take(6)}"
        val pod = ServerSetup.setupPod(alice.account, name, settings = DraftSettings(packsPerPlayer = 1, cardsPerPack = 3))
        alice.auth.navigate(ShellDestination.PODS)
        alice.pods.open(pod)
        waitUntil("Alice's pod setup with the Bots stepper") { alice.shows(hasTestTag("pod-bots")) }

        // The stepper: + twice
        for (bots in 1..2) {
            alice.tagged("pod-bots-more").performScrollTo().performClick()
            waitUntil("$bots bots shown") { alice.shows(hasTestTag("pod-bots-value") and hasText("$bots")) }
        }
        waitUntil("the bots saved and listed") {
            alice.pods.state.setup == null && alice.pods.state.pending == 0 && ServerSetup.pod(alice.account, pod).settings.bots == 2 &&
                alice.showsText("Members (3/8)") && alice.showsText("Bot 2")
        }

        ServerSetup.joinAndStart(alice.account, bob.account, pod)
        alice.pods.refresh()
        waitUntil("the draft's avatars with the bots") {
            alice.pods.state.detail?.phase == PodPhase.DRAFTING &&
                alice.shows(hasContentDescription("Bot 1's avatar") and hasAnyAncestor(hasTestTag("pod-draft-members"))) &&
                alice.shows(hasContentDescription("Bot 2's avatar") and hasAnyAncestor(hasTestTag("pod-draft-members")))
        }

        var steps = 0
        while (ServerSetup.pod(alice.account, pod).phase == PodPhase.DRAFTING) {
            check(steps++ < 12) { "the draft didn't end" }
            val server = ServerSetup.pod(alice.account, pod)
            // The bots never hold a pick: one of the people always has one
            assertTrue(server.members.filter { it.bot }.all { it.packsWaiting == 0 }, "${server.members}")
            ServerSetup.pickUntil(listOf(bob.account), pod) { ServerSetup.pod(bob.account, pod).draft?.head == null }
            val head = ServerSetup.pod(alice.account, pod).draft?.head ?: continue
            alice.pods.refresh()
            waitUntil("Alice's pack ${head.id}") { alice.pods.state.detail?.draft?.head?.id == head.id }
            val card = head.cards.first().id
            alice.tagged("pod-pick-card-$card").performScrollTo().performClick()
            waitUntil("the card menu") { alice.shows(hasText("Keep") and hasAnyAncestor(isPopup())) }
            alice.node(hasText("Keep") and hasAnyAncestor(isPopup())).performClick()
            waitUntil("Confirm") { alice.shows(hasTestTag("pod-pick-confirm")) }
            alice.tagged("pod-pick-confirm").performClick()
            waitUntil("the server took Alice's pick") { card in ServerSetup.pod(alice.account, pod).draft!!.pool.map { it.id } }
        }

        val done = ServerSetup.pod(alice.account, pod)
        assertEquals(PodPhase.DRAFTED, done.phase)
        assertEquals(listOf("Bot 1", "Bot 2"), done.members.filter { it.bot }.map { it.account.username })
        assertTrue(done.members.filter { it.bot }.all { it.picksMade > 0 })
        // Four seats, one pack of three each: every seat ends with three cards
        assertEquals(3, done.draft!!.pool.size)
        alice.pods.open(pod, PodTab.GAMES)
        waitUntil("the Games tab lists Bob and no bot") {
            alice.pods.state.tab == PodTab.GAMES && alice.showsText(bob.account.identity.username) && alice.showsText("Members")
        }
        assertTrue(!alice.showsText("Bot 1") && !alice.showsText("Bot 2"), alice.screen())
    }
}
