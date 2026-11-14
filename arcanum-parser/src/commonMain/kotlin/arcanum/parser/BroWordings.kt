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
    val EXILE_FROM_TOP_OR_BOTTOM = Regex("""[Ee]xile (that many|\w+) cards from the (bottom|top) of your library\.""") // bro chunk 08: also the top
    // bro chunk 06: Su-Chi Cave Guard
    // bro chunk 06: Recommission
    val IF_A_X_ENTERS_THIS_WAY_WITH_COUNTER = Regex("""If an? (.+?) enters this way, it enters with an additional (\S+) counter on it\.""")
    // bro chunk 06: Meticulous Excavation
    val IF_IT_HAS_KEYWORD_INSTEAD_EXILE_RETURN = Regex("""If it has ([a-z]+), instead exile it, then return that card to its owner's hand\.""")
    // bro chunk 06: Soul Partition
    val SPELL_CAST_BY_OPPONENT_THIS_WAY_COSTS_MORE = Regex("""A spell cast by an opponent this way costs ((?:\{[^}]+\})+) more to cast\.""")
    val COUNT_OF_SYMBOL = Regex("""(${Numbers.WORD_PATTERN}) (\{[WUBRGC]\})""")
    // bro chunk 07: Bladecoil Serpent, Clay Champion
    val FOR_EACH_SYMBOLS_SPENT = Regex("""[Ff]or each ((?:\{[WUBRGC]\})+) spent to cast (it|this creature|this spell|this permanent), (.+)""")
    // bro chunk 07: Clay Champion
    val PUT_COUNTER_ON_EACH_OF_THEM = Regex("""Put an? (\S+) counter on each of them\.""")
    // bro chunk 07: Gix's Command
    val RETURN_UP_TO_FROM_GRAVEYARD = Regex("""Return up to (\w+) (?!target )(.+?) from your graveyard to (your hand|the battlefield)\.""")
    // bro chunk 07: Mishra's Command
    val THEY_MAY_DISCARD_UP_TO = Regex("""They may discard up to (\w+) cards\.""")
    val THEN_THEY_DRAW_FOR_EACH_DISCARDED = Regex("""Then they draw a card for each card discarded this way\.""")
    // bro chunk 07: Teferi, Temporal Pilgrim
    val TARGET_CHOOSES_THEY_CONTROL_RETURNS = Regex("""Target (opponent|player) chooses an? (.+?) they control and returns it to its owner's hand\.""")
    val THEN_THEY_SHUFFLE_EACH_THEY_CONTROL = Regex("""Then they shuffle each (.+?) they control into its owner's library\.""")
    // bro chunk 07: Hurkyl, Master Wizard
    // bro chunk 08
    val DEALS_TO_PLAYER_AND_EACH_CREATURE_THEY_CONTROL = Regex("""(.+?) deals (\d+) damage to (that player|target player|target opponent) and (\d+) damage to each creature (?:they control|that player controls)\.""")
    val DRAW_EQUAL_TO_PROPERTY_OF_TARGET = Regex("""Draw cards equal to the (mana value|power|toughness) of target (.+)\.""")
    val CHOOSE_DISCARDED_BY_MANA_VALUES = Regex("""You may choose an? (.+?) card with mana value (\d+) you discarded this way, then do the same for (.+?) cards with mana values (\d+) and (\d+)\.""")
    val TWO_FACE_DOWN_PILES_OPPONENT_CHOOSES = Regex("""Exile the top (\w+) cards of your library in a face-down pile, then exile the top \1 cards of your library in another face-down pile\. Look at the cards in each pile, then turn a pile of your choice face up\. An opponent chooses one of those piles\. Put that pile into your hand and the other into your graveyard\.(?: (.+))?""")
    val AS_ENTERS_MAY_REVEAL_TAPPED_UNLESS = Regex("""As (.+?) enters, you may reveal an? (.+?) from your hand\. (.+?) enters tapped unless you revealed an? (.+?) this way or (.+)\.""")
    val LOSES_KEYWORD_AND_GAINS_QUOTED = Regex("""(.+?) loses ([a-z]+) and gains "(.+?)"\.?""")
    val BECOMES_COLOR_AND_BASE_PT = Regex("""(.+?) becomes (white|blue|black|red|green) and has base power and toughness (\d+)/(\d+)\.""")
    val EXILE_THIS_CARD_FROM_GRAVEYARD = Regex("""[Ee]xile (this card|it) from your graveyard\.""")
    val THEY_DRAW = Regex("""They draw (a card|\w+ cards)\.""")
    val RETURN_THOSE_CARDS_TO_BATTLEFIELD = Regex("""Return (those cards|them) to the battlefield\.""")
    val ITS_CONTROLLER_MAY_PAY = Regex("""Its controller may (pay .+?)\.""")
    val EXILE_TOP_OF_TARGET_LIBRARY = Regex("""Exile the top (X|\w+) cards of target (opponent|player)'s library\.""")
    val MAY_PLAY_LANDS_CAST_SPELLS_EXILED_FREE = Regex("""You may play lands and cast spells from among (?:cards exiled this way|them|those cards) without paying their mana costs\.""")
    val FOR_EACH_TYPE_AMONG_CAST_MAY_PUT = Regex("""For each card type among (.+?) you've cast this turn, you may put a card of that type from among the revealed cards into your hand\.""")
    val EACH_PLAYER_REVEALS_EQUAL_PUTS_ONTO_REST_GRAVEYARD = Regex("""Each player reveals a number of cards from the top of their library equal to the number of (.+?) they control, puts all (.+?) they revealed this way onto the battlefield, and puts the rest into their graveyard\.""")
    /** The card types a spell can have (CR 205.2a; lands and dungeons are never cast). */
    val SPELL_CARD_TYPES: List<arcanum.api.CardType> = listOf(arcanum.api.CardType.Artifact, arcanum.api.CardType.Battle, arcanum.api.CardType.Creature, arcanum.api.CardType.Enchantment,
        arcanum.api.CardType.Instant, arcanum.api.CardType.Kindred, arcanum.api.CardType.Planeswalker, arcanum.api.CardType.Sorcery)

    /** "{G}{G}" spent to cast [of]: how many times that pair of one colour was spent, half that colour's mana rounded down
     * (CR 601.2h; a permanent keeps it from the spell it was, CR 400.7d). Null for other symbol groups. */
    fun pairsSpent(symbols: String, of: arcanum.api.Objects): arcanum.api.Value? {
        val each = Regex("""\{([WUBRGC])\}""").findAll(symbols).map { it.groupValues[1] }.toList()
        if (each.size != 2 || each[0] != each[1]) return null
        val color = arcanum.api.Color.of(each[0].single()) ?: return null
        return arcanum.api.Value.Half(arcanum.api.Value.ManaSpent(color = color, of = of), roundUp = false)
    }
}

