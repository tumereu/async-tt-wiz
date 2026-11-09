package arcanum.tools

import arcanum.api.Ability
import arcanum.parser.OracleCard
import arcanum.parser.OracleParser
import java.io.File

/**
 * Says for an oracle text which sentences and clauses read alone, the first clause that doesn't, and the existing parser rules
 * nearest to it, so a worker needn't search the parser. Reads through the parser's public API only: a clause is read
 * alone inside a carrier sentence (`{T}: X`, `Whenever X, draw a card.`, `X: Draw a card.`), which loses its context, so
 * a clause can fail alone that reads in place; that is reported as what it is, a lead. Args: the text ("\n" for a new line).
 */
fun main(args: Array<String>) {
    val text = args.joinToString(" ").replace("\\n", "\n").trim()
    require(text.isNotEmpty()) { "Usage: parserExplain \"oracle text\"" }
    renderExplanation(explain(text), RuleIndex.at(ParserToolPaths.parserSources)).forEach(::println)
}

internal sealed interface ClauseKind {
    val label: String
    data object Effect : ClauseKind { override val label = "effect" }
    data object Trigger : ClauseKind { override val label = "trigger" }
    data object Cost : ClauseKind { override val label = "cost" }
    data object Condition : ClauseKind { override val label = "condition" }
    /** Each part reads alone, the sentence or line they make doesn't. */
    data object Join : ClauseKind { override val label = "the whole, though its parts read alone" }
}

internal data class ClauseRead(val kind: ClauseKind, val text: String, val read: Boolean)
internal class SentenceRead(val text: String, val read: Boolean, val clauses: List<ClauseRead>)
internal class LineRead(val text: String, val read: Boolean, val sentences: List<SentenceRead>)

internal class Explanation(val lines: List<LineRead>) {
    val unread: List<ClauseRead> get() = lines.filter { !it.read }.flatMap { line ->
        val clauses = line.sentences.flatMap { it.clauses }.filter { !it.read }
        clauses.ifEmpty { listOf(ClauseRead(ClauseKind.Join, line.text, false)) }
    }
    val firstUnread: ClauseRead? get() = unread.firstOrNull()
}

/** Reads [text] on a card of any type, or only as [own] (the printed card) plus the generic carriers. */
internal fun explain(text: String, own: OracleCard? = null): Explanation {
    val lines = abilityBlocks(text).map { line ->
        val body = ABILITY_WORD.replace(line, "")
        if (reads(line, own)) return@map LineRead(line, true, emptyList())
        LineRead(line, false, sentencesOf(body).mapIndexed { i, sentence -> explainSentence(sentence, i == 0, own) })
    }
    return Explanation(lines)
}

private fun explainSentence(sentence: String, first: Boolean, own: OracleCard?): SentenceRead {
    if (reads(sentence, own) || readsAsEffect(sentence, own)) return SentenceRead(sentence, true, emptyList())
    return SentenceRead(sentence, false, clausesOf(sentence, first, own).map { (kind, clause) ->
        ClauseRead(kind, clause, when (kind) {
            ClauseKind.Trigger -> reads("$clause, draw a card.", own)
            ClauseKind.Cost -> reads("$clause: Draw a card.", own)
            ClauseKind.Condition -> readsAsCondition(clause, own)
            else -> readsAsEffect(clause, own)
        })
    })
}

/** A line of a mode or modal header stays with its line; everything else splits at sentence ends outside quotes. */
internal fun sentencesOf(line: String): List<String> {
    val out = ArrayList<String>()
    val current = StringBuilder()
    var quoted = false
    for (i in line.indices) {
        val c = line[i]
        current.append(c)
        if (c == '"' || c == '“' || c == '”') quoted = !quoted
        val endsSentence = c == '.' && !quoted && (i == line.lastIndex || line[i + 1] == ' ')
        if (endsSentence || c == '\n') {
            out += current.toString().trim()
            current.clear()
        }
    }
    if (current.isNotBlank()) out += current.toString().trim()
    return out.filter { it.isNotEmpty() && it != "." }
}

