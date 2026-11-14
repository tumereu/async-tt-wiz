package arcanum.parser

import arcanum.api.CardType
import arcanum.api.Combine
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.Measure
import arcanum.api.NumericProperty
import arcanum.api.ObjectCategory
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.SharedQuality
import arcanum.api.Value
import arcanum.api.Zone

/** Regular expressions of Secrets of Strixhaven (sos) wordings read in [Sentences]. */
internal object Sos {
    // sos chunk 01: converge (an ability word, CR 207.2c)
    val ENTERS_WITH_COUNTER_FOR_EACH_COLOR = Regex("""(.+) enters with an? (\S+) counter on it for each color of mana spent to cast it\.""")
    val CREATURE_SPELL_ENTERS_WITH_X_MORE = Regex("""[Tt]hat creature enters with X additional (\S+) counters on it\.""")
    val A_B_AND_C = Regex("""(.+?), (.+?), and (.+)\.""")
    val IF_DREW_THIS_WAY = Regex("""If you draw one or more cards this way, (.+)\.""")
    val CREATE_TOKENS_PUT_X_ON_EACH_AND_GAIN = Regex("""Put X (\S+) counters on each of them and you gain X life\.""")
    val EXCESS_EXILE_TOP_PLAY_UNTIL_NEXT_TURN = Regex("""(.+?) deals X damage to target (.+?)\. Exile cards from the top of your library equal to the excess damage dealt to that creature this way\. You may play those cards until the end of your next turn\.""")

    // sos chunk 02: opus (an ability word): "If five or more mana was spent to cast that spell, …"
    val IF_INSTEAD = Regex("""If (.+?), (.+) instead\.""")
    val IF_THEN = Regex("""If (.+?), (.+)\.""")
    val UNLESS_CONDITION = Regex("""(.+) unless (.+)\.""")
    val ADD_AMOUNT_EQUAL_TO_POWER = Regex("""[Aa]dd an amount of (\{[WUBRGC]\}) equal to (.+?)'s power\.""")
    val CREATE_COPY_OF_SELF = Regex("""[Cc]reate a token that's a copy of (.+)\.""")
    val IF_EXILE_TOP_PLAY_UNTIL_NEXT_TURN = Regex("""(?:(.+?\.) )?If (.+?), exile the top card of your library\. You may play that card until the end of your next turn\.""")

    // sos chunk 03: paradigm (CR 702.192) and spells exiling themselves
    val EXILE_SELF = Regex("""Exile (.+)\.""")
    val YOU_HAVE_NO_MAXIMUM_HAND_SIZE_REST_OF_GAME = Regex("""You have no maximum hand size for the rest of the game\.""")
    val RETURN_ALL_FROM_GRAVEYARD_TO_HAND = Regex("""Return all (.+?) from your graveyard to your hand\.""")
    val RETURN_UP_TO_X_TARGET = Regex("""Return up to X target (.+?) from your graveyard to your hand\.""")
    val TARGET_PLAYER_CREATES_COPY_OF_TARGET = Regex("""Target player creates a token that's a copy of target (.+)\.""")
    val EXILE_UNTIL_TOTAL_MANA_VALUE_CAST_ANY = Regex("""Exile cards from the top of your library until you exile cards with total mana value (\d+) or greater\. You may cast any number of spells from among them without paying their mana costs\.""")

    // sos chunk 04: stun counters (CR 122.1d) and tapping
    val TAP_IF_YOUR_TURN_STUN = Regex("""Tap target (.+?)\. If it's your turn, put a stun counter on it\.""")
    val TAP_PUT_TWICE_X_STUN = Regex("""Tap target (.+?)\. Put twice X stun counters on it\.""")
    val TAP_UP_TO_PUT_STUN_ON_EACH = Regex("""Tap up to (\w+) target (.+?)\. Put (\w+) stun counters? on each of them\.""")
    val ENTERS_WITH_STUN_THREE_MINUS_X = Regex("""(.+) enters with a number of stun counters on it equal to (\w+) minus X\. If X is (\d+) or less, it enters tapped\.""")

