package arcanum.parser

import arcanum.api.CardType
import arcanum.api.Condition
import arcanum.api.EventPattern
import arcanum.api.Keyword
import arcanum.api.Measure
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Value
import arcanum.api.Zone

/** eoe: conditions, values, nouns and events of Edge of Eternities wordings, tried first by [Conditions], [Values], [Filters] and
 * [Events]. */
internal object EoePhrases {
    // eoe chunk 03: void (an ability word, CR 207.2c): "a nonland permanent left the battlefield this turn or a spell was warped this turn"
    private const val VOID = "a nonland permanent left the battlefield this turn or a spell was warped this turn"

    /** "a nonland permanent left the battlefield this turn or a spell was warped this turn": a spell was warped when it was cast for its
     * warp cost (CR 702.185c). */
    val void: Condition get() = Condition.Or(listOf(
        Condition.HappenedThisTurn(EventPattern.ZoneChange(Filters.noun("nonland permanent")!!, from = Zone.Battlefield), atLeast = 1, measure = Measure.Objects),
        Condition.HappenedThisTurn(EventPattern.Cast(ObjectFilter.WasPaid(Keyword.Warp.Kind, times = 1), by = PlayerFilter.Any), atLeast = 1, measure = Measure.Objects)))

    // eoe chunk 07: "you've cast two or more spells this turn", "you've cast another spell this turn" (not the spell being cast)
    private val CAST_N_OR_MORE = Regex("""you've cast (\w+) or more spells this turn""")
    // eoe chunk 07: "you've gained 3 or more life this turn"
    private val GAINED_N_OR_MORE = Regex("""you've gained (\d+) or more life this turn""")
    // eoe chunk 07: "your opponents control three or more creatures"
    private val OPPONENTS_CONTROL_N_OR_MORE = Regex("""your opponents control (\w+) or more (.+)""")

    fun condition(text: String, self: Set<String>): Condition? {
        if (text == VOID) return void
        CAST_N_OR_MORE.matchEntire(text)?.let { m ->
            return Condition.HappenedThisTurn(EventPattern.Cast(ObjectFilter.Any, PlayerFilter.You), atLeast = Numbers.count(m.groupValues[1]) ?: return null,
                measure = Measure.Events)
        }
        if (text == "you've cast another spell this turn")
            return Condition.HappenedThisTurn(EventPattern.Cast(ObjectFilter.Not(ObjectFilter.Is(Objects.Self)), PlayerFilter.You), atLeast = 1, measure = Measure.Events)
        GAINED_N_OR_MORE.matchEntire(text)?.let { m ->
            return Condition.HappenedThisTurn(EventPattern.GainLife(PlayerFilter.You), atLeast = m.groupValues[1].toInt(), measure = Measure.Amount)
        }
        OPPONENTS_CONTROL_N_OR_MORE.matchEntire(text)?.let { m ->
            // all opponents together (not per opponent)
            val noun = Filters.noun(m.groupValues[2]) ?: return null
            return Condition.Compare(Value.Count(Objects.Each(Filters.all(noun, ObjectFilter.ControlledBy(PlayerFilter.Opponent)))), arcanum.api.Comparison.AtLeast,
                Value.Fixed(Numbers.count(m.groupValues[1]) ?: return null))
        }
        // eoe chunk 08: "you have one or fewer cards in hand"
        Eoe.N_OR_FEWER_IN_HAND.matchEntire(text)?.let { m ->
            return Condition.Not(Condition.Exists(Filters.all(ObjectFilter.InZone(Zone.Hand), ObjectFilter.OwnedBy(PlayerFilter.You)),
                atLeast = (Numbers.count(m.groupValues[1]) ?: return null) + 1))
        }
        // eoe chunk 09: "a player controls no creatures" (CR 102.3)
        Eoe.A_PLAYER_CONTROLS_NO.matchEntire(text)?.let { m ->
            val noun = Filters.noun(m.groupValues[1]) ?: return null
            return Condition.PlayerExists(PlayerFilter.Where(Condition.Not(Condition.Exists(Filters.all(noun,
                ObjectFilter.ControlledBy(PlayerFilter.Is(arcanum.api.Players.Tested))), atLeast = 1))), atLeast = 1)
        }
        return null
    }

