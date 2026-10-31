package org.asyncttwiz.android

import androidx.compose.ui.graphics.Color
import org.asyncttwiz.protocol.*
import org.asyncttwiz.storage.CatalogCard
import org.asyncttwiz.storage.CatalogMetadata
import org.asyncttwiz.storage.CatalogPrinting
import org.asyncttwiz.storage.DeckSection
import org.asyncttwiz.storage.ResolvedDeckEntry

/** A deck (Main, Side, Maybe) or a cube (Main, Maybe): one model, one editor. */
enum class CardListKind(val sections: List<DeckSection>, val maxCards: Int, val noun: String) {
    DECK(listOf(DeckSection.MAINBOARD, DeckSection.SIDEBOARD, DeckSection.MAYBEBOARD), DeckValidation.MAX_CARDS, "deck"),
    CUBE(listOf(DeckSection.MAINBOARD, DeckSection.MAYBEBOARD), CubeValidation.MAX_CARDS, "cube");
    /** A card swiped in [from] moves to the next section, the last one wrapping to Main. */
    fun next(from: DeckSection): DeckSection = sections[(sections.indexOf(from) + 1) % sections.size]
}

internal fun DeckSection.saved() = SavedDeckSection.valueOf(name)
internal fun SavedDeckSection.list() = DeckSection.valueOf(name)

/** SYS-053: section colours used everywhere a section appears: Main green, Side blue, Maybe yellow. */
internal val SectionMaybeYellow = Color(0xFFE0B000)
/** The Maybe colour for text, outlines and chips behind white text, where plain yellow would be hard to read. */
internal val SectionMaybeAmber = Color(0xFF9A6B00)
internal fun SavedDeckSection.sectionColor(): Color = when (this) {
    SavedDeckSection.MAINBOARD -> PodPickGreen
    SavedDeckSection.SIDEBOARD -> PodBuildBlue
    SavedDeckSection.MAYBEBOARD -> SectionMaybeAmber
}
/** Translucent tints (the swipe copy) use the brighter yellow. */
internal fun SavedDeckSection.sectionFill(): Color = if (this == SavedDeckSection.MAYBEBOARD) SectionMaybeYellow else sectionColor()
internal fun SavedDeckSection.sectionLabel() = when (this) {
    SavedDeckSection.MAINBOARD -> "Mainboard"; SavedDeckSection.SIDEBOARD -> "Sideboard"; SavedDeckSection.MAYBEBOARD -> "Maybeboard"
}
/** Short names for the destination buttons. */
internal fun SavedDeckSection.shortLabel() = when (this) {
    SavedDeckSection.MAINBOARD -> "Main"; SavedDeckSection.SIDEBOARD -> "Side"; SavedDeckSection.MAYBEBOARD -> "Maybe"
}
/** The letter of the small other-section counts on search results ("S2"). */
internal fun SavedDeckSection.countLetter() = when (this) {
    SavedDeckSection.MAINBOARD -> "M"; SavedDeckSection.SIDEBOARD -> "S"; SavedDeckSection.MAYBEBOARD -> "Mb"
}

/** Why an edit was refused, in player words; null when it was applied. */
internal class CardListEditResult(val entries: List<ResolvedDeckEntry>, val refused: String? = null)

/**
 * Pure edits of a resolved card list. Every entry keeps its printing (and retained definition) when it moves;
 * one entry per card and section. Cards are matched by name, so another printing of a listed card adds to it.
 */
internal object CardListEdits {
    fun key(entry: ResolvedDeckEntry) = entry.printing.id
    private fun sameCard(entry: ResolvedDeckEntry, name: String) = entry.printing.name.equals(name, ignoreCase = true)

    fun total(entries: List<ResolvedDeckEntry>) = entries.sumOf { it.quantity.toLong() }
    fun count(entries: List<ResolvedDeckEntry>, name: String, section: DeckSection) =
        entries.filter { it.section == section && sameCard(it, name) }.sumOf { it.quantity }

    private fun limit(kind: CardListKind) = "A ${kind.noun} holds at most ${kind.maxCards} cards."

