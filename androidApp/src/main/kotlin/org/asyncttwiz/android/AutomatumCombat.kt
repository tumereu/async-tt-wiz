package org.asyncttwiz.android

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.asyncttwiz.automatum.CombatFlow
import org.asyncttwiz.automatum.CombatRefused
import org.asyncttwiz.automatum.CombatShown
import org.asyncttwiz.automatum.DamageDivision
import org.asyncttwiz.automatum.EngineBatch
import org.asyncttwiz.automatum.EngineCall
import org.asyncttwiz.automatum.EngineState
import org.asyncttwiz.automatum.LaneAttack
import org.asyncttwiz.automatum.LaneBlock
import org.asyncttwiz.automatum.PreAnswer
import org.asyncttwiz.automatum.PreAnswers
import org.asyncttwiz.automatum.PreShare
import org.asyncttwiz.automatum.StepFlow
import org.asyncttwiz.automatum.Unmappable
import org.asyncttwiz.automatum.Unsupported
import org.asyncttwiz.protocol.*
import java.security.SecureRandom
import java.util.UUID

/**
 * Combat through Automatum on today's lanes (integration spec §3 I3; the interim before the combat area redesign, I-D32):
 *
 * - while the engine waits for attackers or blockers the lanes are a draft of the declaration: cards join it by dragging
 *   (CMB-009) or Declare (CMB-017), checked against what the engine allows ([draftRefusal], I-D36);
 * - Attack / No attack, Block / No blocks and Deal damage are the step button's pass (TRN-057): with a declaration
 *   awaited it declares what the lanes hold, else the priority holder passes and the engine deals the combat damage,
 *   asking for a division only where there is a choice (the shared flow's panel, I-D29);
 * - what the table shows ([shown]): the damage preview, "+N" on a blocker blocking several attackers, and divisions
 *   still to come, which their player may give early ([giveEarly], a pre-event, I-D33).
 */
