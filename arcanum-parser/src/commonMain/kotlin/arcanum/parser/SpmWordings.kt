package arcanum.parser

import arcanum.api.CardType
import arcanum.api.Condition
import arcanum.api.EventPattern
import arcanum.api.Keyword
import arcanum.api.Measure
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Subtype
import arcanum.api.Zone

/** Regular expressions of Marvel's Spider-Man (spm) wordings read in [Sentences]. */
internal object Spm {
    // spm chunk 01: web-slinging and mayhem (CR 702.188, 702.187)
    val SPELLS_HAVE_WEB_SLINGING = Regex("""Each (legendary) spell you cast that's one or more colors has web-slinging ((?:\{[^}]+\})+)\.""")
    val ENTERS_WITH_X_IF_WEB_SLINGING = Regex("""If (.+?) was cast using web-slinging, (?:he|she|it|they) enters? with X \+1/\+1 counters on (?:him|her|it|them), where X is the mana value of the returned creature\.""")
    val CARDS_IN_GRAVEYARD_HAVE_MAYHEM = Regex("""Each (.+?) in your graveyard has mayhem\. The mayhem cost is equal to its mana cost\.""")
    val SPELLS_CAST_FROM_COST_LESS = Regex("""Spells you cast from your (graveyard|hand|exile) cost ((?:\{[^}]+\})+) less to cast\.""")
    val IF_COST_PAID_INSTEAD = Regex("""If this spell's ([a-z-]+) cost was paid, (.+) instead\.""")

    // spm chunk 02: modified (CR 700.9)
    val MODIFIED = Regex("""modified (.+)""")
    val HAS_KEYWORD_AS_LONG_AS_MODIFIED = Regex("""(.+?) has ([a-z ]+) as long as it's modified\.""")

    // spm chunk 03: becoming other things
    val ENCHANTED_IS_SUBTYPE_BASE_PT_HAS_LOSES = Regex("""Enchanted creature is an? ([A-Z]\w+) with base power and toughness (\d+)/(\d+)\. It has ([a-z ]+) and loses all other abilities\.""")
    val UNTIL_EOT_BECOMES_SUBTYPE_BASE_PT_GAINS = Regex("""Until end of turn, target (.+?) becomes an? ([A-Z]\w+) with base power and toughness (\d+)/(\d+) and gains (.+)\.""")
    val LOSES_ALL_BECOMES_COLOR_CREATURE = Regex("""(.+?) loses all abilities and becomes an? (white|blue|black|red|green) ([A-Z]\w+) creature with base power and toughness (\d+)/(\d+)\.""")
    val BECOMES_ARTIFACT_CREATURE_BASE_PT_UNTIL_EOT = Regex("""(.+?) becomes an artifact creature with base power and toughness (\d+)/(\d+) until end of turn\.""")
    val IT_BECOMES_TYPES_IN_ADDITION = Regex("""(?:It|That creature) becomes an? (legendary )?((?:[A-Z]\w+ ?)+) in addition to its other types\.""")
    val EQUIPPED_GETS_AND_IS_IN_ADDITION = Regex("""Equipped creature gets ([+-]\d+)/([+-]\d+) and is an? ((?:[A-Z]\w+ ?)+) in addition to its other types\.""")
    val UNTIL_EOT_GETS_AND_BECOMES_IN_ADDITION = Regex("""Until end of turn, target (.+?) gets ([+-]\d+)/([+-]\d+) and becomes an? ([A-Z]\w+) in addition to its other types\.""")
    val GAINS_AND_BECOMES_COLORLESS = Regex("""(?:It|He|She) gains ([a-z ]+) and becomes colorless until end of turn\.""")
    val BECOMES_LAND_AND_GAINS = Regex("""Until your next turn, (?:it|he|she) becomes a land and gains "(.+)"\.""")
    val UNTAP_SELF = Regex("""[Uu]ntap (.+?)\.""")
    val AS_LONG_AS_BASE_PT = Regex("""As long as (.+), (.+?) has base power and toughness (\d+)/(\d+)\.""")

