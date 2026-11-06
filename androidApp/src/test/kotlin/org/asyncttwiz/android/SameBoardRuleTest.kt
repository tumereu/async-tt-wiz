package org.asyncttwiz.android

import arcanum.api.TurnStep
import arcanum.engine.Engine
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.state.GameState
import arcanum.engine.state.PlayerId
import org.asyncttwiz.protocol.game.GameDevice
import org.asyncttwiz.protocol.game.Placement
import org.asyncttwiz.protocol.game.TableChange
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * SYS-011, SYS-012 (owner, 2026-11-05: one "same board" rule for every action): Assume, Undo and the table's own actions
 * tapped on a board the device has moved on from are made on the newer board when the same decision still waits for the
 * player there (only table-only events in between), as casts and passes are, and refused with SYS-012's note when an
 * input arrived. Alice's table is held ([HeldMain]): her device publishes the newer board, her screen still shows the
 * older one.
 */
class SameBoardRuleTest {
    private val f = EngineGameFixture()
    private val alice = PlayerId(f.a)
    private val bobId = PlayerId(f.b)
    private val held = HeldMain()
    private val sim = EngineAppSim(f) { if (it == f.a) held else EngineWorker.Direct }

    /** The engine thread at once; what it posts to the main thread (the published board, its notes) waits while
     * [holding]: the table still shows the older board. */
    private class HeldMain : EngineWorker {
        var holding = false
        private val waiting = ArrayDeque<() -> Unit>()
        override fun run(work: () -> Unit) = work()
        override fun <T> call(work: () -> T): T = work()
        override fun main(work: () -> Unit) { if (holding) waiting.add(work) else work() }
        fun release() { holding = false; while (waiting.isNotEmpty()) waiting.removeFirst()() }
    }

    /** Both devices live: each gets what the server sent, shows it and makes its own steps, until nothing moves. */
    private fun settle() = repeat(20) {
        for (p in listOf(f.a, f.b)) {
            sim.deliver(p); sim.game(p).playToStop(); sim.game(p).tick(sim.now); sim.flush(p)
        }
    }

    /** Both keep (live) and play along until Alice's main phase with priority, after her land, with Bob's Mountain on
     * the battlefield: in their main phases each plays a Mountain, then ends their turn; everyone else passes. */
    private fun aliceInMainWithBobsLand(): EngineGame {
        val game = sim.load(f.a)
        val bob = sim.load(f.b)
        game.tick(1_000); bob.tick(1_000)
        assertIs<GameDevice.Acted.Sent>(bob.keep(emptyList()))
        settle()
        assertIs<GameDevice.Acted.Sent>(game.keep(emptyList()))
        repeat(200) {
            settle()
            val s = game.device.model.engine
            val inMain = s.turn.active == alice && s.turn.step == TurnStep.PrecombatMain && s.stack.isEmpty() && s.priority.holder == alice
            if (inMain && (s.turn.landsPlayed[alice] ?: 0) > 0 && bobsMountain(s) != null && game.device.session.pending.isEmpty()) return game
            for (g in listOf(game, bob)) {
                // HIS-095, HIS-096: a stop at the other side's assumption about them (live too) is answered like their move
                if (g.device.status != GameDevice.Status.YourMove && g.device.status !is GameDevice.Status.Stopped) continue
                val m = g.device.model.engine
                val me = g.device.me
                val land = Engine.options(m, me).filterIsInstance<Input.PlayLand>().firstOrNull()
                when {
                    m.turn.active == me && m.stack.isEmpty() && m.turn.step == TurnStep.PrecombatMain && land != null &&
                        (m.turn.landsPlayed[me] ?: 0) == 0 -> g.send(listOf(land))
                    m.turn.active == me && m.stack.isEmpty() && m.turn.step == TurnStep.PrecombatMain -> g.endTurn()
                    m.priority.holder == me -> g.pass()
                    Engine.options(m, me).any { it is Input.DeclareAttackers || it is Input.DeclareBlockers } -> g.endTurn()
                }
            }
        }
        error("never reached: ${game.device.model.engine.turn}")
    }

