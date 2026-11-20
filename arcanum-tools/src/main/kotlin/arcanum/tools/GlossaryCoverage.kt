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
 * explanations by arcanum-api's own test. Returns one line per gap ("Food: no explanation", "Ward: not found in its
 * text"); empty when there is none.
 */
internal fun glossaryGaps(faces: List<JsonObject>): List<String> {
    fun field(face: JsonObject, key: String) = face.get(key)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
    val text = faces.joinToString("\n") { field(it, "text") }
    val names = (faces.map { field(it, "faceName") } + faces.map { field(it, "name") }).filter { it.isNotBlank() }.distinct()
    val listed = faces.flatMap { face -> face.getAsJsonArray("keywords")?.map { it.asString }.orEmpty() }.distinct()
    val found by lazy { RulesGlossary.rulesWords(text, names).keys }
    val printed = Regex("""\([^)]*\)?""").replace(text, " ")
    return listed.filterNot { it in NOT_RULES_WORDS || leadsALine(it, text) }.mapNotNull { word ->
        val entry = RulesGlossary.lookup(word)
        when {
            entry == null -> "$word: no explanation"
            !wholeWord(word.removeSuffix(" token")).containsMatchIn(printed) -> null
            found.none { it == entry || wholeWord(entry.name).containsMatchIn(it.name) } -> "$word: not found in its text"
            else -> null
        }
    }
}

/** Words card data lists among keywords that aren't rules words: "heal" damage is plain English, and "Jump" is listed beside
 * jump-start. */
private val NOT_RULES_WORDS = setOf("Heal", "Jump")

private fun wholeWord(words: String) = Regex("""(?<![\w-])""" + Regex.escape(words) + """(?![\w-])""", RegexOption.IGNORE_CASE)

/** Whether [text] prints [word] as an ability or flavor word: leading a line, a mode or a chapter's text, before a spaced dash. */
private fun leadsALine(word: String, text: String): Boolean =
    Regex("""(^|\n|— )(• )?""" + Regex.escape(word) + """( \d+| X)? — """).containsMatchIn(text)
