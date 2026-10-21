package org.asyncttwiz.server.database

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

class DatabaseSettingsTest {
    @TempDir lateinit var temporary: Path

    @Test fun `file input strips only a final line ending and redacts string representation`() {
        val secret = temporary.resolve("secret")
        for (ending in listOf("", "\n", "\r\n")) {
            Files.writeString(secret, "  synthetic password  $ending")
            val settings = DatabaseSettings.fromEnvironment(mapOf("DB_URL" to "jdbc:postgresql://localhost:5432/fixture",
                "DB_USER" to "fixture_user", "DB_PASSWORD_FILE" to secret.toString()))
            assertEquals("  synthetic password  ", settings.password())
            assertEquals("DatabaseSettings[redacted]", settings.toString())
        }
    }

    @Test fun `missing inputs unsupported URL credential URL and missing secret fail safely`() {
        assertThrows(IllegalArgumentException::class.java) { DatabaseSettings.fromEnvironment(emptyMap()) }
        for (url in listOf("jdbc:h2:mem:test", "not a URL", "jdbc:postgresql://localhost/test?password=secret",
            "jdbc:postgresql://localhost/test?user=someone")) {
            val failure = assertThrows(IllegalArgumentException::class.java) { DatabaseSettings(url, "fixture", "secret") }
            assertFalse(failure.message!!.contains("secret"))
        }
        assertThrows(IOException::class.java) { DatabaseSettings.fromEnvironment(mapOf("DB_PASSWORD_FILE" to temporary.resolve("missing").toString())) }
        assertThrows(IllegalArgumentException::class.java) { DatabaseSettings("jdbc:postgresql://localhost/test", "", "secret") }
        assertThrows(IllegalArgumentException::class.java) { DatabaseSettings("jdbc:postgresql://localhost/test", "fixture", "") }
    }
}
