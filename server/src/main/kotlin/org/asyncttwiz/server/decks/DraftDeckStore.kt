package org.asyncttwiz.server.decks

import java.util.UUID
import org.asyncttwiz.protocol.*
import org.asyncttwiz.server.generated.tables.DeckEntry.DECK_ENTRY
import org.asyncttwiz.server.generated.tables.SavedDeck.SAVED_DECK
import org.jooq.DSLContext

/** Draft decks: one saved deck per pod membership, kept from the member's latest submitted pod deck.
 * Games never depend on a saved deck (selection copies it), so evicting one cannot affect any game. */
internal object DraftDeckStore {
    /** One card of a draft deck as the pod keeps it: [quantity] copies of card data [digest] in [section]. */
    data class Entry(val section: SavedDeckSection, val digest: String, val quantity: Int)

    /** Main = submitted Main cards and basics; Side = submitted Side cards and the Side basics; Maybe = the Build's Maybe
     * (LOB-055). Basics are left out when [basicDefinitions] is null. Empty when the result would not be a valid saved deck. */
    fun entries(build: PodDeckBuild, pool: List<String>, definitions: Map<String, String>,
                basicDefinitions: Map<BasicLand, String>?): List<Entry> {
        val category = build.copies.associate { it.copyId to it.category }
        val main = build.copies.filter { it.category == PodDeckCategory.MAIN }.map { it.copyId }
        val inMain = main.toSet()
        fun collect(sideBasics: Boolean): List<Entry> {
            val counts = linkedMapOf<Pair<SavedDeckSection, String>, Int>()
            fun add(section: SavedDeckSection, digest: String, quantity: Int) {
                if (quantity > 0) counts[section to digest] = (counts[section to digest] ?: 0) + quantity
            }
            fun basics(section: SavedDeckSection, values: PodDeckBasics) = basicDefinitions?.let { lands ->
                for (land in BasicLand.entries) add(section, lands.getValue(land), land.count(values))
            }
            main.forEach { add(SavedDeckSection.MAINBOARD, definitions.getValue(it), 1) }
            basics(SavedDeckSection.MAINBOARD, build.basics)
            val rest = pool.filter { it !in inMain }
            rest.filter { category[it] != PodDeckCategory.MAYBE }.forEach { add(SavedDeckSection.SIDEBOARD, definitions.getValue(it), 1) }
            if (sideBasics) basics(SavedDeckSection.SIDEBOARD, build.sideBasics)
            rest.filter { category[it] == PodDeckCategory.MAYBE }.forEach { add(SavedDeckSection.MAYBEBOARD, definitions.getValue(it), 1) }
            return counts.map { (key, quantity) -> Entry(key.first, key.second, quantity) }
        }
        fun valid(entries: List<Entry>) = entries.isNotEmpty() && DeckValidation.quantities(entries.map { it.quantity })
        // Side basics are supplied extras: dropped first when the whole would exceed the deck size bound.
        return collect(true).takeIf(::valid) ?: collect(false).takeIf(::valid) ?: emptyList()
    }

