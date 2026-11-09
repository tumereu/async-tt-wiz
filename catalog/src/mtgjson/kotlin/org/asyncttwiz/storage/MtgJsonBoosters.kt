package org.asyncttwiz.storage

import com.google.gson.Strictness
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import org.asyncttwiz.protocol.*

/** A set's booster recipes from its MTGJSON `booster` object, for the catalog build (docs/specs/web-client.md section
 * 3.3). Only the fields packs need are kept; strings, nesting and object sizes are bounded. No limit on a recipe's sheets
 * or cards: the server builds packs from any recipe (section 3.7). */
object MtgJsonBoosters {
    private const val MAX_ENTRIES = 1_000_000

    /** The recipes by kind (play, draft, default...) from the `booster` object [text], each checked as a set source's
     * recipe is; throws when one can't be read. */
    fun boosters(text: String): Map<String, SetBoosterRecipe> =
        JsonReader(BoundedJsonText(java.io.StringReader(text), 64 * 1024)).apply {
            strictness = Strictness.STRICT; nestingLimit = 32
        }.use { reader ->
            val recipes = linkedMapOf<String, SetBoosterRecipe>()
            fields(reader) { kind -> recipes[kind] = recipe(reader, kind) }
            check(reader.peek() == JsonToken.END_DOCUMENT) { "Trailing booster data" }
            recipes
        }

    private fun recipe(reader: JsonReader, name: String): SetBoosterRecipe {
        var boosters: List<SetBoosterComposition> = emptyList()
        var sheets: Map<String, SetBoosterSheet> = emptyMap()
        var total: Long? = null
        var sources: List<String> = emptyList()
        fields(reader) { key -> when (key) {
            "boosters" -> {
                val entries = mutableListOf<SetBoosterComposition>()
                reader.beginArray()
                while (reader.hasNext()) {
                    check(entries.size < SetSourceValidation.MAX_COMPOSITIONS) { "Set composition limit" }
                    var contents: Map<String, Int> = emptyMap()
                    var weight = 0L
                    fields(reader) { field -> when (field) {
                        "contents" -> contents = intMap(reader)
                        "weight" -> weight = reader.nextLong()
                        else -> reader.skipValue()
                    } }
                    entries += SetBoosterComposition(contents, weight)
                }
                reader.endArray()
                boosters = entries
            }
            "sheets" -> {
                val entries = linkedMapOf<String, SetBoosterSheet>()
                fields(reader, MAX_ENTRIES) { sheet ->
                    check(sheet.length in 1..100)
                    entries[sheet] = sheet(reader)
                }
                sheets = entries
            }
            "boostersTotalWeight" -> total = reader.nextLong()
            "sourceSetCodes" -> {
                val codes = linkedSetOf<String>()
                reader.beginArray()
                while (reader.hasNext()) {
                    val code = string(reader, 16)
                    check(Regex("[A-Z0-9]{1,16}").matches(code)) { "Invalid set code" }
                    check(codes.add(code)) { "Duplicate set dependency" }
                }
                reader.endArray()
                sources = codes.toList()
            }
            else -> reader.skipValue()
        } }
        val recipe = SetBoosterRecipe(boosters, sheets, total ?: boosters.sumOf { it.weight }, name, sources)
        // Validate the provider recipe before any missing-card filtering or weight recalculation.
        val uuids = sheets.values.flatMap { it.cards.keys }.toSet()
        check(uuids.isNotEmpty()) { "Set recipe without cards" }
        val issues = SetSourceValidation.validate(SetSourceSnapshot("SET", "Set", uuids.map { SetSourceCard("sha256:" + "0".repeat(64), it) }, recipe))
        check(issues.isEmpty()) { "Invalid set recipe $name: $issues" }
        return recipe
    }

    private fun sheet(reader: JsonReader): SetBoosterSheet {
        var cards: Map<String, Long> = emptyMap()
        var foil = false
        var balance = false
        var duplicates = false
        var fixed = false
        var total: Long? = null
        fields(reader) { key -> when (key) {
            "cards" -> cards = linkedMapOf<String, Long>().apply {
                fields(reader, MAX_ENTRIES) { uuid ->
                    check(uuid.length in 1..36)
                    put(uuid, reader.nextLong())
                }
            }
            "foil" -> foil = reader.nextBoolean()
            "balanceColors" -> balance = reader.nextBoolean()
            "allowDuplicates" -> duplicates = reader.nextBoolean()
            "fixed" -> fixed = reader.nextBoolean()
            "totalWeight" -> total = reader.nextLong()
            else -> reader.skipValue()
        } }
        return SetBoosterSheet(cards, foil, balance, duplicates, fixed, total ?: cards.values.sum())
    }

    private fun intMap(reader: JsonReader): Map<String, Int> = linkedMapOf<String, Int>().apply {
        fields(reader, MAX_ENTRIES) { key ->
            check(key.length in 1..100)
            put(key, reader.nextInt())
        }
    }

    private fun string(reader: JsonReader, maximum: Int): String {
        check(reader.peek() == JsonToken.STRING)
        return reader.nextString().also { check(it.length in 1..maximum) { "Set field size limit" } }
    }

    private inline fun fields(reader: JsonReader, maximum: Int = 1000, visit: (String) -> Unit) {
        reader.beginObject()
        val keys = hashSetOf<String>()
        while (reader.hasNext()) {
            val key = reader.nextName()
            check(key.length <= 1024 && keys.size < maximum && keys.add(key)) { "Set object size or duplicate field" }
            visit(key)
        }
        reader.endObject()
    }
}