    // sos chunk 05: infusion (an ability word)
    val LOOK_REVEAL_ONE_OR_IF_TWO = Regex("""Look at the top (\w+) cards of your library\. You may reveal an? (.+?) from among them and put it into your hand\. If (.+?), you may instead reveal (\w+) (.+?) from among them and put them into your hand\. Put the rest on the bottom of your library in a random order\.""")
    val WHEN_YOU_CAST_COPY_IF = Regex("""When you cast this spell, copy it if (.+?)\. You may choose new targets for the copy\.""")
    val WHEN_YOU_CAST_WHILE_MAY_COPY = Regex("""When you cast this spell while (.+?), you may copy this spell\.""")
    val TARGET_GETS_UNTIL_EOT_IF = Regex("""(.+) until end of turn if (.+)\.""")
    val PUT_COUNTERS_ON_IT_IF = Regex("""[Pp]ut (\w+) (\S+) counters? on it if (.+)\.""")
    val RETURN_UP_TO_ONE_MANA_VALUE_X = Regex("""[Rr]eturn up to one target (.+?) with mana value X or less from your graveyard to the battlefield\.""")

    // sos chunk 06: looking and revealing
    val LOOK_PUT_ONE_OR_TWO_IF = Regex("""Look at the top (\w+) cards of your library\. Put one of them into your hand and the rest on the bottom of your library in any order\. If (.+?), instead put two of them into your hand and the rest on the bottom of your library in any order\.""")
    val LOOK_TOP_X_PUT_ONE_REST_BOTTOM_RANDOM = Regex("""[Ll]ook at the top X cards of your library\. Put one of them into your hand and the rest on the bottom of your library in a random order\.""")
    val LOOK_TOP_X_PUT_ONE_REST_GRAVEYARD = Regex("""[Ll]ook at the top X cards of your library\. Put one of those cards into your hand and the rest into your graveyard\.""")
    val LOOK_TOP_N_PUT_ONE_OTHER = Regex("""Look at the top (\w+) cards of your library\. Put one of (?:them|those cards) into your hand and the other (on the bottom of your library|into your graveyard)\.""")
    val LOOK_REVEAL_UP_TO_LANDS_BATTLEFIELD_CREATURES_HAND = Regex("""Look at the top (\w+) cards of your library\. You may reveal up to (\w+) (.+?) from among them, then put the rest on the bottom of your library in a random order\. Put all land cards revealed this way onto the battlefield tapped and put all creature cards revealed this way into your hand\.""")
    val LOOK_REVEAL_LAND_OR_X = Regex("""[Ll]ook at the top (\w+) cards of your library\. You may reveal a land card or a card with \{X\} in its mana cost from among them and put it into your hand\. Put the rest on the bottom of your library in a random order\.""")
    val REVEAL_UNTIL_PUT_HAND_REST_BOTTOM = Regex("""Reveal cards from the top of your library until you reveal an? (.+?)\. Put that card into your hand and the rest on the bottom of your library in a random order\.""")
    val MILL_PUT_FROM_AMONG_ONTO_BATTLEFIELD = Regex("""Mill (\w+) cards\. Then put an? (.+?) from among them onto the battlefield\.""")
    val MILL_MAY_PLAY_THIS_TURN = Regex("""[Mm]ill a card\. You may play that card this turn\.""")
    val SEARCH_PUT_HAND_OR_GRAVEYARD = Regex("""Search your library for an? (.+?), reveal it, put it into your hand or graveyard, then shuffle\.""")

