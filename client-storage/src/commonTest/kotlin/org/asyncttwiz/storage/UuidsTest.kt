package org.asyncttwiz.storage

import kotlin.test.Test
import kotlin.test.assertEquals

// UUID texts as java.util.UUID makes them (web client step 3b: the browser has no UUID class).
class UuidsTest {
    private fun hex(bytes: ByteArray) = bytes.joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }

    @Test
    fun md5MatchesTheStandardVectors() {
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", hex(Uuids.md5(ByteArray(0))))
        assertEquals("900150983cd24fb0d6963f7d28e17f72", hex(Uuids.md5("abc".encodeToByteArray())))
        assertEquals("57edf4a22be3c955ac49da2e2107b67a",
            hex(Uuids.md5("12345678901234567890123456789012345678901234567890123456789012345678901234567890".encodeToByteArray())))
    }

    @Test
    fun nameBasedAndRandomUuidsSetTheirVersionAndVariant() {
        // UUID.nameUUIDFromBytes("abc".toByteArray())
        assertEquals("90015098-3cd2-3fb0-9696-3f7d28e17f72", Uuids.nameBased("abc".encodeToByteArray()))
        assertEquals("ffffffff-ffff-4fff-bfff-ffffffffffff", Uuids.random(ByteArray(16) { -1 }))
        assertEquals("00000000-0000-4000-8000-000000000000", Uuids.random(ByteArray(16)))
    }
}
