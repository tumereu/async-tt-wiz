package org.asyncttwiz.storage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.google.gson.JsonParser
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.*
import java.net.InetSocketAddress
import java.nio.file.Path
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.zip.GZIPOutputStream

class CatalogTest {
    @TempDir lateinit var temporary: Path
    private val fixtures get() = File(System.getProperty("catalog.fixtures"))
    private val directory get() = temporary.resolve("catalog").toFile()
    private fun repo() = CatalogRepository(directory) { JdbcSqliteDriver("jdbc:sqlite:${it.absolutePath}") }
    private class FilesSource(var edition: File) : CatalogSource {
        var payload: ByteArray? = null
        var hash: String? = null
        override fun metadata() = File(edition, "Meta.json").inputStream().use { MtgJsonCatalog().metadata(it) }
        fun bytes() = payload ?: File(edition, "AllPrintings.json.gz").readBytes()
        override fun size() = bytes().size.toLong()
        override fun checksum() = hash ?: MessageDigest.getInstance("SHA-256").digest(bytes()).joinToString("") { "%02x".format(it) }
        override fun download(output: OutputStream, progress: (Long) -> Unit) { output.write(bytes()); progress(size()) }
    }
    private fun install(source: CatalogSource, repository: CatalogRepository = repo(), limit: Long = HttpCatalogSource.MAX_BYTES,
        space: () -> Long = { Long.MAX_VALUE }): CatalogMetadata = CatalogInstaller(directory, repository, source, space, limit)
        .install(source.metadata(), source.size())
    private fun gzip(text: String): ByteArray = ByteArrayOutputStream().apply {
        GZIPOutputStream(this).use { it.write(text.toByteArray()) }
    }.toByteArray()

    @Test fun bothFamiliesVersionsExactRecordsFacesAndReopen() {
        for (family in listOf("prism", "quilt")) {
            val repository = repo()
            val source = FilesSource(File(fixtures, "$family/v1"))
            val first = install(source, repository)
            assertEquals("2026-10-11", first.date)
            assertTrue(first.records > 10)
            val root = JsonParser.parseString(File(source.edition, "AllPrintings.json").readText()).asJsonObject
            var count = 0L
            root.getAsJsonObject("data").entrySet().forEach { (set, value) ->
                for (section in listOf("cards", "tokens")) value.asJsonObject.getAsJsonArray(section).forEach { element ->
                    val json = element.asJsonObject
                    val record = repo().printing(json.get("uuid").asString)!!
                    assertEquals(json, JsonParser.parseString(record.json))
                    assertEquals(set, record.set)
                    assertEquals(section == "tokens", record.token)
                    assertTrue(repo().find(record.name, set, record.number).contains(record))
                    for (other in record.otherFaces) assertTrue(record.id in repo().printing(other)!!.otherFaces)
                    count++
                }
            }
            assertEquals(count, repo().metadata()!!.records)
            val oldDate = repo().metadata()!!.date
            source.edition = File(fixtures, "$family/v2")
            install(source, repository)
            assertNotEquals(oldDate, repo().metadata()!!.date)
            assertEquals(count + 1, repo().metadata()!!.records)
            assertEquals(1, directory.listFiles()!!.count { it.extension == "db" })
            println("PASS $family v1/v2 exact printing/face/token/default JSON, indexed lookup, atomic switch and reopen")
        }
    }

