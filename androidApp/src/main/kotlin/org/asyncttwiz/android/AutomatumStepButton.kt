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
 * What the step arrow does with Automatum on (I-D46 d), said MTG Arena-style by its label: the priority holder passes
 * ("Pass" when it isn't the turn's player; for the turn's player "To combat" from the first main phase, "End turn" from
 * the second main phase on, when passing hands the turn over, else "Next"), the awaited declaration ("Attack (2)",
 * "No attack", "Block (1)", "No blocks"), "Resolve" with something on the Stack, "Done" for Automatum's step done by hand
 * (I-D37). [Waiting]: another choice is open, which its own panel answers: greyed.
 */
internal sealed class AutomatumStep(val label: String) {
    data object Pass : AutomatumStep("Pass")
    data object Next : AutomatumStep("Next")
    data object ToCombat : AutomatumStep("To combat")
    data object EndTurn : AutomatumStep("End turn")
    data object Resolve : AutomatumStep("Resolve")
    data object Done : AutomatumStep("Done")
    data object Waiting : AutomatumStep("Next")
    data class Attack(val count: Int) : AutomatumStep(if (count == 0) "No attack" else "Attack ($count)")
    data class Block(val count: Int) : AutomatumStep(if (count == 0) "No blocks" else "Block ($count)")

    companion object {
        /** The step arrow on [play]'s board with Automatum on (read from ATW's board alone, so it shows at once). */
        fun of(play: PlayState): AutomatumStep {
            val board = play.board
            if (Automatum.flowOpen(board)) return if (AutomatumBoards.byHand(play)) Done else Waiting
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

internal val AutomatumStep.icon: ImageVector get() = when (this) {
    AutomatumStep.Pass, AutomatumStep.Next, AutomatumStep.Waiting -> Icons.Outlined.SkipNext
    AutomatumStep.ToCombat -> Icons.Outlined.Gavel
    AutomatumStep.EndTurn -> Icons.AutoMirrored.Outlined.LastPage
    AutomatumStep.Resolve -> Icons.Outlined.PlayArrow
    AutomatumStep.Done -> Icons.Outlined.Check
    is AutomatumStep.Attack -> if (count == 0) Icons.Outlined.Block else Icons.Outlined.Gavel
    is AutomatumStep.Block -> if (count == 0) Icons.Outlined.RemoveModerator else Icons.Outlined.Shield
}

/** The neutral step arrow's colours (I-D46 d): white with a dark ink and a grey outline, whoever's turn it is. */
internal val AutomatumStepInk = MenuInk
internal val AutomatumStepOutline = Color(0xff9aa7a3)

/**
 * The step arrow with Automatum on (I-D46 d, I-D48): neutral, an icon above the label that says what a tap does
 * ([AutomatumStep]); tap = that, hold = End turn as before (TRN-017).
 */
@Composable
internal fun AutomatumStepArrow(play: PlayState, enabled: Boolean, compact: Boolean, onHold: () -> Unit, onClick: () -> Unit) {
    val step = AutomatumStep.of(play)
    val usable = enabled && step != AutomatumStep.Waiting
    val ink = AutomatumStepInk.copy(alpha = if (usable) 1f else .38f)
    ActionSquare(step.label, "↦", enabled, Modifier.width(if (compact) 68.dp else 72.dp).shadow(3.dp, AdvanceArrowShape)
        .background(Color.White, AdvanceArrowShape).border(1.dp, AutomatumStepOutline, AdvanceArrowShape).clip(AdvanceArrowShape),
        fill = Color.White, ink = ink, compactContent = true, holdName = "End turn", onHold = onHold,
        gestureKey = step.label,
        icon = { Column(Modifier.fillMaxWidth().padding(start = 2.dp, end = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(step.icon, null, Modifier.size(20.dp), tint = LocalContentColor.current)
            Text(step.label, Modifier.fillMaxWidth(), fontSize = 10.sp, lineHeight = 12.sp, letterSpacing = 0.sp, maxLines = 2,
                fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
        } }, onClick = onClick)
}
