package org.asyncttwiz.android

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.asyncttwiz.catalog.CardCatalog
import org.asyncttwiz.catalog.CardCatalogFiles
import org.asyncttwiz.catalog.CardCatalogFormat
import org.asyncttwiz.catalog.CatalogFaceRecord
import org.asyncttwiz.catalog.CatalogHeader
import org.asyncttwiz.catalog.CatalogPrintingRecord
import org.asyncttwiz.catalog.CatalogSetRecord
import org.asyncttwiz.catalog.CatalogVersion
import org.asyncttwiz.protocol.CardSearchRules
import org.asyncttwiz.storage.CatalogMetadata
import org.asyncttwiz.storage.CatalogPhase
import org.asyncttwiz.storage.CatalogProgress
import org.asyncttwiz.storage.CatalogRepository
import org.asyncttwiz.storage.CatalogSource
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.createTempDirectory
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The app's card records from the server's card catalog (docs/specs/web-client.md sections 3.5, 3.6). */
class CatalogControllerTest {
    private fun catalog(version: String): Pair<ByteArray, String> {
        val faces = listOf(CatalogFaceRecord(0, "Test Bear", type = "Creature — Bear", manaCost = "{1}{G}", power = "2",
            toughness = "2", colors = listOf("G"), identity = listOf("G"), automatum = true))
        val printings = listOf(CatalogPrintingRecord("00000000-0000-4000-8000-000000000001", 0, "TST", "1"))
        val sets = listOf(CatalogSetRecord("TST", "Test Set", "expansion", "2026-01-01", draftable = true, automatum = true))
        val catalog = CardCatalog(CatalogHeader(CardCatalogFormat.FORMAT, version, "2026-10-31", CardSearchRules.FORMATS, 1, 1,
            1, 1, 1, 0), sets, faces, printings, emptyList())
        val out = ByteArrayOutputStream()
        val checksum = CardCatalogFiles.write(catalog, out)
        return out.toByteArray() to checksum
    }

    private class Source(var file: ByteArray, var checksum: String, var failures: Int = 0) : CatalogSource {
        val downloads = AtomicInteger()
        override fun current() = CatalogVersion(checksum, file.size.toLong(), "", "", 1, 1, 1)
        override fun download(checksum: String, output: OutputStream, progress: (Long) -> Unit) {
            downloads.incrementAndGet()
            if (failures-- > 0) error("offline")
            output.write(file); progress(file.size.toLong())
        }
    }

    private val main = Executors.newSingleThreadExecutor()
    private fun controller(directory: File, source: Source, expected: String?): CatalogController {
        val repository = CatalogRepository(directory) { JdbcSqliteDriver("jdbc:sqlite:${it.absolutePath}") }
        // The state is published on one thread, as the main thread does
        return CatalogController(directory, repository, { source }, expected, Executors.newSingleThreadExecutor(),
            { action -> main.execute(action) }, retryPause = 0)
    }

    private fun await(controller: CatalogController, condition: (CatalogUiState) -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
        while (!condition(controller.state)) {
            check(System.nanoTime() < deadline) { "Timed out in ${controller.state}" }
            Thread.sleep(10)
        }
    }

    // LOB-091, LOB-095: the app installs its built-in catalog on start, and only when the installed records differ
    @Test fun installsTheBuiltInCatalogOnStartAndOnlyWhenItDiffers() {
        val directory = createTempDirectory("catalog-controller").toFile()
        try {
            val (file, checksum) = catalog("5.3.0+1")
            val source = Source(file, checksum)
            val first = controller(directory, source, checksum)
            assertEquals(CardRecords.Updating(0), first.state.records)
            first.start()
            await(first) { it.ready }
            assertEquals(CardRecords.Ready, first.state.records)
            assertEquals(CatalogMetadata("2026-10-31", "5.3.0+1", checksum, 1), first.state.installed)
            assertEquals(1, source.downloads.get())
            // The next start finds the app's catalog installed and downloads nothing
            val again = controller(directory, source, checksum)
            again.start()
            await(again) { it.ready }
            assertEquals(1, source.downloads.get())
            // A new app with a new catalog installs it
            val (newer, newerChecksum) = catalog("5.3.0+2")
            source.file = newer; source.checksum = newerChecksum
            val updated = controller(directory, source, newerChecksum)
            updated.start()
            await(updated) { it.ready }
            assertEquals("5.3.0+2", updated.state.installed!!.version)
            assertEquals(2, source.downloads.get())
        } finally { directory.deleteRecursively() }
    }

    // LOB-091: a failed download is tried once more by itself, then the failure shows with Retry
    @Test fun aFailedDownloadIsTriedOnceMoreThenRetryInstallsIt() {
        val directory = createTempDirectory("catalog-controller").toFile()
        try {
            val (file, checksum) = catalog("5.3.0+1")
            val source = Source(file, checksum, failures = 3)
            val controller = controller(directory, source, checksum)
            controller.start()
            await(controller) { it.failed }
            assertEquals(2, source.downloads.get())
            assertEquals(CardRecords.Failed, controller.state.records)
            // Retry, which is also tried once more by itself: the third attempt fails, the fourth installs
            main.submit { controller.retry() }.get()
            await(controller) { it.ready }
            assertEquals(4, source.downloads.get())
        } finally { directory.deleteRecursively() }
    }

    // LOB-094: the share of the whole installation, the download first
    @Test fun progressIsOneShareOfTheWholeInstallation() {
        assertEquals(10, CatalogProgress(CatalogPhase.DOWNLOADING, 50, 100).percent)
        assertEquals(0, CatalogProgress(CatalogPhase.DOWNLOADING, 50, null).percent)
        assertEquals(20, CatalogProgress(CatalogPhase.INDEXING, 0, 100).percent)
        assertEquals(59, CatalogProgress(CatalogPhase.INDEXING, 50, 100).percent)
        assertEquals(99, CatalogProgress(CatalogPhase.FINISHING, 100, 100).percent)
        assertEquals("Card records updating · 59%", CatalogUiState(progress = CatalogProgress(CatalogPhase.INDEXING, 50, 100)).records.line())
        assertEquals("Card records couldn't be downloaded", CatalogUiState(failed = true).records.line())
    }
}
