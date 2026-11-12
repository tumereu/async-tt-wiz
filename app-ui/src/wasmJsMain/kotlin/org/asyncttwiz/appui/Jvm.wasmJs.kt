@file:OptIn(ExperimentalWasmJsInterop::class, kotlin.time.ExperimentalTime::class, kotlin.io.encoding.ExperimentalEncodingApi::class)

package org.asyncttwiz.appui

import kotlin.io.encoding.Base64
import kotlin.math.PI
import org.asyncttwiz.storage.Uuids

// The browser's sides of the JVM pieces (web client step 3b): the same values and texts as Android's, worked out in Kotlin
// or by the browser (clocks, the local time zone, secure random bytes). Files don't exist on the web: the app log lives in
// memory only there, so the file functions are never reached.

actual fun currentTimeMillis(): Long = jsDateNow().toLong()

actual fun nanoTime(): Long = (jsPerformanceNow() * 1_000_000.0).toLong()

actual fun randomUuid(): String = Uuids.random(secureRandomBytes(16))

actual fun nameUuid(bytes: ByteArray): String = Uuids.nameBased(bytes)

actual fun identityHashCode(value: Any?): Int = if (value == null) 0 else jsIdentity(value.toJsReference())

actual fun simpleClassName(value: Any): String = value::class.simpleName ?: ""
actual fun className(value: Any): String = value::class.qualifiedName ?: value::class.simpleName ?: ""

actual fun formatIn(locale: FormatLocale, format: String, vararg args: Any?): String {
    val out = StringBuilder()
    var next = 0
    var index = 0
    val spec = Regex("%(,?)(?:\\.(\\d+))?([dfs%])")
    while (index < format.length) {
        val match = spec.find(format, index) ?: break
        out.append(format, index, match.range.first)
        val (grouped, precision, conversion) = match.destructured
        when (conversion) {
            "%" -> out.append('%')
            "s" -> out.append(args[next++].toString())
            "d" -> out.append((args[next++] as Number).toLong().toString().let { if (grouped.isEmpty()) it else grouped(it) })
            else -> out.append(fixed((args[next++] as Number).toDouble(), precision.ifEmpty { "6" }.toInt(), grouped.isNotEmpty()))
        }
        index = match.range.last + 1
    }
    out.append(format, index, format.length)
    return out.toString()
}

/** Groups a decimal integer's digits in threes with commas (Locale.US and ROOT alike). */
private fun grouped(digits: String): String {
    val negative = digits.startsWith('-')
    val plain = digits.removePrefix("-")
    val text = plain.reversed().chunked(3).joinToString(",").reversed()
    return if (negative) "-$text" else text
}

/** [value] with [precision] decimals, rounded half up as String.format does. */
private fun fixed(value: Double, precision: Int, group: Boolean): String {
    if (value.isNaN()) return "NaN"
    if (value.isInfinite()) return if (value > 0) "Infinity" else "-Infinity"
    var scale = 1.0
    repeat(precision) { scale *= 10 }
    val scaled = kotlin.math.floor(kotlin.math.abs(value) * scale + 0.5).toLong()
    val whole = (scaled / scale.toLong()).toString().let { if (group) grouped(it) else it }
    val fraction = if (precision == 0) "" else "." + (scaled % scale.toLong()).toString().padStart(precision, '0')
    return (if (value < 0 && scaled != 0L) "-" else "") + whole + fraction
}

actual fun isCanonicalUrlBase64(text: String): Boolean {
    val decoded = Base64.UrlSafe.withPadding(Base64.PaddingOption.PRESENT_OPTIONAL).decode(text)
    return Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(decoded) == text
}


private val uriPattern = Regex("^(?:([A-Za-z][A-Za-z0-9+.-]*):)?(//([^/?#]*))?([^?#]*)(?:\\?([^#]*))?(?:#(.*))?$")
private val uriCharacters = Regex("^(?:[A-Za-z0-9\\-._~:/?#\\[\\]@!$&'()*+,;=]|%[0-9A-Fa-f]{2})*$")
private val hostPattern = Regex("^(?:\\[[0-9A-Fa-f:.]+]|[A-Za-z0-9](?:[A-Za-z0-9.-]*[A-Za-z0-9])?)$")

