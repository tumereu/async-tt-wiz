package org.asyncttwiz.android

import org.asyncttwiz.protocol.*

data class TransferFeedback(val serial: Long, val text: String)

/** Compare displayed membership, not submitted quantity or receipt counts. Includes reconciliation. */
internal fun preparationFeedback(before: LobbyPreparation?, after: LobbyPreparation?, actor: String?,
    previous: TransferFeedback?): TransferFeedback? {
    val old = before?.players?.find { it.accountId == actor }?.deck ?: return previous
    val next = after?.players?.find { it.accountId == actor }?.deck ?: return previous
    if (before.gameId != after.gameId || before.roundId != after.roundId || old.id != next.id) return null
    val changes = (old.entries + next.entries).map { it.definition }.distinct().mapNotNull { digest ->
        val delta = next.entries.filter { it.definition == digest && it.section == SavedDeckSection.SIDEBOARD }.sumOf { it.quantity.toLong() } -
            old.entries.filter { it.definition == digest && it.section == SavedDeckSection.SIDEBOARD }.sumOf { it.quantity.toLong() }
        if (delta == 0L) null else {
            val name = after.definitions.find { it.digest == digest }?.definition?.name ?: "Card"
            "${kotlin.math.abs(delta)}× $name → ${if (delta > 0) "Sideboard" else "Mainboard"}"
        }
    }
    return if (changes.isEmpty()) previous else TransferFeedback((previous?.serial ?: 0) + 1, changes.joinToString("; "))
}

/** One pointer owns an immutable capture. Reflow/quantity changes cannot retarget it. */
internal class PreparationGesture(private val captured: PreparedTransfer?, private val slop: Float) {
    var dragging = false
        private set
    private var cancelled = false
    fun move(distance: Float, consumed: Boolean = false) {
        if (consumed && !dragging) cancelled = true
        if (distance > slop) dragging = true
    }
    fun cancel() { cancelled = true }
    fun release(overOppositeSection: Boolean): PreparedTransfer? =
        if (!cancelled && (!dragging || overOppositeSection)) captured else null
}
