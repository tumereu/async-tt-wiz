package org.asyncttwiz.android

/**
 * WEB-057 (owner, 2026-11-15: "Web FPS is still stuttery"): which of the browser's display refreshes were frames the page
 * drew, and how long each picture stayed on screen, so the frame times in the app log ([FrameTimeLog]) say how smoothly the
 * page moved, not how often the browser refreshed an unchanged page.
 *
 * The platform calls [refresh] once per display refresh, with what the page did in it: the time its drawing took ([drawMs],
 * negative when it drew nothing: nothing had changed) and its other work seen since ([otherMs]: the engine's messages, input
 * handling). A picture's time is from the refresh that drew it to the next one that drew:
 *  - drawn again at the very next refresh: a frame, however long that refresh took (a slow one is a stutter);
 *  - not drawn at the next refreshes while something was animating or being dragged: one frame as long as the picture
 *    stood still (the page missed refreshes it should have drawn), up to [MAX_STILL_MS] (longer, the animation had stopped);
 *  - otherwise the page went idle after it (nothing changed): not a frame, unless its own drawing took longer than a
 *    refresh, which then counts as one frame of the refresh it drew in.
 * So the pauses of a page with nothing to draw (and a browser drawing nothing for seconds) are never slow frames.
 *
 * A slow frame's group names what was going on ([refresh]'s group) and why it was slow: "drawing" (the page's own drawing
 * took the time), "page work" (other work of the page between frames), "missed refreshes" (the page drew nothing new for
 * refreshes while animating) or "browser" (none of the page's own work that is seen: the browser's compositing and the
 * graphics card); its labels add the milliseconds.
 */
class DrawnFrames(private val log: FrameTimeLog) {
    private var lastAt = -1.0
    private var lastDrawMs = 0.0
    private var lastAnimating = false
    private var lastGroup = ""
    private var lastLabels = ""
    /** The refresh right after the last drawn one (its length), and the refreshes and other work since that drawn one. */
    private var firstMs = 0.0
    private var refreshes = 0
    private var otherMs = 0.0

    /**
     * A display refresh started at [atMs] and lasting [intervalMs] (to the next one): the page drew in it for [drawMs]
     * (negative: it drew nothing), did [otherMs] of other work during it, [animating] says whether something moved or was
     * being dragged, and [group] / [labels] say what was on screen ([FrameTimeLog.frame]'s).
     */
    fun refresh(atMs: Double, intervalMs: Double, drawMs: Double, otherMs: Double, animating: Boolean, group: String, labels: String) {
        if (intervalMs.isNaN() || intervalMs <= 0.0) return
        if (drawMs >= 0) {
            if (lastAt >= 0) when {
                refreshes == 1 -> emit(atMs - lastAt, if (this.otherMs >= log.periodMs / 2 && lastDrawMs < log.periodMs) "page work" else null)
                lastAnimating && atMs - lastAt <= MAX_STILL_MS -> emit(atMs - lastAt, "missed refreshes")
                lastDrawMs > log.periodMs -> emit(firstMs, null)
            }
            lastAt = atMs; lastDrawMs = drawMs; lastAnimating = animating; lastGroup = group; lastLabels = labels
            firstMs = intervalMs; refreshes = 1; this.otherMs = otherMs.coerceAtLeast(0.0)
        } else if (lastAt >= 0) {
            refreshes++
            this.otherMs += otherMs.coerceAtLeast(0.0)
        }
    }

    /** The page wasn't shown for a while (a hidden tab): the next drawn refresh starts afresh. */
    fun reset() { lastAt = -1.0; refreshes = 0; otherMs = 0.0 }

    private fun emit(ms: Double, cause: String?) {
        if (ms <= log.periodMs * FrameTimeLog.SLOW_FACTOR) { log.frame(ms, lastGroup, lastLabels, lastDrawMs); return }
        val why = cause ?: if (lastDrawMs >= log.periodMs) "drawing" else "browser"
        log.frame(ms, "$lastGroup; $why", "$lastLabels draw_ms=${tenths(lastDrawMs)} other_ms=${tenths(otherMs)} refreshes=$refreshes",
            lastDrawMs)
    }

    private fun tenths(value: Double): String {
        val t = kotlin.math.round(value * 10).toLong()
        return "${t / 10}.${t % 10}"
    }

    companion object {
        /** A picture standing still longer than this while "animating" means the animation had stopped, not a stutter. */
        const val MAX_STILL_MS = 250.0
    }
}
