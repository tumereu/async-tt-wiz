package org.asyncttwiz.android

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
    if (!panelVisible) emptySet() else visibleKeys.filterIsInstance<String>().filter { id ->
        if (messages is IndexedChatMessages) messages.byId(id) != null else messages.any { it.id == id }
    }.toSet()

private class ChatScrollAnchor(var latestKey: String? = null)

@Composable
internal fun ChatPanel(controller: LobbyController, accountId: String, modifier: Modifier = Modifier,
    contentInsets: WindowInsets = WindowInsets.safeDrawing) {
    val state = controller.state
    val game = state.detail ?: return
    val chat = state.chat
    val list = rememberLazyListState()
    val anchor = remember(game.id) { ChatScrollAnchor() }
    val latestKey = chat.outgoing.lastOrNull()?.command?.requestId ?: chat.messages.lastOrNull()?.id
    // Read before the new item provider is measured: key anchoring would otherwise
    // keep the previous newest row, even when the reader was exactly at the bottom.
    val atLatest = list.firstVisibleItemIndex == 0 && list.firstVisibleItemScrollOffset == 0
    SideEffect {
        if (anchor.latestKey != latestKey) {
            anchor.latestKey = latestKey
            if (atLatest) list.requestScrollToItem(0)
        }
    }
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
    ScreenTheme {
    // safeDrawing includes IME, system bars and cutouts. In the edge-to-edge chat dialog
    // this shrinks the bounded panel, rather than panning the window or scrolling the composer.
    Box(modifier.fillMaxHeight().windowInsetsPadding(contentInsets), contentAlignment = Alignment.CenterEnd) {
    Surface(Modifier.widthIn(max = 356.dp).fillMaxWidth().fillMaxHeight().padding(vertical = 8.dp),
        shape = MaterialTheme.shapes.medium, border = BorderStroke(1.dp, ScreenBorder)) {
    Column(Modifier.fillMaxSize()
        .semantics { paneTitle = "Game chat" }) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Game chat", Modifier.weight(1f).padding(12.dp), style = MaterialTheme.typography.titleSmall)
            IconButton(onClick = controller::closeChat) { EntryIcon("Close chat", "close") }
        }
        // Index zero is the newest row: opening and viewport/IME resizing stay at the
        // bottom, while stable keys preserve the reader's anchor when scrolled up.
        // Index from the end directly; never reverse/copy the accumulated transcript.
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp)
            .semantics { contentDescription = "Chat messages" }, state = list, reverseLayout = true,
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Bottom)) {
            // These are retained original requests, never accepted-message predictions.
            // Use the server's eventual message ID so acceptance preserves the same row anchor.
            items(chat.outgoing.size, key = { chat.outgoing[chat.outgoing.lastIndex - it].command.requestId }) { index ->
                val submission = chat.outgoing[chat.outgoing.lastIndex - index]
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(8.dp)) {
                        Text((submission.command.action as GameAction.SendChat).text)
                        Text(submission.failure ?: "Checking delivery…", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            items(chat.messages.size, key = { chat.messages[chat.messages.lastIndex - it].id }) { index ->
                val message = chat.messages[chat.messages.lastIndex - index]
                Surface(Modifier.fillMaxWidth(), color = if (message.author == accountId) MaterialTheme.colorScheme.primaryContainer else ScreenBackground,
                    shape = MaterialTheme.shapes.small) {
                Column(Modifier.padding(10.dp)) {
                    Text(if (message.author == game.inviter.accountId) game.inviter.username else game.recipient.username,
                        style = MaterialTheme.typography.labelMedium)
                    Text(message.text)
                }
                }
            }
        }
        if (chat.draft != null && chat.draft.playId != game.playId) {
            Text("This draft belongs to an earlier play.", Modifier.padding(horizontal = 12.dp))
            TextButton(onClick = { controller.composeChat("") }) { Text("Clear draft") }
        }
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ScreenTextField(chat.draft?.text.orEmpty(), controller::composeChat, Modifier.weight(1f)
                .semantics { contentDescription = "Chat message draft" }, singleLine = false, placeholder = "Message", maxLines = 4)
            ScreenIconButton("Send", "send", {
                controller.sendChat()
                list.requestScrollToItem(0)
            }, enabled = chat.canSend(game, game.id !in state.reconnectingGames))
        }
    }
    }
    FloatingTransientError(state.connectionMessage, controller::dismissSessionError,
        Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp))
    }
    }
}
