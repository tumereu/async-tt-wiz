package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performClick
import arcanum.engine.decision.Decision
import org.asyncttwiz.android.AppLog
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Owner problem report 8KK2M6 (prod 0.3.1, 2026-11-23): "tapping hand card says nobody holds prio now -- hand cards should just
 * be faded out and uninteractable?" He was choosing the card to discard while his spell resolved (draw, then discard); a tap
 * on a card of his hand row shook it, saying "Nobody holds priority now." While nobody holds priority his hand row is dimmed
 * and a tap on it does nothing; holding a card still inspects it. Alice casts an Ember Schemer, which connives as it enters
 * (draw, then discard): her own discard choice, and Bob's wait for it, are such points.
 */
class HandWithoutPriorityTest : E2e() {
    private val inspection = hasTestTag("card-inspection")

    // ZON-137, TBL-128, TBL-212, ZON-095, STK-032, TBL-104 (report 8KK2M6, owner 2026-11-23): Alice's discard while her Schemer's trigger resolves:
    // her hand row dimmed, a tap on it does nothing (no shake, no bubble, nothing raised), holding a card inspects it; Bob, waiting
    // for her choice, is the same
    @Test fun nobodyHoldsPriorityDimsTheHandRowAndATapDoesNothing() {
        startGame(TestDeck.schemers(), TestDeck.schemers(), deal = { a, b -> "Ember Schemer" in hand(a) && hand(b).isNotEmpty() })
        bothOpen()
        bothKeep(bobAway = false)
        alice.playFromHand("Ember Schemer", this)
        waitUntil("Alice's discard choice", along = listOf(bob)) {
            alice.state.pending?.decision is Decision.ChooseObjects && alice.current() && bob.state.pending != null
        }
        assertIs<Decision.ChooseObjects>(alice.state.pending?.decision)
        assertNull(alice.state.priority.holder, "nobody holds priority while she chooses")
        for (player in listOf(alice, bob)) {
            run()
            val hand = player.state.player(player.me).hand
            val tapped = assertNotNull(cardName(player.state, hand.first()))
            val from = player.shownTexts.size
            player.handCard(tapped).performClick()
            repeat(3) { run() }
            val told = player.shownTexts.drop(from)
            assertFalse(told.any { "holds priority now" in it || "is choosing" in it }, "${player.name} told nothing: $told")
            assertFalse(player.shows(hasTestTag("refusal-bubble")), "no bubble on ${player.name}'s table: ${player.screen()}")
            assertNull(player.raisedInHand(), "nothing raised in ${player.name}'s hand")
            assertTrue(AppLog.text().lines().any { "Tap on hand card" in it && "nobody holds priority" in it },
                "the tap logged as done nothing")
            assertEquals(hand, player.state.player(player.me).hand, "nothing left ${player.name}'s hand")
            assertTrue(AppLog.text().lines().any { "Hand row of ${player.id.take(8)} dimmed: nobody holds priority" in it },
                "${player.name}'s hand row dimmed")
            // TBL-104: holding a card of the dimmed row still inspects it
            holding(player.handCard(tapped)) { assertTrue(player.shows(inspection), "${player.name}'s hand card inspected: ${player.screen()}") }
            assertFalse(player.shows(inspection), "Inspect closed once the finger lifted")
        }
        assertIs<Decision.ChooseObjects>(alice.state.pending?.decision, "still her choice")
        assertFalse(alice.refusalSeen || bob.refusalSeen, "no refusal: ${alice.shownTexts} / ${bob.shownTexts}")
    }
}
