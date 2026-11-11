package org.asyncttwiz.appui

// The JVM library pieces (clocks, ids, text formats, URIs, I/O types) the app's code uses, under names close to the JVM's
// (docs/specs/web-client.md §1 item 2). Android: typealiases of the JVM types and calls of the very functions the code
// called before, so values and texts stay exactly as they were. The browser's come with the web client (§11 steps 3, 4).

/** Milliseconds since 1970 by the wall clock (Android: System.currentTimeMillis()). */
expect fun currentTimeMillis(): Long

/** A monotonic clock in nanoseconds (Android: System.nanoTime()). */
expect fun nanoTime(): Long

/** A fresh random (version 4) UUID's text (Android: UUID.randomUUID().toString()). */
expect fun randomUuid(): String

/** The name-based (version 3) UUID's text for [bytes] (Android: UUID.nameUUIDFromBytes(bytes).toString()). */
expect fun nameUuid(bytes: ByteArray): String

/** [value]'s identity hash code (Android: System.identityHashCode). */
expect fun identityHashCode(value: Any?): Int

/** [value]'s class's simple name as the JVM gives it (Android: value.javaClass.simpleName). */
expect fun simpleClassName(value: Any): String

/** [value]'s class's full name as the JVM gives it (Android: value.javaClass.name). */
expect fun className(value: Any): String

/** The locales the app formats numbers in. */
sealed interface FormatLocale {
    /** Android: Locale.ROOT. */
    data object Root : FormatLocale
    /** Android: Locale.US. */
    data object Us : FormatLocale
}

/** [format] filled with [args] in [locale] (Android: String.format(locale, format, *args)). */
expect fun formatIn(locale: FormatLocale, format: String, vararg args: Any?): String

/** Whether [text] decodes as unpadded URL-safe base64 and encodes back to itself (Android: java.util.Base64). */
expect fun isCanonicalUrlBase64(text: String): Boolean

/** A URI reference's parts (Android: as java.net.URI parses and reads them). */
class UriParts(val scheme: String?, val host: String?, val port: Int, val rawUserInfo: String?, val rawPath: String?,
    val path: String?, val rawQuery: String?, val rawFragment: String?)

/** [text]'s parts as a URI reference; throws when it isn't one (Android: java.net.URI(text)). */
expect fun parseUri(text: String): UriParts

/** [text] encoded for a URL's query (Android: URLEncoder.encode(text, "UTF-8")). */
expect fun urlEncode(text: String): String

/** Something to close (Android: java.io.Closeable). */
expect interface Closeable : AutoCloseable

/** A file on this device (Android: java.io.File). */
expect class File

/** An instant on the time line (Android: java.time.Instant; its text is ISO-8601, as Instant.toString() writes it). */
expect class Instant : Comparable<Instant> {
    override fun compareTo(other: Instant): Int
}

/** Now (Android: Instant.now()). */
expect fun instantNow(): Instant

/** [text] (ISO-8601) as an [Instant] (Android: Instant.parse); throws when it isn't one. */
expect fun parseInstant(text: CharSequence): Instant

/** The [Instant] [millis] after 1970 (Android: Instant.ofEpochMilli). */
expect fun instantOfEpochMilli(millis: Long): Instant

/** A wall clock with its time zone (Android: java.time.Clock). */
expect abstract class Clock

/** The device's clock in its current time zone (Android: Clock.systemDefaultZone()). */
expect fun systemDefaultZoneClock(): Clock

/** A date without a time (Android: java.time.LocalDate). */
expect class LocalDate

/** A date and time in a time zone (Android: java.time.ZonedDateTime). */
expect class ZonedDateTime

/** Now by [clock], in its zone (Android: ZonedDateTime.now(clock)). */
expect fun zonedNow(clock: Clock): ZonedDateTime

/** [degrees] in radians (Android: Math.toRadians, whose rounding the drawing keeps). */
expect fun toRadians(degrees: Double): Double

/** A new set that compares its elements by identity (Android: Collections.newSetFromMap(IdentityHashMap())). */
expect fun <T> newIdentitySet(): MutableSet<T>

/** An arbitrarily large integer (Android: java.math.BigInteger); [value] in decimal, throws when it is not a number. */
expect class BigInteger(value: String) : Comparable<BigInteger> {
    override fun compareTo(other: BigInteger): Int
    fun signum(): Int
    fun add(other: BigInteger): BigInteger
    fun subtract(other: BigInteger): BigInteger
}

/** Android: as Kotlin's own BigInteger.plus. */
@Suppress("NOTHING_TO_INLINE")
inline operator fun BigInteger.plus(other: BigInteger): BigInteger = this.add(other)

/** Android: as Kotlin's own BigInteger.minus. */
@Suppress("NOTHING_TO_INLINE")
inline operator fun BigInteger.minus(other: BigInteger): BigInteger = this.subtract(other)

/** [value] as a [BigInteger] (Android: BigInteger.valueOf). */
expect fun bigIntegerOf(value: Long): BigInteger

/** This decimal text as a [BigInteger], null when it is not one (Android: Kotlin's String.toBigIntegerOrNull()). */
expect fun String.toBigIntegerOrNull(): BigInteger?

/** [text]'s code points (Android: String.codePointCount over the whole text). */
expect fun codePointCount(text: String): Int

/** The text of [text]'s first [maxBytes] UTF-8 bytes, a character cut in half dropped (Android: a UTF-8 decoder that
 * ignores malformed input). */
expect fun utf8Prefix(text: String, maxBytes: Int): String

/** The first code point of non-empty [text], as text (Android: String(Character.toChars(text.codePointAt(0)))). */
expect fun firstCodePoint(text: String): String

/** A map that keeps its [maxSize] most recently used entries, dropping the least recently used (Android: an access-order
 * java.util.LinkedHashMap of initial capacity [maxSize] whose removeEldestEntry keeps [maxSize]). */
expect fun <K, V> boundedLruMap(maxSize: Int): MutableMap<K, V>

/** [text] (UTF-8) gzipped, in base64 (Android: GZIPOutputStream and java.util.Base64's encoder). */
expect fun gzipBase64(text: String): String

/** [file]'s lines (UTF-8), null when it is not a file (Android: File.readLines). */
expect fun readLinesIfFile(file: File): List<String>?

/** Appends [text] (UTF-8) to [file] (Android: a FileOutputStream in append mode). */
expect fun appendToFile(file: File, text: String)

/** Replaces [file]'s content with [text] (UTF-8) through a ".part" file renamed over it; throws when that fails. */
expect fun replaceFileText(file: File, text: String)

/** A new map kept in its keys' natural order (Android: java.util.TreeMap, as sortedMapOf() makes it). */
expect fun <K : Comparable<K>, V> sortedMutableMapOf(): MutableMap<K, V>
