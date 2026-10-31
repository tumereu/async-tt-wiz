package org.asyncttwiz.storage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.UUID

class CatalogAvatarsTest {
    @TempDir lateinit var directory: Path
    private fun card(name: String, types: String, legendary: Boolean = true, token: Boolean = false) =
        CatalogPrinting(UUID.randomUUID().toString(), name, "SET", "1", "English", "", UUID.randomUUID().toString(), token,
            emptyList(), MtgJsonCatalog.retainedRecord(JsonParser.parseString(
                """{"name":"$name","type":"${if (legendary) "Legendary " else ""}$types — Wizard","types":["$types"],"supertypes":[${if (legendary) "\"Legendary\"" else ""}]}""").asJsonObject).toString())

    // LOB-011
    @Test fun randomFilterAcceptsOnlyLegendaryCreaturesAndPlaneswalkersWithArtReferences() {
        val creature = card("Wizard", "Creature")
        val walker = card("Walker", "Planeswalker")
        val others = listOf(card("Common", "Creature", false), card("Land", "Land"), card("Token", "Creature", token = true),
            creature.copy(scryfallId = null), creature.copy(scryfallId = "bad"),
            card("Subtype is not a type", "Artifact — Creature"), card("Not legendary", "NonLegendary Creature", false))
        assertNotNull(creature.avatarChoice(true))
        assertNotNull(walker.avatarChoice(true))
        others.forEach { assertNull(it.avatarChoice(true)) }
        assertNotNull(others.first().avatarChoice(false))
        assertEquals(1, creature.copy(side = "b").avatarChoice(true)!!.avatar.face)
    }

    @Test fun repositoryQueriesPublishedCatalogAndSearchIncludesNonLegendaryCards() {
        val repository = CatalogRepository(directory.toFile()) { JdbcSqliteDriver("jdbc:sqlite:${it.absolutePath}") }
        val cards = listOf(card("Wizard", "Creature"), card("Walker", "Planeswalker"), card("Simple spell", "Sorcery", false), card("Token", "Creature", token = true))
        repository.stage { index -> index.addBatch(cards); CatalogMetadata("2026-10-26", "test") }
        assertEquals(setOf("Wizard", "Walker"), repository.avatarChoices().map { it.name }.toSet())
        assertEquals(listOf("Simple spell"), repository.avatarChoices("spell").map { it.name })
        assertTrue(repository.avatarChoices("%_").isEmpty())
    }

    // LOB-011
    @Test fun randomSampleIsBoundedDeduplicatesReprintsAndRerollsWithoutRepeats() {
        val repository = CatalogRepository(directory.toFile()) { JdbcSqliteDriver("jdbc:sqlite:${it.absolutePath}") }
        val cards = (1..270).map { card("Legend $it", "Creature") }
        repository.stage { index ->
            index.addBatch(cards + cards.map { it.copy(id = UUID.randomUUID().toString()) })
            CatalogMetadata("2026-10-26", "test")
        }
        val first = repository.avatarChoices()
        val second = repository.avatarChoices(previous = first)
        assertEquals(6, first.size)
        assertEquals(6, second.size)
        assertEquals(6, first.map { it.name }.distinct().size)
        assertTrue(first.none { a -> second.any { b -> a.name == b.name } })
        assertTrue((first + second).all { a -> cards.any { it.name == a.name } })
    }

    @Test fun realImporterRetainsTypeLineAndExistingCatalogCanBackfillAndRefreshEligibility() {
        val repository = CatalogRepository(directory.toFile()) { JdbcSqliteDriver("jdbc:sqlite:${it.absolutePath}") }
        val id = UUID.randomUUID().toString()
        val scryfall = UUID.randomUUID().toString()
        val meta = CatalogMetadata("2026-10-26", "5.2.2")
        val input = """{"meta":{"date":"${meta.date}","version":"${meta.version}"},"data":{"SET":{"cards":[
            {"uuid":"$id","name":"Imported legend","number":"1","type":"Legendary Artifact Creature — Wizard",
             "types":["Artifact","Creature"],"supertypes":["Legendary"],"identifiers":{"scryfallId":"$scryfall"}}
        ]}}}"""
        repository.stage { index -> input.byteInputStream().use { MtgJsonCatalog().import(it, index, meta) } }
        val retained = JsonParser.parseString(repository.printing(id)!!.json).asJsonObject
        assertFalse(retained.has("types"))
        assertFalse(retained.has("supertypes"))
        assertEquals("Legendary Artifact Creature — Wizard", retained.get("type").asString)
        // An installed pre-fix database has no eligibility table; no provider re-download is needed.
        val file = directory.resolve(directory.resolve("current").toFile().readText()).toFile()
        JdbcSqliteDriver("jdbc:sqlite:${file.absolutePath}").use { it.execute(null, "DROP TABLE avatar_choice", 0) }
        assertEquals(listOf("Imported legend"), repository.avatarChoices().map { it.name })
        assertEquals(scryfall, repository.avatarChoices().single().avatar.scryfallId)
        repository.stage(copyInstalled = true) { index ->
            index.removeSet("SET")
            index.addBatch(listOf(card("New walker", "Planeswalker")))
            meta
        }
        assertEquals(listOf("New walker"), repository.avatarChoices().map { it.name })
    }
}
