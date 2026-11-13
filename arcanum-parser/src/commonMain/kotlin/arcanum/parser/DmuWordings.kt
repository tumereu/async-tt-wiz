package arcanum.parser

import arcanum.api.Objects
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
    private val TIMES_KICKED = Regex("""(?:the number of )?times? (.+) was kicked""")

    /** "time it was kicked", "the number of times this spell was kicked": how many of its kicker costs were paid (CR 702.33d). */
    fun timesKicked(text: String, isSelf: (String) -> Boolean): Value? = TIMES_KICKED.matchEntire(text)
        ?.takeIf { it.groupValues[1] == "it" || isSelf(it.groupValues[1]) }
        ?.let { Value.TimesPaid(arcanum.api.Keyword.Kicker.Kind, Objects.Self) }
}