    private fun bobsMountain(s: GameState) = s.battlefield.firstOrNull { s.obj(it)?.controller == bobId }

    /** A Storm Spark cast by [player] with their own answers (the first target, the suggested payment). */
    private fun spark(s: GameState, player: PlayerId): List<Input> {
        val cast = assertNotNull(Engine.options(s, player).filterIsInstance<Input.Cast>().firstOrNull(), "a Storm Spark castable")
        val copy = s.deepCopy()
        val inputs = mutableListOf<Input>(cast)
        var input: Input = cast
        repeat(10) {
            Engine.perform(copy, input).changes.forEach(copy::apply)
            val d = copy.pending?.decision ?: return inputs
            if (d is Decision.Random || Engine.decider(copy, d.player) != player) return inputs
            input = if (d is Decision.ManaPayment) Input.Answer(d.id, Answer.ManaPayment(d.suggestion))
            else Engine.options(copy, player).filterIsInstance<Input.Answer>().first { it.decision == d.id }
            inputs += input
        }
        return inputs
    }

    /** Alice casts Storm Spark; Bob's device stops at it (his pause), so hers waits for him with Assume. */
    private fun aliceWaitsWithAssume(): EngineGame {
        val game = aliceInMainWithBobsLand()
        val bob = sim.game(f.b)
        assertFalse(game.send(spark(game.device.model.engine, alice)) is GameDevice.Acted.Refused)
        sim.flush(f.a); sim.deliver(f.b); bob.playToStop(); bob.tick(sim.now)
        sim.deliver(f.a); game.tick(sim.now)
        assertEquals(GameDevice.Offer.AssumeResolves, (game.shown?.status as? GameDevice.Status.Waiting)?.offer, "Alice waits for Bob with Assume")
        return game
    }

    /** Bob moves his Mountain on his battlefield (a table-only event); Alice's device gets it and shows it. */
    private fun bobMovesHisMountain(game: EngineGame) {
        val bob = sim.game(f.b)
        val mountain = assertNotNull(bobsMountain(bob.device.model.engine))
        val before = bob.device.model.table.placements[mountain]
        assertIs<GameDevice.Acted.Sent>(bob.table(listOf(TableChange.Placed(mountain, before, Placement(4, 0)))))
        sim.flush(f.b); sim.deliver(f.a); game.playToStop(); game.tick(sim.now)
        assertTrue(game.device.model.table.placements[mountain] == Placement(4, 0), "Bob's move shown on Alice's device")
    }

    /** Bob casts his Storm Spark at the first target it offers (an input); Alice's device gets it and shows it. */
    private fun bobCastsASpark(game: EngineGame) {
        val bob = sim.game(f.b)
        assertFalse(bob.send(spark(bob.device.model.engine, bobId)) is GameDevice.Acted.Refused)
        sim.flush(f.b); sim.deliver(f.a); game.playToStop(); game.tick(sim.now)
        assertTrue(game.device.session.events.any { e -> e.author == f.b && e.engine?.steps.orEmpty().any { it.input is Input.Cast } },
            "Bob's Spark shown on Alice's device")
    }

    private fun assumedByAlice(game: EngineGame) =
        (game.device.session.events + game.device.session.pending).count { it.assumed?.assumedBy == alice }

    private fun refusedNotes(game: EngineGame) = game.notes.count { it == GameDevice.Note.Refused("stale") }

