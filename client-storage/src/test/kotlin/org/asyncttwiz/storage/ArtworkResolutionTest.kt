package org.asyncttwiz.storage

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
