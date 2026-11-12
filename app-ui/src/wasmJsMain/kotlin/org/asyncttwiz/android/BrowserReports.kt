package org.asyncttwiz.android

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import org.asyncttwiz.appui.AppBuild
import org.asyncttwiz.appui.File
import org.asyncttwiz.appui.deviceModel
import org.asyncttwiz.appui.instantNow
import org.asyncttwiz.appui.jsOnHidden
import org.asyncttwiz.appui.jsOnUncaught
import org.asyncttwiz.appui.nanoTime
import org.asyncttwiz.appui.osVersion
import org.asyncttwiz.appui.pageScope
import org.asyncttwiz.appui.platformLogInfo
import org.asyncttwiz.appui.uncaughtFailureHandler
import org.asyncttwiz.protocol.AccountJson
import org.asyncttwiz.protocol.ActionLatency
import org.asyncttwiz.protocol.CardTapDiagnostic
import org.asyncttwiz.protocol.CrashReport
import org.asyncttwiz.protocol.LatencyBatch
import org.asyncttwiz.protocol.ReconnectEpisode

// Problem diagnostics in the browser (web client step 3b): the app log in memory (console plus the ring problem reports
// read), crashes (failures nothing caught: the page's error events and the app's own queues) and slow-action, reconnect and
// tap reports, sent to the same endpoints as Android's while reports are on. Nothing is kept across a reload: a crash
// doesn't end the page, so its report goes at once (or when the session is known). Stutter reports (DebugJank) are
// left out on the web for now: the browser gives no cheap frame timing that matches Android's JankStats.

/** Starts the page's diagnostics; the web entry point calls it once, first thing (after the platform is installed). */
fun startBrowserDiagnostics() {
    uncaughtFailureHandler = { where, failure ->
        try { AppLog.error("crash", "Uncaught exception in $where", failure) } catch (_: Throwable) {}
        DebugCrashes.crashed("Where: $where\n\n${failure.stackTraceToString()}")
    }
    jsOnUncaught { text ->
        try { AppLog.error("crash", "Uncaught error: $text") } catch (_: Throwable) {}
        DebugCrashes.crashed("Where: page\n\n$text")
    }
    AppLog.info("app", "Started. ${AppBuild.VERSION_NAME} (${AppBuild.VERSION_CODE}) ${AppBuild.BUILD_TYPE}; ${deviceModel()}; ${osVersion()}")
    AutomatumReports.install()
    FaultReports.install()
    Diagnostics.start(BrowserReportHooks)
}

/** Reports' platform side in the browser: nothing on disk; a hidden tab counts as the app going to the background. */
object BrowserReportHooks : ReportHooks {
    override val directory: File = File()
    private var started = false
    override fun start() {
        if (started) return
        started = true
        jsOnHidden { DebugLatency.appBackgrounded() }
    }
}

private fun reportVersion() = "${AppBuild.VERSION_NAME} (${AppBuild.VERSION_CODE}) ${AppBuild.BUILD_TYPE}; commit ${AppBuild.GIT_COMMIT}"
private fun reportDevice() = "${deviceModel()}; ${osVersion()}"

