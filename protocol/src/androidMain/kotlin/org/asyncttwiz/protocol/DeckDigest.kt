package org.asyncttwiz.protocol

internal actual fun sha256(bytes: ByteArray): String =
    java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

internal actual fun sha1(bytes: ByteArray): ByteArray = java.security.MessageDigest.getInstance("SHA-1").digest(bytes)
