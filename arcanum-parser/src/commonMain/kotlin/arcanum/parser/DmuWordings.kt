package arcanum.parser

import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.EventPattern
import arcanum.api.Measure
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Value

/** Wordings first met in Dominaria United (DMU). */
internal object Dmu {
    // dmu chunk 01: Archangel of Wrath, Stronghold Arena, Temporal Firestorm: two kicker costs, each payable once (CR 702.33b, d)
    val KICKER_AND_OR = Regex("""Kicker (\{[^ ]+\}) and/or (\{[^ ]+\})""")
    // dmu chunk 01: Monstrous War-Leech, Urborg Lhurgoyf: something done as this permanent enters (CR 614.1c)
    val AS_ENTERS_DO = Regex("""As (.+?) enters, (.+)""")
    // dmu chunk 01: Urborg Lhurgoyf
    val MILL_FOR_EACH = Regex("""[Mm]ill (\w+) cards? for each (.+)\.""")
    // dmu chunk 01: Balduvian Atrocity: "It gains haste." with no duration (until it leaves, CR 611.2a)
    val IT_GAINS = Regex("""It gains ([a-z ,]+)\.""")
    // dmu chunk 01: Heroic Charge
    val THOSE_CREATURES_ALSO_GAIN = Regex("""If (.+?), those creatures also gain (.+) until end of turn\.""")
    // dmu chunk 01: Temporal Firestorm
    val CHOOSE_UP_TO_X = Regex("""Choose up to X (.+)\.""")
    val THOSE_PHASE_OUT = Regex("""Those (?:permanents|creatures) phase out\.""")
    // dmu chunk 01: Pixie Illusionist
    val BECOMES_BASIC_LAND_TYPE_OF_CHOICE = Regex("""Target (.+?) becomes the basic land type of your choice until end of turn\.""")
    // dmu chunk 02: Nael, Avizoa Aeronaut; Shadow Prophecy; Slimefoot's Survey
    val LOOK_THEN_PUT = Regex("""(Look at the top \w+ cards of your library\.) (Put .+)""")
    val PUT_N_OF_THEM_REST = Regex("""Put (up to )?(\w+) of them (into your hand|on top of your library) and the rest (into your graveyard|on the bottom(?: of your library)? in a random order)\.""")
    // dmu chunk 02: Bortuk Bonerattle
    val RETURN_IT = Regex("""Return (?:it|that card) to (the battlefield|your hand)( tapped)?\.""")
    val IF_ITS_MANA_VALUE_AT_MOST = Regex("""(.+?) if its mana value is less than or equal to (.+)\.""")
    // dmu chunk 03: Keldon Flamesage
    val MAY_EXILE_MV_X_FROM_AMONG = Regex("""You may exile an? (.+?) card with mana value X or less from among them\.""")
    // dmu chunk 04
    val CANT_PHASE_IN_WHILE_YOU_CONTROL = Regex("""(?:It|They) can't phase in for as long as you control (.+)\.""")
    val FOR_EACH_DESTROYED_CONTROLLER_CREATES = Regex("""For each (.+?) destroyed this way, its controller creates (.+)\.""")
    // dmu chunk 05
    val MAY_CAST_AS_FLASH_IF = Regex("""You may cast this spell as though it had flash if (.+)\.""")
    val SPEND_ONLY_OR_KICKED = Regex("""cast (?:an? )?(.+?) spell or a kicked spell""")
    val PUT_IT_ON_BOTTOM = Regex("""(You may p|P)ut (?:it|that card) on the bottom of your library\.""")
    val DIDNT_PUT_ONTO_BATTLEFIELD = Regex("""If you didn't put a card onto the battlefield this way, (.+)\.""")
    val IF_SELF_IS_A = Regex("""If (.+?) is an? ([A-Z]\w+), (.+)\.""")
    val YOU_MAY_SACRIFICE_IF_YOU_DO = Regex("""You may sacrifice (an? .+?)\. If you do, (.+)""")
    val EACH_OPPONENT_MAY_SACRIFICE_SHARING = Regex("""[Ee]ach opponent may sacrifice (an? .+?) of their choice that shares a card type with it\.""")
    val FOR_EACH_OPPONENT_WHO_DOESNT = Regex("""For each opponent who doesn't, (.+)""")
    val ADDITIONAL_LIFE_REDUCES_COLOR = Regex("""As an additional cost to cast (\w+) permanent spells, you may pay (\d+) life\. Those spells cost (\{[WUBRG]\}) less to cast if you paid life this way\. This effect reduces only the amount of (\w+) mana you pay\.""")
    private val OPPONENT_CAST_N_OR_MORE = Regex("""an opponent cast (\w+) or more spells this turn""")
    private val OPPONENT_CONTROLLED_DEALT_EXCESS = Regex("""an? (.+?) an opponent controlled was dealt excess damage this turn""")
    private val ACTIVATED_N_OR_MORE_TIMES = Regex("""this ability has been activated (\w+) or more times this turn""")
    private val X_IS_N_OR = Regex("""X is (\w+) or (less|more)""")

