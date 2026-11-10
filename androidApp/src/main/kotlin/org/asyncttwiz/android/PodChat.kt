package org.asyncttwiz.android

import androidx.compose.runtime.*
import kotlinx.coroutines.runBlocking
import org.asyncttwiz.protocol.*
import java.util.UUID
import java.util.concurrent.ExecutorService

/** A message this device sent to a pod's chat, shown until the server has it ("Checking delivery…", or "Not delivered"). */
data class PodChatOutgoing(val requestId: String, val text: String, val failed: Boolean = false)

/**
 * One pod's chat as this device holds it (owner, 2026-11-08): [messages] ascending (the newest page and everything since),
 * [read] the server's read state for the viewer, [seen] the newest ordinal the viewer has seen here since (not yet on the
 * server), the unsent [draft] and sends on their way. [unavailable]: the server refused it (not a member, or no pod).
 */
data class PodChatView(val podId: String, val messages: List<PodChatMessage> = emptyList(),
    val authors: Map<String, AccountIdentity> = emptyMap(), val read: PodChatRead? = null, val seen: Long = 0,
    val outgoing: List<PodChatOutgoing> = emptyList(), val draft: String = "", val unavailable: Boolean = false) {
    val loaded get() = read != null
    /** The viewer's cursor: the server's, or further on where they have seen messages since. */
    val cursor get() = maxOf(read?.readThrough?.toLongOrNull() ?: 0, seen)
    /**
     * The other members' messages [viewer] hasn't seen: the server's count (which covers history not loaded here), less
     * the loaded ones seen since, plus the loaded ones newer than the server's count. Nothing before the server's read state
     * is known.
     */
    fun unread(viewer: String?): Int {
        val state = read ?: return 0
        fun after(ordinal: Long) = messages.count { it.author != viewer && it.ordinal.toLong() > ordinal }
        val serverCursor = state.readThrough.toLongOrNull() ?: 0
        return (state.unread - (after(serverCursor) - after(cursor)) + after(state.newest.toLongOrNull() ?: 0)).coerceAtLeast(0)
    }
    /** The newest message whose reading the server doesn't know yet, to send it as the new cursor. */
    fun pendingRead(): PodChatMessage? {
        val serverCursor = read?.readThrough?.toLongOrNull() ?: 0
        if (seen <= serverCursor) return null
        return messages.lastOrNull { it.ordinal.toLong() <= seen }?.takeIf { it.ordinal.toLong() > serverCursor }
    }
    fun canSend(text: String = draft) = !unavailable && loaded && ChatValidation.text(text)
    /** [this] with [page]'s messages merged by id (ascending) and its authors and read state. */
    fun install(page: PodChatPage): PodChatView {
        val byId = LinkedHashMap<String, PodChatMessage>()
        (messages + page.messages).sortedBy { it.ordinal.toLong() }.forEach { byId[it.id] = it }
        val ids = page.messages.map { it.id }.toSet()
        return copy(messages = byId.values.toList(), authors = authors + page.authors.associateBy { it.accountId },
            outgoing = outgoing.filter { it.requestId !in ids }, unavailable = false).accept(page.read)
    }
    /** [this] with the server's read state [state], unless it is older than the one held. */
    fun accept(state: PodChatRead): PodChatView {
        val old = read
        if (old != null && (state.readThrough.toLong() < old.readThrough.toLong() || state.newest.toLong() < old.newest.toLong())) return this
        return copy(read = state)
    }
    /** [this] with the messages [ids] seen (laid out in the chat's visible list). */
    fun presented(ids: Set<String>): PodChatView {
        val newest = messages.filter { it.id in ids }.maxOfOrNull { it.ordinal.toLong() } ?: return this
        return if (newest > seen) copy(seen = newest) else this
    }
}

/**
 * Pod chats (owner, 2026-11-08), shared by the pod screens and a pod game's table: the newest page of each watched pod and
 * then what is new, read every few seconds while shown ([refresh]); sends; and the viewer's read cursor, sent as messages
 * are seen. Background reads fail quietly; a send that fails says "Not delivered". Main thread, requests on [worker].
 */
