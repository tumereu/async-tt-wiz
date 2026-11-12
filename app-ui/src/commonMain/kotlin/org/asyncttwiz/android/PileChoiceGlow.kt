package org.asyncttwiz.android

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalDensity
import org.asyncttwiz.protocol.PlayZone

/**
 * STK-047 (owner, 2026-11-12: "tap me, your choices are inside"): the piles of [scene] whose id is in [glow] (a Graveyard,
 * Exile or other pile holding a card the viewer may target or choose now; never the Stack, whose entries glow as entries)
 * glow over their whole face, slowly brightening and fading. Drawn in a layer of its own over the table: the pulse is read
 * only while drawing, so a frame redraws this layer alone, with nothing allocated. [frame] gives each pile's current place
 * as the table animates.
 */
@Composable
fun PileChoiceGlow(scene: TableScene, glow: Set<String>, frame: State<TableRenderFrame>) {
    val piles = remember(scene, glow) {
        scene.items.filter { it.kind == TableItemKind.PILE && it.zone != PlayZone.STACK && it.id in glow && it.visible.isNotEmpty() }
            .map { pile -> pile.id to scene.viewports.firstOrNull { it.id == pile.viewport }?.bounds }
    }
    if (piles.isEmpty()) return
    val pulse = rememberInfiniteTransition(label = "pile choice glow").animateFloat(0f, 1f,
        infiniteRepeatable(tween(PILE_GLOW_MILLIS), RepeatMode.Reverse), label = "pile choice pulse")
    val density = LocalDensity.current.density
    val strokes = remember(density) { Array(PILE_GLOW_RINGS) { ring -> Stroke((2f + 2f * ring) * density) } }
    val edge = remember(density) { Stroke(2.5f * density) }
    Canvas(Modifier.fillMaxSize()) {
        val p = pulse.value
        val items = frame.value.items
        val corner = CornerRadius(4 * density)
        // By index: nothing allocated per frame
        for (index in piles.indices) {
            val (id, port) = piles[index]
            val b = items[id]?.bounds ?: continue
            // Room outside the viewport for the halo: only the far side of a pile cut by its viewport is clipped
            val room = 12f
            clipRect(if (port == null) 0f else (port.x - room) * density, if (port == null) 0f else (port.y - room) * density,
                if (port == null) size.width else (port.right + room) * density, if (port == null) size.height else (port.bottom + room) * density) {
                // A wash over the whole pile, so its face reads as lit
                drawRoundRect(AutomatumViolet.copy(alpha = .16f + .26f * p), Offset(b.x * density, b.y * density),
                    Size(b.width * density, b.height * density), corner)
                // Rings spreading out from its edge, stronger at the pulse's height
                for (ring in 0 until PILE_GLOW_RINGS) {
                    val spread = (2f + 3f * ring) * (.75f + .25f * p)
                    drawRoundRect(AutomatumViolet.copy(alpha = (.50f - .13f * ring) * (.65f + .35f * p)),
                        Offset((b.x - spread) * density, (b.y - spread) * density),
                        Size((b.width + 2 * spread) * density, (b.height + 2 * spread) * density), corner, style = strokes[ring])
                }
                drawRoundRect(AutomatumViolet, Offset(b.x * density, b.y * density), Size(b.width * density, b.height * density),
                    corner, style = edge)
            }
        }
    }
}

/** STK-047: one brightening or fading of the pile glow. */
private const val PILE_GLOW_MILLIS = 900

/** STK-047: how many rings spread out from a glowing pile's edge. */
private const val PILE_GLOW_RINGS = 3
