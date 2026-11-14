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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.stateDescription
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
import org.asyncttwiz.protocol.*

/**
 * A division of combat damage being made (CMB-033): "In order" (the creatures' [order], each given lethal damage in turn)
 * or "Free" (any [free] shares), starting from [start] (Automatum's suggestion or an early answer given). A start no order
 * gives opens in Free.
 */
class DivisionDraft(val division: DamageDivision, start: Map<MtgRef, Int>,
    /** Opens in Free whatever the start: a division whose order can't matter, asked only because its player wants to be
     * asked (combat review B). */
    free: Boolean = false) {
    private val creatures = Divisions.creatures(division)
    var inOrder by mutableStateOf(!free && Divisions.inOrder(division, creatures) == division.among.associateWith { start[it] ?: 0 })
        private set
    var order by mutableStateOf(creatures); private set
    var free by mutableStateOf(division.among.associateWith { start[it] ?: 0 }); private set

    /** The damage each recipient gets now; with trample, in Free too, the attacked player, planeswalker or battle gets
     * what the creatures don't (CMB-033: it is shown, not chosen). */
    val shares: Map<MtgRef, Int> get() = if (inOrder) Divisions.inOrder(division, order) else free.let { given ->
        val trample = division.trampleTo ?: return@let given
        given + (trample to (division.amount - given.filterKeys { it != trample }.values.sum()).coerceAtLeast(0))
    }
    /** Why OK can't be pressed yet (CR 510.1c, 702.19b), or null. */
    val refusal: String? get() = Divisions.refusal(division, shares)
    /** The rows in their shown order: the creatures (what tramples over shows on the panel's bottom line, CMB-033). */
    val rows: List<MtgRef> get() = order

    fun mode(inOrder: Boolean) {
        if (inOrder == this.inOrder) return
        if (!inOrder) free = shares
        this.inOrder = inOrder
    }
    fun move(from: Int, to: Int) {
        if (from !in order.indices || to !in order.indices || from == to) return
        order = order.toMutableList().apply { add(to, removeAt(from)) }
    }
    fun set(target: MtgRef, amount: Int) {
        // With trample the creatures share at most all of it; the rest tramples over
        val others = free.filterKeys { it != target && it != division.trampleTo }.values.sum()
        val most = if (division.trampleTo != null) division.amount - others else division.amount
        free = free + (target to amount.coerceIn(0, most.coerceAtLeast(0)))
    }
}

/**
 * The rows of a division (CMB-033, owner playtest 2026-11-06): the In order / Free switch with the hint beside it, then a
 * row per creature (its card and name, its damage as a chip: red with ☠ when it is lethal, grey when not). What tramples
 * over shows as a chip on the panel's bottom line ([TrampleChip]).
 */
