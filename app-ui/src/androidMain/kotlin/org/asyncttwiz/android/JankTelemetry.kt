package org.asyncttwiz.android

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.encodeToString
import org.asyncttwiz.protocol.*
import org.asyncttwiz.appui.AppBuild
import org.asyncttwiz.appui.uptimeMillis
import java.time.Instant
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** A screen's frame tracking (Android: JankStats on an activity's window): [stop] ends it, [state] labels its frames. */
class JankTracking(val stop: () -> Unit, val state: (String, String) -> Unit)

/** Only while reports are on ([Diagnostics]). Frame callbacks copy bounded labels; persistence, percentiles and uploads run on IO. */
object DebugJank {
    @Volatile private var token: String? = null
    private var installed = false
    private val discarding = java.util.concurrent.atomic.AtomicBoolean()
    private val uploadQuota = JankUploadQuota()
    private val frames = Channel<RenderFrame>(4096)
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private val localApply = AtomicInteger()
    private val treeWork = AtomicInteger()
    private val actions = ArrayDeque<String>()
    private val work = ArrayDeque<Triple<Long, Long, String>>()
    private val metrics = mutableMapOf<Any, JankTracking>()
    private val tags = mutableMapOf("screen" to "entry", "table_mode" to "play", "animation" to "false",
        "dragging" to "false", "popup" to "none", "history_nodes" to "0", "visible_cards" to "0")
    private val animations = mutableSetOf<String>()
    private val catchUps = mutableSetOf<String>()
    @Synchronized fun catchUp(game: String, running: Boolean) {
        if (!Diagnostics.enabled) return
        if (running) catchUps.add(game) else catchUps.remove(game)
    }
    @Synchronized fun catchingUp() = Diagnostics.enabled && catchUps.isNotEmpty()
    fun treeInFlight() = Diagnostics.enabled && treeWork.get() > 0
    @Synchronized fun animation(owner: String, running: Boolean) {
        if (!Diagnostics.enabled) return
        if (running) animations.add(owner) else animations.remove(owner)
        state("animation" to animations.isNotEmpty().toString())
    }

    @Synchronized fun state(vararg values: Pair<String, String>) {
        if (!Diagnostics.enabled) return
        for ((key, value) in values) {
            if (tags[key] == value) continue
            tags[key] = value.take(100)
            metrics.values.forEach { it.state(key, value.take(100)) }
        }
    }

    @Synchronized fun action(name: String) {
        if (!Diagnostics.enabled) return
        actions.addLast(name.take(100)); while (actions.size > 8) actions.removeAt(0)
    }

    fun beginWork(kind: String): Long {
        if (!Diagnostics.enabled) return 0
        (if (kind == "tree") treeWork else localApply).incrementAndGet()
        return System.nanoTime()
    }
    @Synchronized fun endWork(kind: String, start: Long) {
        // Paired with beginWork, not the current switch: a change in between must not leave work counted.
        if (start == 0L) return
        (if (kind == "tree") treeWork else localApply).decrementAndGet()
        work.addLast(Triple(start, System.nanoTime(), kind)); while (work.size > 128) work.removeAt(0)
    }

    fun treeExecutor(delegate: ExecutorService): ExecutorService = object : AbstractExecutorService() {
        override fun execute(command: Runnable) = if (!Diagnostics.enabled) delegate.execute(command) else delegate.execute {
            val start = beginWork("tree")
            try { command.run() } finally { endWork("tree", start) }
        }
        override fun shutdown() = delegate.shutdown()
        override fun shutdownNow() = delegate.shutdownNow()
        override fun isShutdown() = delegate.isShutdown
        override fun isTerminated() = delegate.isTerminated
        override fun awaitTermination(timeout: Long, unit: TimeUnit) = delegate.awaitTermination(timeout, unit)
    }

