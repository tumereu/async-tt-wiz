package org.asyncttwiz.android

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

/**
 * WEB-139: how a control or a card the mouse is over shows it can be clicked, beside the hand pointer (WEB-058). Material's
 * own buttons, chips, menu entries and the app's `clickable` controls show it by themselves (Material's hover state layer);
 * these looks are for the app's own controls and cards whose gestures bypass it.
 */
sealed interface HoverLook {
    /** A control (a button, a chip, a toggle): a faint layer of its ink ([ink], else the content colour where it lies) over
     * it, inside [shape], as Material's own controls show hover. */
    data class Tint(val ink: Color? = null, val shape: Shape = RectangleShape) : HoverLook

    /** A card: a thin dark outline just inside its edge, in [shape], as the table's cards show it (WEB-140). */
    data class Outline(val shape: Shape = HoverCardShape) : HoverLook
}

/** WEB-139: the hover layer's strength over a control, Material's hovered state layer (8 %); the app's pressed one is 16 %. */
const val HOVER_TINT_ALPHA = .08f

/** WEB-140: the hover outline's ink and strength around a card (on the table's canvas too) and its width (dp). */
val HoverOutlineInk = MenuInk.copy(alpha = .6f)
const val HOVER_OUTLINE_WIDTH = 2f

/** WEB-140: a card's hover outline follows its rounded corners. */
val HoverCardShape = RoundedCornerShape(4.dp)

/**
 * WEB-139, WEB-140: while a mouse is over this (and it is [enabled]), [look] is drawn over it; a touch or a pen never shows
 * it, so the phone and touch screens look as before. Nothing else changes: its gestures, semantics and size stay its own.
 */
fun Modifier.hoverLook(look: HoverLook = HoverLook.Tint(), enabled: Boolean = true): Modifier =
    this then HoverLookElement(look, enabled)

private data class HoverLookElement(val look: HoverLook, val enabled: Boolean) : ModifierNodeElement<HoverLookNode>() {
    override fun create() = HoverLookNode(look, enabled)
    override fun update(node: HoverLookNode) {
        if (node.look != look || node.enabled != enabled) {
            node.look = look; node.enabled = enabled
            node.invalidateDraw()
        }
    }
}

private class HoverLookNode(var look: HoverLook, var enabled: Boolean) : Modifier.Node(), PointerInputModifierNode,
    DrawModifierNode, CompositionLocalConsumerModifierNode {
    private var hovered = false

    // Only watches, never consumes. It doesn't share input with the siblings under it either: that would hand a touch on this
    // control to whatever lies beneath it too (a timeline chip's tap reached the strip's other chips, merge gate 2026-11-21)

    override fun onPointerEvent(pointerEvent: PointerEvent, pass: PointerEventPass, bounds: IntSize) {
        if (pass != PointerEventPass.Main) return
        if (pointerEvent.changes.none { it.type == PointerType.Mouse }) return
        when (pointerEvent.type) {
            PointerEventType.Enter -> show(true)
            PointerEventType.Exit -> show(false)
        }
    }

    override fun onCancelPointerInput() = show(false)

    override fun onDetach() { hovered = false }

    private fun show(over: Boolean) {
        if (over == hovered) return
        hovered = over
        if (isAttached) invalidateDraw()
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        if (!hovered || !enabled) return
        when (val shown = look) {
            is HoverLook.Tint -> {
                val ink = shown.ink ?: currentValueOf(LocalContentColor)
                drawOutline(shown.shape.createOutline(size, layoutDirection, this), ink.copy(alpha = ink.alpha * HOVER_TINT_ALPHA))
            }
            is HoverLook.Outline -> {
                val width = HOVER_OUTLINE_WIDTH.dp.toPx()
                inset(width / 2) {
                    drawOutline(shown.shape.createOutline(size, layoutDirection, this), HoverOutlineInk, style = Stroke(width))
                }
            }
        }
    }
}