/** As java.net.URI parses a URI reference, for the URIs the app reads (server origins, links): illegal characters throw;
 * a host that isn't a server name or address is null. */
actual fun parseUri(text: String): UriParts {
    require(uriCharacters.matches(text)) { "Illegal character in URI: $text" }
    val match = requireNotNull(uriPattern.matchEntire(text)) { "Not a URI: $text" }
    val scheme = match.groups[1]?.value
    val path = match.groups[4]?.value ?: ""
    var userInfo: String? = null
    var host: String? = null
    var port = -1
    match.groups[3]?.value?.let { authority ->
        var rest = authority
        rest.lastIndexOf('@').takeIf { it >= 0 }?.let { userInfo = rest.substring(0, it); rest = rest.substring(it + 1) }
        val colon = if (rest.startsWith("[")) rest.indexOf("]:").let { if (it < 0) -1 else it + 1 } else rest.lastIndexOf(':')
        if (colon >= 0) {
            val portText = rest.substring(colon + 1)
            if (portText.isNotEmpty()) port = requireNotNull(portText.toIntOrNull()) { "Bad port in URI: $text" }
            rest = rest.substring(0, colon)
        }
        host = rest.takeIf { hostPattern.matches(it) }
    }
    return UriParts(scheme, host, port, userInfo, path, percentDecode(path), match.groups[5]?.value, match.groups[6]?.value)
}

private fun percentDecode(text: String): String {
    if ('%' !in text) return text
    val bytes = ArrayList<Byte>()
    var index = 0
    while (index < text.length) {
        if (text[index] == '%') { bytes.add(text.substring(index + 1, index + 3).toInt(16).toByte()); index += 3 }
        else { text[index].toString().encodeToByteArray().forEach(bytes::add); index++ }
    }
    return bytes.toByteArray().decodeToString()
}


/** As URLEncoder.encode(text, "UTF-8"): letters, digits and ".-*_" stay, a space becomes "+", the rest %XX. */
actual fun urlEncode(text: String): String = buildString {
    for (byte in text.encodeToByteArray()) {
        val value = byte.toInt() and 0xff
        val char = value.toChar()
        when {
            char in 'a'..'z' || char in 'A'..'Z' || char in '0'..'9' || char in ".-*_" -> append(char)
            char == ' ' -> append('+')
            else -> append('%').append("0123456789ABCDEF"[value shr 4]).append("0123456789ABCDEF"[value and 15])
        }
    }
}

actual interface Closeable : AutoCloseable

actual class File

/** An instant, as java.time.Instant writes and compares it. */
actual class Instant internal constructor(internal val value: kotlin.time.Instant) : Comparable<Instant> {
    actual override fun compareTo(other: Instant): Int = value.compareTo(other.value)
    internal val epochMillis: Long get() = value.toEpochMilliseconds()
    override fun equals(other: Any?) = other is Instant && other.value == value
    override fun hashCode() = value.hashCode()
    override fun toString() = value.toString()
}
actual fun instantNow(): Instant = Instant(kotlin.time.Clock.System.now())
actual fun parseInstant(text: CharSequence): Instant = Instant(kotlin.time.Instant.parse(text))
actual fun instantOfEpochMilli(millis: Long): Instant = Instant(kotlin.time.Instant.fromEpochMilliseconds(millis))

/** A wall clock in the browser's time zone (the only zone the web uses). */
actual abstract class Clock {
    internal abstract fun millis(): Long
}
private object BrowserClock : Clock() { override fun millis() = currentTimeMillis() }
actual fun systemDefaultZoneClock(): Clock = BrowserClock

/** A date without a time; its text is ISO-8601 (2026-11-12), as java.time.LocalDate writes it. */
actual class LocalDate internal constructor(internal val year: Int, internal val month: Int, internal val day: Int) {
    /** Days since 1970-01-01. */
    internal val epochDay: Long get() = daysFromCivil(year, month, day)
    /** 1 (Monday) to 7 (Sunday). */
    internal val dayOfWeek: Int get() = (epochDay + 3).mod(7L).toInt() + 1
    internal fun plusDays(days: Long): LocalDate = ofEpochDay(epochDay + days)
    override fun equals(other: Any?) = other is LocalDate && other.year == year && other.month == month && other.day == day
    override fun hashCode() = (year * 13 + month) * 32 + day
    override fun toString() = "${year.toString().padStart(4, '0')}-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"
    internal companion object {
        fun ofEpochDay(days: Long): LocalDate = civilFromDays(days)
    }
}

