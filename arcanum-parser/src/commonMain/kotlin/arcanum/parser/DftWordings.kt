package arcanum.parser

import arcanum.api.Condition
import arcanum.api.ObjectFilter
import arcanum.api.ObjectMark
import arcanum.api.Objects
import arcanum.api.Players
import arcanum.api.Status
import arcanum.api.Value

/** Regular expressions of Aetherdrift (dft) wordings read in [Sentences]. */
internal object Dft {
    // dft chunk 01: Vehicles
    val DURING_OTHER_TURNS = Regex("""During turns other than yours, (.+)""")
    val IS_AN_ARTIFACT_CREATURE = Regex("""(.+) is an artifact creature\.""")
    val GETS_X_WHERE = Regex("""(.+) gets ([+-])X/([+-])X, where X is (.+)\.""")
    val ABILITIES_OF_CHOSEN_NAME_COST_MORE = Regex("""Activated abilities of sources with the chosen name cost (\{\d+\}) more to activate\.""")
    val TAP_N_OTHER_UNTAPPED = Regex("""Tap (\w+) other untapped (.+) you control""")
    val UNTIL_EOT_BECOMES_ARTIFACT_CREATURE_AND_GAINS = Regex("""Until end of turn, (.+?) becomes an artifact creature and gains ([a-z ,]+)\.""")
    val PUT_COUNTERS_ON_IT = Regex("""Put (a|an|one|two|three) (\S+) counters? on it\.""")
    val FOR_EACH_PLAYER_DESTROY_UP_TO_ONE = Regex("""For each player, destroy up to one target (.+?) that player controls\. If an? (.+?) you controlled was destroyed this way, (.+)""")
    val GAIN_CONTROL_AS_LONG_AS_CANT_ATTACK_OR_BLOCK = Regex("""Gain control of target (.+?) for as long as you control (.+?)\. That creature can't attack or block for as long as you control (.+?)\.""")
    val RETURN_A_CARD_FROM_YOUR_GRAVEYARD = Regex("""Return an? (.+?) card from your graveyard to your hand\.""")
    val RETURN_UP_TO_ONE_TARGET_TO_BATTLEFIELD = Regex("""Return up to one target (.+?) card from your graveyard to the battlefield\.""")
    val CREATE_THAT_MANY = Regex("""[Cc]reate that many (tapped )?(.+)\.""")
    // dft chunk 02
    val BECOMES_AN_ARTIFACT_CREATURE = Regex("""(.+?) becomes an artifact creature( until end of turn)?\.""")
    val ENTERS_WITH_ADDITIONAL_IF_MV_OTHERWISE = Regex("""Each other (.+?) you control enters with an additional (\S+) counter on it if its mana value is (\d+) or less\. Otherwise, it enters with (\w+) additional \S+ counters on it\.""")
    val DISTRIBUTE_AMONG_ONE_OR_TWO = Regex("""[Dd]istribute (\w+) (\S+) counters among one or (\w+) (other )?target (.+)\.""")
    val MAY_SEARCH_REVEAL_PUT_IF_MV_OTHERWISE = Regex("""You may search your library for an? (.+?) card and reveal it\. Put it onto the battlefield if its mana value is (\d+) or less\. Otherwise, put it into your hand\. If you search your library this way, shuffle\.""")
    val ARE_THE_CHOSEN_TYPE_IN_ADDITION = Regex("""(.+) are the chosen creature type in addition to their other types\.""")
    // dft chunk 03
    val CHOOSE_UP_TO_ONE_TARGET_THEN = Regex("""Choose up to one target (.+?)\. (.+)""")
    val UNTIL_EOT_SADDLED_IF_MOUNT_CREATURE_IF_VEHICLE = Regex("""Until end of turn, that permanent becomes saddled if it's a Mount and becomes an artifact creature if it's a Vehicle\.""")
    val RETURN_TARGET_IF_IT_BECOMES = Regex("""Return target (.+?) card from your graveyard to the battlefield\. If it's an? (\w+), it becomes an artifact creature\.""")
    val CREATE_VEHICLE_TOKEN = Regex("""[Cc]reate an? (\d+)/(\d+) colorless Vehicle artifact token with crew (\d+)\.""")
    val RETURN_ALL_THEN_SACRIFICE_THEM = Regex("""Return all (.+?) cards from your graveyard to the battlefield\. Sacrifice them at the beginning of the next end step\.""")
    val BECOME_ARTIFACT_CREATURES_UNTIL_EOT = Regex("""(.+) become artifact creatures until end of turn\.""")
    val BECOMES_SADDLED_UNTIL_EOT = Regex("""(.+?) becomes saddled until end of turn\.""")
    val A_AND_A_ATTACKED_THIS_COMBAT = Regex("""an? (\w+) and an? (\w+) attacked this combat""")
    // dft chunk 04
    val ATTACKS_WHILE_SADDLED = Regex("""(.+) attacks while saddled""")
    val ENTERS_AND_WHENEVER_IT_ATTACKS_WHILE_SADDLED = Regex("""(.+) enters and whenever it attacks while saddled""")
    // dft chunk 05
    val SADDLES_OR_CREWS_DURING_YOUR_MAIN_PHASE = Regex("""(.+) saddles a Mount or crews a Vehicle during your main phase""")
    val SADDLES_AND_CREWS_AS_THOUGH_GREATER = Regex("""(.+) saddles Mounts and crews Vehicles as though its power were (\d+) greater\.""")
    val SADDLES_AND_CREWS_USING_TOUGHNESS = Regex("""(.+) saddles Mounts and crews Vehicles using its toughness rather than its power\.""")
    val THAT_MOUNT_OR_VEHICLE = Regex("""That Mount or Vehicle (.+)""")
    val CREATE_X_TOKENS_THEY_HAVE = Regex("""Create X (\d+/\d+ .+? creature) tokens\. The tokens have "(.+)"\.?""")
    // dft chunk 06
    val RETURN_THEN_REDUCE_SPEED = Regex("""Return target (.+?) to its owner's hand\. If that opponent's speed is greater than each other player's speed, reduce that opponent's speed by (\w+)\. This effect can't reduce their speed below (\d+)\.""")
    val EACH_OPPONENT_SACRIFICES_WHO_CANT_DISCARDS = Regex("""Each opponent sacrifices an? (.+?) of their choice\. Each opponent who can't discards a card\.""")
    // dft chunk 07
    val MAY_PAY_IF_YOU_DO_COPY_IT = Regex("""You may pay (\{[^}]+\})\. If you do, copy it\.""")
    val CANT_ATTACK_OR_BLOCK_UNLESS = Regex("""(.+) can't attack or block unless (.+)\.""")
    val THAT_TOKEN_ATTACKS_THIS_COMBAT = Regex("""That token attacks this combat if able\.""")
    val DEALS_TO_EACH_PLAYER_WITHOUT_MAX_SPEED = Regex("""(.+?) deals (\d+) damage to each player who doesn't have max speed\.""")
    val GET_X_WHERE = Regex("""(.+) get \+X/\+(\d+), where X is (.+)\.""")
    val COST_X_LESS_WHERE = Regex("""(.+) cost \{X\} less to cast, where X is (.+)\.""")
    val A_AND_B_YOU_CONTROL_GAIN_UNTIL_EOT = Regex("""(\w+) and (\w+) you control gain ([a-z ,]+) until end of turn\.""")
    // dft chunk 08
    val ONE_OR_MORE_ATTACK_YOU = Regex("""one or more (.+) attack you""")
    val THOSE_CREATURES_GET_UNTIL_EOT = Regex("""Those creatures get ([+-]\d+)/([+-]\d+) until end of turn\.""")
    val DEALS_DAMAGE_TO_THEM = Regex("""(.+?) deals (\d+) damage to them\.""")
    val NEXT_EXHAUST_COPY_IT = Regex("""When you next activate an exhaust ability that isn't a mana ability this turn, copy it\. You may choose new targets for the copy\.""")
    val ANY_NUMBER_OF_TARGET_PLAYERS_EACH_MILL = Regex("""Any number of target players each mill (\w+) cards\.""")
    val ANY_NUMBER_OF_TARGET_PLAYERS_EACH_MILL_GRAVEYARD = Regex("""Any number of target players each mill cards equal to the number of cards in their graveyard\.""")
    val AS_THOUGH_NOT_ACTIVATED = Regex("""During your turn, as long as you haven't activated an? (\w+) ability this turn, you may activate (\w+) abilities as though they haven't been activated\.""")
    val LABEL_ABILITIES_OF_COST_LESS = Regex("""(\w+) abilities of (.+) cost (\{\d+\}) less to activate\.""")
    val PUT_ANY_NUMBER_FROM_HAND = Regex("""Put any number of (.+?) cards from your hand onto the battlefield( tapped)?\.""") // inr chunk 09: tapped
    val REVEAL_UNTIL_TWO_PUT_REST_BOTTOM = Regex("""Reveal cards from the top of your library until you reveal (\w+) (\w+) cards\. Put those \2 cards onto the battlefield( tapped)? and the rest on the bottom of your library in a random order\. (.+)""")
    val PUT_X_COUNTERS_THEN_MAY_BASE_PT = Regex("""Put X (\S+) counters on (.+?)\. Then you may have the base power and toughness of each other creature you control become equal to (.+?)'s power until end of turn\.""")
    val COSTS_LESS_IF_TARGETS_MV = Regex("""This spell costs (\{\d+\}) less to cast if it targets an? (\w+) with mana value (\d+)\.""")
    // dft chunk 10
    val EXCHANGE_CONTROL_OF_TARGETS = Regex("""Exchange control of target (.+?) and target (.+)\.""")
    val MAY_ENTER_AS_COPY = Regex("""You may have (.+?) enter as a copy of an? (.+)\.""")
    val ALL_LOSE_THEN_DESTROY_ALL = Regex("""All (.+?) lose (\w+) until end of turn, then destroy all (.+)\.""")
    val CREATE_X_TOKENS_WITH_QUOTED = Regex("""[Cc]reate X (\d+/\d+ .+? creature) tokens with "(.+)"\.?""")
    val WITH_MANA_VALUE_X = Regex("""(.+) with mana value X""")
    // dft chunk 11
    val DEALS_THAT_MUCH_TO_EACH_OPPONENT = Regex("""(.+?) deals that much damage to each opponent\.""")
    // dft chunk 12
    val GAINS_EMBALM_COST_ITS_MANA_COST = Regex("""Target (.+?) gains embalm until end of turn\. The embalm cost is equal to its mana cost\.""")
    val MAY_CAST_FROM_GRAVEYARD_BY = Regex("""You may cast (.+?) from your graveyard by (.+) in addition to paying its other costs\.""")
    val THAT_PLAYER_CREATES = Regex("""That player creates (.+)\.""")
    val LOOKS_AT_TOP_EXILES_FACE_DOWN_MAY_PLAY = Regex("""Its controller looks at the top card of that opponent's library and exiles it face down\. They may play that card for as long as it remains exiled\. Mana of any type can be spent to cast a spell this way\.""")
    val CHOOSE_TARGET_MILL_DESTROY_IF_MV = Regex("""Choose target (.+?)\. Mill (\w+) cards, then destroy the chosen permanent if its mana value is less than or equal to the number of cards in your graveyard\.""")
    val SACRIFICED_WAS = Regex("""the sacrificed permanent was an? (\w+)""")
    // dft chunk 13
    val CHOOSE_TARGET_MAY_SACRIFICE_RETURN_CHOSEN = Regex("""Choose target (.+?)\. You may sacrifice an? (.+?)\. If you do, return the chosen card to the battlefield\.""")
    val AFTER_THIS_MAIN_PHASE_COMBATS = Regex("""After this main phase, there are (\w+) additional combat phases\.""")
    val EACH_COMBAT_THIS_TURN_UNTAP_ATTACKED = Regex("""At the beginning of each combat this turn, untap all (.+?) that attacked this turn\.""")
    // dft chunk 14
    val UNTAP_ENCHANTED_PERMANENT = Regex("""Untap enchanted (permanent|creature)\.""")
    val MAY_SEARCH_LIBRARY_AND_OR_GRAVEYARD_REVEAL_HAND = Regex("""You may search your library and/or graveyard for an? (.+?), reveal it, and put it into your hand\. If you search your library this way, shuffle\.""")
    val EACH_ATTACKING_AN_OPPONENT_HAS = Regex("""Each (\w+) that's attacking one of your opponents has ([a-z ,]+)\.""")
    val MAY_PUT_FROM_HAND_IF_ARTIFACT_COUNTERS = Regex("""You may put an? (.+?) card from your hand onto the battlefield\. If you put an artifact onto the battlefield this way, put (\w+) (\S+) counters on it\.""")
    val COSTS_LESS_IF_TARGETS_YOU_CONTROL = Regex("""This spell costs (\{\d+\}) less to cast if it targets an? (.+?) you control\.""")
    val RETURN_UP_TO_ONE_EACH_TO_HAND = Regex("""Return (up to one target .+) from your graveyard to your hand\.""")
    val HAVE_BASE_PT_AND_ARE_IN_ADDITION = Regex("""(.+?) have base power and toughness (\d+)/(\d+) and are (\w+?)s in addition to their other types\.""")
    // dft chunk 15
    val PUT_FROM_GRAVEYARD_FINALITY_GAINS_EXILE_NEXT_END = Regex("""Put target (.+?) from a graveyard onto the battlefield under your control with a finality counter on it\. That creature gains ([a-z ,]+)\. At the beginning of your next end step, exile that creature\.""")
    val AS_ENTERS_EXILE_UP_TO_X_COUNTERS_FOR_EACH = Regex("""As (.+?) enters, exile up to X (.+?) cards from your graveyard\. It enters with (\w+) (\S+) counters on it for each \2 card exiled this way\.""")
    val BECOMES_COPY_OF_EXILED_WITH_EXCEPT = Regex("""(.+?) becomes a copy of target (.+?) card exiled with it, except it's (\d+)/(\d+) and has this ability\.""")
    val CHOOSE_TARGET_OPPONENT_CREATE_THEN_DRAIN_X = Regex("""Choose target opponent\. (Create .+?\.) Then the chosen player loses X life and you gain X life\.""")
    val LOOK_AT_HAND_MAY_CHOOSE_DISCARDS_DRAWS = Regex("""Look at target player's hand\. You may choose a card from it\. If you do, that player discards that card, then draws a card\.""")
    val CARDS_PUT_INTO_EXILE_FROM_DURING_YOUR_TURN = Regex("""one or more cards are put into exile from graveyards and/or the battlefield during your turn""")
    val GETS_FOR_EACH_DISCARDED_THEN_WHENEVER_DAMAGE = Regex("""Target creature gets \+(\d+)/\+0 until end of turn for each card discarded this way\. Whenever that creature deals combat damage to a player this turn, you draw a card\.""")
    val FOR_EACH_OPPONENT_PUT_THIRD_FROM_TOP = Regex("""For each opponent, put up to one target (.+?) that player controls into its owner's library (\w+) from the top\.""")
    // dft chunk 16
    val ATTACHED_CANT_BE_ATTACKED_AND_HAS = Regex("""As long as (.+?) is attached to a creature, (.+?) can't be attacked and has "(.+)"\.?""")
    val ATTACH_TO_UP_TO_ONE_PUT_COUNTER = Regex("""Attach (.+?) to up to one target (.+?)\. Put an? (\S+) counter on that creature\.""")
    val SACRIFICE_ONE_OR_MORE = Regex("""Sacrifice one or more (.+)""")
    val CHOOSE_COLOR_TARGET_PLAYER_ADDS_FOR_EACH_SACRIFICED = Regex("""Choose a color\. Target player adds (\w+) mana of the chosen color for each (\w+) sacrificed this way\.""")
    val DESTROY_ANOTHER_TARGET = Regex("""Destroy another target (.+)\.""")
    val PUT_THAT_MANY_COUNTERS_ON = Regex("""[Pp]ut that many (\S+) counters on (.+)\.""")
}

