package org.asyncttwiz.e2e

import androidx.compose.ui.test.performClick
import arcanum.engine.decision.Decision
import org.asyncttwiz.protocol.game.PlaySettings
import org.asyncttwiz.protocol.game.ResponseMode
import org.junit.Test
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Owner, 2026-11-26 (after problem report DH9MW5): "it should never make those if there are multiple options". With Autotap off,
 * Quick paid a mana payment while something resolved with Autotap's suggestion by itself. Alice, on Quick with Autotap off, has
 * an Ember Prism and an Ember Geode out and casts an Ember Levy ("you may pay {1} and 2 life"); after her yes the payment waits
 * for the source she taps.
 */
class QuickPaymentTest : E2e() {
    private fun life() = alice.state.player(alice.me).life

    // SYS-209, SYS-063, STK-049, STK-168, CR 118.12: two sources may pay her {1}: she picks one, then Pay
    @Test fun withAutotapOffAPaymentOfSeveralWaysWaitsForThePlayer() {
        startGame(TestDeck("Levies", listOf(TestCards.mountain to 10, TestCards.levy to 10, TestCards.prism to 10, TestCards.geode to 10)),
            aliceSettings = PlaySettings(mode = ResponseMode.AutoSkip, autotap = false),
            deal = { a, _ -> "Ember Levy" in hand(a) && "Ember Prism" in hand(a) && "Ember Geode" in hand(a) })
        bothOpen()
        bothKeep(bobAway = false)
        for (name in listOf("Ember Prism", "Ember Geode")) {
            alice.playFromHand(name, this)
            waitUntil("$name on the battlefield", along = listOf(bob)) { alice.field(name).isNotEmpty() && alice.inOwnMain() && alice.state.stack.isEmpty() }
        }
        val hand = alice.state.player(alice.me).hand.size
        alice.playFromHand("Ember Levy", this)
        waitUntil("the Levy's question", along = listOf(bob)) {
            if (alice.stepArrow() == "Resolve" || alice.stepArrow() == "Pass") alice.once { alice.tap(alice.stepArrow()!!) }
            alice.state.pending?.decision is Decision.May && alice.showsText("Yes")
        }
        alice.text("Yes").performClick()
        waitUntil("her payment open") { alice.state.pending?.decision is Decision.ManaPayment }
        repeat(4) { run(skip = Duration.ofMillis(500)) }
        assertIs<Decision.ManaPayment>(alice.state.pending?.decision, "still hers to pay: ${alice.screen()}")
        assertEquals(20, life(), "nothing paid by itself")
        assertTrue("Quick leaves the player's own ManaPayment" in E2ePlayer.log(), "the reason logged")
        alice.battlefieldCard("Ember Geode").performClick()
        waitUntil("Pay enabled") { alice.showsControl("Pay") }
        alice.tap("Pay")
        waitUntil("paid and a card drawn, on the server", along = listOf(bob)) {
            alice.state.pending == null && alice.state.stack.isEmpty() && alice.state.player(alice.me).hand.size == hand &&
                alice.engine { device.session.pending.isEmpty() }
        }
        assertEquals(18, life(), "2 life paid")
        assertTrue(alice.state.obj(alice.field("Ember Geode").single())!!.tapped, "the Geode she picked paid")
        assertFalse(alice.state.obj(alice.field("Ember Prism").single())!!.tapped, "the Prism stays untapped")
        assertFalse(alice.refusalSeen, "no refusal: ${alice.shownTexts}")
    }
}
