package org.asyncttwiz.android

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.UnfoldLess
import androidx.compose.material3.Icon
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
/** The combat area's panel, under its lane header pills. */
internal val CombatPanelColor = Color(0xfffbfcfb)

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
    /** CMB-007: folds the combat area away (true) to see the battlefield under it, or brings it back. */
    fold: ((Boolean) -> Unit)? = null, bounds: (TableItem) -> TableBounds) {
    val area = play.board.combat
    // CMB-007: the folded-away area is a button where it was, bringing it back
    val folded = scene.combatFolded
    if (area != null && folded != null && fold != null) {
        Box(Modifier.offset { IntOffset(folded.x.dp.roundToPx(), folded.y.dp.roundToPx()) }.offset((-48).dp, (-16).dp)
            .size(96.dp, 32.dp).background(Color.White, RoundedCornerShape(16.dp)).border(1.5.dp, ArrowColor, RoundedCornerShape(16.dp))
            .semantics { contentDescription = "Show the combat area" }.clickable { fold(false) }, contentAlignment = Alignment.Center) {
            Text("Combat ▴", color = Color(0xff263238), fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
    val port = scene.viewports.firstOrNull { it.id == "combat" }?.bounds
    val shown = if (combat != null) combat.ready(play) ?: CombatShown.NONE else CombatShown.NONE
    val choices = shown.choices
    val copies = scene.items.filter { it.viewport == "combat" && it.kind == TableItemKind.CARD && it.visible.isNotEmpty() }
    val byOccurrence = copies.associateBy { it.cardId }
    val blocks = CombatStackRules.blocks(area)
    val selected = combat?.selected
    val fights = selected?.let { s ->
        blocks.filter { it.first.sourceId == s }.map { it.second.sourceId } + blocks.filter { it.second.sourceId == s }.map { it.first.sourceId }
    }.orEmpty().toSet()
    val blocking = choices.declaring != null && !choices.attackers
    val staging = choices.declaring != null && choices.attackers
    val glowing = if (blocking && selected != null) choices.blocks[selected].orEmpty().toSet() else emptySet()
    val preview = shown.preview?.takeUnless { combat?.numbersHidden == true }
    // The creatures that deal the next combat damage, when the combat has first-strike damage (I-D31)
    val dealers = shown.preview?.dealt?.takeIf { d -> d.any { it.firstStrike } || AutomatumBoards.hadFirstStrikeStep(play.board) }
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
        val chips = mutableListOf<Triple<String, Color, String>>()
        val b = bounds(item)
        val attackerCopy = copy && area?.pairs?.any { p -> p.attackers.any { it.id == item.cardId } } == true
        if (attackerCopy) {
            val keywords = computed.cards[card]?.keywords.orEmpty()
            COMBAT_KEYWORDS.filter { k -> keywords.any { it.equals(k, ignoreCase = true) } }
                .forEach { chips += Triple(it.lowercase(), AutomatumViolet, it.lowercase()) }
            toTargets[card]?.groupBy { it.target }?.forEach { (target, list) ->
                val text = "${list.sumOf { it.amount }} → ${label(target)!!.label.removePrefix("→ ")}"
                chips += Triple(text, PreviewRed, text)
            }
        }
        if (real && card in inCombat) {
            val occurrence = CombatStackRules.occurrences(area).first { it.sourceId == card }
            val pair = area!!.pairs.first { p -> (p.attackers + p.blockers).any { it.id == occurrence.id } }
            val attacker = pair.attackers.any { it.id == occurrence.id }
            when {
                staging && attacker -> ((scene.combatLabels.firstOrNull { it.cardId == pair.attackTarget }?.label
                    ?.let { if (it.startsWith("→ ")) it else "→ $it" } ?: "→ attack")).let { chips += Triple(it, AutomatumViolet, it) }
                // Only where it fits: small cards show their combat by their copies alone
                b.width >= 44f -> (if (attacker) "attacking" else "blocking").let { chips += Triple(it, ArrowColor, it) }
            }
        }
        val dealt = if (copy || card !in inCombat) damage[card] else null
        val dying = (copy || card !in inCombat) && card in dies
        if (chips.isEmpty() && dealt == null && !dying) continue
        // An attacker's badges sit on its side away from the arrows (below it in the attacker's view, above it mirrored),
        // stacked one per line no wider than the card, so neighbours' chips never run into each other (combat review #2)
        val below = attackerCopy && port != null && b.center.y > port.center.y
        Box(Modifier.offset { IntOffset(b.x.dp.roundToPx(), b.y.dp.roundToPx()) }.size(b.width.dp, b.height.dp)) {
            if (chips.isNotEmpty()) Box(Modifier.align(if (below) Alignment.BottomCenter else Alignment.TopCenter).fillMaxWidth().height(0.dp)) {
                Column(Modifier.fillMaxWidth().wrapContentHeight(if (below) Alignment.Top else Alignment.Bottom, unbounded = true)
                    .offset(y = if (below) (-3).dp else 3.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    for ((text, color, description) in (if (below) chips else chips.asReversed())) CombatChip(text, color, b.width, description)
                }
            }
            // The preview's badges sit over the card's middle, clear of its name (combat review #12)
            if (dealt != null || dying) Row(Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                if (dying) Badge("☠", Color(0xff263238), Modifier, if (dealt == null) "Would die" else "Dies")
                if (dealt != null) Badge("−$dealt", PreviewRed, Modifier, "Would take $dealt damage${if (dying) " and die" else ""}")
            }
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
            // The tag is the header pill's right end (same height, tint and rounding), covering the value drawn there
            Box(Modifier.align(Alignment.CenterEnd).padding(end = lane.inset.dp, top = (lane.padding / 2).dp,
                bottom = (lane.padding / 2).dp).fillMaxHeight()
                .background(androidx.compose.ui.graphics.lerp(CombatPanelColor, tint, .13f), RoundedCornerShape(50))
                .padding(start = 4.dp, end = lane.padding.dp), contentAlignment = Alignment.Center) {
                Text("$now → ${now + change}", color = PreviewRed, fontWeight = FontWeight.Bold, fontSize = lane.fontSize.sp,
                    lineHeight = (lane.fontSize * 1.2f).sp, maxLines = 1, softWrap = false)
            }
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
    // Just right of the panel's top-right corner: the fold button (CMB-007), then the eye hiding and showing the preview's
    // numbers (CMB-029)
    val panel = scene.combatPanel ?: return
    // Icon-only, so holding either says what it does (I-D48)
    if (fold != null) Box(Modifier.offset { IntOffset((panel.right + 3f).dp.roundToPx(), panel.y.dp.roundToPx()) }) {
        HoldTip("Hide the combat area") {
            Box(Modifier.size(30.dp).background(Color.White, CircleShape).border(1.dp, ArrowColor, CircleShape)
                .semantics { contentDescription = "Hide the combat area" }.clickable { fold(true) }, contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.UnfoldLess, null, Modifier.size(18.dp), tint = Color(0xff263238))
            }
        }
    }
    // Finalreview-combat #4 (CMB-029): the way to the damage needs a choice, which the preview took as suggested
    if (preview?.assumed == true) Box(Modifier.offset { IntOffset((panel.x + 2f).dp.roundToPx(), (panel.bottom + 3f).dp.roundToPx()) }) {
        PreviewChip("Depends on a choice", PreviewRed, Modifier, "The preview assumes the suggested answer to a choice on the way")
    }
    val eyeY = panel.y + if (fold != null) 34f else 0f
    if (shown.preview != null && combat != null) {
        Box(Modifier.offset { IntOffset((panel.right + 3f).dp.roundToPx(), eyeY.dp.roundToPx()) }) {
            HoldTip(if (combat.numbersHidden) "Show the damage preview" else "Hide the damage preview") {
                Box(Modifier.size(30.dp).background(Color.White, CircleShape).border(1.dp, AutomatumViolet, CircleShape),
                    contentAlignment = Alignment.Center) {
                    EyeButton(hidden = combat.numbersHidden, combat::toggleNumbers, "damage preview")
                }
            }
        }
    }
}

/** A tag of a combatant (CMB-029, CMB-035): one line, no wider than its card ([width] dp), its text made smaller to fit.
 * Drawn only, so taps reach the card beneath. */
@Composable
private fun CombatChip(text: String, color: Color, width: Float, description: String) {
    val room = (width - 8f).coerceAtLeast(8f)
    val natural = CombatStackPresentation.laneTextWidth(text, 9f)
    val size = if (natural <= room) 9f else maxOf(5.5f, 9f * room / natural)
    Box(Modifier.widthIn(max = width.dp).semantics { contentDescription = description }
        .background(color, RoundedCornerShape(6.dp)).border(1.5.dp, color, RoundedCornerShape(6.dp))) {
        Text(text, Modifier.padding(horizontal = 3.dp), color = Color.White, fontSize = size.sp,
            fontWeight = FontWeight.Bold, lineHeight = (size + 2f).sp, maxLines = 1, softWrap = false,
            overflow = androidx.compose.ui.text.style.TextOverflow.Clip)
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
