package org.asyncttwiz.android

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.encodeToString
import org.asyncttwiz.protocol.*
import org.asyncttwiz.appui.AppBuild
import org.asyncttwiz.appui.platformLogInfo
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean

/** Installed only behind the same [Diagnostics] gate as crashes. All serialization/disk/network work is IO. */
actual object DebugLatency {
    private var recorder: LatencyRecorder? = null
    @Volatile private var token: String? = null
    // Runtime gate: entry points check Diagnostics.enabled; the upload loop empties these when reports are off.
    private val buffer = LatencyBuffer(true)
    private val reconnects = ReconnectBuffer(true)
    private val taps = CardTapBuffer()
    private val tapRecorder = CardTapRecorder { taps.add(it); wake.trySend(Unit) }
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private val upload = AtomicBoolean()
    private val forced = AtomicBoolean()
    private val started = AtomicBoolean()

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

    /** The platform's [ReportHooks.start] calls [appBackgrounded] when the app goes to the background. */
    fun install(store: CrashReportStore, version: String, device: String) {
        if (!Diagnostics.enabled || recorder != null) return
        fun report(actions: List<ActionLatency>, episodes: List<ReconnectEpisode>, gestures: List<CardTapDiagnostic>) = CrashReport(Instant.now().toString(), version, device,
            AccountJson.format.encodeToString(LatencyBatch(actions, episodes, gestures)), kind = "latency")
        recorder = LatencyRecorder(true, log = { entry ->
            platformLogInfo("AtwLatency", "action=${entry.actionType} game=${entry.gameId} nodes=${entry.historyNodeCount} " +
                "predicted=${entry.predicted} submitted=${entry.submitted} localApplyMs=${entry.localApplyMs} " +
                "firstFrameMs=${entry.firstFrameMs} serverResultMs=${entry.serverResultMs}" +
                entry.parts.entries.joinToString("") { (label, ms) -> " $label=$ms" })
        }, record = { id, entry -> if (buffer.record(id, entry)) wake.trySend(Unit) })
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            // Restore before handling any persistence/upload signals from the new process.
            runCatching { store.bufferedLatency()?.let { saved ->
                val batch = AccountJson.format.decodeFromString<LatencyBatch>(saved.body)
                buffer.restore(batch.actions); reconnects.restore(batch.reconnects); batch.taps.forEach(taps::add)
            } }
            for (signal in wake) {
                try {
                    if (!Diagnostics.enabled) { clear(); store.bufferLatency(null); continue }
                    fun persist() {
                        val actions = buffer.snapshot().map { it.value }
                        val episodes = reconnects.snapshot()
                        val gestures = taps.snapshot()
                        store.bufferLatency(if (actions.isEmpty() && episodes.isEmpty() && gestures.isEmpty()) null else report(actions, episodes, gestures))
                    }
                    persist()
                    val shouldUpload = upload.getAndSet(false)
                    val force = forced.getAndSet(false)
                    val credential = token
                    if (shouldUpload && credential != null) {
                        val batch = buffer.batch(System.nanoTime() / 1_000_000, force)
                        val episodes = reconnects.batch()
                        val gestures = taps.snapshot().take(40)
                        if (batch.isNotEmpty() || episodes.isNotEmpty() || gestures.isNotEmpty()) AccountApi(AppBuild.ACCOUNT_ORIGIN).use { api ->
                            if (api.uploadCrashReport(credential, report(batch.map { it.value }, episodes, gestures))) {
                                buffer.acknowledge(batch); reconnects.acknowledge(episodes); taps.acknowledge(gestures); persist()
                            }
                        }
                    }
                } catch (cancelled: CancellationException) { throw cancelled
                } catch (_: Exception) { /* Keep the bounded queue for the next scheduled attempt/start. */ }
            }
        }
        scope.launch { while (isActive) { delay(1000); tapRecorder.expire() } }
        scope.launch { while (isActive) { delay(60_000); requestUpload(false) } }
    }

    /** The app went to the background (not a rotation): what waits is sent now. */
    actual fun appBackgrounded() = requestUpload(true)

    /** Reports turned off: the loop empties the queues and deletes the saved batch. */
    actual fun discard() { clear(); wake.trySend(Unit) }
    private fun clear() {
        buffer.acknowledge(buffer.snapshot()); reconnects.acknowledge(reconnects.snapshot()); taps.acknowledge(taps.snapshot())
    }

    actual fun authenticated(value: String?) {
        if (!Diagnostics.enabled) return
        token = value
        if (value != null && started.compareAndSet(false, true)) requestUpload(true)
    }
    private fun requestUpload(force: Boolean) {
        if (!Diagnostics.enabled) return
        upload.set(true)
        if (force) forced.set(true)
        wake.trySend(Unit)
    }
}
