package org.asyncttwiz.android

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/** WEB-005: how long the web app waits before each further try of a built-in file that didn't load. */
val BUILT_IN_RETRY_MILLIS = listOf(1_000L, 3_000L)

/**
 * WEB-005 (owner, 2026-11-20): loads a file built into the app ([what]: which, for the app log; fonts and the like) with
 * [load], trying again after each of [delaysMillis] when it fails, so a passing network hiccup doesn't stop the app from
 * starting; the last try's failure is the caller's ("ATW couldn't load"). Each failed try is in the app log.
 */
suspend fun <T> loadBuiltIn(what: String, delaysMillis: List<Long> = BUILT_IN_RETRY_MILLIS, load: suspend () -> T): T {
    for ((attempt, wait) in delaysMillis.withIndex()) {
        try {
            return load().also { if (attempt > 0) AppLog.info("web-page", "$what loaded at try ${attempt + 1}") }
        } catch (failure: Throwable) {
            if (failure is CancellationException) throw failure
            AppLog.warn("web-page", "$what didn't load (try ${attempt + 1} of ${delaysMillis.size + 1}): trying again in $wait ms", failure)
        }
        delay(wait)
    }
    return try {
        load().also { if (delaysMillis.isNotEmpty()) AppLog.info("web-page", "$what loaded at try ${delaysMillis.size + 1}") }
    } catch (failure: Throwable) {
        if (failure !is CancellationException)
            AppLog.warn("web-page", "$what didn't load after ${delaysMillis.size + 1} tries", failure)
        throw failure
    }
}