    /** Creates or replaces [owner]'s draft deck for [pod]; a new one may evict the oldest beyond the limit. The deck holds
     * the pod's cards by their printings (owner 2026-11-10: card data from the server's catalog), the same card in a
     * section once; card data without a printing is a card of its name alone. [cover] is the member's chosen cover
     * (DRF-166, by digest), if any. */
    fun save(sql: DSLContext, owner: UUID, pod: UUID, podName: String, name: String, entries: List<Entry>, cover: String? = null) {
        val cards = StoredDefinitions.read(sql, entries.map { it.digest }.toSet() + listOfNotNull(cover))
            .associate { it.digest to it.definition }
        // Defensive: a pool card without a stored definition is left out rather than failing the submission.
        val merged = LinkedHashMap<Pair<SavedDeckSection, String>, SavedDeckEntry>()
        for (entry in entries) {
            val card = cards[entry.digest] ?: continue
            val saved = SavedDeckEntry(entry.section, card.printingId, card.name, entry.quantity)
            merged.merge(entry.section to saved.card, saved) { a, b -> a.copy(quantity = a.quantity + b.quantity) }
        }
        val rows = merged.values.toList()
        if (rows.isEmpty()) return
        val coverPrinting = cover?.let { cards[it]?.printingId }
        val existing = sql.selectFrom(SAVED_DECK).where(SAVED_DECK.ACCOUNT_ID.eq(owner)).and(SAVED_DECK.DRAFT_POD_ID.eq(pod))
            .forUpdate().fetchOne()
        val id = existing?.id ?: UUID.randomUUID()
        if (existing == null) {
            sql.insertInto(SAVED_DECK).set(SAVED_DECK.ID, id).set(SAVED_DECK.ACCOUNT_ID, owner).set(SAVED_DECK.NAME, name)
                .set(SAVED_DECK.DRAFT_POD_ID, pod).set(SAVED_DECK.DRAFT_POD_NAME, podName)
                .set(SAVED_DECK.COVER_PRINTING, coverPrinting?.takeIf { chosen -> rows.any { it.printingId == chosen } }).execute()
        } else {
            sql.deleteFrom(DECK_ENTRY).where(DECK_ENTRY.DECK_ID.eq(id)).execute()
            sql.update(SAVED_DECK).set(SAVED_DECK.NAME, name).set(SAVED_DECK.DRAFT_POD_NAME, podName)
                .set(SAVED_DECK.COVER_PRINTING, (coverPrinting ?: existing.coverPrinting)?.takeIf { chosen -> rows.any { it.printingId == chosen } })
                .set(SAVED_DECK.REVISION, SAVED_DECK.REVISION.plus(1L)).where(SAVED_DECK.ID.eq(id)).execute()
        }
        DeckService.insertEntries(sql, id, owner, rows)
        if (existing == null) evict(sql, owner)
    }

    /** Follows the member's private pod deck name, if their draft deck exists. */
    fun rename(sql: DSLContext, owner: UUID, pod: UUID, name: String) {
        sql.update(SAVED_DECK).set(SAVED_DECK.NAME, name).set(SAVED_DECK.REVISION, SAVED_DECK.REVISION.plus(1L))
            .where(SAVED_DECK.ACCOUNT_ID.eq(owner)).and(SAVED_DECK.DRAFT_POD_ID.eq(pod)).and(SAVED_DECK.NAME.ne(name)).execute()
    }

    /** Follows the member's private pod deck cover (DRF-166, card data [cover]), by its printing, if their draft deck exists. */
    fun cover(sql: DSLContext, owner: UUID, pod: UUID, cover: String) {
        val printing = StoredDefinitions.read(sql, setOf(cover)).singleOrNull()?.definition?.printingId ?: return
        sql.update(SAVED_DECK).set(SAVED_DECK.COVER_PRINTING, printing).set(SAVED_DECK.REVISION, SAVED_DECK.REVISION.plus(1L))
            .where(SAVED_DECK.ACCOUNT_ID.eq(owner)).and(SAVED_DECK.DRAFT_POD_ID.eq(pod))
            .and(SAVED_DECK.COVER_PRINTING.isDistinctFrom(printing)).execute()
    }

    /** Keeps the newest [DraftDecks.LIMIT] draft decks by creation. */
    private fun evict(sql: DSLContext, owner: UUID) {
        val stale = sql.select(SAVED_DECK.ID).from(SAVED_DECK).where(SAVED_DECK.ACCOUNT_ID.eq(owner))
            .and(SAVED_DECK.DRAFT_POD_ID.isNotNull).orderBy(SAVED_DECK.CREATED_AT.desc(), SAVED_DECK.ID.desc())
            .offset(DraftDecks.LIMIT).forUpdate().fetch(SAVED_DECK.ID)
        if (stale.isNotEmpty()) sql.deleteFrom(SAVED_DECK).where(SAVED_DECK.ID.`in`(stale)).execute()
    }
}
