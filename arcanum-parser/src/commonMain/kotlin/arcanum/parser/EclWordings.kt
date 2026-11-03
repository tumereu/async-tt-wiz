package arcanum.parser

import arcanum.api.CardType
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CounterKind
import arcanum.api.EventPattern
import arcanum.api.Measure
import arcanum.api.ObjectCategory
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.SharedQuality
import arcanum.api.TurnStep
import arcanum.api.Value
import arcanum.api.Zone

/** Regular expressions of Lorwyn Eclipsed (ecl) wordings read in [Sentences]. */
internal object Ecl {
    // ecl chunk 01: blight (CR 701.68)
    val BLIGHT = Regex("""[Bb]light (\d+|X)""")
    val PLAYERS_BLIGHT = Regex("""(Target opponent|target opponent|Each opponent|each opponent|Each player|each player) blights (\d+)\.""")
    val BLIGHT_SENTENCE = Regex("""(?:Then )?[Bb]light (\d+)\.""")
    val ADDITIONAL_BLIGHT_X = Regex("""As an additional cost to cast this spell, blight X\. X can't be greater than (.+)\.""")
    val ADDITIONAL_MAY = Regex("""As an additional cost to cast this spell, you may (.+?)\.""")
    val MAY_BLIGHT_IF_YOU_DONT = Regex("""[Yy]ou may blight (\d+)\. If you don't, (.+)""")
    val MAY_PAY_IF_YOU_DONT_BLIGHT = Regex("""[Yy]ou may pay (\{[^}]+\})\. If you don't, blight (\d+)\.""")
    val IF_NOT_MAIN_PHASE_BLIGHT = Regex("""Then if it isn't your main phase, blight (\d+)\.""")

    // ecl chunk 02: behold (CR 701.4)
    val ADDITIONAL_BEHOLD_AND_EXILE = Regex("""As an additional cost to cast this spell, behold an? (.+?) and exile it\.""")
    val BEHOLD_COUNT = Regex("""[Bb]ehold (two|three|four|five) (.+)""")
    val ADDITIONAL_MAY_CHOOSE_TYPE_BEHOLD = Regex("""As an additional cost to cast this spell, you may choose a creature type and behold (two|three) creatures of that type\.""")

    // ecl chunk 04: -1/-1 counters and removing counters
    val REMOVE_COUNTERS_FROM_SELF = Regex("""Remove (a|two|three) counters? from (.+)""")
    val REMOVE_A_COUNTER_FROM = Regex("""[Rr]emove an? (-1/-1|\+1/\+1) counter from (.+?)\.""")
    val MAY_REMOVE_COUNTER_WHEN_YOU_DO = Regex("""[Yy]ou may remove a counter from (.+?)\. When you do, (.+)""")
    val REMOVE_ANY_NUMBER_OF_COUNTERS_FROM = Regex("""Remove any number of counters from (.+?)\.""")
    val REMOVE_ALL_COUNTERS_FROM = Regex("""Remove all counters from (.+?)\.""")

    // ecl chunk 03: vivid
    val COSTS_LESS_FOR_EACH_COLOR = Regex("""This spell costs (\{\d+\}) less to cast for each color among permanents you control\.""")
    val COSTS_X_LESS_WHERE = Regex("""This spell costs \{X\} less to cast, where X is (.+)\.""")
    val COSTS_LESS_IF = Regex("""This spell costs (\{\d+\}) less to cast if (.+)\.""")
    val FOR_EACH_COLOR_ADD = Regex("""For each color among permanents you control, add one mana of that color\.""")
    val POWER_IS_EQUAL = Regex("""(.+?)'s power is equal to (.+)\.""")

