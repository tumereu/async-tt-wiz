package org.asyncttwiz.server.decks

import java.util.UUID
import java.util.concurrent.TimeUnit
import org.asyncttwiz.protocol.*
import org.asyncttwiz.server.ServiceRuntime
import org.asyncttwiz.server.TestPrintings
import org.asyncttwiz.server.database.FoundationMigrations
import org.asyncttwiz.server.database.FreshDatabase
import org.asyncttwiz.server.database.TestPostgres
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Owner 2026-11-10 (card data from the server's catalog, step 4): V80 turns saved decks' and cubes' rows of card data into
 * rows of printings, merging rows that then hold the same card in a section; the startup job finds cards whose printing the
 * catalog lacks again by name, else leaves them without a printing (SYS-084).
 */
class SavedPrintingsTest {
    private fun id() = UUID.randomUUID().toString()
    private fun sql(db: TestPostgres, statement: String) = db.connection().use { it.createStatement().execute(statement) }
    private fun rows(db: TestPostgres, query: String): List<List<Any?>> = db.connection().use { connection ->
        connection.createStatement().executeQuery(query).use { result ->
            buildList { while (result.next()) add((1..result.metaData.columnCount).map(result::getObject)) }
        }
    }
    private fun canonical(card: CardDefinition) = DeckJson.canonical(card).replace("'", "''")

    @Test fun `the migration names each saved card by printing, merges duplicates and the job finds missing printings by name`() {
        FreshDatabase(migrated = false).start().use { db ->
            Flyway.configure().configuration(FoundationMigrations.flyway(db.settings).configuration).target("79").load().migrate()
            val pup = TestPrintings.submitted("Ash Pup")
            val pupId = pup.definition.printingId!!
            // The same printing as card data of an earlier catalog (other text): its rows merge with Ash Pup's
            val oldPup = pup.definition.copy(sourceVersion = "2025-01-01/5.2.0", faces = pup.definition.faces.map { it.copy(rulesText = "Old wording.") })
            val cub = TestPrintings.submitted("Ember Cub")
            val cubId = cub.definition.printingId!!
            // Card data without a printing (found again by name) and of a printing no catalog has (its name unknown too)
            val nameOnly = CardDefinition("Ember Cub", listOf(DefinitionFace(id(), "Ember Cub")))
            val goneId = id()
            val gone = CardDefinition("Vanished Card", listOf(DefinitionFace(id(), "Vanished Card")), printingId = goneId)
            val cards = listOf(pup.definition, oldPup, cub.definition, nameOnly, gone)
            val (pupD, oldD, cubD, nameD, goneD) = cards.map(DeckJson::digest)
            val account = id(); val deck = id(); val cube = id()
            sql(db, """INSERT INTO account(id,username,username_key,email_delivery,email_key,password_phc) VALUES
                ('$account','A','a','a@example.invalid','a@example.invalid','retained');
                ${cards.joinToString("\n") { "INSERT INTO definition VALUES ('${DeckJson.digest(it)}', 1, '${canonical(it)}');" }}
                INSERT INTO saved_deck(id,account_id,name,cover_digest) VALUES ('$deck','$account','Old deck','$oldD');
                INSERT INTO deck_entry VALUES ('$deck','$account',0,'MAINBOARD','$pupD',2), ('$deck','$account',1,'MAINBOARD','$oldD',3),
                    ('$deck','$account',2,'SIDEBOARD','$pupD',1), ('$deck','$account',3,'MAINBOARD','$nameD',1),
                    ('$deck','$account',4,'MAINBOARD','$cubD',2), ('$deck','$account',5,'MAINBOARD','$goneD',1);
                INSERT INTO saved_cube(id,account_id,name) VALUES ('$cube','$account','Old cube');
                INSERT INTO cube_entry(cube_id,account_id,position,section,definition_digest,quantity) VALUES
                    ('$cube','$account',0,'MAINBOARD','$pupD',1), ('$cube','$account',1,'MAINBOARD','$oldD',1),
                    ('$cube','$account',2,'MAYBEBOARD','$goneD',1);""")
            FoundationMigrations.migrate(db.settings)
            val deckRows = "SELECT position, section, printing_id, name, quantity FROM deck_entry WHERE deck_id = '$deck' ORDER BY position"
            val cubeRows = "SELECT position, section, printing_id, name, quantity FROM cube_entry WHERE cube_id = '$cube' ORDER BY position"
            assertEquals(listOf(listOf(0, "MAINBOARD", pupId, "Ash Pup", 5), listOf(2, "SIDEBOARD", pupId, "Ash Pup", 1),
                listOf(3, "MAINBOARD", null, "Ember Cub", 1), listOf(4, "MAINBOARD", cubId, "Ember Cub", 2),
                listOf(5, "MAINBOARD", goneId, "Vanished Card", 1)), rows(db, deckRows))
            assertEquals(listOf(listOf(pupId)), rows(db, "SELECT cover_printing FROM saved_deck WHERE id = '$deck'"))
            assertEquals(listOf(listOf(0, "MAINBOARD", pupId, "Ash Pup", 2), listOf(2, "MAYBEBOARD", goneId, "Vanished Card", 1)), rows(db, cubeRows))
            // The card data stays for games and pods
            assertEquals(listOf(listOf(5L)), rows(db, "SELECT count(*) FROM definition"))

            ServiceRuntime(db.settings).use { runtime ->
                runtime.start()
                val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
                while (!runtime.ready()) { assertTrue(System.nanoTime() < end); Thread.sleep(20) }
                val catalog = TestPrintings.catalog
                // Ember Cub without a printing is found by name and joins the Ember Cub row; Vanished Card stays without one
                assertEquals(SavedPrintings.Outcome(byId = 4, byName = 1, unresolved = 2, lists = 2), SavedPrintings.resolve(runtime, catalog))
                assertEquals(listOf(listOf(0, "MAINBOARD", pupId, "Ash Pup", 5), listOf(1, "SIDEBOARD", pupId, "Ash Pup", 1),
                    listOf(2, "MAINBOARD", cubId, "Ember Cub", 3), listOf(3, "MAINBOARD", null, "Vanished Card", 1)), rows(db, deckRows))
                assertEquals(listOf(listOf(0, "MAINBOARD", pupId, "Ash Pup", 2), listOf(1, "MAYBEBOARD", null, "Vanished Card", 1)), rows(db, cubeRows))
                assertEquals(listOf(listOf(pupId, 2L)), rows(db, "SELECT cover_printing, revision FROM saved_deck WHERE id = '$deck'"))
                // Again: nothing changes (the card without a printing is looked for again)
                assertEquals(SavedPrintings.Outcome(byId = 4, byName = 0, unresolved = 2, lists = 0), SavedPrintings.resolve(runtime, catalog))
                assertEquals(listOf(listOf(pupId, 2L)), rows(db, "SELECT cover_printing, revision FROM saved_deck WHERE id = '$deck'"))
            }
        }
    }

    // Owner 2026-11-10: a card found again by name gets a front face's printing of a card of that name, case ignored
    @Test fun `a card is found again by its name`() {
        val catalog = TestPrintings.catalog
        assertEquals(TestPrintings.id("Ember Cub"), catalog.printingNamed("ember CUB"))
        assertEquals(TestPrintings.id("Ember Path // Ember Crag"), catalog.printingNamed("Ember Path // Ember Crag"))
        assertNull(catalog.printingNamed("Vanished Card"))
    }
}
