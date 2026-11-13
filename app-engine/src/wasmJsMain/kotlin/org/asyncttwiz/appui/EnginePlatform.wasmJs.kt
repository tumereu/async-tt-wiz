package org.asyncttwiz.appui

// The page's one thread is its main thread (web client step 3b): posted work runs from the event loop's queue, in order;
// a failure in it is reported as uncaught ([uncaught]) instead of escaping into the browser.

private fun guarded(where: String, action: () -> Unit): () -> Unit = {
    try { action() } catch (failure: Throwable) { uncaught(where, failure) }
}

actual fun postToMain(action: () -> Unit) { jsSetTimeout(0.0, guarded("main", action)) }

actual fun postToMainDelayed(millis: Long, action: () -> Unit): () -> Unit {
    val id = jsSetTimeout(millis.toDouble(), guarded("main", action))
    return { jsClearTimeout(id) }
}

actual fun isMainThread(): Boolean = true

actual fun postFrameCallback(action: () -> Unit) = jsRequestFrame(guarded("frame", action))

/** The browser's console can't be read back; the app log ([org.asyncttwiz.android.AppLog]) is the whole log there. */
actual fun ownPlatformLog(lines: Int, maxBytes: Int): String = ""

/** Milliseconds since the page loaded (performance.now()): the browser's monotonic clock. */
actual fun uptimeMillis(): Long = jsPerformanceNow().toLong()

actual fun elapsedRealtime(): Long = jsPerformanceNow().toLong()

/** The browser, as its user agent names itself. */
actual fun deviceModel(): String = jsUserAgent().take(300)

actual fun osVersion(): String = "Web (${jsPlatform().take(100)})"

/** The browser's console. */
actual fun platformLog(priority: Int, tag: String, message: String) = when (priority) {
    LogPriority.ERROR -> jsConsoleError("$tag: $message")
    LogPriority.WARN -> jsConsoleWarn("$tag: $message")
    else -> jsConsoleInfo("$tag: $message")
}

actual fun platformLogInfo(tag: String, message: String) = jsConsoleInfo("$tag: $message")

/** The web build's values; the page's entry point installs them first thing ([installBuildInfo]). */
private var browserBuildInfo: BuildInfo? = null

/** Installs the web build's [BuildInfo] (the web shell's entry point, before anything reads [AppBuild]). */
fun installBuildInfo(info: BuildInfo) { browserBuildInfo = info }

internal actual fun platformBuildInfo(): BuildInfo = checkNotNull(browserBuildInfo) { "No web build info installed" }
