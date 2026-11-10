package org.asyncttwiz.protocol

import kotlin.test.*

class CubesTest {
    private val digest = "sha256:" + "a".repeat(64)
    // DRF-007
    @Test fun cubeLimitsAndWireRoundTrip() {
        val entries = listOf(SavedCubeEntry(digest, 3000))
        assertTrue(CubeValidation.entries(entries))
        for (bad in listOf(emptyList(), entries + entries, listOf(SavedCubeEntry(digest, 3001)),
            listOf(SavedCubeEntry(digest, 0)), listOf(SavedCubeEntry("not-a-digest", 1)),
            listOf(SavedCubeEntry(digest, 2000), SavedCubeEntry("sha256:" + "b".repeat(64), 1001))))
            assertFalse(CubeValidation.entries(bad))
        val printing = "00000000-0000-4000-8000-000000000002"
        val input = CreateCube("00000000-0000-4000-8000-000000000001", "Cube", listOf(CardListEntry(SavedDeckSection.MAINBOARD, printing, 3000)), "synergy")
        assertTrue(CubeValidation.input(input.entries))
        for (bad in listOf(emptyList(), input.entries + input.entries, listOf(CardListEntry(SavedDeckSection.MAINBOARD, printing, 3001)),
            listOf(CardListEntry(SavedDeckSection.SIDEBOARD, printing, 1)), listOf(CardListEntry(SavedDeckSection.MAINBOARD, "not-a-printing", 1))))
            assertFalse(CubeValidation.input(bad))
        assertEquals(input, DeckJson.format.decodeFromString<CreateCube>(DeckJson.canonical(input)))
        assertFalse(CubeValidation.name(" "))
        assertFalse(CubeValidation.provenance("../../elsewhere"))
    }
    // DRF-007: Main and Maybe count toward the limit; a card may be in both; cubes have no Side.
    @Test fun cubeSectionsShareTheLimitAndMainOnlyCubesKeepTheirWireForm() {
        val other = "sha256:" + "b".repeat(64)
        val main = SavedCubeEntry(digest, 2000); val maybe = SavedCubeEntry(digest, 1000, SavedDeckSection.MAYBEBOARD)
        assertTrue(CubeValidation.entries(listOf(main, maybe)))
        assertTrue(CubeValidation.entries(listOf(maybe)))
        assertFalse(CubeValidation.entries(listOf(main, maybe, SavedCubeEntry(other, 1, SavedDeckSection.MAYBEBOARD))))
        assertFalse(CubeValidation.entries(listOf(main, maybe, maybe.copy(quantity = 1))))
        assertFalse(CubeValidation.entries(listOf(SavedCubeEntry(digest, 1, SavedDeckSection.SIDEBOARD))))
        assertEquals("""{"definition":"$digest","quantity":2000}""", DeckJson.canonical(main))
        assertEquals("""{"definition":"$digest","quantity":1000,"section":"MAYBEBOARD"}""", DeckJson.canonical(maybe))
        val saved = SavedCube(CubeSummary("id", "Cube", "1", "t", "t", 2000, maybeCopies = 1000), listOf(main, maybe))
        assertEquals(saved, DeckJson.format.decodeFromString<SavedCube>(DeckJson.canonical(saved)))
        assertFalse("maybeCopies" in DeckJson.canonical(saved.cube.copy(maybeCopies = 0)))
    }
    @Test fun definitionBatchLimitAndWireRoundTrip() {
        val input = ReadCubeDefinitions(List(250) { digest })
        assertTrue(CubeValidation.definitionRead(input.digests))
        assertFalse(CubeValidation.definitionRead(input.digests + digest))
        assertFalse(CubeValidation.definitionRead(emptyList()))
        assertFalse(CubeValidation.definitionRead(listOf("bad-digest")))
        assertEquals(input, DeckJson.format.decodeFromString<ReadCubeDefinitions>(DeckJson.canonical(input)))
        val output = SubmittedDefinitions(listOf(SubmittedDefinition(digest, CardDefinition("Card", emptyList()))))
        assertEquals(output, DeckJson.format.decodeFromString<SubmittedDefinitions>(DeckJson.canonical(output)))
    }
}
