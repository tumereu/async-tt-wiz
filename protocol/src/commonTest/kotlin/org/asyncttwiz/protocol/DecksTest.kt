package org.asyncttwiz.protocol

import kotlin.test.*
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString

class DecksTest {
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
        val entry = SavedDeckEntry(SavedDeckSection.MAINBOARD, DeckJson.digest(card()), Int.MAX_VALUE)
        assertTrue(DeckValidation.create(CreateDeck(id, " exact name ", listOf(entry))))
        assertFalse(DeckValidation.create(CreateDeck(id, "", listOf(entry))))
        assertFalse(DeckValidation.create(CreateDeck(id, "name", listOf(entry.copy(quantity = 0)))))
        assertFalse(DeckValidation.create(CreateDeck(id, "name", listOf(entry, entry))))
    }
}
