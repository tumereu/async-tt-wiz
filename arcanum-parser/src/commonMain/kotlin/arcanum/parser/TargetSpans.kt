package arcanum.parser

import arcanum.api.Ability
import arcanum.api.Instructions
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.TextSpan

/**
 * Where each target slot's phrase sits in its ability's or mode's printed text ([TargetSlot.span]): "any target",
 * "target creature an opponent controls", "up to two target creatures", "another target creature or planeswalker",
 * "any number of targets", "target player's" (as "target player"). It reads the text once the ability is parsed, so every
 * wording gets spans without its rule knowing about them. Slots take the phrases in order (CR 601.2c chooses them in the
 * order the text names them); when the phrases found aren't one for each slot, or one doesn't fit its slot (a player
 * slot without "player" or "opponent" in it), no slot of that text gets a span.
 */
internal object TargetSpans {
    /** [ability] with its slots' spans, read from its own text and its modes' texts; unchanged without a text. */
    fun placed(ability: Ability): Ability = when (ability) {
        is Ability.Spell -> ability.text?.let { ability.copy(instructions = place(it, ability.instructions)) } ?: ability
        is Ability.Activated -> ability.text?.let { ability.copy(instructions = place(it, ability.instructions)) } ?: ability
        is Ability.Triggered -> ability.text?.let { ability.copy(instructions = place(it, ability.instructions)) } ?: ability
        else -> ability
    }

    /** [instructions] with spans: its own slots in [text] without its modes' lines, each mode's slots in its own text. */
    fun place(text: String, instructions: Instructions): Instructions {
        val modes = instructions.modes?.let { m ->
            m.copy(options = m.options.map { mode ->
                mode.text?.let { mode.copy(instructions = mode.instructions.copy(targets = slots(it, mode.instructions.targets))) } ?: mode
            })
        }
        return instructions.copy(targets = slots(ownText(text, instructions), instructions.targets), modes = modes)
    }

    /** [text] with its modes' lines blanked out, every other character where it was: the ability's own slots never sit in
     * a mode's line. */
    fun ownText(text: String, instructions: Instructions): String {
        var own = text
        instructions.modes?.options?.forEach { mode ->
            val modeText = mode.text?.takeIf { it.isNotEmpty() } ?: return@forEach
            val at = own.indexOf(modeText)
            if (at >= 0) own = own.substring(0, at) + " ".repeat(modeText.length) + own.substring(at + modeText.length)
        }
        return own
    }

    /**
     * [slots] with the spans of [text]'s target phrases, in order; unchanged when they don't pair up. A phrase whose count
     * allows several ("two target creatures controlled by different players", "two other target creatures") may stand
     * for several slots in a row, which then share its span.
     */
    private fun slots(text: String, slots: List<TargetSlot>): List<TargetSlot> {
        if (slots.isEmpty()) return slots
        val phrases = phrases(text)
        fun assign(slot: Int, phrase: Int): List<TextSpan>? {
            if (slot == slots.size || phrase == phrases.size) return if (slot == slots.size && phrase == phrases.size) emptyList() else null
            val span = phrases[phrase]
            val words = span.of(text)!!
            for (taken in 1..minOf(most(words), slots.size - slot)) {
                if (!fits(slots[slot + taken - 1], words)) break
                assign(slot + taken, phrase + 1)?.let { return List(taken) { span } + it }
            }
            return null
        }
        val spans = assign(0, 0) ?: return slots
        return slots.zip(spans).map { (slot, span) -> slot.copy(span = span) }
    }

    /** How many targets a phrase's count words allow at most: "two target creatures" 2, "any number of targets" any. */
    private fun most(phrase: String): Int {
        val count = COUNT_WORDS.find(phrase.lowercase())?.groupValues?.get(1) ?: return 1
        return when {
            count == "any number of" || count == "one or more" || count.endsWith("x") || count.endsWith("that many") -> Int.MAX_VALUE
            count == "one, two, or three" -> 3
            count == "one or two" -> 2
            else -> count.removePrefix("up to ").let { NUMBER_WORDS.indexOf(it).takeIf { i -> i >= 0 }?.plus(1) ?: it.toIntOrNull() ?: 1 }
        }
    }

    /** Whether a phrase can name [slot]'s targets: a player slot's says "player" or "opponent"; an object slot's isn't only
     * "target player" or "target opponent". */
    private fun fits(slot: TargetSlot, phrase: String): Boolean {
        val p = phrase.lowercase()
        val player = "player" in p || "opponent" in p
        return when (slot.what) {
            is TargetKind.Players -> player
            is TargetKind.Objects -> !ONLY_PLAYER.matches(p)
            is TargetKind.Either -> true
        }
    }

