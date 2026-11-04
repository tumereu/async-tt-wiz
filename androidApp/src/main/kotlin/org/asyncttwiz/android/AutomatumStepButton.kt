package org.asyncttwiz.android

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.LastPage
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.asyncttwiz.automatum.CombatFlow
import org.asyncttwiz.protocol.*

/**
 * What the step arrow does (I-D46 d), said MTG Arena-style by its label: the priority holder passes ("Pass" when it isn't
 * the turn's player; for the turn's player "To combat" from the first main phase, "End turn" from the second main phase
 * on, when passing hands the turn over, else "Next"), the awaited declaration ("Attack (2)", "No attack", "Block (1)",
 * "No blocks"), "Resolve" with something on the Stack. [Waiting]: another choice is open, which its own panel answers:
 * greyed ([stepBlocked]).
 */
sealed class AutomatumStep(val label: String) {
    data object Pass : AutomatumStep("Pass")
    data object Next : AutomatumStep("Next")
    data object ToCombat : AutomatumStep("To combat")
    data object EndTurn : AutomatumStep("End turn")
    data object Resolve : AutomatumStep("Resolve")
    data object Waiting : AutomatumStep("Next")
    data class Attack(val count: Int) : AutomatumStep(if (count == 0) "No attack" else "Attack ($count)")
    data class Block(val count: Int) : AutomatumStep(if (count == 0) "No blocks" else "Block ($count)")

    companion object {
        /** The words of a pass on an empty Stack that lands as [landing] (owner playtest 2026-11-03 #1: read from the
         * engine call the tap sends, [SharedFlowController.plan]). */
        fun of(landing: org.asyncttwiz.automatum.StepFlow.Landing): AutomatumStep = when (landing) {
            org.asyncttwiz.automatum.StepFlow.Landing.Pass -> Pass
            org.asyncttwiz.automatum.StepFlow.Landing.ToCombat -> ToCombat
            org.asyncttwiz.automatum.StepFlow.Landing.EndTurn -> EndTurn
            org.asyncttwiz.automatum.StepFlow.Landing.Next -> Next
        }

        /** The step arrow on [play]'s board read from ATW's board alone: what the arrow shows until the tap's plan is
         * worked out ([SharedFlowController.plan]), and where it can't act. */
        fun of(play: TableBoard): AutomatumStep {
            val board = play.board
            if (play.flowOpen) return Waiting
            if (CombatStackRules.stack(board).cards.isNotEmpty()) return Resolve
            val position = TurnPosition.of(board.turn)
            val holder = board.priority?.holder
                ?: return when (position) {
                    TurnPosition.DECLARE_ATTACKERS -> Attack(CombatFlow.lanes(board.combat).first.size)
                    TurnPosition.DECLARE_BLOCKERS -> Block(CombatFlow.lanes(board.combat).second.map { it.blockerId }.distinct().size)
                    else -> Next
                }
            if (holder != board.turn.accountId) return Pass
            return when (position) {
                TurnPosition.PRECOMBAT_MAIN -> ToCombat
                TurnPosition.POSTCOMBAT_MAIN, TurnPosition.END_STEP, TurnPosition.CLEANUP -> EndTurn
                else -> Next
            }
        }
    }
}

/** The step arrow's icon (I-D46 d): crossed swords for going to combat and attacking, as the combat quick actions use
 * ([AutomatumStep.swords]); Pass and Next differ. */
internal val AutomatumStep.icon: ImageVector get() = when (this) {
    AutomatumStep.Pass -> Icons.Outlined.SkipNext
    AutomatumStep.Next, AutomatumStep.Waiting -> Icons.AutoMirrored.Outlined.ArrowForward
    AutomatumStep.ToCombat -> Icons.Outlined.Shield
    AutomatumStep.EndTurn -> Icons.AutoMirrored.Outlined.LastPage
    AutomatumStep.Resolve -> Icons.Outlined.PlayArrow
    is AutomatumStep.Attack -> if (count == 0) Icons.Outlined.Block else Icons.Outlined.Shield
    is AutomatumStep.Block -> if (count == 0) Icons.Outlined.RemoveModerator else Icons.Outlined.Shield
}

/** Whether the arrow draws the attack's crossed swords (To combat, Attack (N)) instead of [icon]. */
internal val AutomatumStep.swords: Boolean get() = this == AutomatumStep.ToCombat || this is AutomatumStep.Attack && count > 0

