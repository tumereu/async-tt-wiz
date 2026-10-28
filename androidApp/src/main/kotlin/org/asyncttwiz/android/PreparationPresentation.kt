package org.asyncttwiz.android

import org.asyncttwiz.protocol.*

data class TransferFeedback(val serial: Long, val text: String)

/** Names are trusted-client presentation only; never expose another player's game deck title. */
internal fun gameDeckName(owner: String, viewer: String?, name: String): String =
    if (owner == viewer) name else "Deck"

/** Compare displayed membership, not submitted quantity or receipt counts. Includes reconciliation. */
internal fun preparationFeedback(before: LobbyPreparation?, after: LobbyPreparation?, actor: String?,
    previous: TransferFeedback?): TransferFeedback? {
    val old = before?.players?.find { it.accountId == actor }?.deck ?: return previous
    val next = after?.players?.find { it.accountId == actor }?.deck ?: return previous
    if (before.gameId != after.gameId || before.roundId != after.roundId || old.id != next.id) return null
    val changes = (old.entries + next.entries).map { it.definition }.distinct().mapNotNull { digest ->
        // Supplied basics are counts set with steppers, not moved copies.
        fun side(deck: PreparedDeck) = deck.entries.filter { it.definition == digest && it.section == SavedDeckSection.SIDEBOARD &&
            it.suppliedBasic == null }.sumOf { it.quantity.toLong() }
        val delta = side(next) - side(old)
        if (delta == 0L) null else {
            val name = after.definitions.find { it.digest == digest }?.definition?.name ?: "Card"
            "${kotlin.math.abs(delta)}× $name → ${if (delta > 0) "Sideboard" else "Mainboard"}"
        }
    }
    return if (changes.isEmpty()) previous else TransferFeedback((previous?.serial ?: 0) + 1, changes.joinToString("; "))
}

/** One undoable preparation tap: the exact copies a transfer moved, or a section's basics before a stepper tap. */
internal sealed interface PreparationStep {
    data class Moved(val transfer: PreparedTransfer) : PreparationStep
    data class Basics(val section: SavedDeckSection, val previous: PodDeckBasics) : PreparationStep
}

/** Personal undo for one game's preparation round and deck. Undo sends the inverse as an ordinary request. */
internal data class PreparationUndo(val gameId: String = "", val roundId: String = "", val deckId: String = "",
    val steps: List<PreparationStep> = emptyList()) {
    private fun matches(gameId: String, roundId: String, deckId: String) =
        gameId == this.gameId && roundId == this.roundId && deckId == this.deckId
    /** The latest step that still belongs to the shown preparation, or null once its game, round or deck changed. */
    fun last(preparation: LobbyPreparation?, actor: String?): PreparationStep? {
        val deck = preparation?.players?.find { it.accountId == actor }?.deck ?: return null
        return steps.lastOrNull()?.takeIf { matches(preparation.gameId, preparation.roundId, deck.id) }
    }
    fun push(gameId: String, roundId: String, deckId: String, step: PreparationStep): PreparationUndo =
        (if (matches(gameId, roundId, deckId)) this else PreparationUndo(gameId, roundId, deckId))
            .let { it.copy(steps = (it.steps + step).takeLast(LIMIT)) }
    fun pop() = copy(steps = steps.dropLast(1))
    companion object { const val LIMIT = 50 }
}

/** The same copies back to where they came from. */
internal fun PreparedTransfer.inverse(): PreparedTransfer {
    val target = if (source == SavedDeckSection.MAINBOARD) SavedDeckSection.SIDEBOARD else SavedDeckSection.MAINBOARD
    return copy(source = target, entries = entries.map { it.copy(section = target) })
}

/** A row swipe moves one copy once it travels [threshold] in either direction; shorter swipes spring back. */
internal fun preparationSwipeThreshold(rowWidth: Float, maximum: Float): Float = minOf(rowWidth * .35f, maximum)
internal fun preparationSwipeCommits(offset: Float, threshold: Float): Boolean = threshold > 0f && kotlin.math.abs(offset) >= threshold
internal fun preparationSwipeProgress(offset: Float, threshold: Float): Float =
    if (threshold <= 0f) 0f else (kotlin.math.abs(offset) / threshold).coerceIn(0f, 1f)
