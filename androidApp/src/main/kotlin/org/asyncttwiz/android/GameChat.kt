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
    private data class Unread(var messages: IndexedChatMessages, val account: String?, var count: Int)
    private val unread = mutableMapOf<String, Unread>()
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
            if (message != null && viewed.add(id)) unread[message.gameId]?.let { if (message.author != it.account) it.count-- }
        }
    }
    fun presentation(gameId: String, account: String?, messages: List<ChatMessage>, outgoing: List<ChatSubmission>): ChatPresentation {
        if (messages !is IndexedChatMessages) return ChatPresentation(messages, outgoing, drafts[gameId], messages.count { it.author != account && it.id !in viewed })
        val old = unread[gameId]?.takeIf { it.account == account }
        val next = if (old?.messages === messages) old else {
            val changes = old?.let { messages.changedSince(it.messages) }
            if (old != null && changes != null) {
                for (id in changes) {
                    inspectedMessages++
                    val before = old.messages.byId(id); val after = messages.byId(id)
                    if (before != null && before.author != account && id !in viewed) old.count--
                    if (after != null && after.author != account && id !in viewed) old.count++
                }
                old.messages = messages; old
            } else Unread(messages, account, messages.size - messages.authoredBy(account) - viewed.count { id ->
                inspectedMessages++; messages.byId(id)?.author?.let { it != account } == true })
        }
        unread[gameId] = next
        return ChatPresentation(messages, outgoing, drafts[gameId], next.count)
    }
    fun clear() { drafts.clear(); viewed.clear(); unread.clear() }
}
