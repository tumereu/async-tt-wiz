package org.asyncttwiz.android

import arcanum.api.TurnStep
import arcanum.engine.Engine
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.state.GameState
import arcanum.engine.state.PlayerId
import org.asyncttwiz.protocol.game.GameDevice
import org.asyncttwiz.protocol.game.GameEvent
import org.asyncttwiz.protocol.game.Placement
import org.asyncttwiz.protocol.game.PlaySettings
import org.asyncttwiz.protocol.game.ResponseMode
import org.asyncttwiz.protocol.game.TableChange
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * HIS-107 (owner problem report 2026-11-05: attack, then a quick Undo): an Undo tapped while the player's own action is
 * still on its way to the server waits for it and then takes back that action, the one they last made when they tapped
 * (by its event), never one made or arriving after; it ends moves waiting to be made again after a lost race (SYS-077);
 * when it is no longer free it opens the Undo dialog (HIS-099). HIS-084: the device holds at the point a free undo landed
 * on while only table-only events arrive. Both devices on [EngineAppSim]; what Alice's device sends waits until
 * [EngineAppSim.flush]ed, what the server says until [EngineAppSim.deliver]ed.
 */
class QueuedUndoTest {
    private val f = EngineGameFixture()
    private val alice = PlayerId(f.a)
    private val bobId = PlayerId(f.b)
    private val sim = EngineAppSim(f)

    /** Both devices live: each gets what the server sent, shows it and makes its own steps, until nothing moves. */
    private fun settle() = repeat(20) {
        for (p in listOf(f.a, f.b)) {
            sim.deliver(p); sim.game(p).playToStop(); sim.game(p).tick(sim.now); sim.flush(p)
        }
    }

    /** Alice's device sends what it has, gets the server's answers and shows them (Bob's device hears nothing). */
    private fun aliceRoundTrip(game: EngineGame) { repeat(3) { sim.flush(f.a); sim.deliver(f.a); game.playToStop(); game.tick(sim.now) } }