    // spm chunk 04: copies
    val MAY_ENTER_AS_COPY_EXCEPT_NAME = Regex("""You may have (.+?) enter as a copy of an? (.+?), except (?:his|her|its|their) name is (.+)\.""")
    val MAY_ENTER_AS_COPY_OF_CARD_IN_GRAVEYARD = Regex("""You may have (.+?) enter as a copy of any (.+?) in a graveyard, except (?:his|her|its|their) name is (.+?) and (?:he's|she's|it's|they're) an? (\d+)/(\d+) ((?:[A-Z]\w+ ?)+) in addition to (?:his|her|its|their) other types\. When you do, exile that card\.""")
    val NEXT_CREATURE_SPELL_COPY_NOT_LEGENDARY = Regex("""When you next cast an? (.+?) spell this turn, copy it, except the copy isn't legendary\.""")
    val COPY_THAT_SPELL_NOT_LEGENDARY = Regex("""[Cc]opy that spell, except the copy isn't legendary\.""")
    val CREATE_TOKEN_COPY_NOT_LEGENDARY = Regex("""[Cc]reate a token that's a copy of it, except it isn't legendary\.""")
    val CHOOSE_NAME_DAMAGE_TRIGGER_THIS_TURN = Regex("""Choose a card name\. Whenever a (creature) with the chosen name deals combat damage to a player this turn, (.+)""")
    val MAY_COPY_FROM_ELSEWHERE_HASTE = Regex("""[Yy]ou may copy it\. If you do, you may choose new targets for the copy\. If the copy is a permanent spell, it gains haste\.""")

    // spm chunk 05: cards from libraries and graveyards
    val LOOK_TOP_N_MAY_REVEAL_UP_TO_PUT_HAND = Regex("""[Ll]ook at the top (\w+) cards of your library\. You may reveal up to (\w+) (.+?) from among them and put them into your hand\. Put the rest on the bottom of your library in a random order\.""")
    val LOOK_TOP_N_MAY_PUT_ONTO_BATTLEFIELD = Regex("""[Ll]ook at the top (\w+) cards of your library\. You may put an? (.+?) from among them onto the battlefield\. Put the rest on the bottom of your library in a random order\.""")
    val LOOK_THAT_MANY_ONE_HAND_REST_GRAVEYARD = Regex("""[Ll]ook at that many cards from the top of your library\. Put one of them into your hand and the rest into your graveyard\.""")
    val BLACK_CAT = Regex("""[Ll]ook at the top (\w+) cards of target opponent's library, exile (\w+) of them face down, then put the rest on the bottom of their library in a random order\. You may play the exiled cards for as long as they remain exiled\. Mana of any type can be spent to cast spells this way\.""")
    val RETURN_SELF_AND_TARGET_FROM_GRAVEYARD = Regex("""Return (.+?) and target (.+?) from your graveyard to the battlefield( tapped)?\.""")
    val PUT_SELF_FROM_HAND_ONTO_BATTLEFIELD = Regex("""Put (.+?) from your hand onto the battlefield\.""")
    val MAY_CAST_FROM_GRAVEYARD_BY_DISCARDING = Regex("""You may cast (.+?) from your graveyard by discarding a card in addition to paying its other costs\.""")
    val RETURN_UP_TO_DIFFERENT_NAMES = Regex("""Return up to (\w+) target (.+?) with different names from your graveyard to the battlefield\.""")
    val MAY_CAST_FROM_HAND_MV_AT_MOST_FREE = Regex("""You may cast an? (.+?) spell from your hand with mana value less than or equal to (.+?) without paying its mana cost\.""")
    val MAY_CAST_TWO_KINDS_FROM_TOP = Regex("""You may cast (\w+) spells and (\w+) spells from the top of your library\.""")

