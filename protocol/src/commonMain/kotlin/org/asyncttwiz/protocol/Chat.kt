package org.asyncttwiz.protocol

import kotlinx.serialization.Serializable

/** Immutable game-wide communication, deliberately outside the board/history tree. */
@Serializable data class ChatMessage(val id: String, val gameId: String, val playId: String,
    val author: String, val text: String, val revision: String, val createdAt: String)

@Serializable data class ChatSync(val revision: String = "0", val messageId: String? = null)
/** Ascending display order. before pages older history; after pages forward catch-up. */
@Serializable data class ChatPage(val gameId: String, val messages: List<ChatMessage>, val sync: ChatSync,
    val older: String? = null, val next: String? = null, val readState: ChatReadState? = null)

/** Private to the authenticated member; throughRevision bounds the unread snapshot. [peerReadThrough]: the other
 * player's read cursor, so the author sees which of their messages were read (LOB-138). */
@Serializable data class ChatReadState(val gameId: String, val readThrough: String = "0",
    val throughRevision: String = "0", val unread: Int = 0, val peerReadThrough: String = "0")
@Serializable data class ReadChat(val messageId: String)

object ChatPaging {
    const val SIZE = 100
    // Text may need six JSON bytes per UTF-8 byte (escaped control characters).
    const val MAX_BYTES = SIZE * (DeckValidation.FIELD_BYTES * 6 + 1024)
}

object ChatValidation {
    // Preserve text exactly, including multiline whitespace. This is not card-text normalization.
    fun text(value: String) = value.isNotBlank() && DeckValidation.text(value)
}

/** Pod chat (owner, 2026-11-08): one chat per pod for its members, kept as long as the pod and outside every game.
 * [ordinal] orders a pod's messages (1, 2, …); its limits are a game chat's ([ChatValidation], [ChatPaging]). */
@Serializable data class PodChatMessage(val id: String, val podId: String, val author: String, val text: String,
    val ordinal: String, val createdAt: String)
/** The caller's private read state of a pod's chat: [readThrough] their read cursor, [newest] the newest message's ordinal
 * when it was read and [unread] the other members' messages after the cursor. */
@Serializable data class PodChatRead(val podId: String, val readThrough: String = "0", val newest: String = "0", val unread: Int = 0)
/** Ascending display order, as [ChatPage]: [older] pages older history (before), [next] says more follows (after).
 * [authors] names every author on the page, also one who has left the pod. */
@Serializable data class PodChatPage(val podId: String, val messages: List<PodChatMessage>, val authors: List<AccountIdentity>,
    val read: PodChatRead, val older: String? = null, val next: String? = null)
/** A message to a pod's chat; [requestId] is its id, so a retried send is stored once. */
@Serializable data class SendPodChat(val requestId: String, val text: String)
