package org.asyncttwiz.android

/**
 * Where the table's fresh request ids and random seeds (shuffles, Automatum's random choices) come from: the device's
 * secure random (Android: SecureRandom). Tests that simulate whole games install a seeded source ([seeded]) so a game
 * plays the same on every run.
 */
expect object DeviceRandom {
    /** Seeded ids and seeds from now on (tests); null: the device's secure random again. */
    fun seeded(seed: Long?)

    /** A fresh random (version 4) UUID. */
    fun uuid(): String

    /** [size] fresh random bytes. */
    fun bytes(size: Int): ByteArray

    /** A fresh shuffle or random-choice seed. */
    fun seed(): String
}