    // spm chunk 06: villains and their tricks
    val CREATE_FOR_EACH_EXILE_WHEN_LEAVES = Regex("""Create (.+?) for each (.+?)\. Exile those tokens when (.+?) leaves the battlefield\.""")
    val TARGET_OPPONENT_LOSES_FOR_THEIR = Regex("""Target opponent loses life equal to the number of (.+?) they control\.""")
    val DEALS_TO_TARGET_AND_ITS_CONTROLLER = Regex("""(.+?) deals (\d+) damage to target (.+?) and (\d+) damage to that (\w+)'s controller\.""")
    val TARGET_DEALS_DAMAGE_TO_ITSELF_IF_ATTACKING = Regex("""Target (.+?) deals damage equal to its power to itself\. If that creature is attacking, (.+?) deals (\d+) damage to that creature's controller\.""")
    val MAY_SACRIFICE_DRAW_X_PUT_PERMANENT = Regex("""[Yy]ou may sacrifice another creature\. If you do, draw X cards, then you may put an? (.+?) with mana value X or less from your hand onto the battlefield(?:, where X is the sacrificed creature's mana value)?\.""") // inr chunk 09: the "where X is" may be read off first
    val GWENOM = Regex("""[Uu]ntil end of turn, you may look at the top card of your library any time and you may play cards from the top of your library\. If you cast a spell this way, pay life equal to its mana value rather than pay its mana cost\.""")
    val PARKER_LUCK = Regex("""(\w+) target players each reveal the top card of their library\. They each lose life equal to the mana value of the card revealed by the other player\. Then they each put the card they revealed into their hand\.""")
    val EACH_PLAYER_MAY_DISCARD_WHO_DOESNT_LOSES = Regex("""Each player may discard a card\. Each player who doesn't loses (\d+) life\.""")
    val EXILE_ANY_NUMBER_TARGET_GRAVEYARDS = Regex("""Exile any number of target players' graveyards\.""")
    val PUT_COUNTER_THEN_SURVEIL_COUNTERS_ON_IT = Regex("""[Pp]ut a \+1/\+1 counter on it\. Then surveil X, where X is the number of counters on it\.""")
    val EXILE_X_TARGETS_RETURN_NEXT_END_STEP = Regex("""Exile X target (.+?)\. Return the exiled cards to the battlefield under their owners' control at the beginning of the next end step\.""")
    val MAY_EXCHANGE_LIFE_DRAW_LOST = Regex("""[Yy]ou may exchange life totals with target opponent\. If you lost life this way, draw that many cards\.""")
    val SUPERIOR_FOES = Regex("""[Yy]ou may exile the top card of your library\. If you do, you may play that card until you exile another card with (.+?)\.""")
    val PUT_ITS_COUNTERS_ON_TARGET = Regex("""[Pp]ut its (\+1/\+1) counters on target (.+)\.""")
    val PUT_SELF_ON_BOTTOM_IF_YOU_DO_RETURN_EXILED = Regex("""[Pp]ut (?:him|her|it|them) on the bottom of (?:his|her|its|their) owner's library\. If you do, return the exiled cards to their owners' hands\.""")
    val EXILE_UP_TO_ONE_AND_UP_TO_ONE = Regex("""[Ee]xile up to one target (.+?) and up to one target (.+?)\.""")

    // spm chunk 07: heroes and their tricks
    val UP_TO_ONE_TARGET_GAINS = Regex("""[Uu]p to one target (.+?) gains (.+) until end of turn\.""")
    // woe chunk 01: also without the bonus ("One or two target creatures each gain haste until end of turn.")
    val ONE_OR_TWO_EACH_GET_AND_GAIN = Regex("""(One or two) target (.+?) each (?:get ([+-]\d+)/([+-]\d+) and )?gain (.+) until end of turn\.""")
    val IF_ITS_A = Regex("""If it's an? ([A-Z]\w+)(?: card)?, (.+)""") // fdn: "If it's a Zombie card, draw a card."
    val SUSPECT = Regex("""[Ss]uspect (up to one )?target (.+?)\.""")
    val WHEN_EXCESS_DAMAGE_THIS_WAY_DESTROY = Regex("""When excess damage is dealt to the (.+?) this way, destroy up to one target (.+)\.""")
    val CREATE_THEN_YOU = Regex("""[Cc]reate (.+?), then you (.+)""")
    val LEGEND_RULE_DOESNT_APPLY_TO = Regex("""The "legend rule" doesn't apply to (.+?) you control\.""")
    val COSTS_LESS_IF_IT_TARGETS_SUBTYPE = Regex("""This spell costs ((?:\{[^}]+\})+) less to cast if it targets an? ([A-Z]\w+)\.""")
    val ATTACHED_GETS_FOR_EACH_ATTACHED = Regex("""Enchanted creature gets ([+-]\d+)/([+-]\d+) for each Aura and Equipment attached to it\.""")
    // rvr chunk 01: also "equipped creature"
    val ALL_DAMAGE_TO_YOU_TO_ENCHANTED = Regex("""All damage that would be dealt to you is dealt to (?:enchanted|equipped) creature instead\.""")
    // rvr chunk 03: also "put that many +1/+1 counters on it instead" (a replacement, not a prevention)
    val PREVENT_DAMAGE_PUT_THAT_MANY_COUNTERS = Regex("""If damage would be dealt to (.+?), (prevent that damage and )?put that many (\+1/\+1) counters on (?:him|her|it|them)( instead)?\.""")
    val LOOK_AT_OPPONENTS_HAND_CHOOSE_CARD_TYPE = Regex("""As (.+?) enters, look at an opponent's hand, then choose (?:a card type other than (\w+)|any card name)\.""")
    val SPELLS_OF_CHOSEN_TYPE_COST_MORE = Regex("""Spells of the chosen type cost ((?:\{[^}]+\})+) more to cast\.""")
    val DOUBLE_EACH_KIND_ON_EACH = Regex("""[Dd]ouble the number of each kind of counter on each (.+)\.""")
    val CHOOSE_ONE_OR_BOTH_MODES = Regex("""choose one or both —""")
    val GAINS_QUOTED_AND_QUOTED = Regex("""It gains "(.+?)" and "(.+?)"\.?""")

