package arcanum.parser

/** One card face as oracle text: its own [name] (the face name for multi-face cards), its rules [text], and the
 * keyword abilities the card data lists for it (used to recognise keyword lines). */
data class OracleFace(val name: String, val text: String, val keywords: List<String> = emptyList())

/** What kind of line a [Pattern] came from. */
enum class PatternKind {
    /** One keyword ability from a keyword line ("Flying", "Ward—Pay N life."). */
    KEYWORD,
    /** One mode of a modal spell or ability (a "•" line). */
    MODE,
    /** One Saga chapter ability ("I, II — …"). */
    CHAPTER,
    /** Any other ability line. */
    ABILITY,
}

/** A normalised piece of oracle text: lines with the same [text] are worded the same way apart from names, numbers,
 * mana, colours and creature types. */
data class Pattern(val kind: PatternKind, val text: String)

/**
 * Turns oracle text into [Pattern]s so wordings can be counted and grouped (docs/specs/arcanum-automatum.md §6 step 1).
 * This is a measuring tool for deciding what to parse next, not the parser itself: it may merge wordings that the
 * parser must later tell apart.
 *
 * Per line: reminder text is dropped, self-references become `~`, an ability word or flavour word before " — " is
 * dropped, runs of mana symbols become `{M}` ({T}, {Q}, {E} and other non-mana symbols stay), numbers and number words
 * become `N`, colour words become `[COLOR]` and the given creature types (and their plurals) become `[TYPE]`.
 */
object OracleNormalizer {
    /** Normalised patterns of [face], in text order. [creatureTypes] are the creature subtypes to replace by `[TYPE]`. */
    fun patterns(face: OracleFace, creatureTypes: Set<String> = emptySet()): List<Pattern> {
        val keywords = face.keywords.map { it.lowercase() }
        val typeRegex = typeRegex(creatureTypes)
        return face.text.split('\n').flatMap { raw ->
            var line = REMINDER.replace(raw, "").trim()
            if (line.isEmpty()) return@flatMap emptyList()
            line = selfReferences(line, face.name)
            var kind = PatternKind.ABILITY
            CHAPTER_PREFIX.find(line)?.let { kind = PatternKind.CHAPTER; line = line.substring(it.range.last + 1) }
            if (line.startsWith("• ")) { kind = PatternKind.MODE; line = line.substring(2) }
            ABILITY_WORD.find(line)?.let { match ->
                val prefix = match.groupValues[1]
                if (!prefix.startsWith("Choose") && prefix.lowercase() !in keywords) line = line.substring(match.range.last + 1)
            }
            val keywordParts = keywordParts(line, keywords)
            if (kind == PatternKind.ABILITY && keywordParts != null) {
                keywordParts.map { Pattern(PatternKind.KEYWORD, values(it.replaceFirstChar(Char::uppercaseChar), typeRegex)) }
            } else listOf(Pattern(kind, values(line, typeRegex)))
        }
    }

    /** The parts of a keyword line ("Flying, vigilance"), or null when [line] is not one. A part is a keyword when it
     * starts with one of the card's keywords and is not a sentence (keyword lines end without a period, except costs
     * after an em dash such as "Ward—Pay 3 life."). */
    private fun keywordParts(line: String, keywords: List<String>): List<String>? {
        if (keywords.isEmpty()) return null
        val parts = line.split(", ", "; ")
        val all = parts.all { part ->
            val lower = part.lowercase()
            keywords.any { k -> lower == k || lower.startsWith("$k ") || lower.startsWith("$k—") } &&
                (!part.endsWith(".") || part.contains('—'))
        }
        return if (all) parts else null
    }

    private fun selfReferences(line: String, name: String): String {
        var result = line
        if (name.isNotBlank()) {
            result = word(name).replace(result, "~")
            // Legendary names are shortened in oracle text: "Velka, Storm Herald" is referred to as "Velka".
            val short = name.substringBefore(", ", "")
            if (short.isNotBlank()) result = word(short).replace(result, "~")
        }
        return THIS_OBJECT.replace(result, "~")
    }

    /** [text] as a whole word or name: not part of a longer word on either side. */
    private fun word(text: String) = Regex("""(?<![\w'])${Regex.escape(text)}(?![\w])""")

    private fun values(line: String, typeRegex: Regex?): String {
        var result = SYMBOLS.replace(line) { run ->
            SYMBOL.findAll(run.value).joinToString("") { s -> if (s.groupValues[1] in NON_MANA) s.value else "{M}" }
                .replace(LazyRegexes.MANA_RUN, "{M}")
        }
        result = DIGITS.replace(result, "N")
        result = NUMBER_WORDS.replace(result, "N")
        result = COLORS.replace(result, "[COLOR]")
        if (typeRegex != null) result = typeRegex.replace(result, "[TYPE]")
        return WHITESPACE.replace(result, " ").trim()
    }

    private fun typeRegex(types: Set<String>): Regex? {
        if (types.isEmpty()) return null
        val forms = types.flatMap { t ->
            listOf(t, "${t}s", "${t}es") + if (t.endsWith("f")) listOf(t.dropLast(1) + "ves") else emptyList()
        }.sortedByDescending { it.length }
        return Regex("""\b(?:${forms.joinToString("|") { Regex.escape(it) }})\b""")
    }

    private val REMINDER = Regex("""\s*\([^)]*\)""")
    private val CHAPTER_PREFIX = Regex("""^[IVX]+(?:, [IVX]+)* — """)
    private val ABILITY_WORD = Regex("""^([A-Z][A-Za-z',! -]*?) — """)
    private val THIS_OBJECT = Regex(
        """\b[Tt]his (?:creature|artifact|enchantment|land|planeswalker|battle|spell|permanent|card|token|Aura|Equipment|Vehicle|Saga|Class|Case|Room)\b"""
    )
    private val SYMBOLS = Regex("""(?:\{[^}]+\})+""")
    private val SYMBOL = Regex("""\{([^}]+)\}""")
    private val NON_MANA = setOf("T", "Q", "E", "A", "TK", "CHAOS", "PW")
    private val DIGITS = Regex("""\d+""")
    private val NUMBER_WORDS = Regex("""\b(?:[Oo]ne|[Tt]wo|[Tt]hree|[Ff]our|[Ff]ive|[Ss]ix|[Ss]even|[Ee]ight|[Nn]ine|[Tt]en)\b""")
    private val COLORS = Regex("""\b(?:white|blue|black|red|green)\b""")
    private val WHITESPACE = Regex("""\s+""")
}
