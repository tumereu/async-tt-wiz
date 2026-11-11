package org.asyncttwiz.protocol

// Kotlin/Wasm has no java.security: the common implementation (Sha).

internal actual fun sha256(bytes: ByteArray): String = Sha.hex(Sha.sha256(bytes))

internal actual fun sha1(bytes: ByteArray): ByteArray = Sha.sha1(bytes)