/** A sentence's parts: its trigger or cost, a leading condition, and the effect clauses. */
internal fun clausesOf(sentence: String, first: Boolean, own: OracleCard? = null): List<Pair<ClauseKind, String>> {
    val body = sentence.trim().removeSuffix(".")
    val out = ArrayList<Pair<ClauseKind, String>>()
    var rest = body
    if (TRIGGER_START.containsMatchIn(rest)) {
        val commas = Regex(", ").findAll(rest).map { it.range.first }.toList()
        // A trigger's own commas ("a red, white, or blue spell"): the first split whose head reads, else the first.
        val at = commas.firstOrNull { reads(rest.substring(0, it) + ", draw a card.", own) } ?: commas.firstOrNull()
        if (at != null) {
            out += ClauseKind.Trigger to rest.substring(0, at)
            rest = rest.substring(at + 2)
        }
    } else if (first && ": " in rest) {
        out += ClauseKind.Cost to rest.substringBefore(": ")
        rest = rest.substringAfter(": ")
    }
    LEADING_CONDITION.find(rest)?.let { m ->
        out += ClauseKind.Condition to m.groupValues[1]
        rest = rest.substring(m.range.last + 1)
    }
    for (part in CLAUSE_BREAK.split(rest).map { it.trim() }.filter { it.isNotEmpty() }) {
        val trailing = TRAILING_CONDITION.find(part)
        if (trailing != null && trailing.range.first > 0) {
            out += ClauseKind.Effect to part.substring(0, trailing.range.first).trim()
            out += ClauseKind.Condition to trailing.value.trim()
        } else {
            out += ClauseKind.Effect to part
        }
    }
    return out
}

private fun readsAsEffect(clause: String, own: OracleCard? = null): Boolean {
    val body = clause.trim().removeSuffix(".")
    if (body.isEmpty()) return false
    val capital = body.replaceFirstChar { it.uppercase() }
    val small = body.replaceFirstChar { it.lowercase() }
    return readsOn(INSTANT, "$capital.") || readsOn(CREATURE, "{T}: $capital.") || readsOn(CREATURE, "When this creature enters, $small.") ||
        (own != null && (readsOn(own, "$capital.") || readsOn(own, "{T}: $capital.")))
}

private fun readsAsCondition(clause: String, own: OracleCard? = null): Boolean {
    val body = clause.trim().removeSuffix(".")
    val small = body.replaceFirstChar { it.lowercase() }
    val capital = body.replaceFirstChar { it.uppercase() }
    return readsOn(CREATURE, "{T}: Draw a card $small.") || readsOn(INSTANT, "$capital, draw a card.") ||
        readsOn(CREATURE, "When this creature enters, $small, draw a card.") ||
        (own != null && (readsOn(own, "{T}: Draw a card $small.") || readsOn(own, "$capital, draw a card.")))
}

private fun reads(text: String, own: OracleCard? = null): Boolean =
    (own != null && readsOn(own, text)) || CARRIERS.any { readsOn(it, text) }

private fun readsOn(carrier: OracleCard, text: String): Boolean = try {
    OracleParser.parse(carrier.copy(text = text)).abilities.let { abilities -> abilities.isNotEmpty() && abilities.none { it is Ability.Unparsed } }
} catch (e: RuntimeException) {
    false
} catch (e: StackOverflowError) {
    false
}

private val CREATURE = OracleCard("Carrier", "{1}", "Creature — Human", "", "2", "2")
private val INSTANT = OracleCard("Carrier", "{1}", "Instant", "")
private val CARRIERS = listOf(CREATURE, INSTANT, OracleCard("Carrier", "{1}", "Enchantment", ""), OracleCard("Carrier", "{1}", "Artifact", ""),
    OracleCard("Carrier", null, "Land", ""), OracleCard("Carrier", "{1}", "Legendary Planeswalker — Carrier", "", loyalty = "3"),
    OracleCard("Carrier", "{1}", "Battle — Siege", "", defense = "5"))

private val ABILITY_WORD = Regex("""^[A-Z][\w' -]{2,30} — """)
private val TRIGGER_START = Regex("""^(?:When|Whenever|At)\b""")
private val LEADING_CONDITION = Regex("""^((?:If|As long as|Unless|During|Until|For as long as) [^,]+), """)
private val TRAILING_CONDITION = Regex("""\b(?:if|unless|only if|as long as) .+$""")
private const val VERBS = "add|attach|cast|counter|create|deal|deals|destroy|discard|draw|exile|gain|gains|get|gets|have|it|look|lose|loses|mill|pay|put|" +
    "remove|return|reveal|sacrifice|scry|search|shuffle|surveil|tap|that|they|this|transform|untap|you"
private val CLAUSE_BREAK = Regex(""", then |; |,? and then |, and (?=(?:$VERBS)\b)|, (?=(?:$VERBS)\b)| and (?=(?:$VERBS)\b)| then (?=(?:$VERBS)\b)""")

/** One regex of the parser's sources, as `file:line`. */
internal class ParserRule(val file: String, val line: Int, val pattern: String, val words: Set<String>)
internal class NearRule(val rule: ParserRule, val score: Double)

/** The regex literals in the parser's sources, searched by the words they and a clause share, the rarer words counting more. */
internal class RuleIndex(val rules: List<ParserRule>) {
    private val frequency = HashMap<String, Int>().also { counts -> rules.forEach { r -> r.words.forEach { counts.merge(it, 1, Int::plus) } } }

