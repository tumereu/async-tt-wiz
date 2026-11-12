package org.asyncttwiz.appui

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.window.PopupProperties
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.js.Js

// The browser's pieces come with the web shell and the web table (docs/specs/web-client.md §11 steps 3 and 4).
private fun later(): Nothing = throw NotImplementedError("web client step 3/4")

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

/** Ktor's browser engine (fetch). */
actual fun platformHttpClient(block: HttpClientConfig<*>.() -> Unit): HttpClient = HttpClient(Js, block)

internal actual fun platformBuildInfo(): BuildInfo = later()

@Composable actual fun BackHandler(enabled: Boolean, onBack: () -> Unit): Unit = later()
@Composable actual fun screenWidthDp(): Int = later()
@Composable actual fun screenHeightDp(): Int = later()
@Composable actual fun screenOrientation(): Int = later()
@Composable actual fun displayRefreshRate(): Float = later()
@Composable actual fun rememberShortNote(): (String) -> Unit = later()
@Composable actual fun DialogFillsScreen(): Unit = later()
@Composable actual fun DialogDim(amount: Float): Unit = later()
actual fun passThroughPopupProperties(): PopupProperties = later()
actual fun coilImageBitmap(image: coil3.Image): ImageBitmap? = (image as? coil3.BitmapImage)?.bitmap?.asComposeImageBitmap()
actual fun NativeTextPaint(): NativeTextPaint = later()
actual fun DrawScope.drawNativeText(text: String, x: Float, y: Float, paint: NativeTextPaint): Unit = later()
actual fun DrawScope.nativeSaveCount(): Int = later()
actual fun DrawScope.nativeRestoreToCount(count: Int): Unit = later()
actual fun DrawScope.drawCentredText(text: String, center: Offset, size: Float, color: Color, weight: Int,
    maxWidth: Float): Unit = later()
actual fun edgeToEdgeDialogProperties(): androidx.compose.ui.window.DialogProperties = later()
