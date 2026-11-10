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

/** Wordings first met in Innistrad Remastered (INR). */
internal object Inr {
    // inr chunk 04: Distended Mindbender
    val YOU_CHOOSE_FROM_IT_A_AND_B = Regex("""You choose from it (an? (?:[a-z]+ )?card)( with .+?)? and (an? (?:[a-z]+ )?card)( with .+?)?\.""")
    val LOOK_TOP_X_PUT_N_REST_BOTTOM_RANDOM = Regex("""Look at the top X cards of your library\. Put (\w+) of them into your hand and the rest on the bottom of your library in a random order\.""")
    // inr chunk 01: Conduit of Storms: mana added later this turn (a delayed trigger, CR 603.7b)
    val ADD_AT_NEXT_MAIN_PHASE_THIS_TURN = Regex("""[Aa]dd ((?:\{[WUBRGC]\})+) at the beginning of your next main phase this turn\.""")
    // inr chunk 01: Garruk Relentless: "That creature deals damage equal to its power to him." (the earlier target fights back one way)
    val THAT_CREATURE_DEALS_POWER_TO_SELF = Regex("""That creature deals damage equal to its power to (him|her|it)\.""")
    // inr chunk 01: Garruk, the Veil-Cursed: "Sacrifice a creature. If you do, …" (CR 701.21a)
    val SACRIFICE_A_IF_YOU_DO = Regex("""(Sacrifice an? [^.]+?)\. If you do, (.+)""")
    val NAMES_THE_SACRIFICED = Regex("""\bthat (?:creature|permanent|artifact|land|card)\b|\bthe sacrificed\b""")
    // inr chunk 01: Soulcipher Board: the other card stays on top (CR 401.4)
    val LOOK_TOP_N_PUT_ONE_INTO_GRAVEYARD = Regex("""Look at the top (\w+) cards of your library\. Put one of them into your graveyard\.""")
    // inr chunk 01: Ravager of the Fells: the creature target belongs to the player or the planeswalker's controller (CR 115.1)
    val DEALS_TO_OPPONENT_OR_PW_AND_UP_TO_ONE_CREATURE_THAT_PLAYER_CONTROLS = Regex(
        """(.+?) deals (\d+) damage to target ((?:opponent|player) or planeswalker) and (\d+) damage to up to one target creature that player or that planeswalker's controller controls\.""")
    // inr chunk 06: Briseis: "Your opponents can't cast spells with mana value 3 or less." (CR 101.2)
    val OPPONENTS_CANT_CAST_SPELLS_WITH = Regex("""Your opponents can't cast (spells with .+)\.""")
    // inr chunk 06: Grisly Eelfish: "Creatures your opponents control attack this turn if able." (CR 508.1d)
    val CREATURES_ATTACK_THIS_TURN_IF_ABLE = Regex("""(Creatures .+?) attack this turn if able\.""")
    // inr chunk 06: Aberrant Scholar: "Mill a card. If an instant or sorcery card was milled this way, transform this creature." (CR 701.17a)
    val MILL_IF_MILLED_TRANSFORM = Regex("""Mill (an?|\w+) cards?\. If an? (.+?) card was milled this way, transform (?:this creature|it)\.""")
}

/** Conditions first met in INR. */
internal object InrPhrases {
    // inr chunk 01: the Innistrad werewolf transform conditions (CR 603.4)
    private val SPELLS_CAST_LAST_TURN = Regex("""(no spells were cast|a player cast (\w+) or more spells) last turn""")
    // inr chunk 01: "each player has 10 or less life"
    private val EACH_PLAYER_HAS_LIFE = Regex("""each player has (\d+) or (less|more) life""")

    // inr chunk 01: "Garruk has two or fewer loyalty counters on him" (this object)
    private val HAS_N_OR_FEWER_COUNTERS_ON = Regex("""(.+) has (\w+) or fewer (\S+) counters on (?:it|him|her)""")

    fun condition(text: String, self: Set<String>): Condition? {
        HAS_N_OR_FEWER_COUNTERS_ON.matchEntire(text)?.takeIf { it.groupValues[1] in self || it.groupValues[1].replaceFirstChar { c -> c.lowercase() } in self }?.let { m ->
            val n = Numbers.count(m.groupValues[2]) ?: return null
            return Condition.Not(Condition.ObjectMatches(Objects.Self, ObjectFilter.HasCounter(Counters.kind(m.groupValues[3]) ?: return null, atLeast = n + 1)))
        }
        SPELLS_CAST_LAST_TURN.matchEntire(text)?.let { m ->
            if (m.groupValues[2].isEmpty())
                return Condition.Not(Condition.HappenedLastTurn(EventPattern.Cast(ObjectFilter.Any, PlayerFilter.Any), atLeast = 1, measure = Measure.Events))
            // one player cast them all, not the total of all players
            val count = Numbers.count(m.groupValues[2]) ?: return null
            return Condition.PlayerExists(PlayerFilter.Where(Condition.HappenedLastTurn(
                EventPattern.Cast(ObjectFilter.Any, PlayerFilter.Is(Players.Tested)), atLeast = count, measure = Measure.Events)), atLeast = 1)
        }
        EACH_PLAYER_HAS_LIFE.matchEntire(text)?.let { m ->
            // no player has more (or less) than that
            val outside = if (m.groupValues[2] == "less") Comparison.Greater else Comparison.Less
            return Condition.Not(Condition.PlayerExists(PlayerFilter.Life(outside, Value.Fixed(m.groupValues[1].toInt())), atLeast = 1))
        }
        return null
    }
}