/** Aetherdrift (dft) values and conditions read in [Values] and [Conditions]. */
internal object DftPhrases {
    /** It is saddled (CR 702.171b). */
    val SADDLED: ObjectFilter = ObjectFilter.HasStatus(Status.Marked(ObjectMark.Saddled))

    fun value(text: String): Value? {
        when (text) {
            // dft chunk 01: "your life total" (CR 119)
            "your life total" -> return Value.LifeOf(Players.You)
            // dft chunk 06: "your speed" (CR 702.179f: 0 without speed)
            "your speed" -> return Value.Speed(Players.You)
        }
        // dft chunk 05: "the number of permanents you control that are creatures and/or Vehicles"
        PERMANENTS_YOU_CONTROL_THAT_ARE.matchEntire(text)?.let { m ->
            return Value.Count(Objects.Each(Filters.all(ObjectFilter.ControlledBy(arcanum.api.PlayerFilter.You), Filters.noun(m.groupValues[1]) ?: return null)))
        }
        // dft chunk 05: "2 plus the number of Mounts and Vehicles you control"
        N_PLUS.matchEntire(text)?.let { m -> return Value.Sum(listOf(Value.Fixed(m.groupValues[1].toInt()), Values.value(m.groupValues[2]) ?: return null)) }
        // dft chunk 05: "the number of Mounts and/or Vehicles that entered the battlefield under your control this turn"
        ENTERED_UNDER_YOUR_CONTROL_THIS_TURN.matchEntire(text)?.let { m ->
            return Value.EventsThisTurn(arcanum.api.EventPattern.ZoneChange(Filters.all(Filters.noun(m.groupValues[1]) ?: return null,
                ObjectFilter.ControlledBy(arcanum.api.PlayerFilter.You)), to = arcanum.api.Zone.Battlefield), arcanum.api.Measure.Objects)
        }
        return null
    }

