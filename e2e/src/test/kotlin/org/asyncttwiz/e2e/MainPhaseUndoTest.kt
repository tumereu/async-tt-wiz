package org.asyncttwiz.e2e

import org.asyncttwiz.android.timeline
import androidx.compose.ui.test.hasTestTag
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

    // HIS-101, HIS-119, HIS-084, HIS-099, HIS-087: Bob away (he has seen nothing of it): the tap is on the timeline as "You went to
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
        // HIS-168 (owner 2026-11-23): her own undo shows no text at the top; her device logs what it took back
        waitUntil("her undo logged") { ownUndoLogged("Undid: went to combat") }
        assertFalse(alice.toldOwnUndo(), alice.shownTexts.toString())

        bob.back()
        waitUntil("Bob back, waiting for Alice") { bob.game?.shown?.let { !it.behind && it.status is GameDevice.Status.Waiting } == true && !bob.showsWatch() }
        assertEquals(alice.me, bob.state.turn.active, "Bob has nothing to redo: Alice's turn goes on")
    }


    // HIS-084, HIS-087, HIS-202, HIS-100 (owner problem report VXSGKH: passes never need consent): Bob live, having seen her
    // assumption of his pass: one Undo still goes back at once, free, with no dialog; he has nothing to redo, and she
    // stands in her Main 1
    @Test fun whenTheOtherPlayerHasSeenItsPassesUndoIsStillFree() {
        val hand = aliceDrewInHerMain()
        val turn = alice.state.turn.number
        aliceLeavesHerMain()
        waitUntil("Bob having seen it") {
            val head = alice.engine { device.session.head }
            alice.settled(this) && bob.shown.localHead == head && alice.engine { device.localHeads[bob.me] } == head
        }
        var dialog = false
        everyRun += { if (alice.shows(hasTestTag("undo-scroller"))) dialog = true }
        alice.tap("Undo")
        aliceBackInHerMain(hand, turn)
        everyRun.clear()
        assertFalse(dialog, "free: no dialog")
        assertEquals(alice.me, bob.state.turn.active, "Alice's turn; Bob has nothing to redo")
        // HIS-076, HIS-168: Bob's phone reads what she pressed from the record (only logged since 2026-11-22: his table shows
        // no text for her undo)
        waitUntil("her undo on Bob's table") { bob.game?.shown?.transition?.undone?.by == alice.id }
        val undone = bob.shown.transition!!.undone!!
        assertEquals("${alice.name} undid: went to combat",
            org.asyncttwiz.android.EngineEventText.undid(undone.event, undone.state, bob.id, undone.by, names()))
        repeat(3) { run() }
        assertFalse(bob.toldATakeBack(alice), "no text for her undo: ${bob.shownTexts}")
    }

    // HIS-101, HIS-120 (owner, 2026-11-08): the other player's press shows on your timeline only as what it led to, also now that it
    // is on the record
    @Test fun theOtherPlayersTapIsNoEventOnYourTimeline() {
        aliceDrewInHerMain()
        val pass = aliceLeavesHerMain()
        waitUntil("Bob having seen it") { alice.settled(this) && bob.shown.line.any { it.id == pass } }
        val game = bob.game!!
        val timeline = game.timeline.events(game, names())
        assertTrue(timeline.none { it.id == pass }, timeline.map { it.description }.toString())
    }

    /** Alice's app closes and starts again, all of her events on the server, and she opens the game: nothing of her phone's
     * memory left, only the record. */
    private fun aliceRestarts() {
        waitUntil("Alice's events on the server") { alice.engine { device.session.pending.isEmpty() } }
        alice = restart(alice)
        alice.openGame(game, this)
    }

    // HIS-101, HIS-119, HIS-120, HIS-084, HIS-099 (owner, 2026-11-08): her press out of her main phase is on the game's record: after the app
    // starts again it is still on the timeline as "You went to combat", and one Undo still returns to that main phase
    @Test fun aTapLeavingTheMainPhaseIsStillAPointAfterTheAppStartsAgain() {
        val hand = aliceDrewInHerMain()
        val turn = alice.state.turn.number
        away(bob, alice)
        val pass = aliceLeavesHerMain()
        aliceRestarts()
        waitUntil("Alice's table past her Main 1") { alice.state.turn.number == turn && alice.state.turn.step != TurnStep.PrecombatMain && alice.status == GameDevice.Status.YourMove }
        val game = alice.game!!
        val timeline = game.timeline.events(game, names())
        assertEquals("You went to combat", timeline.single { it.id == pass }.description, timeline.map { it.description }.toString())
        alice.tap("Undo")
        aliceBackInHerMain(hand, turn)
        // HIS-168 (owner 2026-11-23): her own undo shows no text at the top; her device logs what it took back
        waitUntil("her undo logged") { ownUndoLogged("Undid: went to combat") }
        assertFalse(alice.toldOwnUndo(), alice.shownTexts.toString())
    }

    // HIS-101, HIS-119, HIS-120, HIS-084, HIS-099, TRN-057 (owner, 2026-11-08): the same for End turn: after the app starts again it is one
    // point, "You ended the turn" (only its first pass; the others stay plain), and one Undo returns to the main phase,
    // not into the other player's turn
    @Test fun anEndTurnIsStillAPointAfterTheAppStartsAgain() {
        val hand = aliceDrewInHerMain()
        val turn = alice.state.turn.number
        away(bob, alice)
        val first = alice.engine { device.session.events.size + device.session.pending.size }
        alice.hold("To combat")
        waitUntil("Alice's phone past her Main 1, all of it on the server") {
            alice.state.turn.step != TurnStep.PrecombatMain && alice.settled(this)
        }
        val press = alice.engine { (device.session.events + device.session.pending)[first].id }
        aliceRestarts()
        waitUntil("Alice's table past her Main 1") { alice.game?.shown != null && alice.state.turn.step != TurnStep.PrecombatMain }
        val game = alice.game!!
        val timeline = game.timeline.events(game, names())
        assertEquals("You ended the turn", timeline.single { it.id == press }.description, timeline.map { it.description }.toString())
        assertEquals(1, timeline.count { it.description == "You ended the turn" }, timeline.map { it.description }.toString())
        alice.tap("Undo")
        aliceBackInHerMain(hand, turn)
        // HIS-168 (owner 2026-11-23): her own undo shows no text at the top; her device logs what it took back
        waitUntil("her undo logged") { ownUndoLogged("Undid: End turn") }
        assertFalse(alice.toldOwnUndo(), alice.shownTexts.toString())
    }
}
