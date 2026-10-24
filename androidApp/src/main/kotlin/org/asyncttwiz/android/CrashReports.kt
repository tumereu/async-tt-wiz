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
        .orEmpty().sortedWith(compareBy<File> { it.lastModified() }.thenBy { it.name })

    @Synchronized fun write(report: CrashReport) {
        check(directory.isDirectory || directory.mkdirs())
        pending().dropLast(9).forEach { check(it.delete()) }
        val file = File(directory, "${System.currentTimeMillis()}-${UUID.randomUUID()}.json")
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
    private val now: () -> Instant = Instant::now) : Thread.UncaughtExceptionHandler {
    override fun uncaughtException(thread: Thread, failure: Throwable) {
        try {
            val timestamp = now().toString()
            store.write(CrashReport(timestamp, version, device,
                "Time: $timestamp\nThread: ${thread.name} (${thread.id})\nApp: $version\nDevice: $device\n\n${failure.stackTraceToString()}"))
        } catch (_: Throwable) {
            // Diagnostic failures (including a full disk) must not replace Android's crash handling.
        } finally { previous?.uncaughtException(thread, failure) }
    }
}

class TabletopApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) DebugCrashes.install(this)
    }
}

/** Process lifetime, not Activity lifetime: rotations or repeated sign-ins cannot retry a batch. */
internal object DebugCrashes {
    private var store: CrashReportStore? = null
    private var pending: List<File> = emptyList()
    private val started = AtomicBoolean()

    fun install(application: Application) {
        if (!BuildConfig.DEBUG || store != null) return
        val reports = CrashReportStore(File(application.filesDir, "debug-crashes"))
        store = reports
        pending = reports.pending()
        val version = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) ${BuildConfig.BUILD_TYPE}; commit ${BuildConfig.GIT_COMMIT}"
        val device = "${Build.MANUFACTURER} ${Build.MODEL}; Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
        Thread.setDefaultUncaughtExceptionHandler(CrashExceptionHandler(reports,
            Thread.getDefaultUncaughtExceptionHandler(), version, device))
    }

    fun authenticated(token: String?) {
        if (!BuildConfig.DEBUG || token == null || store == null || !started.compareAndSet(false, true)) return
        val reports = store!!
        if (pending.isEmpty()) return
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                AccountApi(BuildConfig.ACCOUNT_ORIGIN).use { api -> reports.upload(pending) { api.uploadCrashReport(token, it) } }
            } catch (_: Exception) { /* No UI, logs, or immediate retry. */ }
        }
    }
}
