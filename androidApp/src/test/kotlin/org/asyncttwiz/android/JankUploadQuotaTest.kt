package org.asyncttwiz.android

import org.junit.Test
import kotlin.test.*

class JankUploadQuotaTest {
    @Test fun atMostThreeAttemptsPerProcessEvenAfterADayPasses() {
        val quota = JankUploadQuota()
        assertTrue(quota.tryAcquire(0))
        assertTrue(quota.tryAcquire(60_000_000_000L))
        assertTrue(quota.tryAcquire(120_000_000_000L))
        assertFalse(quota.tryAcquire(180_000_000_000L))
        assertFalse(quota.tryAcquire(86_400_000_000_000L))
        // A new process has a new owner/allowance, even on the same day.
        assertTrue(JankUploadQuota().tryAcquire(180_000_000_000L))
    }

    @Test fun oneMinuteSpacingDoesNotConsumeAllowanceWhenTooSoon() {
        val quota = JankUploadQuota()
        assertTrue(quota.tryAcquire(0))
        repeat(10) { assertFalse(quota.tryAcquire(59_999_999_999L)) }
        assertTrue(quota.tryAcquire(60_000_000_000L))
        assertFalse(quota.tryAcquire(60_000_000_001L))
        assertTrue(quota.tryAcquire(120_000_000_000L))
        assertFalse(quota.tryAcquire(180_000_000_000L))
    }
}
