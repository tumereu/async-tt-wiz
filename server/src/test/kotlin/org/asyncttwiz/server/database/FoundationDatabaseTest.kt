package org.asyncttwiz.server.database

import org.asyncttwiz.server.generated.tables.ServiceWriter.SERVICE_WRITER
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.FlywayException
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

class FoundationDatabaseTest {
    @TempDir lateinit var temporary: Path

    private fun command(settings: DatabaseSettings, vararg args: String): Pair<Int, String> {
        val secret = temporary.resolve("database-password")
        Files.writeString(secret, settings.password() + "\n")
        val log = temporary.resolve("migration-output")
        val builder = ProcessBuilder(listOf(System.getProperty("asyncttwiz.migrationExecutable")) + args)
            .redirectErrorStream(true).redirectOutput(log.toFile())
        // Isolated inputs: never inherit a developer's JDBC/secret/application settings.
        val inherited = builder.environment().filterKeys { it in setOf("PATH", "JAVA_HOME", "JAVA_TOOL_OPTIONS", "HOME") }
        builder.environment().clear()
        builder.environment().putAll(inherited)
        builder.environment().putAll(mapOf("DB_URL" to settings.url(), "DB_USER" to settings.user(), "DB_PASSWORD_FILE" to secret.toString()))
        val process = builder.start()
        if (!process.waitFor(45, TimeUnit.SECONDS)) {
            process.destroyForcibly().waitFor()
            fail<Unit>("Explicit migration command exceeded 45 seconds")
        }
        val output = Files.readString(log)
        assertFalse(output.contains(settings.password()), "Secret printed by migration executable")
        assertFalse(output.contains(settings.url()), "Raw database URL printed by migration executable")
        println("Installed executable args=${args.toList()} exit=${process.exitValue()} output=$output")
        return process.exitValue() to output
    }

    private fun epoch(db: OwnedPostgres): Long = db.connection().use {
        DSL.using(it, SQLDialect.POSTGRES).select(SERVICE_WRITER.EPOCH).from(SERVICE_WRITER)
            .where(SERVICE_WRITER.SINGLETON.isTrue).fetchSingle(SERVICE_WRITER.EPOCH)!!
    }

    @Test fun `installed explicit runner migrates clean then retains data on idempotent rerun`() {
        OwnedPostgres().use { db ->
            db.start()
            val expectedVersions = MigrationTestSupport.supportedVersions(db.settings)
            val (exit, output) = command(db.settings, "migrate")
            assertEquals(0, exit)
            assertTrue(output.lineSequence().any { it == "Migration successful; applied=${expectedVersions.size}" })
            assertEquals(0, epoch(db))
            val flyway = FoundationMigrations.flyway(db.settings)
            assertTrue(flyway.validateWithResult().validationSuccessful)
            assertEquals(expectedVersions, flyway.info().applied().map { it.version.toString() })
            val history = flyway.info().applied().single { it.version.toString() == "1" }
            assertEquals("1", history.version.toString())
            assertEquals("V1__service_writer.sql", history.script)
            assertNotNull(history.checksum)
            println("Validated Flyway history: version=${history.version} script=${history.script} checksum=${history.checksum}")
            db.connection().use { DSL.using(it, SQLDialect.POSTGRES).update(SERVICE_WRITER).set(SERVICE_WRITER.EPOCH, 91L).execute() }
            val second = command(db.settings, "migrate")
            assertEquals(0, second.first)
            assertTrue(second.second.contains("applied=0"))
            assertEquals(91, epoch(db))
            assertEquals(history.checksum, flyway.info().applied().single { it.version.toString() == "1" }.checksum)
            db.connection().use { connection ->
                val tables = DSL.using(connection, SQLDialect.POSTGRES).fetch("SELECT tablename FROM pg_tables WHERE schemaname = 'public'")
                    .map { it.get(0, String::class.java) }.toSet()
                assertEquals(setOf("service_writer", "flyway_schema_history", "account", "account_session", "account_token_favorites", "password_reset_token", "reset_mail",
                    "request_identity", "account_request", "definition", "account_definition", "saved_deck", "deck_entry", "saved_cube", "cube_entry", "pod", "pod_member", "pod_invitation",
                    "contact", "game", "invitation", "game_stream", "game_inbox", "game_revision", "game_outbox",
                    "player_preparation", "game_definition", "game_play", "gameplay_group", "gameplay_mutation",
                    "game_chat", "game_chat_read_cursor", "game_play_mutation", "activity_cursor", "account_activity", "activity_summary",
                    "activity_installation", "activity_push", "gameplay_tree_control", "gameplay_endpoint_record", "gameplay_endpoint_edge", "debug_crash_report", "debug_crash_daily_count"), tables)
            }
            assertThrows(FlywayException::class.java) { flyway.clean() }
            assertEquals(91, epoch(db))
        }
    }

