package org.asyncttwiz.android

import org.asyncttwiz.protocol.ReconnectEpisode
import org.asyncttwiz.protocol.ReconnectStep
import java.time.Instant
import java.util.UUID

/** Record at episode start too: a stalled/failed recovery must not disappear from diagnostics. */
internal class ReconnectRecorder(private val enabled: Boolean, private val now: () -> Long,
    private val timestamp: () -> String = { Instant.now().toString() },
    private val record: (ReconnectEpisode) -> Unit) {
    private data class Active(val start: Long, var last: Long, var value: ReconnectEpisode)
    private val active = linkedMapOf<String, Active>()
    fun start(gameId: String, cause: String) {
        if (!enabled) return
        if (gameId in active) { step(gameId, cause); return }
        val time = now()
        val entry = Active(time, time, ReconnectEpisode(UUID.randomUUID().toString(), gameId, timestamp(), cause.take(160)))
        active[gameId] = entry
        while (active.size > 200) active.remove(active.keys.first())
        record(entry.value)
    }
    fun step(gameId: String, stage: String, complete: Boolean = false) {
        if (!enabled) return
        val entry = active[gameId] ?: return
        val time = now()
        entry.value = entry.value.copy(steps = (entry.value.steps + ReconnectStep(stage.take(160),
            (time - entry.start).coerceAtLeast(0), (time - entry.last).coerceAtLeast(0))).takeLast(32),
            durationMs = (time - entry.start).coerceAtLeast(0), complete = complete)
        entry.last = time
        record(entry.value)
        if (complete) active.remove(gameId)
    }
    fun stepAll(stage: String) { active.keys.toList().forEach { step(it, stage) } }
    fun clear() { active.clear() }
}

/** Version-aware acknowledgement retains any progress made during an in-flight upload. */
internal class ReconnectBuffer(private val enabled: Boolean, private val capacity: Int = 200) {
    private val entries = linkedMapOf<String, ReconnectEpisode>()
    @Synchronized fun record(value: ReconnectEpisode): Boolean {
        if (!enabled) return false
        entries[value.id] = value
        while (entries.size > capacity) entries.remove(entries.keys.first())
        return true
    }
    @Synchronized fun snapshot() = entries.values.toList()
    @Synchronized fun restore(values: List<ReconnectEpisode>) {
        val current = snapshot(); entries.clear()
        (values + current).forEach(::record)
    }
    @Synchronized fun batch() = snapshot().take(2) // <=32 bounded steps each; share the 64KiB latency envelope.
    @Synchronized fun acknowledge(batch: List<ReconnectEpisode>) {
        batch.forEach { if (entries[it.id] == it) entries.remove(it.id) }
    }
}
