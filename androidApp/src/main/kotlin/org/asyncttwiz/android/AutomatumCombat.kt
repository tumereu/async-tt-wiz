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
import org.asyncttwiz.automatum.AutomatumLog
import org.asyncttwiz.automatum.CombatFlow
import org.asyncttwiz.automatum.CombatRefused
import org.asyncttwiz.automatum.CombatShown
import org.asyncttwiz.automatum.DamageDivision
import org.asyncttwiz.automatum.Divisions
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

/**
 * Combat through Automatum on today's lanes (integration spec §3 I3; the interim before the combat area redesign, I-D32):
 *
 * - while the engine waits for attackers or blockers the lanes are a draft of the declaration: cards join it by dragging
 *   (CMB-009) or Declare (CMB-017), checked against what the engine allows ([draftRefusal], I-D36);
 * - Attack / No attack, Block / No blocks and Deal damage are the step arrow's pass (TRN-057): with a declaration
 *   awaited it declares what the lanes hold, else the priority holder passes and the engine deals the combat damage,
 *   asking for a division only where there is a choice (the shared flow's panel, I-D29);
 * - what the table shows ([shown]): the damage preview, "+N" on a blocker blocking several attackers, and divisions
 *   still to come, which their player may give early ([giveEarly], a pre-event, I-D33).
 */