    // sos chunk 07: hands and discards
    val DISCARD_HAND_DRAW_EQUAL_OPPONENT_HAND = Regex("""Discard your hand, then draw cards equal to the number of cards in target opponent's hand\.""")
    val DISCARD_HAND_DRAW_EQUAL_DISCARDED = Regex("""(Discard your hand|Discard all the cards in your hand|Target player discards all the cards in their hand), then (?:draw cards equal to the number of cards discarded this way|draws? that many cards)\.""") // bro chunk 08: Queen Kayla bin-Kroog
    val DISCARD_ANY_NUMBER_DRAW_THAT_MANY = Regex("""(Target player discards|[Dd]iscard) any number of cards, then (?:draws?) that many cards( plus one)?\.""")
    val DISCARDS_PUT_LAND_ONTO_BATTLEFIELD = Regex("""Target player discards (\w+) cards\. Put up to one land card discarded this way onto the battlefield tapped under your control\.""")
    val ANY_NUMBER_OF_TARGET_PLAYERS_EACH_DISCARD = Regex("""Any number of target players each discard a card\.""")
    val EACH_PLAYER_LOSES_HALF = Regex("""Each player loses half their life, then discards half the cards in their hand, then sacrifices half the permanents they control of their choice\. Round down each time\.""")
    val TARGET_OPPONENT_EXILES_GREATEST = Regex("""Target opponent exiles an? (.+?) they control with the greatest mana value among (.+?) they control\.""")

    // sos chunk 08: graveyards, flashback and casting from exile
    val EXILE_TARGET_MAY_CAST_UNTIL_NEXT_TURN = Regex("""[Ee]xile target (.+?) from your graveyard\. Until the end of your next turn, you may cast that card\.""")
    val EXILE_TARGET_AND_TOP_EACH_OWNER_MAY_PLAY = Regex("""Exile target (.+?) and the top card of your library\. For each of those cards, its owner may play it until the end of their next turn\.""")
    val EXILE_FROM_OPPONENT_GRAVEYARD_MAY_CAST = Regex("""Exile target (.+?) from an opponent's graveyard\. You may cast it this turn, and mana of any type can be spent to cast that spell\. If that spell would be put into a graveyard, exile it instead\.""")
    val PUT_TARGET_FROM_A_GRAVEYARD_LOSE_LIFE = Regex("""Put target (.+?) from a graveyard onto the battlefield under your control\. You lose life equal to that card's mana value\.""")
    val EXILE_THEN_RETURN_WITH_COUNTER = Regex("""Exile target (.+?), then return that card to the battlefield under its owner's control with an? (\S+) counter on it\.""")
    val RETURN_EACH_WITH_MANA_VALUE_X = Regex("""Return each (.+?) with mana value X from your graveyard to the battlefield\.""")
    val DESTROY_ALL_WITH_MANA_VALUE_X_OR_LESS = Regex("""Destroy all (.+?) with mana value X or less\.""")

    // sos chunk 09: counters
    val MOVE_X_COUNTERS_ONTO_ANOTHER = Regex("""At the beginning of combat on your turn, you may pay \{X\}\. When you do, move X (\S+) counters from (.+?) onto another target (.+)\.""")
    val PUT_COUNTER_THEN_DOUBLE = Regex("""Put an? (\S+) counter on target (.+?), then double the number of (\S+) counters on that creature\.""")
    val CREATE_TOKEN_PUT_COUNTERS_OF_SELF = Regex("""[Cc]reate (.+?) token, then put (.+?)'s counters on that token\.""")
    val AS_LONG_AS_N_COUNTERS_GROUP_GETS = Regex("""As long as there are (\w+) or more (\S+) counters on (.+?), (.+?) get ([+-]\d+)/([+-]\d+)\.""")
    val CREATE_TOKEN_PUT_X_WHERE_DIFFERENT_NAMES = Regex("""Create (.+?) token\. Put X (\S+) counters on it\.""")

