package org.asyncttwiz.storage

/** UUID texts as java.util.UUID makes them, for the browser (web client step 3b), whose platform has no UUID class. */
object Uuids {
    /** The random (version 4) UUID made of 16 random [bytes] (UUID.randomUUID()'s bits). */
    fun random(bytes: ByteArray): String = text(bytes.copyOf(16).also {
        it[6] = (it[6].toInt() and 0x0f or 0x40).toByte()
        it[8] = (it[8].toInt() and 0x3f or 0x80).toByte()
    })

    /** The name-based (version 3) UUID of [bytes] (UUID.nameUUIDFromBytes). */
    fun nameBased(bytes: ByteArray): String = text(md5(bytes).also {
        it[6] = (it[6].toInt() and 0x0f or 0x30).toByte()
        it[8] = (it[8].toInt() and 0x3f or 0x80).toByte()
    })

    private fun text(bytes: ByteArray): String {
        val hex = bytes.joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
        return "${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20)}"
    }

    private val shifts = intArrayOf(7, 12, 17, 22, 5, 9, 14, 20, 4, 11, 16, 23, 6, 10, 15, 21)
    private val constants = IntArray(64) { (kotlin.math.abs(kotlin.math.sin((it + 1).toDouble())) * 4294967296.0).toLong().toInt() }

    /** MD5 (RFC 1321). */
    fun md5(input: ByteArray): ByteArray {
        val padded = ((input.size + 8) / 64 + 1) * 64
        val message = input.copyOf(padded)
        message[input.size] = 0x80.toByte()
        val bits = input.size.toLong() * 8
        for (i in 0 until 8) message[padded - 8 + i] = (bits ushr (8 * i)).toByte()
        var a0 = 0x67452301; var b0 = 0xefcdab89.toInt(); var c0 = 0x98badcfe.toInt(); var d0 = 0x10325476
        val words = IntArray(16)
        for (chunk in 0 until padded / 64) {
            for (i in 0 until 16) {
                val at = chunk * 64 + i * 4
                words[i] = (message[at].toInt() and 0xff) or ((message[at + 1].toInt() and 0xff) shl 8) or
                    ((message[at + 2].toInt() and 0xff) shl 16) or ((message[at + 3].toInt() and 0xff) shl 24)
            }
            var a = a0; var b = b0; var c = c0; var d = d0
            for (i in 0 until 64) {
                val f: Int; val g: Int
                when (i / 16) {
                    0 -> { f = (b and c) or (b.inv() and d); g = i }
                    1 -> { f = (d and b) or (d.inv() and c); g = (5 * i + 1) % 16 }
                    2 -> { f = b xor c xor d; g = (3 * i + 5) % 16 }
                    else -> { f = c xor (b or d.inv()); g = (7 * i) % 16 }
                }
                val sum = a + f + constants[i] + words[g]
                a = d; d = c; c = b
                b += sum.rotateLeft(shifts[(i / 16) * 4 + i % 4])
            }
            a0 += a; b0 += b; c0 += c; d0 += d
        }
        val state = intArrayOf(a0, b0, c0, d0)
        return ByteArray(16) { i -> (state[i / 4] ushr (8 * (i % 4))).toByte() }
    }
}
