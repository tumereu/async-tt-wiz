package org.asyncttwiz.android

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest

class BuiltInFilesTest {
    // WEB-005 (owner, 2026-11-20): a built-in file that fails to load is tried again a couple of times (after 1 s, then 3 s)
    // before the failure stands; each failed try is in the app log
    @Test fun aBuiltInFileThatFailsToLoadIsTriedAgainACoupleOfTimes() = runTest {
        var tries = 0
        val bytes = loadBuiltIn("Bundled file fonts/test.ttf") { if (++tries < 3) error("connection reset") else "font" }
        assertEquals("font", bytes)
        assertEquals(3, tries)
        assertEquals(4_000L, currentTime, "waited 1 s, then 3 s")
        assertTrue("Bundled file fonts/test.ttf didn't load (try 1 of 3): trying again in 1000 ms" in AppLog.text())
        assertTrue("Bundled file fonts/test.ttf loaded at try 3" in AppLog.text())
    }

    // WEB-005: after the last try the failure is the caller's ("ATW couldn't load")
    @Test fun theLastTrysFailureStands() = runTest {
        var tries = 0
        assertFailsWith<IllegalStateException> { loadBuiltIn("Bundled file gone.txt") { tries++; error("HTTP 503") } }
        assertEquals(3, tries)
        assertTrue("Bundled file gone.txt didn't load after 3 tries" in AppLog.text())
        tries = 0
        assertEquals(1, loadBuiltIn("Bundled file first.txt") { ++tries })
        assertEquals(1, tries, "no retry when it loads")
    }
}
