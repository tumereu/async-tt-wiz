package org.asyncttwiz.storage

import okhttp3.OkHttpClient
import kotlin.concurrent.thread

/** Drops pooled connections without blocking the caller: closing a TLS connection writes to the
 * network, which Android refuses on the main thread (NetworkOnMainThreadException). */
fun OkHttpClient.evictConnectionsInBackground() {
    thread(name = "okhttp-evict", isDaemon = true) { connectionPool.evictAll() }
}
