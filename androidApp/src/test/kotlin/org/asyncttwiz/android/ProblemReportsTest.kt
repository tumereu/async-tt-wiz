package org.asyncttwiz.android

import android.util.Log
import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import org.asyncttwiz.protocol.AccountJson
import org.asyncttwiz.protocol.ProblemReport
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ProblemReportsTest {
    @get:Rule val temporary = TemporaryFolder()
    private val time = Instant.parse("2026-10-28T12:00:00Z")

    @Test fun `log keeps the newest lines within its byte bound and survives a restart`() {
        val file = temporary.newFile("app-log.txt")
        val log = LogBuffer(file, maxBytes = 20_000) { time }
        repeat(2_000) { log.add(Log.INFO, "update", "attempt $it") }
        val text = log.text()
        assertTrue(text.toByteArray().size <= 20_000)
        assertTrue(file.length() <= 20_000)
        assertTrue(text.endsWith("2026-10-28T12:00:00Z I/update: attempt 1999"))
        assertFalse(text.contains("attempt 0\n"))
        log.add(Log.ERROR, "crash", "Uncaught", IllegalStateException("boom"))
        val restored = LogBuffer(file, maxBytes = 20_000) { time }.text()
        assertEquals(log.text(), restored)
        assertTrue(restored.contains("E/crash: Uncaught\n    java.lang.IllegalStateException: boom"))
    }

    @Test fun `warnings carry the cause chain on one entry`() {
        val log = LogBuffer(null) { time }
        log.add(Log.WARN, "update", "Download interrupted", java.io.IOException("unexpected end of stream", java.net.SocketException("reset")))
        assertEquals("2026-10-28T12:00:00Z W/update: Download interrupted | java.io.IOException: unexpected end of stream " +
            "<- java.net.SocketException: reset", log.text())
    }

    // LOB-089
    @Test fun `secrets are redacted`() {
        val token = "AbCdEfGhIjKlMnOpQrStUvWxYz0123456789_-abcde"
        assertEquals(43, token.length)
        val text = redactLog("Authorization: Bearer $token; https://x.invalid/reset-password?token=$token " +
            "player@example.com {\"password\":\"hunter2\"} bare $token sha ${"a".repeat(64)}")
        assertFalse(text.contains(token))
        assertFalse(text.contains("player@example.com"))
        assertFalse(text.contains("hunter2"))
        assertTrue(text.contains("Bearer [redacted]"))
        assertTrue(text.contains("token=[redacted]"))
        assertTrue(text.contains("a".repeat(64)))
    }

    @Test fun `oversized reports drop the oldest logcat lines first, then the oldest log lines`() {
        val report = ProblemReport(time.toString(), "0.1.3", 4, "release", "Pixel", "Android 15", "desc", "problem",
            log = (1..2_000).joinToString("\n") { "log line $it" }, logcat = (1..2_000).joinToString("\n") { "cat line $it" })
        fun size(value: ProblemReport) = AccountJson.format.encodeToString(value).toByteArray().size
        val small = fitProblemReport(report, 40_000)
        assertTrue(size(small) <= 40_000)
        assertEquals(report.log, small.log)
        assertTrue(small.logcat.endsWith("cat line 2000") && small.logcat.startsWith("cat line "))
        val tiny = fitProblemReport(report, 10_000)
        assertTrue(size(tiny) <= 10_000)
        assertEquals("", tiny.logcat)
        assertTrue(tiny.log.endsWith("log line 2000") && tiny.log.startsWith("log line "))
        assertEquals("desc", tiny.description)
        assertSame(report, fitProblemReport(report, 1_000_000))
    }

    // LOB-089
    @Test fun `sender shows the code or a failure the player can retry`() = runBlocking {
        val sent = mutableListOf<Pair<ProblemReport, String?>>()
        var fail = true
        val sender = ProblemReportSender(this) { report, token ->
            sent += report to token
            if (fail) throw AccountFailure("network") else "ABC234"
        }
        AppLog.info("update", "Attempt ended at 10 / 20 bytes")
        sender.send("It stops", "Update failed.")
        while (sender.state == ProblemReportState.Sending) delay(10)
        assertEquals(ProblemReportState.Failed("Couldn't reach the server. Check your connection and try again."), sender.state)
        fail = false
        sender.send("It stops", "Update failed.", "t".repeat(43))
        while (sender.state == ProblemReportState.Sending) delay(10)
        assertEquals(ProblemReportState.Sent("ABC234"), sender.state)
        val (report, token) = sent.last()
        assertEquals("t".repeat(43), token)
        assertEquals("It stops", report.description)
        assertEquals("Update failed.", report.problem)
        assertEquals(BuildConfig.VERSION_CODE, report.versionCode)
        assertTrue(report.log.contains("Attempt ended at 10 / 20 bytes"))
    }
}
