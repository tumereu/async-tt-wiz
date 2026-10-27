package org.asyncttwiz.protocol

import kotlinx.serialization.Serializable

/** Immutable game-wide communication, deliberately outside the board/history tree. */
@Serializable data class ChatMessage(val id: String, val gameId: String, val playId: String,
    val author: String, val text: String, val revision: String, val createdAt: String)

@Serializable data class ChatSync(val revision: String = "0", val messageId: String? = null)
/** Ascending display order. before pages older history; after pages forward catch-up. */
@Serializable data class ChatPage(val gameId: String, val messages: List<ChatMessage>, val sync: ChatSync,
    val older: String? = null, val next: String? = null, val readState: ChatReadState? = null)

/** Private to the authenticated member; throughRevision bounds the unread snapshot. */
@Serializable data class ChatReadState(val gameId: String, val readThrough: String = "0",
    val throughRevision: String = "0", val unread: Int = 0)
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
