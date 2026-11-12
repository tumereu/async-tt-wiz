package org.asyncttwiz.storage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.asyncttwiz.catalog.CardCatalogFormat
import org.asyncttwiz.catalog.CatalogFaceRecord
import org.asyncttwiz.catalog.CatalogHeader
import org.asyncttwiz.catalog.CatalogPrintingRecord
import org.asyncttwiz.catalog.CatalogRecord
import org.asyncttwiz.catalog.CatalogSetRecord
import org.asyncttwiz.catalog.CatalogVersion
import org.asyncttwiz.protocol.CardSearchRules
import org.asyncttwiz.protocol.DefinitionFace

// The browser's card records (docs/specs/web-client.md §3.5, §3.6): the installer's checksum, retry and kept-file logic over
// fakes of the browser's download, storage and unpacking, and the in-memory records' reads. Invented cards only.
class BrowserCatalogTest {
    /** A catalog file as the fakes carry it: its lines and the checksum unpacking them works out. */
    private data class File(val text: String, val checksum: String)

    private class Server(var version: String, val files: MutableMap<String, File>) : CatalogDownloads<File> {
        var reachable = true
        var downloads = 0
        val failDownloads = ArrayDeque<Exception>()
        override suspend fun current(): CatalogVersion {
            check(reachable) { "Offline" }
            return CatalogVersion(version, 1000, "5.3.0+test", "2026-11-01", 2, 6, 7)
        }
        override suspend fun download(checksum: String, progress: (Long) -> Unit): File {
            check(reachable) { "Offline" }
            downloads++
            failDownloads.removeFirstOrNull()?.let { throw it }
            progress(500); progress(1000)
            return files.getValue(checksum)
        }
    }

    private class Kept : CatalogFiles<File> {
        var installed: String? = null
        val files = LinkedHashMap<String, File>()
        override suspend fun installed() = installed
        override suspend fun read(checksum: String) = files[checksum]
        override suspend fun install(checksum: String, file: File) { files.clear(); files[checksum] = file; installed = checksum }
        override suspend fun clear() { files.clear(); installed = null }
    }

    private fun unpack(file: File): UnpackedCatalog = object : UnpackedCatalog {
        // Parts that cut lines anywhere, as the browser's decoder does
        private val parts = file.text.chunked(97).toMutableList()
        private var offset = 0L
        override val checksum = file.checksum
        override val size = file.text.length.toLong()
        override val read get() = offset
        override suspend fun next(): String? = parts.removeFirstOrNull()?.also { offset += it.length }
    }

    private fun installer(records: BrowserCardRecords, server: Server, kept: Kept, log: MutableList<String> = mutableListOf()) =
        BrowserCatalogInstaller(records, server, kept, { unpack(it) }, log = { log += it }, retryPause = 10, pause = { yield() })

    @Test
    fun aMissingCatalogIsDownloadedCheckedAndKept() = runTest {
        val records = BrowserCardRecords()
        val server = Server(A, mutableMapOf(A to File(lines(), A)))
        val kept = Kept()
        val phases = mutableListOf<CatalogPhase>()
        val metadata = installer(records, server, kept).run(A) { phases += it.phase }
        assertEquals(A, metadata.checksum)
        assertEquals(7, metadata.records)
        assertEquals(A, kept.installed)
        assertEquals(listOf(CatalogPhase.DOWNLOADING, CatalogPhase.INDEXING, CatalogPhase.FINISHING), phases.distinct())
        assertEquals(A, records.tokenGeneration())
    }

    @Test
    fun theKeptCatalogIsReadWithoutADownloadWhenItIsTheExpectedOne() = runTest {
        val server = Server(A, mutableMapOf(A to File(lines(), A)))
        val kept = Kept().apply { install(A, File(lines(), A)) }
        val records = BrowserCardRecords()
        val phases = mutableListOf<CatalogPhase>()
        installer(records, server, kept).run(A) { phases += it.phase }
        assertEquals(0, server.downloads)
        assertTrue(CatalogPhase.DOWNLOADING !in phases)
        assertNotNull(records.metadata())
        // Already in memory: nothing is read again
        installer(records, server, kept).run(A)
        assertEquals(0, server.downloads)
    }

