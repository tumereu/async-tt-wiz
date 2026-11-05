package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performClick
import arcanum.api.TurnStep
import arcanum.engine.input.Input
import org.asyncttwiz.android.EngineEventText
import org.asyncttwiz.android.EngineGame
import org.asyncttwiz.android.undoChoiceText
import org.asyncttwiz.android.undoChoices
import org.asyncttwiz.protocol.game.GameDevice
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Owner playtest decisions 2026-11-04 (async-play.md §29.2, §29.4; moved from the interim EnginePassesAndUndoTest) on
 * two phones and the server: passes are implied (no "passed" line anywhere, Undo never targets or lists a pass), and an
 * undo animates backwards on both phones with "Undid: <action>" at the top.
 */
class PassesAndUndoTest : E2e() {
    /** Bob comes back and plays everything back (Play, his answers at the stops as Alice assumed them) until he is at
     * the head: the transitions he was shown. */
    private fun bobCatchesUp(): List<EngineGame.Transition> {
        val shown = mutableListOf<EngineGame.Transition>()
        everyRun += { bob.game?.shown?.transition?.let { t -> if (shown.lastOrNull()?.serial != t.serial) shown += t } }
        bob.back()
        val head = alice.engine { device.session.head }
        waitUntil("Bob caught up", along = listOf(bob)) {
            val s = bob.game?.shown
            s != null && !s.behind && s.localHead == head && s.status !is GameDevice.Status.Playing && s.status !is GameDevice.Status.Stopped &&
                s.status != GameDevice.Status.CatchUp && bob.engine { device.session.pending.isEmpty() }
        }
        everyRun.clear()
        return shown
    }

    // HIS-091, HIS-001, HIS-075, HIS-099, HIS-084 (§29.2, owner 2026-11-04): across turns full of passes no strip, timeline
    // or event chip text says anyone passed (assumed or not): a pass reads only as what it led to, one that led to nothing
    // shows nowhere; the Undo dialog lists no pass (an End turn's first pass is "You ended the turn"), and Undo goes back
    // past the player's own Next tap to their cast
    @Test fun passesAreImpliedInTheStripTimelineChipsAndUndo() {
        val cast = aliceCastsAHeraldWhileBobIsAway(endTurn = false)
        val castEvent = cast.cast
        // Alice's own Next tap (To combat: her pass), then End turn; her phone plays on through Bob's turn on assumptions
        assertEquals(GameDevice.Status.YourMove, alice.status)
        val before = alice.engine { device.session.events.size + device.session.pending.size }
        alice.tap("To combat")
        waitUntil("Alice's pass made") { alice.engine { device.session.events.size + device.session.pending.size } > before }
        val next = alice.engine { (device.session.events + device.session.pending)[before] }
        assertTrue(EngineEventText.pass(next), "the Next tap is a pass")
        waitUntil("Alice's own stop after it") { alice.status == GameDevice.Status.YourMove && alice.state.turn.step != TurnStep.PrecombatMain }
        // HIS-084: her own pass is no target: Undo goes back to just before her cast
        assertEquals(castEvent.parentId, alice.shown.undoTarget)
        alice.hold(alice.stepArrow()!!)
        waitUntil("Alice's phone on in Bob's turn, all of it on the server") {
            alice.state.turn.active == bob.me && alice.engine { device.session.pending.isEmpty() }
        }
        val chips = bobCatchesUp()
        waitUntil("Alice's table with Bob's answers") { alice.engine { device.session.pending.isEmpty() } }

        val names = names()
        for (player in listOf(alice, bob)) {
            val game = player.game!!
            val line = game.shown!!.line
            val passes = line.filter(EngineEventText::pass)
            assertTrue(passes.size >= 4, "${player.name}: a line full of passes: ${passes.size}")
            assertTrue(passes.any(EngineEventText::hidden), "${player.name}: a pass that led to nothing shows nowhere")
            val timeline = game.timeline.events(game, names)
            assertTrue(timeline.none { "passed" in it.description || "assumed" in it.description && "pass" in it.description },
                "${player.name}: ${timeline.map { it.description }}")
            assertTrue(timeline.any { it.description.startsWith("Turn ") }, "${player.name}: the turn change shows: ${timeline.map { it.description }}")
            for (e in passes.filterNot(EngineEventText::hidden)) {
                val text = game.timeline.text(game, e, names)
                assertTrue(text.isNotBlank() && "passed" !in text && "assumed" !in text, text)
            }
        }
        // HIS-075: the chip Bob's playback showed each time: never a pass line
        assertTrue(chips.isNotEmpty())
        val bobGame = bob.game!!
        for (t in chips) t.forward.lastOrNull { !EngineEventText.hidden(it) }?.let { told ->
            assertFalse("passed" in bobGame.timeline.text(bobGame, told, names), bobGame.timeline.text(bobGame, told, names))
        }

        // HIS-099: the Undo dialog lists no pass; an End turn that began with a pass is one entry
        val shown = alice.shown
        val choices = undoChoices(shown)
        assertTrue(choices.isNotEmpty())
        assertTrue(choices.none { EngineEventText.pass(it) && it.id !in shown.endTurns }, choices.map { undoChoiceText(it, shown, alice.id, names) }.toString())
        assertFalse(choices.any { it.id == next.id }, "her Next tap isn't listed")
        val texts = choices.map { undoChoiceText(it, shown, alice.id, names) }
        assertTrue(texts.none { "passed" in it }, texts.toString())
        assertTrue("You cast Ember Herald" in texts, texts.toString())
        assertTrue(texts.count { it == "You ended the turn" } <= 1, texts.toString())
    }

