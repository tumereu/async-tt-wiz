package org.asyncttwiz.android

import arcanum.engine.Engine
import arcanum.engine.input.Input
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import org.asyncttwiz.protocol.MtgStackKind
import org.asyncttwiz.protocol.game.GameDevice
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Owner playtest decisions 2026-11-04 (async-play.md §29.2, §29.4) on two app engine games and the simulated server:
 * passes are implied (no "passed" line anywhere, Undo never targets or lists a pass), and an undo animates backwards on
 * both devices with "Undid: <action>" at the top.
 */
class EnginePassesAndUndoTest {
    private val f = EngineGameFixture(heralds = true)
    private val a = PlayerId(f.a)
    private val names = mapOf(f.a to "Alice", f.b to "Bob")

    /** Where card [id] is on [play]'s board ("STACK" while its spell is there), or null when the board hides it. */
    private fun where(play: TableBoard, id: String): String? {
        val board = play.board
        if (board.stackEffects.any { it.sourceId == id && it.item?.kind == MtgStackKind.Spell }) return "STACK"
        val card = board.cards.firstOrNull { it.id == id } ?: return null
        return board.containers.single { it.id == card.containerId }.zone.name
    }

    /** Alice's device on its own while Bob is away. */
    private fun alone(sim: EngineAppSim, alice: EngineGame) {
        sim.deliver(f.a)
        alice.playToStop()
        (alice.device.status as? GameDevice.Status.Stopped)?.let { alice.act(it.assumed) }
        alice.tick(false, sim.now)
        sim.flush(f.a)
        sim.now += 1_000
    }

    /** Both keep; Bob goes away; Alice plays a Mountain and casts an Ember Herald, her device assuming Bob lets it
     * resolve: the Herald's id. */
    private fun aliceCastsAHerald(sim: EngineAppSim, alice: EngineGame, bob: EngineGame): String {
        alice.keep(emptyList())
        sim.flush(f.a)
        sim.deliver(f.b)
        bob.playToStop()
        bob.keep(emptyList())
        sim.flush(f.b)
        repeat(5) { alone(sim, alice) }
        assertEquals(GameDevice.Status.YourMove, alice.device.status)
        val s0 = alice.device.model.engine
        val mountain = assertNotNull(s0.player(a).hand.firstOrNull { s0.obj(it)?.definition?.faces?.first()?.name == "Mountain" })
        alice.send(listOf(Input.PlayLand(a, mountain)))
        sim.flush(f.a)
        val s1 = alice.device.model.engine
        val cast = assertNotNull(Engine.options(s1, a).filterIsInstance<Input.Cast>().firstOrNull {
            s1.obj(it.card)?.definition?.faces?.first()?.name == "Ember Herald" })
        assertIs<GameDevice.Acted.Sent>(alice.send(EngineCombatScenario(f).cast(s1, a, cast)))
        sim.flush(f.a)
        repeat(2) { alone(sim, alice) }
        return (cast.card as ObjectId.Card).card.value
    }

    /** Bob comes back and plays everything back, answering each stop as Alice assumed: the transitions he was shown. */
    private fun bobCatchesUp(sim: EngineAppSim, bob: EngineGame): List<EngineGame.Transition> {
        sim.deliver(f.b)
        val shown = mutableListOf<EngineGame.Transition>()
        var guard = 0
        while (guard++ < 300) {
            when (val status = bob.device.status) {
                is GameDevice.Status.Playing -> bob.step()
                is GameDevice.Status.Stopped -> bob.act(status.assumed)
                else -> break
            }
            bob.transition?.let { t -> if (shown.lastOrNull()?.serial != t.serial) shown += t }
            sim.flush(f.b)
        }
        return shown
    }

