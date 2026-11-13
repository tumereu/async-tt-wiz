package arcanum.parser

import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.Value

/** Wordings first met in Phyrexia: All Will Be One (ONE). */
internal object One {
    // one chunk 01: Carnivorous Canopy, Reject Imperfection, Serum Snare (and tmt's "If its mana value was 4 or less"): the earlier target's
    // mana value as it last existed (CR 608.2h)
    val IF_MANA_VALUE_WAS = Regex(
        """If (?:(?:its|that (?:permanent|spell|creature|card)'s) mana value was|(?:it|that (?:permanent|spell|creature|card)) had mana value) (\d+) or (less|greater), (.+)""")
    // one chunk 01: Expand the Sphere
    val IF_PUT_FEWER_REPEAT_DIFFERENCE = Regex("""If you put fewer than (\w+) (?:\w+ )?(?:lands|cards) onto the battlefield this way, (.+) a number of times equal to the difference\.""")
    // one chunk 01: Ichormoon Gauntlet
    val CHOOSE_COUNTER_PUT_ADDITIONAL = Regex("""Choose a counter on target (\w+)\. Put an additional counter of that kind on that (\w+)\.""")
    // one chunk 01: Tekuthal, Inquiry Dominus
    val WOULD_DO_TWICE_INSTEAD = Regex("""If you would ([a-z ]+), ([a-z ]+) twice instead\.""")
    // one chunk 02: Skrelv, Defector Mite
    val CHOOSE_COLOR_GAINS_HEXPROOF_FROM_UNBLOCKABLE_BY = Regex(
        """Choose a color\. (Another target|Target) (.+?) gains (?:(.+?) and )?hexproof from that color until end of turn\. (?:It|That creature) can't be blocked by (\w+) of that color this turn\.""")
    // one chunk 04: Blade of Shared Souls
    val BECOMES_ATTACHED_TO = Regex("""(?:[Ww]henever )?(.+?) becomes attached to an? (\w+)""")
    val WHILE_ATTACHED_MAY_BECOME_COPY = Regex("""[Ff]or as long as (.+?) remains attached to (?:it|that creature), you may have that creature become a copy of another target (\w+ you control)\.""")
    // one chunk 03: Goliath Hatchery, "choose a creature you control, then draw cards equal to its total toxic value."
    val CHOOSE_THEN = Regex("""Choose an? ([^,]+), then (.+)\.""")
    // one chunk 03: Anoint with Affliction, Bring the Ending
    val EXILE_OR_COUNTER_THAT = Regex("""(Exile) that (?:creature|permanent|card)\.|(Counter) that spell\.""")
    val DRAW_EQUAL_TO_ITS_TOTAL = Regex("""Draw cards equal to its total (\w+) value\.""")
    // one chunk 05: Black Sun's Twilight, "return a creature card with mana value X or less from your graveyard to the battlefield tapped."
    val RETURN_A_FROM_YOUR_GRAVEYARD = Regex("""[Rr]eturn an? (.+?) from your graveyard to the battlefield( tapped)?\.""")
}

/** Phrases first met in ONE. */
internal object OnePhrases {
    // one chunk 03 (Corrupted): "an opponent has three or more poison counters", "you have …", "a player has …" (counters on a player, CR 122.1f)
    private val SOMEONE_HAS_COUNTERS = Regex("""(an opponent|a player|you) (?:has|have) (.+)""")
    private val HAS_N_OR_MORE_COUNTERS = Regex("""(?:has|have) (\w+) or more (\w+) counters""")
    // one chunk 03: "each opponent who has three or more poison counters" (only those opponents, CR 102.3)
    private val EACH_WHO = Regex("""[Ee]ach (opponent|player) who (.+)""")

    /** [player] "has N or more <kind> counters"; null for other wordings. */
    fun playerHas(text: String, player: Players): Condition? {
        val m = HAS_N_OR_MORE_COUNTERS.matchEntire(text) ?: return null
        val kind = Counters.kind(m.groupValues[2]) ?: return null
        return Condition.Compare(Value.PlayerCounters(player, kind), Comparison.AtLeast, Value.Fixed(Numbers.count(m.groupValues[1]) ?: return null))
    }

    fun condition(text: String): Condition? {
        SOMEONE_HAS_COUNTERS.matchEntire(text)?.let { m ->
            val has = "has " + m.groupValues[2]
            return when (m.groupValues[1]) {
                "you" -> playerHas(has, Players.You)
                "an opponent" -> Condition.PlayerExists(PlayerFilter.And(listOf(PlayerFilter.Opponent, PlayerFilter.Where(playerHas(has, Players.Tested) ?: return null))), atLeast = 1)
                else -> Condition.PlayerExists(PlayerFilter.Where(playerHas(has, Players.Tested) ?: return null), atLeast = 1)
            }
        }
        return null
    }

    /** "each opponent who has three or more poison counters": the players who match. */
    fun eachWho(phrase: String): Players? {
        val m = EACH_WHO.matchEntire(phrase) ?: return null
        val who = PlayerFilter.Where(playerHas(m.groupValues[2], Players.Tested) ?: return null)
        return Players.Each(if (m.groupValues[1] == "opponent") PlayerFilter.And(listOf(PlayerFilter.Opponent, who)) else who)
    }

    /** "its controller has three or more poison counters" in a spell's later paragraph: "its" is the spell's one object target. */
    fun itsController(text: String, targets: List<TargetSlot>): Condition? {
        if (!text.startsWith("its controller ")) return null
        val slot = targets.singleOrNull()?.takeIf { it.what is TargetKind.Objects } ?: return null
        return playerHas(text.removePrefix("its controller "), Players.ControllerOf(Objects.Target(slot.name)))
    }
}
