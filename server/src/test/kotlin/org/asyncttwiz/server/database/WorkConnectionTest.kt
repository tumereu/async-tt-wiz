package org.asyncttwiz.server.database

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.sql.SQLException

class WorkConnectionTest {
    private fun Connection.execute(sql: String) = createStatement().use { it.execute(sql) }
    private fun Connection.number(sql: String) = createStatement().use { statement ->
        statement.executeQuery(sql).use { rows -> assertTrue(rows.next()); rows.getLong(1) }
    }

    @Test fun `idle work backend is reused then replaced after termination without ownership fence`() {
        OwnedPostgres().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            WriterOwnership(db.settings).use { owner ->
                val token = owner.refresh().orElseThrow()
                val backend = owner.transaction(token) { it.number("SELECT pg_backend_pid()") }
                assertEquals(backend, owner.transaction(token) { it.number("SELECT pg_backend_pid()") })
                db.connection().use {
                    assertEquals(1, it.number("SELECT count(*) FROM pg_stat_activity WHERE pid = $backend AND state = 'idle' AND application_name = 'async-tt-wiz-work'"))
                    it.execute("SELECT pg_terminate_backend($backend)")
                }
                val replacement = owner.transaction(token) { it.number("SELECT pg_backend_pid()") }
                assertNotEquals(backend, replacement)
                assertSame(token, owner.token().orElseThrow())
                assertEquals(token.epoch(), db.connection().use { it.number("SELECT epoch FROM service_writer") })
            }
        }
    }

    @Test fun `lost work session before commit rolls back and retries DB work exactly once`() {
        OwnedPostgres().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            db.connection().use { it.execute("CREATE TABLE retry_probe (value integer)") }
            WriterOwnership(db.settings).use { owner ->
                val token = owner.refresh().orElseThrow()
                var calls = 0
                assertEquals(7, owner.transaction(token) { connection ->
                    calls++
                    connection.execute("INSERT INTO retry_probe VALUES (7)")
                    if (calls == 1) {
                        val backend = connection.number("SELECT pg_backend_pid()")
                        db.connection().use { it.execute("SELECT pg_terminate_backend($backend)") }
                    }
                    connection.number("SELECT 1") // Observe the lost session, wrapped as jOOQ would do.
                    7
                })
                assertEquals(2, calls)
                assertEquals(1, db.connection().use { it.number("SELECT count(*) FROM retry_probe") })
                assertSame(token, owner.token().orElseThrow())
                calls = 0
                assertThrows(OwnershipFailure::class.java) {
                    owner.transaction<Unit>(token) { connection ->
                        calls++
                        connection.execute("INSERT INTO retry_probe VALUES (99)")
                        throw IllegalStateException("SQL wrapper", SQLException("Connection reset", "08006"))
                    }
                }
                assertEquals(2, calls, "Retry must be bounded even for wrapped connection exceptions")
                assertEquals(7, db.connection().use { it.number("SELECT sum(value) FROM retry_probe") })
                assertSame(token, owner.token().orElseThrow())
            }
        }
    }

    @Test fun `connection class error during commit must never replay callback`() {
        OwnedPostgres().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            db.connection().use {
                it.execute("CREATE TABLE commit_probe (value integer)")
                it.execute("CREATE FUNCTION fail_commit() RETURNS trigger LANGUAGE plpgsql AS 'BEGIN RAISE EXCEPTION USING ERRCODE = ''08006'', MESSAGE = ''test commit failure''; END;'")
                it.execute("CREATE CONSTRAINT TRIGGER fail_commit AFTER INSERT ON commit_probe DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION fail_commit()")
            }
            WriterOwnership(db.settings).use { owner ->
                val token = owner.refresh().orElseThrow()
                var calls = 0
                assertThrows(OwnershipFailure::class.java) {
                    owner.transaction(token) { calls++; it.execute("INSERT INTO commit_probe VALUES (1)") }
                }
                assertEquals(1, calls)
                assertEquals(0, db.connection().use { it.number("SELECT count(*) FROM commit_probe") })
                assertSame(token, owner.token().orElseThrow())
            }
        }
    }

    @Test fun `availability evidence includes state and safe transport causes but never SQL or credentials`() {
        val secret = "jdbc:postgresql://private user password bind-value SELECT * FROM secrets"
        val failure = IllegalStateException(secret, SQLException("The connection attempt failed.", "08001",
            java.net.SocketException("Connection reset")))
        val detail = AvailabilityDiagnostic.detail(failure)
        assertTrue(detail.contains("sqlState=08001"))
        assertTrue(detail.contains("class=java.net.SocketException"))
        assertTrue(detail.contains("message=\"Connection reset\""))
        assertFalse(detail.contains(secret))
        assertFalse(AvailabilityDiagnostic.detail(SQLException(secret, "23505")).contains(secret))
    }
}
