package org.asyncttwiz.android

/** Face-local units (28 × 40), independent of HUD size and battlefield zoom. */
internal data class CardAnnotationLayout(val note: TableBounds?, val counters: List<TableBounds>)

internal fun cardAnnotationLayout(titleBand: Float, artBottom: Float, noteHeight: Float,
    counterHeight: Float, hasNote: Boolean, counterCount: Int): CardAnnotationLayout {
    val top = titleBand - 1f
    if (counterCount > 2) {
        val columns = (counterCount + 1) / 2
        val noteColumns = if (hasNote) 1 else 0
        val slotWidth = 24f / (columns + noteColumns)
        // Two full-height rows may cover the title; keep a separate column for the note.
        val counterTop = (artBottom - 2 * counterHeight).coerceIn(0f, (40f - 2 * counterHeight).coerceAtLeast(0f))
        return CardAnnotationLayout(
            if (hasNote) TableBounds(2f, top.coerceAtMost(39f - noteHeight), slotWidth, noteHeight) else null,
            List(counterCount) { index -> TableBounds(2f + (index % columns + noteColumns) * slotWidth,
                counterTop + index / columns * counterHeight, slotWidth, counterHeight) })
    }
    // Never cap a line to the remaining art height. At large text sizes annotations
    // share a row and extend over the art instead of losing their lower glyphs.
    val shared = hasNote && counterCount > 0 && noteHeight + counterHeight + 1f > artBottom - top
    val slotWidth = 24f / (counterCount + if (shared) 1 else 0).coerceAtLeast(1)
    val noteWidth = if (shared) slotWidth else 24f
    val note = if (hasNote) TableBounds(2f, top.coerceAtMost(39f - noteHeight), noteWidth, noteHeight) else null
    val counterTop = if (shared) top else maxOf(top, artBottom - counterHeight)
    val counters = List(counterCount) { index ->
        val column = index + if (shared) 1 else 0
        val left = 2f + column * slotWidth
        val right = 2f + (column + 1) * slotWidth
        TableBounds(left, counterTop.coerceAtMost(39f - counterHeight), right - left, counterHeight)
    }
    return CardAnnotationLayout(note, counters)
}
