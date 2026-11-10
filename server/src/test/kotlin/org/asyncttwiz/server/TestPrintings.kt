package org.asyncttwiz.server

import org.asyncttwiz.protocol.CardDefinition
import org.asyncttwiz.protocol.CardListEntry
import org.asyncttwiz.protocol.DeckJson
import org.asyncttwiz.protocol.SavedDeck
import org.asyncttwiz.protocol.SavedDeckSection
import org.asyncttwiz.protocol.SubmittedDefinition
import org.asyncttwiz.server.catalog.ServerCatalog
import org.asyncttwiz.server.catalog.ServerCatalogs

/**
 * The cards of the test cards' catalog the server tests read (atw.catalogDir: tests/fixtures/card-catalog, its E2E and
 * M21 sets generated from the e2e TestCards), by name: a card's printing to save in decks and cubes (owner 2026-11-10:
 * card data from the server's catalog), and its card data as the server builds it from the catalog.
 */
object TestPrintings {
    val catalog: ServerCatalog get() = checkNotNull(ServerCatalogs.current()) { "No test cards' catalog: run the server tests with Gradle (atw.catalogDir)" }

    /** The printing id of the card named [name] (the first face's, of its first printing). */
    fun id(name: String): String = with(catalog.catalog) {
        printings.firstOrNull { !it.token && faces[it.face].name == name }?.id ?: error("No card $name in the test cards' catalog")
    }

    /** The card data of [name] as a deck or cube holds it. */
    fun definition(name: String): CardDefinition = checkNotNull(catalog.card(id(name)))
    fun digest(name: String): String = DeckJson.digest(definition(name))
    fun submitted(name: String) = definition(name).let { SubmittedDefinition(DeckJson.digest(it), it) }

    /** The names of set [set]'s cards Automatum plays (the catalog's marks; no tokens, front faces), each once. */
    fun names(set: String): List<String> = supported().filter { name -> with(catalog.catalog) {
        printings.any { it.set == set && !it.token && faces[it.face].name == name } } }

    /** The names of the catalog's cards Automatum plays (its marks; no tokens, front faces), each once, in catalog order. */
    fun supported(): List<String> = with(catalog.catalog) {
        printings.filter { !it.token && faces[it.face].side.let { side -> side == "" || side == "a" } && catalog.supported(it.id) }
            .map { faces[it.face].name }.distinct()
    }

    /** [quantity] copies of [name] in [section] of a deck or cube save. */
    fun entry(name: String, quantity: Int, section: SavedDeckSection = SavedDeckSection.MAINBOARD) = CardListEntry(section, id(name), quantity)
}

/** Saved decks stored by SQL, as the database may hold them: with a card without a printing (owner 2026-11-10), which no
 * device can save. */
object StoredDecks {
    /** [owner]'s new deck [name] of [entries]; its saved form as the server reads it. */
    fun save(runtime: ServiceRuntime, principal: org.asyncttwiz.server.accounts.AccountService.Principal, name: String,
        entries: List<org.asyncttwiz.protocol.SavedDeckEntry>): SavedDeck {
        val owner = java.util.UUID.fromString(principal.account.accountId)
        val deck = java.util.UUID.randomUUID()
        runtime.transaction { connection ->
            val sql = org.jooq.impl.DSL.using(connection, org.jooq.SQLDialect.POSTGRES)
            sql.execute("INSERT INTO saved_deck (id, account_id, name) VALUES (?::uuid, ?::uuid, ?)", deck.toString(), owner.toString(), name)
            org.asyncttwiz.server.decks.DeckService.insertEntries(sql, deck, owner, entries)
        }
        return DeckJson.format.decodeFromString(SavedDeck.serializer(),
            org.asyncttwiz.server.decks.DeckService(runtime).read(principal, deck.toString()).body)
    }
}
