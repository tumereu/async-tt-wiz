package org.asyncttwiz.server.pods

import java.util.UUID
import org.asyncttwiz.protocol.*
import org.asyncttwiz.server.database.FoundationMigrations
import org.asyncttwiz.server.database.FreshDatabase
import org.asyncttwiz.server.database.TestPostgres
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/** DRF-031, DRF-200 (owner, 2026-11-14): V88 gives every existing account the Glimpse preset as an ordinary preset of its own. */
class GlimpsePresetMigrationTest {
    private val json = DeckJson.format
    private fun id() = UUID.randomUUID().toString()
    private fun sql(db: TestPostgres, statement: String) = db.connection().use { it.createStatement().execute(statement) }
    private fun rows(db: TestPostgres, query: String): List<List<String?>> = db.connection().use { connection ->
        connection.createStatement().executeQuery(query).use { result ->
            buildList { while (result.next()) add((1..result.metaData.columnCount).map { result.getString(it) }) }
        }
    }

    // DRF-031, DRF-200: each account gets Glimpse (DraftSettings.glimpse(), no source); one that already has a preset of that name
    // keeps its own, and other presets stay as they were
    @Test fun `every existing account gets the Glimpse preset`() {
        FreshDatabase(migrated = false).start().use { db ->
            Flyway.configure().configuration(FoundationMigrations.flyway(db.settings).configuration).target("87").load().migrate()
            val plain = id(); val owner = id(); val own = id(); val other = id()
            sql(db, """INSERT INTO account(id,username,username_key,email_delivery,email_key,password_phc) VALUES
                ('$plain','A','a','a@example.invalid','a@example.invalid','retained'),
                ('$owner','B','b','b@example.invalid','b@example.invalid','retained');
                INSERT INTO pod_preset (id, account_id, name, name_key, settings) VALUES
                    ('$own', '$owner', 'GLIMPSE', 'glimpse', '{"packsPerPlayer":5}'),
                    ('$other', '$owner', 'Cube night', 'cube night', '{}');""")
            FoundationMigrations.migrate(db.settings)
            val added = rows(db, "SELECT name, settings, cube_id, set_code, pack_sets FROM pod_preset WHERE account_id = '$plain'").single()
            assertEquals("Glimpse", added[0])
            assertEquals(DraftSettings.glimpse(), json.decodeFromString<DraftSettings>(added[1]!!))
            assertEquals(listOf(null, null, null), added.drop(2))
            assertEquals(listOf(listOf(own, "GLIMPSE", "{\"packsPerPlayer\":5}"), listOf(other, "Cube night", "{}")),
                rows(db, "SELECT id, name, settings FROM pod_preset WHERE account_id = '$owner' ORDER BY name_key DESC"))
        }
    }
}
