package org.asyncttwiz.android

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import org.asyncttwiz.protocol.*
import kotlinx.serialization.encodeToString
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.*

class LatencyTelemetryTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun sample(n: Int = 0, apply: Double = 21.0, frame: Double? = null, predicted: Boolean = true) =
        ActionLatency("2026-10-24T12:00:00Z", "game", "action-$n", n, predicted, apply, frame)

    @Test fun onlySlowOrUnpredictedSubmittedActionsAreBufferedAndOldestIsDropped() {
        val buffer = LatencyBuffer(true)
        buffer.record("fast", sample(apply = 20.0, frame = 20.0))
        buffer.record("refused", sample(predicted = false).copy(submitted = false))
        assertTrue(buffer.snapshot().isEmpty())
        buffer.record("frame", sample(apply = 1.0, frame = 20.001))
        buffer.record("server", sample(predicted = false))
        assertEquals(2, buffer.snapshot().size)
        repeat(201) { buffer.record("$it", sample(it)) }
        assertEquals((1..200).map { "$it" }, buffer.snapshot().map { it.id })
    }

    @Test fun batchesAreRateLimitedEvenOnFailureButBackgroundAndNextStartMayFlush() {
        val buffer = LatencyBuffer(true)
        repeat(200) { buffer.record("$it", sample(it)) }
        val first = buffer.batch(0)
        assertEquals(100, first.size)
        assertTrue(buffer.batch(59_999).isEmpty()) // A failed upload retains entries, without an immediate retry.
        assertEquals(first, buffer.batch(60_000))
        buffer.acknowledge(first)
        assertTrue(buffer.batch(60_001).isEmpty())
        assertEquals(100, buffer.batch(60_002, force = true).size)
        val nextStart = LatencyBuffer(true)
        nextStart.restore(buffer.snapshot().map { it.value })
        assertEquals(100, nextStart.batch(0).size)
    }

    @Test fun inFlightAcknowledgementCannotLoseANewerFrameOrNewActions() {
        val buffer = LatencyBuffer(true)
        buffer.record("a", sample())
        val batch = buffer.batch(0)
        buffer.record("a", sample(frame = 90.0))
        buffer.record("b", sample(1))
        buffer.acknowledge(batch)
        assertEquals(2, buffer.snapshot().size)
        buffer.acknowledge(buffer.batch(60_000))
        assertTrue(buffer.snapshot().isEmpty())
    }

    @Test fun rateLimitedLatencyBatchIsDroppedWithoutRetries() = runBlocking {
        val buffer = LatencyBuffer(true)
        buffer.record("a", sample())
        var attempts = 0
        val engine = MockEngine {
            attempts++
            respond("", HttpStatusCode.TooManyRequests)
        }
        AccountApi("https://example.invalid", HttpClient(engine)).use { api ->
            val batch = buffer.batch(0)
            val report = CrashReport("2026-10-24T12:00:00Z", "debug", "device",
                AccountJson.format.encodeToString(LatencyBatch(batch.map { it.value })), "latency")
            if (api.uploadCrashReport("a".repeat(43), report)) buffer.acknowledge(batch)
            assertTrue(buffer.snapshot().isEmpty())
            assertTrue(buffer.batch(0, force = true).isEmpty())
            assertTrue(buffer.batch(60_000).isEmpty())
            assertEquals(1, attempts)
        }
    }

    @Test fun disabledRecorderAndBufferDoNothingNotEvenReadTheClock() {
        val buffer = LatencyBuffer(false)
        buffer.record("a", sample(predicted = false))
        buffer.restore(listOf(sample()))
        assertTrue(buffer.snapshot().isEmpty())
        assertTrue(buffer.batch(0, force = true).isEmpty())
        val recorder = LatencyRecorder(false, now = { error("clock accessed") },
            log = { error("logged") }, record = { _, _ -> error("buffered") })
        assertNull(recorder.begin("game", "Undo", 10))
        recorder.drawn(); recorder.result("request")
    }

    @Test fun localPublicationAndDrawAreSeparateFromServerArrivalAndEveryActionIsLogged() {
        var now = 0L
        val logs = mutableListOf<ActionLatency>()
        val buffer = LatencyBuffer(true)
        val recorder = LatencyRecorder(true, now = { now }, timestamp = { "now" },
            log = { logs += it }, record = { id, entry -> buffer.record(id, entry) })
        val play = recorder.begin("game", "Play.MoveCard", 500)!!
        play.submitted("play", true)
        now = 5_000_000; play.finish()
        assertEquals(5.0, logs.single().localApplyMs)
        assertTrue(buffer.snapshot().isEmpty())
        now = 25_000_000; recorder.drawn()
        assertEquals(25.0, buffer.snapshot().single().value.firstFrameMs)
        now = 1_000_000_000; recorder.result("play")
        assertNull(buffer.snapshot().single().value.serverResultMs)
        val undo = recorder.begin("game", "Undo", 500)!!
        undo.submitted("undo", false); undo.finish()
        assertFalse(logs.last().predicted)
        assertNull(logs.last().localApplyMs)
        now += 120_000_000; recorder.result("undo")
        assertEquals(120.0, logs.last().serverResultMs)
        assertNull(logs.last().firstFrameMs)
        assertEquals(2, buffer.snapshot().size)
    }

    @Test fun pendingBufferUsesCrashStoreAtomicPersistenceWithoutBeingUploadedAsACrash() {
        val store = CrashReportStore(temporary.newFolder())
        val batch = LatencyBatch(listOf(sample()))
        val report = CrashReport("now", "debug", "device", AccountJson.format.encodeToString(batch), "latency")
        store.bufferLatency(report)
        assertEquals(report, store.bufferedLatency())
        assertTrue(store.pending().isEmpty())
        store.bufferLatency(report.copy(body = AccountJson.format.encodeToString(LatencyBatch(listOf(sample(2))))))
        assertEquals(listOf(sample(2)), AccountJson.format.decodeFromString<LatencyBatch>(store.bufferedLatency()!!.body).actions)
        store.bufferLatency(null)
        assertNull(store.bufferedLatency())
    }
}
