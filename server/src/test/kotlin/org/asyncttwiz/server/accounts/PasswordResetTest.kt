package org.asyncttwiz.server.accounts

import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.asyncttwiz.server.ServiceRuntime
import org.asyncttwiz.server.database.FoundationMigrations
import org.asyncttwiz.server.database.FreshDatabase
import org.asyncttwiz.server.database.TestPostgres
import org.flywaydb.core.Flyway
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class PasswordResetTest {
    @TempDir lateinit var directory: Path
    private class Time(var instant: Instant = Instant.parse("2026-10-22T00:00:00Z")) : Clock() {
        override fun instant() = instant
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
        fun minutes(value: Long) { instant = instant.plusSeconds(value * 60) }
    }
    private fun ready(runtime: ServiceRuntime) {
        runtime.start()
        val until = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
        while (!runtime.ready() && System.nanoTime() < until) Thread.sleep(20)
        assertTrue(runtime.ready())
    }
    private fun sql(db: TestPostgres, query: String): String = db.connection().use {
        DSL.using(it, SQLDialect.POSTGRES).fetchValue(query).toString()
    }
    private fun token(link: String) = URI(link).query.substringAfter("token=")

    @Test fun `retained V2 upgrade and real mail reset preserve sessions until explicit atomic confirmation`() {
        FreshDatabase(migrated = false).start().use { db ->
            Flyway.configure().configuration(FoundationMigrations.flyway(db.settings).configuration).target("2").load().migrate()
            val pendingForwards = FoundationMigrations.flyway(db.settings).info().pending().size
            lateinit var first: String
            lateinit var second: String
            val old = " old e\u0301😀 password\n"
            val replacement = "\t New e\u0301😀 password \n"
            // Deliberately populate the old supported schema before applying V3.
            db.connection().use { c ->
                    val phc = PasswordHashing().hash(old)
                    c.prepareStatement("INSERT INTO account(id,username,username_key,email_delivery,email_key,password_phc) VALUES (gen_random_uuid(),'Wizard','wizard','Private@bücher.example','private@xn--bcher-kva.example',?)").use {
                        it.setString(1, phc); it.executeUpdate()
                    }
            }
            assertEquals(pendingForwards, FoundationMigrations.migrate(db.settings))
            assertEquals("1", sql(db, "SELECT count(*) FROM account"))
            SyntheticMailbox(directory.resolve("mail")).use { box ->
                ServiceRuntime(db.settings).use { runtime ->
                    ready(runtime)
                    val accounts = AccountService(runtime)
                    first = accounts.login("private@bücher.example", old)!!.token
                    second = accounts.login("private@bücher.example", old)!!.token
                    val time = Time()
                    val reset = PasswordResetService(runtime, box.settings(), clock = time)
                    reset.request(" PRIVATE@XN--BCHER-KVA.EXAMPLE ")
                    reset.request("private@bücher.example")
                    reset.request("unknown@example.invalid")
                    assertEquals("2", sql(db, "SELECT count(*) FROM password_reset_token"))
                    assertEquals("2", sql(db, "SELECT count(*) FROM reset_mail WHERE status='pending'"))
                    assertEquals("32", sql(db, "SELECT min(octet_length(token_hash)) FROM password_reset_token"))
                    assertEquals("0", sql(db, "SELECT auth_epoch FROM account"))
                    assertNotNull(accounts.authenticate(first)); assertNotNull(accounts.authenticate(second))
                    reset.deliverDue(SmtpResetMailer(box.settings()))
                    val links = box.awaitLinks(2)
                    assertEquals("0", sql(db, "SELECT count(*) FROM reset_mail WHERE encrypted_payload IS NOT NULL"))
                    val a = token(links[0]); val b = token(links[1])
                    assertTrue(a != b && a.length == 43 && b.length == 43)
                    assertTrue(reset.usable(a)); assertTrue(reset.usable(b)) // GET/open-equivalent, not consumption.
                    assertNotNull(accounts.authenticate(first)); assertNotNull(accounts.authenticate(second))
                    assertFalse(reset.confirm("invalid", replacement))
                    assertThrows(InvalidAccountField::class.java) { reset.confirm(a, "short") }
                    assertNotNull(accounts.authenticate(first))
                    reset.request("private@bücher.example") // A still-pending third token is consumed too.
                    Executors.newFixedThreadPool(2).use { pool ->
                        val jobs = listOf(a, b).map { t -> pool.submit(Callable { reset.confirm(t, replacement) }) }
                        assertEquals(1, jobs.count { it.get(20, TimeUnit.SECONDS) })
                    }
                    assertFalse(reset.confirm(a, replacement)); assertFalse(reset.confirm(b, replacement))
                    assertEquals("1", sql(db, "SELECT auth_epoch FROM account"))
                    assertEquals("3", sql(db, "SELECT count(*) FROM password_reset_token WHERE consumed_at IS NOT NULL"))
                    assertEquals("1", sql(db, "SELECT count(*) FROM reset_mail WHERE status='cancelled' AND encrypted_payload IS NULL"))
                    assertEquals("0", sql(db, "SELECT count(*) FROM account_session WHERE revoked_at IS NULL"))
                    assertNull(accounts.authenticate(first)); assertNull(accounts.authenticate(second))
                    assertNull(accounts.login("private@bücher.example", old))
                    assertNull(accounts.login("private@bücher.example", replacement.trim()))
                    assertNull(accounts.login("private@bücher.example", java.text.Normalizer.normalize(replacement, java.text.Normalizer.Form.NFC)))
                    assertNotNull(accounts.login("PRIVATE@BÜCHER.EXAMPLE", replacement))
                    reset.request("private@bücher.example")
                    reset.deliverDue(SmtpResetMailer(box.settings()))
                    val expires = token(box.awaitLinks(3).last())
                    val validSession = accounts.login("private@bücher.example", replacement)!!.token
                    time.minutes(30)
                    assertFalse(reset.usable(expires)); assertFalse(reset.confirm(expires, old))
                    assertNotNull(accounts.authenticate(validSession))
                    println("PASS retained V2 accounts -> V3, canonical email, two independent tokens/real SMTP, no pre-confirmation revocation, concurrent one-winner all-token consumption/epoch/revocation, exact Unicode-whitespace new password, invalid/reuse/30-minute expiry leave sessions unchanged")
                }
            }
        }
    }

    @Test fun `durable encrypted mail survives runtime restart and actual SMTP follows bounded backoff and expiry`() {
        FreshDatabase().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            val time = Time()
            SyntheticMailbox(directory.resolve("mail")).use { box ->
                val settings = box.settings()
                ServiceRuntime(db.settings).use { runtime ->
                    ready(runtime)
                    AccountService(runtime).register("Mail wizard", "mail@example.invalid", "password")
                    val reset = PasswordResetService(runtime, settings, clock = time)
                    reset.request("mail@example.invalid")
                    assertEquals("1", sql(db, "SELECT count(*) FROM reset_mail WHERE encrypted_payload IS NOT NULL AND position(convert_to('mail@example.invalid','UTF8') IN encrypted_payload)=0"))
                    reset.validatePending()
                }
                ServiceRuntime(db.settings).use { runtime ->
                    ready(runtime)
                    val reset = PasswordResetService(runtime, settings, clock = time)
                    reset.validatePending()
                    box.fault("temporary")
                    val sender = SmtpResetMailer(settings)
                    reset.deliverDue(sender)
                    assertEquals("1", sql(db, "SELECT attempts FROM reset_mail"))
                    assertEquals("60.000000", sql(db, "SELECT extract(epoch FROM (next_attempt_at - created_at)) FROM reset_mail JOIN password_reset_token ON token_id=id"))
                    reset.deliverDue(sender); assertEquals("1", sql(db, "SELECT attempts FROM reset_mail"))
                    time.minutes(1); reset.deliverDue(sender)
                    assertEquals("2", sql(db, "SELECT attempts FROM reset_mail"))
                    assertEquals("360.000000", sql(db, "SELECT extract(epoch FROM (next_attempt_at - created_at)) FROM reset_mail JOIN password_reset_token ON token_id=id"))
                    time.minutes(5); reset.deliverDue(sender)
                    assertEquals("3", sql(db, "SELECT attempts FROM reset_mail"))
                    assertEquals("1260.000000", sql(db, "SELECT extract(epoch FROM (next_attempt_at - created_at)) FROM reset_mail JOIN password_reset_token ON token_id=id"))
                    box.fault("ok"); time.minutes(15); reset.deliverDue(sender)
                    box.awaitLinks(1)
                    assertEquals("delivered", sql(db, "SELECT status FROM reset_mail"))
                    assertEquals("0", sql(db, "SELECT count(*) FROM reset_mail WHERE encrypted_payload IS NOT NULL"))
                    reset.request("mail@example.invalid"); box.fault("permanent"); reset.deliverDue(sender)
                    assertEquals("1", sql(db, "SELECT count(*) FROM reset_mail WHERE status='failed' AND attempts=1 AND encrypted_payload IS NULL"))
                    reset.request("mail@example.invalid"); box.fault("temporary"); reset.deliverDue(sender)
                    time.minutes(30); box.fault("ok"); reset.deliverDue(sender)
                    assertEquals("1", sql(db, "SELECT count(*) FROM reset_mail WHERE status='expired' AND encrypted_payload IS NULL"))
                    assertEquals(1, box.links().size)
                    assertEquals("0", sql(db, "SELECT auth_epoch FROM account"))
                    reset.request("mail@example.invalid")
                    val wrong = ResetMailSettings(settings.host, settings.port, settings.tls, null, null, settings.from, settings.resetOrigin, ByteArray(32) { 99 })
                    assertThrows(org.asyncttwiz.server.database.OwnershipFailure::class.java) { PasswordResetService(runtime, wrong, clock = time).validatePending() }
                    println("PASS AES-GCM pending mail retained across runtime restart; actual SMTP 451 at 0/1/6 minutes, 15-minute next delay, delivery deletion, permanent 550 no retry, expiry deletion/no late mail; wrong encryption key refuses recovery, never changes credentials")
                }
            }
        }
    }

    @Test fun `explicit TLS and file secrets fail closed without plaintext credential fallback`() {
        SyntheticMailbox(directory.resolve("mail")).use { box ->
            assertEquals(MailResult.TEMPORARY, SmtpResetMailer(box.settings("starttls")).send("mail@example.invalid", "https://reset.synthetic.invalid/reset-password?token=not-a-real-token"))
            assertTrue(box.links().isEmpty())
        }
        val env = SyntheticMailbox.environment(directory)
        assertEquals(32, ResetMailSettings.fromEnvironment(env).key.size)
        for (bad in listOf(env - "RESET_MAIL_KEY_FILE", env + ("SMTP_TLS" to "false"), env + ("RESET_BASE_URL" to "http://unsafe.invalid"),
            env + ("SMTP_FROM" to "bad\r\nBCC:evil@invalid"), env + ("RESET_BASE_URL" to "https://user:secret@invalid")))
            assertThrows(Exception::class.java) { ResetMailSettings.fromEnvironment(bad) }
        val username = directory.resolve("smtp-user"); val password = directory.resolve("smtp-password")
        Files.writeString(username, "synthetic-user\r\n"); Files.writeString(password, " exact synthetic SMTP password \r\n")
        val secure = ResetMailSettings.fromEnvironment(env + mapOf("SMTP_TLS" to "starttls", "SMTP_USERNAME_FILE" to username.toString(), "SMTP_PASSWORD_FILE" to password.toString()))
        assertTrue(secure.password == " exact synthetic SMTP password ")
        assertEquals("ResetMailSettings[redacted]", secure.toString())
        assertThrows(Exception::class.java) { ResetMailSettings.fromEnvironment(env + ("SMTP_USERNAME_FILE" to username.toString())) }
        Files.writeString(directory.resolve("reset-mail-key"), "invalid-key")
        assertThrows(Exception::class.java) { ResetMailSettings.fromEnvironment(env) }
        println("PASS TLS-required SMTP refuses non-TLS sink before DATA, explicit modes/HTTPS/from/key/paired file secrets validated and redacted, exact SMTP password retained; real external SMTP credentials/TLS exercise remains operator qualification")
    }
}
