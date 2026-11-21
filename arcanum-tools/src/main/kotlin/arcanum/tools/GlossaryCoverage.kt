package arcanum.tools

import arcanum.api.RulesGlossary
import com.google.gson.JsonObject

/**
 * The rules reminders' coverage of a card ([RulesGlossary.reminders]): every rules word its card data lists (MTGJSON's
 * `keywords`: keyword abilities and actions, and token kinds such as "Food", "Treasure" and "Role token") must have a
 * plain-words explanation in the glossary ([RulesGlossary.lookup]), and where the card's text prints the word outside
 * reminder text the glossary must find it there ([RulesGlossary.rulesWords]; within a longer one it names too: "Royal Role"
 * for "Role token", "Manifest dread" for "Manifest"), so a card that prints it without reminder text shows its reminder.
 * Ability and flavor words (a word leading a line or a chapter before a spaced dash: "Landfall — ", "III — Domain — ")
 * are left out: they mean nothing to the rules, and the ability labels printed that way (exhaust, max speed, ...) have
 * explanations by arcanum-api's own test. Owner 2026-11-21 (E6, every rules word explained): the keywords the parser reads on
 * the card ([read], [keywordsRead]: granted ones too) must each have an explanation the glossary finds in the card's
 * text ([RulesGlossary.named], reminder text included), whatever the card data lists. Returns one line per gap ("Food: no
 * explanation", "Ward: not found in its text", "Flying: read by the parser, not named in its text"); empty when there is none.
 */
internal fun glossaryGaps(faces: List<JsonObject>, read: Set<String> = emptySet()): List<String> {
    fun field(face: JsonObject, key: String) = face.get(key)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
    val text = faces.joinToString("\n") { field(it, "text") }
    val names = (faces.map { field(it, "faceName") } + faces.map { field(it, "name") }).filter { it.isNotBlank() }.distinct()
    val listed = faces.flatMap { face -> face.getAsJsonArray("keywords")?.map { it.asString }.orEmpty() }.distinct()
    val found by lazy { RulesGlossary.rulesWords(text, names).keys }
    val printed = Regex("""\([^)]*\)?""").replace(text, " ")
    val listedGaps = listed.filterNot { it in NOT_RULES_WORDS || leadsALine(it, text) }.mapNotNull { word ->
        val entry = RulesGlossary.lookup(word)
        when {
            entry == null -> "$word: no explanation"
            !wholeWord(word.removeSuffix(" token")).containsMatchIn(printed) -> null
            found.none { it == entry || wholeWord(entry.name).containsMatchIn(it.name) } -> "$word: not found in its text"
            else -> null
        }
    }
    val named by lazy { RulesGlossary.named(text) }
    val readGaps = read.sorted().mapNotNull { id ->
        val entry = RulesGlossary.keywords[id]
        when {
            entry == null -> "$id: read by the parser, no explanation"
            entry !in named -> "${entry.name}: read by the parser, not named in its text"
            else -> null
        }
    }
    return (listedGaps + readGaps).distinct()
}

/** The ids of the keyword abilities ([arcanum.api.KeywordKind]) [definition] has anywhere: its own and those it grants (every
 * keyword ability in the card data's JSON). */
internal fun keywordsRead(definition: arcanum.api.CardDefinition): Set<String> {
    val found = HashSet<String>()
    fun walk(element: kotlinx.serialization.json.JsonElement) {
        when (element) {
            is kotlinx.serialization.json.JsonObject -> for ((key, value) in element) {
                if (key in KEYWORD_FIELDS && value is kotlinx.serialization.json.JsonPrimitive && value.isString &&
                    arcanum.api.KeywordKind.byId(value.content) != null) found += value.content
                walk(value)
            }
            is kotlinx.serialization.json.JsonArray -> element.forEach(::walk)
            else -> Unit
        }
    }
    walk(kotlinx.serialization.json.Json.encodeToJsonElement(arcanum.api.CardDefinition.serializer(), definition))
    return found
}

/** The field a keyword ability names its keyword in: its class discriminator ("type", the keyword's id). A keyword a condition
 * or filter only checks for ("if it was warped", "Vehicles you control") is no keyword the card has. */
private val KEYWORD_FIELDS = setOf("type")

/** Words card data lists among keywords that aren't rules words: "heal" damage is plain English, and "Jump" is listed beside
 * jump-start. */
private val NOT_RULES_WORDS = setOf("Heal", "Jump")

private fun wholeWord(words: String) = Regex("""(?<![\w-])""" + Regex.escape(words) + """(?![\w-])""", RegexOption.IGNORE_CASE)

/** Whether [text] prints [word] as an ability or flavor word: leading a line, a mode or a chapter's text, before a spaced dash. */
private fun leadsALine(word: String, text: String): Boolean =
    Regex("""(^|\n|— )(• )?""" + Regex.escape(word) + """( \d+| X)? — """).containsMatchIn(text)
