package org.asyncttwiz.android

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
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

internal fun DrawScope.drawTable(scene: TableScene, viewer: String, turnPlayer: String, selection: TableSelection, preview: TableGesturePreview,
    links: List<PlayLink> = emptyList(), alphas: Map<String, Float> = emptyMap(), pings: List<RecordedPingChange> = emptyList(),
    projectionLinks: List<ProjectionLink> = emptyList(), annotations: TableAnnotationGeometry, artwork: Map<String, ImageBitmap> = emptyMap(),
    cache: TableDrawCache, pingPhase: Float = 0f, statusModes: Map<String, StatusBubbleMode> = emptyMap(),
    presenceDot: PresenceDot? = null) {
    val density = density
    fun xy(x: Float, y: Float) = Offset(x * density, y * density)
    fun size(w: Float, h: Float) = Size(w * density, h * density)
    val paint = cache.paint
    val titleScale = scene.appearance.text(CardTextScalePart.TITLE)
    val noteScale = scene.appearance.text(CardTextScalePart.NOTE)
    val valueScale = scene.appearance.text(CardTextScalePart.VALUES)
    val titleBand = maxOf(11f, 7f * titleScale + 3f)
    val artBottom = 40f - maxOf(10f, 7f * valueScale + 3f)
    fun text(value: String, x: Float, y: Float, max: Int = 16) {
        drawContext.canvas.nativeCanvas.drawText(value.take(max), x * density, y * density, paint)
    }
    fun fittedText(value: String, x: Float, y: Float, width: Float, right: Boolean = false) {
        val left = if (right) x - width else x
        val start = if (right) x - minOf(width, cache.measure(value) / density) else x
        clipRect(left * density, 0f, (left + width) * density, size.height) {
            text(value, start, y, value.length)
        }
    }
    fun centeredText(value: String, bounds: TableBounds, maxSize: Float) {
        paint.textSize = maxSize * density
        val available = (bounds.width - 8f).coerceAtLeast(1f) * density
        val measured = cache.measure(value)
        if (measured > available) paint.textSize *= available / measured
        val x = bounds.center.x * density - cache.measure(value) / 2
        val baseline = bounds.center.y * density - cache.centerOffset()
        drawContext.canvas.nativeCanvas.drawText(value, x, baseline, paint)
    }
    fun emptySlot(i: TableItem, color: Color) {
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
    fun cardAnnotations(i: TableItem, x: Float, y: Float, z: Float) {
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
    fun cardFace(i: TableItem, x: Float, y: Float, color: Color, title: String) {
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
    scene.viewports.firstOrNull { it.id == "sidebar" }?.bounds?.let { b ->
        drawRect(Color.White, xy(b.x, b.y), size(b.width, b.height))
    }
    for (f in scene.fields) {
        val b = f.gridBounds
        val viewport = scene.world?.horizontalBand(b) ?: f.bounds
        clipRect(viewport.x * density, viewport.y * density, viewport.right * density, viewport.bottom * density) {
        val color = battlefieldColor(f.owner, viewer, turnPlayer, lands = false)
        drawRect(color, xy(viewport.x, b.y), size(viewport.width, b.height))
        val lands = f.landsBounds(f.owner == viewer, scene.camera)
        drawRect(battlefieldColor(f.owner, viewer, turnPlayer, lands = true),
            xy(viewport.x, lands.y), size(viewport.width, lands.height))
        }
    }
    scene.world?.let { world -> world.horizontalBand(TableBounds(0f, scene.fields.first().gridBounds.bottom,
        0f, scene.fields.last().gridBounds.y - scene.fields.first().gridBounds.bottom)) }?.let { b ->
        clipRect(b.x * density, b.y * density, b.right * density, b.bottom * density) {
            drawRect(Color(0xfff5f1df), xy(b.x, b.y), size(b.width, b.height))
            for ((from, to) in scene.combatDividers) drawLine(Color.Black.copy(alpha = CombatStackPresentation.SLOT_DIVIDER_ALPHA),
                xy(from.x, from.y), xy(to.x, to.y), CombatStackPresentation.slotDividerStroke(scene.cardWidth) * density)
            for ((from, to) in scene.combatLaneDividers) drawLine(Color.Black.copy(alpha = CombatStackPresentation.LANE_DIVIDER_ALPHA),
                xy(from.x, from.y), xy(to.x, to.y), CombatStackPresentation.laneDividerStroke(scene.cardWidth) * density)
            for (label in scene.combatLabels) {
                val bounds = label.bounds
                val font = label.fontSize
                val thumb = TableBounds(bounds.x + label.inset, bounds.y + label.padding, label.artworkWidth, label.artworkHeight)
                if (label.cardId != null) {
                    val image = label.artwork?.key?.let(artwork::get)
                    if (image != null) drawTableArtwork(image, thumb)
                    else drawRect(Color(0xffb5b0a0), xy(thumb.x, thumb.y), size(thumb.width, thumb.height))
                } else if (label.accountId != null) {
                    // The defending player's profile avatar, round like everywhere else.
                    val image = label.artwork?.key?.let(artwork::get)
                    val circle = androidx.compose.ui.graphics.Path().apply {
                        addOval(androidx.compose.ui.geometry.Rect(thumb.x * density, thumb.y * density, thumb.right * density, thumb.bottom * density))
                    }
                    clipPath(circle) {
                        drawRect(avatarColor(label.accountId), xy(thumb.x, thumb.y), size(thumb.width, thumb.height))
                        if (image != null) drawTableArtwork(image, thumb)
                    }
                }
                paint.textSize = font * density; paint.isFakeBoldText = true
                label.lines.forEachIndexed { index, line ->
                    fittedText(line, bounds.x + label.textInset, bounds.y + label.padding + font + index * label.lineHeight,
                        label.textWidth)
                }
                paint.isFakeBoldText = false; paint.textSize = 11 * density
            }
        }
    }
    fun endpoint(endpoint: LinkEndpoint): TablePoint? = scene.items.firstOrNull { item ->
        if (endpoint.kind == LinkEndpointKind.CARD) item.cardId == endpoint.id else item.id == "life/${endpoint.id}"
    }?.visibleAnchor
    for (link in links) {
        val a = endpoint(link.first); val b = endpoint(link.second)
        if (a != null && b != null) drawLine(Color(0xff667b89), xy(a.x, a.y), xy(b.x, b.y), 2 * density)
    }
    for (link in projectionLinks) {
        val first = scene.items.firstOrNull { it.cardId == link.first }?.visibleAnchor
        val second = scene.items.firstOrNull { it.cardId == link.second }?.visibleAnchor
        if (first != null && second != null) drawLine(Color(0xff667b89), xy(first.x, first.y), xy(second.x, second.y), 2 * density)
    }
    // HUD surfaces cover world drawings and link lines, including the screen-pinned Stack gutter.
    for (port in scene.viewports.filter { it.hand || it.id.startsWith("dock/") || it.id.startsWith("counters/") }) {
        val b = port.bounds
        drawRect(Color.White, xy(b.x, b.y), size(b.width, b.height))
    }
    for (hand in scene.items.filter { it.kind == TableItemKind.HAND }) {
        val b = hand.bounds
        val border = if (hand.owner == viewer) OwnTableColor else OpponentTableColor
        drawRoundRect(Color.White, xy(b.x, b.y), size(b.width, b.height), CornerRadius(5 * density))
        drawRoundRect(border.copy(alpha = .6f), xy(b.x, b.y), size(b.width, b.height), CornerRadius(5 * density), style = Stroke(density / 2))
        text(hand.value, b.right - 18f, b.center.y + 4f, 4)
    }
    scene.stackRegion?.let { b ->
        val purple = Color(0xff2a1740)
        // Layered translucent contours make a soft shadow in the shared Canvas renderer.
        for (spread in 8 downTo 1) drawRoundRect(purple.copy(alpha = .025f),
            xy(b.x - spread / 2f, b.y + 2f - spread / 2f), size(b.width + spread, b.height + spread),
            CornerRadius(5 * density))
        drawRoundRect(Color(0xffeee3f5), xy(b.x, b.y), size(b.width, b.height), CornerRadius(4 * density))
        drawRoundRect(purple, xy(b.x + 1.5f, b.y + 1.5f), size((b.width - 3f).coerceAtLeast(0f), (b.height - 3f).coerceAtLeast(0f)),
            CornerRadius(3 * density), style = Stroke(3 * density))
    }
    for (panel in scene.cardPanels) {
        val b = panel.bounds
        val color = if (panel.display.author == viewer) OwnTableColor else OpponentTableColor
        drawRoundRect(Color.White, xy(b.x, b.y), size(b.width, b.height), CornerRadius(4 * density))
        drawRoundRect(color.copy(alpha = .6f), xy(b.x, b.y), size(b.width, b.height), CornerRadius(4 * density), style = Stroke(density))
        val port = panel.viewport.bounds
        clipRect(port.x * density, port.y * density, port.right * density, port.bottom * density) {
            for (header in panel.typeHeaders) {
                drawRect(color.copy(alpha = .22f), xy(header.bounds.x, header.bounds.y),
                    size(header.bounds.width, header.bounds.height))
                paint.textSize = 8 * density; paint.isFakeBoldText = true
                val measured = cache.measure(header.title)
                val available = (header.bounds.width - 6f) * density
                if (measured > available) paint.textSize *= available / measured
                fittedText(header.title, header.bounds.x + 3, header.bounds.y + 13, header.bounds.width - 6)
                paint.textSize = 11 * density; paint.isFakeBoldText = false
            }
            scene.panelReorder(preview.point ?: TablePoint(-1f, -1f), preview.scope)?.let { reorder ->
                val before = panel.rows.firstOrNull { it.entry.cardId == reorder.beforeId }?.bounds
                val last = panel.rows.lastOrNull()?.bounds
                val cell = before ?: last
                if (reorder.displayId == panel.display.id && cell != null) {
                    val x = if (before != null) cell.x - 2 else cell.right + 2
                    drawLine(color, xy(x, cell.y), xy(x, cell.bottom), 2 * density)
                }
            }
        }
    }
    val highlighted = scene.highlightedSelection(preview, selection)
    val focused = highlighted + listOfNotNull(preview.pressedId?.let { id -> scene.items.firstOrNull { it.id == id }?.cardId })
    val relatedSources = scene.items.filter { it.cardId in focused }.mapNotNull { it.sourceId }.toSet()
    fun outlines(i: TableItem, b: TableBounds, card: Boolean = false) {
        if (i.sourceId != null && i.sourceId in relatedSources) {
            val inset = if (card) -2f else 3f
            drawRect(Color(0xffbd982f), xy(b.x + inset, b.y + inset),
                size((b.width - 2 * inset).coerceAtLeast(0f), (b.height - 2 * inset).coerceAtLeast(0f)), style = Stroke(2 * density))
        }
        val outset = if (card) 2f else 0f
        if (i.cardId in highlighted) drawRect(Color(0xff397ccc), xy(b.x - outset, b.y - outset),
            size(b.width + 2 * outset, b.height + 2 * outset), style = Stroke(3 * density))
        if (i.id == preview.pressedId) drawRect(Color(0xffe7ab24), xy(b.x - outset, b.y - outset),
            size(b.width + 2 * outset, b.height + 2 * outset), style = Stroke(2 * density))
    }
    for (i in scene.items) for (part in i.visible) {
        // Preserve clipped/occluded edges, but leave room around a whole face for its outline.
        val outset = if (i.kind == TableItemKind.CARD) 4f else 0f
        val left = part.x - if (part.x == i.bounds.x) outset else 0f
        val top = part.y - if (part.y == i.bounds.y) outset else 0f
        val right = part.right + if (part.right == i.bounds.right) outset else 0f
        val bottom = part.bottom + if (part.bottom == i.bounds.bottom) outset else 0f
        clipRect(left * density, top * density, right * density, bottom * density) {
        val alpha = alphas[i.id] ?: 1f
        // Opaque cards need only the existing clip, not a separate GPU render target per fragment.
        if (alpha < 1f) drawContext.canvas.saveLayer(androidx.compose.ui.geometry.Rect(left * density, top * density,
            right * density, bottom * density), cache.layerPaint.apply { this.alpha = alpha })
        try {
        val b = i.bounds
        val color = if (i.owner == null) Color(0xff59656a) else if (i.owner == viewer) OwnTableColor else OpponentTableColor
        when (i.kind) {
             TableItemKind.HAND -> Unit
            TableItemKind.LIFE -> {
                drawRect(color, xy(b.x, b.y), size(b.width, b.height))
                paint.color = android.graphics.Color.WHITE; paint.textSize = 24 * density; paint.isFakeBoldText = true
                centeredText(i.value, b, 24f)
                paint.color = android.graphics.Color.DKGRAY; paint.textSize = 11 * density; paint.isFakeBoldText = false
                if (presenceDot != null && i.owner != viewer) {
                    // Opponent presence: inside the top-right corner, clear of the status dot/bubble below the square.
                    val center = xy(b.right - 7f, b.y + 7f)
                    drawCircle(Color.White, 4.5f * density, center)
                    drawCircle(presenceDot.color, 3.5f * density, center)
                }
            }
            TableItemKind.COUNTER -> {
                val marker = when (i.counter) {
                    CounterColor.RED -> Color(0xffb44b51); CounterColor.BLUE -> Color(0xff426f97)
                    CounterColor.GREEN -> Color(0xff3c744f); CounterColor.YELLOW -> Color(0xffc7ab3d)
                    CounterColor.PURPLE -> Color(0xff8c639d); else -> Color(0xff626b6e)
                }
                val radius = minOf(14f, b.height / 2 - 6f)
                val hasValue = TablePresentation.nonzero(i.value)
                drawCircle(if (hasValue) marker else marker.copy(alpha = .3f), radius * density,
                    xy(b.center.x, b.center.y), style = if (hasValue) androidx.compose.ui.graphics.drawscope.Fill else Stroke(density))
                paint.isFakeBoldText = true
                paint.color = if (i.counter == CounterColor.YELLOW) android.graphics.Color.rgb(32, 42, 46) else android.graphics.Color.WHITE
                if (hasValue) centeredText(i.value, TableBounds(b.center.x - radius, b.center.y - radius, 2 * radius, 2 * radius), 11f)
                paint.color = android.graphics.Color.DKGRAY; paint.textSize = 11 * density; paint.isFakeBoldText = false
            }
            TableItemKind.PILE -> {
                if (i.cards.isEmpty()) {
                    if (i.zone in listOf(PlayZone.LIBRARY, PlayZone.GRAVEYARD, PlayZone.EXILE, PlayZone.STACK)) emptySlot(i, color)
                } else if (i.zone != PlayZone.STACK) {
                    cardFace(i, b.x, b.y, color, i.faceTitle)
                    if (i.covered) {
                         val z = b.width / 28f
                         paint.color = android.graphics.Color.WHITE; paint.textSize = 17 * z * density
                         text("✧", b.x + 8 * z, b.y + 26 * z, 1)
                        paint.color = android.graphics.Color.DKGRAY; paint.textSize = 11 * density
                    }
                    if (i.temp) {
                        // A small order number keeps several face-down Temp piles apart.
                        val z = b.width / 28f
                        val tag = TableBounds(b.right - 10f * z, b.bottom - 10f * z, 9f * z, 9f * z)
                        drawRoundRect(Color.White, xy(tag.x, tag.y), size(tag.width, tag.height), CornerRadius(2 * z * density))
                        paint.color = color.toArgb(); paint.isFakeBoldText = true
                        centeredText(i.tempNumber.toString(), tag.copy(x = tag.x - 4f, width = tag.width + 8f), 8f * z)
                        paint.color = android.graphics.Color.DKGRAY; paint.textSize = 11 * density; paint.isFakeBoldText = false
                    }
                }
            }
             TableItemKind.CARD -> {
                   val transform = if (i.viewport == "combat") TableCardTransform(b.width, b.height, 0f) else i.faceTransform()
                  val z = transform.width / 28f
                  val cardWidth = transform.width; val cardHeight = transform.height
                  rotate(transform.rotation, xy(b.center.x, b.center.y)) {
                     val x = b.center.x - cardWidth / 2; val y = b.center.y - cardHeight / 2
                      scale(i.flipScale, 1f, xy(b.center.x, b.center.y)) { cardFace(i, x, y, color, i.value) }
                      if (i.panelRevealed && !i.covered) {
                          // Center in the art crop (11..30), clear of the title and corner text.
                           val cx = x + cardWidth / 2; val cy = y + 21f * z
                           drawCircle(Color.White, 3.5f * z * density, xy(cx, cy))
                           drawOval(color, xy(cx - 3f * z, cy - 1.8f * z), size(6f * z, 3.6f * z), style = Stroke(.6f * z * density))
                           drawCircle(color, 1f * z * density, xy(cx, cy))
                      }
                      if (i.copies > 1) {
                          val label = "×${i.copies}"
                          paint.textSize = 11 * density
                          val badgeWidth = cache.measure(label) / density + 6f
                          drawRoundRect(color, xy(x, y + cardHeight - 16f), size(badgeWidth, 16f), CornerRadius(3 * density))
                          paint.color = android.graphics.Color.WHITE
                          text(label, x + 3f, y + cardHeight - 4f, label.length)
                          paint.color = android.graphics.Color.DKGRAY
                      }
                     paint.textSize = 11 * density
                     outlines(i, TableBounds(x, y, cardWidth, cardHeight), card = true)
                 }
            }
        }
        if (i.kind != TableItemKind.CARD) outlines(i, b)
        } finally { if (alpha < 1f) drawContext.canvas.restore() }
    }
    }
    // Badge bounds belong to the pile, not its top card's face. Draw above Stack projections too.
    // An empty Temp pile shows only its order number, no "0" count.
    for (i in scene.items.filter { it.kind == TableItemKind.PILE && !(it.temp && it.cards.isEmpty()) }) {
        val badge = if (i.zone == PlayZone.STACK) i.stackBadge else i.pileCountBubble
        val port = if (i.countBadge != null) TableBounds(0f, 0f, this.size.width / density, this.size.height / density)
            else scene.viewports.single { it.id == i.viewport }.bounds
        if (i.visible.isNotEmpty()) clipRect(port.x * density, port.y * density, port.right * density, port.bottom * density) {
            drawCircle(Color(0xff345451), badge.width / 2 * density, xy(badge.center.x, badge.center.y))
            drawCircle(Color.White, badge.width / 2 * density, xy(badge.center.x, badge.center.y), style = Stroke(density))
            paint.color = android.graphics.Color.WHITE; paint.isFakeBoldText = true
            val previousSize = paint.textSize
            paint.textSize = (if (i.zone == PlayZone.STACK) 11f else 8.8f) * density
            text(i.value, badge.center.x - cache.measure(i.value) / density / 2, badge.center.y - cache.centerOffset() / density, 5)
            paint.textSize = previousSize
            paint.color = android.graphics.Color.DKGRAY; paint.isFakeBoldText = false
            if (i.keepTopRevealed) {
                // Same eye treatment as revealed panel cards, separate from the count badge.
                val cx = i.bounds.right - 7f; val cy = i.bounds.y + 7f
                val ink = Color(0xff345451)
                drawCircle(Color.White, 6f * density, xy(cx, cy))
                drawOval(ink, xy(cx - 5f, cy - 3f), size(10f, 6f), style = Stroke(density))
                drawCircle(ink, 1.7f * density, xy(cx, cy))
            }
        }
    }
    // Status bubbles are drawn only: taps and drags reach whatever lies beneath them.
    for (i in scene.items.filter { it.kind == TableItemKind.LIFE && it.status.isNotEmpty() && it.visible.isNotEmpty() }) {
        val color = if (i.owner == viewer) OwnTableColor else OpponentTableColor
        val mode = statusModes[i.owner] ?: StatusBubbleMode.BUBBLE
        if (mode == StatusBubbleMode.NONE) continue
        if (mode == StatusBubbleMode.DOT) {
            val dot = StatusBubble.dot(i.bounds, below = i.valueDragSide == ValueDragSide.TOP)
            drawCircle(Color.White, (StatusBubble.DOT / 2 + 1f) * density, xy(dot.center.x, dot.center.y))
            drawCircle(color, StatusBubble.DOT / 2 * density, xy(dot.center.x, dot.center.y))
            continue
        }
        val previousSize = paint.textSize
        paint.textSize = StatusBubble.TEXT * density; paint.isFakeBoldText = false
        val canvasWidth = this.size.width / density
        val maxText = (minOf(StatusBubble.MAX_WIDTH, canvasWidth - i.bounds.x - 4f) - 2 * StatusBubble.PADDING).coerceAtLeast(12f)
        val lines = StatusBubble.lines(i.status, maxText) { cache.measure(it) / density }
        val bubble = StatusBubble.bounds(i.bounds, below = i.valueDragSide == ValueDragSide.TOP,
            lines.maxOf { cache.measure(it) } / density, lines.size)
        val tipX = i.bounds.center.x
        val tail = androidx.compose.ui.graphics.Path().apply {
            val base = if (i.valueDragSide == ValueDragSide.TOP) bubble.y + .5f else bubble.bottom - .5f
            val tip = if (i.valueDragSide == ValueDragSide.TOP) bubble.y - StatusBubble.TAIL else bubble.bottom + StatusBubble.TAIL
            moveTo((tipX - 5f) * density, base * density); lineTo(tipX * density, tip * density)
            lineTo((tipX + 5f) * density, base * density); close()
        }
        drawRoundRect(Color.White, xy(bubble.x, bubble.y), size(bubble.width, bubble.height), CornerRadius(6 * density))
        drawPath(tail, Color.White)
        drawRoundRect(color, xy(bubble.x, bubble.y), size(bubble.width, bubble.height), CornerRadius(6 * density), style = Stroke(density))
        drawPath(tail, color, style = Stroke(density))
        // Hide the outline where the tail joins the bubble.
        drawLine(Color.White, xy(tipX - 4f, if (i.valueDragSide == ValueDragSide.TOP) bubble.y else bubble.bottom),
            xy(tipX + 4f, if (i.valueDragSide == ValueDragSide.TOP) bubble.y else bubble.bottom), 1.5f * density)
        paint.color = android.graphics.Color.rgb(32, 42, 46)
        lines.forEachIndexed { n, line ->
            text(line, bubble.x + StatusBubble.PADDING, bubble.y + StatusBubble.HEIGHT / 2 + n * StatusBubble.LINE -
                cache.centerOffset() / density, line.length)
        }
        paint.color = android.graphics.Color.DKGRAY; paint.textSize = previousSize
    }
    for (ping in pings) {
        val p = annotations.endpoint(ping.target) ?: continue
        val color = if (ping.recording.author == viewer) OwnTableColor else OpponentTableColor
        drawCircle(color.copy(alpha = 1f - pingPhase), (10 + 18 * pingPhase) * density, xy(p.x, p.y), style = Stroke(2 * density))
        drawCircle(color, 10 * density, xy(p.x, p.y))
        paint.color = android.graphics.Color.WHITE
        val number = ping.to!!.ordinal
        text(number, p.x - cache.measure(number) / density / 2, p.y + 4, 5)
        paint.color = android.graphics.Color.DKGRAY
    }
    preview.rectangle?.let { b ->
        drawRect(OwnTableColor.copy(alpha = .14f), xy(b.x, b.y), size(b.width, b.height))
        drawRect(OwnTableColor, xy(b.x, b.y), size(b.width, b.height), style = Stroke(density))
    }
    val landing = tableDragLanding(scene, preview)
    landing.combatInsertion?.let { (from, to) -> scene.world?.viewport?.let { port ->
        clipRect(port.x * density, port.y * density, port.right * density, port.bottom * density) {
            drawLine(Color.Black.copy(alpha = .18f), xy(from.x, from.y), xy(to.x, to.y), density)
        }
    } }
    for ((b, insertionX) in listOfNotNull(landing.hand?.let { it to landing.insertionX }) + landing.additionalHands) {
        drawRect(OwnTableColor.copy(alpha = .18f), xy(b.x, b.y), size(b.width, b.height))
        insertionX?.let { x -> drawLine(OwnTableColor, xy(x, b.y + 3), xy(x, b.bottom - 3), 2 * density) }
    }
    for (b in landing.outlines) {
        drawRoundRect(OwnTableColor.copy(alpha = .08f), xy(b.x, b.y), size(b.width, b.height), CornerRadius(2 * density))
        drawRoundRect(OwnTableColor.copy(alpha = .5f), xy(b.x, b.y), size(b.width, b.height), CornerRadius(2 * density),
            style = Stroke(density, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4 * density, 3 * density))))
    }
    preview.point?.let { p ->
        if (preview.scope != null) {
            drawRoundRect(if (preview.destination == null) OpponentTableColor.copy(alpha = .35f) else OwnTableColor.copy(alpha = .35f),
                xy(p.x - scene.cardWidth / 2, p.y - scene.cardHeight / 2), size(scene.cardWidth, scene.cardHeight), CornerRadius(3 * density))
            text("${preview.scope.cardIds.size}", p.x, p.y)
        }
    }
}

/** Uses the production renderer: size and text are not a separately maintained approximation. */
@Composable
internal fun SampleTableCard(appearance: CardAppearance, scale: Float, modifier: Modifier = Modifier) {
    val bounds = TableBounds(0f, 0f, 28f * scale, 40f * scale)
    val sample = TableItem("sample", bounds, "Sample card: Sprite, note Flying, 2/3, one blue counter", "Sprite",
        owner = "sample", kind = TableItemKind.CARD, corner = "2/3", note = "Flying",
        counters = mapOf(CounterColor.BLUE to "1"), typeIcon = "♙")
    val scene = TableScene(listOf(sample), emptyList(), appearance = appearance)
    val density = LocalDensity.current.density
    val cache = remember(density) { TableDrawCache(density) }
    Canvas(modifier.size(bounds.width.dp, bounds.height.dp).semantics { contentDescription = sample.label }) {
        drawTable(scene, "sample", "sample", TableSelection(), TableGesturePreview(),
            annotations = TableAnnotationGeometry(scene, "sample"), cache = cache)
    }
}
