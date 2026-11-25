package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performClick
import kotlinx.coroutines.runBlocking
import org.asyncttwiz.android.ShellDestination
import org.asyncttwiz.android.podRatingText
import org.asyncttwiz.protocol.*
import org.junit.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Card ratings in the draft history (owner, 2026-11-25): shown once your own draft is done, in a pod of one set with ratings. */
class DraftHistoryRatingsTest : E2e() {
    /** The test cards' invented ratings (tests/fixtures/card-catalog/ratings.tsv), in tenths of a percent. */
    private val rated = mapOf("Test Bear" to 540, "Test Bolt" to 600, "Test Caller" to 500, "Test Knight" to 580)

    // DRF-315, DRF-316, DRF-317: Alice drafts Test Set with a bot; while her draft runs History shows no ratings, citation or By
    // rating; once it is done each rated card of her history carries its win rate, "Ratings: 17Lands" heads the list, and By
    // rating sorts each pack best first, remembered when she opens History again
    @Test fun historyShowsRatingsOnceYourOwnDraftIsDone() {
        launchPlayers(aliceCatalog = true)
        waitUntil("Alice's card records installed") { alice.catalog!!.state.ready }
        val pod = runBlocking {
            org.asyncttwiz.android.PodApi(E2eStack.origin).use { api ->
                api.create(alice.account.token, CreatePod(UUID.randomUUID().toString(), "E2E history ratings", setCode = "TST",
                    settings = DraftSettings(packsPerPlayer = 2, cardsPerPack = 4, bots = 1))).id
            }
        }
        ServerSetup.startPod(alice.account, pod)
        // One pick over the server, so her history has a pack while her draft still runs
        ServerSetup.pickUntil(listOf(alice.account), pod) { ServerSetup.pod(alice.account, pod).picksMade(alice) > 0 }
        alice.auth.navigate(ShellDestination.PODS)
        alice.pods.open(pod)
        waitUntil("Alice's pod while she drafts") {
            alice.pods.state.detail?.takeIf { it.id == pod }?.draftWait != null && alice.shows(hasTestTag("pod-history"))
        }
        alice.tagged("pod-history").performClick()
        waitUntil("her history while she drafts") { alice.pods.history?.picks?.isNotEmpty() == true && alice.showsText("Pack 1 · Pick 1") }
        assertFalse(alice.shows(hasTestTag("pod-ratings-citation")) || alice.shows(hasTestTag("pod-history-by-rating")), alice.screen())
        assertFalse(alice.showsText("%", substring = true), "no win rates before her draft is done: ${alice.screen()}")
        alice.node(androidx.compose.ui.test.hasContentDescription("Close draft history")).performClick()

        ServerSetup.pickUntil(listOf(alice.account), pod) { ServerSetup.pod(alice.account, pod).phase != PodPhase.DRAFTING }
        alice.pods.refresh()
        waitUntil("Alice's drafted pod") { alice.pods.state.detail?.takeIf { it.id == pod }?.phase == PodPhase.DRAFTED }
        alice.tagged("pod-history").performClick()
        waitUntil("the citation and By rating in her history") {
            alice.pods.history?.picks?.isNotEmpty() == true && alice.shows(hasTestTag("pod-ratings-citation")) &&
                alice.shows(hasTestTag("pod-history-by-rating"))
        }
        assertTrue(alice.showsText("Ratings: 17Lands"), alice.screen())
        val history = alice.pods.history!!
        val names = (alice.pods.state.detail!!.definitions + history.definitions).associate { it.digest to it.definition.name }
        // Every rated card of the first pick shows its rating, the others none
        val first = history.picks.first()
        for (card in first.cards) {
            val rating = rated[names[card.definition]]
            val chip = hasTestTag("pod-history-0-${card.id}-rating")
            if (rating == null) assertFalse(alice.shows(chip), "${names[card.definition]} has no rating: ${alice.screen()}")
            else waitUntil("${names[card.definition]}'s ${podRatingText(rating)}") {
                alice.shows(hasTestTag("pod-history-0-${card.id}") and
                    androidx.compose.ui.test.hasContentDescription("rated ${podRatingText(rating)}", substring = true))
            }
        }
        // By rating: the first pack best first, remembered on the device
        alice.tagged("pod-history-by-rating").performClick()
        // The rated cards in reading order, best first (the unrated ones after them)
        val ratedCards = first.cards.filter { rated[names[it.definition]] != null }
        waitUntil("the first pack best first") {
            val order = ratedCards.sortedWith(compareBy({ alice.tagged("pod-history-0-${it.id}").fetchSemanticsNode().positionInRoot.y },
                { alice.tagged("pod-history-0-${it.id}").fetchSemanticsNode().positionInRoot.x }))
            order.map { rated.getValue(names.getValue(it.definition)) }.zipWithNext().all { (a, b) -> a >= b }
        }
        alice.node(androidx.compose.ui.test.hasContentDescription("Close draft history")).performClick()
        alice.tagged("pod-history").performClick()
        waitUntil("By rating still on") {
            alice.shows(hasTestTag("pod-history-by-rating") and androidx.compose.ui.test.isSelected())
        }
    }

    private fun PodDetail.picksMade(player: E2ePlayer) = members.single { it.account.accountId == player.id }.picksMade
}
