package org.asyncttwiz.android

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.CallSplit
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.decision.Share
import org.asyncttwiz.automatum.DamageDivision
import org.asyncttwiz.automatum.Divisions
import org.asyncttwiz.automatum.SharedFlow
import org.asyncttwiz.protocol.*

/**
 * A division of combat damage being made (CMB-033): "In order" (the creatures' [order], each given lethal damage in turn)
 * or "Free" (any [free] shares), starting from [start] (Automatum's suggestion or an early answer given). A start no order
 * gives opens in Free.
 */
internal class DivisionDraft(val division: DamageDivision, start: Map<MtgRef, Int>,
    /** Opens in Free whatever the start: a division whose order can't matter, asked only because its player wants to be
     * asked (combat review B). */
    free: Boolean = false) {
    private val creatures = Divisions.creatures(division)
    var inOrder by mutableStateOf(!free && Divisions.inOrder(division, creatures) == division.among.associateWith { start[it] ?: 0 })
        private set
    var order by mutableStateOf(creatures); private set
    var free by mutableStateOf(division.among.associateWith { start[it] ?: 0 }); private set

    /** The damage each recipient gets now. */
    val shares: Map<MtgRef, Int> get() = if (inOrder) Divisions.inOrder(division, order) else free
    /** Why OK can't be pressed yet (CR 510.1c, 702.19b), or null. */
    val refusal: String? get() = Divisions.refusal(division, shares)
    /** The rows in their shown order: the creatures, then the player or planeswalker with trample. */
    val rows: List<MtgRef> get() = order + listOfNotNull(division.trampleTo)

    fun mode(inOrder: Boolean) {
        if (inOrder == this.inOrder) return
        if (!inOrder) free = shares
        this.inOrder = inOrder
    }
    fun move(from: Int, to: Int) {
        if (from !in order.indices || to !in order.indices || from == to) return
        order = order.toMutableList().apply { add(to, removeAt(from)) }
    }
    fun set(target: MtgRef, amount: Int) { free = free + (target to amount.coerceIn(0, division.amount)) }
}

