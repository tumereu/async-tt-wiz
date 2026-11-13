package org.asyncttwiz.appui

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.android.Android

actual fun platformHttpClient(block: HttpClientConfig<*>.() -> Unit): HttpClient = HttpClient(Android, block)

actual val importsThroughServer: Boolean = false