internal class CombatController(
    private val current: () -> PlayState?,
    private val submit: (PlayAction) -> Boolean,
    private val tags: () -> List<MtgTag?>,
) {
    private var shownBoard: Any? = null
    /** What the table shows of combat on the board last worked out ([show]). */
    var view by mutableStateOf(CombatShown.NONE); private set

    /** [value] is what the table shows of combat on [play]'s board. */
    fun show(play: PlayState, value: CombatShown) { shownBoard = play.board; view = value }

    /** What the table shows of combat on [play]'s board: worked out already, or now (blocking). */
    fun shown(play: PlayState): CombatShown =
        if (shownBoard === play.board) view else AutomatumBoards.combat(play, tags)

    /** What is shown of combat on [play]'s board when already worked out (never blocks); null until then. */
    fun ready(play: PlayState): CombatShown? = if (shownBoard === play.board) view else AutomatumBoards.cachedCombat(play)

    /** The early answer (I-D33) to [division]: [shares] by target. One ordinary event, undoable. */
    fun giveEarly(division: DamageDivision, shares: Map<MtgRef, Int>): Boolean {
        val play = current() ?: return false
        val card = play.board.cards.singleOrNull { it.id == division.source } ?: return false
        // Keyed to that division: this turn's, in its damage step (#3, CR 510.4)
        val answer = PreAnswer.Division(division.player, MtgRef.Card(card.id, card.zoneChanges),
            shares.map { (target, amount) -> PreShare(target, amount) }, play.board.turn.number.toIntOrNull(), division.firstStrike)
        return submit(PreAnswers.set(play.board, answer))
    }

    /** Withdraws the early answer to [division]. */
    fun withdrawEarly(division: DamageDivision): Boolean {
        val play = current() ?: return false
        val card = play.board.cards.singleOrNull { it.id == division.source } ?: return false
        return submit(PreAnswers.withdraw(play.board, PreAnswer.divisionKey(MtgRef.Card(card.id, card.zoneChanges), division.firstStrike))
            ?: return false)
    }

    /** The early answer given on [play]'s board for [division], if any. */
    fun early(play: PlayState, division: DamageDivision): PreAnswer.Division? {
        val card = play.board.cards.singleOrNull { it.id == division.source } ?: return null
        return PreAnswers.find(play.board, PreAnswer.divisionKey(MtgRef.Card(card.id, card.zoneChanges), division.firstStrike)) as? PreAnswer.Division
    }

    /**
     * Why [sources] (Battlefield cards, or the cards of combat copies) can't join the draft in the lanes as [role] in
     * [pairId] (or a new pair attacking [attackTarget]) now (I-D36: a drag into the combat area means declaring an
     * attacker or a blocker); null when they can. Only what the engine allows: attackers while they are awaited, of the
     * creatures that may attack that target; blockers while they are awaited, in the pair of attackers they may block.
     */
    fun draftRefusal(play: PlayState, sources: List<String>, role: CombatRole, pairId: String?, attackTarget: String?): String? {
        val shown = shown(play)
        // A board Automatum can't take over keeps its manual combat
        if (shown.choices.unsupported.isNotEmpty()) return null
        val choices = shown.choices
        val pair = pairId?.let { id -> play.board.combat?.pairs?.singleOrNull { it.id == id } }
        choices.declaring ?: return if (CombatFlow.throughEngine(play.board)) DECLARED else NOT_DECLARING
        fun name(id: String) = CastController.cardName(play, id)
        if (choices.attackers) {
            if (role != CombatRole.ATTACKER) return "Blockers are declared in the Declare blockers step."
            val target = pair?.attackTarget ?: attackTarget
            for (source in sources) {
                if (target in choices.attacks[source].orEmpty()) continue
                return reason(play) { CombatFlow.attackRefusal(it, listOf(LaneAttack(source, target))) }
                    ?: "${name(source)} can't attack ${if (target == null) "the player" else name(target)} now."
            }
            return null
        }
        if (role != CombatRole.BLOCKER) return DECLARED_ATTACKERS
        val attackers = pair?.attackers?.map { it.sourceId }.orEmpty()
        if (attackers.isEmpty()) return "Put a blocker into the pair of the attacker it blocks."
        for (source in sources) {
            if (attackers.all { it in choices.blocks[source].orEmpty() }) continue
            return reason(play) { call -> CombatFlow.blockRefusal(call, attackers.map { LaneBlock(source, it) }) }
                ?: "${name(source)} can't block ${attackers.joinToString(" and ") { name(it) }}."
        }
        return null
    }

    /** The engine's reason for a refusal on [play] (blocking: one engine call); null when it gives none. */
    private fun reason(play: PlayState, refusal: (EngineCall) -> String?): String? = try {
        val definitions = AutomatumBoards.definitions(play)
        refusal(EngineCall(play, definitions, EngineState.flowViewer(play.board),
            AutomatumBoards.engineLog(play, definitions, tags())) { error("No randomness") })
    } catch (problem: Exception) { null }

    companion object {
        const val NOT_DECLARING = "Creatures join combat as attackers in the Declare attackers step, as blockers in the Declare blockers step."
        const val DECLARED = "Attackers and blockers are already declared."
        const val DECLARED_ATTACKERS = "Attackers are already declared."
        const val DAMAGE_BY_AUTOMATUM = "Automatum deals the combat damage itself."
    }
}

/** Works out what combat allows and would do on the shown board, off the main thread (SYS-025), for the quick actions,
 * the drags, the damage preview and early divisions ([CombatController.show]). */
@Composable
internal fun AutomatumCombatHost(toolkit: TableToolkitController, play: PlayState) {
    val on = play.board.automatum
    LaunchedEffect(IdentityKey(play.board), on) {
        if (!on) { toolkit.combat.show(play, CombatShown.NONE); return@LaunchedEffect }
        val shown = AutomatumBoards.cachedCombat(play)
            ?: kotlinx.coroutines.withContext(AutomatumBoards.boardWorker) { AutomatumBoards.combat(play, toolkit::engineTags) }
        toolkit.combat.show(play, shown)
    }
}

/**
 * The quick actions of combat with Automatum on (CMB-017–CMB-022 as I3 changes them), or null when the manual ones
 * stay (Automatum off, outside these steps, or a board Automatum can't take over). Empty until what the engine allows
 * is worked out.
 */
