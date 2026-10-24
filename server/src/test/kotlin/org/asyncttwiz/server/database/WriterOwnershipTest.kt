package org.asyncttwiz.server.database

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.sql.SQLException
import java.time.Duration
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class WriterOwnershipTest {
    private fun Connection.execute(sql: String) = createStatement().use { it.execute(sql) }
    private fun Connection.number(sql: String): Long = createStatement().use { statement ->
        statement.executeQuery(sql).use { rows -> assertTrue(rows.next()); rows.getLong(1) }
    }
    private fun epoch(db: OwnedPostgres) = db.connection().use { it.number("SELECT epoch FROM service_writer") }
    private fun migrated(): OwnedPostgres =
        OwnedPostgres().start().also { FoundationMigrations.migrate(it.settings) }
    private fun acquired(owner: WriterOwnership) = owner.refresh().orElseThrow()
    private fun await(latch: CountDownLatch) { assertTrue(latch.await(8, TimeUnit.SECONDS), "Synchronization timed out") }

    private fun eventually(label: String, assertion: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8)
        do {
            if (assertion()) return
            Thread.sleep(20) // Poll a measured server condition, not a substitute for synchronization.
        } while (System.nanoTime() < deadline)
        fail<Unit>("Timed out waiting for $label")
    }

    private fun terminateLock(db: OwnedPostgres) {
        db.connection().use {
            assertEquals(1, it.number("SELECT count(*) FROM pg_locks WHERE locktype = 'advisory' AND classid = 1096045655::oid AND objid = 1::oid"))
            assertTrue(it.execute("SELECT pg_terminate_backend(pid) FROM pg_locks WHERE locktype = 'advisory' AND classid = 1096045655::oid AND objid = 1::oid"))
        }
    }

    @Test fun `non migrating validation accepts retained supported history and reloads singleton`() {
        migrated().use { db ->
            db.connection().use { it.execute("UPDATE service_writer SET epoch = 41") }
            val checksum = FoundationMigrations.flyway(db.settings).info().applied().single { it.version.toString() == "1" }.checksum
            WriterOwnership(db.settings).use { owner ->
                val token = acquired(owner)
                assertEquals(42, token.epoch())
                assertSame(token, owner.token().orElseThrow())
                assertSame(token, acquired(owner))
                assertEquals(42, epoch(db))
            }
            assertEquals(checksum, FoundationMigrations.flyway(db.settings).info().applied().single { it.version.toString() == "1" }.checksum)
            assertEquals(MigrationTestSupport.supportedVersions(db.settings),
                FoundationMigrations.flyway(db.settings).info().applied().map { it.version.toString() })
            println("PASS supported retained schema validated without migration; epoch 41 -> 42 and foundation reload")
        }
    }

    @Test fun `empty and unversioned schemas remain unchanged and unavailable`() {
        OwnedPostgres().use { db ->
            db.start()
            WriterOwnership(db.settings).use { owner ->
                assertEquals(OwnershipFailure.Reason.UNSUPPORTED_SCHEMA,
                    assertThrows(OwnershipFailure::class.java) { owner.refresh() }.reason())
                assertTrue(owner.token().isEmpty)
                db.connection().use { assertEquals(0, it.number("SELECT count(*) FROM pg_tables WHERE schemaname = 'public'")) }
                db.connection().use { it.execute("CREATE TABLE retained_probe AS SELECT 42 AS value") }
                assertThrows(OwnershipFailure::class.java) { owner.refresh() }
                db.connection().use {
                    assertEquals(42, it.number("SELECT value FROM retained_probe"))
                    assertEquals(1, it.number("SELECT count(*) FROM pg_tables WHERE schemaname = 'public'"))
                }
                println("PASS empty/unversioned schema refused without history creation, migration, baseline or repair")
            }
        }
    }

    @Test fun `checksum future history and missing singleton are rejected without repair`() {
        migrated().use { db ->
            val expectedVersions = MigrationTestSupport.supportedVersions(db.settings)
            val futureVersion = MigrationTestSupport.futureVersion(db.settings)
            val original = FoundationMigrations.flyway(db.settings).info().applied().single { it.version.toString() == "1" }.checksum!!
            WriterOwnership(db.settings).use { owner ->
                db.connection().use { it.execute("UPDATE flyway_schema_history SET checksum = checksum + 1 WHERE version = '1'") }
                assertThrows(OwnershipFailure::class.java) { owner.refresh() }
                assertTrue(owner.token().isEmpty)
                assertEquals(0, epoch(db))
                assertEquals(original + 1, FoundationMigrations.flyway(db.settings).info().applied().single { it.version.toString() == "1" }.checksum)
                db.connection().use {
                    // Test-only restoration of deliberately damaged metadata; runtime has no repair path.
                    it.execute("UPDATE flyway_schema_history SET checksum = $original WHERE version = '1'")
                    it.execute("""INSERT INTO flyway_schema_history
                        SELECT max(installed_rank) + 1, '$futureVersion', 'future', 'SQL', 'V${futureVersion}__future.sql',
                            1, current_user, now(), 1, true FROM flyway_schema_history""")
                }
                assertThrows(OwnershipFailure::class.java) { owner.refresh() }
                assertEquals(expectedVersions + futureVersion,
                    FoundationMigrations.flyway(db.settings).info().applied().map { it.version.toString() })
                assertEquals(0, epoch(db))
                db.connection().use { it.execute("DELETE FROM flyway_schema_history WHERE version = '$futureVersion'; DELETE FROM service_writer") }
                assertThrows(OwnershipFailure::class.java) { owner.refresh() }
                assertTrue(owner.token().isEmpty)
                assertEquals(0, db.connection().use { it.number("SELECT count(*) FROM service_writer") })
                println("PASS checksum/future/missing-foundation refusal; no metadata repair or singleton recreation")
            }
        }
    }

    @Test fun `single writer takeover fences old token even before old manager observes lock loss`() {
        migrated().use { db ->
            db.connection().use { it.execute("CREATE TABLE mutation_probe (id integer PRIMARY KEY, value integer); INSERT INTO mutation_probe VALUES (1, 0)") }
            WriterOwnership(db.settings).use { old ->
                WriterOwnership(db.settings).use { replacement ->
                    val stale = acquired(old)
                    assertTrue(replacement.refresh().isEmpty)
                    assertEquals(1, epoch(db))
                    terminateLock(db)
                    val next = acquired(replacement)
                    assertEquals(stale.epoch() + 1, next.epoch())
                    // Direct lower-layer entry models a transaction delayed after its initial
                    // ownership probe: the database epoch fence, not only local invalidation,
                    // must reject it after takeover.
                    db.connection().use { delayed ->
                        delayed.autoCommit = false
                        assertEquals(OwnershipFailure.Reason.NOT_OWNER, assertThrows(OwnershipFailure::class.java) {
                            WriterOwnership.lockEpoch(delayed, stale.epoch())
                        }.reason())
                        delayed.rollback()
                    }
                    var entered = false
                    assertEquals(OwnershipFailure.Reason.NOT_OWNER, assertThrows(OwnershipFailure::class.java) {
                        old.transaction(stale) { entered = true; it.execute("UPDATE mutation_probe SET value = 99") }
                    }.reason())
                    assertFalse(entered)
                    assertTrue(old.token().isEmpty)
                    replacement.transaction(next) { it.execute("UPDATE mutation_probe SET value = value + 1") }
                    assertEquals(1, db.connection().use { it.number("SELECT value FROM mutation_probe") })
                    // Capabilities are bound to the instance as well as to the persisted epoch.
                    assertThrows(OwnershipFailure::class.java) { old.transaction(next) { fail<Unit>("Foreign token admitted") } }
                    println("PASS two independent managers, one advisory writer, epoch takeover, stale/foreign tokens fenced before callback")
                }
            }
        }
    }

    private fun replacementWaits(rollback: Boolean) {
        migrated().use { db ->
            db.connection().use { it.execute("CREATE TABLE mutation_probe (id integer PRIMARY KEY, value integer); INSERT INTO mutation_probe VALUES (1, 0)") }
            WriterOwnership(db.settings).use { old ->
                WriterOwnership(db.settings).use { replacement ->
                    Executors.newFixedThreadPool(2).use { pool ->
                        val token = acquired(old)
                        val inside = CountDownLatch(1)
                        val release = CountDownLatch(1)
                        val oldTransaction = pool.submit(Callable {
                            if (rollback) {
                                assertThrows(OwnershipFailure::class.java) {
                                    old.transaction(token) {
                                        it.execute("UPDATE mutation_probe SET value = 1")
                                        inside.countDown(); await(release)
                                        throw SQLException("test-owned rollback")
                                    }
                                }
                            } else {
                                old.transaction(token) {
                                    it.execute("UPDATE mutation_probe SET value = 1")
                                    inside.countDown(); await(release)
                                }
                            }
                        })
                        try {
                            await(inside)
                            terminateLock(db)
                            val acquisition = pool.submit(Callable { acquired(replacement) })
                            eventually("replacement exclusive epoch-row lock wait") {
                                db.connection().use { it.number("SELECT count(*) FROM pg_stat_activity WHERE application_name = 'async-tt-wiz-ownership' AND wait_event_type = 'Lock' AND query LIKE '%FOR UPDATE%'") > 0 }
                            }
                            assertFalse(acquisition.isDone)
                            assertEquals(token.epoch(), epoch(db))
                            release.countDown()
                            oldTransaction.get(8, TimeUnit.SECONDS)
                            val next = acquisition.get(8, TimeUnit.SECONDS)
                            assertEquals(token.epoch() + 1, next.epoch())
                            assertEquals(if (rollback) 0 else 1, db.connection().use { it.number("SELECT value FROM mutation_probe") }.toInt())
                            assertThrows(OwnershipFailure::class.java) { old.transaction(token) { fail<Unit>("Delayed old token admitted") } }
                            println("PASS observed replacement waiting in pg_stat_activity; old ${if (rollback) "rollback" else "commit"} precedes epoch ${next.epoch()}; delayed old token fenced")
                        } finally { release.countDown() }
                    }
                }
            }
        }
    }

    @Test fun `replacement epoch waits for old shared lock to commit`() = replacementWaits(false)
    @Test fun `replacement epoch waits for old shared lock to roll back`() = replacementWaits(true)

    @Test fun `current token shared locks allow simultaneous independent transactions`() {
        migrated().use { db ->
            db.connection().use { it.execute("CREATE TABLE mutation_probe (id integer PRIMARY KEY, value integer); INSERT INTO mutation_probe VALUES (1, 0), (2, 0)") }
            WriterOwnership(db.settings).use { owner ->
                val token = acquired(owner)
                Executors.newFixedThreadPool(2).use { pool ->
                    val bothInside = CountDownLatch(2)
                    val release = CountDownLatch(1)
                    val futures = (1..2).map { id -> pool.submit(Callable {
                        owner.transaction(token) {
                            it.execute("UPDATE mutation_probe SET value = 1 WHERE id = $id")
                            bothInside.countDown(); await(release)
                        }
                    }) }
                    try {
                        await(bothInside)
                        assertTrue(futures.none { it.isDone })
                        assertEquals(2, db.connection().use { it.number("SELECT count(*) FROM pg_locks WHERE relation = 'service_writer'::regclass AND mode = 'RowShareLock' AND granted") })
                    } finally { release.countDown() }
                    futures.forEach { it.get(8, TimeUnit.SECONDS) }
                    assertEquals(2, db.connection().use { it.number("SELECT sum(value) FROM mutation_probe") })
                }
                println("PASS both callbacks simultaneously hold shared epoch locks and commit independent updates")
            }
        }
    }

    @Test fun `delayed old transaction failure cannot invalidate reacquired ownership`() {
        migrated().use { db ->
            WriterOwnership(db.settings).use { owner ->
                Executors.newSingleThreadExecutor().use { pool ->
                    val stale = acquired(owner)
                    val entered = CountDownLatch(1)
                    val finish = CountDownLatch(1)
                    val backend = java.util.concurrent.atomic.AtomicLong()
                    val delayed = pool.submit(Callable {
                        assertThrows(OwnershipFailure::class.java) {
                            owner.transaction(stale) { connection ->
                                backend.set(connection.number("SELECT pg_backend_pid()"))
                                entered.countDown(); await(finish)
                                throw SQLException("delayed old work failure")
                            }
                        }
                    })
                    try {
                        await(entered)
                        terminateLock(db)
                        // Drop the old transaction's shared row lock so this same manager can
                        // recover before the failed callback unwinds on the other thread.
                        db.connection().use { it.execute("SELECT pg_terminate_backend(${backend.get()})") }
                        val next = acquired(owner)
                        assertEquals(stale.epoch() + 1, next.epoch())
                        finish.countDown()
                        delayed.get(8, TimeUnit.SECONDS)
                        assertSame(next, owner.token().orElseThrow())
                        assertThrows(OwnershipFailure::class.java) { owner.transaction(stale) { fail<Unit>("Old epoch admitted") } }
                        assertEquals(1, owner.transaction(next) { it.number("SELECT 1") })
                        assertSame(next, acquired(owner))
                    } finally { finish.countDown() }
                }
            }
        }
    }

    @Test fun `acquisition wait is bounded and failed acquisition releases advisory connection`() {
        migrated().use { db ->
            db.connection().use { blocker ->
                blocker.autoCommit = false
                blocker.execute("SELECT epoch FROM service_writer FOR SHARE")
                WriterOwnership(db.settings).use { owner ->
                    assertTimeoutPreemptively(Duration.ofSeconds(8)) {
                        assertThrows(OwnershipFailure::class.java) { owner.refresh() }
                    }
                    assertTrue(owner.token().isEmpty)
                    assertEquals(0, epoch(db))
                    assertEquals(0, db.connection().use { it.number("SELECT count(*) FROM pg_locks WHERE locktype = 'advisory'") })
                    blocker.rollback()
                    assertEquals(1, acquired(owner).epoch())
                    println("PASS epoch lock timeout bounded; failed acquisition leaves no token/advisory lock; subsequent recovery succeeds")
                }
            }
        }
    }

    @Test fun `database restart invalidates ownership and requires schema validation reload and new epoch`() {
        migrated().use { db ->
            WriterOwnership(db.settings).use { owner ->
                val stale = acquired(owner)
                val id = db.container.containerId
                println("Restart diagnostic: original mapped port=${db.container.getMappedPort(5432)}")
                db.container.dockerClient.stopContainerCmd(id).withTimeout(0).exec()
                try {
                    assertTimeoutPreemptively(Duration.ofSeconds(8)) { assertTrue(owner.token().isEmpty) }
                    assertThrows(OwnershipFailure::class.java) { owner.refresh() }
                    assertThrows(OwnershipFailure::class.java) { owner.transaction(stale) { fail<Unit>("Offline transaction admitted") } }
                } finally { db.container.dockerClient.startContainerCmd(id).exec() }
                val publication = db.container.dockerClient.inspectContainerCmd(id).exec().networkSettings.ports.bindings.values
                    .flatMap { it.toList() }.map { it.hostPortSpec }
                println("Restart diagnostic: actual Docker publication=$publication")
                assertEquals(setOf(db.container.getMappedPort(5432).toString()), publication.toSet())
                eventually("PostgreSQL restart") {
                    try { db.connection().use { it.number("SELECT 1") == 1L } } catch (_: SQLException) { false }
                }
                db.connection().use { it.execute("UPDATE flyway_schema_history SET checksum = checksum + 1 WHERE version = '1'") }
                assertThrows(OwnershipFailure::class.java) { owner.refresh() }
                assertTrue(owner.token().isEmpty)
                assertEquals(stale.epoch(), epoch(db))
                db.connection().use { it.execute("UPDATE flyway_schema_history SET checksum = checksum - 1 WHERE version = '1'") }
                assertEquals(stale.epoch() + 1, acquired(owner).epoch())
                println("PASS actual database stop/start, immediate observed invalidation, recovery schema revalidation and new persisted epoch")
            }
        }
    }

    @Test fun `unresponsive database probe times out and recovers without stale ownership`() {
        migrated().use { db ->
            WriterOwnership(db.settings).use { owner ->
                val stale = acquired(owner)
                val id = db.container.containerId
                db.container.dockerClient.pauseContainerCmd(id).exec()
                try {
                    assertTimeoutPreemptively(Duration.ofSeconds(8)) { assertTrue(owner.token().isEmpty) }
                } finally { db.container.dockerClient.unpauseContainerCmd(id).exec() }
                assertThrows(OwnershipFailure::class.java) { owner.transaction(stale) { fail<Unit>("Stale token admitted") } }
                eventually("ownership after unpause") { owner.refresh().isPresent }
                assertEquals(stale.epoch() + 1, owner.token().orElseThrow().epoch())
                println("PASS blackholed DB probe bounded by socket timeout; stale ownership invalidated and reacquired")
            }
        }
    }

    @Test fun `unobserved database restart cannot admit under an unchanged old epoch`() {
        migrated().use { db ->
            WriterOwnership(db.settings).use { owner ->
                val stale = acquired(owner)
                val id = db.container.containerId
                db.container.dockerClient.stopContainerCmd(id).withTimeout(0).exec()
                db.container.dockerClient.startContainerCmd(id).exec()
                eventually("database after unobserved restart") {
                    try { db.connection().use { it.number("SELECT 1") == 1L } } catch (_: SQLException) { false }
                }
                assertEquals(stale.epoch(), epoch(db))
                var entered = false
                assertThrows(OwnershipFailure::class.java) { owner.transaction(stale) { entered = true } }
                assertFalse(entered)
                assertTrue(owner.token().isEmpty)
                assertEquals(stale.epoch() + 1, acquired(owner).epoch())
                println("PASS transaction checks dedicated session, not just same persisted epoch, after unobserved DB restart")
            }
        }
    }

    @Test fun `rollback safe errors release repeated close and restart keep secrets out of diagnostics`() {
        migrated().use { db ->
            db.connection().use { it.execute("CREATE TABLE mutation_probe (value integer); INSERT INTO mutation_probe VALUES (0)") }
            val owner = WriterOwnership(db.settings)
            val token = acquired(owner)
            val failure = assertThrows(OwnershipFailure::class.java) {
                owner.transaction(token) {
                    it.execute("UPDATE mutation_probe SET value = 99")
                    throw SQLException("${db.settings.url()} ${db.settings.user()} ${db.settings.password()}")
                }
            }
            val bad = WriterOwnership(DatabaseSettings(db.settings.url(), "sentinel-user", "sentinel-password"))
            bad.use {
                val rejected = assertThrows(OwnershipFailure::class.java) { bad.refresh() }
                for (error in listOf(failure, rejected)) {
                    val trace = error.stackTraceToString()
                    for (secret in listOf(db.settings.url(), db.settings.user(), db.settings.password(), "sentinel-user", "sentinel-password")) {
                        assertFalse(trace.contains(secret))
                    }
                    assertNull(error.cause)
                    assertTrue(error.suppressed.isEmpty())
                }
                assertEquals("WriterOwnership[redacted]", owner.toString())
                assertEquals("OwnershipDatabase[redacted]", OwnershipDatabase(db.settings).toString())
            }
            assertEquals(0, db.connection().use { it.number("SELECT value FROM mutation_probe") })
            assertSame(token, owner.token().orElseThrow(), "Rolled-back work must not release the healthy lock session")
            val recovered = acquired(owner)
            assertSame(token, recovered)
            assertTimeoutPreemptively(Duration.ofSeconds(8)) { owner.close(); owner.close() }
            assertTrue(owner.token().isEmpty)
            assertEquals(OwnershipFailure.Reason.CLOSED, assertThrows(OwnershipFailure::class.java) { owner.refresh() }.reason())
            WriterOwnership(db.settings).use { restarted -> assertEquals(recovered.epoch() + 1, acquired(restarted).epoch()) }
            assertEquals(0, db.connection().use { it.number("SELECT count(*) FROM pg_locks WHERE locktype = 'advisory'") })
            println("PASS rollback without self-fencing, safe cause/suppression-free errors, failed credentials, idempotent close/restart and release")
        }
    }
}