/** Conditions of The Brothers' War (BRO) wordings. */
internal object BroPhrases {
    private val COLORS_SPENT = Regex("""(\w+) or more colors of mana were spent to cast (this spell|this creature|this permanent)""")
    private val YOU_CONTROL_NAMED_AND = Regex("""you control (creatures|artifacts|permanents) named (.+?) and (.+)""")
    private val AN_OPPONENT_CONTROLS_N_OR_MORE = Regex("""an opponent controls (\w+) or more (.+)""")
    private val SELF_HAS_KEYWORD = Regex("""(.+?) (has|doesn't have) ([a-z]+)""")
    private val SELF_IS_COLOR = Regex("""(.+?) is (white|blue|black|red|green)""")
    private val PLAYED_FROM_EXILE = Regex("""you (didn't play|played) a card from exile this turn""")
    private val MANA_SPENT_GREATER_THAN_POWER = Regex("""the amount of mana spent to cast that spell is greater than (.+?)'s power""")

    fun condition(text: String, self: Set<String> = emptySet()): arcanum.api.Condition? {
        // bro chunk 08: Surge Engine, "this creature doesn't have defender", "this creature is blue" (as it is now, CR 602.5b)
        SELF_HAS_KEYWORD.matchEntire(text)?.takeIf { it.groupValues[1] in self || it.groupValues[1] == "this creature" }?.let { m ->
            val kind = OracleParser.keyword(m.groupValues[3])?.kind ?: return@let
            val has = arcanum.api.Condition.ObjectMatches(arcanum.api.Objects.Self, arcanum.api.ObjectFilter.HasKeyword(kind))
            return if (m.groupValues[2] == "has") has else arcanum.api.Condition.Not(has)
        }
        SELF_IS_COLOR.matchEntire(text)?.takeIf { it.groupValues[1] in self || it.groupValues[1] == "this creature" }?.let { m ->
            val color = arcanum.api.Color.all.firstOrNull { it.toString().equals(m.groupValues[2], ignoreCase = true) } ?: return@let
            return arcanum.api.Condition.ObjectMatches(arcanum.api.Objects.Self, arcanum.api.ObjectFilter.OfColor(color))
        }
        // bro chunk 08: Visions of Phyrexia, "you didn't play a card from exile this turn" (playing a card: playing a land or casting a spell,
        // CR 305.1, 601.2a)
        PLAYED_FROM_EXILE.matchEntire(text)?.let { m ->
            val played = arcanum.api.Condition.Or(listOf(
                arcanum.api.Condition.HappenedThisTurn(arcanum.api.EventPattern.PlayLand(arcanum.api.ObjectFilter.Type(arcanum.api.CardType.Land),
                    arcanum.api.PlayerFilter.You, from = arcanum.api.Zone.Exile), atLeast = 1, measure = arcanum.api.Measure.Events),
                arcanum.api.Condition.HappenedThisTurn(arcanum.api.EventPattern.Cast(arcanum.api.ObjectFilter.Any, arcanum.api.PlayerFilter.You,
                    from = arcanum.api.Zone.Exile), atLeast = 1, measure = arcanum.api.Measure.Events)))
            return if (m.groupValues[1] == "didn't play") arcanum.api.Condition.Not(played) else played
        }
        // bro chunk 07: Razorlash Transmogrant, "an opponent controls four or more nonbasic lands" (one opponent alone, CR 102.3)
        AN_OPPONENT_CONTROLS_N_OR_MORE.matchEntire(text)?.let { m ->
            val noun = Filters.noun(m.groupValues[2]) ?: return null
            val n = Numbers.count(m.groupValues[1]) ?: return null
            val theirs = Filters.all(noun, arcanum.api.ObjectFilter.ControlledBy(arcanum.api.PlayerFilter.Is(arcanum.api.Players.Tested)))
            return arcanum.api.Condition.PlayerExists(arcanum.api.PlayerFilter.And(listOf(arcanum.api.PlayerFilter.Opponent,
                arcanum.api.PlayerFilter.Where(arcanum.api.Condition.Exists(theirs, atLeast = n)))), atLeast = 1)
        }
        // bro chunk 07: Liberator, Urza's Battlethopter, "the amount of mana spent to cast that spell is greater than Liberator's power" (in a
        // cast trigger, CR 601.2h)
        MANA_SPENT_GREATER_THAN_POWER.matchEntire(text)?.takeIf { it.groupValues[1] in self || it.groupValues[1] == "this creature" }?.let {
            return arcanum.api.Condition.Compare(arcanum.api.Value.ManaSpent(of = arcanum.api.Objects.EventObject(arcanum.api.EventRole.Object)),
                arcanum.api.Comparison.Greater, arcanum.api.Value.Of(arcanum.api.NumericProperty.Power, arcanum.api.Objects.Self, arcanum.api.Combine.Single))
        }
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
