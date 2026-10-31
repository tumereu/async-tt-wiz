package org.asyncttwiz.android

/** Speech bubble attached to a life square: below the top player's, above the bottom player's. */
internal object StatusBubble {
    const val TEXT = 11f
    const val PADDING = 6f
    const val HEIGHT = 18f
    const val TAIL = 5f
    const val MAX_WIDTH = 168f
    /** A muted status: a small dot on the life square's corner on the bubble's side. */
    const val DOT = 8f
    fun dot(life: TableBounds, below: Boolean): TableBounds =
        TableBounds(life.right - DOT / 2, (if (below) life.bottom else life.y) - DOT / 2, DOT, DOT)
    /** Extra height of each further text line. */
    const val LINE = 13f
    /** A long status never covers more of the table than this; the life popup shows all of it. */
    const val MAX_LINES = 4
    fun bounds(life: TableBounds, below: Boolean, textWidth: Float, lines: Int = 1): TableBounds {
        val width = maxOf(textWidth + 2 * PADDING, 28f)
        val height = HEIGHT + (lines.coerceIn(1, MAX_LINES) - 1) * LINE
        val y = if (below) life.bottom + TAIL + 1f else life.y - TAIL - 1f - height
        return TableBounds(life.x, y, width, height)
    }
    /** The whole text on one line when it fits; otherwise wrapped (at a space when possible) onto up to
     * [MAX_LINES] lines, the last of which is ellipsized when even those are not enough. */
    fun lines(text: String, maxWidth: Float, measure: (String) -> Float): List<String> {
        val result = mutableListOf<String>()
        var rest = text
        while (rest.isNotEmpty()) {
            if (measure(rest) <= maxWidth) { result += rest; break }
            if (result.size == MAX_LINES - 1) { result += fit(rest, maxWidth, measure); break }
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