    /** dmu chunk 05: conditions. */
    fun condition(text: String): Condition? {
        // "an opponent cast two or more spells this turn": one opponent cast them all, not the total of all opponents
        OPPONENT_CAST_N_OR_MORE.matchEntire(text)?.let { m ->
            val n = Numbers.count(m.groupValues[1]) ?: return null
            return Condition.PlayerExists(PlayerFilter.And(listOf(PlayerFilter.Opponent, PlayerFilter.Where(Condition.HappenedThisTurn(
                EventPattern.Cast(ObjectFilter.Any, PlayerFilter.Is(Players.Tested)), atLeast = n, measure = Measure.Events)))), atLeast = 1)
        }
        // "a creature or planeswalker an opponent controlled was dealt excess damage this turn" (CR 120.4a; controlled as it was dealt it)
        OPPONENT_CONTROLLED_DEALT_EXCESS.matchEntire(text)?.let { m ->
            val noun = Filters.noun(m.groupValues[1]) ?: return null
            return Condition.HappenedThisTurn(EventPattern.Damage(ObjectFilter.Any, arcanum.api.RecipientFilter(objects = Filters.all(noun,
                ObjectFilter.ControlledBy(PlayerFilter.Opponent))), excess = true, per = arcanum.api.DamageOccurrence.Recipient), atLeast = 1, measure = Measure.Events)
        }
        // "this ability has been activated four or more times this turn" (counting this activation)
        ACTIVATED_N_OR_MORE_TIMES.matchEntire(text)?.let { m ->
            return Condition.Compare(Value.AbilityUsesThisTurn(resolved = false), Comparison.AtLeast, Value.Fixed(Numbers.count(m.groupValues[1]) ?: return null))
        }
        // "X is 3 or less": this spell's X (CR 107.3)
        X_IS_N_OR.matchEntire(text)?.let { m ->
            return Condition.Compare(Value.CastX(Objects.Self), if (m.groupValues[2] == "less") Comparison.AtMost else Comparison.AtLeast,
                Value.Fixed(Numbers.count(m.groupValues[1]) ?: return null))
        }
        return null
    }

    private val TIMES_KICKED = Regex("""(?:the number of )?times? (.+) was kicked""")

    /** "time it was kicked", "the number of times this spell was kicked": how many of its kicker costs were paid (CR 702.33d). */
    fun timesKicked(text: String, isSelf: (String) -> Boolean): Value? = TIMES_KICKED.matchEntire(text)
        ?.takeIf { it.groupValues[1] == "it" || isSelf(it.groupValues[1]) }
        ?.let { Value.TimesPaid(arcanum.api.Keyword.Kicker.Kind, Objects.Self) }
}
