package org.asyncttwiz.storage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import java.sql.DriverManager
import java.util.Locale

class TokenSearchTest {
    @TempDir lateinit var temporary: Path
    private val directory get() = temporary.resolve("public-catalog").toFile()
    private fun repo() = CatalogRepository(directory) { JdbcSqliteDriver("jdbc:sqlite:${it.absolutePath}") }
    private fun id(n: Int) = "00000000-0000-4000-8000-${n.toString().padStart(12, '0')}"
    private fun token(n: Int, name: String = "Goblin ${n.toString().padStart(4, '0')}",
        token: Boolean = true, side: String = "", others: List<String> = emptyList()) =
        CatalogPrinting(id(n), name, "SYN", "$n", "English", side, null, token, others,
            """{"uuid":"${id(n)}","name":"$name","faceName":"Face $n","type":"Token Creature — Goblin","text":"Exact text $n. Ω","power":"*","toughness":"2","layout":"normal"}""")
    private fun install(records: List<CatalogPrinting>, date: String = "2026-10-23"): CatalogRepository = repo().also { repository ->
        repository.stage { index -> index.addBatch(records); CatalogMetadata(date, "5.2.2", "exact-checksum") }
    }
    private fun activeFile() = File(directory, File(directory, "current").readText())
    private fun <T> sql(block: (java.sql.Connection) -> T): T =
        DriverManager.getConnection("jdbc:sqlite:${activeFile().absolutePath}").use(block)
    private fun rows(query: String): List<List<String?>> = sql { connection ->
        connection.createStatement().use { statement -> statement.executeQuery(query).use { result ->
            buildList { while (result.next()) add((1..result.metaData.columnCount).map { result.getString(it) }) }
        } }
    }
    private fun all(repository: CatalogRepository, query: String, limit: Int = 17): List<CatalogToken> {
        val found = mutableListOf<CatalogToken>()
        var cursor: CatalogTokenCursor? = null
        do {
            val page = repository.searchTokens(query, cursor, limit)
            assertTrue(page.tokens.size <= limit)
            assertEquals(repository.tokenGeneration(), page.generation)
            found += page.tokens
            cursor = page.next
        } while (cursor != null)
        assertEquals(found.size, found.map { it.printing.id }.distinct().size)
        return found
    }

