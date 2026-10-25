package org.asyncttwiz.android

import org.asyncttwiz.protocol.JankEpisode
import org.junit.Test
import kotlin.test.*

class StutterDetectorTest {
    private val episodes = mutableListOf<JankEpisode>()
    private val detector = StutterDetector({ "start:$it" }, episodes::add)
    private fun frame(at: Long, ms: Double = 8.0, jank: Boolean = false, local: Boolean = false, tree: Boolean = false) =
        detector.frame(RenderFrame(at * 1_000_000, (ms * 1_000_000).toLong(), jank, 120f,
            mapOf("screen" to "table", "popup" to "library"), listOf("Actions"), local, tree))

    @Test fun smoothHighRefreshFramesAndTwoJanksDoNotTrigger() {
        repeat(120) { frame(it * 8L) }
        frame(1000, 12.0, true); frame(1400, 12.0, true); frame(2101, 12.0, true)
        detector.flush(4_000_000_000)
        assertTrue(episodes.isEmpty())
    }
    @Test fun slidingWindowDetectsThreeJanksAcrossSecondBoundariesAndKeepsStatsAndContext() {
        frame(800, 8.0); frame(900, 15.0, true, local = true); frame(1100, 18.0, true, tree = true)
        frame(1500, 20.0, true); frame(1600, 8.0)
        detector.flush(2_600_000_000)
        val e = episodes.single()
        assertEquals("start:800000000", e.startedAt)
        assertEquals(808.0, e.durationMs)
        assertEquals(5, e.frameCount); assertEquals(3, e.jankyCount)
        assertEquals(15.0, e.p50FrameMs); assertEquals(20.0, e.p95FrameMs); assertEquals(20.0, e.worstFrameMs)
        assertEquals(120f, e.refreshRate); assertEquals("library", e.states["popup"])
        assertEquals(listOf("Actions"), e.recentActions)
        assertTrue(e.localApplyInFlight); assertTrue(e.treeWorkInFlight); assertFalse(e.frozen)
    }
    @Test fun thresholdsAreStrictAndSevereFramesTriggerEvenWithoutJankFlag() {
        frame(0, 50.0); detector.flush(2_000_000_000); assertTrue(episodes.isEmpty())
        frame(3000, 50.1); detector.flush(5_000_000_000)
        assertEquals(50.1, episodes.single().worstFrameMs)
        frame(6000, 700.0); assertEquals(1, episodes.size)
        detector.flush(8_000_000_000); assertFalse(episodes.last().frozen)
        frame(9000, 700.1)
        assertEquals(3, episodes.size); assertTrue(episodes.last().frozen)
    }
    @Test fun sustainedStutterIsBoundedAndQuietPeriodSeparatesEpisodes() {
        repeat(20) { frame(it * 300L, 60.0, true) }
        detector.flush(8_000_000_000)
        assertEquals(2, episodes.size)
        assertTrue(episodes.all { it.durationMs <= 5200 })
        frame(9000, 60.0); detector.flush(11_000_000_000)
        assertEquals(3, episodes.size)
    }
    @Test fun bufferWaitsForFiveOrFrozenBoundsRestorationAndAcknowledgesOnlyBatch() {
        frame(0, 701.0)
        val sample = episodes.single()
        val buffer = JankBuffer(6)
        repeat(4) { buffer.add(sample.copy(frozen = false)) }
        assertTrue(buffer.batch().isEmpty())
        buffer.add(sample.copy(frozen = false)); assertEquals(5, buffer.batch().size)
        buffer.acknowledge(buffer.batch()); assertTrue(buffer.snapshot().isEmpty())
        buffer.add(sample); assertEquals(1, buffer.batch().size)
        buffer.restore(List(10) { sample }); assertEquals(6, buffer.snapshot().size)
    }
}
