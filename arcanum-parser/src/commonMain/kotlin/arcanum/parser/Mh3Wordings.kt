package arcanum.parser

/** Regular expressions of Modern Horizons 3 (mh3) wordings read in [Sentences]. */
internal object Mh3 {
    // mh3 chunk 01
    /** The rest of a list after a comma ("5, or 6, create …"): the list's words, then what follows. */
    val LIST_CONTINUES = Regex("""((?:\w+, )*(?:or|and) \w+), (.+)""")
    /** "When you cast this spell and when this creature dies, …": two trigger conditions of one effect (CR 603.1). */
    val AND_WHEN = Regex("""((?:When|Whenever) .+?) and when (.+? (?:dies|leaves the battlefield|enters)), (.+)""")
    /** "If this creature was kicked, it enters with two +1/+1 counters and a trample counter on it and with haste." */
    val IF_IT_ENTERS_WITH_COUNTERS_AND_WITH = Regex("""If (.+), it enters with (.+?) on it(?: and with (.+?))?\.""")
    val COUNTER_IN_LIST = Regex("""(a|an|one|two|three|four|five) (\S+) counters?""")
    /** "Exile up to one target …. If you do, …", "Exile target …. You may cast that card …": then a sentence about the exiled card. */
    val EXILE_THEN = Regex("""(Exile (?:up to one )?target [^.]+\.) ((?:If you do, |You may cast that card ).+)""")
    /** "As this creature enters, it becomes your choice of a 3/3 creature with flying, a 2/5 creature with vigilance, or …" (CR 614.12) */
    val AS_ENTERS_BECOMES_YOUR_CHOICE = Regex("""As (.+) enters, it becomes your choice of (.+)\.""")
    /** The items of "A, B, or C". */
    val OR_LIST = Regex(""",? or |, """)
    val CHOICE_PT_CREATURE_WITH = Regex("""an? (\d+)/(\d+) creature with (.+)""")
    /** "You may cast that card for as long as it remains exiled, and mana of any type can be spent to cast it." (blb), "…, and you may spend
     * colorless mana as though it were mana of any color to cast that spell." (CR 609.4b) */
    val MAY_CAST_REMAINS_EXILED_SPEND = Regex("""You may cast that card for as long as it remains exiled, and (mana of any type can be spent to cast it|you may spend colorless mana as though it were mana of any color to cast that spell)\.""")