internal class CombatController(
    private val current: () -> PlayState?,
    private val submit: (PlayAction) -> Boolean,
    private val tags: () -> List<MtgTag?>,
    /** Where the engine's reasons for refused drafts are worked out (SYS-025): off the main thread in the app. */
    private val work: AutomatumWork = AutomatumWork.Inline,
    /** The message shown now, and showing another (an engine reason worked out after the refusal). */
    private val error: () -> String? = { null },
    private val fail: (String?) -> Unit = {},
    /** The board shown now (also while the viewer can't act), which the viewer's selection belongs to. */
    private val board: () -> PlayState? = current,
) {
    private var shownBoard: BoardState? = null
    /** What the table shows of combat on the board last worked out ([show]). */
    var view by mutableStateOf(CombatShown.NONE); private set

    /** [value] is what the table shows of combat on [play]'s board. */
    fun show(play: PlayState, value: CombatShown) {
        shownBoard = play.board; view = value
        noteDivisions(play, value)
    }

    // ---- The viewer's own state of the combat area (I-D32): nothing of it is shared

    /** CMB-032, CMB-034: the combatant the viewer selected, by its real card's id; null when none. It belongs to the
     * combat step it was made in (combat review #7): once that declaration or step is over, by whoever's action, it is
     * gone. */
    val selected: String? get() = selection?.takeIf { it.second == step(board()) }?.first
    private var selection by mutableStateOf<Pair<String, String>?>(null)
    private fun step(play: PlayState?): String = play?.board?.let { b -> "${b.turn.number}/${b.turn.phase}/${b.turn.step}/${b.combat?.id}" }.orEmpty()
    /** CMB-028: the attack target the viewer's taps stage to, with the turn it was chosen in (null target: the defending
     * player). */
    private var lane by mutableStateOf<Pair<String, String?>?>(null)
    /** CMB-029: the eye hid the preview's numbers. */
    var numbersHidden by mutableStateOf(false); private set

    fun select(card: String?) { selection = card?.let { it to step(board()) } }
    fun clearSelection() { selection = null }
    fun toggleNumbers() { numbersHidden = !numbersHidden }

    /** CMB-028: the active target lane on [play]'s board: chosen this turn and still a target, else the defending player. */
    fun activeTarget(play: PlayState): String? {
        val chosen = lane?.takeIf { it.first == play.board.turn.number }?.second ?: return null
        return chosen.takeIf { id -> play.board.cards.any { it.id == id } &&
            id in CombatStackPresentation.attackTargets(play.board, play.definitions, play.board.turn.accountId) }
    }
    fun chooseTarget(play: PlayState, target: String?) { lane = play.board.turn.number to target }

    /** CMB-033: the divisions with a choice asked in a damage step, as first seen (turn, first-strike step) → sources,
     * so the dialog can say "2 of 3" after the first ones were answered. */
    private var stepDivisions: Pair<Pair<String, Boolean>, List<String>>? = null

    private fun noteDivisions(play: PlayState, value: CombatShown) {
        val asked = value.preview?.divisions.orEmpty().map { it.first }.filter { askedInDialog(play, it) }
        for (step in listOf(true, false)) {
            val sources = asked.filter { it.firstStrike == step }.map { it.source }.distinct()
            if (sources.isEmpty()) continue
            val key = play.board.turn.number to step
            val known = stepDivisions?.takeIf { it.first == key }?.second.orEmpty()
            // A list from before any answer holds every one; a later look holds only those still to come
            if (known.isEmpty() || !known.containsAll(sources)) stepDivisions = key to (known.filterNot { it in sources } + sources).distinct()
            break
        }
    }

    /** CMB-033: "n of m" for the open [division] on [play]'s board, or null when it is the only one of its step. */
    fun divisionCount(play: PlayState, division: DamageDivision): Pair<Int, Int>? {
        val known = stepDivisions?.takeIf { it.first == (play.board.turn.number to division.firstStrike) }?.second.orEmpty()
        val index = known.indexOf(division.source)
        val coming = view.early.filter { askedInDialog(play, it) }.map { it.source }.distinct()
        val (n, m) = if (index >= 0) index + 1 to maxOf(known.size, index + 1 + coming.size) else 1 to 1 + coming.size
        return (n to m).takeIf { m > 1 }
    }

    /**
     * What may be declared on [play]'s board, never worked out on the main thread in the app (#12, finalreview-combat #5,
     * SYS-025): the board's own when worked out, else the last shown board's while it awaits the same declaration
     * (drafting only moves cards in the lanes, which the engine doesn't read while a declaration is awaited); else
     * [WORKING] until [AutomatumCombatHost] has worked it out ([working] tells it). Worked out now only with
     * [AutomatumWork.Inline] (tests).
     */
    fun choices(play: PlayState): CombatShown {
        ready(play)?.let { return it }
        val last = shownBoard
        if (last != null && last.automatum && sameDeclaration(last, play.board)) return view
        return if (work === AutomatumWork.Inline) shown(play) else WORKING
    }

    /** Whether [shown] is [WORKING]: what combat allows on the board isn't worked out yet. */
    fun working(shown: CombatShown): Boolean = shown === WORKING

    /** Whether [a] and [b] differ only in what a draft of a declaration changes (the lanes, what is only shown). */
    private fun sameDeclaration(a: BoardState, b: BoardState): Boolean =
        a.turn == b.turn && a.priority == b.priority && a.engineRecords == b.engineRecords &&
            a.cards.size == b.cards.size && a.cards.zip(b.cards).all { (x, y) -> x.id == y.id && x.containerId == y.containerId &&
                x.tapped == y.tapped && x.zoneChanges == y.zoneChanges }

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
        AutomatumLog.preEvent(play.gameId, "given", answer.toString())
        return submit(PreAnswers.set(play.board, answer))
    }

    /** Withdraws the early answer to [division]. */
    fun withdrawEarly(division: DamageDivision): Boolean {
        val play = current() ?: return false
        val card = play.board.cards.singleOrNull { it.id == division.source } ?: return false
        AutomatumLog.preEvent(play.gameId, "withdrawn", "division of ${card.id}")
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
        val shown = choices(play)
        if (working(shown)) return WORKING_OUT
        // A board Automatum can't take over keeps its manual combat
        if (shown.choices.unsupported.isNotEmpty()) return null
        val choices = shown.choices
        val pair = pairId?.let { id -> play.board.combat?.pairs?.singleOrNull { it.id == id } }
        choices.declaring ?: return if (AutomatumBoards.throughEngine(play.board)) DECLARED else NOT_DECLARING
        fun name(id: String) = combatName(play, id)
        if (choices.attackers) {
            if (role != CombatRole.ATTACKER) return "Blockers are declared in the Declare blockers step."
            val target = pair?.attackTarget ?: attackTarget
            for (source in sources) {
                if (target in choices.attacks[source].orEmpty()) continue
                return refused(play, "${name(source)} can't attack ${if (target == null) "the player" else name(target)} now.") {
                    CombatFlow.attackRefusal(it, listOf(LaneAttack(source, target)))
                }
            }
            return null
        }
        if (role != CombatRole.BLOCKER) return DECLARED_ATTACKERS
        val attackers = pair?.attackers?.map { it.sourceId }.orEmpty()
        if (attackers.isEmpty()) return "Put a blocker into the pair of the attacker it blocks."
        for (source in sources) {
            if (attackers.all { it in choices.blocks[source].orEmpty() }) continue
            return refused(play, "${name(source)} can't block ${attackers.joinToString(" and ") { name(it) }}.") { call ->
                CombatFlow.blockRefusal(call, attackers.map { LaneBlock(source, it) })
            }
        }
        return null
    }

    /**
     * A refusal with the engine's reason ([refusal]) when it gives one, else [fallback]. The reason is worked out on [work]
     * (#12, SYS-025): meanwhile [fallback] is shown, replaced by the reason once it is ready unless another message
     * replaced it.
     */
    private fun refused(play: PlayState, fallback: String, refusal: (EngineCall) -> String?): String {
        if (work === AutomatumWork.Inline) return reason(play, refusal) ?: fallback
        work.run({ reason(play, refusal) }) { why -> if (why != null && error() == fallback) fail(why) }
        return fallback
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
        const val WORKING_OUT = "Automatum is still working out this combat: try again in a moment."
        /** What [choices] gives while the board's combat isn't worked out yet (an identity, never equal to a result). */
        private val WORKING = CombatShown(CombatShown.NONE.choices)
    }
}

