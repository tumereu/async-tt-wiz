package org.asyncttwiz.protocol

import org.asyncttwiz.core.EntityId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ScalarCodecsTest {
    @Test
    fun identityRoundTripUsesAStringAndKeepsTheSameId() {
        val id = EntityId("01234567-89ab-4cde-8f01-23456789abcd")
        assertEquals("\"${id.value}\"", ScalarCodecs.encodeId(id))
        assertEquals(id, ScalarCodecs.decodeId(ScalarCodecs.encodeId(id)))
    }

    @Test
    fun revisionRoundTripsWithoutJavascriptNumberPrecisionLoss() {
        listOf(0L, 9_007_199_254_740_993L, Long.MAX_VALUE).forEach {
            assertEquals("\"$it\"", ScalarCodecs.encodeRevision(it))
            assertEquals(it, ScalarCodecs.decodeRevision(ScalarCodecs.encodeRevision(it)))
        }
    }

    @Test
    fun rejectsWrongTypesNegativeOverflowAndNoncanonicalRevisions() {
        listOf("0", "null", "[]", "{}", "\"-1\"", "\"01\"", "\"+1\"", "\"1.0\"",
            "\"9223372036854775808\"", "\" 1\"", "\"١\"").forEach {
            assertFailsWith<IllegalArgumentException> { ScalarCodecs.decodeRevision(it) }
        }
        assertFailsWith<IllegalArgumentException> { ScalarCodecs.encodeRevision(-1) }
    }
}
