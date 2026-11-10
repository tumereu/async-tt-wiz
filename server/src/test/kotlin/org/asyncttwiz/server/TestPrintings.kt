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

    /** The names of set [set]'s cards (no tokens), each once. */
    fun names(set: String): List<String> = with(catalog.catalog) {
        printings.filter { it.set == set && !it.token }.map { faces[it.face].name }.distinct()
    }

    /** [quantity] copies of [name] in [section] of a deck or cube save. */
    fun entry(name: String, quantity: Int, section: SavedDeckSection = SavedDeckSection.MAINBOARD) = CardListEntry(section, id(name), quantity)
}

/**
 * A deck of card data no catalog has, stored as the database may hold one from before the server built card data from its
 * catalog (owner 2026-11-10): for a test whose cards must be invented card data of its own (one the server can't read,
 * one a test checks the text of). No device can save such a deck any more.
 */
object StoredDecks {
    /** [owner]'s new deck [name] of [entries] (section, card, copies), stored by SQL; its saved form as the server reads it. */
    fun save(runtime: ServiceRuntime, principal: org.asyncttwiz.server.accounts.AccountService.Principal, name: String,
        entries: List<Triple<SavedDeckSection, CardDefinition, Int>>): SavedDeck {
        val owner = principal.account.accountId
        val deck = java.util.UUID.randomUUID()
        runtime.transaction { connection ->
            val sql = org.jooq.impl.DSL.using(connection, org.jooq.SQLDialect.POSTGRES)
            for ((_, card, _) in entries) sql.execute("INSERT INTO definition VALUES (?, 1, ?) ON CONFLICT DO NOTHING",
                DeckJson.digest(card), DeckJson.canonical(card))
            sql.execute("INSERT INTO saved_deck (id, account_id, name) VALUES (?::uuid, ?::uuid, ?)", deck.toString(), owner, name)
            entries.forEachIndexed { index, (section, card, quantity) ->
                sql.execute("INSERT INTO deck_entry VALUES (?::uuid, ?::uuid, ?, ?, ?, ?)", deck.toString(), owner, index, section.name,
                    DeckJson.digest(card), quantity)
            }
        }
        return DeckJson.format.decodeFromString(SavedDeck.serializer(),
            org.asyncttwiz.server.decks.DeckService(runtime).read(principal, deck.toString()).body)
    }

    /** [owner]'s new cube [name] of [cards] ([quantity] each, in Main) stored by SQL on [connection] with their invented card
     * data, as a cube from before may hold it; its id. */
    fun cube(connection: java.sql.Connection, owner: String, name: String, cards: List<CardDefinition>, quantity: Int = 1): String {
        val cube = java.util.UUID.randomUUID().toString()
        val sql = org.jooq.impl.DSL.using(connection, org.jooq.SQLDialect.POSTGRES)
        for (card in cards) sql.execute("INSERT INTO definition VALUES (?, 1, ?) ON CONFLICT DO NOTHING", DeckJson.digest(card), DeckJson.canonical(card))
        sql.execute("INSERT INTO saved_cube (id, account_id, name) VALUES (?::uuid, ?::uuid, ?)", cube, owner, name)
        cards.forEachIndexed { index, card ->
            sql.execute("INSERT INTO cube_entry (cube_id, account_id, position, section, definition_digest, quantity) VALUES (?::uuid, ?::uuid, ?, 'MAINBOARD', ?, ?)",
                cube, owner, index, DeckJson.digest(card), quantity)
        }
        return cube
    }
}
