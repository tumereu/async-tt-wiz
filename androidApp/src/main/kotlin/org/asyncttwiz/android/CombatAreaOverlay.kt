package org.asyncttwiz.android

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.PersonAddAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.testTag
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
import androidx.compose.ui.semantics.stateDescription
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
 * keyword badges and the chips of real cards in combat (CMB-035), the attacker's choices and the costs of combat on the
 * staged attackers and drafted blockers (CMB-036–CMB-038). Drawn only: taps reach the table beneath, except the headers
 * while staging, the attacking player's own staging chips, held chips and the eye.
 */
@Composable
internal fun CombatAreaOverlay(combat: CombatController?, play: TableBoard, scene: TableScene, viewer: String, writable: Boolean,
    /** CMB-001 (owner, 2026-11-05): opens the band from the thin strip before any attacker is staged. */
    openBand: (() -> Unit)? = null, bounds: (TableItem) -> TableBounds) {
    // CMB-001: a band tapped open before any attacker is staged shows its empty lanes (an area drawn only)
    val area = play.board.combat ?: CombatArea(TablePresentation.OPENED_COMBAT_AREA).takeIf { scene.combatPanel != null }
    // CMB-001: while the viewer declares attackers with none staged the band is a thin strip that pulses; a tap opens it
    // ... short of the Stack at its right, which keeps its own taps
    val strip = scene.combatStrip?.let { b -> scene.stackRegion?.takeIf { it.intersection(b) != null && it.x > b.x }
        ?.let { b.copy(width = it.x - 4f - b.x) } ?: b }
    if (strip != null && strip.height > 0f) CombatStrip(strip, writable && openBand != null) { openBand?.invoke() }
    val port = scene.viewports.firstOrNull { it.id == "combat" }?.bounds
    val shown = if (combat != null) combat.readyOrLast(play) ?: CombatShown.NONE else CombatShown.NONE
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
    // CMB-036–CMB-038: the attacker's choices with the staged attackers, which only the attacking player changes
    val staged = area?.pairs.orEmpty().flatMap { it.attackers }.map { it.sourceId }.toSet()
    val attackChoices = if (staging) CombatFlow.attackChoices(play.board).filterKeys { it in staged } else emptyMap()
    val choosing = staging && writable && combat != null && viewer == choices.declaring
    val picking = if (choosing) combat?.picked else null
    val bands = attackChoices.values.filter { it.band != null }.groupBy { it.band!! }.mapValues { (_, c) -> c.map { it.cardId } }
        .filterValues { it.size >= 2 }
    val enlistedBy = attackChoices.values.flatMap { c -> c.enlisted.filter { it !in staged }.map { it to c.cardId } }.toMap()
    val enlistable = if (picking is CombatController.Picking.Enlist) choices.enlistable.filter { it !in staged }.toSet() else emptySet()
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
            // CMB-038: a band's copies joined by a violet bar on their arrows' side, with a tick to each member
            for (members in bands.values) {
                val boxes = copies.filter { it.sourceId in members }.map(bounds).sortedBy { it.x }
                if (boxes.size < 2) continue
                val down = port.center.y > boxes.first().center.y
                val y = if (down) boxes.maxOf { it.bottom } + 4f else boxes.minOf { it.y } - 4f
                drawLine(AutomatumViolet, Offset(boxes.first().center.x, y), Offset(boxes.last().center.x, y), 3f * d,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round)
                for (box in boxes) drawLine(AutomatumViolet, Offset(box.center.x, y), Offset(box.center.x, if (down) box.bottom else box.y), 3f * d)
            }
        }
        // CMB-036: while the viewer picks what a staged attacker enlists, the creatures it may enlist glow violet
        if (enlistable.isNotEmpty()) for (item in scene.items) {
            if (item.kind != TableItemKind.CARD || item.viewport == "combat" || item.zone != PlayZone.BATTLEFIELD || item.projection) continue
            if (item.sourceId !in enlistable || item.visible.isEmpty()) continue
            val b = bounds(item)
            drawRoundRect(AutomatumViolet.copy(alpha = .25f), Offset(b.x - 3f, b.y - 3f), Size((b.width + 6f) * d, (b.height + 6f) * d),
                CornerRadius(4 * d), style = Stroke(6f * d))
            drawRoundRect(AutomatumViolet, Offset(b.x - 1.5f, b.y - 1.5f), Size((b.width + 3f) * d, (b.height + 3f) * d),
                CornerRadius(3 * d), style = Stroke(1.5f * d))
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
        val actions = mutableListOf<StagingChip>()
        val b = bounds(item)
        val attackerCopy = copy && area?.pairs?.any { p -> p.attackers.any { it.id == item.cardId } } == true
        if (attackerCopy && staging) actions += stagingChips(play, combat, choices, attackChoices, bands, picking, card, choosing, computed)
        // CMB-037: the costs it must pay to attack or block as drafted
        if (copy && choices.declaring != null) shown.costs[card]?.forEach { cost ->
            actions += StagingChip(cost.label, null, on = false, picking = false, cost = true,
                hold = cost.text ?: "Paid as it is declared", description = "Pays ${cost.label}")
        }
        if (attackerCopy) {
            val keywords = computed.cards[card]?.keywords.orEmpty()
            COMBAT_KEYWORDS.filter { k -> keywords.any { it.equals(k, ignoreCase = true) } }
                .forEach { chips += Triple(it.lowercase(), AutomatumViolet, it.lowercase()) }
            toTargets[card]?.groupBy { it.target }?.forEach { (target, list) ->
                val text = "${list.sumOf { it.amount }} → ${label(target)!!.label.removePrefix("→ ")}"
                chips += Triple(text, PreviewRed, text)
            }
        }
        // CMB-036: a creature a staged attacker enlists
        if (real && card in enlistedBy) chips += Triple("enlisted", AutomatumViolet,
            "Enlisted by ${combatName(play, enlistedBy.getValue(card))}")
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
        if (chips.isEmpty() && actions.isEmpty() && dealt == null && !dying) continue
        // An attacker's badges sit on its side away from the arrows (below it in the attacker's view, above it mirrored),
        // stacked one per line no wider than the card, so neighbours' chips never run into each other (combat review #2)
        val below = attackerCopy && port != null && b.center.y > port.center.y
        Box(Modifier.offset { IntOffset(b.x.dp.roundToPx(), b.y.dp.roundToPx()) }.size(b.width.dp, b.height.dp)) {
            if (chips.isNotEmpty() || actions.isNotEmpty()) Box(Modifier.align(if (below) Alignment.BottomCenter else Alignment.TopCenter).fillMaxWidth().height(0.dp)) {
                Column(Modifier.fillMaxWidth().wrapContentHeight(if (below) Alignment.Top else Alignment.Bottom, unbounded = true)
                    .offset(y = if (below) (-3).dp else 3.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    // CMB-036–CMB-038: the staging's own chips nearest the card, then the badges
                    if (below) for (chip in actions) StagingChipView(chip, b.width)
                    for ((text, color, description) in (if (below) chips else chips.asReversed())) CombatChip(text, color, b.width, description)
                    if (!below) for (chip in actions.asReversed()) StagingChipView(chip, b.width)
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
    // Just right of the panel's top-right corner: the eye hiding and showing the preview's numbers (CMB-029)
    val panel = scene.combatPanel ?: return
    // Finalreview-combat #4 (CMB-029): the way to the damage needs a choice, which the preview took as suggested
    if (preview?.assumed == true) Box(Modifier.offset { IntOffset((panel.x + 2f).dp.roundToPx(), (panel.bottom + 3f).dp.roundToPx()) }) {
        PreviewChip("Depends on a choice", PreviewRed, Modifier, "The preview assumes the suggested answer to a choice on the way")
    }
    val eyeY = panel.y
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

/** The colour of the thin combat strip's pulse and of the creatures that may attack (CMB-001, owner 2026-11-05). */
internal val AttackOrange = Color(0xffe8590c)

/**
 * CMB-001 (owner, 2026-11-05): the thin strip between the battlefields while the viewer declares attackers with none
 * staged, at [bounds] on the screen: it pulses in the attack's red-orange; a tap ([enabled]) opens the band ([open]).
 */
@Composable
private fun CombatStrip(bounds: TableBounds, enabled: Boolean, open: () -> Unit) {
    val pulse by androidx.compose.animation.core.rememberInfiniteTransition(label = "Combat strip").animateFloat(.18f, .5f,
        androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(900),
            androidx.compose.animation.core.RepeatMode.Reverse), label = "Combat strip pulse")
    Box(Modifier.offset { IntOffset(bounds.x.dp.roundToPx(), bounds.y.dp.roundToPx()) }.size(bounds.width.dp, bounds.height.dp)
        .testTag("combat-strip").semantics { contentDescription = "Open the combat area" }
        .let { if (enabled) it.clickable(onClick = open) else it }
        .drawBehind {
            drawRect(AttackOrange.copy(alpha = pulse))
            drawLine(AttackOrange, Offset(0f, 0f), Offset(size.width, 0f), 1.5f * density)
            drawLine(AttackOrange, Offset(0f, size.height), Offset(size.width, size.height), 1.5f * density)
        })
}

/** A chip of a staged attacker or drafted blocker (CMB-036–CMB-038): [onTap] null when the viewer can't change it (the
 * other player sees the choices); [cost] an amber cost to pay (CMB-037). Holding it says [hold]. */
internal class StagingChip(val text: String, val icon: androidx.compose.ui.graphics.vector.ImageVector?, val on: Boolean, val picking: Boolean,
    val hold: String, val description: String, val cost: Boolean = false, val onTap: (() -> Unit)? = null)

/** The chips of the staged attacker [card] (CMB-036–CMB-038): its optional costs to attack as toggles ("Exert"),
 * "Enlist" with what it enlists, "Band" with banding; changeable only when [choosing]. */
private fun stagingChips(play: TableBoard, combat: CombatController?, choices: org.asyncttwiz.automatum.CombatChoices,
    given: Map<String, org.asyncttwiz.automatum.AttackChoice>, bands: Map<Int, List<String>>, picking: CombatController.Picking?,
    card: String, choosing: Boolean, computed: org.asyncttwiz.automatum.ComputedBoard): List<StagingChip> = buildList {
    val choice = given[card]
    fun tap(action: CombatController.() -> Unit): (() -> Unit)? = if (choosing && combat != null) ({ combat.action() }) else null
    for (cost in choices.optional[card].orEmpty()) {
        val on = choice?.paying?.contains(cost.key) == true
        if (!choosing && !on) continue
        add(StagingChip(cost.label, if (on) Icons.Outlined.CheckBox else Icons.Outlined.CheckBoxOutlineBlank,
            on, false, cost.text ?: "${cost.label} as it attacks", "${cost.label} as it attacks", onTap = tap { toggleOptional(card, cost.key) }))
    }
    val enlists = choices.enlist[card]
    if (enlists != null) {
        val enlisted = choice?.enlisted.orEmpty()
        val power = enlisted.sumOf { computed.cards[it]?.power ?: 0 }
        val on = enlisted.isNotEmpty()
        val pickingThis = picking == CombatController.Picking.Enlist(card)
        if (choosing || on) add(StagingChip(if (on) "+$power" else "Enlist", Icons.Outlined.PersonAddAlt, on, pickingThis,
            "Enlist: as it attacks, tap a creature of yours that isn't attacking and has no summoning sickness; its power is added until end of turn." +
                if (on) " Enlists ${enlisted.joinToString(" and ") { combatName(play, it) }}." else "",
            if (on) "Enlists ${enlisted.joinToString(" and ") { combatName(play, it) }}" else "Enlist",
            onTap = tap { if (on && !pickingThis) clearEnlisted(card) else pickEnlisted(card) }))
    }
    val band = choice?.band?.takeIf { it in bands }
    if (card in choices.banding || band != null) {
        val pickingThis = picking == CombatController.Picking.Band(card)
        if (choosing && card in choices.banding || band != null) add(StagingChip("Band", Icons.Outlined.Link,
            band != null, pickingThis,
            "Banding: tap Band, then the other attackers to band with it; a creature blocking one of a band blocks them all.",
            if (band != null) "In a band" else "Band", onTap = if (card in choices.banding) tap { pickBand(card) } else null))
    }
}

@Composable
private fun StagingChipView(chip: StagingChip, width: Float) {
    val color = if (chip.cost) AutotapAmber else AutomatumViolet
    val shape = RoundedCornerShape(6.dp)
    val room = (width - if (chip.icon != null) 18f else 6f).coerceAtLeast(8f)
    val natural = CombatStackPresentation.laneTextWidth(chip.text, 10f)
    val size = if (chip.cost) 13f else if (natural <= room) 10f else maxOf(6f, 10f * room / natural)
    HoldTip(chip.hold) {
        Row(Modifier.width(width.dp).heightIn(min = 22.dp).background(if (chip.on) color else Color.White, shape)
            .border(if (chip.picking) 2.5.dp else 1.5.dp, color, shape)
            .semantics {
                contentDescription = chip.description
                if (chip.onTap != null) stateDescription = listOfNotNull(if (chip.on) "on" else "off", "picking".takeIf { chip.picking }).joinToString()
            }
            .let { if (chip.onTap != null) it.clickable(onClick = chip.onTap) else it }
            .padding(horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            chip.icon?.let { Icon(it, null, Modifier.size(12.dp), tint = if (chip.on) Color.White else color); Spacer(Modifier.width(2.dp)) }
            if (chip.cost) CardText(chip.text, style = androidx.compose.ui.text.TextStyle(fontSize = size.sp, fontWeight = FontWeight.Bold,
                color = Color(0xff7a4a00)), maxLines = 1)
            else Text(chip.text, color = if (chip.on) Color.White else color, fontSize = size.sp, fontWeight = FontWeight.Bold,
                lineHeight = (size + 2f).sp, maxLines = 1, softWrap = false, overflow = androidx.compose.ui.text.style.TextOverflow.Clip)
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
