package org.asyncttwiz.android

import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/** Proportional 45-degree cuts remain visible even on an 18dp-wide zoomed-out card.
 * Only the drawing is cut: layout and pointer bounds remain the full rectangle. */
internal fun cardSilhouette(width: Float, height: Float, token: Boolean, x: Float = 0f, y: Float = 0f,
    radius: Float = 2f): Path = Path().apply {
    if (token) {
        val cut = minOf(width, height) * .22f
        moveTo(x + cut, y); lineTo(x + width - cut, y)
        lineTo(x + width, y + cut); lineTo(x + width, y + height - cut)
        lineTo(x + width - cut, y + height); lineTo(x + cut, y + height)
        lineTo(x, y + height - cut); lineTo(x, y + cut); close()
    } else addRoundRect(RoundRect(Rect(x, y, x + width, y + height), CornerRadius(radius)))
}

internal fun cardShape(token: Boolean): Shape = object : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density) =
        Outline.Generic(cardSilhouette(size.width, size.height, token, radius = with(density) { 2.dp.toPx() }))
}

/** Preview artwork and its owner-colored outline share the table silhouette. */
internal fun Modifier.cardFrame(token: Boolean, owner: Color, projection: Boolean = false): Modifier =
    clip(cardShape(token)).background(Color.White).drawWithContent {
        drawContent()
        val inset = 1.dp.toPx()
        drawPath(cardSilhouette(size.width - 2 * inset, size.height - 2 * inset, token, inset, inset,
            2.dp.toPx()), owner, style = Stroke(2.dp.toPx(), pathEffect = if (projection)
                PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 3.dp.toPx())) else null))
    }
