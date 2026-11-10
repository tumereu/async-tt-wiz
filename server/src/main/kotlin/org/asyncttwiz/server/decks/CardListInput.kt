package org.asyncttwiz.server.decks

import org.asyncttwiz.protocol.CardListEntry
import org.asyncttwiz.protocol.DeckValidation
import org.asyncttwiz.protocol.SavedDeckSection
import org.asyncttwiz.server.catalog.ServerCatalogs

/**
 * A deck or cube save's cards (owner 2026-11-10: card data from the server's catalog): each entry's printing, checked
 * against the server's catalog, stored by the printing id of its card's front face with the card's name; an entry of a
 * card without a printing names it by name ([kept]), kept only while its list already holds it. Entries of the same card
 * and section become one, their copies added up, in the order first met.
 */
internal class CardListInput private constructor(val entries: List<Entry>, val kept: Set<String>) {
    data class Entry(val section: SavedDeckSection, val printingId: String?, val name: String, val quantity: Int)

    /** Why a save's cards can't be built: [status] and [code] for the reply. */
    class Refused(val status: Int, val code: String)

    companion object {
        /** [input]'s cards checked against the current catalog, or why not: no catalog (503), a printing it doesn't have or
         * card data that isn't valid (400). Outside the request's transaction: a refusal here isn't kept as its outcome. */
        fun build(input: List<CardListEntry>): Pair<CardListInput?, Refused?> {
            val catalog = ServerCatalogs.current() ?: return null to Refused(503, "catalog_unavailable")
            val cards = HashMap<String, Pair<String, String>>()
            val kept = HashSet<String>()
            val copies = LinkedHashMap<Triple<SavedDeckSection, String?, String>, Int>()
            for (entry in input) {
                val key = when (val printing = entry.printingId) {
                    null -> {
                        val name = entry.name ?: return null to Refused(400, "invalid_request")
                        kept += name
                        Triple(entry.section, null, name)
                    }
                    else -> {
                        val (front, name) = cards[printing] ?: run {
                            val card = catalog.card(printing) ?: return null to Refused(400, "definitions_unavailable")
                            if (!DeckValidation.definition(card)) return null to Refused(400, "invalid_definition")
                            ((card.printingId ?: printing) to card.name).also { cards[printing] = it }
                        }
                        Triple(entry.section, front, name)
                    }
                }
                copies.merge(key, entry.quantity, Int::plus)
            }
            return CardListInput(copies.map { (key, quantity) -> Entry(key.first, key.second, key.third, quantity) }, kept) to null
        }
    }
}
