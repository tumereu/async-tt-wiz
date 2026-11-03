package org.asyncttwiz.protocol.game

import arcanum.engine.decision.Answer
import arcanum.engine.decision.RandomKind
import org.asyncttwiz.protocol.RandomV1
import org.asyncttwiz.protocol.sha256

/**
 * A game's randomness, deterministic per line of play (I-D56 §9): the k-th random answer of line n (its reshuffle of the
 * libraries' unknown parts, shuffles, mulligans, coin flips, die rolls, random choices) comes from H(secret, n, k), so the
 * same input on a line gives the same result, every new line gives new results, and the server checks each device's
 * random answer against it. The secret is made by the server when a play starts ([TableState.secret]); both devices hold
 * it (I-D27: the unmodified app never shows anything worked out from it before it happens).
 */
object LineRandom {
    /** A secret from 32 random bytes the server made. */
    fun secret(bytes: ByteArray): String = RandomV1.encodeSeed(bytes)

    /** The answer to a random decision of [kind] as the [k]-th random answer of line [line]. */
    fun answer(secret: String, line: Int, k: Int, kind: RandomKind): Answer {
        val words = Words(secret, line, k)
        return when (kind) {
            RandomKind.CoinFlip -> Answer.CoinFlip(RandomV1.sample(2, words::next) == 1)
            is RandomKind.Die -> Answer.DieRolls(List(kind.count) { RandomV1.sample(kind.sides, words::next) + 1 })
            is RandomKind.Objects -> Answer.RandomObjects(shuffle(kind.among, words).take(kind.count))
        }
    }

    /** [items] in a new random order as the [k]-th random answer of line [line] (a reshuffle of an unknown part). */
    fun <T> order(secret: String, line: Int, k: Int, items: List<T>): List<T> = shuffle(items, Words(secret, line, k))

    private fun <T> shuffle(items: List<T>, words: Words): List<T> {
        val result = items.toMutableList()
        for (i in result.lastIndex downTo 1) {
            val j = RandomV1.sample(i + 1, words::next)
            val value = result[i]; result[i] = result[j]; result[j] = value
        }
        return result
    }

    /** 32-bit words of SHA-256 in counter mode over the domain, the secret, the line and the answer's index. */
    private class Words(secret: String, line: Int, k: Int) {
        private val prefix = (DOMAIN + secret + "\u0000" + line + "\u0000" + k + "\u0000").encodeToByteArray()
        private var counter = 0L
        private var block = ByteArray(0)
        private var offset = 0

        fun next(): Long {
            if (offset == block.size) {
                val hex = sha256(prefix + ByteArray(8) { (counter shr ((7 - it) * 8)).toByte() })
                block = ByteArray(32) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
                counter++; offset = 0
            }
            var word = 0L
            repeat(4) { word = (word shl 8) or (block[offset++].toLong() and 255) }
            return word
        }
    }

    private const val DOMAIN = "async-tt-wiz/engine-line/v1\u0000"
}
