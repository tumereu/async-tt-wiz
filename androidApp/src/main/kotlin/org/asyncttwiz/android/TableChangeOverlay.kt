package org.asyncttwiz.android

import android.graphics.Paint
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import org.asyncttwiz.protocol.*

internal val LocalTableChanges = staticCompositionLocalOf<TableChanges?> { null }

@Composable
private fun changeProgress(changes: TableChanges, active: ActiveTableChange): State<Float> {
    val progress = remember(active.change.key) { Animatable(1f) }
    LaunchedEffect(active.serial) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(TableChanges.WINDOW_MS.toInt(), easing = LinearEasing))
        changes.expire(active.change.key, active.serial)
    }
    return progress.asState()
}

/** The small affected control observes its own clock only in draw, not in parent composition. */
@Composable
internal fun Modifier.tableChangeFlash(changes: TableChanges?, key: TableChangeKey): Modifier {
    val active = changes?.active?.get(key) ?: return this
    val progress = changeProgress(changes, active)
    return drawWithContent {
        drawContent()
        drawRect(Color(0xffffce56).copy(alpha = flashAlpha(progress.value)))
    }
}

private fun flashAlpha(progress: Float) = ((1f - progress * 3f).coerceAtLeast(0f) * .65f)

/** Resolve the recorded card/occurrence identity, not its source or a container's top card.
 * Both lookups use the rendered frame's index; buried/offscreen entries never get a stand-in. */
private fun changeCard(id: String, item: (String) -> TableItem?) =
    item("card/$id") ?: item("stackDrawing/$id")

internal fun tableChangeTarget(key: TableChangeKey, item: (String) -> TableItem?): TableItem? {
    val shown = when (key.kind) {
        TableChangeKind.LIFE -> item("life/${key.target}")
        // Portrait removes an empty counter row immediately. Only the visual feedback moves to
        // the public HUD; no removed counter item or input target is kept alive for the fade.
        TableChangeKind.PLAYER_COUNTER -> item("counter/${key.target}/${key.color}") ?: item("life/${key.target}")
        else -> changeCard(key.target, item)
    } ?: return null
    return shown.takeIf { it.visible.isNotEmpty() && (!it.covered || key.kind == TableChangeKind.OWNER) }
}

internal fun tableChangeLinkAnchors(change: TableChange, item: (String) -> TableItem?): Pair<TablePoint, TablePoint>? {
    fun endpoint(e: LinkEndpoint) = (if (e.kind == LinkEndpointKind.CARD) changeCard(e.id, item)
        else item("life/${e.id}"))?.visibleAnchor
    val first = change.link?.first?.let(::endpoint)
        ?: change.projectionLink?.first?.let { changeCard(it, item)?.visibleAnchor } ?: return null
    val second = change.link?.second?.let(::endpoint)
        ?: change.projectionLink?.second?.let { changeCard(it, item)?.visibleAnchor } ?: return null
    return first to second
}

/** One independent draw node per changed value. No input modifiers and no board/frame diff. */
@Composable
internal fun TableChangeOverlay(changes: TableChanges?, viewer: String, appearance: CardAppearance,
    item: (String) -> TableItem?) {
    if (changes == null) return
    for ((changeKey, active) in changes.active) if (changeKey.kind != TableChangeKind.STEP) key(changeKey) {
        TableElementChange(changes, active, viewer, appearance, item)
    }
}

