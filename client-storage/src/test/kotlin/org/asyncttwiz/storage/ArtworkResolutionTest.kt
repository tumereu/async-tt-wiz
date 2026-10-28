package org.asyncttwiz.storage

import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class ArtworkResolutionTest {
    @TempDir lateinit var temporary: Path
    private val id = "2487c5bd-8d7f-5925-af7a-a2f6e5eb6b7a"
    private val resultId = "2487c5bd-8d7f-5925-af7a-a2f6e5eb6b7b"
    private val origin = "http://127.0.0.1:12345"
    private val png = byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10)

    @Test fun representativeCatalogRowsResolveEveryPrintedLayoutWithoutSearchingNames() {
        for (layout in listOf("normal", "transform", "modal_dfc", "adventure", "flip", "meld", "saga",
            "battle", "split", "aftermath", "room", "token", "double_faced_token")) {
            val physicalBack = layout in setOf("transform", "modal_dfc", "battle", "double_faced_token")
            val parts = physicalBack || layout in setOf("adventure", "flip", "meld", "split", "aftermath", "room")
            val name = if (parts) "Front // Back" else "Front"
            val rows = (if (parts) listOf("a", "b") else listOf("")).map { side ->
                val faceName = if (side == "b") "Back" else "Front"
                CatalogPrinting("catalog-$layout-$side", name, "TST", "1", "English", side, id, layout.endsWith("token"),
                    emptyList(), """{"name":"$name","faceName":"$faceName","layout":"$layout","side":"$side"}""")
            }
            val urls = mutableListOf<String>()
            val metadata = when {
                physicalBack -> """{"card_faces":[{"name":"Front","image_uris":{"normal":"$origin/front"}},
                    {"name":"Back","image_uris":{"normal":"$origin/back"}}]}"""
                layout == "meld" -> """{"image_uris":{"normal":"$origin/front"},
                    "all_parts":[{"component":"meld_result","name":"Back","id":"$resultId"}]}"""
                else -> """{"image_uris":{"normal":"$origin/front"},"card_faces":[{"name":"Front"},{"name":"Back"}]}"""
            }.removePrefix("{").removeSuffix("}")
            val repository = HttpArtworkRepository(ArtworkDiskCache(temporary.resolve(layout).toFile()),
                ArtworkTransport { url, _, _, _ ->
                    urls += url
                    when (url) {
                        "$origin/cards/$id" -> ArtworkResponse(200,
                            """{"object":"card","id":"$id","name":"${if (layout == "meld") "Front" else name}",
                                "layout":"$layout",$metadata}""".toByteArray(), "application/json")
                        "$origin/cards/$resultId" -> ArtworkResponse(200,
                            """{"object":"card","id":"$resultId","name":"Back","layout":"meld",
                                "image_uris":{"normal":"$origin/back"}}""".toByteArray(), "application/json")
                        else -> ArtworkResponse(200, png + url.toByteArray(), "image/png")
                    }
                }, ArtworkUrlPolicy(origin), "$origin/cards/")
            rows.forEachIndexed { index, row ->
                val reference = ArtworkReference.from(row, "catalog-v1")!!
                val expected = if (index == 1 && (physicalBack || layout == "meld")) "back" else "front"
                assertArrayEquals(png + "$origin/$expected".toByteArray(), repository.load(reference), "$layout ${row.side}")
            }
            assertTrue(urls.all { it in listOf("$origin/cards/$id", "$origin/cards/$resultId", "$origin/front", "$origin/back") })
        }
    }

    /** Trimmed real provider data: modal_dfc (Valakut Awakening), transform (Elesh Norn), battle
     * (Invasion of Alara), meld (Fang + Ragnarok), reversible_card (Command Tower, same name on both
     * sides) and double_faced_token (Punchcard // Punchcard). Every row shows its own physical side. */
    @Test fun realDoubleFacedProviderRowsResolveTheirOwnPhysicalSide() {
        val fixture = JsonParser.parseString(javaClass.getResource("/artwork/double-faced.json")!!.readText()).asJsonObject
        val cards = fixture.getAsJsonArray("scryfall").associate { it.asJsonObject.get("id").asString to it.toString() }
        val base = "https://api.scryfall.com/cards/"
        val requested = mutableListOf<String>()
        val repository = HttpArtworkRepository(ArtworkDiskCache(temporary.resolve("real").toFile()),
            ArtworkTransport { url, _, _, _ ->
                requested += url
                cards[url.removePrefix(base)]?.let { ArtworkResponse(200, it.toByteArray(), "application/json") }
                    ?: ArtworkResponse(200, png + url.toByteArray(), "image/png")
            }, ArtworkUrlPolicy(), base)
        val rows = fixture.getAsJsonArray("mtgjson").map { it.asJsonObject }
        assertEquals(setOf("modal_dfc", "transform", "meld", "reversible_card", "double_faced_token"),
            rows.map { it.get("layout").asString }.toSet())
        for (row in rows) for (size in listOf("large", "art_crop")) {
            val scryfall = row.getAsJsonObject("identifiers").get("scryfallId").asString
            val side = row.get("side").asString
            val printing = CatalogPrinting(row.get("uuid").asString, row.get("name").asString, row.get("setCode").asString,
                row.get("number").asString, "English", side, scryfall, row.get("layout").asString.endsWith("token"),
                row.getAsJsonArray("otherFaceIds").map { it.asString }, row.toString())
            val reference = ArtworkReference.from(printing, "catalog-v1", size)!!
            // Meld results are their own printing; every other side b is the physical back image.
            val physical = if (side == "b" && row.get("layout").asString != "meld") "back" else "front"
            val expected = "https://cards.scryfall.io/$size/$physical/${scryfall[0]}/${scryfall[1]}/$scryfall.jpg"
            val loaded = repository.load(reference)?.drop(png.size)?.toByteArray()?.toString(Charsets.UTF_8)
            assertEquals(expected, loaded?.substringBefore('?'), "${row.get("name").asString} $side $size")
        }
        assertTrue(requested.none { it.startsWith(base) && it.removePrefix(base) !in cards })
    }

    @Test fun independentlyPrintedTokenBackAndMeldFrontUseTheirOwnTopLevelImagesDespiteSideLetters() {
        for (layout in listOf("token", "meld")) {
            val repository = HttpArtworkRepository(ArtworkDiskCache(temporary.resolve("separate-$layout").toFile()),
                ArtworkTransport { url, _, _, _ ->
                    if (url.endsWith(id)) ArtworkResponse(200, """{"object":"card","id":"$id","layout":"$layout",
                        "name":"Back","image_uris":{"normal":"$origin/back"}}""".toByteArray(), "application/json")
                    else ArtworkResponse(200, png, "image/png")
                }, ArtworkUrlPolicy(origin), "$origin/cards/")
            assertNotNull(repository.load(ArtworkReference(id, 1, "v1", faceName = "Back")))
        }
    }

    @Test fun missingCustomAndLargeImagesFallBackToRetainedPrintingAndOtherFullImageSize() {
        val urls = mutableListOf<String>()
        val repository = HttpArtworkRepository(ArtworkDiskCache(temporary.resolve("fallback").toFile()),
            ArtworkTransport { url, _, _, _ ->
                urls += url
                when (url) {
                    "$origin/cards/$id" -> ArtworkResponse(200, """{"object":"card","id":"$id",
                        "image_uris":{"large":"$origin/missing-large","normal":"$origin/front"}}""".toByteArray(), "application/json")
                    "$origin/front" -> ArtworkResponse(200, png, "image/png")
                    else -> ArtworkResponse(404)
                }
            }, ArtworkUrlPolicy(origin), "$origin/cards/")
        assertNotNull(repository.load(ArtworkReference(id, 0, "v1", "large", "$origin/missing-custom")))
        assertEquals(listOf("$origin/missing-custom", "$origin/cards/$id", "$origin/missing-large", "$origin/front"), urls)
    }
}
