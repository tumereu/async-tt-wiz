package org.asyncttwiz.android

import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import org.asyncttwiz.protocol.AccountJson
import org.asyncttwiz.protocol.ProblemReport

/** Client margin below the server limit for request framing. */
internal const val PROBLEM_REPORT_BYTES = ProblemReport.MAX_BYTES - 16_384

internal fun problemReport(description: String, problem: String?, log: String, logcat: String, now: Instant = Instant.now()) =
    ProblemReport(now.toString(), BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE, BuildConfig.BUILD_TYPE,
        "${Build.MANUFACTURER} ${Build.MODEL}".take(200), "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})".take(100),
        description.trim().take(ProblemReport.MAX_DESCRIPTION), problem.orEmpty().take(ProblemReport.MAX_PROBLEM), log, logcat)

/** Drops the oldest logcat lines first, then the oldest app log lines, until the encoded report fits. */
internal fun fitProblemReport(report: ProblemReport, maxBytes: Int = PROBLEM_REPORT_BYTES): ProblemReport {
    var fitted = report
    while (true) {
        val over = AccountJson.format.encodeToString(fitted).toByteArray(Charsets.UTF_8).size - maxBytes
        fitted = when {
            over <= 0 -> return fitted
            fitted.logcat.isNotEmpty() -> fitted.copy(logcat = dropOldestLines(fitted.logcat, over))
            fitted.log.isNotEmpty() -> fitted.copy(log = dropOldestLines(fitted.log, over))
            else -> return fitted
        }
    }
}

private fun dropOldestLines(text: String, bytes: Int): String {
    var index = 0
    var dropped = 0
    while (dropped < bytes && index < text.length) {
        val end = text.indexOf('\n', index).let { if (it == -1) text.length else it + 1 }
        dropped += text.substring(index, end).toByteArray(Charsets.UTF_8).size
        index = end
    }
    return text.substring(index)
}

/** The app may read its own process's logcat without extra permissions; anything else yields nothing. */
internal fun ownLogcat(lines: Int = 2_000, maxBytes: Int = 400_000): String = try {
    val process = ProcessBuilder("logcat", "-d", "-v", "threadtime", "-v", "UTC", "-t", lines.toString(),
        "--pid=${android.os.Process.myPid()}").redirectErrorStream(true).start()
    try {
        val output = java.io.ByteArrayOutputStream()
        process.inputStream.use { input ->
            val buffer = ByteArray(16_384)
            while (output.size() < maxBytes) {
                val count = input.read(buffer)
                if (count == -1) break
                output.write(buffer, 0, count)
            }
        }
        if (process.waitFor(3, TimeUnit.SECONDS) && process.exitValue() == 0) redactLog(output.toString(Charsets.UTF_8.name())) else ""
    } finally { process.destroy() }
} catch (_: Exception) { "" }

sealed interface ProblemReportState {
    data object Idle : ProblemReportState
    data object Sending : ProblemReportState
    data class Sent(val code: String) : ProblemReportState
    data class Failed(val message: String) : ProblemReportState
}

/** One report at a time; a failure is shown and the player may simply send again. */
class ProblemReportSender(private val scope: CoroutineScope,
    private val upload: suspend (ProblemReport, String?) -> String = { report, token ->
        // A large log over a slow connection needs longer than ordinary account calls.
        AccountApi(BuildConfig.ACCOUNT_ORIGIN, AccountApi.client(requestTimeout = 90_000)).use { it.sendProblemReport(report, token) }
    }) {
    var state by mutableStateOf<ProblemReportState>(ProblemReportState.Idle)
        private set

    fun send(description: String = "", problem: String? = null, token: String? = null) {
        if (state == ProblemReportState.Sending) return
        state = ProblemReportState.Sending
        AppLog.info("report", "Sending problem report")
        scope.launch {
            state = try {
                val report = withContext(Dispatchers.IO) {
                    fitProblemReport(problemReport(description, problem, AppLog.text(), ownLogcat()))
                }
                val code = upload(report, token)
                AppLog.info("report", "Problem report sent: $code")
                ProblemReportState.Sent(code)
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (failure: Exception) {
                AppLog.warn("report", "Problem report failed", failure)
                ProblemReportState.Failed(problemReportFailure(failure))
            }
        }
    }

    fun reset() { if (state != ProblemReportState.Sending) state = ProblemReportState.Idle }
}

internal fun problemReportFailure(failure: Throwable): String = when ((failure as? AccountFailure)?.code) {
    "network" -> "Couldn't reach the server. Check your connection and try again."
    "try_later" -> "Too many reports right now. Try again later."
    "not_configured" -> "Problem reports aren't available in this build."
    else -> "The report couldn't be sent. Try again."
}
