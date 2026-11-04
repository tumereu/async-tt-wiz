package org.asyncttwiz.android

import android.util.Log
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.GZIPOutputStream
import org.asyncttwiz.automatum.AutomatumLog
import org.asyncttwiz.automatum.ReproBundle
import org.asyncttwiz.protocol.CrashReport
import org.asyncttwiz.protocol.game.GameRepro

/**
 * I-D43: Automatum's log in the Android log (debug level) and, in problem and crash reports, the latest game's
 * Automatum log and reproduction bundle ([AutomatumLog.bundle]) as [ReproBundle.REPORT_HEADER] and gzip+base64, cut
 * down to fit [CrashReport.MAX_AUTOMATUM] ([ReproBundle.decode] reads it back).
 */
internal object AutomatumReports {
    /** Writes every Automatum log entry to the Android log at debug level. */
    fun install() {
        AutomatumLog.listener = { entry -> Log.d(TAG, "${entry.game.take(8)} ${entry.line()}") }
    }

    /** The latest game's bundle as report text: a game on engine state's device bundle ([GameRepro], I-D56 step D) when
     * one was opened last, else today's Automatum bundle; empty when nothing was recorded or it can't be made. Never
     * throws (also used while the app crashes). */
    fun text(maxChars: Int = CrashReport.MAX_AUTOMATUM): String = try {
        engine?.invoke()?.let { fitEngine(it, maxChars) }
            ?: AutomatumLog.bundle()?.let { fit(it, maxChars) }.orEmpty()
    } catch (_: Throwable) { "" }

    /** The latest game on engine state's device bundle ([EngineGame.repro]), set while one is open on this device. */
    @Volatile var engine: (() -> GameRepro?)? = null

    /** [repro] as report text ([GameRepro.REPORT_HEADER] and gzip+base64), dropping its least useful parts until it
     * fits [maxChars]: older log entries, then the oldest events (the base can't be replayed past them then), then the
     * base state. */
    internal fun fitEngine(repro: GameRepro, maxChars: Int): String? {
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
    internal fun fit(bundle: ReproBundle, maxChars: Int): String {
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

    internal fun encode(bundle: ReproBundle): String = ReproBundle.REPORT_HEADER + "\n" + gzip(bundle.encode())

    private fun encode(engineBundle: String): String = GameRepro.REPORT_HEADER + "\n" + gzip(engineBundle)

    private fun gzip(text: String): String {
        val bytes = ByteArrayOutputStream()
        GZIPOutputStream(bytes).use { it.write(text.toByteArray(Charsets.UTF_8)) }
        return Base64.getEncoder().encodeToString(bytes.toByteArray())
    }

    private const val TAG = "Automatum"
    private const val MIN_ENTRIES = 20
}