    // spm chunk 08: turns, lands and mana
    val CANT_CAST_DURING_FIRST_TURNS = Regex("""You can't cast (.+?) during your first, second, or third turns of the game\.""")
    val DONT_LOSE_UNSPENT_MANA = Regex("""You don't lose unspent (white|blue|black|red|green) mana as steps and phases end\.""")
    val SPELLS_AND_ABILITIES_CANT_BE_COUNTERED = Regex("""Spells and abilities can't be countered\.""")
    val CHOOSE_BASIC_LAND_TYPE_THEN_MAY_PAY_LIFE = Regex("""As (.+?) enters, choose a basic land type\. Then you may pay (\d+) life\. If you don't, it enters tapped\.""")
    val IS_THE_CHOSEN_TYPE = Regex("""(.+?) is the chosen type\.""")
    val EACH_PLAYERS_MAIN_MAY_COUNTER_ADD_C = Regex("""At the beginning of each player's first main phase, that player may put a \+1/\+1 counter on (.+?)\. If they do, they add \{C\} for each counter on it\.""")
    val MAY_PLAY_THAT_CARD_AS_LONG_AS_YOU_CONTROL = Regex("""You may play that card for as long as you control (.+?)\.""")
    val COUNTER_TARGET_SPELLS_OR_TRIGGERED = Regex("""Counter target instant spell, sorcery spell, or triggered ability\.""")
    val MAXIMUM_CARNAGE = Regex("""Until your next turn, each creature attacks each combat if able and attacks a player other than you if able\.""")
    val OTHER_PLURAL_LIST_GET = Regex("""Other ((?:[A-Z]\w+, )+and [A-Z]\w+) you control get ([+-]\d+)/([+-]\d+)\.""")
    // ltr chunk 01: also "a Goblin or Orc"
    val DEALS_DAMAGE_TO_SUBTYPE_DESTROY = Regex("""Whenever (.+?) deals damage to an? ([A-Z]\w+(?: or [A-Z]\w+)?), destroy that creature\.""")
}

/** Phrases (nouns, conditions) of Marvel's Spider-Man (spm) read by [Filters] and [Conditions]. */
internal object SpmPhrases {
    /** spm chunk 02: "modified creature you control" (CR 700.9). */
    fun noun(phrase: String): ObjectFilter? {
        Spm.MODIFIED.matchEntire(phrase)?.let { m -> return Filters.all(Filters.noun(m.groupValues[1]) ?: return null, ObjectFilter.Modified) }
        // spm chunk 06: "non-Equipment artifact you control"
        if (phrase.startsWith("non-")) {
            val rest = phrase.removePrefix("non-")
            val word = rest.substringBefore(' ')
            if (word in RulesWords.subtypes && rest.contains(' ') && !rest.contains(" or ") && !rest.contains(" and ") && !rest.contains(',')) {
                return Filters.all(ObjectFilter.Not(ObjectFilter.Sub(Subtype(word))), Filters.noun(rest.substringAfter(' ')) ?: return null)
            }
        }
        return null
    }

    /** spm: a plural creature type ("Spiders", "Mice", "Wolves") as its subtype. */
    fun pluralSubtype(plural: String): Subtype? = listOf(plural.removeSuffix("s"), plural.removeSuffix("es"), plural.replace(LazyRegexes.VES_END, "f"),
        plural.replace(LazyRegexes.ICE_END, "ouse"), plural).firstOrNull { it in RulesWords.subtypes }?.let { Subtype(it) }

