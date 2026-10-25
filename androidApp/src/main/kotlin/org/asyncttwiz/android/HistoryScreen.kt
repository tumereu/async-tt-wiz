package org.asyncttwiz.android

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import org.asyncttwiz.protocol.*
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Shown only when toggling history off away from the shared head. Dismiss keeps review open. */
@Composable internal fun HistoryContinueConfirmation(enabled: Boolean, dismiss: () -> Unit,
    confirm: () -> Unit, backToLatest: () -> Unit) {
    TableMenuPanel(dismiss, title = "Leave history?") {
        Text("Your next successful action will start a new path. The existing future will be kept.", Modifier.padding(vertical = 8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(backToLatest, Modifier.weight(1f).heightIn(min = 48.dp), shape = MaterialTheme.shapes.extraSmall,
                border = BorderStroke(1.dp, MenuBorder), colors = ButtonDefaults.outlinedButtonColors(contentColor = MenuInk)) {
                Text("Back to latest")
            }
            Button(confirm, Modifier.weight(1f).heightIn(min = 48.dp), enabled = enabled, shape = MaterialTheme.shapes.extraSmall,
                colors = ButtonDefaults.buttonColors(containerColor = OwnTableColor)) {
                Text("Continue from here")
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
        val pages = remember(IdentityKey(play.annotations)) { CommentPages(play.annotations) }
        var comments by remember(pages) { mutableStateOf(persistentListOf<RecordedEventComment>()) }
        var more by remember(pages) { mutableStateOf(true) }
        var loading by remember(pages) { mutableStateOf(true) }
        var page by remember(pages) { mutableIntStateOf(0) }
        LaunchedEffect(pages, page) {
            val (next, remaining) = withContext(Dispatchers.Default) { pages.next() to pages.hasMore() }
            comments = comments.addAll(next); more = remaining; loading = false
        }
        val reference = EventReference(play.id, cursor, history.view?.takeIf { it.cursor == cursor }?.contextVersion)
        LazyColumn(Modifier.padding(12.dp).heightIn(max = 480.dp)) {
            item {
            Text("Selected event", style = MaterialTheme.typography.labelSmall)
            val event = history.playback?.tree?.nodes?.get(cursor)
            Text(history.playback?.tree?.texts?.get(cursor)?.description ?: historyText(event).description)
            }
            items(comments, key = { it.recording.nodeId }) { comment -> EventCommentRecord(comment, { history.select(it.nodeId) }) }
            item {
            if (more) TextButton(enabled = !loading, onClick = { loading = true; page++ }) { Text("Load more comments") }
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
            for (ping in play.currentGroup?.id?.let { numberedActivePings(play.annotations, it) }.orEmpty()
                .filter { it.reference.reference.nodeId == cursor })
                EventPingMarker(ping, ping.target.kind.name.lowercase(), targetPresent = true,
                    onReference = { history.select(it.nodeId) }, usernames = history.usernames,
                    color = if (ping.recording.author == toolkit.viewer) OwnTableColor else OpponentTableColor)
            TextButton(enabled = connected, onClick = { draft = EventCommentDraft(reference) }) { Text("Comment on event") }
            draft?.let { captured -> EventCommentComposer(captured, connected, { action ->
                if (submit(action)) draft = null
            }, { draft = null }) }
            TextButton(onClick = { open = false; draft = null }) { Text("Close event details") }
            }
        }
    } }
}
