package org.asyncttwiz.android

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.asyncttwiz.appui.AppBuild
import org.asyncttwiz.appui.AtomicInteger
import org.asyncttwiz.appui.ioDispatcher
import org.asyncttwiz.appui.newConcurrentKeySet
import org.asyncttwiz.protocol.Fault
import org.asyncttwiz.protocol.Faults
import org.asyncttwiz.protocol.ProblemReport

/**
 * The app's side of [Faults]: a fault is written to the app log at error level and, while reports are on
 * ([Diagnostics.enabled]), sent by itself as a problem report of kind [ProblemReport.FAULT] carrying the app log and the
 * latest game's Automatum bundle ([AutomatumReports.text]), so it is found without a player having to report it.
 * Deduplicated: each [Fault.key] once per run (a fault in a draw loop logs and sends once), at most [MAX_KEYS] keys and
 * [MAX_SENT] reports per run. Sending is quiet: a failure is logged and never retried.
 */
object FaultReports {
    const val MAX_KEYS = 200
    const val MAX_SENT = 5
    private const val TAG = "fault"

    private val seen = newConcurrentKeySet<String>()
    private val sent = AtomicInteger()

    /** Installs the app's [Faults.sink]; [send] uploads one report (the default: the problem-report endpoint, off the
     * caller's thread). */
    fun install(send: (ProblemReport) -> Unit = ::upload) {
        Faults.sink = { handle(it, send) }
    }

    /** Logs [fault] and sends it unless its key was already seen this run or the caps are reached. */
    fun handle(fault: Fault, send: (ProblemReport) -> Unit) {
        if (seen.size >= MAX_KEYS || !seen.add(fault.key)) return
        AppLog.error(TAG, fault.line())
        if (!Diagnostics.enabled || sent.incrementAndGet() > MAX_SENT) return
        send(problemReport("Automatic fault report: ${fault.line()}", null, "", "").copy(kind = ProblemReport.FAULT))
    }

    /** Test hook: forgets what this run has seen and sent. */
    fun reset() { seen.clear(); sent.set(0) }

    private val scope by lazy { CoroutineScope(SupervisorJob() + ioDispatcher) }

    private fun upload(report: ProblemReport) {
        scope.launch {
            try {
                AutomatumReports.logBoardNow()
                val full = fitProblemReport(report.copy(log = AppLog.text(), automatum = AutomatumReports.textNow()))
                val code = AccountApi(AppBuild.ACCOUNT_ORIGIN, AccountApi.client(requestTimeout = 90_000))
                    .use { it.sendProblemReport(full, DebugCrashes.sessionToken()) }
                AppLog.info(TAG, "Fault report sent: $code")
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (failure: Exception) { AppLog.warn(TAG, "Fault report failed", failure) }
        }
    }
}
