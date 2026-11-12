package arcanum.parser

import arcanum.api.Ability
import arcanum.api.Cost
import arcanum.api.CostObjects
import arcanum.api.CostPart
import arcanum.api.Keyword
import arcanum.api.Mechanic
import arcanum.api.TextSpan
import kotlinx.serialization.json.JsonObject

/**
 * Where each part of a parsed cost sits in its printed text ([Cost.spans]): "{2}", "{T}", "Pay 2 life", and for a part that
 * chooses objects the objects' phrase ("a creature" in "Sacrifice a creature", "two cards" in "Discard two cards", "a
 * creature you control" in "Remove a +1/+1 counter from a creature you control"). Like [TargetSpans] it reads the text once
 * the ability is parsed, so every wording gets spans without its rule knowing about them: an activated ability's cost in the
 * text before its colon, a keyword's cost in its line after the keyword's name, an additional or alternative cost in its
 * static ability's sentence ("As an additional cost to cast this spell, sacrifice a creature.", "You may sacrifice a
 * Mountain rather than pay this spell's mana cost."). Parts take the phrases in order; when one isn't found, or they don't
 * come in order, the cost gets none.
 */
internal object CostSpans {
    /** [ability] with its costs' spans in [text] (its own text, or a keyword's line). */
    fun placed(ability: Ability, text: String): Ability = when (ability) {
        is Ability.Activated -> ability.copy(cost = spanned(ability.cost, text, activatedRegion(text)))
        is Ability.SpecialAction -> ability.copy(cost = spanned(ability.cost, text, activatedRegion(text)))
        is Ability.Static -> staticRegion(text)?.let { region ->
            ability.copy(mechanics = ability.mechanics.map { m ->
                when (m) {
                    is Mechanic.AdditionalCost -> m.copy(cost = spanned(m.cost, text, region))
                    is Mechanic.AlternativeCost -> m.copy(cost = spanned(m.cost, text, region))
                    else -> m
                }
            })
        } ?: ability
        is Keyword -> keyword(ability, text)
        else -> ability
    }

    /** The cost of a keyword written with one ("Equip {2}", "Equip—Sacrifice a creature.", "Ward—Pay 3 life.") with its spans
     * in [line]: after the keyword's name, up to the end of the line. Through the keyword's JSON form, so every keyword with
     * a cost is handled alike. */
    private fun keyword(keyword: Keyword, line: String): Keyword {
        // A class level's cost is its level bar's, not a keyword line's
        if (keyword is Keyword.ClassLevel) return keyword
        val cost = (keyword as? arcanum.api.HasCost)?.cost ?: (keyword as? Keyword.Mayhem)?.cost ?: return keyword
        if (cost.parts.isEmpty()) return keyword
        val name = Regex("(?<![\\p{L}])${Regex.escape(keyword.text)}(?![\\p{L}])", RegexOption.IGNORE_CASE).find(line) ?: return keyword
        val start = name.range.last + 1
        val spanned = spanned(cost, line, start until line.length)
        if (spanned.spans.isEmpty()) return keyword
        val encoded = JSON.encodeToJsonElement(Ability.serializer(), keyword) as? JsonObject ?: return keyword
        if (encoded["cost"] !is JsonObject) return keyword
        val withSpans = JsonObject(encoded + ("cost" to JSON.encodeToJsonElement(Cost.serializer(), spanned)))
        return runCatching { JSON.decodeFromJsonElement(Ability.serializer(), withSpans) as? Keyword }.getOrNull() ?: keyword
    }

    /** An activated ability's cost: before its first colon outside quotation marks, after an ability word or label ("Boast —"). */
    private fun activatedRegion(text: String): IntRange? {
        var quoted = false
        for ((i, c) in text.withIndex()) {
            if (c == '"' || c == '“' || c == '”') quoted = !quoted
            if (c == ':' && !quoted) {
                val dash = text.lastIndexOf(" — ", i).takeIf { it >= 0 }?.plus(3) ?: 0
                return dash until i
            }
        }
        return null
    }

    /** An additional or alternative cost's words in its static ability's sentence. */
    private fun staticRegion(text: String): IntRange? {
        ADDITIONAL.find(text)?.let { return it.groups[1]?.range }
        ALTERNATIVE.find(text)?.let { return it.groups[1]?.range }
        return null
    }

    /** [cost] with the spans of its parts' phrases in [region] of [text]; unchanged when they aren't all found in order. */
    fun spanned(cost: Cost, text: String, region: IntRange?): Cost {
        if (region == null || cost.parts.isEmpty() || region.isEmpty()) return cost
        var at = region.first
        val spans = mutableListOf<TextSpan>()
        for (part in cost.parts) {
            val span = find(part, text, at, region.last + 1) ?: return cost
            spans += span
            at = span.end
        }
        return cost.copy(spans = spans)
    }

