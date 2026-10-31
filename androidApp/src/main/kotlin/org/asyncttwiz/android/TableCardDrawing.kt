package org.asyncttwiz.android

import android.graphics.Paint
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import org.asyncttwiz.protocol.*

/** Retained across tap endpoints: don't rebuild every text metric/path on the first rotation frame. */
internal class TableDrawCache(density: Float) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.DKGRAY; textSize = 11 * density }
    val layerPaint = androidx.compose.ui.graphics.Paint()
    private data class TextKey(val text: String, val size: Float, val bold: Boolean)
    private data class FaceKey(val width: Float, val height: Float, val token: Boolean)
    private fun <K, V> bounded() = object : java.util.LinkedHashMap<K, V>(256, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?) = size > 256
    }
    private val measurements = bounded<TextKey, Float>()
    private val centerOffsets = bounded<Float, Float>()
    private val outlines = bounded<FaceKey, androidx.compose.ui.graphics.Path>()
    fun measure(value: String): Float = measurements.getOrPut(TextKey(value, paint.textSize, paint.isFakeBoldText)) { paint.measureText(value) }
    fun centerOffset(): Float = centerOffsets.getOrPut(paint.textSize) { paint.fontMetrics.let { (it.ascent + it.descent) / 2 } }
    fun outline(width: Float, height: Float, token: Boolean) = outlines.getOrPut(FaceKey(width, height, token)) {
        cardSilhouette(width, height, token, 0f, 0f, width / 14f)
    }
}

