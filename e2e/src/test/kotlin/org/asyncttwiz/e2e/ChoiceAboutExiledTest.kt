package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasContentDescription
import arcanum.api.CardType
import org.junit.Test
import kotlin.test.assertTrue

/**
 * Problem report MDLPM7 (owner, 2026-11-18, Bre of Clan Stoutarm; playtest notes 2026-11-19, N18): "cards from bre did not show
 * what they were". A "You may cast that card …" asked as the ability resolved named no card: the choice's "About:" took its cards
 * only from what the ability triggered on and its targets, never from what its own resolution exiled.
 */
class ChoiceAboutExiledTest : E2e() {
    // STK-030: Alice casts her free Ember Unearthing: it exiles cards from her library's top until a nonland card; the "you may cast
    // that card" choice that follows shows "About:" with that card, drawn as a card
    @Test fun aMayToCastTheExiledCardShowsThatCard() {
        startGame(TestDeck("Unearthings", listOf(TestCards.mountain to 14, TestCards.unearthing to 13, TestCards.spark to 13)),
            TestDeck.glimmers(), deal = { a, _ ->
                // Her library's first nonland card a Storm Spark: not the source's own name, which the panel shows anyway
                "Ember Unearthing" in hand(a) && library(a).firstOrNull { it != "Mountain" } == "Storm Spark"
            })
        bothOpen()
        bothKeep(bobAway = false)
        waitUntil("Alice's Main 1", along = listOf(bob)) { alice.inOwnMain() }
        alice.playFromHand("Ember Unearthing", this)
        // HIS-233 (owner 2026-11-19): the exile from her library shows first as a reveal on her table too, closed by its OK
        waitUntil("the exile from Alice's library shown as a reveal", along = listOf(bob)) {
            alice.shown.reveal?.exiled != null && alice.showsRevealOk()
        }
        // (Mountains over the Spark go with it: "You exiled 3 cards from your library")
        assertTrue(alice.showsText("You exiled ", substring = true) && alice.showsText(" from your library", substring = true),
            "the exile's panel: ${alice.screen()}")
        alice.revealOk()
        waitUntil("the choice to cast the exiled card", along = listOf(bob)) {
            alice.shown.reveal == null && alice.showsText("About:") && alice.state.pending?.decision is arcanum.engine.decision.Decision.May
        }
        val found = alice.state.exile.mapNotNull { alice.state.obj(it) }
            .single { CardType.Land !in (it.definition?.faces?.first()?.types.orEmpty()) }.definition!!.faces.first().name
        kotlin.test.assertEquals("Storm Spark", found, "the card exiled")
        assertTrue(alice.shows(hasContentDescription(found)), "the exiled $found about the choice: ${alice.screen()}")
    }
}
