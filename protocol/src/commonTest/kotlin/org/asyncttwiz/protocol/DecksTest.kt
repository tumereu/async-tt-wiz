package org.asyncttwiz.protocol

import kotlin.test.*
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString

class DecksTest {
    @Test fun deckExpansionHasABoundedCombinedCardBudget() {
        val entry = SavedDeckEntry(SavedDeckSection.MAINBOARD, DeckJson.digest(card()), 1000)
        assertTrue(DeckValidation.entries(listOf(entry)))
        for (quantity in listOf(0, -1, 1001, Int.MAX_VALUE))
            assertFalse(DeckValidation.entries(listOf(entry.copy(quantity = quantity))), "quantity=$quantity")
        assertTrue(DeckValidation.entries(listOf(entry.copy(quantity = 600), entry.copy(section = SavedDeckSection.SIDEBOARD, quantity = 400))))
        assertFalse(DeckValidation.entries(listOf(entry.copy(quantity = 600), entry.copy(section = SavedDeckSection.SIDEBOARD, quantity = 401))))
    }

    private val id = "00000000-0000-4000-8000-000000000001"
    private val other = "00000000-0000-4000-8000-000000000002"
    private fun card() = CardDefinition("Test 😀", listOf(DefinitionFace(id, "Test 😀")))

    @Test fun literalCanonicalVectorAndMissingDefaults() {
        // Independently calculated with Python stdlib json(sort_keys=True, ensure_ascii=False) + hashlib.
        assertEquals("sha256:c38d5d771dc860ea7be31ba87aae26179eba4e257c65e23145856d8bd79db3a7", DeckJson.digest(card()))
        val minimal = """{"name":"Test 😀","faces":[{"faceId":"$id","name":"Test 😀"}]}"""
        assertEquals(card(), DeckJson.format.decodeFromString<CardDefinition>(minimal))
        assertTrue(DeckValidation.definition(card()))
        assertEquals("", card().faces.single().corner)
        assertEquals(card(), DeckJson.format.decodeFromString<CardDefinition>(DeckJson.format.encodeToString(card())))
        assertNotEquals(DeckJson.digest(card()), DeckJson.digest(card().copy(name = "Test 😀 ")))
        assertFailsWith<SerializationException> {
            DeckJson.format.decodeFromString<CardDefinition>(minimal.dropLast(1) + ",\"executable\":true}")
        }
    }

    @Test fun explicitClosedFacesAndOrderedDefaults() {
        val a = DefinitionFace(id, "Front", listOf(other), corner = "2/3")
        val b = DefinitionFace(other, "Back", listOf(id), corner = "")
        val value = CardDefinition("Both", listOf(a, b))
        assertTrue(DeckValidation.definition(value))
        assertFalse(DeckValidation.definition(value.copy(faces = listOf(a))))
        assertFalse(DeckValidation.definition(value.copy(faces = listOf(a, b.copy(otherFaceIds = emptyList())))))
        assertFalse(DeckValidation.definition(value.copy(faces = listOf(a, a))))
        assertFalse(DeckValidation.definition(value.copy(faces = listOf(a.copy(otherFaceIds = listOf(other, other)), b))))
        assertNotEquals(DeckJson.digest(value), DeckJson.digest(value.copy(faces = listOf(b, a))))
        assertNotEquals(DeckJson.digest(value), DeckJson.digest(value.copy(faces = listOf(a.copy(corner = "3/4"), b))))
    }

    @Test fun resourcesUnicodeAndUntrustedMetadata() {
        val face = card().faces.single()
        for (invalid in listOf(card().copy(schemaVersion = 2), card().copy(faces = emptyList()), card().copy(name = " "),
            card().copy(faces = List(65) { face }), card().copy(name = "\ud800"), card().copy(name = "a\u0000b"),
            card().copy(faces = listOf(face.copy(rulesText = "é".repeat(32769)))),
            card().copy(faces = listOf(face.copy(rulesText = "x".repeat(65536), typeLine = "x".repeat(65536),
                manaCost = "x".repeat(65536), sourceData = "x".repeat(65536)))),
            card().copy(faces = listOf(face.copy(scryfallId = "wrong"))))) assertFalse(DeckValidation.definition(invalid))
        for (uri in listOf("file:///private", "http://public.example/art", "https://user:pass@public.example/a",
            "https://127.0.0.1/a", "https://metadata.internal/a", "https://public.example:8080/a", "https://public.example\\@local/a"))
            assertFalse(DeckValidation.definition(card().copy(faces = listOf(face.copy(imageUri = uri)))))
        assertTrue(DeckValidation.definition(card().copy(faces = listOf(face.copy(imageUri = "https://art.example.invalid/a.png",
            rulesText = "<script>plain text only</script>\n", corner = "*/*")))))
        val entry = SavedDeckEntry(SavedDeckSection.MAINBOARD, DeckJson.digest(card()), 1000)
        assertTrue(DeckValidation.create(CreateDeck(id, " exact name ", listOf(entry))))
        assertFalse(DeckValidation.create(CreateDeck(id, "", listOf(entry))))
        assertFalse(DeckValidation.create(CreateDeck(id, "name", listOf(entry.copy(quantity = 0)))))
        assertFalse(DeckValidation.create(CreateDeck(id, "name", listOf(entry, entry))))
    }
    @Test fun contextualImportWireDefaultsAndExactContentIdentities() {
        val entry = SavedDeckEntry(SavedDeckSection.MAINBOARD, DeckJson.digest(card()), 2)
        val input = """{"requestId":"$id","contents":{"entries":[{"section":"MAINBOARD","definition":"${entry.definition}","quantity":2}]}}"""
        val decoded = DeckJson.format.decodeFromString<UpdateDeck>(input)
        assertEquals(DeckImportMode.OVERWRITE, decoded.contents!!.mode)
        assertNull(decoded.name); assertNull(decoded.cover)
        assertEquals(listOf(entry), decoded.contents!!.entries)
        val changed = card().copy(faces = listOf(card().faces.single().copy(corner = "3/4")))
        assertNotEquals(DeckJson.digest(card()), DeckJson.digest(changed))
        assertTrue(DeckValidation.entries(listOf(entry, entry.copy(section = SavedDeckSection.SIDEBOARD))))
        assertFailsWith<SerializationException> { DeckJson.format.decodeFromString<UpdateDeck>(input.replace("\"entries\"", "\"unknown\"")) }
    }
}
