package org.asyncttwiz.storage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.nio.file.Path
import java.security.MessageDigest
import java.time.LocalDate
import java.util.zip.GZIPOutputStream

/** LOB-125: card records installed before index version 4 kept the card→token links (SYS-072) are downloaded again
 * once, through the card records update of LOB-092 / LOB-110. Every card here is invented. */
class CatalogTokenLinksUpdateTest {
    @TempDir lateinit var temporary: Path
    private val repository by lazy {
        CatalogRepository(temporary.resolve("catalog").toFile()) { JdbcSqliteDriver("jdbc:sqlite:${it.absolutePath}") }
    }
    private val today = LocalDate.parse("2026-11-06")
    private val sets = listOf(CatalogSet("OLD", "2015-01-01"), CatalogSet("NEW", "2026-08-01"))
    private val available = CatalogSetList(CatalogMetadata("2026-11-06", "5.3.0"), sets)

    private fun id(n: Int) = "00000000-0000-4000-8000-${n.toString().padStart(12, '0')}"
    private fun card(n: Int, set: String, token: Int) = JsonObject().apply {
        addProperty("uuid", id(n)); addProperty("name", "Warren Herald $n"); addProperty("setCode", set); addProperty("number", "$n")
        addProperty("language", "English"); addProperty("layout", "normal"); addProperty("type", "Creature — Rabbit")
        addProperty("manaCost", "{1}{W}"); addProperty("text", "When this creature enters, create a 1/1 white Rabbit creature token.")
        add("relatedCards", JsonObject().apply { add("tokens", JsonArray().apply { add(id(token)) }) })
    }
    private fun token(n: Int, set: String) = JsonObject().apply {
        addProperty("uuid", id(n)); addProperty("name", "Rabbit"); addProperty("setCode", set); addProperty("number", "T$n")
        addProperty("language", "English"); addProperty("layout", "token"); addProperty("type", "Token Creature — Rabbit")
        add("colors", JsonArray().apply { add("W") }); addProperty("power", "1"); addProperty("toughness", "1")
    }
    private val root = JsonObject().apply {
        add("meta", JsonObject().apply { addProperty("date", "2026-11-06"); addProperty("version", "5.3.0") })
        add("data", JsonObject().apply {
            sets.forEachIndexed { index, set -> add(set.code, JsonObject().apply {
                addProperty("code", set.code); addProperty("name", "Invented ${set.code}"); addProperty("type", "expansion")
                addProperty("releaseDate", set.releaseDate)
                add("cards", JsonArray().apply { add(card(10 * index + 1, set.code, 10 * index + 2)) })
                add("tokens", JsonArray().apply { add(token(10 * index + 2, set.code)) })
            }) }
        })
    }
    private val payload = ByteArrayOutputStream().apply { GZIPOutputStream(this).use { it.write(root.toString().toByteArray()) } }.toByteArray()
    private val source = object : CatalogSource {
        override fun metadata() = available.metadata
        override fun size() = payload.size.toLong()
        override fun checksum() = MessageDigest.getInstance("SHA-256").digest(payload).joinToString("") { "%02x".format(it) }
        override fun download(output: OutputStream, progress: (Long) -> Unit) { output.write(payload); progress(size()) }
    }
    private fun install(plan: CatalogUpdatePlan = CatalogUpdatePlan(available.metadata, full = true)) =
        CatalogInstaller(temporary.resolve("catalog").toFile(), repository, source, { Long.MAX_VALUE }).install(plan, source.size())
    private fun sql(vararg statements: String) = repository.snapshot { index -> statements.forEach { index!!.sql(it) } }
    private fun plan() = CatalogUpdatePolicy.plan(repository.metadata(), repository.installedSets(), available, today)
    private fun links() = repository.snapshot { it!!.long("SELECT count(*) FROM token_relation") }

    // LOB-125, LOB-092
    @Test fun olderRecordsDownloadAgainOnceAskedAsAnyUpdate() {
        install()
        sql("PRAGMA user_version=3") // any catalog from before version 4, with or without links
        val installed = repository.metadata()!!
        assertEquals(3, installed.indexVersion)
        val plan = plan()!!
        assertTrue(plan.full)
        assertTrue(CatalogUpdatePolicy.canDownload(installed, plan, metered = false, consent = false), "automatic when unmetered")
        assertFalse(CatalogUpdatePolicy.canDownload(installed, plan, metered = true, consent = false), "asked when metered")
        assertTrue(CatalogUpdatePolicy.canDownload(installed, plan, metered = true, consent = true))

        assertEquals(CATALOG_INDEX_VERSION, install(plan).indexVersion)
        assertEquals(2L, links())
        assertNull(plan(), "once: the new records need nothing more")
    }
}
