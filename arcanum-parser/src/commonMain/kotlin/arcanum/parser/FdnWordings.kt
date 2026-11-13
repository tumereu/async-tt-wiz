package arcanum.parser

/** Wordings first met in Foundations (FDN). */
internal object Fdn {
    // chunk 11c
    val COLOR_TYPE_OR_TYPE = Regex("""(white|blue|black|red|green) (instant|sorcery|creature|artifact|enchantment|land|planeswalker) or (instant|sorcery|creature|artifact|enchantment|land|planeswalker)( spell| card)?""")
    val ADD_WHEN_THAT_MANA_SPENT = Regex("""(\{T\}: Add \{\w\}\.) When that mana is spent to cast an? (.+? spell), (.+)\.""")
    val YOU_PUT_COUNTERS_ON_SELF = Regex("""you put one or more (\S+) counters on (.+)""")
    val COUNTERS_ARE_PUT_ON = Regex("""one or more (\S+) counters are put on (.+)""")
    val ADD_SPELL_GAINS_KEYWORD_UNTIL_EOT = Regex("""(\{T\}: Add \{\w\}\.) If that mana is spent on an? (.+? spell), it gains ([a-z ]+) until end of turn\.""")
    val DEALT_DAMAGE_BY_DIES = Regex("""an? (\w+) dealt damage by (.+) this turn dies""")
    // fdn chunk 10b
    val LOSES_LIFE_EQUAL_TO_COUNTERS_ON_SELF = Regex("""(Each opponent|Each player) loses life equal to the number of (\w+) counters on (this \w+)\.""")
    val SELF_BECOMES_CREATURE_WITH_ALL_TYPES = Regex("""(This \w+) becomes an? (\d+)/(\d+) creature with (\w+) and all creature types\. It's still an? \w+\.""")
    val PLAYER_SUBJECT = Regex("""^(?:Each opponent|Each player|Target opponent|Target player|Each other player)""")
    val VERB_PHRASE = Regex("""^(?:loses|gains|draws|discards|mills|sacrifices|exiles|gets) .+""") // one chunk 05: "gets a poison counter"
    val CANT_LOSE_AND_OPPONENTS_CANT_WIN = Regex("""You can't lose the game and your opponents can't win the game\.""")
    val PREVENT_COMBAT_TO_AND_BY_SELF = Regex("""Prevent all combat damage that would be dealt to and dealt by (this \w+|enchanted creature|equipped creature)\.""")
    val YOU_LOSE_LIFE_EQUAL_TO_THAT = Regex("""You (lose|gain) life equal to that (permanent|creature|card)'s (mana value|power|toughness)\.""")
    // fdn chunk 01: a state trigger's condition (CR 603.8)
    // inr chunk 01: also "When Garruk has two or fewer loyalty counters on him, transform him."
    val WHEN_THERE_ARE = Regex("""When (there are .+?|.+? has \w+ or (?:fewer|more) \S+ counters on (?:it|him|her)), (.+)""")
    // fdn chunk 01: "If you do" after a mandatory exile (CR 118.12)
    val EXILE_IT_IF_YOU_DO = Regex("""Exile it\. If you do, ([^.]+\.)""")
    // fdn chunk 02: the counter and the count on the same object, named by the ability
    val COUNTER_ON_IT_THEN_IF_IT_HAS = Regex("""[Pp]ut an? (\S+) counter on it\. Then if it has (\w+) or more (\S+) counters on it, (.+)""")
    // fdn chunk 02: an Aura's own card, after the creature it enchanted died
    val RETURN_THIS_CARD_TO_HAND = Regex("""[Rr]eturn this card to its owner's hand\.""")
    // chunk 03: "Whenever equipped creature becomes untapped"
    val BECOMES_UNTAPPED = Regex("""(.+) becomes untapped""")
    // chunk 03: "Put a bait counter on Fishing Pole." in a granted ability
    val PUT_COUNTER_ON_NAME = Regex("""Put an? (\S+) counter on (.+)\.""")
    // chunk 04
    val DOUBLE_COUNTERS_ON_UP_TO = Regex("""Double the number of each kind of counter on up to (\w+) target (.+)\.""")
    val MAY_HAVE_IT_FIGHT = Regex("""[Yy]ou may have it fight target (.+)\.""")
    val PUT_SELF_FROM_GRAVEYARD_ON_TOP = Regex("""[Pp]ut this card from your graveyard on top of your library\.""")
    // chunk 09b
    val PLAY_NOT_OWNED_WITH_COUNTERS_ANY_TYPE = Regex("""During your turn, you may play cards you don't own with (\S+) counters on them from exile, and mana of any type can be spent to cast those spells\.""")
    val EXILE_IT_FROM_GRAVEYARD_WITH_COUNTER = Regex("""[Ee]xile it from their graveyard with an? (\S+) counter on it\.""")
    val GAINS_AND_GETS_UNTIL_EOT = Regex("""(.+?) gains (.+?) and gets ([+-]\w+)/([+-]\w+) until end of turn\.?""")
    val OPPONENT_GAINS_CONTROL = Regex("""Target opponent gains control of target (.+?)\.""")
    val DESTROY_ALL_GAIN_FOR_EACH = Regex("""Destroy all (.+?)\. You gain (\d+) life for each (?:.+?) destroyed this way\.""")
    // chunk 05
    val TWO_AS_LONG_AS = Regex("""(As long as [^.]+\.) ([^.]+ as long as [^.]+\.)""")
    val ANY_PLAYER_CONTROLS = Regex("""any player controls an? (.+)""")
    val IF_IT_ENTERS_WITH_COUNTER = Regex("""If (.+), it enters with (\S+ \S+) counters? on it\.""")
    val FOR_EACH_PUT_INTO_GRAVEYARD_CREATE = Regex("""For each (.+?) put into a graveyard this way, (?:you )?create (.+)\.""")
    val FIRST_TIME_DURING_EACH_OF_YOUR_TURNS = Regex("""(.+) for the first time during each of your turns""")
    // chunk 06
    val AS_LONG_AS_HAS_AND_IS_IN_ADDITION = Regex("""As long as (.+?), (it|.+?) has ([a-z ,]+) and is an? ((?:[A-Z]\w+ ?)+) in addition to its other types\.""")
    val RETURN_MANA_VALUE_AT_MOST_SELF_POWER = Regex("""[Rr]eturn (another )?target (.+?) with mana value less than or equal to (.+)'s power from your graveyard to the battlefield\.""")
    // chunk 07
    val TARGET_PLAYER_HAS_EXACTLY_LIFE = Regex("""target player has exactly (\d+) life""")
    // rvr chunk 03: also "… You gain 2 life if at least one creature card was exiled this way."
    val EXILE_IF_AT_LEAST_ONE_EXILED = Regex("""([Ee]xile [^.]+\.) (?:If (?:at least one|an?) (.+?) (?:was|is) exiled this way, ([^.]+\.)|([^.]+?) if (?:at least one|an?) (.+?) (?:was|is) exiled this way\.)""")
    val IT_WAS_ATTACKING = Regex("""(?:it|that creature) was (attacking|blocking)""")
    val EFFECT_WOULD_PUT_COUNTERS_TWICE = Regex("""If an effect would put one or more counters on an? (.+?), it puts twice that many of those counters on that \S+ instead\.""")
    val PLAY_LAND_AND_EACH_PERMANENT_TYPE_FROM_GRAVEYARD = Regex("""During each of your turns, you may play a land and cast a permanent spell of each permanent type from your graveyard\.""")
    val IF_SELF_IS_A_IT_BECOMES_PT_AND_GAINS = Regex("""If (.+?) is an? ([A-Z]\w+), it becomes an? (?:(\d+)/(\d+) )?([A-Z]\w+(?: [A-Z]\w+)*) and gains (.+?)\.?""")
    // chunk 08
    val X_SPELLS_AND_Y_SPELLS = Regex("""(\w+) spells and (\w+) spells( you cast)?""")
    val FLASH_IF_PAY_MORE = Regex("""You may cast this spell as though it had flash if you pay ((?:\{[^}]+\})+) more to cast it\.""")
    val SELF_BECOMES_SUBTYPES = Regex("""(.+?) becomes an? ((?:[A-Z]\w+ ?)+)\.""")
    val DEALS_DAMAGE_CREATE_EXCESS = Regex("""([^.]+ deals (?:X|\d+) damage to target [^.]+\.) Create a number of (.+? tokens?) equal to the amount of excess damage dealt to that creature this way\.""")
    val COPY_FOR_EACH_SPELL_CAST_BEFORE = Regex("""[Cc]opy it for each other (.+?) spell you've cast before it this turn\. You may choose new targets for the copies\.""")
    val IT_HAD_A_COUNTER = Regex("""it had an? (\S+) counter on it""")
    val RETURN_IT_WITH_ONE_FEWER_AT_NEXT_END_STEP = Regex("""[Rr]eturn it to the battlefield with one fewer (\S+) counter on it at the beginning of the next end step\.""")
    val REMOVE_COUNTERS_FROM_AMONG = Regex("""Remove (\w+) counters from among (.+)""")
    val SPEND_ANY_TYPE_TO_CAST = Regex("""You can spend mana of any type to cast (.+) spells\.""")
    val CHOOSES_PERMANENT_OF_EACH_TYPE_SACRIFICES_REST = Regex("""Each (opponent|player) chooses a permanent they control of each permanent type and sacrifices the rest\.""")
    // chunk 09
    val EXILE_TOP_OF_EACH_LIBRARY_CAST_ANY_NUMBER = Regex("""[Ee]xile the top card of each player's library, then you may cast any number of spells from among those cards without paying their mana costs\.?""")
    val EXILE_UP_TO_ONE_ATTACHED_TO_THAT_CREATURE = Regex("""[Ee]xile up to one target (.+?) attached to that creature\.""")
    val DESTROY_EACH_MV_X_CONTROLLER_DEALT_COMBAT = Regex("""[Dd]estroy each (.+?) with mana value X whose controller was dealt combat damage by (.+?) this turn\.""")
    val EXILE_PUT_THERE_THIS_TURN_COPY_EXCEPT_SUBTYPE_EXILE_OTHERS = Regex("""[Ee]xile target (.+?) from a graveyard that was put there this turn\. Create a token that's a copy of it, except it's an? ([A-Z]\w+) in addition to its other types\. Then exile all other \2 tokens you control\.""")
    // chunk 10a
    val MAY_HAVE_TARGET_GET = Regex("""[Yy]ou may have (target .+?) get (.+)""")
    val GETS_UNTIL_EOT_MUST_BE_BLOCKED = Regex("""(.+? (?:gets?|gains?|get|gain) .+?) until end of turn and must be blocked this turn if able\.""")
    val MAY_HAVE_IT_BECOME_PT_SUBTYPE_CREATURE = Regex("""(?:Then )?[Yy]ou may have it become an? (\d+)/(\d+) ([A-Z]\w+(?: [A-Z]\w+)*) creature until end of turn\.""")
    val YOU_HAVE_HEXPROOF = Regex("""You have hexproof\.""")
    val PREVENT_ALL_NONCOMBAT_DAMAGE_TO = Regex("""Prevent all noncombat damage that would be dealt to (.+)\.""")
    val SPELLS_YOU_CONTROL_CANT_BE_COUNTERED = Regex("""(\w+) and (\w+) spells you control can't be countered\.""")
    val TWO_GROUPS_YOU_CONTROL_GET = Regex("""(.+? you control) and (other .+? you control) (get .+)""")
    val IT_HAS_AND_IS_IN_ADDITION = Regex("""It has ([a-z ,]+) and is an? ((?:[A-Z]\w+ ?)+) in addition to its other types\.""")
    // chunk 11a
    val PUT_ALL_FROM_ALL_GRAVEYARDS_UNDER_YOUR_CONTROL = Regex("""Put all (.+?) from all graveyards onto the battlefield under your control\.""")
    val RETURN_ALL_TARGET_PLAYER_CONTROLS_TO_HAND = Regex("""Return all (.+?) target player controls to their owner's hand\.""")
    val PUT_COUNTER_FOR_EACH_COLOR_OF_THAT_SPELL = Regex("""[Pp]ut an? (\S+) counter on (.+?) for each of that spell's colors\.""")
    val REVEAL_X_PUT_ANY_NUMBER_MV_X_REST_GRAVEYARD = Regex("""Reveal the top X cards of your library\. You may put any number of (.+?) with mana value X or less from among them onto the battlefield\. Then put all cards revealed this way that weren't put onto the battlefield into your graveyard\.""")
    val LOOK_REVEAL_MV_AT_MOST_COUNT_PUT_REST_BOTTOM = Regex("""Look at the top (\w+) cards of your library\. You may reveal an? (.+?) with mana value less than or equal to the number of (.+? you control) from among them and put it onto the battlefield\. Put the rest on the bottom(?: of your library)? in a random order\.""")
    // chunk 11b
    val ITS_A_IN_ADDITION = Regex("""It's an? ((?:[A-Z]\w+ ?)+) in addition to its other types\.""")
    val MAY_SEARCH_EXILE_SHUFFLE = Regex("""[Yy]ou may search your library for (an? .+?), exile it, then shuffle\.""")
    val SHUFFLE_GRAVEYARD_INSTEAD_IF_X = Regex("""Draw X cards\. If X is (\d+) or more, instead shuffle your graveyard into your library, draw X cards, (untap up to \w+ lands), and you have no maximum hand size for the rest of the game\.""")
    val AS_ENTERS_CHOOSE_TYPE_THEN = Regex("""As (.+) enters, choose a creature type\. (This .+ enters with .+)""")
    val EACH_OPPONENT_LOSES_X_GAIN_LOST = Regex("""Each opponent loses X life\. You gain life equal to the life lost this way\.""")
}