internal fun automatumCombatQuickActions(play: PlayState, context: QuickContext, scope: TableActionScope): List<QuickAction>? {
    if (!play.board.automatum) return null
    val position = TurnPosition.of(play.board.turn)
    if (position !in setOf(TurnPosition.DECLARE_ATTACKERS, TurnPosition.DECLARE_BLOCKERS, TurnPosition.FIRST_STRIKE_DAMAGE, TurnPosition.COMBAT_DAMAGE)) return null
    val shown = context.automatumCombat ?: return emptyList()
    if (shown.choices.unsupported.isNotEmpty()) return null
    // A combat declared by hand (not through Automatum) keeps its manual damage tools, with Automatum's values (CMB-024)
    if (position == TurnPosition.COMBAT_DAMAGE && !CombatFlow.throughEngine(play.board) && shown.passing == null) return null
    val viewer = context.viewer
    val area = context.combatAreaId
    fun action(name: String, tool: CombatTool, ids: List<String> = emptyList()) =
        QuickAction(name, "", TableActionIntent.Combat(scope, tool, area, ids))
    val choices = shown.choices
    val pairs = play.board.combat?.pairs.orEmpty()
    return buildList {
        when {
            choices.declaring != null && choices.attackers -> if (viewer == play.board.turn.accountId) {
                if (context.selectedSourceIds.isNotEmpty()) add(QuickAction("Declare without tapping", "⚔",
                    TableActionIntent.Combat(scope, CombatTool.DECLARE, area, context.selectedSourceIds.distinct())))
                else {
                    val drafted = pairs.any { it.attackers.isNotEmpty() }
                    add(QuickAction(if (drafted) "Select more attackers" else "Select attackers", "",
                        TableActionIntent.SelectAttackers(scope, advance = false)))
                    add(action(if (drafted) "Attack" else "No attack", CombatTool.AUTOMATUM_DECLARE))
                }
            }
            choices.declaring != null -> if (viewer == choices.declaring)
                add(action(if (pairs.any { it.blockers.isNotEmpty() }) "Block" else "No blocks", CombatTool.AUTOMATUM_DECLARE))
            shown.passing != null && shown.passing == viewer ->
                add(action(if (shown.firstStrikeStep) "Regular damage" else "Deal damage", CombatTool.AUTOMATUM_PASS))
        }
    }
}

/**
 * The damage preview on the table (I-D29) and the "+N" of a blocker blocking several attackers (interim lanes): over
 * each combat copy (or a Battlefield card without one, an attacked planeswalker) the damage it would take, with ✝ when
 * it would die; by each life square the life change. Public information, shown to both players.
 */
@Composable
internal fun CombatPreviewMarks(play: PlayState, shown: CombatShown, scene: TableScene, bounds: (TableItem) -> TableBounds) {
    if (!play.board.automatum) return
    val preview = shown.preview
    val damage = preview?.damage.orEmpty()
    val dies = preview?.dies.orEmpty().toSet()
    val copies = scene.items.filter { it.viewport == "combat" && it.sourceId != null }
    val inCombat = copies.mapNotNull { it.sourceId }.toSet()
    for (item in scene.items) {
        if (item.kind != TableItemKind.CARD || item.visible.isEmpty()) continue
        val card = item.sourceId ?: continue
        val copy = item.viewport == "combat"
        if (!copy && (card in inCombat || item.zone != PlayZone.BATTLEFIELD || item.projection)) continue
        val dealt = damage[card]
        val also = if (copy) shown.alsoBlocks[card] else null
        if (dealt == null && card !in dies && also == null) continue
        val b = bounds(item)
        Box(Modifier.offset { IntOffset(b.x.dp.roundToPx(), b.y.dp.roundToPx()) }.size(b.width.dp, b.height.dp)) {
            // One row over the card's top edge, so the marks of small cards in a column never cover each other
            Row(Modifier.align(Alignment.TopCenter).offset(y = (-9).dp).wrapContentSize(unbounded = true),
                horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                if (dealt != null || card in dies) PreviewChip(listOfNotNull(dealt?.let { "−$it" }, "✝".takeIf { card in dies }).joinToString(" "),
                    Color(0xffb3261e), Modifier, "Would take ${dealt ?: 0} damage${if (card in dies) " and die" else ""}")
                if (also != null) PreviewChip("+$also", AutomatumViolet, Modifier, "Also blocks $also more")
            }
        }
    }
    for ((player, change) in preview?.life.orEmpty()) {
        if (change == 0) continue
        val item = scene.items.firstOrNull { it.id == "life/$player" } ?: continue
        val b = bounds(item)
        Box(Modifier.offset { IntOffset(b.x.dp.roundToPx(), b.y.dp.roundToPx()) }.size(b.width.dp, b.height.dp)) {
            PreviewChip(if (change < 0) "−${-change}" else "+$change", if (change < 0) Color(0xffb3261e) else Color(0xff237d68),
                Modifier.align(Alignment.BottomEnd).offset(x = 10.dp, y = 6.dp), "Life would change by $change")
        }
    }
}