    // sos chunk 10: casting, copying and keywords given to spells
    val CAST_SPELL_WITH_X = Regex("""you cast a spell with \{X\} in its mana cost""")
    val CAST_SPELL_YOU_DONT_OWN = Regex("""you cast a spell you don't own""")
    val CANT_BE_COPIED = Regex("""This spell can't be copied\.""")
    val COPY_TARGET_SPELL_GAINS = Regex("""Copy target (.+?) spell you control\. The copy gains (\w+) and "(.+)"\.?""")
    val SPELLS_HAVE_KEYWORD = Regex("""(Instant and sorcery spells you cast(?: from your hand)?|Each instant and sorcery spell you cast) (?:have|has) (.+?)\.""")
    val CARDS_IN_HAND_HAVE_KEYWORD = Regex("""Each (.+?) card in your hand has (.+?)\.""")
    val ONCE_EACH_TURN_CAST_FREE = Regex("""Once during each of your turns, you may cast an? (.+?) from your hand( or the top of your library)? without paying its mana cost\.""")
    val CAST_TRIGGER_MAY_SACRIFICE_COPY = Regex("""[Yy]ou may sacrifice an? (.+?)\. If you do, copy that spell and you may choose new targets for the copy\.""")
    val CAST_TRIGGER_MAY_TAP_COPY = Regex("""[Yy]ou may tap (\w+) untapped (.+?) you control\. If you do, copy that spell\. You may choose new targets for the copy\.""")

