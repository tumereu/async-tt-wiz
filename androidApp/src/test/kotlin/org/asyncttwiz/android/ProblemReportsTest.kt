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

    // LOB-089: the Automatum log keeps its newest entries up to its bound, in order, added from several threads alike
    @Test fun `the Automatum log keeps its newest entries`() {
        val log = org.asyncttwiz.automatum.AutomatumLog
        log.clear()
        val total = log.ENTRIES * 2 + 500
        repeat(total) { log.refusal("game-1", "step", "$it") }
        val kept = log.entries("game-1").map { (it as org.asyncttwiz.automatum.AutomatumEntry.Refusal).reason.toInt() }
        assertEquals((total - log.ENTRIES until total).toList(), kept)
        log.clear()
        List(4) { thread -> kotlin.concurrent.thread { repeat(500) { log.refusal("game-2", "step", "$thread:$it") } } }.forEach { it.join() }
        val reasons = log.entries("game-2").map { (it as org.asyncttwiz.automatum.AutomatumEntry.Refusal).reason }
        assertEquals(2_000, reasons.toSet().size)
        for (thread in 0 until 4) assertEquals((0 until 500).map { "$thread:$it" }, reasons.filter { it.startsWith("$thread:") })
        log.clear()
    }

    private fun decodeEngine(report: String) = org.asyncttwiz.protocol.game.GameRepro.decode(java.util.zip.GZIPInputStream(
        java.util.Base64.getDecoder().decode(report.substringAfter("\n")).inputStream()).use { it.readBytes() }.decodeToString())

    // LOB-089: a bundle with the device's whole record (GameDevice.MAX_RECORD) fits a report as it is when its lines are the
    // usual ones; lines too many for the bound go oldest first, and the rest still decodes
    @Test fun `a bundle with the whole record fits and decodes`() {
        val max = org.asyncttwiz.protocol.game.GameDevice.MAX_RECORD
        fun id() = java.util.UUID.randomUUID().toString()
        val usual = org.asyncttwiz.protocol.game.GameRepro(me = id(), definitions = "", lineStart = id(), localHead = id(),
            log = List(max) { "out GameAction\$Submit ${id()} on ${id()}" })
        val text = assertNotNull(AutomatumReports.fitEngine(usual, org.asyncttwiz.protocol.CrashReport.MAX_AUTOMATUM))
        assertTrue(text.length <= org.asyncttwiz.protocol.CrashReport.MAX_AUTOMATUM)
        assertEquals(usual, decodeEngine(text))
        val heavy = usual.copy(log = List(max) { List(9) { id() }.joinToString("") })
        val small = assertNotNull(AutomatumReports.fitEngine(heavy, 100_000))
        assertTrue(small.length <= 100_000)
        val kept = decodeEngine(small).log
        assertTrue(kept.size in 20 until max)
        assertEquals(heavy.log.takeLast(kept.size), kept)
    }

    // LOB-089, I-D43: a report carries the latest game's Automatum log and reproduction bundle, compressed and bounded
    @Test fun `reports carry the Automatum bundle within its bound`() = runBlocking {
        org.asyncttwiz.automatum.AutomatumLog.clear()
        assertEquals("", AutomatumReports.text(), "nothing recorded, nothing sent")
        repeat(300) { org.asyncttwiz.automatum.AutomatumLog.refusal("game-1", "cast step", "refusal $it " + List(4) { java.util.UUID.randomUUID() }) }
        val text = AutomatumReports.text()
        assertTrue(text.startsWith(org.asyncttwiz.automatum.ReproBundle.REPORT_HEADER + "\n"))
        fun decode(report: String) = org.asyncttwiz.automatum.ReproBundle.decode(java.util.zip.GZIPInputStream(
            java.util.Base64.getDecoder().decode(report.substringAfter("\n")).inputStream()).use { it.readBytes() }.decodeToString())
        val bundle = decode(text)
        assertEquals("game-1", bundle.game)
        assertEquals(300, bundle.entries.size)
        // Too big for the bound: older entries go first
        val small = AutomatumReports.fit(bundle, 3_000)
        assertTrue(small.length <= 3_000)
        val kept = decode(small)
        assertTrue(kept.entries.size < 300 && kept.entries.last() == bundle.entries.last())

        val sent = mutableListOf<ProblemReport>()
        val sender = ProblemReportSender(this, automatum = { text }) { report, _ -> sent += report; "ABC234" }
        sender.send("Odd")
        while (sender.state == ProblemReportState.Sending) delay(10)
        assertEquals(text, sent.single().automatum)
        org.asyncttwiz.automatum.AutomatumLog.clear()
    }
}
