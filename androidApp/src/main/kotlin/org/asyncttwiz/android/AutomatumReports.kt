package org.asyncttwiz.android

import android.util.Log
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.GZIPOutputStream
import org.asyncttwiz.automatum.AutomatumLog
import org.asyncttwiz.automatum.ReproBundle
import org.asyncttwiz.protocol.CrashReport

/**
 * I-D43: Automatum's log in the Android log (debug level) and, in problem and crash reports, the latest game's
 * Automatum log and reproduction bundle ([AutomatumLog.bundle]) as [ReproBundle.REPORT_HEADER] and gzip+base64, cut
 * down to fit [CrashReport.MAX_AUTOMATUM]. A developer loads it with the automatum tests' `ReproBundles`.
 */
internal object AutomatumReports {
    /** Writes every Automatum log entry to the Android log at debug level. */
    fun install() {
        AutomatumLog.listener = { entry -> Log.d(TAG, "${entry.game.take(8)} ${entry.line()}") }
    }

    /** The latest game's bundle as report text; empty when Automatum recorded nothing or it can't be made. Never throws
     * (also used while the app crashes). */
    fun text(maxChars: Int = CrashReport.MAX_AUTOMATUM): String = try {
        AutomatumLog.bundle()?.let { fit(it, maxChars) }.orEmpty()
    } catch (_: Throwable) { "" }

    /** [bundle] as report text, dropping its least useful parts until it fits [maxChars]: older engine calls, then ATW's
     * board, older log entries, the last call and the engine state. */
    internal fun fit(bundle: ReproBundle, maxChars: Int): String {
        var fitted = bundle
        while (true) {
            val text = encode(fitted)
            if (text.length <= maxChars) return text
            fitted = when {
                fitted.calls.size > 1 -> fitted.copy(calls = fitted.calls.drop(1))
                fitted.play != null -> fitted.copy(play = null)
                fitted.entries.size > MIN_ENTRIES -> fitted.copy(entries = fitted.entries.takeLast(fitted.entries.size / 2))
                fitted.calls.isNotEmpty() -> fitted.copy(calls = emptyList())
                fitted.engine != null -> fitted.copy(engine = null, log = emptyList())
                fitted.definitions.isNotEmpty() -> fitted.copy(definitions = emptyMap())
                else -> return ""
            }
        }
    }

    internal fun encode(bundle: ReproBundle): String {
        val bytes = ByteArrayOutputStream()
        GZIPOutputStream(bytes).use { it.write(bundle.encode().toByteArray(Charsets.UTF_8)) }
        return ReproBundle.REPORT_HEADER + "\n" + Base64.getEncoder().encodeToString(bytes.toByteArray())
    }

    private const val TAG = "Automatum"
    private const val MIN_ENTRIES = 20
}
