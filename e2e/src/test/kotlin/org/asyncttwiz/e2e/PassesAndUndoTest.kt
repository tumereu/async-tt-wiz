package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.performClick
import arcanum.api.TurnStep
import arcanum.engine.input.Input
import org.asyncttwiz.android.EngineEventText
import org.asyncttwiz.android.EngineGame
import org.asyncttwiz.android.undoPoints
import org.asyncttwiz.protocol.game.GameDevice
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Owner playtest decisions 2026-11-04 (async-play.md §29.2, §29.4; moved from the interim EnginePassesAndUndoTest) on
 * two phones and the server: passes are implied (no "passed" line anywhere, Undo never targets or lists a pass), and an
 * undo animates backwards on both phones, in one go (owner 2026-11-05), with "Undid: <action>" at the top.
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

    /** Alice's Undo dialog is open at the point of her Herald's cast (its Undo enabled: the board there worked out), she
     * asks, Bob accepts: the server goes back to [target] and both tables show the [herald] back in her hand. */
    private fun aliceAsksAndBobAccepts(target: String, herald: String) {
        val confirm = hasTestTag("rewind-confirm") and isEnabled()
        waitUntil("Alice's undo request dialog at her cast") {
            alice.shows(confirm) && alice.shows(hasTestTag("undo-point") and hasText("You cast Ember Herald"))
        }
        alice.node(confirm).performClick()
        waitUntil("Bob asked to accept the undo") { bob.shows(hasTestTag("request-accept")) }
        bob.node(hasTestTag("request-accept")).performClick()
        waitUntil("the server back at the target") { serverGame()?.headId == target }
        waitUntil("both tables back") { listOf(alice, bob).all { p -> p.game?.shown?.transition?.undone != null && where(p.shown.board, herald) == "HAND" } }
    }

    // HIS-101, HIS-075, HIS-099, HIS-084 (§29.2, owner 2026-11-04): across turns full of passes no timeline
    // or event chip text says anyone passed (assumed or not): a pass reads only as what it led to, one that led to nothing
    // shows nowhere; the Undo dialog lists no pass (an End turn's first pass is "You ended the turn", her tap leaving her main
    // phase "You went to combat", owner 2026-11-08), and Undo goes back to just before that tap, then past it to her cast
    @Test fun passesAreImpliedInTheTimelineChipsAndUndo() {
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
        // HIS-084, HIS-101 (owner 2026-11-08): her tap leaving her main phase is the target, not the assumed passes after it
        waitUntil("her tap on the server: the target") { alice.shown.undoTarget == next.parentId }
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
            // HIS-005: a pass shows on the timeline only as a resolution or an own End turn; the new turn and steps it led
            // to are the separators between events
            val byId = line.associateBy { it.id }
            assertTrue(timeline.none { t -> byId[t.id]!!.let { EngineEventText.pass(it) && EngineEventText.ownPress(it, player.id) == null &&
                it.engine?.changes.orEmpty().none { c -> c is arcanum.engine.change.Change.StackItemRemoved } } },
                "${player.name}: no step-only pass on the timeline: ${timeline.map { it.description }}")
            assertTrue(timeline.none { it.description.startsWith("Turn ") } && timeline.any { it.separator != null },
                "${player.name}: turn and step changes show only as separators: ${timeline.map { it.separator?.label to it.description }}")
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

        // HIS-099: the Undo dialog's points are exactly the timeline's events up to her local head: no pass line (a
        // resolution reads as what resolved; an End turn that began with a pass is one point)
        val shown = alice.shown
        val aliceGame = alice.game!!
        val timeline = aliceGame.timeline.events(aliceGame, names)
        val points = undoPoints(shown, timeline)
        assertTrue(points.isNotEmpty())
        val held = shown.held.associateBy { it.id }
        assertEquals(timeline.filter { held[it.id]?.parentId != null }.map { it.id }, points.map { it.event.id })
        val texts = points.map { it.shown.description }
        assertEquals("You went to combat", points.single { it.event.id == next.id }.shown.description, "her tap out of her main phase is a point")
        assertTrue(texts.none { "passed" in it }, texts.toString())
        assertTrue("You cast Ember Herald" in texts, texts.toString())
        assertTrue(texts.count { it == "You ended the turn" } <= 1, texts.toString())
    }

    // HIS-084, TRN-057 (analysis 2026-11-05): Alice ends her turn (Bob away, "Always assume resolves"), then Undo takes the
    // End turn back: she lands in her main phase, and her next action there (a Mountain played) is only that; her
    // taken-back End turn passes nothing more for her (it carried the game on to Bob's turn after the Mountain)
    @Test fun anUndoneEndTurnPassesNothingAfterTheNextAction() {
        startGame(TestDeck.thirds(TestCards.herald), TestDeck.thirds(TestCards.herald),
            aliceSettings = org.asyncttwiz.protocol.game.PlaySettings(assume = org.asyncttwiz.protocol.game.AssumeResolves.Always),
            deal = { a, _ -> "Mountain" in hand(a) })
        bothOpen()
        bothKeep(bobAway = true)
        alice.hold("To combat")
        waitUntil("Alice's turn ended, all of it on the server") {
            alice.state.turn.active == bob.me && alice.engine { device.session.pending.isEmpty() }
        }
        alice.tap("Undo")
        waitUntil("Alice back in her main phase") { alice.state.turn.number == 1 && alice.inOwnMain() && alice.status == GameDevice.Status.YourMove }
        alice.playFromHand("Mountain", this)
        waitUntil("the Mountain played, on the server") {
            alice.field("Mountain").isNotEmpty() && alice.engine { device.session.pending.isEmpty() }
        }
        repeat(5) { run(skip = java.time.Duration.ofSeconds(1)) }
        assertEquals(1 to TurnStep.PrecombatMain, alice.state.turn.let { it.number to it.step }, "still her main phase")
        assertEquals(GameDevice.Status.YourMove, alice.status)
    }

    // HIS-030, HIS-108, HIS-099, TBL-040 (§29.4, owner 2026-11-04 and 2026-11-05): Alice undoes her cast that Bob had seen
    // (he accepts her request): on both phones the board goes back in one animation (the Herald from the battlefield
    // straight back to her hand, no board in between, no jump) with "Undid: cast Ember Herald" for her and "Alice undid:
    // cast Ember Herald" for Bob
    @Test fun undoAnimatesBackwardsOnBothDevicesWithItsText() {
        val cast = aliceCastsAHeraldWhileBobIsAway(endTurn = false)
        val herald = cast.herald
        assertEquals("BATTLEFIELD", where(alice.shown.board, herald), "the Herald resolved on Alice's assumption")
        bobCatchesUp()
        assertEquals("BATTLEFIELD", where(bob.shown.board, herald), "Bob saw it resolve")
        // Bob saw it: Alice asks (Undo opens the request), Bob accepts
        val target = assertNotNull(alice.shown.undoTarget)
        alice.tap("Undo")
        aliceAsksAndBobAccepts(target, herald)

        for ((player, text) in listOf(alice to "Undid: cast Ember Herald", bob to "${alice.name} undid: cast Ember Herald")) {
            val t = assertNotNull(player.shown.transition)
            val undone = assertNotNull(t.undone, "${player.name}: the transition is the undo")
            assertFalse(t.jump, "${player.name}: an undo animates, never jumps")
            assertEquals(text, EngineEventText.undid(undone.event, undone.state, player.id, undone.by, names()))
            waitUntil("\"$text\" shown on ${player.name}'s table") { player.told(text) }
            assertTrue(t.forward.isEmpty() && t.backward.isNotEmpty())
            // One animation: the Herald flies from the battlefield straight back to the hand, no board in between
            assertTrue(t.steps.isEmpty(), "${player.name}: no boards in between: ${t.steps.map { where(it, herald) }}")
            val now = where(player.shown.board, herald)
            assertTrue(now != "STACK" && now != "BATTLEFIELD", "${player.name}: the Herald back in Alice's hand: $now")
        }
        assertEquals("HAND", where(alice.shown.board, herald))
    }

    // HIS-084, HIS-099, SYS-025 (full suites 2026-11-08): Alice's phone hasn't heard yet that Bob saw her cast when she
    // taps Undo, so it shows the undo at once; the server finds it needs Bob's approval, her game loads again and the Undo
    // dialog opens at the point before her cast (never at the newest point), its Undo waiting until that point's board is
    // in; she asks, Bob accepts and the server goes back to before her cast
    @Test fun anUndoShownAtOnceThatNeedsApprovalOpensTheDialogAtItsPoint() {
        val cast = aliceCastsAHeraldWhileBobIsAway(endTurn = false)
        val herald = cast.herald
        // What the server tells Alice waits: she doesn't hear that Bob saw her cast
        alice.link.holdIncoming()
        bobCatchesUp()
        val target = assertNotNull(alice.shown.undoTarget)
        assertEquals(cast.cast.parentId, target)
        alice.tap("Undo")
        waitUntil("the undo shown on her table at once") { where(alice.shown.board, herald) == "HAND" }
        assertTrue(serverGame()?.headId != target, "not on the server yet")
        alice.link.releaseIncoming()
        aliceAsksAndBobAccepts(target, herald)
    }

    // HIS-102 (§29.2, smoke 2026-11-04): with both apps open, Alice casts an Ember Herald and Bob's pass lets it resolve:
    // on Alice's live table his pass shows at once (the Herald resolves), it never turns into playback
    @Test fun theOtherPlayersPassShowsAtOnceOnALiveTable() {
        // Every board her phone shows is looked at as it reaches the screen: the Herald on the Stack shows only on her cast's
        // own board when her phone assumes his pass at once (his response mode passes there), and a test round could take
        // that board and the next one together on a loaded machine (full run 2026-11-13)
        val aliceEngine = HeldWorker()
        startGame(TestDeck.thirds(TestCards.herald), TestDeck.thirds(TestCards.herald), aliceWorker = aliceEngine,
            deal = { a, _ -> "Mountain" in hand(a) && "Ember Herald" in hand(a) })
        bothOpen()
        bothKeep(bobAway = false)
        alice.playFromHand("Mountain", this)
        waitUntil("the Mountain on the battlefield", along = listOf(bob)) { alice.field("Mountain").isNotEmpty() && alice.inOwnMain() }
        val herald = atw(alice.hand("Ember Herald").first())
        var playback = false
        everyRun += { if (alice.game?.shown?.status is GameDevice.Status.Playing) playback = true }
        var stacked = false
        aliceEngine.observe = { if (alice.game?.shown?.let { where(it.board, herald) } == "STACK") stacked = true }
        alice.playFromHand("Ember Herald", this)
        waitUntil("Alice's live phone waiting for Bob's answer") { stacked }
        // Bob's app passes (his own tap, or his phone by his response mode)
        waitUntil("the Herald resolving on Bob's pass", along = listOf(bob)) { where(alice.shown.board, herald) == "BATTLEFIELD" }
        val shown = alice.shown
        assertFalse(playback, "no playback for Bob's pass")
        assertFalse(shown.behind, "his pass is shown")
        assertTrue(shown.held.last { it.author == bob.id }.let(EngineEventText::pass), "the last event of Bob's she was shown is his pass")
    }
}
