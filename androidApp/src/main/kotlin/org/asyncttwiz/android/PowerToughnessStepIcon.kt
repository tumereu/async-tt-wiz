package org.asyncttwiz.android

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Which side of a single-value P/T step changes: +1, −1 or 0 (drawn as a centre dot). */
internal data class PowerToughnessStep(val power: Int, val toughness: Int, val untilEndOfTurn: Boolean)

private val stepPattern = Regex("""([+−-][01])/([+−-][01])""")

/** "P/T +1/+0", "EOT P/T +0/−1", or the bare ring labels "+1/+0" / "EOT −1/−0". */
internal fun powerToughnessStep(name: String): PowerToughnessStep? {
    val match = stepPattern.find(name) ?: return null
    fun sign(value: String) = if (value.drop(1) == "0") 0 else if (value[0] == '+') 1 else -1
    val (power, toughness) = match.destructured
    return PowerToughnessStep(sign(power), sign(toughness), name.trimStart().startsWith("EOT", ignoreCase = true))
}

private val stepIcons = mutableMapOf<PowerToughnessStep, ImageVector>()

/** "+/·", "·/−" ...; the until-end-of-turn version lifts the symbols and adds a small hourglass below. */
internal fun powerToughnessStepIcon(step: PowerToughnessStep): ImageVector = stepIcons.getOrPut(step) {
    val y = if (step.untilEndOfTurn) 9f else 12f
    fun PathBuilder.symbol(x: Float, sign: Int) {
        if (sign == 0) return
        moveTo(x - 3.5f, y); lineTo(x + 3.5f, y)
        if (sign > 0) { moveTo(x, y - 3.5f); lineTo(x, y + 3.5f) }
    }
    ImageVector.Builder("P/T step", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            symbol(5.5f, step.power)
            symbol(18.5f, step.toughness)
            moveTo(10f, y + 6.5f); lineTo(14f, y - 6.5f)
        }
        for ((x, sign) in listOf(5.5f to step.power, 18.5f to step.toughness)) if (sign == 0)
            path(fill = SolidColor(Color.Black)) {
                moveTo(x - 1.7f, y); arcTo(1.7f, 1.7f, 0f, true, true, x + 1.7f, y)
                arcTo(1.7f, 1.7f, 0f, true, true, x - 1.7f, y); close()
            }
        if (step.untilEndOfTurn) path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.4f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(8.5f, 17f); lineTo(15.5f, 17f)
            moveTo(8.5f, 23f); lineTo(15.5f, 23f)
            moveTo(9.8f, 17f); lineTo(14.2f, 23f)
            moveTo(14.2f, 17f); lineTo(9.8f, 23f)
        }
    }.build()
}
