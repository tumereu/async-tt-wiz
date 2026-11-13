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
    val PAY_ENERGY = Regex("""Pay ((?:\{E\})+|X \{E\})""")
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
}