    // ecl chunk 09
    val AS_LONG_AS_EQUIPPED_TOUGHNESS_GREATER = Regex("""As long as equipped creature's toughness is greater than its power, it assigns combat damage equal to its toughness rather than its power\.""")
    val EACH_CREATURE_TARGET_OPPONENT_LOSES = Regex("""[Ee]ach creature target opponent controls loses all abilities, becomes an? ([A-Z]\w+) in addition to its other types, and has base power and toughness (\d+)/(\d+)\.""")
    val EXILE_UP_TO_N_TARGET_CARDS_FROM_GRAVEYARDS = Regex("""[Ee]xile up to (one|two|three) target cards from graveyards\.""")
    val LOOK_TOP_X_PUT_ANY_NUMBER = Regex("""Look at the top X cards of your library\. You may put any number of (.+?) from among them onto the battlefield\. Then shuffle\.""")
    val MAY_PUT_FROM_HAND_TAPPED_ATTACKING = Regex("""[Yy]ou may put an? (.+?) from your hand onto the battlefield tapped and attacking\.""")
    val EXILE_ANY_NUMBER_RETURN_NEXT_END = Regex("""Exile any number of target (creatures)\. At the beginning of the next end step, return those cards to the battlefield tapped under their owners' control\.""")
    val UNTIL_YOUR_NEXT_TURN_PREVENT_TO_YOU = Regex("""Until your next turn, prevent all damage that would be dealt to you\.""")
    val EXILE_SELF = Regex("""Exile (.+?)\.""")
    val FOR_EACH_PLAYER_CHOOSE_SACRIFICE_NOT_SHARING = Regex("""For each player, you choose a creature that player controls\. Then each player sacrifices all other creatures they control that don't share a creature type with the chosen creature they control\.""")
    val TAP_UNTAPPED_CREATURES_COST = Regex("""Tap (two|three|four) untapped creatures you control""")

