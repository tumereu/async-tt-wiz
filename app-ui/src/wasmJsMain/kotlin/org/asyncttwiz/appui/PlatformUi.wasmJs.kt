@file:OptIn(ExperimentalComposeUiApi::class)

package org.asyncttwiz.appui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.PopupProperties

// The browser's pieces of drawing, windows and input the shared UI uses (web client step 3).

/**
 * The page's Back registrations in the order they were first composed (the later one is on top, as Android's dispatcher
 * orders them): the shared UI's [BackHandler]s (layers: dialogs' own, panels, popups, the side menu) and
 * [ScreenBackHandler]s (moving between screens). The on-screen Back (WEB-003) calls the topmost enabled one, as Android's
 * system Back would ([dispatch]); Esc and Android Chrome's system Back close only layers, through Compose's own Back
 * handling, which layers also register with. [layerOpen] tells the stale-tab reload (WEB-006) to wait.
 */
object WebBack {
    class Entry(val screen: Boolean) {
        var enabled by mutableStateOf(false)
        var onBack: () -> Unit = {}
    }

    private val entries = mutableStateListOf<Entry>()

    /** Dialogs open that observe their input ([AppPlatform.ObserveDialogInput]), counted as layers. */
    var dialogs by mutableStateOf(0)

    /** Whether a layer (a panel, popup, side menu or dialog) is open. */
    val layerOpen: Boolean get() = dialogs > 0 || entries.any { it.enabled && !it.screen }

    /** Whether the on-screen Back has something to do. */
    val canDispatch: Boolean get() = entries.any { it.enabled }

    /** The topmost enabled Back runs, as the system's Back would on Android; false when none is enabled. */
    fun dispatch(): Boolean {
        val top = entries.lastOrNull { it.enabled } ?: return false
        org.asyncttwiz.android.AppLog.info("web-back", "On-screen Back: ${if (top.screen) "screen" else "layer"}")
        top.onBack()
        return true
    }

    internal fun add(entry: Entry) { entries += entry }
    internal fun remove(entry: Entry) { entries -= entry }
}

@Composable private fun WebBackEntry(screen: Boolean, enabled: Boolean, onBack: () -> Unit) {
    val entry = remember { WebBack.Entry(screen) }
    SideEffect { entry.enabled = enabled; entry.onBack = onBack }
    DisposableEffect(entry) {
        WebBack.add(entry)
        onDispose { entry.enabled = false; WebBack.remove(entry) }
    }
}

@Composable actual fun BackHandler(enabled: Boolean, onBack: () -> Unit) {
    WebBackEntry(screen = false, enabled, onBack)
    // Compose's own Back: Esc, and Android Chrome's system Back through the shell's close watcher
    androidx.compose.ui.backhandler.BackHandler(enabled, onBack)
}

@Composable actual fun ScreenBackHandler(enabled: Boolean, onBack: () -> Unit) = WebBackEntry(screen = true, enabled, onBack)

/** WEB-004: Compose's own Back (Esc, Android Chrome's system Back through the shell's close watcher) leaves the table or
 * the draft when nothing above it takes it; not a layer (the stale-tab reload doesn't wait for it). */
@Composable actual fun LeaveScreenBackHandler(enabled: Boolean, onBack: () -> Unit) {
    androidx.compose.ui.backhandler.BackHandler(enabled) {
        org.asyncttwiz.android.AppLog.info("web-back", "Esc or the system Back leaves the screen")
        onBack()
    }
}

/** WEB-002: Back leaves a game's table at once in the browser. */
actual val secondBackLeavesTable: Boolean = false

@Composable actual fun screenWidthDp(): Int =
    (LocalWindowInfo.current.containerSize.width / LocalDensity.current.density).toInt()

@Composable actual fun screenHeightDp(): Int =
    (LocalWindowInfo.current.containerSize.height / LocalDensity.current.density).toInt()

@Composable actual fun screenOrientation(): Int =
    LocalWindowInfo.current.containerSize.let { if (it.width > it.height) ScreenOrientation.LANDSCAPE else ScreenOrientation.PORTRAIT }

/** Browsers don't say; the table's animations use it only as a hint. */
@Composable actual fun displayRefreshRate(): Float = 60f

