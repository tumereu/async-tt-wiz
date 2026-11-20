package org.asyncttwiz.server.database

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import org.flywaydb.core.api.CoreErrorCode
import org.flywaydb.core.api.FlywayException
import org.postgresql.util.PSQLException
import org.postgresql.util.PSQLState

class MigrationCommandTest {
    @TempDir lateinit var temporary: Path

    @Test fun `installed CLI rejects missing and malformed inputs without leaking secrets or paths`() {
        val marker = "synthetic-do-not-log-credential"
        val secret = temporary.resolve("private-secret-file")
        Files.writeString(secret, marker)
        val valid = mapOf("DB_URL" to "jdbc:postgresql://localhost:5432/fixture", "DB_USER" to "fixture_user",
            "DB_PASSWORD_FILE" to secret.toString())
        val cases = listOf(
            emptyMap(),
            valid - "DB_USER",
            valid + ("DB_PASSWORD_FILE" to temporary.resolve("absent-file").toString()),
            valid + ("DB_URL" to "jdbc:postgresql://localhost:invalid/fixture?password=$marker"),
        )
        for ((index, inputs) in cases.withIndex()) {
            val log = temporary.resolve("config-case-$index")
            val builder = ProcessBuilder(System.getProperty("asyncttwiz.migrationExecutable"), "migrate")
                .redirectErrorStream(true).redirectOutput(log.toFile())
            val inherited = builder.environment().filterKeys { it in setOf("PATH", "JAVA_HOME", "JAVA_TOOL_OPTIONS", "HOME") }
            builder.environment().clear()
            builder.environment().putAll(inherited + inputs)
            val process = builder.start()
            if (!process.waitFor(20, TimeUnit.SECONDS)) {
                process.destroyForcibly().waitFor()
                fail<Unit>("Malformed configuration did not fail promptly")
            }
            val output = Files.readString(log)
            assertEquals(1, process.exitValue())
            assertTrue(output.contains("Migration failed"))
            assertTrue(output.contains(" Migration failure: flywayError=NONE cause0={class=java.lang.IllegalArgumentException")
                || output.contains(" Migration failure: flywayError=NONE cause0={class=java.io.IOException"), "Reason missing: $output")
            assertFalse(output.contains(marker), "Secret exposed by malformed configuration case $index")
            assertFalse(output.contains(secret.toString()), "Secret path exposed by case $index")
            assertFalse(output.contains("jdbc:postgresql:"), "Raw JDBC URL exposed by case $index")
            println("PASS installed migration configuration failure/redaction case=$index exit=${process.exitValue()}")
        }
    }

    @Test fun `failure diagnostic names the reason and cause chain without the connection inputs`() {
        val url = "jdbc:postgresql://db.internal:5432/atw?password=synthetic-do-not-log"
        val failure = FlywayException("Unable to obtain connection from database ($url)",
            PSQLException("The connection attempt failed.", PSQLState.CONNECTION_UNABLE_TO_CONNECT, java.net.SocketException("Connection reset")),
            CoreErrorCode.DB_CONNECTION)
        val line = MigrationCommand.diagnostic(failure)
        java.time.Instant.parse(line.substringBefore(' '))
        assertTrue(line.contains(" Migration failure: flywayError=DB_CONNECTION cause0={class=org.flywaydb.core.api.FlywayException"), line)
        assertTrue(line.contains("cause1={class=org.postgresql.util.PSQLException sqlState=08001 message=\"The connection attempt failed.\"}"), line)
        assertTrue(line.contains("cause2={class=java.net.SocketException sqlState=NONE message=\"Connection reset\"}"), line)
        assertFalse(line.contains("db.internal") || line.contains("synthetic-do-not-log") || line.contains("jdbc:"), line)
    }
}