    // mh3 chunk 02: energy (CR 107.14, 122.1)
    /** "Pay {E}{E}", "Pay X {E}": paying energy counters (CR 107.14). */
    val PAY_ENERGY = Regex("""Pay ((?:\{E\})+|X \{E\}|\w+ \{E\})""") // mh3 chunk 08: "Pay six {E}"
    /** "you get {E}{E}", "you get X {E}", "you get an amount of {E} equal to …": energy counters for you (CR 122.1). */
    val GET_ENERGY = Regex("""(?:[Yy]ou )?[Gg]et ((?:\{E\})+|X \{E\}|an amount of \{E\} equal to (.+?))\.""")
    /** "you may pay any amount of {E}": any number from 0 up to the energy you have (CR 107.1b, 107.14). */
    val PAY_ANY_AMOUNT_OF_ENERGY = Regex("""(?:You may p|P)ay any amount of \{E\}\.""")
    /** What "paid this way" counts, read as the amount just paid. */
    val ENERGY_PAID_THIS_WAY = Regex("""(?:the amount of )?\{E\} paid this way""")
    /** "Counter that spell unless its controller pays {1} for each {E} paid this way." (CR 118.12a) */
    val COUNTER_THAT_SPELL_UNLESS_PAYS_FOR_EACH = Regex("""Counter (?:that|the chosen) spell unless its controller pays (\{\d+\}) for each (.+)\.""")
    /** "Destroy each artifact, creature, and enchantment with mana value less than or equal to the amount of {E} paid this way." */
    val DESTROY_EACH_MV_AT_MOST = Regex("""Destroy each (.+?) with mana value less than or equal to (.+)\.""")
    /** "You may cast creature spells with mana value 3 or less by paying {E} rather than paying their mana costs. If you cast a spell this way,
     * you may cast it as though it had flash." (CR 118.9, 601.3c) */
    val MAY_CAST_SPELLS_BY_PAYING_RATHER = Regex("""You may cast (.+? spells(?: with .+?)?) by paying (.+?) rather than paying their mana costs\.( If you cast a spell this way, you may cast it as though it had flash\.)?""")
    val SPELLS_WORD = Regex("""\bspells\b""")
    /** "You may cast that card by paying an amount of {E} equal to its mana value rather than paying its mana cost." (CR 118.9, 608.2g) */
    val MAY_CAST_IT_BY_PAYING_RATHER = Regex("""You may cast (?:that card|it) by paying (.+?) rather than paying its mana cost\.""")
    val AMOUNT_OF_ENERGY_EQUAL_TO = Regex("""an amount of \{E\} equal to (.+)""")
    /** "Exile the top card of your library. You may play it until you exile another card with this artifact." (CR 607.2a) */
    val EXILE_TOP_MAY_PLAY_UNTIL_ANOTHER = Regex("""Exile the top card of your library\. You may play (?:it|that card) until you exile another card with (.+?)\.""")
    /** "It becomes an Angel in addition to its other types." (no duration: for as long as it stays, CR 611.2a) */
    val IT_BECOMES_SUBTYPE_IN_ADDITION = Regex("""It becomes an? ([A-Z]\w+) in addition to its other types\.""")
    /** "At the beginning of combat on your turn, you may pay {E}{E}. When you do, choose one —": a cost on resolution, then a modal reflexive
     * trigger (CR 118.12, 603.12) */
    val YOU_MAY_WHEN_YOU_DO_CHOOSE = Regex("""you may (.+?)\. When you do, (choose .+)""")
    /** "If seven or more {E} was paid this way, you may play cards you own exiled this way until the end of your next turn." */
    val IF_N_OR_MORE_PAID_MAY_PLAY_EXILED = Regex("""If (\w+) or more \{E\} was paid this way, you may play cards you own exiled this way until the end of your next turn\.""")
    /** "Galvanic Discharge deals that much damage to that permanent." after "Choose target creature or planeswalker." */
    val DEALS_THAT_MUCH_TO_THAT = Regex("""(.+?) deals that much damage to that (permanent|creature|planeswalker)\.""")
    /** "Then if you cast it from your hand, exile cards …. You may cast that card …": the condition holds for both sentences (CR 608.2c). */
    // mh3 chunk 03
    /** "Then you may pay an amount of {E} equal to that card's mana value. If you do, …" (CR 107.14, 118.12) */
    val MAY_PAY_ENERGY_EQUAL_TO_IF_YOU_DO = Regex("""(?:Then )?[Yy]ou may pay an amount of \{E\} equal to (.+?)\. If you do, (.+)""")
    /** "Return it from your graveyard to the battlefield.", "… to your hand." about the card "it" names (a target chosen before). */
    val RETURN_IT_FROM_YOUR_GRAVEYARD = Regex("""[Rr]eturn (?:it|that card) from your graveyard to (the battlefield|your hand)\.""")
    val THEN_IF_THEN_MAY_CAST_THAT_CARD = Regex("""(.+?\. )?Then if (.+?), ([^.]+\.) (You may cast that card [^.]+\.)""")

    // mh3 chunk 04: adapt, modified, counters
    /** "the number of counters on permanents you control": the counters of all kinds on all of them (CR 122.1). */
    val NUMBER_OF_COUNTERS_ON = Regex("""the number of counters on ((?:permanents|creatures|artifacts|lands|enchantments) .+)""")
    /** "Put an additional +1/+1 counter on it.": "additional" only says it comes on top of the counters the event put. */
    val PUT_ADDITIONAL_COUNTER = Regex("""[Pp]ut an additional (\S+) counter on (.+)\.""")
    /** "Put a creature card exiled with this creature onto the battlefield under your control with a finality counter on it." */
    val PUT_A_EXILED_WITH_ONTO_BATTLEFIELD = Regex("""Put an? (.+?) exiled with (.+?) onto the battlefield under your control(?: with an? (\S+) counter on it)?\.""")
    /** "It gains haste.": keywords for good (the sentence's own duration words make it another wording). */
    val IT_GAINS = Regex("""It gains ([a-z ,]+)\.""")

