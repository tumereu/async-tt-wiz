package org.asyncttwiz.android

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import arcanum.engine.decision.ManaUse
import org.asyncttwiz.automatum.CombatCostShown
import org.asyncttwiz.automatum.CombatFlow
import org.asyncttwiz.automatum.CombatShown
import org.asyncttwiz.automatum.ManaPicks
import org.asyncttwiz.protocol.BoardState

/**
 * CMB-098 (owner, 2026-11-25): what the costs panel asks of a declaration of attackers or blockers, worked out from what the
 * table shows of combat ([CombatShown]) without the engine.
 */
object CombatCosts {
    /**
     * CMB-098, CMB-075: whether declaring what [board]'s lanes hold asks something of the viewer before it is made, so the main
     * button opens the costs panel instead of declaring: an optional cost to attack a staged attacker may pay (CR 508.1g, exert),
     * or with Autotap off ([autotap]) mana a cost to attack or block asks (CR 508.1h, 509.1d). With Autotap on and only costs
     * the creatures must pay there is nothing to decide: Autotap pays as the main button declares.
     */
    fun toDecide(board: BoardState, shown: CombatShown, autotap: Boolean): Boolean {
        val choices = shown.choices
        if (choices.declaring == null) return false
        val (attacks, blocks) = CombatFlow.lanes(board.combat)
        val creatures = if (choices.attackers) attacks.map { it.cardId } else blocks.map { it.blockerId }.distinct()
        if (choices.attackers && creatures.any { choices.optional[it].orEmpty().isNotEmpty() }) return true
        return !autotap && creatures.any { card -> shown.costs[card].orEmpty().any(::asksMana) }
    }

    /** Whether [cost] asks mana: a part of its words that is mana symbols ("{2}" of "{2}, 2 life"). */
    fun asksMana(cost: CombatCostShown): Boolean = parts(cost).any { it.startsWith("{") }

    /** The parts of [cost]'s words ("{2}", "2 life"), as [CombatFlow.costLabel] joins them. */
    fun parts(cost: CombatCostShown): List<String> = cost.label.split(", ").map { it.trim() }.filter { it.isNotEmpty() }

    /**
     * CMB-098: the panel's "Total to pay" line: the declaration's mana as one payment ([mana], the payment's cost; CR 508.1h,
     * 509.1d), then every other part of [costs] in order ("{3}, 2 life, Exert"); null when nothing is to be paid.
     */
    fun total(mana: String?, costs: List<CombatCostShown>): String? {
        val others = costs.flatMap(::parts).filterNot { it.startsWith("{") }
        val printedMana = if (mana.isNullOrEmpty()) costs.flatMap(::parts).filter { it.startsWith("{") } else listOf(mana)
        return (printedMana + others).takeIf { it.isNotEmpty() }?.joinToString(", ")
    }
}

/** An optional cost to attack of a staged attacker in the costs panel: [card]'s [cost], [paid] when chosen. */
private data class OptionalRow(val card: String, val cost: CombatCostShown, val paid: Boolean)

