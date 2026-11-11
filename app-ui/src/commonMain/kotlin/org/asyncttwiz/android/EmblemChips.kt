package org.asyncttwiz.android

/** A player's emblems (I-D25, TRN-053; owner gallery review 2026-11-06): a small round chip each with the emblem's art
 * (an emblem icon when it has none), at most [SHOWN] of them and then a "+N" chip, in the player's row of round chips by
 * their life square, after their dungeon's room and their states ([PlayerChips], TRN-072, TRN-069). Holding any of them
 * inspects all that player's emblems ([EmblemInspect]). */
object EmblemChips {
    /** A round chip's size, the smallest it gets where room is short, and the gap between chips (dp). */
    const val SIZE = 24f
    const val MIN_SIZE = 20f
    const val ROW_GAP = 4f
    /** The most emblems shown as chips; beyond them one "+N" chip. */
    const val SHOWN = 3
    /** The room right of the piles a player's chips always get in portrait (owner gallery review 2026-11-09): the piles scroll under it. */
    const val MIN_AREA_WIDTH = 96f

    /** [player]'s emblems' texts on [board], oldest first. */
    fun texts(board: org.asyncttwiz.protocol.BoardState, player: String): List<String> = board.engineRecords
        .filter { it.kind == org.asyncttwiz.protocol.EngineRecordKind.Emblem && it.player == player }
        .sortedWith(compareBy({ it.timestamp ?: 0 }, { it.id })).map { it.text }

    /** How many round chips [count] emblems take: each up to [SHOWN], then one "+N" chip for the rest. */
    fun chipCount(count: Int): Int = if (count <= SHOWN) count else SHOWN + 1

    /** The width of a row of [chips] round chips [size] dp each. */
    fun rowWidth(chips: Int, size: Float = SIZE): Float = if (chips == 0) 0f else chips * size + (chips - 1) * ROW_GAP

    /** How many of [count] emblems [chips] chips show by themselves: all when there is a chip for each, else all but the
     * last chip, which reads "+N". */
    fun shown(count: Int, chips: Int): Int = if (count <= chips) count else chips - 1
}

/** The text metrics of the chips beside a life square (EmblemChips). */
object ChipText {
    const val TEXT = 11f
    const val PADDING = 6f
    const val HEIGHT = 18f
    const val TAIL = 5f
    const val MAX_WIDTH = 168f
    /** Extra height of each further text line. */
    const val LINE = 13f
    /** The whole text on one line when it fits; otherwise wrapped (at a space when possible) onto up to [maxLines]
     * lines, the last of which is ellipsized when even those are not enough. */
    fun lines(text: String, maxWidth: Float, maxLines: Int, measure: (String) -> Float): List<String> {
        val result = mutableListOf<String>()
        var rest = text
        while (rest.isNotEmpty()) {
            if (measure(rest) <= maxWidth) { result += rest; break }
            if (result.size == maxLines - 1) { result += fit(rest, maxWidth, measure); break }
            var end = rest.length
            while (end > 1 && (measure(rest.substring(0, end)) > maxWidth || rest[end].isLowSurrogate())) end--
            val space = rest.lastIndexOf(' ', end).takeIf { it > 0 && rest[end] != ' ' }
            val split = space ?: end
            result += rest.substring(0, split).trimEnd()
            rest = rest.substring(split).trimStart()
        }
        return result.ifEmpty { listOf("") }
    }
    /** The whole text when it fits, otherwise the longest prefix followed by an ellipsis. */
    fun fit(text: String, maxWidth: Float, measure: (String) -> Float): String {
        if (measure(text) <= maxWidth) return text
        var end = text.length
        while (end > 0) {
            if (end < text.length && text[end].isLowSurrogate()) { end--; continue }
            val candidate = text.substring(0, end).trimEnd() + "…"
            if (measure(candidate) <= maxWidth) return candidate
            end--
        }
        return "…"
    }
}
