package org.asyncttwiz.appui

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig

// The platform's own pieces the UI and its controllers call directly (docs/specs/web-client.md §1 item 2). Android: each
// is the code the app used before, moved here unchanged.

/** Runs [action] on the main (UI) thread, after what is already queued there. */
expect fun postToMain(action: () -> Unit)

/** Runs [action] on the main thread after [millis]; the returned function cancels it if it hasn't run. */
expect fun postToMainDelayed(millis: Long, action: () -> Unit): () -> Unit

/** Whether this is the main (UI) thread. */
expect fun isMainThread(): Boolean

/** On the main thread: runs [action] when the next frame starts (Android: the Choreographer's frame callback). */
expect fun postFrameCallback(action: () -> Unit)

/** The app process's own recent platform log ([lines] at most, [maxBytes] at most), "" when it can't be read (Android:
 * its logcat). Not redacted. */
expect fun ownPlatformLog(lines: Int, maxBytes: Int): String

/** Milliseconds since the device started, not counting deep sleep (Android: SystemClock.uptimeMillis). */
expect fun uptimeMillis(): Long

/** Milliseconds since the device started, deep sleep included (Android: SystemClock.elapsedRealtime). */
expect fun elapsedRealtime(): Long

/** The device's maker and model, as problem and crash reports name it. */
expect fun deviceModel(): String

/** The operating system and its version, as problem and crash reports name it. */
expect fun osVersion(): String

/** Log priorities, the platform log's own numbers (Android: android.util.Log's). */
object LogPriority {
    const val INFO = 4
    const val WARN = 5
    const val ERROR = 6
}

/** Writes [message] to the platform's log at [priority] ([LogPriority]) under [tag] (Android: logcat). */
expect fun platformLog(priority: Int, tag: String, message: String)

/** Writes [message] to the platform's log as information under [tag] (Android: Log.i). */
expect fun platformLogInfo(tag: String, message: String)

/** A Ktor client on the platform's HTTP engine (Android: Ktor's Android engine), configured by [block]. */
expect fun platformHttpClient(block: HttpClientConfig<*>.() -> Unit): HttpClient