    @Test fun failuresKeepPreviousCatalogAndPrivateDatabaseUntouched() {
        val repository = repo()
        val source = FilesSource(File(fixtures, "prism/v1"))
        val previous = install(source, repository)
        val privateFile = temporary.resolve("private.db").toFile()
        (ClientStorage.open { JdbcSqliteDriver("jdbc:sqlite:${privateFile.absolutePath}", java.util.Properties().apply {
            setProperty("foreign_keys", "true")
        }) } as StorageOpenResult.Opened).storage.use {
            it.commitAccepted("game", null, 1, listOf(AcceptedRecord(RecordKey("definition", "retained"), StoredPayload(1, "private v1"))))
        }
        val privateBytes = privateFile.readBytes()
        val original = File(fixtures, "prism/v2/AllPrintings.json").readText()
        fun failure(label: String, mutate: () -> Unit) {
            source.edition = File(fixtures, "prism/v2"); source.payload = null; source.hash = null
            mutate()
            assertThrows(Exception::class.java) { install(source, repository) }
            assertEquals(previous, repo().metadata(), label)
            assertArrayEquals(privateBytes, privateFile.readBytes())
            println("PASS $label retains exact active generation/private DB")
        }
        failure("checksum") { source.hash = "0".repeat(64) }
        failure("truncated gzip") { source.payload = source.bytes().copyOf(source.bytes().size - 12) }
        failure("unsupported schema") { source.payload = gzip(original.replace("5.2.2", "6.0.0")) }
        failure("missing reciprocal face") {
            val root = JsonParser.parseString(original).asJsonObject
            val set = root.getAsJsonObject("data").entrySet().first().value.asJsonObject
            set.getAsJsonArray("cards").first { it.asJsonObject.has("otherFaceIds") }.asJsonObject.remove("otherFaceIds")
            source.payload = gzip(root.toString())
        }
        failure("invalid UTF-8") { source.payload = ByteArrayOutputStream().apply {
            GZIPOutputStream(this).use { it.write(byteArrayOf(0xc3.toByte(), 0x28)) }
        }.toByteArray() }
        source.payload = null; source.hash = null
        assertThrows(Exception::class.java) { install(source, repository, space = { 10 }) }
        assertThrows(Exception::class.java) { install(source, repository, limit = 100) }
        assertEquals(previous, repo().metadata())
        assertArrayEquals(privateBytes, privateFile.readBytes())
    }

    @Test fun interruptionAndUnpublishedStagingNeverBecomeActive() {
        val repository = repo()
        val source = FilesSource(File(fixtures, "quilt/v1"))
        val before = install(source, repository)
        assertThrows(Exception::class.java) { repository.stage { index ->
            val records = (0 until 250).map { number -> CatalogPrinting(java.util.UUID.randomUUID().toString(), "Record $number",
                "SET", "$number", "English", "", null, false, emptyList(), "{}") }
            index.addBatch(records)
            throw IOException("simulated process interruption after committed staging batch")
        } }
        File(directory, "${java.util.UUID.randomUUID()}.db").writeText("unpublished crash residue")
        File(directory, "current.new").writeText("unpublished pointer")
        assertEquals(before, repo().metadata())
        Thread.currentThread().interrupt()
        try { assertThrows(Exception::class.java) { install(source, repository) } }
        finally { Thread.interrupted() }
        assertEquals(before, repo().metadata())
        install(source, repository)
        assertEquals(1, directory.listFiles()!!.count { it.extension == "db" })
    }

    @Test fun boundedRecordsFieldsNestingDuplicateIdsAndTrailingJsonAreRejected() {
        val source = FilesSource(File(fixtures, "prism/v1"))
        val before = install(source)
        val original = File(source.edition, "AllPrintings.json").readText()
        val mutations = listOf(
            original.replace("Original synthetic survey text, revision 1.", "x".repeat(64 * 1024 + 1)),
            original.dropLast(2),
            original + "{}",
            original.replace("\"text\":", "\"text\": \"duplicate\", \"text\":"),
            original.replace("\"legalities\": {}", "\"legalities\":" + "[".repeat(33) + "0" + "]".repeat(33)),
        )
        mutations.forEach { text -> source.payload = gzip(text); assertThrows(Exception::class.java) { install(source) } }
        assertEquals(before, repo().metadata())
    }

    @Test fun cadencePersistsSuccessBackoffAndClockCorrectionAcrossRelaunches() {
        val file = temporary.resolve("cadence").toFile()
        fun cadence() = CatalogCadence(file)
        val time = 1_000_000_000L
        assertTrue(cadence().due(time))
        cadence().checked(time, true)
        assertFalse(cadence().due(time + 86_399_999))
        assertTrue(cadence().due(time + 86_400_000))
        var now = time + 86_400_000
        for (delay in listOf(900_000L, 3_600_000L, 21_600_000L, 21_600_000L)) {
            cadence().checked(now, false)
            assertFalse(cadence().due(now + delay - 1))
            assertTrue(cadence().due(now + delay))
            now += delay
        }
        cadence().checked(now, true)
        assertEquals(0, cadence().state().failures)
        assertTrue(cadence().due(now - 1))
    }

