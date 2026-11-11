package org.asyncttwiz.android

import androidx.compose.ui.text.input.ImeAction
import org.asyncttwiz.appui.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import org.asyncttwiz.protocol.*

/** Only intersecting laid-out rows count as presented; lazy prefetch is not viewing. */
fun presentedChatIds(messages: List<ChatMessage>, visibleKeys: List<Any>, panelVisible: Boolean): Set<String> =
    if (!panelVisible) emptySet() else visibleKeys.filterIsInstance<String>().filter { id ->
        if (messages is IndexedChatMessages) messages.byId(id) != null else messages.any { it.id == id }
    }.toSet()

private class ChatScrollAnchor(var latestKey: String? = null)

/** LOB-139: how long Write's focus waits for the chat's window to take the focus before asking anyway. */
private const val WRITE_FOCUS_WAIT_MS = 500L

/** The chat panel's tabs in a pod game (LOB-145). */
enum class ChatTab { GAME, POD }

/** LOB-145: which tab the table's chat opens on, which tab is highlighted, and the chat button's count. */
object ChatTabs {
    /** The tab last used in this game ([last], owner 2026-11-10); before any, Game, or Pod when only the pod's chat holds
     * unread messages ([pod]: the game has a pod chat to show). */
    fun opening(pod: Boolean, gameUnread: Int, podUnread: Int, last: ChatTab? = null): ChatTab = when {
        !pod -> ChatTab.GAME
        last != null -> last
        gameUnread == 0 && podUnread > 0 -> ChatTab.POD
        else -> ChatTab.GAME
    }
    /** [tab] holds a message the viewer hasn't seen while [selected] is shown. */
    fun highlighted(tab: ChatTab, selected: ChatTab, gameUnread: Int, podUnread: Int): Boolean =
        tab != selected && (if (tab == ChatTab.GAME) gameUnread else podUnread) > 0
    /** LOB-066: the table's chat button counts the game's unread messages and the pod's. */
    fun buttonUnread(gameUnread: Int, podUnread: Int): Int = gameUnread + podUnread
}

/** The pod chat of the open pod screens and pod games (null in previews and tests without one). */
val LocalPodChat = staticCompositionLocalOf<PodChatController?> { null }

/** Reads [podId]'s chat again every few seconds while composed and the app is in front, for [where] (the pod screen, a pod
 * game's table): every 3 s while it is [open], else every 10 s ([PodChatController.watch]: on the main thread's clock). */
@Composable
fun PodChatRefresh(chat: PodChatController, podId: String, open: Boolean, where: String) {
    WhileInFront(chat, podId, open) { chat.watch(podId, if (open) 3_000 else 10_000, if (open) "$where, chat open" else where) }
}