actual object DebugCrashes {
    private const val MAX_QUEUED = 10
    private var installed = false
    private var token: String? = null
    private val queued = ArrayDeque<CrashReport>()
    private var sending = false

    actual fun sessionToken(): String? = token

    actual fun install(hooks: ReportHooks) {
        if (!Diagnostics.enabled || installed) return
        installed = true
        DebugLatency.install()
        hooks.start()
        token?.let(::authenticated)
    }

    /** A failure nothing caught: reported while reports are on, at most [MAX_QUEUED] waiting. */
    internal fun crashed(detail: String) {
        try {
            if (!Diagnostics.enabled || !installed) return
            val timestamp = instantNow().toString()
            // Exception messages could carry an email address or a token; reports never do (LOB-090).
            val report = CrashReport(timestamp, reportVersion(), reportDevice(),
                redactLog("Time: $timestamp\nApp: ${reportVersion()}\nDevice: ${reportDevice()}\n$detail"),
                automatum = AutomatumReports.text())
            if (queued.size >= MAX_QUEUED) queued.removeFirst()
            queued.addLast(report)
            send()
        } catch (_: Throwable) { /* Diagnostics never add a failure of their own. */ }
    }

    actual fun discard() {
        queued.clear()
        DebugLatency.discard()
        DebugJank.discard()
    }

    actual fun authenticated(token: String?) {
        this.token = token
        if (!Diagnostics.enabled) return
        DebugJank.authenticated(token)
        DebugLatency.authenticated(token)
        send()
    }

    /** One attempt per report; quiet. */
    private fun send() {
        val credential = token ?: return
        if (sending || queued.isEmpty() || !installed) return
        sending = true
        pageScope.launch {
            try {
                AccountApi(AppBuild.ACCOUNT_ORIGIN).use { api ->
                    while (queued.isNotEmpty() && Diagnostics.enabled) {
                        val report = queued.removeFirst()
                        runCatching { api.uploadCrashReport(credential, report) }
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) { /* No UI, logs, or immediate retry. */
            } finally { sending = false }
        }
    }
}

/** Slow-action, reconnect and tap reports as on Android (DebugLatency.android.kt), kept in memory only. */
actual object DebugLatency {
    private var recorder: LatencyRecorder? = null
    private var token: String? = null
    private val buffer = LatencyBuffer(true)
    private val reconnects = ReconnectBuffer(true)
    private val taps = CardTapBuffer()
    private val tapRecorder = CardTapRecorder { taps.add(it); wake.trySend(Unit) }
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private var upload = false
    private var forced = false
    private var started = false

    actual fun begin(gameId: String, action: String, nodes: Int): LatencyRecorder.Trace? {
        if (!Diagnostics.enabled) return null
        DebugJank.action(action)
        return recorder?.begin(gameId, action, nodes)
    }
    actual fun result(requestId: String) { if (Diagnostics.enabled) recorder?.result(requestId) }
    actual fun drawn() { if (Diagnostics.enabled) recorder?.drawn() }
    actual fun tap(gameId: String, event: String, popup: Boolean, cardId: String?) {
        if (Diagnostics.enabled && recorder != null) tapRecorder.gesture(gameId, event, popup, DebugJank.catchingUp(), DebugJank.treeInFlight(), cardId)
    }
    actual fun tapAnimation(gameId: String, rotatingCardIds: Set<String>) {
        if (Diagnostics.enabled && recorder != null) tapRecorder.animation(gameId, rotatingCardIds)
    }
    actual fun tapFrame(gameId: String, progress: Float, rate: Float) {
        if (Diagnostics.enabled && recorder != null) tapRecorder.frame(gameId, progress, rate, DebugJank.catchingUp(), DebugJank.treeInFlight())
    }
    actual fun reconnect(value: ReconnectEpisode) {
        if (!Diagnostics.enabled || recorder == null) return
        platformLogInfo("AtwReconnect", "game=${value.gameId} episode=${value.id} started=${value.startedAt} cause=${value.cause} " +
            "step=${value.steps.lastOrNull()} durationMs=${value.durationMs} complete=${value.complete}")
        if (reconnects.record(value)) wake.trySend(Unit)
    }

    internal fun install() {
        if (!Diagnostics.enabled || recorder != null) return
        fun report(actions: List<ActionLatency>, episodes: List<ReconnectEpisode>, gestures: List<CardTapDiagnostic>) =
            CrashReport(instantNow().toString(), reportVersion(), reportDevice(),
                AccountJson.format.encodeToString(LatencyBatch(actions, episodes, gestures)), kind = "latency")
        recorder = LatencyRecorder(true, log = { entry ->
            platformLogInfo("AtwLatency", "action=${entry.actionType} game=${entry.gameId} nodes=${entry.historyNodeCount} " +
                "predicted=${entry.predicted} submitted=${entry.submitted} localApplyMs=${entry.localApplyMs} " +
                "firstFrameMs=${entry.firstFrameMs} serverResultMs=${entry.serverResultMs}" +
                entry.parts.entries.joinToString("") { (label, ms) -> " $label=$ms" })
        }, record = { id, entry -> if (buffer.record(id, entry)) wake.trySend(Unit) })
        pageScope.launch {
            for (signal in wake) {
                try {
                    if (!Diagnostics.enabled) { clear(); continue }
                    val shouldUpload = upload.also { upload = false }
                    val force = forced.also { forced = false }
                    val credential = token
                    if (shouldUpload && credential != null) {
                        val batch = buffer.batch(nanoTime() / 1_000_000, force)
                        val episodes = reconnects.batch()
                        val gestures = taps.snapshot().take(40)
                        if (batch.isNotEmpty() || episodes.isNotEmpty() || gestures.isNotEmpty()) AccountApi(AppBuild.ACCOUNT_ORIGIN).use { api ->
                            if (api.uploadCrashReport(credential, report(batch.map { it.value }, episodes, gestures))) {
                                buffer.acknowledge(batch); reconnects.acknowledge(episodes); taps.acknowledge(gestures)
                            }
                        }
                    }
                } catch (cancelled: CancellationException) { throw cancelled
                } catch (_: Exception) { /* Keep the bounded queue for the next scheduled attempt. */ }
            }
        }
        pageScope.launch { while (isActive) { delay(1000); tapRecorder.expire() } }
        pageScope.launch { while (isActive) { delay(60_000); requestUpload(false) } }
    }

    actual fun appBackgrounded() = requestUpload(true)

    actual fun discard() { clear(); wake.trySend(Unit) }
    private fun clear() {
        buffer.acknowledge(buffer.snapshot()); reconnects.acknowledge(reconnects.snapshot()); taps.acknowledge(taps.snapshot())
    }

    actual fun authenticated(value: String?) {
        if (!Diagnostics.enabled) return
        token = value
        if (value != null && !started) { started = true; requestUpload(true) }
    }
    private fun requestUpload(force: Boolean) {
        if (!Diagnostics.enabled) return
        upload = true
        if (force) forced = true
        wake.trySend(Unit)
    }
}

/** Stutter reports are off on the web (see above): nothing is recorded. */
actual object DebugJank {
    actual fun catchUp(game: String, running: Boolean) {}
    actual fun catchingUp(): Boolean = false
    actual fun treeInFlight(): Boolean = false
    actual fun animation(owner: String, running: Boolean) {}
    actual fun state(vararg values: Pair<String, String>) {}
    actual fun action(name: String) {}
    actual fun beginWork(kind: String): Long = 0
    actual fun endWork(kind: String, start: Long) {}
    actual fun track(owner: Any, tracking: JankTracking) {}
    actual fun untrack(owner: Any) {}
    actual fun frame(startNanos: Long, durationNanos: Long, isJank: Boolean, refreshRate: Float, states: Map<String, String>) {}
    actual fun authenticated(value: String?) {}
    actual fun discard() {}
}
