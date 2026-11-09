package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.performClick
import org.junit.Test

/** Owner, 2026-11-09: a tap on a graveyard or exile opens its search view at once, in timeline mode too. */
class PileTapTest : E2e() {
    private fun graveyard() = hasContentDescription("${alice.name} graveyard, 1 card", substring = true)
    private fun E2ePlayer.showsView() = shows(hasContentDescription("Graveyard panel,", substring = true))

    // ZON-033, ZON-035: Alice's Idle Glimmer has resolved: a tap on her graveyard opens its view with no menu in between, another
    // closes it; the same in timeline mode, and Bob can look at it too
    @Test fun aTapOpensTheSearchViewLiveAndInTimelineMode() {
        startGame(TestDeck.thirds(TestCards.glimmer), TestDeck.thirds(TestCards.glimmer), deal = { a, _ -> "Idle Glimmer" in hand(a) })
        bothOpen()
        bothKeep(bobAway = false)
        alice.playFromHand("Idle Glimmer", this)
        waitUntil("the Glimmer in Alice's graveyard on both tables", along = listOf(bob)) {
            if (alice.stepArrow() == "Resolve") alice.once { alice.tap("Resolve") }
            alice.shows(graveyard()) && bob.shows(graveyard())
        }

        alice.node(graveyard()).performClick()
        waitUntil("Alice's graveyard view") { alice.showsView() }
        alice.node(graveyard()).performClick()
        waitUntil("closed by another tap") { !alice.showsView() }

        bob.node(graveyard()).performClick()
        waitUntil("Bob's view of her graveyard") { bob.showsView() }

        alice.openTimeline(this)
        waitUntil("Alice in timeline mode") { alice.showsTimelineLook() }
        alice.node(graveyard()).performClick()
        waitUntil("the view in timeline mode") { alice.showsView() }
    }
}