@Composable
fun DivisionRows(draft: DivisionDraft, play: TableBoard, names: Map<String, String>) {
    val division = draft.division
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.background(Color(0xffeceff1), RoundedCornerShape(50)).padding(2.dp)) {
                for ((name, mode) in listOf("In order" to true, "Free" to false)) {
                    val on = draft.inOrder == mode
                    Box(Modifier.background(if (on) AutomatumViolet else Color.Transparent, RoundedCornerShape(50))
                        .semantics { contentDescription = "$name${if (on) ", chosen" else ""}" }
                        .pointerInput(mode) { detectTapGestures { draft.mode(mode) } }
                        .padding(horizontal = 12.dp, vertical = 5.dp)) {
                        Text(name, color = if (on) Color.White else Color(0xff263238), fontSize = 13.sp,
                            fontWeight = if (on) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
            Text(draft.refusal ?: if (draft.inOrder) "Drag to reorder: each gets lethal damage in turn, the rest goes on."
                else "Any division of all ${division.amount} damage.", Modifier.weight(1f), fontSize = 11.sp, lineHeight = 13.sp,
                maxLines = 2, color = if (draft.refusal != null) PreviewRed else Color(0xff4b5a5f))
        }
        val step = with(LocalDensity.current) { 46.dp.toPx() }
        val inspector = remember(division.source) { ChoiceInspector() }
        val viewer = LocalChoiceFrame.current.viewer
        ChoiceInspection(inspector, play, viewer)
        val shares = draft.shares
        for ((index, ref) in draft.rows.withIndex()) {
            val creature = ref != division.trampleTo
            val amount = shares[ref] ?: 0
            val lethal = lethal(play, division, ref, amount)
            val name = refName(play, names, ref)
            val actions = if (!draft.inOrder || !creature) emptyList() else listOfNotNull(
                CustomAccessibilityAction("Move up") { draft.move(index, index - 1); true }.takeIf { index > 0 },
                CustomAccessibilityAction("Move down") { draft.move(index, index + 1); true }.takeIf { index < draft.order.lastIndex })
            Row(Modifier.fillMaxWidth().heightIn(min = 44.dp)
                .border(1.dp, if (creature) Color(0xffcfd8dc) else AutomatumViolet.copy(alpha = .35f), RoundedCornerShape(8.dp))
                .background(if (creature) Color(0xfff7f9f9) else Color(0xfff6f3fb), RoundedCornerShape(8.dp)).padding(horizontal = 6.dp)
                .semantics { contentDescription = "$name: $amount"; if (lethal) stateDescription = "lethal"; customActions = actions },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // In order, a creature's place in the order and the handle that moves it; the trample row never moves
                if (draft.inOrder && creature) Text("≡", Modifier.pointerInput(ref) {
                    var moved = 0f
                    detectVerticalDragGestures(onDragStart = { moved = 0f }) { change, dy ->
                        change.consume()
                        moved += dy
                        val at = draft.order.indexOf(ref)
                        if (moved > step / 2 && at < draft.order.lastIndex) { draft.move(at, at + 1); moved -= step }
                        else if (moved < -step / 2 && at > 0) { draft.move(at, at - 1); moved += step }
                    }
                }.semantics { contentDescription = "Drag to reorder" }, color = Color(0xff78909c), fontSize = 20.sp)
                // I-D48: a creature or planeswalker shows as its card (a face-down one covered); holding it inspects it;
                // a player as their round avatar
                when (ref) {
                    is MtgRef.Card -> {
                        val played = play.board.cards.singleOrNull { it.id == ref.cardId }
                        val face = choiceCard(play, viewer, ref.cardId, ref.cardId, played?.isFaceDown == false, name)
                        ChoiceCardFace(face, Modifier.padding(vertical = 2.dp)
                            .choiceCardGestures(ref.cardId, tap = {}, hold = { inspector.hold(face, it) }, moved = inspector::moved), small = true)
                    }
                    else -> {}
                }
                Text(if (draft.inOrder && creature) "${index + 1}. $name" else name, Modifier.weight(1f), fontSize = 13.sp,
                    lineHeight = 15.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    color = Color(0xff263238))
                if (draft.inOrder) DamageChip(amount, lethal)
                else Row(verticalAlignment = Alignment.CenterVertically) {
                    TipIconButton(Icons.Outlined.Remove, "One less", Modifier.size(32.dp), enabled = amount > 0) { draft.set(ref, amount - 1) }
                    DamageChip(amount, lethal)
                    TipIconButton(Icons.Outlined.Add, "One more", Modifier.size(32.dp), enabled = amount < division.amount) {
                        draft.set(ref, amount + 1) }
                }
            }
        }
    }
}


/**
 * Whether [amount] of [division]'s damage to [ref] is lethal (CMB-033): for a creature at least its lethal damage
 * (deathtouch makes 1 lethal, CR 702.2c); for the player at least their life; for a planeswalker or battle at least its
 * loyalty or defense.
 */
fun lethal(play: TableBoard, division: DamageDivision, ref: MtgRef, amount: Int): Boolean {
    if (amount <= 0) return false
    division.lethal[ref]?.let { return it in 1..amount }
    return when (ref) {
        is MtgRef.Player -> play.board.players.firstOrNull { it.accountId == ref.accountId }?.life?.toIntOrNull()?.let { amount >= it } == true
        is MtgRef.Card -> play.board.cards.firstOrNull { it.id == ref.cardId }?.typedCounters
            ?.firstOrNull { it.kind == "loyalty" || it.kind == "defense" }?.count?.let { amount >= it } == true
        else -> false
    }
}

/**
 * CMB-033 (owner playtest 2026-11-06): with trample, what tramples over as a compact chip on the panel's bottom line
 * ("Tramples 2 → Bob" with the defender's round avatar, or the planeswalker's or battle's tiny art), red with ☠ when it is
 * lethal to them, grey when not. Nothing to choose: it is what the creatures don't get.
 */
@Composable
fun TrampleChip(draft: DivisionDraft, play: TableBoard, names: Map<String, String>) {
    val division = draft.division
    val target = division.trampleTo ?: return
    val amount = draft.shares[target] ?: 0
    val lethal = lethal(play, division, target, amount)
    val name = refName(play, names, target)
    val ink = if (lethal) Color.White else Color(0xff37474f)
    Row(Modifier.heightIn(min = 30.dp).background(if (lethal) PreviewRed else Color(0xffe3e7e9), RoundedCornerShape(50))
        .padding(start = 10.dp, end = 4.dp).semantics(mergeDescendants = true) {
            contentDescription = "Tramples $amount to $name"; if (lethal) stateDescription = "lethal" },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        if (lethal) Text("☠", color = ink, fontSize = 12.sp, lineHeight = 14.sp)
        Text("Tramples", color = ink, fontSize = 12.sp, lineHeight = 14.sp)
        Text("$amount", color = ink, fontSize = 15.sp, lineHeight = 17.sp, fontWeight = FontWeight.Bold)
        Text("→", color = ink, fontSize = 13.sp, lineHeight = 15.sp)
        when (target) {
            is MtgRef.Player -> Box(Modifier.size(22.dp).clip(CircleShape).background(avatarColor(target.accountId))
                .border(1.dp, Color.White, CircleShape), contentAlignment = Alignment.Center) {
                Text(avatarInitial(name), color = Color.White, fontSize = 11.sp, lineHeight = 12.sp, fontWeight = FontWeight.Bold)
            }
            is MtgRef.Card -> {
                val viewer = LocalChoiceFrame.current.viewer
                val card = play.board.cards.singleOrNull { it.id == target.cardId }
                val face = choiceCard(play, viewer, "trample/${target.cardId}", target.cardId, card?.isFaceDown == false, name)
                Box(Modifier.size(22.dp).clip(CircleShape).background(Color(0xff9e9a8c)).border(1.dp, Color.White, CircleShape)) {
                    CardArtwork(face.art, "", 64, 64, Modifier.fillMaxSize(), crop = true)
                }
            }
            else -> {}
        }
        Text(name, color = ink, fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 110.dp).padding(end = 6.dp))
    }
}

