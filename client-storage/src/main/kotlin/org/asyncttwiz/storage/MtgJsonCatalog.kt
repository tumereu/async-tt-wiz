package org.asyncttwiz.storage

import com.google.gson.*
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.CodingErrorAction
import java.time.LocalDate
import java.util.UUID
import java.util.logging.Logger

/** Bounded streaming traversal: only a single record is materialized, never a set or full catalog. */
class MtgJsonCatalog(private val recordBytes: Int = 256 * 1024, private val fieldBytes: Int = 64 * 1024) {
    fun metadata(input: InputStream): CatalogMetadata = reader(input).use { reader ->
        val root = value(reader, 0, intArrayOf(0)).asJsonObject
        check(reader.peek() == JsonToken.END_DOCUMENT)
        metadata(root.getAsJsonObject("data"))
    }

    fun import(input: InputStream, index: CatalogIndex, expected: CatalogMetadata, progress: (Long) -> Unit = {}): CatalogMetadata {
        var meta: CatalogMetadata? = null
        var sawData = false
        var count = 0L
        var skipped = 0L
        var tokens = 0L
        var alchemy = 0L
        var onlineOnly = 0L
        val languages = sortedMapOf<String, Long>()
        val reasons = sortedMapOf<String, Long>()
        val batch = ArrayList<CatalogPrinting>(100)
        reader(input).use { reader ->
            objectFields(reader) { key -> when (key) {
                "meta" -> meta = metadata(value(reader, 1, intArrayOf(0)).asJsonObject)
                "data" -> {
                    sawData = true
                    objectFields(reader) { set ->
                        check(set.length in 1..32)
                        var sawCards = false
                        var releaseDate = ""
                        objectFields(reader) { field -> when (field) {
                            "code" -> check(reader.nextString() == set)
                            "releaseDate" -> releaseDate = reader.nextString().also { LocalDate.parse(it) }
                            "cards", "tokens" -> {
                                if (field == "cards") sawCards = true
                                reader.beginArray()
                                while (reader.hasNext()) {
                                    check(!Thread.currentThread().isInterrupted)
                                    // Consume the complete value even when it exceeds the per-record
                                    // budget, so one unsupported record cannot strand the stream.
                                    val budget = intArrayOf(0, 0)
                                    val element = value(reader, 4, budget)
                                    val record = try {
                                        require(budget[1] == 0) { "record/field size limit" }
                                        printing(element.asJsonObject, set, field == "tokens")
                                    } catch (error: IllegalArgumentException) {
                                        reasons.merge("unsupported fields", 1L, Long::plus)
                                        null
                                    } catch (error: IllegalStateException) {
                                        reasons.merge("invalid record", 1L, Long::plus)
                                        null
                                    } catch (error: UnsupportedOperationException) {
                                        reasons.merge("unsupported fields", 1L, Long::plus)
                                        null
                                    }
                                    if (record == null) { skipped++; continue }
                                    batch += record
                                    count++
                                    if (record.token) tokens++
                                    if (record.name.startsWith("A-")) alchemy++
                                    if (element.asJsonObject.get("isOnlineOnly")?.let { it.isJsonPrimitive && it.asBoolean } == true) onlineOnly++
                                    languages.merge(record.language.ifEmpty { "unspecified" }, 1L, Long::plus)
                                    if (batch.size == 100) { index.addBatch(batch); batch.clear(); progress(count) }
                                }
                                reader.endArray()
                            }
                            else -> skip(reader, 3)
                        } }
                        check(sawCards)
                        index.addSet(set, releaseDate)
                    }
                }
                else -> skip(reader, 1)
            } }
            check(reader.peek() == JsonToken.END_DOCUMENT)
        }
        check(sawData && meta?.date == expected.date && meta?.version == expected.version)
        index.addBatch(batch)
        progress(count)
        Logger.getLogger(MtgJsonCatalog::class.java.name).info(
            "MTGJSON records: imported=$count skipped=$skipped reasons=$reasons tokens=$tokens alchemy=$alchemy onlineOnly=$onlineOnly languages=$languages")
        return expected.copy(records = index.count())
    }