    fun condition(text: String, self: Set<String>): Condition? {
        fun isSelf(t: String) = t in self || t.replaceFirstChar { it.lowercase() } in self
        // spm chunk 01: "they were cast using web-slinging" (CR 702.188a; the card's own alternative cost)
        WAS_CAST_USING.matchEntire(text)?.let { m ->
            if (m.groupValues[1] !in PRONOUNS && !isSelf(m.groupValues[1])) return null
            val kind = when (m.groupValues[2]) { "web-slinging" -> Keyword.WebSlinging.Kind; else -> return null }
            return Condition.Paid(kind, times = 1, of = Objects.Self)
        }
        // spm chunk 07: "it was cast from your graveyard" (this permanent, CR 400.7d)
        WAS_CAST_FROM.matchEntire(text)?.let { m ->
            if (m.groupValues[1] !in PRONOUNS && !isSelf(m.groupValues[1])) return null
            return Condition.CastFrom(when (m.groupValues[2]) { "graveyard" -> Zone.Graveyard; "hand" -> Zone.Hand; else -> return null }, Objects.Self)
        }
        // spm chunk 03: "Hydro-Man is a creature"
        IS_A_TYPE.matchEntire(text)?.let { m ->
            if (!isSelf(m.groupValues[1])) return null
            return Condition.ObjectMatches(Objects.Self, Filters.noun(m.groupValues[2]) ?: return null)
        }
        // spm chunk 07: "two or more creatures entered the battlefield under your control this turn"
        // woe chunk 06: Celebration (an ability word, CR 207.2c), "two or more nonland permanents entered ..."
        ENTERED_UNDER_YOUR_CONTROL_THIS_TURN.matchEntire(text)?.let { m ->
            val n = Numbers.count(m.groupValues[1]) ?: return null
            val noun = Filters.noun(m.groupValues[2].removeSuffix("s")) ?: return null
            return Condition.HappenedThisTurn(EventPattern.ZoneChange(Filters.all(noun, ObjectFilter.ControlledBy(PlayerFilter.You)), to = Zone.Battlefield),
                atLeast = n, measure = Measure.Objects)
        }
        // spm chunk 06: "you've cast a spell with mana value 4 or greater this turn"
        CAST_SPELL_THIS_TURN.matchEntire(text)?.let { m ->
            val spell = Filters.noun(m.groupValues[1]) ?: return null
            return Condition.HappenedThisTurn(EventPattern.Cast(spell, PlayerFilter.You), atLeast = 1, measure = Measure.Events)
        }
        // spm chunk 08: "you've played a land or cast a spell this turn from anywhere other than your hand"
        if (text == "you've played a land or cast a spell this turn from anywhere other than your hand")
            return Condition.Or(listOf(
                Condition.HappenedThisTurn(EventPattern.PlayLand(ObjectFilter.Type(CardType.Land), PlayerFilter.You, notFrom = Zone.Hand), atLeast = 1, measure = Measure.Events),
                Condition.HappenedThisTurn(EventPattern.Cast(notFromHand, PlayerFilter.You), atLeast = 1, measure = Measure.Events)))
        return null
    }

    /** spm chunk 08: a spell cast from anywhere other than its caster's hand (CR 601.2a). */
    val notFromHand: ObjectFilter get() = ObjectFilter.Not(ObjectFilter.WasCast(Zone.Hand))

    /** spm chunk 08: "you play a land from exile or cast a spell from exile", "you play a land or cast a spell from anywhere other than your
     * hand" (CR 305.1, 601.2a). */
    fun events(text: String): List<EventPattern>? = when (text) {
        "you play a land from exile or cast a spell from exile" -> listOf(
            EventPattern.PlayLand(ObjectFilter.Type(CardType.Land), PlayerFilter.You, from = Zone.Exile),
            EventPattern.Cast(ObjectFilter.Any, PlayerFilter.You, from = Zone.Exile))
        "you play a land or cast a spell from anywhere other than your hand" -> listOf(
            EventPattern.PlayLand(ObjectFilter.Type(CardType.Land), PlayerFilter.You, notFrom = Zone.Hand),
            EventPattern.Cast(notFromHand, PlayerFilter.You))
        "you cast a spell from anywhere other than your hand" -> listOf(EventPattern.Cast(notFromHand, PlayerFilter.You))
        else -> null
    }

    private val PRONOUNS = setOf("it", "he", "she", "they")
    private val WAS_CAST_USING = Regex("""(.+?) (?:was|were) cast using ([a-z-]+)""")
    private val WAS_CAST_FROM = Regex("""(.+?) was cast from your (graveyard|hand)""")
    private val IS_A_TYPE = Regex("""(.+?) is an? (creature|land|artifact)""")
    private val ENTERED_UNDER_YOUR_CONTROL_THIS_TURN = Regex("""(\w+) or more (.+?) entered the battlefield under your control this turn""")
    private val CAST_SPELL_THIS_TURN = Regex("""you've cast an? (spell with mana value \d+ or greater) this turn""")
}
