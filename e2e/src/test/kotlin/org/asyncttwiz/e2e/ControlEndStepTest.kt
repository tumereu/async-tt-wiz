package org.asyncttwiz.e2e

import arcanum.api.TurnStep
import org.asyncttwiz.protocol.game.AssumeResolves
import org.asyncttwiz.protocol.game.GameDevice
import org.asyncttwiz.protocol.game.PlaySettings
import org.asyncttwiz.protocol.game.ResponseMode
import org.junit.Test
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Owner bug 2026-11-07: a player in Control couldn't stop at their own end step: in their second main phase the step arrow
 * read "End turn" and its tap went past the end step. Now it reads "End step" and stops there, with them holding priority.
 */
class ControlEndStepTest : E2e() {
    // TRN-063, STK-036 (owner bug 2026-11-07): Alice in Control taps her way to her second main phase, where the arrow reads
    // "End step"; its tap stops at her own end step with her holding priority, Bob's phone (Assume: Never) waiting there
    // meanwhile; she casts an Idle Glimmer at her end step
    @Test fun controlStopsAtYourOwnEndStep() {
        startGame(TestDeck.glimmers(), TestDeck.thirds(), aliceSettings = PlaySettings(mode = ResponseMode.FullControl),
            bobSettings = PlaySettings().assuming(AssumeResolves.Never), deal = { a, _ -> "Idle Glimmer" in hand(a) })
        bothOpen()
        alice.tap("Keep")
        bob.tap("Keep")
        // Alice taps the step arrow (never End turn) wherever she gets priority on the way; Bob plays along
        waitUntil("Alice's second main phase", along = listOf(bob)) {
            val there = alice.inOwnMain(TurnStep.PostcombatMain)
            if (!there && (alice.status == GameDevice.Status.YourMove || alice.status == GameDevice.Status.Drafting) &&
                alice.engine { device.session.pending.isEmpty() })
                alice.stepArrow()?.takeIf { it in listOf("Pass", "Next", "To combat", "No attack", "Resolve") }
                    ?.let { arrow -> alice.once { alice.tap(arrow) } }
            there && quiet()
        }
        assertEquals("End step", alice.stepArrow(), "in Control the second main phase's arrow goes on to the end step")
        alice.tap("End step")
        waitUntil("Alice holding priority at her own end step", along = listOf(bob)) {
            alice.state.let { s -> s.turn.active == alice.me && s.turn.step == TurnStep.End && s.priority.holder == alice.me } &&
                alice.status == GameDevice.Status.YourMove && quiet()
        }
        repeat(3) { run(skip = Duration.ofSeconds(20)) }
        alice.state.let { s ->
            assertEquals(TurnStep.End, s.turn.step, "Alice's phone still at her end step")
            assertEquals(alice.me, s.priority.holder)
        }
        assertEquals("End turn", alice.stepArrow(), "at the end step the arrow hands the turn over")
        bob.state.let { s ->
            assertEquals(alice.me, s.turn.active, "Bob's phone still in Alice's turn")
            assertEquals(TurnStep.End, s.turn.step, "Bob's phone not past Alice's end step")
        }
        assertEquals(GameDevice.Offer.AssumeResolves, (bob.status as? GameDevice.Status.Waiting)?.offer, "Bob's phone waits for Alice")
        // She acts there: an instant cast at her end step
        alice.playFromHand("Idle Glimmer", this)
        waitUntil("the Glimmer on the Stack at Alice's end step, on both phones") {
            listOf(alice, bob).all { p -> p.state.let { s -> s.stack.size == 1 && s.turn.step == TurnStep.End && s.turn.active == alice.me } }
        }
        assertTrue(alice.leaks.leaks.isEmpty() && bob.leaks.leaks.isEmpty())
    }
}
