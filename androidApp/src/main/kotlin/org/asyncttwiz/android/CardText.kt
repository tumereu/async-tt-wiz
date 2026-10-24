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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** Original letter badges, not Magic artwork, a symbol font, or official symbol shapes. */
@Composable
internal fun CardText(text: String, modifier: Modifier = Modifier, style: TextStyle = LocalTextStyle.current,
    maxLines: Int = Int.MAX_VALUE) {
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
    Text(annotated, modifier, style = style, maxLines = maxLines, inlineContent = inline)
}

@Composable
private fun CardSymbol(token: String) {
    val parts = token.split('/')
    BoxWithConstraints(Modifier.fillMaxSize().clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
        val textSize = with(LocalDensity.current) { maxHeight.toSp() }
        Canvas(Modifier.fillMaxSize()) {
            val radius = size.minDimension * .47f
            drawCircle(symbolColor(parts.first()), radius)
            if (parts.size > 1) drawArc(symbolColor(parts[1]), -90f, 180f, useCenter = true,
                topLeft = center - Offset(radius, radius), size = Size(radius * 2, radius * 2))
            drawCircle(Color(0xff536066), radius, style = Stroke(size.minDimension * .045f))
        }
        Text(token, color = Color(0xff182329), fontWeight = FontWeight.Bold,
            fontSize = textSize * (when (token.length) { 1 -> .66f; 2 -> .57f; 3 -> .43f; else -> .31f }),
            letterSpacing = 0.sp, textAlign = TextAlign.Center, maxLines = 1,
            // Use the inherited text size, but not its potentially much taller paragraph line height.
            style = TextStyle(lineHeight = textSize))
    }
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
