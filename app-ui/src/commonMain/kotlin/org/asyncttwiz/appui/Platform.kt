package org.asyncttwiz.appui

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig

// The platform's own pieces the UI and its controllers call directly (docs/specs/web-client.md §1 item 2). Android: each
// is the code the app used before, moved here unchanged.

/** A Ktor client on the platform's HTTP engine (Android: Ktor's Android engine), configured by [block]. */
expect fun platformHttpClient(block: HttpClientConfig<*>.() -> Unit): HttpClient

/** Whether deck imports from other sites go through the app's own server (the web: Archidekt allows no browser requests,
 * docs/specs/web-client.md §2.3), not straight to the site (Android). */
expect val importsThroughServer: Boolean
