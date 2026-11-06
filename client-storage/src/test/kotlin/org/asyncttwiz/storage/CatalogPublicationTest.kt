package org.asyncttwiz.storage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import java.sql.DriverManager

class CatalogPublicationTest {
    @TempDir lateinit var temporary: Path
    private val directory get() = temporary.resolve("public-catalog").toFile()
    private fun repo() = CatalogRepository(directory) { JdbcSqliteDriver("jdbc:sqlite:${it.absolutePath}") }
    private fun id(n: Int) = "00000000-0000-4000-8000-${n.toString().padStart(12, '0')}"
    private fun token(n: Int, name: String = "Goblin ${n.toString().padStart(4, '0')}") =
        CatalogPrinting(id(n), name, "SYN", "$n", "English", "", null, true, emptyList(),
            """{"uuid":"${id(n)}","name":"$name","faceName":"Face $n","type":"Token Creature — Goblin","text":"Exact text $n. Ω","power":"*","toughness":"2","layout":"normal"}""")
    private fun install(records: List<CatalogPrinting>): CatalogRepository = repo().also { repository ->
        repository.stage { index -> index.addBatch(records); CatalogMetadata("2026-10-23", "5.2.2", "exact-checksum") }
    }
    private fun activeFile() = File(directory, File(directory, "current").readText())
    private fun tables(): List<String> = DriverManager.getConnection("jdbc:sqlite:${activeFile().absolutePath}").use { connection ->
        connection.createStatement().use { statement -> statement.executeQuery("SELECT name FROM sqlite_master WHERE type='table'").use { result ->
            buildList { while (result.next()) add(result.getString(1)) }
        } }
    }

    @Test fun absentAndFailedInstallNeverEraseOrFabricateCatalog() {
        val repository = repo()
        assertNull(repository.tokenGeneration())
        assertNull(repository.printing(id(1)))
        assertThrows(IllegalStateException::class.java) { repository.stage { error("download failed") } }
        assertNull(repository.tokenGeneration())
        assertNull(repository.metadata())
        install(listOf(token(1)))
        val previous = activeFile().readBytes()
        assertThrows(IllegalStateException::class.java) { repository.stage { error("refresh failed") } }
        assertEquals(token(1), repository.printing(id(1)))
        assertArrayEquals(previous, activeFile().readBytes())
    }

    @Test fun finishingBuildsNoTokenNameSearchAndDropsAnOldGenerationsTables() {
        install(listOf(token(1)))
        assertTrue(tables().none { it.startsWith("token_search") })
        JdbcSqliteDriver("jdbc:sqlite:${activeFile().absolutePath}").use {
            it.execute(null, "CREATE TABLE token_search (gram TEXT NOT NULL)", 0)
            it.execute(null, "CREATE TABLE token_search_ready (version INTEGER NOT NULL)", 0)
        }
        repo().stage(copyInstalled = true) { index -> index.addBatch(listOf(token(2))); CatalogMetadata("2026-10-24", "5.2.2") }
        assertTrue(tables().none { it.startsWith("token_search") })
        assertEquals(token(2), repo().printing(id(2)))
    }
}
