package org.asyncttwiz.android

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.dp
import org.asyncttwiz.protocol.*

internal class TableRenderFrame(val scene: TableScene, val alphas: Map<String, Float>) {
    val items = scene.items.associateBy { it.id }
}

/** The animated accessibility rectangle participates in layout, without recomposing its node. */
internal fun Modifier.tableFrameBounds(bounds: () -> TableBounds?): Modifier = layout { measurable, _ ->
    val b = bounds() ?: TableBounds(0f, 0f, 0f, 0f)
    val width = b.width.dp.roundToPx().coerceAtLeast(0)
    val height = b.height.dp.roundToPx().coerceAtLeast(0)
    val child = measurable.measure(androidx.compose.ui.unit.Constraints.fixed(width, height))
    layout(width, height) { child.place(b.x.dp.roundToPx(), b.y.dp.roundToPx()) }
}

@Composable
internal fun AnimatedStackBubbleOverlay(frame: State<TableRenderFrame>, play: PlayState, viewer: String,
    preview: () -> TableGesturePreview, enabled: Boolean, perform: (TableIntent) -> Unit) {
    val scene = frame.value.scene
    val bubble = scene.stackBubbles ?: return
    val card = scene.items.find { it.cardId == bubble.entryId && it.zone == PlayZone.STACK }
    StackBubbleOverlay(bubble, stackTextOptions(play, bubble.entryId, viewer),
        if (card?.owner == viewer) OwnTableColor else OpponentTableColor, preview(), enabled, perform)
}

@Composable
internal fun AnimatedCardRingAnchors(frame: State<TableRenderFrame>, active: Boolean, content: @Composable () -> Unit) {
    val anchors = if (!active) emptyMap() else frame.value.scene.items.filter { it.visible.isNotEmpty() }.mapNotNull { item ->
        item.cardId?.let { id -> id to if (item.visible.any { it.contains(item.bounds.center) }) item.bounds
            else item.visible.maxBy { it.width * it.height } }
    }.toMap()
    CompositionLocalProvider(LocalCardRingAnchors provides anchors, content = content)
}
