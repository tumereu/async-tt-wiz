package org.asyncttwiz.android

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// WEB-057, WEB-067, WEB-068: the web table's frame times in the app log
class FrameTimeLogTest {
    private val lines = mutableListOf<String>()
    private val log = FrameTimeLog(lines::add, windowMs = 10_000.0)

    private fun smooth(count: Int, ms: Double = 1000.0 / 60) = repeat(count) { log.frame(ms, "nothing animating", "anim=none") }

    @Test fun aSmoothStretchWritesNoWindowLineAndTheSessionSummarySaysItWasSmooth() {
        log.begin("table of game g1", "window 1440x900 at 1x")
        smooth(1200)
        log.end("left")
        assertEquals(listOf("Frame times: table of game g1 opened; window 1440x900 at 1x"), lines.dropLast(1))
        val summary = lines.last()
        assertTrue(summary.startsWith("Frame times: table of game g1 closed (left): 20.0 s drawn: 1200 frames (60 fps; display 16.8 ms)"), summary)
        assertTrue("p50 16.7 p95 16.7 p99 16.7 max 16.7 ms; slow 0 (0.0%)" in summary, summary)
        assertTrue("worst" !in summary, summary)
    }

    @Test fun slowFramesAreCountedByGroupAndTheWorstKeepTheirLabels() {
        log.begin("table of game g1", "")
        smooth(130)
        log.frame(50.0, "table animating", "anim=table action=Cast")
        log.frame(120.0, "popup animating", "anim=popup action=Pass")
        repeat(4) { log.frame(33.4, "table animating", "anim=table action=Cast") }
        smooth(400)
        log.flush()
        val window = lines.single { "last" in it }
        assertTrue("slow 6 (1.1%): table animating 5, popup animating 1" in window, window)
        assertTrue("max 120.0 ms" in window, window)
        assertTrue("worst 120.0 ms at 2.2 s [anim=popup action=Pass], 50.0 ms at 2.2 s [anim=table action=Cast], 33.4 ms" in window, window)
    }

    @Test fun aStretchIsLoggedOnlyWithEnoughSlowFramesOrOneLongFrame() {
        log.begin("draft of pod p1", "")
        smooth(130); repeat(4) { log.frame(40.0, "g", "l") }; smooth(600)
        assertTrue(lines.none { "last" in it }, "four slow frames are noise")
        smooth(130); log.frame(150.0, "g", "l"); smooth(600)
        assertEquals(1, lines.count { "last" in it }, "one long frame is logged")
    }

    @Test fun theDisplayRateIsLearnedSoA144HzFrameThatMissesARefreshIsSlow() {
        log.begin("table of game g1", "")
        smooth(240, 1000.0 / 144)
        assertEquals(6.75, log.periodMs)
        log.frame(13.9, "g", "missed one")
        log.flush()
        assertTrue(lines.last().contains("slow 1 "), lines.last())
        assertTrue(lines.any { it.contains("so far: ") }, "flush also writes the session so far")
    }

    @Test fun flushWritesTheCurrentStretchAndAnotherSessionEndsTheFirst() {
        log.begin("table of game g1", "")
        smooth(10)
        log.flush()
        assertTrue(lines.any { it.startsWith("Frame times: table of game g1, last 0 s: 10 frames") }, lines.joinToString("\n"))
        log.begin("draft of pod p1", "")
        assertTrue(lines.any { it.startsWith("Frame times: table of game g1 closed (another screen opened)") })
        log.end("left"); log.end("left")
        assertEquals(1, lines.count { "draft of pod p1 closed" in it })
        assertTrue(lines.last().endsWith("no frames drawn"))
    }

    @Test fun framesOutsideASessionAndNonsenseIntervalsAreIgnored() {
        smooth(10)
        log.begin("table of game g1", "")
        log.frame(0.0, "g", "l"); log.frame(-5.0, "g", "l"); log.frame(Double.NaN, "g", "l")
        log.end("left")
        assertTrue(lines.last().endsWith("no frames drawn"))
    }
}
