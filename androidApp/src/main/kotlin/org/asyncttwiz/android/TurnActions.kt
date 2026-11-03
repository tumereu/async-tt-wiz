package org.asyncttwiz.android

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import org.asyncttwiz.protocol.*

internal fun phaseColor(phase: String) = when (phase) {
    "BEGINNING" -> Color(0xffdcebf8)
    "PRECOMBAT_MAIN" -> Color(0xffe9dff6)
    "COMBAT" -> Color(0xffffe5ce)
    "POSTCOMBAT_MAIN" -> Color(0xffdcefe5)
    else -> Color(0xffe5e7e9)
}

internal fun phaseName(phase: String) = when (phase) {
    "BEGINNING" -> "Beginning"
    "PRECOMBAT_MAIN" -> "Precombat main"
    "COMBAT" -> "Combat"
    "POSTCOMBAT_MAIN" -> "Postcombat main"
    "ENDING" -> "Ending"
    else -> phase
}

internal val TurnPosition.shortCode: String get() = when (this) {
    TurnPosition.UNTAP -> "UNT"
    TurnPosition.UPKEEP -> "UPK"
    TurnPosition.DRAW -> "DRW"
    TurnPosition.PRECOMBAT_MAIN -> "M1"
    TurnPosition.BEGIN_COMBAT -> "BOC"
    TurnPosition.DECLARE_ATTACKERS -> "ATK"
    TurnPosition.DECLARE_BLOCKERS -> "BLK"
    TurnPosition.FIRST_STRIKE_DAMAGE -> "FS"
    TurnPosition.COMBAT_DAMAGE -> "DMG"
    TurnPosition.END_COMBAT -> "EOC"
    TurnPosition.POSTCOMBAT_MAIN -> "M2"
    TurnPosition.END_STEP -> "END"
    TurnPosition.CLEANUP -> "CLN"
}

internal fun turnStepName(turn: TurnState): String {
    val position = TurnPosition.of(turn) ?: return "${phaseName(turn.phase)} · ${turn.step}"
    val phase = phaseName(turn.phase)
    return if (position.label == phase) phase else "$phase · ${position.label}"
}

/** The one short, one-line step vocabulary shared by the step button (TRN-007), the phase dialog's step squares
 *  (TRN-008) and the step arrow (TRN-017); the phase label or dialog title gives the rest. */
internal fun stepLabel(position: TurnPosition): String = when (position) {
    TurnPosition.PRECOMBAT_MAIN -> "Main 1"
    TurnPosition.BEGIN_COMBAT -> "Begin"
    TurnPosition.DECLARE_ATTACKERS -> "Attackers"
    TurnPosition.DECLARE_BLOCKERS -> "Blockers"
    TurnPosition.FIRST_STRIKE_DAMAGE -> "1st strike"
    TurnPosition.COMBAT_DAMAGE -> "Damage"
    TurnPosition.END_COMBAT, TurnPosition.END_STEP -> "End"
    TurnPosition.POSTCOMBAT_MAIN -> "Main 2"
    else -> position.label
}

/** The phase dialog's steps (TRN-008): the twelve, and the first-strike damage step while the turn is in it or its
 * combat had one (TRN-056). */
internal fun pickerSteps(board: BoardState): List<TurnPosition> =
    if (TurnPosition.of(board.turn) == TurnPosition.FIRST_STRIKE_DAMAGE ||
        AutomatumBoards.hadFirstStrikeStep(board)) TurnPosition.entries
    else TurnPosition.MANUAL

@Composable
internal fun TurnStepIcon(turn: TurnState) {
    StepIcon(TurnPosition.of(turn))
}

@Composable
internal fun StepIcon(position: TurnPosition?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp).semantics { hideFromAccessibility() }) {
        MenuIcon(position?.label ?: "Choose phase / step", Modifier.size(20.dp), tint = LocalContentColor.current)
        Text(position?.let(::stepLabel) ?: "?", Modifier.fillMaxWidth(), fontSize = 9.sp, lineHeight = 10.sp,
            letterSpacing = 0.sp, maxLines = 1, softWrap = false, textAlign = TextAlign.Center)
    }
}

/** Icon + phase background + turn-player border. Number is a separate badge, never priority. */
@Composable
fun TurnIndicator(turn: TurnState, turnPlayerName: String, turnPlayerColor: Color,
    onOpenPicker: () -> Unit, modifier: Modifier = Modifier) {
    val position = TurnPosition.of(turn) ?: return
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        ActionSquare("$turnPlayerName, ${position.label}. Choose phase/step", position.glyph, true,
            Modifier.border(2.dp, turnPlayerColor), fill = phaseColor(turn.phase), ink = Color(0xff25333b),
            icon = { TurnStepIcon(turn) }, onClick = onOpenPicker)
        Text(turn.number, Modifier.padding(4.dp).semantics { contentDescription = "Turn ${turn.number}" },
            style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun PhaseStepPicker(play: PlayState, viewerId: String, endingPlayerColor: Color,
    controller: TableActionController, onDismiss: () -> Unit, onComplete: () -> Unit = onDismiss,
    names: Map<String, String> = emptyMap()) {
    val scope = TableActionScope(play.gameId, play.id)
    val landscape = LocalConfiguration.current.screenWidthDp > LocalConfiguration.current.screenHeightDp
    val turn = play.board.turn
    val player = names[turn.accountId]?.let { "$it's turn" } ?: if (turn.accountId == viewerId) "Your turn" else "Opponent's turn"
    TableMenuPanel(onDismiss, width = if (landscape) 484.dp else 400.dp, title = turnStepName(turn),
        border = endingPlayerColor, gap = if (landscape) 4.dp else 8.dp, contentPadding = if (landscape) 8.dp else 12.dp) {
        Text(player, style = MaterialTheme.typography.bodySmall)
        for ((phase, steps) in pickerSteps(play.board).groupBy { it.phase }) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(phaseName(phase), style = MaterialTheme.typography.labelSmall, color = MenuInk.copy(alpha = .7f))
                Row(Modifier.fillMaxWidth().background(phaseColor(phase), MaterialTheme.shapes.extraSmall)
                    .horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp, vertical = if (landscape) 0.dp else 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (step in steps) ActionSquare(step.label, step.glyph, controller.enabled(scope),
                        Modifier.border(1.dp, if (TurnPosition.of(turn) == step) endingPlayerColor else MenuBorder),
                        ink = if (TurnPosition.of(turn) == step) endingPlayerColor else MenuInk,
                        selected = TurnPosition.of(turn) == step,
                        icon = { StepIcon(step) },
                        onClick = { if (controller.perform(TableActionIntent.Choose(scope, step))) onComplete() })
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MenuButton("Next step", Modifier.weight(1f), controller.enabled(scope)) {
                if (controller.perform(TableActionIntent.Next(scope))) onComplete()
            }
            MenuButton(if (turn.accountId == viewerId) "End turn" else "End opponent's turn",
                Modifier.weight(1f), controller.enabled(scope), endingPlayerColor) {
                if (controller.perform(TableActionIntent.End(scope))) onComplete()
            }
        }
    }
}
