package org.asyncttwiz.android

import android.app.Application
import android.os.Build
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import org.asyncttwiz.protocol.AccountJson
import org.asyncttwiz.protocol.CrashReport
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
    private val enabled: () -> Boolean = { true }, private val now: () -> Instant = Instant::now) : Thread.UncaughtExceptionHandler {
    override fun uncaughtException(thread: Thread, failure: Throwable) {
        try {
            if (!enabled()) return
            val timestamp = now().toString()
            // Exception messages could carry an email address or a token; reports never do (LOB-090).
            store.write(CrashReport(timestamp, version, device, redactLog(
                "Time: $timestamp\nThread: ${thread.name} (${thread.id})\nApp: $version\nDevice: $device\n\n${failure.stackTraceToString()}")))
        } catch (_: Throwable) {
            // Diagnostic failures (including a full disk) must not replace Android's crash handling.
        } finally { previous?.uncaughtException(thread, failure) }
    }
}

class TabletopApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppLog.install(File(filesDir, "app-log.txt"))
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, failure ->
            // Logging must never replace Android's crash handling.
            try { AppLog.error("crash", "Uncaught exception on thread ${thread.name}", failure) } catch (_: Throwable) {
            } finally { previous?.uncaughtException(thread, failure) }
        }
        AppLog.info("app", "Started. ${appVersionText()}; ${Build.MANUFACTURER} ${Build.MODEL}; API ${Build.VERSION.SDK_INT}")
        Diagnostics.start(this)
        AndroidTokenCatalog.source(this)
    }
}

/** Process lifetime, not Activity lifetime: rotations or repeated sign-ins cannot retry a batch. */
internal object DebugCrashes {
    private var store: CrashReportStore? = null
    private var pending: List<File> = emptyList()
    private val started = AtomicBoolean()
    @Volatile private var token: String? = null

    @Synchronized fun install(application: Application) {
        if (!Diagnostics.enabled || store != null) return
        val reports = CrashReportStore(File(application.filesDir, "debug-crashes"))
        store = reports
        pending = reports.pending()
        val version = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) ${BuildConfig.BUILD_TYPE}; commit ${BuildConfig.GIT_COMMIT}"
        val device = "${Build.MANUFACTURER} ${Build.MODEL}; Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
        Thread.setDefaultUncaughtExceptionHandler(CrashExceptionHandler(reports,
            Thread.getDefaultUncaughtExceptionHandler(), version, device, enabled = { Diagnostics.enabled }))
        DebugLatency.install(application, reports, version, device)
        DebugJank.install(application, reports, version, device)
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
                AccountApi(BuildConfig.ACCOUNT_ORIGIN).use { api -> reports.upload(files) { api.uploadCrashReport(token, it) } }
            } catch (_: Exception) { /* No UI, logs, or immediate retry. */ }
        }
    }
}