/** A date and time in the browser's time zone. */
actual class ZonedDateTime internal constructor(internal val epochMillis: Long) {
    private val fields by lazy { jsLocalFields(epochMillis.toDouble()).split(',').map { it.toInt() } }
    internal val year: Int get() = fields[0]
    internal val month: Int get() = fields[1]
    internal val day: Int get() = fields[2]
    internal val hour: Int get() = fields[3]
    internal val minute: Int get() = fields[4]
    internal val date: LocalDate get() = LocalDate(year, month, day)
    internal fun minusSeconds(seconds: Long) = ZonedDateTime(epochMillis - seconds * 1000)
    override fun toString() = "${date}T${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
}
actual fun zonedNow(clock: Clock): ZonedDateTime = ZonedDateTime(clock.millis())
internal fun Clock.nowMillis(): Long = millis()

// Howard Hinnant's civil calendar algorithms (proleptic Gregorian, as java.time).
private fun daysFromCivil(year: Int, month: Int, day: Int): Long {
    val y = (if (month <= 2) year - 1 else year).toLong()
    val era = (if (y >= 0) y else y - 399) / 400
    val yoe = y - era * 400
    val mp = (month + 9) % 12
    val doy = (153 * mp + 2) / 5 + day - 1
    val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
    return era * 146097 + doe - 719468
}

private fun civilFromDays(days: Long): LocalDate {
    val z = days + 719468
    val era = (if (z >= 0) z else z - 146096) / 146097
    val doe = z - era * 146097
    val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val day = (doy - (153 * mp + 2) / 5 + 1).toInt()
    val month = (if (mp < 10) mp + 3 else mp - 9).toInt()
    val year = (yoe + era * 400 + if (month <= 2) 1 else 0).toInt()
    return LocalDate(year, month, day)
}

/** Math.toRadians's own computation (degrees times its constant), for the same rounding. */
actual fun toRadians(degrees: Double): Double = degrees * (PI / 180.0)

actual fun <T> newIdentitySet(): MutableSet<T> = IdentitySet()

/** A set comparing its elements by identity, through the browser's identity numbers ([identityHashCode]). */
private class IdentitySet<T> : AbstractMutableSet<T>() {
    private val elements = LinkedHashMap<Int, T>()
    override val size get() = elements.size
    override fun add(element: T): Boolean {
        val key = identityHashCode(element)
        if (key in elements) return false
        elements[key] = element; return true
    }
    override fun remove(element: T) = elements.remove(identityHashCode(element)) != null
    override fun contains(element: T) = identityHashCode(element) in elements
    override fun clear() = elements.clear()
    override fun iterator(): MutableIterator<T> = elements.values.iterator()
}