    private fun weight(word: String): Double = 1.0 / kotlin.math.ln(2.0 + (frequency[word] ?: 0))

    /** The rules nearest to [clause]: a score of 1 means the rule has every word of the clause. */
    fun nearest(clause: String, count: Int = 8): List<NearRule> {
        val wanted = wordsOf(clause).ifEmpty { return emptyList() }
        val total = wanted.sumOf { weight(it) }
        return rules.mapNotNull { rule ->
            val shared = wanted.filter { it in rule.words }
            if (shared.isEmpty()) null else NearRule(rule, shared.sumOf { weight(it) } / total)
        }.sortedWith(compareBy<NearRule> { -it.score }.thenBy { it.rule.words.size }.thenBy { it.rule.file }.thenBy { it.rule.line }).distinctBy { it.rule.pattern }.take(count)
    }

    companion object {
        private val indexes = HashMap<String, RuleIndex>()

        fun at(sources: File): RuleIndex = indexes.getOrPut(sources.path) { RuleIndex(readRules(sources)) }

        private fun readRules(sources: File): List<ParserRule> = sources.walkTopDown().filter { it.extension == "kt" }.sortedBy { it.path }.flatMap { file ->
            val text = file.readText()
            RULE_LITERAL.findAll(text).mapNotNull { m ->
                val literal = m.groupValues[1].ifEmpty { m.groupValues[2] }
                val words = wordsOf(CLASS_OR_ESCAPE.replace(literal, " "))
                if (words.isEmpty()) null else ParserRule(file.name, text.substring(0, m.range.first).count { it == '\n' } + 1, literal, words)
            }.toList()
        }.toList()

        private val RULE_LITERAL = Regex("\\bRegex\\(\\s*(?:\"\"\"(?s:(.*?))\"\"\"(?!\")|\"((?:\\\\.|[^\"\\\\])*)\")")
        /** Regex syntax that isn't a word of the wording: an escape class, a group's opening, a named group. */
        private val CLASS_OR_ESCAPE = Regex("""\\[a-zA-Z]|\(\?<\w+>|\(\?[:=!]|\$\{?\w+\}?""")
    }
}

private val STOP_WORDS = setOf("a", "an", "the", "of", "to", "or", "and", "on", "in", "it", "its", "that", "this", "from", "with", "for", "at", "as", "is", "are", "be", "by")

/** A clause's or rule's words, lower case, without a plural's s. */
internal fun wordsOf(text: String): Set<String> = Regex("""[a-z][a-z']+""").findAll(text.lowercase()).map { it.value }
    .filter { it !in STOP_WORDS }.map { it.removeSuffix("'s").removeSuffix("s") }.filter { it.length > 1 }.toSet()

internal const val NEAR_ENOUGH = 0.5

/** The explanation in at most 30 lines: the lines, then the first unread clause and the rules nearest it. */
internal fun renderExplanation(explanation: Explanation, index: RuleIndex, width: Int = 150, near: Int = 8): List<String> {
    fun cut(text: String) = text.replace("\n", " / ").let { if (it.length > width) it.take(width - 3) + "..." else it }
    val detail = ArrayList<String>()
    for (line in explanation.lines) {
        detail += "${if (line.read) "READS " else "UNREAD"} ${cut(line.text)}"
        for (sentence in line.sentences) {
            if (line.sentences.size > 1) detail += "  ${if (sentence.read) "reads " else "unread"} sentence: ${cut(sentence.text)}"
            for (clause in sentence.clauses) detail += "    ${if (clause.read) "reads " else "UNREAD"} ${clause.kind.label}: ${cut(clause.text)}"
        }
    }
    val first = explanation.firstUnread
    val tail = ArrayList<String>()
    if (first == null) {
        tail += "Every line reads."
    } else {
        tail += "First unread: ${first.kind.label}: ${cut(first.text)}"
        val rules = index.nearest(first.text, near)
        tail += if (rules.isEmpty()) "No rule shares a word with it." else "Nearest rules (arcanum-parser/src/commonMain/kotlin/arcanum/parser/):"
        rules.forEach { tail += "  ${it.rule.file}:${it.rule.line} ${"%.2f".format(java.util.Locale.ROOT, it.score)} ${cut(it.rule.pattern.replace(Regex("""\s+"""), " ")).take(100)}" }
    }
    val room = MAX_LINES - tail.size
    val shown = if (detail.size > room) detail.take(room - 1) + "... ${detail.size - room + 1} more lines" else detail
    return shown + tail
}

private const val MAX_LINES = 30
