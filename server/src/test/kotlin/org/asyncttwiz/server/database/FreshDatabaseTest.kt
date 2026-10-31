package org.asyncttwiz.server.database

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/** The shared-container fixture must give each test what a fresh owned container plus migration gave it. */
class FreshDatabaseTest {
    private fun TestPostgres.tables(): List<String> = connection().use { c ->
        c.createStatement().executeQuery("SELECT tablename FROM pg_tables WHERE schemaname = 'public' ORDER BY tablename").use { rows ->
            buildList { while (rows.next()) add(rows.getString(1)) }
        }
    }
    private fun TestPostgres.rows(table: String): Int = connection().use { c ->
        c.createStatement().executeQuery("SELECT count(*) FROM \"$table\"").use { it.next(); it.getInt(1) }
    }
    private fun TestPostgres.name(): String = connection().use { c ->
        c.createStatement().executeQuery("SELECT current_database()").use { it.next(); it.getString(1) }
    }

    @Test fun `fresh databases are freshly migrated or empty, isolated from each other and dropped on close`() {
        lateinit var dropped: String
        FreshDatabase().start().use { first ->
            FreshDatabase().start().use { second ->
                assertNotEquals(first.name(), second.name())
                // Fully migrated: nothing pending, and only V1's service_writer seed row exists.
                assertEquals(0, FoundationMigrations.migrate(first.settings))
                assertEquals(MigrationTestSupport.supportedVersions(first.settings),
                    FoundationMigrations.flyway(first.settings).info().applied().map { it.version.toString() })
                for (table in first.tables() - "flyway_schema_history")
                    assertEquals(if (table == "service_writer") 1 else 0, first.rows(table), table)
                first.connection().use { it.createStatement().execute("UPDATE service_writer SET epoch = epoch + 7") }
                second.connection().use { c ->
                    c.createStatement().executeQuery("SELECT epoch FROM service_writer").use { assertTrue(it.next()); assertEquals(0, it.getLong(1)) }
                }
            }
            dropped = first.name()
        }
        FreshDatabase(migrated = false).start().use { empty ->
            assertEquals(emptyList<String>(), empty.tables())
            assertEquals(0, empty.connection().use { c ->
                c.createStatement().executeQuery("SELECT count(*) FROM pg_database WHERE datname = '$dropped'").use { it.next(); it.getInt(1) }
            })
        }
    }
}
