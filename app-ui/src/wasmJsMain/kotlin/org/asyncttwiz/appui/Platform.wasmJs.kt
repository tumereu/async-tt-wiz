package org.asyncttwiz.appui

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.js.Js
import io.ktor.client.request.HttpRequestPipeline

/**
 * Ktor's browser engine (fetch). A request the browser couldn't make (the connection dropped, reset or refused, no network)
 * comes out of the engine as a `kotlin.Error` ("Fail to fetch"), which isn't an Exception: here it becomes the IOException
 * a failed request is on Android, so the API clients take it as a network failure (retried, kept quiet by background
 * refreshes: SYS-020) instead of letting it through as a crash.
 */
actual fun platformHttpClient(block: HttpClientConfig<*>.() -> Unit): HttpClient = HttpClient(Js, block).apply {
    requestPipeline.intercept(HttpRequestPipeline.Before) {
        try { proceed() } catch (failure: Throwable) {
            throw if (failure is Exception) failure else requestFailed(failure)
        }
    }
}

/** [failure] (a failed fetch: a JavaScript error, not an Exception) as the IOException of a failed request. */
fun requestFailed(failure: Throwable): kotlinx.io.IOException =
    kotlinx.io.IOException(listOfNotNull(failure.message, failure.cause?.message).distinct().joinToString(": ")
        .ifEmpty { simpleClassName(failure) }, failure)

/** Archidekt allows no browser requests: deck links are read through the ATW server (WEB-010). */
actual val importsThroughServer: Boolean = true
