package arcanum.parser

/** Wordings first met in Wilds of Eldraine (WOE). */
internal object Woe {
    // woe chunk 01: Role tokens: "create a Monster Role token attached to target creature you control" (CR 111.10j–r, 303.4i)
    val CREATE_ATTACHED_TO = Regex("""[Cc]reate (an? .+? token) attached to (.+)\.""")
    // woe chunk 01: "For each creature your opponents control, create a Cursed Role token attached to that creature."
    // woe chunk 02: "… attached to that Rat."
    val FOR_EACH_CREATE_ATTACHED = Regex("""For each (.+?), create (an? .+? token) attached to (?:that [A-Za-z]+|it)\.""")
    // woe chunk 03: "You may sacrifice an artifact, enchantment, or token. If you don't, tap this creature." (CR 118.12: the optional payment's bound result)
    val MAY_PAY_IF_YOU_DONT = Regex("""[Yy]ou may (.+?)\. If you don't, (.+)\.""")
    // woe chunk 03: "create half X Food tokens, rounded up" (X as the permanent was cast, CR 107.3m; CR 107.1a rounding)
    val CREATE_HALF_X = Regex("""[Cc]reate half X (.+? token)s, rounded (up|down)\.""")
    // woe chunk 03: "up to one target creature gets -X/-X until end of turn" (X from the ability's "where X is", CR 107.3a)
    val TARGET_GETS_MINUS_X_UNTIL_EOT = Regex("""((?:[Uu]p to one )?[Tt]arget .+?) gets -X/-X until end of turn\.""")
    // woe chunk 04: "If this spell was bargained, destroy that creature instead.", "…, it deals twice X damage to that permanent instead.",
    // "…, instead it deals 3 damage to that permanent and you scry 1." (after damage to a target)
    val IF_INSTEAD_OF_DAMAGE = Regex("""If (.+?), (?:instead (.+)|(.+) instead)\.""")
    val DEALS_TO_THAT = Regex("""it deals (\d+|twice X) damage to that (?:creature|permanent)(?: and (.+))?""")
    val DESTROY_THAT = Regex("""destroy that (?:creature|permanent)""")
    // woe chunk 04: "If this spell was bargained, you may put one of those cards with mana value 4 or less onto the battlefield instead of putting
    // it into your hand." (after returning target cards to their owner's hand)
    val MAY_PUT_ONE_OF_THOSE_ONTO_BATTLEFIELD_INSTEAD = Regex("""If (.+?), you may put one of those cards with mana value (\d+) or less onto the battlefield instead of putting it into your hand\.""")
    // woe chunk 04: "Put two of them into your hand and the rest on the bottom of your library in a random order." (after looking at the top cards)
    val PUT_N_OF_THEM_INTO_HAND_REST_BOTTOM = Regex("""Put (\w+) of them into your hand and the rest on the bottom of your library in (any|a random) order\.""")
    // woe chunk 04: "put up to two of them back on top of your library in any order and the rest into your graveyard" (CR 401.4)
    val PUT_UP_TO_BACK_ON_TOP_REST_GRAVEYARD = Regex("""[Pp]ut up to (\w+) of them back on top of your library in any order and the rest into your graveyard\.""")
    // woe chunk 04: "Put the revealed cards onto the battlefield." / "… into your hand." (the cards revealed from among those looked at)
    val PUT_THE_REVEALED_CARDS = Regex("""[Pp]ut the revealed cards (onto the battlefield|into your hand)\.""")
    // woe chunk 04: "target land you control becomes a 3/3 Elemental creature with haste that's still a land" (for good, CR 611.2a; CR 205.1b)
    val BECOMES_THATS_STILL = Regex("""(.+?) becomes (an? .+?) that's still an? (\w+)\.""")
    // woe chunk 04: "If a permanent dealt damage by Torch the Tower would die this turn, exile it instead." (a later paragraph about the
    // spell's target, CR 614.1a)
    val IF_DEALT_DAMAGE_BY_WOULD_DIE_EXILE = Regex("""If a (?:creature|permanent) dealt damage by (.+?) would die this turn, exile it instead\.""")
    // woe chunk 04: "Search your library for a card, exile it face down, then shuffle. If this spell was bargained, you may cast the exiled card
    // without paying its mana cost if that spell's mana value is 4 or less. Put the exiled card into your hand if it wasn't cast this way."
    val SEARCH_EXILE_FACE_DOWN_MAY_CAST_FREE_OR_HAND = Regex("""Search your library for (an? .+?), exile it face down, then shuffle\. If (.+?), you may cast the exiled card without paying its mana cost if that spell's mana value is (\d+) or less\. Put the exiled card into your hand if it wasn't cast this way\.""")
    // woe chunk 05: "Whenever you tap an untapped creature an opponent controls" (the trigger event; only an untapped permanent can be tapped)
    // woe chunk 10: "you tap one or more untapped creatures your opponents control" (once for the batch)
    val YOU_TAP_AN_UNTAPPED = Regex("""you tap (an|one or more) untapped (.+)""")
    // woe chunk 05: "Whenever …, you may pay {1}. When you do, choose one —" (a mana cost paid on resolution, CR 118.12; the reflexive trigger's modes, CR 603.12)
    val MAY_PAY_WHEN_YOU_DO_CHOOSE = Regex("""(Whenever .+?), you may pay (\{[^}]+\})\. When you do, (.+)""")
    // woe chunk 05: 'Until end of turn, this land becomes a 2/1 blue and red Elemental creature with "…"' (It's still a land. is a separate sentence)
    val BECOMES_WITH_QUOTED = Regex("""(Until end of turn, )?(.+?) becomes (an? [^"]+?) with "(.+)"\.""")
    // woe chunk 05: "If the token is an Aura, untap Yenna, then scry 2." (after "Create a token that's a copy of it, …")
    val IF_THE_TOKEN_IS = Regex("""If the token is an? (\w+), (.+)\.""")
    // woe chunk 06: 'As long as …, Goddric is a Dragon with base power and toughness 4/4, flying, and "{R}: …."' (keywords, then a quoted ability)
    val IS_SUBTYPE_WITH_BASE_PT = Regex("""(?:As long as (.+?), )?(.+?) is an? ([A-Z]\w+(?: [A-Z]\w+)*) with base power and toughness (\d+)/(\d+)((?:, [a-z ]+?)*),? and "(.+)"\.?""")
    // woe chunk 07: "You may look at and play those cards for as long as they remain exiled." (after exiling them face down, CR 406.3)
    val MAY_LOOK_AT_AND_PLAY_WHILE_EXILED = Regex("""You may look at and play (those cards|that card) for as long as (?:they remain|it remains) exiled\.""")
    // woe chunk 07: "Counter target spell unless its controller pays {2} plus an additional {1} for each Faerie you control." (CR 118.12)
    val COUNTER_UNLESS_PAYS_PLUS_FOR_EACH = Regex("""Counter target (.+?) unless its controller pays \{(\d+)\} plus an additional \{(\d+)\} for each (.+)\.""")
    // woe chunk 07: "Create a Monster Role token attached to target creature you control. When you do, that creature fights …" (CR 603.12)
    val CREATE_ATTACHED_WHEN_YOU_DO = Regex("""(Create an? .+? token attached to .+?)\. When you do, (.+)""")
    // woe chunk 08: "If you tap a basic land for mana, it produces three times as much of that mana instead." (CR 106.12b)
    val TAP_FOR_MANA_PRODUCES_TIMES = Regex("""If you tap an? (.+?) for mana, it produces (twice|three times) as much of that mana instead\.""")
    // woe chunk 08: "exile a card from your hand" (you choose it; a hand is hidden, so no target)
    val EXILE_N_FROM_YOUR_HAND = Regex("""[Ee]xile (a|an|one|two|three) cards? from your hand\.""")
    // woe chunk 08: "you may cast it from your graveyard as an Adventure until the end of your next turn" (CR 715.3, 715.4)
    val MAY_CAST_FROM_GRAVEYARD_AS_ADVENTURE = Regex("""[Yy]ou may cast (it|that card) from your graveyard as an Adventure (this turn|until the end of your next turn)\.""")
    // woe chunk 08: "Choose a number between 0 and 10. Destroy all creatures with power greater than or equal to the chosen number."
    val CHOOSE_NUMBER_DESTROY_ALL_WITH_POWER = Regex("""Choose a number between (\d+) and (\d+)\. Destroy all (.+?) with (power|toughness|mana value) (greater than or equal to|less than or equal to|equal to) the chosen number\.""")
    // woe chunk 08: "Exile target creature or planeswalker. Search its controller's graveyard, hand, and library for any number of cards with the same
    // name as that permanent and exile them. That player shuffles, then draws a card for each card exiled from their hand this way."
    val EXILE_SEARCH_SAME_NAME_DRAW_FOR_HAND = Regex("""Exile target (.+?)\. Search its controller's graveyard, hand, and library for any number of cards with the same name as that (?:permanent|creature) and exile them\. That player shuffles, then draws a card for each card exiled from their hand this way\.""")
    // woe chunk 08: "Return target card you own in exile that has an Adventure to your hand."
    val RETURN_TARGET_IN_EXILE_TO_HAND = Regex("""[Rr]eturn target (.+? in exile.*?) to your hand\.""")
    // woe chunk 08: "Until end of turn, target creature you control has base power and toughness 4/4 and gains flying and hexproof." (layers 7b, 6)
    val UNTIL_EOT_BASE_PT_AND_GAINS = Regex("""Until end of turn, ((?:[Uu]p to one )?target .+?) has base power and toughness (\d+)/(\d+) and gains ([a-z ,]+)\.""")
    // woe chunk 09: "Other creatures you control that are enchanted by Auras you control have base power and toughness 4/4 and have flying."
    val HAVE_BASE_PT_AND_HAVE = Regex("""(.+?) have base power and toughness (\d+)/(\d+) and have ([a-z ,]+)\.""")
    // woe chunk 09: "Other creatures you control get +1/+0 for each Aura and Equipment attached to Kellan." (counted continuously, layer 7c)
    val GET_FOR_EACH_ATTACHED_TO = Regex("""(.+?) gets? \+(\d+)/\+(\d+) for each (Aura and Equipment|Aura|Equipment) attached to (.+)\.""")
    // woe chunk 09: "you may exile one or more creature cards from your graveyard. When you do, each other creature gets -X/-X until end of turn,
    // where X is the number of cards exiled this way." (CR 603.12)
    val MAY_EXILE_FROM_GRAVEYARD_WHEN_YOU_DO_GET_MINUS_X = Regex("""You may exile one or more (.+?) from your graveyard\. When you do, each (other )?(.+?) gets -X/-X until end of turn, where X is the number of cards exiled this way\.""")
    // woe chunk 09r: "That creature gets an additional -3/-3 until end of turn if you controlled a Faerie as you cast this spell."
    val GETS_ADDITIONAL_IF = Regex("""(.+?) gets an additional ([+-]\d+)/([+-]\d+) until end of turn if (.+)\.""")
    // woe chunk 09r: "At the beginning of the next combat phase this turn, target creature you control deals damage equal to its power to …"
    val AT_NEXT_COMBAT_THIS_TURN = Regex("""At the beginning of the next combat phase this turn, (.+)""")
    // woe chunk 09r: "For each of those cards, its owner may play it for as long as it remains exiled."
    val EACH_OWNER_MAY_PLAY_WHILE_EXILED = Regex("""(?:For each of those cards, its|Its) owner may (play|cast) (?:it|that card) for as long as it remains exiled\.""")
    // woe chunk 09r: "Whenever one or more nontoken creatures enter, if one or more of them entered from exile or was cast from exile, you draw a card."
    val ENTER_IF_ONE_ENTERED_OR_CAST_FROM = Regex("""Whenever one or more (.+?) enter, if one or more of them entered from (exile|a graveyard) or (?:was|were) cast from (?:exile|a graveyard), (.+?)\.( This ability triggers only once each turn\.)?""")
    // woe chunk 09r: 'you may have The Irencrag become a legendary Equipment artifact named Everflame, Heroes' Legacy. If you do, it gains equip {3}
    // and "Equipped creature gets +3/+3" and loses all other abilities.'
    val MAY_BECOME_NAMED_GAINS_LOSES_OTHERS = Regex("""[Yy]ou may have (.+?) become an? (legendary )?([A-Z][a-z]+(?: [A-Z][a-z]+)*) (artifact|enchantment|creature|land) named (.+?)\. If you do, it gains ([a-z]+(?: \{[^}]+\})*) and "(.+?)" and loses all other abilities\.""")
    // woe chunk 09r: "You may exile a creature card from among them."
    val MAY_EXILE_FROM_AMONG = Regex("""You may exile an? (.+?) from among them\.""")
    // woe chunk 10: "When this creature enters, if it isn't a token, create two tokens that are copies of it." (CR 707.2)
    val CREATE_COPIES_OF_IT = Regex("""[Cc]reate (\w+) tokens that are copies of it\.""")
    // woe chunk 10: "Sacrifice any number of artifacts, enchantments, and/or tokens", "…, then draw that many cards." (CR 701.21a)
    val SACRIFICE_ANY_NUMBER = Regex("""[Ss]acrifice any number of (.+?)(?:, then (.+))?\.""")
    // woe chunk 10: "Return that many creature cards from your graveyard to the battlefield." (chosen as it resolves, no target)
    val RETURN_THAT_MANY_FROM_GRAVEYARD = Regex("""[Rr]eturn that many (.+?) from your graveyard to (the battlefield|your hand)\.""")
    // woe chunk 10: "For each opponent who controlled a creature returned this way, you create two 1/1 blue Faerie creature tokens …"
    val FOR_EACH_OPPONENT_WHO_CONTROLLED_RETURNED = Regex("""For each opponent who controlled an? (.+?) returned this way, (.+)""")
    // woe chunk 10: "Until your next end step, you may play those cards." (the duration first)
    val UNTIL_YOU_MAY_PLAY = Regex("""Until (your next end step|end of turn|the end of your next turn), you may (play|cast) (.+)\.""")
    // woe chunk 10: "Each creature that's enchanted by an Aura you control can't attack you or planeswalkers you control." (CR 508.1c)
    val CANT_ATTACK_YOU_OR_PLANESWALKERS = Regex("""(.+?) can't attack you or planeswalkers you control\.""")
    // woe chunk 10: "Whenever an instant or sorcery spell you control that targets only a single creature deals damage to that creature, …"
    val SPELL_TARGETING_SINGLE_DEALS_DAMAGE_TO_IT = Regex("""Whenever (an? .+? spell you control) that targets only a single (.+?) deals damage to that (?:creature|permanent), (.+)""")
    // woe chunk 10: "you cast your first instant or sorcery spell each turn" (CR 603.2)
    val FIRST_SPELL_EACH_TURN = Regex("""you cast your first (.+?) spell each turn""")
    // woe chunk 10: the rest of a list of single words cut at its first comma ("blue, black, or red, put …")
    val LIST_GOES_ON = Regex("""^(?:[a-z]+(?:, [a-z]+)*,? )?(?:or|and) [a-z]+, """)
    // woe chunk 09: "Sacrifice all Reflections you control."
    val SACRIFICE_ALL_YOU_CONTROL = Regex("""[Ss]acrifice all (.+?) you control\.""")
}