    // HIS-101, HIS-001, HIS-075, HIS-099, HIS-084 (§29.2, owner 2026-11-04): across turns full of passes no strip, timeline
    // or event chip text says anyone passed (assumed or not): a pass reads only as what it led to, one that led to nothing
    // shows nowhere; the Undo dialog lists no pass (an End turn's first pass is "You ended the turn"), and Undo goes back
    // past the player's own Next tap to their cast
    @Test fun passesAreImpliedInTheStripTimelineChipsAndUndo() {
        val sim = EngineAppSim(f)
        val alice = sim.load(f.a)
        val bob = sim.load(f.b)
        aliceCastsAHerald(sim, alice, bob)
        val castEvent = alice.device.session.events.last { e -> e.engine?.steps?.any { it.input is Input.Cast } == true }
        // Alice's own Next tap, then End turn; her device plays on through Bob's turn on assumptions
        assertEquals(GameDevice.Status.YourMove, alice.device.status)
        assertIs<GameDevice.Acted.Sent>(alice.pass())
        val next = alice.device.session.pending.lastOrNull() ?: alice.device.session.events.last()
        assertTrue(EngineEventText.pass(next), "the Next tap is a pass")
        sim.flush(f.a)
        // HIS-084: her own pass is no target: Undo goes back to just before her cast
        assertEquals(castEvent.parentId, alice.shown!!.undoTarget)
        alice.endTurn()
        repeat(4) { alone(sim, alice) }
        val chips = bobCatchesUp(sim, bob)
        sim.deliver(f.a)

        for (game in listOf(alice, bob)) {
            val line = game.shown!!.line
            val passes = line.filter(EngineEventText::pass)
            assertTrue(passes.size >= 4, "${game.me}: a line full of passes: ${passes.size}")
            assertTrue(passes.any(EngineEventText::hidden), "${game.me}: a pass that led to nothing shows nowhere")
            val timeline = game.timeline.events(game, names)
            assertTrue(timeline.none { "passed" in it.description || "assumed" in it.description && "pass" in it.description }, "${game.me}: ${timeline.map { it.description }}")
            assertTrue(timeline.any { it.description.startsWith("Turn ") }, "${game.me}: the turn change shows: ${timeline.map { it.description }}")
            for (e in passes.filterNot(EngineEventText::hidden)) {
                val text = game.timeline.text(game, e, names)
                assertTrue(text.isNotBlank() && "passed" !in text && "assumed" !in text, text)
            }
        }
        // HIS-075: the chip Bob's playback showed each time: never a pass line
        assertTrue(chips.isNotEmpty())
        for (t in chips) t.forward.lastOrNull { !EngineEventText.hidden(it) }?.let { told ->
            assertFalse("passed" in bob.timeline.text(bob, told, names), bob.timeline.text(bob, told, names))
        }

        // HIS-099: the Undo dialog lists no pass; an End turn that began with a pass is one entry
        val shown = alice.shown!!
        val choices = undoChoices(shown)
        assertTrue(choices.isNotEmpty())
        assertTrue(choices.none { EngineEventText.pass(it) && it.id !in shown.endTurns }, choices.map { undoChoiceText(it, shown, f.a, names) }.toString())
        assertFalse(choices.any { it.id == next.id }, "her Next tap isn't listed")
        val texts = choices.map { undoChoiceText(it, shown, f.a, names) }
        assertTrue(texts.none { "passed" in it }, texts.toString())
        assertTrue("You cast Ember Herald" in texts, texts.toString())
        assertTrue(texts.count { it == "You ended the turn" } <= 1, texts.toString())
    }

    // HIS-030, HIS-099, TBL-040 (§29.4, owner 2026-11-04): Alice undoes her cast that Bob had seen (he accepts her request):
    // on both devices the board goes back the way it came (the Herald from the battlefield through the Stack back to her
    // hand, step by step, no jump) with "Undid: cast Ember Herald" for her and "Alice undid: cast Ember Herald" for Bob
    @Test fun undoAnimatesBackwardsOnBothDevicesWithItsText() {
        val sim = EngineAppSim(f)
        val alice = sim.load(f.a)
        val bob = sim.load(f.b)
        val herald = aliceCastsAHerald(sim, alice, bob)
        assertEquals("BATTLEFIELD", where(alice.play(), herald), "the Herald resolved on Alice's assumption")
        bobCatchesUp(sim, bob)
        assertEquals("BATTLEFIELD", where(bob.play(), herald), "Bob saw it resolve")
        sim.deliver(f.a)
        // Bob saw it: Alice asks, Bob accepts
        val undo = assertIs<GameDevice.Undo.Request>(alice.undo())
        alice.requestUndo(undo.target, "")
        sim.flush(f.a)
        sim.deliver(f.b)
        bob.answerRequest(true)
        sim.flush(f.b)
        sim.deliver(f.a)
        sim.deliver(f.b)
        assertEquals(undo.target, sim.head)

        for ((game, text) in listOf(alice to "Undid: cast Ember Herald", bob to "Alice undid: cast Ember Herald")) {
            val t = assertNotNull(game.transition)
            val undone = assertNotNull(t.undone, "${game.me}: the transition is the undo")
            assertFalse(t.jump, "${game.me}: an undo animates, never jumps")
            assertEquals(text, EngineEventText.undid(undone.event, undone.state, game.me, undone.by, names))
            assertTrue(t.forward.isEmpty() && t.backward.isNotEmpty())
            // The steps fly the Herald back the way it came: off the battlefield, onto the Stack, then the hand
            val path = t.steps.map { where(it, herald) }
            assertTrue(t.steps.isNotEmpty(), "${game.me}: steps in between")
            assertTrue("STACK" in path, "${game.me}: back through the Stack: $path")
            assertTrue(path.indexOf("STACK") >= path.indexOfFirst { it == "BATTLEFIELD" }, "${game.me}: battlefield before Stack: $path")
            val now = where(game.play(), herald)
            assertTrue(now != "STACK" && now != "BATTLEFIELD", "${game.me}: the Herald back in Alice's hand: $now")
        }
        assertEquals("HAND", where(alice.play(), herald))
    }

