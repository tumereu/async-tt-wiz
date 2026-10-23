package org.asyncttwiz.server.game

import java.lang.management.ManagementFactory
import kotlin.math.ceil
import kotlinx.serialization.json.*
import org.asyncttwiz.protocol.*
import org.asyncttwiz.server.ServiceRuntime
import org.asyncttwiz.server.database.FoundationMigrations
import org.asyncttwiz.server.database.OwnedPostgres
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/** Real PostgreSQL + real GameService ingress/inbox/transaction/outbox, NOT a reducer benchmark.
 * Executor supplies isolated 2-CPU/2-GiB app cgroup, separate 2-GiB PG allocation and SSD, and
 * retains actual cgroup/device/image identities. This service-boundary clock excludes socket/HTTP,
 * authentication, command creation, result lookup and fixture setup; it is NOT remote-visible RTT.
 */
class RuntimeWorkloadTest {
    @Test fun warmSimpleCommandIngressThroughCommit() {
        val source = System.getenv("ATW_RUNTIME_SOURCE_COMMIT").orEmpty()
        require(Regex("[0-9a-f]{40}").matches(source)) { "Executor must pin ATW_RUNTIME_SOURCE_COMMIT" }
        require(System.getenv("ATW_RUNTIME_PROFILE") == "2cpu-2g-app-2g-pg-ssd") {
            "Executor must supply and retain the actual external runtime resource profile"
        }
        val samples = setting("ATW_RUNTIME_SAMPLES", 100, 20..500)
        val warmup = setting("ATW_RUNTIME_WARMUP", 20, 10..100)
        OwnedPostgres().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            ServiceRuntime(db.settings).use { runtime ->
                runtime.start(); gameplayAwait(runtime::ready)
                val fixture = GameplayFixture(runtime)
                val actor = fixture.a.account.accountId
                val initial = fixture.games.head(fixture.a, fixture.game.id).play!!
                val ingress = mutableListOf<Long>()
                val commit = mutableListOf<Long>()
                val total = mutableListOf<Long>()
                val heap = mutableListOf<Long>()
                repeat(warmup + samples) { index ->
                    // Sequential unloaded commands, no batching, setup and warmup never enter percentiles.
                    val input = fixture.owner.command(PlayAction.AdjustLife(actor, "1"))
                    val start = System.nanoTime()
                    val receipt = fixture.owner.admit(input)
                    val admitted = System.nanoTime()
                    val executed = fixture.games.executeOne()
                    val committed = System.nanoTime() // executeOne returned after durable commit, before result fetch
                    assertTrue(receipt is GameReceipt)
                    assertTrue(executed)
                    val result = fixture.owner.result(input)
                    assertEquals("accepted", result.outcome)
                    assertNotNull(result.commitRevision)
                    val state = fixture.games.head(fixture.a, fixture.game.id).play!!
                    assertEquals((21 + index).toString(), state.board.players.single { it.accountId == actor }.life)
                    assertEquals(initial.board.cards, state.board.cards)
                    if (index >= warmup) {
                        ingress += admitted - start
                        commit += committed - admitted
                        total += committed - start
                        heap += ManagementFactory.getMemoryMXBean().heapMemoryUsage.used
                    }
                }
                for ((metric, values) in listOf("admit-transaction" to ingress,
                    "execute-through-durable-commit" to commit, "service-ingress-through-commit" to total)) {
                    println(buildJsonObject {
                        put("workload", "I33-v1"); put("layer", "service-boundary-real-postgresql")
                        put("metric", metric); put("sourceCommit", source); put("samples", values.size)
                        put("warmupExcluded", warmup); put("profile", "externally-supplied-2cpu-2g-app-2g-pg-ssd")
                        put("jvm", System.getProperty("java.version")); put("arch", System.getProperty("os.arch"))
                        put("availableProcessors", Runtime.getRuntime().availableProcessors())
                        put("maxHeapBytes", Runtime.getRuntime().maxMemory())
                        put("postgresImage", db.container.dockerImageName)
                        put("p95Nanos", percentile(values, .95)); put("p99Nanos", percentile(values, .99))
                        putJsonArray("rawNanos") { values.forEach { add(JsonPrimitive(it)) } }
                        putJsonArray("heapUsedBytesAfterEachSample") { heap.forEach { add(JsonPrimitive(it)) } }
                    })
                }
                assertTrue(percentile(total, .95) <= 100_000_000L, "Simple command service ingress/commit p95 exceeds 100ms")
                assertFalse(fixture.games.executeOne())
            }
        }
    }

    private fun setting(name: String, default: Int, range: IntRange): Int =
        (System.getenv(name)?.toInt() ?: default).also { require(it in range) { "Invalid workload sample count" } }

    private fun percentile(values: List<Long>, p: Double) = values.sorted()[ceil(values.size * p).toInt() - 1]
}