    fun condition(text: String): Condition? {
        // dft chunk 07: "you have max speed" (CR 702.179e)
        if (text == "you have max speed") return Condition.MaxSpeed
        // dft chunk 15: "it has an even number of counters on it" (zero is even), "… an odd number …"
        EVEN_OR_ODD_COUNTERS.matchEntire(text)?.let { m ->
            val odd = Condition.Odd(Value.Of(arcanum.api.NumericProperty.Counters(null), Objects.Self, arcanum.api.Combine.Single))
            return if (m.groupValues[1] == "odd") odd else Condition.Not(odd)
        }
        // dft chunk 15: "there are seven or more cards in exile"
        CARDS_IN_EXILE.matchEntire(text)?.let { m ->
            return Condition.Exists(Filters.all(ObjectFilter.Category(arcanum.api.ObjectCategory.Card), ObjectFilter.InZone(arcanum.api.Zone.Exile)),
                atLeast = Numbers.count(m.groupValues[1]) ?: return null)
        }
        // dft chunk 12: "the sacrificed permanent was a Vehicle" (the cost's permanent as it last existed, CR 608.2h)
        Dft.SACRIFICED_WAS.matchEntire(text)?.let { m -> return Condition.ObjectMatches(Objects.Bound(SACRIFICED), Filters.noun(m.groupValues[1]) ?: return null) }
        // dft chunk 08: "it's not their turn" (the player of the event isn't the active player)
        if (text == "it's not their turn") return Condition.PlayerMatches(Players.EventPlayer, arcanum.api.PlayerFilter.Not(arcanum.api.PlayerFilter.Is(Players.Active)))
        // dft chunk 03: "a Pirate and a Vehicle attacked this combat" (attacking creatures of each)
        Dft.A_AND_A_ATTACKED_THIS_COMBAT.matchEntire(text)?.let { m ->
            val attacking = ObjectFilter.HasStatus(Status.Attacking)
            return Condition.And(m.groupValues.drop(1).map { Condition.Exists(Filters.all(Filters.noun(it) ?: return null, attacking), atLeast = 1) })
        }
        return null
    }