    // eoe chunk 07: "the amount of mana spent to cast this creature" (CR 601.2h)
    private val MANA_SPENT_SELF = Regex("""the amount of mana spent to cast (?:this \w+|it)""")

    fun value(text: String): Value? {
        if (MANA_SPENT_SELF.matches(text)) return Value.ManaSpent(of = Objects.Self)
        // eoe chunk 10: "one plus the mana value of the sacrificed artifact"
        Eoe.ONE_PLUS.matchEntire(text)?.let { m -> return Value.Sum(listOf(Value.Fixed(1), Values.value(m.groupValues[1]) ?: return null)) }
        // eoe chunk 10: "the mana value of the sacrificed artifact" (the sacrificed object as it last existed, CR 608.2h)
        Eoe.PROPERTY_OF_SACRIFICED.matchEntire(text)?.let { m ->
            val property = if (m.groupValues[1] == "mana value") arcanum.api.NumericProperty.ManaValue else arcanum.api.NumericProperty.Power
            return Value.Of(property, Objects.Bound(SACRIFICED), arcanum.api.Combine.Single)
        }
        // eoe chunk 12: "the number of cards you own in exile"
        if (text == "the number of cards you own in exile")
            return Value.Count(Objects.Each(Filters.all(ObjectFilter.Category(arcanum.api.ObjectCategory.Card), ObjectFilter.OwnedBy(PlayerFilter.You), ObjectFilter.InZone(Zone.Exile))))
        return null
    }

    fun noun(phrase: String): ObjectFilter? {
        // eoe chunk 07: "creatures and Spacecraft" (Spacecraft is its own plural)
        if (phrase == "creatures and Spacecraft") return ObjectFilter.Or(listOf(ObjectFilter.Type(CardType.Creature), ObjectFilter.Sub(arcanum.api.Subtype("Spacecraft"))))
        // eoe chunk 10: "artifact cards and red creature cards" (cards of either kind)
        Eoe.CARDS_AND_CARDS.matchEntire(phrase)?.let { m -> return Filters.noun(m.groupValues[1] + " card or " + m.groupValues[2] + " card") }
        // eoe chunk 09: "exiled card with warp" (a card in exile that has the keyword)
        Eoe.EXILED_CARD_WITH_WARP.matchEntire(phrase)?.let { m ->
            val kind = arcanum.api.KeywordKind.byId(m.groupValues[1]) ?: return null
            return Filters.all(ObjectFilter.Category(arcanum.api.ObjectCategory.Card), ObjectFilter.InZone(Zone.Exile), ObjectFilter.HasKeyword(kind))
        }
        // eoe chunk 11: "warped creature card you own in exile" (a warped card, CR 702.185b)
        Eoe.WARPED_CARD_YOU_OWN_IN_EXILE.matchEntire(phrase)?.let { m ->
            return Filters.all(Filters.noun(m.groupValues[1]) ?: return null, ObjectFilter.OwnedBy(PlayerFilter.You), ObjectFilter.InZone(Zone.Exile),
                ObjectFilter.HasStatus(arcanum.api.Status.Marked(arcanum.api.ObjectMark.Warped)))
        }
        return null
    }