    // HIS-030, HIS-099, TBL-040 (§29.4, owner 2026-11-04): Alice undoes her cast that Bob had seen (he accepts her request):
    // on both phones the board goes back the way it came (the Herald from the battlefield through the Stack back to her
    // hand, step by step, no jump) with "Undid: cast Ember Herald" for her and "Alice undid: cast Ember Herald" for Bob
    @Test fun undoAnimatesBackwardsOnBothDevicesWithItsText() {
        val cast = aliceCastsAHeraldWhileBobIsAway(endTurn = false)
        val herald = cast.herald
        assertEquals("BATTLEFIELD", where(alice.shown.board, herald), "the Herald resolved on Alice's assumption")
        bobCatchesUp()
        assertEquals("BATTLEFIELD", where(bob.shown.board, herald), "Bob saw it resolve")
        // Bob saw it: Alice asks (Undo opens the request), Bob accepts
        val target = assertNotNull(alice.shown.undoTarget)
        alice.tap("Undo")
        waitUntil("Alice's undo request dialog") { alice.shows(hasTestTag("rewind-confirm")) }
        alice.node(hasTestTag("rewind-confirm")).performClick()
        waitUntil("Bob asked to accept the undo") { bob.shows(hasTestTag("request-accept")) }
        bob.node(hasTestTag("request-accept")).performClick()
        waitUntil("the server back at the target") { serverGame()?.headId == target }
        waitUntil("both tables back") { listOf(alice, bob).all { p -> p.game?.shown?.transition?.undone != null && where(p.shown.board, herald) == "HAND" } }

        for ((player, text) in listOf(alice to "Undid: cast Ember Herald", bob to "${alice.name} undid: cast Ember Herald")) {
            val t = assertNotNull(player.shown.transition)
            val undone = assertNotNull(t.undone, "${player.name}: the transition is the undo")
            assertFalse(t.jump, "${player.name}: an undo animates, never jumps")
            assertEquals(text, EngineEventText.undid(undone.event, undone.state, player.id, undone.by, names()))
            assertTrue(t.forward.isEmpty() && t.backward.isNotEmpty())
            // The steps fly the Herald back the way it came: off the battlefield, onto the Stack, then the hand
            val path = t.steps.map { where(it, herald) }
            assertTrue(t.steps.isNotEmpty(), "${player.name}: steps in between")
            assertTrue("STACK" in path, "${player.name}: back through the Stack: $path")
            assertTrue(path.indexOf("STACK") >= path.indexOfFirst { it == "BATTLEFIELD" }, "${player.name}: battlefield before Stack: $path")
            val now = where(player.shown.board, herald)
            assertTrue(now != "STACK" && now != "BATTLEFIELD", "${player.name}: the Herald back in Alice's hand: $now")
        }
        assertEquals("HAND", where(alice.shown.board, herald))
    }

    // HIS-095 (§29.2, smoke 2026-11-04): with both apps open, Alice casts an Ember Herald and Bob's pass lets it resolve:
    // on Alice's live table his pass shows at once (the Herald resolves), it never turns into playback
    @Test fun theOtherPlayersPassShowsAtOnceOnALiveTable() {
        startGame(TestDeck.thirds(TestCards.herald), TestDeck.thirds(TestCards.herald),
            deal = { a, _ -> "Mountain" in hand(a) && "Ember Herald" in hand(a) })
        bothOpen()
        bothKeep(bobAway = false)
        alice.playFromHand("Mountain", this)
        waitUntil("the Mountain on the battlefield", along = listOf(bob)) { alice.field("Mountain").isNotEmpty() && alice.inOwnMain() }
        val herald = atw(alice.hand("Ember Herald").first())
        var playback = false
        everyRun += { if (alice.game?.shown?.status is GameDevice.Status.Playing) playback = true }
        alice.playFromHand("Ember Herald", this)
        waitUntil("Alice's live phone waiting for Bob's answer") { where(alice.shown.board, herald) == "STACK" }
        // Bob's app passes (his own tap, or his phone by his response mode)
        waitUntil("the Herald resolving on Bob's pass", along = listOf(bob)) { where(alice.shown.board, herald) == "BATTLEFIELD" }
        val shown = alice.shown
        assertFalse(playback, "no playback for Bob's pass")
        assertFalse(shown.behind, "his pass is shown")
        assertTrue(shown.held.last { it.author == bob.id }.let(EngineEventText::pass), "the last event of Bob's she was shown is his pass")
    }
}