/** The neutral step arrow's colours (I-D46 d): white with a dark ink and a grey outline, whoever's turn it is. */
internal val AutomatumStepInk = MenuInk
internal val AutomatumStepOutline = Color(0xff9aa7a3)

/**
 * Why the step arrow can't act on [play] now (UI review round 2 #3, I-D46 b), or null when it can: the viewer's own cast
 * is open ([castOpen]), the opening (turn 1 while a player is still Mulliganing, TRN-062), another choice is open
 * ([AutomatumStep.Waiting]), or the declaration the lanes hold isn't one Automatum allows ([combat]: what it worked out).
 */
internal fun stepBlocked(play: TableBoard, castOpen: Boolean, combat: org.asyncttwiz.automatum.CombatShown?): String? {
    if (castOpen) return CastController.BUSY
    if (play.board.turn.number == "1" && PlayerStatus.anyOpening(play.board)) return SharedFlowController.OPENING
    val step = AutomatumStep.of(play)
    if (step == AutomatumStep.Waiting) return if (play.flowOpen) SharedFlowController.BUSY
        else SharedFlowController.STACK_WAITING
    val choices = combat?.choices ?: return null
    if (choices.declaring == null) return null
    return when (step) {
        is AutomatumStep.Attack -> if (step.count == 0) combat.noneRefusal else combat.draftRefusal
        is AutomatumStep.Block -> if (step.count == 0) combat.noneRefusal else combat.draftRefusal
        else -> null
    }
}

/**
 * Owner playtest 2026-11-03 #2 (MTG Arena): whether [viewer] sees the step arrow on [play]: in their own turn, and in the
 * other player's turn only while they hold priority (a pause, Resolve) or must decide (their blockers, a choice of
 * theirs: they act on the board shown).
 */
internal fun stepArrowShown(play: TableBoard, viewer: String?): Boolean {
    val board = play.board
    if (viewer == null || board.turn.accountId == viewer) return true
    return board.priority?.holder == viewer || board.effectiveAttentionHolder == viewer
}

/**
 * The step arrow with Automatum on (I-D46 d, I-D48): neutral, an icon above the label that says what a tap does
 * ([AutomatumStep]); tap = that, hold = End turn as before (TRN-017). The one control that moves the game on with
 * Automatum on (UI review round 2 #1): panels and quick actions have no Pass, Let it resolve or Commit of their own.
 * [blocked] ([stepBlocked]): greyed; a tap shakes it and holding it says why (I-D46 b).
 */
@Composable
internal fun AutomatumStepArrow(play: TableBoard, enabled: Boolean, compact: Boolean, onHold: () -> Unit,
    blocked: String? = null, planned: AutomatumStep? = null, onClick: () -> Unit) {
    // Owner playtest 2026-11-03 #1: the words of the tap's own plan once worked out, the board's until then
    val step = planned ?: AutomatumStep.of(play)
    val usable = enabled && blocked == null && step != AutomatumStep.Waiting
    val ink = AutomatumStepInk.copy(alpha = if (usable) 1f else .38f)
    var shakes by remember { mutableIntStateOf(0) }
    ActionSquare(step.label, "↦", enabled, Modifier.width(if (compact) 68.dp else 72.dp).shaken(shakes).shadow(3.dp, AdvanceArrowShape)
        .background(Color.White, AdvanceArrowShape).border(1.dp, AutomatumStepOutline, AdvanceArrowShape).clip(AdvanceArrowShape),
        fill = Color.White, ink = ink, compactContent = true, holdName = if (blocked == null) "End turn" else null,
        onHold = if (blocked == null) onHold else null, tooltip = blocked,
        gestureKey = step.label to blocked,
        icon = { Column(Modifier.fillMaxWidth().padding(start = 2.dp, end = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (step.swords) Icon(painterResource(R.drawable.swords_outlined), null, Modifier.size(20.dp), tint = LocalContentColor.current)
            else Icon(step.icon, null, Modifier.size(20.dp), tint = LocalContentColor.current)
            Text(step.label, Modifier.fillMaxWidth(), fontSize = 10.sp, lineHeight = 12.sp, letterSpacing = 0.sp, maxLines = 2,
                fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
        } }, onClick = { if (blocked != null) shakes++ else onClick() })
}