    @Test fun initialAndMeteredDownloadsRequireConsentButUnchangedStartupNeverDownloads() {
        val old = CatalogMetadata("2026-01-01", "5.2.2")
        val new = old.copy(date = "2026-01-02")
        for (metered in listOf(false, true)) {
            assertFalse(CatalogUpdatePolicy.download(null, new, metered, false))
            assertTrue(CatalogUpdatePolicy.download(null, new, metered, true))
            assertFalse(CatalogUpdatePolicy.download(old, old, metered, true))
            assertFalse(CatalogUpdatePolicy.download(old, null, metered, true))
        }
        assertFalse(CatalogUpdatePolicy.download(old, new, true, false))
        assertTrue(CatalogUpdatePolicy.download(old, new, true, true))
        assertTrue(CatalogUpdatePolicy.download(old, new, false, false))
        assertTrue(CatalogUpdatePolicy.changed(old, old.copy(version = "5.2.3")))
    }

    @Test fun metadataTimeoutIsBoundedToTenSeconds() {
        val executor = Executors.newSingleThreadExecutor()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply { this.executor = executor }
        server.createContext("/") { exchange -> try { Thread.sleep(15_000) } catch (_: InterruptedException) { } finally { exchange.close() } }
        server.start()
        val started = System.nanoTime()
        try {
            val origin = "http://127.0.0.1:${server.address.port}"
            assertThrows(Exception::class.java) { HttpCatalogSource("$origin/", origin).metadata() }
            val elapsed = (System.nanoTime() - started) / 1_000_000
            assertTrue(elapsed in 9500..11_500, "actual timeout $elapsed ms")
            println("PASS actual stalled Meta.json timeout: $elapsed ms")
        } finally { server.stop(0); executor.shutdownNow() }
    }

    @Test fun realHttpUsesSameParserBothFamiliesAndRejectsUnsafeRedirectsWithoutCredentials() {
        val executor = Executors.newCachedThreadPool()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply { this.executor = executor }
        var redirects = false
        val requests = mutableListOf<String>()
        server.createContext("/") { exchange ->
            synchronized(requests) {
                assertNull(exchange.requestHeaders.getFirst("Authorization"))
                assertNull(exchange.requestHeaders.getFirst("Cookie"))
                assertTrue(exchange.requestHeaders.getFirst("User-Agent").startsWith("AsyncTabletopWizards/"))
                requests += "${exchange.requestMethod} ${exchange.requestURI.path}"
            }
            if (redirects) {
                exchange.responseHeaders.set("Location", "http://169.254.169.254/latest/meta-data/")
                exchange.sendResponseHeaders(302, -1)
            } else {
                val file = File(fixtures, exchange.requestURI.path.removePrefix("/"))
                val bytes = file.readBytes()
                exchange.responseHeaders.set("Content-Length", bytes.size.toString())
                exchange.sendResponseHeaders(200, if (exchange.requestMethod == "HEAD") -1 else bytes.size.toLong())
                if (exchange.requestMethod != "HEAD") exchange.responseBody.use { it.write(bytes) }
            }
            exchange.close()
        }
        server.start()
        try {
            val origin = "http://127.0.0.1:${server.address.port}"
            for (family in listOf("prism", "quilt")) {
                val source = HttpCatalogSource("$origin/$family/v1/", origin)
                install(source)
                assertEquals(source.metadata().date, repo().metadata()!!.date)
            }
            assertEquals(2, requests.count { it.startsWith("GET ") && it.endsWith(".gz") })
            redirects = true
            assertThrows(Exception::class.java) { HttpCatalogSource("$origin/prism/v1/", origin).metadata() }
            assertThrows(IllegalArgumentException::class.java) { HttpCatalogSource("http://127.0.0.1:80/") }
            assertThrows(IllegalArgumentException::class.java) { HttpCatalogSource("https://evil.example/") }
            assertThrows(IllegalArgumentException::class.java) { HttpCatalogSource("$origin@evil.example/prism/", origin) }
        } finally { server.stop(0); executor.shutdownNow() }
        println("PASS real credential-free HTTP/HEAD/checksum/gzip streaming for both families, redirect origin isolation")
    }
}
