package org.asyncttwiz.android

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import org.asyncttwiz.protocol.*

data class TableMenuAction(val name: String, val intent: TableActionIntent)

/** Full constituent routes have no quick-window gates. Library/card/occurrence entry points use
 * the same typed intents as the quick strip, so the host opens the actual corresponding component. */
object TableActionMenus {
    fun table(play: PlayState, context: QuickContext, controller: TableActionController,
        pendingSuggestionId: String? = null, mayAccept: Boolean = false): List<TableMenuAction> {
        val scope = TableActionScope(play.gameId, play.id)
        val actions = mutableListOf(
            TableMenuAction("Next step", TableActionIntent.Next(scope)),
            TableMenuAction("End turn", TableActionIntent.End(scope)))
        controller.untap(play, false)?.let { actions += TableMenuAction("Untap all", it) }
        controller.draw(play, false)?.let { actions += TableMenuAction("Draw one", it) }
        actions += TableMenuAction("Create combat area", TableActionIntent.Combat(scope, CombatTool.CREATE, context.combatAreaId))
        actions += TableMenuAction("Close combat area", TableActionIntent.Combat(scope, CombatTool.CLOSE, context.combatAreaId))
        actions += TableMenuAction("Create token / Blank", TableActionIntent.Token(scope))
        if (pendingSuggestionId == null) {
            actions += TableMenuAction("Suggest my win", TableActionIntent.Ending(scope, EndingTool.SUGGEST_WIN))
            actions += TableMenuAction("Suggest my loss", TableActionIntent.Ending(scope, EndingTool.SUGGEST_LOSS))
            actions += TableMenuAction("Suggest draw", TableActionIntent.Ending(scope, EndingTool.SUGGEST_DRAW))
        } else if (mayAccept) actions += TableMenuAction("Accept result", TableActionIntent.Ending(scope, EndingTool.ACCEPT, pendingSuggestionId))
        return actions
    }

    fun library(play: PlayState, targets: TurnPlayerTargets, controller: TableActionController): List<TableMenuAction> =
        LibraryTool.entries.mapNotNull { tool -> controller.library(play, tool, targets)?.let {
            TableMenuAction(when (tool) {
                LibraryTool.CONTENTS -> "Library contents"
                LibraryTool.DRAW_N -> "Draw N"
                LibraryTool.VIEW -> "View"
                LibraryTool.REVEAL -> "Reveal"
                LibraryTool.SHUFFLE -> "Shuffle all"
                LibraryTool.MULLIGAN_N -> "Return Hand → Shuffle → Draw N"
                LibraryTool.MULLIGAN_MINUS_ONE -> "Return Hand → Shuffle → Draw previous Hand size −1"
            }, it)
        } }

    fun declaration(scope: TableActionScope, areaId: String?, sourceIds: List<String>) = listOf(
        TableMenuAction("Declare without tapping", TableActionIntent.Combat(scope, CombatTool.DECLARE, areaId, sourceIds.distinct())),
        TableMenuAction("Declare + tap", TableActionIntent.Combat(scope, CombatTool.DECLARE_AND_TAP, areaId, sourceIds.distinct())),
        TableMenuAction("Tap", TableActionIntent.Orient(scope, sourceIds.distinct(), true)),
        TableMenuAction("Untap", TableActionIntent.Orient(scope, sourceIds.distinct(), false)))

    fun stack(scope: TableActionScope, target: ExplicitStackTarget) = listOf(TableMenuAction(when (target.kind) {
        StackTargetKind.INSTANT_OR_SORCERY -> "Move to Graveyard"
        StackTargetKind.PERMANENT -> "Move to Battlefield"
        StackTargetKind.EFFECT -> "Remove this effect"
    }, TableActionIntent.Stack(scope, target)))
}

/** Actual phase and random input controls plus routes to the other capability components.
 * A die has only a sides input; no preselected result or result chooser exists. */
@Composable
fun TableActionMenu(play: PlayState, viewerId: String, context: QuickContext, controller: TableActionController,
    endingPlayerColor: Color, onDismiss: () -> Unit, pendingSuggestionId: String? = null, mayAccept: Boolean = false,
    perform: (TableActionIntent) -> Boolean = controller::perform, blankToken: (() -> Boolean)? = null,
    names: Map<String, String> = emptyMap()) {
    val scope = TableActionScope(play.gameId, play.id)
    var sides by remember(scope) { mutableStateOf("6") }
    var picker by remember(scope) { mutableStateOf(false) }
    var die by remember(scope) { mutableStateOf(false) }
    val landscape = LocalConfiguration.current.screenWidthDp > LocalConfiguration.current.screenHeightDp
    val enabled = context.playable && controller.enabled(scope)
    val table = TableActionMenus.table(play, context, controller, pendingSuggestionId, mayAccept)
    fun control(entry: TableMenuAction): MenuControl {
            val end = entry.intent is TableActionIntent.End
            val label = when (entry.intent) {
                is TableActionIntent.Token -> "Create catalog token"
                is TableActionIntent.Untap -> "Untap all · turn player"
                is TableActionIntent.Draw -> "Draw one · turn player"
                else -> if (end && play.board.turn.accountId != viewerId) "End opponent's turn" else entry.name
            }
            return MenuControl(label,
                enabled, color = if (end) endingPlayerColor else null) {
                if (perform(entry.intent)) onDismiss()
            }
    }
    val entries = listOf(MenuControl("Choose phase / step", enabled) { picker = true }) +
        table.filterNot { it.intent is TableActionIntent.Ending }.map(::control) +
        listOfNotNull(blankToken?.let { create -> MenuControl("Create Blank token", enabled) { if (create()) onDismiss() } }) +
        listOf(MenuControl("Roll die…", enabled) { die = true }, MenuControl("Flip coin", enabled) {
            if (controller.flipCoin(scope)) onDismiss()
        }) + table.filter { it.intent is TableActionIntent.Ending }.map(::control)
    if (!picker && !die) TableMenuPanel(onDismiss, width = if (landscape) 776.dp else 400.dp, title = "Table actions") {
        for (row in entries.chunked(if (landscape) 3 else 2)) Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (entry in row) MenuButton(entry.name, Modifier.weight(1f), entry.enabled, entry.color, entry.click)
            repeat((if (landscape) 3 else 2) - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
    if (picker) PhaseStepPicker(play, viewerId, endingPlayerColor, controller, onDismiss = { picker = false }, onComplete = onDismiss, names = names)
    if (die) TableMenuPanel({ die = false }, title = "Roll die") {
        OutlinedTextField(sides, { sides = it }, label = { Text("Die sides") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(), isError = sides.toIntOrNull()?.let { it <= 0 } != false)
        MenuButton("Roll die", Modifier.fillMaxWidth(), enabled && (sides.toIntOrNull() ?: 0) > 0) {
            sides.toIntOrNull()?.let { if (controller.rollDie(scope, it)) onDismiss() }
        }
    }
}

/** Reusable full Library/card/occurrence menu, usable at every phase. All callbacks are typed. */
@Composable
fun ConstituentActionMenu(entries: List<TableMenuAction>, controller: TableActionController,
    onDismiss: () -> Unit) {
    TableMenuPanel(onDismiss) {
        for (entry in entries) MenuButton(entry.name, Modifier.fillMaxWidth(), controller.enabled(entry.intent.scope)) {
            if (controller.perform(entry.intent)) onDismiss()
        }
    }
}
