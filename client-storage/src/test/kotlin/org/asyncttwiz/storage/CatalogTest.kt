package org.asyncttwiz.storage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.google.gson.JsonParser
import com.sun.net.httpserver.HttpServer
import org.asyncttwiz.catalog.CardCatalog
import org.asyncttwiz.catalog.CardCatalogFiles
import org.asyncttwiz.catalog.CardCatalogFormat
import org.asyncttwiz.catalog.CatalogCards
import org.asyncttwiz.catalog.CatalogFaceRecord
import org.asyncttwiz.catalog.CatalogHeader
import org.asyncttwiz.catalog.CatalogPrintingRecord
import org.asyncttwiz.catalog.CatalogSetRecord
import org.asyncttwiz.catalog.CatalogVersion
import org.asyncttwiz.protocol.CardSearchRules
import org.asyncttwiz.protocol.PhysicalCardFaces
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.*
import java.net.InetSocketAddress
import java.nio.file.Path

/** The device's card records from the server's card catalog (docs/specs/web-client.md section 3.5). */
class CatalogTest {
    @TempDir lateinit var temporary: Path
    private val directory get() = temporary.resolve("catalog").toFile()
    private fun repo() = CatalogRepository(directory) { JdbcSqliteDriver("jdbc:sqlite:${it.absolutePath}") }

    private fun id(n: Int) = "00000000-0000-4000-8000-${n.toString().padStart(12, '0')}"

    /** An invented catalog: a supported bear, an unsupported wizard, a token its maker makes, two sets. */
    private fun catalog(version: String = "5.3.0+20261031"): CardCatalog {
        val modern = 1L shl CardSearchRules.FORMATS.indexOf("modern")
        val faces = listOf(
            CatalogFaceRecord(0, "Test Bear", type = "Creature — Bear", manaCost = "{1}{G}", power = "2", toughness = "2",
                colors = listOf("G"), identity = listOf("G"), legal = modern, oracle = id(900), automatum = true),
            CatalogFaceRecord(1, "Glorp Wizard", type = "Creature — Wizard", manaCost = "{U}", text = "Flarn a glorp.",
                power = "1", toughness = "1", colors = listOf("U"), identity = listOf("U"), oracle = id(901)),
            CatalogFaceRecord(2, "Elemental", layout = "token", type = "Token Creature — Elemental", power = "1", toughness = "1"),
        )
        val printings = listOf(
            CatalogPrintingRecord(id(1), 0, "TST", "1", id(101), "common"),
            CatalogPrintingRecord(id(2), 0, "OLD", "7", id(102), "uncommon"),
            CatalogPrintingRecord(id(3), 1, "TST", "2", id(103), "rare", tokens = listOf(id(4))),
            CatalogPrintingRecord(id(4), 2, "TTST", "1", id(104), token = true, makers = listOf("glorp wizard")),
        )
        val sets = listOf(
            CatalogSetRecord("OLD", "Old Set", "core", "2001-01-01", draftable = true, automatum = true),
            CatalogSetRecord("TST", "Test Set", "expansion", "2026-01-01", draftable = true, unsupported = listOf("Glorp Wizard")),
            CatalogSetRecord("TTST", "Test Set Tokens", "token", "2026-01-01"),
        )
        val header = CatalogHeader(CardCatalogFormat.FORMAT, version, "2026-10-31", CardSearchRules.FORMATS, 1, 1,
            sets.size, faces.size, printings.size, 0)
        return CardCatalog(header, sets, faces, printings, emptyList())
    }

    private fun bytes(catalog: CardCatalog): Pair<ByteArray, String> {
        val out = ByteArrayOutputStream()
        val checksum = CardCatalogFiles.write(catalog, out)
        return out.toByteArray() to checksum
    }

    private class Source(var file: ByteArray, var checksum: String) : CatalogSource {
        var currentFails = false
        val asked = mutableListOf<String>()
        override fun current(): CatalogVersion {
            check(!currentFails) { "offline" }
            return CatalogVersion(checksum, file.size.toLong(), "5.3.0", "2026-10-31", 3, 3, 4)
        }
        override fun download(checksum: String, output: OutputStream, progress: (Long) -> Unit) {
            asked += checksum
            file.toList().chunked(64).forEach { chunk -> output.write(chunk.toByteArray()); progress(chunk.size.toLong()) }
        }
    }

