package org.asyncttwiz.android

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.asyncttwiz.appui.Instant
import org.asyncttwiz.appui.instantNow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import org.asyncttwiz.protocol.AccountJson
import org.asyncttwiz.protocol.ProblemReport
import org.asyncttwiz.appui.AppBuild
import org.asyncttwiz.appui.deviceModel
import org.asyncttwiz.appui.osVersion
import org.asyncttwiz.appui.ownPlatformLog
import org.asyncttwiz.appui.ioDispatcher

/** Client margin below the server limit for request framing. */
const val PROBLEM_REPORT_BYTES = ProblemReport.MAX_BYTES - 16_384

/** [automatum]: the latest game's Automatum log and bundle (I-D43, [AutomatumReports.text]), already within its bound. */
fun problemReport(description: String, problem: String?, log: String, logcat: String, now: Instant = instantNow(),
    automatum: String = "") =
    ProblemReport(now.toString(), AppBuild.VERSION_NAME, AppBuild.VERSION_CODE, AppBuild.BUILD_TYPE,
        deviceModel().take(200), osVersion().take(100),
        description.trim().take(ProblemReport.MAX_DESCRIPTION), problem.orEmpty().take(ProblemReport.MAX_PROBLEM), log, logcat,
        automatum.takeIf { it.length <= ProblemReport.MAX_AUTOMATUM }.orEmpty())

/** Drops the oldest logcat lines first, then the oldest app log lines, until the encoded report fits. */
fun fitProblemReport(report: ProblemReport, maxBytes: Int = PROBLEM_REPORT_BYTES): ProblemReport {
    var fitted = report
    while (true) {
        val over = AccountJson.format.encodeToString(fitted).encodeToByteArray().size - maxBytes
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
        dropped += text.substring(index, end).encodeToByteArray().size
        index = end
    }
    return text.substring(index)
}

/** The app may read its own process's logcat without extra permissions; anything else yields nothing. */
fun ownLogcat(lines: Int = 2_000, maxBytes: Int = 400_000): String = redactLog(ownPlatformLog(lines, maxBytes))

sealed interface ProblemReportState {
    data object Idle : ProblemReportState
    data object Sending : ProblemReportState
    data class Sent(val code: String) : ProblemReportState
    data class Failed(val message: String) : ProblemReportState
}

/** One report at a time; a failure is shown and the player may simply send again. */
class ProblemReportSender(private val scope: CoroutineScope,
    /** I-D43: the latest game's Automatum log and bundle. */
    private val automatum: suspend () -> String = { AutomatumReports.textNow() },
    private val upload: suspend (ProblemReport, String?) -> String = { report, token ->
        // A large log over a slow connection needs longer than ordinary account calls.
        AccountApi(AppBuild.ACCOUNT_ORIGIN, AccountApi.client(requestTimeout = 90_000)).use { it.sendProblemReport(report, token) }
    }) {
    var state by mutableStateOf<ProblemReportState>(ProblemReportState.Idle)
        private set

    fun send(description: String = "", problem: String? = null, token: String? = null) {
        if (state == ProblemReportState.Sending) return
        state = ProblemReportState.Sending
        AppLog.info("report", "Sending problem report")
        scope.launch {
            state = try {
                val report = withContext(ioDispatcher) {
                    AutomatumReports.logBoardNow()
                    fitProblemReport(problemReport(description, problem, AppLog.text(), ownLogcat(), automatum = automatum()))
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

fun problemReportFailure(failure: Throwable): String = when ((failure as? AccountFailure)?.code) {
    "network" -> "Couldn't reach the server. Check your connection and try again."
    "try_later" -> "Too many reports right now. Try again later."
    "not_configured" -> "Problem reports aren't available in this build."
    else -> "The report couldn't be sent. Try again."
}
