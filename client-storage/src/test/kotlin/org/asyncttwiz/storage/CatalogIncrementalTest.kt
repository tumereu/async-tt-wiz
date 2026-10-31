package org.asyncttwiz.storage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.*
import java.net.InetSocketAddress
import java.nio.file.Path
import java.security.MessageDigest
import java.time.LocalDate
import java.util.UUID
import java.util.zip.GZIPOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class CatalogIncrementalTest {
    @TempDir lateinit var temporary: Path
    private val directory get() = temporary.resolve("catalog").toFile()
    private fun repo() = CatalogRepository(directory) { JdbcSqliteDriver("jdbc:sqlite:${it.absolutePath}") }
    private fun root() = JsonParser.parseString(File(System.getProperty("catalog.fixtures"), "prism/v1/AllPrintings.json").readText()).asJsonObject
    private fun gzip(text: String) = ByteArrayOutputStream().apply { GZIPOutputStream(this).use { it.write(text.toByteArray()) } }.toByteArray()
    private val today = LocalDate.parse("2026-10-25")

    private class BytesSource(private val root: JsonObject, private val payload: ByteArray) : CatalogSource {
        override fun metadata() = CatalogMetadata(root.getAsJsonObject("meta").get("date").asString,
            root.getAsJsonObject("meta").get("version").asString)
        override fun size() = payload.size.toLong()
        override fun checksum() = hash(payload)
        override fun download(output: OutputStream, progress: (Long) -> Unit) { output.write(payload); progress(size()) }
    }
    private fun install(root: JsonObject = root()): CatalogMetadata {
        val source = BytesSource(root, gzip(root.toString()))
        return CatalogInstaller(directory, repo(), source, { Long.MAX_VALUE }).install(source.metadata(), source.size())
    }

    @Test fun trimsOnlyUnusedFieldsAndPreservesImportDisplayFacesDefaultsAndArtwork() {
        val root = root()
        val card = root.getAsJsonObject("data").getAsJsonObject("ZPA").getAsJsonArray("cards").first().asJsonObject
        val kept = listOf("uuid", "name", "number", "type", "text", "manaCost", "layout", "language", "setCode",
            "availability", "isPromo", "isOnlineOnly", "side", "otherFaceIds", "faceName", "power", "toughness", "loyalty", "defense")
        for (key in listOf("power", "toughness", "loyalty", "defense")) card.addProperty(key, "*")
        val dropped = listOf("foreignData", "legalities", "rulings", "purchaseUrls", "prices", "edhrecRank", "edhrecSaltiness", "translations")
        dropped.forEach { card.addProperty(it, "unnecessary data".repeat(100)) }
        card.getAsJsonObject("identifiers").addProperty("mtgArenaId", "123")
        val metadata = install(root)
        assertEquals(2, metadata.indexVersion)
        val record = repo().printing(card.get("uuid").asString)!!
        val stored = JsonParser.parseString(record.json).asJsonObject
        kept.filter { card.has(it) }.forEach { assertEquals(card[it], stored[it], it) }
        dropped.forEach { assertFalse(stored.has(it), it) }
        assertEquals(setOf("scryfallId", "scryfallOracleId"), stored.getAsJsonObject("identifiers").keySet())
        assertTrue(record.json.length < card.toString().length / 2)
        assertEquals(record.scryfallId, ArtworkReference.from(record, metadata.date)!!.printing)
        val result = DeckImportResolver(repo()).resolve("1 ${record.name} (${record.set}) ${record.number}")
        assertTrue(result.resolved, result.issues.toString())
        assertTrue(result.entries.single().faces.all { face -> face.otherFaces.all { repo().printing(it) != null } })
    }

    // LOB-092, LOB-095
    @Test fun plansNewRecentBoundaryAndPreviewSetsButNotDailyBuildsOrOldSets() {
        val meta = CatalogMetadata("2026-10-24", "5.2.2")
        val old = CatalogSet("OLD", today.minusDays(61).toString())
        val recent = CatalogSet("REC", today.minusDays(60).toString())
        val preview = CatalogSet("FUT", today.plusDays(30).toString())
        val new = CatalogSet("NEW", "1993-01-01")
        val available = CatalogSetList(meta.copy(date = today.toString()), listOf(old, recent, preview, new))
        val plan = CatalogUpdatePolicy.plan(meta, listOf(old, recent, preview), available, today)!!
        assertFalse(plan.full)
        assertEquals(listOf("REC", "FUT", "NEW"), plan.sets.map { it.code })
        assertNull(CatalogUpdatePolicy.plan(meta, listOf(old), available.copy(sets = listOf(old)), today))
        assertTrue(CatalogUpdatePolicy.plan(null, emptyList(), available, today)!!.full)
        assertTrue(CatalogUpdatePolicy.plan(meta.copy(indexVersion = 1), listOf(old), available, today)!!.full)
        assertFalse(CatalogUpdatePolicy.canDownload(meta, plan, metered = true, consent = false))
        assertTrue(CatalogUpdatePolicy.canDownload(meta, plan, metered = true, consent = true))
        assertTrue(CatalogUpdatePolicy.canDownload(meta, plan, metered = false, consent = false))
        assertFalse(CatalogUpdatePolicy.canDownload(null, CatalogUpdatePlan(meta, full = true), false, false))
    }

    // LOB-093
    @Test fun manualFullRefreshWorksEvenWithUnchangedMetadata() {
        val root = root()
        val before = install(root)
        val generation = repo().tokenGeneration()
        val source = BytesSource(root, gzip(root.toString()))
        val plan = CatalogUpdatePolicy.fullRefresh(before)
        assertTrue(plan.full)
        val events = mutableListOf<CatalogProgress>()
        assertEquals(before, CatalogInstaller(directory, repo(), source, { Long.MAX_VALUE }).install(plan, source.size(), events::add))
        assertNotEquals(generation, repo().tokenGeneration())
        assertEquals(CatalogPhase.FINISHING, events.last().phase)
    }

    @Test fun readersInSeparateRepositoriesCannotRaceGenerationReclamation() {
        val before = install()
        val opening = CountDownLatch(1)
        val resume = CountDownLatch(1)
        val reader = CatalogRepository(directory) { file ->
            opening.countDown()
            check(resume.await(5, TimeUnit.SECONDS))
            JdbcSqliteDriver("jdbc:sqlite:${file.absolutePath}")
        }
        val workers = Executors.newFixedThreadPool(2)
        try {
            val read = workers.submit<CatalogMetadata?> { reader.metadata() }
            assertTrue(opening.await(5, TimeUnit.SECONDS))
            val writer = workers.submit<CatalogMetadata> { repo().stage(copyInstalled = true) { before } }
            assertThrows(TimeoutException::class.java) { writer.get(100, TimeUnit.MILLISECONDS) }
            resume.countDown()
            assertEquals(before, read.get(5, TimeUnit.SECONDS))
            assertEquals(before, writer.get(5, TimeUnit.SECONDS))
            assertEquals(before, repo().metadata())
        } finally { resume.countDown(); workers.shutdownNow() }
    }

    @Test fun localHttpUpdatesOnlySelectedSetsAndRejectsChecksumAndMidApplyCrash() {
        val root = root()
        val before = install(root)
        val originalSets = repo().installedSets()
        assertTrue(originalSets.all { it.installedAt > 0 && it.checksum.length == 64 })
        val oldData = root.getAsJsonObject("data")
        val recent = oldData.getAsJsonObject("ZPA").deepCopy().apply {
            addProperty("releaseDate", today.minusDays(1).toString())
            getAsJsonArray("cards").first().asJsonObject.addProperty("text", "Updated preview text")
            // Deleted tokens must disappear from both records and search postings.
            add("tokens", JsonArray())
        }
        val fresh = JsonObject().apply {
            addProperty("code", "CON")
            addProperty("releaseDate", "2009-02-06")
            add("cards", JsonArray().apply { add(recent.getAsJsonArray("cards").first().deepCopy().asJsonObject.apply {
                addProperty("uuid", UUID.randomUUID().toString()); addProperty("setCode", "CON")
                addProperty("name", "Conflux fixture"); remove("side"); remove("otherFaceIds")
            }) })
        }
        val sets = oldData.entrySet().map { (code, _) -> CatalogSet(code, if (code == "ZPA") today.toString() else "2000-01-01") } +
            CatalogSet("CON", "2009-02-06")
        val setList = JsonObject().apply {
            add("meta", root["meta"])
            add("data", JsonArray().apply { sets.forEach { add(JsonObject().apply {
                addProperty("code", it.code); addProperty("releaseDate", it.releaseDate)
            }) } })
        }
        val files = mutableMapOf("/SetList.json" to setList.toString().toByteArray())
        fun setPayload(code: String, set: JsonObject) {
            val bytes = gzip(JsonObject().apply { add("meta", root["meta"]); add("data", set) }.toString())
            files["/$code.json.gz"] = bytes
            files["/$code.json.gz.sha256"] = hash(bytes).toByteArray()
        }
        setPayload("ZPA", recent); setPayload("CON_", fresh)
        val requests = mutableListOf<String>()
        var redirect = false
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            requests += exchange.requestURI.path
            assertNull(exchange.requestHeaders.getFirst("Authorization"))
            assertNull(exchange.requestHeaders.getFirst("Cookie"))
            if (redirect) {
                exchange.responseHeaders.set("Location", "http://169.254.169.254/latest/meta-data/")
                exchange.sendResponseHeaders(302, -1)
            } else {
                val bytes = checkNotNull(files[exchange.requestURI.path])
                exchange.responseHeaders.set("Content-Length", bytes.size.toString())
                exchange.sendResponseHeaders(200, if (exchange.requestMethod == "HEAD") -1 else bytes.size.toLong())
                if (exchange.requestMethod != "HEAD") exchange.responseBody.use { it.write(bytes) }
            }
            exchange.close()
        }
        server.start()
        try {
            val origin = "http://127.0.0.1:${server.address.port}"
            val source = HttpCatalogSource("$origin/", origin)
            val available = source.setList()
            val plan = CatalogUpdatePolicy.plan(before, originalSets, available, today)!!
            assertEquals(listOf("ZPA", "CON"), plan.sets.map { it.code })
            val installer = CatalogInstaller(directory, repo(), source, { Long.MAX_VALUE })
            files["/CON_.json.gz.sha256"] = "0".repeat(64).toByteArray()
            assertThrows(Exception::class.java) { installer.install(plan) }
            assertEquals(before, repo().metadata())
            setPayload("CON_", fresh)
            val originalGeneration = repo().tokenGeneration()
            assertThrows(IOException::class.java) { installer.install(plan) { event ->
                if (event.phase == CatalogPhase.INDEXING && event.records > 0) throw IOException("Crash after replacement batch")
            } }
            assertEquals(before, repo().metadata())
            assertEquals(originalGeneration, repo().tokenGeneration())
            assertEquals(originalSets, repo().installedSets())
            assertTrue(DeckImportResolver(repo()).resolve("1 Velvet Prism Engine").resolved)
            val tokensBefore = repo().searchTokens("").tokens
            val events = mutableListOf<CatalogProgress>()
            val installed = installer.install(plan, progress = events::add)
            assertEquals(installed, repo().metadata())
            assertNotEquals(originalGeneration, repo().tokenGeneration())
            assertEquals("Updated preview text", JsonParser.parseString(repo().printing(recent.getAsJsonArray("cards").first()
                .asJsonObject["uuid"].asString)!!.json).asJsonObject["text"].asString)
            assertEquals(1, repo().find("Conflux fixture").size)
            originalSets.filter { it.code != "ZPA" }.forEach { old -> assertEquals(old, repo().installedSets().single { it.code == old.code }) }
            assertTrue(repo().installedSets().single { it.code == "ZPA" }.installedAt >= originalSets.single { it.code == "ZPA" }.installedAt)
            assertEquals(hash(files["/ZPA.json.gz"]!!), repo().installedSets().single { it.code == "ZPA" }.checksum)
            assertEquals(tokensBefore.filter { it.printing.set != "ZPA" }.map { it.printing.id }, repo().searchTokens("").tokens.map { it.printing.id })
            assertEquals(CatalogPhase.FINISHING, events.last().phase)
            // One bar for the whole update: totals cover every set and progress never restarts per set.
            val downloadTotal = files["/ZPA.json.gz"]!!.size.toLong() + files["/CON_.json.gz"]!!.size
            for (phase in listOf(CatalogPhase.DOWNLOADING, CatalogPhase.INDEXING)) {
                val phased = events.filter { it.phase == phase }
                assertTrue(phased.isNotEmpty(), phase.name)
                assertTrue(phased.all { it.total == downloadTotal }, phase.name)
                assertEquals(phased.map { it.done }.sorted(), phased.map { it.done }, phase.name)
                assertEquals(phased.map { it.records }.sorted(), phased.map { it.records }, phase.name)
            }
            assertEquals(downloadTotal, events.last { it.phase == CatalogPhase.DOWNLOADING }.done)
            assertFalse(requests.any { "AllPrintings" in it })
            assertTrue(requests.all { it == "/SetList.json" || it.startsWith("/ZPA.") || it.startsWith("/CON_.") })
            redirect = true
            assertThrows(Exception::class.java) { source.setList() }
            assertThrows(Exception::class.java) { source.setSource("CON").checksum() }
            assertThrows(IllegalArgumentException::class.java) { source.setSource("../Meta") }
            assertThrows(IllegalArgumentException::class.java) { source.setSource("CON_") }
        } finally { server.stop(0) }
    }

    companion object {
        private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
