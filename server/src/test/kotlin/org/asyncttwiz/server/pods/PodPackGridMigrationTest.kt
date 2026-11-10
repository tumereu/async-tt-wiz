package org.asyncttwiz.server.pods

import java.util.UUID
import kotlinx.serialization.encodeToString
import org.asyncttwiz.protocol.*
import org.asyncttwiz.server.database.FoundationMigrations
import org.asyncttwiz.server.database.FreshDatabase
import org.asyncttwiz.server.database.TestPostgres
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Multiset drafting (DRF-177): V82 keeps every stored pod readable. A pod of one set becomes that set for every pack,
 * its snapshot unchanged to the byte (a fixed sheet's card order matters); cube pods and presets stay as they were.
 */
class PodPackGridMigrationTest {
    private val json = DeckJson.format
    private fun id() = UUID.randomUUID().toString()
    private fun sql(db: TestPostgres, statement: String) = db.connection().use { it.createStatement().execute(statement) }
    private fun text(db: TestPostgres, query: String): String? = db.connection().use { connection ->
        connection.createStatement().executeQuery(query).use { result -> assertTrue(result.next()); result.getString(1) }
    }

    // DRF-177, SYS-052: stored set pods and presets still read after the change
    @Test fun `a stored pod of one set becomes that set for every pack`() {
        FreshDatabase(migrated = false).start().use { db ->
            Flyway.configure().configuration(FoundationMigrations.flyway(db.settings).configuration).target("81").load().migrate()
            val cards = (1..3).map { SetSourceCard("sha256:" + it.toString().padStart(64, '0'), "00000000-0000-0000-0000-00000000000$it") }
            // A fixed sheet lists its cards out of uuid order on purpose: that order must survive
            val recipe = SetBoosterRecipe(listOf(SetBoosterComposition(mapOf("fixed" to 2), 1)), mapOf("fixed" to SetBoosterSheet(
                linkedMapOf(cards[2].uuid to 1L, cards[0].uuid to 1L), fixed = true)), name = "play")
            val snapshot = SetSourceSnapshot("O'K", "Set's \"name\"", cards, recipe)
            val stored = json.encodeToString(snapshot)
            val account = id(); val setPod = id(); val cubePod = id(); val preset = id()
            fun quoted(value: String) = value.replace("'", "''")
            sql(db, """INSERT INTO account(id,username,username_key,email_delivery,email_key,password_phc) VALUES
                ('$account','A','a','a@example.invalid','a@example.invalid','retained');
                INSERT INTO pod (id, name, creator_id, settings, source_name, source_snapshot) VALUES
                    ('$setPod', 'Set pod', '$account', '{}'::jsonb, 'Set', to_jsonb('${quoted(stored)}'::text)),
                    ('$cubePod', 'Cube pod', '$account', '{}'::jsonb, 'Cube', '[{"definition":"d","quantity":1}]'::jsonb);
                INSERT INTO pod_preset (id, account_id, name, name_key, settings, set_code, set_name) VALUES
                    ('$preset', '$account', 'Old', 'old', '{}', 'DMU', 'Dominaria United');""")
            FoundationMigrations.migrate(db.settings)
            val migrated = json.decodeFromString<String>(text(db, "SELECT source_snapshot::text FROM pod WHERE id = '$setPod'")!!)
            assertEquals(SetPackSources(listOf("O'K"), listOf(snapshot)), json.decodeFromString<SetPackSources>(migrated))
            assertTrue(migrated.contains(stored), "the snapshot is kept as it was stored")
            assertEquals("[{\"quantity\": 1, \"definition\": \"d\"}]", text(db, "SELECT source_snapshot::text FROM pod WHERE id = '$cubePod'"))
            assertNull(text(db, "SELECT pack_sets FROM pod_preset WHERE id = '$preset'"))
        }
    }
}
