package org.asyncttwiz.android

import androidx.compose.runtime.*
import org.asyncttwiz.appui.AppPlatform

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

/** The desktop layout's card sizes before the player changes them (WEB-022, docs/specs/web-client.md §7.2): battlefield
 * 2× (the wireframes' proportions for a 1440-wide table; the opening view still fits the battlefields' height, TBL-121),
 * hand 2×, the other areas 1.5×. */
val DesktopCardAppearance = CardAppearance(cardSize = 2f, customizeAreas = true,
    areas = mapOf(CardArea.HAND to 2f, CardArea.PILES to 1.5f, CardArea.STACK to 1.5f, CardArea.PANELS to 1.5f))

/** One file for the device, intentionally independent of account IDs and logout cleanup. WEB-019: one per layout, the
 * desktop layout's ([AppLayout.Desktop]) starting from [DesktopCardAppearance]. */
class CardAppearancePreferences(platform: AppPlatform, layout: AppLayout = AppLayout.Phone) {
    private val defaults = if (layout == AppLayout.Desktop) DesktopCardAppearance else CardAppearance()
    private val store = platform.preferences(if (layout == AppLayout.Desktop) "card-appearance-desktop" else "card-appearance")
    fun read(): CardAppearance {
        val size = store.getFloat("size", defaults.cardSize)
        val text = store.getFloat("text", defaults.textSize)
        return CardAppearance(cardScale(size), store.getBoolean("custom-areas", defaults.customizeAreas),
            CardArea.entries.associateWith { cardScale(store.getFloat("area/${it.name}", if (store.contains("size")) size else defaults.areas[it] ?: size)) },
            cardScale(text), store.getBoolean("custom-text", defaults.customizeText),
            CardTextScalePart.entries.associateWith { cardScale(store.getFloat("text/${it.name}", text)) })
    }

    fun setSize(value: Float, area: CardArea? = null) {
        store.edit().putFloat(area?.let { "area/${it.name}" } ?: "size", cardScale(value)).apply()
    }
    fun setText(value: Float, part: CardTextScalePart? = null) {
        store.edit().putFloat(part?.let { "text/${it.name}" } ?: "text", cardScale(value)).apply()
    }
    fun customizeAreas(enabled: Boolean) {
        val edit = store.edit().putBoolean("custom-areas", enabled)
        if (enabled && !read().customizeAreas) for (area in CardArea.entries) edit.putFloat("area/${area.name}", read().cardSize)
        edit.apply()
    }
    fun customizeText(enabled: Boolean) {
        val edit = store.edit().putBoolean("custom-text", enabled)
        if (enabled && !read().customizeText) for (part in CardTextScalePart.entries) edit.putFloat("text/${part.name}", read().textSize)
        edit.apply()
    }
    fun observe(change: () -> Unit): () -> Unit = store.observe { change() }
}

@Composable
fun rememberCardAppearancePreferences(): CardAppearancePreferences {
    val platform = AppPlatform.current
    // WEB-019: the sizes of the layout the app is drawn in
    val layout = LocalAppLayout.current
    return remember(platform, layout) { CardAppearancePreferences(platform, layout) }
}

@Composable
fun CardAppearancePreferences.state(): State<CardAppearance> {
    val state = remember(this) { mutableStateOf(read()) }
    DisposableEffect(this) {
        val stop = observe { state.value = read() }
        state.value = read()
        onDispose { stop() }
    }
    return state
}

@Composable
fun rememberCardAppearance(): CardAppearance = rememberCardAppearancePreferences().state().value