    fun events(text: String, self: Set<String>): List<EventPattern>? {
        // eoe chunk 08: "enchanted creature becomes tapped or is dealt damage" (two events of one subject)
        Eoe.BECOMES_TAPPED_OR_DEALT_DAMAGE.matchEntire(text)?.let { m ->
            return (Events.events(m.groupValues[1] + " becomes tapped", self) ?: return null) + (Events.events(m.groupValues[1] + " is dealt damage", self) ?: return null)
        }
        // eoe chunk 12: "another creature you control dies or is put into exile" (from the battlefield, CR 700.4)
        Eoe.DIES_OR_EXILED.matchEntire(text)?.let { m ->
            val noun = Filters.noun(m.groupValues[1]) ?: return null
            return listOf(EventPattern.ZoneChange(noun, from = Zone.Battlefield, to = Zone.Graveyard), EventPattern.ZoneChange(noun, from = Zone.Battlefield, to = Zone.Exile))
        }
        // eoe chunk 13: "you play a land or cast a spell" (CR 305.1, 601.2i)
        if (Eoe.PLAY_LAND_OR_CAST.matches(text)) return listOf(EventPattern.PlayLand(ObjectFilter.Any, PlayerFilter.You), EventPattern.Cast(ObjectFilter.Any, PlayerFilter.You))
        // eoe chunk 11: "this creature blocks a creature with flying" (CR 509.1)
        Eoe.BLOCKS_A.matchEntire(text)?.let { m ->
            if (m.groupValues[1] !in self) return@let
            return listOf(EventPattern.Block(ObjectFilter.Is(Objects.Self), Filters.noun(m.groupValues[2]) ?: return null))
        }
        // eoe chunk 09: "an opponent discards a card" (CR 701.9a)
        if (text == "an opponent discards a card") return listOf(EventPattern.Discard(ObjectFilter.Any, PlayerFilter.Opponent))
        // eoe chunk 09: "you sacrifice another creature or artifact", "you sacrifice an artifact" (its controller sacrifices it, CR 701.21a)
        Eoe.YOU_SACRIFICE.matchEntire(text)?.let { m ->
            val noun = Filters.noun(m.groupValues[2].removeArticle()) ?: return null
            val other = if (m.groupValues[1].isNotEmpty()) listOf(ObjectFilter.Not(ObjectFilter.Is(Objects.Self))) else emptyList()
            return listOf(EventPattern.Sacrifice(Filters.all(*(other + noun + ObjectFilter.ControlledBy(PlayerFilter.You)).toTypedArray())))
        }
        return null
    }
}

