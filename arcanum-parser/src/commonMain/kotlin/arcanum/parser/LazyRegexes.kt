package arcanum.parser

/**
 * The regexes rules use only inside a function, compiled once and only when a rule first needs one: a card's first parse
 * on a phone compiles nothing here that it doesn't use. Most other regexes are compiled as their object is first touched.
 */
internal object LazyRegexes {
    // Lists
    val AND_LIST by lazy { Regex(""",? and |, """) }
    val OR_LIST by lazy { Regex(""",? or |, """) }
    val OR_AND_LIST by lazy { Regex(""",? or |,? and |, """) }
    val AND_OR_LIST by lazy { Regex(""",? and |, |,? or """) }
    val COMMA_AND_WORDS by lazy { Regex(", and |, | and ") }
    val AND_BEFORE_ARTICLE by lazy { Regex(""",? and (?=an? )|, (?=an? )""") }
    val OR_BEFORE_COST_VERB by lazy { Regex(""" or (?=(?:sacrifice|pay|discard|exile|tap) )""") }
    val OR_BEFORE_COST_VERB_LONG by lazy { Regex(""" or (?=(?:sacrifice|pay|discard|exile|return|tap|reveal|choose) )""") }
    val QUOTED by lazy { Regex(""""(.+?)"""") }
    val AND_QUOTED by lazy { Regex(""",? ?(?:and )?"(.+?)"""") }

    // Word forms
    val VES_END by lazy { Regex("ves$") }
    val ICE_END by lazy { Regex("ice$") }
    val PLURAL_FIRST_WORD by lazy { Regex("""^(\w+)s\b""") }
    val MANA_SYMBOL by lazy { Regex("""\{[^}]+\}""") }
    val MANA_RUN by lazy { Regex("""(\{M\})+""") }

    // A legendary creature's gendered pronouns (msh)
    private const val HER_NOUNS = "power|toughness|owner's|controller's|mana|base"
    val HE_OR_SHE_IS by lazy { Regex("""\b(?:he|she)'s\b""") }
    val HE_OR_SHE_IS_CAPITAL by lazy { Regex("""\b(?:He|She)'s\b""") }
    val HE_OR_SHE by lazy { Regex("""\b(?:he|she)\b(?!-)""") }
    val HE_OR_SHE_CAPITAL by lazy { Regex("""\b(?:He|She)\b(?!-)""") }
    val HIS by lazy { Regex("""\bhis\b""") }
    val HIS_CAPITAL by lazy { Regex("""\bHis\b""") }
    val HIMSELF_OR_HERSELF by lazy { Regex("""\b(?:himself|herself)\b""") }
    val HIM by lazy { Regex("""\bhim\b""") }
    val HER_BEFORE_NOUN by lazy { Regex("""\bher (?=(?:$HER_NOUNS)\b)""") }
    val HER_BEFORE_NOUN_CAPITAL by lazy { Regex("""\bHer (?=(?:$HER_NOUNS)\b)""") }
    val HER by lazy { Regex("""\bher\b""") }
    val PRONOUN_IS_AT_START by lazy { Regex("""^(?:It|He|She)(?:'s| is) """) }

    // Single wordings
    val COUNTERS_REMOVED_THIS_WAY by lazy { Regex("""the number of \S+ counters removed this way""") }
    val ATTACK_WITH_TOTAL_POWER by lazy { Regex("""you attack with creatures with total power (\d+) or greater for the first time each turn""") }
    val BENDING_WORD by lazy { Regex("""(waterbend|earthbend|firebend|airbend)""") }
    val THAT_CREATURES_POWER_OR_TOUGHNESS by lazy { Regex("""that creature's (power|toughness)""") }
    val CAST_A_WORD_SPELL by lazy { Regex("""cast an? (\w+) spell""") }
    val ACTIVATE_ABILITIES_OF_SOURCES by lazy { Regex("""activate (?:an ability|abilities) of (?:an? )?(\w+) sources?( of the chosen type)?""") }
    val CAST_SPELLS by lazy { Regex("""cast (?:an? )?(.+?) spells?""") }
    val TARGET_SENTENCE by lazy { Regex("""Target (.+)\.""") }
    val EACH_OPPONENT_DRAWS by lazy { Regex("""[Ee]ach opponent draws (a|\w+) cards?\.""") }
    val IT_DEALS_DAMAGE_TO_EACH_OPPONENT by lazy { Regex("""It deals (\d+) damage to each opponent\.""") }
    val CREATE_TOKENS_WITH by lazy { Regex("""[Cc]reate (.+ creature tokens? with .+)\.""") }
    val WORD_AND_LEGENDARY_CREATURE by lazy { Regex("""([A-Z]\w+) and legendary creature you control""") }
}
