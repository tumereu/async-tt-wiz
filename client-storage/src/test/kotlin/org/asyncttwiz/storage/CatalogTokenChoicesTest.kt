package org.asyncttwiz.storage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.asyncttwiz.protocol.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.io.File

class CatalogTokenChoicesTest {
    @Test fun deckAndTokenFacesShareLoyaltyDefenseAndSagaCorners() {
        for (token in listOf(false, true)) for ((type, fields, expected) in listOf(
            Triple("Legendary Planeswalker — Test", "\"loyalty\":\"4\"", "4"),
            Triple("Battle — Siege", "\"defense\":\"7\"", "7"),
            Triple("Enchantment — Saga", "\"text\":\"Chapter text\"", "1"),
            Triple("Creature", "\"power\":\"2\",\"toughness\":\"3\"", "2/3"))) {
            val printing = CatalogPrinting(id(90), "Test", "SYN", "1", "English", "", null, token, emptyList(),
                """{"type":"$type",$fields}""")
            val definition = ResolvedDeckEntry(DeckSection.MAINBOARD, 1, listOf(printing), emptyList()).definition(null)
            assertEquals(expected, definition.faces.single().corner)
            assertEquals(expected, definition.faces.single().defaultCorner())
        }
    }
    @TempDir lateinit var temporary: Path
    private var opens = 0
    private fun repo(name: String = "catalog") = CatalogRepository(temporary.resolve(name).toFile()) {
        opens++
        JdbcSqliteDriver("jdbc:sqlite:${it.absolutePath}")
    }
    private fun id(n: Int) = "00000000-0000-4000-8000-${n.toString().padStart(12, '0')}"
    private fun token(n: Int, set: String = "OLD", name: String = "Spirit", text: String = "Flying", power: String = "1",
        colors: String = "[\"W\"]", type: String = "Token Creature — Spirit", art: Boolean = true,
        side: String = "", others: List<String> = emptyList(), keywords: String = "[]") =
        CatalogPrinting(id(n), name, set, "$n", "English", side, if (art) id(n + 1000) else null, true, others,
            """{"faceName":"$name","type":"$type","text":"$text","power":"$power","toughness":"1","colors":$colors,"keywords":$keywords,"layout":"transform"}""")

    @Test fun publicationPrecomputesGameplayDistinctRowsAndPrefersNewestArtwork() {
        val repository = repo()
        repository.stage { index ->
            index.addSet("OLD", "2020-01-01"); index.addSet("NEW", "2026-01-01"); index.addSet("NOART", "2026-10-01")
            index.addBatch(listOf(token(1), token(2, "NEW", keywords = "[\"Flying\"]"), token(3, "NOART", art = false),
                token(4, text = "Vigilance"), token(5, power = "2"), token(6, colors = "[\"B\"]"),
                token(7, type = "Token Enchantment Creature — Spirit"), token(8, name = "Soldier"),
                token(9).copy(token = false)))
            CatalogMetadata("2026-10-26", "5")
        }
        val list = repository.loadTokens()
        assertEquals(6, list.choices.size)
        val chosen = list.choices.single { it.submitted.definition.printingId == id(2) }
        assertEquals("Spirit", chosen.name)
        assertEquals("1/1 · Flying", chosen.detail)
        assertEquals(id(1002), chosen.submitted.definition.faces.single().scryfallId)
        assertEquals(DeckJson.digest(chosen.submitted.definition), chosen.submitted.digest)
        assertEquals("2026-10-26/5", chosen.submitted.definition.sourceVersion)
        val file = File(temporary.resolve("catalog").toFile(), temporary.resolve("catalog/current").toFile().readText())
        CatalogIndex(JdbcSqliteDriver("jdbc:sqlite:${file.absolutePath}")).use { index ->
            assertTrue(index.hasTokenChoices())
            assertEquals(list.choices, index.tokenChoices(), "The published DB contains complete ready-to-submit rows")
        }
        val reads = opens
        repeat(10) { assertSame(list, repo().loadTokens()) }
        assertEquals(reads, opens, "Reopening dialogs/repositories reuses the generation without SQL or decoding")
    }

