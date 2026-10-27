package org.asyncttwiz.protocol

import kotlinx.serialization.encodeToString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class PodDeckNameTest {
    @Test fun deckNameRequestRoundTripsWithoutAnOwnerOverride() {
        val request = SetPodDeckName("request", "attempt", "My pod deck")
        val encoded = DeckJson.format.encodeToString(request)
        assertEquals(request, DeckJson.format.decodeFromString<SetPodDeckName>(encoded))
        assertFalse(encoded.contains("accountId"))
    }
}
