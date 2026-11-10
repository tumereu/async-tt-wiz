package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import org.asyncttwiz.protocol.PodDeckCategory
import org.junit.Test
import java.util.UUID

/**
 * SYS-083 (owner and a player, 2026-11-09): one swipe rule wherever cards move between sections: right one section up, left
 * one down, in the order Main, Side, Maybe, wrapping; with two sections either way to the other.
 */
class SectionSwipeTest : E2e() {
    // SYS-083, LOB-105: in the deck editor a swipe right on a Main card moves one copy to Maybe (up, wrapping), a swipe left on
    // a Main card one to Side, and a swipe right on that Side card back to Main
    @Test fun theDeckEditorSwipesUpRightAndDownLeft() {
        val deck = TestDeck("E2E Swipes", listOf(TestCards.mountain to 20, TestCards.cub to 10, TestCards.spark to 10))
        ServerSetup.deck(ServerSetup.accounts().first, deck)
        launchPlayers()
        alice.decksShown = true
        waitUntil("Alice's menu") { alice.shows(hasTestTag("menu-DECKS")) }
        alice.tagged("menu-DECKS").performClick()
        waitUntil("her deck in the list") { alice.showsText(deck.name) }
        alice.text(deck.name).performClick()
        waitUntil("the saved deck") { alice.showsText("Edit") && alice.showsText("Mainboard · 40") }
        alice.text("Edit").performClick()
        waitUntil("the Cards tab") { alice.shows(hasContentDescription("Storm Spark, 10 copies, Mainboard")) }
        alice.described("Storm Spark, 10 copies, Mainboard").performTouchInput { swipeRight() }
        waitUntil("one Spark in Maybe") { alice.showsText("39 main · 1 maybe") }
        alice.described("Storm Spark, 9 copies, Mainboard").performTouchInput { swipeLeft() }
        waitUntil("one Spark in Side") { alice.showsText("38 main · 1 side · 1 maybe") }
        alice.tagged("card-list-cards").performScrollToNode(hasContentDescription("Storm Spark, 1 copies, Sideboard"))
        alice.described("Storm Spark, 1 copies, Sideboard").performTouchInput { swipeRight() }
        waitUntil("the Side Spark back in Main") { alice.showsText("39 main · 1 maybe") }
    }

    // SYS-083, DRF-099: in Build a swipe right on a Main card sends it to Maybe, a swipe left on a Main card to Side
    @Test fun buildSwipesUpRightAndDownLeft() {
        launchPlayers()
        val name = "E2E swipes ${UUID.randomUUID().toString().take(6)}"
        val pod = ServerSetup.draftedPod(alice.account, bob.account, name)
        waitUntil("Alice's menu") { alice.shows(hasTestTag("menu-PODS")) }
        alice.tagged("menu-PODS").performClick()
        waitUntil("the pod in Alice's list") { alice.showsText(name) }
        alice.text(name).performScrollTo().performClick()
        waitUntil("Alice's Build") { alice.pods.state.detail?.takeIf { it.id == pod }?.build != null && alice.shows(hasTestTag("pod-build")) }
        fun count(category: PodDeckCategory) = alice.pods.state.detail!!.build!!.copies.count { it.category == category }
        fun mainCard() = alice.nodes(hasContentDescription(", Main", substring = true)).onFirst()
        val main = count(PodDeckCategory.MAIN)
        mainCard().performTouchInput { swipeRight() }
        waitUntil("one card in Maybe") { count(PodDeckCategory.MAYBE) == 1 && count(PodDeckCategory.MAIN) == main - 1 && alice.pods.state.pending == 0 }
        mainCard().performTouchInput { swipeLeft() }
        waitUntil("one card in Side") { count(PodDeckCategory.SIDE) == 1 && count(PodDeckCategory.MAIN) == main - 2 && alice.pods.state.pending == 0 }
    }
}
