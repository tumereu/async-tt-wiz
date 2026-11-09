package arcanum.parser

/** Wordings first met in Foundations (FDN). */
internal object Fdn {
    // fdn chunk 01: a state trigger's condition (CR 603.8)
    val WHEN_THERE_ARE = Regex("""When (there are .+?), (.+)""")
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
    // chunk 05
    val TWO_AS_LONG_AS = Regex("""(As long as [^.]+\.) ([^.]+ as long as [^.]+\.)""")
    val ANY_PLAYER_CONTROLS = Regex("""any player controls an? (.+)""")
    val IF_IT_ENTERS_WITH_COUNTER = Regex("""If (.+), it enters with (\S+ \S+) counters? on it\.""")
    val FOR_EACH_PUT_INTO_GRAVEYARD_CREATE = Regex("""For each (.+?) put into a graveyard this way, (?:you )?create (.+)\.""")
    val FIRST_TIME_DURING_EACH_OF_YOUR_TURNS = Regex("""(.+) for the first time during each of your turns""")
    // chunk 06
    val AS_LONG_AS_HAS_AND_IS_IN_ADDITION = Regex("""As long as (.+?), (it|.+?) has ([a-z ,]+) and is an? ((?:[A-Z]\w+ ?)+) in addition to its other types\.""")
    val RETURN_MANA_VALUE_AT_MOST_SELF_POWER = Regex("""[Rr]eturn target (.+?) with mana value less than or equal to (.+)'s power from your graveyard to the battlefield\.""")
    // chunk 07
    val TARGET_PLAYER_HAS_EXACTLY_LIFE = Regex("""target player has exactly (\d+) life""")
    val EXILE_IF_AT_LEAST_ONE_EXILED = Regex("""([Ee]xile [^.]+\.) If (?:at least one|an?) (.+?) (?:was|is) exiled this way, ([^.]+\.)""")
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
}