/** Phrases first met in Wilds of Eldraine (WOE). */
internal object WoePhrases {
    // woe chunk 07: "permanent spells you cast that have an Adventure", "cards that have an Adventure" (CR 715.2a)
    private val THAT_ARE_TYPES_OR_ADVENTURE = Regex("""(.+?) that are ((?:[a-z]+ cards, )+)and/or have an Adventure""")
    private val WITH_MV_POWER_OR_TOUGHNESS_EQUAL_TO_CHOSEN = Regex("""(.+?) with mana value, power, or toughness equal to the chosen number""")
    private val OWN_IN_EXILE = Regex("""(cards?) you (own|don't own) in exile""")
    private val THAT_HAS_AN_ADVENTURE = Regex("""(.+?) that (?:has|have) an Adventure""")
    private val THAT_ARE_ENCHANTED_BY = Regex("""(.+?) that(?: are| is|'s) enchanted by (?:an? )?(.+)""")
    private val THATS_COLORS = Regex("""(.+?) that's ((?:white|blue|black|red|green)(?:,? (?:or )?(?:white|blue|black|red|green))*)""")
    private val BLOCKS_OR_BECOMES_BLOCKED_BY = Regex("""(.+?) blocks or becomes blocked by an? (.+)""")

    fun noun(phrase: String): arcanum.api.ObjectFilter? {
        // woe chunk 08: "card you own in exile", "card you don't own in exile" (cards in exile are owned, CR 108.3)
        OWN_IN_EXILE.matchEntire(phrase)?.let { m ->
            val base = Filters.noun(m.groupValues[1]) ?: return null
            val you = arcanum.api.PlayerFilter.You
            return Filters.all(base, arcanum.api.ObjectFilter.InZone(arcanum.api.Zone.Exile),
                arcanum.api.ObjectFilter.OwnedBy(if (m.groupValues[2] == "own") you else arcanum.api.PlayerFilter.Not(you)))
        }
        // woe chunk 08: "cards in your graveyard that are instant cards, sorcery cards, and/or have an Adventure" (any of them, CR 715.2a)
        THAT_ARE_TYPES_OR_ADVENTURE.matchEntire(phrase)?.let { m ->
            val base = Filters.noun(m.groupValues[1]) ?: return null
            val kinds = m.groupValues[2].trimEnd(',', ' ').split(", ").map { Filters.noun(it.removeSuffix(" cards")) ?: return null }
            return Filters.all(base, arcanum.api.ObjectFilter.Or(kinds + arcanum.api.ObjectFilter.HasAdventure))
        }
        // woe chunk 08: "a spell with mana value, power, or toughness equal to the chosen number" (the number this permanent chose as it
        // entered, CR 607.2d)
        WITH_MV_POWER_OR_TOUGHNESS_EQUAL_TO_CHOSEN.matchEntire(phrase)?.let { m ->
            val base = Filters.noun(m.groupValues[1]) ?: return null
            val n = arcanum.api.Value.Remembered("number")
            val eq = arcanum.api.Comparison.Equal
            return Filters.all(base, arcanum.api.ObjectFilter.Or(listOf(arcanum.api.ObjectFilter.ManaValue(eq, n), arcanum.api.ObjectFilter.Power(eq, n),
                arcanum.api.ObjectFilter.Toughness(eq, n))))
        }
        // woe chunk 09: "other creatures you control that are enchanted by Auras you control" (an Aura of that kind attached, CR 303.4)
        THAT_ARE_ENCHANTED_BY.matchEntire(phrase)?.let { m ->
            val base = Filters.noun(m.groupValues[1]) ?: return null
            val auras = Filters.noun(m.groupValues[2])?.takeIf { m.groupValues[2].startsWith("Aura") } ?: return null
            return Filters.all(base, arcanum.api.ObjectFilter.Attached(attachments = auras))
        }
        // woe chunk 10: "a spell that's white, blue, black, or red" (any of those colours, CR 105.2)
        THATS_COLORS.matchEntire(phrase)?.let { m ->
            val base = Filters.noun(m.groupValues[1].removeArticle()) ?: return null
            val colors = m.groupValues[2].split(Regex(""",? (?:or )?""")).map { arcanum.api.ObjectFilter.OfColor(Tokens.color(it) ?: return null) }
            return Filters.all(base, if (colors.size == 1) colors.single() else arcanum.api.ObjectFilter.Or(colors))
        }
        THAT_HAS_AN_ADVENTURE.matchEntire(phrase)?.let { m ->
            val base = Filters.noun(m.groupValues[1]) ?: Filters.noun(m.groupValues[1].removeSuffix("s")) ?: return null
            return Filters.all(base, arcanum.api.ObjectFilter.HasAdventure)
        }
        return null
    }