    @Test
    fun aDifferentKeptCatalogIsReplaced() = runTest {
        val server = Server(B, mutableMapOf(B to File(lines(), B)))
        val kept = Kept().apply { install(A, File(lines(), A)) }
        val records = BrowserCardRecords()
        installer(records, server, kept).run(B)
        assertEquals(1, server.downloads)
        assertEquals(B, kept.installed)
        assertEquals(setOf(B), kept.files.keys)
    }

    @Test
    fun aFileThatIsntTheExpectedCatalogIsNeverKeptAndIsTriedOnceMore() = runTest {
        val server = Server(A, mutableMapOf(A to File(lines(), B)))
        val kept = Kept()
        val records = BrowserCardRecords()
        val failure = assertFailsWith<IllegalStateException> { installer(records, server, kept).run(A) }
        assertTrue("checksum mismatch" in failure.message.orEmpty())
        assertEquals(2, server.downloads)
        assertNull(kept.installed)
        assertNull(records.metadata())
    }

    @Test
    fun aFailedDownloadIsTriedOnceMoreByItself() = runTest {
        val server = Server(A, mutableMapOf(A to File(lines(), A)))
        server.failDownloads += IllegalStateException("Connection reset")
        val records = BrowserCardRecords()
        val log = mutableListOf<String>()
        installer(records, server, Kept(), log).run(A)
        assertEquals(2, server.downloads)
        assertTrue(log.any { "trying once more" in it })
        assertNotNull(records.metadata())
    }

    @Test
    fun aDamagedKeptCatalogIsDroppedAndDownloadedAgain() = runTest {
        val server = Server(A, mutableMapOf(A to File(lines(), A)))
        val kept = Kept().apply { install(A, File(lines().dropLast(40), A)) }
        val records = BrowserCardRecords()
        installer(records, server, kept).run(A)
        assertEquals(1, server.downloads)
        assertEquals(lines(), kept.files.getValue(A).text)
    }

    @Test
    fun withoutABuiltInChecksumTheServersCurrentIsUsedAndAnOfflineServerKeepsTheKeptOne() = runTest {
        val server = Server(B, mutableMapOf(B to File(lines(), B)))
        val kept = Kept()
        installer(BrowserCardRecords(), server, kept).run(null)
        assertEquals(B, kept.installed)
        server.reachable = false
        val records = BrowserCardRecords()
        assertEquals(B, installer(records, server, kept).run(null).checksum)
        // Offline with nothing kept: it fails, so the screens offer Retry
        assertFailsWith<IllegalStateException> { installer(BrowserCardRecords(), server, Kept()).run(null) }
    }

    @Test
    fun aCatalogOfAnotherFormatIsRefused() = runTest {
        val other = lines().replaceFirst("\"format\":${CardCatalogFormat.FORMAT}", "\"format\":${CardCatalogFormat.FORMAT + 1}")
        val server = Server(A, mutableMapOf(A to File(other, A)))
        assertFailsWith<IllegalStateException> { installer(BrowserCardRecords(), server, Kept()).run(A) }
    }