    @Test fun realFileTokenOnlySubstringLiteralNormalizationAndStableBoundedPages() {
        val records = (1..620).map { token(it) } + listOf(
            token(700, "Goblin ordinary card", token = false), token(701, "100% Goblin_"),
            token(702, "ELF"), token(703, "Goblin 0001"), token(704, "İ Spirit"))
        val repository = install(records)
        val oldLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            for (query in listOf("", "g", "OB", " bLiN ", "%", "_", "ELF", "İ", "does not exist")) {
                val normalized = query.trim().lowercase(Locale.ROOT)
                val expected = records.filter { it.token && it.name.lowercase(Locale.ROOT).contains(normalized) }
                    .sortedWith(compareBy<CatalogPrinting> { it.name.lowercase(Locale.ROOT) }.thenBy { it.id })
                assertEquals(expected, all(repository, query).map { it.printing }, "query=$query")
            }
        } finally { Locale.setDefault(oldLocale) }
        val first = repository.searchTokens("goblin", limit = 1)
        assertEquals(1, first.tokens.size)
        assertNotNull(first.next)
        assertThrows(IllegalArgumentException::class.java) { repository.searchTokens("elf", first.next) }
        assertThrows(IllegalArgumentException::class.java) { repository.searchTokens("", limit = 0) }
        assertThrows(IllegalArgumentException::class.java) { repository.searchTokens("", limit = 101) }
        assertThrows(IllegalArgumentException::class.java) { repository.searchTokens("x".repeat(513)) }
        assertEquals(0, all(repository, "ordinary").size)
        println("PASS real SQLite token-only literal substring search, ROOT normalization, bounded stable keyset paging")
    }

    @Test fun existingInstalledGenerationBackfillsNonDestructivelyOnceWithIndexedPlans() {
        val front = token(801, "Spirit", side = "a", others = listOf(id(802)))
        val back = token(802, "Spirit", side = "b", others = listOf(id(801)))
        val repository = install((1..620).map { token(it) } + listOf(front, back))
        // Exactly the pre-search public schema: no private Cache/Replica migration or replacement.
        sql { connection -> connection.createStatement().use { statement ->
            statement.execute("DROP TABLE token_search")
            statement.execute("DROP TABLE token_search_ready")
            statement.execute("DROP INDEX token_name_lookup")
        } }
        val file = activeFile()
        val identity = file.name
        val printingBefore = rows("SELECT rowid, * FROM printing ORDER BY id")
        val faceBefore = rows("SELECT rowid, * FROM face ORDER BY source, target")
        val metadataBefore = rows("SELECT rowid, * FROM metadata")
        val privateFile = temporary.resolve("private-account.db").toFile().apply { writeText("private sentinel: never opened") }
        val privateBefore = privateFile.readBytes()
        repo().prepareTokens() // Startup worker, never the first dialog/search.
        assertEquals(620, all(repo(), "goblin").size) // a new repository/process-style reopen
        assertEquals(identity, activeFile().name)
        assertEquals(printingBefore, rows("SELECT rowid, * FROM printing ORDER BY id"))
        assertEquals(faceBefore, rows("SELECT rowid, * FROM face ORDER BY source, target"))
        assertEquals(metadataBefore, rows("SELECT rowid, * FROM metadata"))
        assertArrayEquals(privateBefore, privateFile.readBytes())
        assertEquals(repository.metadata(), repo().metadata())
        assertEquals(listOf(listOf("1")), rows("SELECT version FROM token_search_ready"))
        val afterBackfill = file.readBytes()
        repeat(3) { repo().searchTokens("spirit", limit = 1) }
        assertArrayEquals(afterBackfill, file.readBytes(), "Already-ready queries must not rebuild/write postings")
        val browse = rows("EXPLAIN QUERY PLAN SELECT * FROM printing INDEXED BY token_name_lookup WHERE token=1 AND (normalized,id) > ('g','') ORDER BY normalized,id LIMIT 41").flatten().filterNotNull().joinToString()
        val search = rows("EXPLAIN QUERY PLAN SELECT p.* FROM token_search s JOIN printing p ON p.id=s.printing_id WHERE s.gram='gob' AND instr(p.normalized,'goblin')>0 AND (s.normalized,s.printing_id)>('g','') ORDER BY s.normalized,s.printing_id LIMIT 41").flatten().filterNotNull().joinToString()
        assertTrue(browse.contains("SEARCH") && browse.contains("token_name_lookup"), browse)
        assertTrue(search.contains("SEARCH s") && search.contains("SEARCH p"), search)
        assertFalse(search.contains("SCAN p") || search.contains("TEMP B-TREE"), search)
        println("PASS existing-generation in-place compatibility, row/face/metadata identity, one backfill, indexed query plans: $search")
    }

    @Test fun generationSwapFencesCursorsEvenWhenMetadataRepeatsAndSnapshotsKeepAllExactFaces() {
        val front = token(901, "Dawn Spirit", side = "a", others = listOf(id(902)))
        val back = token(902, "Dusk Spirit", side = "b", others = listOf(id(901)))
        val repository = install(listOf(front, back, token(903, "Spirit")))
        val page = repository.searchTokens("spirit", limit = 1)
        assertEquals(listOf(front, back), page.tokens.single().faces)
        assertEquals(front.json, page.tokens.single().faces[0].json)
        assertEquals(back.json, page.tokens.single().faces[1].json)
        assertEquals(listOf(front, back), repository.searchTokens("dusk").tokens.single().faces)
        assertNotNull(page.next)
        assertThrows(IllegalStateException::class.java) { repository.stage { index ->
            index.addBatch(listOf(token(950))); error("download/backfill interrupted before publication")
        } }
        assertEquals(page.generation, repository.tokenGeneration())
        assertEquals(back, repository.searchTokens("spirit", page.next, limit = 1).tokens.single().printing)
        // Same date/version/checksum/record count is deliberately not a generation identity.
        install(listOf(front.copy(json = front.json.replace("Exact text", "Updated text")), back, token(904, "Spirit")))
        assertEquals(page.metadata, repository.metadata())
        assertNotEquals(page.generation, repository.tokenGeneration())
        assertThrows(CatalogGenerationChangedException::class.java) { repository.searchTokens("spirit", page.next) }
        assertEquals(listOf(front, back), page.tokens.single().faces, "Retained exact old snapshots never mutate")
        assertEquals(1, directory.listFiles()!!.count { it.extension == "db" })
        println("PASS failed install retains queryable generation, swap rejects old keyset and retains exact multi-face snapshots")
    }

    @Test fun absentAndFailedInstallNeverEraseOrFabricateCatalog() {
        val repository = repo()
        assertNull(repository.tokenGeneration())
        assertThrows(CatalogNotInstalledException::class.java) { repository.searchTokens("") }
        assertThrows(IllegalStateException::class.java) { repository.stage { error("download failed") } }
        assertNull(repository.tokenGeneration())
        assertThrows(CatalogNotInstalledException::class.java) { repository.searchTokens("goblin") }
        install(listOf(token(1)))
        val previous = activeFile().readBytes()
        assertThrows(IllegalStateException::class.java) { repository.stage { error("refresh failed") } }
        assertEquals(token(1), repository.searchTokens("").tokens.single().printing)
        assertArrayEquals(previous, activeFile().readBytes())
    }
}
