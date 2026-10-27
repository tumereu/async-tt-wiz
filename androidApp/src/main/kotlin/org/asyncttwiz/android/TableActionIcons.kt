package org.asyncttwiz.android

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

internal val MultipleSelectionIcon = ImageVector.Builder("Multiple selection", 24.dp, 24.dp, 24f, 24f).apply {
    path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.7f,
        strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(3f, 17f); lineTo(3f, 2f); lineTo(17f, 2f); lineTo(17f, 4f)
        moveTo(7f, 6f); lineTo(21f, 6f); lineTo(21f, 22f); lineTo(7f, 22f); close()
        moveTo(10f, 14f); lineTo(13f, 17f); lineTo(18f, 11f)
    }
}.build()

internal val HandCardsIcon = ImageVector.Builder("Hand of cards", 24.dp, 24.dp, 24f, 24f).apply {
    path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.5f,
        strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(7f, 5f); lineTo(1f, 8f); lineTo(7f, 22f); lineTo(10f, 21f)
        moveTo(17f, 5f); lineTo(23f, 8f); lineTo(17f, 22f); lineTo(14f, 21f)
        moveTo(8f, 3f); lineTo(16f, 3f); lineTo(16f, 21f); lineTo(8f, 21f); close()
    }
}.build()
