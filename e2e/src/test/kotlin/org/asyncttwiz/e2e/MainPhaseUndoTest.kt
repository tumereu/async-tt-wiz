package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.performClick
import arcanum.api.TurnStep
import org.asyncttwiz.protocol.game.GameDevice
import org.junit.Test
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Owner, prod playtest 2026-11-08: a player leaving main 1 by mistake could only go back to the other player's turn (passes
 * aren't undo points) and had to end their turn again. Now the tap is an event on the timeline, "You went to combat", which
 * one Undo returns to: in that main phase, with the draw unchanged, the phone passing nothing there until they act, and
 * the other player having nothing to redo.
 */
class MainPhaseUndoTest : E2e() {
    /** Bob's first turn ended, Alice in her Main 1 after her draw: what her hand held then, by card. */
    private fun aliceDrewInHerMain(): List<String> {
        startGame(bobStarts = true, deal = { _, b -> "Mountain" in hand(b) })
        bothOpen()
        alice.tap("Keep")
        bob.tap("Keep")
        waitUntil("Bob's Main 1", along = listOf(alice)) { bob.inOwnMain() }
        bob.hold("To combat")
        waitUntil("Alice's Main 1 after her draw", along = listOf(bob)) { alice.inOwnMain() && alice.state.turn.active == alice.me }
        waitUntil("both settled") { alice.settled(this) && bob.settled(this) }
        return handOf(alice)
    }

    private fun handOf(player: E2ePlayer) = player.state.player(player.me).hand.map(::atw).sorted()

    /** Alice's tap out of her Main 1, and her phone on past it to her declaration: the pass's event on her line. */
    private fun aliceLeavesHerMain(): String {
        val turn = alice.state.turn.number
        val before = alice.engine { device.session.events.size + device.session.pending.size }
        alice.tap("To combat")
        waitUntil("Alice past her Main 1") { alice.state.turn.number == turn && alice.state.turn.step != TurnStep.PrecombatMain && alice.status == GameDevice.Status.YourMove }
        return alice.engine { (device.session.events + device.session.pending)[before].id }
    }

    /** Alice stands in her Main 1 as she did, her phone making nothing, and her hand as it was. */
    private fun aliceBackInHerMain(hand: List<String>, turn: Int) {
        waitUntil("Alice back in her Main 1") { alice.inOwnMain() && alice.state.turn.number == turn }
        assertEquals(hand, handOf(alice), "her draw unchanged")
        val head = alice.engine { device.session.head }
        repeat(3) { run(skip = Duration.ofMillis(800)) }
        assertTrue(alice.inOwnMain(), "her phone passes nothing there: ${alice.state.turn}")
        assertEquals(head, alice.engine { device.session.head })
        assertEquals("To combat", alice.stepArrow())
        // HIS-084: acting goes on from there
        val still = alice.engine { device.session.tip }
        alice.tap("To combat")
        waitUntil("Alice past her Main 1 again") { alice.state.turn.step != TurnStep.PrecombatMain && alice.engine { device.session.tip } != still }
    }

    // HIS-101, HIS-084, HIS-099, HIS-087: Bob away (he has seen nothing of it): the tap is on the timeline as "You went to
    // combat"; one Undo goes back at once, free, to Alice's Main 1 in this turn, never to Bob's; he comes back to nothing to
    // redo
    @Test fun oneUndoReturnsToTheMainPhaseWhileTheOtherPlayerIsAway() {
        val hand = aliceDrewInHerMain()
        val turn = alice.state.turn.number
        away(bob, alice)
        val pass = aliceLeavesHerMain()
        waitUntil("Alice's events on the server") { alice.engine { device.session.pending.isEmpty() } }
        val game = alice.game!!
        val timeline = game.timeline.events(game, names())
        assertEquals("You went to combat", timeline.single { it.id == pass }.description, timeline.map { it.description }.toString())
        alice.tap("Undo")
        aliceBackInHerMain(hand, turn)
        assertFalse(alice.shows(hasTestTag("rewind-confirm")), "free: no dialog")
        assertTrue(alice.told("Undid: went to combat"), alice.shownTexts.toString())

        bob.back()
        waitUntil("Bob back, waiting for Alice") { bob.game?.shown?.let { !it.behind && it.status is GameDevice.Status.Waiting } == true && !bob.showsWatch() }
        assertEquals(alice.me, bob.state.turn.active, "Bob has nothing to redo: Alice's turn goes on")
    }


    // HIS-084, HIS-087, HIS-099, HIS-100: Bob live, having seen her assumption of his pass: the same rules as ever ask him:
    // Undo opens its dialog at the tap's point; he accepts and has nothing to redo, and she stands in her Main 1
    @Test fun whenTheOtherPlayerHasSeenItUndoAsksThemAtTheTapsPoint() {
        val hand = aliceDrewInHerMain()
        val turn = alice.state.turn.number
        aliceLeavesHerMain()
        waitUntil("Bob having seen it") {
            val head = alice.engine { device.session.head }
            alice.settled(this) && bob.shown.localHead == head && alice.engine { device.localHeads[bob.me] } == head
        }
        alice.tap("Undo")
        val confirm = hasTestTag("rewind-confirm") and isEnabled()
        waitUntil("Alice's Undo dialog at her tap, asking Bob") {
            alice.shows(confirm) && alice.shows(hasTestTag("undo-point") and hasText("You went to combat")) && alice.showsText("Ask ${bob.name}")
        }
        alice.node(confirm).performClick()
        waitUntil("Bob asked") { bob.shows(hasTestTag("request-accept")) }
        bob.tagged("request-accept").performClick()
        aliceBackInHerMain(hand, turn)
        assertEquals(alice.me, bob.state.turn.active, "Alice's turn; Bob has nothing to redo")
    }
}
