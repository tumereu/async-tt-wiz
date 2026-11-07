package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.performClick
import arcanum.api.TurnStep
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import java.util.concurrent.CopyOnWriteArrayList
import org.asyncttwiz.android.AppLog
import org.asyncttwiz.android.EngineGame
import org.asyncttwiz.android.EngineShownProbe
import org.asyncttwiz.protocol.game.GameDevice
import org.asyncttwiz.protocol.game.ResponseMode
import org.junit.After
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Owner 2026-11-07 (SYS-077, HIS-109, SYS-009): Alice's own pass crosses Bob's assumption of it on the wire. Alice attacks
 * with her Cinder Brute (2/2, trample) and Bob blocks with three Ash Pups; at the blocks her phone (Normal) waits for her
 * and his waits for her with Assume. Her uplink is held ([NetworkLink.holdOutgoing]) while she passes, so her phone goes
 * on to her division of combat damage (CMB-033) with her pass still on its way; Bob presses Assume and his assumption of
 * her pass reaches the server first, then her phone. Her pass is the same answer: her board and the open division stay
 * exactly as they are (no take-back, nothing played back or made again), and once released the server takes her pass as
 * the assumption's confirmation, nothing refused.
 */
class ConfirmCrossingTest : E2e() {
    @After fun noProbe() { EngineShownProbe.listener = null }

    /** Taps [player]'s main button while it passes or goes on, once their game has settled (DamageDivisionTest's). */
    private fun goOn(player: E2ePlayer) {
        if (!player.settled(this)) return
        player.once {
            if (player.status == GameDevice.Status.YourMove || player.status is GameDevice.Status.Stopped)
                player.stepArrow()?.takeIf { it in listOf("Pass", "Next", "Resolve") }?.let(player::tap)
        }
    }

    private val division = hasContentDescription("Division of combat damage")

    /** Whether [shown]'s board has Alice's division of combat damage open. */
    private fun dividing(shown: EngineGame.Shown): Boolean = shown.engine.pending?.decision is Decision.Divide