    private fun reader(input: InputStream): JsonReader = JsonReader(BoundedJsonText(InputStreamReader(input,
        Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)), fieldBytes))
        .apply { strictness = Strictness.STRICT; nestingLimit = 32 }

    private fun metadata(json: JsonObject): CatalogMetadata {
        val date = string(json, "date", true)
        LocalDate.parse(date)
        val version = string(json, "version", true)
        check(Regex("5\\.[0-9]+\\.[0-9]+(?:[+.-][A-Za-z0-9.-]+)?").matches(version))
        return CatalogMetadata(date, version)
    }

    private fun printing(json: JsonObject, set: String, token: Boolean): CatalogPrinting {
        val id = uuid(string(json, "uuid", true))
        val other = json.get("otherFaceIds")?.takeUnless { it.isJsonNull }?.asJsonArray?.map { uuid(it.asString) }.orEmpty()
        check(other.size <= 63 && other.distinct().size == other.size && id !in other)
        val side = string(json, "side")
        check(other.isEmpty() || side.isNotEmpty())
        json.get("setCode")?.takeUnless { it.isJsonNull }?.let { check(it.asString == set) }
        val scryfall = json.get("identifiers")?.takeUnless { it.isJsonNull }?.asJsonObject
            ?.get("scryfallId")?.takeUnless { it.isJsonNull }?.asString?.let(::uuid)
        // These optional fields are consumed by the import/default-printing and artwork paths.
        string(json, "faceName")
        listOf("isOnlineOnly", "isPromo").forEach { key -> json.get(key)?.takeUnless { it.isJsonNull }?.let {
            check(it.isJsonPrimitive && it.asJsonPrimitive.isBoolean)
        } }
        json.get("availability")?.takeUnless { it.isJsonNull }?.asJsonArray?.forEach {
            check(it.isJsonPrimitive && it.asJsonPrimitive.isString)
        }
        json.get("identifiers")?.takeUnless { it.isJsonNull }?.asJsonObject
            ?.get("scryfallOracleId")?.takeUnless { it.isJsonNull }?.let { uuid(it.asString) }
        val raw = json.toString()
        check(raw.toByteArray(Charsets.UTF_8).size <= recordBytes)
        return CatalogPrinting(id, string(json, "name", true), set, string(json, "number", true),
            string(json, "language"), side, scryfall, token, other, raw)
    }

    private fun uuid(value: String): String { check(UUID.fromString(value).toString() == value); return value }
    private fun string(json: JsonObject, key: String, required: Boolean = false): String {
        val element = json.get(key)
        if (element == null || element.isJsonNull) { check(!required); return "" }
        check(element.isJsonPrimitive && element.asJsonPrimitive.isString)
        return element.asString.also { check(!required || it.isNotEmpty()) }
    }

    private inline fun objectFields(reader: JsonReader, visit: (String) -> Unit) {
        reader.beginObject()
        val keys = HashSet<String>()
        while (reader.hasNext()) {
            val key = reader.nextName()
            check(key.length <= 1024 && keys.size < 100_000 && keys.add(key))
            visit(key)
        }
        reader.endObject()
    }

    private fun value(reader: JsonReader, depth: Int, budget: IntArray): JsonElement {
        check(depth <= 32)
        budget[0] += 8
        if (budget[0] > recordBytes && budget.size > 1) {
            budget[1] = 1
            reader.skipValue()
            return JsonNull.INSTANCE
        }
        check(budget[0] <= recordBytes)
        return when (reader.peek()) {
            JsonToken.BEGIN_OBJECT -> JsonObject().apply {
                objectFields(reader) { key ->
                    budget[0] += key.toByteArray(Charsets.UTF_8).size
                    add(key, value(reader, depth + 1, budget))
                }
            }
            JsonToken.BEGIN_ARRAY -> JsonArray().apply {
                reader.beginArray()
                while (reader.hasNext()) add(value(reader, depth + 1, budget))
                reader.endArray()
            }
            JsonToken.STRING, JsonToken.NUMBER -> {
                val number = reader.peek() == JsonToken.NUMBER
                val text = reader.nextString()
                val size = text.toByteArray(Charsets.UTF_8).size
                if (budget.size > 1 && (size > fieldBytes || budget[0] + size > recordBytes)) {
                    budget[1] = 1
                    budget[0] = recordBytes
                    return JsonNull.INSTANCE
                }
                check(size <= fieldBytes)
                budget[0] += size
                check(budget[0] <= recordBytes)
                if (number) JsonParser.parseString(text) else JsonPrimitive(text)
            }
            JsonToken.BOOLEAN -> JsonPrimitive(reader.nextBoolean())
            JsonToken.NULL -> { reader.nextNull(); JsonNull.INSTANCE }
            else -> error("Invalid JSON value")
        }
    }

    private fun skip(reader: JsonReader, depth: Int) {
        check(depth <= 32)
        when (reader.peek()) {
            JsonToken.BEGIN_OBJECT -> objectFields(reader) { skip(reader, depth + 1) }
            JsonToken.BEGIN_ARRAY -> {
                reader.beginArray(); while (reader.hasNext()) skip(reader, depth + 1); reader.endArray()
            }
            JsonToken.STRING, JsonToken.NUMBER -> check(reader.nextString().toByteArray(Charsets.UTF_8).size <= fieldBytes)
            JsonToken.BOOLEAN -> reader.nextBoolean()
            JsonToken.NULL -> reader.nextNull()
            else -> error("Invalid JSON value")
        }
    }
}

/** Drain oversized strings without letting Gson allocate them. Keep a prefix guaranteed to exceed
 * the decoded field budget (a JSON escape uses at most six characters), so value() rejects the
 * entire record, never stores truncated data, and can continue at the next record. Validate the
 * discarded tail too: malformed escapes, control characters and unterminated strings remain fatal.
 */
private class BoundedJsonText(reader: java.io.Reader, fieldBytes: Int) : java.io.FilterReader(reader) {
    private val maximum = 6L * fieldBytes + 6
    private var quoted = false
    private var escaped = false
    private var unicode = 0
    private var characters = 0L
    private var dropping = false
    override fun read(bytes: CharArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        while (true) {
            val read = super.read(bytes, offset, length)
            if (read < 0) { check(!quoted) { "Unterminated JSON string" }; return -1 }
            var written = 0
            for (index in offset until offset + read) {
                val char = bytes[index]
                if (!quoted) {
                    if (char == '"') { quoted = true; characters = 0; dropping = false }
                    bytes[offset + written++] = char
                    continue
                }
                val keep = !dropping
                when {
                    unicode > 0 -> { check(char in '0'..'9' || char in 'a'..'f' || char in 'A'..'F'); unicode-- }
                    escaped -> {
                        check(char in "\"\\/bfnrtu") { "Invalid JSON escape" }
                        if (char == 'u') unicode = 4
                        escaped = false
                    }
                    char == '\\' -> escaped = true
                    char == '"' -> {
                        quoted = false
                        bytes[offset + written++] = char
                        continue
                    }
                    else -> check(char >= ' ') { "Invalid JSON control character" }
                }
                if (keep) bytes[offset + written++] = char
                // Never cut an escape in half, or the retained prefix would not be valid JSON.
                if (++characters >= maximum && !escaped && unicode == 0) dropping = true
            }
            if (written > 0) return written
        }
    }
}
