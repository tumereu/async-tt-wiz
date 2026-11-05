package org.asyncttwiz.e2e

import arcanum.api.TurnStep
import arcanum.engine.input.Input
import org.asyncttwiz.android.AppLog
import org.asyncttwiz.protocol.game.GameDevice
import org.asyncttwiz.protocol.game.GameEvent
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Owner problem report 2026-11-05 (attack, then a quick Undo) and the undo's look: an Undo tapped while the player's own
 * action is still on its way waits for it and then takes it back, never refused (no shake, no "That wasn't taken"); an
 * undo shows its whole result at once and animates once, with no bottom bar (main button, quick actions) of a board in
 * between.
 */
class QuickUndoTest : E2e() {
    /** The events on Alice's device (the main line she knows and her own on their way). */
    private fun aliceEvents(): List<GameEvent> = alice.engine { device.session.events + device.session.pending }

    private fun mine(input: (Input) -> Boolean): List<GameEvent> = aliceEvents().filter { e ->
        e.author == alice.id && e.engine?.steps.orEmpty().any { it.assumedBy == null && it.input?.let(input) == true } }

    /** Whether the Undo button was refused (shaken) since the app log's line [from]. */
    private fun undoRefused(from: Int): Boolean = AppLog.text().lines().drop(from).any { "Undo not taken" in it }

    private fun logLines(): Int = AppLog.text().lines().size

    // HIS-107 (owner problem report 2026-11-05): Alice attacks with her Ember Cub and taps Undo at once, while her attack is
    // still on its way to the server: the Undo waits for it and takes the attack back (she is declaring attackers again,
    // the Cub staged), with no shake and no note
    @Test fun anUndoRightAfterAttackTakesTheAttackBack() {
        startGame(TestDeck.thirds(TestCards.cub), TestDeck.thirds(TestCards.cub),
            deal = { a, b -> "Mountain" in hand(a) && "Ember Cub" in hand(a) && "Mountain" in hand(b) })
        bothOpen()
        bothKeep(bobAway = false)
        alice.playFromHand("Mountain", this)
        waitUntil("the Mountain on the battlefield", along = listOf(bob)) { alice.field("Mountain").isNotEmpty() && alice.inOwnMain() }
        alice.playFromHand("Ember Cub", this)
        waitUntil("the Cub on the battlefield", along = listOf(bob)) { alice.field("Ember Cub").isNotEmpty() && alice.inOwnMain() }
        alice.hold("To combat")
        waitUntil("Alice's next Main 1", along = listOf(alice, bob)) { alice.inOwnMain() && alice.state.turn.number >= 3 }
        away(bob, alice)
        alice.tap("To combat")
        waitUntil("Alice declaring attackers") { alice.state.turn.step == TurnStep.DeclareAttackers }
        alice.stageAttacker("Ember Cub", this)
        waitUntil("the Cub staged, all of it on the server") { alice.stepArrow() == "Attack (1)" && alice.engine { device.session.pending.isEmpty() } }
        val staged = alice.engine { device.session.head }
        val log = logLines()
        alice.link.holdIncoming()
        alice.tap("Attack (1)")
        alice.tap("Undo")
        // Her attack is worked out first (off the main thread), then made: the Undo waits for it
        waitUntil("her attack made, on its way") {
            mine { it is Input.DeclareAttackers }.size == 1 && alice.engine { device.session.pending.isNotEmpty() }
        }
        alice.link.releaseIncoming()
        waitUntil("the attack taken back") {
            serverGame()?.headId == staged && alice.engine { device.session.pending.isEmpty() } && mine { it is Input.DeclareAttackers }.isEmpty()
        }
        waitUntil("Alice declaring attackers again") { alice.state.turn.step == TurnStep.DeclareAttackers && alice.stepArrow() == "Attack (1)" }
        assertFalse(undoRefused(log), "the Undo button didn't shake")
        assertFalse(alice.refusalSeen, "no note: ${alice.shownTexts}")
        assertTrue(alice.leaks.leaks.isEmpty())
    }

