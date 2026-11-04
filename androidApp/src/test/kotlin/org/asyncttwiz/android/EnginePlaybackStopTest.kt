package org.asyncttwiz.android

import arcanum.engine.Engine
import arcanum.engine.input.Input
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import org.asyncttwiz.protocol.MtgStackKind
import org.asyncttwiz.protocol.PlayZone
import org.asyncttwiz.protocol.game.GameDevice
import org.asyncttwiz.protocol.game.PlaySettings
import org.asyncttwiz.protocol.game.ResponseMode
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Problem report SMUJ7M (owner playtest 2026-11-04): watching the other player's playback, their creature spell must not
 * reach the battlefield before the watcher answered at their decision stop. Two app engine games on the simulated server:
 * Alice casts an Ember Herald (an enters trigger, as the report's Squirrel) while Bob is away, her device assumes Bob lets
 * it resolve and she plays on; Bob's playback stops with the spell on the Stack, nothing after it shown (board, motion
 * steps, local head), and his Resolve there confirms and plays on. (The report itself was Auto-skip working as STK-036
 * says: the watcher couldn't respond to the spell, so playback confirmed it, and stopped at its enters trigger, where
 * they could.)
 */
class EnginePlaybackStopTest {
    private val f = EngineGameFixture(heralds = true)
    private val a = PlayerId(f.a)
    private val b = PlayerId(f.b)

    /** Where the Herald [herald] is on [play]'s board: "STACK" while its spell is there, else its card's zone. */
    private fun where(play: TableBoard, herald: String): String {
        val board = play.board
        if (board.stackEffects.any { it.sourceId == herald && it.item?.kind == MtgStackKind.Spell }) return "STACK"
        val card = board.cards.single { it.id == herald }
        return board.containers.single { it.id == card.containerId }.zone.name
    }

    private fun tokens(play: TableBoard) = play.board.cards.count { c ->
        c.token && play.board.containers.single { it.id == c.containerId }.zone == PlayZone.BATTLEFIELD }

    /** Alice's device on its own while Bob is away: what the server sent played back, her device's own steps sent. */
    private fun alone(sim: EngineAppSim, alice: EngineGame) {
        sim.deliver(f.a)
        alice.playToStop()
        (alice.device.status as? GameDevice.Status.Stopped)?.let { alice.act(it.assumed) }
        alice.tick(false, sim.now)
        sim.flush(f.a)
        sim.now += 1_000
    }

    // HIS-095, STK-036, I-D56 §7 (SMUJ7M): playback stops before the assumed pass about the watcher with the spell on the
    // Stack and shows nothing of the assumed line after it; answering the same confirms it and playback goes on
    @Test fun playbackStopsWithTheSpellOnTheStackUntilTheWatcherAnswers() {
        val sim = EngineAppSim(f)
        val alice = sim.load(f.a)
        val bob = sim.load(f.b)
        // Normal: Bob's playback stops at Alice's spell on the Stack whatever his hand holds
        bob.settings(PlaySettings(mode = ResponseMode.Normal))
        sim.flush(f.b)
        alice.keep(emptyList())
        sim.flush(f.a)
        sim.deliver(f.b)
        bob.playToStop()
        bob.keep(emptyList())
        sim.flush(f.b)
        // Bob is away from here on: Alice's device assumes his answers at once
        repeat(5) { alone(sim, alice) }
        assertEquals(GameDevice.Status.YourMove, alice.device.status)
        val s0 = alice.device.model.engine
        val mountain = assertNotNull(s0.player(a).hand.firstOrNull { s0.obj(it)?.definition?.faces?.first()?.name == "Mountain" },
            "a Mountain in hand: ${s0.player(a).hand.map { s0.obj(it)?.definition?.faces?.first()?.name }}")
        alice.send(listOf(Input.PlayLand(a, mountain)))
        sim.flush(f.a)
        val s1 = alice.device.model.engine
        val cast = assertNotNull(Engine.options(s1, a).filterIsInstance<Input.Cast>().firstOrNull {
            s1.obj(it.card)?.definition?.faces?.first()?.name == "Ember Herald" }, "Herald castable: ${Engine.options(s1, a)}")
        val herald = (cast.card as ObjectId.Card).card.value
        assertIs<GameDevice.Acted.Sent>(alice.send(EngineCombatScenario(f).cast(s1, a, cast)))
        sim.flush(f.a)
        // Alice plays on past Bob's assumed pass: the Herald resolves, its token comes, her turn ends
        alice.endTurn()
        repeat(3) { alone(sim, alice) }
        val line = sim.line()
        val castEvent = assertNotNull(line.firstOrNull { e -> e.engine?.steps?.any { it.input == cast } == true },
            "the cast on the line: ${line.map { e -> e.engine?.steps?.mapNotNull { it.input } }}")
        val assumed = line[line.indexOf(castEvent) + 1]
        assertEquals(a, assumed.assumed?.assumedBy, "the next event is Alice's assumed pass for Bob")
        assertEquals(Input.PassPriority(b), assumed.assumed?.input)
        assertTrue(line.indexOf(assumed) < line.lastIndex, "Alice played on after the assumption")

        // Bob watches the playback as the events arrive
        sim.deliver(f.b)
        val boards = mutableListOf<TableBoard>()
        var guard = 0
        while (bob.device.status is GameDevice.Status.Playing && guard++ < 200) {
            assertNotNull(bob.step())
            val shown = bob.shown!!
            boards += shown.board
            shown.transition?.steps?.let(boards::addAll)
        }
        val stop = assertIs<GameDevice.Status.Stopped>(bob.device.status)
        assertEquals(assumed.id, stop.event.id)
        assertEquals(Input.PassPriority(b), stop.assumed)
        assertEquals(castEvent.id, bob.device.session.localHead)
        assertEquals("STACK", where(bob.play(), herald), "the spell waits on the Stack for Bob")
        // Nothing of the assumed continuation was drawn: never the Herald on the battlefield, never its token
        assertTrue(boards.isNotEmpty())
        for (board in boards + bob.play()) {
            assertTrue(where(board, herald) != PlayZone.BATTLEFIELD.name, "the Herald shown on the battlefield before Bob answered")
            assertEquals(0, tokens(board), "the Herald's token shown before Bob answered")
        }
        sim.flush(f.b)
        assertEquals(castEvent.id, sim.heads[f.b], "the server holds Bob's local head at the cast")

        // Resolve: the same as assumed, so it confirms and playback goes on to the Herald on the battlefield
        assertEquals(GameDevice.Acted.Confirmed, bob.act(stop.assumed))
        sim.flush(f.b)
        assertEquals(assumed.id, sim.heads[f.b])
        bob.step()
        assertEquals(PlayZone.BATTLEFIELD.name, where(bob.play(), herald))
    }
}
