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

/** What a build-values message to the engine's Web Worker starts with ([buildInfoMessage]). */
const val BUILD_INFO_PREFIX = "atw-build:"

/**
 * Web client step 4d: the page's build values as one message to the engine's Web Worker (the devices there read them: the
 * diagnostics switch, the version in a report), or null when none is installed on the page.
 */
fun buildInfoMessage(): String? {
    val info = browserBuildInfo ?: return null
    val json = kotlinx.serialization.json.buildJsonObject {
        put("accountOrigin", kotlinx.serialization.json.JsonPrimitive(info.accountOrigin))
        put("resetOrigin", kotlinx.serialization.json.JsonPrimitive(info.resetOrigin))
        put("loopbackApi", kotlinx.serialization.json.JsonPrimitive(info.loopbackApi))
        put("versionName", kotlinx.serialization.json.JsonPrimitive(info.versionName))
        put("versionCode", kotlinx.serialization.json.JsonPrimitive(info.versionCode))
        put("buildType", kotlinx.serialization.json.JsonPrimitive(info.buildType))
        put("gitCommit", kotlinx.serialization.json.JsonPrimitive(info.gitCommit))
        put("debug", kotlinx.serialization.json.JsonPrimitive(info.debug))
        put("diagnostics", kotlinx.serialization.json.JsonPrimitive(info.diagnostics))
        put("accountSwitcher", kotlinx.serialization.json.JsonPrimitive(info.accountSwitcher))
        put("catalogChecksum", kotlinx.serialization.json.JsonPrimitive(info.catalogChecksum))
        put("artTestOrigin", kotlinx.serialization.json.JsonPrimitive(info.artTestOrigin))
        put("artBase", kotlinx.serialization.json.JsonPrimitive(info.artBase))
        put("artContact", kotlinx.serialization.json.JsonPrimitive(info.artContact))
    }
    return BUILD_INFO_PREFIX + json.toString()
}

/** The engine's Web Worker: installs the page's build values from [text] ([buildInfoMessage]); false when it isn't one. */
fun installBuildInfoMessage(text: String): Boolean {
    if (!text.startsWith(BUILD_INFO_PREFIX)) return false
    val o = kotlinx.serialization.json.Json.parseToJsonElement(text.removePrefix(BUILD_INFO_PREFIX)) as kotlinx.serialization.json.JsonObject
    fun s(key: String) = (o[key] as? kotlinx.serialization.json.JsonPrimitive)?.content.orEmpty()
    fun b(key: String) = s(key) == "true"
    installBuildInfo(object : BuildInfo {
        override val accountOrigin = s("accountOrigin")
        override val resetOrigin = s("resetOrigin")
        override val loopbackApi = b("loopbackApi")
        override val versionName = s("versionName")
        override val versionCode = s("versionCode").toIntOrNull() ?: 0
        override val buildType = s("buildType")
        override val gitCommit = s("gitCommit")
        override val debug = b("debug")
        override val diagnostics = b("diagnostics")
        override val accountSwitcher = b("accountSwitcher")
        override val catalogChecksum = s("catalogChecksum")
        override val artTestOrigin = s("artTestOrigin")
        override val artBase = s("artBase")
        override val artContact = s("artContact")
    })
    return true
}
