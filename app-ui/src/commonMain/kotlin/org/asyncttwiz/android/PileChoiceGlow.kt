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
 * glow over their whole face, slowly brightening and fading. Owner notes 2026-11-14: so do the closed battlefield groups whose
 * keys are in [spread] (a tap spreads them open to pick from, [TableGlows.spread]), over all their members. Drawn in a layer of its
 * own over the table: the pulse is read only while drawing, so a frame redraws this layer alone, with nothing allocated. [frame]
 * gives each pile's current place as the table animates.
 */
@Composable
fun PileChoiceGlow(scene: TableScene, glow: Set<String>, frame: State<TableRenderFrame>, spread: Set<String> = emptySet()) {
    val piles = remember(scene, glow, spread) {
        scene.items.filter { it.kind == TableItemKind.PILE && it.zone != PlayZone.STACK && it.id in glow && it.visible.isNotEmpty() }
            .map { pile -> listOf(pile.id) to scene.viewports.firstOrNull { it.id == pile.viewport }?.bounds } +
            scene.battlefieldGroups.filter { it.key in spread }.mapNotNull { group ->
                val items = scene.items.filter { it.battlefieldCard && it.cardId in group.members && it.visible.isNotEmpty() }
                if (items.isEmpty()) null else items.map { it.id } to scene.viewports.firstOrNull { it.id == items.first().viewport }?.bounds
            }
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
            val (ids, port) = piles[index]
            val b = if (ids.size == 1) items[ids[0]]?.bounds ?: continue else union(ids, items) ?: continue
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

/** The bounds around every one of [ids] drawn now in [items], null when none is. */
private fun union(ids: List<String>, items: Map<String, TableItem>): TableBounds? {
    var left = Float.MAX_VALUE; var top = Float.MAX_VALUE; var right = -Float.MAX_VALUE; var bottom = -Float.MAX_VALUE
    for (i in ids.indices) {
        val b = items[ids[i]]?.bounds ?: continue
        left = minOf(left, b.x); top = minOf(top, b.y); right = maxOf(right, b.right); bottom = maxOf(bottom, b.bottom)
    }
    return if (left > right) null else TableBounds(left, top, right - left, bottom - top)
}

/** STK-047: one brightening or fading of the pile glow. */
private const val PILE_GLOW_MILLIS = 900

/** STK-047: how many rings spread out from a glowing pile's edge. */
private const val PILE_GLOW_RINGS = 3