    fun noun(phrase: String): ObjectFilter? {
        // dft chunk 09: "artifact card with mana value 4 or 5"
        MANA_VALUE_N_OR_M.matchEntire(phrase)?.let { m ->
            val n = m.groupValues[2].toInt()
            val mv = { v: Int -> ObjectFilter.ManaValue(arcanum.api.Comparison.Equal, Value.Fixed(v)) }
            return Filters.all(Filters.noun(m.groupValues[1]) ?: return null, if (m.groupValues[3].isEmpty()) mv(n) else ObjectFilter.Or(listOf(mv(n), mv(m.groupValues[3].toInt()))))
        }
        // dft chunk 11: "artifact or enchantment with mana value X" (X read by the ability: announced, or the cycling's)
        Dft.WITH_MANA_VALUE_X.matchEntire(phrase)?.let { m ->
            return Filters.all(Filters.noun(m.groupValues[1]) ?: return null, ObjectFilter.ManaValue(arcanum.api.Comparison.Equal, Value.Announced("X")))
        }
        // dft chunk 14: "creature card with no abilities" (CR 113.1)
        WITH_NO_ABILITIES.matchEntire(phrase)?.let { m -> return Filters.all(Filters.noun(m.groupValues[1]) ?: return null, ObjectFilter.NoAbilities) }
        return null
    }

    private val EVEN_OR_ODD_COUNTERS = Regex("""it has an (even|odd) number of counters on it""")
    private val CARDS_IN_EXILE = Regex("""there are (\w+) or more cards in exile""")
    private val MANA_VALUE_N_OR_M = Regex("""(.+) with mana value (\d+)(?: or (\d+))?""")
    private val WITH_NO_ABILITIES = Regex("""(.+) with no abilities""")
    private val PERMANENTS_YOU_CONTROL_THAT_ARE = Regex("""the number of permanents you control that are (.+)""")
    private val N_PLUS = Regex("""(\d+) plus (.+)""")
    private val ENTERED_UNDER_YOUR_CONTROL_THIS_TURN = Regex("""the number of (.+) that entered the battlefield under your control this turn""")
}
