package org.asyncttwiz.android

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.asyncttwiz.protocol.*
import org.asyncttwiz.appui.NativeColors
import org.asyncttwiz.appui.nativeSaveCount
import org.asyncttwiz.appui.nativeRestoreToCount

/** ZON-079, ZON-080 (I-D50): the mana sources Autotap would tap for the raised hand card, and the extras' chips. */
val AutotapAmber = Color(0xffe09a1c)

fun DrawScope.drawTable(scene: TableScene, viewer: String, turnPlayer: String, preview: TableGesturePreview,
    alphas: Map<String, Float> = emptyMap(), artwork: Map<String, ImageBitmap> = emptyMap(),
    cache: TableDrawCache, presenceDot: PresenceDot? = null,
    /** Automatum cast (I-D6): legal targets glow violet: cards by card id (and their Stack entries and combat copies),
     * players by their life square's id. */
    glow: Set<String> = emptySet(),
    /** I-D46 (a): the viewer's cards Automatum lets them do something with now glow softly from behind: card id to the
     * glow's strength (0–1; it fades in and out, TBL-117). */
    playable: Map<String, Float> = emptyMap(),
    /** I-D46 (b): the item shaken by a refused gesture (an item id, card id or "life/<account>") and its offset in dp. */
    shake: Pair<String, Float>? = null,
    /** ZON-079 (I-D50): the mana sources Autotap would tap for the raised hand card, outlined in amber, by card id. */
    sources: Set<String> = emptySet(),
    /** TBL-117, TBL-122: the members of the battlefield groups a tap opens, glowing in [GroupExpandGlow] from behind: card
     * id to the glow's strength. */
    expandGlow: Map<String, Float> = emptyMap(),
    /** CMB-028 (owner, 2026-11-05): while the viewer declares attackers, their creatures that may attack glow red-orange
     * from behind, by card id (cyan stays "can activate", TBL-117). */
    attackable: Set<String> = emptySet(),
    /** TBL-117 (owner playtest 2026-11-06): the item a finger is down on and how far its glow has grown (0–1): a glowing
     * card's glow grows a little and brightens under the finger. */
    pressed: Pair<String, Float>? = null,
    /** STK-047 (owner 2026-11-07): the targets picked so far (ids as [glow]): their violet glow at full strength, a violet
     * wash over them and a white check in a violet dot at their top right. */
    picked: Set<String> = emptySet(),
    /** Whether the piles' count bubbles are drawn here; false when the caller draws them over its overlays
     * ([drawPileBadges]: over the Stack's target arrows, STK-058). */
    pileBadges: Boolean = true) {
    val density = density
    with(TableCardPainter(density, scene, cache, artwork)) {
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
    // CMB-001, CMB-007 (owner playtest 2026-11-06): the band between the battlefields is part of the table: a cream area
    // across its whole width, collapsed to its chip row or open with the lanes, their separators running through it (the
    // chips and values are drawn over it, CombatAreaOverlay)
    scene.world?.let { world -> world.horizontalBand(TableBounds(0f, scene.fields.first().gridBounds.bottom,
        0f, scene.fields.last().gridBounds.y - scene.fields.first().gridBounds.bottom)) }?.let { b ->
        clipRect(b.x * density, b.y * density, b.right * density, b.bottom * density) {
            drawRect(CombatBandColor, xy(b.x, b.y), size(b.width, b.height))
            if (scene.combatPanel != null) for ((from, to) in scene.combatLaneDividers) drawLine(
                Color.Black.copy(alpha = CombatStackPresentation.LANE_DIVIDER_ALPHA), xy(from.x, from.y), xy(to.x, to.y),
                CombatStackPresentation.laneDividerStroke(scene.combatCardWidth) * density)
            // CMB-050 (owner playtest 2026-11-10): what reached each pair's target, a small splash behind its blockers
            for (splash in scene.combatLaneDamage) damageSplash(xy(splash.bounds.x, splash.bounds.y),
                size(splash.bounds.width, splash.bounds.height), splash.amount.toString())
        }
    }
    // HUD surfaces cover world drawings and link lines, including the screen-pinned Stack gutter; a row in a bar (landscape,
    // TBL-009) sits on the bar's own colour.
    for (port in scene.viewports.filter { it.hand || !it.bar && (it.id.startsWith("dock/") || it.id.startsWith("counters/")) }) {
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
        }
    }
    val focused = listOfNotNull(preview.pressedId?.let { id -> scene.items.firstOrNull { it.id == id }?.cardId })
    val relatedSources = scene.items.filter { it.cardId in focused }.mapNotNull { it.sourceId }.toSet()
    fun outlines(i: TableItem, b: TableBounds, card: Boolean = false) {
        if (i.sourceId != null && i.sourceId in relatedSources) {
            val inset = if (card) -2f else 3f
            drawRect(Color(0xffbd982f), xy(b.x + inset, b.y + inset),
                size((b.width - 2 * inset).coerceAtLeast(0f), (b.height - 2 * inset).coerceAtLeast(0f)), style = Stroke(2 * density))
        }
        val outset = if (card) 2f else 0f
        if (i.id == preview.pressedId) drawRect(Color(0xffe7ab24), xy(b.x - outset, b.y - outset),
            size(b.width + 2 * outset, b.height + 2 * outset), style = Stroke(2 * density))
        if (sources.isNotEmpty() && i.zone == PlayZone.BATTLEFIELD && !i.projection && i.cardId in sources) {
            drawRect(AutotapAmber.copy(alpha = .35f), xy(b.x - outset - 2, b.y - outset - 2),
                size(b.width + 2 * outset + 4, b.height + 2 * outset + 4), style = Stroke(6 * density))
            drawRect(AutotapAmber, xy(b.x - outset, b.y - outset), size(b.width + 2 * outset, b.height + 2 * outset), style = Stroke(2.5f * density))
        }
        val chosen = picked.isNotEmpty() && (i.cardId in picked || i.sourceId in picked || i.id in picked)
        if (chosen || glow.isNotEmpty() && (i.cardId in glow || i.sourceId in glow || i.id in glow)) {
            // TBL-117, STK-047 (owner playtest 2026-11-06): under a finger the target's glow grows a little and brightens;
            // a picked target's is always at full strength
            val press = if (chosen) 1f else if (pressed != null && pressed.first == i.id) pressed.second else 0f
            val halo = 2f + 1.5f * press
            drawRect(AutomatumViolet.copy(alpha = .35f + .25f * press), xy(b.x - outset - halo, b.y - outset - halo),
                size(b.width + 2 * outset + 2 * halo, b.height + 2 * outset + 2 * halo), style = Stroke((6 + 3 * press) * density))
            drawRect(AutomatumViolet, xy(b.x - outset, b.y - outset), size(b.width + 2 * outset, b.height + 2 * outset), style = Stroke(2.5f * density))
        }
        if (chosen) {
            // STK-047 (owner 2026-11-07): picked: a violet wash over it and a white check in a violet dot at its top right
            drawRect(AutomatumViolet.copy(alpha = .3f), xy(b.x, b.y), size(b.width, b.height))
            val r = (minOf(b.width, b.height) * .2f).coerceIn(4f, 8f)
            val c = xy(b.right - r - 1f, b.y + r + 1f)
            drawCircle(AutomatumViolet, r * density, c)
            drawCircle(Color.White, r * density, c, style = Stroke(.8f * density))
            val check = androidx.compose.ui.graphics.Path().apply {
                moveTo(c.x - .5f * r * density, c.y)
                lineTo(c.x - .12f * r * density, c.y + .4f * r * density)
                lineTo(c.x + .52f * r * density, c.y - .35f * r * density)
            }
            drawPath(check, Color.White, style = Stroke(1.4f * density, cap = androidx.compose.ui.graphics.StrokeCap.Round,
                join = androidx.compose.ui.graphics.StrokeJoin.Round))
        }
    }
    for (i in scene.items) for (part in i.visible) {
        // Preserve clipped/occluded edges, but leave room around a whole face for its outline.
        // ... more for the card a finger is down on, whose glow grows (TBL-117)
        val outset = if (i.kind != TableItemKind.CARD) 0f else if (pressed?.first == i.id) 8f else 4f
        val left = part.x - if (part.x == i.bounds.x) outset else 0f
        val top = part.y - if (part.y == i.bounds.y) outset else 0f
        val right = part.right + if (part.right == i.bounds.right) outset else 0f
        val bottom = part.bottom + if (part.bottom == i.bounds.bottom) outset else 0f
        // An empty fragment (or one with no finite size) draws nothing: no clip, layer or offset for it.
        if (!(right > left && bottom > top)) continue
        clipRect(left * density, top * density, right * density, bottom * density) {
        val alpha = alphas[i.id] ?: 1f
        val shift = shake?.takeIf { (id, _) -> i.id == id || i.cardId == id }?.second ?: 0f
        // Playtest crash 2026-11-03 ("Underflow in restore" on Keep): the fade's layer and the shake's offset are undone
        // by restoring to the save count from before them, never by counting restores (a layer the renderer didn't push
        // must not pop a save of the nodes around the table).
        val saved = nativeSaveCount()
        // Opaque cards need only the existing clip, not a separate GPU render target per fragment.
        if (alpha < 1f) drawContext.canvas.saveLayer(androidx.compose.ui.geometry.Rect(left * density, top * density,
            right * density, bottom * density), cache.layerPaint.apply { this.alpha = alpha })
        if (shift != 0f) { drawContext.canvas.save(); drawContext.canvas.translate(shift * density, 0f) }
        try {
        val b = i.bounds
        val color = if (i.owner == null) Color(0xff59656a) else if (i.owner == viewer) OwnTableColor else OpponentTableColor
        when (i.kind) {
             TableItemKind.HAND -> Unit
            TableItemKind.LIFE -> {
                drawRect(color, xy(b.x, b.y), size(b.width, b.height))
                paint.color = NativeColors.WHITE; paint.textSize = 24 * density; paint.isFakeBoldText = true
                centeredText(i.value, b, 24f)
                paint.color = NativeColors.DKGRAY; paint.textSize = 11 * density; paint.isFakeBoldText = false
                if (presenceDot != null && i.owner != viewer) {
                    // Opponent presence: inside the top-right corner, clear of the status dot/bubble below the square.
                    val center = xy(b.right - 7f, b.y + 7f)
                    drawCircle(Color.White, 4.5f * density, center)
                    drawCircle(presenceDot.color, 3.5f * density, center)
                }
            }
            TableItemKind.PILE -> {
                if (i.cards.isEmpty()) {
                    if (i.zone in listOf(PlayZone.LIBRARY, PlayZone.GRAVEYARD, PlayZone.EXILE, PlayZone.STACK)) emptySlot(i, color)
                } else if (i.zone != PlayZone.STACK) {
                    cardFace(i, b.x, b.y, color, i.faceTitle)
                    if (i.covered) {
                         val z = b.width / 28f
                         paint.color = NativeColors.WHITE; paint.textSize = 17 * z * density
                         text("✧", b.x + 8 * z, b.y + 26 * z, 1)
                        paint.color = NativeColors.DKGRAY; paint.textSize = 11 * density
                    }
                    if (i.temp) {
                        // A small order number keeps several face-down Temp piles apart.
                        val z = b.width / 28f
                        val tag = TableBounds(b.right - 10f * z, b.bottom - 10f * z, 9f * z, 9f * z)
                        drawRoundRect(Color.White, xy(tag.x, tag.y), size(tag.width, tag.height), CornerRadius(2 * z * density))
                        paint.color = color.toArgb(); paint.isFakeBoldText = true
                        centeredText(i.tempNumber.toString(), tag.copy(x = tag.x - 4f, width = tag.width + 8f), 8f * z)
                        paint.color = NativeColors.DKGRAY; paint.textSize = 11 * density; paint.isFakeBoldText = false
                    }
                }
            }
            // TRN-069: a player's dungeon, drawn like a pile's top card
            TableItemKind.DUNGEON -> cardFace(i, b.x, b.y, color, i.faceTitle)
             TableItemKind.CARD -> {
                   val transform = if (i.viewport == "combat") TableCardTransform(b.width, b.height, 0f) else i.faceTransform()
                  val z = transform.width / 28f
                  val cardWidth = transform.width; val cardHeight = transform.height
                  rotate(transform.rotation, xy(b.center.x, b.center.y)) {
                     val x = b.center.x - cardWidth / 2; val y = b.center.y - cardHeight / 2
                      // TBL-117 (owner playtest 2026-11-06): under a finger a card's glow grows a little and brightens
                      val press = if (pressed != null && pressed.first == i.id) pressed.second else 0f
                      val grow = 1f + .6f * press; val bright = 1f + .7f * press
                      // I-D46 (a): a soft halo behind the card, fading outward within the card's outline room
                      val glowAlpha = if (playable.isEmpty() || i.zone == PlayZone.STACK) 0f else playable[i.cardId] ?: 0f
                      if (glowAlpha > 0f) for (ring in 4 downTo 1) {
                          val spread = ring * grow
                          drawRoundRect(AutomatumCyan.copy(alpha = (.16f * glowAlpha * bright).coerceAtMost(1f)), xy(x - spread, y - spread),
                              size(cardWidth + 2 * spread, cardHeight + 2 * spread), CornerRadius((2 + spread) * density))
                      }
                      // TBL-122: a group a tap opens glows in its own colour, a thin grey edge setting it off the table
                      val expandAlpha = if (expandGlow.isEmpty()) 0f else expandGlow[i.cardId] ?: 0f
                      if (expandAlpha > 0f) {
                          for (ring in 4 downTo 1) {
                              val spread = ring * grow
                              drawRoundRect(GroupExpandGlow.copy(alpha = .75f * expandAlpha), xy(x - spread, y - spread),
                                  size(cardWidth + 2 * spread, cardHeight + 2 * spread), CornerRadius((2 + spread) * density))
                          }
                          drawRoundRect(GroupExpandEdge.copy(alpha = .9f * expandAlpha), xy(x - 4f * grow, y - 4f * grow),
                              size(cardWidth + 8f * grow, cardHeight + 8f * grow), CornerRadius(6f * density), style = Stroke(1.2f * density))
                      }
                      if (i.viewport != "combat" && !i.projection && i.cardId in attackable) for (ring in 4 downTo 1) {
                          val spread = ring * grow
                          drawRoundRect(AttackOrange.copy(alpha = (.3f * bright).coerceAtMost(1f)), xy(x - spread, y - spread),
                              size(cardWidth + 2 * spread, cardHeight + 2 * spread), CornerRadius((2 + spread) * density))
                      }
                      scale(i.flipScale, 1f, xy(b.center.x, b.center.y)) { cardFace(i, x, y, color, i.value) }
                      // CMB-047 (owner playtest 2026-11-07): a stack's staged member, at its right end: a wash and an edge in
                      // the attackers' red-orange or the blocks' arrow colour
                      i.staged?.let { role ->
                          val tint = if (role == CombatRole.ATTACKER) AttackOrange else ArrowColor
                          drawRoundRect(tint.copy(alpha = .3f), xy(x, y), size(cardWidth, cardHeight), CornerRadius(2 * density))
                          drawRoundRect(tint, xy(x - 1f, y - 1f), size(cardWidth + 2f, cardHeight + 2f), CornerRadius(3 * density),
                              style = Stroke(2f * density))
                      }
                      if (i.copies > 1) {
                          val label = "×${i.copies}"
                          // TBL-053: a battlefield stack's count badge is sized with its card (a panel's keeps its size)
                          val s = if (i.zone == PlayZone.BATTLEFIELD && i.viewport != "combat") z * .7f else 1f
                          paint.textSize = 11 * s * density
                          val badgeWidth = cache.measure(label) / density + 6f * s
                          drawRoundRect(color, xy(x, y + cardHeight - 16f * s), size(badgeWidth, 16f * s), CornerRadius(3 * s * density))
                          paint.color = NativeColors.WHITE
                          text(label, x + 3f * s, y + cardHeight - 4f * s, label.length)
                          paint.color = NativeColors.DKGRAY
                      }
                     paint.textSize = 11 * density
                     outlines(i, TableBounds(x, y, cardWidth, cardHeight), card = true)
                 }
            }
        }
        if (i.kind != TableItemKind.CARD) outlines(i, b)
        } finally {
            nativeRestoreToCount(saved)
        }
    }
    }
    // STK-058: drawn here unless the caller draws them over its overlays (drawPileBadges)
    if (pileBadges) pileBadges(this@with, scene)
    val landing = tableDragLanding(scene, preview)
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
}

