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
    private data class IconKey(val type: String, val size: Float)
    private val icons = bounded<IconKey, androidx.compose.ui.graphics.Path>()
    /** TBL-233: card type [type]'s icon [size] px wide ([CardTypeIcons.path]); empty for a type without one. */
    fun typeIcon(type: String, size: Float) = icons.getOrPut(IconKey(type, size)) {
        CardTypeIcons.path(type, size) ?: androidx.compose.ui.graphics.Path()
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
    fun DrawScope.zoneGlyph(zone: PlayZone?, cx: Float, cy: Float, z: Float, ink: Color) = drawZoneGlyph(zone, cx, cy, z, ink, density)
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
        drawZoneBadge(zone, x + r - 2f, y + cardHeight - r + 2f, r, density)
    }
    /**
     * TBL-233 (owner, 2026-11-21): the card's types as small icons ([CardTypeIcons]) at its bottom left, from [left] up to [right]
     * (card-local dp), as many as fit; on the frame's bottom row centred on [centerY], else ([chip]: a full picture, a custom image,
     * a face-down card) on a small white chip at the card's bottom.
     */
    fun DrawScope.typeIcons(i: TableItem, z: Float, left: Float, right: Float, centerY: Float, chip: Boolean, rowHeight: Float) {
        if (i.cardTypes.isEmpty()) return
        val iconSize = minOf((TYPE_ICON_SIZE * valueScale + .5f) * z, rowHeight - 2f * z).coerceAtLeast(2f * z)
        val gap = .8f * z
        val pad = if (chip) .9f * z else 0f
        val shown = CardTypeIcons.fit(i.cardTypes, right - left - 2 * pad + gap, iconSize + gap)
        if (shown.isEmpty()) return
        val top = centerY - iconSize / 2
        if (chip) drawRoundRect(Color.White, xy(left, top - pad), size(shown.size * (iconSize + gap) - gap + 2 * pad, iconSize + 2 * pad),
            CornerRadius(z * density))
        var at = left + pad
        for (type in shown) {
            translate(at * density, top * density) { drawPath(cache.typeIcon(type, iconSize * density), CardTypeIcons.Ink) }
            at += iconSize + gap
        }
    }
    /** TBL-233: the white chip's centre for [typeIcons] on a card without the frame's bottom row, [cardHeight] tall. */
    private fun typeIconChipCenter(cardHeight: Float, z: Float): Float =
        cardHeight - 1.6f * z - (TYPE_ICON_SIZE * valueScale + .5f) * z / 2 - .9f * z
    /** TBL-024: where the damage badge's left edge is (card-local dp) for [corner] on a [cardWidth] card, or null without damage. */
    private fun damageLeft(i: TableItem, corner: String, z: Float, cardWidth: Float): Float? {
        if (!TablePresentation.nonzero(i.damage)) return null
        val previous = paint.textSize
        paint.textSize = 6 * valueScale * z * density
        val width = (cache.measure(i.damage) / density + 3 * z).coerceAtMost(cardWidth * .42f)
        paint.textSize = 7 * valueScale * z * density
        val ptWidth = cache.measure(corner) / density + 3 * z
        paint.textSize = previous
        return (cardWidth - ptWidth - width - 2 * z).coerceAtLeast(z)
    }
    /**
     * TBL-235 (owner, 2026-11-21): the card's subtype tags ([SubtypeTags.shown]), dark pills with white text at the art's top right,
     * one under another from [top] (card units), sized with the counters' bubbles (TBL-135).
     */
    fun DrawScope.subtypeTags(i: TableItem, x: Float, y: Float, z: Float, top: Float) {
        if (i.subtypeTags.isEmpty()) return
        val height = maxOf(7f, 5.5f * valueScale + 1.5f)
        // Above the P/T row: as many rows as the art has room for
        val rows = ((artBottom - .5f - top + .75f) / (height + .75f)).toInt()
        val tags = SubtypeTags.shown(i.subtypeTags, rows)
        if (tags.isEmpty()) return
        paint.textSize = height * .7f * z * density
        val previous = paint.color
        paint.color = SubtypeTags.Text.toArgb()
        var at = top
        for (tag in tags) {
            val width = (cache.measure(tag) / (z * density) + 3f).coerceAtMost(25f)
            val left = 28f - 1.5f - width
            val corner = CornerRadius(height / 2 * z * density)
            drawRoundRect(SubtypeTags.Fill, xy(x + left * z, y + at * z), size(width * z, height * z), corner)
            drawRoundRect(Color.White, xy(x + left * z, y + at * z), size(width * z, height * z), corner, style = Stroke(.5f * z * density))
            val textWidth = minOf(cache.measure(tag) / density, (width - 2f) * z)
            clipRect((x + (left + 1f) * z) * density, (y + at * z) * density, (x + (left + width - 1f) * z) * density, (y + (at + height) * z) * density) {
                text(tag, x + (left + width / 2) * z - textWidth / 2, y + (at + height / 2) * z - cache.centerOffset() / density, tag.length)
            }
            at += height + .75f
        }
        paint.color = previous
        paint.textSize = 11 * density
    }
    fun DrawScope.cardAnnotations(i: TableItem, x: Float, y: Float, z: Float) {
        counterBubbles(i, x, y, z)
        effectsMarker(i, x, y, z)
        // TBL-235: the subtype tags start at the art's top, under the chip of what Automatum changed when it has one
        if (i.automatumMarks.isEmpty()) { subtypeTags(i, x, y, z, titleBand + .5f); return }
        // TBL-113: what Automatum changed on the card besides P/T, one violet-text chip under the title
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
        subtypeTags(i, x, y, z, slot.bottom + .75f)
        paint.textSize = 7 * noteScale * z * density
    }
    fun DrawScope.cardFace(i: TableItem, x: Float, y: Float, color: Color, title: String) {
        translate(x * density, y * density) {
        val x = 0f; val y = 0f
        val z = if (i.kind == TableItemKind.CARD) i.faceTransform().width / 28f else i.bounds.width / 28f
        val cardWidth = 28f * z; val cardHeight = 40f * z
        // WEB-022 (owner 2026-11-16): on the desktop hand's large cards the name row, the P/T row and the border don't grow
        // past a sensible size: [tz] scales their text, [ez] the border and the frame around the art (elsewhere both are [z])
        val tz = if (i.handSlot != null) minOf(z, DESKTOP_HAND_TEXT_SCALE) else z
        val ez = if (i.handSlot != null) minOf(z, DESKTOP_HAND_EDGE_SCALE) else z
        val corner = if (!i.cornerHourglass) i.corner else "⌛${i.corner}"
        // TBL-137 (owner notes 2026-11-14): an uncrewed Vehicle's "Crew N", one short line on a white chip just above its P/T
        // at the art's bottom right, where it fits the card's width
        fun crewLine() {
            val crew = i.crew ?: return
            paint.textSize = 6 * valueScale * z * density
            val width = cache.measure(crew) / density + 3 * z
            if (width > cardWidth - 4 * z) return
            val height = maxOf(8f, 6 * valueScale + 2) * z
            val top = y + artBottom * z - height - .5f * z
            drawRoundRect(Color.White, xy(x + cardWidth - 2 * z - width, top), size(width, height), CornerRadius(z * density))
            val previousColor = paint.color
            paint.color = NativeColors.DKGRAY
            fittedText(crew, x + cardWidth - 3.5f * z, top + height - 2 * z, width - 3 * z, right = true)
            paint.color = previousColor
        }
        fun damageBadge() {
            val left = damageLeft(i, corner, z, cardWidth) ?: return
            val previousColor = paint.color
            paint.textSize = 6 * valueScale * z * density
            val width = (cache.measure(i.damage) / density + 3 * z).coerceAtMost(cardWidth * .42f)
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
        /** TBL-233: the left edge of the corner's white badge ([cornerBadge]), or the card's right edge without one. */
        fun cornerBadgeLeft(): Float {
            if (i.corner.isEmpty()) return cardWidth
            val previous = paint.textSize
            paint.textSize = 7 * valueScale * z * density
            val badgeWidth = (cache.measure(corner) / density + 4 * z).coerceAtMost(cardWidth - 4 * z)
            paint.textSize = previous
            return cardWidth - badgeWidth - 2 * z
        }
        /** TBL-233: the type icons on their white chip, on a card without the frame's bottom row; [badge]: its corner badge shows. */
        fun chipIcons(badge: Boolean) {
            val inset = if (i.token) cardWidth * .22f else 1.5f * z
            val right = minOf(if (badge) cornerBadgeLeft() else cardWidth, damageLeft(i, corner, z, cardWidth) ?: cardWidth) - z
            typeIcons(i, z, inset, right, typeIconChipCenter(cardHeight, z), chip = true, rowHeight = cardHeight)
        }
        fun cornerBadge() {
            if (i.corner.isEmpty()) return
            paint.textSize = 7 * valueScale * z * density
            val badgeWidth = (cache.measure(corner) / density + 4 * z).coerceAtMost(cardWidth - 4 * z)
            drawRoundRect(Color.White, xy(x + cardWidth - badgeWidth - 2 * z, y + artBottom * z - z),
                size(badgeWidth, maxOf(10f, 7 * valueScale + 3) * z), CornerRadius(z * density))
            val previousColor = paint.color
            if (i.cornerColor != null) paint.color = i.cornerColor
            else if (i.changedPowerToughness) paint.color = ChangedPowerToughnessColor
            fittedText(corner, x + cardWidth - 4 * z, y + cardHeight - 3 * z, badgeWidth - 4 * z, right = true)
            paint.color = previousColor
        }
        val ability = i.stackAbility
        if (ability != null && !i.covered) { abilityFace(i, ability, color, z); copyMarker(i, x + cardWidth - 5f * z, y + 1f * z, z); return@translate }
        val outline = cache.outline(cardWidth * density, cardHeight * density, i.token)
        drawPath(outline, if (i.covered) Color(0xff506b61) else Color.White)
        val art = i.artwork?.key?.let(artwork::get)
        val mode = tableArtworkMode(i.artwork, art != null, i.fullPicture)
        // TBL-139 (owner 2026-11-16): a full picture is the printed card, its own P/T printed on it: the app's P/T badge only
        // when it says something else (a changed P/T, a loyalty or other counter count)
        val fullPicture = !i.covered && i.fullPicture && mode == TableArtworkMode.CUSTOM_CARD
        if (!i.covered && mode == TableArtworkMode.CUSTOM_CARD) clipPath(outline) {
            drawTableArtwork(requireNotNull(art), TableBounds(x, y, cardWidth, cardHeight))
            if (!fullPicture || fullPictureCorner(i)) cornerBadge()
        }
        // TBL-139: a full picture's owner-colour border is thin, whatever the card's size
        val edge = if (fullPicture) FULL_PICTURE_EDGE else 1.5f * ez
        val dash = if (fullPicture) edge / 1.5f else z
        drawPath(outline, color,
            style = Stroke(edge * density, pathEffect = if (i.projection) PathEffect.dashPathEffect(floatArrayOf(5 * density * dash, 3 * density * dash)) else null))
        if (i.covered) {
            // Only a public face-down Battlefield card carries values while covered (rules v23).
            chipIcons(badge = true); cornerBadge(); damageBadge(); cardAnnotations(i, x, y, z)
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
        if (mode == TableArtworkMode.CUSTOM_CARD) {
            chipIcons(badge = !fullPicture || fullPictureCorner(i)); damageBadge(); cardAnnotations(i, x, y, z)
            paint.textSize = 11 * density; return@translate
        }
        paint.textSize = 7 * titleScale * tz * density; paint.isFakeBoldText = true
        val titleInset = if (i.token) cardWidth * .22f else 2 * ez
        val textColor = paint.color
        if (i.enteredThisTurn) paint.color = i.titleColor
        fittedText(title, x + titleInset, y + (1 + 7 * titleScale) * tz, cardWidth - 2 * titleInset)
        paint.color = textColor
        paint.isFakeBoldText = false
        // The art between the name row and the P/T row (each [tz] tall in card units), inside the frame ([ez])
        val artTop = y + titleBand * tz
        val artEnd = y + cardHeight - (40f - artBottom) * tz
        if (mode == TableArtworkMode.ART_CROP && artEnd > artTop) drawTableArtwork(requireNotNull(art),
            TableBounds(x + 2 * ez, artTop, cardWidth - 4 * ez, artEnd - artTop))
        else if (artEnd > artTop) {
            paint.textSize = 17 * z * density
            clipRect(x * density, artTop * density, (x + cardWidth) * density, artEnd * density) {
                text(i.typeIcon, x + (cardWidth - cache.measure(i.typeIcon) / density) / 2, y + cardHeight * .7f, i.typeIcon.length)
            }
        }
        paint.textSize = 7 * valueScale * tz * density
        val previousColor = paint.color
        if (i.cornerColor != null) paint.color = i.cornerColor
        else if (i.changedPowerToughness) paint.color = ChangedPowerToughnessColor
        fittedText(corner, x + cardWidth - titleInset, y + cardHeight - 3 * tz, cardWidth - 2 * titleInset, right = true)
        paint.color = previousColor
        // TBL-233: the type icons left of the P/T (and of the damage badge) on the bottom row
        if (i.cardTypes.isNotEmpty()) {
            val cornerWidth = if (corner.isEmpty()) 0f else minOf(cache.measure(corner) / density, cardWidth - 2 * titleInset) + 1.5f * z
            val right = minOf(cardWidth - titleInset - cornerWidth, (damageLeft(i, corner, z, cardWidth) ?: cardWidth) - .5f * z)
            // a token's cut corner leaves room nearer its edge at the icons' height than at its title's
            val left = if (i.token) cardWidth * .16f else titleInset
            typeIcons(i, z, left, right, (artEnd + cardHeight) / 2, chip = false, rowHeight = cardHeight - artEnd)
        }
        crewLine()
        damageBadge()
        cardAnnotations(i, x, y, z)
        copyMarker(i, x + cardWidth - 4f * z, y - 1f * z, z)
        paint.textSize = 11 * density
        }
    }

    /**
     * D7 (owner 2026-11-19): the copy marker of a copied spell or ability on the Stack ([TableItem.stackCopy]): a small violet disc
     * with two overlapping white sheets, centred at ([cx], [cy]) in card units [z]. Permanents never have one.
     */
    private fun DrawScope.copyMarker(i: TableItem, cx: Float, cy: Float, z: Float) {
        if (!i.stackCopy || i.covered) return
        val r = 4.2f * z
        drawCircle(AutomatumViolet, r * density, xy(cx, cy))
        drawCircle(Color.White, r * density, xy(cx, cy), style = Stroke(.7f * z * density))
        val w = 2.6f * z; val h = 3.2f * z
        drawRoundRect(Color.White, xy(cx - w * .8f, cy - h * .7f), size(w, h), CornerRadius(.5f * z * density), style = Stroke(.6f * z * density))
        drawRoundRect(AutomatumViolet, xy(cx - w * .2f, cy - h * .3f), size(w, h), CornerRadius(.5f * z * density))
        drawRoundRect(Color.White, xy(cx - w * .2f, cy - h * .3f), size(w, h), CornerRadius(.5f * z * density), style = Stroke(.6f * z * density))
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

/** WEB-022 (owner 2026-11-16): the largest scale of the desktop hand's cards' name and P/T rows, and of their border and the
 * frame around their art (the hand's cards are 5.2 times the phone's; the battlefield's 2). */
const val DESKTOP_HAND_TEXT_SCALE = 2.4f
const val DESKTOP_HAND_EDGE_SCALE = 2f

/** TBL-233: a type icon's width at the P/T & counters text size 1× (card units). */
const val TYPE_ICON_SIZE = 5f

/** TBL-139: a full picture's border width (dp), thin at any card size. */
const val FULL_PICTURE_EDGE = 1.5f

/** TBL-139: whether a full picture ([TableItem.fullPicture]) shows the app's P/T badge over the printed card: only when it
 * says what the printed card doesn't: a changed P/T, or a count (loyalty and other counters) rather than a P/T. */
fun fullPictureCorner(i: TableItem): Boolean = i.corner.isNotEmpty() &&
    (i.cornerColor != null || i.cornerHourglass || i.changedPowerToughness || '/' !in i.corner)

/** How far a state's word on a card's chip may shrink to fit (TBL-133). */
private const val MIN_STATE_TEXT = .72f

/** [drawZoneGlyph]'s points and sizes in dp. */
private fun glyphXy(x: Float, y: Float, density: Float) = Offset(x * density, y * density)
private fun glyphSize(w: Float, h: Float, density: Float) = Size(w * density, h * density)

/** The glyph of a pile's [zone] (a tombstone, a portal, the Stack's layers, a book), centred on [cx], [cy] at scale [z]: an
 * empty pile's, and the zone badge of a hand row card from another zone (ZON-079); [density] px per dp. */
fun DrawScope.drawZoneGlyph(zone: PlayZone?, cx: Float, cy: Float, z: Float, ink: Color, density: Float) {
    fun line(a: Float, b: Float, c: Float, d: Float) = drawLine(ink,
        glyphXy(cx + a * z, cy + b * z, density), glyphXy(cx + c * z, cy + d * z, density), 1.4f * density * z)
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
            drawOval(ink, glyphXy(cx - 7 * z, cy - 10 * z, density), glyphSize(14 * z, 20 * z, density), style = Stroke(1.4f * density * z))
            drawOval(ink, glyphXy(cx - 3 * z, cy - 6 * z, density), glyphSize(6 * z, 12 * z, density), style = Stroke(density * z))
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
 * ZON-079, ZON-136: a pile's zone as a badge, the empty pile's glyph ([drawZoneGlyph]) white in a dark teal disc ringed in white,
 * [r] dp in radius around [cx], [cy] (dp); [density] px per dp. A hand row card from another zone carries it, and the other
 * player's playable list puts it under each card.
 */
fun DrawScope.drawZoneBadge(zone: PlayZone, cx: Float, cy: Float, r: Float, density: Float) {
    drawCircle(HandRowBadgeColor, r * density, Offset(cx * density, cy * density))
    drawCircle(Color.White, r * density, Offset(cx * density, cy * density), style = Stroke(density))
    drawZoneGlyph(zone, cx, cy + r * .06f, r / 13f, Color.White, density)
}
