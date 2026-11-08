package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import kotlinx.coroutines.runBlocking
import org.asyncttwiz.protocol.*
import org.junit.Test
import java.util.UUID

/** Booster draft history (DRF-165): each player's own picks with the packs as they came and a wheel's cards taken since. */
class DraftHistoryTest : E2e() {
    // DRF-165, DRF-160: two players draft a few picks; each phone's History shows that player's picks in order, keeps
    // marked, and the pack that came round again with the card the other player took from it
    @Test fun historyShowsEachPlayersPicksAndTheWheel() {
        launchPlayers()
        val name = "E2E History ${UUID.randomUUID().toString().take(6)}"
        val pod = ServerSetup.draftingPod(alice.account, bob.account, name,
            settings = DraftSettings(packsPerPlayer = 2, cardsPerPack = 3))
        // Two picks each over the server: their own pack, then the other's; the last card of each pack comes back automatically
        val kept = mutableMapOf<String, MutableList<String>>()
        runBlocking {
            org.asyncttwiz.android.PodApi(E2eStack.origin).use { api ->
                repeat(2) {
                    for (player in listOf(alice, bob)) {
                        val detail = api.detail(player.account.token, pod)
                        val head = detail.draft?.head ?: continue
                        val card = head.cards.first().id
                        api.pick(player.account.token, pod, PickPod(UUID.randomUUID().toString(), detail.draftAttemptId!!, head.id, listOf(card)))
                        kept.getOrPut(player.id) { mutableListOf() } += card
                    }
                }
            }
        }
        for (player in listOf(alice, bob)) {
            waitUntil("${player.name}'s menu") { player.shows(hasTestTag("menu-PODS")) }
            player.tagged("menu-PODS").performClick()
            waitUntil("the pod in ${player.name}'s list") { player.showsText(name) }
            player.text(name).performScrollTo().performClick()
            waitUntil("${player.name}'s History button") { player.shows(hasTestTag("pod-history")) }
            player.tagged("pod-history").performClick()
            waitUntil("${player.name}'s draft history") {
                player.pods.history?.picks?.filter { !it.automatic }?.map { it.kept.single() } == kept.getValue(player.id) &&
                    player.showsText("Pack 1 · Pick 1") && player.showsText("Pack 1 · Pick 2")
            }
            // Each keep is marked on its card
            val history = player.pods.history!!
            history.picks.forEachIndexed { index, pick ->
                waitUntil("${player.name}'s keep of pick ${index + 1} marked") {
                    player.shows(hasTestTag("pod-history-$index-${pick.kept.single()}") and hasContentDescription("kept", substring = true))
                }
            }
            // The wheel: a pack seen twice lists what the other player took from it meanwhile
            val wheel = history.picks.indexOfFirst { it.takenSince.isNotEmpty() }
            if (wheel >= 0) waitUntil("${player.name}'s wheel marks") { player.shows(hasTestTag("pod-history-since-$wheel")) }
        }
        // Each player: two picks of their own and their first pack's last card given automatically, with the one card the
        // other player took from it meanwhile (in time order: the award may come before their second pick); the draft
        // goes on in round 2
        for (player in listOf(alice, bob)) {
            val picks = player.pods.history!!.picks
            kotlin.test.assertEquals(setOf(Triple(1, false, false), Triple(2, false, false), Triple(3, true, true)),
                picks.map { Triple(it.pick, it.takenSince.isNotEmpty(), it.automatic) }.toSet())
        }
    }
}