    // HIS-107: Alice casts an Ember Herald and taps Undo while her cast is still on its way to the server: the Undo waits
    // for it and takes the cast back (the Herald in her hand, her Mountain untapped), with no shake and no note
    @Test fun anUndoWhileACastIsSentTakesItBack() {
        startGame(TestDeck.thirds(TestCards.herald), TestDeck.thirds(TestCards.herald),
            deal = { a, _ -> "Mountain" in hand(a) && "Ember Herald" in hand(a) })
        bothOpen()
        bothKeep(bobAway = true)
        alice.playFromHand("Mountain", this)
        waitUntil("the Mountain on the battlefield, on the server") {
            alice.field("Mountain").isNotEmpty() && alice.inOwnMain() && alice.engine { device.session.pending.isEmpty() }
        }
        val before = alice.engine { device.session.head }
        val herald = atw(alice.hand("Ember Herald").first())
        val log = logLines()
        alice.link.holdIncoming()
        alice.playFromHand("Ember Herald", this)
        waitUntil("the Herald cast") { where(alice.shown.board, herald) == "STACK" }
        assertTrue(alice.engine { device.session.pending.isNotEmpty() }, "her cast still on its way")
        alice.tap("Undo")
        alice.link.releaseIncoming()
        waitUntil("the cast taken back") {
            serverGame()?.headId == before && alice.engine { device.session.pending.isEmpty() } && where(alice.shown.board, herald) == "HAND"
        }
        assertTrue(mine { it is Input.Cast }.isEmpty(), "no cast on her line")
        assertTrue(alice.field("Mountain").none { alice.state.obj(it)?.tapped == true }, "her Mountain untapped")
        assertTrue(alice.inOwnMain())
        assertFalse(undoRefused(log), "the Undo button didn't shake")
        assertFalse(alice.refusalSeen, "no note: ${alice.shownTexts}")
    }

    // HIS-108, HIS-030: Alice undoes her Ember Herald (it resolved and made its token on her assumption of Bob's pass): her
    // table shows the whole result at once and animates once, straight from the board before to the board after; no
    // bottom bar of a board in between ever shows (the Herald back on the Stack would have said "Resolve")
    @Test fun anUndoShowsItsWholeResultAtOnce() {
        val cast = aliceCastsAHeraldWhileBobIsAway(endTurn = false)
        val herald = cast.herald
        assertEquals("BATTLEFIELD", where(alice.shown.board, herald))
        val bar = alice.shownTexts.last { it.startsWith(BOTTOM_BAR) }
        val from = alice.shownTexts.size
        alice.tap("Undo")
        waitUntil("the undo shown") { alice.game?.shown?.transition?.undone != null && where(alice.shown.board, herald) == "HAND" }
        waitUntil("\"Undid: cast Ember Herald\" shown") { alice.told("Undid: cast Ember Herald") }
        repeat(5) { run(java.time.Duration.ofMillis(300)) }
        val t = assertNotNull(alice.shown.transition)
        assertFalse(t.jump, "an undo animates")
        assertTrue(t.steps.isEmpty(), "one animation, no boards in between: ${t.steps.size}")
        val bars = alice.shownTexts.drop(from).filter { it.startsWith(BOTTOM_BAR) }
        val after = alice.shownTexts.last { it.startsWith(BOTTOM_BAR) }
        assertEquals(bar, after, "back where she cast it: the same bottom bar")
        assertTrue(bars.all { it == after }, "no bottom bar in between: $bars")
        assertTrue(alice.shownTexts.drop(from).none { "Resolve" in it }, "${alice.shownTexts.drop(from)}")
        assertTrue(alice.inOwnMain())
    }

    private companion object {
        const val BOTTOM_BAR = "Bottom bar: "
    }
}
