package org.asyncttwiz.protocol

import kotlinx.serialization.Serializable

@Serializable enum class ContentsSelection { TOP, BOTTOM, ALL }
@Serializable enum class ContentsRest { TOP, BOTTOM, BOTTOM_RANDOM, GRAVEYARD, SHUFFLE, EXILE }
/** A shared tray of references, not a second containing zone. Order is top first. */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable data class ContentsDisplay(val id: String, val author: String, val containerId: String,
    val selection: ContentsSelection, val requestedCount: Int, val reveal: Boolean, val cardIds: List<String>,
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val revealedIds: Set<String> = emptySet(),
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val panelVersion: Int = 1,
    /** Rules v30, a Search on a Library: the cards put on its top / bottom while it is open, waiting to be
     * placed after the close shuffle. One entry per put in put order; a random-order put is one entry of all its cards. */
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val queuedTop: List<List<String>> = emptyList(),
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER) val queuedBottom: List<List<String>> = emptyList()) {
    fun facesVisibleTo(accountId: String) = reveal || author == accountId
    fun faceVisibleTo(cardId: String, accountId: String) = facesVisibleTo(accountId) || cardId in revealedIds
    val search get() = selection == ContentsSelection.ALL && !reveal
    val title get() = if (reveal) "Reveal" else if (search) "Search" else "Look"
}