    /** The target phrases of [text], in order: each "target"/"targets" naming targets, with its count words in front and
     * the rest of its noun phrase after it. Text in quotation marks (another ability's) is left out, and so is a reflexive
     * trigger's sentence and what follows it ("When you do, …", "When a creature card is exiled this way, …", CR 603.12):
     * its targets are its own, chosen when it triggers. */
    fun phrases(text: String): List<TextSpan> {
        val found = mutableListOf<TextSpan>()
        val reflexive = REFLEXIVE.find(text)?.range?.first ?: text.length
        for (m in TARGET_WORD.findAll(text)) {
            val at = m.range.first
            if (at >= reflexive) break
            if (quoted(text, at)) continue
            val before = text.substring(0, at)
            val count = COUNT.find(before)
            val start = if (count != null) count.range.first else {
                // "the target", "that target", "its target", "can't target", "spell's target": not naming one
                val previous = PREVIOUS_WORD.find(before)?.groupValues?.get(1)?.lowercase()
                if (previous != null && (previous in NOT_NAMING || previous.endsWith("'s") || previous.endsWith("’s"))) continue
                // "targets" with no count in front is the verb ("if it targets", "spells that target")
                if (m.value.equals("targets", ignoreCase = true)) continue
                at
            }
            found += TextSpan(start, end(text, start, m.range.last + 1))
        }
        return found
    }

    /** Where the noun phrase that continues at [from] ends: before punctuation (a list "A, B, or C" reads on), before a
     * word that begins the rest of the sentence ("gets", "to", "until", "and"), or at a possessive ("target player's"). */
    private fun end(text: String, start: Int, from: Int): Int {
        var end = from
        var p = from
        scan@ while (p < text.length) {
            when (text[p]) {
                ' ' -> {
                    val word = WORD.find(text, p + 1)?.takeIf { it.range.first == p + 1 } ?: break@scan
                    val w = word.value.lowercase()
                    val rest = text.substring(word.range.last + 1)
                    // "with power less than or equal to 2"
                    if (w == "equal" && rest.startsWith(" to ") && text.substring(start, end).endsWith("than or")) {
                        end = word.range.last + 4; p = end; continue@scan
                    }
                    if (w in STOP || w.replace('’', '\'') in CONTRACTIONS) break@scan
                    // A player is named by "target player" alone: "target player controls", "Target opponent blights 2"
                    if (PLAYER_NOUN.containsMatchIn(text.substring(start, end).lowercase()) && w !in PLAYER_GOES_ON) break@scan
                    // "up to one target instant card and/or up to one target sorcery card": the next phrase begins
                    if ((w == "or" || w == "and/or") && NEXT_TARGET.find(rest)?.range?.first == 0) break@scan
                    // "without paying its mana cost", "by replacing …", "X times"
                    if (w == "without" && rest.startsWith(" paying") || w == "by" && BY_DOING.find(rest)?.range?.first == 0 ||
                        rest.startsWith(" times") || rest.startsWith(" time ")) break@scan
                    val possessive = w.endsWith("'s") || w.endsWith("’s") || w.endsWith("s'") || w.endsWith("s’")
                    end = if (w.endsWith("s'") || w.endsWith("s’")) word.range.last else if (possessive) word.range.last - 1 else word.range.last + 1
                    if (possessive) return end
                    p = word.range.last + 1
                }
                ',' -> {
                    val list = LIST.find(text.substring(p))?.takeIf { it.range.first == 0 } ?: break@scan
                    if (list.value.split(' ', ',').any { it.lowercase() in STOP }) break@scan
                    p += list.value.length - 1
                }
                else -> break@scan
            }
        }
        // Words that only lead into what follows: "target creature that", "any target chosen", "… that blocked or"
        while (true) {
            val last = text.substring(start, end).substringAfterLast(' ')
            if (last.lowercase() !in TRAILING || end - last.length - 1 <= from - 1) break
            end -= last.length + 1
        }
        return end
    }

    /** Whether [at] is inside quotation marks ("gain \"{T}: … any target\""): another ability's text. */
    private fun quoted(text: String, at: Int): Boolean =
        text.substring(0, at).count { it == '"' } % 2 == 1 || text.substring(0, at).let { it.lastIndexOf('“') > it.lastIndexOf('”') }

