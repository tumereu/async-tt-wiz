package org.asyncttwiz.storage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import java.security.MessageDigest
import java.sql.DriverManager

/**
 * Card records installed the fast way (multi-row statements, lookups built after the records) store and answer exactly
 * what the row-by-row install did: for each fixture catalog, every table's rows and the answers to a set of searches,
 * lookups and imports are compared with the answers recorded from the row-by-row install (`src/test/resources/
 * catalog-answers`). An intended change of what is stored or answered records them again: `./scripts/gradle.sh
 * :client-storage:testAndroidHostTest -PcatalogAnswers=record --tests '*CatalogInstallAnswersTest*'`.
 */
class CatalogInstallAnswersTest {
    @TempDir lateinit var temporary: Path

    @Test fun fixtureCatalogsStoreAndAnswerAsTheRowByRowInstallDid() {
        val recording = System.getProperty("catalog.answers.record") == "true"
        val answers = File(checkNotNull(System.getProperty("catalog.answers")))
        val differing = mutableListOf<String>()
        for (family in listOf("prism", "quilt")) for (edition in listOf("v1", "v2")) {
            val directory = temporary.resolve("$family-$edition").toFile()
            val repository = CatalogRepository(directory) { JdbcSqliteDriver("jdbc:sqlite:${it.absolutePath}") }
            val source = File(System.getProperty("catalog.fixtures"), "$family/$edition")
            val metadata = File(source, "Meta.json").inputStream().use { MtgJsonCatalog().metadata(it) }
            repository.stage { index -> File(source, "AllPrintings.json").inputStream().use { MtgJsonCatalog().import(it, index, metadata) } }
            val actual = CatalogAnswers.stored(File(directory, File(directory, "current").readText())) + CatalogAnswers.answered(repository)
            val file = File(answers, "$family-$edition.txt")
            if (recording) file.apply { parentFile.mkdirs() }.writeText(actual)
            else if (file.readText() != actual) differing += file.name
        }
        assertEquals(emptyList<String>(), differing, "Stored or answered differently from the recorded answers")
    }

    // A printing id seen again (later in a batch, or in a later batch) keeps its first record whole: the later record and
    // its import names are skipped
    @Test fun aRepeatedPrintingKeepsItsFirstRecord() {
        fun id(n: Int) = "00000000-0000-4000-8000-${n.toString().padStart(12, '0')}"
        fun printing(n: Int, name: String) = CatalogPrinting(id(n), name, "TST", "$n", "English", "", null, false, emptyList(),
            """{"uuid":"${id(n)}","name":"$name","faceName":"$name Face","type":"Creature — Bear","layout":"normal"}""")
        val directory = temporary.resolve("repeated").toFile()
        val repository = CatalogRepository(directory) { JdbcSqliteDriver("jdbc:sqlite:${it.absolutePath}") }
        repository.stage { index ->
            index.addBatch(listOf(printing(1, "First Bear"), printing(2, "Other Bear"), printing(1, "Second Bear")))
            index.addBatch(listOf(printing(2, "Third Bear"), printing(3, "Last Bear")))
            CatalogMetadata("2026-11-10", "test")
        }
        assertEquals(listOf("First Bear", "Other Bear", "Last Bear"), listOf(1, 2, 3).map { repository.printing(id(it))?.name })
        assertEquals(listOf(id(1)), repository.find("first bear face").map { it.id })
        for (skipped in listOf("second bear", "second bear face", "third bear", "third bear face")) {
            assertEquals(emptyList<CatalogPrinting>(), repository.find(skipped), skipped)
        }
        assertEquals(3L, repository.metadata()?.records)
    }

    // The server's card catalog of the test cards (meld pairs, double-faced cards, tokens and their makers, support marks):
    // what the installer takes from each face stores and answers the same as reading it back from the printing's record
    @Test fun aServerCatalogInstallsAsIfEachRecordWereReadBack() {
        val catalogs = File(checkNotNull(System.getProperty("catalog.testCards")))
        val file = File(catalogs, File(catalogs, "current").readText().trim() + org.asyncttwiz.catalog.CardCatalogFiles.SUFFIX)
        fun install(name: String, readBack: Boolean): String {
            val directory = temporary.resolve(name).toFile()
            val repository = CatalogRepository(directory) { JdbcSqliteDriver("jdbc:sqlite:${it.absolutePath}") }
            repository.stage { index ->
                val indexer = CatalogRecordIndexer(index, readBack)
                file.inputStream().use { org.asyncttwiz.catalog.CardCatalogFiles.stream(it, visit = indexer::add) }
                indexer.finish(file.name.removeSuffix(org.asyncttwiz.catalog.CardCatalogFiles.SUFFIX))
            }
            return CatalogAnswers.stored(File(directory, File(directory, "current").readText())) + CatalogAnswers.answered(repository)
        }
        val installed = install("installed", readBack = false)
        assertTrue(installed.contains("table token_relation: ") && !installed.contains("table token_relation: 0 rows"), installed)
        assertEquals(install("read-back", readBack = true), installed)
    }
}