    /** One more copy of [faces] in [section]: an entry of the same card there grows, otherwise a new entry is added. */
    fun add(kind: CardListKind, entries: List<ResolvedDeckEntry>, faces: List<CatalogPrinting>, section: DeckSection): CardListEditResult {
        if (total(entries) >= kind.maxCards) return CardListEditResult(entries, limit(kind))
        val name = faces.first().name
        val index = entries.indexOfFirst { it.section == section && sameCard(it, name) }
        return CardListEditResult(if (index < 0) entries + ResolvedDeckEntry(section, 1, faces, emptyList())
            else entries.toMutableList().also { it[index] = it[index].copy(quantity = it[index].quantity + 1) })
    }

    /** One copy fewer of the card named [name] in [section]. */
    fun removeOne(entries: List<ResolvedDeckEntry>, name: String, section: DeckSection): List<ResolvedDeckEntry> {
        val entry = entries.lastOrNull { it.section == section && sameCard(it, name) } ?: return entries
        return change(entries, key(entry), section, -1)
    }

    /** +1 / −1 on one entry; at zero it leaves the list. */
    fun change(entries: List<ResolvedDeckEntry>, key: String, section: DeckSection, delta: Int): List<ResolvedDeckEntry> =
        entries.mapNotNull { entry ->
            if (key(entry) != key || entry.section != section) entry
            else (entry.quantity + delta).takeIf { it > 0 }?.let { entry.copy(quantity = it) }
        }

    fun increase(kind: CardListKind, entries: List<ResolvedDeckEntry>, key: String, section: DeckSection): CardListEditResult =
        if (total(entries) >= kind.maxCards) CardListEditResult(entries, limit(kind))
        else CardListEditResult(change(entries, key, section, 1))

    fun removeAll(entries: List<ResolvedDeckEntry>, key: String, section: DeckSection) =
        entries.filterNot { key(it) == key && it.section == section }

    /** Moves [quantity] copies (all when null) of one entry to [to], joining that section's entry of the same printing. */
    fun move(entries: List<ResolvedDeckEntry>, key: String, from: DeckSection, to: DeckSection, quantity: Int? = null): List<ResolvedDeckEntry> {
        if (from == to) return entries
        val source = entries.firstOrNull { key(it) == key && it.section == from } ?: return entries
        val moved = (quantity ?: source.quantity).coerceIn(0, source.quantity)
        if (moved == 0) return entries
        val result = change(entries, key, from, -moved).toMutableList()
        val target = result.indexOfFirst { key(it) == key && it.section == to }
        if (target < 0) result += source.copy(section = to, quantity = moved, sourceLines = emptyList())
        else result[target] = result[target].copy(quantity = result[target].quantity + moved)
        return result
    }

    /** The list as text in the editor's syntax, shaped like the text an existing list opens with
     * (DeckEditorSource, CubeEditorSource): "Deck", a deck's "Sideboard", then "Maybeboard" only when it has cards. */
    fun text(kind: CardListKind, entries: List<ResolvedDeckEntry>): String = kind.sections.mapNotNull { section ->
        val lines = entries.filter { it.section == section }.map { entry ->
            val printing = entry.printing
            "${entry.quantity} ${printing.name}" + if (printing.set.isNotBlank() && printing.number.isNotBlank())
                " (${printing.set}) ${printing.number}" else ""
        }
        if (lines.isEmpty() && section == DeckSection.MAYBEBOARD) null
        else (listOf(if (section == DeckSection.MAINBOARD) "Deck" else section.title) + lines).joinToString("\n")
    }.joinToString("\n")

    fun entry(card: CatalogCard, section: DeckSection) = ResolvedDeckEntry(section, 1, card.faces, emptyList())
}

/** What the search page and the Cards tab need from a deck or cube editor. */
interface CardListEditor {
    val kind: CardListKind
    /** The list being edited, or null while it is not resolved (lookup running or problems to fix in the Text tab). */
    val entries: List<ResolvedDeckEntry>?
    val listCatalog: CatalogMetadata?
    val editable: Boolean
    /** Where search adds cards; Main each time the editor opens, then remembered for the edit session. */
    val destination: DeckSection
    fun destination(section: DeckSection)
    /** Replaces the list with [entries] (and the text with its rewrite), keeping it resolved without a new lookup. */
    fun applyCards(entries: List<ResolvedDeckEntry>, catalog: CatalogMetadata?)
    /** A retained card's saved definition, by printing id; null for catalog cards. */
    fun retainedDefinition(printingId: String): CardDefinition?
    /** The latest Cards tab or search message (e.g. a refused add); null clears it. */
    val cardsError: String?
    fun cardsMessage(message: String?)
    val inspection: CardInspectionController
}