    // sos chunk 11: becoming things
    val UNTIL_EOT_BECOMES_FRACTAL_X_PLUS = Regex("""Until end of turn, target (.+?) becomes a (\w+) and (\w+) (\w+) with base power and toughness each equal to X plus (\d+)\.""")
    val IF_NOT_CREATURE_BECOMES = Regex("""If (.+?) isn't a creature, it becomes a (\d+)/(\d+) (\w+) creature with "(.+)"\.?""")
    val COPY_EXCEPT_FRACTAL_PUT_COUNTERS = Regex("""Create a token that's a copy of target (.+?), except it's a 0/0 (\w+) creature in addition to its other types\. Put (\w+) (\S+) counters on it\.""")
    val LANDS_WITH_CHOSEN_NAME_HAVE = Regex("""(.+?) with the chosen name have "(.+)"""")

    // sos chunk 12: modes and multi-target spells
    val CHOOSE_UP_TO_SAME_MODE = Regex("""Choose up to (\w+)\. You may choose the same mode more than once\.""")
    val ONE_OR_TWO_TARGET_EACH_GET = Regex("""One or two target (.+?) each get ([+-]\d+)/([+-]\d+) until end of turn\.""")
    val DEALS_DAMAGE_TO_EACH_OF_ONE_OR_TWO = Regex("""(.+?) deals (\d+) damage to each of one or two targets\.""")
    val ANY_NUMBER_TARGET_EACH_GET_AND_GAIN = Regex("""Until end of turn, any number of target (.+?) each get ([+-]\d+)/([+-]\d+) and gain "(.+)"\.?""")
    val GROUP_GETS_GAINS_KEYWORD_AND_QUOTED = Regex("""Until end of turn, (.+?) get ([+-]\d+)/([+-]\d+) and gain (\w+) and "(.+)"\.?""")
    val DISCARDS_ANY_THEN_DRAWS_THAT_MANY = Regex("""Target player discards any number of cards, then draws that many cards\.""")

    // sos chunk 13: skipping turns, powers of two and other one-offs
    val FLIP_COINS_SKIP_TURNS = Regex("""Flip (\w+) coins\. Target opponent skips their next X turns, where X is the number of coins that came up heads\.""")
    val DRAWS_TWO_TO_THE_X = Regex("""Target player draws 2ˣ cards\.""")
    val COUNTER_ADD_C_NEXT_MAIN = Regex("""Counter target spell\. If you control an? (.+?), add an amount of \{C\} equal to the amount of mana spent to cast that spell at the beginning of your next main phase\.""")
    val ADD_TWO_MANA_ANY_ONE_COLOR_SPEND_ONLY = Regex("""\{T\}: Add two mana of any one color\. Spend this mana only to cast instant and sorcery spells\.""")
    val SACRIFICE_LANDS_SEARCH_BASICS = Regex("""Sacrifice (\w+) lands\. Search your library for (\w+) basic land cards, put them onto the battlefield tapped, then shuffle\.""")
    val TARGET_GETS_IF_CAST_ANOTHER_THEN_DEALS = Regex("""Target (.+?) gets ([+-]\d+)/([+-]\d+) until end of turn if you've cast another (.+?) spell this turn\. Then it deals damage equal to its power to up to one target (.+)\.""")
    val TARGET_PLAYER_DISCARDS_X = Regex("""Target player discards X cards\.""")
    val ANOTHER_TARGET_COUNTER = Regex("""[Pp]ut an? (\S+) counter on another target (.+)\.""")
    val THAT_PLAYER_MILLS = Regex("""[Tt]hat player mills (\w+) cards\.""")
    val PUT_COUNTER_ON_EACH_OF_THEM = Regex("""Put an? (\S+) counter on each of them\.""")
    val THAT_CREATURES_CONTROLLER_LOSES = Regex("""[Tt]hat creature's controller loses (\d+) life\.""")
    val GROUP_GETS_FOR_EACH_COLOR_SPENT = Regex("""(.+?) get ([+-]\d+)/([+-]\d+) until end of turn for each color of mana spent to cast that spell\.""")
    val PLUS_NAMED_IN_GRAVEYARD = Regex("""(\d+) plus the number of cards named (.+) in your graveyard""")
    val DEALS_DAMAGE_EQUAL_TO_TARGET = Regex("""(.+?) deals damage equal to (.+?) to target (.+)\.""")
    val CREATE_TOKEN_AND_PUT_COUNTERS_ON_IT = Regex("""[Cc]reate (an? .+?) token and put (X|\w+) (\S+) counters on it\.""")
    val IT_GAINS_UNTIL_EOT = Regex("""It gains (.+) until end of turn\.""")
    val GRANDEUR = Regex("""Grandeur — Discard another card named (.+?): (.+)""")
    val LANDS_HAVE_QUOTED = Regex("""(Lands you control) have "(.+)"""")
    val PUT_X_COUNTERS_ON_IT = Regex("""Put (X|\w+) (\S+) counters on it\.""")
    val WHEN_ENTERS_CHOOSE_CARD_NAME = Regex("""When (.+?) enters, choose an? (.+?) card name\.""")
}

/** Conditions, values and nouns of Secrets of Strixhaven (sos) wordings. */
internal object SosPhrases {
    /** The spell a cast trigger is about ("that spell"). */
    private val thatSpell: Objects = Objects.EventObject(EventRole.Object)

    fun condition(text: String, self: Set<String>): Condition? {
        fun isSelf(t: String) = t in self || t.replaceFirstChar { it.lowercase() } in self
        // sos chunk 02: opus, "five or more mana was spent to cast that spell" (CR 601.2h)
        FIVE_OR_MORE_MANA_SPENT.matchEntire(text)?.let { m ->
            return Condition.Compare(Value.ManaSpent(of = thatSpell), Comparison.AtLeast, Value.Fixed(Numbers.count(m.groupValues[1]) ?: return null))
        }
        // sos chunk 05: "you gained 2 or more life this turn" (counted by the life gained, CR 119.9)
        GAINED_N_OR_MORE_LIFE.matchEntire(text)?.let { m ->
            return Condition.HappenedThisTurn(EventPattern.GainLife(PlayerFilter.You), atLeast = m.groupValues[1].toInt(), measure = Measure.Amount)
        }
        // sos chunk 08: "one or more cards were put into exile this turn" (from anywhere; tokens aren't cards)
        if (text == "one or more cards were put into exile this turn")
            return Condition.HappenedThisTurn(EventPattern.ZoneChange(ObjectFilter.Category(ObjectCategory.Card), to = Zone.Exile), atLeast = 1, measure = Measure.Events)
        // sos chunk 08: "this spell was cast from anywhere other than your hand"
        if (text == "this spell was cast from anywhere other than your hand")
            return Condition.Not(Condition.CastFrom(Zone.Hand, Objects.Self))
        // sos chunk 13: "creatures you control have total toughness 10 or greater"
        TOTAL_TOUGHNESS.matchEntire(text)?.let { m ->
            return Condition.Compare(Value.Of(NumericProperty.Toughness, Filters.objects(m.groupValues[1]) ?: return null, Combine.Total),
                Comparison.AtLeast, Value.Fixed(m.groupValues[2].toInt()))
        }
        // sos chunk 10: "you've cast an instant or sorcery spell this turn"
        CAST_THIS_TURN.matchEntire(text)?.let { m ->
            val spells = Filters.noun(m.groupValues[1]) ?: return null
            return Condition.HappenedThisTurn(EventPattern.Cast(spells, PlayerFilter.You), atLeast = 1, measure = Measure.Events)
        }
        // sos chunk 13: "you have seven or more cards in your hand"
        CARDS_IN_YOUR_HAND.matchEntire(text)?.let { m ->
            return Condition.Compare(Value.HandSize(Players.You), Comparison.AtLeast, Value.Fixed(Numbers.count(m.groupValues[1]) ?: return null))
        }
        // sos chunk 06: "there is an instant card and a sorcery card in your graveyard"
        THERE_IS_A_AND_B_IN_GRAVEYARD.matchEntire(text)?.let { m ->
            fun exists(noun: String) = Filters.noun(noun)?.let { Condition.Exists(Filters.all(it, ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You)), atLeast = 1) }
            return Condition.And(listOf(exists(m.groupValues[1]) ?: return null, exists(m.groupValues[2]) ?: return null))
        }
        // sos chunk 09: "a creature died under your control this turn"
        DIED_UNDER_YOUR_CONTROL.matchEntire(text)?.let { m ->
            return Condition.HappenedThisTurn(EventPattern.ZoneChange(Filters.all(Filters.noun(m.groupValues[1]) ?: return null,
                ObjectFilter.ControlledBy(PlayerFilter.You)), from = Zone.Battlefield, to = Zone.Graveyard), atLeast = 1, measure = Measure.Events)
        }
        // sos chunk 09: "you put a counter on this creature this turn" (by your spells and abilities)
        YOU_PUT_COUNTER_ON_THIS_TURN.matchEntire(text)?.takeIf { isSelf(it.groupValues[1]) }?.let {
            return Condition.HappenedThisTurn(EventPattern.CountersPut(ObjectFilter.Is(Objects.Self), null, causedBy = ObjectFilter.ControlledBy(PlayerFilter.You)),
                atLeast = 1, measure = Measure.Events)
        }
        // sos chunk 09: "it had one or more counters on it" (a creature that died, as it last existed, CR 603.10a)
        if (text == "it had one or more counters on it")
            return Condition.ObjectMatches(Objects.EventObject(EventRole.Before), ObjectFilter.HasCounter(null, atLeast = 1))
        // sos chunk 11: "this land isn't a creature"
        ISNT_A_CREATURE.matchEntire(text)?.takeIf { isSelf(it.groupValues[1]) }?.let {
            return Condition.Not(Condition.ObjectMatches(Objects.Self, ObjectFilter.Type(CardType.Creature)))
        }
        // sos chunk 04: "it's your turn"
        if (text == "it's your turn") return Condition.YourTurn
        return null
    }

    fun value(text: String): Value? {
        // sos chunk 01: converge, "the number of colors of mana spent to cast this spell" (CR 601.2h; a permanent keeps it, CR 400.7d)
        COLORS_SPENT.matchEntire(text)?.let { m ->
            return Value.ManaSpent(distinctColors = true, of = if (m.groupValues[1] == "that spell") thatSpell else Objects.Self)
        }
        // sos chunk 02: "the amount of mana spent to cast that spell"
        MANA_SPENT.matchEntire(text)?.let { m ->
            return Value.ManaSpent(of = if (m.groupValues[1] == "that spell") thatSpell else Objects.Self)
        }
        // sos chunk 12: "the number of instant and sorcery cards in your graveyard"
        if (text == "the number of instant and sorcery cards in your graveyard")
            return Value.Count(Objects.Each(Filters.all(ObjectFilter.Or(listOf(ObjectFilter.Type(CardType.Instant), ObjectFilter.Type(CardType.Sorcery))),
                ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.You))))
        // sos chunk 09: "the number of cards you've drawn this turn" (one draw is one card, CR 121.2)
        if (text == "the number of cards you've drawn this turn") return Value.EventsThisTurn(EventPattern.Draw(PlayerFilter.You), Measure.Events)
        // sos chunk 05: "the amount of life you gained this turn"
        if (text == "the amount of life you gained this turn") return Value.EventsThisTurn(EventPattern.GainLife(PlayerFilter.You), Measure.Amount)
        // sos chunk 09: "the number of differently named lands you control"
        DIFFERENTLY_NAMED.matchEntire(text)?.let { m ->
            return Value.CountDistinct(Filters.objects(m.groupValues[1]) ?: return null, SharedQuality.Name)
        }
        return null
    }