/** Works out what combat allows and would do on the shown board, off the main thread (SYS-025), for the quick actions,
 * the drags, the damage preview and early divisions ([CombatController.show]). */
@Composable
internal fun AutomatumCombatHost(toolkit: TableToolkitController, play: PlayState) {
    LaunchedEffect(IdentityKey(play.board)) {
        val shown = AutomatumBoards.cachedCombat(play)
            ?: kotlinx.coroutines.withContext(AutomatumBoards.boardWorker) { AutomatumBoards.combat(play, toolkit::engineTags) }
        toolkit.combat.show(play, shown)
    }
}

/**
 * The quick actions of combat with Automatum on (CMB-028, CMB-029, CMB-034), or null when the manual ones
 * stay (Automatum off, outside these steps, or a board Automatum can't take over). Empty until what the engine allows
 * is worked out.
 */
/** CMB-028: [this] context with the viewer's active attack target lane and its name ([playerName]: the defending
 * player's). */
internal fun QuickContext.withCombatTarget(combat: CombatController, play: PlayState, playerName: String?): QuickContext {
    if (play.board.combat == null && TurnPosition.of(play.board.turn) != TurnPosition.DECLARE_ATTACKERS) return this
    val target = combat.activeTarget(play)
    return copy(combatTarget = target, combatTargetName = if (target == null) playerName ?: "the player" else CastController.cardName(play, target))
}

internal fun automatumCombatQuickActions(play: PlayState, context: QuickContext, scope: TableActionScope): List<QuickAction>? {
    val position = TurnPosition.of(play.board.turn)
    if (position !in setOf(TurnPosition.DECLARE_ATTACKERS, TurnPosition.DECLARE_BLOCKERS, TurnPosition.FIRST_STRIKE_DAMAGE, TurnPosition.COMBAT_DAMAGE)) return null
    val shown = context.automatumCombat ?: return emptyList()
    if (shown.choices.unsupported.isNotEmpty()) return null
    // A combat declared by hand (not through Automatum) keeps its manual quick actions (Close + next, CMB-022)
    if (position == TurnPosition.COMBAT_DAMAGE && !AutomatumBoards.throughEngine(play.board) && shown.passing == null) return null
    val viewer = context.viewer
    val area = context.combatAreaId
    val choices = shown.choices
    val pairs = play.board.combat?.pairs.orEmpty()
    fun greyed(name: String, tool: CombatTool, reason: String?, ids: List<String> = emptyList()) =
        QuickAction(name, "", TableActionIntent.Combat(scope, tool, area, ids), enabled = reason == null, refused = reason)
    // UI review round 2 #1 (decided for the owner): the step arrow alone declares and passes ("Attack (N)" / "No attack",
    // "Block (N)" / "No blocks", "Next"); combat's quick actions are only the staging helpers
    return buildList {
        when {
            // CMB-028: the attacker's staging
            choices.declaring != null && choices.attackers -> if (viewer == play.board.turn.accountId) {
                val staged = pairs.flatMap { it.attackers }.map { it.sourceId }
                val target = context.combatTarget
                val name = context.combatTargetName.ifEmpty { "target" }
                val rest = choices.attacks.filter { (card, targets) -> target in targets && card !in staged }.keys.toList()
                add(greyed("Rest → $name", CombatTool.AUTOMATUM_STAGE,
                    if (rest.isEmpty()) "No other creature can attack $name now." else null, rest))
                add(greyed("Clear", CombatTool.AUTOMATUM_CLEAR, if (staged.isEmpty()) "Nothing is staged." else null))
            }
            // CMB-034: the defender's blocks
            choices.declaring != null -> if (viewer == choices.declaring) {
                val blocks = CombatStackRules.blocks(play.board.combat)
                add(greyed("Clear", CombatTool.AUTOMATUM_CLEAR, if (blocks.isEmpty()) "Nothing blocks yet." else null))
            }
        }
    }
}

/** A card in combat as everyone may see it named in combat's messages (I-D27, combat review #6): a face-down card stays
 * hidden. */
internal fun combatName(play: PlayState, cardId: String): String =
    if (play.board.cards.singleOrNull { it.id == cardId }?.isFaceDown == true) "A face-down creature" else CastController.cardName(play, cardId)

/** A card or player as the viewer may see it named (I-D27: a face-down card stays hidden). */
internal fun refName(play: PlayState, names: Map<String, String>, ref: MtgRef): String = when (ref) {
    is MtgRef.Player -> names[ref.accountId] ?: "A player"
    is MtgRef.Card -> play.board.cards.singleOrNull { it.id == ref.cardId }?.let { card ->
        if (card.isFaceDown) "A face-down creature" else CastController.cardName(play, card.id) } ?: "A card"
    is MtgRef.StackItem -> "An ability"
}