    @Test
    fun theRecordsAnswerDeckImportsAsAndroidsDo() = runTest {
        val records = loaded()
        val catalog = assertNotNull(records.current)
        assertEquals(listOf("p-bear-old", "p-bear-tst"), catalog.find("TEST BEAR", null, null, 10).map { it.id })
        assertEquals(listOf("p-bear-tst"), catalog.find("Test Bear", "TST", "1", 10).map { it.id })
        assertEquals(listOf("p-dawn-b"), catalog.find("dusk stalker", null, null, 10).map { it.id })
        assertEquals(listOf("p-glorp"), catalog.findScryfall(scryfall(3).uppercase()).map { it.id })
        // A printing whose faces don't link up is left out, as Android's validation does
        assertNull(catalog.printing("p-broken"))
        val dawn = assertNotNull(catalog.printing("p-dawn-a"))
        assertEquals(listOf("p-dawn-b"), dawn.otherFaces)
        assertEquals("2026-01-01", dawn.releaseDate)
        val result = DeckImportResolver(records).resolve("2 Test Bear\n1 Dawn Hunter\n1 Glorp Wizard (TST) 2")
        assertEquals(emptyList(), result.issues)
        // Newest set first, as the default printing
        assertEquals(listOf("p-bear-tst", "p-dawn-a", "p-glorp"), result.entries.map { it.printing.id })
        assertEquals(listOf("p-dawn-a", "p-dawn-b"), result.entries[1].faces.map { it.id })
        assertTrue(DeckImportResolver(BrowserCardRecords()).resolve("1 Test Bear").issues.single().message.contains("not installed"))
    }

    @Test
    fun theRecordsAnswerMarksSetsAndCards() = runTest {
        val records = loaded()
        assertEquals(listOf(DraftableSet("TST", "Test Set", "2026-01-01", 4, false, listOf("Glorp Wizard"), "tst")), records.draftableSets())
        assertEquals(setOf("p-glorp", "p-unknown"), records.unsupported(listOf("p-bear-tst", "p-glorp", "p-unknown")))
        assertEquals(setOf(scryfall(3)), records.unsupportedPictures(listOf(scryfall(1), scryfall(3).uppercase())))
        assertEquals(listOf("p-dawn-a", "p-dawn-b"), records.cards(listOf("p-dawn-b", "p-missing")).getValue("p-dawn-b").map { it.id })
        assertEquals(scryfall(6), records.emblemArt("Creatures you control get +1/+1. (Reminder.)"))
        val bear = DefinitionFace("f", "Bear Token", typeLine = "Token Creature — Bear", power = "2", toughness = "2", colorIndicator = "G")
        assertEquals("p-token", records.tokenArt(bear, "Test Bear", "p-bear-tst")?.id)
        // Tokens too, as Android's search of avatar names
        assertEquals(listOf("Bear", "Test Bear"), records.avatarChoices("bear").map { it.name })
    }

    @Test
    fun theRecordsSearchCardsInPages() = runTest {
        val records = loaded()
        val creatures = assertIs<CatalogCardSearch.Page>(records.searchCards("t:creature", limit = 2))
        assertEquals(listOf("Dawn Hunter // Dusk Stalker", "Glorp Wizard"), creatures.cards.map { it.name })
        val next = assertNotNull(creatures.next)
        val rest = assertIs<CatalogCardSearch.Page>(records.searchCards("t:creature", next, limit = 2))
        assertEquals(listOf("Test Bear"), rest.cards.map { it.name })
        assertNull(rest.next)
        // A set term shows the card with that set's printing
        val old = assertIs<CatalogCardSearch.Page>(records.searchCards("bear s:old"))
        assertEquals(listOf("p-bear-old"), old.cards.map { it.printing.id })
        assertIs<CatalogCardSearch.Invalid>(records.searchCards("bolt foo:bar"))
        assertFailsWith<CatalogGenerationChangedException> {
            records.searchCards("t:creature", next.copy(generation = B), limit = 2)
        }
        val data = records.searchData(listOfNotNull(records.current?.printing("p-bear-old")))
        assertEquals(setOf("standard", "modern"), data.getValue("p-bear-old").legal)
        assertFailsWith<CatalogNotInstalledException> { BrowserCardRecords().searchCards("t:creature") }
    }