class PodChatController(private val api: Pods, private val credential: () -> String?, private val worker: ExecutorService,
    private val post: (() -> Unit) -> Unit) {
    var views by mutableStateOf<Map<String, PodChatView>>(emptyMap())
        private set
    private var epoch = 0L
    private val reading = mutableSetOf<String>()
    private val readAgain = mutableSetOf<String>()
    private val cursors = mutableSetOf<String>()
    /** Per pod, why its reads have been failing since the last one that worked (logged once). */
    private val failing = mutableMapOf<String, String>()
    fun view(podId: String): PodChatView = views[podId] ?: PodChatView(podId)
    private fun update(podId: String, change: (PodChatView) -> PodChatView) { views = views + (podId to change(view(podId))) }

    /** Reads what is new in [podId]'s chat (its newest page the first time), with the viewer's read state. */
    fun refresh(podId: String) {
        if (podId in reading) { readAgain += podId; return }
        val token = credential() ?: return
        val generation = epoch
        // The newest page the first time, then what is newer than the newest message held
        val after = view(podId).messages.lastOrNull()?.ordinal ?: if (view(podId).loaded) "0" else null
        reading += podId
        worker.execute {
            val result = runCatching { runBlocking { api.chat(token, podId, after = after) } }
            post {
                if (generation != epoch) return@post
                reading -= podId
                result.onSuccess { page ->
                    failing.remove(podId)
                    if (page.messages.isNotEmpty()) AppLog.info("pod-chat", "Pod $podId: ${page.messages.size} new messages " +
                        "#${page.messages.first().ordinal}–#${page.messages.last().ordinal} (${page.messages.sumOf { it.text.length }} chars), " +
                        "read through #${page.read.readThrough}, ${page.read.unread} unread")
                    update(podId) { it.install(page) }
                    if (page.next != null) readAgain += podId
                    syncRead(podId)
                }.onFailure { failure ->
                    val code = (failure as? PodFailure)?.code
                    if (code == "not_found") {
                        AppLog.info("pod-chat", "Pod $podId: chat unavailable (not a member or no pod)")
                        update(podId) { it.copy(unavailable = true) }
                    } else {
                        // Once per stretch of failures, so an offline phone's polling doesn't push out what matters
                        val cause = code ?: failure.javaClass.simpleName
                        if (failing.put(podId, cause) != cause) AppLog.info("pod-chat", "Pod $podId: chat read failed quietly: $cause")
                    }
                }
                if (readAgain.remove(podId)) refresh(podId)
            }
        }
    }

    fun compose(podId: String, text: String) = update(podId) { it.copy(draft = text) }

    /** Sends [text] ([fromDraft]: the typed draft, which then clears). The message shows as being sent until the server has it. */
    fun send(podId: String, text: String = view(podId).draft, fromDraft: Boolean = true) {
        if (!view(podId).canSend(text)) return
        val token = credential() ?: return
        val requestId = UUID.randomUUID().toString()
        val generation = epoch
        update(podId) { it.copy(draft = if (fromDraft) "" else it.draft, outgoing = it.outgoing + PodChatOutgoing(requestId, text)) }
        AppLog.info("pod-chat", "Pod $podId: sending message $requestId (${text.length} chars)")
        worker.execute {
            val result = runCatching { runBlocking { api.sendChat(token, podId, SendPodChat(requestId, text)) } }
            post {
                if (generation != epoch) return@post
                result.onSuccess { message ->
                    AppLog.info("pod-chat", "Pod $podId: message ${message.id} stored as #${message.ordinal}")
                    refresh(podId)
                }.onFailure { failure ->
                    AppLog.warn("pod-chat", "Pod $podId: message $requestId not delivered: ${(failure as? PodFailure)?.code ?: failure.javaClass.simpleName}")
                    update(podId) { view -> view.copy(outgoing = view.outgoing.map { if (it.requestId == requestId) it.copy(failed = true) else it }) }
                }
            }
        }
    }

    /** The messages [ids] of [podId] are laid out in the open chat: they are read (the cursor goes to the server). */
    fun presented(podId: String, ids: Set<String>) {
        if (ids.isEmpty()) return
        update(podId) { it.presented(ids) }
        syncRead(podId)
    }

    private fun syncRead(podId: String) {
        if (podId in cursors) return
        val message = view(podId).pendingRead() ?: return
        val token = credential() ?: return
        val generation = epoch
        cursors += podId
        AppLog.info("pod-chat", "Pod $podId: read through #${message.ordinal} (message ${message.id})")
        worker.execute {
            val result = runCatching { runBlocking { api.readChat(token, podId, ReadChat(message.id)) } }
            post {
                if (generation != epoch) return@post
                cursors -= podId
                result.onSuccess { state -> update(podId) { it.accept(state) }; syncRead(podId) }
                    .onFailure { AppLog.info("pod-chat", "Pod $podId: read cursor not saved: ${(it as? PodFailure)?.code ?: it.javaClass.simpleName}") }
            }
        }
    }

    fun clear() { epoch++; reading.clear(); readAgain.clear(); cursors.clear(); failing.clear(); views = emptyMap() }
}
