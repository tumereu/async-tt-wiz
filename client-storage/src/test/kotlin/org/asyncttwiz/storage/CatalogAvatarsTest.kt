package org.asyncttwiz.storage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.UUID

class CatalogAvatarsTest {
    @TempDir lateinit var directory: Path
    private fun card(name: String, types: String, legendary: Boolean = true, token: Boolean = false) =
        CatalogPrinting(UUID.randomUUID().toString(), name, "SET", "1", "English", "", UUID.randomUUID().toString(), token,
            emptyList(), """{"name":"$name","types":["$types"],"supertypes":[${if (legendary) "\"Legendary\"" else ""}]}""")

    @Test fun randomFilterAcceptsOnlyLegendaryCreaturesAndPlaneswalkersWithArtReferences() {
        val creature = card("Wizard", "Creature")
        val walker = card("Walker", "Planeswalker")
        val others = listOf(card("Common", "Creature", false), card("Land", "Land"), card("Token", "Creature", token = true),
            creature.copy(scryfallId = null), creature.copy(scryfallId = "bad"))
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

    @Test fun randomPoolReadsBeyondOneBoundedBatchAndDeduplicatesReprints() {
        val repository = CatalogRepository(directory.toFile()) { JdbcSqliteDriver("jdbc:sqlite:${it.absolutePath}") }
        val cards = (1..270).map { card("Legend $it", "Creature") }
        repository.stage { index ->
            index.addBatch(cards + cards.map { it.copy(id = UUID.randomUUID().toString()) })
            CatalogMetadata("2026-10-26", "test")
        }
        assertEquals(cards.map { it.name }.toSet(), repository.avatarChoices().map { it.name }.toSet())
        assertEquals(270, repository.avatarChoices().size)
    }
}
