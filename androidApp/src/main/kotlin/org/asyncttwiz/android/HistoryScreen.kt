package org.asyncttwiz.android

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import org.asyncttwiz.protocol.*

@Composable internal fun HistoryContinueConfirmation(enabled: Boolean, dismiss: () -> Unit, confirm: () -> Unit) {
    TableMenuPanel(dismiss, title = "Continue playing from here?") {
        Text("Your next successful action will start a new path. The existing future will be kept.", Modifier.padding(vertical = 8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(dismiss, Modifier.weight(1f).height(48.dp), shape = MaterialTheme.shapes.extraSmall,
                border = BorderStroke(1.dp, MenuBorder), colors = ButtonDefaults.outlinedButtonColors(contentColor = MenuInk)) {
                TableChromeIcon("close"); Spacer(Modifier.width(6.dp)); Text("Cancel")
            }
            Button(confirm, Modifier.weight(1f).height(48.dp), enabled = enabled, shape = MaterialTheme.shapes.extraSmall,
                colors = ButtonDefaults.buttonColors(containerColor = OwnTableColor)) {
                TableChromeIcon("fork"); Spacer(Modifier.width(6.dp)); Text("Continue")
            }
        }
    }
}

/** Exact-event detail stays separate from game-wide chat and group representative glyphs. */
@Composable internal fun HistoryEventActions(history: TableHistoryController, play: PlayState,
    toolkit: TableToolkitController, connected: Boolean, submit: (PlayAction) -> Boolean) {
    val cursor = history.playback?.position?.cursorId ?: return
    var open by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf<EventCommentDraft?>(null) }
    TextButton(onClick = { open = true }) { Text("Event details") }
    if (open) Dialog(onDismissRequest = { open = false; draft = null }) { Surface {
        Column(Modifier.padding(12.dp).heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
            Text("Selected event", style = MaterialTheme.typography.labelSmall)
            val reference = EventReference(play.id, cursor, history.view?.takeIf { it.cursor == cursor }?.contextVersion)
            val event = history.playback?.tree?.nodes?.get(cursor)
            Text(history.playback?.tree?.texts?.get(cursor)?.description ?: historyText(event).description)
            for (comment in play.annotations.comments) EventCommentRecord(comment, { history.select(it.nodeId) })
            val selected = toolkit.selectionState.value.ids.mapNotNull { id -> when {
                play.board.cards.any { it.id == id } -> PingTarget(PingTargetKind.CARD, id)
                CombatStackRules.occurrences(play.board.combat).any { it.id == id } || play.board.stackEffects.any { it.id == id } ->
                    PingTarget(PingTargetKind.OCCURRENCE, id)
                else -> null
            } }
            EventPingAction(EventPingIntent(reference, selected, true), connected) { actions -> actions.forEach { submit(it) } }
            EventPingAction(EventPingIntent(reference, selected, false), connected) { actions -> actions.forEach { submit(it) } }
            for (player in play.board.players) TextButton(enabled = connected,
                onClick = { submit(PlayAction.Ping(reference, PingTarget(PingTargetKind.PLAYER, player.accountId))) }) {
                Text("Ping ${historyPlayerName(player.accountId, history.usernames)}")
            }
            for (ping in play.annotations.pings.filter { it.reference.reference.nodeId == cursor })
                EventPingMarker(ping, ping.target.kind.name.lowercase(), targetPresent = true, onReference = { history.select(it.nodeId) }, usernames = history.usernames)
            TextButton(enabled = connected, onClick = { draft = EventCommentDraft(reference) }) { Text("Comment on event") }
            draft?.let { captured -> EventCommentComposer(captured, connected, { action ->
                if (submit(action)) draft = null
            }, { draft = null }) }
            TextButton(onClick = { open = false; draft = null }) { Text("Close event details") }
        }
    } }
}

/** Compact advanced context; switching via H06 never archives anything. Every shared action is a
 * typed original, with exact previews prepared on the history worker and captured until dismissal. */
@Composable internal fun HistoryAdvanced(history: TableHistoryController, tree: GameplayTree, cursor: String,
    game: GameSummary, viewer: String, landscape: Boolean, connected: Boolean, dismiss: () -> Unit) {
    val branchId = remember(cursor, tree.initial.id) { tree.nodes[cursor]?.branchId ?: tree.initial.root.branchId }
    val scope = GameScope(tree.initial.gameId, playId = tree.initial.id)
    Dialog(onDismissRequest = dismiss) { Surface {
        Column(Modifier.padding(12.dp).heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
            Text("Branch details")
            val archived = branchId in tree.branchArchives.archivedBranchIds
            TextButton(enabled = connected && (archived || branchId != tree.currentBranchId), onClick = {
                history.request(CapturedBranchArchiveAction(scope, if (archived) BranchArchiveIntent.Restore(branchId) else BranchArchiveIntent.Archive(branchId)))
            }) { Text(if (archived) "Restore branch" else "Archive branch") }
            TextButton(enabled = connected, onClick = { history.prepareReturn(branchId) }) { Text("Delete this branch and return…") }
            for ((index, id) in tree.branchArchives.archivedBranchIds.sorted().withIndex()) TextButton(enabled = connected, onClick = {
                history.request(CapturedBranchArchiveAction(scope, BranchArchiveIntent.Restore(id)))
            }) { Text("Restore archived continuation ${index + 1}") }
            tree.branchArchives.lastControlByBranch[branchId]?.let { control ->
                for (selection in listOf(false, true)) TextButton(enabled = connected, onClick = {
                    history.request(CapturedBranchArchiveAction(scope, BranchArchiveIntent.Recover(control, selection)))
                }) { Text(if (selection) "Recover archive state and selection" else "Recover branch archive state") }
            }
            TextButton(onClick = dismiss) { Text("Close branch details") }
        }
    } }
    history.confirmation?.let { confirmation ->
        if (confirmation.preview.needsSelection) BranchReturnSelectionDialog(history.choices,
            confirmation.preview.reason.orEmpty(), connected, history::dismissReturn) { chosen, restore ->
            history.branch?.let { history.prepareReturn(it, chosen, restore) }
        } else BranchArchiveReturnDialog(confirmation, "selected continuation",
            "return point", connected, history::dismissReturn,
            onConfirm = { history.request(it); history.dismissReturn() }) { preview ->
            Box(Modifier.fillMaxWidth().height(230.dp)) {
                TableSurface(game, preview, viewer, landscape, connected = false)
            }
        }
    }
}
