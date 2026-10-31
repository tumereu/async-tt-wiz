package org.asyncttwiz.android

import org.asyncttwiz.protocol.*

/** GameSession's per-game chat: accepted messages (head snapshot or paged catch-up, then live
 * deliveries), older pages, outgoing submissions and syncing the personal read cursor. Main thread. */
internal class SessionChat(private val scope: SessionScope, private val connection: GameConnection,
    private val personalChat: GameChat, private val now: () -> Long, private val openedGame: () -> String?,
    /** Session is ready, the game is available and it is watched (has a stream). */
    private val syncable: (String) -> Boolean, private val available: (String) -> Boolean) {
    private val messages = mutableMapOf<String, List<ChatMessage>>()
    private val chatRevisions = mutableMapOf<String, String>()
    private val chatCursors = mutableMapOf<String, String>()
    private val olderChat = mutableMapOf<String, String>()
    private val chatReads = mutableSetOf<String>()
    private val chatReadRequests = mutableSetOf<String>()
    private val chatReadRefresh = mutableMapOf<String, Long>()
    private val chatReadRetry = mutableMapOf<String, Long>()
    private val chatSubmissions = linkedMapOf<String, ChatSubmission>()
    fun chat(gameId: String) = messages[gameId].orEmpty()
    fun chatLoaded(gameId: String) = gameId in chatRevisions
    fun presentedChat(gameId: String, ids: Set<String>) {
        personalChat.presented(chat(gameId), ids)
        syncChatRead(gameId)
    }
    /** At most one request per game; newer presentations replace queued intent. Failures remain
     * pending for the next tick/reconnect. Polling also observes reads on another device. */
    fun syncChatRead(gameId: String) {
        if (!syncable(gameId) || gameId in chatReadRequests) return
        val input = personalChat.pendingRead(gameId)
        if (gameId != openedGame() && input == null) return
        if (now() < (chatReadRetry[gameId] ?: 0)) return
        if (input == null && now() < (chatReadRefresh[gameId] ?: 0)) return
        chatReadRequests.add(gameId)
        val generation = scope.epoch; val owner = scope.account
        val started = connection.chatRead(gameId, input) { state -> scope.post {
            if (scope.closed || generation != scope.epoch || owner != scope.account || !scope.sameAccount()) return@post
            chatReadRequests.remove(gameId)
            if (state == null) chatReadRetry[gameId] = now() + 1_000 else {
                chatReadRetry.remove(gameId)
                chatReadRefresh[gameId] = now() + 10_000
            }
            if (state != null && available(gameId)) {
                personalChat.accept(state); scope.statusChanged()
                // Coalesce all visibility callbacks during the request into its latest cursor.
                if (personalChat.pendingRead(gameId) != null) syncChatRead(gameId)
            }
        } }
        if (!started) { chatReadRequests.remove(gameId); chatReadRetry[gameId] = now() + 1_000 }
    }
    fun outgoingChat(gameId: String) = chatSubmissions.values.filter { it.command.scope.gameId == gameId }
    fun submitted(command: GameCommand) { chatSubmissions[command.requestId] = ChatSubmission(command) }
    fun failed(requestId: String, failure: String) {
        chatSubmissions[requestId]?.let { chatSubmissions[requestId] = it.copy(failure = failure) }
    }
    fun installChat(gameId: String, revision: String, incoming: List<ChatMessage>, snapshot: Boolean) {
        if (!available(gameId)) return
        require(incoming.all { it.gameId == gameId && GameWire.ordinal(it.revision) <= GameWire.ordinal(revision) })
        val oldRevision = chatRevisions[gameId]
        if (snapshot && oldRevision != null && !GameSession.newerOrEqual(revision, oldRevision)) return
        val retained = if (snapshot) IndexedChatMessages() else (messages[gameId] as? IndexedChatMessages) ?: IndexedChatMessages()
        messages[gameId] = retained.upsert(incoming)
        if (oldRevision == null || GameSession.newerOrEqual(revision, oldRevision)) chatRevisions[gameId] = revision
        incoming.forEach { chatSubmissions.remove(it.id) }
    }
    fun installChatHead(head: GameHead) {
        if (!GameSession.ended(head.summary)) syncChatRead(head.gameId)
        val sync = head.chatSync
        if (sync == null) { installChat(head.gameId, head.revision, head.chat, snapshot = true); return }
        if (!GameSession.ended(head.summary)) fetchChat(head.gameId, sync)
    }

    /** Bounded initial window, independent revision catch-up. Heads never replace chat with an
     * empty snapshot. Live deliveries may race pages, so merge by immutable message identity. */
    private fun fetchChat(gameId: String, sync: ChatSync) {
        val cursor = chatCursors[gameId]
        if (cursor != null && GameWire.ordinal(cursor) >= GameWire.ordinal(sync.revision)) return
        if (!chatReads.add(gameId)) return
        val generation = scope.epoch; val owner = scope.account
        if (!connection.chatPage(gameId, after = cursor) { page -> scope.post {
            if (scope.closed || generation != scope.epoch || owner != scope.account || !scope.sameAccount()) return@post
            chatReads.remove(gameId)
            if (page != null && available(gameId)) {
                page.readState?.let(personalChat::accept)
                installChat(gameId, page.sync.revision, page.messages, snapshot = false)
                if (cursor == null) page.older?.let { olderChat[gameId] = it }
                chatCursors[gameId] = page.next ?: page.sync.revision
                scope.statusChanged()
                if (page.next != null) fetchChat(gameId, page.sync)
                else if (GameWire.ordinal(sync.revision) > GameWire.ordinal(page.sync.revision)) fetchChat(gameId, sync)
            }
        } }) chatReads.remove(gameId)
    }

    fun loadOlderChat(gameId: String): Boolean {
        val cursor = olderChat[gameId] ?: return false
        if (!chatReads.add(gameId)) return false
        val generation = scope.epoch; val owner = scope.account
        val started = connection.chatPage(gameId, before = cursor) { page -> scope.post {
            if (scope.closed || generation != scope.epoch || owner != scope.account || !scope.sameAccount()) return@post
            chatReads.remove(gameId)
            if (page != null && available(gameId)) {
                page.readState?.let(personalChat::accept)
                installChat(gameId, page.sync.revision, page.messages, snapshot = false)
                olderChat.remove(gameId); page.older?.let { olderChat[gameId] = it }
                scope.statusChanged()
            }
        } }
        if (!started) chatReads.remove(gameId)
        return started
    }
    /** The game's personal context is gone (unavailable, cancelled or deleted). */
    fun remove(gameId: String) {
        messages.remove(gameId); chatRevisions.remove(gameId); chatCursors.remove(gameId); olderChat.remove(gameId); chatReads.remove(gameId)
        personalChat.remove(gameId); chatReadRequests.remove(gameId); chatReadRefresh.remove(gameId); chatReadRetry.remove(gameId)
        chatSubmissions.entries.removeAll { it.value.command.scope.gameId == gameId }
    }
    /** In-flight reads belong to the old socket epoch. */
    fun disconnected() { chatReads.clear(); chatReadRequests.clear(); chatReadRefresh.clear(); chatReadRetry.clear() }
    fun clear() { messages.clear(); chatRevisions.clear(); chatCursors.clear(); olderChat.clear(); chatReads.clear(); personalChat.clear(); chatSubmissions.clear() }
}
