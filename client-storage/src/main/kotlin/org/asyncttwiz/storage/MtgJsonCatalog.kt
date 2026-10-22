package org.asyncttwiz.storage

import com.google.gson.*
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.CodingErrorAction
import java.time.LocalDate
import java.util.UUID

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
        val batch = ArrayList<CatalogPrinting>(100)
        reader(input).use { reader ->
            objectFields(reader) { key -> when (key) {
                "meta" -> meta = metadata(value(reader, 1, intArrayOf(0)).asJsonObject)
                "data" -> {
                    sawData = true
                    objectFields(reader) { set ->
                        check(set.length in 1..32)
                        var sawCards = false
                        objectFields(reader) { field -> when (field) {
                            "code" -> check(reader.nextString() == set)
                            "cards", "tokens" -> {
                                if (field == "cards") sawCards = true
                                reader.beginArray()
                                while (reader.hasNext()) {
                                    check(!Thread.currentThread().isInterrupted)
                                    val json = value(reader, 4, intArrayOf(0)).asJsonObject
                                    batch += printing(json, set, field == "tokens")
                                    count++
                                    if (batch.size == 100) { index.addBatch(batch); batch.clear(); progress(count) }
                                }
                                reader.endArray()
                            }
                            else -> skip(reader, 3)
                        } }
                        check(sawCards)
                    }
                }
                else -> skip(reader, 1)
            } }
            check(reader.peek() == JsonToken.END_DOCUMENT)
        }
        check(sawData && meta?.date == expected.date && meta?.version == expected.version)
        index.addBatch(batch)
        progress(count)
        return expected.copy(records = count)
    }

    private fun reader(input: InputStream): JsonReader = JsonReader(BoundedJsonText(InputStreamReader(input,
        Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT))))
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
        val other = json.getAsJsonArray("otherFaceIds")?.map { uuid(it.asString) }.orEmpty()
        check(other.size <= 63 && other.distinct().size == other.size && id !in other)
        val side = string(json, "side")
        check(other.isEmpty() || side.isNotEmpty())
        json.get("setCode")?.let { check(it.asString == set) }
        val scryfall = json.getAsJsonObject("identifiers")?.get("scryfallId")?.asString?.let(::uuid)
        val raw = json.toString()
        check(raw.toByteArray(Charsets.UTF_8).size <= recordBytes)
        return CatalogPrinting(id, string(json, "name", true), set, string(json, "number", true),
            string(json, "language"), side, scryfall, token, other, raw)
    }

    private fun uuid(value: String): String { check(UUID.fromString(value).toString() == value); return value }
    private fun string(json: JsonObject, key: String, required: Boolean = false): String {
        val element = json.get(key)
        if (element == null) { check(!required); return "" }
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

/** Stops oversized JSON strings before the streaming library allocates them (including skipped fields). */
private class BoundedJsonText(reader: java.io.Reader) : java.io.FilterReader(reader) {
    private var quoted = false
    private var escaped = false
    private var characters = 0
    override fun read(bytes: CharArray, offset: Int, length: Int): Int {
        val read = super.read(bytes, offset, length)
        for (index in offset until offset + maxOf(0, read)) {
            val char = bytes[index]
            if (quoted) {
                check(++characters <= 6 * 64 * 1024)
                if (escaped) escaped = false
                else if (char == '\\') escaped = true
                else if (char == '"') quoted = false
            } else if (char == '"') { quoted = true; characters = 0 }
        }
        return read
    }
}
