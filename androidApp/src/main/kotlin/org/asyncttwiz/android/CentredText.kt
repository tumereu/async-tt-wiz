package org.asyncttwiz.android

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb

/** The paint and bounds [drawCentredText] reuses on the drawing thread. */
private val centredPaint = ThreadLocal.withInitial { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG) }
private val centredBounds = ThreadLocal.withInitial { android.graphics.Rect() }

/**
 * Draws [text] [size] px tall in [color] at font [weight] with its ink centred exactly on [center] (owner gallery review
 * 2026-11-06): the letters' own outline is centred, not the font's line box, so a "G", a "2" or a "12" sits in the middle
 * of its badge however the font places it on its line. With [maxWidth] the text is drawn smaller when its ink would be
 * wider than that, so a longer number fits the same badge.
 */
internal fun DrawScope.drawCentredText(text: String, center: Offset, size: Float, color: Color, weight: Int,
    maxWidth: Float = Float.POSITIVE_INFINITY) {
    val paint = centredPaint.get()!!
    paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, weight, false)
    paint.textSize = size
    paint.color = color.toArgb()
    paint.textAlign = android.graphics.Paint.Align.LEFT
    val bounds = centredBounds.get()!!
    paint.getTextBounds(text, 0, text.length, bounds)
    if (bounds.width() > maxWidth) {
        paint.textSize = size * maxWidth / bounds.width()
        paint.getTextBounds(text, 0, text.length, bounds)
    }
    drawContext.canvas.nativeCanvas.drawText(text, center.x - (bounds.left + bounds.right) / 2f,
        center.y - (bounds.top + bounds.bottom) / 2f, paint)
}
