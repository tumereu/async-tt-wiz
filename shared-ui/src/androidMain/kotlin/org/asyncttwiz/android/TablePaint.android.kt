package org.asyncttwiz.android

import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas

actual class TablePaint actual constructor() {
    val native = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    actual var color: Int
        get() = native.color
        set(value) { native.color = value }
    actual var textSize: Float
        get() = native.textSize
        set(value) { native.textSize = value }
    actual var isFakeBoldText: Boolean
        get() = native.isFakeBoldText
        set(value) { native.isFakeBoldText = value }
    actual fun measureText(text: String): Float = native.measureText(text)
    actual val fontMetrics: TableFontMetrics
        get() = native.fontMetrics.let { TableFontMetrics(it.ascent, it.descent, it.top, it.bottom) }
}

actual fun DrawScope.nativeText(text: String, x: Float, y: Float, paint: TablePaint) {
    drawContext.canvas.nativeCanvas.drawText(text, x, y, paint.native)
}
