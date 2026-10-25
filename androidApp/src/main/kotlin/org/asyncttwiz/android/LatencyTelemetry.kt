package org.asyncttwiz.android

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.encodeToString
import org.asyncttwiz.protocol.*
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/** Bounded, oldest-first queue. A frame/result can update a sample already awaiting upload. */
internal class LatencyBuffer(private val enabled: Boolean, private val capacity: Int = 200) {
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
        actions.takeLast(capacity).forEach { record(UUID.randomUUID().toString(), it) }
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
internal class LatencyRecorder(private val enabled: Boolean, private val now: () -> Long = System::nanoTime,
    private val timestamp: () -> String = { Instant.now().toString() },
    private val log: (ActionLatency) -> Unit, private val record: (String, ActionLatency) -> Unit) {
    private val frames = linkedMapOf<String, Trace>()
    private val results = linkedMapOf<String, Trace>()

    inner class Trace internal constructor(gameId: String, action: String, nodes: Int) {
        private val start = now()
        private val workStart = DebugJank.beginWork("apply")
        private val id = UUID.randomUUID().toString()
        private var finished = false
        private var sample = ActionLatency(timestamp(), gameId, action, nodes, predicted = false, submitted = false)
        fun submitted(requestId: String?, predicted: Boolean) {
            sample = sample.copy(predicted = predicted, submitted = true)
            if (!predicted && requestId != null) {
                results[requestId] = this
                while (results.size > 200) results.remove(results.keys.first())
            }
        }
        fun finish() {
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
        internal fun drawn() { sample = sample.copy(firstFrameMs = elapsed()); emit() }
        internal fun result() { sample = sample.copy(serverResultMs = elapsed()); emit() }
        private fun emit() { log(sample); record(id, sample) }
    }

    fun begin(gameId: String, action: String, nodes: Int): Trace? =
        if (enabled) Trace(gameId, action, nodes) else null
    fun drawn() { val pending = frames.values.toList(); frames.clear(); pending.forEach { it.drawn() } }
    fun result(requestId: String) { results.remove(requestId)?.result() }
}

/** Installed only behind the same DEBUG gate as crashes. All serialization/disk/network work is IO. */
internal object DebugLatency {
    private var recorder: LatencyRecorder? = null
    @Volatile private var token: String? = null
    private val buffer = LatencyBuffer(BuildConfig.DEBUG)
    private val reconnects = ReconnectBuffer(BuildConfig.DEBUG)
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private val upload = AtomicBoolean()
    private val forced = AtomicBoolean()
    private val started = AtomicBoolean()

    fun begin(gameId: String, action: String, nodes: Int): LatencyRecorder.Trace? {
        if (!BuildConfig.DEBUG) return null
        DebugJank.action(action)
        return recorder?.begin(gameId, action, nodes)
    }
    fun result(requestId: String) { if (BuildConfig.DEBUG) recorder?.result(requestId) }
    fun drawn() { if (BuildConfig.DEBUG) recorder?.drawn() }
    fun reconnect(value: ReconnectEpisode) {
        if (!BuildConfig.DEBUG || recorder == null) return
        Log.i("AtwReconnect", "game=${value.gameId} episode=${value.id} started=${value.startedAt} cause=${value.cause} " +
            "step=${value.steps.lastOrNull()} durationMs=${value.durationMs} complete=${value.complete}")
        if (reconnects.record(value)) wake.trySend(Unit)
    }

    fun install(application: Application, store: CrashReportStore, version: String, device: String) {
        if (!BuildConfig.DEBUG || recorder != null) return
        fun report(actions: List<ActionLatency>, episodes: List<ReconnectEpisode>) = CrashReport(Instant.now().toString(), version, device,
            AccountJson.format.encodeToString(LatencyBatch(actions, episodes)), kind = "latency")
        recorder = LatencyRecorder(true, log = { entry ->
            Log.i("AtwLatency", "action=${entry.actionType} game=${entry.gameId} nodes=${entry.historyNodeCount} " +
                "predicted=${entry.predicted} submitted=${entry.submitted} localApplyMs=${entry.localApplyMs} " +
                "firstFrameMs=${entry.firstFrameMs} serverResultMs=${entry.serverResultMs}")
        }, record = { id, entry -> if (buffer.record(id, entry)) wake.trySend(Unit) })
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            // Restore before handling any persistence/upload signals from the new process.
            runCatching { store.bufferedLatency()?.let { saved ->
                val batch = AccountJson.format.decodeFromString<LatencyBatch>(saved.body)
                buffer.restore(batch.actions); reconnects.restore(batch.reconnects)
            } }
            for (signal in wake) {
                try {
                    fun persist() {
                        val actions = buffer.snapshot().map { it.value }
                        val episodes = reconnects.snapshot()
                        store.bufferLatency(if (actions.isEmpty() && episodes.isEmpty()) null else report(actions, episodes))
                    }
                    persist()
                    val shouldUpload = upload.getAndSet(false)
                    val force = forced.getAndSet(false)
                    val credential = token
                    if (shouldUpload && credential != null) {
                        val batch = buffer.batch(System.nanoTime() / 1_000_000, force)
                        val episodes = reconnects.batch()
                        if (batch.isNotEmpty() || episodes.isNotEmpty()) AccountApi(BuildConfig.ACCOUNT_ORIGIN).use { api ->
                            if (api.uploadCrashReport(credential, report(batch.map { it.value }, episodes))) {
                                buffer.acknowledge(batch); reconnects.acknowledge(episodes); persist()
                            }
                        }
                    }
                } catch (cancelled: CancellationException) { throw cancelled
                } catch (_: Exception) { /* Keep the bounded queue for the next scheduled attempt/start. */ }
            }
        }
        scope.launch { while (isActive) { delay(60_000); requestUpload(false) } }
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            private var started = 0
            override fun onActivityStarted(activity: Activity) { started++ }
            override fun onActivityStopped(activity: Activity) {
                if (--started == 0 && !activity.isChangingConfigurations) requestUpload(true)
            }
            override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }

    fun authenticated(value: String?) {
        if (!BuildConfig.DEBUG) return
        token = value
        if (value != null && started.compareAndSet(false, true)) requestUpload(true)
    }
    private fun requestUpload(force: Boolean) {
        if (!BuildConfig.DEBUG) return
        upload.set(true)
        if (force) forced.set(true)
        wake.trySend(Unit)
    }
}
