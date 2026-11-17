package org.asyncttwiz.android

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// WEB-057, WEB-101, WEB-067 (owner, 2026-11-15: "Web FPS is still stuttery"): the browser's display refreshes made frames: a page with
// nothing to draw adds none, a stutter while something moves says why
class DrawnFramesTest {
    private val lines = mutableListOf<String>()
    private val log = FrameTimeLog(lines::add, windowMs = 10_000.0)
    private val frames = DrawnFrames(log)
    private var at = 0.0

    /** A refresh of [ms]: drawn in [drawMs] (negative: nothing drawn), with [otherMs] of other page work. */
    private fun refresh(ms: Double = 1000.0 / 60, drawMs: Double = 4.0, otherMs: Double = 0.0, animating: Boolean = true,
        group: String = "table animating", labels: String = "anim=table") {
        frames.refresh(at, ms, drawMs, otherMs, animating, group, labels)
        at += ms
    }

    private fun summary(): String { log.end("left"); return lines.last() }

    @Test fun idleRefreshesAndTheBrowsersLongPausesAreNoFrames() {
        log.begin("table of game g1", "")
        repeat(60) { refresh() }
        // Nothing to draw for a while; then Firefox's hidden-tab pace (2, 4 and 8 s between refreshes) with nothing drawn
        repeat(300) { refresh(drawMs = -1.0, animating = false, group = "nothing animating") }
        refresh(2000.0, drawMs = -1.0, animating = false); refresh(4000.0, drawMs = -1.0, animating = false)
        refresh(8000.0, drawMs = 0.5, animating = false, group = "nothing animating")
        refresh(drawMs = -1.0, animating = false)
        repeat(60) { refresh() }
        val text = summary()
        assertTrue("slow 0 " in text, text)
        assertTrue(Regex(" s drawn: 1[12][0-9] frames \\(60 fps").containsMatchIn(text), text)
    }

    @Test fun aSlowRefreshWhileDrawingIsASlowFrameNamingItsCause() {
        log.begin("table of game g1", "")
        repeat(130) { refresh() }
        refresh(50.0, drawMs = 40.0)                 // the page's drawing took the time
        refresh(33.4, drawMs = 3.0, otherMs = 20.0)  // the engine's messages between frames
        refresh(33.4, drawMs = 3.0)                  // neither: the browser or the graphics card
        repeat(10) { refresh() }
        val text = summary()
        assertTrue("slow 3 " in text, text)
        assertTrue("table animating; drawing 1" in text && "table animating; page work 1" in text && "table animating; browser 1" in text, text)
        assertTrue("50.0 ms at " in text && "draw_ms=40.0 other_ms=0.0 refreshes=1" in text, text)
        assertTrue(Regex("drawing [0-9.]+ ms a frame, at most 40.0 ms").containsMatchIn(text), text)
    }

    @Test fun refreshesNotDrawnWhileAnimatingMakeOneLongFrame() {
        log.begin("table of game g1", "")
        repeat(130) { refresh() }
        refresh(); refresh(drawMs = -1.0); refresh(drawMs = -1.0)
        repeat(10) { refresh() }
        val text = summary()
        assertTrue("slow 1 " in text && "table animating; missed refreshes 1" in text, text)
        assertTrue("50.0 ms at " in text && "refreshes=3" in text, text)
    }

    @Test fun aLongDrawingBeforeTheTableGoesIdleIsOneSlowFrame() {
        log.begin("table of game g1", "")
        repeat(130) { refresh() }
        // A popup opening: one drawing of 70 ms, then nothing more to draw
        refresh(83.3, drawMs = 70.0, animating = false, group = "nothing animating", labels = "anim=none popup=card")
        repeat(30) { refresh(drawMs = -1.0, animating = false) }
        refresh(drawMs = 2.0, animating = false)
        val text = summary()
        assertTrue("slow 1 " in text && "nothing animating; drawing 1" in text, text)
        assertTrue("83.3 ms at" in text && "popup=card" in text, text)
    }

    @Test fun anAnimationThatStoppedIsNoStutter() {
        log.begin("table of game g1", "")
        repeat(130) { refresh() }
        refresh(); repeat(40) { refresh(drawMs = -1.0) }; refresh()
        assertTrue("slow 0 " in summary(), lines.last())
    }

    @Test fun aHiddenTabStartsAfresh() {
        log.begin("table of game g1", "")
        repeat(130) { refresh() }
        frames.reset()
        at += 5000.0
        repeat(10) { refresh() }
        assertTrue("slow 0 " in summary(), lines.last())
    }
}
