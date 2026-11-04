package org.asyncttwiz.android

import org.asyncttwiz.protocol.*
import org.asyncttwiz.storage.*
import org.junit.Test
import java.util.UUID
import kotlin.test.*

class CardInspectionTest {
    private val front = UUID.randomUUID().toString()
    private val back = UUID.randomUUID().toString()
    private val printing = UUID.randomUUID().toString()
    private fun result(): DeckImportResult {
        val faces = listOf("a" to front, "b" to back).map { (side, id) ->
            CatalogPrinting(id, "Front // Back", "SET", "4", "English", side, printing, false,
                listOf(if (id == front) back else front),
                """{"faceName":"${if (side == "a") "Front" else "Back"}","side":"$side","layout":"transform","manaCost":"{2}{U}","type":"Creature — Wizard","text":"Exact  text\nUnicode 🧙","power":"*","toughness":"2"}""")
        }
        return DeckImportResult("Deck\n4 Front // Back", listOf(ResolvedDeckEntry(DeckSection.MAINBOARD, 4, faces, listOf(2))),
            emptyList(), CatalogMetadata("2026-10-11", "5.2.2", "catalog-identity"))
    }
    @Test fun previewAndSavedSnapshotHaveIdenticalTextDefaultsAndExplicitFaceArtwork() {
        val result = result()
        val entry = result.entries.single()
        val preview = InspectedCard.preview(entry, result.catalog)
        val submitted = DeckSavePlan.from(result, "Name").definitions.definitions.single()
        val saved = InspectedCard.retained(submitted.digest, submitted.definition)
        assertEquals(submitted.definition, preview.definition)
        assertEquals(listOf("Front", "Back"), saved.definition.faces.map { it.name })
        assertEquals("Exact  text\nUnicode 🧙", saved.definition.faces[1].rulesText)
        assertEquals("*/2", saved.definition.faces[0].corner)
        for (card in listOf(preview, saved)) {
            assertEquals(listOf(0, 1), card.artwork.map { it!!.face })
            assertEquals(listOf(printing, printing), card.artwork.map { it!!.printing })
            assertEquals(listOf("large", "large"), card.artwork.map { it!!.size })
        }
        assertEquals(listOf(submitted.digest, submitted.digest), saved.artwork.map { it!!.version })
        assertEquals(0, InspectedCard.retained("single", CardDefinition("Blank", listOf(DefinitionFace(front, "Blank"))))
            .artwork.single()!!.face)
        assertNull(InspectedCard.retained("bad-side", CardDefinition("Blank", listOf(DefinitionFace(front, "Blank",
            sourceData = """{"side":"not-a-side"}""")))).artwork.single())
    }
}