    // mh3 chunk 05: spells and their costs
    /** "Creature spells of the chosen type cost {2} less to cast.", "Colorless spells you cast with mana value 7 or greater cost {1} less …" */
    val SPELLS_COST = Regex("""(.+? spells(?: .+?)?) cost (\{\d+\}) (more|less) to cast\.""")
    /** "this creature's power" (as the normalizer leaves it, the name or "this creature") */
    val SELF_POWER = Regex("""(.+)'s power""")
    /** "Enchantment spells you cast have affinity for Auras." (CR 702.41a) */
    val SPELLS_HAVE_AFFINITY = Regex("""(.+? spells you cast) have affinity for (\w+)\.""")
    /** "Target opponent exiles the top three cards of their library." */
    val TARGET_OPPONENT_EXILES_TOP = Regex("""Target opponent exiles the top (\w+) cards? of their library\.""")
    /** "… you may play those cards. If you cast a spell this way, mana of any type can be spent to cast it." (CR 609.4b) */
    val IF_CAST_THIS_WAY_ANY_TYPE = Regex("""(.+\.) If you cast a spell this way, mana of any type can be spent to cast it\.""")
    // mh3 chunk 06: "you may pay {1}{G} if this permanent is attached to a creature you control. If you do, create a token that's a copy of
    // that creature. If you didn't create a token this way, create a 1/1 green Insect creature token."
    val MAY_PAY_IF_ATTACHED_IF_YOU_DO_ELSE_TOKEN = Regex("""[Yy]ou may pay ((?:\{[^}]+\})+) if (.+?) is attached to (an? .+?)\. If you do, ([^.]+\.) If you didn't create a token this way, ([^.]+\.)""")

    // mh3 chunk 07
    /** "Target creature you control connives X", "it connives two": connive N (CR 701.50d). */
    val CONNIVES_N = Regex("""(.+?) connives (X|one|two|three|four|five)\.""")
    /** "Until end of turn, double target creature's power and it gains first strike.": two effects, both until end of turn. */
    val UNTIL_EOT_AND_IT_GAINS = Regex("""Until end of turn, (.+?) and it (gains [a-z ,]+)\.""")
    /** "target opponent exiles the top card of their library, a card at random from their graveyard, and a card at random from their hand." */
    val TARGET_OPPONENT_EXILES_LIST = Regex("""[Tt]arget opponent exiles ((?:[^,]+, )+and [^,]+)\.""")
    val LIST_ITEMS = Regex(""",(?: and)? """)
    /** "You may cast a spell from among cards exiled this way without paying its mana cost." (CR 608.2g) */
    val MAY_CAST_FROM_AMONG_EXILED_THIS_WAY = Regex("""You may cast a spell from among (?:the )?cards exiled this way without paying its mana cost\.""")
    /** "As long as another player controls enchanted creature, it can't attack or block. Otherwise, this Aura has umbra armor." */
    val AS_LONG_AS_CONTROLS_IT_OTHERWISE = Regex("""As long as (another player|an opponent|you) controls? (enchanted|equipped) creature, it ([^.]+)\. Otherwise, (.+\.)""")
    /** "double the number of each kind of counter on it." (CR 701.10e) */
    val DOUBLE_EACH_KIND_ON_IT = Regex("""[Dd]ouble the number of each kind of counter on (?:it|that creature)\.""")
    /** "Then for each nontoken permanent attached to it, create a token that's a copy of that permanent attached to that creature." */
    val FOR_EACH_ATTACHED_CREATE_COPY_ATTACHED = Regex("""(?:Then )?[Ff]or each (.+?) attached to (?:it|that creature), create a token that's a copy of that permanent attached to (?:it|that creature)\.""")

