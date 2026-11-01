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
 * Understood: keyword lines for the keywords in [KeywordSyntax.all], and the sentence wordings in [Sentences] (the
 * cube chunk tests list them). Reminder text in parentheses is ignored. An instant or sorcery with any unread line keeps
 * all its spell text unparsed (CR 608.2b).
 */
object OracleParser {
    /** Parses a whole face: its printed characteristics and its rules text. */
    fun parse(card: OracleCard): FaceDefinition {
        val face = characteristics(card)
        val isSpell = face.types.any { it == arcanum.api.CardType.Instant || it == arcanum.api.CardType.Sorcery }
        val self = selfNames(card.name, face)
        var abilities = abilities(card.text, self, isSpell)
        val hasX = face.manaCost?.symbols?.any { it is arcanum.api.ManaSymbol.Variable } == true
        if (hasX) abilities = abilities.map { if (it is Ability.Spell) it.copy(instructions = it.instructions.copy(announced = listOf("X"))) else it }
        // Overload (CR 702.96b): the spell's text with "target" replaced by "each".
        if (abilities.any { it is Keyword.Overload }) {
            val spellText = card.text.split('\n').map { REMINDER.replace(it, "").trim() }
                .filter { it.isNotEmpty() && keywordLine(it) == null }.joinToString(" ")
            val overloaded = Sentences(self, isSpell).instructions(spellText.replace("target ", "each "))
            abilities = abilities.map { if (it is Keyword.Overload && overloaded != null) it.copy(instructions = overloaded) else it }
        }
        return face.copy(abilities = abilities)
    }

    fun parse(name: String, text: String): FaceDefinition = FaceDefinition(name = name, abilities = abilities(text, setOf(name)))

    /** What the card calls itself: its name, a legendary card's short name ("Thalia"), "this [type]", "this [subtype]"
     * ("this Equipment", "this Vehicle"). */
    private fun selfNames(name: String, face: FaceDefinition): Set<String> =
        setOf(name, name.substringBefore(',')) + face.types.map { "this " + it.toString().lowercase() } +
            listOf("this card", "this permanent", "this spell") +
            // chunk 26: a legendary card's first name ("Kaito" of "Kaito Shizuki", CR 201.5c)
            listOfNotNull(name.substringBefore(' ').takeIf { arcanum.api.Supertype.Legendary in face.supertypes && ',' !in name && it != "The" }) +
            // chunk 20
            face.subtypes.map { "this " + it.name }