    @Test fun `generated fields query commit rollback and enforce singleton constraints`() {
        OwnedPostgres().use { db ->
            db.start()
            FoundationMigrations.migrate(db.settings)
            db.connection().use { connection ->
                val sql = DSL.using(connection, SQLDialect.POSTGRES)
                sql.transaction { configuration ->
                    assertEquals(1, DSL.using(configuration).update(SERVICE_WRITER)
                        .set(SERVICE_WRITER.EPOCH, SERVICE_WRITER.EPOCH.plus(1L))
                        .where(SERVICE_WRITER.SINGLETON.isTrue).execute())
                }
                assertEquals(1, epoch(db))
                assertThrows(IllegalStateException::class.java) {
                    sql.transaction { configuration ->
                        DSL.using(configuration).update(SERVICE_WRITER).set(SERVICE_WRITER.EPOCH, 999L).execute()
                        throw IllegalStateException("Force rollback")
                    }
                }
                assertEquals(1, epoch(db))
                for (invalidSql in listOf(
                    "INSERT INTO service_writer VALUES (false, 0)",
                    "INSERT INTO service_writer VALUES (true, 0)",
                    "UPDATE service_writer SET epoch = -1",
                    "UPDATE service_writer SET epoch = NULL",
                )) {
                    assertThrows(org.jooq.exception.DataAccessException::class.java) { sql.execute(invalidSql) }
                }
                assertEquals(1, epoch(db))
            }
            println("PASS generated-field query/commit/rollback and database singleton/epoch constraints")
        }
    }

    private fun withExtraMigration(db: OwnedPostgres, version: String, sql: String): Flyway {
        val directory = Files.createDirectory(temporary.resolve("extra-migrations"))
        Files.writeString(directory.resolve("V${version}__test_future.sql"), sql)
        return Flyway.configure().configuration(FoundationMigrations.flyway(db.settings).configuration)
            .locations("classpath:db/migration", "filesystem:$directory").load()
    }

    @Test fun `failed forward migration rolls back DDL and retains existing rows and history`() {
        OwnedPostgres().use { db ->
            db.start()
            val expectedVersions = MigrationTestSupport.supportedVersions(db.settings)
            FoundationMigrations.migrate(db.settings)
            val migration = withExtraMigration(db, MigrationTestSupport.futureVersion(db.settings),
                "CREATE TABLE rollback_probe (id integer); UPDATE service_writer SET epoch = 55; SELECT 1 / 0;")
            assertThrows(FlywayException::class.java) { migration.migrate() }
            assertEquals(0, epoch(db))
            assertEquals(expectedVersions, FoundationMigrations.flyway(db.settings).info().applied().map { it.version.toString() })
            db.connection().use { connection ->
                assertNull(DSL.using(connection, SQLDialect.POSTGRES).fetchValue("SELECT to_regclass('public.rollback_probe')"))
            }
            // Retry the supported command without clean, repair or rewriting history.
            assertEquals(0, command(db.settings, "migrate").first)
            println("PASS failed transactional forward migration preserves supported schema/data without repair")
        }
    }

    @Test fun `checksum mismatch is refused without automatic repair or data loss`() {
        OwnedPostgres().use { db ->
            db.start()
            FoundationMigrations.migrate(db.settings)
            val original = FoundationMigrations.flyway(db.settings).info().applied().single { it.version.toString() == "1" }.checksum!!
            db.connection().use {
                DSL.using(it, SQLDialect.POSTGRES).execute("UPDATE flyway_schema_history SET checksum = checksum + 1 WHERE version = '1'")
            }
            assertEquals(1, command(db.settings, "migrate").first)
            assertEquals(original + 1, FoundationMigrations.flyway(db.settings).info().applied().single { it.version.toString() == "1" }.checksum)
            assertEquals(0, epoch(db))
            println("PASS mismatched checksum rejected and not repaired")
        }
    }

    @Test fun `newer schema is refused instead of silently ignored or downgraded`() {
        OwnedPostgres().use { db ->
            db.start()
            val expectedVersions = MigrationTestSupport.supportedVersions(db.settings)
            val futureVersion = MigrationTestSupport.futureVersion(db.settings)
            FoundationMigrations.migrate(db.settings)
            assertEquals(1, withExtraMigration(db, futureVersion, "CREATE TABLE future_probe (id integer); UPDATE service_writer SET epoch = 7;")
                .migrate().migrationsExecuted)
            assertEquals(1, command(db.settings, "migrate").first)
            assertEquals(expectedVersions + futureVersion, FoundationMigrations.flyway(db.settings).info().applied().map { it.version.toString() })
            assertEquals(7, epoch(db))
            println("PASS unsupported future schema rejected with retained rows/history")
        }
    }

    @Test fun `nonempty unversioned database is refused without baselining or replacing it`() {
        OwnedPostgres().use { db ->
            db.start()
            db.connection().use { DSL.using(it, SQLDialect.POSTGRES).execute("CREATE TABLE retained_probe AS SELECT 42 AS value") }
            assertEquals(1, command(db.settings, "migrate").first)
            db.connection().use {
                val sql = DSL.using(it, SQLDialect.POSTGRES)
                assertEquals(42, sql.fetchValue("SELECT value FROM retained_probe"))
                assertNull(sql.fetchValue("SELECT to_regclass('public.service_writer')"))
            }
            assertEquals(2, command(db.settings, "clean").first)
            println("PASS nonempty unversioned schema retained and destructive CLI mode unavailable")
        }
    }
}
