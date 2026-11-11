package org.asyncttwiz.android

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** A card and downward placement arrow above a tabletop. */
internal val PlayCardIcon = ImageVector.Builder("Play card onto table", 24.dp, 24.dp, 24f, 24f).apply {
    path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.6f,
        strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(3f, 2f); lineTo(11f, 2f); lineTo(11f, 15f); lineTo(3f, 15f); close()
        moveTo(18f, 4f); lineTo(18f, 15f)
        moveTo(14f, 11f); lineTo(18f, 15f); lineTo(22f, 11f)
        moveTo(2f, 20f); lineTo(22f, 20f)
        moveTo(4f, 20f); lineTo(4f, 23f)
        moveTo(20f, 20f); lineTo(20f, 23f)
    }
}.build()
