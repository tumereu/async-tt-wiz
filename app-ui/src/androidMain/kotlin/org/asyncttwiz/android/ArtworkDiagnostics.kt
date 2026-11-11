package org.asyncttwiz.android

import org.asyncttwiz.storage.ArtworkAttempt
import org.asyncttwiz.storage.ArtworkLog
import org.asyncttwiz.storage.ArtworkReference
import java.net.URI

/** Artwork download diagnostics for problem reports. A draft downloads hundreds of images, so
 * every failure reason is written once per minute with its URL and the rest are counted into a
 * minute summary; a success after failures is written as a recovery. */
internal class ArtworkDiagnostics(private val now: () -> Long = System::currentTimeMillis,
    private val write: (warning: Boolean, message: String) -> Unit = { warning, message ->
        if (warning) AppLog.warn("art", message) else AppLog.info("art", message)
    }) : ArtworkLog {
    private var windowStart = now()
    private var downloaded = 0
    private var downloadedBytes = 0L
    private var failed = 0
    private var gaveUp = 0
    private val reasons = HashSet<String>()
    private var failing = false

    @Synchronized override fun attempt(attempt: ArtworkAttempt) {
        roll()
        val what = if (attempt.image) "image" else "card data"
        val failure = attempt.failure
        if (failure == null) {
            downloaded++; downloadedBytes += attempt.bytes
            if (failing) write(false, "Downloads work again: $what ${shortUrl(attempt.url)} in ${attempt.millis} ms")
            failing = false
            return
        }
        failed++; failing = true
        // Ports and timings vary per attempt; the same kind of failure is written once a minute.
        if (reasons.add("$what ${host(attempt.url)} ${failure.replace(DIGITS, "#")}"))
            write(true, "Download failed: $what ${shortUrl(attempt.url)}: ${failure.take(400)} " +
                "(failure ${attempt.failures} in a row, retry in ${(attempt.retryInMillis + 999) / 1000} s)")
    }

    @Synchronized override fun failed(reference: ArtworkReference, failure: Throwable) {
        roll()
        gaveUp++
        val reason = "${failure.javaClass.simpleName}: ${failure.message.orEmpty().take(200)}"
        if (reasons.add("load ${reason.replace(DIGITS, "#")}")) write(true, "No art for printing ${reference.printing} face ${reference.face}: $reason")
    }

    /** App back in front or its network allowed again: waiting retries run now. */
    @Synchronized fun woke(reason: String) {
        roll()
        if (failing) write(false, "Retrying artwork now ($reason)")
    }

    /** Writes the finished minute's summary, if anything happened in it. */
    @Synchronized fun roll(force: Boolean = false) {
        val time = now()
        if (!force && time - windowStart < WINDOW) return
        if (downloaded + failed + gaveUp > 0)
            write(failed + gaveUp > 0, "Last ${(time - windowStart + 500) / 1000} s: $downloaded downloaded " +
                "(${downloadedBytes / 1024} KB), $failed failed requests" + if (gaveUp > 0) ", $gaveUp cards without art" else "")
        windowStart = time; downloaded = 0; downloadedBytes = 0; failed = 0; gaveUp = 0
        reasons.clear()
    }

    companion object {
        const val WINDOW = 60_000L
        private val DIGITS = Regex("[0-9]+")
        private fun host(url: String) = runCatching { URI(url).host }.getOrNull() ?: "?"
        /** Host and last path segment: enough to tell metadata, image size and card apart. */
        fun shortUrl(url: String) = runCatching { URI(url) }.getOrNull()
            ?.let { "${it.host}/…/${it.path.substringAfterLast('/')}" } ?: url.take(120)
    }
}