/** One drawTable pass: its paint, scaled text helpers and the card-face drawing every layer shares. */
internal class TableCardPainter(val density: Float, scene: TableScene, val cache: TableDrawCache,
    val artwork: Map<String, ImageBitmap>) {
    fun xy(x: Float, y: Float) = Offset(x * density, y * density)
    fun size(w: Float, h: Float) = Size(w * density, h * density)
    val paint = cache.paint
    val titleScale = scene.appearance.text(CardTextScalePart.TITLE)
    val noteScale = scene.appearance.text(CardTextScalePart.NOTE)
    val valueScale = scene.appearance.text(CardTextScalePart.VALUES)
    val titleBand = maxOf(11f, 7f * titleScale + 3f)
    val artBottom = 40f - maxOf(10f, 7f * valueScale + 3f)
    fun DrawScope.text(value: String, x: Float, y: Float, max: Int = 16) {
        drawContext.canvas.nativeCanvas.drawText(value.take(max), x * density, y * density, paint)
    }
    fun DrawScope.fittedText(value: String, x: Float, y: Float, width: Float, right: Boolean = false) {
        val left = if (right) x - width else x
        val start = if (right) x - minOf(width, cache.measure(value) / density) else x
        clipRect(left * density, 0f, (left + width) * density, size.height) {
            text(value, start, y, value.length)
        }
    }
    fun DrawScope.centeredText(value: String, bounds: TableBounds, maxSize: Float) {
        paint.textSize = maxSize * density
        val available = (bounds.width - 8f).coerceAtLeast(1f) * density
        val measured = cache.measure(value)
        if (measured > available) paint.textSize *= available / measured
        val x = bounds.center.x * density - cache.measure(value) / 2
        val baseline = bounds.center.y * density - cache.centerOffset()
        drawContext.canvas.nativeCanvas.drawText(value, x, baseline, paint)
    }
    fun DrawScope.emptySlot(i: TableItem, color: Color) {
        val z = i.bounds.width / 28f
        val x = i.bounds.x; val y = i.bounds.y
        val w = i.bounds.width; val h = i.bounds.height
        val ink = if (i.zone == PlayZone.STACK) Color(0xff795497) else color.copy(alpha = .7f)
        drawRoundRect(ink, xy(x, y), size(w, h), CornerRadius(2 * density), style = Stroke(density))
        val cx = x + w / 2; val cy = y + h / 2
        if (i.temp) {
            // An empty Temp pile is its outline and order number, never a card back.
            val previous = paint.textSize; val previousColor = paint.color
            paint.color = ink.toArgb(); paint.isFakeBoldText = true
            centeredText(i.tempNumber.toString(), i.bounds, 16f * z)
            paint.textSize = previous; paint.color = previousColor; paint.isFakeBoldText = false
            return
        }
        fun line(a: Float, b: Float, c: Float, d: Float) = drawLine(ink,
            xy(cx + a * z, cy + b * z), xy(cx + c * z, cy + d * z), 1.4f * density * z)
        when (i.zone) {
            PlayZone.GRAVEYARD -> {
                val stone = androidx.compose.ui.graphics.Path().apply {
                    moveTo((cx - 7 * z) * density, (cy + 8 * z) * density)
                    lineTo((cx - 7 * z) * density, (cy - 3 * z) * density)
                    quadraticTo(cx * density, (cy - 13 * z) * density, (cx + 7 * z) * density, (cy - 3 * z) * density)
                    lineTo((cx + 7 * z) * density, (cy + 8 * z) * density); close()
                }
                drawPath(stone, ink, style = Stroke(1.4f * density * z))
                line(0f, -3f, 0f, 5f); line(-3f, 0f, 3f, 0f); line(-10f, 8f, 10f, 8f)
            }
            PlayZone.EXILE -> {
                drawOval(ink, xy(cx - 7 * z, cy - 10 * z), size(14 * z, 20 * z), style = Stroke(1.4f * density * z))
                drawOval(ink, xy(cx - 3 * z, cy - 6 * z), size(6 * z, 12 * z), style = Stroke(density * z))
                line(-10f, -6f, -7f, -4f); line(7f, 4f, 10f, 6f)
            }
            PlayZone.STACK -> {
                for (dy in listOf(-4f, 2f)) {
                    line(-9f, dy, 0f, dy - 5f); line(0f, dy - 5f, 9f, dy)
                    line(9f, dy, 0f, dy + 5f); line(0f, dy + 5f, -9f, dy)
                }
            }
            PlayZone.LIBRARY -> {
                line(-7f, -8f, 5f, -8f); line(-7f, -8f, -7f, 7f)
                line(-7f, 7f, 5f, 7f); line(5f, -8f, 5f, 7f)
                line(-3f, 10f, 8f, 10f); line(8f, -5f, 8f, 10f)
            }
            else -> Unit
        }
    }
    fun DrawScope.cardAnnotations(i: TableItem, x: Float, y: Float, z: Float) {
        // Hidden cards carry no values; a public face-down card's values are drawn on its back.
        if (i.note.isEmpty() && i.eotNote.isEmpty() && i.counters.isEmpty()) return
        // Use the complete font bounds plus padding, not the old fixed art band.
        paint.textSize = 7 * noteScale * z * density
        val noteMetrics = paint.fontMetrics
        val noteHeight = (noteMetrics.bottom - noteMetrics.top) / (z * density) + 2f
        paint.textSize = 7 * valueScale * z * density
        val valueMetrics = paint.fontMetrics
        val counterHeight = maxOf(10 * valueScale, (valueMetrics.bottom - valueMetrics.top) / (z * density) + 2f)
        val entries = CounterColor.entries.mapNotNull { color -> i.counters[color]?.takeIf(TablePresentation::nonzero)?.let { color to it } }
        val layout = cardAnnotationLayout(titleBand, artBottom, noteHeight, counterHeight, i.note.isNotEmpty(), entries.size, i.eotNote.isNotEmpty())
        fun label(value: String, b: TableBounds, metrics: Paint.FontMetrics) {
            val baseline = y + (b.y + b.height / 2) * z - (metrics.top + metrics.bottom) / (2 * density)
            // Only horizontal clipping: every line retains its complete vertical extent.
            clipRect((x + b.x * z) * density, y * density,
                (x + b.right * z) * density, (y + 40 * z) * density) {
                text(value, x + (b.x + 1f) * z, baseline, value.length)
            }
        }
        layout.note?.let { slot ->
            paint.textSize = 7 * noteScale * z * density
            val b = slot.copy(width = minOf(slot.width, cache.measure(i.note.take(32)) / (z * density) + 2f))
            drawRoundRect(Color.White, xy(x + b.x * z, y + b.y * z), size(b.width * z, b.height * z), CornerRadius(z * density))
            paint.color = android.graphics.Color.DKGRAY
            label(i.note.take(32), b, noteMetrics)
        }
        layout.eotNote?.let { slot ->
            val value = "⌛ ${i.eotNote.take(30)}"
            paint.textSize = 7 * noteScale * z * density
            val b = slot.copy(width = minOf(slot.width, cache.measure(value) / (z * density) + 2f))
            drawRoundRect(Color.White, xy(x + b.x * z, y + b.y * z), size(b.width * z, b.height * z), CornerRadius(z * density))
            paint.color = EotPowerToughnessColor
            label(value, b, noteMetrics)
        }
        paint.textSize = 7 * valueScale * z * density
        entries.forEachIndexed { index, (color, value) ->
            val slot = layout.counters[index]
            val width = minOf(slot.width, maxOf(counterHeight, cache.measure(value.take(3)) / (z * density) + 2f))
            val b = slot.copy(width = width)
            drawRoundRect(counterColor(color), xy(x + b.x * z, y + b.y * z), size(b.width * z, b.height * z),
                CornerRadius(minOf(b.width, b.height) * z * density / 2))
            paint.color = android.graphics.Color.DKGRAY
            val shown = value.take(3)
            val glyphs = android.graphics.Rect()
            paint.getTextBounds(shown, 0, shown.length, glyphs)
            val availableWidth = (b.width - 2f).coerceAtLeast(1f) * z * density
            val availableHeight = (b.height - 2f).coerceAtLeast(1f) * z * density
            val fit = minOf(1f, availableWidth / glyphs.width().coerceAtLeast(1),
                availableHeight / glyphs.height().coerceAtLeast(1))
            if (fit < 1f) {
                paint.textSize *= fit
                paint.getTextBounds(shown, 0, shown.length, glyphs)
            }
            text(shown, x + b.center.x * z - glyphs.exactCenterX() / density,
                y + b.center.y * z - glyphs.exactCenterY() / density, shown.length)
            paint.textSize = 7 * valueScale * z * density
        }
    }
    fun DrawScope.cardFace(i: TableItem, x: Float, y: Float, color: Color, title: String) {
        translate(x * density, y * density) {
        val x = 0f; val y = 0f
        val z = if (i.kind == TableItemKind.CARD) i.faceTransform().width / 28f else i.bounds.width / 28f
        val cardWidth = 28f * z; val cardHeight = 40f * z
        val corner = if (i.eotCorner.isEmpty()) i.corner else "⌛${i.corner}"
        fun damageBadge() {
            if (!TablePresentation.nonzero(i.damage)) return
            val previousColor = paint.color
            paint.textSize = 6 * valueScale * z * density
            val width = (cache.measure(i.damage) / density + 3 * z).coerceAtMost(cardWidth * .42f)
            paint.textSize = 7 * valueScale * z * density
            val ptWidth = cache.measure(corner) / density + 3 * z
            val left = (cardWidth - ptWidth - width - 2 * z).coerceAtLeast(z)
            val height = maxOf(9f, 6 * valueScale + 3) * z
            val top = cardHeight - height - z
            val red = Color(0xffb3261e)
            val lethal = damageIsLethal(i.damage, i.corner)
            drawRoundRect(if (lethal) red else Color.White, xy(left, top), size(width, height), CornerRadius(2 * z * density))
            drawRoundRect(red, xy(left, top), size(width, height), CornerRadius(2 * z * density), style = Stroke(z * density))
            paint.textSize = 6 * valueScale * z * density
            paint.color = if (lethal) android.graphics.Color.WHITE else 0xffb3261e.toInt()
            fittedText(i.damage, left + z, cardHeight - 3 * z, width - 2 * z)
            paint.color = previousColor
        }
        fun cornerBadge() {
            if (i.corner.isEmpty()) return
            paint.textSize = 7 * valueScale * z * density
            val badgeWidth = (cache.measure(corner) / density + 4 * z).coerceAtMost(cardWidth - 4 * z)
            drawRoundRect(Color.White, xy(x + cardWidth - badgeWidth - 2 * z, y + artBottom * z - z),
                size(badgeWidth, maxOf(10f, 7 * valueScale + 3) * z), CornerRadius(z * density))
            val previousColor = paint.color
            if (i.eotCorner.isNotEmpty()) paint.color = EotPowerToughnessColor
            else if (i.changedPowerToughness) paint.color = ChangedPowerToughnessColor
            fittedText(corner, x + cardWidth - 4 * z, y + cardHeight - 3 * z, badgeWidth - 4 * z, right = true)
            paint.color = previousColor
        }
        val outline = cache.outline(cardWidth * density, cardHeight * density, i.token)
        drawPath(outline, if (i.covered) Color(0xff506b61) else Color.White)
        val art = i.artwork?.key?.let(artwork::get)
        val mode = tableArtworkMode(i.artwork, art != null)
        if (!i.covered && mode == TableArtworkMode.CUSTOM_CARD) clipPath(outline) {
            drawTableArtwork(requireNotNull(art), TableBounds(x, y, cardWidth, cardHeight))
            cornerBadge()
        }
        drawPath(outline, color,
            style = Stroke(1.5f * density * z, pathEffect = if (i.projection) PathEffect.dashPathEffect(floatArrayOf(5 * density * z, 3 * density * z)) else null))
        if (i.covered) {
            // Only a public face-down Battlefield card carries values while covered (rules v23).
            cornerBadge(); damageBadge(); cardAnnotations(i, x, y, z)
            if (i.coveredTitle.isNotEmpty()) {
                // Its "?" title sits on a white chip like the P/T badge, styled (and blue) like a normal title.
                paint.textSize = 7 * titleScale * z * density; paint.isFakeBoldText = true
                val titleInset = if (i.token) cardWidth * .22f else 2 * z
                val baseline = y + (1 + 7 * titleScale) * z
                val metrics = paint.fontMetrics
                val chipWidth = (cache.measure(i.coveredTitle) / density + 2 * z).coerceAtMost(cardWidth - 2 * titleInset)
                drawRoundRect(Color.White, xy(x + titleInset - z, baseline + metrics.ascent / density),
                    size(chipWidth, (metrics.descent - metrics.ascent) / density), CornerRadius(z * density))
                val textColor = paint.color
                paint.color = i.titleColor
                fittedText(i.coveredTitle, x + titleInset, baseline, chipWidth - 2 * z)
                paint.color = textColor; paint.isFakeBoldText = false
            }
            paint.textSize = 11 * density
            return@translate
        }
        if (mode == TableArtworkMode.CUSTOM_CARD) { damageBadge(); cardAnnotations(i, x, y, z); paint.textSize = 11 * density; return@translate }
        paint.textSize = 7 * titleScale * z * density; paint.isFakeBoldText = true
        val titleInset = if (i.token) cardWidth * .22f else 2 * z
        val textColor = paint.color
        if (i.enteredThisTurn) paint.color = i.titleColor
        fittedText(title, x + titleInset, y + (1 + 7 * titleScale) * z, cardWidth - 2 * titleInset)
        paint.color = textColor
        paint.isFakeBoldText = false
        if (mode == TableArtworkMode.ART_CROP && artBottom > titleBand) drawTableArtwork(requireNotNull(art),
            TableBounds(x + 2 * z, y + titleBand * z, cardWidth - 4 * z, (artBottom - titleBand) * z))
        else if (artBottom > titleBand) {
            paint.textSize = 17 * z * density
            clipRect(x * density, (y + titleBand * z) * density, (x + cardWidth) * density, (y + artBottom * z) * density) {
                text(i.typeIcon, x + (cardWidth - cache.measure(i.typeIcon) / density) / 2, y + cardHeight * .7f, i.typeIcon.length)
            }
        }
        paint.textSize = 7 * valueScale * z * density
        val previousColor = paint.color
        if (i.eotCorner.isNotEmpty()) paint.color = EotPowerToughnessColor
        else if (i.changedPowerToughness) paint.color = ChangedPowerToughnessColor
        fittedText(corner, x + cardWidth - titleInset, y + cardHeight - 3 * z, cardWidth - 2 * titleInset, right = true)
        paint.color = previousColor
        damageBadge()
        cardAnnotations(i, x, y, z)
        paint.textSize = 11 * density
        }
    }
}