    /** sos nouns, tried first in [Filters.noun]. */
    fun noun(phrase: String): ObjectFilter? {
        // sos chunk 01: "nonland permanent an opponent controls with mana value less than or equal to the number of colors of mana spent to
        // cast this creature"
        MANA_VALUE_AT_MOST_VALUE.matchEntire(phrase)?.let { m ->
            val value = value(m.groupValues[2]) ?: return null
            return Filters.all(Filters.noun(m.groupValues[1]) ?: return null, ObjectFilter.ManaValue(Comparison.AtMost, value))
        }
        // sos chunk 03: "instant and/or sorcery card" (one card of either type)
        if (phrase == "instant and/or sorcery card" || phrase == "instant and/or sorcery cards")
            return ObjectFilter.Or(listOf(ObjectFilter.Type(CardType.Instant), ObjectFilter.Type(CardType.Sorcery)))
        // sos chunk 03: "instant and sorcery card", "instant and sorcery cards" (each a card of either type)
        if (phrase == "instant and sorcery card" || phrase == "instant and sorcery cards")
            return ObjectFilter.Or(listOf(ObjectFilter.Type(CardType.Instant), ObjectFilter.Type(CardType.Sorcery)))
        // sos chunk 06: "creature and/or land card", "creature and/or land cards"
        if (phrase == "creature and/or land card" || phrase == "creature and/or land cards")
            return ObjectFilter.Or(listOf(ObjectFilter.Type(CardType.Creature), ObjectFilter.Type(CardType.Land)))
        return null
    }

