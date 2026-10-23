package org.asyncttwiz.protocol

import kotlinx.serialization.Serializable

/** Immutable game-wide communication, deliberately outside the board/history tree. */
@Serializable data class ChatMessage(val id: String, val gameId: String, val playId: String,
    val author: String, val text: String, val revision: String, val createdAt: String)

object ChatValidation {
    // Preserve text exactly, including multiline whitespace. This is not card-text normalization.
    fun text(value: String) = value.isNotBlank() && DeckValidation.text(value)
}
