package org.asyncttwiz.protocol

internal actual fun sha256(bytes: ByteArray): String =
    java.security.MessageDigest.getInstance("SHA-256").digest(bytes).let { digest ->
        // Lowercase hex, two digits a byte, as "%02x" without a format call per byte (every history event's seal is one)
        buildString(digest.size * 2) { for (b in digest) { val v = b.toInt() and 0xff; append(HEX[v ushr 4]); append(HEX[v and 0xf]) } }
    }

private const val HEX = "0123456789abcdef"

internal actual fun sha1(bytes: ByteArray): ByteArray = java.security.MessageDigest.getInstance("SHA-1").digest(bytes)