/** As java.math.BigInteger, for the whole numbers the table counts (life, damage): decimal text of any length. */
actual class BigInteger private constructor(private val negative: Boolean, private val digits: String) : Comparable<BigInteger> {
    actual constructor(value: String) : this(parse(value).first, parse(value).second)
    actual fun signum(): Int = if (digits == "0") 0 else if (negative) -1 else 1
    actual fun add(other: BigInteger): BigInteger = when {
        negative == other.negative -> of(negative, addMagnitudes(digits, other.digits))
        compareMagnitudes(digits, other.digits) >= 0 -> of(negative, subtractMagnitudes(digits, other.digits))
        else -> of(other.negative, subtractMagnitudes(other.digits, digits))
    }
    actual fun subtract(other: BigInteger): BigInteger = add(of(!other.negative, other.digits))
    actual override fun compareTo(other: BigInteger): Int {
        if (signum() != other.signum()) return signum().compareTo(other.signum())
        val magnitude = compareMagnitudes(digits, other.digits)
        return if (negative) -magnitude else magnitude
    }
    override fun equals(other: Any?) = other is BigInteger && other.negative == negative && other.digits == digits
    override fun hashCode() = digits.hashCode() * if (negative) -1 else 1
    override fun toString() = if (negative) "-$digits" else digits

    internal companion object {
        fun of(negative: Boolean, digits: String): BigInteger {
            val trimmed = digits.trimStart('0').ifEmpty { "0" }
            return BigInteger(negative && trimmed != "0", trimmed)
        }
        fun parse(value: String): Pair<Boolean, String> {
            val negative = value.startsWith('-')
            val body = if (value.startsWith('-') || value.startsWith('+')) value.substring(1) else value
            if (body.isEmpty() || !body.all { it in '0'..'9' }) throw NumberFormatException("For input string: \"$value\"")
            val trimmed = body.trimStart('0').ifEmpty { "0" }
            return (negative && trimmed != "0") to trimmed
        }
        fun compareMagnitudes(a: String, b: String): Int = if (a.length != b.length) a.length.compareTo(b.length) else a.compareTo(b)
        fun addMagnitudes(a: String, b: String): String {
            val out = StringBuilder(); var carry = 0; var i = a.length - 1; var j = b.length - 1
            while (i >= 0 || j >= 0 || carry > 0) {
                val sum = (if (i >= 0) a[i--] - '0' else 0) + (if (j >= 0) b[j--] - '0' else 0) + carry
                out.append('0' + sum % 10); carry = sum / 10
            }
            return out.reverse().toString()
        }
        /** a - b for a >= b. */
        fun subtractMagnitudes(a: String, b: String): String {
            val out = StringBuilder(); var borrow = 0; var i = a.length - 1; var j = b.length - 1
            while (i >= 0) {
                var difference = (a[i--] - '0') - (if (j >= 0) b[j--] - '0' else 0) - borrow
                borrow = if (difference < 0) { difference += 10; 1 } else 0
                out.append('0' + difference)
            }
            return out.reverse().toString()
        }
    }
}
actual fun bigIntegerOf(value: Long): BigInteger = BigInteger(value.toString())
actual fun String.toBigIntegerOrNull(): BigInteger? = try { BigInteger(this) } catch (_: NumberFormatException) { null }

actual fun codePointCount(text: String): Int {
    var count = 0; var index = 0
    while (index < text.length) {
        index += if (text[index].isHighSurrogate() && index + 1 < text.length && text[index + 1].isLowSurrogate()) 2 else 1
        count++
    }
    return count
}

actual fun utf8Prefix(text: String, maxBytes: Int): String {
    val bytes = text.encodeToByteArray()
    if (bytes.size <= maxBytes) return text
    var end = maxBytes
    // Drop a character cut in half: back up to the start of the last (incomplete) sequence.
    var start = end
    while (start > 0 && (bytes[start - 1].toInt() and 0xC0) == 0x80) start--
    if (start > 0) {
        val lead = bytes[start - 1].toInt() and 0xff
        val length = when { lead < 0x80 -> 1; lead >= 0xF0 -> 4; lead >= 0xE0 -> 3; lead >= 0xC0 -> 2; else -> 1 }
        if (start - 1 + length > end) end = start - 1
    }
    return bytes.copyOf(end).decodeToString()
}

actual fun firstCodePoint(text: String): String =
    if (text[0].isHighSurrogate() && text.length > 1 && text[1].isLowSurrogate()) text.substring(0, 2) else text.substring(0, 1)

actual fun <K, V> boundedLruMap(maxSize: Int): MutableMap<K, V> = LruMap(maxSize)

/** Keeps its [maxSize] most recently used entries (reads and writes both count), as an access-order LinkedHashMap. */
private class LruMap<K, V>(private val maxSize: Int) : AbstractMutableMap<K, V>() {
    private val backing = LinkedHashMap<K, V>()
    override val entries: MutableSet<MutableMap.MutableEntry<K, V>> get() = backing.entries
    override fun get(key: K): V? {
        if (!backing.containsKey(key)) return null
        @Suppress("UNCHECKED_CAST") val value = backing.remove(key) as V
        backing[key] = value
        return value
    }
    override fun put(key: K, value: V): V? {
        val previous = backing.remove(key)
        backing[key] = value
        while (backing.size > maxSize) backing.remove(backing.keys.first())
        return previous
    }
    override fun containsKey(key: K) = backing.containsKey(key)
    override fun remove(key: K) = backing.remove(key)
}

