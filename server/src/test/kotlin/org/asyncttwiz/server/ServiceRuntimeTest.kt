package org.asyncttwiz.server

import org.asyncttwiz.server.database.FoundationMigrations
import org.asyncttwiz.server.database.OwnedPostgres
import org.asyncttwiz.server.database.OwnershipFailure
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ServiceRuntimeTest {
    private fun awaitReady(runtime: ServiceRuntime) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
        while (!runtime.ready()) {
            assertTrue(System.nanoTime() < deadline, "Runtime failed to acquire ownership")
            Thread.sleep(50)
        }
    }

    @Test fun `admission rejects nonowners and loss then recovers through the same fenced transaction boundary`() {
        OwnedPostgres().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            ServiceRuntime(db.settings).use { first ->
                assertFalse(first.ready())
                assertThrows(OwnershipFailure::class.java) { first.transaction { "must not run" } }
                first.start()
                awaitReady(first)
                ServiceRuntime(db.settings).use { second ->
                    second.start()
                    assertFalse(second.ready())
                    assertThrows(OwnershipFailure::class.java) { second.transaction { "must not run" } }
                    first.transaction { connection ->
                        connection.createStatement().use { it.execute("CREATE TABLE test_runtime_probe (value integer)") }
                    }
                    assertThrows(OwnershipFailure::class.java) {
                        first.transaction<Unit> { connection ->
                            connection.createStatement().use { it.execute("INSERT INTO test_runtime_probe VALUES (1)") }
                            throw java.sql.SQLException("synthetic private callback input")
                        }
                    }
                    // Whichever runtime wins recovery, only that owner can write; failed callback rolled back.
                    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
                    while (!first.ready() && !second.ready()) {
                        assertTrue(System.nanoTime() < deadline)
                        Thread.sleep(50)
                    }
                    val owner = if (first.ready()) first else second
                    val other = if (owner === first) second else first
                    assertFalse(other.ready())
                    assertThrows(OwnershipFailure::class.java) { other.transaction { "must not run" } }
                    assertEquals(0, owner.transaction { connection ->
                        connection.createStatement().use { statement -> statement.executeQuery("SELECT count(*) FROM test_runtime_probe").use {
                            assertTrue(it.next()); it.getInt(1)
                        } }
                    })
                    println("PASS service admission not-owner/rollback/reacquisition through actual PostgreSQL fence")
                }
            }
        }
    }

    @Test fun `shutdown immediately stops admission and drains an already admitted commit before release`() {
        OwnedPostgres().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            val executor = Executors.newFixedThreadPool(2)
            val entered = CountDownLatch(1)
            val finish = CountDownLatch(1)
            try {
                ServiceRuntime(db.settings).use { runtime ->
                    runtime.start()
                    awaitReady(runtime)
                    val transaction = executor.submit<Int> { runtime.transaction { connection ->
                        connection.createStatement().use { it.execute("CREATE TABLE test_shutdown_commit (value integer)") }
                        entered.countDown()
                        assertTrue(finish.await(5, TimeUnit.SECONDS))
                        connection.createStatement().use { it.execute("INSERT INTO test_shutdown_commit VALUES (7)") }
                        7
                    } }
                    assertTrue(entered.await(5, TimeUnit.SECONDS))
                    val closed = executor.submit { runtime.close() }
                    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
                    while (runtime.ready()) { assertTrue(System.nanoTime() < deadline); Thread.sleep(10) }
                    assertThrows(OwnershipFailure::class.java) { runtime.transaction { "must not run" } }
                    assertFalse(closed.isDone, "Shutdown released ownership before an admitted transaction drained")
                    finish.countDown()
                    assertEquals(7, transaction.get(5, TimeUnit.SECONDS))
                    closed.get(5, TimeUnit.SECONDS)
                    assertFalse(runtime.ready())
                    assertThrows(OwnershipFailure::class.java) { runtime.transaction { "must not run" } }
                    db.connection().use { connection -> connection.createStatement().use { statement ->
                        statement.executeQuery("SELECT value FROM test_shutdown_commit").use { assertTrue(it.next()); assertEquals(7, it.getInt(1)) }
                    } }
                    println("PASS stop-admission precedes bounded drain; admitted commit retained; closed runtime refuses new writes")
                }
            } finally {
                finish.countDown()
                executor.shutdownNow()
                assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS))
            }
        }
    }
}