    private val TARGET_WORD = Regex("\\btargets?\\b", RegexOption.IGNORE_CASE)
    private val NUMBER_WORDS = listOf("one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten")
    private const val NUMBER = "one|two|three|four|five|six|seven|eight|nine|ten|x|that many|\\d+"
    /** A phrase's count words, at its start (lower case). */
    private val COUNT_WORDS = Regex("^(up to (?:$NUMBER)|one, two, or three|one or two|one or more|any number of|$NUMBER) ")
    /** A reflexive trigger's sentence inside the text (CR 603.12): "When"/"Whenever" beginning a later sentence. */
    private val REFLEXIVE = Regex("(?<=[.!]\\s|\\n)(?:When|Whenever)\\b")
    /** What begins another target phrase after "or" ("and/or up to one target sorcery card"). */
    private val NEXT_TARGET = Regex("^ (?:(?:up to (?:$NUMBER)|any number of|$NUMBER) (?:other )?|any (?:other )?|another )?targets?\\b",
        RegexOption.IGNORE_CASE)
    /** The count words in front of "target": "any", "another", "up to two", "one or two", "any number of", "X". */
    private val COUNT = Regex("\\b(?:(?:up to (?:$NUMBER)|one, two, or three|one or two|one or more|any number of|$NUMBER) (?:other )?|any (?:other )?|another )$",
        RegexOption.IGNORE_CASE)
    private val PREVIOUS_WORD = Regex("([\\p{L}'’]+)\\s+$")
    private val NOT_NAMING = setOf("the", "that", "its", "their", "this", "those", "these", "a", "an", "new", "each", "single",
        "same", "chosen", "his", "her", "your", "can", "can't", "cannot", "could", "couldn't", "would", "may", "must",
        "no", "which", "it", "what", "only")
    private val ONLY_PLAYER = Regex("target (?:player|opponent)s?")
    /** A phrase so far that names a player by "target player"/"target opponent" ("any number of target players"). */
    private val PLAYER_NOUN = Regex("targets? (?:player|opponent)s?$")
    /** Words that may still follow a player's noun in its phrase ("target player or planeswalker", "target player who …"). */
    private val PLAYER_GOES_ON = setOf("or", "and/or", "who")
    /** "that's red or white": a clause about the targets, not part of their noun. */
    private val CONTRACTIONS = setOf("that's", "it's", "who's", "which's", "they're")
    /** "by replacing …": how, not which. */
    private val BY_DOING = Regex("^ \\p{L}+ing\\b")
    /** Words a phrase doesn't end with: they lead into what follows it. */
    private val TRAILING = setOf("that", "who", "which", "chosen", "or", "and/or", "of", "with", "by", "from", "than", "other")
    private val WORD = Regex("[\\p{L}\\d'’/+\\-{}]+")
    /** A list's further items and its "or" ("target artifact, enchantment, or creature"). */
    private val LIST = Regex(",(?: [\\p{L}\\-]+){1,2}(?:,(?: [\\p{L}\\-]+){1,2})*,? (?:and/)?or ")
    /** Words that begin what the sentence says of its targets, or a new part of it. */
    private val STOP = setOf(
        "and", "then", "each", "to", "on", "onto", "into", "until", "in", "at", "for", "as", "this", "where", "unless", "if",
        "except", "instead", "divided", "among", "equal", "plus", "beneath", "under", "would", "rather", "but", "during",
        "gets", "get", "gains", "gain", "has", "have", "can't", "can", "cannot", "loses", "lose", "deals", "deal", "fights", "fight",
        "draws", "draw", "discards", "discard", "mills", "mill", "sacrifices", "sacrifice", "reveals", "reveal", "puts", "put",
        "returns", "return", "becomes", "become", "phases", "is", "are", "was", "were", "may", "must", "blocks", "block",
        "attacks", "attack", "searches", "search", "shuffles", "shuffle", "exiles", "exile", "creates", "create", "pays", "pay",
        "explores", "connives", "surveils", "surveil", "scries", "scry", "chooses", "choose", "looks", "look", "takes", "take",
        "assigns", "untaps", "untap", "taps", "tap", "transforms", "copies", "copy", "perpetually", "adds", "add", "plays",
        "play", "casts", "cast", "skips", "doesn't", "stays", "remains", "connive", "explore", "investigates",
        "endures", "learns", "proliferates", "manifests", "amasses", "votes", "wins", "drops", "assign", "counts", "count",
    )
}
