package arcanum.parser

import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.TargetKind
import arcanum.api.TargetSlot
import arcanum.api.Value
import arcanum.api.Zone

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
    // one chunk 07: Melira, the Living Cure
    val WOULD_GET_COUNTERS_INSTEAD_ONE_THEN_CANT = Regex("""If you would get one or more (\w+) counters, instead you get one \1 counter and you can't get additional \1 counters this turn\.""")
    // one chunk 07: Phyrexian Vindicator
    val IF_DAMAGE_WOULD_PREVENT_WHEN_PREVENTED = Regex("""If damage would be dealt to (.+?), prevent that damage\. When damage is prevented this way, (.+)""")
    // one chunk 07: Kethek, Crucible Goliath
    val MAY_SACRIFICE_REVEAL_UNTIL_LESSER = Regex("""[Yy]ou may sacrifice (an? .+?|another .+?)\. If you do, reveal cards from the top of your library until you reveal an? (.+?) card with lesser mana value, put it onto the battlefield, then put the rest on the bottom of your library in a random order\.""")
    // one chunk 07: Evolved Spinoderm
    val HAS_AS_LONG_AS_OTHERWISE = Regex("""(.+?) has ([a-z ,]+) as long as (.+?)\. Otherwise, it has ([a-z ,]+)\.""")
    // one chunk 07: Conduit of Worlds
    val CANT_CAST_ADDITIONAL_SPELLS_THIS_TURN = Regex("""You can't cast (?:additional|more) spells this turn\.""")
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
    // one chunk 06: Jace, the Perfected Mind and Nahiri, the Unforgiving, "Until your next turn, up to one target creature gets -3/-0."
    val UNTIL_YOUR_NEXT_TURN_PREFIX = Regex("""Until your next turn, (.+)\.""")
    // one chunk 06: Nahiri, "up to one target creature attacks a player each combat if able" (as "Until your next turn, …" is reread)
    val ATTACKS_EACH_COMBAT_UNTIL_YOUR_NEXT_TURN = Regex("""(.+?) attacks( a player)? each combat if able until your next turn\.""")
    // one chunk 06: Nahiri, the Unforgiving
    val EXILE_FROM_GRAVEYARD_COPY_HASTE_EXILE_AT_END = Regex(
        """Exile target (.+?) from your graveyard\. Create a token that's a copy of it\. That token gains (\w+)\. Exile it at the beginning of the next end step\.""")
    val WITH_MANA_VALUE_LESS_THAN_SELFS = Regex("""(.+) with mana value less than (.+?'s (?:loyalty|power|toughness))""")
    // one chunk 06: Vraska, Betrayal's Sting
    val BECOMES_WITH_LOSES_OTHER_TYPES_AND_ABILITIES = Regex("""(Target .+?) becomes an? ([A-Z][A-Za-z ]+?) with "(.+?)" and loses all other card types and abilities\.""")
    val IF_FEWER_COUNTERS_GET_DIFFERENCE = Regex("""If (target player|target opponent|that player) has fewer than (\w+) (\w+) counters, they get a number of (\w+) counters equal to the difference\.""")
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

    // one chunk 06: "a graveyard has twenty or more cards in it" (any player's, CR 404.1)
    private val A_GRAVEYARD_HAS = Regex("""a graveyard has (\w+) or more cards in it""")
    // one chunk 06: "enchanted creature has toxic" (any toxic value, CR 702.164)
    private val ATTACHED_HAS_KEYWORD = Regex("""(?:enchanted|equipped) creature has (\w+)""")
    // one chunk 06: "… as you activate this ability": the value then, already how a where-X value of an activated ability is read (CR 602.2b, 601.2b)
    private val AS_YOU_ACTIVATE = Regex("""(.+) as you activate this ability""")

    fun value(text: String, exiled: Objects?): Value? = AS_YOU_ACTIVATE.matchEntire(text)?.let { Values.value(it.groupValues[1], exiled) }

    fun condition(text: String): Condition? {
        A_GRAVEYARD_HAS.matchEntire(text)?.let { m ->
            val n = Numbers.count(m.groupValues[1]) ?: return null
            return Condition.PlayerExists(PlayerFilter.Where(Condition.Exists(
                ObjectFilter.And(listOf(ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.Is(Players.Tested)))), atLeast = n)), atLeast = 1)
        }
        ATTACHED_HAS_KEYWORD.matchEntire(text)?.let { m ->
            val kind = OracleParser.keyword(m.groupValues[1].replaceFirstChar { it.uppercase() })?.kind
                ?: Filters.AMOUNT_KEYWORD_KINDS.firstOrNull { it.text.equals(m.groupValues[1], ignoreCase = true) } ?: return null
            return Condition.ObjectMatches(Objects.AttachedTo(Objects.Self), ObjectFilter.HasKeyword(kind))
        }
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
