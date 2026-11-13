package org.asyncttwiz.android

import org.asyncttwiz.appui.gzipBase64
import org.asyncttwiz.automatum.AutomatumEntry
import org.asyncttwiz.automatum.AutomatumLog
import org.asyncttwiz.automatum.ReproBundle
import org.asyncttwiz.protocol.CrashReport
import org.asyncttwiz.protocol.game.GameRepro
import kotlin.concurrent.Volatile
import kotlin.coroutines.resume

/**
 * I-D43: Automatum's log in the Android log (debug level) and, in problem and crash reports, the latest game's
 * Automatum log and reproduction bundle ([AutomatumLog.bundle]) as [ReproBundle.REPORT_HEADER] and gzip+base64, cut
 * down to fit [CrashReport.MAX_AUTOMATUM] ([ReproBundle.decode] reads it back).
 */
object AutomatumReports {
    /** Writes every Automatum log entry to the app log (an [AutomatumEntry.Error] as an error), which a report carries (a game on engine state's report carries its
     * [GameRepro], not [AutomatumLog.bundle]: report DH3QX6's refusals reached nobody), and so to the Android log. */
    fun install() {
        AutomatumLog.listener = { entry ->
            val line = "${entry.game.take(8)} ${entry.line()}"
            if (entry is AutomatumEntry.Error) AppLog.error(TAG, line) else AppLog.info(TAG, line)
        }
    }

    /** The latest game's bundle as report text: a game on engine state's device bundle ([GameRepro], I-D56 step D) when
     * one was opened last, else today's Automatum bundle; empty when nothing was recorded or it can't be made. Never
     * throws (also used while the app crashes). */
    fun text(maxChars: Int = CrashReport.MAX_AUTOMATUM): String = try {
        // Only where the engine side answers at once (Android, while the app crashes too); else today's bundle
        val repro = engine?.let { ask -> runCatching { Answer<GameRepro?>().also { a -> ask(a::set) }.get() }.getOrNull() }
        text(repro, maxChars)
    } catch (_: Throwable) { "" }

    /** [text] for a report that can wait for the engine side (web client step 4c: in the browser the device answers from
     * its Web Worker; on Android at once, as [text]). Never throws. */
    suspend fun textNow(maxChars: Int = CrashReport.MAX_AUTOMATUM): String = try {
        val ask = engine
        val repro = if (ask == null) null else kotlinx.coroutines.suspendCancellableCoroutine<GameRepro?> { c ->
            try { ask { c.resume(it) } } catch (failure: Throwable) { c.resume(null) }
        }
        text(repro, maxChars)
    } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled } catch (_: Throwable) { "" }

    private fun text(repro: GameRepro?, maxChars: Int): String =
        repro?.let { fitEngine(it, maxChars) } ?: AutomatumLog.bundle()?.let { fit(it, maxChars) }.orEmpty()

    /** The latest game on engine state's device bundle ([EngineGame.repro]), given to its callback (on Android before it
     * returns), set while one is open on this device. */
    @Volatile var engine: ((done: (GameRepro?) -> Unit) -> Unit)? = null

    /** The latest open game's [EngineGame.logBoard] (its callback once the lines are in the log), set while one is open on
     * this device. */
    @Volatile var board: ((done: () -> Unit) -> Unit)? = null

    /** LOB-089: writes the latest game's board into the app log before a problem report reads it. Never throws. */
    fun logBoard() { try { board?.invoke {} } catch (_: Throwable) {} }

    /** [logBoard] for a report that can wait for the engine side (web client step 4c; on Android at once). Never throws. */
    suspend fun logBoardNow() {
        try {
            val ask = board ?: return
            kotlinx.coroutines.suspendCancellableCoroutine<Unit> { c -> try { ask { c.resume(Unit) } } catch (failure: Throwable) { c.resume(Unit) } }
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled } catch (_: Throwable) {}
    }

    /** [repro] as report text ([GameRepro.REPORT_HEADER] and gzip+base64), dropping its least useful parts until it
     * fits [maxChars]: older log entries, then the oldest events (the base can't be replayed past them then), then the
     * base state. */
    fun fitEngine(repro: GameRepro, maxChars: Int): String? {
        var fitted = repro
        while (true) {
            val text = encode(fitted.encode())
            if (text.length <= maxChars) return text
            fitted = when {
                fitted.log.size > MIN_ENTRIES -> fitted.copy(log = fitted.log.takeLast(fitted.log.size / 2))
                fitted.base != null && fitted.events.size > MIN_ENTRIES -> fitted.copy(base = null, events = fitted.events.takeLast(fitted.events.size / 2))
                fitted.events.size > MIN_ENTRIES -> fitted.copy(events = fitted.events.takeLast(fitted.events.size / 2))
                fitted.base != null -> fitted.copy(base = null)
                fitted.definitions.isNotEmpty() -> fitted.copy(definitions = "")
                else -> return null
            }
        }
    }

    /** [bundle] as report text, dropping its older log entries until it fits [maxChars]. */
    fun fit(bundle: ReproBundle, maxChars: Int): String {
        var fitted = bundle
        while (true) {
            val text = encode(fitted)
            if (text.length <= maxChars) return text
            fitted = when {
                fitted.entries.size > MIN_ENTRIES -> fitted.copy(entries = fitted.entries.takeLast(fitted.entries.size / 2))
                else -> return ""
            }
        }
    }

    fun encode(bundle: ReproBundle): String = ReproBundle.REPORT_HEADER + "\n" + gzip(bundle.encode())

    private fun encode(engineBundle: String): String = GameRepro.REPORT_HEADER + "\n" + gzip(engineBundle)

    private fun gzip(text: String): String = gzipBase64(text)

    private const val TAG = "Automatum"
    private const val MIN_ENTRIES = 20
}
