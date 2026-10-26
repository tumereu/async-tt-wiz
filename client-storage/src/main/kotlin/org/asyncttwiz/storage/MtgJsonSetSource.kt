package org.asyncttwiz.storage

import com.google.gson.Strictness
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import org.asyncttwiz.protocol.*
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.CodingErrorAction

internal data class SetFileCard(val uuid: String, val rarity: SetCardRarity, val colors: Set<DraftColor>)
internal data class SetFile(val code: String, val name: String, val cards: List<SetFileCard>, val recipe: SetBoosterRecipe?)

/** Only card sampling fields and the three supported recipes are retained. Unknown provider data is
 * streamed past; strings, nesting, arrays and maps are bounded before allocating collections. */
internal class MtgJsonSetSource {
    fun parse(input: InputStream, code: String, wanted: Set<String>? = null): SetFile {
        val text = InputStreamReader(input, Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT))
        return JsonReader(BoundedJsonText(text, 64 * 1024)).apply {
            strictness = Strictness.STRICT; nestingLimit = 32
        }.use { reader ->
            var result: SetFile? = null
            fields(reader) { key ->
                if (key != "data") reader.skipValue() else {
                    var actualCode: String? = null
                    var name = code
                    var sawCards = false
                    val cards = linkedMapOf<String, SetFileCard>()
                    val recipes = linkedMapOf<String, SetBoosterRecipe>()
                    fields(reader) { field -> when (field) {
                        "code" -> actualCode = string(reader, 16)
                        "name" -> name = string(reader, 256)
                        "cards" -> {
                            sawCards = true
                            reader.beginArray()
                            while (reader.hasNext()) {
                                check(!Thread.currentThread().isInterrupted)
                                val card = card(reader)
                                if (card != null && (wanted == null || card.uuid in wanted)) {
                                    check(cards.size < SetSourceValidation.MAX_CARDS) { "Set card limit exceeded" }
                                    check(cards.put(card.uuid, card) == null) { "Duplicate set card UUID" }
                                }
                            }
                            reader.endArray()
                        }
                        "booster" -> if (wanted != null || reader.peek() == JsonToken.NULL) reader.skipValue() else {
                            fields(reader) { kind ->
                                if (kind in listOf("play", "draft", "default")) recipes[kind] = recipe(reader, kind)
                                else reader.skipValue()
                            }
                        }
                        else -> reader.skipValue()
                    } }
                    check(actualCode == code && sawCards) { "Wrong or incomplete set file: $code" }
                    result = SetFile(code, name, cards.values.toList(), selectSetBoosterRecipe(recipes))
                }
            }
            check(reader.peek() == JsonToken.END_DOCUMENT) { "Trailing set data" }
            checkNotNull(result) { "Missing set data" }
        }
    }

    private fun card(reader: JsonReader): SetFileCard? {
        var uuid: String? = null
        var token = false
        var rarity = SetCardRarity.UNKNOWN
        val colors = linkedSetOf<DraftColor>()
        fields(reader) { key -> when (key) {
            "uuid" -> if (reader.peek() == JsonToken.NULL) reader.nextNull() else uuid = string(reader, 36)
            "isToken" -> token = reader.nextBoolean() || token
            "layout" -> token = string(reader, 100).let { it == "token" || it == "double_faced_token" } || token
            "rarity" -> rarity = when (string(reader, 100)) {
                "common" -> SetCardRarity.COMMON
                "uncommon" -> SetCardRarity.UNCOMMON
                "rare" -> SetCardRarity.RARE
                "mythic" -> SetCardRarity.MYTHIC
                "special" -> SetCardRarity.SPECIAL
                "bonus" -> SetCardRarity.BONUS
                else -> SetCardRarity.UNKNOWN
            }
            "colors" -> {
                reader.beginArray()
                var count = 0
                while (reader.hasNext()) {
                    check(++count <= 5) { "Set card color limit" }
                    colors += DraftColor.valueOf(string(reader, 1))
                }
                reader.endArray()
            }
            else -> reader.skipValue()
        } }
        return uuid?.takeUnless { token }?.let {
            check(DeckValidation.uuid(it)) { "Invalid set card UUID" }
            SetFileCard(it, rarity, colors)
        }
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
                    var weight = 0
                    fields(reader) { field -> when (field) {
                        "contents" -> contents = intMap(reader, SetSourceValidation.MAX_SHEETS, 100)
                        "weight" -> weight = reader.nextInt()
                        else -> reader.skipValue()
                    } }
                    entries += SetBoosterComposition(contents, weight)
                }
                reader.endArray()
                boosters = entries
            }
            "sheets" -> {
                val entries = linkedMapOf<String, SetBoosterSheet>()
                fields(reader, SetSourceValidation.MAX_SHEETS) { sheet ->
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
                    check(codes.size < SetSourceValidation.MAX_SHEETS) { "Set dependency limit" }
                    val code = string(reader, 16)
                    catalogSetFilename(code)
                    check(codes.add(code)) { "Duplicate set dependency" }
                }
                reader.endArray()
                sources = codes.toList()
            }
            else -> reader.skipValue()
        } }
        val recipe = SetBoosterRecipe(boosters, sheets, total ?: boosters.sumOf { it.weight.toLong() }, name, sources)
        // Validate the provider recipe before any missing-card filtering or weight recalculation.
        val uuids = sheets.values.flatMap { it.cards.keys }.toSet()
        check(uuids.size in 1..SetSourceValidation.MAX_CARDS) { "Set recipe card limit" }
        val issues = SetSourceSnapshot("SET", "Set", uuids.map { SetSourceCard("sha256:" + "0".repeat(64), it) }, recipe).validate()
        check(issues.isEmpty()) { "Invalid set recipe $name: $issues" }
        return recipe
    }

    private fun sheet(reader: JsonReader): SetBoosterSheet {
        var cards: Map<String, Int> = emptyMap()
        var foil = false
        var balance = false
        var duplicates = false
        var fixed = false
        var total: Long? = null
        fields(reader) { key -> when (key) {
            "cards" -> cards = intMap(reader, SetSourceValidation.MAX_CARDS, 36)
            "foil" -> foil = reader.nextBoolean()
            "balanceColors" -> balance = reader.nextBoolean()
            "allowDuplicates" -> duplicates = reader.nextBoolean()
            "fixed" -> fixed = reader.nextBoolean()
            "totalWeight" -> total = reader.nextLong()
            else -> reader.skipValue()
        } }
        return SetBoosterSheet(cards, foil, balance, duplicates, fixed, total ?: cards.values.sumOf { it.toLong() })
    }

    private fun intMap(reader: JsonReader, maximum: Int, keyLength: Int): Map<String, Int> =
        linkedMapOf<String, Int>().apply {
            fields(reader, maximum) { key ->
                check(key.length in 1..keyLength)
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
