package org.asyncttwiz.server

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class ServiceSettingsTest {
    @TempDir lateinit var temporary: Path

    @Test fun `typed defaults preserve unicode whitespace secret and redact all configuration`() {
        val secret = temporary.resolve("private-file")
        Files.writeString(secret, "  ä synthetic password \r\n")
        val settings = ServiceSettings.fromEnvironment(mapOf("DB_URL" to "jdbc:postgresql://127.0.0.1:1/private_db",
            "DB_USER" to "private_username", "DB_PASSWORD_FILE" to secret.toString(),
            "PUBLIC_BASE_URL" to "https://synthetic.invalid/base"))
        assertEquals("  ä synthetic password ", settings.database().password())
        assertEquals(8080, settings.publicPort())
        assertEquals(8081, settings.healthPort())
        assertEquals("0.0.0.0", settings.publicAddress().hostAddress)
        assertEquals("127.0.0.1", settings.healthAddress().hostAddress)
        assertEquals("https://synthetic.invalid/base", settings.publicBaseUrl().toString())
        assertEquals("ServiceSettings[redacted]", settings.toString())
    }

    @Test fun `oversized file secret is refused rather than loading unlimited bytes`() {
        val secret = temporary.resolve("too-large")
        Files.writeString(secret, "a".repeat(65537))
        assertThrows(java.io.IOException::class.java) {
            ServiceSettings.fromEnvironment(mapOf("DB_URL" to "jdbc:postgresql://127.0.0.1:1/private_db",
                "DB_USER" to "private_username", "DB_PASSWORD_FILE" to secret.toString(),
                "PUBLIC_BASE_URL" to "https://synthetic.invalid"))
        }
    }
}
