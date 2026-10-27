package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.asyncttwiz.storage.*
import org.junit.Test
import kotlin.test.*

class PodArtworkTest {
    private val id = "2487c5bd-8d7f-5925-af7a-a2f6e5eb6b7a"

    @Test fun catalogRowsKeepExactPrintingAndProviderFaceNamesAcrossEveryLayout() {
        for (layout in listOf("normal", "transform", "modal_dfc", "adventure", "flip", "meld", "saga",
            "battle", "split", "aftermath", "room", "token", "double_faced_token")) {
            val multiple = layout !in setOf("normal", "saga", "token")
            val rows = (if (multiple) listOf("a", "b") else listOf("")).mapIndexed { index, side ->
                val name = if (index == 0) "Front" else "Back"
                CatalogPrinting("catalog-$layout-$side", if (multiple) "Front // Back" else "Front", "TST", "1",
                    "English", side, id, layout.endsWith("token"), emptyList(),
                    """{"name":"Front // Back","faceName":"$name","side":"$side","layout":"$layout",
                        "type":"${if (layout == "battle") "Battle — Siege" else "Creature"}"}""")
            }
            val definition = ResolvedDeckEntry(DeckSection.MAINBOARD, 1, rows, emptyList()).definition(null)
            val inspected = InspectedCard.retained("snapshot", definition)
            assertNotNull(inspected.artwork.first(), layout)
            inspected.artwork.forEachIndexed { index, reference ->
                assertEquals(ArtworkReference.from(rows[index], "snapshot", "large"), reference, layout)
                assertEquals(reference?.copy(size = "normal"), definition.faces[index].tableArtwork(index, "snapshot"), layout)
            }
            val doubleSided = layout in setOf("transform", "modal_dfc", "meld", "battle", "double_faced_token")
            assertEquals(if (doubleSided) listOf(0, 1) else listOf(0), podPreviewFaces(definition), layout)
        }
    }

    @Test fun retainedBackOnlyDefinitionUsesCatalogSideRatherThanListPosition() {
        val face = DefinitionFace("back", "Back", scryfallId = id,
            sourceData = """{"faceName":"Back","side":"b"}""")
        val definition = CardDefinition("Back", listOf(face), layout = "transform")
        val artwork = InspectedCard.retained("snapshot", definition).artwork.single()!!
        assertEquals(1, artwork.face)
        assertEquals("Back", artwork.faceName)
        assertEquals(artwork.copy(size = "normal"), face.tableArtwork(0, "snapshot"))
    }

    @Test fun meldPreviewDoesNotInsertTheOtherFrontComponentBetweenFrontAndResult() {
        val definition = CardDefinition("Meld front", listOf(DefinitionFace("a", "Front"),
            DefinitionFace("b", "Other front"), DefinitionFace("c", "Result")), layout = "meld")
        assertEquals(listOf(0, 2), podPreviewFaces(definition))
        assertEquals(listOf(0), podPreviewFaces(definition.copy(layout = "split")))
    }
}
