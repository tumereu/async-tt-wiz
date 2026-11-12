package org.asyncttwiz.android

import arcanum.engine.EngineProbes
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.state.PlayerId
import arcanum.engine.state.TargetRef
import org.asyncttwiz.protocol.game.GameDevice
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The engine API rework's exact work count (owner, 2026-11-12; spec `docs/specs/arcanum-engine-api.md`, "Performance
 * guard"): fixed scenarios on the device's engine path ([EngineGame], the engine thread inline), each with the exact
 * number of full layer runs ([EngineProbes.layersWorkedOut]), state copies ([EngineProbes.statesCopied]) and option searches
 * ([EngineProbes.optionsSearched]) it costs, and the bytes ([org.asyncttwiz.protocol.game.EngineJson]) of the events it stores
 * and of the checkpoint after it. Counted, never timed.
 *
 * The rules for these numbers:
 * - A count going down: update the number in the same commit.
 * - A count going up: the test fails. Find the cause; don't just update the number.
 * - Byte sizes may change only where the stored format changes (the rework's Phases 3–4); give the old and new sizes,
 *   and growth over 5% needs the owner's approval.
 */
class EngineWorkCountTest {
    @Before fun clean() = AutomatumBoards.reset()
    @After fun reset() {
        EngineProbes.layersWorkedOut = null; EngineProbes.statesCopied = null; EngineProbes.optionsSearched = null
        AutomatumBoards.reset()
    }

    private data class Work(val layers: Int, val copies: Int, val searches: Int, val eventBytes: Int, val checkpointBytes: Int)

    /** What [op] costs on [game]: the counts while it runs, the bytes of the events it made and of the state after it. */
    private fun counted(game: EngineGame, op: () -> Unit): Work {
        val known = game.read { d -> (d.session.events + d.session.pending).map { it.id }.toSet() }
        var layers = 0
        var copies = 0
        var searches = 0
        EngineProbes.layersWorkedOut = { layers++ }
        EngineProbes.statesCopied = { copies++ }
        EngineProbes.optionsSearched = { searches++ }
        try { op() } finally { EngineProbes.layersWorkedOut = null; EngineProbes.statesCopied = null; EngineProbes.optionsSearched = null }
        return game.read { d ->
            val made = (d.session.events + d.session.pending).filter { it.id !in known }
            assertTrue(made.isNotEmpty(), "no event was made")
            Work(layers, copies, searches, made.sumOf { game.codec.encodeEvent(it).encodeToByteArray().size },
                game.codec.encodeModel(d.model).encodeToByteArray().size)
        }
    }

    /** Fails naming each number that moved, from what to what. */
    private fun assertWork(expected: Work, actual: Work) {
        val moved = listOf(
            Triple("full layer runs", expected.layers, actual.layers),
            Triple("state copies (deepCopy)", expected.copies, actual.copies),
            Triple("option searches", expected.searches, actual.searches),
            Triple("stored events' bytes", expected.eventBytes, actual.eventBytes),
            Triple("checkpoint's bytes", expected.checkpointBytes, actual.checkpointBytes),
        ).filter { it.second != it.third }
        assertTrue(moved.isEmpty(), moved.joinToString("; ") { (name, from, to) ->
            "$name moved from $from to $to (${if (to < from) "down: update the number" else "up: find the cause"})"
        })
    }

    // End turn on EndTurnBenchmark's heavy board (about 44 permanents a side), from Alice's first main phase
    @Test fun endTurnOnTheHeavyBoard() {
        val game = BenchBoard("heavy", BenchBoard.HEAVY).game()
        val work = counted(game) { game.endTurn() }
        assertWork(Work(layers = 31, copies = 73, searches = 10, eventBytes = 18_020, checkpointBytes = 176_364), work)
    }

    // On the heavy board, Alice casts Storm Spark at Bob's Thornback Brute: she chooses its target; the device pays its
    // mana (the one payment there is) and makes the steps after it by itself, and the spell resolves
    @Test fun aCastWithATargetAndPayment() {
        val board = BenchBoard("heavy", BenchBoard.HEAVY, hand = listOf(BenchCard.SPARK to 1))
        val game = board.game()
        val a = PlayerId(board.a)
        val (spark, brute) = game.read { d ->
            val s = d.model.engine
            s.player(a).hand.first { s.obj(it)?.definition == BenchCard.SPARK.definition } to
                s.battlefield.first { s.obj(it)?.controller == PlayerId(board.b) && s.obj(it)?.definition == BenchCard.BRUTE.definition }
        }
        val work = counted(game) {
            game.act(Input.Cast(a, spark))
            while (true) {
                val answer = when (val d = game.read { it.model.engine.pending?.decision } ?: break) {
                    is Decision.Targets -> Input.Answer(d.id, Answer.Targets(listOf(TargetRef.Object(brute))))
                    is Decision.ManaPayment -> Input.Answer(d.id, Answer.ManaPayment(d.suggestion))
                    else -> error("unexpected $d")
                }
                game.act(answer)
            }
        }
        game.read { d ->
            val s = d.model.engine
            assertEquals(listOf("Storm Spark"), s.player(a).graveyard.map { s.obj(it)?.definition?.faces?.first()?.name }, "Storm Spark resolved")
            assertTrue(s.stack.isEmpty(), "the Stack is empty")
        }
        assertWork(Work(layers = 747, copies = 1_008, searches = 12, eventBytes = 27_046, checkpointBytes = 172_519), work)
    }

    // MassDeathTriggersScenariosTest's 30-creature board wipe: Alice with four Plains, a Ledger of Debts and 15 creatures,
    // Bob with 15; Alice casts the wipe and passes until its 30 death triggers have resolved
    @Test fun aBoardWipeOfThirtyCreatures() {
        val ledger = BenchCard("Ledger of Debts", "{1}{B}{B}", "Enchantment",
            "Whenever a creature dies, each opponent loses 1 life and you gain 1 life.")
        val wipe = BenchCard("Last Reckoning", "{2}{W}{W}", "Sorcery", "Destroy all creatures.")
        val plains = BenchCard("Plains", null, "Basic Land — Plains")
        val pup = BenchCard("Cinder Pup", "{R}", "Creature — Elemental Dog", "", "1", "1")
        val board = BenchBoard("wipe", listOf(plains to 4, ledger to 1, pup to 15), hand = listOf(wipe to 1), life = 40,
            other = listOf(pup to 15))
        val game = board.game()
        val a = PlayerId(board.a)
        val wipeId = game.read { d -> d.model.engine.player(a).hand.first { d.model.engine.obj(it)?.definition == wipe.definition } }
        val work = counted(game) {
            game.act(Input.Cast(a, wipeId))
            while (true) {
                val d = game.read { it.model.engine.pending?.decision } ?: break
                check(d is Decision.ManaPayment) { "unexpected $d" }
                game.act(Input.Answer(d.id, Answer.ManaPayment(d.suggestion)))
            }
            var passes = 0
            while (game.read { it.model.engine.stack.isNotEmpty() } && passes++ < 200) {
                check(game.shown?.status == GameDevice.Status.YourMove) { "status ${game.shown?.status}" }
                game.pass()
            }
        }
        game.read { d ->
            val s = d.model.engine
            assertTrue(s.stack.isEmpty(), "the Stack emptied")
            assertEquals(10, s.player(PlayerId(board.b)).life)
            assertEquals(70, s.player(a).life)
        }
        assertWork(Work(layers = 599, copies = 728, searches = 67, eventBytes = 362_917, checkpointBytes = 298_436), work)
    }
}
