package org.asyncttwiz.android

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
import androidx.compose.ui.graphics.toArgb
import org.asyncttwiz.protocol.*
import org.asyncttwiz.appui.NativeTextPaint
import org.asyncttwiz.appui.NativeColors
import org.asyncttwiz.appui.drawNativeText
import org.asyncttwiz.appui.boundedLruMap

/** Retained across tap endpoints: don't rebuild every text metric/path on the first rotation frame. */
class TableDrawCache(density: Float) {
    val paint = NativeTextPaint().apply { color = NativeColors.DKGRAY; textSize = 11 * density }
    val layerPaint = androidx.compose.ui.graphics.Paint()
    private data class TextKey(val text: String, val size: Float, val bold: Boolean)
    private data class FaceKey(val width: Float, val height: Float, val token: Boolean)
    private fun <K, V> bounded() = boundedLruMap<K, V>(256)
    private val measurements = bounded<TextKey, Float>()
    private val centerOffsets = bounded<Float, Float>()
    private val outlines = bounded<FaceKey, androidx.compose.ui.graphics.Path>()
    fun measure(value: String): Float = measurements.getOrPut(TextKey(value, paint.textSize, paint.isFakeBoldText)) { paint.measureText(value) }
    fun centerOffset(): Float = centerOffsets.getOrPut(paint.textSize) { paint.fontMetrics().let { (it.ascent + it.descent) / 2 } }
    fun outline(width: Float, height: Float, token: Boolean) = outlines.getOrPut(FaceKey(width, height, token)) {
        cardSilhouette(width, height, token, 0f, 0f, width / 14f)
    }
}