    /** Where [part]'s phrase is in [text] from [from] (to [end]): the first piece of the cost from there that names it. */
    private fun find(part: CostPart, text: String, from: Int, end: Int): TextSpan? {
        val window = text.substring(0, end)
        fun literal(s: String): TextSpan? = window.indexOf(s, from).takeIf { it >= 0 }?.let { TextSpan(it, it + s.length) }
        return when (part) {
            is CostPart.Mana -> literal(part.cost.toString())
            is CostPart.Waterbend -> piece(window, from)
            CostPart.TapSelf -> literal("{T}")
            CostPart.UntapSelf -> literal("{Q}")
            is CostPart.Loyalty -> LOYALTY.find(window, from)?.let { TextSpan(it.range.first, it.range.last + 1) }
            is CostPart.Sacrifice -> objects(window, from, "sacrifice", part.what)
            is CostPart.TapObjects -> objects(window, from, "tap", part.what)
            is CostPart.Discard -> objects(window, from, "discard", part.what)
            is CostPart.Exile -> objects(window, from, "exile", part.what)
            is CostPart.ReturnToHand -> objects(window, from, "return", part.what)
            is CostPart.Reveal -> objects(window, from, "reveal", part.what)
            is CostPart.RemoveCounters -> objects(window, from, "remove", part.from, after = " from ")
            is CostPart.PutCounters -> objects(window, from, "put", part.on, after = " on ")
            else -> piece(window, from)
        }
    }

    /**
     * A part that uses objects: from its [verb] on, the objects' phrase (after the verb, or after [after] in the piece: "from
     * a creature you control"), up to where the piece goes on with where they go ("to its owner's hand", "from your
     * graveyard", "on it") or with more objects of the same verb ("Sacrifice a Swamp and a Forest": the second part's phrase
     * follows the "and"); with the verb for objects it doesn't choose ("Sacrifice this creature", "Discard your hand").
     */
    private fun objects(text: String, from: Int, verb: String, what: CostObjects, after: String? = null): TextSpan? {
        val and = AND.find(text, from)?.takeIf { it.range.first == from }
        val verbAt: Int
        var start: Int
        if (and != null) {
            verbAt = and.range.last + 1
            start = verbAt
        } else {
            val m = Regex("(?<![\\p{L}])$verb(?![\\p{L}])", RegexOption.IGNORE_CASE).find(text, from) ?: return null
            verbAt = m.range.first
            start = m.range.last + 2
        }
        val piece = piece(text, verbAt) ?: return null
        if (what !is CostObjects.Choose) start = verbAt
        else if (after != null && and == null) {
            val a = text.indexOf(after, start).takeIf { it in start until piece.end } ?: return piece
            start = a + after.length
        }
        if (start >= piece.end) return piece
        var stop = piece.end
        for (word in ENDS + " and ") {
            val i = text.indexOf(word, start)
            if (i in (start + 1) until stop) stop = i
        }
        return TextSpan(start, stop)
    }

    /** The piece of a cost starting at the first word at or after [from]: up to ", " starting another part (a capital, a
     * symbol, or a verb of a cost), " and " before such a verb, a colon, a full stop, or the end. */
    private fun piece(text: String, from: Int): TextSpan? {
        var start = from
        while (start < text.length && (text[start] == ' ' || text[start] == ',' || text[start] == '—')) start++
        if (start >= text.length) return null
        var p = start
        while (p < text.length) {
            val c = text[p]
            if (c == ':' || c == '.' || c == '(' || c == '\n') break
            if (c == ',' && p + 2 < text.length) {
                val next = text[p + 2]
                if (next.isUpperCase() || next == '{' || next == '[' || startsVerb(text, p + 2)) break
            }
            if (text.startsWith(" and ", p) && startsVerb(text, p + 5)) break
            p++
        }
        var end = p
        while (end > start && (text[end - 1] == ' ' || text[end - 1] == ',')) end--
        return if (end > start) TextSpan(start, end) else null
    }

    private fun startsVerb(text: String, at: Int): Boolean =
        VERBS.any { v -> text.regionMatches(at, v, 0, v.length, ignoreCase = true) && text.getOrNull(at + v.length)?.isLetter() != true }

    private val JSON = kotlinx.serialization.json.Json
    /** More objects of the part before's verb: "a Swamp and a Forest". */
    private val AND = Regex(",? and ")
    private val LOYALTY = Regex("\\[?[+−\\-]?(?:\\d+|X)]?")
    private val ADDITIONAL = Regex("^As an additional cost to (?:cast|activate) [^,]+, (.+?)\\.?$", RegexOption.IGNORE_CASE)
    private val ALTERNATIVE = Regex("^(?:[^.]*?, )?you may (.+?) rather than pay", RegexOption.IGNORE_CASE)
    /** Where an objects' phrase ends inside its piece: where they go or come from. */
    private val ENDS = listOf(" to ", " from ", " onto ", " into ", " on ", " in ")
    /** Verbs that begin a cost's part. */
    private val VERBS = listOf("sacrifice", "tap", "untap", "discard", "exile", "return", "reveal", "remove", "put", "pay", "mill",
        "collect", "forage", "behold", "blight", "exert", "waterbend")
}
