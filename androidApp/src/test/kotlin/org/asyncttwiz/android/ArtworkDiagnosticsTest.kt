package org.asyncttwiz.android

import org.asyncttwiz.storage.ArtworkAttempt
import org.asyncttwiz.storage.ArtworkReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtworkDiagnosticsTest {
    private var clock = 0L
    private val lines = mutableListOf<Pair<Boolean, String>>()
    private val diagnostics = ArtworkDiagnostics({ clock }) { warning, message -> lines += warning to message }
    private val image = "https://cards.scryfall.io/normal/front/1/2/1234.jpg?99"

    private fun failure(reason: String = "UnknownHostException: cards.scryfall.io", url: String = image) =
        ArtworkAttempt(true, url, reason, 5, 0, 2, 1_500)

    @Test fun eachFailureReasonIsWrittenOncePerMinuteAndCountedInTheSummary() {
        repeat(5) { diagnostics.attempt(failure()) }
        // Only ports and timings differ: still the same failure.
        diagnostics.attempt(failure("ConnectException: from /127.0.0.1 (port 45314) after 10000ms"))
        diagnostics.attempt(failure("ConnectException: from /127.0.0.1 (port 45316) after 10001ms"))
        lines.removeAt(1)
        diagnostics.attempt(failure("HTTP 503"))
        assertEquals(listOf(true to "Download failed: image cards.scryfall.io/…/1234.jpg: UnknownHostException: cards.scryfall.io (failure 2 in a row, retry in 2 s)",
            true to "Download failed: image cards.scryfall.io/…/1234.jpg: HTTP 503 (failure 2 in a row, retry in 2 s)"), lines)
        lines.clear()
        clock += ArtworkDiagnostics.WINDOW
        diagnostics.attempt(failure())
        assertEquals(true to "Last 60 s: 0 downloaded (0 KB), 8 failed requests", lines[0])
        // A new minute writes the same reason again.
        assertTrue(lines[1].second.startsWith("Download failed: image"))
    }

    @Test fun recoveryWakeAndQuietSuccesses() {
        diagnostics.attempt(ArtworkAttempt(false, "https://api.scryfall.com/cards/abc", null, 40, 2048, 0, 0))
        diagnostics.woke("app opened")
        assertEquals(emptyList<Pair<Boolean, String>>(), lines)
        diagnostics.attempt(failure())
        diagnostics.woke("app opened")
        diagnostics.attempt(ArtworkAttempt(true, image, null, 120, 4096, 0, 0))
        diagnostics.attempt(ArtworkAttempt(true, image, null, 120, 4096, 0, 0))
        assertEquals(listOf(false to "Retrying artwork now (app opened)",
            false to "Downloads work again: image cards.scryfall.io/…/1234.jpg in 120 ms"), lines.drop(1))
        lines.clear()
        diagnostics.failed(ArtworkReference("abc", 0, "v1"), IllegalStateException("bad card"))
        clock += 1_000
        diagnostics.roll(force = true)
        assertEquals(listOf(true to "No art for printing abc face 0: IllegalStateException: bad card",
            true to "Last 1 s: 3 downloaded (10 KB), 1 failed requests, 1 cards without art"), lines)
        lines.clear()
        clock += ArtworkDiagnostics.WINDOW
        diagnostics.roll()
        assertEquals(emptyList<Pair<Boolean, String>>(), lines)
    }
}