/** What an installed generation holds and answers, as text to compare. */
internal object CatalogAnswers {
    /** Each table's row count and the SHA-256 of its rows in sorted order (rowids aside), and the index names. */
    fun stored(file: File): String = DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}").use { connection ->
        val text = StringBuilder()
        fun strings(query: String) = connection.createStatement().use { statement ->
            statement.executeQuery(query).use { rows -> buildList { while (rows.next()) add(rows.getString(1)) } }
        }
        text.appendLine("indexes: " + strings("SELECT name FROM sqlite_master WHERE type='index' ORDER BY name").joinToString())
        text.appendLine("user_version: " + strings("PRAGMA user_version").single())
        for (table in strings("SELECT name FROM sqlite_master WHERE type='table' ORDER BY name")) {
            val columns = connection.createStatement().use { statement ->
                statement.executeQuery("SELECT * FROM $table LIMIT 0").use { it.metaData.columnCount }
            }
            connection.createStatement().use { statement ->
                // Streamed in a total order of all columns, so a big catalog fits a phone-sized heap
                statement.executeQuery("SELECT * FROM $table ORDER BY ${(1..columns).joinToString()}").use { rows ->
                    val digest = MessageDigest.getInstance("SHA-256")
                    var count = 0
                    while (rows.next()) {
                        count++
                        for (column in 1..columns) {
                            digest.update((rows.getObject(column)?.let { "${it::class.simpleName}:$it" } ?: "null").toByteArray(Charsets.UTF_8))
                            digest.update(1)
                        }
                        digest.update(0)
                    }
                    text.appendLine("table $table: $count rows, ${digest.digest().joinToString("") { "%02x".format(it) }}")
                }
            }
        }
        text.toString()
    }

    private val QUERIES = listOf("", "a", "e", "t:creature", "t:instant", "t:land", "-t:land", "o:draw", "o:\"a card\"", "c:r",
        "c:u", "c=wu", "c>=2", "id:g", "id<=wb", "mv>=3", "mv=0", "mv<2", "pow>=3", "tou<=1", "pow>tou", "loy>=1", "r:common",
        "r>=rare", "is:dfc", "is:mdfc", "is:split", "is:transform", "f:standard", "f:commander", "layout:transform", "t:token")

    /** Every search's results page by page (cursor and work done included), and lookups by name, printing and set. */
    fun answered(repository: CatalogRepository, pagesPerSearch: Int = 100): String {
        val text = StringBuilder()
        val names = sortedSetOf<String>()
        val printings = sortedSetOf<String>()
        for (query in QUERIES) {
            text.appendLine("search \"$query\"")
            var cursor: CatalogCardCursor? = null
            var pages = 0
            do {
                when (val page = repository.searchCards(query, cursor, limit = 7, budget = 9)) {
                    is CatalogCardSearch.Invalid -> { text.appendLine("  invalid ${page.error}"); cursor = null }
                    is CatalogCardSearch.Page -> {
                        text.appendLine("  page examined=${page.examined} next=${page.next?.let { "${it.nameKey}|${it.key}" }}")
                        page.cards.forEach { card ->
                            names += card.name
                            printings += card.faces.map { it.id }
                            text.appendLine("    ${card.key} ${card.name}: " + card.faces.joinToString { "${it.id} ${it.set} ${it.number} ${it.side}" } +
                                " parts=" + card.namedParts().joinToString { "${it.name}/${it.typeLine}" })
                        }
                        cursor = page.next
                    }
                }
            } while (cursor != null && ++pages < pagesPerSearch)
        }
        for (name in names) {
            val found = repository.find(name)
            text.appendLine("find \"$name\": " + found.joinToString { "${it.id} ${it.otherFaces} ${it.releaseDate}" })
            printings += found.map { it.id }
        }
        val records = printings.mapNotNull { repository.printing(it) }
        records.forEach { text.appendLine("printing ${it.id}: ${it.name} ${it.set} ${it.number} ${it.side} ${it.token} ${it.otherFaces} ${it.releaseDate} ${it.json}") }
        repository.searchData(records).toSortedMap().forEach { (id, card) -> text.appendLine("search data $id: $card") }
        text.appendLine("draftable sets: ${repository.draftableSets()}")
        text.appendLine("unsupported: ${repository.unsupported(printings)?.sorted()}")
        text.appendLine("avatars \"a\": ${repository.avatarChoices("a")}")
        val deck = names.take(12).joinToString("\n") { "1 $it" }
        text.appendLine("deck import: " + DeckImportResolver(repository).resolve(deck).let { result ->
            result.entries.joinToString { "${it.printing.id} ${it.faces.map { face -> face.id }}" } + " issues=${result.issues}"
        })
        return text.toString()
    }
}
