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
import androidx.compose.ui.platform.testTag
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
 * on, when passing hands the turn over, else "Next"; in Control "End step" from the second main phase, whose tap stops at
 * the end step, TRN-063), the awaited declaration ("Attack (2)", "No attack", "Block (1)", "No blocks"), "Resolve" with
 * something on the Stack. [Waiting]: another choice is open, which its own panel answers: greyed ([stepBlocked]).
 */
sealed class AutomatumStep(val label: String) {
    data object Pass : AutomatumStep("Pass")
    data object Next : AutomatumStep("Next")
    data object ToCombat : AutomatumStep("To combat")
    data object EndTurn : AutomatumStep("End turn")
    /** TRN-063 (owner bug 2026-11-07): in Control, the second main phase's tap goes on to the end step, where Control stops. */
    data object ToEndStep : AutomatumStep("End step")
    data object Resolve : AutomatumStep("Resolve")
    data object Waiting : AutomatumStep("Next")
    data class Attack(val count: Int) : AutomatumStep(if (count == 0) "No attack" else "Attack ($count)")
    data class Block(val count: Int) : AutomatumStep(if (count == 0) "No blocks" else "Block ($count)")

    companion object {
        /** The step arrow on [play]'s board, read from the board and whether the viewer's response mode is Control
         * ([control], STK-036: it stops at their own end step, so their second main phase's tap is "End step"). */
        fun of(play: TableBoard, control: Boolean = false): AutomatumStep {
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
                TurnPosition.POSTCOMBAT_MAIN -> if (control) ToEndStep else EndTurn
                TurnPosition.END_STEP, TurnPosition.CLEANUP -> EndTurn
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
    AutomatumStep.ToEndStep -> Icons.Outlined.Bedtime
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
 * HIS-110 (owner, 2026-11-07): the other player's reveal panel is open ([revealOpen]) until its OK.
 */
internal fun stepBlocked(play: TableBoard, castOpen: Boolean, combat: org.asyncttwiz.automatum.CombatShown?,
    revealOpen: Boolean = false): String? {
    if (revealOpen) return REVEAL_BUSY
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
 * Owner playtest 2026-11-03 #2 (MTG Arena), TRN-015: whether [viewer] sees the step arrow on [play] (a table without
 * an engine game's own rule, [EngineTableRoutes.arrowShown]): only while they hold priority (a pause, Resolve) or must
 * act (their move, their blockers, a choice of theirs: they act on the board shown), in either player's turn; while the
 * other player must act its place stays empty, in the viewer's own turn too.
 */
internal fun stepArrowShown(play: TableBoard, viewer: String?): Boolean {
    val board = play.board
    if (viewer == null) return true
    return board.priority?.holder == viewer || board.effectiveAttentionHolder == viewer
}

/**
 * The step arrow with Automatum on (I-D46 d, I-D48), the main button (owner 2026-11-05): neutral, an icon above the label
 * that says what a tap does ([AutomatumStep]); tap = that, hold = End turn as before (TRN-017), except Resolve (STK-029),
 * whose hold says what it does ([resolveTip]). The one control that moves the game on with Automatum on (UI review round
 * 2 #1): panels and quick actions have no Pass, Let it resolve or Commit of their own. [blocked] ([stepBlocked]): greyed;
 * a tap shakes it and holding it says why (I-D46 b).
 */
@Composable
internal fun AutomatumStepArrow(play: TableBoard, enabled: Boolean, modifier: Modifier, onHold: () -> Unit,
    blocked: String? = null, resolveTip: String? = null,
    /** CMB-033 (owner, 2026-11-05): the go-on action of an open combat choice (the damage division's OK) in its place. */
    main: MainAction? = null,
    /** TRN-063: the viewer's response mode is Control ([AutomatumStep.of]). */
    control: Boolean = false, onClick: () -> Unit) {
    if (main != null) { MainActionArrow(main, enabled, modifier); return }
    val step = AutomatumStep.of(play, control)
    val usable = enabled && blocked == null && step != AutomatumStep.Waiting
    val ink = AutomatumStepInk.copy(alpha = if (usable) 1f else .38f)
    var shakes by remember { mutableIntStateOf(0) }
    val endTurn = blocked == null && step != AutomatumStep.Resolve
    ActionSquare(step.label, "↦", enabled, modifier.testTag("main-button").shaken(shakes).shadow(3.dp, AdvanceArrowShape)
        .background(Color.White, AdvanceArrowShape).border(1.dp, AutomatumStepOutline, AdvanceArrowShape).clip(AdvanceArrowShape),
        fill = Color.White, ink = ink, compactContent = true, holdName = if (endTurn) "End turn" else null,
        onHold = if (endTurn) onHold else null, tooltip = blocked ?: resolveTip.takeIf { step == AutomatumStep.Resolve },
        gestureKey = step.label to blocked,
        icon = { MainButtonContent(icon = {
            if (step.swords) Icon(painterResource(R.drawable.swords_outlined), null, Modifier.size(20.dp), tint = LocalContentColor.current)
            else Icon(step.icon, null, Modifier.size(20.dp), tint = LocalContentColor.current)
        }, label = step.label) }, onClick = { if (blocked != null) shakes++ else onClick() })
}

/**
 * A go-on action of an open choice on the main button (TRN-063, owner 2026-11-05): [label] under [icon]; [refusal] greys
 * it (a tap shakes it, holding it says why); [tooltip] is what holding it says otherwise.
 */
class MainAction(val label: String, val icon: ImageVector, val refusal: String? = null, val tooltip: String? = null,
    val run: () -> Unit)

/** The main button holding [action] ([MainAction]): the step arrow's look, no hold command. */
@Composable
internal fun MainActionArrow(action: MainAction, enabled: Boolean, modifier: Modifier) {
    var shakes by remember { mutableIntStateOf(0) }
    val ink = AutomatumStepInk.copy(alpha = if (enabled && action.refusal == null) 1f else .38f)
    ActionSquare(action.label, "", enabled, modifier.testTag("main-button").shaken(shakes).shadow(3.dp, AdvanceArrowShape)
        .background(Color.White, AdvanceArrowShape).border(1.dp, AutomatumStepOutline, AdvanceArrowShape).clip(AdvanceArrowShape),
        fill = Color.White, ink = ink, compactContent = true, tooltip = action.refusal ?: action.tooltip,
        gestureKey = action.label to action.refusal,
        icon = { MainButtonContent(icon = { Icon(action.icon, null, Modifier.size(20.dp), tint = LocalContentColor.current) },
            label = action.label) }, onClick = { if (action.refusal != null) shakes++ else action.run() })
}

/**
 * The main button's face: an icon above the words of what a tap does, clear of the arrow's tip. TRN-063 (owner playtest
 * 2026-11-05 evening): the words have room for two lines, wrapped between words ("Assume no / response"); where two lines
 * don't fit under the icon (large system text) or a word is wider than the button, they are drawn smaller rather than cut.
 */
@Composable
internal fun MainButtonContent(icon: @Composable () -> Unit, label: String) {
    Column(Modifier.fillMaxWidth().padding(start = 4.dp, end = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        icon()
        BoxWithConstraints(Modifier.fillMaxWidth().heightIn(max = MAIN_LABEL_ROOM), contentAlignment = Alignment.Center) {
            val measurer = androidx.compose.ui.text.rememberTextMeasurer()
            val style = androidx.compose.material3.LocalTextStyle.current.merge(MainLabelStyle)
            val scale = remember(label, style, constraints, measurer) { cellLabelScale(measurer, label, style, constraints) }
            Text(label, Modifier.fillMaxWidth(), maxLines = 2, textAlign = TextAlign.Center,
                style = style.copy(fontSize = style.fontSize * scale, lineHeight = style.lineHeight * scale))
        }
    }
}

/** The main button's words: 11 sp on up to two lines of 12 sp under its 20 dp icon, in the 48 dp button. */
private val MainLabelStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, lineHeight = 12.sp, letterSpacing = 0.sp,
    fontWeight = FontWeight.Medium)
private val MAIN_LABEL_ROOM = 27.dp
