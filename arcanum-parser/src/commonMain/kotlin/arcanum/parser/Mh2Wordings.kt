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
    val BECOMES_THE_TARGET_OF_A_SPELL_OR_ABILITY = Regex("""(.+) becomes the target of a spell or ability""")
    // mh2 chunk 04
    val CHOOSE_TARGET_OWNER_PUTS_TOP_OR_BOTTOM = Regex("""Choose up to one target (.+?)\. Its owner puts it on their choice of the top or bottom of their library\.""")
    val BECOMES_ARTIFACT_CREATURE_PUT_COUNTERS_ON_EACH_THAT_BECAME = Regex("""(Target|Each) (.+?) becomes an? (\d+)/(\d+) artifact creature\. Put (\w+) (\S+) counters on each artifact that became a creature this way\.""")
    val PUT_INTO_OWNERS_LIBRARY_NTH_FROM_TOP = Regex("""Put (.+?) into its owner's library (\w+) from the top\.""")
    val RETURN_N_TARGETS_TO_OWNERS_HANDS = Regex("""Return (two|three) target (.+?) to their owners' hands\.""")
    val EXILE_TARGET_PUT_TIME_COUNTERS_GAINS_SUSPEND = Regex("""Exile target (.+?) and put (\w+) time counters on it\. If it doesn't have suspend, it gains suspend\.""")
    val CREATE_FOR_EACH_COLOR_SPENT = Regex("""Create (an? .+? token(?: with .+?)?) for each color of mana spent to cast this spell\.""")
    val THOSE_DONT_UNTAP_DURING_NEXT = Regex("""Those (\w+) don't untap during their controller's next untap step\.""")
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
    val ENTERS_WITH_COUNTER_FOR_EACH_TIME_KICKED = Regex("""(.+) enters with an? (\S+) counter on it for each time it was kicked\.""")
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
        // mh2 chunk 02: "Skyblade's Boon is on the battlefield or in your graveyard" (the zones it works in, CR 113.6b)
        Mh2.IS_ON_BATTLEFIELD_OR_IN_YOUR_GRAVEYARD.matchEntire(text)?.takeIf { isSelf(it.groupValues[1]) }?.let {
            return Condition.ObjectMatches(Objects.Self, ObjectFilter.Or(listOf(ObjectFilter.InZone(Zone.Battlefield), ObjectFilter.InZone(Zone.Graveyard))))
        }
        return null
    }

    /** [exiled]: what "the exiled card" means on this card, when an ability links to it (CR 607.2a). */
    fun value(text: String, @Suppress("UNUSED_PARAMETER") exiled: Objects?): Value? {
        // mh2 chunk 08: "the number of card types the discarded card has" (a card discarded as the ability resolves, CR 205.2a)
        if (text == "the number of card types the discarded card has")
            return Value.CountDistinct(Objects.Bound("discarded"), arcanum.api.SharedQuality.CardType)
        return null
    }

    fun noun(phrase: String): ObjectFilter? {
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
