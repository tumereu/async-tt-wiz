package arcanum.engine.state

/**
 * Card ids of tokens, emblems and card copies the engine creates (D35): name-based UUIDs (version 5, RFC 9562) from what
 * every view of the game agrees on, the timestamp the creation gets ([created]) and the object's number, so every client and
 * every player's view (D60, where a spell's card has another id after a shuffle) computes the same ids and later steps of the
 * same batch can name them.
 */
object TokenIds {
    /** The namespace of Arcanum token ids (a fixed random UUID). */
    const val NAMESPACE: String = "3f6c1d2a-8b4e-4c7a-9e15-7d2b0a6f4c91"

    /** The card id of token number [number] (from 0) created by [creator] ([created], or another view-independent name). */
    fun token(creator: String, number: Int): CardId = CardId(uuid5(NAMESPACE, "$creator/$number"))

    /** The creator name of objects of [kind] ("token", "emblem") created with [timestamp], the next timestamp as they are
     * created: each creation stamps its objects, so no two creations share one. */
    fun created(kind: String, timestamp: Long): String = "$kind@$timestamp"

    /** A version 5 UUID of [name] in [namespace] (RFC 9562 §5.5). */
    fun uuid5(namespace: String, name: String): String {
        val ns = namespace.replace("-", "").chunked(2).map { it.toInt(16).toByte() }
        val hash = Sha1.digest((ns + name.encodeToByteArray().toList()).toByteArray())
        val bytes = hash.copyOf(16)
        bytes[6] = ((bytes[6].toInt() and 0x0f) or 0x50).toByte()
        bytes[8] = ((bytes[8].toInt() and 0x3f) or 0x80).toByte()
        val hex = bytes.joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
        return "${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20)}"
    }
}

/** SHA-1 (RFC 3174), for [TokenIds.uuid5] only: common code has no platform digest. */
internal object Sha1 {
    fun digest(message: ByteArray): ByteArray {
        var h0 = 0x67452301
        var h1 = 0xEFCDAB89.toInt()
        var h2 = 0x98BADCFE.toInt()
        var h3 = 0x10325476
        var h4 = 0xC3D2E1F0.toInt()
        val bitLength = message.size.toLong() * 8
        val padded = ByteArray(((message.size + 9 + 63) / 64) * 64)
        message.copyInto(padded)
        padded[message.size] = 0x80.toByte()
        for (i in 0 until 8) padded[padded.size - 1 - i] = (bitLength ushr (8 * i)).toByte()
        val w = IntArray(80)
        for (chunk in padded.indices step 64) {
            for (i in 0 until 16) {
                w[i] = ((padded[chunk + 4 * i].toInt() and 0xff) shl 24) or ((padded[chunk + 4 * i + 1].toInt() and 0xff) shl 16) or
                    ((padded[chunk + 4 * i + 2].toInt() and 0xff) shl 8) or (padded[chunk + 4 * i + 3].toInt() and 0xff)
            }
            for (i in 16 until 80) w[i] = (w[i - 3] xor w[i - 8] xor w[i - 14] xor w[i - 16]).rotateLeft(1)
            var a = h0; var b = h1; var c = h2; var d = h3; var e = h4
            for (i in 0 until 80) {
                val (f, k) = when (i) {
                    in 0..19 -> ((b and c) or (b.inv() and d)) to 0x5A827999
                    in 20..39 -> (b xor c xor d) to 0x6ED9EBA1
                    in 40..59 -> ((b and c) or (b and d) or (c and d)) to 0x8F1BBCDC.toInt()
                    else -> (b xor c xor d) to 0xCA62C1D6.toInt()
                }
                val temp = a.rotateLeft(5) + f + e + k + w[i]
                e = d; d = c; c = b.rotateLeft(30); b = a; a = temp
            }
            h0 += a; h1 += b; h2 += c; h3 += d; h4 += e
        }
        return intArrayOf(h0, h1, h2, h3, h4).flatMap { h -> (3 downTo 0).map { (h ushr (8 * it)).toByte() } }.toByteArray()
    }
}
