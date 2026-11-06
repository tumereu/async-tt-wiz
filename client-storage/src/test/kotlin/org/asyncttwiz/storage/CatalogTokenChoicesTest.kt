package org.asyncttwiz.storage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.asyncttwiz.protocol.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.io.File

class CatalogTokenChoicesTest {
    // TBL-021
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
    private fun token(n: Int, set: String = "OLD", name: String = "Lantern Crypt", text: String = "Crypt Door — You gain 1 life.",
        type: String = "Dungeon", art: Boolean = true, side: String = "", others: List<String> = emptyList(), power: String = "") =
        CatalogPrinting(id(n), name, set, "$n", "English", side, if (art) id(n + 1000) else null, true, others,
            """{"faceName":"$name","type":"$type","text":"$text","power":"$power","toughness":"","colors":[],"layout":"token"}""")

    // Token choices are built for the dungeons only, their one use left (LOB-117).
    @Test fun publicationPrecomputesDungeonRowsOnlyAndPrefersNewestArtwork() {
        val repository = repo()
        repository.stage { index ->
            index.addSet("OLD", "2020-01-01"); index.addSet("NEW", "2026-01-01"); index.addSet("NOART", "2026-10-01")
            index.addBatch(listOf(token(1), token(2, "NEW"), token(3, "NOART", art = false),
                token(4, text = "Vault Door — You gain 2 life."), token(5, type = "Dungeon — Undercity"),
                token(6, name = "Spirit", text = "Flying", type = "Token Creature — Spirit", power = "1"),
                token(7, name = "Treasure", text = "{T}, Sacrifice this token: Add one mana of any color.", type = "Token Artifact — Treasure"),
                token(8, name = "Dungeon Delver", type = "Token Creature — Human", power = "2"),
                token(9).copy(token = false)))
            CatalogMetadata("2026-10-26", "5")
        }
        val list = repository.loadTokens()
        assertEquals(3, list.choices.size)
        assertEquals(setOf("Lantern Crypt"), list.choices.map { it.name }.toSet())
        val chosen = list.choices.single { it.submitted.definition.printingId == id(2) }
        assertEquals("Crypt Door — You gain 1 life.", chosen.detail)
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
        assertEquals(reads, opens, "Reopening workers/repositories reuses the generation without SQL or decoding")
    }

    @Test fun incrementalAndFullUpdatesRebuildAllFacesAndKeepFailedPublicationIsolated() {
        val repository = repo()
        fun pair(n: Int, set: String) = listOf(token(n, set, side = "a", others = listOf(id(n + 1))),
            token(n + 1, set, name = "The Claim", text = "(You can take the initiative.)", type = "Card", side = "b", others = listOf(id(n))))
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
            index.addBatch(pair(30, "NEW") + token(40, "NEW", name = "Sunken City"))
            CatalogMetadata("two", "5")
        }
        val after = repository.loadTokens()
        assertNotEquals(before.generation, after.generation)
        assertEquals(setOf("Lantern Crypt", "Sunken City"), after.choices.map { it.name }.toSet())
        assertEquals(id(30), after.choices.single { it.name == "Lantern Crypt" }.submitted.definition.printingId)
        assertThrows(IllegalStateException::class.java) { repository.stage(copyInstalled = true) { index ->
            index.removeSet("NEW"); error("Interrupted import")
        } }
        assertSame(after, repository.loadTokens())
        repository.stage { index -> index.addBatch(listOf(token(50, name = "Mossy Maze"))); CatalogMetadata("three", "5") }
        assertEquals(listOf("Mossy Maze"), repository.loadTokens().choices.map { it.name })
        assertEquals(1, before.choices.size, "Retained old snapshots stay immutable")
    }

    @Test fun gameplayIdentityChecksEveryFaceAndIgnoresPrintingMetadata() {
        val repository = repo()
        fun pair(n: Int, text: String) = listOf(token(n, side = "a", others = listOf(id(n + 1))),
            token(n + 1, name = "The Claim", text = text, type = "Card", side = "b", others = listOf(id(n))))
        repository.stage { index ->
            index.addBatch(pair(10, "(You can take the initiative.)") + pair(20, "(Venture into Undercity.)") + listOf(
                token(30, name = "Sunken City"), token(31, name = "Sunken City").copy(set = "NEW", number = "99")))
            CatalogMetadata("test", "5")
        }
        val choices = repository.loadTokens().choices
        assertEquals(3, choices.size)
        assertEquals(setOf("(You can take the initiative.)", "(Venture into Undercity.)"), choices.filter { it.name == "Lantern Crypt" }
            .map { it.submitted.definition.faces[1].rulesText }.toSet())
        assertEquals(1, choices.count { it.name == "Sunken City" })
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
        assertEquals(listOf("Lantern Crypt"), prepared.choices.map { it.name })
        val bytes = file.readBytes()
        reopened.prepareTokens()
        assertArrayEquals(bytes, file.readBytes(), "Startup compatibility preparation only happens once")
        assertSame(prepared, reopened.loadTokens())
        assertThrows(CatalogNotInstalledException::class.java) { repo("missing").loadTokens() }
    }

    @Test fun realImporterKeepsADungeonsPhysicalFacesAcrossStartupPreparationAndLeavesOtherDesignations() {
        val repository = repo()
        val metadata = CatalogMetadata("2026-10-26", "5.2.2")
        fun face(n: Int, name: String, face: String = name, type: String = "Card", side: String = "", other: Int? = null,
            layout: String = "token") = """{"uuid":"${id(n)}","name":"$name","faceName":"$face","number":"$n","type":"$type","text":"{T}: Add {G}.","layout":"$layout","side":"$side","otherFaceIds":[${other?.let { "\"${id(it)}\"" }.orEmpty()}]}"""
        val names = setOf("Undercity // The Initiative")
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
            val undercity = choices.single().submitted.definition
            assertEquals(listOf("Undercity", "The Initiative"), undercity.faces.map { it.name })
            assertEquals(listOf(id(4)), undercity.faces.first().otherFaceIds)
            assertEquals("{T}: Add {G}.", undercity.faces.last().rulesText)
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