    @Synchronized private fun context(start: Long, end: Long): Triple<List<String>, Boolean, Boolean> {
        fun overlaps(kind: String) = work.any { it.third == kind && it.first <= end && it.second >= start }
        return Triple(actions.toList(), localApply.get() > 0 || overlaps("apply"), treeWork.get() > 0 || overlaps("tree"))
    }

    /** The platform's [ReportHooks.start] tracks the screens' frames ([track], [frame], [untrack]). */
    fun install(store: CrashReportStore, version: String, device: String) {
        if (!Diagnostics.enabled || installed) return
        installed = true
        discarding.set(false)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val buffer = JankBuffer()
        fun report(values: List<JankEpisode>) = CrashReport(Instant.now().toString(), version, device,
            AccountJson.format.encodeToString(JankBatch(values)), "jank")
        // Frame timestamps use the uptime clock, not elapsedRealtime (which includes deep sleep).
        val wallOrigin = System.currentTimeMillis() - uptimeMillis()
        var dirty = false
        val detector = StutterDetector({ Instant.ofEpochMilli(wallOrigin + it / 1_000_000).toString() }) {
            buffer.add(it); dirty = true; wake.trySend(Unit)
        }
        scope.launch {
            runCatching { store.bufferedJank()?.let { buffer.restore(AccountJson.format.decodeFromString<JankBatch>(it.body).episodes) } }
            wake.trySend(Unit)
            while (isActive) {
                withTimeoutOrNull(1000) { wake.receive() }
                if (discarding.getAndSet(false)) {
                    while (frames.tryReceive().isSuccess) Unit
                    buffer.acknowledge(buffer.snapshot()); dirty = false
                    runCatching { store.bufferJank(null) }
                }
                if (!Diagnostics.enabled) continue
                // One coroutine owns detector, queue, quota, disk and network. No frame object is retained.
                while (true) { val frame = frames.tryReceive().getOrNull() ?: break; detector.frame(frame) }
                detector.flush(System.nanoTime())
                if (buffer.snapshot().isNotEmpty()) {
                    try {
                        fun persist() { store.bufferJank(buffer.snapshot().takeIf { it.isNotEmpty() }?.let(::report)) }
                        if (dirty) { persist(); dirty = false }
                        val credential = token
                        val batch = buffer.batch()
                        if (credential != null && batch.isNotEmpty() && uploadQuota.tryAcquire(System.nanoTime())) {
                            AccountApi(AppBuild.ACCOUNT_ORIGIN).use { api ->
                                if (api.uploadCrashReport(credential, report(batch))) { buffer.acknowledge(batch); persist() }
                            }
                        }
                    } catch (cancelled: CancellationException) { throw cancelled
                    } catch (_: Exception) { /* Bounded persisted queue survives until the next attempt/start. */ }
                }
            }
        }
    }

    /** A screen ([owner]) starts its frame tracking: an earlier one of it stops; the labels so far are set on it. */
    @Synchronized fun track(owner: Any, tracking: JankTracking) {
        metrics.remove(owner)?.stop?.invoke()
        metrics[owner] = tracking
        tags.forEach { (key, value) -> tracking.state(key, value) }
    }

    /** [owner]'s frame tracking stops. */
    @Synchronized fun untrack(owner: Any) { metrics.remove(owner)?.stop?.invoke() }

    /** A frame the platform drew (only while reports are on), with the labels ([state]) it was drawn under. */
    fun frame(startNanos: Long, durationNanos: Long, isJank: Boolean, refreshRate: Float, states: Map<String, String>) {
        val end = startNanos + durationNanos
        val context = context(startNanos, end)
        frames.trySend(RenderFrame(startNanos, durationNanos, isJank, refreshRate, states, context.first, context.second, context.third))
        wake.trySend(Unit)
    }

    fun authenticated(value: String?) { if (Diagnostics.enabled) { token = value; wake.trySend(Unit) } }
    /** Reports turned off: the loop drops queued stutters and deletes the saved batch. */
    fun discard() { discarding.set(true); wake.trySend(Unit) }
}
