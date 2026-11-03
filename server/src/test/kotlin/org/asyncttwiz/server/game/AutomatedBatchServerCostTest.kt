package org.asyncttwiz.server.game

import org.asyncttwiz.protocol.*
import org.asyncttwiz.server.ServiceRuntime
import org.asyncttwiz.server.database.FoundationMigrations
import org.asyncttwiz.server.database.FreshDatabase
import org.jooq.ExecuteContext
import org.jooq.impl.DefaultExecuteListener
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * A printed probe of what the server spends accepting a 1000-member Automatum batch (I-D14, SYS-010) on a busy board:
 * the shared reducer, storing every member and its activity, in one request. It prints timings and statement counts
 * and asserts only outcomes, never elapsed time (no timing tests; slow requests are logged by the server).
 */
class AutomatedBatchServerCostTest {
    @Test fun `a thousand member automated batch is accepted whole on a busy board`() {
        FreshDatabase().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            ServiceRuntime(db.settings).use { runtime ->
                runtime.start(); gameplayAwait(runtime::ready)
                val f = GameplayFixture(runtime, deckSize = 300)
                val owner = f.a.account.accountId
                fun state() = f.games.head(f.a, f.game.id).play!!
                // 250 permanents a player, put there 100 at a time
                for ((stream, player) in listOf(f.owner to owner, f.recipient to f.b.account.accountId)) {
                    val board = state().board
                    val library = board.containers.single { it.zone == PlayZone.LIBRARY && it.accountId == player && !it.temp }
                    val field = board.containers.single { it.zone == PlayZone.BATTLEFIELD && it.accountId == player }
                    library.cards.take(250).chunked(100).forEachIndexed { chunk, ids ->
                        val moves = ids.mapIndexed { i, id -> PlayAction.MoveCard(id, MoveSource(library.id),
                            MoveDestination(field.id, MoveInsertion.EMPTY, BoardPosition((chunk * 100 + i) % 25 * 120, (chunk * 100 + i) / 25 * 170))) }
                        val result = stream.execute(stream.command(PlayAction.NextStep).copy(action = GameAction.PlayBatch(moves)))
                        assertEquals("accepted", result.outcome, result.reason)
                    }
                }
                assertEquals("accepted", f.owner.execute(f.owner.command(PlayAction.Mtg(MtgAction.SetAutomatum(true)))).outcome)
                // A long game's history
                repeat(200) { assertEquals("accepted", f.owner.execute(f.owner.command(PlayAction.AdjustLife(owner, "-1"))).outcome) }
                var statements = 0
                val listener = object : DefaultExecuteListener() { override fun executeEnd(ctx: ExecuteContext) { statements++ } }
                val times = mutableListOf<Long>()
                repeat(5) { round ->
                    val board = state().board
                    val field = board.cards.filter { card -> board.containers.any { it.id == card.containerId && it.zone == PlayZone.BATTLEFIELD } }
                    val actions = mutableListOf<PlayAction>(PlayAction.Mtg(MtgAction.SetPriority(board.priority, MtgPriority(owner, round))))
                    actions += field.take(400).map { PlayAction.SetTapped(it.id, !it.tapped) }
                    actions += field.take(400).map { PlayAction.Mtg(MtgAction.SetLedger(MtgRef.Card(it.id, it.zoneChanges), it.ledger, """{"round":$round}""")) }
                    actions += List(199) { PlayAction.AdjustLife(owner, if (it % 2 == 0) "-1" else "+1") }
                    val tags = listOf(MtgTag(listOf("untapped"), board = MtgFacts.digest(board))) + List(actions.size - 1) { MtgTag(folded = true) }
                    assertEquals(PlayBatches.MAX_AUTOMATED, actions.size)
                    val command = f.owner.command(PlayAction.NextStep).copy(action = GameAction.PlayBatch(actions, tags))
                    assertTrue(f.owner.admit(command) is GameReceipt)
                    statements = 0
                    val start = System.nanoTime()
                    assertTrue(f.games.executeOne(listener))
                    times += System.nanoTime() - start
                    val result = f.owner.result(command)
                    assertEquals("accepted", result.outcome, result.reason)
                    assertEquals(PlayBatches.MAX_AUTOMATED, result.createdIds.size)
                    println("1000-member batch round $round on ${board.cards.size} cards: ${times.last() / 1_000_000.0} ms, $statements statements")
                }
                val warm = times.drop(2).sorted()
                println("1000-member automated batch on the server: warm median ${warm[warm.size / 2] / 1_000_000.0} ms")
            }
        }
    }
}