    private val FIVE_OR_MORE_MANA_SPENT = Regex("""(\w+) or more mana was spent to cast that spell""")
    private val GAINED_N_OR_MORE_LIFE = Regex("""you gained (\d+) or more life this turn""")
    private val TOTAL_TOUGHNESS = Regex("""(.+) have total toughness (\d+) or greater""")
    private val CAST_THIS_TURN = Regex("""you've cast an? (.+?) spell this turn""")
    private val CARDS_IN_YOUR_HAND = Regex("""you have (\w+) or more cards in your hand""")
    private val THERE_IS_A_AND_B_IN_GRAVEYARD = Regex("""there is an? (.+?) and an? (.+?) in your graveyard""")
    private val DIED_UNDER_YOUR_CONTROL = Regex("""an? (.+?) died under your control this turn""")
    private val YOU_PUT_COUNTER_ON_THIS_TURN = Regex("""you put a counter on (.+) this turn""")
    private val ISNT_A_CREATURE = Regex("""(.+) isn't a creature""")
    private val COLORS_SPENT = Regex("""the number of colors of mana spent to cast (this spell|this creature|it|that spell)""")
    private val MANA_SPENT = Regex("""the amount of mana spent to cast (this spell|that spell)""")
    private val DIFFERENTLY_NAMED = Regex("""the number of differently named (.+)""")
    private val MANA_VALUE_AT_MOST_VALUE = Regex("""(.+) with mana value less than or equal to (.+)""")
}
