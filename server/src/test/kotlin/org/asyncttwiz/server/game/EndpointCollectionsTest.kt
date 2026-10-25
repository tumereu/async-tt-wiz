package org.asyncttwiz.server.game

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class EndpointCollectionsTest {
    @Test fun `map overlays share base reads including misses without sharing edits`() {
        val reads = mutableListOf<String>()
        val base = EndpointMap<String, String>({ reads += it; if (it == "a") "original" else null }, { setOf("a") })
        assertEquals("original", base["a"])
        assertNull(base["missing"])
        val edited = base.putting("b", "added") as EndpointMap
        val removed = edited.removing("a")
        assertEquals("original", edited["a"])
        assertNull(edited["missing"])
        assertNull(removed["missing"])
        assertNull(removed["a"])
        assertNull(base["b"])
        assertEquals(setOf("b"), edited.changedKeys)
        assertEquals(mapOf("b" to "added"), removed)
        assertEquals(listOf("a", "missing", "b"), reads)
    }

    @Test fun `edge overlays memoize membership count and enumeration across edits`() {
        var lookups = 0; var counts = 0; var enumerations = 0
        val base = EndpointSet<String>({ lookups++; it == "a" }, { counts++; 1 }, { enumerations++; setOf("a") })
        val added = base.adding("b") as EndpointSet
        val replaced = added.removing("a")
        repeat(3) {
            assertTrue("a" in base); assertFalse("b" in base)
            assertEquals(1, base.size); assertEquals(2, added.size); assertEquals(1, replaced.size)
            assertEquals(listOf("a"), base.toList())
            assertEquals(setOf("a", "b"), added.toSet())
            assertEquals(listOf("b"), replaced.toList())
        }
        assertEquals(2, lookups); assertEquals(1, counts); assertEquals(1, enumerations)
    }

    @Test fun `high fanout size and membership never enumerate edges`() {
        val base = EndpointSet<Int>({ it in 0 until 10000 }, { 10000 }, { error("must stay indexed") })
        val edited = (base.removing(4) as EndpointSet).adding(10000)
        assertEquals(10000, edited.size)
        assertFalse(4 in edited); assertTrue(10000 in edited); assertTrue(9000 in edited)
        assertEquals(10000, base.size); assertTrue(4 in base); assertFalse(10000 in base)
    }
}
