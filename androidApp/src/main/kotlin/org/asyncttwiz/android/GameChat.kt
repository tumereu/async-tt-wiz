package org.asyncttwiz.android

import org.asyncttwiz.protocol.*

data class ChatDraft(val playId: String, val text: String)
data class ChatSubmission(val command: GameCommand, val failure: String? = null)
data class ChatPresentation(val messages: List<ChatMessage> = emptyList(), val outgoing: List<ChatSubmission> = emptyList(),
    val draft: ChatDraft? = null, val unread: Int = 0) {
    fun canSend(game: GameSummary, connected: Boolean) = connected && game.lifecycle == GameLifecycle.ACTIVE &&
        draft != null && draft.playId == game.playId && ChatValidation.text(draft.text)
}

/** Personal, process-memory UI state only. Opening a panel does not mark any message viewed. */
class GameChat {
    private val drafts = mutableMapOf<String, ChatDraft>()
    private val viewed = mutableSetOf<String>()
    fun compose(game: GameSummary, text: String) {
        if (text.isEmpty()) drafts.remove(game.id)
        else game.playId?.let { drafts[game.id] = ChatDraft(drafts[game.id]?.playId ?: it, text) }
    }
    fun sent(gameId: String) { drafts.remove(gameId) }
    fun presented(messages: List<ChatMessage>, visibleIds: Set<String>) {
        viewed.addAll(messages.filter { it.id in visibleIds }.map { it.id })
    }
    fun presentation(gameId: String, account: String?, messages: List<ChatMessage>, outgoing: List<ChatSubmission>) =
        ChatPresentation(messages, outgoing, drafts[gameId], messages.count { it.author != account && it.id !in viewed })
    fun clear() { drafts.clear(); viewed.clear() }
}
