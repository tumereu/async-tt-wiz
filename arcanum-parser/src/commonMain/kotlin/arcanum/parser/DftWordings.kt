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
        // dft chunk 03: "a Pirate and a Vehicle attacked this combat" (attacking creatures of each)
        Dft.A_AND_A_ATTACKED_THIS_COMBAT.matchEntire(text)?.let { m ->
            val attacking = ObjectFilter.HasStatus(Status.Attacking)
            return Condition.And(m.groupValues.drop(1).map { Condition.Exists(Filters.all(Filters.noun(it) ?: return null, attacking), atLeast = 1) })
        }
        return null
    }

    private val PERMANENTS_YOU_CONTROL_THAT_ARE = Regex("""the number of permanents you control that are (.+)""")
    private val N_PLUS = Regex("""(\d+) plus (.+)""")
    private val ENTERED_UNDER_YOUR_CONTROL_THIS_TURN = Regex("""the number of (.+) that entered the battlefield under your control this turn""")
}
