package org.asyncttwiz.automatum

/**
 * STK-220 (owner 2026-11-19): a spell's or ability's own "you", "your" and "yours" (its controller) as its controller's
 * name, for lines about another player's Stack entry, so they don't read as if they meant the viewer: "you gain 2 life"
 * as "Bob gains 2 life", "your graveyard" as "Bob's graveyard", "you may draw a card" as "Bob may draw a card", "deals 1
 * damage to you" as "deals 1 damage to Bob". A "you" whose sentence can't be rewritten cleanly (an unknown verb after it)
 * reads "you (Bob)". "Opponent" phrases ("each opponent", "target opponent") are left as printed: in another player's text
 * they mean the viewer, or one of several players.
 */
object ControllerWords {
    /** A piece of rewritten text: [Name] is the controller's name put in, [Plain] the rest. */
    sealed interface Part {
        val text: String
        data class Plain(override val text: String) : Part
        data class Name(override val text: String) : Part
    }

    /** [text] with its controller's words as [name]; [before]: the text shown before it in the same line, which tells
     * whether a "you" at its start is an object ("deals 3 damage to " + "you"). */
    fun parts(text: String, name: String, before: String = ""): List<Part> {
        val out = mutableListOf<Part>()
        val plain = StringBuilder()
        fun flush() { if (plain.isNotEmpty()) { out += Part.Plain(plain.toString()); plain.clear() } }
        fun name() { flush(); out += Part.Name(name) }
        var at = 0
        while (true) {
            val match = WORD.find(text, at) ?: break
            plain.appendRange(text, at, match.range.first)
            val word = match.value
            at = match.range.last + 1
            when (val lower = word.lowercase().replace('’', '\'')) {
                "your", "yours" -> { name(); plain.append("'s") }
                "yourself" -> name()
                "you're" -> { name(); plain.append(" is") }
                "you've" -> { name(); plain.append(" has") }
                "you'll" -> { name(); plain.append(" will") }
                "you'd" -> { name(); plain.append(" would") }
                else -> {
                    check(lower == "you")
                    val previous = PREVIOUS.find(before + text.substring(0, match.range.first))?.groupValues?.get(1)?.lowercase()
                    val after = text.substring(at)
                    when {
                        // An object: after a preposition or a verb taking one ("to you", "attacks you"), or with no word after it
                        previous in OBJECT_LEADS || NEXT.find(after) == null -> name()
                        else -> {
                            val verb = agreed(after)
                            if (verb == null) {
                                plain.append(word).append(" ("); name(); plain.append(")")
                            } else {
                                name()
                                plain.append(verb.first)
                                at += verb.second
                            }
                        }
                    }
                }
            }
        }
        plain.appendRange(text, at, text.length)
        flush()
        return out
    }

    /** [parts] as one plain string. */
    fun text(text: String, name: String, before: String = ""): String = parts(text, name, before).joinToString("") { it.text }

    /**
     * The words right after a subject "you" ([after] starts with them) as they read after a name, and how many characters of
     * [after] they replace: a modal or a past tense unchanged ("may", "drew"), a verb agreeing ("gains", "has", "doesn't"),
     * an adverb with the verb after it agreeing ("also gains"); null when the word isn't known to be a verb.
     */
    private fun agreed(after: String): Pair<String, Int>? {
        val next = NEXT.find(after) ?: return null
        val (space, first) = next.destructured
        val lower = first.lowercase().replace('’', '\'')
        val replaced = when {
            lower in MODALS || lower in PAST -> first
            lower in IRREGULAR -> IRREGULAR.getValue(lower)
            lower in ADVERBS -> {
                val (verb, used) = agreed(after.substring(next.value.length)) ?: return null
                return space + first + verb to next.value.length + used
            }
            lower in VERBS -> thirdPerson(first)
            // A regular past tense ("you controlled", "you attacked with") reads the same after a name
            lower.length > 3 && lower.endsWith("ed") -> first
            else -> return null
        }
        return space + replaced to next.value.length
    }

    /** [verb]'s third person singular: "gains", "searches", "copies". */
    private fun thirdPerson(verb: String): String = when {
        SIBILANT.any { verb.endsWith(it) } -> verb + "es"
        verb.length > 1 && verb.endsWith("y") && verb[verb.length - 2] !in "aeiou" -> verb.dropLast(1) + "ies"
        else -> verb + "s"
    }

    private val WORD = Regex("""\b[Yy]ou(?:r(?:s|self)?|['’](?:re|ve|ll|d))?\b""")
    /** The word right before (only spaces between). */
    private val PREVIOUS = Regex("""([A-Za-z'’-]+)[ \t]+$""")
    /** A space and the word right after it. */
    private val NEXT = Regex("""^([ \t]+)([A-Za-z'’]+)""")
    private val SIBILANT = listOf("s", "sh", "ch", "x", "z", "o")
    private val OBJECT_LEADS = setOf("to", "than", "from", "at", "by", "with", "for", "of", "on", "onto", "upon", "toward",
        "towards", "against", "except", "but", "attack", "attacks", "attacking", "attacked", "targets", "targeting", "chooses",
        "including", "besides")
    private val MODALS = setOf("may", "can", "can't", "cannot", "could", "couldn't", "would", "wouldn't", "should", "might",
        "must", "will", "won't")
    private val PAST = setOf("did", "didn't", "had", "hadn't", "drew", "lost", "paid", "chose", "won", "gave", "took", "made",
        "began", "became", "spent")
    private val IRREGULAR = mapOf("do" to "does", "don't" to "doesn't", "have" to "has", "haven't" to "hasn't", "are" to "is",
        "aren't" to "isn't", "were" to "was", "weren't" to "wasn't", "go" to "goes")
    private val ADVERBS = setOf("also", "then", "only", "still", "never", "ever")
    private val VERBS = setOf("gain", "lose", "draw", "discard", "sacrifice", "control", "own", "cast", "play", "put", "create",
        "search", "reveal", "look", "choose", "pay", "exile", "return", "scry", "surveil", "mill", "attack", "block", "get",
        "win", "tap", "untap", "shuffle", "attach", "activate", "spend", "add", "copy", "counter", "destroy", "take", "skip",
        "divide", "distribute", "name", "flip", "roll", "vote", "investigate", "proliferate", "explore", "manifest", "cycle",
        "become", "need", "know", "guess", "decide", "deal", "let", "make", "move", "prevent", "remove", "exert", "venture",
        "collect", "forage", "plot", "foretell", "transform", "turn", "give", "keep", "assign", "complete", "commit", "double",
        "connive", "discover", "learn", "seek", "begin", "receive", "unlock", "suspect", "cloak", "amass", "adapt", "bolster",
        "support", "fateseal", "clash", "populate", "regenerate", "open", "lead", "note", "reorder", "separate", "planeswalk",
        "set", "start", "finish", "end", "crash", "airbend", "earthbend", "firebend", "waterbend", "harness", "endure",
        "behold", "tempt")
}