    // LOB-091, LOB-094: the app downloads the server's catalog, indexes it and reports how far it is as one share
    @Test fun installsTheServersCatalogWithItsRecordsMarksAndProgress() {
        val (file, checksum) = bytes(catalog())
        val source = Source(file, checksum)
        val events = mutableListOf<CatalogProgress>()
        val installed = CatalogInstaller(directory, repo(), source, { Long.MAX_VALUE }).install(checksum, events::add)
        assertEquals(CatalogMetadata("2026-10-31", "5.3.0+20261031", checksum, 4), installed)
        assertEquals(installed, repo().metadata())
        assertEquals(listOf(checksum), source.asked)
        assertEquals(listOf(CatalogPhase.DOWNLOADING, CatalogPhase.INDEXING, CatalogPhase.FINISHING), events.map { it.phase }.distinct())
        assertEquals(events.map { it.percent }.sorted(), events.map { it.percent }, "the share only grows")
        assertEquals(99, events.last().percent)
        // The records as the app keeps them, with their search traits (LOB-110) and tokens (LOB-125)
        val bear = repo().printing(id(1))!!
        assertEquals("Test Bear", bear.name)
        assertEquals("TST", bear.set)
        assertEquals(id(101), bear.scryfallId)
        assertEquals("Creature — Bear", JsonParser.parseString(bear.json).asJsonObject.get("type").asString)
        assertEquals(2, repo().find("test bear").size)
        // SYS-082's marks (section 3.8): supported printings, and the draft sets with their unsupported cards
        assertEquals(setOf(id(3), id(4), id(9)), repo().unsupported(listOf(id(1), id(2), id(3), id(4), id(9))))
        assertEquals(listOf(DraftableSet("TST", "Test Set", "2026-01-01", 2, false, listOf("Glorp Wizard")),
            DraftableSet("OLD", "Old Set", "2001-01-01", 1, true)), repo().draftableSets())
    }

    // LOB-095: an app whose built-in checksum differs from the installed catalog's installs its own; without one it takes
    // the server's current catalog
    @Test fun aNewCatalogReplacesTheInstalledOneAndNoBuiltInChecksumFollowsTheServer() {
        val (first, firstChecksum) = bytes(catalog())
        val source = Source(first, firstChecksum)
        assertEquals(firstChecksum, CatalogInstaller(directory, repo(), source, { Long.MAX_VALUE }).install(null).checksum)
        val (second, secondChecksum) = bytes(catalog("5.3.0+20261101"))
        source.file = second; source.checksum = secondChecksum; source.currentFails = true
        // The server can't say what is current: the built-in checksum is enough
        assertEquals(secondChecksum, CatalogInstaller(directory, repo(), source, { Long.MAX_VALUE }).install(secondChecksum).checksum)
        assertEquals("5.3.0+20261101", repo().metadata()!!.version)
        assertEquals(1, directory.listFiles()!!.count { it.extension == "db" })
        assertThrows(Exception::class.java) { CatalogInstaller(directory, repo(), source, { Long.MAX_VALUE }).install(null) }
    }

    // LOB-096: a failed download or a file that isn't the catalog asked for leaves the installed records as they were
    @Test fun aFailedOrWrongDownloadKeepsTheInstalledRecords() {
        val (file, checksum) = bytes(catalog())
        val source = Source(file, checksum)
        val before = CatalogInstaller(directory, repo(), source, { Long.MAX_VALUE }).install(checksum)
        val (other, _) = bytes(catalog("5.3.0+20261101"))
        fun failure(label: String, expected: String, bytes: ByteArray, space: Long = Long.MAX_VALUE) {
            source.file = bytes
            assertThrows(Exception::class.java, { CatalogInstaller(directory, repo(), source, { space }).install(expected) }, label)
            assertEquals(before, repo().metadata(), label)
        }
        failure("another catalog's file", checksum, other)
        failure("cut short", checksum, file.copyOf(file.size - 12))
        failure("not gzip", checksum, "{}".toByteArray())
        failure("no room", checksum, file, space = 10)
        assertEquals(1, directory.listFiles()!!.count { it.extension == "db" })
        assertFalse(File(directory, "catalog.jsonl.gz.part").exists())
    }

    @Test fun countingInputCountsSingleBulkAndSkippedBytesExactlyOnce() {
        val input = CountingInput(ByteArrayInputStream(ByteArray(100)))
        input.read(); input.read(ByteArray(10)); input.skip(5)
        assertEquals(16, input.count)
    }

