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
import org.asyncttwiz.android.StoredPodSets
import org.asyncttwiz.protocol.PodPhase
import org.asyncttwiz.protocol.SetBoosterKind
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

/**
 * Problem report 7ZZC89: Final Fantasy couldn't be chosen as a draft set ("Could not load source."): MTGJSON links a meld
 * result to both its fronts, which failed the whole set. Alice's phone has real card records and a set's real card file
 * (an invented set with a meld pair): she picks the set in the Source dialog, creates the pod, and the server takes it.
 */
class MeldSetSourceTest : E2e() {
    private val metadata = CatalogMetadata("2026-10-31", "5.3.0")
    private fun id(name: String) = UUID.nameUUIDFromBytes("e2e-meld/$name".encodeToByteArray()).toString()

    private fun card(name: String, number: Int, block: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit = {}): JsonObject = buildJsonObject {
        put("uuid", id(name)); put("name", name); put("number", "$number"); put("language", "English"); put("layout", "normal")
        put("type", "Creature — Elf"); put("manaCost", "{G}"); put("power", "1"); put("toughness", "1"); put("rarity", "common")
        putJsonArray("colors") { add("G") }
        block()
    }

    private fun meldFront(name: String, number: Int) = buildJsonObject {
        card(name, number).forEach { (key, value) -> put(key, value) }
        put("name", "$name // Thornwall Colossus"); put("faceName", name); put("side", "a"); put("layout", "meld")
        put("uuid", id(name)); putJsonArray("otherFaceIds") { add(id("Thornwall Colossus")) }
    }

    private val set: JsonObject = run {
        val fronts = listOf("Bramble Twin", "Hollow Twin")
        val commons = (1..20).map { "Thorn Scout $it" }
        buildJsonObject {
            put("code", "TWL"); put("name", "Thornwall Crossing"); put("type", "expansion"); put("releaseDate", "2020-01-01")
            putJsonArray("cards") {
                fronts.forEachIndexed { index, name -> add(meldFront(name, index + 1)) }
                add(buildJsonObject {
                    card("Thornwall Colossus", 1).forEach { (key, value) -> put(key, value) }
                    put("number", "1b"); put("faceName", "Thornwall Colossus"); put("side", "b"); put("layout", "meld"); put("manaCost", "")
                    putJsonArray("otherFaceIds") { fronts.forEach { add(id(it)) } }
                })
                commons.forEachIndexed { index, name -> add(card(name, index + 3)) }
            }
            putJsonObject("booster") { putJsonObject("play") {
                putJsonArray("boosters") { addJsonObject { put("weight", 1); putJsonObject("contents") { put("main", 15) } } }
                put("boostersTotalWeight", 1)
                putJsonObject("sheets") { putJsonObject("main") {
                    putJsonObject("cards") { (fronts + commons).forEach { put(id(it), 1) } }
                    put("totalWeight", fronts.size + commons.size)
                } }
                putJsonArray("sourceSetCodes") { add("TWL") }
            } }
        }
    }

    /** The card file server, as MTGJSON serves [set]. */
    private val source = object : CatalogSource {
        val file = ByteArrayOutputStream().apply {
            GZIPOutputStream(this).use { it.write(buildJsonObject { put("data", set) }.toString().encodeToByteArray()) }
        }.toByteArray()
        override fun metadata() = metadata
        override fun size(): Long = file.size.toLong()
        override fun checksum() = MessageDigest.getInstance("SHA-256").digest(file).joinToString("") { "%02x".format(it) }
        override fun download(output: OutputStream, progress: (Long) -> Unit) { output.write(file); progress(file.size.toLong()) }
        override fun setSource(code: String): CatalogSource = this.also { assertEquals("TWL", code) }
    }

    private fun sets(directory: File): StoredPodSets {
        val catalog = CatalogRepository(File(directory, "catalog")) { JdbcSqliteDriver("jdbc:sqlite:${it.absolutePath}") }
        catalog.stage { index ->
            val root = buildJsonObject {
                putJsonObject("meta") { put("date", metadata.date); put("version", metadata.version) }
                putJsonObject("data") { put("TWL", set) }
            }
            MtgJsonCatalog().import(root.toString().byteInputStream(), index, metadata)
        }
        return StoredPodSets({ SetSourceRepository(File(directory, "sets"), catalog, source) })
    }

    // DRF-167, DRF-019
    @Test fun aSetWithMeldCardsCanBeChosenAndAPodCreatedFromIt() {
        val directory = createTempDirectory("e2e-meld").toFile()
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
            waitUntil("the set in the list") { alice.showsText("Thornwall Crossing") }
            alice.node(hasText("Thornwall Crossing")).performClick()
            waitUntil("the set chosen as the source, or its refusal") {
                alice.pods.state.creating?.setSource != null || alice.pods.state.error != null
            }
            assertEquals(null, alice.pods.state.error)
            val chosen = alice.pods.state.creating!!.setSource!!.snapshot
            assertEquals(setOf(id("Bramble Twin"), id("Hollow Twin")), chosen.cards.map { it.uuid }.filter { it == id("Bramble Twin") || it == id("Hollow Twin") }.toSet())
            alice.nodes(hasClickAction() and hasAnyDescendant(hasText("Create"))).onFirst().performClick()
            // The server takes the set and its card data
            waitUntil("the pod from the set") { alice.pods.state.detail?.source?.setCode == "TWL" }
            val detail = alice.pods.state.detail!!
            assertEquals(PodPhase.SETUP, detail.phase)
            assertEquals(SetBoosterKind.RECIPE_BASED, detail.source.boosterKind)
            assertEquals(null, alice.pods.state.error)
        } finally {
            directory.deleteRecursively()
        }
    }
}
