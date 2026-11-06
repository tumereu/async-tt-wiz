package org.asyncttwiz.e2e

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription

/** The top of the row described [row] of a drag-to-reorder list (STK-030) on [player]'s screen. */
internal fun E2ePlayer.rowTop(row: String): Float = node(hasContentDescription(row)).fetchSemanticsNode().boundsInRoot.top

/** [player] drags the row described [row] of a drag-to-reorder list (STK-030) up by [places] rows by its handle, as a finger
 * does: down on the handle, moved, up. */
internal fun E2e.dragRowUp(player: E2ePlayer, row: String, places: Int = 1) {
    val handle = player.node(hasContentDescription("Drag to reorder") and hasAnyAncestor(hasContentDescription(row)))
    val height = player.node(hasContentDescription(row)).fetchSemanticsNode().size.height.toFloat()
    holding(handle, millis = 48) { moveHeldFinger(handle, Offset(0f, -height * places * 1.15f), steps = 12) }
}
