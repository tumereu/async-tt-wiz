package org.asyncttwiz.android

import org.asyncttwiz.protocol.JankEpisode
import kotlin.math.ceil

internal data class RenderFrame(val startNanos: Long, val durationNanos: Long, val isJank: Boolean,
    val refreshRate: Float, val states: Map<String, String> = emptyMap(), val actions: List<String> = emptyList(),
    val localApply: Boolean = false, val treeWork: Boolean = false) {
    val end get() = startNanos + durationNanos
    val ms get() = durationNanos / 1_000_000.0
}

/** Sliding one-second trigger, coalesced until one quiet second (maximum five seconds).
 * Frozen frames flush immediately. Idle flushes use the same monotonic clock as frame starts.
 * No Android dependency, allocation-heavy sorting only when an episode actually ends. */
internal class StutterDetector(private val timestamp: (Long) -> String,
    private val record: (JankEpisode) -> Unit) {
    private val window = ArrayDeque<RenderFrame>()
    private val episode = ArrayList<RenderFrame>()
    private var lastBad = 0L
    private val second = 1_000_000_000L

    @Synchronized fun frame(frame: RenderFrame) {
        flush(frame.startNanos)
        if (episode.isNotEmpty()) {
            episode += frame
            if (frame.isJank || frame.ms > 50) lastBad = frame.end
        } else {
            while (window.isNotEmpty() && frame.startNanos - window.first().startNanos > second) window.removeFirst()
            window += frame
            if (frame.ms > 50 || window.count { it.isJank } >= 3) {
                episode.addAll(window); window.clear(); lastBad = frame.end
            }
        }
        if (frame.ms > 700 || episode.size >= 4096) emit()
    }

    @Synchronized fun flush(nowNanos: Long, force: Boolean = false) {
        if (episode.isNotEmpty() && (force || nowNanos - lastBad >= second ||
                nowNanos - episode.first().startNanos >= 5 * second)) emit()
        if (force) window.clear()
    }

    private fun emit() {
        if (episode.isEmpty()) return
        val values = episode.map { it.ms }.sorted()
        fun percentile(p: Double) = values[(ceil(values.size * p).toInt() - 1).coerceIn(values.indices)]
        val worst = episode.maxBy { it.durationNanos }
        record(JankEpisode(timestamp(episode.first().startNanos),
            (episode.last().end - episode.first().startNanos) / 1_000_000.0,
            episode.size, episode.count { it.isJank }, values.last(), percentile(.5), percentile(.95),
            worst.refreshRate, worst.states, worst.actions, episode.any { it.localApply },
            episode.any { it.treeWork }, values.last() > 700))
        episode.clear(); window.clear()
    }
}

/** Bounded across process death by its owner. Attempt quota is persisted BEFORE network IO. */
internal class JankBuffer(private val capacity: Int = 30) {
    private val episodes = ArrayDeque<JankEpisode>()
    fun restore(values: List<JankEpisode>) { values.takeLast(capacity).forEach(::add) }
    fun add(value: JankEpisode) { episodes.addLast(value); while (episodes.size > capacity) episodes.removeFirst() }
    fun snapshot() = episodes.toList()
    fun batch() = if (episodes.size >= 5 || episodes.any { it.frozen })
        episodes.sortedByDescending { it.frozen }.take(10) else emptyList()
    fun acknowledge(batch: List<JankEpisode>) { batch.forEach { episodes.remove(it) } }
}