    /** Trigger events: [subject] reads the noun naming who acts. */
    fun events(text: String, subject: (String) -> arcanum.api.ObjectFilter?): List<arcanum.api.EventPattern>? {
        // woe chunk 10: "this creature blocks or becomes blocked by a creature" (once for each creature it blocks or that blocks it, CR 509.3a,
        // 509.3d)
        BLOCKS_OR_BECOMES_BLOCKED_BY.matchEntire(text)?.let { m ->
            val who = subject(m.groupValues[1]) ?: return null
            val other = Filters.noun(m.groupValues[2]) ?: return null
            return listOf(arcanum.api.EventPattern.Block(who, other, perAttacker = true), arcanum.api.EventPattern.BecomesBlocked(who, other, perBlocker = true))
        }
        return null
    }

    /** A number in a "where X is" ([event]: the ability's trigger event). */
    fun value(text: String, event: arcanum.api.EventPattern?): arcanum.api.Value? {
        // woe chunk 09: "When this creature dies, …, where X is the amount of damage dealt to it this turn" (the creature as it last existed on
        // the battlefield, CR 603.10a; all damage dealt to it this turn, CR 120.3)
        if (text == "the amount of damage dealt to it this turn") {
            val died = event as? arcanum.api.EventPattern.ZoneChange ?: return null
            if (died.from != arcanum.api.Zone.Battlefield) return null
            val it = arcanum.api.Objects.EventObject(arcanum.api.EventRole.Before)
            return arcanum.api.Value.EventsThisTurn(arcanum.api.EventPattern.Damage(arcanum.api.ObjectFilter.Any,
                arcanum.api.RecipientFilter(objects = arcanum.api.ObjectFilter.Is(it))), arcanum.api.Measure.Amount)
        }
        return null
    }

