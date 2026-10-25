package org.asyncttwiz.server.game

import org.asyncttwiz.protocol.*
import org.asyncttwiz.server.ServiceRuntime
import org.asyncttwiz.server.database.FoundationMigrations
import org.asyncttwiz.server.database.OwnedPostgres
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class GameplayPerformanceTest {
    @Test fun `360 legacy events are loaded once and subsequent commands stay below 50ms`() {
        OwnedPostgres().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            ServiceRuntime(db.settings).use { runtime ->
                runtime.start(); gameplayAwait(runtime::ready)
                val f = GameplayFixture(runtime, deckSize = 60)
                val actor = f.a.account.accountId
                repeat(360) { assertEquals("accepted", f.owner.execute(f.owner.command(PlayAction.AdjustLife(actor, "1"))).outcome) }
                db.connection().use { connection ->
                    connection.prepareStatement("""UPDATE gameplay_mutation SET payload =
                        jsonb_set((payload - 'bookkeeping') #- '{transition,delta}', '{schemaVersion}', '1'::jsonb)
                        WHERE play_id = ?""").use {
                        it.setObject(1, java.util.UUID.fromString(f.root.id)); assertEquals(360, it.executeUpdate())
                    }
                }
                // New service = empty memory cache, as after process ownership recovery.
                val games = GameService(runtime)
                fun execute(action: GameAction): Long {
                    val command = f.owner.command(PlayAction.AdjustLife(actor, "1")).copy(action = action)
                    assertTrue(f.owner.admit(command) is GameReceipt)
                    val start = System.nanoTime()
                    assertTrue(games.executeOne())
                    val elapsed = System.nanoTime() - start
                    assertEquals("accepted", f.owner.result(command).outcome)
                    return elapsed
                }
                val cold = execute(GameAction.Play(PlayAction.AdjustLife(actor, "1")))
                val samples = (0 until 12).map { n -> execute(when (n % 3) {
                    0 -> GameAction.Play(PlayAction.AdjustLife(actor, "1"))
                    1 -> GameAction.Undo
                    else -> GameAction.Redo
                }) }.sorted()
                val medianMs = samples[samples.size / 2] / 1_000_000.0
                println("360-node gameplay: coldMs=${cold / 1_000_000.0}, warmMedianMs=$medianMs, warmMaxMs=${samples.last() / 1_000_000.0}")
                assertTrue(medianMs < 50, "Warm per-command median must be <50ms, was $medianMs")
            }
        }
    }
}
