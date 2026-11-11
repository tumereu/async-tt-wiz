package org.asyncttwiz.protocol

/**
 * SHA-1 and SHA-256 (FIPS 180-4) in plain Kotlin, for platforms without `java.security.MessageDigest` (Kotlin/Wasm).
 * The JVM and Android keep `MessageDigest`; `ShaTest` checks both against the standard's test vectors and each other.
 */
internal object Sha {
    private val K256 = longArrayOf(
        0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
        0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
        0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
        0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
        0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
        0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
        0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
        0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208, 0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2,
    ).let { k -> IntArray(k.size) { k[it].toInt() } }
    private val H256 = longArrayOf(0x6a09e667, 0xbb67ae85, 0x3c6ef372, 0xa54ff53a, 0x510e527f, 0x9b05688c, 0x1f83d9ab, 0x5be0cd19)
        .let { h -> IntArray(h.size) { h[it].toInt() } }
    private val H1 = longArrayOf(0x67452301, 0xefcdab89, 0x98badcfe, 0x10325476, 0xc3d2e1f0).let { h -> IntArray(h.size) { h[it].toInt() } }
    private const val HEX = "0123456789abcdef"

    fun sha256(input: ByteArray): ByteArray {
        val m = pad(input)
        val h = H256.copyOf()
        val w = IntArray(64)
        for (chunk in m.indices step 64) {
            for (t in 0 until 16) w[t] = word(m, chunk + 4 * t)
            for (t in 16 until 64) {
                val s0 = w[t - 15].rotateRight(7) xor w[t - 15].rotateRight(18) xor (w[t - 15] ushr 3)
                val s1 = w[t - 2].rotateRight(17) xor w[t - 2].rotateRight(19) xor (w[t - 2] ushr 10)
                w[t] = w[t - 16] + s0 + w[t - 7] + s1
            }
            var a = h[0]; var b = h[1]; var c = h[2]; var d = h[3]; var e = h[4]; var f = h[5]; var g = h[6]; var hh = h[7]
            for (t in 0 until 64) {
                val t1 = hh + (e.rotateRight(6) xor e.rotateRight(11) xor e.rotateRight(25)) + ((e and f) xor (e.inv() and g)) + K256[t] + w[t]
                val t2 = (a.rotateRight(2) xor a.rotateRight(13) xor a.rotateRight(22)) + ((a and b) xor (a and c) xor (b and c))
                hh = g; g = f; f = e; e = d + t1; d = c; c = b; b = a; a = t1 + t2
            }
            h[0] += a; h[1] += b; h[2] += c; h[3] += d; h[4] += e; h[5] += f; h[6] += g; h[7] += hh
        }
        return bytes(h)
    }

    fun sha1(input: ByteArray): ByteArray {
        val m = pad(input)
        val h = H1.copyOf()
        val w = IntArray(80)
        for (chunk in m.indices step 64) {
            for (t in 0 until 16) w[t] = word(m, chunk + 4 * t)
            for (t in 16 until 80) w[t] = (w[t - 3] xor w[t - 8] xor w[t - 14] xor w[t - 16]).rotateLeft(1)
            var a = h[0]; var b = h[1]; var c = h[2]; var d = h[3]; var e = h[4]
            for (t in 0 until 80) {
                val f: Int
                val k: Int
                when (t / 20) {
                    0 -> { f = (b and c) or (b.inv() and d); k = 0x5a827999 }
                    1 -> { f = b xor c xor d; k = 0x6ed9eba1 }
                    2 -> { f = (b and c) or (b and d) or (c and d); k = 0x8f1bbcdc.toInt() }
                    else -> { f = b xor c xor d; k = 0xca62c1d6.toInt() }
                }
                val temp = a.rotateLeft(5) + f + e + k + w[t]
                e = d; d = c; c = b.rotateLeft(30); b = a; a = temp
            }
            h[0] += a; h[1] += b; h[2] += c; h[3] += d; h[4] += e
        }
        return bytes(h)
    }

    /** Lowercase hex, two digits a byte. */
    fun hex(digest: ByteArray): String =
        buildString(digest.size * 2) { for (b in digest) { val v = b.toInt() and 0xff; append(HEX[v ushr 4]); append(HEX[v and 0xf]) } }

    /** The message, a 1 bit, zeros, and its length in bits as 64 bits big-endian, to a multiple of 64 bytes. */
    private fun pad(bytes: ByteArray): ByteArray {
        val bitLength = bytes.size.toLong() * 8
        val padded = ((bytes.size + 9 + 63) / 64) * 64
        val out = bytes.copyOf(padded)
        out[bytes.size] = 0x80.toByte()
        for (i in 0 until 8) out[padded - 1 - i] = (bitLength ushr (8 * i)).toByte()
        return out
    }

    private fun word(b: ByteArray, i: Int) = ((b[i].toInt() and 0xff) shl 24) or ((b[i + 1].toInt() and 0xff) shl 16) or
        ((b[i + 2].toInt() and 0xff) shl 8) or (b[i + 3].toInt() and 0xff)

    private fun bytes(h: IntArray): ByteArray = ByteArray(h.size * 4) { (h[it / 4] ushr (24 - 8 * (it % 4))).toByte() }
}