    // SYS-081: the endpoints, open without sign-in
    @Test fun theHttpSourceReadsTheServersCatalogEndpoints() {
        val (file, checksum) = bytes(catalog())
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/v1/catalog") { exchange ->
            val path = exchange.requestURI.path
            assertNull(exchange.requestHeaders.getFirst("Authorization"))
            val body = when (path) {
                "/v1/catalog" -> """{"checksum":"$checksum","size":${file.size},"mtgjsonVersion":"5.3.0","mtgjsonDate":"2026-10-31","sets":3,"faces":3,"printings":4,"extra":1}""".toByteArray()
                "/v1/catalog/$checksum" -> file
                else -> null
            }
            exchange.sendResponseHeaders(if (body == null) 404 else 200, body?.size?.toLong() ?: -1)
            body?.let { exchange.responseBody.use { out -> out.write(it) } }
            exchange.close()
        }
        server.start()
        try {
            val source = HttpCatalogSource("http://127.0.0.1:${server.address.port}")
            assertEquals(checksum, source.current().checksum)
            assertEquals(checksum, CatalogInstaller(directory, repo(), source, { Long.MAX_VALUE }).install(checksum).checksum)
            assertThrows(Exception::class.java) { source.download("0".repeat(64), ByteArrayOutputStream()) {} }
        } finally { server.stop(0) }
        assertThrows(IllegalArgumentException::class.java) { HttpCatalogSource("http://example.com") }
        assertThrows(IllegalArgumentException::class.java) { HttpCatalogSource("https://example.com/path") }
    }

    // Card data from the server's catalog (owner 2026-11-10): the card data the phone makes of a deck's card is what the
    // server builds from the same catalog for its printing (CatalogCards.definition, as a save holds it), so the digests
    // the phone works with (a cover, its support check) are the server's
    @Test fun thePhonesCardDataOfADeckCardIsTheServers() {
        val base = catalog()
        val faces = base.faces + listOf(
            CatalogFaceRecord(3, "Dawn Hunter // Dusk Hunter", "Dawn Hunter", "transform", "a", "Creature — Human", "{2}{G}",
                "At the beginning of each upkeep, transform this creature.", "2", "2", colors = listOf("G"), identity = listOf("G")),
            CatalogFaceRecord(4, "Dawn Hunter // Dusk Hunter", "Dusk Hunter", "transform", "b", "Creature — Wolf", "",
                "Trample", "4", "4", colors = listOf("G"), identity = listOf("G")))
        val printings = base.printings + listOf(CatalogPrintingRecord(id(5), 3, "TST", "3", id(105), others = listOf(id(6))),
            CatalogPrintingRecord(id(6), 4, "TST", "3", id(105), others = listOf(id(5))))
        val catalog = CardCatalog(base.header.copy(faces = faces.size, printings = printings.size), base.sets, faces, printings, emptyList())
        val (file, checksum) = bytes(catalog)
        CatalogInstaller(directory, repo(), Source(file, checksum), { Long.MAX_VALUE }).install(checksum)
        val result = DeckImportResolver(repo()).resolve("Deck\n1 Test Bear (TST) 1\n2 Glorp Wizard\n1 Dawn Hunter")
        assertTrue(result.resolved, result.issues.toString())
        assertEquals(3, result.entries.size)
        for (entry in result.entries) assertEquals(
            PhysicalCardFaces.normalize(CatalogCards.definition(catalog, catalog.printing.getValue(entry.printing.id))),
            PhysicalCardFaces.normalize(entry.definition(result.catalog)), entry.printing.name)
        // A saved list's printings read back by id (owner 2026-11-10): each card's faces, front first, whichever face names
        // it, built into the server's card data; a printing the records don't have is left out
        val cards = repo().cards(listOf(id(1), id(5), id(6), id(99)))
        assertEquals(setOf(id(1), id(5), id(6)), cards.keys)
        assertEquals(listOf(id(5), id(6)), cards.getValue(id(6)).map { it.id })
        for ((printing, sides) in cards) assertEquals(
            PhysicalCardFaces.normalize(CatalogCards.definition(catalog, catalog.printing.getValue(printing))),
            PhysicalCardFaces.normalize(ResolvedDeckEntry(DeckSection.MAINBOARD, 1, sides, emptyList()).definition(result.catalog)), printing)
    }
}
