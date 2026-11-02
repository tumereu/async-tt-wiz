package org.asyncttwiz.android

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.asyncttwiz.automatum.CombatFlow
import org.asyncttwiz.automatum.CombatShown
import org.asyncttwiz.protocol.*

internal val ArrowColor = Color(0xff5f7a87)
internal val FightsColor = Color(0xffe8890c)
internal val PreviewRed = Color(0xffb3261e)

/** The keywords whose badges combat copies show with Automatum on (CMB-035), lowercase, in this order. */
private val COMBAT_KEYWORDS = listOf("Flying", "Reach", "Menace", "Trample", "First strike", "Double strike", "Deathtouch",
    "Lifelink", "Vigilance")

/**
 * What the combat area shows over the table's drawing (I-D32): the arrow of every block (CMB-031, both modes); with
 * Automatum on also the active target lane of the attacker's staging (CMB-028, its headers tappable), the selection and
 * what it fights (CMB-032), the attackers a picked blocker may block (CMB-034), the damage preview with the eye (CMB-029),
 * keyword badges and the chips of real cards in combat (CMB-035). Drawn only: taps reach the table beneath, except the
 * headers while staging and the eye.
 */
@Composable
internal fun CombatAreaOverlay(combat: CombatController?, play: PlayState, scene: TableScene, viewer: String, writable: Boolean,
    bounds: (TableItem) -> TableBounds) {
    val area = play.board.combat
    val port = scene.viewports.firstOrNull { it.id == "combat" }?.bounds
    val automatum = play.board.automatum
    val shown = if (automatum && combat != null) combat.ready(play) ?: CombatShown.NONE else CombatShown.NONE
    val choices = shown.choices
    val copies = scene.items.filter { it.viewport == "combat" && it.kind == TableItemKind.CARD && it.visible.isNotEmpty() }
    val byOccurrence = copies.associateBy { it.cardId }
    val blocks = CombatStackRules.blocks(area)
    val selected = if (automatum) combat?.selected else null
    val fights = selected?.let { s ->
        blocks.filter { it.first.sourceId == s }.map { it.second.sourceId } + blocks.filter { it.second.sourceId == s }.map { it.first.sourceId }
    }.orEmpty().toSet()
    val blocking = choices.declaring != null && !choices.attackers
    val staging = choices.declaring != null && choices.attackers
    val glowing = if (blocking && selected != null) choices.blocks[selected].orEmpty().toSet() else emptySet()
    val preview = shown.preview?.takeUnless { combat?.numbersHidden == true }
    // The creatures that deal the next combat damage, when the combat has first-strike damage (I-D31)
    val dealers = shown.preview?.dealt?.takeIf { d -> d.any { it.firstStrike } || CombatFlow.hadFirstStrikeStep(play.board) }
        ?.let { d -> d.filter { it.firstStrike == d.first().firstStrike }.map { it.source }.toSet() }.orEmpty()
    val activeLane = if (staging && combat != null) scene.combatLabels.firstOrNull { it.cardId == combat.activeTarget(play) } else null
    if (area != null && port != null) Canvas(Modifier.fillMaxSize()) {
        val d = density
        fun Offset(x: Float, y: Float) = androidx.compose.ui.geometry.Offset(x * d, y * d)
        clipRect(port.x * d, port.y * d, port.right * d, port.bottom * d) {
            if (activeLane != null) drawRoundRect(AutomatumViolet, Offset(activeLane.bounds.x + 1.5f, port.y + 1.5f),
                Size((activeLane.bounds.width - 3f) * d, (port.height - 3f) * d), CornerRadius(6 * d), style = Stroke(2.5f * d))
            for ((blocker, attacker) in blocks) {
                val from = byOccurrence[blocker.id]?.let(bounds) ?: continue
                val to = byOccurrence[attacker.id]?.let(bounds) ?: continue
                val down = from.center.y < to.center.y
                val hot = selected != null && (blocker.sourceId == selected || attacker.sourceId == selected)
                arrow(Offset(from.center.x, if (down) from.bottom + 1f else from.y - 1f),
                    Offset(to.center.x, if (down) to.y - 1f else to.bottom + 1f), if (hot) FightsColor else ArrowColor, d)
            }
            for (item in copies) {
                val card = item.sourceId ?: continue
                val b = bounds(item)
                fun frame(color: Color, width: Float, out: Float) = drawRoundRect(color, Offset(b.x - out, b.y - out),
                    Size((b.width + 2 * out) * d, (b.height + 2 * out) * d), CornerRadius(3 * d), style = Stroke(width * d))
                if (card in dealers) frame(PreviewRed.copy(alpha = .8f), 1.5f, 1f)
                if (card in glowing && item.cardId?.let { id -> area.pairs.any { p -> p.attackers.any { it.id == id } } } == true) {
                    frame(AutomatumViolet.copy(alpha = .25f), 6f, 3f)
                    frame(AutomatumViolet, 1.5f, 1.5f)
                }
                if (card in fights) frame(FightsColor, 2.5f, 1.5f)
                if (card == selected) frame(AutomatumViolet, 3f, 2f)
            }
        }
    }
    if (area == null && !automatum) return
    val damage = preview?.damage.orEmpty()
    val dies = preview?.dies.orEmpty().toSet()
    val inCombat = copies.mapNotNull { it.sourceId }.toSet()
    val computed = LocalComputedBoard.current
    fun label(target: MtgRef) = when (target) {
        is MtgRef.Player -> scene.combatLabels.firstOrNull { it.cardId == null }
        is MtgRef.Card -> scene.combatLabels.firstOrNull { it.cardId == target.cardId }
        else -> null
    }
    // "5 → Bob": damage an attacker deals to its target (unblocked or with trample), by source
    val toTargets = preview?.dealt.orEmpty().filter { label(it.target) != null }.groupBy { it.source }
    for (item in scene.items) {
        if (item.kind != TableItemKind.CARD || item.visible.isEmpty()) continue
        val card = item.sourceId ?: continue
        val copy = item.viewport == "combat"
        val real = !copy && item.zone == PlayZone.BATTLEFIELD && !item.projection
        if (!copy && !real) continue
        val chips = mutableListOf<Pair<String, Color>>()
        val b = bounds(item)
        val attackerCopy = copy && area?.pairs?.any { p -> p.attackers.any { it.id == item.cardId } } == true
        if (attackerCopy && automatum) {
            val keywords = computed.cards[card]?.keywords.orEmpty()
            COMBAT_KEYWORDS.filter { k -> keywords.any { it.equals(k, ignoreCase = true) } }.forEach { chips += it.lowercase() to AutomatumViolet }
            toTargets[card]?.groupBy { it.target }?.forEach { (target, list) ->
                chips += "${list.sumOf { it.amount }} → ${label(target)!!.label.removePrefix("→ ")}" to PreviewRed
            }
        }
        if (real && automatum && card in inCombat) {
            val occurrence = CombatStackRules.occurrences(area).first { it.sourceId == card }
            val pair = area!!.pairs.first { p -> (p.attackers + p.blockers).any { it.id == occurrence.id } }
            val attacker = pair.attackers.any { it.id == occurrence.id }
            when {
                staging && attacker -> chips += (scene.combatLabels.firstOrNull { it.cardId == pair.attackTarget }?.label
                    ?.let { if (it.startsWith("→ ")) it else "→ $it" } ?: "→ attack") to AutomatumViolet
                // Only where it fits: small cards show their combat by their copies alone
                b.width >= 44f -> chips += (if (attacker) "attacking" else "blocking") to ArrowColor
            }
        }
        val dealt = if (copy || card !in inCombat) damage[card] else null
        val dying = (copy || card !in inCombat) && card in dies
        if (chips.isEmpty() && dealt == null && !dying) continue
        // An attacker's badges sit on its side away from the arrows (below it in the attacker's view, above it mirrored)
        val below = attackerCopy && port != null && b.center.y > port.center.y
        Box(Modifier.offset { IntOffset(b.x.dp.roundToPx(), b.y.dp.roundToPx()) }.size(b.width.dp, b.height.dp)) {
            if (chips.isNotEmpty()) Row(Modifier.align(if (below) Alignment.BottomCenter else Alignment.TopCenter)
                .offset(y = if (below) 9.dp else (-9).dp).wrapContentSize(unbounded = true),
                horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                for ((text, color) in chips) PreviewChip(text, color, Modifier, text, filled = true)
            }
            if (dealt != null) Badge("−$dealt", PreviewRed, Modifier.align(Alignment.TopEnd).offset(x = 7.dp, y = 4.dp),
                "Would take $dealt damage${if (dying) " and die" else ""}")
            if (dying) Badge("☠", Color(0xff263238), Modifier.align(Alignment.TopStart).offset(x = (-7).dp, y = 4.dp),
                if (dealt == null) "Would die" else "Dies")
        }
    }
    // The target's header: "16 → 11" over its value at the right end
    if (preview != null) for (lane in scene.combatLabels) {
        if (lane.value.isEmpty()) continue
        val now = lane.value.toIntOrNull() ?: continue
        val change = if (lane.cardId == null) CombatStackRules.defendingPlayer(play.board)?.let { preview.life[it] } ?: 0
            else -(damage[lane.cardId] ?: 0)
        if (change == 0) continue
        val b = lane.bounds
        val tint = if (lane.side == viewer) OwnTableColor else OpponentTableColor
        Box(Modifier.offset { IntOffset(b.x.dp.roundToPx(), b.y.dp.roundToPx()) }.size(b.width.dp, b.height.dp)
            .semantics { contentDescription = "Header would go from $now to ${now + change}" }) {
            Text("$now → ${now + change}", Modifier.align(Alignment.CenterEnd).padding(end = lane.inset.dp)
                .background(androidx.compose.ui.graphics.lerp(Color.White, tint, .13f), RoundedCornerShape(50))
                .padding(horizontal = 3.dp), color = PreviewRed, fontWeight = FontWeight.Bold, fontSize = lane.fontSize.sp,
                lineHeight = (lane.fontSize * 1.2f).sp, maxLines = 1, softWrap = false)
        }
    }
    for ((player, change) in preview?.life.orEmpty()) {
        if (change == 0) continue
        val item = scene.items.firstOrNull { it.id == "life/$player" } ?: continue
        val b = bounds(item)
        Box(Modifier.offset { IntOffset(b.x.dp.roundToPx(), b.y.dp.roundToPx()) }.size(b.width.dp, b.height.dp)) {
            PreviewChip(if (change < 0) "−${-change}" else "+$change", if (change < 0) PreviewRed else Color(0xff237d68),
                Modifier.align(Alignment.BottomEnd).offset(x = 10.dp, y = 6.dp), "Life would change by $change")
        }
    }
    if (area == null || port == null || scene.combatLabels.isEmpty()) return
    // CMB-028: tapping a target's header makes its lane the active one
    if (staging && writable && combat != null) for (lane in scene.combatLabels) {
        val b = lane.bounds
        Box(Modifier.offset { IntOffset(b.x.dp.roundToPx(), b.y.dp.roundToPx()) }.size(b.width.dp, b.height.dp)
            .semantics { contentDescription = "Attack ${lane.label.removePrefix("→ ")}" }
            .clickable { combat.chooseTarget(play, lane.cardId) })
    }
    // CMB-029: the eye at the area's top-right corner hides and shows the preview's numbers
    if (shown.preview != null && combat != null) {
        val right = scene.combatLabels.maxOf { it.bounds.right } + 3f
        Box(Modifier.offset { IntOffset((right + 3f).dp.roundToPx(), port.y.dp.roundToPx()) }.size(30.dp)
            .background(Color.White, CircleShape).border(1.dp, AutomatumViolet, CircleShape), contentAlignment = Alignment.Center) {
            EyeButton(hidden = combat.numbersHidden, combat::toggleNumbers, "damage preview")
        }
    }
}