    @Test fun incrementalAndFullUpdatesRebuildAllFacesAndKeepFailedPublicationIsolated() {
        val repository = repo()
        fun pair(n: Int, set: String) = listOf(token(n, set, side = "a", others = listOf(id(n + 1))),
            token(n + 1, set, name = "Night Spirit", side = "b", others = listOf(id(n))))
        repository.stage { index ->
            index.addSet("OLD", "2020-01-01"); index.addSet("NEW", "2026-01-01")
            index.addBatch(pair(10, "OLD") + pair(20, "NEW"))
            CatalogMetadata("one", "5")
        }
        val before = repository.loadTokens()
        assertEquals(1, before.choices.size)
        assertEquals(listOf(id(20), id(21)), before.choices.single().submitted.definition.faces.map { it.faceId })
        repository.stage(copyInstalled = true) { index ->
            index.removeSet("NEW"); index.addSet("NEW", "2026-10-01")
            index.addBatch(pair(30, "NEW") + token(40, "NEW", name = "Food"))
            CatalogMetadata("two", "5")
        }
        val after = repository.loadTokens()
        assertNotEquals(before.generation, after.generation)
        assertEquals(setOf("Spirit", "Food"), after.choices.map { it.name }.toSet())
        assertEquals(id(30), after.choices.single { it.name == "Spirit" }.submitted.definition.printingId)
        assertThrows(IllegalStateException::class.java) { repository.stage(copyInstalled = true) { index ->
            index.removeSet("NEW"); error("Interrupted import")
        } }
        assertSame(after, repository.loadTokens())
        repository.stage { index -> index.addBatch(listOf(token(50, name = "Treasure"))); CatalogMetadata("three", "5") }
        assertEquals(listOf("Treasure"), repository.loadTokens().choices.map { it.name })
        assertEquals(1, before.choices.size, "Retained old snapshots stay immutable")
    }

    @Test fun gameplayIdentityChecksEveryFaceAndIgnoresColorOrderAndPrintingMetadata() {
        val repository = repo()
        fun pair(n: Int, text: String) = listOf(token(n, side = "a", others = listOf(id(n + 1))),
            token(n + 1, name = "Night Spirit", text = text, side = "b", others = listOf(id(n))))
        repository.stage { index ->
            index.addBatch(pair(10, "Flying") + pair(20, "Vigilance") + listOf(
                token(30, name = "Soldier", colors = "[\"W\",\"B\"]"),
                token(31, name = "Soldier", colors = "[\"B\",\"W\"]"),
                token(40, name = "Food", text = "Pay 2, sacrifice this token: You gain 3 life.", keywords = "[\"Sacrifice\"]")))
            CatalogMetadata("test", "5")
        }
        val choices = repository.loadTokens().choices
        assertEquals(4, choices.size)
        assertEquals(setOf("Flying", "Vigilance"), choices.filter { it.name == "Spirit" }
            .map { it.submitted.definition.faces[1].rulesText }.toSet())
        assertEquals("1/1 · Pay 2, sacrifice this token: You gain 3 life.", choices.single { it.name == "Food" }.detail)
    }

    @Test fun realImporterRetainsColorsAndKeepsOtherwiseIdenticalSoldiersDistinct() {
        val repository = repo()
        val metadata = CatalogMetadata("2026-10-26", "5.2.2")
        val tokens = listOf("W", "R", "W").mapIndexed { n, color ->
            """{"uuid":"${id(n + 1)}","name":"Soldier","number":"${n + 1}","type":"Token Creature — Soldier","text":"","power":"1","toughness":"1","colors":["$color"]}"""
        }.joinToString(",")
        val json = """{"meta":{"date":"${metadata.date}","version":"${metadata.version}"},"data":{"SYN":{"cards":[],"tokens":[$tokens]}}}"""
        repository.stage { index -> MtgJsonCatalog().import(json.byteInputStream(), index, metadata) }
        val choices = repository.loadTokens().choices
        assertEquals(2, choices.size, "Same-color reprints collapse, but white and red Soldiers must not")
        assertEquals(setOf("[\"W\"]", "[\"R\"]"), choices.map {
            com.google.gson.JsonParser.parseString(it.submitted.definition.faces.single().sourceData)
                .asJsonObject.getAsJsonArray("colors").toString()
        }.toSet())
        assertEquals(2, choices.map { it.identity }.distinct().size)
    }

