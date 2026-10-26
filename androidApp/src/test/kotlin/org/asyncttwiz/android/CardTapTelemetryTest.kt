package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.*

class CardTapTelemetryTest {
    @Test fun recordsEveryOutcomeEvenFastTapsAndMeasuresFirstAnimationFrameAndMissedVsyncs() {
        var time = 0L
        val samples = mutableListOf<CardTapDiagnostic>()
        val recorder = CardTapRecorder({ time }, samples::add)
        recorder.gesture("game", "recognized", popup = true, catchUp = true)
        recorder.gesture("game", "popupClosing")
        recorder.gesture("game", "rotated", cardId = "card")
        recorder.animation("game", setOf("card"))
        time = 20_000_000; recorder.frame("game", .1f, 60f, false, false)
        time = 70_000_000; recorder.frame("game", .6f, 60f, false, true)
        time = 120_000_000; recorder.frame("game", 1f, 60f, false, false)
        val sample = samples.single()
        assertEquals("rotated", sample.outcome)
        assertEquals(20.0, sample.firstAnimationFrameMs)
        assertEquals(4, sample.droppedFrames)
        assertTrue(sample.popupOpen && sample.popupClosing && sample.catchUpInFlight && sample.treeWorkInFlight)
        recorder.gesture("game", "recognized"); recorder.gesture("game", "dropped:predictionRefused")
        recorder.gesture("game", "nearMiss:deadline")
        assertEquals(listOf("rotated", "dropped", "nearMiss"), samples.map { it.outcome })
        assertEquals("predictionRefused", samples[1].reason)
        val batch = LatencyBatch(emptyList(), taps = samples)
        assertEquals(batch, AccountJson.format.decodeFromString<LatencyBatch>(AccountJson.format.encodeToString(LatencyBatch.serializer(), batch)))
    }
    @Test fun combinedMoveAndRotationMeasuresOnlyTheRotatedCardAndTheWholeTransition() {
        var time = 0L
        val samples = mutableListOf<CardTapDiagnostic>()
        val recorder = CardTapRecorder({ time }, samples::add)
        val base = TableFixture.scene(TableFixture.play(board = 2))
        val rotated = base.items.first { it.cardId == "a/BATTLEFIELD/0" }
        val moved = base.items.first { it.cardId == "b/BATTLEFIELD/0" }
        recorder.gesture("game", "recognized")
        recorder.gesture("game", "rotated", cardId = rotated.cardId)
        recorder.animation("game", setOf(moved.cardId!!))
        time = 10_000_000; recorder.frame("game", 1f, 60f, false, false)
        assertTrue(samples.isEmpty(), "An unrelated animation cannot consume the rotation")

        val target = base.copy(items = base.items.map { when (it.id) {
            rotated.id -> it.copy(tapped = true)
            moved.id -> it.copy(bounds = it.bounds.copy(x = it.bounds.x + 42f))
            else -> it
        } })
        val animation = HistorySceneAnimation(base)
        assertTrue(animation.retarget(target, 1f))
        assertEquals(350, animation.durationMillis)
        assertEquals(setOf(rotated.cardId), animation.rotatingCardIds)
        recorder.animation("game", animation.rotatingCardIds)
        time = 20_000_000; recorder.frame("game", .1f, 60f, false, false)
        time = 120_000_000; recorder.frame("game", .6f, 60f, false, false)
        assertTrue(samples.isEmpty(), "Do not stop at the old fixed 90 ms limit")
        time = 170_000_000; recorder.frame("game", .9f, 60f, false, true)
        time = 186_666_667; recorder.frame("game", 1f, 60f, false, false)
        val sample = samples.single()
        assertEquals("predictedAndQueued", sample.reason)
        assertEquals(20.0, sample.firstAnimationFrameMs)
        assertEquals(7, sample.droppedFrames)
        assertTrue(sample.treeWorkInFlight)
    }
    @Test fun absentAnimationAndBoundedQueueCannotLeaveUnreportedOrUnboundedCaptures() {
        var time = 0L
        val buffer = CardTapBuffer(2)
        val recorder = CardTapRecorder({ time }, buffer::add)
        repeat(40) { recorder.gesture("game", "recognized"); recorder.gesture("game", "rotated") }
        time = 3_000_000_000; recorder.expire()
        assertEquals(2, buffer.snapshot().size)
        assertTrue(buffer.snapshot().all { it.reason == "animationNotObserved" })
        buffer.acknowledge(buffer.snapshot()); assertTrue(buffer.snapshot().isEmpty())
    }
}
