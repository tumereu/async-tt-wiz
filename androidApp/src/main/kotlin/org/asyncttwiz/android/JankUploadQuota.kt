package org.asyncttwiz.android

/** Owned by DebugJank for the process lifetime. Failed uploads also consume an attempt. */
internal class JankUploadQuota {
    private var attempts = 0
    private var lastAttempt: Long? = null

    fun tryAcquire(nowNanos: Long): Boolean {
        if (attempts >= 3) return false
        if (lastAttempt?.let { nowNanos - it < 60_000_000_000L } == true) return false
        attempts++
        lastAttempt = nowNanos
        return true
    }
}
