package org.asyncttwiz.android

import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas

/** The browser has no system fonts: the page sets the typeface once loaded (null: text isn't drawn). */
object TableFonts {
    var typeface: org.jetbrains.skia.Typeface? = null
}

actual class TablePaint actual constructor() {
    private val paint = org.jetbrains.skia.Paint().apply { isAntiAlias = true }
    actual var color: Int
        get() = paint.color
        set(value) { paint.color = value }
    actual var textSize: Float = 12f
    actual var isFakeBoldText: Boolean = false
    private fun font() = org.jetbrains.skia.Font(TableFonts.typeface, textSize).apply { isEmboldened = isFakeBoldText }
    actual fun measureText(text: String): Float = if (TableFonts.typeface == null) 0f else font().measureTextWidth(text)
    actual val fontMetrics: TableFontMetrics
        get() = font().metrics.let { TableFontMetrics(it.ascent, it.descent, it.top, it.bottom) }
    internal fun draw(canvas: org.jetbrains.skia.Canvas, text: String, x: Float, y: Float) {
        if (TableFonts.typeface != null) canvas.drawString(text, x, y, font(), paint)
    }
}

actual fun DrawScope.nativeText(text: String, x: Float, y: Float, paint: TablePaint) {
    paint.draw(drawContext.canvas.nativeCanvas, text, x, y)
}
