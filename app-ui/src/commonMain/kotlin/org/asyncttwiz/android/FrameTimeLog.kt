package org.asyncttwiz.android

import kotlin.math.roundToInt

/**
 * Frame times for the app log, so a problem report says how smoothly the screen drew and what was on it when it didn't
 * (WEB-057; the web client's table and draft). The platform feeds it the time between drawn frames ([frame]); it keeps a
 * fixed histogram and the few worst frames, so a frame allocates nothing, and writes at most one line per [windowMs] of
 * drawing (only when that stretch had slow frames) plus one line per session ([end]).
 *
 * A frame is slow when it took more than one and a half display frames ([periodMs], estimated from the frames themselves:
 * the commonest frame time), i.e. at least one display refresh was missed. Slow frames are counted by the [frame]'s
 * group (what was animating, say), and the worst keep their full labels.
 */
class FrameTimeLog(private val log: (String) -> Unit, private val windowMs: Double = 60_000.0) {
    private val window = Stats(3)
    private val session = Stats(5)
    private var screen: String? = null
    /** The display's frame time, re-estimated every [ESTIMATE_EVERY] frames; 60 Hz until then. */
    var periodMs = 1000.0 / 60
        private set
    private var sinceEstimate = 0

    /** A session on [screen] starts ([detail]: the window and display); one still running ends first. */
    fun begin(screen: String, detail: String) {
        if (this.screen != null) end("another screen opened")
        this.screen = screen
        window.clear(); session.clear()
        log("Frame times: $screen opened; $detail")
    }

    /** A frame drawn [intervalMs] after the previous one; [group] names what was going on in a few words, [labels] in full. */
    fun frame(intervalMs: Double, group: String, labels: String) {
        if (screen == null || intervalMs <= 0.0 || intervalMs.isNaN()) return
        if (++sinceEstimate >= ESTIMATE_EVERY) { sinceEstimate = 0; session.modeMs()?.let { periodMs = it } }
        val slow = intervalMs > periodMs * SLOW_FACTOR
        window.add(intervalMs, slow, group, labels)
        session.add(intervalMs, slow, group, labels)
        if (window.drawnMs >= windowMs) closeWindow(false)
    }

    /** The stretch so far and the session so far into the log (a problem report is about to read it). */
    fun flush() {
        val current = screen ?: return
        closeWindow(true)
        if (session.frames > 0) log("Frame times: $current so far: ${session.describe(periodMs, "drawing")}")
    }

    /** The session ends ([reason]): its summary goes to the log. */
    fun end(reason: String) {
        val current = screen ?: return
        closeWindow(false)
        log("Frame times: $current closed ($reason): " + if (session.frames == 0) "no frames drawn" else session.describe(periodMs, "drawing"))
        screen = null
        window.clear(); session.clear()
    }

    private fun closeWindow(always: Boolean) {
        if (window.frames > 0 && (always || window.slow >= WINDOW_SLOW_FRAMES || window.maxMs >= LONG_FRAME_MS))
            log("Frame times: $screen, ${window.describe(periodMs, "last")}")
        window.clear()
    }

    /** Frame times of a stretch: a histogram ([BUCKETS]), slow frames by group and the [keep] worst frames with labels. */
    private class Stats(private val keep: Int) {
        val histogram = IntArray(BUCKETS)
        var frames = 0; var slow = 0; var drawnMs = 0.0; var maxMs = 0.0
        val groups = LinkedHashMap<String, Int>()
        val worstMs = DoubleArray(keep); val worstAt = DoubleArray(keep); val worstLabels = arrayOfNulls<String>(keep)
        var worstCount = 0

        fun clear() {
            histogram.fill(0); frames = 0; slow = 0; drawnMs = 0.0; maxMs = 0.0; groups.clear(); worstCount = 0
        }

