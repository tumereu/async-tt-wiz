package org.asyncttwiz.server

import org.asyncttwiz.server.database.FoundationMigrations
import org.asyncttwiz.server.database.DatabaseSettings
import org.asyncttwiz.server.database.OwnedPostgres
import org.asyncttwiz.server.database.OwnershipFailure
import org.asyncttwiz.server.activity.ActivityDelivery
import org.asyncttwiz.server.game.GameHub
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class ServiceRuntimeTest {
    private fun awaitReady(runtime: ServiceRuntime) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
        while (!runtime.ready()) {
            assertTrue(System.nanoTime() < deadline, "Runtime failed to acquire ownership")
            Thread.sleep(50)
        }
    }

    @Test fun `single owner and game activity workers retain epoch when new work connections fail`() {
        OwnedPostgres().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            // A non-superuser connection limit rejects new work connections without touching
            // the already connected advisory-lock session. No network timing/fault proxy needed.
            db.connection().use { connection -> connection.createStatement().use {
                it.execute("CREATE ROLE runtime_writer LOGIN PASSWORD 'test-only-password'")
                it.execute("GRANT USAGE ON SCHEMA public TO runtime_writer")
                it.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO runtime_writer")
                it.execute("GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA public TO runtime_writer")
            } }
            fun limitConnections(limit: Int) = db.connection().use { connection -> connection.createStatement().use {
                it.execute("ALTER ROLE runtime_writer CONNECTION LIMIT $limit")
            } }
            fun ownership() = db.connection().use { connection -> connection.createStatement().use { statement ->
                statement.executeQuery("SELECT epoch, (SELECT pid FROM pg_locks WHERE locktype = 'advisory' AND classid = 1096045655::oid AND objid = 1::oid AND granted) FROM service_writer").use {
                    assertTrue(it.next())
                    it.getLong(1) to it.getInt(2)
                }
            } }
            val settings = DatabaseSettings(db.settings.url(), "runtime_writer", "test-only-password")
            ServiceRuntime(settings).use { runtime ->
                GameHub(runtime).use { games ->
                    ActivityDelivery(runtime).use { activity ->
                        val recoveries = AtomicInteger()
                        runtime.attachWorker({ games.validatePending(); recoveries.incrementAndGet() }, games::available)
                        runtime.start()
                        awaitReady(runtime)
                        val original = ownership()
                        assertEquals(1, original.first)
                        games.start()
                        activity.start()
                        fun exerciseWorkers(expectedEpoch: Long) {
                            assertEquals(expectedEpoch, games.dispatcher.submit("ownership-regression") {
                                assertFalse(games.games.executeOne())
                                assertTrue(games.games.publications().isEmpty())
                                activity.deliverDue()
                                runtime.transaction { connection -> connection.createStatement().use { statement ->
                                    statement.executeQuery("SELECT epoch FROM service_writer").use { assertTrue(it.next()); it.getLong(1) }
                                } }
                            }.get(8, TimeUnit.SECONDS))
                        }
                        // Six normal 500ms refresh periods and many 100ms game-worker periods.
                        // Advisory ownership has no lease/expiry interval to shorten.
                        val stableUntil = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
                        do {
                            exerciseWorkers(original.first)
                            assertTrue(runtime.ready())
                            assertEquals(original, ownership(), "Single owner replaced its lock session or epoch")
                            Thread.sleep(50)
                        } while (System.nanoTime() < stableUntil)
                        repeat(2) {
                            limitConnections(0)
                            try {
                                assertThrows(OwnershipFailure::class.java) { runtime.transaction { fail<Unit>("New connection admitted") } }
                                // Cross at least one recovery tick while game/activity workers also fail.
                                val blockedUntil = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(600)
                                do {
                                    assertTrue(runtime.ready(), "Work-connection rejection fenced the healthy owner")
                                    assertEquals(original, ownership())
                                    Thread.sleep(50)
                                } while (System.nanoTime() < blockedUntil)
                            } finally { limitConnections(-1) }
                            exerciseWorkers(original.first)
                        }
                        assertEquals(1, recoveries.get(), "Work errors forced needless runtime recovery")
                        // A real session loss must still recover; existing workers/dispatcher must
                        // fetch the new token rather than retaining their former epoch.
                        db.connection().use { connection -> connection.createStatement().use {
                            it.execute("SELECT pg_terminate_backend(${original.second})")
                        } }
                        awaitReady(runtime)
                        val replacement = ownership()
                        assertEquals(original.first + 1, replacement.first)
                        assertNotEquals(original.second, replacement.second)
                        exerciseWorkers(replacement.first)
                        assertEquals(2, recoveries.get())
                    }
                }
            }
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
                    assertTrue(first.ready(), "A callback rollback is not ownership loss")
                    // Force real lock-session loss, not an unrelated work-connection failure.
                    db.connection().use { connection -> connection.createStatement().use {
                        it.execute("SELECT pg_terminate_backend(pid) FROM pg_locks WHERE locktype = 'advisory' AND classid = 1096045655::oid AND objid = 1::oid")
                    } }
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
