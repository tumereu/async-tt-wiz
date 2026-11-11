package org.asyncttwiz.appui

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.window.PopupProperties
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig

// The browser's pieces come with the web shell and the web table (docs/specs/web-client.md §11 steps 3 and 4).
private fun later(): Nothing = throw NotImplementedError("web client step 3/4")

actual fun postToMain(action: () -> Unit): Unit = later()
actual fun postToMainDelayed(millis: Long, action: () -> Unit): () -> Unit = later()
actual fun isMainThread(): Boolean = later()
actual fun postFrameCallback(action: () -> Unit): Unit = later()
actual fun ownPlatformLog(lines: Int, maxBytes: Int): String = later()
actual fun uptimeMillis(): Long = later()
actual fun elapsedRealtime(): Long = later()
actual fun deviceModel(): String = later()
actual fun osVersion(): String = later()
actual fun platformLog(priority: Int, tag: String, message: String): Unit = later()
actual fun platformLogInfo(tag: String, message: String): Unit = later()
actual fun platformHttpClient(block: HttpClientConfig<*>.() -> Unit): HttpClient = later()
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
actual fun coilImageBitmap(image: coil3.Image): ImageBitmap? = later()
actual fun NativeTextPaint(): NativeTextPaint = later()
actual fun DrawScope.drawNativeText(text: String, x: Float, y: Float, paint: NativeTextPaint): Unit = later()
actual fun DrawScope.nativeSaveCount(): Int = later()
actual fun DrawScope.nativeRestoreToCount(count: Int): Unit = later()
actual fun DrawScope.drawCentredText(text: String, center: Offset, size: Float, color: Color, weight: Int,
    maxWidth: Float): Unit = later()