/**
 * STK-058 (owner gallery review 2026-11-07): the piles' count bubbles of [scene] (the Stack's corner count, a pile's
 * count, ZON-092's counter mark, the revealed top's eye), for drawing over the table's overlays: the Stack's target
 * arrows and highlights never cover them.
 */
fun DrawScope.drawPileBadges(scene: TableScene, cache: TableDrawCache) =
    pileBadges(TableCardPainter(density, scene, cache, emptyMap()), scene)

private fun DrawScope.pileBadges(painter: TableCardPainter, scene: TableScene) = with(painter) {
    // Badge bounds belong to the pile, not its top card's face. Draw above Stack projections too.
    // An empty Temp pile shows only its order number, no "0" count.
    for (i in scene.items.filter { it.kind == TableItemKind.PILE && !(it.temp && it.cards.isEmpty()) }) {
        val badge = if (i.zone == PlayZone.STACK) i.stackBadge else i.pileCountBubble
        val port = if (i.countBadge != null) TableBounds(0f, 0f, size.width / density, size.height / density)
            else scene.viewports.single { it.id == i.viewport }.bounds
        if (i.visible.isNotEmpty()) clipRect(port.x * density, port.y * density, port.right * density, port.bottom * density) {
            drawCircle(Color(0xff345451), badge.width / 2 * density, xy(badge.center.x, badge.center.y))
            drawCircle(Color.White, badge.width / 2 * density, xy(badge.center.x, badge.center.y), style = Stroke(density))
            paint.color = NativeColors.WHITE; paint.isFakeBoldText = true
            val previousSize = paint.textSize
            paint.textSize = (if (i.zone == PlayZone.STACK) 11f else 8.8f) * density
            text(i.value, badge.center.x - cache.measure(i.value) / density / 2, badge.center.y - cache.centerOffset() / density, 5)
            paint.textSize = previousSize
            paint.color = NativeColors.DKGRAY; paint.isFakeBoldText = false
            if (i.countersInside) {
                // ZON-092: a card in the pile carries counters: a small violet counter (the colour of the counter chips,
                // TBL-113) ringed in white like the count bubble, at the corner opposite it
                val center = xy(i.bounds.right - 1f, i.bounds.y + 1f)
                drawCircle(EmblemChipColor, 5f * density, center)
                drawCircle(Color.White, 5f * density, center, style = Stroke(density))
                drawCircle(Color.White, 1.6f * density, center)
            }
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
}

/** Uses the production renderer: size and text are not a separately maintained approximation. */
@Composable
fun SampleTableCard(appearance: CardAppearance, scale: Float, modifier: Modifier = Modifier) {
    val bounds = TableBounds(0f, 0f, 28f * scale, 40f * scale)
    val sample = TableItem("sample", bounds, "Sample card: Sprite, 2/3", "Sprite",
        owner = "sample", kind = TableItemKind.CARD, corner = "2/3", typeIcon = "♙")
    val scene = TableScene(listOf(sample), emptyList(), appearance = appearance)
    val density = LocalDensity.current.density
    val cache = remember(density) { TableDrawCache(density) }
    Canvas(modifier.size(bounds.width.dp, bounds.height.dp).semantics { contentDescription = sample.label }) {
        drawTable(scene, "sample", "sample", TableGesturePreview(), cache = cache)
    }
}
