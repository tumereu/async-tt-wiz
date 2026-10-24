package org.asyncttwiz.android

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BorderColor
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.TableRows
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import org.asyncttwiz.protocol.*

/** Dark, fixed marker colors: white numerals remain readable on either table background. */
internal val StackMarkPalette = listOf(0xff6a3dad, 0xff956000, 0xff235bb0, 0xff9b3075,
    0xff526b12, 0xffa3440b, 0xff42528a, 0xff70513f).map { Color(it) }
internal fun stackMarkColor(index: Int) = StackMarkPalette[index.mod(StackMarkPalette.size)]

/** A live projection is not a disclosure grant for a source that has since entered a hidden zone. */
internal fun stackTextOptions(state: PlayState, entryId: String, viewer: String): List<StackTextOption> {
    val card = CombatStackRules.inspection(state.board, entryId) ?: return emptyList()
    return if (CardVisibility.isVisibleFaceUp(state.board, card, viewer)) StackText.options(state, entryId) else emptyList()
}

data class StackBubbleGeometry(val entryId: String, val anchor: TableBounds, val controls: List<TableItem>,
    val selected: Int?, val optionCount: Int) {
    companion object {
        fun create(entryId: String, anchor: TableBounds, width: Float, height: Float, selected: Int?, count: Int): StackBubbleGeometry {
            val side = 40f
            fun bounds(x: Float, y: Float) = TableBounds(x.coerceIn(4f, (width - side - 4).coerceAtLeast(4f)),
                y.coerceIn(4f, (height - side - 4).coerceAtLeast(4f)), side, side)
            val toward = if (anchor.center.x > width / 2) anchor.x - side - 10 else anchor.right + 10
            val above = bounds(anchor.center.x - side / 2, anchor.y - side - 10)
            val below = bounds(anchor.center.x - side / 2, anchor.bottom + 10)
            // Near top/bottom flip the obstructed action beside the other; never stack hit regions.
            val beside = if (toward > anchor.center.x) above.right + 8 else above.x - side - 8
            val top = if (above.intersection(anchor) != null) bounds(beside, below.y) else above
            val bottom = if (below.intersection(anchor) != null) bounds(beside, above.y) else below
            val handle = bounds(toward, anchor.center.y - side / 2)
            val controls = listOf("battlefield" to top, "graveyard" to bottom, "mark" to handle).map { (kind, b) ->
                TableItem("stackBubble/$kind", b, when (kind) {
                    "battlefield" -> "Put to battlefield"; "graveyard" -> "Put to graveyard"; else -> "Mark text · drag up or down"
                }, entryId)
            }
            return StackBubbleGeometry(entryId, anchor, controls, selected, count)
        }
    }
}

/** Semantics and drawings only: all pointer input stays with TableGestureCoordinator. No scrim. */
@Composable
internal fun StackBubbleOverlay(geometry: StackBubbleGeometry, options: List<StackTextOption>, ownerColor: Color,
    preview: TableGesturePreview, enabled: Boolean, perform: (TableIntent) -> Unit) {
    for (item in geometry.controls) {
        val b = item.bounds
        val marker = item.id.endsWith("/mark")
        SpeechBubbleTail(geometry.anchor, b, ownerColor)
        Surface(Modifier.offset(b.x.dp, b.y.dp).size(b.width.dp, b.height.dp).semantics {
            contentDescription = item.label
            if (!enabled) disabled()
            if (marker) {
                stateDescription = geometry.selected?.let { "Option ${it + 1}" } ?: "No mark"
                customActions = options.mapIndexed { index, option -> CustomAccessibilityAction("Mark ${index + 1} — ${option.text}") {
                    if (enabled) perform(TableIntent.Submit(PlayAction.Stack(StackAction.MarkText(geometry.entryId, index)), null)); enabled
                } } + CustomAccessibilityAction("Clear text mark") {
                    if (enabled) perform(TableIntent.Submit(PlayAction.Stack(StackAction.MarkText(geometry.entryId, null)), null)); enabled
                }
            } else onClick(item.label) {
                if (enabled) perform(TableIntent.StackDestination(geometry.entryId,
                    if (item.id.endsWith("battlefield")) PlayZone.BATTLEFIELD else PlayZone.GRAVEYARD)); enabled
            }
        }, shape = RoundedCornerShape(10.dp), color = ownerColor, contentColor = Color.White, shadowElevation = 3.dp) {
            Box(contentAlignment = Alignment.Center) { Icon(when {
                marker -> Icons.Outlined.BorderColor
                item.id.endsWith("battlefield") -> Icons.Outlined.TableRows
                else -> Icons.Outlined.Inventory2
            }, null, Modifier.size(22.dp)) }
        }
    }
    if (preview.marking) {
        val index = preview.markIndex
        val text = index?.let { options.getOrNull(it)?.text } ?: "Clear text mark"
        val width = 240f
        val handle = geometry.controls.last().bounds
        // Preview on the inward side, above the handle, so neither finger nor handle covers it.
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val x = (if (handle.center.x > maxWidth.value / 2) handle.x - width - 10 else handle.right + 10)
                .coerceIn(8f, (maxWidth.value - width - 8).coerceAtLeast(8f))
            val y = (handle.y - 114).coerceIn(8f, (maxHeight.value - 160).coerceAtLeast(8f))
            Surface(Modifier.offset(x.dp, y.dp).width(width.dp).heightIn(max = 150.dp)
                .semantics { contentDescription = "Stack text drag preview" }, shape = RoundedCornerShape(10.dp),
                color = Color.White, shadowElevation = 5.dp) {
                Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StackMarkChip(index)
                    CardText(text, style = MaterialTheme.typography.bodyMedium, maxLines = 5)
                }
            }
        }
    }
}

/** Shared pointer toward a card, used by both the Stack shortcuts and the action ring. */
@Composable
internal fun SpeechBubbleTail(anchor: TableBounds, b: TableBounds, color: Color) {
        Canvas(Modifier.offset(b.x.dp, b.y.dp).size(b.width.dp, b.height.dp)) {
            val a = anchor.center
            val dx = a.x - b.center.x; val dy = a.y - b.center.y
            val path = Path()
            if (kotlin.math.abs(dx) > kotlin.math.abs(dy)) {
                val x = if (dx > 0) size.width else 0f
                path.moveTo(x, size.height / 2 - 6.dp.toPx()); path.lineTo(x + (if (dx > 0) 8 else -8).dp.toPx(), size.height / 2)
                path.lineTo(x, size.height / 2 + 6.dp.toPx())
            } else {
                val y = if (dy > 0) size.height else 0f
                path.moveTo(size.width / 2 - 6.dp.toPx(), y); path.lineTo(size.width / 2, y + (if (dy > 0) 8 else -8).dp.toPx())
                path.lineTo(size.width / 2 + 6.dp.toPx(), y)
            }
            path.close(); drawPath(path, color)
        }
}

@Composable
internal fun StackMarkChip(index: Int?, modifier: Modifier = Modifier) {
    Text(index?.let { "${it + 1}" } ?: "–", modifier.background(index?.let(::stackMarkColor) ?: Color.DarkGray,
        RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 1.dp), color = Color.White,
        style = MaterialTheme.typography.labelSmall)
}
