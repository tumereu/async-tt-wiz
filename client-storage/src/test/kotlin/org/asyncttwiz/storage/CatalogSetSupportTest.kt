package org.asyncttwiz.storage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.google.gson.JsonObject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.UUID

/** DRF-161 (I-D51): draft sets' Automatum support is worked out from the installed card records, per parser version. */
class CatalogSetSupportTest {
    @TempDir lateinit var directory: Path
    private fun repository() = CatalogRepository(directory.toFile()) { JdbcSqliteDriver("jdbc:sqlite:${it.absolutePath}") }

    // Invented cards with oracle-style wording; the oracle id ties reprints together.
    private fun card(name: String, set: String, text: String, oracle: String = "oracle-$name") = CatalogPrinting(UUID.randomUUID().toString(),
        name, set, "1", "English", "", UUID.randomUUID().toString(), false, emptyList(), MtgJsonCatalog.retainedRecord(JsonObject().apply {
            addProperty("name", name); addProperty("type", "Creature — Bear"); addProperty("manaCost", "{1}{G}")
            addProperty("text", text); addProperty("power", "2"); addProperty("toughness", "2"); addProperty("layout", "normal")
            add("identifiers", JsonObject().apply { addProperty("scryfallOracleId", oracle) })
        }).toString())

    @Test fun setsNameTheirUnsupportedCardsAndReprintsAreReadOnce() {
        val repository = repository()
        val odd = "Whenever the moon is full, this creature dances a slow jig."
        repository.stage { index ->
            index.addBatch(listOf(card("Plain Cub", "AAA", "Trample"), card("Odd Cub", "AAA", odd), card("Odd Cub", "BBB", odd),
                card("Plain Cub", "BBB", "Trample"), card("Plain Pup", "CCC", "Trample")))
            CatalogMetadata("2026-11-03", "test")
        }
        val read = mutableListOf<String>()
        val support = CatalogSetSupport(repository, { definition ->
            read += definition.faces.single().name
            definition.faces.any { face -> face.abilities.any { it is arcanum.api.Ability.Unparsed } }
        }, parserVersion = 7)
        assertTrue(support.sets().isEmpty(), "nothing is known before it is worked out")
        assertEquals(3, support.refresh())
        assertEquals(mapOf("AAA" to listOf("Odd Cub"), "BBB" to listOf("Odd Cub"), "CCC" to emptyList<String>()), support.sets())
        assertEquals(listOf("Odd Cub", "Plain Cub", "Plain Pup"), read.sorted(), "each card is read once for all its printings")
        assertEquals(0, support.refresh(), "sets already worked out stay")

        // Another parser version works every set out again
        val next = CatalogSetSupport(repository, { false }, parserVersion = 8)
        assertTrue(next.sets().isEmpty())
        assertEquals(3, next.refresh())
        assertEquals(setOf("AAA", "BBB", "CCC"), next.sets().filterValues { it.isEmpty() }.keys)
        assertTrue(support.sets().isEmpty(), "the older version's results are dropped")

        // So does another engine support version with the same parser (I-D51)
        val engine = CatalogSetSupport(repository, { false }, parserVersion = 8, engineVersion = 2)
        assertTrue(engine.sets().isEmpty())
        assertEquals(3, engine.refresh())
        assertTrue(next.sets().isEmpty(), "the older engine version's results are dropped")
    }

    // DRF-161 (smoke 2026-11-04): only the sets asked for are worked out, and an app restart or update with the same
    // parser and engine versions (a new CatalogSetSupport on the same card records) works none out again
    @Test fun onlyRequestedSetsAreWorkedOutAndKeptAcrossUpdates() {
        val repository = repository()
        repository.stage { index ->
            index.addBatch(listOf(card("Plain Cub", "AAA", "Trample"), card("Plain Cub", "BBB", "Trample"),
                card("Plain Pup", "CCC", "Trample")))
            CatalogMetadata("2026-11-03", "test")
        }
        val read = mutableListOf<String>()
        val support = CatalogSetSupport(repository, { read += it.faces.single().name; false }, parserVersion = 7, engineVersion = 3)
        var pauses = 0
        assertEquals(2, support.refresh(listOf("CCC", "AAA", "ZZZ"), between = { pauses++ }))
        assertEquals(setOf("AAA", "CCC"), support.sets().keys, "BBB wasn't asked for, ZZZ isn't a set")
        assertEquals(1, pauses, "a pause between the two sets")
        assertEquals(listOf("Plain Pup", "Plain Cub"), read)

        val updated = CatalogSetSupport(repository, { read += it.faces.single().name; false }, parserVersion = 7, engineVersion = 3)
        assertEquals(setOf("AAA", "CCC"), updated.sets().keys)
        assertEquals(0, updated.refresh(listOf("AAA", "CCC")), "nothing is worked out again")
        assertEquals(1, updated.refresh(listOf("AAA", "BBB")))
        assertEquals(listOf("Plain Pup", "Plain Cub"), read, "the reprint in BBB is known by its oracle id")
    }
}