@Composable
fun ChatPanel(controller: LobbyController, accountId: String, modifier: Modifier = Modifier,
    contentInsets: WindowInsets = WindowInsets.safeDrawing) {
    val state = controller.state
    val game = state.detail ?: return
    // LOB-145: a pod game's chat has the pod's beside it, while the viewer is still in that pod
    val podChat = LocalPodChat.current
    val podView = podChat?.let { chat -> game.podId?.let { id -> chat.views[id] ?: PodChatView(id) } }?.takeIf { !it.unavailable }
    val tab = if (podView == null) ChatTab.GAME else state.chatTab
    val chat = state.chat
    val deliveryFailure = chat.outgoing.lastOrNull { it.failure != null }?.failure
    var deliveryDismissed by remember(deliveryFailure) { mutableStateOf(false) }
    val staleDraft = chat.draft != null && chat.draft.playId != game.playId
    var staleDismissed by remember(chat.draft?.playId, game.playId) { mutableStateOf(false) }
    BackHandler { controller.closeChat() }
    ScreenTheme {
    // safeDrawing includes IME, system bars and cutouts. In the edge-to-edge chat dialog
    // this shrinks the bounded panel, rather than panning the window or scrolling the composer.
    Box(modifier.fillMaxHeight().windowInsetsPadding(contentInsets), contentAlignment = Alignment.CenterEnd) {
    Surface(Modifier.widthIn(max = 356.dp).fillMaxWidth().fillMaxHeight().padding(vertical = 8.dp),
        shape = MaterialTheme.shapes.medium, border = BorderStroke(1.dp, ScreenBorder)) {
    Column(Modifier.fillMaxSize()
        .semantics { paneTitle = "Game chat" }) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (podView == null) Text("Game chat", Modifier.weight(1f).padding(12.dp), style = MaterialTheme.typography.titleSmall)
            else {
                val podUnread = podView.unread(accountId)
                ChatTabRow(tab, state.chat.unread, podUnread, controller::chatTab, Modifier.weight(1f))
            }
            IconButton(onClick = controller::closeChat) { EntryIcon("Close chat", "close") }
        }
        if (tab == ChatTab.GAME || podView == null || podChat == null) GameChatBody(controller, game, accountId, staleDraft, Modifier.weight(1f))
        else PodChatBody(podChat, podView, accountId,
            if (game.inviter.accountId == accountId) game.inviter.username else game.recipient.username, state.chatOpen, Modifier.weight(1f))
    }
    }
    val gameTab = tab == ChatTab.GAME
    FloatingTransientError(state.connectionMessage ?: deliveryFailure?.takeUnless { deliveryDismissed || !gameTab }
        ?: "This draft belongs to an earlier play. Clear it to write a new message.".takeIf { staleDraft && !staleDismissed && gameTab },
        {
            if (state.connectionMessage != null) controller.dismissSessionError()
            else if (deliveryFailure != null && !deliveryDismissed) deliveryDismissed = true
            else staleDismissed = true
        },
        Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp))
    }
    }
}

/** LOB-145: Game and Pod; the tab that isn't shown has a dot after its name while it holds a message you haven't seen. */
@Composable
private fun ChatTabRow(selected: ChatTab, gameUnread: Int, podUnread: Int, select: (ChatTab) -> Unit, modifier: Modifier) {
    TabRow(selected.ordinal, modifier.testTag("chat-tabs"), containerColor = Color.Transparent, divider = {}) {
        ChatTab.entries.forEach { tab ->
            val highlighted = ChatTabs.highlighted(tab, selected, gameUnread, podUnread)
            val name = if (tab == ChatTab.GAME) "Game" else "Pod"
            Tab(tab == selected, { select(tab) }, Modifier.testTag("chat-tab-${tab.name.lowercase()}")
                .semantics { stateDescription = if (highlighted) "New messages" else "" }, text = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(name, fontWeight = if (highlighted) FontWeight.Bold else null)
                    if (highlighted) Box(Modifier.size(6.dp).background(OpponentTableColor, CircleShape))
                }
            })
        }
    }
}

