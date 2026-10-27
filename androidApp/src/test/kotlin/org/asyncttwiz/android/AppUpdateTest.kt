package org.asyncttwiz.android

import java.io.File
import java.security.MessageDigest
import org.junit.Test
import org.junit.Assert.*

class AppUpdateTest {
    private val release = AppRelease(42, "1.2.3", "a".repeat(64), 100)

    @Test fun `only newer releases or a rules incompatibility block entry`() {
        assertFalse(appUpdateRequired(42, null))
        assertFalse(appUpdateRequired(42, release))
        assertFalse(appUpdateRequired(43, release))
        assertTrue(appUpdateRequired(41, release))
        assertTrue(appUpdateRequired(42, null, incompatibleRules = true))
        assertTrue(appUpdateRequired(42, release, incompatibleRules = true))
    }

    @Test fun `offline missing and rollback checks retain cached newer metadata`() {
        assertEquals(release, newestAppRelease(release, null))
        assertEquals(release, newestAppRelease(release, release.copy(versionCode = 41)))
        assertTrue(appUpdateRequired(41, newestAppRelease(release, null)))
        assertNull(newestAppRelease(null, null))
        assertEquals(release.copy(versionCode = 43), newestAppRelease(release, release.copy(versionCode = 43)))
    }

    @Test fun `metadata validates download limits and digest`() {
        val text = """{"versionCode":42,"versionName":"1.2.3","sha256":"${release.sha256}","size":100}"""
        assertEquals(release, AppRelease.parse(text))
        listOf(text.replace(":100", ":0"), text.replace(":42", ":0"),
            text.replace(release.sha256, "../apk"), text.replace(":100", ":536870913")).forEach {
            assertTrue(runCatching { AppRelease.parse(it) }.isFailure)
        }
    }

    @Test fun `APK requires exact length and SHA256 including cache hits`() {
        val file = File.createTempFile("atw-update", ".apk")
        try {
            val bytes = ByteArray(100_000) { (it % 251).toByte() }
            file.writeBytes(bytes)
            val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            val target = release.copy(sha256 = hash, size = bytes.size.toLong())
            assertTrue(verifyAppApk(file, target))
            assertFalse(verifyAppApk(file, target.copy(size = target.size + 1)))
            file.writeBytes(bytes.apply { this[1] = 0 })
            assertFalse(verifyAppApk(file, target))
            file.delete()
            assertFalse(verifyAppApk(file, target))
        } finally { file.delete() }
    }
}
