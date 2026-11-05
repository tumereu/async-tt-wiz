package org.asyncttwiz.e2e

import org.asyncttwiz.protocol.game.GameDevice
import org.junit.Test
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Owner, 2026-11-05 (TRN-015): while the other player must act, the step arrow is hidden in your own turn too.
 */
class StepArrowWaitTest : E2e() {
    // TRN-015, TRN-004: in Alice's own turn she casts an Ember Herald while Bob is online: the game waits for him (his
    // pause) and her step arrow isn't shown; once he lets it resolve it is back
    @Test fun theStepArrowIsHiddenInYourOwnTurnWhileTheOtherPlayerMustAct() {
        startGame(TestDeck.thirds(TestCards.herald), TestDeck.thirds(TestCards.herald),
            deal = { a, _ -> "Mountain" in hand(a) && "Ember Herald" in hand(a) })
        bothOpen()
        bothKeep(bobAway = false)
        alice.playFromHand("Mountain", this)
        waitUntil("the Mountain on the battlefield", along = listOf(bob)) { alice.field("Mountain").isNotEmpty() && alice.inOwnMain() }
        assertEquals("To combat", alice.stepArrow())
        alice.playFromHand("Ember Herald", this)
        waitUntil("Alice waiting for Bob at her Herald") { alice.status is GameDevice.Status.Waiting && alice.state.stack.isNotEmpty() }
        repeat(3) { run(skip = Duration.ofMillis(500)) }
        assertIs<GameDevice.Status.Waiting>(alice.status)
        assertEquals(alice.me, alice.state.turn.active, "her own turn")
        assertEquals(null, alice.stepArrow(), "no step arrow while Bob must act")
        waitUntil("Alice's main phase again", along = listOf(bob)) { alice.inOwnMain() && alice.field("Ember Herald").isNotEmpty() }
        assertEquals("To combat", alice.stepArrow())
    }
}