    /** Both keep (live) and play along until [stop] holds for Alice's device: in their main phases each plays a Mountain,
     * then ends their turn; everyone else passes. */
    private fun playUntil(what: String, stop: (EngineGame) -> Boolean): EngineGame {
        val game = sim.load(f.a)
        val bob = sim.load(f.b)
        game.tick(1_000); bob.tick(1_000)
        assertIs<GameDevice.Acted.Sent>(bob.keep(emptyList()))
        settle()
        assertIs<GameDevice.Acted.Sent>(game.keep(emptyList()))
        repeat(200) {
            settle()
            if (game.device.session.pending.isEmpty() && stop(game)) return game
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
        error("never reached $what: ${game.device.model.engine.turn}")
    }

    private fun inMain(s: GameState) =
        s.turn.active == alice && s.turn.step == TurnStep.PrecombatMain && s.stack.isEmpty() && s.priority.holder == alice

    /** Alice's main phase with priority after her land, with Bob's Mountain on the battlefield. */
    private fun aliceInMain(): EngineGame = playUntil("Alice's main after her land") { g ->
        val s = g.device.model.engine
        inMain(s) && (s.turn.landsPlayed[alice] ?: 0) > 0 && bobsMountain(s) != null && castable(s)
    }

    private fun castable(s: GameState) = Engine.options(s, alice).any { it is Input.Cast }
    private fun bobsMountain(s: GameState) = s.battlefield.firstOrNull { s.obj(it)?.controller == bobId }
    private fun alicesMountain(s: GameState) = s.battlefield.first { s.obj(it)?.controller == alice }

    /** A Storm Spark cast by Alice with her own answers (the first target, the suggested payment). */
    private fun spark(s: GameState): List<Input> {
        val cast = assertNotNull(Engine.options(s, alice).filterIsInstance<Input.Cast>().firstOrNull(), "a Storm Spark castable")
        val copy = s.deepCopy()
        val inputs = mutableListOf<Input>(cast)
        var input: Input = cast
        repeat(10) {
            Engine.perform(copy, input).changes.forEach(copy::apply)
            val d = copy.pending?.decision ?: return inputs
            if (d is Decision.Random || Engine.decider(copy, d.player) != alice) return inputs
            input = if (d is Decision.ManaPayment) Input.Answer(d.id, Answer.ManaPayment(d.suggestion))
            else Engine.options(copy, alice).filterIsInstance<Input.Answer>().first { it.decision == d.id }
            inputs += input
        }
        return inputs
    }

    private fun casts(game: EngineGame): List<GameEvent> = (game.device.session.events + game.device.session.pending)
        .filter { e -> e.author == f.a && e.engine?.steps.orEmpty().any { it.input is Input.Cast } }

    private fun lastCast(game: EngineGame): GameEvent = casts(game).last()

    private fun refusedNotes(game: EngineGame) = game.notes.count { it is GameDevice.Note.Refused }

    private fun moveHerMountain(game: EngineGame, x: Int): GameEvent {
        val mountain = alicesMountain(game.device.model.engine)
        val acted = assertIs<GameDevice.Acted.Sent>(game.table(listOf(TableChange.Placed(mountain, game.device.model.table.placements[mountain], Placement(x, 0)))))
        return acted.event
    }

    // HIS-107: Alice casts Storm Spark and taps Undo before the server has her cast: the Undo waits for it, then takes it
    // back (no shake, no note)
    @Test fun anUndoTappedWhileHerCastIsSentWaitsAndTakesItBack() {
        val game = aliceInMain()
        val before = sim.head
        assertFalse(game.send(spark(game.device.model.engine)) is GameDevice.Acted.Refused)
        val cast = lastCast(game)
        assertTrue(game.device.session.pending.any { it.id == cast.id }, "her cast on its way")
        assertEquals(GameDevice.Undo.Queued(cast.id), game.undo())
        aliceRoundTrip(game)
        assertEquals(before, sim.head, "the server back before her cast")
        assertTrue(casts(game).none { it.id == cast.id }, "her cast taken back on her device")
        assertTrue(inMain(game.device.model.engine), "back in her main phase")
        assertEquals(GameDevice.Status.YourMove, game.device.status)
        assertEquals(0, refusedNotes(game), "no note: ${game.notes}")
    }

    // HIS-107: the queued Undo takes back the cast it was tapped after, not only the table move she made after the tap
    // (its point is before the cast, so the move goes with it)
    @Test fun aQueuedUndoTakesBackTheCastNotOnlyALaterTableMove() {
        val game = aliceInMain()
        val before = sim.head
        game.send(spark(game.device.model.engine))
        val cast = lastCast(game)
        assertEquals(GameDevice.Undo.Queued(cast.id), game.undo())
        moveHerMountain(game, 6)
        aliceRoundTrip(game)
        assertEquals(before, sim.head, "back before her cast")
        assertTrue(casts(game).none { it.id == cast.id }, "her cast taken back")
        assertEquals(0, refusedNotes(game), "no note: ${game.notes}")
    }

    // HIS-107: Undo tapped while her table move (made after her confirmed cast) is on its way takes back only that move,
    // never the earlier cast
    @Test fun aQueuedUndoOfATableMoveKeepsTheEarlierCast() {
        val game = aliceInMain()
        game.send(spark(game.device.model.engine))
        aliceRoundTrip(game)
        val cast = lastCast(game)
        assertTrue(game.device.session.pending.isEmpty(), "her cast on the server")
        val afterCast = sim.head
        val mountain = alicesMountain(game.device.model.engine)
        val placed = game.device.model.table.placements[mountain]
        val move = moveHerMountain(game, 7)
        assertEquals(GameDevice.Undo.Queued(move.id), game.undo())
        aliceRoundTrip(game)
        assertEquals(afterCast, sim.head, "only the move taken back")
        assertTrue(casts(game).any { it.id == cast.id }, "her cast stays")
        assertEquals(placed, game.device.model.table.placements[mountain], "her Mountain back in its place")
        assertEquals(0, refusedNotes(game), "no note: ${game.notes}")
    }

    // HIS-107, HIS-087, HIS-099: Bob has answered her cast (his pass) by the time the queued Undo is made: not free, so the
    // Undo dialog opens with the point before her cast chosen
    @Test fun aQueuedUndoThatIsNoLongerFreeOpensTheUndoDialog() {
        val game = aliceInMain()
        val bob = sim.game(f.b)
        game.send(spark(game.device.model.engine))
        val cast = lastCast(game)
        assertEquals(GameDevice.Undo.Queued(cast.id), game.undo())
        sim.flush(f.a)
        // Bob's device shows her cast and he passes before Alice hears anything back
        sim.deliver(f.b); bob.playToStop(); bob.tick(sim.now)
        assertFalse(bob.pass() is GameDevice.Acted.Refused)
        sim.flush(f.b)
        aliceRoundTrip(game)
        assertTrue(game.notes.any { it == GameDevice.Note.RequestNeeded(cast.parentId) }, "the Undo dialog: ${game.notes}")
        assertTrue(casts(game).any { it.id == cast.id }, "nothing taken back yet")
    }

    // HIS-107, SYS-077: the server took Bob's real pass before Alice's assumption of it and her land played on it; the
    // refusal comes first, so her land waits to be made again on his pass. Her Undo then takes the land back: it is not
    // made again (and nothing tells her it wasn't taken)
    @Test fun anUndoOfAMoveWaitingToBeMadeAgainEndsIt() {
        val game = playUntil("Alice's main before her land, a Spark to cast") { g ->
            val s = g.device.model.engine
            inMain(s) && s.turn.number >= 3 && (s.turn.landsPlayed[alice] ?: 0) == 0 && castable(s) &&
                Engine.options(s, alice).any { it is Input.PlayLand }
        }
        val bob = sim.game(f.b)
        game.send(spark(game.device.model.engine))
        // Bob's device stops at her Spark (his pause); Alice's waits for him with Assume
        sim.flush(f.a); sim.deliver(f.b); bob.playToStop(); bob.tick(sim.now); sim.flush(f.b)
        sim.deliver(f.a); game.tick(sim.now)
        assertEquals(GameDevice.Offer.AssumeResolves, (game.device.status as? GameDevice.Status.Waiting)?.offer)
        // Bob passes (the server takes it); Alice, hearing nothing, assumes his pass and plays her land
        assertFalse(bob.pass() is GameDevice.Acted.Refused)
        sim.flush(f.b)
        assertIs<GameDevice.Acted.Sent>(game.assume())
        assertTrue(inMain(game.device.model.engine), "the Spark resolved on her assumption")
        val land = Engine.options(game.device.model.engine, alice).filterIsInstance<Input.PlayLand>().first()
        assertFalse(game.send(listOf(land)) is GameDevice.Acted.Refused)
        sim.flush(f.a)
        // Her refusals before Bob's revision: the land waits to be made again on his pass
        sim.refusalsFirst(f.a)
        sim.deliver(f.a, 2)
        assertTrue(game.device.session.pending.isEmpty(), "her assumption and land refused")
        assertEquals(0, game.device.model.engine.turn.landsPlayed[alice] ?: 0, "her land taken back for now")
        assertIs<GameDevice.Undo.Queued>(game.undo())
        aliceRoundTrip(game)
        settle()
        assertEquals(0, game.device.model.engine.turn.landsPlayed[alice] ?: 0, "her land not made again")
        assertTrue(game.device.repro(game.codec).log.none { it.startsWith("remake PlayLand") }, "nothing made again")
        assertEquals(0, refusedNotes(game), "no note: ${game.notes}")
    }

    // HIS-084: after a free undo her device waits for her where she acted, also after Bob's table-only move arrives there
    // (it held only while the tip was that point's event: then her Auto-skip passed her beginning of combat by itself)
    @Test fun theUndoPointHoldsWhenATableOnlyMoveArrives() {
        val game = aliceInMain()
        // Full control: she stops at her beginning of combat and casts Storm Spark there, keeping priority after it
        game.settings(PlaySettings(mode = ResponseMode.FullControl))
        aliceRoundTrip(game)
        assertFalse(game.pass() is GameDevice.Acted.Refused)
        aliceRoundTrip(game)
        assertEquals(TurnStep.BeginningOfCombat, game.device.model.engine.turn.step)
        assertEquals(GameDevice.Status.YourMove, game.device.status)
        game.send(spark(game.device.model.engine))
        aliceRoundTrip(game)
        val cast = lastCast(game)
        // Back to Auto-skip, then Undo (free: Bob hasn't seen it): she lands at her beginning of combat, where Auto-skip
        // would pass for her
        game.settings(PlaySettings())
        val undo = game.undo()
        assertTrue(undo == GameDevice.Undo.Free(cast.parentId) || undo is GameDevice.Undo.Queued, "$undo")
        aliceRoundTrip(game)
        assertEquals(cast.parentId, sim.head, "her cast taken back")
        fun atCombatStart() = game.device.model.engine.let { s -> s.turn.step == TurnStep.BeginningOfCombat && s.priority.holder == alice }
        assertTrue(atCombatStart(), "back at her beginning of combat")
        // Bob opens the game (catching up, so his device makes nothing of its own) and moves his Mountain, a table-only
        // event made at the head: her device still waits for her
        val bob = sim.load(f.b)
        assertTrue(bob.device.session.behind, "Bob catching up")
        val mountain = assertNotNull(bobsMountain(bob.device.model.engine))
        assertIs<GameDevice.Acted.Sent>(bob.table(listOf(TableChange.Placed(mountain, bob.device.model.table.placements[mountain], Placement(4, 0)))))
        sim.flush(f.b)
        aliceRoundTrip(game)
        assertEquals(Placement(4, 0), game.device.model.table.placements[mountain], "Bob's move shown")
        assertTrue(atCombatStart(), "still at her beginning of combat: ${game.device.model.engine.turn.step}")
        assertEquals(GameDevice.Status.YourMove, game.device.status)
    }
}