/**
 * CMB-098 (owner, 2026-11-25): the costs panel, opened by the main button's declaration when it asks something of the viewer
 * ([CombatCosts.toDecide]), docked above the hand: the optional costs to attack as the attackers' cards (a tap marks each paid,
 * CR 508.1g), the costs each creature must pay with their total (CR 508.1h, 509.1d), the mana section paying them on the table as
 * the cast panel does (STK-157: Autotap's payment outlined there, or sources tapped by hand with Autotap off), the abilities' whole
 * texts (STK-181), Back to staging; Attack (Block) is the main button (TRN-114). Nothing is declared before Attack.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CombatCostPanel(toolkit: TableToolkitController, play: TableBoard) {
    val combat = toolkit.combat
    val flows = toolkit.flows
    val viewer = toolkit.viewer
    val shown = combat.readyOrLast(play) ?: return
    val attackers = shown.choices.attackers
    val (attacks, blocks) = CombatFlow.lanes(play.board.combat)
    val creatures = if (attackers) attacks.map { it.cardId } else blocks.map { it.blockerId }.distinct()
    val given = CombatFlow.attackChoices(play.board)
    val optional = if (!attackers) emptyList() else creatures.flatMap { card ->
        shown.choices.optional[card].orEmpty().map { OptionalRow(card, it, it.key in given[card]?.paying.orEmpty()) } }
    val required = creatures.flatMap { card -> shown.costs[card].orEmpty().map { card to it } }
    // The payment of the declaration as the lanes and choices hold it now, once worked out on this board
    val loaded = flows.flowOn(play) != null
    val payment = if (loaded) flows.declarationPayment else null
    val due = payment?.cost?.toString()?.takeIf { it.isNotEmpty() }
    // The total of what the creatures must pay, with the optional costs chosen beside them
    val total = if (required.isEmpty()) null else CombatCosts.total(due, required.map { it.second } + optional.filter { it.paid }.map { it.cost })
    val inspector = remember { ChoiceInspector() }
    var hidden by remember { mutableStateOf(false) }
    val what = if (attackers) "attack" else "block"
    // TRN-114: Attack (Block) is the main button, greyed with why while it can't be made yet
    val refusal = when {
        flows.busy -> SharedFlowController.COSTS_WORKING
        !loaded -> SharedFlowController.COSTS_WORKING
        payment != null && !flows.paid -> SharedFlowController.owed(due.orEmpty(), flows.mana.isEmpty())
        else -> shown.draftRefusal
    }
    LaunchedEffect(play.board.turn.number, optional.map { it.paid }, required.size, due, refusal) {
        AppLog.info("combat", "Costs panel shows: optional ${optional.map { "${it.card} ${it.cost.label}${if (it.paid) " paid" else ""}" }}, " +
            "required ${required.map { "${it.first} ${it.second.label}" }}, total ${total ?: "nothing"}, payment ${due ?: if (loaded) "none" else "being worked out"}" +
            (refusal?.let { ", can't go on: $it" } ?: ""))
    }
    MainButtonAction(MainAction(if (attackers) "Attack" else "Block", Icons.Outlined.Shield, refusal,
        tooltip = if (attackers) "Attack, paying these costs" else "Block, paying these costs", swords = attackers) {
        if (flows.declareWithCosts()) AppLog.info("combat", "Costs panel: the declaration went out")
    })
    AutomatumDockPanel("Costs to $what", hidden, { hidden = !hidden }, "costs panel", buttons = {
        TextButton({ combat.closeCosts(play, "Back to staging") }, Modifier.testTag(COSTS_BACK_TAG)) {
            IconText(Icons.AutoMirrored.Outlined.ArrowBack, "Back")
        }
    }) {
        ChoiceHead(if (attackers) "Costs to attack" else "Costs to block", null, inspector)
        if (optional.isNotEmpty()) Section("As they attack, you may pay", "Tap a card to pay its cost, again not to.") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (row in optional) OptionalCard(play, viewer, row, inspector) {
                    AppLog.info("combat", "Costs panel: ${row.card}'s ${row.cost.label} ${if (row.paid) "no longer paid" else "paid"}")
                    combat.toggleOptional(row.card, row.cost.key)
                }
            }
            AbilityTexts(play, optional.map { it.cost }.distinctBy { it.text })
        }
        if (required.isNotEmpty()) Section(if (attackers) "To attack, they must pay" else "To block, they must pay") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for ((card, cost) in required) RequiredCard(play, viewer, card, cost, inspector)
            }
            AbilityTexts(play, required.map { it.second }.distinctBy { it.text to it.source })
        }
        if (total != null) CardText("Total to pay: $total", Modifier.semantics { contentDescription = "Total to pay: $total" },
            style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold))
        if (payment != null && due != null) CostsMana(flows, play, payment, due)
        // CMB-075, TRN-063: an unpayable total (or another refusal of the engine's) is said here as on the main button
        if (payment == null || flows.paid) shown.draftRefusal?.let {
            CardText(it, Modifier.semantics { contentDescription = "Can't $what: $it" }, color = Color(0xffb3261e),
                style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium))
        }
        ChoiceInspection(inspector, play, viewer)
    }
}

/** CMB-098: an optional cost's attacker as a card, ringed in violet with a check while it pays [OptionalRow.cost]; a tap marks
 * it paid or not ([toggle]), holding it inspects the card. Its cost's words under it. */
@Composable
private fun OptionalCard(play: TableBoard, viewer: String, row: OptionalRow, inspector: ChoiceInspector, toggle: () -> Unit) {
    val card = choiceCard(play, viewer, "optional/${row.card}/${row.cost.key}", row.card, true, combatName(play, row.card))
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        ChoiceCardFace(card, Modifier.testTag(COSTS_OPTIONAL_TAG)
            .semantics {
                contentDescription = "${row.cost.label} ${card.name} as it attacks"
                stateDescription = if (row.paid) "paid" else "not paid"
            }
            .choiceCardGestures(card.key, tap = toggle, hold = { inspector.hold(card, it) }, moved = inspector::moved),
            selected = row.paid)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(if (row.paid) Icons.Outlined.CheckBox else Icons.Outlined.CheckBoxOutlineBlank, null, Modifier.size(14.dp),
                tint = if (row.paid) AutomatumViolet else Color(0xff8a9296))
            Spacer(Modifier.width(2.dp))
            CardText(row.cost.label, style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold,
                color = if (row.paid) AutomatumViolet else Color(0xff4b5a5f)))
        }
    }
}

