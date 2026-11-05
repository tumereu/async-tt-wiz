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
 * player there, and refused with SYS-012's note when the board changed in a way that matters.
 */
class SameDecisionTapTest {
    private val f = EngineGameFixture()
    private val alice = PlayerId(f.a)

    /** Engine work inline (as in tests), but what it posts to the main thread waits while [holding]: the table still
     * shows the board from before (the newer one is published but hasn't reached the screen). */
    private class HeldWorker : EngineWorker {
        var holding = false
        /** Called after each main-thread post runs (the table's view of the game after it). */
        var observe: () -> Unit = {}
        private val main = ArrayDeque<() -> Unit>()
        override fun run(work: () -> Unit) = work()
        override fun <T> call(work: () -> T): T = work()
        override fun main(work: () -> Unit) { if (holding) main.add(work) else { work(); observe() } }
        fun release() { holding = false; while (main.isNotEmpty()) { main.removeAt(0)(); observe() } }
    }

    private val held = HeldWorker()
    private val sim = EngineAppSim(f) { p -> if (p == f.a) held else EngineWorker.Direct }

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

    /** Bob comes back and confirms what Alice's device assumed for him (the same answers): the server takes his
     * confirmations, which reach Alice as revisions that change nothing in the game. */
    private fun bobConfirms() {
        sim.flush(f.a)
        sim.deliver(f.a)
        val bob = sim.load(f.b)
        if (bob.device.catchUp) bob.startPlayback()
        var confirmed = 0
        repeat(50) {
            when (val status = bob.device.status) {
                is GameDevice.Status.Playing -> bob.step()
                is GameDevice.Status.Stopped -> { bob.act(status.assumed); confirmed++ }
                else -> return@repeat
            }
        }
        assertTrue(confirmed > 0, "Bob confirmed his assumed keep")
        sim.flush(f.b)
        assertTrue(sim.pending(f.a) > 0, "his confirmations are on their way to Alice")
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

    private fun sameDecisionLogged(what: String) = AppLog.text().lines().any { "Own $what made on board" in it && "the same decision" in it }

    // SYS-011: Alice taps Next in her main phase while Bob's confirmation of the pass (keep) assumed for him arrives
    // and rebuilds her board: the same decision still waits for her there, so her pass is made on the newer board
    @Test fun aPassTappedWhileTheOpponentsPassArrivesIsMade() {
        val game = aliceInMain()
        bobConfirms()
        val shown = assertNotNull(game.shown)
        held.holding = true
        sim.deliver(f.a)
        assertEquals(shown.version, game.shown!!.version, "the table still shows the board from before Bob's revision")
        val acted = game.pass()
        held.release()
        assertFalse(acted is GameDevice.Acted.Refused, "her pass is taken: $acted")
        assertTrue(game.notes.none { it is GameDevice.Note.Refused }, "no refusal note: ${game.notes}")
        assertTrue(sameDecisionLogged("pass"), "made on the newer board as the same decision")
        assertNotEquals(TurnStep.PrecombatMain to 1, game.device.model.engine.turn.let { it.step to it.number }, "her turn went on")
    }

    // SYS-011: Alice casts Storm Spark while his revision arrives that changes nothing in the game: the cast,
    // its target and its payment are the same decision on the newer board, so it is made there
    @Test fun aCastWhileTheOpponentsMoveChangesNothingRelevantIsMade() {
        val game = aliceInMain(land = true)
        bobConfirms()
        val inputs = spark(game.shown!!.engine)
        held.holding = true
        sim.deliver(f.a)
        val acted = game.send(inputs)
        held.release()
        assertFalse(acted is GameDevice.Acted.Refused, "her cast is taken: $acted")
        assertTrue(game.notes.none { it is GameDevice.Note.Refused }, "no refusal note: ${game.notes}")
        assertTrue(sameDecisionLogged("send"), "made on the newer board as the same decision")
        assertTrue(game.device.session.pending.any { e -> e.engine?.steps.orEmpty().any { it.input is Input.Cast } }, "Storm Spark was cast")
    }

    // SYS-011, SYS-012: Alice casts Storm Spark on her main phase while Bob's real mulligan arrives instead of the keep
    // her device assumed: the game is back in the opening, the board she tapped on is gone; refused with the note
    @Test fun aCastOnABoardThatChangedInAWayThatMattersIsRefusedWithTheNote() {
        val game = aliceInMain(land = true)
        val inputs = spark(game.shown!!.engine)
        val bob = sim.load(f.b)
        assertIs<GameDevice.Acted.Sent>(bob.mulligan())
        sim.flush(f.b)
        held.holding = true
        sim.deliver(f.a)
        val acted = game.send(inputs)
        held.release()
        assertEquals(GameDevice.Acted.Refused("stale"), acted)
        assertTrue(game.notes.any { it == GameDevice.Note.Refused("stale") }, "told with SYS-012's note: ${game.notes}")
        assertTrue(game.device.model.engine.stack.isEmpty(), "nothing cast")
    }

    // SYS-011 (switchreview #6 kept): a fast second tap of Next on the still-shown board would pass at a point Alice never
    // saw (Full control: her beginning of combat); a different decision, so it isn't taken
    @Test fun aFastSecondPassIsNotMadeAtAPointNeverShown() {
        val game = aliceInMain()
        game.settings(PlaySettings(mode = ResponseMode.FullControl))
        val shown = assertNotNull(game.shown)
        held.holding = true
        assertIs<GameDevice.Acted.Sent>(game.pass())
        assertEquals(shown.version, game.shown!!.version, "the table still shows main 1")
        val second = game.pass()
        held.release()
        assertEquals(GameDevice.Acted.Refused("stale"), second)
        assertTrue(game.notes.any { it == GameDevice.Note.Refused("stale") })
        assertEquals(TurnStep.BeginningOfCombat, game.device.model.engine.turn.step, "her first pass only")
    }

    // SYS-076 (smoke 2026-11-04): Alice taps To combat (her pass in main 1), then End turn before the board her device
    // moved on to by itself (Bob's passes assumed, past an empty combat) reaches the screen: End turn may be made at any
    // priority of her own turn, so it is made on the newer board, not dropped
    @Test fun endTurnTappedWhileTheDeviceStepsOnByItselfIsMade() {
        val game = aliceInMain()
        val turn = game.shown!!.engine.turn.number
        held.holding = true
        assertFalse(game.pass() is GameDevice.Acted.Refused, "To combat")
        assertEquals(TurnStep.PrecombatMain, game.shown!!.engine.turn.step, "the table still shows main 1")
        assertNotEquals(TurnStep.PrecombatMain, game.device.model.engine.turn.step, "her device went on by itself")
        game.endTurn()
        held.release()
        assertTrue(game.notes.none { it is GameDevice.Note.Refused }, "no refusal note: ${game.notes}")
        assertTrue(sameDecisionLogged("endTurn"), "made on the newer board")
        val s = game.device.model.engine
        assertTrue(s.turn.number > turn && s.turn.active != alice, "her turn ended: ${s.turn}")
    }

    // SYS-076, SYS-012 (smoke 2026-11-04): a second End turn tapped on Alice's turn after the first one already took the
    // game into Bob's turn is another decision: not taken, and the note comes with a board of its own, so the table
    // (which reads notes when a board arrives) tells it at once
    @Test fun endTurnOnABoardOfAnEndedTurnIsRefusedWithTheNoteAtOnce() {
        val game = aliceInMain()
        held.holding = true
        game.endTurn()
        assertEquals(alice, game.shown!!.engine.turn.active, "the table still shows her turn")
        assertNotEquals(alice, game.device.model.engine.turn.active, "her device is in Bob's turn")
        val seen = mutableListOf<Pair<Long, Boolean>>()
        held.observe = { seen += game.version to game.notes.any { it == GameDevice.Note.Refused("stale") } }
        game.endTurn()
        held.release()
        assertTrue(game.notes.any { it == GameDevice.Note.Refused("stale") }, "told with SYS-012's note: ${game.notes}")
        val told = seen.indexOfFirst { it.second }
        assertTrue(told > 0 && seen[told].first > seen[told - 1].first, "the note arrived with a new board: $seen")
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

    // SYS-077: the server took Bob's keep before Alice's own pass made on her device's assumption of it; on his keep the
    // same decision waits for her, so her pass is made again there without a note (his revision first)
    @Test fun aPassLostToTheOpponentsSamePassIsMadeAgainRevisionFirst() = passMadeAgain(refusalsFirst = false)

    // SYS-077: as above, the refusal reaching her before Bob's revision
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

    // SYS-077: the same with a Mountain played and Storm Spark cast on it (its target and payment as she chose them), both
    // reply orders: each made again on the one before
    @Test fun aCastLostToTheOpponentsSamePassIsMadeAgain() {
        for (refusalsFirst in listOf(false, true)) {
            val game = SameDecisionTapTest().race(refusalsFirst) {
                val land = Engine.options(it.device.model.engine, alice).filterIsInstance<Input.PlayLand>().first()
                assertIs<GameDevice.Acted.Sent>(it.send(listOf(land)))
                it.send(spark(it.device.model.engine))
            }
            assertTrue(game.notes.none { it is GameDevice.Note.Refused }, "refusals first $refusalsFirst: no refusal note: ${game.notes}")
            assertTrue(game.made { it is Input.PlayLand } && game.made { it is Input.Cast },
                "refusals first $refusalsFirst: her land and cast made again: ${game.device.repro(f.codec).log.takeLast(20)}")
        }
    }

    // SYS-077, SYS-012: Bob concedes after his keep, so the game Alice's move was made in is gone: not made again, and she
    // is told once, both reply orders
    @Test fun aMoveLostToAnOpponentsMoveThatChangedTheGameIsToldOnce() {
        for (refusalsFirst in listOf(false, true)) {
            val game = SameDecisionTapTest().race(refusalsFirst, bobsMove = { it.concede() }) { it.pass() }
            assertEquals(1, game.notes.count { it == GameDevice.Note.Refused("stale") }, "refusals first $refusalsFirst: ${game.notes}")
            assertFalse(game.made { it == Input.PassPriority(alice) }, "refusals first $refusalsFirst: not made again")
        }
    }
}
