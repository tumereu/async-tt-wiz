package org.asyncttwiz.android

/** Face-local units (28 × 40), independent of HUD size and battlefield zoom. */
internal data class CardAnnotationLayout(val note: TableBounds?, val counters: List<TableBounds>)

internal fun cardAnnotationLayout(titleBand: Float, artBottom: Float, noteHeight: Float,
    counterHeight: Float, hasNote: Boolean, counterCount: Int): CardAnnotationLayout {
    val top = titleBand - 1f
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
