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
        val actions = mutableListOf<TableMenuAction>()
        val combat = play.board.combat
        actions += if (combat == null) TableMenuAction("Create combat area", TableActionIntent.Combat(scope, CombatTool.CREATE, null))
            else TableMenuAction("Close combat area", TableActionIntent.Combat(scope, CombatTool.CLOSE, combat.id))
        actions += TableMenuAction("Create token", TableActionIntent.Token(scope))
        if (pendingSuggestionId == null) {
            actions += TableMenuAction("Suggest game end", TableActionIntent.Ending(scope, EndingTool.SUGGEST_WIN))
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
     perform: (TableActionIntent) -> Boolean = controller::perform,
    names: Map<String, String> = emptyMap(), onTimeline: (() -> Unit)? = null) {
    val scope = TableActionScope(play.gameId, play.id)
    var sides by remember(scope) { mutableStateOf(selectedLibraryQuantity("6")) }
    var ending by remember(scope) { mutableStateOf(false) }
    var outcome by remember(scope) { mutableStateOf<Pair<String, EndingTool>?>(null) }
    var die by remember(scope) { mutableStateOf(false) }
    val landscape = LocalConfiguration.current.screenWidthDp > LocalConfiguration.current.screenHeightDp
    val enabled = context.playable && controller.enabled(scope)
    val table = TableActionMenus.table(play, context, controller, pendingSuggestionId, mayAccept)
    fun control(entry: TableMenuAction): MenuControl {
            val end = entry.intent is TableActionIntent.End
            val label = when (entry.intent) {
                 is TableActionIntent.Token -> "Create token"
                is TableActionIntent.Untap -> "Untap all · turn player"
                is TableActionIntent.Draw -> "Draw one · turn player"
                else -> if (end && play.board.turn.accountId != viewerId) "End opponent's turn" else entry.name
            }
            return MenuControl(label,
                enabled, color = if (end) endingPlayerColor else null) {
                if (entry.name == "Suggest game end") ending = true
                else if (perform(entry.intent)) onDismiss()
            }
    }
    val entries = table.filterNot { it.intent is TableActionIntent.Ending }.map(::control) +
        listOf(MenuControl("Roll die…", enabled) { die = true }, MenuControl("Flip coin", enabled) {
            if (controller.flipCoin(scope)) onDismiss()
         }) + listOf(MenuControl("Timeline", onTimeline != null) { onDismiss(); onTimeline?.invoke() }) +
        table.filter { it.intent is TableActionIntent.Ending }.map(::control)
    if (!ending && !die) TableMenuPanel(onDismiss, width = if (landscape) 776.dp else 400.dp, title = "Table actions") {
        for (row in entries.chunked(if (landscape) 3 else 2)) Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (entry in row) MenuButton(entry.name, Modifier.weight(1f), entry.enabled, entry.color, entry.click)
            repeat((if (landscape) 3 else 2) - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
    if (ending && outcome == null) TableMenuPanel({ ending = false }, title = "Suggest game end") {
        for (choice in listOf("My win" to EndingTool.SUGGEST_WIN, "My loss" to EndingTool.SUGGEST_LOSS, "Draw game" to EndingTool.SUGGEST_DRAW))
            MenuButton(choice.first, Modifier.fillMaxWidth(), enabled) { outcome = choice }
    }
    outcome?.let { chosen ->
        AlertDialog(onDismissRequest = { outcome = null }, title = { Text("Suggest ${chosen.first.lowercase().removeSuffix(" game")}?") },
            confirmButton = { TextButton(enabled = enabled, onClick = {
                if (perform(TableActionIntent.Ending(scope, chosen.second))) onDismiss()
            }) { Text("Confirm") } },
            dismissButton = { TextButton(onClick = { outcome = null }) { Text("Cancel") } })
    }
    if (die) TableMenuPanel({ die = false }, title = "Roll die") {
        ValueInputControl("Die sides", sides, { sides = it }, enabled, minimum = 1)
        MenuButton("Roll die", Modifier.fillMaxWidth(), enabled && (sides.text.toIntOrNull() ?: 0) > 0) {
            sides.text.toIntOrNull()?.let { if (controller.rollDie(scope, it)) onDismiss() }
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
