package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.asyncttwiz.android.AUTOMATUM_SOURCE_BLOCKED
import org.asyncttwiz.android.StoredPodSets
import org.asyncttwiz.storage.CatalogMetadata
import org.asyncttwiz.storage.CatalogRepository
import org.asyncttwiz.storage.CatalogSource
import org.asyncttwiz.storage.MtgJsonCatalog
import org.asyncttwiz.storage.SetSourceRepository
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.GZIPOutputStream
import kotlin.io.path.createTempDirectory
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The card catalog's support marks (docs/specs/web-client.md section 3.8) decide which sets can be drafted: Alice's phone
 * has the cards of a set it doesn't know Automatum's verdict on yet ("Unlisted Set", OFF), and the server's catalog (the
 * test cards' catalog) says its packs hold a card Automatum doesn't support. She picks it and presses Create: the server
 * refuses and she reads why; no pod is made.
 */
class SetSupportMarksTest : E2e() {
    private val metadata = CatalogMetadata("2026-10-31", "5.3.0")
    private fun id(name: String) = UUID.nameUUIDFromBytes("e2e-marks/$name".encodeToByteArray()).toString()

    private val set: JsonObject = run {
        val names = (1..15).map { "Plain Bear $it" }
        buildJsonObject {
            put("code", "OFF"); put("name", "Unlisted Set"); put("type", "expansion"); put("releaseDate", "2020-04-01")
            putJsonArray("cards") {
                names.forEachIndexed { index, name -> addJsonObject {
                    put("uuid", id(name)); put("name", name); put("number", "${index + 1}"); put("language", "English")
                    put("layout", "normal"); put("type", "Creature — Bear"); put("manaCost", "{1}{G}"); put("power", "2")
                    put("toughness", "2"); put("rarity", "common"); putJsonArray("colors") { add("G") }
                } }
            }
            putJsonObject("booster") { putJsonObject("play") {
                putJsonArray("boosters") { addJsonObject { put("weight", 1); putJsonObject("contents") { put("main", 15) } } }
                put("boostersTotalWeight", 1)
                putJsonObject("sheets") { putJsonObject("main") {
                    putJsonObject("cards") { names.forEach { put(id(it), 1) } }
                    put("totalWeight", names.size)
                } }
                putJsonArray("sourceSetCodes") { add("OFF") }
            } }
        }
    }

    private val source = object : CatalogSource {
        val file = ByteArrayOutputStream().apply {
            GZIPOutputStream(this).use { it.write(buildJsonObject { put("data", set) }.toString().encodeToByteArray()) }
        }.toByteArray()
        override fun metadata() = metadata
        override fun size(): Long = file.size.toLong()
        override fun checksum() = MessageDigest.getInstance("SHA-256").digest(file).joinToString("") { "%02x".format(it) }
        override fun download(output: OutputStream, progress: (Long) -> Unit) { output.write(file); progress(file.size.toLong()) }
        override fun setSource(code: String): CatalogSource = this.also { assertEquals("OFF", code) }
    }

    private fun sets(directory: File): StoredPodSets {
        val catalog = CatalogRepository(File(directory, "catalog")) { JdbcSqliteDriver("jdbc:sqlite:${it.absolutePath}") }
        catalog.stage { index ->
            val root = buildJsonObject {
                putJsonObject("meta") { put("date", metadata.date); put("version", metadata.version) }
                putJsonObject("data") { put("OFF", set) }
            }
            MtgJsonCatalog().import(root.toString().byteInputStream(), index, metadata)
        }
        return StoredPodSets({ SetSourceRepository(File(directory, "sets"), catalog, source) })
    }

    // DRF-162, SYS-082
    @Test fun aSetTheCardCatalogSaysCantBeDraftedIsRefused() {
        val directory = createTempDirectory("e2e-marks").toFile()
        try {
            launchPlayers(aliceSets = sets(directory))
            waitUntil("Alice's menu") { alice.shows(hasTestTag("menu-PODS")) }
            alice.tagged("menu-PODS").performClick()
            waitUntil("Alice's Pods") { alice.shows(hasTestTag("new-pod")) }
            alice.tagged("new-pod").performClick()
            waitUntil("the New pod form") { alice.pods.state.creating != null }
            alice.pods.chooseSet()
            waitUntil("the Source dialog") { alice.showsText("Sets") }
            alice.node(hasText("Sets")).performClick()
            waitUntil("the set in the list") { alice.showsText("Unlisted Set") }
            alice.node(hasText("Unlisted Set")).performClick()
            waitUntil("the set chosen as the source") { alice.pods.state.creating?.setSource != null || alice.pods.state.error != null }
            assertNull(alice.pods.state.error)
            alice.nodes(hasClickAction() and hasAnyDescendant(hasText("Create"))).onFirst().performClick()
            waitUntil("the server's refusal") { alice.pods.state.error != null }
            assertEquals(AUTOMATUM_SOURCE_BLOCKED, alice.pods.state.error)
            assertNull(alice.pods.state.detail)
            assertEquals(emptyList(), E2eStack.query("SELECT id FROM pod"))
        } finally {
            directory.deleteRecursively()
        }
    }
}
