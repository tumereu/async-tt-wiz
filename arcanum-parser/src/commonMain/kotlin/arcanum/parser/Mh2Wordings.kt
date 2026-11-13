package arcanum.parser

import arcanum.api.Combine
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Value
import arcanum.api.Zone

/** Regular expressions of Modern Horizons 2 (mh2) wordings read in [Sentences]. */
internal object Mh2 {
    // mh2 chunk 01
    val REMOVE_X_COUNTERS_FROM_SELF = Regex("""Remove X (\S+) counters from (.+)""")
    val THEN_ATTACH_SELF_TO_IT = Regex("""(.+), then attach (.+?) to it\.""")
    val IF_ITS_A_THEN = Regex("""If it's an? (.+?), (it .+)""")
    val YOU_GAIN_HEXPROOF_UNTIL_YOUR_NEXT_TURN = Regex("""You gain hexproof until your next turn\.""")
    val PUT_TARGET_CARD_FROM_OPPONENTS_GRAVEYARD_ON_BOTTOM = Regex("""[Pp]ut target card from an opponent's graveyard on the bottom of their library\.""")
    // mh2 chunk 02
    val REVEAL_SELF_FROM_HAND = Regex("""Reveal (.+) from your hand""")
    val UNTAP_ALL_THEN_PHASE_OUT_COUNTER_EACH = Regex("""Untap all (\w+), then those \1 phase out until (.+?) leaves the battlefield\. Put an? (\S+) counter on (.+?) for each (.+?) that phased out this way\.""")
    val EXILE_ALL_CARDS_OF_TWO_COLORS_FROM_ALL_GRAVEYARDS = Regex("""[Ee]xile all cards that are (\w+) or (\w+) from all graveyards\.""")
    val TWO_COLORS_WOULD_BE_PUT_INTO_GRAVEYARD_EXILE = Regex("""If an? (\w+) or (\w+) permanent, spell, or card not on the battlefield would be put into a graveyard, exile it instead\.""")
    val AS_ENTERS_CHOOSE_CARD_TYPE = Regex("""As (.+) enters, choose a card type\.""")
    val YOU_AND_HAVE_PROTECTION_FROM_CHOSEN_CARD_TYPE = Regex("""You and (.+) have protection from the chosen card type\.""")
    val IS_ON_BATTLEFIELD_OR_IN_YOUR_GRAVEYARD = Regex("""(.+) is on the battlefield or in your graveyard""")
    val EXILE_TARGET_FROM_YOUR_GRAVEYARD = Regex("""Exile target (.+? card) from your graveyard\.""")
    val EXILED_CARDS_NUMBER = Regex("""the exiled card's (power|toughness)""")
    val PUT_THAT_MANY_COUNTERS_ON = Regex("""([Yy]ou may )?[Pp]ut that many (\S+) counters on (.+)\.""")
    // mh2 chunk 03
    val X_TARGET_GAIN_UNTIL_EOT = Regex("""X target (.+?) gain (.+) until end of turn\.""")
    val LOSE_ALL_ABILITIES = Regex("""(.+) lose all abilities\.""")
    val ENTERS_WITH_COUNTER_FOR_EACH_EXILED_WITH_IT = Regex("""(.+) enters with an? (\S+) counter on it for each (.+?) card exiled with it\.""")
    val SWITCH_PT_UNTIL_EOT = Regex("""Switch (.+?)'s power and toughness until end of turn\.""")
    val SEARCH_OPPONENTS_LIBRARY_PUT_UNDER_YOUR_CONTROL = Regex("""Search target opponent's library for an? (.+?) card and put that card onto the battlefield under your control\. Then that player shuffles\.""")
    val BECOMES_THE_TARGET_OF_A_SPELL_OR_ABILITY = Regex("""(.+) becomes the target of a spell( or ability)?""")
    // mh2 chunk 04
    val CHOOSE_TARGET_OWNER_PUTS_TOP_OR_BOTTOM = Regex("""Choose up to one target (.+?)\. Its owner puts it on their choice of the top or bottom of their library\.""")
    val BECOMES_ARTIFACT_CREATURE_PUT_COUNTERS_ON_EACH_THAT_BECAME = Regex("""(Target|Each) (.+?) becomes an? (\d+)/(\d+) artifact creature\. Put (\w+) (\S+) counters on each artifact that became a creature this way\.""")
    val PUT_INTO_OWNERS_LIBRARY_NTH_FROM_TOP = Regex("""Put (.+?) into its owner's library (\w+) from the top\.""")
    val RETURN_N_TARGETS_TO_OWNERS_HANDS = Regex("""[Rr]eturn (two|three) target (.+?) to their (?:owners' hands|owner's hand)\.""")
    val EXILE_TARGET_PUT_TIME_COUNTERS_GAINS_SUSPEND = Regex("""Exile target (.+?) and put (\w+) time counters on it\. If it doesn't have suspend, it gains suspend\.""")
    val CREATE_FOR_EACH_COLOR_SPENT = Regex("""Create (an? .+? token(?: with .+?)?) for each color of mana spent to cast this spell\.""")
    val THOSE_DONT_UNTAP_DURING_NEXT = Regex("""(?:Those [\w ]+?|They) don't untap during their controller's next untap step\.""")
    val TARGET_BECOMES_LAND_TYPE_AS_LONG_AS_REMAINS = Regex("""[Tt]arget (\w+) becomes an? (\w+) for as long as (.+?) remains on the battlefield\.""")
    val AN_OPPONENT_CONTROLS_A = Regex("""an opponent controls an? (.+)""")
    // mh2 chunk 05
    val OPPONENT_SACRIFICES_DISCARDS_LOSES_YOU_DRAW_GAIN = Regex("""[Tt]arget opponent sacrifices an? (.+?) of their choice, discards a card, and loses (\d+) life\. You draw a card and gain (\d+) life\.""")
    val GAIN_LIFE_EQUAL_TO_COUNTERS_ON_IT = Regex("""You gain life equal to the number of counters on it\.""")
    val LOOK_TOP_ONE_HAND_REST_GRAVEYARD_GAIN_GREATEST_POWER = Regex("""Look at the top (\w+) cards of your library\. Put one of them into your hand and the rest into your graveyard\. You gain life equal to the greatest power among creature cards put into your graveyard this way\.""")
    val CARD_TO_OPPONENTS_GRAVEYARD_EXILE_WITH_COUNTER = Regex("""If a card would be put into an opponent's graveyard from anywhere, instead exile it with an? (\S+) counter on it\.""")
    val CHOOSE_EXILED_CARD_OPPONENT_OWNS_WITH_COUNTER_MAY_PLAY_FREE = Regex("""Choose an exiled card an opponent owns with an? (\S+) counter on it\. You may play it this turn without paying its mana cost\.""")
    val RETURN_TARGET_AND_ALL_SAME_NAME = Regex("""Return target (.+? card) and all other cards with the same name as that card from your graveyard to your hand\.""")
    val THAT_IS_SNOW_OR_COULD_PRODUCE = Regex("""(.+?) that is snow or could produce (\{[A-Z]\})""")
    val EXPLOITS_A_CREATURE = Regex("""(.+) exploits a creature""")
    // mh2 chunk 06
    val TARGET_OPPONENT_DISCARDS_AT_RANDOM = Regex("""[Tt]arget opponent discards (\w+) cards? at random\.""")
    val REVEALS_HAND_CHOOSE_CARD_DISCARDS_THEN_AT_RANDOM = Regex("""Target opponent reveals their hand\. You choose a card from it\. That player discards that card, then discards a card at random\.""")
    val CARDS_IN_ALL_GRAVEYARDS = Regex("""(.+?) cards in all graveyards""")
    val THAT_PLAYER_DISCARDS_A_CARD = Regex("""[Tt]hat player discards a card\.""")
    val CREATE_NUMBER_EQUAL_TO_DEVOTION = Regex("""[Cc]reate a number of (.+? tokens?(?: with .+?)?) equal to your devotion to (\w+)\.""")
    val ESCAPES_WITH_CHOICE_OF_COUNTERS = Regex("""(.+) escapes with your choice of an? (\S+) counter or an? (\S+) counter on it\.""")
    // mh2 chunk 07
    val SPELL_OTHER_THAN_FIRST_EACH_TURN = Regex("""you cast a spell other than your first spell each turn""")
    val EXILE_TOP_DURING_YOUR_NEXT_TURN_MAY_PLAY = Regex("""Exile the top card of your library\. During your next turn, you may play that card\.""")
    val ENTERS_WITH_COUNTER_FOR_EACH_TIME_KICKED = Regex("""(.+) enters with (an?|\w+) (\S+) counters? on it for each time it was kicked\.""")
    val GAIN_CONTROL_OF_SPELL_COPY_RESELECT_AT_RANDOM = Regex("""Gain control of target (.+?)\. Copy it, then reselect the targets at random for the spell and the copy\. The new targets can't be you or a permanent you control\.""")
    val SPELL_THAT_TARGETS_ONLY_SINGLE_PERMANENT_OR_PLAYER = Regex("""spell that targets only a single permanent or player""")
    val REVEAL_UNTIL_NONLAND_BOTTOM_WHEN_YOU_REVEAL_DEALS_MV = Regex("""Reveal cards from the top of your library until you reveal an? (.+?) card\. Put the revealed cards on the bottom of your library in a random order\. When you reveal an? \1 card this way, (.+?) deals damage equal to that card's mana value to any target\.""")
    // mh2 chunk 08
    val MAY_DISCARD_WHEN_YOU_DO = Regex("""You may discard a card\. When you do, (.+)""")
    val THAT_COULD_PRODUCE = Regex("""(.+?) that could produce (\{[A-Z]\})""")
    val YOU_WIN_A_COIN_FLIP = Regex("""you win a coin flip""")
    val TREASURE_AND_EXILE_TOP_OF_THAT_PLAYERS_LIBRARY_MAY_CAST = Regex("""Create a Treasure token and exile the top card of that player's library\. Until end of turn, you may cast that card\.""")
    val FLIP_COINS_FOR_EACH_WIN_CREATE = Regex("""Flip (\w+) coins\. For each flip you win, create (.+?) that's tapped and attacking\.""")
    val SHUFFLE_PERMANENTS_REVEAL_THAT_MANY_PUT_NON_AURA_THEN_AURA = Regex("""Shuffle all permanents you own into your library, then reveal that many cards from the top of your library\. Put all non-Aura permanent cards revealed this way onto the battlefield, then do the same for Aura cards, then put the rest on the bottom of your library in a random order\.""")
    // mh2 chunk 09
    val COUNTER_ON_CREATURES = Regex("""(\S+) counter on (.+?)""")
    val GET_PT_FOR_EACH_COUNTER_ON_SELF = Regex("""(.+) get \+(\d+)/\+(\d+) for each (\S+) counter on (.+)\.""")
    val ISNT_LEGENDARY_IF_TOKEN = Regex("""(.+) isn't legendary if it's a token\.""")
    val CHOOSE_LAND_OR_NONLAND_REVEAL_UNTIL = Regex("""Choose land or nonland\. Reveal cards from the top of your library until you reveal a card of the chosen kind\. Put that card into your hand and the rest on the bottom of your library in a random order\.""")
    val ENTERS_WITH_N_COUNTERS_FOR_EACH_COLOR_SPENT = Regex("""(.+) enters with (\w+) (\S+) counters on it for each color of mana spent to cast it\.""")
    val TARGET_PLAYER_SHUFFLES_GRAVEYARD_INTO_LIBRARY = Regex("""Target player shuffles their graveyard into their library\.""")
    val PREVENT_ALL_COMBAT_DAMAGE_THIS_TURN = Regex("""Prevent all combat damage that would be dealt this turn\.""")
    // mh2 chunk 10
    val COSTS_LESS_FOR_EACH_OTHER_SPELL_CAST_THIS_TURN = Regex("""This spell costs (\{\d+\}) less to cast for each other spell cast this turn\.""")
    val HAS_AS_LONG_AS_IT_ENTERED_THIS_TURN = Regex("""(.+?) has ([a-z ]+) as long as it entered this turn\.""")
    val TARGET_PLAYER_CREATES_TOKENS = Regex("""Target player creates (\w+) (tapped )?(.+? tokens?)\.""")
    val COUNTER_TARGET_LOYALTY_ABILITY = Regex("""Counter target loyalty ability of a planeswalker\.""")
    // mh2 chunk 11
    val PROTECTION_FROM_WITH_COUNTERS = Regex("""Protection from (.+?) with (\S+) counters on them""")
    val GAIN_CONTROL_OF_EACH = Regex("""Gain control of each (.+)\.""")
    val WITHOUT_COUNTER_ON_IT = Regex("""(.+?) without an? (\S+) counter on it""")
    val GAIN_LIFE_EQUAL_TO_CARDS_DISCARDED_THIS_TURN = Regex("""You gain life equal to the number of cards you've discarded this turn\.""")
    val LOOK_TOP_MAY_REVEAL_PUT_INTO_HAND_REST_BOTTOM = Regex("""Look at the top (\w+) cards of your library\. You may reveal an? (.+?) card from among them and put it into your hand\. Put the rest on the bottom of your library in a random order\.""")
    val LOYALTY_ABILITIES_COST_ADDITIONAL = Regex("""Planeswalkers' loyalty abilities you activate cost an additional \[\+(\d+)\] to activate\.""")
    val EACH_SPELL_YOU_CAST_THATS_COSTS_LESS = Regex("""Each spell you cast that's (\w+) or (\w+) costs (\{\d+\}) less to cast\.""")
    val REVEAL_TOP_TWO_ONE_HAND_OTHER_GRAVEYARD_IF = Regex("""Reveal the top two cards of your library\. Put one of them into your hand and the other into your graveyard\. If you put an? (.+?) card into your hand this way, (.+)""")
    val THIS_OR_ANOTHER_ENTERS_FROM_GRAVEYARD_OR_CAST_FROM = Regex("""Whenever (.+?) or another (.+?) enters, if that creature entered from a graveyard or you cast it from a graveyard, (.+)""")
    val SELF_ENTERS_OR_DIES = Regex("""(.+?) enters or (.+?) dies""")
    // mh2 chunk 12
    val ECHO_COST = Regex("""Echo—(.+?)\.?""")
    val PT_EACH_EQUAL_TO = Regex("""(.+)'s power and toughness are each equal to (.+)\.""")
    val CHOOSE_ONE_IF_CHOOSE_BOTH_INSTEAD = Regex("""choose one\. If (.+), choose both instead\.""")
    val INVESTIGATE_TWICE = Regex("""[Ii]nvestigate twice\.""")
    val CANT_CAST_MORE_THAN_ONE_SPELL_EACH_TURN = Regex("""You can't cast more than one spell each turn\.""")
    val AS_LONG_AS_NOT_ON_BATTLEFIELD_ITS_A_CREATURE = Regex("""As long as (.+?) isn't on the battlefield, it's an? (\d+)/(\d+) (\w+) creature in addition to its other types\.""")
    val CREATE_THEN_MILL_IF_MILLED_COUNTER_REPEAT = Regex("""Create (.+? token), then mill a card\. If an? (\w+) card was milled this way, put an? (\S+) counter on (.+?) and repeat this process\.""")
    val EACH_OPPONENT_LOSES_LIFE_EQUAL_TO = Regex("""Each opponent loses life equal to (.+)\.""")
    val CHOOSE_NUMBER_FLIP_THAT_MANY = Regex("""Choose a number between (\d+) and (\d+)\. Flip that many coins\. For each flip you win, draw a card\. For each flip you lose, (.+?) deals (\d+) damage to you\. If you won (\w+) flips this way, you may cast spells from your hand this turn without paying their mana costs\.""")
    val LOOK_TOP_ONE_HAND_REST_BOTTOM = Regex("""Look at the top (\w+) cards of your library\. Put one of them into your hand and the rest on the bottom of your library in a random order\.""")
    // mh2 chunk 13
    val GETS_AND_HAS_KEYWORDS_AND_QUOTED = Regex("""(.+?) gets ([+-]\d+)/([+-]\d+) and has (.+), and "(.+)"""")
    val GETS_AND_HAS_PROTECTION_FROM_TWO = Regex("""(.+?) gets ([+-]\d+)/([+-]\d+) and has protection from (\w+) and from (\w+)\.""")
    val CREATE_CLUE_FOOD_TREASURE_INSTEAD_ONE_OF_EACH = Regex("""If you would create a Clue, Food, or Treasure token, instead create one of each\.""")
    val SPELL_COSTS_LESS_FOR_EACH_BASIC_LAND_TYPE = Regex("""This spell costs (\{\d+\}) less to cast for each basic land type among lands you control\.""")
    val EACH_CREATURE_HAS_KEYWORD_IF_ITS_COLOR = Regex("""Each creature you control has (.+)\.""")
    val KEYWORD_IF_ITS_COLOR = Regex("""(?:and )?([a-z ]+) if it's (\w+)""")
    val EXILE_UP_TO_ONE_YOU_OWN_THEN_SEARCH_PUT_BOTH = Regex("""Exile up to one target (.+?) you own, then search your library for an? (.+?) card\. Put both cards onto the battlefield under your control, then shuffle\.""")
    val DOESNT_UNTAP_IF_HAS_COUNTER = Regex("""(.+) doesn't untap during your untap step if it has an? (\S+) counter on it\.""")
    val TARGET_BECOMES_TYPE_IN_ADDITION_UNTIL_EOT = Regex("""Target (.+?) becomes an? (\w+(?: \w+)?) in addition to its other (colors and )?types until end of turn\.""")
    val AS_ENTERS_EXILE_A_CARD_FROM_A_GRAVEYARD = Regex("""As (.+?) enters, exile an? (.+?) card from a graveyard\.""")
    val UNTIL_EOT_BECOMES_COPY_OF_EXILED_EXCEPT = Regex("""Until end of turn, (.+?) becomes a copy of the exiled card, except it's an? (\w+) (\w+) in addition to its other types\.""")
    val ARTIFACT_LANDWALK = Regex("""[Aa]rtifact landwalk""")
    val DEALS_COMBAT_DAMAGE_TO_A_CREATURE = Regex("""(.+) deals combat damage to a creature""")
    // mh2 chunk 14
    val TOKEN_WITH_SINGLE_QUOTED = Regex("""(.+ token) with '(.+)'\.""")
    val WITH_MANA_COST_OR = Regex("""(.+?) with mana cost (\{[^}]+\}) or (\{[^}]+\})""")
    val EACH_IS_A_TYPE_IN_ADDITION = Regex("""Each (\w+) is an? (\w+) in addition to its other (?:land )?types\.""")
    // mh2 chunk 15
    val IF_MAY_CAST_WITHOUT_PAYING = Regex("""If (.+), you may cast this spell without paying its mana cost\.""")
    val THAT_PLAYER_SACRIFICES_OF_THEIR_CHOICE = Regex("""[Tt]hat player sacrifices an? (.+) of their choice\.""")
    val SKIP_YOUR_STEP = Regex("""Skip your (\w+) step\.""")
    val YOU_HAVE_SHROUD = Regex("""You have shroud\.""")
    val PREVENT_ALL_DAMAGE_TO_YOU = Regex("""Prevent all damage that would be dealt to you\.""")
    val THATS_ATTACKING_YOU_OR_YOUR_PLANESWALKER = Regex("""(.+?) that's attacking you or a planeswalker you control""")
    val EACH_PLAYER_CHOOSES_CREATURE_TYPE_RETURNS = Regex("""Each player chooses a creature type\. Each player returns all (.+?) cards of a type chosen this way from their graveyard to the battlefield\.""")
    // mh2 chunk 16
    val BECOMES_PT_SUBTYPE_ARTIFACT_CREATURE_STILL_LAND = Regex("""(.+?) becomes an? (\d+)/(\d+) ([A-Z][\w-]+(?: [A-Z][\w-]+)*) artifact creature until end of turn\. It's still a land\.""")
    val MAY_CAST_A_SPELL_FROM_YOUR_GRAVEYARD_THIS_TURN = Regex("""You may cast an? (\w+) spell from your graveyard this turn\.""")
    val YOU_DIDNT_CAST_IT_FROM_YOUR_HAND = Regex("""you didn't cast it from your hand""")
    val IT_GAINS_UNTIL_YOUR_NEXT_TURN = Regex("""[Ii]t gains ([a-z ]+) until your next turn\.""")
    val DESTROY_ALL_THREE_KINDS = Regex("""Destroy all (\w+)s, (\w+)s, and (\w+)s\.""")
    val ADD_ONE_MANA_OF_ANY_TYPE_THAT_LAND_PRODUCED = Regex("""[Aa]dd one mana of any type that land produced\.""")
    val AS_ENTERS_CHOOSE_A_NUMBER = Regex("""As (.+) enters, choose a number\.""")
    val SPELLS_WITH_MV_EQUAL_TO_CHOSEN_NUMBER_CANT_BE_CAST = Regex("""(\w+) spells with mana value equal to the chosen number can't be cast\.""")
    // mh2 engine: "This spell has cascade as long as …"
    val THIS_SPELL_HAS_CASCADE_AS_LONG_AS = Regex("""This spell has cascade as long as (.+)\.""")
    // mh2 chunk 11
    val CHOOSE_NAME_UNCHOSEN_COPY_MAY_CAST = Regex("""Choose a card name that hasn't been chosen from among (.+), and (.+?)\. Create a copy of the card with the chosen name\. You may cast the copy\.(?: \(You still pay its costs\.\))?""")
}

/** Values, conditions and nouns of Modern Horizons 2 (mh2) wordings. */
internal object Mh2Phrases {
    fun condition(text: String, self: Set<String>): Condition? {
        fun isSelf(name: String) = name in self || name.replaceFirstChar { it.lowercase() } in self
        // mh2 chunk 01: "you have more life than an opponent": some opponent has less life than you (CR 102.3)
        if (text == "you have more life than an opponent")
            return Condition.PlayerExists(PlayerFilter.And(listOf(PlayerFilter.Opponent, PlayerFilter.Life(Comparison.Less, Value.LifeOf(Players.You)))), atLeast = 1)
        // mh2 chunk 04: "an opponent controls an Island" (CR 102.3)
        Mh2.AN_OPPONENT_CONTROLS_A.matchEntire(text)?.let { m ->
            val noun = Filters.noun(m.groupValues[1]) ?: return@let
            return Condition.Exists(Filters.all(noun, ObjectFilter.ControlledBy(PlayerFilter.Opponent)), atLeast = 1)
        }
        // mh2 chunk 13: "no colored mana was spent to cast it" (CR 601.2h)
        if (text == "no colored mana was spent to cast it")
            return Condition.Compare(Value.ManaSpent(distinctColors = true, of = Objects.EventObject(arcanum.api.EventRole.Object)), Comparison.Equal, Value.Fixed(0))
        // mh2 chunk 16: "you didn't cast it from your hand" (the creature that entered, CR 601.2a)
        if (Mh2.YOU_DIDNT_CAST_IT_FROM_YOUR_HAND.matches(text))
            return Condition.Not(Condition.CastFrom(Zone.Hand, Objects.EventObject(arcanum.api.EventRole.After)))
        // mh2 chunk 02: "Skyblade's Boon is on the battlefield or in your graveyard" (the zones it works in, CR 113.6b)
        Mh2.IS_ON_BATTLEFIELD_OR_IN_YOUR_GRAVEYARD.matchEntire(text)?.takeIf { isSelf(it.groupValues[1]) }?.let {
            return Condition.ObjectMatches(Objects.Self, ObjectFilter.Or(listOf(ObjectFilter.InZone(Zone.Battlefield), ObjectFilter.InZone(Zone.Graveyard))))
        }
        return null
    }

