package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
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

    /** Asserts Inspect is up, in a window of the dialog [dialog] and so drawn over it, not in the app's window behind it. */
    private fun assertInspectOverDialog(player: E2ePlayer, dialog: String) {
        fun viewOf(matcher: androidx.compose.ui.test.SemanticsMatcher) =
            (player.node(matcher).fetchSemanticsNode().root as androidx.compose.ui.node.RootForTest as androidx.compose.ui.platform.ViewRootForTest).view
        val inspect = hasContentDescription("Card inspection content")
        kotlin.test.assertTrue(player.shows(inspect), "Inspect is up: ${player.screen()}")
        val parent = (viewOf(inspect).rootView.layoutParams as android.view.WindowManager.LayoutParams).token
        kotlin.test.assertEquals(viewOf(hasTestTag(dialog)).windowToken, parent, "Inspect's window hangs on the dialog's, so it draws over the dialog")
    }

    // DRF-165 (owner, beta9): holding a card in the draft history shows it over the dialog, not behind it
    @Test fun holdingACardInTheHistoryShowsItOverTheDialog() {
        launchPlayers()
        val name = "E2E History hold ${UUID.randomUUID().toString().take(6)}"
        val pod = ServerSetup.draftingPod(alice.account, bob.account, name, settings = DraftSettings(packsPerPlayer = 2, cardsPerPack = 3))
        val kept = pickOver(pod, alice)
        waitUntil("Alice's menu") { alice.shows(hasTestTag("menu-PODS")) }
        alice.tagged("menu-PODS").performClick()
        waitUntil("the pod in Alice's list") { alice.showsText(name) }
        alice.text(name).performScrollTo().performClick()
        waitUntil("Alice's History button") { alice.shows(hasTestTag("pod-history")) }
        alice.tagged("pod-history").performClick()
        val card = hasTestTag("pod-history-0-$kept")
        waitUntil("her pick in the history") { alice.shows(card) }
        holding(alice.node(card)) { assertInspectOverDialog(alice, "pod-history-dialog") }
        kotlin.test.assertTrue(!alice.shows(hasContentDescription("Card inspection content")), "closed on release")
    }

    /** [player] takes the first card of the pack in front of them, over the server. */
    private fun pickOver(pod: String, player: E2ePlayer): String = runBlocking {
        org.asyncttwiz.android.PodApi(E2eStack.origin).use { api ->
            val detail = api.detail(player.account.token, pod)
            val head = detail.draft!!.head!!
            val card = head.cards.first().id
            api.pick(player.account.token, pod, PickPod(UUID.randomUUID().toString(), detail.draftAttemptId!!, head.id, listOf(card)))
            card
        }
    }

    // DRF-165 (owner, beta9): with the draft over, the History button is in the pod's header on the tabs a builder uses, and
    // the history still says what was taken from a pack that came round again: what the opponent may be building
    @Test fun historyIsThereWhileBuildingAndAfter() {
        launchPlayers()
        val name = "E2E History build ${UUID.randomUUID().toString().take(6)}"
        val pod = ServerSetup.draftingPod(alice.account, bob.account, name, settings = DraftSettings(packsPerPlayer = 1, cardsPerPack = 3))
        // Everyone takes what's in front of them until the draft is over
        repeat(8) {
            for (player in listOf(alice, bob)) runCatching { pickOver(pod, player) }
        }
        val drafted = runBlocking { org.asyncttwiz.android.PodApi(E2eStack.origin).use { it.detail(alice.account.token, pod) } }
        kotlin.test.assertEquals(PodPhase.DRAFTED, drafted.phase, "the draft is over")
        waitUntil("Alice's menu") { alice.shows(hasTestTag("menu-PODS")) }
        alice.tagged("menu-PODS").performClick()
        waitUntil("the pod in Alice's list") { alice.showsText(name) }
        alice.text(name).performScrollTo().performClick()
        waitUntil("the Build tab with Alice's History button") {
            alice.shows(hasTestTag("pod-build") or hasTestTag("pod-tabs")) && alice.shows(hasTestTag("pod-history"))
        }
        // The Stats tab has it too, and it opens from there
        alice.text("Stats").performClick()
        waitUntil("History on the Stats tab") { alice.shows(hasTestTag("pod-history")) }
        alice.text("Build").performClick()
        waitUntil("History on the Build tab") { alice.shows(hasTestTag("pod-history")) }
        alice.tagged("pod-history").performClick()
        waitUntil("the picks and the wheel in the history") {
            val picks = alice.pods.history?.picks.orEmpty()
            val wheel = picks.indexOfFirst { it.takenSince.isNotEmpty() }
            alice.showsText("Pack 1 · Pick 1") && wheel >= 0 && alice.shows(hasTestTag("pod-history-since-$wheel"))
        }
        val picks = alice.pods.history!!.picks
        val first = hasTestTag("pod-history-0-${picks.first().kept.single()}")
        waitUntil("her first pick") { alice.shows(first) }
        holding(alice.node(first)) { assertInspectOverDialog(alice, "pod-history-dialog") }
        alice.described("Close draft history").performClick()
        waitUntil("the history closed, back at Build") { !alice.shows(hasTestTag("pod-history-dialog")) && alice.shows(hasTestTag("pod-history")) }
    }

    // DRF-165, DRF-043 (owner, 2026-11-10 and 2026-11-11): the history shows a header per round naming where its packs
    // went and a seat ring of the pod's real seating; Returning packs only leaves just the packs that came back to you in a
    // round (here the first pack's last card, given automatically), and stays on for this pod when the history opens again
    @Test fun historyHasRoundHeadersAndReturningPacksOnly() {
        launchPlayers()
        val name = "E2E History rounds ${UUID.randomUUID().toString().take(6)}"
        val pod = ServerSetup.draftingPod(alice.account, bob.account, name, settings = DraftSettings(packsPerPlayer = 2, cardsPerPack = 3))
        // Round 1: two picks each (the last cards come back automatically); round 2: one pick each
        repeat(3) { for (player in listOf(alice, bob)) pickOver(pod, player) }
        waitUntil("Alice's menu") { alice.shows(hasTestTag("menu-PODS")) }
        alice.tagged("menu-PODS").performClick()
        waitUntil("the pod in Alice's list") { alice.showsText(name) }
        alice.text(name).performScrollTo().performClick()
        waitUntil("Alice's History button") { alice.shows(hasTestTag("pod-history")) }
        alice.tagged("pod-history").performClick()
        // Two players: the packs always go to the other one, with no way round named
        val round1 = hasTestTag("pod-history-round-1") and hasText("Round 1 · passed to ${bob.name}")
        val round2 = hasTestTag("pod-history-round-2") and hasText("Round 2 · passed to ${bob.name}")
        waitUntil("both rounds in Alice's history") {
            alice.pods.history?.picks?.count { !it.automatic } == 3 && alice.shows(round1) && alice.showsText("Pack 1 · Pick 1")
        }
        // The seating is the server's, both players in it; the ring shows it
        val seating = kotlinx.serialization.json.Json.decodeFromString<List<String>>(
            E2eStack.query("select (draft_state::jsonb)->'seating' from pod where id = '$pod'").single())
        kotlin.test.assertEquals(seating, alice.pods.history!!.seating)
        kotlin.test.assertEquals(setOf(alice.account.identity.accountId, bob.account.identity.accountId), seating.toSet())
        waitUntil("the seat ring") { alice.shows(hasTestTag("pod-history-seats")) }
        alice.node(hasTestTag("pod-history-picks")).performScrollToNode(round2)
        waitUntil("round 2's header") { alice.shows(round2) }
        val returning = alice.pods.history!!.picks.withIndex().filter { it.value.returning }
        kotlin.test.assertEquals(listOf(Triple(1, 3, true)), returning.map { Triple(it.value.round, it.value.pick, it.value.automatic) },
            "the one pack that came back to Alice: her first pack's last card")
        alice.tagged("pod-history-returning").performClick()
        val onlyReturning = {
            alice.shows(hasTestTag("pod-history-header-${returning.single().index}")) && alice.shows(round1) &&
                !alice.showsText("Pack 1 · Pick 1") && !alice.shows(hasTestTag("pod-history-round-2"))
        }
        waitUntil("only the returning pack") { onlyReturning() }
        // Remembered for this pod on the phone: closed and opened again, the history still shows only the returning pack
        alice.node(hasContentDescription("Close draft history")).performClick()
        waitUntil("the history closed") { !alice.shows(hasTestTag("pod-history-dialog")) }
        alice.tagged("pod-history").performClick()
        waitUntil("the returning pack only, again") { alice.pods.history != null && onlyReturning() }
        alice.tagged("pod-history-returning").performClick()
        waitUntil("every pick again") { alice.showsText("Pack 1 · Pick 1") }
    }
}
