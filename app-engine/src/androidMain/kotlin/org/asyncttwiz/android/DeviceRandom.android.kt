package org.asyncttwiz.android

import org.asyncttwiz.protocol.RandomV1
import java.security.SecureRandom
import java.util.Random
import java.util.UUID

/**
 * Where the table's fresh request ids and random seeds (shuffles, Automatum's random choices) come from: the device's
 * secure random. Tests that simulate whole games install a seeded [source] ([seeded]) so a game plays the same on every
 * run.
 */
actual object DeviceRandom {
    private val secure = SecureRandom()
    @Volatile private var source: Random? = null

    /** Seeded ids and seeds from now on (tests); null: the device's secure random again. */
    actual fun seeded(seed: Long?) { source = seed?.let(::Random) }

    /** A fresh random (version 4) UUID. */
    actual fun uuid(): String {
        val random = source ?: return UUID.randomUUID().toString()
        val bytes = ByteArray(16).also(random::nextBytes)
        bytes[6] = (bytes[6].toInt() and 0x0f or 0x40).toByte()
        bytes[8] = (bytes[8].toInt() and 0x3f or 0x80).toByte()
        val buffer = java.nio.ByteBuffer.wrap(bytes)
        return UUID(buffer.long, buffer.long).toString()
    }

    /** [size] fresh random bytes. */
    actual fun bytes(size: Int): ByteArray = ByteArray(size).also { (source ?: secure).nextBytes(it) }

    /** A fresh shuffle or random-choice seed. */
    actual fun seed(): String = RandomV1.encodeSeed(bytes(32))
}
