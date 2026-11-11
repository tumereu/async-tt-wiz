package org.asyncttwiz.android

import org.asyncttwiz.appui.nameUuid
import org.asyncttwiz.protocol.*
import org.asyncttwiz.storage.CatalogMetadata
import org.asyncttwiz.storage.CatalogPrinting
import org.asyncttwiz.storage.DeckSection
import org.asyncttwiz.storage.ResolvedDeckEntry

/**
 * Owner 2026-11-10 (card data from the server's catalog, step 4): saved decks and cubes hold their cards by printing, and
 * the phone builds the cards it shows from its own card records. [read] gives each printing's card's faces, front first,
 * and the records' version (blocking: a worker only); [NONE] has no records.
 */
fun interface SavedCardRecords {
    fun read(printingIds: Collection<String>): SavedCardFaces

    companion object {
        val NONE = SavedCardRecords { SavedCardFaces(emptyMap(), null) }
    }
}

/** One card of a saved list: [quantity] copies in [section] of printing [printingId]'s card, [name] its name. */
data class SavedCard(val section: SavedDeckSection, val printingId: String?, val name: String, val quantity: Int) {
    val key get() = SavedCards.key(printingId, name)
}

fun SavedDeck.cards() = entries.map { SavedCard(it.section, it.printingId, it.name, it.quantity) }
fun SavedCube.cards() = entries.map { SavedCard(it.section, it.printingId, it.name, it.quantity) }

/**
 * The card records' faces of a saved list's printings ([faces]) and the records' version ([catalog]). A card without a
 * printing, or one the records don't have (not installed yet), is a card of its name alone: no picture, and Automatum
 * can't play it (LOB-115).
 */
data class SavedCardFaces(val faces: Map<String, List<CatalogPrinting>>, val catalog: CatalogMetadata?) {
    /** [card]'s card as the server builds it from its catalog, or a card of its name alone ([placeholder]). */
    fun definition(card: SavedCard): CardDefinition = card.printingId?.let { faces[it] }
        ?.let { PhysicalCardFaces.normalize(ResolvedDeckEntry(DeckSection.MAINBOARD, 1, it, emptyList()).definition(catalog)) }
        ?: placeholder(card.printingId, card.name)

    /** Each of [cards]' cards by its key ([SavedCard.key]). */
    fun definitions(cards: List<SavedCard>): Map<String, CardDefinition> = cards.distinctBy { it.key }.associate { it.key to definition(it) }

    /** The printed cards of [cards] as the server builds them, each once: what a deck's selection carries. */
    fun submitted(cards: List<SavedCard>): List<SubmittedDefinition> = cards.filter { it.printingId != null && it.printingId in faces }
        .distinctBy { it.printingId }.map { definition(it).let { card -> SubmittedDefinition(DeckJson.digest(card), card) } }

    /** [cards] as an editor's entries: a printing the records have by its faces, any other card by [placeholderPrinting]. */
    fun entries(cards: List<SavedCard>, section: (SavedDeckSection) -> DeckSection = { DeckSection.valueOf(it.name) }): List<ResolvedDeckEntry> =
        cards.map { card -> ResolvedDeckEntry(section(card.section), card.quantity,
            card.printingId?.let { faces[it] } ?: listOf(placeholderPrinting(card)), emptyList()) }

    /** The cards of [cards] an editor can't build from the records, by their entries' printing ids ([CardListEditor.retainedDefinition]). */
    fun placeholders(cards: List<SavedCard>): Map<String, CardDefinition> =
        cards.filter { it.printingId == null || it.printingId !in faces }.associate { it.key to placeholder(it.printingId, it.name) }

    companion object {
        /** A card of [name] alone, for a card without a printing or one the records don't have ([printingId]). */
        fun placeholder(printingId: String?, name: String) = CardDefinition(name,
            listOf(DefinitionFace(nameUuid(("saved-card:" + (printingId ?: name)).encodeToByteArray()), name)),
            printingId = printingId)

        /** An editor entry's printing for [card] the records can't build: its id is the card's key, so a save names it as
         * the list holds it (a printing id, or "name:" and the name for a card without a printing). */
        fun placeholderPrinting(card: SavedCard) = CatalogPrinting(card.key, card.name, "", "", "English", "", null, false,
            emptyList(), "{}")
    }
}

/** [entry]'s card as a save names it: its printing, or for a card without one its name ([SavedCards.unprinted]). */
fun ResolvedDeckEntry.listEntry(section: SavedDeckSection, quantity: Int = this.quantity): CardListEntry =
    SavedCards.unprinted(printing.id)?.let { name -> CardListEntry(section, null, quantity, name) }
        ?: CardListEntry(section, printing.id, quantity)