/** One drawTable pass: its paint, scaled text helpers and the card-face drawing every layer shares. */
class TableCardPainter(val density: Float, scene: TableScene, val cache: TableDrawCache,
    val artwork: Map<String, ImageBitmap>) {
    fun xy(x: Float, y: Float) = Offset(x * density, y * density)
    fun size(w: Float, h: Float) = Size(w * density, h * density)
    val paint = cache.paint
    val titleScale = scene.appearance.text(CardTextScalePart.TITLE)
    val noteScale = scene.appearance.text(CardTextScalePart.VALUES)
    val valueScale = scene.appearance.text(CardTextScalePart.VALUES)
    val titleBand = maxOf(11f, 7f * titleScale + 3f)
    val artBottom = 40f - maxOf(10f, 7f * valueScale + 3f)
    fun DrawScope.text(value: String, x: Float, y: Float, max: Int = 16) {
        drawNativeText(value.take(max), x * density, y * density, paint)
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
        drawNativeText(value, x, baseline, paint)
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
        zoneGlyph(i.zone, cx, cy, z, ink)
    }
    /** The glyph of a pile's [zone] (a tombstone, a portal, the Stack's layers, a book), centred on [cx], [cy] at scale [z]: an
     * empty pile's, and the zone badge of a hand row card from another zone (ZON-079). */
    fun DrawScope.zoneGlyph(zone: PlayZone?, cx: Float, cy: Float, z: Float, ink: Color) {
        fun line(a: Float, b: Float, c: Float, d: Float) = drawLine(ink,
            xy(cx + a * z, cy + b * z), xy(cx + c * z, cy + d * z), 1.4f * density * z)
        when (zone) {
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
    /**
     * TBL-135 (owner, 2026-11-11): the card's counters, but the one its corner shows, as small bubbles with their number on its
     * art ([CounterBubbles] says how each looks): left to right along the art's bottom, a further row above when they don't fit.
     */
    fun DrawScope.counterBubbles(i: TableItem, x: Float, y: Float, z: Float) {
        if (i.counterBubbles.isEmpty()) return
        val height = maxOf(7f, 5.5f * valueScale + 1.5f)
        paint.textSize = height * .7f * z * density
        paint.isFakeBoldText = true
        var left = 1.5f
        var bottom = artBottom - 1f
        for (counter in i.counterBubbles) {
            val look = CounterBubbles.look(counter)
            val width = maxOf(height, cache.measure(look.text) / (z * density) + 3f).coerceAtMost(24f)
            if (left > 1.5f && left + width > 26.5f) { left = 1.5f; bottom -= height + .75f }
            val top = bottom - height
            val corner = CornerRadius(height / 2 * z * density)
            drawRoundRect(look.fill, xy(x + left * z, y + top * z), size(width * z, height * z), corner)
            drawRoundRect(look.edge, xy(x + left * z, y + top * z), size(width * z, height * z), corner, style = Stroke(.6f * z * density))
            val previous = paint.color
            paint.color = look.ink.toArgb()
            val textWidth = minOf(cache.measure(look.text) / density, (width - 2f) * z)
            clipRect((x + left * z) * density, (y + top * z) * density, (x + (left + width) * z) * density, (y + bottom * z) * density) {
                text(look.text, x + (left + width / 2) * z - textWidth / 2, y + (top + height / 2) * z - cache.centerOffset() / density, look.text.length)
            }
            paint.color = previous
            left += width + .75f
        }
        paint.isFakeBoldText = false
        paint.textSize = 11 * density
    }
    /**
     * TBL-136 effects marker (owner, 2026-11-12): a small violet sparkle in the card's top-left corner while a continuous effect
     * other than counters applies to it ([TableItem.effectsMarker]; Inspect lists them). A token's sits inside its cut corner.
     */
    fun DrawScope.effectsMarker(i: TableItem, x: Float, y: Float, z: Float) {
        if (!i.effectsMarker) return
        val r = 3.2f * z
        val inset = if (i.token) 5.2f * z else 3.4f * z
        val cx = (x + inset) * density; val cy = (y + inset) * density
        val outer = r * density; val inner = outer * .32f
        val star = androidx.compose.ui.graphics.Path().apply {
            moveTo(cx, cy - outer)
            lineTo(cx + inner, cy - inner); lineTo(cx + outer, cy); lineTo(cx + inner, cy + inner)
            lineTo(cx, cy + outer); lineTo(cx - inner, cy + inner); lineTo(cx - outer, cy); lineTo(cx - inner, cy - inner)
            close()
        }
        drawPath(star, Color.White, style = Stroke(1.1f * z * density))
        drawPath(star, AutomatumViolet)
    }
    /**
     * ZON-079 (owner, 2026-11-12): a card of the hand row from another zone: its pile's zone as a badge in its bottom-left
     * corner (the empty pile's glyph, white in a dark teal disc ringed in white).
     */
    fun DrawScope.handZoneBadge(i: TableItem, x: Float, y: Float, cardWidth: Float, cardHeight: Float) {
        val zone = i.handZone ?: return
        val z = cardWidth / 28f
        val r = 7.5f * maxOf(1f, z * .8f)
        // Inside the room its outline leaves around the card (drawing is clipped there)
        val cx = x + r - 2f; val cy = y + cardHeight - r + 2f
        drawCircle(HandRowBadgeColor, r * density, xy(cx, cy))
        drawCircle(Color.White, r * density, xy(cx, cy), style = Stroke(density))
        zoneGlyph(zone, cx, cy + r * .06f, r / 13f, Color.White)
    }
    fun DrawScope.cardAnnotations(i: TableItem, x: Float, y: Float, z: Float) {
        counterBubbles(i, x, y, z)
        effectsMarker(i, x, y, z)
        // TBL-113: what Automatum changed on the card besides P/T, one violet-text chip under the title
        if (i.automatumMarks.isEmpty()) return
        // Use the complete font bounds plus padding, not the old fixed art band.
        paint.textSize = 7 * noteScale * z * density
        val noteMetrics = paint.fontMetrics()
        val noteHeight = (noteMetrics.bottom - noteMetrics.top) / (z * density) + 2f
        val slot = automatumMarksSlot(titleBand, noteHeight)
        val value = i.automatumMarks.joinToString(", ").take(32)
        // TBL-133: on a small card a state's word shrinks a little rather than be cut
        if (i.automatumState) {
            val word = cache.measure(i.automatumMarks.first()) / (z * density) + 2f
            if (word > slot.width) paint.textSize *= maxOf(MIN_STATE_TEXT, slot.width / word)
        }
        val b = slot.copy(width = minOf(slot.width, cache.measure(value) / (z * density) + 2f))
        drawRoundRect(Color.White, xy(x + b.x * z, y + b.y * z), size(b.width * z, b.height * z), CornerRadius(z * density))
        paint.color = EmblemChipColor.toArgb()
        val baseline = y + (b.y + b.height / 2) * z - (noteMetrics.top + noteMetrics.bottom) / (2 * density)
        // Only horizontal clipping: every line retains its complete vertical extent.
        clipRect((x + b.x * z) * density, y * density, (x + b.right * z) * density, (y + 40 * z) * density) {
            text(value, x + (b.x + 1f) * z, baseline, value.length)
        }
        paint.color = NativeColors.DKGRAY
        paint.textSize = 7 * noteScale * z * density
    }
    fun DrawScope.cardFace(i: TableItem, x: Float, y: Float, color: Color, title: String) {
        translate(x * density, y * density) {
        val x = 0f; val y = 0f
        val z = if (i.kind == TableItemKind.CARD) i.faceTransform().width / 28f else i.bounds.width / 28f
        val cardWidth = 28f * z; val cardHeight = 40f * z
        val corner = if (i.eotCorner.isEmpty() && !i.cornerHourglass) i.corner else "⌛${i.corner}"
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
            paint.color = if (lethal) NativeColors.WHITE else 0xffb3261e.toInt()
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
            if (i.cornerColor != null) paint.color = i.cornerColor
            else if (i.eotCorner.isNotEmpty()) paint.color = EotPowerToughnessColor
            else if (i.changedPowerToughness) paint.color = ChangedPowerToughnessColor
            fittedText(corner, x + cardWidth - 4 * z, y + cardHeight - 3 * z, badgeWidth - 4 * z, right = true)
            paint.color = previousColor
        }
        val ability = i.stackAbility
        if (ability != null && !i.covered) { abilityFace(i, ability, color, z); return@translate }
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
                val metrics = paint.fontMetrics()
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
        if (i.cornerColor != null) paint.color = i.cornerColor
        else if (i.eotCorner.isNotEmpty()) paint.color = EotPowerToughnessColor
        else if (i.changedPowerToughness) paint.color = ChangedPowerToughnessColor
        fittedText(corner, x + cardWidth - titleInset, y + cardHeight - 3 * z, cardWidth - 2 * titleInset, right = true)
        paint.color = previousColor
        damageBadge()
        cardAnnotations(i, x, y, z)
        paint.textSize = 11 * density
        }
    }

    /**
     * STK-046: a triggered or activated ability on the Stack, as MTG Arena draws one: a token smaller than a card, dark,
     * with the controller's outline, the source's art filling it (its type glyph while the art is missing) and a small
     * chip at its bottom: "T" for a triggered ability, "A" for an activated one. No name or text: Inspect shows those
     * (STK-050). Drawn at the origin of a card of width 28 [z].
     */
    private fun DrawScope.abilityFace(i: TableItem, ability: StackAbilityShown, color: Color, z: Float) {
        val body = TableBounds(2f * z, 4f * z, 24f * z, 32f * z)
        val radius = CornerRadius(4f * z * density)
        val shape = androidx.compose.ui.graphics.Path().apply {
            addRoundRect(androidx.compose.ui.geometry.RoundRect(body.x * density, body.y * density, body.right * density,
                body.bottom * density, radius))
        }
        drawPath(shape, AbilityTokenColor)
        val art = i.artwork?.key?.let(artwork::get)
        clipPath(shape) {
            if (art != null) drawTableArtwork(art, body)
            else {
                drawRect(AbilityTokenArtColor, xy(body.x, body.y), size(body.width, body.height))
                paint.textSize = 12 * z * density; paint.color = NativeColors.WHITE
                text(i.typeIcon, body.center.x - cache.measure(i.typeIcon) / density / 2, body.center.y + 4f * z, i.typeIcon.length)
            }
        }
        drawPath(shape, color, style = Stroke(1.5f * density * z))
        // The kind chip at the bottom, centred
        val letter = ability.letter
        paint.color = NativeColors.WHITE; paint.isFakeBoldText = true
        paint.textSize = 5.5f * z * density
        val chip = TableBounds(body.center.x - 4f * z, body.bottom - 9f * z, 8f * z, 7f * z)
        drawRoundRect(AutomatumViolet, xy(chip.x, chip.y), size(chip.width, chip.height), CornerRadius(2f * z * density))
        drawRoundRect(Color.White, xy(chip.x, chip.y), size(chip.width, chip.height), CornerRadius(2f * z * density),
            style = Stroke(.7f * z * density))
        text(letter, chip.center.x - cache.measure(letter) / density / 2, chip.center.y - cache.centerOffset() / density, letter.length)
        paint.isFakeBoldText = false
        paint.color = NativeColors.DKGRAY
        paint.textSize = 11 * density
    }
}

/** STK-046: an ability token's body, its art band while the art is missing, and the Inspect panel's text (STK-050). */
val AbilityTokenColor = Color(0xff2e2838)
val AbilityTokenArtColor = Color(0xff4a4259)
val AbilityTokenTextColor = Color(0xffe8e2f0)

/** How far a state's word on a card's chip may shrink to fit (TBL-133). */
private const val MIN_STATE_TEXT = .72f
