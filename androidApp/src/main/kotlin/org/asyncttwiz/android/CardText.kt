package org.asyncttwiz.android

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Text with its mana and tap symbols ({G}, {T}, {X}, hybrid, Phyrexian, snow, energy) drawn as symbols (TBL-124, owner
 * playtest 2026-11-05 evening: everywhere text shows them): original letter badges and a drawn turning arrow for tap and
 * untap, not Magic artwork, a symbol font, or official symbol shapes. Its text (semantics, tests) stays the printed words with their braces.
 */
@Composable
internal fun CardText(text: String, modifier: Modifier = Modifier, style: TextStyle = LocalTextStyle.current,
    maxLines: Int = Int.MAX_VALUE, overflow: TextOverflow = TextOverflow.Clip, color: Color = Color.Unspecified) {
    val parts = remember(text) { parseCardText(text) }
    val annotated = remember(parts) { buildAnnotatedString {
        for (part in parts) when (part) {
            is CardTextPart.Plain -> append(part.text)
            is CardTextPart.Symbol -> appendInlineContent(part.token, "{${part.token}}")
        }
    } }
    val symbols = parts.filterIsInstance<CardTextPart.Symbol>().distinct()
    val inline = symbols.associate { symbol -> symbol.token to InlineTextContent(
        Placeholder(1.em, 1.em, PlaceholderVerticalAlign.TextCenter)
    ) { CardSymbol(symbol.token) } }
    Text(annotated, modifier, color = color, style = style, maxLines = maxLines, overflow = overflow, inlineContent = inline)
}

/** One symbol as drawn in card text, filling its parent (e.g. "G", "3", "W/U"); search chips use it at their own size.
 * The tap symbol {T} is a turning arrow, as printed (owner playtest 2026-11-06), and the untap symbol {Q} the same arrow
 * mirrored; the others are letter badges. */
@Composable
internal fun CardSymbol(token: String) {
    val parts = token.split('/')
    val arrow = token == "T" || token == "Q"
    BoxWithConstraints(Modifier.fillMaxSize().clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
        val textSize = with(LocalDensity.current) { maxHeight.toSp() }
        Canvas(Modifier.fillMaxSize()) {
            val radius = size.minDimension * .47f
            drawCircle(symbolColor(parts.first()), radius)
            if (parts.size > 1) drawArc(symbolColor(parts[1]), -90f, 180f, useCenter = true,
                topLeft = center - Offset(radius, radius), size = Size(radius * 2, radius * 2))
            drawCircle(Color(0xff536066), radius, style = Stroke(size.minDimension * .045f))
            if (arrow) scale(if (token == "Q") -1f else 1f, 1f) { turningArrow(radius) }
        }
        if (!arrow) Text(token, color = Color(0xff182329), fontWeight = FontWeight.Bold,
            fontSize = textSize * (when (token.length) { 1 -> .66f; 2 -> .57f; 3 -> .43f; else -> .31f }),
            letterSpacing = 0.sp, textAlign = TextAlign.Center, maxLines = 1,
            // Use the inherited text size, but not its potentially much taller paragraph line height.
            style = TextStyle(lineHeight = textSize))
    }
}

/** A clockwise arrow around the circle's centre: from the left, over the top, its head pointing down at the lower right. */
private fun DrawScope.turningArrow(radius: Float) {
    val ink = Color(0xff182329)
    val arc = radius * .52f
    val width = radius * .2f
    val start = 160f
    val sweep = 225f
    drawArc(ink, start, sweep, useCenter = false, topLeft = center - Offset(arc, arc), size = Size(arc * 2, arc * 2),
        style = Stroke(width, cap = StrokeCap.Butt))
    val end = Math.toRadians((start + sweep).toDouble())
    val tip = center + Offset((arc * cos(end)).toFloat(), (arc * sin(end)).toFloat())
    // Clockwise, the arrow runs a quarter turn ahead of its angle; its head reaches past the arc's end
    val ahead = Offset(-sin(end).toFloat(), cos(end).toFloat())
    val across = Offset(cos(end).toFloat(), sin(end).toFloat())
    val head = width * 1.9f
    drawPath(Path().apply {
        val point = tip + ahead * head
        moveTo(point.x, point.y)
        (tip + across * head * .85f).let { lineTo(it.x, it.y) }
        (tip - across * head * .85f).let { lineTo(it.x, it.y) }
        close()
    }, ink)
}

private fun symbolColor(part: String): Color = when (part) {
    "W" -> Color(0xffffefbd)
    "U" -> Color(0xff8fc9ec)
    "B" -> Color(0xffb8aec9)
    "R" -> Color(0xffefa799)
    "G" -> Color(0xff9dceaa)
    "S" -> Color(0xffceeff3)
    "E" -> Color(0xffffd77b)
    else -> Color(0xffd6dbdd)
}
