package org.asyncttwiz.server.game

import org.asyncttwiz.protocol.*
import org.asyncttwiz.server.ServiceRuntime
import org.asyncttwiz.server.database.FoundationMigrations
import org.asyncttwiz.server.database.OwnedPostgres
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.jooq.ExecuteContext
import org.jooq.impl.DefaultExecuteListener

class GameplayPerformanceTest {
    @Test fun `360 incremental events are loaded once and subsequent commands stay below 50ms`() {
        OwnedPostgres().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            ServiceRuntime(db.settings).use { runtime ->
                runtime.start(); gameplayAwait(runtime::ready)
                val f = GameplayFixture(runtime, deckSize = 60)
                val actor = f.a.account.accountId
                repeat(360) { assertEquals("accepted", f.owner.execute(f.owner.command(PlayAction.AdjustLife(actor, "1"))).outcome) }
                // New service = empty memory cache, as after process ownership recovery.
                val games = GameService(runtime)
                var statements = 0
                var checkpoints = 0
                var controlWrites = 0
                var endpointWrites = 0
                val listener = object : DefaultExecuteListener() {
                    override fun executeEnd(ctx: ExecuteContext) {
                        statements++
                        val query = ctx.sql()!!
                        if (query.startsWith("select") && query.contains("\"game_play\".\"payload\"")) checkpoints++
                        if (query.startsWith("insert into \"public\".\"gameplay_tree_control\"")) controlWrites++
                        if (query.startsWith("insert into \"public\".\"gameplay_endpoint_record\"")) endpointWrites++
                    }
                }
                data class Sample(val action: String, val elapsed: Long, val statements: Int)
                fun execute(action: GameAction): Sample {
                    val command = f.owner.command(PlayAction.AdjustLife(actor, "1")).copy(action = action)
                    assertTrue(f.owner.admit(command) is GameReceipt)
                    statements = 0; checkpoints = 0; controlWrites = 0; endpointWrites = 0
                    val start = System.nanoTime()
                    assertTrue(games.executeOne(listener))
                    val elapsed = System.nanoTime() - start
                    assertEquals("accepted", f.owner.result(command).outcome)
                    assertEquals(1, checkpoints, "Read and verify the bounded board exactly once")
                    assertEquals(1, controlWrites, "Batch changed controls")
                    assertEquals(1, endpointWrites, "Batch endpoint records, including edge degrees")
                    return Sample(action.latencyType(), elapsed, statements)
                }
                val cold = execute(GameAction.Play(PlayAction.AdjustLife(actor, "1")))
                val samples = (0 until 12).map { n -> execute(when (n % 3) {
                    0 -> GameAction.Play(PlayAction.AdjustLife(actor, "1"))
                    1 -> GameAction.Undo
                    else -> GameAction.Redo
                }) }
                val elapsed = samples.map { it.elapsed }.sorted()
                val medianMs = elapsed[elapsed.size / 2] / 1_000_000.0
                println("360-node gameplay: coldMs=${cold.elapsed / 1_000_000.0}, coldStatements=${cold.statements}, warmMedianMs=$medianMs, warmMaxMs=${elapsed.last() / 1_000_000.0}")
                samples.groupBy { it.action }.forEach { (action, values) ->
                    val times = values.map { it.elapsed }.sorted()
                    println("$action: medianMs=${times[times.size / 2] / 1_000_000.0}, statements=${values.map { it.statements }}")
                }
                assertTrue(cold.statements <= 30, "Cold command: ${cold.statements} statements")
                assertTrue(samples.all { it.statements <= 23 }, "Warm command SQL must stay bounded: $samples")
                assertTrue(medianMs < 50, "Warm per-command median must be <50ms, was $medianMs")
            }
        }
    }
}
