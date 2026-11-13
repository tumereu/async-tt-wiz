package org.asyncttwiz.appui

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.js.Js

/** Ktor's browser engine (fetch). */
actual fun platformHttpClient(block: HttpClientConfig<*>.() -> Unit): HttpClient = HttpClient(Js, block)

/** Archidekt allows no browser requests: deck links are read through the ATW server (docs/specs/web-client.md §2.3). */
actual val importsThroughServer: Boolean = true