    // SYS-077, HIS-109, SYS-009, CMB-033
    @Test fun aPassCrossingTheAssumptionOfItKeepsTheBoardAndTheOpenDivision() {
        startGame(TestDeck("Brutes", listOf(TestCards.mountain to 20, TestCards.brute to 20)),
            TestDeck("Pups", listOf(TestCards.mountain to 10, TestCards.pup to 30)),
            deal = { a, b -> "Cinder Brute" in hand(a) && hand(b).count { it == "Ash Pup" } >= 3 })
        bothOpen()
        bothKeep(bobAway = false)
        // Turn 1: Alice's Brute
        alice.playFromHand("Cinder Brute", this)
        waitUntil("the Brute on the battlefield", along = listOf(bob)) { alice.field("Cinder Brute").isNotEmpty() && alice.inOwnMain() }
        alice.hold("To combat")
        // Turn 2: Bob's three Pups
        waitUntil("Bob's Main 1", along = listOf(alice)) { bob.inOwnMain() }
        for (n in 1..3) {
            bob.playFromHand("Ash Pup", this)
            waitUntil("Pup $n on the battlefield", along = listOf(alice)) { bob.field("Ash Pup").size == n && bob.inOwnMain() }
        }
        bob.hold("To combat")
        waitUntil("Alice's second Main 1", along = listOf(bob)) { alice.inOwnMain() && alice.state.turn.number >= 3 }
        // Alice in Normal: the blocks are her pause, so her phone waits for her there, and so does Bob's (STK-036, HIS-096)
        alice.game!!.settings(alice.shown.settings.copy(mode = ResponseMode.Normal))
        waitUntil("Bob's phone knowing Alice's Normal") { bob.engine { device.opponentMode } == ResponseMode.Normal }
        // Turn 3: Alice attacks with the Brute, Bob blocks with the three Pups
        alice.tap("To combat")
        waitUntil("Alice declaring attackers", along = listOf(bob)) { alice.state.turn.step == TurnStep.DeclareAttackers }
        alice.stageAttacker("Cinder Brute", this)
        waitUntil("the Brute staged") { alice.stepArrow() == "Attack (1)" }
        alice.declare("Attack (1)", this)
        waitUntil("Bob declaring blockers") {
            goOn(alice); goOn(bob)
            bob.status == GameDevice.Status.YourMove && bob.stepArrow() == "No blocks"
        }
        for (n in 0..2) {
            bob.pickBlocker("Ash Pup", this, index = n)
            run(skip = java.time.Duration.ofSeconds(1))
            bob.bandCard("Cinder Brute", owner = alice).performClick()
            waitUntil("Pup ${n + 1} blocking") { bob.stepArrow() == "Block (${n + 1})" }
        }
        bob.declare("Block (3)", this)
        waitUntil("Alice's pause at the blocks, Bob waiting for her with Assume") {
            alice.settled(this) && bob.settled(this) && alice.status == GameDevice.Status.YourMove &&
                alice.state.turn.step == TurnStep.DeclareBlockers && bob.showsControl("Assume no response")
        }
        // Her pass goes out slowly: her phone goes on to her division with it still on its way
        alice.link.holdOutgoing()
        alice.tap(requireNotNull(alice.stepArrow()?.takeIf { it in listOf("Pass", "Next", "Resolve") }) { "Alice's pass: ${alice.screen()}" })
        waitUntil("Alice's division open, her pass on its way") {
            alice.current() && alice.shows(division) && alice.engine { device.session.pending.isNotEmpty() }
        }
        val boards = CopyOnWriteArrayList<EngineGame.Shown>()
        EngineShownProbe.listener = { me, shown -> if (me == alice.id) boards += shown }
        val transition = alice.shown.transition?.serial
        val takenBack = AppLog.text().lines().count { "Shown board taken back" in it }
        // Bob presses Assume: his assumption of her pass reaches the server first, then her phone
        bob.tap("Assume no response")
        waitUntil("Bob's assumption of her pass on the server") {
            bob.engine { device.session.pending.isEmpty() && device.session.events.any { it.assumed?.input == Input.PassPriority(alice.me) &&
                it.author == bob.id } && device.model.engine.pending?.decision is Decision.Divide }
        }
        val bobs = bob.engine { device.session.head }
        waitUntil("Bob's events on Alice's phone") { alice.engine { device.session.knows(bobs) } }
        repeat(5) { run(skip = java.time.Duration.ofMillis(200)) }
        assertTrue(alice.shows(division), "her division still open: ${alice.screen()}")
        assertTrue(boards.all(::dividing), "no board without her division since it opened: ${boards.map { it.engine.turn.step }}")
        assertTrue(boards.none { it.transition?.serial != transition }, "nothing moved, jumped or went back")
        assertEquals(bobs, alice.engine { device.session.localHead }, "Bob's events in place of hers, nothing to play back")
        // Her pass reaches the server: the assumption's confirmation
        alice.link.releaseOutgoing()
        waitUntil("her pass taken as the confirmation") { bob.engine { device.localHeads[alice.me] } == bobs && alice.engine { device.localHeads[alice.me] } == bobs }
        repeat(5) { run(skip = java.time.Duration.ofMillis(200)) }
        assertTrue(alice.shows(division), "her division still open: ${alice.screen()}")
        assertTrue(boards.all(::dividing), "no board without her division: ${boards.map { it.engine.turn.step }}")
        assertTrue(boards.none { it.transition?.serial != transition }, "nothing moved, jumped or went back")
        assertEquals(takenBack, AppLog.text().lines().count { "Shown board taken back" in it }, "no board taken back")
        assertFalse(alice.refusalSeen, "nothing refused")
        assertEquals(bobs, alice.engine { device.session.head }, "no new event")
        // She divides on the board she had all along
        alice.tap("OK")
        waitUntil("the combat damage dealt", along = listOf(bob)) {
            goOn(alice); goOn(bob)
            bob.field("Ash Pup").size == 1
        }
        assertFalse(alice.refusalSeen || bob.refusalSeen, "no refusal")
        assertTrue(alice.leaks.leaks.isEmpty() && bob.leaks.leaks.isEmpty())
    }
}
