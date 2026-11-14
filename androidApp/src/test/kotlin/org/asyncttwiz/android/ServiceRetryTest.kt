package org.asyncttwiz.android

import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.*

class ServiceRetryTest {
    @Test fun transientFailuresWaitQuietlyAndKeepTheCapturedOriginal() = runBlocking {
        val original = org.asyncttwiz.protocol.AddContact("request", "account")
        val attempts = mutableListOf<Any>()
        val waits = mutableListOf<Long>()
        val result = serviceRetry(wait = { waits.add(it) }) {
            attempts.add(original)
            if (attempts.size < 3) throw LobbyFailure("network")
            original
        }
        assertSame(original, result)
        assertTrue(attempts.all { it === original })
        assertEquals(listOf(500L, 1_500L), waits)
    }

    @Test fun onlyTheExhaustedUserRequestFailsAndSemanticOrAuthenticationFailuresNeverRetry() = runBlocking {
        var attempts = 0
        assertFailsWith<DeckFailure> { serviceRetry(wait = {}) { attempts++; throw DeckFailure("network") } }
        assertEquals(3, attempts)
        for (code in listOf("authentication_required", "not_found", "request_conflict", "invalid_response")) {
            attempts = 0
            assertFailsWith<DeckFailure> { serviceRetry(wait = { fail("Must not wait") }) { attempts++; throw DeckFailure(code) } }
            assertEquals(1, attempts)
        }
    }
    @Test fun accountSwitchDuringBackoffCancelsBeforeAnotherPrivateRequest() = runBlocking {
        var current = true
        var calls = 0
        assertFailsWith<kotlinx.coroutines.CancellationException> {
            serviceRetry(wait = { current = false }, current = { current }) { calls++; throw LobbyFailure("network") }
        }
        assertEquals(1, calls)
    }

    // SYS-020
    @Test fun quietFailuresReportOnlyAfterThirtySecondsOfContinuousFailure() {
        var now = 1_000L
        val failures = QuietRefreshFailures({ now })
        assertFalse(failures.failed())
        now += 29_999; assertFalse(failures.failed())
        now += 1; assertTrue(failures.failed())
        failures.succeeded()
        now += 5_000; assertFalse(failures.failed())
    }

    // SYS-020: a stretch of quiet failures is in the app log once as it starts, once as the note shows and once as it
    // ends, not once per poll
    @Test fun quietFailuresAreLoggedAtEachChangeOnly() {
        var now = 1_000L
        val failures = QuietRefreshFailures({ now }, tag = "quiet-test")
        fun lines() = AppLog.text().lines().filter { "quiet-test:" in it }
        val before = lines().size
        failures.failed(); now += 10_000; failures.failed(); now += 10_000; failures.failed()
        assertEquals(1, lines().size - before, lines().joinToString("\n"))
        now += 10_000; failures.failed(); now += 10_000; failures.failed()
        assertEquals(2, lines().size - before, lines().joinToString("\n"))
        assertTrue("note shown" in lines().last(), lines().last())
        failures.succeeded(); failures.succeeded()
        assertEquals(3, lines().size - before, lines().joinToString("\n"))
        assertTrue("5 quiet failures" in lines().last(), lines().last())
    }
}