    // woe chunk 07: "you own a card in exile that has an Adventure" (CR 715.2a; cards in exile are owned, CR 108.3)
    private val LIFE_TOTAL_OR_LESS = Regex("""your life total is (\d+) or less""")
    private val A_NOUN_IS_ATTACKING = Regex("""an? ([A-Z]\w+|creature) is attacking""")
    private val YOU_OWN_IN_EXILE = Regex("""you own an? (\w+) in exile(?: (that .+))?""")

    private val AS_YOU_CAST_THIS_SPELL = Regex("""(.+) as you cast this spell""")

    fun condition(text: String, self: Set<String>): arcanum.api.Condition? {
        // woe chunk 09r: "you controlled a Faerie as you cast this spell" (answered as casting finishes, CR 601.2i)
        AS_YOU_CAST_THIS_SPELL.matchEntire(text)?.let { m ->
            val now = m.groupValues[1].replace(Regex("""^you controlled """), "you control ")
            return arcanum.api.Condition.AsCast(Conditions.condition(now, self) ?: return null)
        }
        // woe chunk 08: "your life total is 5 or less"
        LIFE_TOTAL_OR_LESS.matchEntire(text)?.let { m ->
            return arcanum.api.Condition.Compare(arcanum.api.Value.LifeOf(arcanum.api.Players.You), arcanum.api.Comparison.AtMost, arcanum.api.Value.Fixed(m.groupValues[1].toInt()))
        }
        // woe chunk 08: "a Rat is attacking" (any player's attacking Rat, CR 506.4)
        A_NOUN_IS_ATTACKING.matchEntire(text)?.let { m ->
            return arcanum.api.Condition.Exists(Filters.noun("attacking " + m.groupValues[1]) ?: return null, atLeast = 1)
        }
        YOU_OWN_IN_EXILE.matchEntire(text)?.let { m ->
            val noun = Filters.noun(listOf(m.groupValues[1], m.groupValues[2]).filter { it.isNotEmpty() }.joinToString(" ")) ?: return null
            return arcanum.api.Condition.Exists(Filters.all(noun, arcanum.api.ObjectFilter.InZone(arcanum.api.Zone.Exile),
                arcanum.api.ObjectFilter.OwnedBy(arcanum.api.PlayerFilter.You)), atLeast = 1)
        }
        return null
    }
}