/** The game's own chat: its messages (with "Read" marks, LOB-138), outgoing sends and the composer. */
@Composable
private fun GameChatBody(controller: LobbyController, game: GameSummary, accountId: String, staleDraft: Boolean, modifier: Modifier) {
    val state = controller.state
    val chat = state.chat
    val list = rememberLazyListState()
    val latestKey = chat.outgoing.lastOrNull()?.command?.requestId ?: chat.messages.lastOrNull()?.id
    FollowLatest(list, game.id, latestKey)
    // LOB-136: opened by holding a bubble: at that message
    val focus = state.chatFocus
    LaunchedEffect(game.id, focus) {
        val at = focus?.let { id -> chat.messages.indexOfFirst { it.id == id } }?.takeIf { it >= 0 } ?: return@LaunchedEffect
        list.scrollToItem(chat.outgoing.size + chat.messages.lastIndex - at)
    }
    // LOB-139: opened by Write: ready to type. The chat's window takes the focus a moment after it opens, and only a focused
    // window gets the keyboard
    val input = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val window = LocalWindowInfo.current
    LaunchedEffect(game.id, state.chatWrite) {
        if (!state.chatWrite) return@LaunchedEffect
        withTimeoutOrNull(WRITE_FOCUS_WAIT_MS) { snapshotFlow { window.isWindowFocused }.first { it } }
        input.requestFocus()
        keyboard?.show()
    }
    val viewEpoch = controller.chatViewEpoch
    LaunchedEffect(game.id, viewEpoch, list) {
        snapshotFlow {
            presentedChatIds(controller.state.chat.messages, list.visibleKeys(), controller.state.chatOpen)
        }.collect { controller.presentedChat(game.id, viewEpoch, it) }
    }
    Column(modifier) {
        // Index zero is the newest row: opening and viewport/IME resizing stay at the
        // bottom, while stable keys preserve the reader's anchor when scrolled up.
        // Index from the end directly; never reverse/copy the accumulated transcript.
        InitialLoadArea(state.chatLoaded, Modifier.weight(1f).fillMaxWidth()) {
        ChatTranscript(list, "Chat messages") {
            // These are retained original requests, never accepted-message predictions.
            // Use the server's eventual message ID so acceptance preserves the same row anchor.
            items(chat.outgoing.size, key = { chat.outgoing[chat.outgoing.lastIndex - it].command.requestId }) { index ->
                val submission = chat.outgoing[chat.outgoing.lastIndex - index]
                ChatMessageRow(if (game.inviter.accountId == accountId) game.inviter.username else game.recipient.username,
                    (submission.command.action as GameAction.SendChat).text, true,
                    if (submission.failure != null) "Not delivered" else "Checking delivery…")
            }
            items(chat.messages.size, key = { chat.messages[chat.messages.lastIndex - it].id }) { index ->
                val message = chat.messages[chat.messages.lastIndex - index]
                // LOB-138: your message the other player has read says so
                ChatMessageRow(if (message.author == game.inviter.accountId) game.inviter.username else game.recipient.username,
                    message.text, message.author == accountId, if (message.author == accountId && chat.read(message)) "Read" else "")
            }
        }
        }
        val maySend = chat.canSend(game, game.id !in state.reconnectingGames)
        ChatComposer(chat.draft?.text.orEmpty(), controller::composeChat, {
            controller.sendChat()
            list.requestScrollToItem(0)
        }, maySend, staleDraft, { controller.composeChat("") }, Modifier.focusRequester(input))
    }
}

/** DRF-170, LOB-145: a pod's chat: its messages with their authors' names, sends on their way and the composer; no "Read" marks. */
@Composable
fun PodChatBody(chat: PodChatController, view: PodChatView, accountId: String, ownName: String, visible: Boolean,
    modifier: Modifier = Modifier) {
    val podId = view.podId
    val list = rememberLazyListState()
    FollowLatest(list, podId, view.outgoing.lastOrNull()?.requestId ?: view.messages.lastOrNull()?.id)
    val current by rememberUpdatedState(view)
    LaunchedEffect(podId, list, visible) {
        if (!visible) return@LaunchedEffect
        snapshotFlow {
            val keys = list.visibleKeys().filterIsInstance<String>().toSet()
            current.messages.filter { it.id in keys }.map { it.id }.toSet()
        }.collect { chat.presented(podId, it) }
    }
    fun name(account: String) = view.authors[account]?.username ?: "Member"
    Column(modifier.semantics { paneTitle = "Pod chat" }) {
        InitialLoadArea(view.loaded, Modifier.weight(1f).fillMaxWidth()) {
            ChatTranscript(list, "Pod chat messages") {
                items(view.outgoing.size, key = { view.outgoing[view.outgoing.lastIndex - it].requestId }) { index ->
                    val sending = view.outgoing[view.outgoing.lastIndex - index]
                    ChatMessageRow(ownName, sending.text, true,
                        if (sending.failed) "Not delivered" else "Checking delivery…")
                }
                items(view.messages.size, key = { view.messages[view.messages.lastIndex - it].id }) { index ->
                    val message = view.messages[view.messages.lastIndex - index]
                    ChatMessageRow(name(message.author), message.text, message.author == accountId, "")
                }
            }
        }
        ChatComposer(view.draft, { chat.compose(podId, it) }, {
            chat.send(podId)
            list.requestScrollToItem(0)
        }, view.canSend(), stale = false, clear = {}, description = "Pod chat message draft")
    }
}