    // ecl chunk 10
    val CANT_BECOME_UNTAPPED_AND_NO_COUNTERS = Regex("""Enchanted creature can't become untapped and can't have counters put on it\.""")
    val TAP_UP_TO_ONE_AND_STUN = Regex("""[Tt]ap up to (one|two) target (creatures?) and put a stun counter on (?:it|each of them)\.""")
    val TAP_UP_TO_TWO_STUN_EACH = Regex("""Tap up to (one|two) target (creatures?)\. Put a stun counter on (?:it|each of them)\.""")
    val DELAYED_COMBAT_DAMAGE_DRAW = Regex("""Whenever a creature you control deals combat damage to a player or planeswalker this turn, draw a card\.""")
    val CHOOSE_UP_TO_ONE_OTHER_BASE_PT_ALL_TYPES = Regex("""[Cc]hoose up to one other target creature\. Until end of turn, that creature has base power and toughness (\d+)/(\d+) and gains all creature types\.""")
    val COUNTER_ALL_OPPONENTS_CREATE_FOR_EACH = Regex("""Counter all spells your opponents control and all abilities your opponents control\. Create (.+?) for each spell and ability countered this way\.""")
    val CHOOSE_TYPE_DRAW_FOR_EACH = Regex("""Choose a creature type\. Draw a card for each (.+?) of that type\.""")
    val HEXPROOF_AS_LONG_AS_UNTAPPED = Regex("""(.+?) has (hexproof|indestructible) as long as it's untapped\.""")
    val MAY_CAST_AS_THOUGH_FLASH_IF = Regex("""You may cast (.+?) as though it had flash if (.+)\.""")
    val EACH_BECOMES_COPY_OF_TARGET = Regex("""Each (.+?) becomes a copy of target (.+)\.""")
    val ENCHANTED_LOSES_ALL_COLORLESS_BASE = Regex("""Enchanted creature loses all abilities and is a colorless ([A-Z]\w+) with base power and toughness (\d+)/(\d+)\.""")
    val GAINS_ALL_CREATURE_TYPES = Regex("""(Up to one target creature|Target creature) gains all creature types\.""")
    val GETS_UNTIL_YOUR_NEXT_TURN = Regex("""Target creature gets ([+-]\d+)/([+-]\d+) until your next turn\.""")
    val EMBLEM_CHOSEN_TYPE = Regex("""Choose a creature type\. You get an emblem with "(.+)"""")
    val MAY_ENTER_AS_COPY_EXCEPT_CHANGELING = Regex("""You may have (.+?) enter as a copy of any creature on the battlefield, except it has changeling\.""")
    val NEXT_INSTANT_SORCERY_COPY = Regex("""When you next cast an instant or sorcery spell this turn, copy it\. You may choose new targets for the copy\.""")
    val OWNER_PUTS_TOP_OR_BOTTOM = Regex("""The owner of target (.+?) puts it on their choice of the top or bottom of their library\.""")
    val OWNER_PUTS_SECOND_OR_BOTTOM = Regex("""The owner of target (.+?) puts it into their library second from the top or on the bottom\.""")
    val TARGET_GAINS_QUOTED = Regex("""[Tt]arget creature gains "(.+)" until end of turn\.""")
    val GAINS_PROTECTION_EACH_COLOR_UNTIL_NEXT_TURN = Regex("""[Tt]arget creature you control gains protection from each color until your next turn\.""")
    val DRAW_THEN_DISCARD_UNLESS = Regex("""Draw three cards\. Then discard two cards unless you discard a creature card\.""")
    val RETURN_ONE_OR_TWO_THEN_IF_CREATE_FOR_EACH = Regex("""Return one or two target (.+?) to their owners' hands\. Then if you control an? (.+?), create (.+?) for each permanent returned to its owner's hand this way\.""")
    val CREATURES_CONTROLLED_BY_DIFFERENT = Regex("""Choose two target creatures controlled by different players\. Return those creatures to their owners' hands\.""")

    // ecl chunk 12
    val HAVE_QUOTED_KEYWORD = Regex("""(.+?) have "(.+)"""")
    val ALL_HAVE = Regex("""All (creatures) have ([a-z ,]+)\.""")

    // ecl chunk 11
    val ENTERS_WHILE_HAS_COUNTER = Regex("""(.+?) while (.+?) has an? (-1/-1|\+1/\+1) counter on it""")
    val REVEALS_X_YOU_CHOOSE_EXILES = Regex("""Target opponent reveals X cards from their hand, where X is (.+?)\. You choose one of those cards\. That player exiles it\. If an instant or sorcery card is exiled this way, you may cast it for as long as you control (.+?), and mana of any type can be spent to cast that spell\.""")
    val CAST_DURING_OPPONENTS_TURN = Regex("""you cast a spell during an opponent's turn""")
    val DIES_IF_HAD_COUNTER_RETURN_LOSES = Regex("""When (.+?) dies, if it had an? (-1/-1) counter on it, return it to the battlefield under its owner's control and it loses all abilities\.""")
    val GETS_AND_LOSES_ALL_TYPES = Regex("""Target creature gets ([+-]\d+)/([+-]\d+) and loses all creature types until end of turn\.""")
    val WHEN_THAT_CREATURE_DIES_THIS_TURN_CREATE = Regex("""When that creature dies this turn, create (.+)\.""")
    val ENTERED_OR_CAST_FROM_GRAVEYARD = Regex("""Whenever one or more other creatures you control enter, if they entered or were cast from a graveyard, create a token that's a copy of one of them\. This ability triggers only once each turn\.""")
    val RETURN_TWO_SHARING_TYPE = Regex("""Return two target (creature cards) that share a creature type from your graveyard to your hand\.""")
    val TARGET_OPPONENT_EXILES_FROM_HAND = Regex("""Target opponent exiles (two|three) cards from their hand\.""")
    val PLAYERS_CANT_DRAW_OR_GAIN = Regex("""Players can't draw cards or gain life\.""")
    val EACH_DRAW_STEP_LOSES_SEARCHES = Regex("""At the beginning of each player's draw step, that player loses (\d+) life, searches their library for a card, puts it into their hand, then shuffles\.""")
    val CAST_FROM_EXILED_WITH_REMOVING_COUNTERS = Regex("""During your turn, you may cast creature spells from among cards you own exiled with (.+?) by removing (\w+) counters from among creatures you control in addition to paying their other costs\.""")
    val COPY_OF_BLIGHTED_EXCEPT_SACRIFICE = Regex("""create a tapped and attacking token that's a copy of the blighted creature, except it has "At the beginning of the end step, sacrifice this token\."""")

    // ecl: more wordings, by chunk
    val EXCEPT_IT_HAS = Regex("""it has (.+)""")
    // ecl chunk 06
    val AS_ENTERS_CHOOSE_TYPE_AMONG = Regex("""As (.+?) enters, choose ((?:[A-Z]\w+, )+or [A-Z]\w+)\.""")
    val CHOOSE_A_CREATURE_TYPE_THEN = Regex("""Choose a creature type\. (.+)""")
    val ADD_N_ANY_ONE_COLOR_SPEND_ONLY = Regex("""[Aa]dd (\w+) mana of any one color\. Spend this mana only to (.+)\.""")
    val IT_DEALS_INSTEAD_IF =Regex("""It deals (\d+) damage to that creature instead if (.+)\.""")
    val GET_X_X_WHERE = Regex("""(.+?) get \+X/\+X, where X is (.+)\.""")
    val GETS_FOR_EACH_AND_EACH = Regex("""(.+?) gets \+1/\+1 for each (.+?) and each (.+)\.""")
    val SPELLS_OF_CHOSEN_TYPE_COST_LESS = Regex("""Spells you cast of the chosen type cost (\{\d+\}) less to cast\.""")
    val CREATURE_SPELL_TOUGHNESS_GREATER_COSTS_LESS = Regex("""Each creature spell you cast with toughness greater than its power costs (\{\d+\}) less to cast\.""")
    val EACH_SPELL_YOU_CAST_HAS = Regex("""Each (\w+) spell you cast has (\w+)\.""")
    val HEXPROOF_FROM_EACH_OF_ITS_COLORS = Regex("""Each (.+?) has hexproof from each of its colors\.""")
    val TRIGGERS_ADDITIONAL_TIME = Regex("""If a triggered ability of (.+?) triggers, it triggers an additional time\.""")
    val EQUIPPED_GETS_AND_ALL_TYPES = Regex("""Equipped creature gets \+(\d+)/\+(\d+) and is all creature types\.""")
    val FIRST_TIME_TOKENS_COPIES_OF_EQUIPPED = Regex("""As long as (.+?) is attached to a creature, the first time you would create one or more tokens each turn, you may instead create that many tokens that are copies of equipped creature\.""")
    val ONCE_EACH_TURN_CAST_EXILED_WITH = Regex("""Once each turn, you may cast a spell with mana value less than or equal to the number of (.+?) you control from among cards exiled with (.+?) this turn without paying its mana cost\.""")
    val EXILE_UP_TO_ONE_UNTIL_LEAVES = Regex("""[Ee]xile up to one target (.+?) until (.+?) leaves the battlefield\.""")
    val DEALS_TO_ANY_AND_ANY_OTHER = Regex("""(.+?) deals (\d+) damage to any target and (\d+) damage to any other target\.""")
    val DEALS_X_TO_EACH_OPPONENT_AND_THEIR_CREATURES = Regex("""(.+?) deals X damage to each opponent and each creature they control\.""")
    val IT_DEALS_POWER_TO_UP_TO_ONE = Regex("""It deals damage equal to its power to up to one target (.+?)\.""")
    val GAIN_LIFE_EQUAL_TO = Regex("""[Yy]ou gain life equal to (.+)\.""")
    val ENCHANTED_FIGHTS_UP_TO_ONE = Regex("""[Ee]nchanted creature fights up to one target (.+?)\.""")
    val UP_TO_X_TARGETS_GET_X = Regex("""[Uu]p to X target (.+?) get \+X/\+X until end of turn\.""")
    val CREATE_MUTAVAULT = Regex("""[Cc]reate a (tapped )?Mutavault token\.""")
    val UP_TO_ONE_OTHER_LOSES_ALL = Regex("""[Uu]p to one (other target creature) loses all abilities\.""")
    val PUT_KEYWORD_COUNTERS_ON = Regex("""Put (an? [a-z ]+ counter, an? [a-z ]+ counter,? and an? [a-z ]+ counter) on (that creature|it)\.""")
    val DEALS_TO_EACH_CREATURE_TARGET_PLAYER_CONTROLS = Regex("""(.+?) deals (\d+) damage to each creature target player controls\.""")
    val CREATURES_TARGET_PLAYER_CONTROLS_GET_GAIN = Regex("""Creatures target player controls (?:get ([+-]\d+)/([+-]\d+)(?: and )?)?(?:gain ([a-z ,]+?))? until end of turn\.""")
    val MILLS_THEN_PUTS_EACH_INTO_HAND = Regex("""Target player mills (\w+) cards, then puts each (.+?) milled this way into their hand\.""")
    val RETURN_ONE_OR_TWO_FROM_GRAVEYARD = Regex("""Return one or two target (.+?) from your graveyard to your hand\.""")
    val CREATE_FOR_EACH = Regex("""[Cc]reate (an? .+? token(?: with [a-z ]+)?) for each (.+)\.""")
    val EXILE_TOP_OF_TARGET_OPPONENTS_LIBRARY = Regex("""[Ee]xile the top (\w+) cards of target opponent's library\.""")
    val BECOMES_ALL_COLORS = Regex("""Target (.+?) becomes all colors until end of turn\.""")
    val BECOMES_THE_CHOSEN_COLOR = Regex("""(.+?) becomes the chosen color\.""")
    val ADD_X_OR_X = Regex("""Add X \{([WUBRG])\} or X \{([WUBRG])\}\.""")
    val REVEALS_X_YOU_CHOOSE_EXILES_BODY = Regex("""Target opponent reveals X cards from their hand\. You choose one of those cards\. That player exiles it\. If an instant or sorcery card is exiled this way, you may cast it for as long as you control (.+?), and mana of any type can be spent to cast that spell\.""")
    val WHEN_THAT_CREATURE_DIES_THIS_TURN = Regex("""(.+?\.) When that creature dies this turn, (.+)""")
    val DISCARD_IF_YOU_DO = Regex("""Discard a card\. If you do, (.+)""")
    val MAY_PUT_TOTAL_PT_HASTE_SACRIFICE = Regex("""You may put a creature card with total power and toughness (\d+) or less from your hand onto the battlefield\. That creature gains haste\. At the beginning of the next end step, sacrifice that creature\.""")
    val EXILE_TOP_INSTEAD_IF = Regex("""Exile the top (\w+) cards of your library\. If (.+?), exile the top (\w+) cards instead\. (.+)""")
    val MAY_COPY_IT_THOSE_GAIN = Regex("""You may copy it\. If you do, those spells gain (\w+)\. You may choose new targets for the copy\.""")
    val REVEAL_UNTIL_X_PUT_ANY_NUMBER = Regex("""Reveal cards from the top of your library until you reveal X (.+?)\. Put any number of those (?:.+?) onto the battlefield, then put the rest of the revealed cards on the bottom of your library in a random order\.""")
    val SEARCH_HAND_INSTEAD_BATTLEFIELD_IF = Regex("""Search your library for an? (.+?), reveal it, put it into your hand, then shuffle\. If (.+?) and the revealed card is the chosen type, put that card onto the battlefield instead of putting it into your hand\.""")
    val MILL_THEN_MAY_PUT_FROM_AMONG = Regex("""Mill (\w+) cards, then you may (?:return|put) an? (.+?) from among (?:them|the milled cards) (to your hand|on top of your library)\.""")
    val SEARCH_UP_TO_X_REVEAL_HAND = Regex("""Search your library for up to X (.+?)\. Reveal those cards, put them into your hand, then shuffle\.""")
    val CHOOSE_TYPE_THOSE_GAIN = Regex("""Choose a creature type\. (.+?) of that type gain ([a-z ]+) until end of turn\.""")
    val CHOOSE_TWO_DRAW_X_GET_X = Regex("""Choose exactly two (.+?)\. You draw X cards and the chosen creatures get \+X/\+X and gain ([a-z ]+) until end of turn\.""")
    val THEN_YOU_MAY_IF_YOU_DO = Regex("""(.+?), then you may (.+?)\. If you do, (.+)""")
    val EXILE_EQUAL_TO_ITS_POWER_CHOOSE_PLAY = Regex("""exile a number of cards from the top of your library equal to its power, then choose a card exiled this way\. Until the end of your next turn, you may play that card\.""")
    val GETS_X_AND_UP_TO_ONE_GETS = Regex("""[Tt]arget (.+?) gets \+X/\+0 until end of turn and up to one target (.+?) gets -0/-X until end of turn\.""")
    val ATTACKS_OR_BLOCKS_DIFFERENCE = Regex("""Whenever (.+?) attacks or blocks, it gets \+X/\+X until end of turn, where X is the difference between its power and toughness\.""")
    val EXILE_UNTIL_NONLAND_CAST_IF_LIFE = Regex("""Exile cards from the top of your library until you exile a nonland card\. You may cast that card without paying its mana cost if the spell's mana value is less than or equal to the amount of life you gained this turn\. Otherwise, put it into your hand\.""")
    val BECOMES_SUBTYPES_BASE_PT = Regex("""(?:If (.+?) is an? ([A-Z]\w+), )?(.+?) becomes an? ((?:[A-Z]\w+ )*[A-Z]\w+) with base power and toughness (\d+)/(\d+)( and protection from each of your opponents)?\.""")
    val ADD_ANY_COLOR_BECOMES_THAT_COLOR = Regex("""Add one mana of any color\. (.+?) becomes that color until end of turn\.""")
    val EXILE_WITH_DREAM_COUNTER_INSTEAD = Regex("""[Ee]xile that card with an? (\w+) counter on it instead of putting it into your graveyard as it resolves\.""")
    val MAY_CAST_FROM_EXILE_WITH_COUNTERS = Regex("""[Yy]ou may cast a spell from among cards you own in exile with (\w+) counters on them without paying its mana cost\.""")
    val OPPONENTS_EXILE_UNTIL_TOTAL_MV = Regex("""Each opponent exiles cards from the top of their library until they have exiled cards with total mana value (\d+) or greater this way\. Until end of turn, you may cast cards exiled this way without paying their mana costs\.""")
    val REVEAL_UNTIL_X_NONLAND_EXILE_EACH_COLOR = Regex("""Reveal cards from the top of your library until you reveal X nonland cards\. For each of those colors, you may exile a card of that color from among the revealed cards\. Then shuffle\. You may cast the exiled cards this turn\.""")
    val CREATE_COPY_OF_TARGET_EXCEPT = Regex("""Create a token that's a copy of target (.+?), except (it has .+)\.""")
    val CREATE_TAPPED_ATTACKING_COPY_OF_BLIGHTED = Regex("""[Cc]reate a tapped and attacking token that's a copy of the blighted creature, except (it has .+)\.""")
    val MAY_ENTER_AS_COPY_EXCEPT = Regex("""You may have (.+?) enter as a copy of any creature on the battlefield, except (it has .+)\.""")
}

/** Lorwyn Eclipsed (ecl) phrases: values, conditions, nouns and trigger events, tried first by [Values], [Conditions], [Filters]
 * and [Events]. */
internal object EclPhrases {
    private val permanentYouControl: ObjectFilter get() = ObjectFilter.And(listOf(ObjectFilter.Category(ObjectCategory.Permanent), ObjectFilter.ControlledBy(PlayerFilter.You)))

    /** ecl chunk 03: vivid, "the number of colors among permanents you control" (CR 105.4). */
    val colorsAmongPermanents: Value get() = Value.CountDistinct(Objects.Each(permanentYouControl), SharedQuality.Color)

    fun value(text: String): Value? {
        if (text == "the number of colors among permanents you control") return colorsAmongPermanents
        // ecl chunk 09
        if (text == "your life total") return Value.LifeOf(Players.You)
        ENTERED_UNDER_YOUR_CONTROL.matchEntire(text)?.let { m ->
            val filter = Filters.noun(m.groupValues[1]) ?: return null
            return Value.EventsThisTurn(EventPattern.ZoneChange(Filters.all(filter, ObjectFilter.ControlledBy(PlayerFilter.You)), to = Zone.Battlefield), Measure.Objects)
        }
        // ecl chunk 11: "the number of Elves you control plus the number of Elf cards in your graveyard"
        PLUS.matchEntire(text)?.let { m ->
            return Value.Sum(listOf(Values.value(m.groupValues[1]) ?: return null, Values.value(m.groupValues[2]) ?: return null))
        }
        // ecl chunk 13: "the difference between the chosen creatures' powers" (the greater less the smaller)
        if (text == "the difference between the chosen creatures' powers") {
            val chosen = Objects.Bound("chosen")
            return Value.Difference(Value.Of(arcanum.api.NumericProperty.Power, chosen, arcanum.api.Combine.Greatest),
                Value.Of(arcanum.api.NumericProperty.Power, chosen, arcanum.api.Combine.Least))
        }
        // ecl chunk 12: "the greatest toughness among creatures you control"
        if (text.startsWith("the greatest toughness among ")) {
            return Value.Of(arcanum.api.NumericProperty.Toughness, Filters.objects(text.removePrefix("the greatest toughness among ")) ?: return null, arcanum.api.Combine.Greatest)
        }
        return null
    }

    fun condition(text: String, self: Set<String>): Condition? {
        // ecl chunk 04: "it has a -1/-1 counter on it" (this permanent, in its own trigger)
        HAS_COUNTER_ON_IT.matchEntire(text)?.let { m ->
            val subject = m.groupValues[1]
            if (subject != "it" && subject !in self && subject.replaceFirstChar { it.lowercase() } !in self) return null
            return Condition.ObjectMatches(Objects.Self, ObjectFilter.HasCounter(Counters.kind(m.groupValues[2]) ?: return null, atLeast = 1))
        }
        // ecl chunk 11: "there is an Elf card in your graveyard"
        THERE_IS_CARD_IN_YOUR_GRAVEYARD.matchEntire(text)?.let { m ->
            val filter = Filters.noun(m.groupValues[1]) ?: return null
            return Condition.Exists(Filters.all(filter, ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You)), atLeast = 1)
        }
        // ecl chunk 01: "this spell's additional cost was paid" (CR 601.2f)
        if (text == "this spell's additional cost was paid") return Condition.Paid(null, times = 1, of = Objects.Self)
        // ecl chunk 11: "it isn't your main phase" (CR 505.1)
        if (text == "it isn't your main phase") return Condition.Not(Condition.And(listOf(Condition.YourTurn,
            Condition.Or(listOf(Condition.StepIs(TurnStep.PrecombatMain), Condition.StepIs(TurnStep.PostcombatMain))))))
        // ecl chunk 14: "you attacked with three or more Merfolk this turn"
        ATTACKED_WITH_N_THIS_TURN.matchEntire(text)?.let { m ->
            val n = Numbers.count(m.groupValues[1]) ?: return null
            val filter = Filters.noun(m.groupValues[2]) ?: return null
            return Condition.HappenedThisTurn(EventPattern.Attack(Filters.all(filter, ObjectFilter.ControlledBy(PlayerFilter.You))), atLeast = n, measure = Measure.Objects)
        }
        // ecl chunk 15: "there are five colors among permanents you control"
        COLORS_AMONG.matchEntire(text)?.let { m ->
            return Condition.Compare(colorsAmongPermanents, Comparison.AtLeast, Value.Fixed(Numbers.count(m.groupValues[1]) ?: return null))
        }
        // ecl chunk 12: "you put a counter on a creature this turn" (by your spells and abilities)
        PUT_A_COUNTER_THIS_TURN.matchEntire(text)?.let { m ->
            val filter = Filters.noun(m.groupValues[1]) ?: return null
            return Condition.HappenedThisTurn(EventPattern.CountersPut(filter, null, causedBy = ObjectFilter.ControlledBy(PlayerFilter.You)), atLeast = 1, measure = Measure.Events)
        }
        // ecl chunk 10: "a creature is attacking you"
        if (text == "a creature is attacking you") return Condition.Exists(Filters.all(ObjectFilter.Type(CardType.Creature),
            ObjectFilter.HasStatus(arcanum.api.Status.Attacking),
            ObjectFilter.Where(Condition.PlayerMatches(Players.DefendingPlayerOf(Objects.Tested), PlayerFilter.You))), atLeast = 1)
        return null
    }

    fun noun(phrase: String): ObjectFilter? {
        // ecl chunk 09: "cards from graveyards"
        if (phrase == "cards from graveyards" || phrase == "card from a graveyard") return Filters.all(ObjectFilter.Category(ObjectCategory.Card), ObjectFilter.InZone(Zone.Graveyard))
        // ecl chunk 13: "Treefolk and Forests you control"
        AND_YOU_CONTROL.matchEntire(phrase)?.let { m ->
            val a = Filters.noun(m.groupValues[1]) ?: return null
            val b = Filters.noun(m.groupValues[2]) ?: return null
            return Filters.all(ObjectFilter.Or(listOf(a, b)), ObjectFilter.ControlledBy(PlayerFilter.You))
        }
        return null
    }

    fun events(text: String, self: Set<String>): List<EventPattern>? {
        // ecl chunk 06: "this creature enters or transforms into Brigid, Clachan's Heart"
        ENTERS_OR_TRANSFORMS_INTO.matchEntire(text)?.let { m ->
            if (m.groupValues[2] !in self) return null
            val subject = Events.subjectOf(m.groupValues[1], self) ?: return null
            return listOf(EventPattern.ZoneChange(subject, to = Zone.Battlefield), EventPattern.Transform(Filters.all(subject, ObjectFilter.Named(m.groupValues[2]))))
        }
        // ecl chunk 06: "this creature transforms into Ashling Rimebound and at the beginning of your first main phase", "this artifact enters and at
        // the beginning of your upkeep" (one ability, two events)
        AND_AT.matchEntire(text)?.let { m ->
            val first = Events.events(m.groupValues[1], self) ?: return null
            return first + (Events.event(m.groupValues[2], self) ?: return null)
        }
        // ecl chunk 08: "this creature enters or becomes tapped"
        ENTERS_OR_BECOMES_TAPPED.matchEntire(text)?.let { m ->
            val subject = Events.subjectOf(m.groupValues[1], self) ?: return null
            return listOf(EventPattern.ZoneChange(subject, to = Zone.Battlefield), EventPattern.Tap(subject))
        }
        return null
    }

    fun event(text: String, self: Set<String>): EventPattern? {
        // ecl chunk 10: "you cast a spell with mana value 4 or greater", "you cast an instant or sorcery spell from your hand"
        YOU_CAST_FROM_YOUR_HAND.matchEntire(text)?.let { m ->
            return EventPattern.Cast(Filters.noun(m.groupValues[1] + " spell") ?: return null, PlayerFilter.You, from = Zone.Hand)
        }
        YOU_CAST_WITH_SINGLE_TARGET.matchEntire(text)?.let { m ->
            return EventPattern.Cast(Filters.all(Filters.noun(m.groupValues[1] + " spell") ?: return null, ObjectFilter.SingleTarget()), PlayerFilter.You)
        }
        YOU_CAST_SPELL_WITH.matchEntire(text)?.let { m ->
            val spell = Filters.noun("spell with " + m.groupValues[1]) ?: Filters.all(ObjectFilter.Category(ObjectCategory.Spell),
                manaValue(m.groupValues[1]) ?: return null)
            return EventPattern.Cast(spell, PlayerFilter.You)
        }
        // ecl chunk 06: "this creature deals combat damage to a player or planeswalker" (CR 510.2)
        DEALS_COMBAT_DAMAGE_TO_PLAYER_OR_PLANESWALKER.matchEntire(text)?.let { m ->
            val subject = Events.subjectOf(m.groupValues[1], self) ?: return null
            return EventPattern.Damage(subject, arcanum.api.RecipientFilter(objects = ObjectFilter.Type(CardType.Planeswalker), players = PlayerFilter.Any), combat = true)
        }
        // ecl chunk 15: "you cast a spell of the chosen type"
        if (text == "you cast a spell of the chosen type")
            return EventPattern.Cast(ObjectFilter.Sub(arcanum.api.SubtypeRef.Remembered("type")), PlayerFilter.You)
        // ecl chunk 14: "a creature you control with one or more counters on it dies"
        WITH_COUNTERS_DIES.matchEntire(text)?.let { m ->
            val filter = Filters.noun(m.groupValues[1].removeArticle()) ?: return null
            return EventPattern.ZoneChange(Filters.all(filter, ObjectFilter.HasCounter(null, atLeast = 1)), from = Zone.Battlefield, to = Zone.Graveyard)
        }
        return null
    }

    private fun manaValue(text: String): ObjectFilter? {
        val m = MANA_VALUE_N.matchEntire(text) ?: return null
        val comparison = if (m.groupValues[2] == "greater") Comparison.AtLeast else Comparison.AtMost
        return ObjectFilter.ManaValue(comparison, Value.Fixed(m.groupValues[1].toInt()))
    }

    private val ENTERED_UNDER_YOUR_CONTROL = Regex("""the number of (.+?) that entered the battlefield under your control this turn""")
    private val PLUS = Regex("""(the number of .+?) plus (the number of .+)""")
    private val HAS_COUNTER_ON_IT = Regex("""(.+?) has an? (-1/-1|\+1/\+1|stun) counter on it""")
    private val THERE_IS_CARD_IN_YOUR_GRAVEYARD = Regex("""there is an? (.+?) in your graveyard""")
    private val ATTACKED_WITH_N_THIS_TURN = Regex("""you attacked with (two|three|four|five) or more (.+?) this turn""")
    private val COLORS_AMONG = Regex("""there are (two|three|four|five) colors among permanents you control""")
    private val PUT_A_COUNTER_THIS_TURN = Regex("""you put a counter on an? (.+?) this turn""")
    private val AND_YOU_CONTROL = Regex("""([A-Z]\w+) and ([A-Z]\w+) you control""")
    private val ENTERS_OR_TRANSFORMS_INTO = Regex("""(.+?) enters or transforms into (.+)""")
    private val AND_AT = Regex("""(.+?) and at (the beginning of .+)""")
    private val ENTERS_OR_BECOMES_TAPPED =Regex("""(.+?) enters or becomes tapped""")
    private val YOU_CAST_FROM_YOUR_HAND = Regex("""you cast an? (.+?) spell from your hand""")
    private val YOU_CAST_WITH_SINGLE_TARGET = Regex("""you cast an? (.+?) spell with a single target""")
    private val YOU_CAST_SPELL_WITH = Regex("""you cast a spell with (mana value \d+ or (?:greater|less))""")
    private val DEALS_COMBAT_DAMAGE_TO_PLAYER_OR_PLANESWALKER = Regex("""(.+?) deals combat damage to a player or planeswalker""")
    private val WITH_COUNTERS_DIES =Regex("""(.+?) with one or more counters on it dies""")
    private val MANA_VALUE_N = Regex("""mana value (\d+) or (greater|less)""")
}