    @Test fun legacyPreparationIsExplicitAndNeverTriggeredByLoading() {
        val repository = repo()
        repository.stage { index -> index.addBatch(listOf(token(1))); CatalogMetadata("old", "5") }
        val old = temporary.resolve("catalog").toFile()
        val legacy = temporary.resolve("legacy").toFile().apply { mkdirs() }
        old.listFiles()!!.forEach { it.copyTo(File(legacy, it.name)) }
        val file = File(legacy, File(legacy, "current").readText())
        JdbcSqliteDriver("jdbc:sqlite:${file.absolutePath}").use { it.execute(null, "DROP TABLE token_choice", 0) }
        val reopened = repo("legacy")
        assertThrows(CatalogTokensNotReadyException::class.java) { reopened.loadTokens() }
        reopened.prepareTokens()
        val prepared = reopened.loadTokens()
        assertEquals(listOf("Spirit"), prepared.choices.map { it.name })
        val bytes = file.readBytes()
        reopened.prepareTokens()
        assertArrayEquals(bytes, file.readBytes(), "Startup compatibility preparation only happens once")
        assertSame(prepared, reopened.loadTokens())
        assertThrows(CatalogNotInstalledException::class.java) { repo("missing").loadTokens() }
    }

    @Test fun realImporterIncludesPlayerDesignationsAndPreservesTheirPhysicalFacesAcrossStartupPreparation() {
        val repository = repo()
        val metadata = CatalogMetadata("2026-10-26", "5.2.2")
        fun face(n: Int, name: String, face: String = name, type: String = "Card", side: String = "", other: Int? = null,
            layout: String = "token") = """{"uuid":"${id(n)}","name":"$name","faceName":"$face","number":"$n","type":"$type","text":"{T}: Add {G}.","layout":"$layout","side":"$side","otherFaceIds":[${other?.let { "\"${id(it)}\"" }.orEmpty()}]}"""
        val names = setOf("The Monarch", "City's Blessing", "Undercity // The Initiative", "Day // Night")
        val records = listOf(face(1, "The Monarch"), face(2, "City's Blessing"),
            face(3, "Undercity // The Initiative", "Undercity", "Dungeon", "a", 4, "double_faced_token"),
            face(4, "Undercity // The Initiative", "The Initiative", "Card", "b", 3, "double_faced_token"),
            face(5, "Day // Night", "Day", side = "a", other = 6),
            face(6, "Day // Night", "Night", side = "b", other = 5))
        val json = """{"meta":{"date":"${metadata.date}","version":"${metadata.version}"},"data":{"SYN":{"cards":[],"tokens":[${records.joinToString(",")}]}}}"""
        repository.stage { index -> MtgJsonCatalog().import(json.byteInputStream(), index, metadata) }
        fun verify() {
            val choices = repository.loadTokens().choices
            assertEquals(names, choices.map { it.name }.toSet())
            assertTrue(choices.all { TokenLinkRules.validDefinition(it.submitted) })
            assertEquals(listOf("Undercity", "The Initiative"), choices.single { it.name.startsWith("Undercity") }
                .submitted.definition.faces.map { it.name })
            val day = choices.single { it.name == "Day // Night" }.submitted.definition
            assertEquals(listOf("Day", "Night"), day.faces.map { it.name })
            assertEquals(listOf(id(6)), day.faces.first().otherFaceIds)
            assertEquals("{T}: Add {G}.", day.faces.last().rulesText)
        }
        verify()
        // An installed pre-fix prepared list must be rebuilt from retained provider records at startup.
        val directory = temporary.resolve("catalog").toFile()
        val file = File(directory, File(directory, "current").readText())
        JdbcSqliteDriver("jdbc:sqlite:${file.absolutePath}").use {
            it.execute(null, "DROP TABLE token_choice_ready", 0)
            it.execute(null, "DELETE FROM token_choice", 0)
        }
        repository.prepareTokens()
        verify()
        val bytes = file.readBytes()
        repository.prepareTokens()
        assertArrayEquals(bytes, file.readBytes(), "Already upgraded choices must not be rebuilt on every startup")
    }
}
