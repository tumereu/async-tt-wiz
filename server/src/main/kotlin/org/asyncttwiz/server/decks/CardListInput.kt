package org.asyncttwiz.server.decks

import org.asyncttwiz.protocol.CardDefinition
import org.asyncttwiz.protocol.CardListEntry
import org.asyncttwiz.protocol.DeckJson
import org.asyncttwiz.protocol.DeckValidation
import org.asyncttwiz.protocol.SavedDeckSection
import org.asyncttwiz.server.catalog.ServerCatalogs
import org.asyncttwiz.server.generated.tables.Definition.DEFINITION
import org.jooq.DSLContext

/**
 * A deck or cube save's cards (owner 2026-11-10: card data from the server's catalog): each entry's printing becomes the
 * card data a device made of it before ([org.asyncttwiz.server.catalog.ServerCatalog.card]), so the stored rows and digests
 * are the ones they always were; an entry naming the digest of a card its list already holds ([kept]) keeps that card as
 * stored. Entries of the same card and section become one, their copies added up, in the order first met.
 */
internal class CardListInput private constructor(val entries: List<Entry>, val definitions: Map<String, CardDefinition>,
    val kept: Set<String>) {
    data class Entry(val section: SavedDeckSection, val digest: String, val quantity: Int)

    /** Why a save's cards can't be built: [status] and [code] for the reply. */
    class Refused(val status: Int, val code: String)

    /** Stores the built cards' card data the `definition` table doesn't have yet, in one statement. */
    fun store(sql: DSLContext) {
        if (definitions.isEmpty()) return
        var insert = sql.insertInto(DEFINITION, DEFINITION.DIGEST, DEFINITION.SCHEMA_VERSION, DEFINITION.PAYLOAD)
        for ((digest, definition) in definitions) insert = insert.values(digest, definition.schemaVersion, DeckJson.canonical(definition))
        insert.onConflictDoNothing().execute()
    }

    companion object {
        /** [input]'s cards built from the current catalog, or why not: no catalog (503), a printing it doesn't have or card
         * data that isn't valid (400). Outside the request's transaction: a refusal here isn't kept as its outcome. */
        fun build(input: List<CardListEntry>): Pair<CardListInput?, Refused?> {
            val catalog = ServerCatalogs.current() ?: return null to Refused(503, "catalog_unavailable")
            val definitions = LinkedHashMap<String, CardDefinition>()
            val digests = HashMap<String, String>()
            val kept = HashSet<String>()
            val copies = LinkedHashMap<Pair<SavedDeckSection, String>, Int>()
            for (entry in input) {
                val digest = if (DeckValidation.digest(entry.printingId)) entry.printingId.also { kept += it }
                    else digests[entry.printingId] ?: run {
                        val definition = catalog.card(entry.printingId) ?: return null to Refused(400, "definitions_unavailable")
                        if (!DeckValidation.definition(definition)) return null to Refused(400, "invalid_definition")
                        DeckJson.digest(definition).also { digests[entry.printingId] = it; definitions[it] = definition }
                    }
                copies.merge(entry.section to digest, entry.quantity, Int::plus)
            }
            return CardListInput(copies.map { (key, quantity) -> Entry(key.first, key.second, quantity) }, definitions, kept) to null
        }
    }
}
