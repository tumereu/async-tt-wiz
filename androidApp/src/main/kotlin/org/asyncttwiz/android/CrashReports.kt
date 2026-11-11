package org.asyncttwiz.android

import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import org.asyncttwiz.protocol.AccountJson
import org.asyncttwiz.protocol.CrashReport
import org.asyncttwiz.appui.AppBuild
import org.asyncttwiz.appui.deviceModel
import org.asyncttwiz.appui.osVersion
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/** App-private pending queue. An interrupted write is never mistaken for a complete report. */
internal class CrashReportStore(private val directory: File) {
    init {
        // A killed process may leave its temporary write, but never an uploadable partial report.
        directory.listFiles()?.filter { it.isFile && it.extension == "part" }?.forEach { it.delete() }
    }
    @Synchronized fun pending(): List<File> = directory.listFiles()?.filter { it.isFile && it.extension == "json" }
        ?.filter { it.name !in setOf("latency-buffer.json", "jank-buffer.json") }
        .orEmpty().sortedWith(compareBy<File> { it.lastModified() }.thenBy { it.name })

    /** Turning reports off deletes everything not yet sent, including the slow-action and stutter queues. */
    @Synchronized fun clear() {
        directory.listFiles()?.filter { it.isFile && (it.extension == "json" || it.extension == "part") }?.forEach { it.delete() }
    }

    @Synchronized fun write(report: CrashReport) {
        check(directory.isDirectory || directory.mkdirs())
        pending().dropLast(9).forEach { check(it.delete()) }
        val file = File(directory, "${System.currentTimeMillis()}-${UUID.randomUUID()}.json")
        atomicWrite(file, report)
    }

    @Synchronized fun bufferedLatency(): CrashReport? = runCatching {
        AccountJson.format.decodeFromString<CrashReport>(File(directory, "latency-buffer.json").readText())
    }.getOrNull()

    @Synchronized fun bufferLatency(report: CrashReport?) {
        val file = File(directory, "latency-buffer.json")
        if (report == null) { file.delete(); return }
        check(directory.isDirectory || directory.mkdirs())
        atomicWrite(file, report)
    }

    @Synchronized fun bufferedJank(): CrashReport? = runCatching {
        AccountJson.format.decodeFromString<CrashReport>(File(directory, "jank-buffer.json").readText())
    }.getOrNull()

    @Synchronized fun bufferJank(report: CrashReport?) {
        val file = File(directory, "jank-buffer.json")
        if (report == null) { file.delete(); return }
        check(directory.isDirectory || directory.mkdirs())
        atomicWrite(file, report)
    }

    private fun atomicWrite(file: File, report: CrashReport) {
        val part = File(directory, "${file.name}.part")
        try {
            FileOutputStream(part).use { output ->
                output.write(AccountJson.format.encodeToString(report).toByteArray(Charsets.UTF_8))
                output.fd.sync()
            }
            check(part.renameTo(file))
        } finally { part.delete() }
    }

    /** One attempt per file per start. Only success or a permanent daily-cap rejection drops it. */
    suspend fun upload(files: List<File> = pending(), send: suspend (CrashReport) -> Boolean) {
        for (file in files) {
            try {
                val report = AccountJson.format.decodeFromString<CrashReport>(file.readText())
                if (send(report)) file.delete()
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) { /* Quiet; next process start retries. */ }
        }
    }
}

internal class CrashExceptionHandler(private val store: CrashReportStore,
    private val previous: Thread.UncaughtExceptionHandler?, private val version: String, private val device: String,
    private val enabled: () -> Boolean = { true },
    /** I-D43: the latest game's Automatum log and bundle ([AutomatumReports.text]); never throws. */
    private val automatum: () -> String = { "" },
    private val now: () -> Instant = Instant::now) : Thread.UncaughtExceptionHandler {
    override fun uncaughtException(thread: Thread, failure: Throwable) {
        try {
            if (!enabled()) return
            val timestamp = now().toString()
            // Exception messages could carry an email address or a token; reports never do (LOB-090).
            store.write(CrashReport(timestamp, version, device, redactLog(
                "Time: $timestamp\nThread: ${thread.name} (${thread.id})\nApp: $version\nDevice: $device\n\n${failure.stackTraceToString()}"),
                automatum = automatum()))
        } catch (_: Throwable) {
            // Diagnostic failures (including a full disk) must not replace Android's crash handling.
        } finally { previous?.uncaughtException(thread, failure) }
    }
}

/** Process lifetime, not Activity lifetime: rotations or repeated sign-ins cannot retry a batch. */
internal object DebugCrashes {
    private var store: CrashReportStore? = null
    private var pending: List<File> = emptyList()
    private val started = AtomicBoolean()
    @Volatile private var token: String? = null

    /** The signed-in session's token as the app last said ([authenticated]), for a problem report sent from a screen
     * that doesn't hold the account (the table's Send problem report). */
    fun sessionToken(): String? = token

    @Synchronized fun install(hooks: ReportHooks) {
        if (!Diagnostics.enabled || store != null) return
        val reports = CrashReportStore(hooks.directory)
        store = reports
        pending = reports.pending()
        val version = "${AppBuild.VERSION_NAME} (${AppBuild.VERSION_CODE}) ${AppBuild.BUILD_TYPE}; commit ${AppBuild.GIT_COMMIT}"
        val device = "${deviceModel()}; ${osVersion()}"
        Thread.setDefaultUncaughtExceptionHandler(CrashExceptionHandler(reports,
            Thread.getDefaultUncaughtExceptionHandler(), version, device, enabled = { Diagnostics.enabled },
            automatum = { AutomatumReports.text() }))
        DebugLatency.install(reports, version, device)
        DebugJank.install(reports, version, device)
        hooks.start()
        // Opting in after sign-in: start with the session the app already has.
        token?.let(::authenticated)
    }

    /** Reports turned off: queued reports are deleted on disk and in memory; nothing more is recorded or sent. */
    @Synchronized fun discard() {
        pending = emptyList()
        store?.clear()
        DebugLatency.discard()
        DebugJank.discard()
    }

    fun authenticated(token: String?) {
        this.token = token
        if (!Diagnostics.enabled) return
        DebugJank.authenticated(token)
        DebugLatency.authenticated(token)
        val reports = synchronized(this) { store }
        if (token == null || reports == null || !started.compareAndSet(false, true)) return
        val files = synchronized(this) { pending }
        if (files.isEmpty()) return
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                AccountApi(AppBuild.ACCOUNT_ORIGIN).use { api -> reports.upload(files) { api.uploadCrashReport(token, it) } }
            } catch (_: Exception) { /* No UI, logs, or immediate retry. */ }
        }
    }
}
