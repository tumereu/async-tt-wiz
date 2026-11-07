package org.asyncttwiz.android

// Web spike: split from CardAppearancePreferences.kt (androidApp) with the table drawing
enum class CardArea(val label: String) { HAND("Hand"), PILES("Piles"), STACK("Stack"), PANELS("Panels") }
/** TBL-035: the per-text sizes (notes are gone with the tabletop controls, I-D52). */
enum class CardTextScalePart(val label: String) { TITLE("Title"), VALUES("P/T & counters") }

/** Presentation only. Defaults deliberately preserve the existing table's dimensions. */
data class CardAppearance(
    val cardSize: Float = 1f,
    val customizeAreas: Boolean = false,
    val areas: Map<CardArea, Float> = emptyMap(),
    val textSize: Float = 1f,
    val customizeText: Boolean = false,
    val text: Map<CardTextScalePart, Float> = emptyMap()
) {
    fun size(area: CardArea) = cardScale(if (customizeAreas) areas[area] ?: cardSize else cardSize)
    fun text(part: CardTextScalePart) = cardScale(if (customizeText) text[part] ?: textSize else textSize)
}

const val MIN_CARD_SCALE = .5f
const val MAX_CARD_SCALE = 3f
fun cardScale(value: Float) = if (value.isFinite()) value.coerceIn(MIN_CARD_SCALE, MAX_CARD_SCALE) else 1f
fun parseCardScale(value: String): Float? = value.trim().toFloatOrNull()
    ?.takeIf { it.isFinite() }?.let(::cardScale)
