package org.asyncttwiz.android

/** Face-local units (28 × 40), independent of HUD size and battlefield zoom: where Automatum's marks chip (TBL-113:
 * changed types and colours, keywords gained or lost, typed counters) sits, one line under the title band. */
internal fun automatumMarksSlot(titleBand: Float, lineHeight: Float): TableBounds =
    TableBounds(2f, (titleBand - 1f).coerceAtMost(39f - lineHeight), 24f, lineHeight)
