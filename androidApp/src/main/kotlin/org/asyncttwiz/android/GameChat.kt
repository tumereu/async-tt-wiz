package org.asyncttwiz.android

import org.asyncttwiz.protocol.*

data class ChatDraft(val playId: String, val text: String)
data class ChatSubmission(val command: GameCommand, val failure: String? = null)
data class ChatPresentation(val messages: List<ChatMessage> = emptyList(), val outgoing: List<ChatSubmission> = emptyList(),
    val draft: ChatDraft? = null, val unread: Int = 0) {
    fun canSend(game: GameSummary, connected: Boolean) = connected && game.lifecycle == GameLifecycle.ACTIVE &&
        draft != null && draft.playId == game.playId && ChatValidation.text(draft.text)
}

/** Drafts are local. Private unread state comes from the server, with a provisional cursor. */
class GameChat {
    private val drafts = mutableMapOf<String, ChatDraft>()
    private val accepted = mutableMapOf<String, ChatReadState>()
    private val presented = mutableMapOf<String, ChatMessage>()
    var inspectedMessages = 0; private set
    fun compose(game: GameSummary, text: String) {
        if (text.isEmpty()) drafts.remove(game.id)
        else game.playId?.let { drafts[game.id] = ChatDraft(drafts[game.id]?.playId ?: it, text) }
    }
    fun sent(gameId: String) { drafts.remove(gameId) }
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
        val count = if (state == null || cursor >= GameWire.ordinal(state.throughRevision)) after(cursor) else {
            // The server includes unloaded history. Only subtract locally presented messages;
            // the acknowledgement supplies the exact count if the cursor crosses unloaded rows.
            state.unread - (after(GameWire.ordinal(state.readThrough)) - after(cursor)) + after(GameWire.ordinal(state.throughRevision))
        }
        return ChatPresentation(messages, outgoing, drafts[gameId], count.coerceAtLeast(0))
    }
    fun remove(gameId: String) { accepted.remove(gameId); presented.remove(gameId) }
    fun clear() { drafts.clear(); accepted.clear(); presented.clear() }
}