    // mh3 chunk 08
    /** "Discard any number of cards, then investigate twice for each card discarded this way." (CR 701.16a) */
    val DISCARD_ANY_NUMBER_INVESTIGATE_FOR_EACH = Regex("""[Dd]iscard any number of cards, then investigate (once|twice|\w+ times)? ?for each card discarded this way\.""")
    /** "Choose target creature with mana value 3 or less. If this spell was kicked, instead choose target creature. Exile the chosen creature, …" */
    val CHOOSE_TARGET_KICKED_INSTEAD = Regex("""Choose target (.+?)\. If this spell was kicked, instead choose target (.+?)\. (.+)""")
    val THE_CHOSEN = Regex("""\bthe chosen (?:creature|card|permanent)\b""")
    /** "it was kicked with its {G} kicker" (CR 702.33f) */
    val KICKED_WITH_ITS = Regex("""(.+) was kicked with its ((?:\{[^}]+\})+) kicker""")
    /** "… exchange control of this creature and target creature an opponent controls. If you do, …" (CR 701.12b) */
    val EXCHANGE_IF_YOU_DO = Regex("""(.*[Ee]xchange control of [^.]+\.) If you do, (.+)""")
    /** "Sacrifice that creature unless you pay an amount of {E} equal to its mana value." */
    val SACRIFICE_UNLESS_PAY_ENERGY_EQUAL = Regex("""[Ss]acrifice (?:that creature|it) unless you pay an amount of \{E\} equal to its (mana value|power)\.""")

    // mh3 chunk 09
    /** "it's a 1/1 black and green Insect" (a copy's exceptions, CR 707.9b) */
    val COPY_EXCEPT_PT_COLORS_SUBTYPE = Regex("""it's an? (\d+)/(\d+) ((?:white|blue|black|red|green)(?:(?:, | and )(?:white|blue|black|red|green))*) ([A-Z]\w+)""")
    /** "Target player creates X 0/1 colorless Eldrazi Spawn creature tokens with …" (X announced, CR 107.3a) */
    val TARGET_PLAYER_CREATES_X = Regex("""Target player creates X (.+)""")
    /** "Create a 1/1 … Insect creature token, then mill two cards. Put a deathtouch counter on the token if a black card was milled this way." */
    val CREATE_THEN_MILL_COUNTER_ON_TOKEN_IF_MILLED = Regex("""(Create an? [^.]+? token), then mill (\w+) cards\. Put an? (\S+) counter on the token if an? (.+?) card was milled this way\.""")
    /** "Ral enters with an additional loyalty counter on him for each instant and sorcery spell you've cast this turn." (CR 614.1c, 306.5b) */
    val ENTERS_WITH_ADDITIONAL_FOR_EACH = Regex("""(.+?) enters with an additional (\S+) counter on (?:it|him|her|them) for each (.+?) (spell|creature|card|permanent|land) (.+)\.""")
    /** "Put a lifelink counter on it if you control a white permanent other than that creature or Sorin." */
    val IF_YOU_CONTROL_OTHER_THAN_THAT_OR_SELF = Regex("""(.+?) if you control an? (.+?) other than that (?:creature|permanent) or (.+)\.""")
    /** "Up to two target players each manifest two cards from their hands. For each card manifested this way, you draw a card." (CR 701.40a) */
    val TARGET_PLAYERS_EACH_MANIFEST_FROM_HANDS = Regex("""(?:Up to (\w+)|(\w+)) target players each manifest (\w+) cards? from their hands\.(?: For each card manifested this way, you draw a card\.)?""")
    /** "Each opponent chooses an artifact, a creature, an enchantment, and a planeswalker from among the nonland permanents they control, then
     * sacrifices the rest." */
    val CHOOSES_TYPES_FROM_AMONG_SACRIFICES_REST = Regex("""Each (opponent|player) chooses ((?:an? \w+, )+and an? \w+) from among the (.+?) they control, then sacrifices the rest\.""")
    /** "Mill three cards. Then you may pay {1} and 3 life. If you do, put a card from among those cards into your hand." */
    val MILL_THEN_MAY_PAY_PUT_FROM_AMONG = Regex("""Mill (\w+) cards\. Then you may pay ([^.]+)\. If you do, put an? (.+?) from among (?:those cards|them) into your hand\.""")