/** A recipient's damage as a chip (CMB-033): red with ☠ when it is lethal, grey when not. */
@Composable
private fun DamageChip(amount: Int, lethal: Boolean) {
    Row(Modifier.heightIn(min = 26.dp).widthIn(min = 42.dp)
        .background(if (lethal) PreviewRed else Color(0xffe3e7e9), RoundedCornerShape(50)).padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        if (lethal) Text("☠", color = Color.White, fontSize = 12.sp, lineHeight = 14.sp, modifier = Modifier.padding(end = 3.dp))
        Text("$amount", color = if (lethal) Color.White else Color(0xff37474f), fontSize = 15.sp, lineHeight = 17.sp,
            fontWeight = FontWeight.Bold)
    }
}

/** The keyword abilities that change what combat damage does (CMB-033), in this order. */
private val DAMAGE_KEYWORDS = listOf("First strike", "Double strike", "Trample", "Deathtouch", "Lifelink", "Infect", "Wither")

/**
 * The head of a division (CMB-033, owner playtest 2026-11-06): the creature dealing the damage at the top left as its
 * card with its [power], the title "Divide N damage" (and "1 of 2" when the step has more), whose damage it is, and chips
 * for its [keywords] that change what the damage does.
 */
@Composable
fun DivisionHead(division: DamageDivision, play: TableBoard, names: Map<String, String>, count: Pair<Int, Int>?,
    power: Int?, keywords: List<String>, inspector: ChoiceInspector) {
    val viewer = LocalChoiceFrame.current.viewer
    val source = choiceCard(play, viewer, "source/${division.source}", division.source, shown = true, fallback = "Attacker")
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box {
            ChoiceSource(source, inspector, Modifier.padding(end = 6.dp, bottom = 6.dp))
            if (power != null) Box(Modifier.align(Alignment.BottomEnd).size(20.dp).background(Color(0xff37474f), CircleShape)
                .border(1.5.dp, Color.White, CircleShape).semantics { contentDescription = "Power $power" },
                contentAlignment = Alignment.Center) {
                Text("$power", color = Color.White, fontSize = 11.sp, lineHeight = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Divide ${division.amount} damage", Modifier.weight(1f).semantics { heading() }, fontWeight = FontWeight.Bold,
                    fontSize = 15.sp)
                if (count != null) Text("${count.first} of ${count.second}", color = Color(0xff78909c), fontSize = 13.sp)
            }
            Text("Among what it fights" + if (division.firstStrike) " · first-strike damage step" else "",
                fontSize = 12.sp, lineHeight = 14.sp, color = Color(0xff4b5a5f),
                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            val shown = DAMAGE_KEYWORDS.filter { k -> keywords.any { it.equals(k, ignoreCase = true) } }
            if (shown.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (keyword in shown) Text(keyword.lowercase(), Modifier.border(1.2.dp, AutomatumViolet, RoundedCornerShape(50))
                    .padding(horizontal = 7.dp, vertical = 1.dp), color = AutomatumViolet, fontSize = 11.sp, lineHeight = 14.sp,
                    fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** The engine's shares of [shares] for the open [decision] (its targets in the same order as the division's). */
fun engineShares(decision: Decision.Divide, division: DamageDivision, shares: Map<MtgRef, Int>): List<Share> =
    division.among.zip(decision.among).map { (ref, target) -> Share(target, shares[ref] ?: 0) }

/**
 * The division of combat damage (CMB-033) open for the player who divides (or chooses for them), attached to the combat
 * band ([BandAttached], owner 2026-11-05): filled in with Automatum's suggestion, so the main button's OK alone accepts it
 * ([SharedFlowController.divisionAction]); the eye hides it to look at the table.
 */
@Composable
fun CombatDivisionPanel(flows: SharedFlowController, play: TableBoard, names: Map<String, String>,
    decision: Decision.Divide, division: DamageDivision, count: Pair<Int, Int>?) {
    // The decision's suggestion: Automatum's, or an early answer given just as it opened (I-D33)
    val start = division.among.zip(decision.among).associate { (ref, target) ->
        ref to (decision.suggested.firstOrNull { it.target == target }?.amount ?: 0) }
    val draft = remember(decision.id) { DivisionDraft(division, start, free = Divisions.orderFree(division)) }
    DisposableEffect(decision.id, draft) {
        flows.division = decision to draft
        onDispose { if (flows.division?.second === draft) flows.division = null }
    }
    val computed = LocalComputedBoard.current.cards[division.source]
    DivisionDock(draft, play, names, count, computed?.power, computed?.keywords.orEmpty(), flows.hidden, flows::toggleHidden)
}

/** The division panel in the dock's look (CMB-033): its content, and on the bottom line by the eye what tramples over. */
@Composable
fun DivisionDock(draft: DivisionDraft, play: TableBoard, names: Map<String, String>, count: Pair<Int, Int>?,
    power: Int?, keywords: List<String>, hidden: Boolean, toggleHidden: () -> Unit) {
    AutomatumDockPanel("Division of combat damage", hidden, toggleHidden, "division",
        buttons = { TrampleChip(draft, play, names) }) {
        DivisionContent(draft, play, names, count, power, keywords)
    }
}

/** A division panel's content (CMB-033): its head (the dealing creature at the top left, STK-033: only its controller
 * divides) and its rows. */
@Composable
fun DivisionContent(draft: DivisionDraft, play: TableBoard, names: Map<String, String>, count: Pair<Int, Int>?,
    power: Int?, keywords: List<String>) {
    val viewer = LocalChoiceFrame.current.viewer
    val inspector = remember(draft) { ChoiceInspector() }
    DivisionHead(draft.division, play, names, count, power, keywords, inspector)
    DivisionRows(draft, play, names)
    ChoiceInspection(inspector, play, viewer)
}

/**
 * CMB-033 (owner, 2026-11-05, 2026-11-06): [content] attached to the combat band of [scene], centred on its lanes, at most
 * [BAND_PANEL_WIDTH] wide: under the band when it fits there whole, else above it when it fits there, else over the lower
 * part of the table as tall as it needs (at most the table's height: then it scrolls inside), so its rows are seen. Never
 * docked above the hand.
 */
@Composable
fun BandAttached(scene: TableScene?, content: @Composable () -> Unit) {
    androidx.compose.ui.layout.Layout(content = content, modifier = Modifier.fillMaxSize()) { measurables, constraints ->
        val table = scene?.world?.visibleViewport
        val band = scene?.combatPanel ?: table?.let { TableBounds(it.x, it.center.y, it.width, 0f) }
        val width = minOf(BAND_PANEL_WIDTH.roundToPx(), constraints.maxWidth)
        val top = (table?.y ?: 0f).dp.roundToPx().coerceIn(0, constraints.maxHeight)
        val bottom = (table?.bottom?.dp?.roundToPx() ?: constraints.maxHeight).coerceIn(top, constraints.maxHeight)
        val gap = 4.dp.roundToPx()
        val below = ((band?.bottom ?: 0f).dp.roundToPx() + gap).coerceIn(top, bottom)
        val above = ((band?.y ?: 0f).dp.roundToPx() - gap).coerceIn(top, bottom)
        val placeable = measurables.firstOrNull()?.measure(androidx.compose.ui.unit.Constraints(minWidth = width, maxWidth = width,
            maxHeight = (bottom - top).coerceAtLeast(1)))
        layout(constraints.maxWidth, constraints.maxHeight) {
            if (placeable == null) return@layout
            val centre = band?.center?.x?.dp?.roundToPx() ?: (constraints.maxWidth / 2)
            val x = (centre - placeable.width / 2).coerceIn(0, (constraints.maxWidth - placeable.width).coerceAtLeast(0))
            val y = when {
                bottom - below >= placeable.height -> below
                above - top >= placeable.height -> above - placeable.height
                else -> bottom - placeable.height
            }
            placeable.place(x, y)
        }
    }
}


/** The widest a panel attached to the combat band is ([BandAttached]). */
val BAND_PANEL_WIDTH = 400.dp

/**
 * The viewer's division still to come in this damage step (I-D29, I-D33, STK-034): while another division is open, they
 * may give theirs early in the same rows as the dialog (CMB-033), applied when it opens. Ready gives it, Withdraw takes it
 * back.
 */
@Composable
fun EarlyDivisionPanel(combat: CombatController, play: TableBoard, division: DamageDivision, names: Map<String, String>,
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
                TrampleChip(draft, play, names)
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
fun dividingInDialog(flow: SharedShown): Boolean = flow.decision is Decision.Divide &&
    flow.division?.let { askedInDialog(flow.board, it) } == true

/**
 * Whether [division] on [board] is asked in the dialog (CMB-033): when its order can change something, and also
 * when it can't but its player has "Skip when I have no choice" off (combat review B: then it opens in Free, where the
 * player still has choices). Else the table makes it with the suggestion by itself.
 */
fun askedInDialog(board: BoardState, division: DamageDivision): Boolean = !Divisions.orderFree(division) ||
    board.players.firstOrNull { it.accountId == division.player }?.skipWhenNoChoice == false
