package org.asyncttwiz.android

import org.asyncttwiz.protocol.*

data class ChatDraft(val playId: String, val text: String)
data class ChatSubmission(val command: GameCommand, val failure: String? = null)
data class ChatPresentation(val messages: List<ChatMessage> = emptyList(), val outgoing: List<ChatSubmission> = emptyList(),
    val draft: ChatDraft? = null, val unread: Int = 0,
    /** LOB-138: the other player's read cursor: the viewer's messages up to it are read. */
    val peerReadThrough: Long = 0,
    /** LOB-136: the other player's oldest unread message as a bubble on the table; null when none is unread. */
    val bubble: IncomingChatBubble? = null,
    /** LOB-137: the message last sent from this device, shown briefly by the viewer's own life square. */
    val sent: SentChat? = null) {
    fun canSend(game: GameSummary, connected: Boolean) = connected && game.lifecycle == GameLifecycle.ACTIVE &&
        draft != null && draft.playId == game.playId && ChatValidation.text(draft.text)
    /** LOB-139: a quick message can go whenever a typed one could, whatever the draft holds. */
    fun canSendQuick(game: GameSummary, connected: Boolean) = connected && game.lifecycle == GameLifecycle.ACTIVE && game.playId != null
    /** LOB-138: whether the other player has read [message]. */
    fun read(message: ChatMessage) = GameWire.ordinal(message.revision) <= peerReadThrough
}

/** Drafts are local. Private unread state comes from the server, with a provisional cursor. */
class GameChat {
    private val drafts = mutableMapOf<String, ChatDraft>()
    private val accepted = mutableMapOf<String, ChatReadState>()
    private val presented = mutableMapOf<String, ChatMessage>()
    /** LOB-136: per game, the read cursor when the current stretch of unread messages began (personal UI). */
    private val stretches = mutableMapOf<String, Long>()
    private val sentChats = mutableMapOf<String, SentChat>()
    private var sentKey = 0L
    var inspectedMessages = 0; private set
    fun compose(game: GameSummary, text: String) {
        if (text.isEmpty()) drafts.remove(game.id)
        else game.playId?.let { drafts[game.id] = ChatDraft(drafts[game.id]?.playId ?: it, text) }
    }
    /** A typed message ([fromDraft]) or a quick one ([text]) was sent in [gameId]. */
    fun sent(gameId: String, text: String, fromDraft: Boolean = true) {
        if (fromDraft) drafts.remove(gameId)
        sentChats[gameId] = SentChat(++sentKey, text)
    }
    /** LOB-137: the bubble of the sent message [key] has faded. */
    fun sentShown(gameId: String, key: Long) { if (sentChats[gameId]?.key == key) sentChats.remove(gameId) }
    fun presented(messages: List<ChatMessage>, visibleIds: Set<String>) {
        for (id in visibleIds) {
            val message = if (messages is IndexedChatMessages) messages.byId(id) else messages.firstOrNull { it.id == id }
            inspectedMessages++
            if (message != null && GameWire.ordinal(message.revision) > cursor(message.gameId)) presented[message.gameId] = message
        }
    }
    private fun cursor(gameId: String) = maxOf(accepted[gameId]?.readThrough?.let(GameWire::ordinal) ?: 0,
        presented[gameId]?.revision?.let(GameWire::ordinal) ?: 0)
    fun pendingRead(gameId: String): ReadChat? = presented[gameId]?.takeIf {
        GameWire.ordinal(it.revision) > (accepted[gameId]?.readThrough?.let(GameWire::ordinal) ?: 0)
    }?.let { ReadChat(it.id) }
    fun accept(state: ChatReadState) {
        val old = accepted[state.gameId]
        if (old != null && (GameWire.ordinal(state.readThrough) < GameWire.ordinal(old.readThrough) ||
            GameWire.ordinal(state.throughRevision) < GameWire.ordinal(old.throughRevision))) return
        accepted[state.gameId] = state
        if (pendingRead(state.gameId) == null) presented.remove(state.gameId)
    }
    fun presentation(gameId: String, account: String?, messages: List<ChatMessage>, outgoing: List<ChatSubmission>): ChatPresentation {
        fun after(revision: Long) = if (messages is IndexedChatMessages) messages.incomingAfter(revision, account)
            else messages.count { it.author != account && GameWire.ordinal(it.revision) > revision }
        val cursor = cursor(gameId)
        val state = accepted[gameId]
        // LOB-136: a stretch of unread messages starts at the cursor it found and ends once nothing is unread; none before
        // the server's read state is known (every loaded message would look unread)
        val bubble = if (state == null) null
            else ChatBubbleRules.incoming(messages, account, cursor, stretches.getOrPut(gameId) { cursor })
        if (bubble == null) stretches.remove(gameId)
        val count = if (state == null || cursor >= GameWire.ordinal(state.throughRevision)) after(cursor) else {
            // The server includes unloaded history. Only subtract locally presented messages;
            // the acknowledgement supplies the exact count if the cursor crosses unloaded rows.
            state.unread - (after(GameWire.ordinal(state.readThrough)) - after(cursor)) + after(GameWire.ordinal(state.throughRevision))
        }
        return ChatPresentation(messages, outgoing, drafts[gameId], count.coerceAtLeast(0),
            state?.peerReadThrough?.let(GameWire::ordinal) ?: 0, bubble, sentChats[gameId])
    }
    fun remove(gameId: String) { accepted.remove(gameId); presented.remove(gameId); stretches.remove(gameId); sentChats.remove(gameId) }
    fun clear() { drafts.clear(); accepted.clear(); presented.clear(); stretches.clear(); sentChats.clear() }
}