        fun add(ms: Double, isSlow: Boolean, group: String, labels: String) {
            histogram[bucket(ms)]++
            frames++
            val at = drawnMs
            drawnMs += ms
            if (ms > maxMs) maxMs = ms
            if (!isSlow) return
            slow++
            if (groups.size < MAX_GROUPS || group in groups) groups[group] = (groups[group] ?: 0) + 1
            else groups[OTHER] = (groups[OTHER] ?: 0) + 1
            // The worst frames, slowest first: insertion into a small sorted array
            if (worstCount == keep && ms <= worstMs[keep - 1]) return
            var index = minOf(worstCount, keep - 1)
            while (index > 0 && worstMs[index - 1] < ms) {
                worstMs[index] = worstMs[index - 1]; worstAt[index] = worstAt[index - 1]; worstLabels[index] = worstLabels[index - 1]
                index--
            }
            worstMs[index] = ms; worstAt[index] = at; worstLabels[index] = labels
            if (worstCount < keep) worstCount++
        }

        /** The commonest frame time (a bucket's middle), or null before enough frames. */
        fun modeMs(): Double? {
            if (frames < ESTIMATE_EVERY) return null
            var best = 0
            for (i in 1 until FINE_BUCKETS) if (histogram[i] > histogram[best]) best = i
            return if (histogram[best] == 0) null else (best + 0.5) * FINE_MS
        }

        fun percentile(p: Double): Double {
            val rank = (frames * p).let { r -> if (r == r.toInt().toDouble()) r.toInt() else r.toInt() + 1 }.coerceIn(1, frames)
            var seen = 0
            for (i in histogram.indices) {
                seen += histogram[i]
                if (seen >= rank) return minOf(middle(i), maxMs)
            }
            return maxMs
        }

        fun describe(periodMs: Double, span: String): String = buildString {
            val seconds = drawnMs / 1000
            append(if (span == "last") "last ${seconds.roundToInt()} s: " else "${seconds.oneDecimal()} s drawn: ")
            append("$frames frames (${(frames / seconds).roundToInt()} fps; display ${periodMs.oneDecimal()} ms), ")
            append("p50 ${percentile(.5).oneDecimal()} p95 ${percentile(.95).oneDecimal()} p99 ${percentile(.99).oneDecimal()} ")
            append("max ${maxMs.oneDecimal()} ms; slow $slow (${(slow * 1000.0 / frames).roundToInt() / 10.0}%)")
            if (groups.isNotEmpty()) append(": ").append(groups.entries.sortedByDescending { it.value }.joinToString(", ") { "${it.key} ${it.value}" })
            if (worstCount > 0) {
                append("; worst ")
                append((0 until worstCount).joinToString(", ") { "${worstMs[it].oneDecimal()} ms at ${(worstAt[it] / 1000).oneDecimal()} s [${worstLabels[it]}]" })
            }
        }
    }

    companion object {
        /** A frame over this many display frames is slow (a refresh missed). */
        const val SLOW_FACTOR = 1.5
        /** A stretch is logged when it had this many slow frames, or one of [LONG_FRAME_MS] or more. */
        const val WINDOW_SLOW_FRAMES = 5
        const val LONG_FRAME_MS = 100.0
        private const val ESTIMATE_EVERY = 120
        private const val MAX_GROUPS = 8
        private const val OTHER = "other"
        // Half-millisecond buckets to 100 ms, ten-millisecond ones to a second, then one for the rest.
        private const val FINE_MS = 0.5
        private const val FINE_BUCKETS = 200
        private const val COARSE_MS = 10.0
        private const val COARSE_BUCKETS = 90
        private const val BUCKETS = FINE_BUCKETS + COARSE_BUCKETS + 1

        private fun bucket(ms: Double): Int = when {
            ms < FINE_BUCKETS * FINE_MS -> (ms / FINE_MS).toInt()
            ms < FINE_BUCKETS * FINE_MS + COARSE_BUCKETS * COARSE_MS -> FINE_BUCKETS + ((ms - FINE_BUCKETS * FINE_MS) / COARSE_MS).toInt()
            else -> BUCKETS - 1
        }

        private fun middle(bucket: Int): Double = when {
            bucket < FINE_BUCKETS -> (bucket + 0.5) * FINE_MS
            bucket < BUCKETS - 1 -> FINE_BUCKETS * FINE_MS + (bucket - FINE_BUCKETS + 0.5) * COARSE_MS
            else -> Double.MAX_VALUE
        }

        private fun Double.oneDecimal(): String {
            val tenths = (this * 10).roundToInt()
            return "${tenths / 10}.${tenths % 10}"
        }
    }
}
