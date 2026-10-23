package org.asyncttwiz.storage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIf
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.OutputStream
import java.lang.management.ManagementFactory
import java.nio.file.Path
import java.sql.DriverManager
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/** No download or external network: opt in with -Datw.realCatalog=/path/to/cached/mtgjson. */
@EnabledIf("enabled")
class RealCatalogTest {
    @TempDir lateinit var temporary: Path

    @Test fun installAndImportRealExportsWithinPhoneHeap() {
        val source = LocalCatalogSource(File(System.getProperty("atw.realCatalog") ?: System.getenv("atw.realCatalog")))
        val directory = temporary.resolve("catalog").toFile()
        val repository = CatalogRepository(directory) { JdbcSqliteDriver("jdbc:sqlite:${it.absolutePath}") }
        val memory = ManagementFactory.getMemoryMXBean()
        assertTrue(Runtime.getRuntime().maxMemory() <= 256L * 1024 * 1024, "Run with -Xmx256m")
        val peak = AtomicLong()
        val sampler = Executors.newSingleThreadScheduledExecutor()
        fun sample() { peak.accumulateAndGet(memory.heapMemoryUsage.used, ::maxOf) }
        sampler.scheduleAtFixedRate(::sample, 0, 5, TimeUnit.MILLISECONDS)
        val start = System.nanoTime()
        val installed = try {
            CatalogInstaller(directory, repository, source).install(source.metadata(), source.size())
        } finally {
            sample()
            sampler.shutdownNow()
            assertTrue(sampler.awaitTermination(5, TimeUnit.SECONDS))
            println("REAL catalog install: ${elapsedMs(start)} ms; peak JVM heap (5 ms samples): ${peak.get()} bytes; heap limit: ${Runtime.getRuntime().maxMemory()} bytes")
        }
        assertTrue(installed.records > 100_000, installed.toString())
        assertEquals(installed, repository.metadata())
        val indexFile = File(directory, File(directory, "current").readText())
        println("REAL catalog: ${installed.records} records; index on disk: ${indexFile.length()} bytes")
        DriverManager.getConnection("jdbc:sqlite:${indexFile.absolutePath}").use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("""SELECT sum(token), sum(name LIKE 'A-%'), sum(language != 'English'),
                    sum(instr(json, '"isOnlineOnly":true') > 0), max(length(json)) FROM printing""").use { rows ->
                    assertTrue(rows.next())
                    for (column in 1..4) assertTrue(rows.getLong(column) > 0, "Real catalog category $column is missing")
                    println("REAL installed categories: tokens=${rows.getLong(1)} A-cards=${rows.getLong(2)} non-English=${rows.getLong(3)} online-only=${rows.getLong(4)} largest record=${rows.getLong(5)} characters")
                }
            }
        }

        val deck = """
            4 Lightning Bolt
            4 Counterspell
            2 Snapcaster Mage
            1 Delver of Secret Insight
            3 Fable of the Mirror-Breaker
            1 Brazen Borrower
            2 Fire // Ice
            4 Island
            4 Mountain
            1 Jace, the Mind Sculptor (WWK) 31
            Sideboard
            2 Pyroblast
        """.trimIndent()
        val resolver = DeckImportResolver(repository)
        fun resolve(label: String, text: String): DeckImportResult {
            val started = System.nanoTime()
            val result = resolver.resolve(text)
            println("REAL resolution $label: ${elapsedMs(started)} ms; issues: ${result.issues}")
            assertTrue(result.resolved, result.issues.toString())
            return result
        }
        // The supplied example contains a nonexistent name. Do not silently substitute it.
        val invalid = resolver.resolve(deck)
        assertEquals(listOf(4), invalid.issues.map { it.line })
        assertTrue(invalid.issues.single().message.contains("could not be resolved"))
        val corrected = deck.replace("Delver of Secret Insight", "Delver of Secrets")
        val explicit = resolve("explicit sideboard", corrected)
        val headerless = resolve("MTGO blank-line sideboard", corrected.replace("Sideboard", ""))
        assertEquals(explicit.entries, headerless.entries)
        assertEquals(26L, explicit.count(DeckSection.MAINBOARD))
        assertEquals(2L, explicit.count(DeckSection.SIDEBOARD))
        explicit.entries.forEach { entry ->
            assertEquals("English", entry.printing.language)
            assertFalse(entry.printing.token)
            val raw = JsonParser.parseString(entry.printing.json).asJsonObject
            assertFalse(raw.get("isPromo")?.asBoolean == true)
            assertFalse(raw.get("isOnlineOnly")?.asBoolean == true)
        }
        val jace = explicit.entries.single { it.printing.name == "Jace, the Mind Sculptor" }.printing
        assertEquals("WWK", jace.set)
        assertEquals("31", jace.number)
        val moxfield = resolve("Moxfield full face names", corrected
            .replace("3 Fable of the Mirror-Breaker", "3 Fable of the Mirror-Breaker // Reflection of Kiki-Jiki")
            .replace("1 Brazen Borrower", "1 Brazen Borrower // Petty Theft"))
        assertEquals(explicit.entries, moxfield.entries)

        for ((front, full) in listOf(
            "Fable of the Mirror-Breaker" to "Fable of the Mirror-Breaker // Reflection of Kiki-Jiki",
            "Brazen Borrower" to "Brazen Borrower // Petty Theft",
            "Fire" to "Fire // Ice",
            "Delver of Secrets" to "Delver of Secrets // Insectile Aberration",
            "Valki, God of Lies" to "Valki, God of Lies // Tibalt, Cosmic Impostor",
        )) {
            val short = resolve(front, "1 $front")
            val combined = resolve(full, "1 $full")
            assertEquals(short.entries.single().faces, combined.entries.single().faces)
            assertEquals(2, combined.entries.single().faces.size)
        }

        // Exercise the app's actual URL derivation with a fake transport, never DNS or HTTP.
        // Scryfall GET /cards/:id: https://scryfall.com/docs/api/cards/id
        val requests = mutableListOf<String>()
        val artwork = HttpArtworkRepository(ArtworkDiskCache(temporary.resolve("art").toFile()),
            ArtworkTransport { url, _, _, _ -> requests += url; ArtworkResponse(404) })
        val reference = checkNotNull(ArtworkReference.from(jace, installed.date))
        assertNotNull(reference.printing)
        assertNull(artwork.load(reference))
        assertEquals(listOf("https://api.scryfall.com/cards/${jace.scryfallId}"), requests)
        assertTrue(Regex("https://api\\.scryfall\\.com/cards/[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}").matches(requests.single()))
    }

    private class LocalCatalogSource(private val directory: File) : CatalogSource {
        private val gzip get() = File(directory, "AllPrintings.json.gz")
        override fun metadata() = File(directory, "Meta.json").inputStream().use { MtgJsonCatalog().metadata(it) }
        override fun checksum() = File(directory, "AllPrintings.json.gz.sha256").readText().trim()
        override fun size() = gzip.length()
        override fun download(output: OutputStream, progress: (Long) -> Unit) {
            gzip.inputStream().buffered().use { input ->
                val buffer = ByteArray(64 * 1024)
                var bytes = 0L
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                    bytes += count
                    progress(bytes)
                }
            }
        }
    }

    companion object {
        @JvmStatic fun enabled() = !(System.getProperty("atw.realCatalog") ?: System.getenv("atw.realCatalog")).isNullOrBlank()
        private fun elapsedMs(start: Long) = (System.nanoTime() - start) / 1_000_000
    }
}