@Composable
private fun PreviewChip(text: String, color: Color, modifier: Modifier, description: String) {
    // Wider than a small card when it has to be: one line, never wrapped
    Surface(modifier.wrapContentSize(unbounded = true).semantics { contentDescription = description }, shape = RoundedCornerShape(6.dp),
        color = Color.White, border = BorderStroke(1.5.dp, color), shadowElevation = 2.dp) {
        Text(text, Modifier.padding(horizontal = 4.dp), color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold, lineHeight = 13.sp,
            maxLines = 1, softWrap = false)
    }
}

/**
 * The viewer's divisions still to come in this damage step (I-D29, I-D33): while another division is open, they may give
 * theirs early, applied when it opens. Pre-filled with the suggestion (or the early answer given), changeable; Ready
 * gives it, Withdraw takes it back.
 */
@Composable
internal fun EarlyDivisionPanel(combat: CombatController, play: PlayState, division: DamageDivision, names: Map<String, String>,
    enabled: Boolean) {
    val given = combat.early(play, division)
    val start = given?.shares?.associate { it.target to it.amount } ?: division.suggested
    var shares by remember(division.source, given) { mutableStateOf(division.among.associateWith { start[it] ?: 0 }) }
    val total = shares.values.sum()
    Surface(Modifier.semantics { contentDescription = "Early division" }, shape = RoundedCornerShape(8.dp), color = Color.White,
        shadowElevation = 6.dp, border = BorderStroke(2.dp, AutomatumViolet)) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${CastController.cardName(play, division.source)} — divide ${division.amount} damage", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(if (given != null) "Ready: applied when your division comes up." else "You may divide now; it is applied when your division comes up.",
                fontSize = 11.sp, color = Color(0xff4b5a5f))
            for (target in division.among) Row(verticalAlignment = Alignment.CenterVertically) {
                Text(refName(play, names, target) + (division.lethal[target]?.let { " (lethal $it)" } ?: ""),
                    Modifier.weight(1f), fontSize = 12.sp)
                Stepper(shares.getValue(target), 0, division.amount) { shares = shares + (target to it) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                if (given != null) TextButton({ combat.withdrawEarly(division) }, enabled = enabled) { Text("Withdraw") }
                Button({ combat.giveEarly(division, shares) }, enabled = enabled && total == division.amount &&
                    shares != given?.shares?.associate { it.target to it.amount },
                    colors = ButtonDefaults.buttonColors(containerColor = AutomatumViolet)) { Text("Ready") }
            }
        }
    }
}

/** A card or player as the viewer may see it named (I-D27: a face-down card stays hidden). */
internal fun refName(play: PlayState, names: Map<String, String>, ref: MtgRef): String = when (ref) {
    is MtgRef.Player -> names[ref.accountId] ?: "A player"
    is MtgRef.Card -> play.board.cards.singleOrNull { it.id == ref.cardId }?.let { card ->
        if (card.isFaceDown) "A face-down creature" else CastController.cardName(play, card.id) } ?: "A card"
    is MtgRef.StackItem -> "An ability"
}