    // mh3 chunk 10
    /** The memory key of a card exiled from the hand by an imprint ability ([Sentences] remembers it so). */
    const val IMPRINTED = "imprinted"
    val IMPRINT_FROM_HAND = Regex("""^Imprint — When .+?, you may exile an? .+ from your hand\.""")
    /** "If you sacrificed an Angel this way, create a number of Blood tokens equal to its toughness instead." (CR 608.2h) */
    val IF_YOU_SACRIFICED_THIS_WAY_INSTEAD = Regex("""If you sacrificed (an? .+?) this way, (.+) instead\.""")
    val YOU_SACRIFICED_THIS_WAY = Regex("""\byou sacrificed an? .+? this way\b""")
    /** "If the modified creature was sacrificed, draw a card." after "sacrifice a creature or sacrifice a modified creature" (CR 700.9, 608.2h) */
    val IF_THE_X_WAS_SACRIFICED = Regex("""If the (.+?) was sacrificed, (.+)""")
    /** "…, where X is that creature's power." after a sacrifice (CR 608.2h) */
    val WHERE_X_IS_THAT_CREATURES = Regex(""", where X is that creature's (power|toughness|mana value)\.$""")
    /** "Gain control of that spell and you may choose new targets for it." (CR 115.7) */
    val GAIN_CONTROL_OF_THAT_SPELL = Regex("""[Gg]ain control of that spell( and you may choose new targets for it)?\.""")
    /** "You may cast that card without paying its mana cost if the two spells have the same mana value." (CR 601.3, 608.2g) */
    val MAY_CAST_FREE_IF_SAME_MANA_VALUE = Regex("""[Yy]ou may cast (?:that card|it) without paying its mana cost if the two spells have the same mana value\.""")
    /** "Until end of turn, your life total can't change, and permanents you control gain hexproof and indestructible." (CR 119.7, 119.8) */
    val UNTIL_EOT_LIFE_TOTAL_CANT_CHANGE_AND = Regex("""Until end of turn, your life total can't change(?:, and (.+?) gain ([a-z ,]+))?\.""")
    /** "Target opponent exiles the top half of their library, rounded up." (CR 107.1a) */
    val TARGET_OPPONENT_EXILES_TOP_HALF = Regex("""[Tt]arget opponent exiles the top half of their library, rounded (up|down)\.""")
    /** "Ulamog enters with a number of +1/+1 counters on it equal to the greatest mana value among cards in exile." (CR 614.1c) */
    val ENTERS_WITH_COUNTERS_EQUAL_TO = Regex("""(.+?) enters with a number of (\S+) counters on it equal to (.+)\.""")
    /** "the greatest mana value among cards in exile" */
    val GREATEST_MANA_VALUE_AMONG = Regex("""the (greatest|least) (mana value|power|toughness) among (.+)""")
    /** "Ulamog has annihilator X, where X is the number of +1/+1 counters on it." (CR 702.86a) */
    /** "Create a number of Blood tokens equal to its toughness." */
    val CREATE_NUMBER_OF_TOKENS_EQUAL_TO_ITS = Regex("""[Cc]reate a number of ([A-Z]\w+) tokens equal to its (power|toughness)\.""")
    /** "Return each creature card from your graveyard to the battlefield with a finality counter on it. Those creatures are Vampires in addition to
     * their other types." (as they enter) */
    val RETURN_EACH_WITH_COUNTER_THOSE_ARE = Regex("""(Return each .+? to the battlefield)(?: with an? (\S+) counter on it)?\. Those creatures are ([A-Z]\w+?)s in addition to their other types\.""")
    val NUMBER_OF_COUNTERS_ON_IT = Regex("""the number of (\S+) counters on it""")
    val HAS_ANNIHILATOR_X = Regex("""(.+?) has annihilator X, where X is (.+)\.""")
    /** "Look at the top seven cards of your library. Then you may sacrifice a creature. If you do, you may put a creature card with mana value X
     * or less from among those cards onto the battlefield, where X is 1 plus the sacrificed creature's mana value. Put the rest on the bottom of
     * your library in a random order." */
    val LOOK_MAY_SACRIFICE_PUT_MV_PLUS = Regex("""Look at the top (\w+) cards of your library\. Then you may sacrifice (an? [^.]+?)\. If you do, you may put an? (.+?) with mana value X or less from among (?:those cards|them) onto the battlefield(?:, where X is (\d+) plus the sacrificed \w+'s mana value)?\. Put the rest on the bottom of your library in a random order\.""")
    /** "a card is exiled with this land" (CR 607.2a) */
    val A_CARD_IS_EXILED_WITH = Regex("""a card is exiled with (.+)""")
    /** "If you would get one or more {E}, you get that many plus one {E} instead." (CR 614.1a) */
    val GET_ENERGY_PLUS_INSTEAD = Regex("""If you would get one or more \{E\}, you get that many plus (\w+) \{E\} instead\.""")
    /** "you've paid or lost four or more {E} this turn" (CR 107.14) */
    val PAID_OR_LOST_ENERGY_THIS_TURN = Regex("""you've paid or lost (\w+) or more \{E\} this turn""")
    /** "As this artifact enters, you may have it become a copy of any creature on the battlefield until end of turn, except it has haste."
     * (CR 614.12, 707.9) */
    val AS_ENTERS_MAY_BECOME_COPY_UNTIL_EOT = Regex("""As (.+?) enters, you may have it become a copy of any (.+?) on the battlefield until end of turn(?:, except (.+))?\.""")

