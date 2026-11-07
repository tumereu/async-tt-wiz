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

    /** Bob keeps and leaves; Alice keeps and stands in her first main phase with priority, after playing a Mountain
     * when [land]; nothing of hers sent yet. */
    private fun aliceInMain(land: Boolean = false): EngineGame {
        val game = sim.load(f.a)
        val bob = sim.load(f.b)
        assertIs<GameDevice.Acted.Sent>(bob.keep(emptyList()))
        sim.flush(f.b); sim.deliver(f.a); game.playToStop()
        game.tick(1_000)
        assertIs<GameDevice.Acted.Sent>(game.keep(emptyList()))
        game.tick(2_000)
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

    // SYS-076 (smoke 2026-11-04): End turn's sameness: the same own turn moved on (another step, the player's own Stack
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
        // Stable decision identity (owner, 2026-11-05): a new line's reshuffle of a library's unknown part is no new
        // decision; an input in between is
        val parts = Engine.unknownOrder(s, alice)
        assertTrue(parts.any { it.size > 1 }, "an unknown part to reorder")
        val reorder = assertNotNull(Engine.reorderUnknown(s, alice, parts.map { it.reversed() }))
        assertTrue(GameDevice.sameDecision(s, s.deepCopy().also { it.apply(reorder) }), "the library reshuffled")
        assertFalse(GameDevice.sameDecision(s, s.deepCopy().also { it.inputCount += 1 }), "an input in between")
    }

    // ---- The server's race: the other player's event taken before the player's own move

    /** Both devices live: each gets what the server sent, shows it and makes its own steps, until nothing moves. */
    private fun settle() = repeat(20) {
        for (p in listOf(f.a, f.b)) {
            sim.deliver(p); sim.game(p).playToStop(); sim.game(p).tick(sim.now); sim.flush(p)
        }
    }

    /** Both keep (live) and play along until Alice's main phase with priority after her land, where [there] holds: in
     * their main phases each plays a Mountain, then ends their turn; everyone else passes. */
    private fun bothLiveTo(there: (GameState) -> Boolean): EngineGame {
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
            if (inMain && (s.turn.landsPlayed[alice] ?: 0) > 0 && there(s)) return game
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

    /**
     * Both live, in Alice's main phase (her first, or where [there] holds) after her land: she casts Storm Spark at Bob, so her game
     * waits for him (his pause, live). Bob passes (taken by the server; [bobsMove] after it) while Alice, hearing nothing
     * from him, taps Assume (the main button) and her device assumes that pass, the spark resolves and she is back in her main phase, where she makes [move]. Her assumption and her move reach the server after his pass: refused as stale, delivered with
     * [refusalsFirst] before his revision or after it.
     */
    private fun race(refusalsFirst: Boolean, there: (GameState) -> Boolean = { true }, bobsMove: (EngineGame) -> Unit = {},
        move: (EngineGame) -> GameDevice.Acted): EngineGame {
        // Bob in Normal: his pause at her spark waits on her phone (HIS-096: in Quick, unable to respond, his own mode
        // would pass it and her phone would assume that at once)
        sim.settings[f.b] = PlaySettings(mode = ResponseMode.Normal)
        val game = bothLiveTo(there)
        val bob = sim.game(f.b)
        assertIs<GameDevice.Acted.Sent>(game.send(spark(game.device.model.engine)))
        sim.flush(f.a); sim.deliver(f.b); bob.playToStop(); bob.tick(sim.now)
        if (bob.device.model.engine.priority.holder?.value == f.b) assertIs<GameDevice.Acted.Sent>(bob.pass())
        bobsMove(bob)
        sim.flush(f.b)
        assertTrue(sim.pending(f.a) > 0, "Bob's event is on its way to Alice")
        assertIs<GameDevice.Status.Waiting>(game.device.status, "Alice waits for Bob at his pause")
        game.tick(sim.now + 1_000)
        assertIs<GameDevice.Acted.Sent>(game.assume())
        game.tick(sim.now + 2_000)
        val s = game.device.model.engine
        assertEquals(TurnStep.PrecombatMain to f.a, s.turn.step to s.priority.holder?.value, "Alice in main 1 on Bob's assumed pass")
        assertTrue(s.stack.isEmpty(), "the spark resolved")
        game.notes.clear()
        assertIs<GameDevice.Acted.Sent>(move(game))
        sim.flush(f.a)
        if (refusalsFirst) sim.refusalsFirst(f.a)
        sim.deliver(f.a)
        game.playToStop()
        game.tick(sim.now + 3_000)
        return game
    }

    /** Two Storm Sparks in Alice's hand and two untapped Mountains of hers. */
    private fun twoSparks(s: GameState): Boolean =
        s.player(alice).hand.count { s.obj(it)?.definition?.faces?.first()?.name == "Storm Spark" } >= 2 &&
            s.battlefield.count { s.obj(it)?.controller == alice && s.obj(it)?.tapped == false } >= 2

    private fun EngineGame.made(input: (Input) -> Boolean) =
        device.session.pending.any { e -> e.engine?.steps.orEmpty().any { it.assumedBy == null && it.input?.let(input) == true } }

    // SYS-077: the server took Bob's pass before Alice's own pass made on her device's assumption of it; on his pass the
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

    // SYS-077: the same with a second Storm Spark cast on another Mountain (its target and payment as she chose them), in
    // a later turn of hers, the refusal first (his revision first: e2e): made again on his pass
    @Test fun aCastLostToTheOpponentsSamePassIsMadeAgain() {
        for (refusalsFirst in listOf(true)) {
            val game = SameDecisionRulesTest().race(refusalsFirst, there = ::twoSparks) { it.send(spark(it.device.model.engine)) }
            assertTrue(game.notes.none { it is GameDevice.Note.Refused }, "refusals first $refusalsFirst: no refusal note: ${game.notes}")
            assertTrue(game.made { it is Input.Cast },
                "refusals first $refusalsFirst: her cast made again: ${game.device.repro(f.codec).log.takeLast(20)}")
        }
    }

    // SYS-077 (e2e DamageDivisionTest, 2026-11-06: her pass To combat, then her attack): two moves of Alice's lost to Bob's
    // same pass, her device's own steps between them (after her first it assumed his pass and went on): both are made
    // again, each where those steps leave it, without a note
    @Test fun twoMovesWithTheDevicesOwnStepsBetweenAreBothMadeAgain() {
        val game = race(refusalsFirst = true) { g ->
            assertIs<GameDevice.Acted.Sent>(g.pass())
            g.tick(sim.now + 2_500)
            val s = g.device.model.engine
            assertEquals(f.a to GameDevice.Status.YourMove, s.priority.holder?.value to g.device.status, "her move again: ${s.turn}")
            assertNotEquals(TurnStep.PrecombatMain, s.turn.step, "on from her main 1 by her device's own steps")
            g.pass()
        }
        val log = game.device.repro(f.codec).log
        assertTrue(game.notes.none { it is GameDevice.Note.Refused }, "no refusal note: ${game.notes} log ${log.takeLast(25)}")
        assertEquals(2, log.count { it.startsWith("remake PassPriority") }, "both made again: ${log.takeLast(25)}")
    }

    // SYS-077, HIS-096 (e2e CombatBandTest, 2026-11-06; owner 2026-11-07): Bob passes at Alice's spell just as her device
    // assumes that pass for him (her Assume) and the server takes her assumption first: his pass is his answer at it
    // (HIS-109), the same one, so it is simply the assumption's confirmation: her assumption takes its place on his phone
    // with his board as it is, and nothing is told, both reply orders
    @Test fun aPassTheOtherPlayerAssumedFirstIsMadeAlready() {
        for (refusalsFirst in listOf(false, true)) SameDecisionRulesTest().assumedFirst(refusalsFirst)
    }

    private fun assumedFirst(refusalsFirst: Boolean) {
        // Bob can respond (an untapped Mountain and a Storm Spark of his), so his device leaves the pass to him
        val bobId = PlayerId(f.b)
        val game = bothLiveTo { s ->
            s.battlefield.any { s.obj(it)?.controller == bobId && s.obj(it)?.tapped == false } &&
                s.player(bobId).hand.any { s.obj(it)?.definition?.faces?.first()?.name == "Storm Spark" }
        }
        val bob = sim.game(f.b)
        assertIs<GameDevice.Acted.Sent>(game.send(spark(game.device.model.engine)))
        // Her own pass on it goes out; Bob's table shows the spark with his priority
        for (round in 1..5) {
            game.tick(sim.now); sim.flush(f.a); sim.deliver(f.b); bob.playToStop(); bob.tick(sim.now)
            if (bob.device.model.engine.priority.holder?.value == f.b) break
        }
        assertEquals(f.b, bob.device.model.engine.priority.holder?.value, "Bob's priority at her spark")
        assertTrue(bob.device.model.engine.stack.isNotEmpty(), "her spark on the Stack")
        assertIs<GameDevice.Acted.Sent>(bob.pass())
        // Alice, hearing nothing from him yet, assumes his pass; hers reaches the server first
        game.tick(sim.now + 1_000)
        assertIs<GameDevice.Acted.Sent>(game.assume())
        sim.flush(f.a)
        bob.notes.clear()
        sim.flush(f.b)
        if (refusalsFirst) sim.refusalsFirst(f.b)
        sim.deliver(f.b)
        bob.playToStop()
        bob.tick(sim.now + 3_000)
        val log = bob.device.repro(f.codec).log
        assertTrue(bob.notes.none { it is GameDevice.Note.Refused }, "refusals first $refusalsFirst: no note: ${bob.notes} ${log.takeLast(12)}")
        assertTrue(log.any { it.startsWith("adopted") } && log.none { it.startsWith("remake") }, "refusals first $refusalsFirst: ${log.takeLast(12)}")
        assertTrue(bob.device.session.pending.isEmpty(), "refusals first $refusalsFirst: his pass taken as the confirmation")
    }

    // SYS-077, SYS-012: Bob concedes after his pass, so the game Alice's move was made in is gone: not made again, and she
    // is told once, both reply orders
    @Test fun aMoveLostToAnOpponentsMoveThatChangedTheGameIsToldOnce() {
        for (refusalsFirst in listOf(false, true)) {
            val game = SameDecisionRulesTest().race(refusalsFirst, bobsMove = { it.concede() }) { it.pass() }
            assertEquals(1, game.notes.count { it == GameDevice.Note.Refused("stale") }, "refusals first $refusalsFirst: ${game.notes}")
            assertFalse(game.made { it == Input.PassPriority(alice) }, "refusals first $refusalsFirst: not made again")
        }
    }
}
