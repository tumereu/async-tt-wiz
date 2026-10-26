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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.sp
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

internal fun tableChangeTagAlpha(progress: Float): Float =
    ((1f - progress) * TableChanges.WINDOW_MS / (TableChanges.WINDOW_MS - TableChanges.HOLD_MS)).coerceIn(0f, 1f)

internal val TableChangeTagFill = Color(0xff202a2e)

internal fun tableChangeTagInk(fill: Color): Color =
    if ((fill.luminance() + .05f) / .05f > 1.05f / (fill.luminance() + .05f)) Color.Black else Color.White

/** Resolve the recorded card/occurrence identity, not its source or a container's top card.
 * Both lookups use the rendered frame's index; buried/offscreen entries never get a stand-in. */
private fun changeCard(id: String, item: (String) -> TableItem?) =
    item("card/$id") ?: item("stackDrawing/$id")

internal fun tableChangeTarget(key: TableChangeKey, item: (String) -> TableItem?): TableItem? {
    val shown = when (key.kind) {
        TableChangeKind.LIFE -> item("life/${key.target}")
        // Portrait retains empty slots; hidden landscape counters fall back to the life HUD.
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

/** Independent clocks per changed value. No input modifiers and no board/frame diff. */
@Composable
internal fun TableChangeOverlay(changes: TableChanges?, viewer: String, appearance: CardAppearance,
    item: (String) -> TableItem?) {
    if (changes == null) return
    val effects = changes.active.values.filter { it.change.key.kind != TableChangeKind.STEP }.map { active ->
        key(active.change.key) { active to changeProgress(changes, active) }
    }
    // All pills must be above ALL value flashes, not just their own. In particular a small
    // card's P/T flash must never tint a neighbouring counter pill drawn by another node.
    for (tagsOnly in listOf(false, true)) for ((active, progress) in effects) key(tagsOnly, active.change.key) {
        TableElementChange(active, progress, viewer, appearance, item, tagsOnly)
    }
}

@Composable
private fun TableElementChange(active: ActiveTableChange, progress: State<Float>, viewer: String,
    appearance: CardAppearance, item: (String) -> TableItem?, tagsOnly: Boolean) {
        val key = active.change.key
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
                 if (!tagsOnly) tableChangeLinkAnchors(change, item)?.let { (first, second) ->
                    drawLine(Color(0xffffce56).copy(alpha = .25f * fade), point(first), point(second), 10f * density)
                    drawLine(Color(0xffffbd36).copy(alpha = fade), point(first), point(second), 3f * density)
                }
                return@Canvas
            }
            val shown = tableChangeTarget(key, item) ?: return@Canvas
             val color = key.color?.let(::counterColor)
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
                 TableChangeKind.CORNER -> {
                     val height = maxOf(10f, 7f * valueScale + 3f)
                     local(TableBounds(1f, 40f - height, 26f, height))
                 }
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
                     r?.let {
                         val width = if (key.kind == TableChangeKind.COUNTER)
                             minOf(it.width, maxOf(counterHeight, paint.measureText(shown.counters[key.color].orEmpty().take(3)) / (z * density) + 2f))
                         else it.width
                         local(it.copy(width = width))
                     } ?: return@Canvas
                }
                TableChangeKind.TEXT_MARK -> TableBounds(b.x, b.y, b.width, 16f)
                else -> b
            }
             if (!tagsOnly) {
                 for (fragment in shown.visible) clipRect(fragment.x * density, fragment.y * density, fragment.right * density, fragment.bottom * density) {
                     rotate(if (card) transform.rotation else 0f, point(b.center)) {
                         if (key.kind == TableChangeKind.OWNER) {
                             val pulse = fade * (.4f + .6f * kotlin.math.abs(kotlin.math.cos(t * 3 * Math.PI)).toFloat())
                             rect(local(TableBounds(0f, 0f, 28f, 40f)), color.copy(alpha = pulse), border = true)
                         } else {
                             // A bright pulse over the value, even on a white card; not just an ink hue change.
                             rect(slot, Color(0xffffce56).copy(alpha = flashAlpha(t)))
                         }
                     }
                 }
                 return@Canvas
             }
            if (active.tag.isNotEmpty()) {
                 // Screen-space sibling above all cards: no card/fragment clip, rotation or hit target.
                 // Keep the anchor attached to the value when the card is tapped, but the tag upright.
                 paint.textSize = maxOf(11.sp.toPx(), if (card) 7f * z * valueScale * density else 0f)
                 val width = paint.measureText(active.tag) / density + 10f
                 val height = (paint.fontMetrics.bottom - paint.fontMetrics.top) / density + 4f
                 val radians = Math.toRadians(transform.rotation.toDouble())
                 val dx = slot.right - b.center.x
                 val dy = (if (key.kind == TableChangeKind.CORNER) slot.bottom else slot.center.y) - b.center.y
                 val anchor = if (card) TablePoint(
                     b.center.x + (dx * kotlin.math.cos(radians) - dy * kotlin.math.sin(radians)).toFloat(),
                     b.center.y + (dx * kotlin.math.sin(radians) + dy * kotlin.math.cos(radians)).toFloat())
                     else TablePoint(b.right - width / 2, b.y)
                  val x = (if (card) maxOf(anchor.x + 3f, b.right + 3f) else anchor.x)
                     .coerceIn(2f, (size.width / density - width - 2f).coerceAtLeast(2f))
                  // At the smallest card size, screen-sized counter and P/T pills need separate rows.
                  val tagY = if (card && key.kind == TableChangeKind.COUNTER && b.height < 2 * height)
                      minOf(anchor.y, b.bottom - height - 4f) else anchor.y
                  val y = (tagY - height / 2)
                     .coerceIn(48f, (size.height / density - height - 2f).coerceAtLeast(48f))
                 val tag = TableBounds(x, y, width, height)
                 val fill = if (key.kind == TableChangeKind.COUNTER) key.color?.let(::counterColor) ?: TableChangeTagFill else TableChangeTagFill
                 val alpha = tableChangeTagAlpha(t)
                 drawRoundRect(fill.copy(alpha = alpha), point(TablePoint(tag.x, tag.y)),
                     Size(tag.width * density, tag.height * density), CornerRadius(height * density / 2))
                 paint.color = tableChangeTagInk(fill).toArgb(); paint.alpha = (alpha * 255).toInt()
                 drawContext.canvas.nativeCanvas.drawText(active.tag, (x + 5f) * density,
                    (y + height / 2) * density - (paint.fontMetrics.top + paint.fontMetrics.bottom) / 2, paint)
            }
        }
}
