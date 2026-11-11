package org.asyncttwiz.appui

import java.net.URI
import java.util.Base64
import java.util.Locale
import java.util.UUID
import kotlin.text.toBigIntegerOrNull as jvmToBigIntegerOrNull

actual fun currentTimeMillis(): Long = System.currentTimeMillis()

actual fun nanoTime(): Long = System.nanoTime()

actual fun randomUuid(): String = UUID.randomUUID().toString()

actual fun nameUuid(bytes: ByteArray): String = UUID.nameUUIDFromBytes(bytes).toString()

actual fun identityHashCode(value: Any?): Int = System.identityHashCode(value)

actual fun simpleClassName(value: Any): String = value.javaClass.simpleName

actual fun className(value: Any): String = value.javaClass.name

actual fun formatIn(locale: FormatLocale, format: String, vararg args: Any?): String =
    String.format(when (locale) { FormatLocale.Root -> Locale.ROOT; FormatLocale.Us -> Locale.US }, format, *args)

actual fun isCanonicalUrlBase64(text: String): Boolean =
    Base64.getUrlEncoder().withoutPadding().encodeToString(Base64.getUrlDecoder().decode(text)) == text

actual fun parseUri(text: String): UriParts = URI(text).let {
    UriParts(it.scheme, it.host, it.port, it.rawUserInfo, it.rawPath, it.path, it.rawQuery, it.rawFragment)
}

actual fun urlEncode(text: String): String = java.net.URLEncoder.encode(text, "UTF-8")

actual typealias Closeable = java.io.Closeable

actual typealias File = java.io.File

actual typealias Instant = java.time.Instant

actual fun instantNow(): Instant = java.time.Instant.now()

actual fun parseInstant(text: CharSequence): Instant = java.time.Instant.parse(text)

actual fun instantOfEpochMilli(millis: Long): Instant = java.time.Instant.ofEpochMilli(millis)

actual typealias Clock = java.time.Clock

actual fun systemDefaultZoneClock(): Clock = java.time.Clock.systemDefaultZone()

actual typealias LocalDate = java.time.LocalDate

actual typealias ZonedDateTime = java.time.ZonedDateTime

actual fun zonedNow(clock: Clock): ZonedDateTime = java.time.ZonedDateTime.now(clock)

actual fun toRadians(degrees: Double): Double = Math.toRadians(degrees)

actual fun <T> newIdentitySet(): MutableSet<T> = java.util.Collections.newSetFromMap(java.util.IdentityHashMap())

actual typealias BigInteger = java.math.BigInteger

actual fun bigIntegerOf(value: Long): BigInteger = java.math.BigInteger.valueOf(value)

actual fun String.toBigIntegerOrNull(): BigInteger? = jvmToBigIntegerOrNull()

actual fun codePointCount(text: String): Int = text.codePointCount(0, text.length)

actual fun utf8Prefix(text: String, maxBytes: Int): String {
    val bytes = text.toByteArray(Charsets.UTF_8)
    return Charsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.IGNORE)
        .decode(java.nio.ByteBuffer.wrap(bytes, 0, minOf(bytes.size, maxBytes))).toString()
}

actual fun firstCodePoint(text: String): String = String(Character.toChars(text.codePointAt(0)))

actual fun <K, V> boundedLruMap(maxSize: Int): MutableMap<K, V> = object : java.util.LinkedHashMap<K, V>(maxSize, .75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?) = size > maxSize
}

actual fun gzipBase64(text: String): String {
    val bytes = java.io.ByteArrayOutputStream()
    java.util.zip.GZIPOutputStream(bytes).use { it.write(text.toByteArray(Charsets.UTF_8)) }
    return Base64.getEncoder().encodeToString(bytes.toByteArray())
}

actual fun readLinesIfFile(file: File): List<String>? = file.takeIf { it.isFile }?.readLines(Charsets.UTF_8)

actual fun appendToFile(file: File, text: String) {
    java.io.FileOutputStream(file, true).use { it.write(text.toByteArray(Charsets.UTF_8)) }
}

actual fun replaceFileText(file: File, text: String) {
    val part = File(file.path + ".part")
    part.writeText(text, Charsets.UTF_8)
    check(part.renameTo(file))
}

actual fun <K : Comparable<K>, V> sortedMutableMapOf(): MutableMap<K, V> = sortedMapOf()
