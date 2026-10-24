package org.asyncttwiz.server.accounts

import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.asyncttwiz.server.ServiceRuntime
import org.asyncttwiz.server.database.FoundationMigrations
import org.asyncttwiz.server.database.OwnedPostgres
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AccountRepositoryTest {
    private fun ready(runtime: ServiceRuntime) {
        runtime.start()
        val until = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
        while (!runtime.ready() && System.nanoTime() < until) Thread.sleep(20)
        assertTrue(runtime.ready())
    }

    @Test fun `V1 to V2 forward upgrade retains writer data and registration uniqueness is atomic`() {
        OwnedPostgres().start().use { db ->
            val v1 = Flyway.configure().configuration(FoundationMigrations.flyway(db.settings).configuration).target("1").load()
            assertEquals(1, v1.migrate().migrationsExecuted)
            val checksum = v1.info().applied().single().checksum
            db.connection().use { it.createStatement().execute("UPDATE service_writer SET epoch=79") }
            // V2 through V16, excluding the nonexistent V10/V12 migrations.
            assertEquals(15, FoundationMigrations.migrate(db.settings))
            assertEquals(checksum, FoundationMigrations.flyway(db.settings).info().applied().first().checksum)
            db.connection().use { connection -> connection.createStatement().executeQuery("SELECT epoch FROM service_writer").use {
                assertTrue(it.next()); assertEquals(79, it.getLong(1))
            } }
            ServiceRuntime(db.settings).use { runtime ->
                ready(runtime)
                val service = AccountService(runtime)
                Executors.newFixedThreadPool(2).use { pool ->
                    val jobs = (1..2).map { i -> pool.submit(Callable { service.register(if (i == 1) "Straße" else "STRASSE", "user$i@example.invalid", "password-$i") }) }
                    assertEquals(1, jobs.map { it.get(10, TimeUnit.SECONDS) }.count { it != null })
                }
                val usedEmail = db.connection().use { connection -> connection.createStatement().executeQuery("SELECT email_key FROM account").use {
                    assertTrue(it.next()); it.getString(1)
                } }
                assertNull(service.register("Other name", usedEmail.uppercase(), "password-1"))
                // A callback failure rolls back a real generated-field mutation through the existing fence.
                assertThrows(org.asyncttwiz.server.database.OwnershipFailure::class.java) {
                    runtime.transaction { connection ->
                        connection.createStatement().execute("UPDATE account SET username='must roll back'")
                        throw IllegalStateException("test rollback")
                    }
                }
                db.connection().use { connection -> connection.createStatement().executeQuery("SELECT count(*) FROM account WHERE username='must roll back'").use {
                    assertTrue(it.next()); assertEquals(0, it.getInt(1))
                } }
            }
            assertEquals(0, FoundationMigrations.migrate(db.settings))
            println("PASS retained V1 epoch/checksum -> V2, concurrent normalized uniqueness, atomic rollback and idempotent explicit migration")
        }
    }

    @Test fun `only token hashes persist epochs revoke and successful legacy login rehashes`() {
        OwnedPostgres().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            ServiceRuntime(db.settings).use { runtime ->
                ready(runtime)
                val service = AccountService(runtime)
                val account = service.register("private-name", "private-address@example.invalid", "exact-password")!!
                val old = org.springframework.security.crypto.argon2.Argon2PasswordEncoder(16, 32, 1, 16384, 2).encode("exact-password")!!
                db.connection().use { it.prepareStatement("UPDATE account SET password_phc=?").use { statement -> statement.setString(1, old); statement.executeUpdate() } }
                val session = service.login("PRIVATE-ADDRESS@EXAMPLE.INVALID", "exact-password")!!
                assertEquals(account, session.account)
                assertEquals(43, session.token.length)
                assertNotNull(service.authenticate(session.token))
                assertNull(service.authenticate(session.token + "="))
                db.connection().use { connection ->
                    connection.createStatement().executeQuery("SELECT password_phc, token_hash, a.auth_epoch, s.auth_epoch FROM account a JOIN account_session s ON s.account_id=a.id").use {
                        assertTrue(it.next())
                        assertTrue(it.getString(1).startsWith("\$argon2id\$v=19\$m=65536,t=3,p=1\$"))
                        assertArrayEquals(java.security.MessageDigest.getInstance("SHA-256").digest(session.token.toByteArray()), it.getBytes(2))
                        assertEquals(it.getLong(3), it.getLong(4))
                    }
                    connection.createStatement().executeQuery("SELECT column_name FROM information_schema.columns WHERE table_name='account_session'").use {
                        val columns = buildSet { while (it.next()) add(it.getString(1)) }
                        assertFalse("token" in columns); assertFalse("password" in columns)
                    }
                    connection.createStatement().execute("UPDATE account SET auth_epoch=auth_epoch+1")
                }
                assertNull(service.authenticate(session.token))
                val newSession = service.login("private-address@example.invalid", "exact-password")!!
                assertNotNull(service.authenticate(newSession.token))
                service.revokeToken(newSession.token)
                service.revokeToken(newSession.token)
                assertNull(service.authenticate(newSession.token))
                println("PASS hashed-only random opaque tokens, persistent epoch authorization/revocation and ordinary successful legacy verifier upgrade")
            }
        }
    }
}