/** Gzip with stored (uncompressed) deflate blocks: a valid gzip file any reader unpacks, base64 encoded. The browser's
 * compression is asynchronous only, and this is called synchronously; the reports it packs are size-limited anyway. */
actual fun gzipBase64(text: String): String {
    val data = text.encodeToByteArray()
    val out = ArrayList<Byte>(data.size + 64)
    fun byte(value: Int) { out.add(value.toByte()) }
    fun int32(value: Int) { byte(value); byte(value ushr 8); byte(value ushr 16); byte(value ushr 24) }
    listOf(0x1f, 0x8b, 8, 0, 0, 0, 0, 0, 0, 255).forEach(::byte)
    var offset = 0
    do {
        val length = minOf(65_535, data.size - offset)
        byte(if (offset + length >= data.size) 1 else 0)
        byte(length); byte(length ushr 8); byte(length.inv()); byte(length.inv() ushr 8)
        for (index in offset until offset + length) out.add(data[index])
        offset += length
    } while (offset < data.size)
    int32(crc32(data)); int32(data.size)
    return Base64.Default.encode(out.toByteArray())
}

private val crcTable = IntArray(256) { n ->
    var c = n
    repeat(8) { c = if (c and 1 != 0) (c ushr 1) xor 0xEDB88320.toInt() else c ushr 1 }
    c
}

private fun crc32(data: ByteArray): Int {
    var crc = -1
    for (byte in data) crc = crcTable[(crc xor byte.toInt()) and 0xff] xor (crc ushr 8)
    return crc.inv()
}

actual fun readLinesIfFile(file: File): List<String>? = null
actual fun appendToFile(file: File, text: String) {}
actual fun replaceFileText(file: File, text: String) {}

actual fun <K : Comparable<K>, V> sortedMutableMapOf(): MutableMap<K, V> = SortedMap()

/** A map kept in its keys' natural order, as a TreeMap iterates. */
private class SortedMap<K : Comparable<K>, V> : AbstractMutableMap<K, V>() {
    private val stored = HashMap<K, V>()
    private val ordered = ArrayList<K>()
    override val size get() = ordered.size
    override fun get(key: K): V? = stored[key]
    override fun containsKey(key: K) = stored.containsKey(key)
    override fun put(key: K, value: V): V? {
        if (!stored.containsKey(key)) ordered.add(ordered.binarySearch(key).let { -(it + 1) }, key)
        return stored.put(key, value)
    }
    override fun remove(key: K): V? {
        if (!stored.containsKey(key)) return null
        ordered.removeAt(ordered.binarySearch(key))
        return stored.remove(key)
    }
    override val entries: MutableSet<MutableMap.MutableEntry<K, V>> get() = object : AbstractMutableSet<MutableMap.MutableEntry<K, V>>() {
        override val size get() = ordered.size
        override fun add(element: MutableMap.MutableEntry<K, V>): Boolean { put(element.key, element.value); return true }
        override fun iterator() = object : MutableIterator<MutableMap.MutableEntry<K, V>> {
            private var index = 0
            override fun hasNext() = index < ordered.size
            override fun next(): MutableMap.MutableEntry<K, V> {
                val key = ordered[index++]
                return object : MutableMap.MutableEntry<K, V> {
                    override val key = key
                    @Suppress("UNCHECKED_CAST") override val value: V get() = stored[key] as V
                    override fun setValue(newValue: V): V = stored.put(key, newValue) as V
                }
            }
            override fun remove() { val key = ordered.removeAt(--index); stored.remove(key) }
        }
    }
}

/** [size] secure random bytes (crypto.getRandomValues). */
internal fun secureRandomBytes(size: Int): ByteArray {
    if (size == 0) return ByteArray(0)
    val out = ByteArray(size)
    var offset = 0
    while (offset < size) { // getRandomValues gives at most 65,536 bytes a call
        val count = minOf(65_536, size - offset)
        val bytes = jsRandomBytes(count)
        for (index in 0 until count) out[offset + index] = jsByteAt(bytes, index).toByte()
        offset += count
    }
    return out
}