/** CMB-098, CMB-044: a cost a creature must pay as a card with its amber cost chip under it; holding the card inspects it. */
@Composable
private fun RequiredCard(play: TableBoard, viewer: String, cardId: String, cost: CombatCostShown, inspector: ChoiceInspector) {
    val card = choiceCard(play, viewer, "required/$cardId/${cost.label}/${cost.source}", cardId, true, combatName(play, cardId))
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        ChoiceCardFace(card, Modifier.semantics { contentDescription = "${card.name} pays ${cost.label}" }
            .choiceCardGestures(card.key, tap = {}, hold = { inspector.hold(card, it) }, moved = inspector::moved))
        Surface(shape = RoundedCornerShape(6.dp), color = Color.White, border = BorderStroke(1.5.dp, AutotapAmber)) {
            CardText(cost.label, Modifier.padding(horizontal = 4.dp), style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold,
                color = Color(0xff7a4a00)), maxLines = 1)
        }
    }
}

/** STK-181, STK-226: each ability asking a cost, its source named over its whole text, once. */
@Composable
private fun AbilityTexts(play: TableBoard, costs: List<CombatCostShown>) {
    for (cost in costs) {
        // STK-181: the ability's whole text, its lines kept, as the choice panels show it
        val text = spellText(cost.text) ?: continue
        // STK-230 (owner 2026-11-25): nothing highlighted: the text is the one ability asking the cost, and where its cost's words sit
        // in it isn't known exactly (the parser keeps no place for a cost to attack or block)
        LaunchedEffect(cost.source, text) {
            AppLog.info("combat", "Costs panel text of ${cost.source ?: "no card"}'s ${cost.label}: \"${text.take(160)}\", nothing highlighted: " +
                "the text is the ability asking the cost, its cost's words not placed in it")
        }
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            cost.source?.let { ChoiceSourceName(combatName(play, it), small = true) }
            AskedText(text, null, "Ability")
        }
    }
}

/**
 * CMB-098, STK-157: the costs' mana, paid on the table as the cast panel's: "Mana · {3}" saying only that the outlined sources pay
 * and how to change them, or in red what is still owed; pool mana as small cards (STK-228) and life or other ways the table can't
 * tap as chips.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CostsMana(flows: SharedFlowController, play: TableBoard, payment: arcanum.engine.decision.Decision.ManaPayment, due: String) {
    val viewer = LocalChoiceFrame.current.viewer
    Section("Mana · $due") {
        val reason = if (flows.paid) null else SharedFlowController.owed(due, flows.mana.isEmpty())
        if (reason != null) CardText(reason, Modifier.semantics { contentDescription = "Not paid: $reason" }, color = Color(0xffb3261e),
            style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium))
        else Text(if (flows.mana.none { flows.tableSource(it) != null }) "Tap a source on the table to change it."
            else "Outlined sources pay. Tap sources on the table to change them.", fontSize = 11.sp, color = Color(0xff4b5a5f))
        val others = ManaPicks.groups(payment).filter { group -> flows.tableSource(group.first()) == null && !ManaPicks.usesObjects(group.first()) }
        if (others.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (group in others) {
                val used = flows.mana.firstOrNull { it in group }
                val use = used ?: group.first()
                if (use is ManaUse.Pool) PoolManaTile(poolManaLook(flows, play, payment.player, viewer, use), used != null, { flows.toggleMana(group) })
                else {
                    val label = when (use) {
                        is ManaUse.Life -> "2 life"
                        is ManaUse.Special -> use.objects.joinToString { objectName(flows, play, it) }
                        is ManaUse.Ability -> objectName(flows, play, use.source)
                        is ManaUse.Pool -> ""
                    }
                    FilterChip(used != null, { flows.toggleMana(group) },
                        modifier = Modifier.semantics { contentDescription = "Mana source $label${if (used != null) ", chosen" else ""}" },
                        label = { CardText(label, style = TextStyle(fontSize = 12.sp)) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AutomatumViolet.copy(alpha = .18f)))
                }
            }
        }
    }
}

/** The test tag of the costs panel's Back ([CombatCostPanel]). */
const val COSTS_BACK_TAG = "costs-back"

/** The test tag of an optional cost's card in the costs panel. */
const val COSTS_OPTIONAL_TAG = "costs-optional"