/** The rows of a division (CMB-033): the switch, one row per recipient and the hint line. */
@Composable
internal fun DivisionRows(draft: DivisionDraft, play: TableBoard, names: Map<String, String>) {
    val division = draft.division
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.background(Color(0xffeceff1), RoundedCornerShape(50)).padding(3.dp)) {
            for ((name, mode) in listOf("In order" to true, "Free" to false)) {
                val on = draft.inOrder == mode
                Box(Modifier.background(if (on) AutomatumViolet else Color.Transparent, RoundedCornerShape(50))
                    .semantics { contentDescription = "$name${if (on) ", chosen" else ""}" }
                    .pointerInput(mode) { detectTapGestures { draft.mode(mode) } }
                    .padding(horizontal = 18.dp, vertical = 6.dp)) {
                    Text(name, color = if (on) Color.White else Color(0xff263238), fontSize = 14.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
        val step = with(LocalDensity.current) { 66.dp.toPx() }
        val inspector = remember(division.source) { ChoiceInspector() }
        ChoiceInspection(inspector, play, LocalChoiceFrame.current.viewer)
        val shares = draft.shares
        for ((index, ref) in draft.rows.withIndex()) {
            val creature = ref != division.trampleTo
            val amount = shares[ref] ?: 0
            val lethal = division.lethal[ref]
            val name = refName(play, names, ref) + if (!creature) " (trample)" else ""
            val actions = if (!draft.inOrder || !creature) emptyList() else listOfNotNull(
                CustomAccessibilityAction("Move up") { draft.move(index, index - 1); true }.takeIf { index > 0 },
                CustomAccessibilityAction("Move down") { draft.move(index, index + 1); true }.takeIf { index < draft.order.lastIndex })
            Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).border(1.dp, Color(0xffcfd8dc), RoundedCornerShape(8.dp))
                .background(Color(0xfff7f9f9), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp)
                .semantics { contentDescription = "$name: $amount"; customActions = actions },
                verticalAlignment = Alignment.CenterVertically) {
                if (draft.inOrder && creature) Text("≡", Modifier.padding(end = 8.dp).pointerInput(ref) {
                    var moved = 0f
                    detectVerticalDragGestures(onDragStart = { moved = 0f }) { change, dy ->
                        change.consume()
                        moved += dy
                        val at = draft.order.indexOf(ref)
                        if (moved > step / 2 && at < draft.order.lastIndex) { draft.move(at, at + 1); moved -= step }
                        else if (moved < -step / 2 && at > 0) { draft.move(at, at - 1); moved += step }
                    }
                }.semantics { contentDescription = "Drag to reorder" }, color = Color(0xff78909c), fontSize = 20.sp)
                // I-D48: a creature or planeswalker shows as its card (a face-down one covered); holding it inspects it
                (ref as? MtgRef.Card)?.let { card ->
                    val played = play.board.cards.singleOrNull { it.id == card.cardId }
                    val face = choiceCard(play, LocalChoiceFrame.current.viewer, card.cardId, card.cardId, played?.isFaceDown == false, name)
                    ChoiceCardFace(face, Modifier.padding(end = 8.dp, top = 2.dp, bottom = 2.dp)
                        .choiceCardGestures(card.cardId, tap = {}, hold = { inspector.hold(face, it) }, moved = inspector::moved), small = true)
                }
                Text(if (draft.inOrder && creature) "${index + 1}. $name" else name, Modifier.weight(1f), fontSize = 14.sp, maxLines = 2)
                if (draft.inOrder) Text("$amount", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                else Stepper(amount, 0, division.amount) { draft.set(ref, it) }
                Box(Modifier.width(54.dp), contentAlignment = Alignment.CenterEnd) {
                    if (lethal != null && lethal > 0 && amount >= lethal) Text("lethal ☠", color = PreviewRed, fontSize = 11.sp)
                }
            }
        }
        Text(draft.refusal ?: if (draft.inOrder) "Drag to change the order: each gets lethal damage in turn, the rest goes on."
            else "Any division of all ${division.amount} damage.", fontSize = 11.sp, lineHeight = 14.sp,
            color = if (draft.refusal != null) PreviewRed else Color(0xff4b5a5f))
    }
}

/** The engine's shares of [shares] for the open [decision] (its targets in the same order as the division's). */
internal fun engineShares(decision: Decision.Divide, division: DamageDivision, shares: Map<MtgRef, Int>): List<Share> =
    division.among.zip(decision.among).map { (ref, target) -> Share(target, shares[ref] ?: 0) }

/**
 * The division of combat damage (CMB-033) open for the player who divides (or chooses for them), attached to the combat
 * band ([BandAttached], owner 2026-11-05): filled in with Automatum's suggestion, so the main button's OK alone accepts it
 * ([SharedFlowController.divisionAction]); the eye hides it to look at the table.
 */
@Composable
internal fun CombatDivisionPanel(flows: SharedFlowController, play: TableBoard, names: Map<String, String>,
    decision: Decision.Divide, division: DamageDivision, count: Pair<Int, Int>?) {
    // The decision's suggestion: Automatum's, or an early answer given just as it opened (I-D33)
    val start = division.among.zip(decision.among).associate { (ref, target) ->
        ref to (decision.suggested.firstOrNull { it.target == target }?.amount ?: 0) }
    val draft = remember(decision.id) { DivisionDraft(division, start, free = Divisions.orderFree(division)) }
    DisposableEffect(decision.id, draft) {
        flows.division = decision to draft
        onDispose { if (flows.division?.second === draft) flows.division = null }
    }
    AutomatumDockPanel("Division of combat damage", flows.hidden, flows::toggleHidden, "division") {
        // STK-044: the title says only the choice; the creature dealing the damage is the card at the right (its own: only
        // the creature's controller divides, STK-033)
        val viewer = LocalChoiceFrame.current.viewer
        val inspector = remember(decision.id) { ChoiceInspector() }
        val source = choiceCard(play, viewer, "source/${division.source}", division.source, shown = true, fallback = "Attacker")
        ChoiceHead("Divide ${division.amount} damage", source, inspector, trailing = {
            if (count != null) Text("${count.first} of ${count.second}", color = Color(0xff78909c), fontSize = 13.sp)
        }) {
            Text("${division.amount} damage to divide among what it fights" + if (division.trampleTo != null) " · trample" else "",
                fontSize = 12.sp, color = Color(0xff4b5a5f))
        }
        DivisionRows(draft, play, names)
        ChoiceInspection(inspector, play, viewer)
    }
}

/**
 * CMB-033 (owner, 2026-11-05): [content] attached to the combat band of [scene], centred on its panel, at most
 * [BAND_PANEL_WIDTH] wide: under the band when there is room for it there (it scrolls inside when longer), else above it,
 * else over the lower part of the table. Never docked above the hand.
 */
@Composable
internal fun BandAttached(scene: TableScene?, content: @Composable () -> Unit) {
    androidx.compose.ui.layout.Layout(content = content, modifier = Modifier.fillMaxSize()) { measurables, constraints ->
        val table = scene?.world?.visibleViewport
        val band = scene?.combatPanel ?: table?.let { TableBounds(it.x, it.center.y, it.width, 0f) }
        val width = minOf(BAND_PANEL_WIDTH.roundToPx(), constraints.maxWidth)
        val top = (table?.y ?: 0f).dp.roundToPx().coerceIn(0, constraints.maxHeight)
        val bottom = (table?.bottom?.dp?.roundToPx() ?: constraints.maxHeight).coerceIn(top, constraints.maxHeight)
        val gap = 4.dp.roundToPx()
        val below = ((band?.bottom ?: 0f).dp.roundToPx() + gap).coerceIn(top, bottom)
        val above = ((band?.y ?: 0f).dp.roundToPx() - gap).coerceIn(top, bottom)
        val least = BAND_PANEL_LEAST.roundToPx()
        val (room, under) = when {
            bottom - below >= least -> (bottom - below) to true
            above - top >= least -> (above - top) to false
            else -> ((bottom - top) * 3 / 5) to true
        }
        val placeable = measurables.firstOrNull()?.measure(androidx.compose.ui.unit.Constraints(minWidth = width, maxWidth = width,
            maxHeight = room.coerceAtLeast(1)))
        layout(constraints.maxWidth, constraints.maxHeight) {
            if (placeable == null) return@layout
            val centre = band?.center?.x?.dp?.roundToPx() ?: (constraints.maxWidth / 2)
            val x = (centre - placeable.width / 2).coerceIn(0, (constraints.maxWidth - placeable.width).coerceAtLeast(0))
            val y = when {
                !under -> above - placeable.height
                bottom - below >= least -> below
                else -> bottom - placeable.height
            }
            placeable.place(x, y)
        }
    }
}

/** The least room under or above the combat band a panel attached to it takes there ([BandAttached]). */
internal val BAND_PANEL_LEAST = 160.dp

/** The widest a panel attached to the combat band is ([BandAttached]). */
internal val BAND_PANEL_WIDTH = 400.dp

/**
 * The viewer's division still to come in this damage step (I-D29, I-D33, STK-034): while another division is open, they
 * may give theirs early in the same rows as the dialog (CMB-033), applied when it opens. Ready gives it, Withdraw takes it
 * back.
 */
@Composable
internal fun EarlyDivisionPanel(combat: CombatController, play: TableBoard, division: DamageDivision, names: Map<String, String>,
    enabled: Boolean) {
    val given = combat.early(play, division)
    val start = given?.shares?.associate { it.target to it.amount } ?: division.suggested
    val draft = remember(division.source, given) { DivisionDraft(division, start, free = Divisions.orderFree(division)) }
    // Combat review #9: Inspect folds the panel away to look at the combat area, as the division dialog's does
    var hidden by remember(division.source, division.firstStrike) { mutableStateOf(false) }
    if (hidden) {
        AutomatumDockBar("Early division hidden") {
            Text("Your division comes later", Modifier.weight(1f), fontSize = 13.sp)
            BarButton("Divide early", Icons.Outlined.CallSplit) { hidden = false }
        }
        return
    }
    Surface(Modifier.fillMaxWidth().semantics { contentDescription = "Early division" }, shape = RoundedCornerShape(0.dp),
        color = Color.White, shadowElevation = 6.dp, border = BorderStroke(2.dp, AutomatumViolet)) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("${combatName(play, division.source)} — divide ${division.amount} damage", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(if (given != null) "Ready: applied when your division comes up." else "You may divide now; it is applied when your division comes up.",
                fontSize = 11.sp, color = Color(0xff4b5a5f))
            DivisionRows(draft, play, names)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                BarButton("Inspect", Icons.Outlined.ZoomIn, Modifier.semantics { contentDescription = "Inspect the table" }) { hidden = true }
                Spacer(Modifier.weight(1f))
                if (given != null) BarButton("Withdraw", Icons.AutoMirrored.Outlined.Undo, enabled = enabled) { combat.withdrawEarly(division) }
                ChoiceButton("Ready", Icons.Outlined.Check, enabled = enabled && draft.refusal == null &&
                    draft.shares != given?.shares?.associate { it.target to it.amount }, primary = true) { combat.giveEarly(division, draft.shares) }
            }
        }
    }
}

/** Whether the shared [flow]'s open decision is a division of combat damage shown in the dialog (not one Automatum's
 * table makes by itself, CMB-033). */
internal fun dividingInDialog(flow: SharedFlow): Boolean = flow.decision is Decision.Divide &&
    flow.division?.let { askedInDialog(flow.board, it) } == true

/**
 * Whether [division] on [board] is asked in the dialog (CMB-033): when its order can change something, and also
 * when it can't but its player has "Skip when I have no choice" off (combat review B: then it opens in Free, where the
 * player still has choices). Else the table makes it with the suggestion by itself.
 */
internal fun askedInDialog(board: BoardState, division: DamageDivision): Boolean = !Divisions.orderFree(division) ||
    board.players.firstOrNull { it.accountId == division.player }?.skipWhenNoChoice == false