    /** Both players' apps open on the game: each shows what arrived, answers a stop as assumed, ticks live and sends. */
    private fun bothLive(sim: EngineAppSim, vararg games: EngineGame) {
        for (game in games) {
            sim.deliver(game.me)
            if (game.device.catchUp) game.startPlayback()
            game.playToStop()
            (game.device.status as? GameDevice.Status.Stopped)?.let { game.act(it.assumed) }
            game.tick(true, sim.now)
            sim.flush(game.me)
        }
    }

    // HIS-102 (§29.2, smoke 2026-11-04): with both apps open, Alice casts an Ember Herald and Bob's pass lets it resolve:
    // on Alice's live table his pass shows at once (the Herald resolves), it never turns into playback
    @Test fun theOtherPlayersPassShowsAtOnceOnALiveTable() {
        val sim = EngineAppSim(f)
        val alice = sim.load(f.a)
        val bob = sim.load(f.b)
        alice.tick(true, sim.now); bob.tick(true, sim.now)
        alice.keep(emptyList())
        sim.flush(f.a)
        sim.deliver(f.b)
        bob.playToStop()
        bob.keep(emptyList())
        sim.flush(f.b)
        var guard = 0
        while (guard++ < 40 && !(alice.device.status == GameDevice.Status.YourMove &&
                alice.device.model.engine.turn.let { it.number >= 1 && it.active == a && it.step == arcanum.api.TurnStep.PrecombatMain })) {
            bothLive(sim, alice, bob)
            sim.now += 500
        }
        val s0 = alice.device.model.engine
        assertEquals(arcanum.api.TurnStep.PrecombatMain to a, s0.turn.step to s0.turn.active, "Alice in her main phase")
        val mountain = assertNotNull(s0.player(a).hand.firstOrNull { s0.obj(it)?.definition?.faces?.first()?.name == "Mountain" })
        alice.send(listOf(Input.PlayLand(a, mountain)))
        sim.flush(f.a)
        val s1 = alice.device.model.engine
        val cast = assertNotNull(Engine.options(s1, a).filterIsInstance<Input.Cast>().firstOrNull {
            s1.obj(it.card)?.definition?.faces?.first()?.name == "Ember Herald" })
        assertIs<GameDevice.Acted.Sent>(alice.send(EngineCombatScenario(f).cast(s1, a, cast)))
        val herald = (cast.card as ObjectId.Card).card.value
        alice.tick(true, sim.now)
        sim.flush(f.a)
        assertEquals("STACK", where(alice.play(), herald), "Alice's live device waits for Bob's answer")
        // Bob's app passes (his own tap, or his device by his response mode)
        sim.deliver(f.b)
        bob.playToStop()
        bob.tick(true, sim.now)
        if (bob.device.status == GameDevice.Status.YourMove) assertIs<GameDevice.Acted.Sent>(bob.pass())
        sim.flush(f.b)
        assertTrue(sim.pending(f.a) > 0, "Bob's pass is on its way to Alice")
        sim.deliver(f.a)
        val shown = assertNotNull(alice.shown)
        assertFalse(shown.status is GameDevice.Status.Playing, "no playback for Bob's pass: ${shown.status}")
        assertFalse(shown.behind, "his pass is shown")
        assertEquals("BATTLEFIELD", where(alice.play(), herald), "the Herald resolved on his pass")
        assertTrue(shown.held.last { it.author == f.b }.let(EngineEventText::pass), "the last event of Bob's she was shown is his pass")
    }
}
