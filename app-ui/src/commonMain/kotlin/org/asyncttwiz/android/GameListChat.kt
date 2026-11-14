package org.asyncttwiz.android

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.asyncttwiz.appui.BackHandler
import org.asyncttwiz.appui.ScreenOrientation
import org.asyncttwiz.appui.edgeToEdgeDialogProperties
import org.asyncttwiz.appui.screenOrientation

/** LOB-148: the game row menu's chat button: "Chat", or "Chat · 2 unread" while the other player's messages wait. */
fun gameRowChatLabel(unread: Int): String = if (unread > 0) "Chat · $unread unread" else "Chat"

/**
 * LOB-148: a game's chat opened from the games list (its row's menu): the table's Game chat (LOB-067, LOB-068, LOB-138) as a
 * side panel over the list, its messages read as they show, without opening the game. Closed by its ✕ or Back.
 */
@Composable
fun GameListChat(controller: LobbyController, accountId: String) {
    val state = controller.state
    val game = state.listChat?.takeIf { state.chatOpen && state.selected == null }
        ?.let { id -> state.games.firstOrNull { it.id == id } } ?: return
    androidx.compose.ui.window.Dialog(onDismissRequest = controller::closeChat, properties = edgeToEdgeDialogProperties()) {
        val landscape = screenOrientation() == ScreenOrientation.LANDSCAPE
        BackHandler { controller.closeChat() }
        ScreenTheme {
            Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing), contentAlignment = Alignment.CenterEnd) {
                Surface(Modifier.widthIn(max = 356.dp).fillMaxWidth(if (landscape) .5f else 1f).fillMaxHeight().padding(vertical = 8.dp),
                    shape = MaterialTheme.shapes.medium, border = BorderStroke(1.dp, ScreenBorder)) {
                    Column(Modifier.fillMaxSize().semantics { paneTitle = "Game chat" }) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f).padding(12.dp)) {
                                Text("Game chat", style = MaterialTheme.typography.titleSmall)
                                Text("${game.gameName} · vs ${game.opponent(accountId).username}", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            IconButton(onClick = controller::closeChat) { EntryIcon("Close chat", "close") }
                        }
                        val staleDraft = state.chat.draft != null && state.chat.draft.playId != game.playId
                        GameChatBody(controller, game, accountId, staleDraft, Modifier.weight(1f), visible = true)
                    }
                }
                FloatingTransientError(state.connectionMessage, controller::dismissSessionError,
                    Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp))
            }
        }
    }
}
