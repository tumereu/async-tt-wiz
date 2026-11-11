package org.asyncttwiz.appui

import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.window.PopupProperties

@Composable actual fun BackHandler(enabled: Boolean, onBack: () -> Unit) {
    androidx.activity.compose.BackHandler(enabled, onBack)
}

@Composable actual fun screenWidthDp(): Int = LocalConfiguration.current.screenWidthDp

@Composable actual fun screenHeightDp(): Int = LocalConfiguration.current.screenHeightDp

@Composable actual fun screenOrientation(): Int = LocalConfiguration.current.orientation

@Composable actual fun displayRefreshRate(): Float = LocalView.current.display?.refreshRate ?: 60f

@Composable actual fun rememberShortNote(): (String) -> Unit {
    val context = LocalContext.current
    return remember(context) { { text -> Toast.makeText(context, text, Toast.LENGTH_SHORT).show() } }
}

@Composable actual fun DialogFillsScreen() {
    val window = (LocalView.current.parent as? DialogWindowProvider)?.window
    SideEffect { window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT) }
}

@Composable actual fun DialogDim(amount: Float) {
    val window = (LocalView.current.parent as? DialogWindowProvider)?.window
    SideEffect { window?.setDimAmount(amount) }
}

actual fun passThroughPopupProperties(): PopupProperties = PopupProperties(
    flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
    dismissOnBackPress = false, dismissOnClickOutside = false, excludeFromSystemGesture = false,
)

actual fun coilImageBitmap(image: coil3.Image): ImageBitmap? = (image as? coil3.BitmapImage)?.bitmap?.asImageBitmap()

private class AndroidTextPaint : NativeTextPaint {
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    override var textSize: Float
        get() = paint.textSize
        set(value) { paint.textSize = value }
    override var color: Int
        get() = paint.color
        set(value) { paint.color = value }
    override var alpha: Int
        get() = paint.alpha
        set(value) { paint.alpha = value }
    override var isFakeBoldText: Boolean
        get() = paint.isFakeBoldText
        set(value) { paint.isFakeBoldText = value }
    override var textAlign: NativeTextPaint.TextAlign
        get() = if (paint.textAlign == android.graphics.Paint.Align.CENTER) NativeTextPaint.TextAlign.Center
            else NativeTextPaint.TextAlign.Left
        set(value) {
            paint.textAlign = when (value) {
                NativeTextPaint.TextAlign.Center -> android.graphics.Paint.Align.CENTER
                NativeTextPaint.TextAlign.Left -> android.graphics.Paint.Align.LEFT
            }
        }
    override fun measureText(text: String): Float = paint.measureText(text)
    override fun fontMetrics(): NativeTextPaint.FontMetrics =
        paint.fontMetrics.let { NativeTextPaint.FontMetrics(it.top, it.ascent, it.descent, it.bottom) }
}

actual fun NativeTextPaint(): NativeTextPaint = AndroidTextPaint()

actual fun DrawScope.drawNativeText(text: String, x: Float, y: Float, paint: NativeTextPaint) {
    drawContext.canvas.nativeCanvas.drawText(text, x, y, (paint as AndroidTextPaint).paint)
}

actual fun DrawScope.nativeSaveCount(): Int = drawContext.canvas.nativeCanvas.saveCount

actual fun DrawScope.nativeRestoreToCount(count: Int) { drawContext.canvas.nativeCanvas.restoreToCount(count) }

/** The paint and bounds [drawCentredText] reuses on the drawing thread. */
private val centredPaint = ThreadLocal.withInitial { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG) }
private val centredBounds = ThreadLocal.withInitial { android.graphics.Rect() }

actual fun DrawScope.drawCentredText(text: String, center: Offset, size: Float, color: Color, weight: Int,
    maxWidth: Float) {
    val paint: android.graphics.Paint = centredPaint.get()
    paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, weight, false)
    paint.textSize = size
    paint.color = color.toArgb()
    paint.textAlign = android.graphics.Paint.Align.LEFT
    val bounds: android.graphics.Rect = centredBounds.get()
    paint.getTextBounds(text, 0, text.length, bounds)
    if (bounds.width() > maxWidth) {
        paint.textSize = size * maxWidth / bounds.width()
        paint.getTextBounds(text, 0, text.length, bounds)
    }
    drawContext.canvas.nativeCanvas.drawText(text, center.x - (bounds.left + bounds.right) / 2f,
        center.y - (bounds.top + bounds.bottom) / 2f, paint)
}

actual fun edgeToEdgeDialogProperties(): androidx.compose.ui.window.DialogProperties =
    androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
