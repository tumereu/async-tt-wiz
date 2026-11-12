package org.asyncttwiz.appui

import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Choreographer
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.android.Android
import java.util.ServiceLoader
import java.util.concurrent.TimeUnit

private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

actual fun postToMain(action: () -> Unit) { mainHandler.post { action() } }

actual fun postToMainDelayed(millis: Long, action: () -> Unit): () -> Unit {
    val runnable = Runnable(action)
    mainHandler.postDelayed(runnable, millis)
    return { mainHandler.removeCallbacks(runnable) }
}

actual fun isMainThread(): Boolean = Looper.myLooper() == Looper.getMainLooper()

actual fun postFrameCallback(action: () -> Unit) { Choreographer.getInstance().postFrameCallback { action() } }

/** The app may read its own process's logcat without extra permissions; anything else yields nothing. */
actual fun ownPlatformLog(lines: Int, maxBytes: Int): String = try {
    val process = ProcessBuilder("logcat", "-d", "-v", "threadtime", "-v", "UTC", "-t", lines.toString(),
        "--pid=${android.os.Process.myPid()}").redirectErrorStream(true).start()
    try {
        val output = java.io.ByteArrayOutputStream()
        process.inputStream.use { input ->
            val buffer = ByteArray(16_384)
            while (output.size() < maxBytes) {
                val count = input.read(buffer)
                if (count == -1) break
                output.write(buffer, 0, count)
            }
        }
        if (process.waitFor(3, TimeUnit.SECONDS) && process.exitValue() == 0) output.toString(Charsets.UTF_8.name()) else ""
    } finally { process.destroy() }
} catch (_: Exception) { "" }

actual fun uptimeMillis(): Long = SystemClock.uptimeMillis()

actual fun elapsedRealtime(): Long = SystemClock.elapsedRealtime()

actual fun deviceModel(): String = "${Build.MANUFACTURER} ${Build.MODEL}"

actual fun osVersion(): String = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"

actual fun platformLog(priority: Int, tag: String, message: String) { Log.println(priority, tag, message) }

actual fun platformLogInfo(tag: String, message: String) { Log.i(tag, message) }

actual fun platformHttpClient(block: HttpClientConfig<*>.() -> Unit): HttpClient = HttpClient(Android, block)

/** The app module registers its BuildConfig's values as a Java service (META-INF/services), found here once. */
internal actual fun platformBuildInfo(): BuildInfo =
    ServiceLoader.load(BuildInfo::class.java, BuildInfo::class.java.classLoader).iterator().next()

actual val importsThroughServer: Boolean = false