@Composable
private fun TableElementChange(changes: TableChanges, active: ActiveTableChange, viewer: String,
    appearance: CardAppearance, item: (String) -> TableItem?) {
        val key = active.change.key
        val progress = changeProgress(changes, active)
        if (key.kind == TableChangeKind.FACE) return // Existing scene flip owns the transform.
        val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { isFakeBoldText = true } }
        Canvas(Modifier.fillMaxSize()) {
            val t = progress.value
            val fade = (1f - t).coerceIn(0f, 1f)
            val change = active.change
            fun point(p: TablePoint) = Offset(p.x * density, p.y * density)
            fun rect(b: TableBounds, color: Color, border: Boolean = false) {
                drawRoundRect(color, point(TablePoint(b.x, b.y)), Size(b.width * density, b.height * density),
                    CornerRadius(2f * density), style = if (border) Stroke(3f * density) else androidx.compose.ui.graphics.drawscope.Fill)
            }
            if (key.kind == TableChangeKind.LINK) {
                tableChangeLinkAnchors(change, item)?.let { (first, second) ->
                    drawLine(Color(0xffffce56).copy(alpha = .25f * fade), point(first), point(second), 10f * density)
                    drawLine(Color(0xffffbd36).copy(alpha = fade), point(first), point(second), 3f * density)
                }
                return@Canvas
            }
            val shown = tableChangeTarget(key, item) ?: return@Canvas
            val color = key.color?.let { counterColor(it).let { c -> Color(c.red * .55f, c.green * .55f, c.blue * .55f) } }
                ?: if ((change.owner ?: shown.owner) == viewer) OwnTableColor else OpponentTableColor
            val b = shown.bounds
            val transform = shown.faceTransform()
            val card = shown.kind == TableItemKind.CARD
            val z = if (card) transform.width / 28f else 1f
            val origin = if (card) TablePoint(b.center.x - transform.width / 2, b.center.y - transform.height / 2) else TablePoint(b.x, b.y)
            val valueScale = appearance.text(CardTextScalePart.VALUES)
            val noteScale = appearance.text(CardTextScalePart.NOTE)
            fun local(r: TableBounds) = TableBounds(origin.x + r.x * z, origin.y + r.y * z, r.width * z, r.height * z)
            val slot = when (key.kind) {
                TableChangeKind.CORNER -> local(TableBounds(1f, 29f, 26f, 11f))
                TableChangeKind.NOTE, TableChangeKind.COUNTER -> {
                    paint.textSize = 7f * noteScale * z * density
                    val noteHeight = (paint.fontMetrics.bottom - paint.fontMetrics.top) / (z * density) + 2f
                    paint.textSize = 7f * valueScale * z * density
                    val counterHeight = maxOf(10f * valueScale, (paint.fontMetrics.bottom - paint.fontMetrics.top) / (z * density) + 2f)
                    val colors = CounterColor.entries.filter { it in shown.counters || it == key.color }
                    val layout = cardAnnotationLayout(maxOf(11f, 7f * appearance.text(CardTextScalePart.TITLE) + 3f),
                        40f - maxOf(10f, 7f * valueScale + 3f), noteHeight, counterHeight,
                        shown.note.isNotEmpty() || key.kind == TableChangeKind.NOTE, colors.size)
                    val r = if (key.kind == TableChangeKind.NOTE) layout.note else layout.counters.getOrNull(colors.indexOf(key.color))
                    r?.let(::local) ?: return@Canvas
                }
                TableChangeKind.TEXT_MARK -> TableBounds(b.x, b.y, b.width, 16f)
                else -> b
            }
            for (fragment in shown.visible) clipRect(fragment.x * density, fragment.y * density, fragment.right * density, fragment.bottom * density) {
                rotate(if (card) transform.rotation else 0f, point(b.center)) {
                    if (key.kind == TableChangeKind.OWNER) {
                        val pulse = fade * (.4f + .6f * kotlin.math.abs(kotlin.math.cos(t * 3 * Math.PI)).toFloat())
                        rect(local(TableBounds(0f, 0f, 28f, 40f)), color.copy(alpha = pulse), border = true)
                    } else rect(slot, color.copy(alpha = flashAlpha(t)))
                }
            }
            if (active.tag.isNotEmpty()) {
                // Tags may extend beside their value, but never install a hit target.
                paint.textSize = (if (card) 7f * z * valueScale else 11f) * density
                val width = paint.measureText(active.tag) / density + 5f
                val height = (paint.fontMetrics.bottom - paint.fontMetrics.top) / density + 2f
                val x = (if (card) slot.right - width else b.right - width / 2).coerceIn(0f, (size.width / density - width).coerceAtLeast(0f))
                val y = (if (card) slot.y - height else b.y - height / 2).coerceAtLeast(48f)
                val tag = TableBounds(x, y, width, height)
                rect(tag, Color.White.copy(alpha = fade))
                rect(tag, color.copy(alpha = fade), border = true)
                paint.color = color.toArgb(); paint.alpha = (fade * 255).toInt()
                drawContext.canvas.nativeCanvas.drawText(active.tag, (x + 2.5f) * density,
                    (y + height / 2) * density - (paint.fontMetrics.top + paint.fontMetrics.bottom) / 2, paint)
            }
        }
}