/** Regular expressions of Edge of Eternities (eoe) wordings read in [Sentences]. */
internal object Eoe {
    // eoe chunk 07: white wordings
    val UNTAP_IF_ITS_COUNTER = Regex("""Untap target (.+?)\. If it's an? (.+?), put a \+1/\+1 counter on it\.""")
    val COUNTERS_IF_NOT_CREATURE_BECOMES = Regex("""[Pp]ut (\w+) \+1/\+1 counters on target (.+?)\. If it's not a creature, it becomes an? (\d+)/(\d+) ([A-Z]\w+) artifact creature\.""")
    val CREATE_AND_ATTACH_SELF = Regex("""[Cc]reate (an? .+? token) and attach (.+?) to it\.""")
    val ENCHANTED_PERMANENT = Regex("""(.*)([Ee])nchanted permanent(.*)""")
    val EXILE_ALL_UNTIL_LEAVES = Regex("""[Ee]xile all (.+?) until (.+?) leaves the battlefield\.""")
    val EXILED_WITH_TO_GRAVEYARD_CREATE_FOR_EACH = Regex("""Put each card exiled with (.+?) into its owner's graveyard, then create (an? .+? token) for each card put into a graveyard this way\. Sacrifice (.+?)\.""")
    val IF_YOU_DIDNT_PUT_INTO_HAND = Regex("""If you didn't put a card into your hand this way, (.+)""")
    val RETURN_UP_TO_TOTAL_MV_COUNTER_EACH = Regex("""Return up to (\w+) target (.+?) with total mana value (\d+) or less from your graveyard to the battlefield\. Put a \+1/\+1 counter on each of them\.""")
    val COSTS_LESS_TO_ACTIVATE_IF = Regex("""This ability costs ((?:\{[^}]+\})+) less to activate if (.+?)\.""") // mh3 chunk 10: "{4}{B}"
    // inr chunk 01: also an ability quoted inside it ("… have haste and '{T}: … any target.'")
    val EMBLEM_PERIOD_INSIDE = Regex("""You get an emblem with "(.+\.'?)"""")
    val ENTERS_WITH_COUNTER_FOR_EACH = Regex("""(.+) enters with an? (\S+) counter on it for each (.+)\.""")
    val ENTERS_WITH_NUMBER_EQUAL_TO = Regex("""(.+) enters with a number of (\S+) counters on it equal to (.+)\.""")

    // eoe chunk 08: blue wordings
    val CHOOSE_UP_TO_ONE_UNBLOCKABLE_BASE_PT = Regex("""[Cc]hoose up to one target (.+?)\. That creature can't be blocked this turn and has base power and toughness (\d+)/(\d+) until end of turn\.""")
    val SURVEIL_X = Regex("""Surveil X\.""")
    val LOOK_TWO_ONE_HAND_OTHER_GRAVEYARD = Regex("""[Ll]ook at the top two cards of your library\. Put one into your hand and the other into your graveyard\.""")
    val LOOK_X_ONE_KICKED_TWO = Regex("""Look at the top X cards of your library\. Put one of those cards into your hand\. If this spell was kicked, put two of those cards into your hand instead\. Put the rest on the bottom of your library in a random order\.""")
    val BECOMES_TAPPED_OR_DEALT_DAMAGE = Regex("""(.+?) becomes tapped or is dealt damage""")
    val RETURN_IF_IT_WAS_ATTACKING_CREATE = Regex("""Return target (.+?) to its owner's hand\. If it was attacking, create (.+)""")
    val GETS_PLUS_X_WHERE = Regex("""(.+?) gets \+X/\+0, where X is (.+)\.""")
    val COSTS_LESS_DURING_YOUR_TURN = Regex("""This spell costs (\{\d+\}) less to cast during your turn\.""")
    val FIRST_TIME_CREATE_TOKENS_COPIES_INSTEAD = Regex("""The first time you would create one or more tokens each turn, you may instead create that many tokens that are copies of (.+)\.""")
    val GET_UNTIL_YOUR_NEXT_TURN = Regex("""(.+?) get ([+-]\d+)/([+-]\d+) until your next turn\.""")
    val AS_LONG_AS_DRAW_PLUS_ONE = Regex("""As long as (.+?), if you would draw one or more cards, you draw that many cards plus one instead\.""")
    val COUNTER_ON_TARGET_AND_DRAW = Regex("""[Pp]ut a \+1/\+1 counter on target (.+?) and draw a card\.""")
    val DEFENDING_PLAYER_MILLS = Regex("""[Dd]efending player mills (\w+) cards\.""")
    val ENTERING_TRIGGERS_ADDITIONAL_TIME = Regex("""If a permanent entering the battlefield causes a triggered ability of an? (.+?) to trigger, that ability triggers an additional time\.""")
    val BECOMES_ARTIFACT_CREATURE_BASE_GAINS = Regex("""([Uu]p to one (?:other )?target .+?) becomes an artifact creature with base power and toughness (\d+)/(\d+) and gains ([a-z ]+) until end of turn\.""")
    val COUNTER_IF_SPENT_LESS_DRAW = Regex("""Counter target spell\. If the amount of mana spent to cast that spell was less than its mana value, you draw a card\.""")
    val NTH_SPELL_EACH_TURN_COSTS_LESS = Regex("""The (\w+) spell you cast each turn costs ((?:\{[^}]+\})+) less to cast\.""")
    val SHUFFLE_HAND_GRAVEYARD_DRAW = Regex("""[Ss]huffle your hand and graveyard into your library, then draw (\w+) cards\.""")
    val EACH_OPPONENT_EXILES_MAY_PLAY = Regex("""[Ee]ach opponent exiles a card from their hand and may play that card for as long as it remains exiled\. Each spell cast this way costs ((?:\{[^}]+\})+) more to cast\. Each land played this way enters tapped\.""")
    val FIRST_SPELL_EACH_PLAYER_FREE = Regex("""The first spell each player casts during each of their turns may be cast without paying its mana cost\.""")

    // eoe chunk 09: black wordings
    val DEFENDING_PLAYER_LOSES_HALF = Regex("""[Dd]efending player loses half their life, rounded (up|down)\.""")
    val PUT_ONTO_BATTLEFIELD_TAPPED_ATTACKING = Regex("""[Pp]ut target (.+?) from defending player's graveyard onto the battlefield under your control tapped and attacking\.""")
    val THEN_IF_POWER_DESTROY = Regex("""Then if that creature's power is (\d+) or less, destroy it\.""")
    val THEY_LOSE_LIFE = Regex("""[Tt]hey lose (\d+) life\.""")
    val EACH_OPPONENT_DISCARDS_WHO_CANT_LOSES = Regex("""[Ee]ach opponent discards a card\. Each opponent who can't loses (\d+) life\.""")
    val DESTROY_IF_NOT_DAMAGED_CONTROLLER_DRAWS = Regex("""[Dd]estroy (up to one (?:other )?target .+?)\. If that creature wasn't dealt damage this turn, its controller draws (\w+) cards\.""")
    val MILL_THEN_RETURN_FROM_GRAVEYARD = Regex("""[Mm]ill (\w+) cards, then return an? (.+?) from your graveyard to your hand\.""")
    val DRAW_ANOTHER_CARD = Regex("""[Dd]raw another card\.""")
    val YOU_SACRIFICE = Regex("""you sacrifice (another )(.+ or .+)""")
    val UNTIL_EOT_X_AND_MINUS_X = Regex("""[Uu]ntil end of turn, target (.+?) gets \+X/\+0 and target (.+?) gets -0/-X\.""")
    val UNTIL_EOT_GETS_AND_GAINS_QUOTED = Regex("""[Uu]ntil end of turn, (another target .+?|target .+?) gets ([+-]\d+)/([+-]\d+) and gains "(.+)"\.?""")
    val UNTIL_EOT_GAINS_QUOTED_CONTROLLER_MAY_DAMAGE = Regex("""Until end of turn, target (.+?) gains "(.+?)" That creature's controller may have (.+?) deal (\d+) damage to it\.""")
    val EACH_OPPONENT_CHOOSES_AND_EXILES = Regex("""[Ee]ach opponent chooses an? (.+?) they control and exiles it\.""")
    val SACRIFICE_SELF_PUT_EXILED_WITH_COUNTERS = Regex("""[Ss]acrifice (.+?), then put an? (.+?) exiled with it onto the battlefield under your control with (\w+) additional \+1/\+1 counters on it\.""")
    val RETURN_IT_IS_SUBTYPE_NO_ABILITIES = Regex("""Return target (.+?) from your graveyard to the battlefield\. It's an? ([A-Z]\w+) in addition to its other types and has no abilities\.""")
    val DESTROY_LOSE_X_IF_X_RETURN = Regex("""Destroy all (.+?) with toughness X or less\. You lose X life\. If X is (\d+) or more, return an? (.+?) put into a graveyard this way to the battlefield under your control\.""")
    val A_PLAYER_CONTROLS_NO = Regex("""a player controls no (.+)""")
    val UNTIL_EOT_GAINS_QUOTED_THEN = Regex("""(Until end of turn, target .+? gains ".+?") (That creature's controller may .+)""")

    val IF_IT_HAS_COUNTER = Regex("""If it has an? (\S+) counter on it, (.+)""")
    val GET_IF_NOT_YOUR_TURN_UNTAP_THOSE = Regex("""(Creatures you control) get \+(\d+)/\+(\d+) until end of turn\. If it's not your turn, untap those creatures\.""")
    val IT_DEALS_THAT_MUCH_TO_EACH_OPPONENT = Regex("""[Ii]t deals that much damage to each opponent\.""")

    // eoe chunk 10: red wordings
    val DEALS_DAMAGE_TO_ITSELF_TWICE_IF = Regex("""Target (.+?) deals damage to itself equal to its power\. If that creature has (\w+), it deals twice that much damage to itself instead\.""")
    val CREATE_X_COPIES_HASTE_SACRIFICE = Regex("""Create X tokens that are copies of target (.+?)\. Those tokens gain haste until end of turn\. Sacrifice them at the beginning of the next end step\.""")
    val GETS_IF_WARPED_ALSO_GAINS = Regex("""Target (.+?) gets ([+-]\d+)/([+-]\d+) until end of turn\. If that creature was cast for its warp cost, it also gains (.+) until end of turn\.""")
    val DEALS_DAMAGE_INSTEAD_IF = Regex("""(.+?) deals (\d+) damage to target (.+?)\. It deals (\d+) damage instead if (.+)\.""")
    val CREATE_SACRIFICE_ON_NEXT_TURN = Regex("""[Cc]reate (an? .+? token)\. At the beginning of the end step on your next turn, sacrifice that token\.""")
    val MAY_PAY_CREATE_ATTACKING_SACRIFICE_EOC = Regex("""[Yy]ou may pay ((?:\{[^}]+\})+)\. If you do, create (an? .+? token) that's tapped and attacking\. Sacrifice that token at end of combat\.""")
    val ONE_PLUS = Regex("""one plus (.+)""")
    val DEALS_IF_EXCESS_CREATE = Regex("""(.+?) deals (\d+) damage to target (.+?)\. If excess damage was dealt this way, create (.+)\.""")
    val ENCHANTED_DEALS_POWER_TO_ANY_OTHER = Regex("""[Ee]nchanted creature deals damage equal to its power to any other target\.""")
    val EXILE_TOP_PLAY_IF_CONTROL = Regex("""[Ee]xile the top card of your library\. For as long as that card remains exiled, you may play it if (.+)\.""")
    val EXILE_COPY_CAST_FREE = Regex("""[Ee]xile (up to one target .+?)\. Copy it\. You may cast the copy without paying its mana cost\.""")
    val GAIN_CONTROL_SPACECRAFT_CHARGE = Regex("""Gain control of target (.+?) until end of turn\. Untap that permanent\. It gains haste until end of turn\. If it's an? (\w+), put (\w+) (\w+) counters on it\. If you do, remove (\w+) (\w+) counters from it at the beginning of the next end step\.""")
    val IN_YOUR_HAND_HAVE_WARP = Regex("""(.+?) in your hand have warp ((?:\{[^}]+\})+)\.""")
    val MAY_PUT_FROM_HAND_GAINS_QUOTED = Regex("""You may put an? (.+?) from your hand onto the battlefield\. That permanent gains haste, "(.+?)," and "(.+?\.)"""")
    val OPPONENT_MAY_HAVE_YOU_CREATE = Regex("""target opponent may have you create (\w+) (\w+) tokens\. If they don't, put (\w+) \+1/\+1 counters on (.+?)\.""")
    val EXILE_UNTIL_NONLAND_CAST_THIS_TURN = Regex("""[Ee]xile cards from the top of your library until you exile an? (.+?)\. You may cast that card this turn\.""")

    // eoe chunk 11: green wordings
    val EACH_ENTERS_WITH_ADDITIONAL_FOR_EACH_ENTERED = Regex("""Each (.+?) enters with an additional (\S+) counter on it for each (.+?) that entered the battlefield under your control this turn\.""")
    val ADDITIONAL_COST_CHOOSE = Regex("""As an additional cost to cast this spell, choose (an? .+?)\.""")
    val DEALS_DAMAGE_EQUAL_POWER_OF_CHOSEN = Regex("""(.+?) deals damage equal to the power of the chosen creature or card to target (.+?)\.""")
    val DEVOUR_QUALITY = Regex("""Devour (\w+) (\d+)""")
    val LOOK_X_PUT_ANY_NUMBER_ONTO_BATTLEFIELD_SHUFFLE = Regex("""[Ll]ook at the top X cards of your library\. Put any number of (.+?) from among them onto the battlefield tapped, then shuffle\.""")
    val COMBAT_DAMAGE_CANT_BE_PREVENTED = Regex("""Combat damage can't be prevented\.""")
    val COUNTERS_WOULD_BE_PUT_TWICE = Regex("""If one or more counters would be put on an? (.+?), twice that many of each of those kinds of counters are put on it instead\.""")
    val DOUBLE_POWER_UNTIL_EOT = Regex("""[Dd]ouble (?:the power of target (.+?)|target (.+?)'s power) until end of turn\.""")
    val RETURN_UP_TO_HAND_THEN_UP_TO_BATTLEFIELD = Regex("""Return up to (\w+) target (.+?) from your graveyard to your hand, then return up to (\w+) target (.+?) from your graveyard to the battlefield tapped\.""")
    val BLOCKS_A = Regex("""(.+?) blocks an? (.+)""")
    val STATIONS_USING_TOUGHNESS = Regex("""Each (.+?) stations permanents using its toughness rather than its power\.""")

    // eoe chunk 12-13: multicolored and artifact wordings
    val POWER_AND_TOUGHNESS_PLUS = Regex("""(.+?)'s power is equal to (.+?) and its toughness is equal to that number plus (\d+)\.""")
    val MAY_REMOVE_COUNTER_FROM_EACH_OF_TWO = Regex("""[Yy]ou may remove a \+1/\+1 counter from each of (\w+) (.+?)\. If you do, draw a card and create (.+)\.""")
    val BASE_PT_IF_INSTEAD = Regex("""target (.+?) has base power and toughness (\d+)/(\d+) until end of turn\. If (.+?), that creature has base power and toughness (\d+)/(\d+) until end of turn instead\.""")
    val CREATE_TAPPED_FOR_EACH = Regex("""[Cc]reate a tapped (.+? token) for each (.+)\.""")
    val ODD_EVEN_DESTROY_GAIN_CONTROL = Regex("""Choose odd or even\. Destroy each (.+?) with mana value of the chosen quality\. Then gain control of all (.+?) until end of turn\. Untap them\. They gain haste until end of turn\.""")
    val ARE_SUBTYPE_AND_HAVE_QUOTED = Regex("""(.+?) are ([A-Z]\w+)s in addition to their other types and have "(.+)"\.?""")
    val SPELLS_YOU_CAST_HAVE_AFFINITY = Regex("""Spells you cast have affinity for (\w+)\.""")
    val DESTROY_ALL_THEN_TARGET_PLAYERS_MILL_HALF = Regex("""Destroy all (.+?), then any number of target players each mill half their library, rounded (down|up)\.""")
    val TARGET_PLAYER_MILLS_EQUAL_LIFE = Regex("""Target player mills cards equal to your life total\.""")
    val DIES_OR_EXILED = Regex("""(another .+?) dies or is put into exile""")
    val SELF_DIES_OR_EXILED_WHILE_POWER = Regex("""When (.+?) dies or is put into exile while its power is (\d+) or greater, (.+)""")
    val EXILE_EACH_OPPONENTS_GRAVEYARD = Regex("""[Ee]xile each opponent's graveyard\.""")
    val ACTIVATED_COSTS_X_LESS_WHERE = Regex("""This ability costs \{X\} less to activate, where X is (.+?)\.""")
    val YOU_CONTROL_TARGET_OPPONENT_NEXT_TURN = Regex("""You control target opponent during their next turn\.""")
    val PLAY_LAND_OR_CAST = Regex("""you play a land or cast a spell""")
    val LIFE_BECOMES_HALF_STARTING = Regex("""[Yy]our life total becomes half your starting life total, rounded (up|down)\.""")
    val ADD_X_ANY_ONE_COLOR_COUNTERS = Regex("""Add X mana of any one color, where X is the number of (\S+) counters on (.+)\.""")
    val TARGET_PLAYER_DRAWS_AND_GAINS = Regex("""Target player draws (\w+) cards and gains (\d+) life\.""")
    val THAT_PLAYER_GETS_POISON = Regex("""[Tt]hat player gets (\w+) poison counters\.""")
    val CREATE_COPY_EXCEPT_LEGENDARY = Regex("""Create a token that's a copy of target (.+?), except it's legendary\.""")
    val EQUIPPED_GETS_AND_HAS_QUOTED = Regex("""Equipped creature gets ([+-]\d+)/([+-]\d+) and has "(.+?\.)"""")
    val TAP_X_UNTAPPED = Regex("""Tap X untapped (.+)""")
    val TARGET_GETS_PLUS_X_UNTIL_EOT = Regex("""Target (.+?) gets \+X/\+0 until end of turn\.""")
    val EXILED_CARD_WITH_WARP = Regex("""exiled card with (\w+)""")
    val PROPERTY_OF_SACRIFICED = Regex("""the (mana value|power) of the sacrificed (\w+)""")
    val CARDS_AND_CARDS = Regex("""(.+?) cards and (.+?) cards""")
    val N_OR_FEWER_IN_HAND = Regex("""you have (\w+) or fewer cards in hand""")
    val IT_DEALS_DAMAGE_EQUAL_TO_ITS_TO_EACH = Regex("""[Ii]t deals damage equal to its (mana value|power) to each (.+)\.""")
    val WARPED_CARD_YOU_OWN_IN_EXILE = Regex("""warped (.+?) you own in exile""")
}
