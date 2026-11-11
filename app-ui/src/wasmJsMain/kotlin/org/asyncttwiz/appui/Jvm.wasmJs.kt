package org.asyncttwiz.appui

// The browser's pieces come with the web shell and the web table (docs/specs/web-client.md §11 steps 3 and 4).
private fun later(): Nothing = throw NotImplementedError("web client step 3/4")

actual fun currentTimeMillis(): Long = later()
actual fun nanoTime(): Long = later()
actual fun randomUuid(): String = later()
actual fun nameUuid(bytes: ByteArray): String = later()
actual fun identityHashCode(value: Any?): Int = later()
actual fun simpleClassName(value: Any): String = value::class.simpleName ?: ""
actual fun className(value: Any): String = value::class.simpleName ?: ""
actual fun formatIn(locale: FormatLocale, format: String, vararg args: Any?): String = later()
actual fun isCanonicalUrlBase64(text: String): Boolean = later()
actual fun parseUri(text: String): UriParts = later()
actual fun urlEncode(text: String): String = later()

actual interface Closeable : AutoCloseable

actual class File

actual class Instant : Comparable<Instant> {
    actual override fun compareTo(other: Instant): Int = later()
}
actual fun instantNow(): Instant = later()
actual fun parseInstant(text: CharSequence): Instant = later()
actual fun instantOfEpochMilli(millis: Long): Instant = later()

actual abstract class Clock
actual fun systemDefaultZoneClock(): Clock = later()

actual class LocalDate
actual class ZonedDateTime
actual fun zonedNow(clock: Clock): ZonedDateTime = later()

actual fun toRadians(degrees: Double): Double = later()
actual fun <T> newIdentitySet(): MutableSet<T> = later()
actual class BigInteger actual constructor(value: String) : Comparable<BigInteger> {
    actual fun signum(): Int = later()
    actual fun add(other: BigInteger): BigInteger = later()
    actual fun subtract(other: BigInteger): BigInteger = later()
    actual override fun compareTo(other: BigInteger): Int = later()
}
actual fun bigIntegerOf(value: Long): BigInteger = later()
actual fun String.toBigIntegerOrNull(): BigInteger? = later()
actual fun codePointCount(text: String): Int = later()
actual fun utf8Prefix(text: String, maxBytes: Int): String = later()
actual fun firstCodePoint(text: String): String = later()
actual fun <K, V> boundedLruMap(maxSize: Int): MutableMap<K, V> = later()
actual fun gzipBase64(text: String): String = later()
actual fun readLinesIfFile(file: File): List<String>? = later()
actual fun appendToFile(file: File, text: String): Unit = later()
actual fun replaceFileText(file: File, text: String): Unit = later()
actual fun <K : Comparable<K>, V> sortedMutableMapOf(): MutableMap<K, V> = later()
