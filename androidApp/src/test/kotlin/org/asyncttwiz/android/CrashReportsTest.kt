package org.asyncttwiz.android

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import org.asyncttwiz.protocol.AccountJson
import org.asyncttwiz.protocol.CrashReport
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CrashReportsTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun report(n: Int = 0) = CrashReport("2026-10-24T12:00:00Z", "debug-$n", "Android test device", "trace $n")

    @Test fun keepsOnlyTenNewestCompleteReports() {
        val directory = temporary.newFolder()
        val store = CrashReportStore(directory)
        repeat(13) { n ->
            store.write(report(n))
            // Distinct stable modification times make the oldest-file policy explicit.
            store.pending().single { AccountJson.format.decodeFromString<CrashReport>(it.readText()) == report(n) }
                .setLastModified(10_000L + n)
        }
        assertEquals((3..12).map { report(it) }, store.pending().map { AccountJson.format.decodeFromString<CrashReport>(it.readText()) })
        assertEquals(10, directory.listFiles()!!.size)
    }

    @Test fun deletesOnlySuccessfulUploadsAndRetriesFailuresOnLaterStart() = runBlocking {
        val store = CrashReportStore(temporary.newFolder())
        repeat(3) { store.write(report(it)) }
        val first = store.pending()
        val attempts = mutableListOf<CrashReport>()
        store.upload(first) { input ->
            attempts += input
            when (input.appVersion) {
                "debug-0" -> true
                "debug-1" -> false
                else -> error("offline")
            }
        }
        assertEquals(3, attempts.size)
        assertEquals(2, store.pending().size)
        store.upload { true }
        assertTrue(store.pending().isEmpty())
    }

    @Test fun handlerIncludesCausesSuppressedAndMetadataThenChainsEvenIfStorageFails() {
        val store = CrashReportStore(temporary.newFolder())
        val thread = Thread.currentThread()
        val failure = IllegalStateException("outer", IllegalArgumentException("cause")).apply {
            addSuppressed(UnsupportedOperationException("suppressed"))
        }
        var chained = 0
        val previous = Thread.UncaughtExceptionHandler { actualThread, actualFailure ->
            assertSame(thread, actualThread); assertSame(failure, actualFailure); chained++
        }
        CrashExceptionHandler(store, previous, "1.0 debug commit abc", "Model; Android 15") {
            Instant.parse("2026-10-24T12:00:00Z")
        }.uncaughtException(thread, failure)
        val saved = AccountJson.format.decodeFromString<CrashReport>(store.pending().single().readText())
        assertEquals("2026-10-24T12:00:00Z", saved.reportedAt)
        for (text in listOf(thread.name, "commit abc", "Android 15", "outer", "Caused by:", "cause", "Suppressed:", "suppressed"))
            assertContains(saved.body, text)
        val notDirectory = temporary.newFile()
        CrashExceptionHandler(CrashReportStore(notDirectory), previous, "debug", "device").uncaughtException(thread, failure)
        assertEquals(2, chained)
    }

    @Test fun authenticatedUploadDropsDailyCapRejectionsButKeepsServerFailures() = runBlocking {
        val store = CrashReportStore(temporary.newFolder())
        store.write(report())
        var status = HttpStatusCode.ServiceUnavailable
        val token = "a".repeat(43)
        val engine = MockEngine { request ->
            assertEquals("Bearer $token", request.headers["Authorization"])
            assertEquals("/debug/crash-reports", request.url.encodedPath)
            respond("", status)
        }
        AccountApi("https://example.invalid", HttpClient(engine)).use { api ->
            store.upload { api.uploadCrashReport(token, it) }
            assertEquals(1, store.pending().size)
            status = HttpStatusCode.TooManyRequests
            store.upload { api.uploadCrashReport(token, it) }
            assertTrue(store.pending().isEmpty())
        }
    }
}
