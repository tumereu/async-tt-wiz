package org.asyncttwiz.android

import kotlin.random.Random
import org.asyncttwiz.appui.secureRandomBytes
import org.asyncttwiz.protocol.RandomV1
import org.asyncttwiz.storage.Uuids

/**
 * The browser's secure random (crypto.getRandomValues) for the table's request ids and random seeds, as Android's
 * SecureRandom; a seeded [Random] instead after [seeded] (tests; its numbers differ from the JVM's seeded ones).
 */
actual object DeviceRandom {
    private var source: Random? = null

    actual fun seeded(seed: Long?) { source = seed?.let(::Random) }

    actual fun uuid(): String = Uuids.random(bytes(16))

    actual fun bytes(size: Int): ByteArray = source?.nextBytes(size) ?: secureRandomBytes(size)

    actual fun seed(): String = RandomV1.encodeSeed(bytes(32))
}