    // mh3 chunk 11
    /** "You gain 1 life for each of its colors." (each colour counted once, CR 105.2; a destroyed target by last known information, 608.2h) */
    val GAIN_LIFE_FOR_EACH_OF_ITS_COLORS = Regex("""You gain (\d+) life for each of its colors\.""")
    /** "Nonbasic lands are Islands." (layer 4: they lose their other land types and their rules-text abilities, CR 305.7) */
    val ARE_BASIC_LAND_TYPE = Regex("""(.+?) are (Plains|Islands|Swamps|Mountains|Forests)\.""")
    /** "Create a Spellgorger Weird token." (a token named only here, defined by the card's reminder text) */
    val CREATE_NAMED_TOKEN = Regex("""Create an? ([A-Z][\w',-]*(?: [A-Z][\w',-]*)*) token\.""")
    /** The reminder that defines it: "Create a Spellgorger Weird token. (It's a {2}{R} 2/2 Weird creature with "…")" */
    val REMINDER_TOKEN_IN_TEXT = Regex("""Create an? ([A-Z][\w',-]*(?: [A-Z][\w',-]*)*) token\. \(It's an? ([^)]+?)\.?\)""")
    /** The reminder's description: "{2}{R} 2/2 Weird creature with "…"" (mana cost, P/T, colours, types, abilities) */
    val REMINDER_TOKEN = Regex("""(?:((?:\{[^}]+\})+) )?(\d+/\d+) (?:((?:white|blue|black|red|green|colorless)(?: and (?:white|blue|black|red|green))?) )?((?:[A-Z]\w+ )*(?:artifact )?creature)(?: with (.+))?""")
    /** "Whenever …, choose one that hasn't been chosen this turn. Create a creature token with those characteristics." */
    val CHOOSE_CREATE_WITH_THOSE_CHARACTERISTICS = Regex("""(.+?\.) Create an? (?:creature )?token with those characteristics\.""")
    /** Its modes: "• 2/2 white Fox with vigilance." */
    val CHARACTERISTICS_MODE = Regex("""(?:• )?(\d+/\d+ (?:white|blue|black|red|green|colorless)(?: and (?:white|blue|black|red|green))? [A-Z]\w*(?: [A-Z]\w*)*)( with [^.]+)?\.""")
    /** "Pay six {C}": that many colorless mana symbols (CR 107.4c), in a cost written after an em dash ("Madness—Pay six {C}.") */
    val PAY_N_COLORLESS = Regex("""Pay (\w+) \{C\}""")
    /** "sacrifice all creatures you control." (CR 701.21a) */
    val SACRIFICE_ALL_YOU_CONTROL = Regex("""[Ss]acrifice all (.+? you control)\.""")