    private suspend fun loaded(): BrowserCardRecords {
        val records = BrowserCardRecords()
        installer(records, Server(A, mutableMapOf(A to File(lines(), A))), Kept()).run(A)
        return records
    }

    private companion object {
        val A = "a".repeat(64)
        val B = "b".repeat(64)
        fun scryfall(n: Int) = "00000000-0000-4000-8000-00000000000$n"

        fun lines(): String {
            val legal = (1L shl CardSearchRules.FORMATS.indexOf("standard")) or (1L shl CardSearchRules.FORMATS.indexOf("modern"))
            val faces = listOf(
                CatalogFaceRecord(0, "Test Bear", type = "Creature — Bear", manaCost = "{1}{G}", power = "2", toughness = "2",
                    colors = listOf("G"), identity = listOf("G"), legal = legal, oracle = "o-bear", automatum = true),
                CatalogFaceRecord(1, "Glorp Wizard", type = "Creature — Human Wizard", manaCost = "{U}", text = "Flying",
                    power = "1", toughness = "1", colors = listOf("U"), identity = listOf("U"), oracle = "o-glorp"),
                CatalogFaceRecord(2, "Dawn Hunter // Dusk Stalker", faceName = "Dawn Hunter", layout = "transform", side = "a",
                    type = "Creature — Human", manaCost = "{W}", power = "1", toughness = "1", colors = listOf("W"),
                    identity = listOf("W", "B"), oracle = "o-dawn", automatum = true),
                CatalogFaceRecord(3, "Dawn Hunter // Dusk Stalker", faceName = "Dusk Stalker", layout = "transform", side = "b",
                    type = "Creature — Vampire", power = "3", toughness = "3", colors = listOf("B"), identity = listOf("W", "B"),
                    oracle = "o-dawn", automatum = true),
                CatalogFaceRecord(4, "Bear", layout = "token", type = "Token Creature — Bear", power = "2", toughness = "2",
                    colors = listOf("G")),
                CatalogFaceRecord(5, "Test Emblem", layout = "emblem", type = "Emblem — Test", text = "Creatures you control get +1/+1."),
            )
            val printings = listOf(
                CatalogPrintingRecord("p-bear-tst", 0, "TST", "1", scryfall(1), "common", availability = listOf("paper"), tokens = listOf("p-token")),
                CatalogPrintingRecord("p-bear-old", 0, "OLD", "10", scryfall(2), "common", availability = listOf("paper")),
                CatalogPrintingRecord("p-glorp", 1, "TST", "2", scryfall(3), "rare", availability = listOf("paper")),
                CatalogPrintingRecord("p-dawn-a", 2, "TST", "3", scryfall(4), "uncommon", others = listOf("p-dawn-b"), availability = listOf("paper")),
                CatalogPrintingRecord("p-dawn-b", 3, "TST", "3", scryfall(4), "uncommon", others = listOf("p-dawn-a"), availability = listOf("paper")),
                CatalogPrintingRecord("p-token", 4, "TST", "T1", scryfall(5), token = true, makers = listOf("test bear")),
                CatalogPrintingRecord("p-emblem", 5, "TST", "E1", scryfall(6), token = true),
                CatalogPrintingRecord("p-broken", 1, "OLD", "5", scryfall(7), "common", others = listOf("p-missing")),
            )
            val sets = listOf(
                CatalogSetRecord("TST", "Test Set", "expansion", "2026-01-01", "tst", draftable = true, unsupported = listOf("Glorp Wizard")),
                CatalogSetRecord("OLD", "Old Set", "core", "2001-01-01"),
            )
            val header = CatalogHeader(CardCatalogFormat.FORMAT, "5.3.0+test", "2026-11-01", CardSearchRules.FORMATS, 1, 1,
                sets.size, faces.size, printings.size, 0)
            return (listOf<CatalogRecord>(header) + sets + faces + printings).joinToString("") { CardCatalogFormat.encode(it) + "\n" }
        }
    }
}
