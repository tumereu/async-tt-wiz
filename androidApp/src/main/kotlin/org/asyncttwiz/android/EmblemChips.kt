package org.asyncttwiz.android

/** A player's emblems (I-D25, TRN-053): a chip each with the emblem's text in the room right of the player's piles (in
 * landscape, or when that room is too narrow, beside the life square, away from the table's centre); drawn only, so taps
 * reach the table beneath. */
internal object EmblemChips {
    const val MAX_LINES = 2
    const val GAP = 3f
    /** The narrowest room right of the piles the chips use; narrower, they go by the life square. */
    const val MIN_AREA_WIDTH = 96f

    /** The chips of the given [lines] counts stacked in [area], oldest first: centred in it when they fit; otherwise
     * from its edge nearer the table's centre outward ([below]: the opponent's, growing upward). */
    fun stacked(area: TableBounds, below: Boolean, widths: List<Float>, lines: List<Int>): List<TableBounds> {
        val heights = lines.map { ChipText.HEIGHT + (it.coerceIn(1, MAX_LINES) - 1) * ChipText.LINE }
        val total = heights.sum() + (heights.size - 1).coerceAtLeast(0) * GAP
        var y = when {
            total <= area.height -> area.y + (area.height - total) / 2
            below -> area.bottom - total
            else -> area.y
        }
        return heights.mapIndexed { n, height ->
            TableBounds(area.x, y, minOf(widths[n] + 2 * ChipText.PADDING, area.width), height).also { y += height + GAP }
        }
    }

    /** [player]'s emblems' texts on [board], oldest first. */
    fun texts(board: org.asyncttwiz.protocol.BoardState, player: String): List<String> = board.engineRecords
        .filter { it.kind == org.asyncttwiz.protocol.EngineRecordKind.Emblem && it.player == player }
        .sortedWith(compareBy({ it.timestamp ?: 0 }, { it.id })).map { "Emblem: " + it.text.replace('\n', ' ') }

    /** The chip after [previous] (the chip before it; null: the first, by the [life] square itself), away from the
     * square, never narrower than it. */
    fun bounds(previous: TableBounds?, life: TableBounds, below: Boolean, textWidth: Float, lines: Int): TableBounds {
        val width = maxOf(textWidth + 2 * ChipText.PADDING, life.width)
        val height = ChipText.HEIGHT + (lines.coerceIn(1, MAX_LINES) - 1) * ChipText.LINE
        val after = previous ?: life
        val gap = if (previous == null) ChipText.TAIL + 1f else GAP
        val y = if (below) after.bottom + gap else after.y - gap - height
        return TableBounds(life.x, y, width, height)
    }
}

/** The text metrics of the chips beside a life square (EmblemChips). */
internal object ChipText {
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