    /** "You may draw cards equal to its power. If you do, discard that many cards." (CR 121.1, 608.2c: "that many" is the number drawn) */
    val MAY_DRAW_EQUAL_TO_IF_YOU_DO_DISCARD_THAT_MANY = Regex("""You may draw cards equal to (.+?)\. If you do, discard that many cards\.""")
    // mh3 chunk 12
    /** "Look at the top four cards of your library. You may put a land card from among them onto the battlefield tapped. If you don't, put a card
     * from among them into your hand. Put the rest on the bottom of your library in a random order." */
    val LOOK_MAY_PUT_ONTO_BATTLEFIELD_IF_YOU_DONT_HAND = Regex("""Look at the top (\w+) cards of your library\. You may put an? (.+?) from among them onto the battlefield( tapped)?\. If you don't, put an? (.+?) from among them into your hand\. Put the rest on the bottom of your library in a random order\.""")
    /** "two or more creatures attack" (anyone's, once for the declaration, CR 508.1) */
    val N_OR_MORE_ATTACK = Regex("""(two|three|four|five) or more (.+?) attack""")
    /** "one or more cards are put into exile from your library and/or your graveyard" (one batch, CR 603.2c) */
    val PUT_INTO_EXILE_FROM_YOUR = Regex("""one or more (.+?) are put into exile from your (library|graveyard)(?: and/or your (library|graveyard))?""")
    /** "you cast or copy an instant or sorcery spell" (magecraft, CR 207.2c, 707.10) */
    val YOU_CAST_OR_COPY = Regex("""you cast or copy an? (.+? spell)""")
    /** "When this enchantment enters and whenever you draw your third card each turn, …": two trigger conditions of one effect (CR 603.1) */
    val ENTERS_AND_WHENEVER = Regex("""When (.+?) enters and whenever (.+?), (.+)""")
    /** "Exile the top three cards of your library. You may play those cards this turn. At the beginning of the next end step, if any of those cards
     * remain exiled, put them into your graveyard, then create a 0/1 colorless Eldrazi Spawn creature token for each card put into your graveyard this
     * way. Those tokens have "Sacrifice this token: Add {C}."" */
    val EXILE_TOP_PLAY_THIS_TURN_END_STEP_GRAVEYARD_TOKENS = Regex("""Exile the top (\w+) cards of your library\. You may play those cards this turn\. At the beginning of the next end step, if any of those cards remain exiled, put them into your graveyard, then create (an? .+? token) for each card put into your graveyard this way\. Those tokens have "([^"]+)"\.?""")
    /** "Put a collection counter on this enchantment. Then if there are three or more collection counters on it, sacrifice it. If you do, …" */
    val COUNTER_THEN_IF_SACRIFICE_IT_IF_YOU_DO = Regex("""(Put an? \S+ counter on (.+?)\.) Then if (there are .+? counters on it), sacrifice it\. If you do, (.+)""")
    /** "Tap all creatures target player controls. You may pay any amount of {E}. If you do, choose up to that many creatures tapped this way. Put a stun
     * counter on each of them." */
    val TAP_ALL_PAY_ENERGY_CHOOSE_THAT_MANY_COUNTER = Regex("""Tap all (.+?) target player controls\. You may pay any amount of \{E\}\. If you do, choose up to that many (.+?) tapped this way\. Put an? (\S+) counter on each of them\.""")
    /** "Sacrifice any number of creatures. Search your library and/or graveyard for a creature card with mana value less than or equal to the number of
     * creatures sacrificed this way and put it onto the battlefield. If you search your library this way, shuffle." */
    val SACRIFICE_ANY_NUMBER_SEARCH_MV_AT_MOST_SACRIFICED = Regex("""Sacrifice any number of (.+?)\. Search your library and/or graveyard for an? (.+?) with mana value less than or equal to the number of (.+?) sacrificed this way and put it onto the battlefield\. If you search your library this way, shuffle\.""")
    /** "Search your library for a card, exile it face down, then shuffle." (the card remembered, CR 406.3, 607.2a) */
    val SEARCH_EXILE_IT_FACE_DOWN = Regex("""Search your library for an? (.+?), exile it face down, then shuffle\.""")
    /** "Turn the exiled card face up. If it's a creature card, you lose life equal to its mana value." */
    val TURN_EXILED_FACE_UP_IF_LOSE_LIFE = Regex("""Turn the exiled card face up\. If it's an? (.+?), you lose life equal to its mana value\.""")
    /** "You may put the exiled card onto the battlefield if it's a creature card. If you don't put it onto the battlefield, put it into its owner's hand." */
    val MAY_PUT_EXILED_IF_ELSE_HAND = Regex("""You may put the exiled card onto the battlefield if it's an? (.+?)\. If you don't put it onto the battlefield, put it into its owner's hand\.""")
    /** "Reveal a card from your hand or the top card of your library. If you reveal a creature card this way, put it onto the battlefield." */
    val REVEAL_FROM_HAND_OR_TOP_IF_PUT = Regex("""Reveal a card from your hand or the top card of your library\. If you reveal an? (.+?) this way, put it onto the battlefield\.""")
    /** "you may remove a counter from another target permanent or opponent" (of any kind, CR 122.1) */
    val REMOVE_COUNTER_FROM_TARGET_OR_PLAYER = Regex("""([Yy]ou may )?[Rr]emove a counter from (another )?target (permanent|creature|artifact) or (opponent|player)\.""")
    /** "Kaalia attacks an opponent" (a player, not a planeswalker or battle, CR 508.1b) */
    val ATTACKS_AN_OPPONENT = Regex("""(.+) attacks an opponent""")
    /** "Put a +1/+1 counter on target creature you control, then each modified creature you control deals damage equal to its power to target …" */
    val COUNTER_THEN_EACH_DEALS_POWER = Regex("""(Put an? \S+ counter on target [^,]+), then (each .+ deals damage equal to its power to target .+\.)""")
    /** "Consuming Corruption deals X damage to target creature or planeswalker and you gain X life, where X is …" */
    val DEALS_X_AND_YOU_GAIN_X = Regex("""(.+?) deals X damage to (target [^,]+?) and you gain X life(, where X is .+)?\.""")
    // mh3 fixes
    /** "Players can't untap more than one nonbasic land during their untap steps." / "You can't untap more than two permanents during your untap
     * step." */
    val CANT_UNTAP_MORE_THAN = Regex("""(Players|You) can't untap more than (\w+) (.+?) during (?:their|your) untap steps?\.""")
    val PAY_LIFE_RATHER_THAN_MANA = Regex("""For each \{([WUBRG])\} in a cost, you may pay 2 life rather than pay that mana\.""")
    val REVEAL_FROM_OPENING_HAND_FIRST_UPKEEP = Regex("""You may reveal (.+?) from your opening hand\. If you do, at the beginning of your first upkeep, (.+)""")
    val EXILE_ANY_NUMBER_WITH_TYPES_AMONG_COST = Regex("""Exile any number of other (cards?) from your graveyard with (\w+) or more card types among them""")
}
