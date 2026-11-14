package org.asyncttwiz.android

import androidx.compose.runtime.*
import org.asyncttwiz.appui.AppPlatform

enum class CardArea(val label: String) { HAND("Hand"), PILES("Piles"), STACK("Stack"), PANELS("Panels") }
/** TBL-035: the per-text sizes (notes are gone with the tabletop controls, I-D52). */
enum class CardTextScalePart(val label: String) { TITLE("Title"), VALUES("P/T & counters") }

/** Presentation only. Defaults deliberately preserve the existing table's dimensions. [layout]: how much larger the layout
 * draws each area's cards at the same slider sizes (WEB-022; the phone's 1×). */
data class CardAppearance(
    val cardSize: Float = 1f,
    val customizeAreas: Boolean = false,
    val areas: Map<CardArea, Float> = emptyMap(),
    val textSize: Float = 1f,
    val customizeText: Boolean = false,
    val text: Map<CardTextScalePart, Float> = emptyMap(),
    val layout: LayoutCardScale = LayoutCardScale()
) {
    /** The slider size of [area] (TBL-034), as Settings shows it. */
    fun slider(area: CardArea) = cardScale(if (customizeAreas) areas[area] ?: cardSize else cardSize)
    /** How large [area]'s cards are drawn: its slider size on top of the layout's own ([layout]). */
    fun size(area: CardArea) = slider(area) * layout.of(area)
    /** How large battlefield cards are drawn (before zoom): the card size on top of the layout's own ([layout]). */
    val battlefield: Float get() = cardScale(cardSize) * layout.battlefield
    fun text(part: CardTextScalePart) = cardScale(if (customizeText) text[part] ?: textSize else textSize)
}

/** WEB-022: how much larger than the phone's a layout draws the cards of the battlefield and each area at the same slider
 * sizes (TBL-034's sliders scale on top of it). */
data class LayoutCardScale(val battlefield: Float = 1f, val areas: Map<CardArea, Float> = emptyMap()) {
    fun of(area: CardArea) = areas[area] ?: 1f
}

const val MIN_CARD_SCALE = .5f
const val MAX_CARD_SCALE = 3f
fun cardScale(value: Float) = if (value.isFinite()) value.coerceIn(MIN_CARD_SCALE, MAX_CARD_SCALE) else 1f
fun parseCardScale(value: String): Float? = value.trim().toFloatOrNull()
    ?.takeIf { it.isFinite() }?.let(::cardScale)

/** WEB-022 (owner, desktop playtest 2026-11-14: "Battlefield cards can be at least twice as big, hand cards can be maybe 30%
 * bigger"): the desktop layout draws hand cards 2.6× the phone's (2× before), the piles 1.6× (twice the 32 dp a landscape
 * bar row fits, filling the taller seat header, [DesktopTable.PILE_HEIGHT]) and the Stack's and panels' cards 2× (1.5×
 * before), with every slider at 1×. Battlefield cards stay 2× for now: at 4× the six battlefield rows don't fit a 1440 × 900
 * or 1920 × 1080 window even at the opening's least zoom (TBL-121), so the land rows open out of view; the Card size slider
 * at 2× gives 4× until the owner decides. */
val DesktopCardScale = LayoutCardScale(battlefield = 2f,
    areas = mapOf(CardArea.HAND to 2.6f, CardArea.PILES to 1.6f, CardArea.STACK to 2f, CardArea.PANELS to 2f))

/** The desktop layout's card sizes before the player changes them (WEB-022, docs/specs/web-client.md §7.2): every slider at
 * 1×, on top of [DesktopCardScale]. */
val DesktopCardAppearance = CardAppearance(layout = DesktopCardScale)

/** One file for the device, intentionally independent of account IDs and logout cleanup. WEB-019: one per layout, the
 * desktop layout's ([AppLayout.Desktop]) starting from [DesktopCardAppearance]. */
class CardAppearancePreferences(platform: AppPlatform, layout: AppLayout = AppLayout.Phone) {
    private val defaults = if (layout == AppLayout.Desktop) DesktopCardAppearance else CardAppearance()
    // WEB-022: since 2026-11-14 the desktop layout's sliders scale on top of its own sizes (DesktopCardScale), so its store
    // starts afresh rather than multiplying the sizes saved before
    private val store = platform.preferences(if (layout == AppLayout.Desktop) "card-sizes-desktop" else "card-appearance")
    fun read(): CardAppearance {
        val size = store.getFloat("size", defaults.cardSize)
        val text = store.getFloat("text", defaults.textSize)
        return CardAppearance(cardScale(size), store.getBoolean("custom-areas", defaults.customizeAreas),
            CardArea.entries.associateWith { cardScale(store.getFloat("area/${it.name}", if (store.contains("size")) size else defaults.areas[it] ?: size)) },
            cardScale(text), store.getBoolean("custom-text", defaults.customizeText),
            CardTextScalePart.entries.associateWith { cardScale(store.getFloat("text/${it.name}", text)) }, defaults.layout)
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
