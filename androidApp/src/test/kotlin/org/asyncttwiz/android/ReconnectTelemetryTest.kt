package org.asyncttwiz.android

import kotlinx.serialization.encodeToString
import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.*

class ReconnectTelemetryTest {
    // SYS-030
    @Test fun recordsStartRetriesLifecycleAndEachRecoveryStageIncludingStalledEpisodes() {
        var now = 0L
        val values = mutableListOf<ReconnectEpisode>()
        val recorder = ReconnectRecorder(true, { now }, { "2026-10-25T00:00:00Z" }, values::add)
        recorder.start("game", "socketClose:1001:Reconnect")
        assertFalse(values.single().complete)
        now = 10; recorder.stepAll("socketConnectStart")
        now = 110; recorder.stepAll("socketConnectedHttpAuthenticated")
        now = 130; recorder.stepAll("protocolWelcome")
        now = 140; recorder.step("game", "streamResumeStart")
        now = 200; recorder.step("game", "streamAuthorized")
        now = 220; recorder.start("game", "headRequiresCatchUp")
        now = 250; recorder.stepAll("lifecycleBackground")
        now = 300; recorder.stepAll("lifecycleForeground")
        now = 400; recorder.step("game", "ready", complete = true)
        val complete = values.last()
        assertEquals(1, values.map { it.id }.distinct().size)
        assertEquals("socketClose:1001:Reconnect", complete.cause)
        assertEquals(400L, complete.durationMs)
        assertEquals(100L, complete.steps.last().durationMs)
        assertTrue(complete.complete)
        recorder.stepAll("ignored")
        assertEquals(complete, values.last())
        recorder.start("game", "socketPingTimeout")
        repeat(40) { now++; recorder.step("game", "retry") }
        assertFalse(values.last().complete)
        assertEquals(32, values.last().steps.size)
        assertNotEquals(complete.id, values.last().id)
    }

    @Test fun boundedBatchRestoresAndAcknowledgesOnlyTheUploadedVersion() {
        val buffer = ReconnectBuffer(true, 3)
        fun entry(id: String) = ReconnectEpisode(id, "game", "2026-10-25T00:00:00Z", "socketFailure")
        (1..4).forEach { buffer.record(entry("$it")) }
        assertEquals(listOf("2", "3", "4"), buffer.snapshot().map { it.id })
        val batch = buffer.batch()
        assertEquals(2, batch.size)
        val progress = entry("2").copy(durationMs = 30, complete = true)
        buffer.record(progress)
        buffer.acknowledge(batch)
        assertEquals(listOf(progress, entry("4")), buffer.snapshot())
        val restored = ReconnectBuffer(true)
        restored.restore(AccountJson.format.decodeFromString<LatencyBatch>(
            AccountJson.format.encodeToString(LatencyBatch(emptyList(), buffer.snapshot()))).reconnects)
        assertEquals(buffer.snapshot(), restored.snapshot())
        assertTrue(AccountJson.format.decodeFromString<LatencyBatch>("{\"actions\":[]}").reconnects.isEmpty())
    }

    // SYS-030
    @Test fun releaseDoesNotReadClockOrBufferAnything() {
        val recorder = ReconnectRecorder(false, { error("clock") }, record = { error("record") })
        recorder.start("game", "cause"); recorder.step("game", "ready", true); recorder.stepAll("background")
        val buffer = ReconnectBuffer(false)
        assertFalse(buffer.record(ReconnectEpisode("id", "game", "now", "cause")))
        assertTrue(buffer.batch().isEmpty())
    }
}