    /** [exiled]: what "the exiled card" means on this card, when an ability links to it (CR 607.2a). */
    fun value(text: String, @Suppress("UNUSED_PARAMETER") exiled: Objects?): Value? {
        // mh2 chunk 12: "the number of basic land types among lands you control" (domain, CR 305.6)
        if (text == "the number of basic land types among lands you control")
            return Value.CountDistinct(Objects.Each(Filters.all(ObjectFilter.Type(arcanum.api.CardType.Land), ObjectFilter.ControlledBy(PlayerFilter.You))),
                arcanum.api.SharedQuality.BasicLandType)
        // mh2 chunk 08: "the number of card types the discarded card has" (a card discarded as the ability resolves, CR 205.2a)
        if (text == "the number of card types the discarded card has")
            return Value.CountDistinct(Objects.Bound("discarded"), arcanum.api.SharedQuality.CardType)
        return null
    }

    fun noun(phrase: String): ObjectFilter? {
        // mh2 chunk 15: "creature that's attacking you or a planeswalker you control" (you are its defending player, CR 506.2)
        Mh2.THATS_ATTACKING_YOU_OR_YOUR_PLANESWALKER.matchEntire(phrase)?.let { m ->
            val noun = Filters.noun(m.groupValues[1]) ?: return null
            return Filters.all(noun, ObjectFilter.HasStatus(arcanum.api.Status.Attacking),
                ObjectFilter.Where(Condition.PlayerMatches(Players.DefendingPlayerOf(Objects.Tested), PlayerFilter.You)))
        }
        // mh2 chunk 14: "artifact card with mana cost {0} or {1}" (CR 202.1)
        Mh2.WITH_MANA_COST_OR.matchEntire(phrase)?.let { m ->
            val noun = Filters.noun(m.groupValues[1]) ?: return null
            return Filters.all(noun, ObjectFilter.Or(listOf(ObjectFilter.HasManaCost(arcanum.api.ManaCost.parse(m.groupValues[2]) ?: return null),
                ObjectFilter.HasManaCost(arcanum.api.ManaCost.parse(m.groupValues[3]) ?: return null))))
        }
        // mh2 chunk 11: "other creature you control without a +1/+1 counter on it"
        Mh2.WITHOUT_COUNTER_ON_IT.matchEntire(phrase)?.let { m ->
            val noun = Filters.noun(m.groupValues[1]) ?: return null
            return Filters.all(noun, ObjectFilter.Not(ObjectFilter.HasCounter(Counters.kind(m.groupValues[2]) ?: return null, atLeast = 1)))
        }
        // mh2 chunk 08: "land your opponents control that could produce {C}" (CR 106.7)
        Mh2.THAT_COULD_PRODUCE.matchEntire(phrase)?.let { m ->
            val noun = Filters.noun(m.groupValues[1]) ?: return null
            return Filters.all(noun, ObjectFilter.CouldProduce(arcanum.api.ManaCost.parse(m.groupValues[2]) ?: return null))
        }
        // mh2 chunk 07: "spell that targets only a single permanent or player" (CR 115.7)
        if (Mh2.SPELL_THAT_TARGETS_ONLY_SINGLE_PERMANENT_OR_PLAYER.matches(phrase))
            return Filters.all(ObjectFilter.Category(arcanum.api.ObjectCategory.Spell),
                ObjectFilter.SingleTarget(ObjectFilter.Category(arcanum.api.ObjectCategory.Permanent), players = PlayerFilter.Any))
        // mh2 chunk 06: "creature cards in all graveyards"
        Mh2.CARDS_IN_ALL_GRAVEYARDS.matchEntire(phrase)?.let { m ->
            return Filters.all(Filters.noun(m.groupValues[1] + " card") ?: return null, ObjectFilter.InZone(Zone.Graveyard))
        }
        // mh2 chunk 05: "land that is snow or could produce {C}" (CR 106.7)
        Mh2.THAT_IS_SNOW_OR_COULD_PRODUCE.matchEntire(phrase)?.let { m ->
            val noun = Filters.noun(m.groupValues[1]) ?: return null
            val mana = arcanum.api.ManaCost.parse(m.groupValues[2]) ?: return null
            return Filters.all(noun, ObjectFilter.Or(listOf(ObjectFilter.Super(arcanum.api.Supertype.Snow), ObjectFilter.CouldProduce(mana))))
        }
        return null
    }
}