/** An arrow from [start] to [end] (px) with its head at [end]. */
private fun DrawScope.arrow(start: Offset, end: Offset, color: Color, d: Float) {
    val dx = end.x - start.x; val dy = end.y - start.y
    val length = kotlin.math.sqrt(dx * dx + dy * dy).takeIf { it > 1f } ?: return
    val ux = dx / length; val uy = dy / length
    val head = minOf(7f * d, length / 2)
    val base = Offset(end.x - ux * head, end.y - uy * head)
    drawLine(color, start, base, 2f * d)
    drawPath(Path().apply {
        moveTo(end.x, end.y)
        lineTo(base.x - uy * head * .55f, base.y + ux * head * .55f)
        lineTo(base.x + uy * head * .55f, base.y - ux * head * .55f)
        close()
    }, color)
}

@Composable
internal fun PreviewChip(text: String, color: Color, modifier: Modifier, description: String, filled: Boolean = false) {
    // Wider than a small card when it has to be: one line, never wrapped. Drawn only, so taps reach the card beneath.
    Box(modifier.wrapContentSize(unbounded = true).semantics { contentDescription = description }
        .background(if (filled) color else Color.White, RoundedCornerShape(6.dp))
        .border(1.5.dp, color, RoundedCornerShape(6.dp))) {
        Text(text, Modifier.padding(horizontal = 3.dp), color = if (filled) Color.White else color, fontSize = 9.sp,
            fontWeight = FontWeight.Bold, lineHeight = 11.sp, maxLines = 1, softWrap = false)
    }
}

@Composable
private fun Badge(text: String, color: Color, modifier: Modifier, description: String) {
    Box(modifier.size(18.dp).semantics { contentDescription = description }.background(color, CircleShape)
        .border(1.dp, Color.White, CircleShape), contentAlignment = Alignment.Center) {
        Text(text, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold, lineHeight = 10.sp, maxLines = 1, softWrap = false)
    }
}
