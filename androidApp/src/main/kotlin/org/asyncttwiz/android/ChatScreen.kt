package org.asyncttwiz.android

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.asyncttwiz.protocol.*

/** Only intersecting laid-out rows count as presented; lazy prefetch is not viewing. */
internal fun presentedChatIds(messages: List<ChatMessage>, visibleKeys: List<Any>, panelVisible: Boolean): Set<String> =
    if (!panelVisible) emptySet() else visibleKeys.filterIsInstance<String>().toSet().intersect(messages.map { it.id }.toSet())

@Composable
internal fun ChatPanel(controller: LobbyController, accountId: String, modifier: Modifier = Modifier) {
    val state = controller.state
    val game = state.detail ?: return
    val chat = state.chat
    val list = rememberLazyListState()
    val viewEpoch = controller.chatViewEpoch
    BackHandler { controller.closeChat() }
    LaunchedEffect(game.id, viewEpoch, list) {
        snapshotFlow {
            val layout = list.layoutInfo
            presentedChatIds(controller.state.chat.messages, layout.visibleItemsInfo.filter {
                it.offset < layout.viewportEndOffset && it.offset + it.size > layout.viewportStartOffset
            }.map { it.key }, controller.state.chatOpen)
        }.collect { controller.presentedChat(game.id, viewEpoch, it) }
    }
    Column(modifier.fillMaxHeight().background(MaterialTheme.colorScheme.surface).imePadding()
        .semantics { paneTitle = "Game chat" }) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Chat", Modifier.weight(1f).padding(12.dp), style = MaterialTheme.typography.titleLarge)
            IconButton(onClick = controller::closeChat) { EntryIcon("Close chat", "close") }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp), state = list,
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(chat.messages, key = { it.id }) { message ->
                Column(Modifier.fillMaxWidth()) {
                    Text(if (message.author == accountId) "You" else game.opponent(accountId).username,
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text(message.text)
                }
            }
            // These are retained original requests, never accepted-message predictions.
            items(chat.outgoing, key = { "pending-${it.command.requestId}" }) { submission ->
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(8.dp)) {
                        Text((submission.command.action as GameAction.SendChat).text)
                        Text(submission.failure ?: "Checking delivery…", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        if (game.id !in state.connectedGames) Text("Disconnected · draft kept", Modifier.padding(horizontal = 12.dp))
        state.connectionMessage?.let { Text(it, Modifier.padding(horizontal = 12.dp), style = MaterialTheme.typography.bodySmall) }
        if (game.id !in state.connectedGames) TextButton(onClick = controller::reconnect) { Text("Reconnect") }
        if (chat.draft != null && chat.draft.playId != game.playId) {
            Text("This draft belongs to an earlier play.", Modifier.padding(horizontal = 12.dp))
            TextButton(onClick = { controller.composeChat("") }) { Text("Clear draft") }
        }
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(chat.draft?.text.orEmpty(), controller::composeChat, Modifier.weight(1f)
                .semantics { contentDescription = "Chat message draft" }, label = { Text("Message") }, maxLines = 4)
            TextButton(onClick = controller::sendChat, enabled = chat.canSend(game, game.id in state.connectedGames)) { Text("Send") }
        }
    }
}
