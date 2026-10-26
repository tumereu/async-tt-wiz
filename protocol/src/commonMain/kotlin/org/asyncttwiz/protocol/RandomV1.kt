package org.asyncttwiz.protocol

/** Pure versioned random input. Entropy acquisition belongs to platform invocation adapters. */
object RandomV1 {
    private const val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
    fun encodeSeed(bytes: ByteArray): String {
        require(bytes.size == 32)
        var bits = 0; var count = 0
        return buildString {
            for (byte in bytes) {
                bits = (bits shl 8) or (byte.toInt() and 255); count += 8
                while (count >= 6) { count -= 6; append(alphabet[(bits ushr count) and 63]) }
            }
            if (count > 0) append(alphabet[(bits shl (6 - count)) and 63])
        }
    }
    fun decodeSeed(seed: String): ByteArray {
        require(seed.length == 43 && seed.all { it in alphabet })
        var bits = 0; var count = 0; var index = 0
        val bytes = ByteArray(32)
        for (char in seed) {
            bits = (bits shl 6) or alphabet.indexOf(char); count += 6
            if (count >= 8) { count -= 8; bytes[index++] = (bits ushr count).toByte() }
        }
        require(encodeSeed(bytes) == seed) { "Noncanonical seed" }
        return bytes
    }
    fun canonical(ids: List<String>): List<String> {
        require(ids.all(DeckValidation::uuid) && ids.distinct().size == ids.size)
        // Canonical lowercase UUID text has the same order as unsigned RFC UUID bytes.
        return ids.sorted()
    }
    class Words(seed: String, val algorithm: String = "shuffle-v1") {
        private val bytes = decodeSeed(seed)
        private val domain = when (algorithm) {
            "shuffle-v1" -> "async-tt-wiz/shuffle/v1\u0000"
            "starter-v1" -> "async-tt-wiz/starter/v1\u0000"
            "branch-reseed-v1" -> "async-tt-wiz/branch-reseed/v1\u0000"
            else -> error("Unsupported random algorithm")
        }.encodeToByteArray()
        private var counter = 0uL
        private var block = ByteArray(0)
        private var offset = 0
        fun next(): Long {
            if (offset == block.size) {
                val input = domain + bytes + ByteArray(8) { (counter shr ((7 - it) * 8)).toByte() }
                val hex = sha256(input)
                block = ByteArray(32) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
                check(counter != ULong.MAX_VALUE); counter++; offset = 0
            }
            var word = 0L
            repeat(4) { word = (word shl 8) or (block[offset++].toLong() and 255) }
            return word
        }
    }
    fun sample(bound: Int, word: () -> Long): Int {
        require(bound > 0)
        val limit = (4294967296L / bound) * bound
        while (true) {
            val value = word(); require(value in 0..4294967295L)
            if (value < limit) return (value % bound).toInt()
        }
    }
    /** Keep existing 32-bit draws stable; larger bounds use two words and rejection, not modulo alone. */
    fun sample(bound: Long, word: () -> Long): Long {
        require(bound > 0)
        if (bound <= Int.MAX_VALUE) return sample(bound.toInt(), word).toLong()
        // Uniform 63-bit values. Discard the incomplete final bucket (and possibly one full bucket).
        val limit = Long.MAX_VALUE - Long.MAX_VALUE % bound
        fun next(): Long = word().also { require(it in 0..4294967295L) }
        while (true) {
            val value = ((next() and 0x7fffffffL) shl 32) or next()
            if (value < limit) return value % bound
        }
    }
    fun shuffle(ids: List<String>, seed: String, algorithm: String = "shuffle-v1"): List<String> {
        require(algorithm == "shuffle-v1" || algorithm == "branch-reseed-v1")
        val words = Words(seed, algorithm)
        val result = canonical(ids).toMutableList()
        for (i in result.lastIndex downTo 1) {
            val j = sample(i + 1, words::next)
            val value = result[i]; result[i] = result[j]; result[j] = value
        }
        return result
    }
    fun starter(participants: List<String>, seed: String): String {
        val sorted = canonical(participants); require(sorted.size == 2)
        return sorted[sample(2, Words(seed, "starter-v1")::next)]
    }
}
