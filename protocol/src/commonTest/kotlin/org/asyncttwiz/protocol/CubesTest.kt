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
        val input = CreateCube("00000000-0000-4000-8000-000000000001", "Cube", entries, "synergy")
        assertEquals(input, DeckJson.format.decodeFromString<CreateCube>(DeckJson.canonical(input)))
        assertFalse(CubeValidation.name(" "))
        assertFalse(CubeValidation.provenance("../../elsewhere"))
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
