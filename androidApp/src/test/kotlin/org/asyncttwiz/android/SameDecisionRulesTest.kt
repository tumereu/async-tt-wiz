package org.asyncttwiz.android

import arcanum.api.TurnStep
import arcanum.engine.Engine
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.state.GameState
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import org.asyncttwiz.protocol.game.GameDevice
import org.asyncttwiz.protocol.game.PlaySettings
import org.asyncttwiz.protocol.game.ResponseMode
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * SYS-011 (owner, 2026-11-04): an own action tapped on a board the device has already moved on from (the other
 * player's event arrived, not on screen yet) is made on the newer board when the same decision still waits for the
 * player there, and refused with SYS-012's note when the board changed in a way that matters. The taps themselves play
 * on the real server in e2e/ (SameDecisionTapTest); here are what makes two decisions the same, and the server's race
 * with the refusal reaching the device before the other player's revision, an order one socket never delivers but a
 * reconnect may.
 */
class SameDecisionRulesTest {
    private val f = EngineGameFixture()
    private val alice = PlayerId(f.a)

    private val sim = EngineAppSim(f)

    /** Alice keeps while Bob is away (his keep assumed) and stands in her first main phase with priority, after playing
     * a Mountain when [land]; nothing of hers sent yet. */
    private fun aliceInMain(land: Boolean = false): EngineGame {
        val game = sim.load(f.a)
        sim.load(f.b)
        game.tick(live = false, now = 1_000)
        assertIs<GameDevice.Acted.Sent>(game.keep(emptyList()))
        game.tick(live = false, now = 2_000)
        if (land) {
            val s = game.device.model.engine
            val mountain = assertNotNull(Engine.options(s, alice).filterIsInstance<Input.PlayLand>().firstOrNull(), "a Mountain in hand")
            assertIs<GameDevice.Acted.Sent>(game.send(listOf(mountain)))
        }
        val shown = assertNotNull(game.shown)
        assertEquals(TurnStep.PrecombatMain, shown.engine.turn.step)
        assertEquals(GameDevice.Status.YourMove, shown.status)
        return game
    }

    /** Spark's cast with Alice's own answers (its first target, the suggested payment), as the cast panel makes it. */
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
            else Engine.options(copy, alice).filterIsInstance<Input.Answer>().filter { it.decision == d.id }
                .first()
            inputs += input
        }
        return inputs
    }

    // SYS-011 (smoke 2026-11-04): End turn's sameness: the same own turn moved on (another step, the player's own Stack
    // object) still ends it; the other player's Stack object or another turn don't
    @Test fun endTurnIsTheSameDecisionAnywhereInTheSameOwnTurn() {
        val game = aliceInMain(land = true)
        val s = game.device.model.engine
        val bob = PlayerId(f.b)
        val combat = s.deepCopy().also { it.turn = it.turn.copy(step = TurnStep.BeginningOfCombat) }
        assertTrue(GameDevice.sameEndTurn(s, combat, alice), "another step of her turn")
        assertFalse(GameDevice.sameEndTurn(s, s.deepCopy().also { it.turn = it.turn.copy(number = it.turn.number + 1, active = bob) }, alice),
            "Bob's turn")
        assertFalse(GameDevice.sameEndTurn(s, combat, bob), "not Bob's own turn")
        val cast = s.deepCopy().also { c -> for (input in spark(s)) Engine.perform(c, input).changes.forEach(c::apply) }
        assertTrue(cast.stack.isNotEmpty(), "Storm Spark on the Stack")
        assertTrue(GameDevice.sameEndTurn(s, cast, alice), "her own spell")
        val theirs = cast.deepCopy().also { c -> c.stackItems.replaceAll { _, item -> item.copy(controller = bob) } }
        assertFalse(GameDevice.sameEndTurn(s, theirs, alice), "a Stack object of Bob's she never saw")
    }

    // SYS-011: what makes a decision the same: nothing in the game differs but its record of what happened; a new Stack
    // object, another step or an object gone (a chosen target) make it another one
    @Test fun theSameDecisionIsTheSameGameApartFromItsRecord() {
        val game = aliceInMain(land = true)
        val s = game.device.model.engine
        assertTrue(GameDevice.sameDecision(s, s.deepCopy().also { it.log.clear(); it.timestampCounter += 5 }))
        assertFalse(GameDevice.sameDecision(s, s.deepCopy().also { it.turn = it.turn.copy(step = TurnStep.BeginningOfCombat) }), "another step")
        val land: ObjectId = s.battlefield.first()
        assertFalse(GameDevice.sameDecision(s, s.deepCopy().also { it.battlefield.remove(land) }), "the target gone")
        assertFalse(GameDevice.sameDecision(s, s.deepCopy().also { it.stack.add(land) }), "a new Stack object")
    }

    // ---- The server's race: the other player's event taken before the player's own move

    /**
     * Alice keeps while Bob is live, so her game waits for his keep; Bob keeps (taken by the server; [bobsMove] after
     * it) while Alice's device, his presence gone, assumes that keep and goes on to her first main phase, where she
     * makes [move]. Her assumption and her move reach the server after his keep: refused as stale, delivered with
     * [refusalsFirst] before his revision or after it.
     */
    private fun race(refusalsFirst: Boolean, bobsMove: (EngineGame) -> Unit = {}, move: (EngineGame) -> GameDevice.Acted): EngineGame {
        val game = sim.load(f.a)
        val bob = sim.load(f.b)
        game.tick(live = true, now = 1_000); bob.tick(live = true, now = 1_000)
        assertIs<GameDevice.Acted.Sent>(game.keep(emptyList()))
        sim.flush(f.a); sim.deliver(f.b); bob.playToStop()
        assertIs<GameDevice.Acted.Sent>(bob.keep(emptyList()))
        bobsMove(bob)
        sim.flush(f.b)
        assertTrue(sim.pending(f.a) > 0, "Bob's event is on its way to Alice")
        game.tick(live = false, now = 200_000); game.tick(live = false, now = 300_000)
        val s = game.device.model.engine
        assertEquals(TurnStep.PrecombatMain to f.a, s.turn.step to s.priority.holder?.value, "Alice in main 1 on Bob's assumed keep")
        game.notes.clear()
        assertIs<GameDevice.Acted.Sent>(move(game))
        sim.flush(f.a)
        if (refusalsFirst) sim.refusalsFirst(f.a)
        sim.deliver(f.a)
        game.playToStop()
        game.tick(live = false, now = 201_000)
        return game
    }

    private fun EngineGame.made(input: (Input) -> Boolean) =
        device.session.pending.any { e -> e.engine?.steps.orEmpty().any { it.assumedBy == null && it.input?.let(input) == true } }

    // SYS-011: the server took Bob's keep before Alice's own pass made on her device's assumption of it; on his keep the
    // same decision waits for her, so her pass is made again there without a note, the refusal reaching her before
    // Bob's revision (his revision first: e2e SameDecisionTapTest)
    @Test fun aPassLostToTheOpponentsSamePassIsMadeAgainRefusalFirst() = passMadeAgain(refusalsFirst = true)

    private fun passMadeAgain(refusalsFirst: Boolean) {
        val game = race(refusalsFirst) { it.pass() }
        assertTrue(game.notes.none { it is GameDevice.Note.Refused }, "no refusal note: ${game.notes}")
        assertTrue(game.made { it == Input.PassPriority(alice) },
            "her pass made again: ${game.device.session.pending.map { e -> e.engine?.steps?.map { it.input } }} log " +
                game.device.repro(f.codec).log.takeLast(25))
        assertTrue(game.device.repro(f.codec).log.any { it.startsWith("remake PassPriority") }, "made again after the race")
        assertNotEquals(TurnStep.PrecombatMain, game.device.model.engine.turn.step, "her main 1 is over")
    }

    // SYS-011: the same with a Mountain played and Storm Spark cast on it (its target and payment as she chose them), the
    // refusal first (his revision first: e2e): each made again on the one before
    @Test fun aCastLostToTheOpponentsSamePassIsMadeAgain() {
        for (refusalsFirst in listOf(true)) {
            val game = SameDecisionRulesTest().race(refusalsFirst) {
                val land = Engine.options(it.device.model.engine, alice).filterIsInstance<Input.PlayLand>().first()
                assertIs<GameDevice.Acted.Sent>(it.send(listOf(land)))
                it.send(spark(it.device.model.engine))
            }
            assertTrue(game.notes.none { it is GameDevice.Note.Refused }, "refusals first $refusalsFirst: no refusal note: ${game.notes}")
            assertTrue(game.made { it is Input.PlayLand } && game.made { it is Input.Cast },
                "refusals first $refusalsFirst: her land and cast made again: ${game.device.repro(f.codec).log.takeLast(20)}")
        }
    }

    // SYS-011, SYS-012: Bob concedes after his keep, so the game Alice's move was made in is gone: not made again, and she
    // is told once, both reply orders
    @Test fun aMoveLostToAnOpponentsMoveThatChangedTheGameIsToldOnce() {
        for (refusalsFirst in listOf(false, true)) {
            val game = SameDecisionRulesTest().race(refusalsFirst, bobsMove = { it.concede() }) { it.pass() }
            assertEquals(1, game.notes.count { it == GameDevice.Note.Refused("stale") }, "refusals first $refusalsFirst: ${game.notes}")
            assertFalse(game.made { it == Input.PassPriority(alice) }, "refusals first $refusalsFirst: not made again")
        }
    }
}
