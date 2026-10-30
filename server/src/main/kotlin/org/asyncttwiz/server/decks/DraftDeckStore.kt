package org.asyncttwiz.server.decks

import java.util.UUID
import org.asyncttwiz.protocol.*
import org.asyncttwiz.server.generated.tables.AccountDefinition.ACCOUNT_DEFINITION
import org.asyncttwiz.server.generated.tables.DeckEntry.DECK_ENTRY
import org.asyncttwiz.server.generated.tables.Definition.DEFINITION
import org.asyncttwiz.server.generated.tables.SavedDeck.SAVED_DECK
import org.jooq.DSLContext

/** Draft decks: one saved deck per pod membership, kept from the member's latest submitted pod deck.
 * Games never depend on a saved deck (selection copies it), so evicting one cannot affect any game. */
internal object DraftDeckStore {
    /** Main = submitted Main cards and basics; Side = the rest of the drafted pool and the Side basics.
     * Basics are left out when [basicDefinitions] is null. Empty when the result would not be a valid saved deck. */
    fun entries(build: PodDeckBuild, pool: List<String>, definitions: Map<String, String>,
                basicDefinitions: Map<BasicLand, String>?): List<SavedDeckEntry> {
        val main = build.copies.filter { it.category == PodDeckCategory.MAIN }.map { it.copyId }
        val inMain = main.toSet()
        fun collect(sideBasics: Boolean): List<SavedDeckEntry> {
            val counts = linkedMapOf<Pair<SavedDeckSection, String>, Int>()
            fun add(section: SavedDeckSection, digest: String, quantity: Int) {
                if (quantity > 0) counts[section to digest] = (counts[section to digest] ?: 0) + quantity
            }
            fun basics(section: SavedDeckSection, values: PodDeckBasics) = basicDefinitions?.let { lands ->
                for (land in BasicLand.entries) add(section, lands.getValue(land), land.count(values))
            }
            main.forEach { add(SavedDeckSection.MAINBOARD, definitions.getValue(it), 1) }
            basics(SavedDeckSection.MAINBOARD, build.basics)
            pool.filter { it !in inMain }.forEach { add(SavedDeckSection.SIDEBOARD, definitions.getValue(it), 1) }
            if (sideBasics) basics(SavedDeckSection.SIDEBOARD, build.sideBasics)
            return counts.map { (key, quantity) -> SavedDeckEntry(key.first, key.second, quantity) }
        }
        // Side basics are supplied extras: dropped first when the whole would exceed the deck size bound.
        return collect(true).takeIf(DeckValidation::entries) ?: collect(false).takeIf(DeckValidation::entries) ?: emptyList()
    }

    /** Creates or replaces [owner]'s draft deck for [pod]; a new one may evict the oldest beyond the limit.
     * Every digest must already be a stored definition. */
    fun save(sql: DSLContext, owner: UUID, pod: UUID, podName: String, name: String, entries: List<SavedDeckEntry>) {
        // Defensive: a pool card without a stored definition is left out rather than failing the submission.
        val stored = sql.select(DEFINITION.DIGEST).from(DEFINITION).where(DEFINITION.DIGEST.`in`(entries.map { it.definition }.toSet()))
            .fetchSet(DEFINITION.DIGEST)
        val entries = entries.filter { it.definition in stored }
        if (entries.isEmpty()) return
        for (digest in entries.map { it.definition }.toSet()) sql.insertInto(ACCOUNT_DEFINITION)
            .set(ACCOUNT_DEFINITION.ACCOUNT_ID, owner).set(ACCOUNT_DEFINITION.DIGEST, digest).onConflictDoNothing().execute()
        val existing = sql.selectFrom(SAVED_DECK).where(SAVED_DECK.ACCOUNT_ID.eq(owner)).and(SAVED_DECK.DRAFT_POD_ID.eq(pod))
            .forUpdate().fetchOne()
        val id = existing?.id ?: UUID.randomUUID()
        if (existing == null) {
            sql.insertInto(SAVED_DECK).set(SAVED_DECK.ID, id).set(SAVED_DECK.ACCOUNT_ID, owner).set(SAVED_DECK.NAME, name)
                .set(SAVED_DECK.DRAFT_POD_ID, pod).set(SAVED_DECK.DRAFT_POD_NAME, podName).execute()
        } else {
            sql.deleteFrom(DECK_ENTRY).where(DECK_ENTRY.DECK_ID.eq(id)).execute()
            sql.update(SAVED_DECK).set(SAVED_DECK.NAME, name).set(SAVED_DECK.DRAFT_POD_NAME, podName)
                .set(SAVED_DECK.COVER_DIGEST, existing.coverDigest?.takeIf { cover -> entries.any { it.definition == cover } })
                .set(SAVED_DECK.REVISION, SAVED_DECK.REVISION.plus(1L)).where(SAVED_DECK.ID.eq(id)).execute()
        }
        entries.forEachIndexed { index, entry ->
            sql.insertInto(DECK_ENTRY).set(DECK_ENTRY.DECK_ID, id).set(DECK_ENTRY.ACCOUNT_ID, owner)
                .set(DECK_ENTRY.POSITION, index).set(DECK_ENTRY.SECTION, entry.section.name)
                .set(DECK_ENTRY.DEFINITION_DIGEST, entry.definition).set(DECK_ENTRY.QUANTITY, entry.quantity).execute()
        }
        if (existing == null) evict(sql, owner)
    }

    /** Follows the member's private pod deck name, if their draft deck exists. */
    fun rename(sql: DSLContext, owner: UUID, pod: UUID, name: String) {
        sql.update(SAVED_DECK).set(SAVED_DECK.NAME, name).set(SAVED_DECK.REVISION, SAVED_DECK.REVISION.plus(1L))
            .where(SAVED_DECK.ACCOUNT_ID.eq(owner)).and(SAVED_DECK.DRAFT_POD_ID.eq(pod)).and(SAVED_DECK.NAME.ne(name)).execute()
    }

    /** Keeps the newest [DraftDecks.LIMIT] draft decks by creation. */
    private fun evict(sql: DSLContext, owner: UUID) {
        val stale = sql.select(SAVED_DECK.ID).from(SAVED_DECK).where(SAVED_DECK.ACCOUNT_ID.eq(owner))
            .and(SAVED_DECK.DRAFT_POD_ID.isNotNull).orderBy(SAVED_DECK.CREATED_AT.desc(), SAVED_DECK.ID.desc())
            .offset(DraftDecks.LIMIT).forUpdate().fetch(SAVED_DECK.ID)
        if (stale.isNotEmpty()) sql.deleteFrom(SAVED_DECK).where(SAVED_DECK.ID.`in`(stale)).execute()
    }
}
