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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.onClick
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
    /** TBL-117, CMB-034 (owner playtest 2026-11-06): the item a finger is down on and how far its glow has grown (0–1). */
    pressed: () -> Pair<String, Float>? = { null }, bounds: (TableItem) -> TableBounds) {
    val area = play.board.combat
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
    val active = if (staging && combat != null) combat.activeTarget(play) else null
    val activeLane = if (staging && scene.combatPanel != null) scene.combatLabels.firstOrNull { it.cardId == active } else null
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
            // CMB-028: the active target's lane, framed in violet under its chip
            if (activeLane != null) drawRoundRect(AutomatumViolet.copy(alpha = .7f), Offset(activeLane.bounds.x + 2f, port.y + 1f),
                Size((activeLane.bounds.width - 4f) * d, (port.height - 2f) * d), CornerRadius(6 * d), style = Stroke(1.5f * d))
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
                    // TBL-117 (owner playtest 2026-11-06): under a finger the glow grows a little and brightens
                    val press = pressed()?.takeIf { it.first == item.id }?.second ?: 0f
                    frame(AutomatumViolet.copy(alpha = .25f + .25f * press), 6f + 3f * press, 3f + 1.5f * press)
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
            val press = pressed()?.takeIf { it.first == item.id }?.second ?: 0f
            val out = 3f + 1.5f * press
            drawRoundRect(AutomatumViolet.copy(alpha = .25f + .25f * press), Offset(b.x - out, b.y - out),
                Size((b.width + 2 * out) * d, (b.height + 2 * out) * d), CornerRadius(4 * d), style = Stroke((6f + 3f * press) * d))
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
            // CMB-035 (owner playtest 2026-11-07): a staged attacker carries no chip, its lane already names its target.
            // Only where it fits: small cards show their combat by their copies alone
            if (!(staging && attacker) && b.width >= 44f)
                (if (attacker) "attacking" else "blocking").let { chips += Triple(it, ArrowColor, it) }
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
                    // TBL-132 (owner gallery review 2026-11-06): holding a keyword badge explains the keyword
                    for ((text, color, description) in (if (below) chips else chips.asReversed()))
                        RuleTip(if (color == AutomatumViolet) text else "") { CombatChip(text, color, b.width, description) }
                    if (!below) for (chip in actions.asReversed()) StagingChipView(chip, b.width)
                }
            }
            // CMB-029 (owner playtest 2026-11-06): one badge over the card's middle, clear of its name (combat review #12), sized
            // to the card so it fits at any zoom
            if (dealt != null || dying) DamageBadge(dealt, dying, (b.width * .6f).coerceIn(12f, 26f),
                Modifier.align(Alignment.Center).wrapContentSize(unbounded = true))
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
    // CMB-001, CMB-013, CMB-028 (owner playtest 2026-11-06): the target chips fade in (once per declare attackers step);
    // while the viewer declares attackers a tap on one makes its target the active one; holding one shows its full name
    // and highlights the target's card or the player's life square
    // CMB-013, CMB-029 (owner gallery review 2026-11-06): each chip shows its target's value, and with the damage preview
    // what it would become ("20 → 17")
    val changes = scene.combatLabels.associate { lane ->
        lane.cardId to if (preview == null || lane.value.toIntOrNull() == null) 0 else if (lane.cardId == null)
            CombatStackRules.defendingPlayer(play.board)?.let { preview.life[it] } ?: 0 else -(damage[lane.cardId] ?: 0)
    }
    TargetChips(scene, play, viewer, active, chooser = if (staging && writable && combat != null && viewer == choices.declaring)
        ({ target -> combat.chooseTarget(play, target) }) else null, bounds, changes)
    if (area == null || port == null || scene.combatLabels.isEmpty()) return
    // Just right of the lanes' top-right corner: the eye hiding and showing the preview's numbers (CMB-029)
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

/** The colour of the creatures that may attack (CMB-028, owner 2026-11-05). */
internal val AttackOrange = Color(0xffe8590c)
/** CMB-007 (owner playtest 2026-11-06): the combat band's colour, the old combat area's cream. */
internal val CombatBandColor = Color(0xfff5f1df)
/** The ink of a target's value on its chip (CMB-013). */
private val CombatValueInk = Color(0xff4a4636)

/** What a target chip shows after the name (CMB-013, CMB-029): the target's value, or "20 → 17" when the damage preview
 * would change it by [change]; empty for a target without one. */
internal fun chipValue(lane: CombatLaneLabel, change: Int): String {
    val now = lane.value.toIntOrNull()
    return if (change == 0 || now == null) lane.value else "$now → ${signedValue(now + change)}"
}

private fun signedValue(n: Int) = if (n < 0) "−${-n}" else "$n"