/** DRF-170: the pod screens' "Pod chat" side panel. */
@Composable
fun PodChatPanel(chat: PodChatController, podId: String, accountId: String, ownName: String, close: () -> Unit,
    modifier: Modifier = Modifier, contentInsets: WindowInsets = WindowInsets.safeDrawing) {
    val view = chat.views[podId] ?: PodChatView(podId)
    BackHandler { close() }
    ScreenTheme {
    Box(modifier.fillMaxHeight().windowInsetsPadding(contentInsets), contentAlignment = Alignment.CenterEnd) {
    Surface(Modifier.widthIn(max = 356.dp).fillMaxWidth().fillMaxHeight().padding(vertical = 8.dp),
        shape = MaterialTheme.shapes.medium, border = BorderStroke(1.dp, ScreenBorder)) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Pod chat", Modifier.weight(1f).padding(12.dp), style = MaterialTheme.typography.titleSmall)
                IconButton(onClick = close) { EntryIcon("Close chat", "close") }
            }
            if (view.unavailable) Text("This pod's chat is no longer yours to read.", Modifier.padding(12.dp))
            else PodChatBody(chat, view, accountId, ownName, visible = true, Modifier.weight(1f))
        }
    }
    }
    }
}

/** The keys of the rows laid out in [this] list's viewport (lazy prefetch is not viewing). */
private fun LazyListState.visibleKeys(): List<Any> {
    val layout = layoutInfo
    return layout.visibleItemsInfo.filter {
        it.offset < layout.viewportEndOffset && it.offset + it.size > layout.viewportStartOffset
    }.map { it.key }
}

/** A transcript that was at its newest row stays there when a newer one ([latestKey]) arrives. */
@Composable
private fun FollowLatest(list: LazyListState, owner: String, latestKey: String?) {
    val anchor = remember(owner) { ChatScrollAnchor() }
    // Read before the new item provider is measured: key anchoring would otherwise
    // keep the previous newest row, even when the reader was exactly at the bottom.
    val atLatest = list.firstVisibleItemIndex == 0 && list.firstVisibleItemScrollOffset == 0
    SideEffect {
        if (anchor.latestKey != latestKey) {
            anchor.latestKey = latestKey
            if (atLatest) list.requestScrollToItem(0)
        }
    }
}

/** A chat's message list, newest at the bottom (index zero), its rows given by [content]. */
@Composable
private fun ChatTranscript(list: LazyListState, description: String, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)
        .semantics { contentDescription = description }, state = list, reverseLayout = true,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Bottom), content = content)
}

/** The message field and Send; the keyboard's Send key sends like the button, a [stale] draft only closes the keyboard and has Clear. */
@Composable
private fun ChatComposer(draft: String, compose: (String) -> Unit, send: () -> Unit, maySend: Boolean, stale: Boolean,
    clear: () -> Unit, inputModifier: Modifier = Modifier, description: String = "Chat message draft") {
    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ScreenTextField(draft, compose, Modifier.weight(1f).then(inputModifier)
            .semantics { contentDescription = description }, singleLine = false, placeholder = "Message", maxLines = 4,
            keyboardOptions = imeOptions(ImeAction.Send),
            keyboardActions = submitKeyboardActions(!stale && maySend, keepKeyboard = true, submit = send))
        if (stale) ScreenIconButton("Clear draft", "delete", clear)
        else ScreenIconButton("Send", "send", send, enabled = maySend)
    }
}

@Composable
private fun ChatMessageRow(author: String, text: String, own: Boolean, status: String) {
    Surface(Modifier.fillMaxWidth(), color = if (own) MaterialTheme.colorScheme.primaryContainer else ScreenBackground,
        shape = MaterialTheme.shapes.small) {
        Column(Modifier.padding(10.dp)) {
            Text(author, style = MaterialTheme.typography.labelMedium, maxLines = 1)
            Text(text)
            Box(Modifier.fillMaxWidth().height(16.dp)) {
                if (status.isNotEmpty()) Text(status, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
        }
    }
}
