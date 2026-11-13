package arcanum.parser

import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.EventPattern
import arcanum.api.KeywordKind
import arcanum.api.Measure
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.SharedQuality
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
    val WHILE_ATTACHED_MAY_BECOME_COPY = Regex("""[Ff]or as long as (.+?) remains attached to (?:it|that creature), you may have that creature become a copy of another target (\w+ you control)\.""")
    // one chunk 08: Compleat Devotion, Hexgold Slash, Porcelain Zealot
    val IT_HAS_KEYWORD = Regex("""(?:that creature|it) has ([a-z]+)""")
    val IF_IT_INSTEAD = Regex("""If ((?:that creature|it) .+?), (?:instead (.+)|(.+) instead)\.""")
    val SELF_DEALS_N_TO_IT = Regex("""(.+) deals (\d+) damage to (?:that creature|it)""")
    // one chunk 08: The Filigree Sylex
    val COST_AND_SACRIFICE = Regex("""([A-Z].+?) and (sacrifice .+)""")
    // one chunk 03: Goliath Hatchery, "choose a creature you control, then draw cards equal to its total toxic value."
    val CHOOSE_THEN = Regex("""Choose an? ([^,]+), then (.+)\.""")
    // one chunk 03: Anoint with Affliction, Bring the Ending
    val EXILE_OR_COUNTER_THAT = Regex("""(Exile) that (?:creature|permanent|card)\.|(Counter) that spell\.""")
    val DRAW_EQUAL_TO_ITS_TOTAL = Regex("""Draw cards equal to its total (\w+) value\.""")
    // one chunk 05: Black Sun's Twilight, "return a creature card with mana value X or less from your graveyard to the battlefield tapped."
    val RETURN_A_FROM_YOUR_GRAVEYARD = Regex("""[Rr]eturn an? (.+?) from your graveyard to the battlefield( tapped)?\.""")
    val NO_MORE_THAN_CAN_ATTACK = Regex("""No more than (\w+) creatures? can attack (.+?) each combat\.""")
    val FOR_EACH_PLAYER_CHOOSE_SACRIFICE_REST = Regex("""For each player, choose an? (\w+) that player controls\. Each player sacrifices all (\w+) they control not chosen this way\.""")
    val ACTIVATE_LOYALTY_TWICE = Regex("""You may activate loyalty abilities of (.+?) twice this turn rather than only once\.""")
    val RETURN_ONE_OF_THEM = Regex("""Return one of them to its owner's hand\.""")
    val CANT_ENTER_THE_BATTLEFIELD = Regex("""([A-Z].+? cards in .+?) can't enter the battlefield\.""")
    val PLAYERS_CANT_CAST_FROM = Regex("""(Players|Your opponents) can't cast (.+?) from (graveyards|exile|graveyards or exile)\.""")
    val REVEAL_X_PLUS_CHOOSE_AND_OR_IF_X_BATTLEFIELD_OR_HAND = Regex(
        """Reveal the top X plus (\w+) cards of your library\. Choose (an? .+? card) and/or (an? .+? card) from among them\. Put those cards into your hand and the rest on the bottom of your library in a random order\. If X is (\d+) or more, instead put the chosen cards onto the battlefield or into your hand and the rest on the bottom of your library in a random order\.""")
    val ARE_TYPE_SAME_IS_TRUE_FOR_SPELLS_AND_CARDS = Regex(
        """(.+?) you control are (the chosen type|artifacts|enchantments) in addition to their other types\. The same is true for (.+?) spells you control and (.+?) cards you own that aren't on the battlefield\.""")
    val CANT_BE_TARGET_OF_SPELLS_OR_ABILITIES_FROM = Regex("""(.+?) can't be the target of (\S+) spells your opponents control or abilities from \2 sources your opponents control\.""")
    val THAT_SOURCES_CONTROLLER_SACRIFICES = Regex("""That source's controller sacrifices (that many|an?|\w+) (.+?) of their choice\.""")
    val ATTACH_ALL_ATTACHED_TO_THAT_TO = Regex("""Attach all (Equipment|Auras) attached to (?:that creature|it) to (.+)\.""")
    val YOU_PUT_COUNTERS_ON_PERMANENT_OR_PLAYER = Regex("""(?:[Ww]henever )?you put one or more counters on a permanent or player""")
    val WHEN_YOU_DISCARD_THIS_WAY = Regex("""When you discard a card this way, (.+)""")
    val DEALS_TO_OPPONENT_CREATURE_OR_PLANESWALKER = Regex("""(.+?) deals (that much|\d+) damage to target opponent, creature an opponent controls, or planeswalker an opponent controls\.""")
    val RETURN_IT_AT_NEXT_END_STEP = Regex("""Return (?:it|that card) to the battlefield under its owner's control at the beginning of (?:the|(that player's)) next end step\.""")
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
    // one chunk 09: Capricious Hellraiser
    val EXILE_RANDOM_CHOOSE_COPY_MAY_CAST = Regex(
        """[Ee]xile (\w+) cards at random from your graveyard\. Choose an? (.+?) card from among them and copy it\. You may cast the copy without paying its mana cost\.""")
    // one chunk 09: Red Sun's Twilight
    val DESTROY_UP_TO_X_IF_COPY_EACH_HASTE_EXILE = Regex(
        """Destroy up to X target (.+?)\. If X is (\d+) or more, for each (.+?) destroyed this way, create a token that's a copy of it\. Those tokens gain (\w+)\. Exile them at the beginning of the next end step\.""")
    // one chunk 09: Ria Ivor, Bane of Bladehold
    val NEXT_TIME_COMBAT_DAMAGE_TO_PLAYERS_PREVENT_CREATE = Regex(
        """[Tt]he next time (target .+?) would deal combat damage to one or more players this combat, prevent that damage\. If damage is prevented this way, create that many (.+?)\.?""")
    // one chunk 09: Monument to Perfection
    val BECOMES_LOSES_ALL_GAINS = Regex("""(.+?) becomes an? (\d+)/(\d+) ([A-Z]\w*(?: [A-Z]\w*)*) artifact creature, loses all abilities, and gains (.+)\.""")
    // one chunk 09: Geth, Thane of Contracts (a granted ability)
    val WOULD_LEAVE_EXILE_INSTEAD_ANYWHERE_ELSE = Regex("""If (.+?) would leave the battlefield, exile it instead of putting it anywhere else\.""")
    val IT_GAINS_QUOTED = Regex("""It gains "(.+)"\.?""")
    val IF_X_DESTROY_ALL_OTHER = Regex("""If X is (\d+) or more, destroy all other (\w+)\.""")
    val ITS_ATTACHED = Regex("""(?:it's|it is|that creature is) (equipped|enchanted)""")
    // one chunk 09: Awaken the Sleeper, "destroy all Equipment attached to that creature"
    val DESTROY_ALL_ATTACHED_TO = Regex("""([Yy]ou may )?[Dd]estroy all (Equipment|Auras) attached to (that creature|it)\.""")
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

    // one chunk 08: "you control two or fewer other lands" (fastlands), "that player controls two or more basic lands" (the event's player)
    private val CONTROLS_N_OR_MORE_OR_FEWER = Regex("""(you|that player) controls? (\w+) or (more|fewer) (.+)""")

    // one chunk 08: Churning Reservoir, "an oil counter was removed from a permanent you controlled this turn or a permanent with an oil counter on
    // it was put into a graveyard this turn"
    private val TWO_THIS_TURN = Regex("""(.+? this turn) or (.+ this turn)""")
    private val COUNTER_WAS_REMOVED_THIS_TURN = Regex("""an? (\S+) counter was removed from (.+?) this turn""")
    private val PUT_INTO_A_GRAVEYARD_THIS_TURN = Regex("""(an? .+?) was put into a graveyard this turn""")
    // one chunk 08: Mirran Safehouse, "as long as this artifact is on the battlefield" (the zone its static ability works in, CR 113.6)
    // one chunk 09: "there are nine or more lands with different names among the basic, Sphere, and Locus lands you control" (CR 201.2)
    private val DIFFERENT_NAMES_AMONG = Regex("""there are (\w+) or more (\w+) with different names among (?:the )?(.+?) (\w+) you control""")
    private val IS_ON_THE_BATTLEFIELD = Regex("""(.+) is on the battlefield""")

    fun value(text: String, exiled: Objects?): Value? {
        // one chunk 10: "the mana value of the discarded card" (as it is in the graveyard, CR 202.3)
        if (text == "the mana value of the discarded card") return Value.Of(arcanum.api.NumericProperty.ManaValue, Objects.Bound("discarded"), arcanum.api.Combine.Single)
        return AS_YOU_ACTIVATE.matchEntire(text)?.let { Values.value(it.groupValues[1], exiled) }
    }

    /** A keyword named bare ("flying", "toxic": an amount keyword with any amount). */
    fun keywordKind(word: String): KeywordKind? = OracleParser.keyword(word.replaceFirstChar { it.uppercase() })?.kind
        ?: Filters.AMOUNT_KEYWORD_KINDS.firstOrNull { it.text.equals(word, ignoreCase = true) }

    fun condition(text: String, self: Set<String>): Condition? {
        DIFFERENT_NAMES_AMONG.matchEntire(text)?.takeIf { it.groupValues[2] == it.groupValues[4] }?.let { m ->
            val n = Numbers.count(m.groupValues[1]) ?: return null
            val noun = m.groupValues[2].removeSuffix("s")
            val kinds = m.groupValues[3].split(LazyRegexes.AND_LIST).map { Filters.noun("$it $noun you control") ?: return null }
            val among = if (kinds.size == 1) kinds.single() else ObjectFilter.Or(kinds)
            return Condition.Compare(Value.CountDistinct(Objects.Each(among), SharedQuality.Name), Comparison.AtLeast, Value.Fixed(n))
        }
        IS_ON_THE_BATTLEFIELD.matchEntire(text)?.takeIf { it.groupValues[1] in self }?.let {
            return Condition.ObjectMatches(Objects.Self, ObjectFilter.InZone(Zone.Battlefield))
        }
        TWO_THIS_TURN.matchEntire(text)?.let { m ->
            val first = Conditions.condition(m.groupValues[1], self) ?: return@let
            val second = Conditions.condition(m.groupValues[2], self) ?: return@let
            return Condition.Or(listOf(first, second))
        }
        // a counter removed by anything, from the permanent as it was then (its controller then, CR 603.10a; "you controlled": CR 108.4)
        COUNTER_WAS_REMOVED_THIS_TURN.matchEntire(text)?.let { m ->
            val kind = Counters.kind(m.groupValues[1]) ?: return null
            val from = Filters.noun(m.groupValues[2].removeArticle().replace(" you controlled", " you control")) ?: return null
            return Condition.HappenedThisTurn(EventPattern.CountersRemoved(from, kind), atLeast = 1, measure = Measure.Events)
        }
        // "a permanent …": from the battlefield (only there is it a permanent, CR 110.1), tested as it last existed there (CR 603.10a)
        PUT_INTO_A_GRAVEYARD_THIS_TURN.matchEntire(text)?.let { m ->
            val noun = Filters.noun(m.groupValues[1].removeArticle()) ?: return null
            val from = if (m.groupValues[1].startsWith("a permanent")) Zone.Battlefield else null
            return Condition.HappenedThisTurn(EventPattern.ZoneChange(noun, from = from, to = Zone.Graveyard), atLeast = 1, measure = Measure.Events)
        }
        CONTROLS_N_OR_MORE_OR_FEWER.matchEntire(text)?.let { m ->
            val n = Numbers.count(m.groupValues[2]) ?: return null
            val filter = Filters.noun(m.groupValues[4] + if (m.groupValues[1] == "you") " you control" else " that player controls") ?: return null
            return if (m.groupValues[3] == "more") Condition.Exists(filter, atLeast = n)
                else Condition.Compare(Value.Count(Objects.Each(filter)), Comparison.AtMost, Value.Fixed(n))
        }
        A_GRAVEYARD_HAS.matchEntire(text)?.let { m ->
            val n = Numbers.count(m.groupValues[1]) ?: return null
            return Condition.PlayerExists(PlayerFilter.Where(Condition.Exists(
                ObjectFilter.And(listOf(ObjectFilter.InZone(Zone.Graveyard), ObjectFilter.OwnedBy(PlayerFilter.Is(Players.Tested)))), atLeast = n)), atLeast = 1)
        }
        ATTACHED_HAS_KEYWORD.matchEntire(text)?.let { m ->
            val kind = keywordKind(m.groupValues[1]) ?: return null
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
