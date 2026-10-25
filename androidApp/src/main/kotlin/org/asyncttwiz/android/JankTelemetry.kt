package org.asyncttwiz.android

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.SystemClock
import androidx.metrics.performance.JankStats
import androidx.metrics.performance.PerformanceMetricsState
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.encodeToString
import org.asyncttwiz.protocol.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Debug only. Frame callbacks copy bounded labels; persistence, percentiles and uploads run on IO. */
internal object DebugJank {
    @Volatile private var token: String? = null
    private var installed = false
    private val frames = Channel<RenderFrame>(4096)
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private val localApply = AtomicInteger()
    private val treeWork = AtomicInteger()
    private val actions = ArrayDeque<String>()
    private val work = ArrayDeque<Triple<Long, Long, String>>()
    private val metrics = mutableMapOf<Activity, Pair<JankStats, PerformanceMetricsState.Holder>>()
    private val tags = mutableMapOf("screen" to "entry", "table_mode" to "play", "animation" to "false",
        "dragging" to "false", "popup" to "none", "history_nodes" to "0", "visible_cards" to "0")
    private val animations = mutableSetOf<String>()
    private val catchUps = mutableSetOf<String>()
    @Synchronized fun catchUp(game: String, running: Boolean) {
        if (!BuildConfig.DEBUG) return
        if (running) catchUps.add(game) else catchUps.remove(game)
    }
    @Synchronized fun catchingUp() = BuildConfig.DEBUG && catchUps.isNotEmpty()
    fun treeInFlight() = BuildConfig.DEBUG && treeWork.get() > 0
    @Synchronized fun animation(owner: String, running: Boolean) {
        if (!BuildConfig.DEBUG) return
        if (running) animations.add(owner) else animations.remove(owner)
        state("animation" to animations.isNotEmpty().toString())
    }

    @Synchronized fun state(vararg values: Pair<String, String>) {
        if (!BuildConfig.DEBUG) return
        for ((key, value) in values) {
            if (tags[key] == value) continue
            tags[key] = value.take(100)
            metrics.values.forEach { it.second.state?.putState(key, value.take(100)) }
        }
    }

    @Synchronized fun action(name: String) {
        if (!BuildConfig.DEBUG) return
        actions.addLast(name.take(100)); while (actions.size > 8) actions.removeFirst()
    }

    fun beginWork(kind: String): Long {
        if (!BuildConfig.DEBUG) return 0
        (if (kind == "tree") treeWork else localApply).incrementAndGet()
        return System.nanoTime()
    }
    @Synchronized fun endWork(kind: String, start: Long) {
        if (!BuildConfig.DEBUG) return
        (if (kind == "tree") treeWork else localApply).decrementAndGet()
        work.addLast(Triple(start, System.nanoTime(), kind)); while (work.size > 128) work.removeFirst()
    }

    fun treeExecutor(delegate: ExecutorService): ExecutorService = if (!BuildConfig.DEBUG) delegate else object : AbstractExecutorService() {
        override fun execute(command: Runnable) = delegate.execute {
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

    fun install(application: Application, store: CrashReportStore, version: String, device: String) {
        if (!BuildConfig.DEBUG || installed) return
        installed = true
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val buffer = JankBuffer()
        val preferences = application.getSharedPreferences("jank-upload-quota", 0)
        fun report(values: List<JankEpisode>) = CrashReport(Instant.now().toString(), version, device,
            AccountJson.format.encodeToString(JankBatch(values)), "jank")
        // Frame timestamps use the uptime clock, not elapsedRealtime (which includes deep sleep).
        val wallOrigin = System.currentTimeMillis() - SystemClock.uptimeMillis()
        var dirty = false
        var lastAttempt = 0L
        val detector = StutterDetector({ Instant.ofEpochMilli(wallOrigin + it / 1_000_000).toString() }) {
            buffer.add(it); dirty = true; wake.trySend(Unit)
        }
        scope.launch {
            runCatching { store.bufferedJank()?.let { buffer.restore(AccountJson.format.decodeFromString<JankBatch>(it.body).episodes) } }
            wake.trySend(Unit)
            while (isActive) {
                withTimeoutOrNull(1000) { wake.receive() }
                // One coroutine owns detector, queue, quota, disk and network. No frame object is retained.
                while (true) { val frame = frames.tryReceive().getOrNull() ?: break; detector.frame(frame) }
                detector.flush(System.nanoTime())
                if (buffer.snapshot().isNotEmpty()) {
                    try {
                        fun persist() { store.bufferJank(buffer.snapshot().takeIf { it.isNotEmpty() }?.let(::report)) }
                        if (dirty) { persist(); dirty = false }
                        val credential = token
                        val batch = buffer.batch()
                        val day = LocalDate.now(ZoneOffset.UTC).toString()
                        val used = if (preferences.getString("day", null) == day) preferences.getInt("attempts", 0) else 0
                        val now = System.nanoTime()
                        if (credential != null && batch.isNotEmpty() && used < 20 &&
                            (lastAttempt == 0L || now - lastAttempt >= 60_000_000_000L) &&
                            preferences.edit().putString("day", day).putInt("attempts", used + 1).commit()) {
                            lastAttempt = now
                            AccountApi(BuildConfig.ACCOUNT_ORIGIN).use { api ->
                                if (api.uploadCrashReport(credential, report(batch))) { buffer.acknowledge(batch); persist() }
                            }
                        }
                    } catch (cancelled: CancellationException) { throw cancelled
                    } catch (_: Exception) { /* Bounded persisted queue survives until the next attempt/start. */ }
                }
            }
        }
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                activity.window.decorView.post {
                    if (activity.isFinishing || activity.isDestroyed) return@post
                    val holder = PerformanceMetricsState.getHolderForHierarchy(activity.window.decorView)
                    val stats = JankStats.createAndTrack(activity.window) { frame ->
                        val end = frame.frameStartNanos + frame.frameDurationUiNanos
                        val context = context(frame.frameStartNanos, end)
                        frames.trySend(RenderFrame(frame.frameStartNanos, frame.frameDurationUiNanos, frame.isJank,
                            activity.window.decorView.display?.refreshRate ?: 60f,
                            frame.states.take(16).associate { it.key to it.value }, context.first, context.second, context.third))
                        wake.trySend(Unit)
                    }.apply { jankHeuristicMultiplier = 1f }
                    synchronized(this@DebugJank) {
                        metrics.remove(activity)?.first?.isTrackingEnabled = false
                        metrics[activity] = stats to holder
                        tags.forEach { (key, value) -> holder.state?.putState(key, value) }
                    }
                }
            }
            override fun onActivityPaused(activity: Activity) {
                synchronized(this@DebugJank) { metrics.remove(activity)?.first?.isTrackingEnabled = false }
            }
            override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = onActivityPaused(activity)
        })
    }

    fun authenticated(value: String?) { if (BuildConfig.DEBUG) { token = value; wake.trySend(Unit) } }
}
