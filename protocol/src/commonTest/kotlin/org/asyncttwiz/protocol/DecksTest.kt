package org.asyncttwiz.protocol

import kotlin.test.*
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString

class DecksTest {
    // LOB-063
    @Test fun deckExpansionHasABoundedCombinedCardBudget() {
        val entry = SavedDeckEntry(SavedDeckSection.MAINBOARD, id, "Test 😀", 1000)
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
            card().copy(faces = listOf(face.copy(scryfallId = "wrong"))))) assertFalse(DeckValidation.definition(invalid))
        assertTrue(DeckValidation.definition(card().copy(faces = listOf(face.copy(rulesText = "<script>plain text only</script>\n", corner = "*/*")))))
    }

    // Card data from the server's catalog (owner 2026-11-10): a save names printings, or a card without a printing by its name
    @Test fun aSaveNamesPrintingsOrKeptCards() {
        val entry = CardListEntry(SavedDeckSection.MAINBOARD, id, 1000)
        assertTrue(DeckValidation.create(CreateDeck(id, " exact name ", listOf(entry))))
        assertTrue(DeckValidation.create(CreateDeck(id, "name", listOf(entry), other)))
        assertTrue(DeckValidation.create(CreateDeck(id, "name", listOf(entry.copy(printingId = null, name = "Lost card")))))
        assertFalse(DeckValidation.create(CreateDeck(id, "name", listOf(entry.copy(printingId = null)))))
        assertFalse(DeckValidation.create(CreateDeck(id, "name", listOf(entry.copy(printingId = DeckJson.digest(card()))))))
        assertFalse(DeckValidation.create(CreateDeck(id, "", listOf(entry))))
        assertFalse(DeckValidation.create(CreateDeck(id, "name", listOf(entry.copy(quantity = 0)))))
        assertFalse(DeckValidation.create(CreateDeck(id, "name", listOf(entry, entry.copy(quantity = 1)))))
        assertFalse(DeckValidation.create(CreateDeck(id, "name", listOf(entry.copy(printingId = "Test 😀")))))
        assertFalse(DeckValidation.create(CreateDeck(id, "name", listOf(entry), cover = DeckJson.digest(card()))))
        // The same card twice is allowed: the server adds up its copies
        assertTrue(DeckValidation.input(listOf(entry.copy(quantity = 500), entry.copy(quantity = 500))))
        assertFalse(DeckValidation.input(emptyList()))
    }
    @Test fun contextualImportWireDefaultsAndExactContentIdentities() {
        val entry = SavedDeckEntry(SavedDeckSection.MAINBOARD, id, "Test 😀", 2)
        val input = """{"requestId":"$id","contents":{"entries":[{"section":"MAINBOARD","printingId":"$other","quantity":2}]}}"""
        val decoded = DeckJson.format.decodeFromString<UpdateDeck>(input)
        assertNull(decoded.name); assertNull(decoded.cover)
        assertEquals(listOf(CardListEntry(SavedDeckSection.MAINBOARD, other, 2)), decoded.contents!!.entries)
        val changed = card().copy(faces = listOf(card().faces.single().copy(corner = "3/4")))
        assertNotEquals(DeckJson.digest(card()), DeckJson.digest(changed))
        assertTrue(DeckValidation.entries(listOf(entry, entry.copy(section = SavedDeckSection.SIDEBOARD))))
        // A card without a printing is its name; it may stand beside printings of a card of the same name
        val unprinted = SavedDeckEntry(SavedDeckSection.MAINBOARD, null, "Test 😀", 1)
        assertTrue(DeckValidation.entries(listOf(entry, unprinted)))
        assertFalse(DeckValidation.entries(listOf(entry, unprinted, unprinted)))
        assertFalse(DeckValidation.entries(listOf(unprinted.copy(name = " "))))
        assertEquals("name:Test 😀", unprinted.card); assertEquals(id, entry.card)
        assertEquals("Test 😀", SavedCards.unprinted(unprinted.card)); assertNull(SavedCards.unprinted(entry.card))
        // LOB-063: the 1000-card limit counts Maybe too.
        val maybe = entry.copy(section = SavedDeckSection.MAYBEBOARD)
        assertTrue(DeckValidation.entries(listOf(entry.copy(quantity = 999), maybe.copy(quantity = 1))))
        assertFalse(DeckValidation.entries(listOf(entry.copy(quantity = 1000), maybe.copy(quantity = 1))))
        // Merge import is gone: an import carries only the new list.
        assertFailsWith<SerializationException> { DeckJson.format.decodeFromString<UpdateDeck>(input.replace("]}}", "],\"mode\":\"MERGE\"}}")) }
        assertFailsWith<SerializationException> { DeckJson.format.decodeFromString<UpdateDeck>(input.replace("\"entries\"", "\"unknown\"")) }
    }

    /** Printed values for rules engines (docs/specs/arcanum-atw-integration.md I-D2): absent, they are left out of the
     * JSON, so existing definitions keep their bytes and digests (the literal vector above). */
    @Test fun colourIndicatorAndPrintedPartsAreOptionalAdditions() {
        assertFalse(DeckJson.definitionBytes(card()).decodeToString().let { "colorIndicator" in it || "parts" in it })
        val back = DefinitionFace(other, "Howling Back", listOf(id), typeLine = "Creature — Wolf", colorIndicator = "G")
        val transform = CardDefinition("Front // Howling Back", listOf(DefinitionFace(id, "Front", listOf(other)), back),
            layout = "transform")
        assertTrue(DeckValidation.definition(transform))
        assertEquals(transform, DeckJson.format.decodeFromString<CardDefinition>(DeckJson.format.encodeToString(transform)))
        assertNotEquals(DeckJson.digest(transform), DeckJson.digest(transform.copy(faces = listOf(transform.faces[0], back.copy(colorIndicator = null)))))
        for (invalid in listOf("", "g", "GW", "WUBRGW", "X"))
            assertFalse(DeckValidation.definition(transform.copy(faces = listOf(transform.faces[0], back.copy(colorIndicator = invalid)))), invalid)

        // Flattening a split card's halves into one physical face keeps them as printed parts.
        val split = CardDefinition("Kindle // Rime", listOf(
            DefinitionFace(id, "Kindle", listOf(other), "Instant", "{1}{R}", "Kindle deals 2 damage to any target."),
            DefinitionFace(other, "Rime", listOf(id), "Instant", "{1}{U}", "Draw a card.")), provider = "mtgjson", layout = "split")
        val physical = PhysicalCardFaces.normalize(split)
        assertEquals(listOf(PrintedPart("Kindle", "Instant", "{1}{R}", "Kindle deals 2 damage to any target."),
            PrintedPart("Rime", "Instant", "{1}{U}", "Draw a card.")), physical.faces.single().parts)
        assertTrue(DeckValidation.definition(physical))
        assertEquals(physical, DeckJson.format.decodeFromString<CardDefinition>(DeckJson.format.encodeToString(physical)))
        assertEquals(physical, PhysicalCardFaces.normalize(physical))
    }
}
