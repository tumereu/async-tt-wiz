package arcanum.parser

import arcanum.api.Ability
import arcanum.api.ManaProduction
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

/**
 * The printed words of the riders of the mana a parsed ability adds ([ManaProduction.words]): "Spend this mana only to cast creature
 * spells.", "If that mana is spent on a Dragon creature spell, it gains haste until end of turn.", "Until end of turn, you don't lose
 * this mana as steps and phases end." (CR 106.4, 106.6). Like [CostSpans] it reads the text once the ability is parsed, so every
 * wording gets its words without its rule knowing about them: a rider is a sentence that speaks of "this mana" or "that mana", and
 * belongs to the mana of the "Add …" sentence before it. When the ability adds rider-carrying mana once, or as many times as the text
 * has groups of riders, each gets its own group in order (replacing words a rule gave it); otherwise they keep what they have, none
 * when no rule gave any (a host then knows they aren't known).
 */
internal object ManaWords {
    /** [ability] with the words of its mana's riders read from [text] (its own printed text, reminder text left out). */
    fun placed(ability: Ability, text: String): Ability {
        if (!RIDER.containsMatchIn(text)) return ability
        val groups = groups(text)
        if (groups.isEmpty()) return ability
        val encoded = runCatching { JSON.encodeToJsonElement(Ability.serializer(), ability) }.getOrNull() ?: return ability
        val wanting = count(encoded)
        if (wanting == 0 || (groups.size != 1 && groups.size != wanting)) return ability
        var next = 0
        fun fill(e: JsonElement): JsonElement = when (e) {
            is JsonObject -> if (wants(e)) {
                val words = groups[if (groups.size == 1) 0 else next++]
                JsonObject(e.mapValues { (_, v) -> fill(v) } + ("words" to JsonArray(words.map(::JsonPrimitive))))
            } else JsonObject(e.mapValues { (_, v) -> fill(v) })
            is JsonArray -> JsonArray(e.map(::fill))
            else -> e
        }
        return runCatching { JSON.decodeFromJsonElement(Ability.serializer(), fill(encoded)) }.getOrNull() ?: ability
    }

    /** The groups of rider sentences in [text], each those after one "Add …" sentence, in order; groups without riders left out. */
    fun groups(text: String): List<List<String>> {
        val groups = mutableListOf<MutableList<String>>()
        var current: MutableList<String>? = null
        for (sentence in sentences(text)) {
            if (RIDER.containsMatchIn(sentence)) {
                val group = current ?: mutableListOf<String>().also { groups += it; current = it }
                group += sentence
            } else if (ADD.containsMatchIn(sentence)) {
                current = mutableListOf<String>().also { groups += it }
            }
        }
        return groups.filter { it.isNotEmpty() }
    }

    /** [text]'s sentences, line by line, without a mode's bullet or the quotation marks around a quoted ability. */
    private fun sentences(text: String): List<String> = text.lines().flatMap { line ->
        line.replace(BREAK, "$1\n").split('\n').map { it.trim().removePrefix("•").trim().trim('"', '“', '”').trim() }.filter { it.isNotEmpty() }
    }

    /** How many mana productions with riders [e] holds. */
    private fun count(e: JsonElement): Int = when (e) {
        is JsonObject -> (if (wants(e)) 1 else 0) + e.values.sumOf(::count)
        is JsonArray -> e.sumOf(::count)
        else -> 0
    }

    /** [e] is a [ManaProduction] (its kind one of the mana kinds) with a rider. Words it has already are replaced: a rule may have
     * parsed a sentence of its own making ("…, and that spell can't be countered" read as two sentences), whose words aren't printed. */
    private fun wants(e: JsonObject): Boolean {
        val kind = e["kind"] as? JsonObject ?: return false
        if ((kind["type"] as? JsonPrimitive)?.let { runCatching { it.jsonPrimitive.content }.getOrNull() } !in KINDS) return false
        return RIDERS.any { it in e }
    }

    private val JSON = kotlinx.serialization.json.Json
    /** A sentence speaking of the mana it follows. */
    private val RIDER = Regex("""\b(?:this|that) mana\b""", RegexOption.IGNORE_CASE)
    /** A sentence adding mana. */
    private val ADD = Regex("""\badds?\b""", RegexOption.IGNORE_CASE)
    /** Where one sentence ends and the next begins. */
    private val BREAK = Regex("""([.!?]["”]?)\s+""")
    /** The serial names of the mana kinds ([arcanum.api.ManaKind]). */
    private val KINDS = setOf("same-as-produced", "fixed", "choose", "as-could", "each-color-among")
    /** The JSON keys of [ManaProduction]'s riders (present only when not their defaults). */
    private val RIDERS = listOf("restriction", "onlyCostsWithX", "spellGets", "keptUntil", "whenSpent")
}
