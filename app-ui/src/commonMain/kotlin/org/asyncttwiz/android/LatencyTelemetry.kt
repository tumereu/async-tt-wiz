package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.asyncttwiz.appui.instantNow
import org.asyncttwiz.appui.nanoTime
import org.asyncttwiz.appui.randomUuid
import org.asyncttwiz.appui.Synchronized

/** Bounded, oldest-first queue. A frame/result can update a sample already awaiting upload. */
class LatencyBuffer(private val enabled: Boolean, private val capacity: Int = 200) {
    data class Entry(val id: String, val value: ActionLatency)
    private val entries = linkedMapOf<String, ActionLatency>()
    private var lastAttempt: Long? = null

    @Synchronized fun record(id: String, value: ActionLatency): Boolean {
        if (!enabled || (value.predicted && (value.localApplyMs ?: 0.0) <= 20 && (value.firstFrameMs ?: 0.0) <= 20) ||
            !value.submitted) return false
        entries[id] = value
        while (entries.size > capacity) entries.remove(entries.keys.first())
        return true
    }
    @Synchronized fun restore(actions: List<ActionLatency>) {
        val current = snapshot()
        entries.clear()
        actions.takeLast(capacity).forEach { record(randomUuid(), it) }
        current.forEach { record(it.id, it.value) }
    }
    @Synchronized fun snapshot(): List<Entry> = entries.map { Entry(it.key, it.value) }
    @Synchronized fun batch(nowMs: Long, force: Boolean = false): List<Entry> {
        if (!enabled || entries.isEmpty() || (!force && lastAttempt?.let { nowMs - it < 60_000 } == true)) return emptyList()
        lastAttempt = nowMs
        // At most 100 fixed-shape samples keeps the body safely inside the existing 64 KiB limit.
        return snapshot().take(100)
    }
    @Synchronized fun acknowledge(batch: List<Entry>) {
        batch.forEach { if (entries[it.id] == it.value) entries.remove(it.id) }
    }
}

/** No Android dependencies in the clock/measurement logic; no server time is called local latency. */
class LatencyRecorder(private val enabled: Boolean, private val now: () -> Long = ::nanoTime,
    private val timestamp: () -> String = { instantNow().toString() },
    private val log: (ActionLatency) -> Unit, private val record: (String, ActionLatency) -> Unit) {
    private val frames = linkedMapOf<String, Trace>()
    private val results = linkedMapOf<String, Trace>()

    inner class Trace constructor(gameId: String, action: String, nodes: Int) : LatencyTrace {
        private val start = now()
        private val workStart = DebugJank.beginWork("apply")
        private val id = randomUuid()
        private var finished = false
        private var sample = ActionLatency(timestamp(), gameId, action, nodes, predicted = false, submitted = false)
        override fun submitted(requestId: String?, predicted: Boolean) {
            sample = sample.copy(predicted = predicted, submitted = true)
            if (!predicted && requestId != null) {
                results[requestId] = this
                while (results.size > 200) results.remove(results.keys.first())
            }
        }
        override fun finish() {
            if (finished) return
            finished = true
            DebugJank.endWork("apply", workStart)
            if (sample.predicted) {
                sample = sample.copy(localApplyMs = elapsed())
                frames[id] = this
                while (frames.size > 200) frames.remove(frames.keys.first())
            }
            emit()
        }
        private fun elapsed() = ((now() - start).coerceAtLeast(0)) / 1_000_000.0
        fun drawn() { sample = sample.copy(firstFrameMs = elapsed()); emit() }
        /** An own action's parts (SYS-030, [OwnActionTiming]), in milliseconds by their labels, once they are in. */
        override fun parts(parts: Map<String, Double>) { sample = sample.copy(parts = parts); emit() }
        fun result() { sample = sample.copy(serverResultMs = elapsed()); emit() }
        private fun emit() { log(sample); record(id, sample) }
    }

    fun begin(gameId: String, action: String, nodes: Int): Trace? =
        if (enabled) Trace(gameId, action, nodes) else null
    fun drawn() { val pending = frames.values.toList(); frames.clear(); pending.forEach { it.drawn() } }
    fun result(requestId: String) { results.remove(requestId)?.result() }
}
