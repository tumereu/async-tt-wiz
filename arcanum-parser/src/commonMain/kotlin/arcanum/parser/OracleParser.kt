package arcanum.parser

import arcanum.api.Ability
import arcanum.api.FaceDefinition
import arcanum.api.HasAmount
import arcanum.api.HasCost
import arcanum.api.Keyword
import arcanum.api.Cost
import arcanum.api.CostPart
import arcanum.api.ManaCost

/**
 * Parses one card face's oracle text into a [FaceDefinition]. Reads only the text (no card-data keyword lists), so it
 * works on any card, including custom ones. What it can't read is kept as [Ability.Unparsed], never guessed.
 *
 * Understood so far: keyword lines ("Flying, first strike", "Equip {2}", "Crew 3", "Suspend 4—{1}{R}") for the
 * keywords in [KeywordSyntax.all]. Reminder text in parentheses is ignored.
 */
object OracleParser {
    /** Parses a whole face: its printed characteristics and its rules text. */
    fun parse(card: OracleCard): FaceDefinition {
        val face = characteristics(card)
        val isSpell = face.types.any { it == arcanum.api.CardType.Instant || it == arcanum.api.CardType.Sorcery }
        return face.copy(abilities = abilities(card.text, selfNames(card.name, face), isSpell))
    }

    fun parse(name: String, text: String): FaceDefinition = FaceDefinition(name = name, abilities = abilities(text, setOf(name)))

    /** What the card calls itself: its name, a legendary card's short name ("Thalia"), "this [type]". */
    private fun selfNames(name: String, face: FaceDefinition): Set<String> =
        setOf(name, name.substringBefore(',')) + face.types.map { "this " + it.toString().lowercase() } +
            listOf("this card", "this permanent", "this spell")

    /** Lines that are only reminder text ("({W/P} can be paid with either {W} or 2 life.)") give no ability. */
    private fun abilities(text: String, self: Set<String>, isSpell: Boolean = false): List<Ability> {
        val sentences = Sentences(self, isSpell)
        val result = mutableListOf<Ability>()
        for (raw in text.split('\n').filter { REMINDER.replace(it, "").isNotBlank() }) {
            val line = REMINDER.replace(raw, "").trim()
            val previous = result.lastOrNull()
            val instead = if (previous is Ability.Spell) sentences.insteadOf(line, previous) else null
            when {
                instead != null -> result[result.size - 1] = instead
                else -> result += keywordLine(line) ?: sentences.line(line)?.let(::listOf) ?: listOf(Ability.Unparsed(raw.trim()))
            }
        }
        return result
    }

    /**
     * A keyword line is a list of keywords separated by ", " (or "; " when a keyword's own text has commas). It is
     * recognised by its first part being a known keyword and by not being a sentence: it has no final period, except
     * after an em dash cost ("Ward—Pay 3 life."). Further parts that aren't known keywords stay unparsed one by one.
     */
    private fun keywordLine(line: String): List<Ability>? {
        if (line.isEmpty() || (line.endsWith(".") && !line.contains('—'))) return null
        val parts = line.split(if (line.contains("; ")) "; " else ", ")
        val first = keyword(parts.first()) ?: return null
        return listOf(first) + parts.drop(1).map { keyword(it) ?: Ability.Unparsed(it) }
    }

    /** One keyword with its values, or null when [part] isn't exactly a known keyword written in its form. */
    fun keyword(part: String): Keyword? {
        // "Splice onto Arcane {1}{R}" (CR 702.47a): a quality, then a cost.
        SPLICE.matchEntire(part)?.let { m ->
            val cost = ManaCost.parse(m.groupValues[2]) ?: return null
            return Keyword.Splice(arcanum.api.ObjectFilter.Sub(arcanum.api.Subtype(m.groupValues[1])), Cost.mana(cost))
        }
        val lower = part.lowercase()
        val syntax = BY_LENGTH.firstOrNull { k ->
            lower.startsWith(k.lowercase) && (lower.length == k.lowercase.length || lower[k.lowercase.length] in " —")
        } ?: return null
        val rest = part.substring(syntax.text.length)
        return when (syntax) {
            is KeywordSyntax.None -> if (rest.isEmpty()) syntax.keyword else null
            is KeywordSyntax.Amount -> AMOUNT.matchEntire(rest)?.let { syntax.build(it.groupValues[1].toInt()) }
            is KeywordSyntax.Cost -> if (rest.startsWith(" ")) ManaCost.parse(rest.substring(1))?.let(syntax.build) else null
            is KeywordSyntax.AmountAndCost -> AMOUNT_AND_COST.matchEntire(rest)?.let { m ->
                ManaCost.parse(m.groupValues[2])?.let { syntax.build(m.groupValues[1].toInt(), it) }
            }
        }
    }

    /** Longest names first, so "Double strike" or "Multikicker" is never read as a shorter name. */
    private val BY_LENGTH = KeywordSyntax.all.sortedByDescending { it.lowercase.length }
    private val SPLICE = Regex("""Splice onto (\w+) (\{.+\})""")
    private val REMINDER = Regex("""\s*\([^)]*\)""")
    private val AMOUNT = Regex(""" (\d+)""")
    private val AMOUNT_AND_COST = Regex(""" (\d+)—(.+)""")
}

/** Writes definitions back as oracle-style English, for checking parses against the original text. */
object OracleRenderer {
    fun render(ability: Ability): String = when (ability) {
        is Ability.Unparsed -> ability.text
        is Keyword.Kicker -> "Kicker " + ability.costs.joinToString(" and/or ") { cost(it) }
        is Keyword -> buildString {
            append(ability.text)
            if (ability is HasAmount) append(' ').append(ability.amount)
            if (ability is HasCost) append(if (ability is HasAmount) "—" else " ").append(cost(ability.cost))
        }
        else -> error("Rendering ${ability::class.simpleName} is not written yet")
    }

    /** Mana-only costs as printed; other costs are not rendered yet. */
    private fun cost(cost: Cost): String = cost.parts.joinToString(", ") { part ->
        (part as? CostPart.Mana)?.cost?.toString() ?: error("Rendering cost part $part is not written yet")
    }
}