/** The short notes the web shell shows over the app, as Android's Toast ([rememberShortNote]). */
object WebNotes {
    var text by mutableStateOf<String?>(null)
        private set
    private var serial = 0
    private var cancel: () -> Unit = {}

    fun show(value: String) {
        text = value
        val shown = ++serial
        cancel()
        cancel = postToMainDelayed(2500) { if (serial == shown) text = null }
    }
}

@Composable actual fun rememberShortNote(): (String) -> Unit = remember { { text -> WebNotes.show(text) } }

/** A browser dialog layer already spans the page. */
@Composable actual fun DialogFillsScreen() {}

/** The browser's dialogs keep Compose's own scrim. */
@Composable actual fun DialogDim(amount: Float) {}

actual fun passThroughPopupProperties(): PopupProperties = PopupProperties(focusable = false, dismissOnBackPress = false,
    dismissOnClickOutside = false, clippingEnabled = false)

actual fun coilImageBitmap(image: coil3.Image): ImageBitmap? = (image as? coil3.BitmapImage)?.bitmap?.asComposeImageBitmap()

actual fun edgeToEdgeDialogProperties(): DialogProperties = DialogProperties(usePlatformDefaultWidth = false)

// Text at exact pixel positions: laid out by Compose's own text measurer (the fonts the rest of the page uses), since the
// browser's Skia has no system fonts of its own.

/**
 * The page's one font resolver: the screens' (the web shell provides it as their LocalFontFamilyResolver) and the table's
 * text here share it, so the fallback fonts preloaded into it at start draw in both.
 */
internal val webFontResolver by lazy { createFontFamilyResolver() }
private var measurer: Pair<Float, TextMeasurer>? = null

private fun measurer(density: Float): TextMeasurer = measurer?.takeIf { it.first == density }?.second
    ?: TextMeasurer(webFontResolver, Density(density), LayoutDirection.Ltr).also { measurer = density to it }

private class BrowserTextPaint : NativeTextPaint {
    override var textSize: Float = 12f
    override var color: Int = 0xFF000000.toInt()
    override var alpha: Int
        get() = (color ushr 24) and 0xff
        set(value) { color = (color and 0x00ffffff) or ((value and 0xff) shl 24) }
    override var isFakeBoldText: Boolean = false
    override var textAlign: NativeTextPaint.TextAlign = NativeTextPaint.TextAlign.Left

    fun layout(text: String, density: Float = 1f): TextLayoutResult = measurer(density).measure(text, TextStyle(
        color = Color(color), fontSize = (textSize / density).sp,
        fontWeight = if (isFakeBoldText) FontWeight.Bold else FontWeight.Normal))

    override fun measureText(text: String): Float = layout(text).size.width.toFloat()

    override fun fontMetrics(): NativeTextPaint.FontMetrics {
        val line = layout("Hg")
        val baseline = line.firstBaseline
        return NativeTextPaint.FontMetrics(-baseline, -baseline, line.size.height - baseline, line.size.height - baseline)
    }
}

actual fun NativeTextPaint(): NativeTextPaint = BrowserTextPaint()

actual fun DrawScope.drawNativeText(text: String, x: Float, y: Float, paint: NativeTextPaint) {
    val layout = (paint as BrowserTextPaint).layout(text, density)
    val left = if (paint.textAlign == NativeTextPaint.TextAlign.Center) x - layout.size.width / 2f else x
    translate(left, y - layout.firstBaseline) { drawText(layout) }
}

actual fun DrawScope.nativeSaveCount(): Int = drawContext.canvas.nativeCanvas.saveCount

actual fun DrawScope.nativeRestoreToCount(count: Int) { drawContext.canvas.nativeCanvas.restoreToCount(count) }

/** Centred on the text's line box (the browser's text measurer gives no ink bounds), shrunk to [maxWidth]. */
actual fun DrawScope.drawCentredText(text: String, center: Offset, size: Float, color: Color, weight: Int,
    maxWidth: Float) {
    fun layout(px: Float) = measurer(density).measure(text, TextStyle(color = color, fontSize = (px / density).sp,
        fontWeight = FontWeight(weight.coerceIn(1, 1000))))
    var shown = layout(size)
    if (shown.size.width > maxWidth) shown = layout(size * maxWidth / shown.size.width)
    translate(center.x - shown.size.width / 2f, center.y - shown.size.height / 2f) { drawText(shown) }
}
