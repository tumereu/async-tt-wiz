package org.asyncttwiz.protocol

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

/** The plain-Kotlin SHA-1/SHA-256 (Kotlin/Wasm) against FIPS 180-4's test vectors and the platform digests. */
class ShaTest {
    private val abc = "abc".encodeToByteArray()
    private val twoBlocks = "abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq".encodeToByteArray()
    private val millionA = ByteArray(1_000_000) { 'a'.code.toByte() }

    @Test
    fun sha256MatchesTheStandardVectors() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", Sha.hex(Sha.sha256(abc)))
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", Sha.hex(Sha.sha256(ByteArray(0))))
        assertEquals("248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1", Sha.hex(Sha.sha256(twoBlocks)))
        assertEquals("cdc76e5c9914fb9281a1c7e284d73e67f1809a48a497200e046d39ccc7112cd0", Sha.hex(Sha.sha256(millionA)))
    }

    @Test
    fun sha1MatchesTheStandardVectors() {
        assertEquals("a9993e364706816aba3e25717850c26c9cd0d89d", Sha.hex(Sha.sha1(abc)))
        assertEquals("da39a3ee5e6b4b0d3255bfef95601890afd80709", Sha.hex(Sha.sha1(ByteArray(0))))
        assertEquals("84983e441c3bd26ebaae4aa1f95129e5e54670f1", Sha.hex(Sha.sha1(twoBlocks)))
        assertEquals("34aa973cd4c4daa4f61eeb2bdbad27316534016f", Sha.hex(Sha.sha1(millionA)))
    }

    /** Every length around the padding's block boundaries (55, 56, 64 bytes, …) gives the platform's digest. */
    @Test
    fun platformDigestsAgreeWithThePlainImplementation() {
        val random = Random(180)
        for (length in 0..200) {
            val bytes = random.nextBytes(length)
            assertEquals(Sha.hex(Sha.sha256(bytes)), sha256(bytes), "SHA-256 of $length bytes")
            assertEquals(Sha.hex(Sha.sha1(bytes)), Sha.hex(sha1(bytes)), "SHA-1 of $length bytes")
        }
    }
}