/**
 * The target chips of the combat band ([TableScene.combatLabels], CMB-013), clipped to the table: each fades in, the
 * [active] target's filled violet; with [chooser] (the viewer declaring attackers) each glows softly as something to tap,
 * more while a finger is down on it (TBL-117), and a tap makes its target the active one (CMB-028). Holding one shows its
 * full name over it and highlights its target: the card on the battlefield, or the player's life square.
 */
@Composable
private fun TargetChips(scene: TableScene, play: TableBoard, viewer: String, active: String?, chooser: ((String?) -> Unit)?,
    bounds: (TableItem) -> TableBounds, changes: Map<String?, Int>) {
    val lanes = scene.combatLabels
    // The table, short of the Stack where it lies over the chips (portrait: at the table's right edge), which keeps its taps
    val table = scene.world?.visibleViewport?.let { t ->
        val stack = scene.stackRegion
        val row = lanes.firstOrNull()?.chip
        if (stack != null && row != null && stack.y < row.bottom && stack.bottom > row.y && stack.x > t.x && stack.x < t.right)
            t.copy(width = stack.x - t.x) else t
    } ?: return
    // CMB-001: the chips fade in when they first show in a step (staging's opening keeps them)
    val key = if (lanes.isEmpty()) null else combatBandKey(play.board)
    val appear = remember(key) { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(key) { if (key != null) appear.animateTo(1f, androidx.compose.animation.core.tween(CHIP_FADE_MILLIS)) }
    if (lanes.isEmpty()) return
    var held by remember(key) { mutableStateOf<CombatLaneLabel?>(null) }
    // HIS-113: a finger held on a chip is a finger held on the table
    val pause = LocalEngineRoutes.current?.viewPause
    // The target of the held chip, highlighted where it is on the table
    held?.let { lane ->
        val item = if (lane.cardId == null) scene.items.firstOrNull { it.id == "life/${lane.accountId}" }
            else scene.items.firstOrNull { it.cardId == lane.cardId && it.zone == PlayZone.BATTLEFIELD && !it.projection && it.viewport != "combat" }
        item?.takeIf { it.visible.isNotEmpty() }?.let(bounds)?.let { b ->
            Canvas(Modifier.fillMaxSize()) {
                val d = density
                for (ring in 3 downTo 1) drawRoundRect(AutomatumViolet.copy(alpha = .22f), Offset((b.x - 2f - ring * 1.5f) * d, (b.y - 2f - ring * 1.5f) * d),
                    Size((b.width + 4f + ring * 3f) * d, (b.height + 4f + ring * 3f) * d), CornerRadius((4f + ring) * d), style = Stroke(2.5f * d))
                drawRoundRect(AutomatumViolet, Offset((b.x - 2f) * d, (b.y - 2f) * d), Size((b.width + 4f) * d, (b.height + 4f) * d),
                    CornerRadius(4f * d), style = Stroke(2.5f * d))
            }
        }
    }
    Box(Modifier.offset { IntOffset(table.x.dp.roundToPx(), table.y.dp.roundToPx()) }.size(table.width.dp, table.height.dp)
        .clipToBounds().graphicsLayer { alpha = appear.value }) {
        for (lane in lanes) key(lane.cardId) {
            val change = changes[lane.cardId] ?: 0
            // The preview's "20 → 17" widens the chip about its centre, within the room its lane keeps for it (a rarer,
            // wider result squeezes the name instead), so nothing moves (CMB-013)
            val width = minOf(CombatStackPresentation.chipWidth(lane.label, chipValue(lane, change)) *
                (lane.chip.height / CombatStackPresentation.CHIP_HEIGHT), lane.bounds.width - 2 * lane.inset)
            val x = lane.chip.center.x - width / 2
            TargetChip(lane, change, width, viewer, lane.cardId == active && chooser != null, chooser?.let { choose -> { choose(lane.cardId) } },
                held = { heldNow -> held = if (heldNow) lane else held?.takeIf { h -> h.cardId != lane.cardId }; pause?.finger = heldNow },
                Modifier.offset { IntOffset((x - table.x).dp.roundToPx(), (lane.chip.y - table.y).dp.roundToPx()) })
        }
    }
    held?.let { lane ->
        // The full name, just above the chip
        val c = lane.chip
        // Centred over the chip, its bottom 6 dp above it
        val width = CombatStackPresentation.laneTextWidth(lane.label, 12f) + 16f
        Box(Modifier.offset { IntOffset((c.center.x - width / 2).dp.roundToPx(), (c.y - 29f).dp.roundToPx()) }
            .size(width.dp, 23.dp).semantics { contentDescription = "Target: ${lane.label}" }
            .background(Color(0xe6263238), RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
            Text(lane.label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, lineHeight = 15.sp,
                maxLines = 1, softWrap = false)
        }
    }
}

/** How long the target chips take to fade in (CMB-001). */
internal const val CHIP_FADE_MILLIS = 300

/** One target chip at its place in the band, [width] wide: crossed swords, the round avatar, the name cut to the chip's
 * letters, then the target's value, or what the damage preview would make of it ([change], CMB-029). */
@Composable
private fun TargetChip(lane: CombatLaneLabel, change: Int, width: Float, viewer: String, active: Boolean, onTap: (() -> Unit)?,
    held: (Boolean) -> Unit, modifier: Modifier) {
    val c = lane.chip
    val zoom = c.height / CombatStackPresentation.CHIP_HEIGHT
    var down by remember { mutableStateOf(false) }
    // TBL-117 (owner playtest 2026-11-06): under a finger the glow grows a little and brightens
    val press by androidx.compose.animation.core.animateFloatAsState(if (down) 1f else 0f,
        androidx.compose.animation.core.tween(PRESS_GLOW_MILLIS), label = "Chip press")
    val tint = if (lane.side == viewer) OwnTableColor else OpponentTableColor
    val shape = RoundedCornerShape(50)
    val fill = if (active) AutomatumViolet else Color.White
    val ink = if (active) Color.White else Color(0xff2b3437)
    val currentTap by androidx.compose.runtime.rememberUpdatedState(onTap)
    val currentHeld by androidx.compose.runtime.rememberUpdatedState(held)
    Row(modifier.size(width.dp, c.height.dp)
        .testTag("combat-target-chip")
        .semantics {
            contentDescription = if (onTap != null) "Attack ${lane.label}" else lane.label
            if (active) stateDescription = "active"
            if (onTap != null) onClick { onTap(); true }
        }
        .drawBehind {
            if (onTap == null) return@drawBehind
            val r = size.height / 2
            for (ring in 4 downTo 1) {
                val spread = ring * (1f + .6f * press) * zoom * density
                drawRoundRect(AutomatumViolet.copy(alpha = .12f + .1f * press), Offset(-spread, -spread),
                    Size(size.width + 2 * spread, size.height + 2 * spread), CornerRadius(r + spread))
            }
        }
        .background(fill, shape).border((if (active) 1.5f else 1.2f).dp * zoom, if (active) AutomatumViolet else tint.copy(alpha = .75f), shape)
        .pointerInput(lane.cardId) {
            detectTapGestures(
                onPress = { down = true; tryAwaitRelease(); down = false; currentHeld(false) },
                onLongPress = { currentHeld(true) },
                onTap = { currentTap?.invoke() })
        },
        verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.width((CombatStackPresentation.CHIP_SIDE * zoom).dp))
        Icon(androidx.compose.ui.res.painterResource(R.drawable.swords_outlined), null,
            Modifier.size((CombatStackPresentation.CHIP_ICON * zoom).dp), tint = if (active) Color.White else tint)
        Spacer(Modifier.width((CombatStackPresentation.CHIP_GAP * zoom).dp))
        val avatar = (CombatStackPresentation.CHIP_AVATAR * zoom).dp
        Box(Modifier.size(avatar).clip(CircleShape).background(if (lane.accountId != null) avatarColor(lane.accountId) else Color(0xff9e9a8c))
            .border(1.dp * zoom, Color.White.copy(alpha = .8f), CircleShape), contentAlignment = Alignment.Center) {
            val initial = if (lane.hidden) "?" else avatarInitial(lane.label)
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides Color.White) {
                if (lane.artwork != null) CardArtwork(lane.artwork, initial, 64, 64, Modifier.fillMaxSize(), crop = true)
                else Text(initial, color = Color.White, fontSize = (9f * zoom).sp, fontWeight = FontWeight.Bold, lineHeight = (10f * zoom).sp)
            }
        }
        Spacer(Modifier.width((CombatStackPresentation.CHIP_GAP * zoom).dp))
        Text(lane.chipText, Modifier.weight(1f), color = ink, fontSize = lane.fontSize.sp, fontWeight = FontWeight.Bold,
            lineHeight = (lane.fontSize * 1.15f).sp, maxLines = 1, softWrap = false,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        // CMB-013, CMB-029 (owner gallery review 2026-11-06): the target's life, loyalty or defense on the chip, and what
        // the damage preview would make of it in red
        if (lane.value.isNotEmpty()) {
            val now = lane.value.toIntOrNull()
            val text = chipValue(lane, change)
            Spacer(Modifier.width((CombatStackPresentation.CHIP_VALUE_GAP * zoom).dp))
            Text(text, Modifier.semantics { contentDescription = if (change == 0 || now == null) "${lane.label}: ${lane.value}" else
                    "${lane.label} would go from $now to ${now + change}" },
                color = when { change != 0 -> if (active) Color.White else PreviewRed; active -> Color.White; else -> CombatValueInk },
                fontSize = lane.fontSize.sp, fontWeight = FontWeight.Bold, lineHeight = (lane.fontSize * 1.15f).sp, letterSpacing = 0.sp,
                maxLines = 1, softWrap = false)
        }
        Spacer(Modifier.width((CombatStackPresentation.CHIP_SIDE * zoom).dp))
    }
}

/** How fast a pressed glow grows and shrinks back (TBL-117, owner playtest 2026-11-06). */
internal const val PRESS_GLOW_MILLIS = 90

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
    // TBL-132 (owner gallery review 2026-11-06): holding a chip naming a keyword ("Exert") explains it too, unless what
    // holding it says explains it already ("Enlist: …")
    RuleTip(if (chip.hold.startsWith("${chip.text}:")) "" else chip.text, lead = chip.hold) {
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

/**
 * CMB-029 (owner playtest 2026-11-06): what combat damage would do to a creature, as one badge [size] dp square: a red
 * splash with the damage ("3", no minus) when it survives, a black skull with the damage inside when it dies (without a
 * number when it dies without damage). The badge keeps its size whatever the number; a longer number is drawn smaller
 * to fit, its outline centred where the eye expects it: the splash's middle, or the skull's cranium above the jaw (owner
 * gallery review 2026-11-07).
 */
@Composable
internal fun DamageBadge(dealt: Int?, dying: Boolean, size: Float, modifier: Modifier = Modifier) {
    val description = when {
        dying && dealt != null -> "Would take $dealt damage and die"
        dying -> "Would die"
        else -> "Would take $dealt damage"
    }
    val text = dealt?.toString().orEmpty()
    Canvas(modifier.size(size.dp).semantics { contentDescription = description }) {
        if (dying) skull() else splash()
        if (text.isNotEmpty()) {
            val centre = if (dying) SkullNumberCentre else SplashNumberCentre
            drawCentredText(text, Offset(this.size.width * centre.x, this.size.height * centre.y),
                this.size.height * (if (dying) .4f else .44f), Color.White, 900,
                maxWidth = this.size.width * (if (dying) SkullNumberWidth else SplashNumberWidth))
        }
    }
}

/** The widest a damage badge's number is drawn, as a share of the badge: inside the splash's core and the cranium, so
 * one or two digits keep their full size and three are drawn smaller. */
internal const val SplashNumberWidth = .54f
internal const val SkullNumberWidth = .6f
/** Where a splash's number is centred, as shares of the badge: the middle of the splash's area (its spikes are uneven). */
internal val SplashNumberCentre = Offset(.499f, .498f)
/** Where a skull's number is centred, as shares of the badge: the middle of the round cranium above the jaw. */
internal val SkullNumberCentre = Offset(.5f, .37f)

/** A red splash filling the canvas: a ragged star with a thin white edge. */
private fun DrawScope.splash() {
    val points = 11
    val c = center
    val rx = size.width / 2f
    val ry = size.height / 2f
    val path = Path()
    for (i in 0 until points * 2) {
        val angle = Math.PI * i / points - Math.PI / 2
        // Uneven spikes read as a splash, not a star
        val r = if (i % 2 == 0) (if (i % 4 == 0) .98f else .86f) else .66f
        val x = c.x + (rx * r * kotlin.math.cos(angle)).toFloat()
        val y = c.y + (ry * r * kotlin.math.sin(angle)).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, PreviewRed)
    drawPath(path, Color.White, style = Stroke(size.minDimension * .05f))
}

/** A black skull filling the canvas: a round cranium over a narrower jaw with teeth gaps, a thin white edge. */
private fun DrawScope.skull() {
    val w = size.width
    val h = size.height
    val ink = Color(0xff1d2326)
    val edge = Stroke(size.minDimension * .05f)
    val cranium = Path().apply {
        addOval(androidx.compose.ui.geometry.Rect(w * .04f, h * .02f, w * .96f, h * .78f))
    }
    val jaw = Path().apply {
        addRoundRect(androidx.compose.ui.geometry.RoundRect(w * .24f, h * .6f, w * .76f, h * .98f, CornerRadius(w * .08f)))
    }
    drawPath(cranium, Color.White, style = edge)
    drawPath(jaw, Color.White, style = edge)
    drawPath(cranium, ink)
    drawPath(jaw, ink)
    // Teeth gaps
    for (x in listOf(.42f, .58f)) drawLine(Color.White.copy(alpha = .7f), Offset(w * x, h * .82f), Offset(w * x, h * .97f), w * .035f)
}