    /** Lines that are only reminder text ("({W/P} can be paid with either {W} or 2 life.)") give no ability. */
    private fun abilities(text: String, self: Set<String>, isSpell: Boolean = false): List<Ability> {
        // chunks 21, 26: abilities that exile are linked to "cards exiled with [this]" (CR 607.2a)
        val sentences = Sentences(self, isSpell, linkedExile = self.any { text.contains("exiled with $it") })
        val result = mutableListOf<Ability>()
        val raws = mutableListOf<String>()
        val lines = text.split('\n').filter { REMINDER.replace(it, "").isNotBlank() }
        var i = 0
        while (i < lines.size) {
            val raw = lines[i++]
            val line = REMINDER.replace(raw, "").trim()
            // Leveler bands (CR 711.2a): what follows a "LEVEL N-M" line applies only within that band, so the bands
            // stay printed text until the parser reads them as conditional abilities.
            if (LEVEL_BAND.matches(line)) {
                val band = (listOf(raw) + lines.drop(i)).joinToString("\n") { it.trim() }
                result += Ability.Unparsed(band); raws += band
                break
            }
            // "Choose one —" and its "• " modes (CR 700.2)
            val choose = CHOOSE.matchEntire(line)
            if (choose != null && isSpell) {
                val modeLines = mutableListOf<String>()
                while (i < lines.size && lines[i].startsWith("• ")) modeLines += REMINDER.replace(lines[i++].removePrefix("• "), "").trim()
                val modes = sentences.modes(choose, modeLines)
                result += if (modes != null) Ability.Spell(arcanum.api.Instructions(modes = modes))
                    else Ability.Unparsed((listOf(raw) + modeLines.map { "• $it" }).joinToString("\n"))
                raws += (listOf(raw) + modeLines.map { "• $it" }).joinToString("\n")
                continue
            }
            // chunk 21: a triggered ability with modes ("Whenever …, choose one that hasn't been chosen this turn —", CR 700.2)
            if (!isSpell && line.endsWith(" —") && i < lines.size && lines[i].startsWith("• ")) {
                val modeLines = mutableListOf<String>()
                while (i < lines.size && lines[i].startsWith("• ")) modeLines += REMINDER.replace(lines[i++].removePrefix("• "), "").trim()
                val printed = (listOf(raw.trim()) + modeLines.map { "• $it" }).joinToString("\n")
                result += sentences.modalTrigger(line, modeLines) ?: Ability.Unparsed(printed)
                raws += printed
                continue
            }
            val previous = result.lastOrNull()
            val instead = if (previous is Ability.Spell) sentences.insteadOf(line, previous) else null
            val parsed = if (instead == null) keywordLine(line) ?: sentences.multi(line) ?: sentences.line(line)?.let(::listOf) else null
            when {
                instead != null -> { result[result.size - 1] = instead; raws[raws.size - 1] += "\n" + raw.trim() }
                // An instant's or sorcery's text is one spell ability (CR 113.3a): join its lines.
                previous is Ability.Spell && parsed?.singleOrNull() is Ability.Spell && (parsed.single() as Ability.Spell).abilityWord == null ->
                {
                    result[result.size - 1] = previous.copy(instructions = previous.instructions.join((parsed.single() as Ability.Spell).instructions))
                    raws[raws.size - 1] += "\n" + raw.trim()
                }
                else -> {
                    val added = parsed ?: listOf(Ability.Unparsed(raw.trim()))
                    result += added
                    added.forEach { _ -> raws += raw.trim() }
                }
            }
        }
        // A spell resolves as one object (CR 608.2b): if any of its lines can't be read, none of its spell text or
        // casting costs is automated; they stay as printed for the players.
        if (isSpell && result.any { it is Ability.Unparsed }) {
            return result.mapIndexed { index, ability ->
                if (ability is Ability.Spell || ability.isCastingCost()) Ability.Unparsed(raws[index]) else ability
            }.distinct()
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
        // A keyword with an em-dash cost ("Flashback—{1}{U}, Pay 3 life.") is alone on its line; its commas are the cost's.
        val parts = when {
            line.contains("; ") -> line.split("; ")
            line.contains('—') -> listOf(line)
            else -> line.split(", ")
        }
        val first = keyword(parts.first()) ?: return null
        return listOf(first) + parts.drop(1).map { keyword(it) ?: Ability.Unparsed(it) }
    }

    /** One keyword with its values, or null when [part] isn't exactly a known keyword written in its form. */
    fun keyword(part: String): Keyword? {
        // "Flashback—Sacrifice a Mountain." (CR 702.34a): a cost that isn't only mana.
        FLASHBACK_COST.matchEntire(part)?.let { m ->
            return Keyword.Flashback(Sentences(setOf("this card")).cost(m.groupValues[1]) ?: return null)
        }
        // chunk 13: "Escalate—Discard a card." (CR 702.120a): a cost that isn't only mana.
        ESCALATE_COST.matchEntire(part)?.let { m ->
            return Keyword.Escalate(Sentences(setOf("this card")).cost(m.groupValues[1]) ?: return null)
        }
        // "Splice onto Arcane {1}{R}" (CR 702.47a): a quality, then a cost.
        SPLICE.matchEntire(part)?.let { m ->
            val cost = ManaCost.parse(m.groupValues[2]) ?: return null
            return Keyword.Splice(arcanum.api.ObjectFilter.Sub(arcanum.api.Subtype(m.groupValues[1])), Cost.mana(cost))
        }
        // chunk 25: "Enchant creature or Vehicle", "Enchant player" (CR 702.5a)
        ENCHANT.matchEntire(part)?.let { m ->
            return Keyword.Enchant(when (m.groupValues[1]) {
                "player" -> arcanum.api.TargetKind.Players(arcanum.api.PlayerFilter.Any)
                "opponent" -> arcanum.api.TargetKind.Players(arcanum.api.PlayerFilter.Opponent)
                else -> arcanum.api.TargetKind.Objects(Filters.noun(m.groupValues[1]) ?: return null)
            })
        }
        // chunk 40: "Forestwalk", "Islandwalk" (CR 702.14a): a land type, then "walk"
        LANDWALK.matchEntire(part)?.let { m ->
            if (m.groupValues[1] !in RulesWords.subtypes) return null
            return Keyword.Landwalk(arcanum.api.ObjectFilter.Sub(arcanum.api.Subtype(m.groupValues[1])), part)
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
    private val LEVEL_BAND = Regex("""LEVEL \d+(?:-\d+|\+)""")
    private val CHOOSE = Regex("""Choose (one|two|three|four)( or more| or both)?(?: —|\. You may choose the same mode more than once\.)""")
    private val FLASHBACK_COST = Regex("""Flashback—(.+?)\.?""")
    private val SPLICE = Regex("""Splice onto (\w+) (\{.+\})""")
    private val REMINDER = Regex("""\s*\([^)]*\)""")
    private val AMOUNT = Regex(""" (\d+)""")
    private val AMOUNT_AND_COST = Regex(""" (\d+)—(.+)""")
    // chunk 25
    private val ENCHANT = Regex("""Enchant (.+)""")
    // chunk 13
    private val ESCALATE_COST = Regex("""Escalate—(.+?)\.?""")
    // chunk 40
    private val LANDWALK = Regex("""([A-Z][a-z]+)walk""")
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

private fun arcanum.api.Instructions.join(next: arcanum.api.Instructions) = copy(
    targets = targets + next.targets, steps = steps + next.steps, replacements = replacements + next.replacements,
    announced = (announced + next.announced).distinct(),
)

private fun Ability.isCastingCost() = this is Ability.Static &&
    mechanics.any { it is arcanum.api.Mechanic.AdditionalCost || it is arcanum.api.Mechanic.AlternativeCost }
