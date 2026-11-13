package arcanum.parser

/** Wordings first met in The Brothers' War (BRO). */
internal object Bro {
    // bro chunk 04: Mishra's Domination
    val AS_LONG_AS_IT_OTHERWISE = Regex("""As long as (.+?), it ((?:gets|has|can't) .+?)\. Otherwise, it ((?:gets|has|can't) .+)\.""")
    val YOU_CONTROL_ATTACHED = Regex("""you control (enchanted|equipped) creature""")
    // bro chunk 04: Mishra, Claimed by Gix
    val MELD_IF_ATTACKING = Regex("""If (.+?) and an? (creature|land|artifact) named (.+?) are attacking, and you both own and control them, exile them, then meld them into (.+)\.""")
    // bro chunk 04: Urza, Planeswalker
    val LOYALTY_TWICE = Regex("""You may activate the loyalty abilities of (.+?) twice each turn rather than only once\.""")
    // bro chunk 05: The Temporal Anchor
    val SCRIED_TO_BOTTOM = Regex("""(you|a player|an opponent) chooses? to put one or more cards on the bottom of (?:your|their) library while scrying""")
    val EXILE_FROM_BOTTOM = Regex("""[Ee]xile (that many|\w+) cards from the bottom of your library\.""")
    // bro chunk 06: Su-Chi Cave Guard
    // bro chunk 06: Recommission
    val IF_A_X_ENTERS_THIS_WAY_WITH_COUNTER = Regex("""If an? (.+?) enters this way, it enters with an additional (\S+) counter on it\.""")
    // bro chunk 06: Meticulous Excavation
    val IF_IT_HAS_KEYWORD_INSTEAD_EXILE_RETURN = Regex("""If it has ([a-z]+), instead exile it, then return that card to its owner's hand\.""")
    // bro chunk 06: Soul Partition
    val SPELL_CAST_BY_OPPONENT_THIS_WAY_COSTS_MORE = Regex("""A spell cast by an opponent this way costs ((?:\{[^}]+\})+) more to cast\.""")
    val COUNT_OF_SYMBOL = Regex("""(${Numbers.WORD_PATTERN}) (\{[WUBRGC]\})""")
}

/** Conditions of The Brothers' War (BRO) wordings. */
internal object BroPhrases {
    private val COLORS_SPENT = Regex("""(\w+) or more colors of mana were spent to cast (this spell|this creature|this permanent)""")
    private val YOU_CONTROL_NAMED_AND = Regex("""you control (creatures|artifacts|permanents) named (.+?) and (.+)""")

    fun condition(text: String): arcanum.api.Condition? {
        // bro chunk 06: Steel Exemplar, "two or more colors of mana were spent to cast this creature" (CR 601.2h; a permanent keeps it, CR 400.7d)
        COLORS_SPENT.matchEntire(text)?.let { m ->
            return arcanum.api.Condition.Compare(arcanum.api.Value.ManaSpent(distinctColors = true, of = arcanum.api.Objects.Self),
                arcanum.api.Comparison.AtLeast, arcanum.api.Value.Fixed(Numbers.count(m.groupValues[1]) ?: return null))
        }
        // bro chunk 06: Mine Worker, "you control creatures named Power Plant Worker and Tower Worker" (one of each, CR 201.2)
        YOU_CONTROL_NAMED_AND.matchEntire(text)?.let { m ->
            val kind = Filters.noun(m.groupValues[1].removeSuffix("s")) ?: return null
            return arcanum.api.Condition.And(listOf(m.groupValues[2], m.groupValues[3]).map { name ->
                arcanum.api.Condition.Exists(Filters.all(kind, arcanum.api.ObjectFilter.Named(name), arcanum.api.ObjectFilter.ControlledBy(arcanum.api.PlayerFilter.You)), atLeast = 1)
            })
        }
        return null
    }
}