    // SYS-011: Assume tapped while Bob's table-only move arrived (not on Alice's screen yet): the same decision, made there
    @Test fun anAssumeOnABoardChangedOnlyByATableOnlyEventIsMade() {
        val game = aliceWaitsWithAssume()
        val before = assumedByAlice(game)
        val shown = assertNotNull(game.shown).version
        held.holding = true
        bobMovesHisMountain(game)
        assertEquals(shown, game.shown!!.version, "Alice's table still shows the older board")
        assertIs<GameDevice.Acted.Sent>(game.assume())
        held.release()
        assertEquals(0, refusedNotes(game), "no note: ${game.notes}")
        assertEquals(before + 1, assumedByAlice(game), "Bob's pass assumed")
    }

    // SYS-011, SYS-012: Assume tapped while Bob's Spark arrived (an input): another decision, not taken, with the note
    @Test fun anAssumeOnABoardWhereAnInputArrivedIsRefusedWithTheNote() {
        val game = aliceWaitsWithAssume()
        val before = assumedByAlice(game)
        held.holding = true
        bobCastsASpark(game)
        assertEquals(GameDevice.Acted.Refused("stale"), game.assume())
        held.release()
        assertEquals(1, refusedNotes(game), "told once: ${game.notes}")
        assertEquals(before, assumedByAlice(game), "nothing assumed")
    }

    // SYS-011, HIS-084: Undo tapped while Bob's table-only move arrived: the same decision, made there (her land taken back)
    @Test fun anUndoOnABoardChangedOnlyByATableOnlyEventIsMade() {
        val game = aliceInMainWithBobsLand()
        val shown = assertNotNull(game.shown).version
        held.holding = true
        bobMovesHisMountain(game)
        assertEquals(shown, game.shown!!.version, "Alice's table still shows the older board")
        assertIs<GameDevice.Undo.Free>(game.undo())
        held.release()
        assertEquals(0, refusedNotes(game), "no note: ${game.notes}")
    }

    // SYS-011, HIS-107 (owner problem report 2026-11-05): Undo tapped on the board of her own cast while her device had
    // published its later outcome (the server's confirmation, then Bob's pass assumed: the Spark resolved): moved on only
    // by her own events, so it takes back her cast, with no note
    @Test fun anUndoOnABoardMovedOnOnlyByHerOwnEventsTakesBackHerAction() {
        val game = aliceInMainWithBobsLand()
        game.settings(org.asyncttwiz.protocol.game.PlaySettings(alwaysAssumeResolves = true))
        sim.flush(f.a)
        assertFalse(game.send(spark(game.device.model.engine, alice)) is GameDevice.Acted.Refused)
        val cast = (game.device.session.events + game.device.session.pending).last { e ->
            e.author == f.a && e.engine?.steps.orEmpty().any { it.input is Input.Cast } }
        val shown = assertNotNull(game.shown).version
        held.holding = true
        sim.flush(f.a); sim.deliver(f.a); game.tick(sim.now)
        assertTrue(game.device.model.engine.stack.isEmpty(), "her device went on: Bob's pass assumed, the Spark resolved")
        assertEquals(shown, game.shown!!.version, "Alice's table still shows the board of her cast")
        val undo = game.undo()
        assertTrue(undo is GameDevice.Undo.Free || undo is GameDevice.Undo.Queued, "$undo")
        held.release()
        repeat(3) { sim.flush(f.a); sim.deliver(f.a); game.tick(sim.now) }
        assertEquals(0, refusedNotes(game), "no note: ${game.notes}")
        assertTrue((game.device.session.events + game.device.session.pending).none { it.id == cast.id }, "her cast taken back")
        assertEquals(cast.parentId, sim.head)
    }

    // SYS-011, SYS-012: Undo tapped while Bob's Spark arrived (an input): another decision, not taken, with the note
    @Test fun anUndoOnABoardWhereAnInputArrivedIsRefusedWithTheNote() {
        val game = aliceWaitsWithAssume()
        held.holding = true
        bobCastsASpark(game)
        assertEquals(GameDevice.Undo.Unavailable("stale"), game.undo())
        held.release()
        assertEquals(1, refusedNotes(game), "told once: ${game.notes}")
    }
}
