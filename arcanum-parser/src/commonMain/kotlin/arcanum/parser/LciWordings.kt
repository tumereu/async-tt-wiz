package arcanum.parser

import arcanum.api.CardType
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.CostObjects
import arcanum.api.EventPattern
import arcanum.api.Measure
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.RecipientFilter
import arcanum.api.Value
import arcanum.api.Zone

/** Regular expressions of The Lost Caverns of Ixalan (lci) wordings read in [Sentences]. */
internal object Lci {
    // lci chunk 01: "Enchanted artifact is a Golem creature with base power and toughness 5/4 in addition to its other types." (CR 613.1d, 613.4b)
    val ATTACHED_IS_CREATURE_BASE_PT = Regex("""(?:Enchanted|Equipped) (?:artifact|land|permanent|creature) is an? ((?:[A-Z]\w+ )+)creature with base power and toughness (\d+)/(\d+) in addition to its other types\.""")
    // lci chunk 01: "If the discovered card's mana value is less than 10, create a number of tapped Treasure tokens equal to the difference." (CR 701.57c)
    val IF_DISCOVERED_LESS_CREATE_DIFFERENCE = Regex("""If the discovered card's mana value is less than (\d+), create a number of (tapped )?(.+? tokens?) equal to the difference\.""")
    // lci chunk 01: "remove those counters and transform it" after "If there are three or more bore counters on it"
    // lci chunk 07: also "remove those counters, transform this artifact, and create three Treasure tokens"
    val REMOVE_THOSE_COUNTERS_AND = Regex("""If there are (\w+) or more (\S+) counters on it, remove those counters(?: and|,) (.+)\.""")
    val RETURN_TO_ZONE_TARGET_IN_GRAVEYARD = Regex("""Return to (the battlefield|your hand) target (.+?) in your graveyard( with .+)?\.""")
    val RETURN_CHOSEN_FROM_GRAVEYARD_TO_HAND = Regex("""(?:Then )?[Rr]eturn (a|an|one|two|three|up to (?:one|two|three|four)) (.+?) cards? from your graveyard to your hand\.""")
    val THAT_LEFT_UNDER_YOUR_CONTROL = Regex("""(.+?) that left the battlefield under your control this turn""")
    // lci chunk 01: "Whenever you cast a permanent spell using mana produced by Tecutlan" (CR 106.3)
    val YOU_CAST_USING_MANA_FROM = Regex("""you cast an? (.+?) using mana produced by (.+)""")
    // lci chunk 02: "Craft with artifact {5}{W}{W}", "Craft with one or more Dinosaurs {4}{R}" (CR 702.167a)
    val THIS_OR_ANOTHER_ENTERED = Regex("""this (\w+) or another \1 entered the battlefield under your control this turn""")
    val EACH_COLOR_AMONG_ADD = Regex("""\{T\}: For each color among (.+), add one mana of that color\.""")
    val CRAFTED_WITH = Regex("""the exiled cards? used to craft (?:it|this \w+|[A-Z].*)""")
    val CRAFTED_WITH_VALUE = Regex("""(the mana value|the total power|the number of colors among) (?:of )?(the exiled cards? used to craft .+)""")
    val MAY_PUT_CRAFTED_ONTO_BATTLEFIELD = Regex("""You may put an exiled (.+? card) used to craft (.+?) onto the battlefield under your control( tapped and attacking)?\.""")
    val HAS_EACH_ACTIVATED_ABILITY_OF = Regex("""(.+) has each activated ability of (.+?)\.( You may activate each of those abilities only once each turn\.)?""")
    // lci chunk 03: "Then you may have it become a copy of another creature you control until end of turn." (layer 1, CR 707.2)
    val MAY_HAVE_IT_BECOME_COPY_OF_ANOTHER = Regex("""(?:Then )?[Yy]ou may have (?:it|this creature) become a copy of another (.+?) until end of turn\.""")
    // lci chunk 04: "Then for each card you put on top of your library, you draw a card and you lose 3 life." after a surveil (CR 701.25a)
    val FOR_EACH_PUT_ON_TOP = Regex("""(?:Then )?[Ff]or each card you put on top of your library, (.+)\.""")
    // lci chunk 04: "up to one other target permanent you control becomes a copy of that spell until end of turn" (layer 1, CR 707.2)
    val TARGET_BECOMES_COPY_OF_THAT_SPELL = Regex("""((?:[Uu]p to one )?(?:other )?target .+?) becomes a copy of that spell until end of turn\.""")
    // lci chunk 05
    val WOULD_DEAL_LESS_THAN_POWER = Regex("""If an? (.+?) source you control would deal an amount of (noncombat |combat )?damage less than (.+?)'s power to (an opponent), that source deals damage equal to (.+?)'s power instead\.""")
    val A_PLAYER_HAS_N_OR_FEWER_IN_HAND = Regex("""a player has (\w+) or fewer cards in hand""")
    val SOURCES_YOU_CONTROLLED_DEALT_NONCOMBAT = Regex("""(\w+) sources you controlled dealt (\d+) or more (noncombat )?damage this turn""")
    val IT_GAINS_KEYWORD = Regex("""(?:[Ii]t|[Tt]hat spell) gains ([a-z][a-z ]+)\.""")
    val IT_HAS_NO_COUNTERS = Regex("""it has no (\S+) counters on it""")
    val UNTIL_EOT_LOOK_AND_PLAY_FROM_TOP = Regex("""[Uu]ntil end of turn, you may look at the top card of your library any time,? and you may play (?:cards|lands and cast spells) from the top of your library\.""")
    val HAS_DURING_YOUR_TURN_OTHERWISE = Regex("""(.+?) has ([a-z ,]+) during your turn\. Otherwise, it has ([a-z ,]+)\.""")
    val YOU_UNTAP_DURING_YOUR_UNTAP_STEP = Regex("""you untap one or more (.+?) during your untap step""")
    val CREATE_TOKEN_BASE_PT_EQUAL_TO_TOTAL_POWER = Regex("""[Cc]reate an? ((?:white|blue|black|red|green|colorless)(?: and \w+)? [A-Z][A-Za-z ]+? creature) token with base power and toughness each equal to the total power of those creatures\.""")
    // lci chunk 08
    val YOURE_DEALT_DAMAGE = Regex("""you're dealt (combat )?damage""")
    val ATTACKING_PLAYER_GAINS_CONTROL_AND_UNTAPS = Regex("""[Tt]he attacking player gains control of (.+?) and untaps it\.""")
    // lci chunk 09: also "you put a Cave onto the battlefield this way"
    val YOU_EXILED_CARD_THIS_WAY = Regex("""you (?:exiled an? (.+?) card|put an? (.+?) onto the battlefield) this way""")
    val THE_TARGET_ENTERED_THIS_TURN = Regex("""the (.+) entered this turn""")
    // lci fixes: Unstable Glyphbridge (an untargeted choice for each player, then all the others; CR 608.2c)
    val FOR_EACH_PLAYER_CHOOSE_ONE = Regex("""[Ff]or each (player|opponent), (?:you )?choose (an?|up to one) (.+?) that player controls\.""")
    val ALL_EXCEPT_CHOSEN_THIS_WAY = Regex("""(?:Then )?([Dd]estroy|[Ee]xile) all (.+?) except (?:those|the ones|[a-z]+) chosen this way\.""")
    // lci fixes: Sandswirl Wanderglyph ("they" is the player the trigger's event names; CR 508.1c)
    val PLAYER_CANT_ATTACK_YOU_THIS_TURN = Regex("""([Tt]hey|[Tt]hat player) can't attack you( or planeswalkers you control)? this turn\.""")
    val OPPONENT_WHO_ATTACKED_YOU_CANT_CAST = Regex("""Each opponent who attacked you( or a planeswalker you control)? this turn can't cast spells\.""")
    val FOR_EACH_PLAYER_CHOOSE_UP_TO_ONE_TARGET = Regex("""[Ff]or each player, choose up to one (other )?target (.+?) that player controls\.""")
    val CHOSEN_BECOME_ARTIFACTS_LOSE_OTHER_ABILITIES = Regex("""For as long as (.+?) remains on the battlefield, the chosen permanents become ([A-Z]\w+) artifacts with "(.+)" and lose all other abilities\.""")
    val FOR_EACH_POWER_GREATER_THAN_BASE_COUNTERS = Regex("""[Ff]or each (.+?) with power greater than that creature's base power, put a number of (\S+) counters on that creature equal to the difference\.""")
    // lci chunk 09
    val WOULD_GAIN_OR_LOSE_LIFE_TWICE = Regex("""If (you|an opponent|a player) would (gain|lose) life( during your turn)?, (you|they) \2 twice that much life instead\.""")
    val IF_A_WOULD_DIE_THIS_TURN_EXILE = Regex("""If an? (.+?) would die this turn, exile it instead\.""")
    val N_MANA_OF_CHOSEN_COLOR = Regex("""(\w+) mana of the chosen color""")
    val IT_BECOMES_PT_SUBTYPE_CREATURE = Regex("""It becomes an? (\d+)/(\d+) ((?:[A-Z]\w+ )+)creature(?: with ([a-z ,]+))?\.""")
    val MAY_CAST_FROM_AMONG_EXILED_WITH_FINALITY = Regex("""You may cast (.+?) spells from among cards you own exiled with (.+?)\. If you cast a spell this way, that creature enters with a finality counter on it\.""")
    val MAY_CAST_DISCARDED_WITHOUT_PAYING = Regex("""(?:If (.+?), )?you may cast the discarded card without paying its mana cost\.""")
    val EACH_RETURNS_ALL_NOT_PUT_THERE_THIS_WAY = Regex("""Then each player returns all (.+?) cards from their graveyard that weren't put there this way to the battlefield\.""")
    val EACH_PLAYER_SACRIFICES_ALL_OTHER = Regex("""Each player sacrifices all other (.+?) they control\.""")
    val EXILE_ONE_OR_MORE_TOTAL_MANA_VALUE_X = Regex("""Exile one or more (other )?(.+?)s you control with total mana value X""")
    // lci chunk 10
    val IF_ABILITY_OF_COUNTERED_LOSES_ALL = Regex("""If an ability of (an? .+?) is countered this way, that permanent loses all abilities for as long as (.+?) remains on the battlefield\.""")
    val OPPONENTS_WHO_CONTROL = Regex("""the number of opponents who control an? (.+)""")
    val WHICHEVER_IS_GREATER = Regex("""(\w+) or (.+), whichever is greater""")
    val VALUE_IS_N_OR_GREATER = Regex("""(the number of .+) is (\w+) or greater""")
    val THAT_ARE_EITHER = Regex("""(.+?) that are (\w+) and/or (\w+)""")
    val ITS_A_PT_CREATURE_IN_ADDITION = Regex("""It's an? (\d+)/(\d+) ((?:[A-Z]\w+ )+)creature(?: with ([a-z ,]+))? in addition to its other types\.""")
    val TARGET_DEALS_POWER_TO_ANOTHER_TARGET = Regex("""[Tt]arget (.+?) deals damage equal to its power to another target (.+)\.""")
    val REVEAL_PUT_ANY_NUMBER_ONTO_BATTLEFIELD = Regex("""[Rr]eveal (?:the top (\w+) cards|(that many) cards from the top) of your library\. Put any number of (.+?) from among them onto the battlefield and the rest on the bottom of your library in a random order\.""")
    val YOU_MAY_IF_YOU_DO_OR_IF = Regex("""You may (.+?)\. If you do or if (.+?), (.+)""")
    val MAY_HAVE_BASE_POWER_BECOME_THAT_CREATURES = Regex("""[Yy]ou may have (.+?)'s base power become equal to that creature's power until end of turn\.""")
    val ATTACKS_THE_PLAYER_WITH_MOST_LIFE = Regex("""(.+) attacks the player with the most life or tied for most life""")
    val WITH_POWER_GREATER_THAN_BASE = Regex("""(.+?),? (?:each )?with power greater than its base power""")
    val EXILED_WHILE_ACTIVATING_CRAFT = Regex("""(.+) is exiled from the battlefield while you're activating a craft ability""")
    val CRAFT = Regex("""Craft with (.+?) ((?:\{[^}]+\})+)""")
    val CRAFT_EACH_OF = Regex("""(?:an? [A-Z]\w+, )+and an? [A-Z]\w+""")
    val CRAFT_COUNT = Regex("""(\w+)( or more)?(?: (.+))?""")
}

/** The lci condition phrases, tried by [Conditions.condition]. */
internal object LciPhrases {
    /** CR 700.11: "you descended this turn": a permanent card was put into your graveyard from anywhere this turn (a token isn't a card). */
    val descended: Condition get() = Condition.HappenedThisTurn(descend, atLeast = 1, measure = Measure.Objects)
    /** CR 700.11: one descend: a permanent card put into your graveyard from anywhere. */
    private val descend: EventPattern = EventPattern.ZoneChange(ObjectFilter.And(listOf(
        ObjectFilter.Or(listOf(CardType.Artifact, CardType.Battle, CardType.Creature, CardType.Enchantment, CardType.Land, CardType.Planeswalker).map { ObjectFilter.Type(it) }),
        ObjectFilter.Category(arcanum.api.ObjectCategory.Card), ObjectFilter.OwnedBy(PlayerFilter.You))), to = Zone.Graveyard)

    /** The materials of a craft ability (CR 702.167a, b): "artifact", "Cave", "two creatures", "one or more Dinosaurs", "one or more",
     * "four or more nonlands with activated abilities", "a Dinosaur, a Merfolk, a Pirate, and a Vampire" (each its own material). */
    fun craftMaterials(text: String): List<CostObjects.Choose>? {
        if (Lci.CRAFT_EACH_OF.matches(text)) return text.removePrefix("and ").split(", and ", ", ").map { one ->
            CostObjects.Choose(Filters.noun(one.removeArticle()) ?: return null, Value.Fixed(1))
        }
        val m = Lci.CRAFT_COUNT.matchEntire(text)
        val n = m?.let { Numbers.count(it.groupValues[1]) }
        if (m == null || n == null) return listOf(CostObjects.Choose(Filters.noun(text) ?: return null, Value.Fixed(1)))
        val noun = m.groupValues[3]
        val filter: ObjectFilter = if (noun.isEmpty()) ObjectFilter.Any else craftNoun(noun) ?: return null
        return listOf(if (m.groupValues[2].isEmpty()) CostObjects.Choose(filter, Value.Fixed(n)) else CostObjects.Choose(filter, null, atLeast = n))
    }

    /** A plural noun of craft materials: "Dinosaurs", "creatures", "nonlands with activated abilities" (CR 702.167b). */
    private fun craftNoun(plural: String): ObjectFilter? {
        plural.removeSuffix(" with activated abilities").takeIf { it != plural }?.let { rest ->
            return Filters.all(craftNoun(rest) ?: return null, ObjectFilter.HasAbilityKind(arcanum.api.AbilityKind.Activated))
        }
        SpmPhrases.pluralSubtype(plural)?.let { return ObjectFilter.Sub(it) }
        return Filters.noun(plural.removeSuffix("s"))
    }

    /** CR 702.167c: "the exiled cards used to craft it" (also "this creature", its name): the cards its craft ability kept. */
    fun craftedWith(phrase: String): Objects? =
        if (Lci.CRAFTED_WITH.matches(phrase)) Objects.LinkedTo(arcanum.api.Keyword.Craft.Kind) else null

    /** "the mana value of the exiled card used to craft it", "the total power of …", "the number of colors among …" (CR 702.167c). */
    fun value(text: String): Value? {
        // lci chunk 11: "the number of times you descended this turn" (each permanent card put into your graveyard, CR 700.11)
        if (text == "the number of times you descended this turn") return Value.EventsThisTurn(descend, Measure.Objects)
        // lci chunk 10: "the number of opponents who control an artifact" (CR 102.2)
        Lci.OPPONENTS_WHO_CONTROL.matchEntire(text)?.let { o ->
            val filter = Filters.noun(o.groupValues[1]) ?: return null
            return Value.CountPlayers(Players.Each(PlayerFilter.And(listOf(PlayerFilter.Opponent, PlayerFilter.Where(Condition.Exists(
                Filters.all(filter, ObjectFilter.ControlledBy(PlayerFilter.Is(Players.Tested))), atLeast = 1))))))
        }
        // lci chunk 10: "2 or the greatest power among Dinosaurs you control, whichever is greater"
        Lci.WHICHEVER_IS_GREATER.matchEntire(text)?.let { g ->
            val n = Numbers.count(g.groupValues[1]) ?: return null
            return Value.Max(listOf(Value.Fixed(n), Values.value(g.groupValues[2]) ?: return null))
        }
        val m = Lci.CRAFTED_WITH_VALUE.matchEntire(text) ?: return null
        val crafted = craftedWith(m.groupValues[2]) ?: return null
        return when (m.groupValues[1]) {
            "the mana value" -> Value.Of(arcanum.api.NumericProperty.ManaValue, crafted, arcanum.api.Combine.Single)
            "the total power" -> Value.Of(arcanum.api.NumericProperty.Power, crafted, arcanum.api.Combine.Total)
            else -> Value.CountDistinct(crafted, arcanum.api.SharedQuality.Color)
        }
    }

    /** "the player with the most life or tied for most life" (CR 702.105a). */
    private val mostLife: PlayerFilter get() = PlayerFilter.Life(Comparison.AtLeast, Value.GreatestFor(Players.Each(PlayerFilter.Any), Value.LifeOf(Players.Tested)))

    fun noun(phrase: String): ObjectFilter? {
        // lci chunk 11: "creatures you control each with power greater than its base power" (each its own, CR 208.4b)
        Lci.WITH_POWER_GREATER_THAN_BASE.matchEntire(phrase)?.let { m ->
            return Filters.all(Filters.noun(m.groupValues[1]) ?: return null, ObjectFilter.Power(Comparison.Greater,
                Value.Of(arcanum.api.NumericProperty.BasePower, Objects.Tested, arcanum.api.Combine.Single)))
        }
        // lci chunk 11: "creatures you control that are Fungi and/or Saprolings" (either subtype or both)
        Lci.THAT_ARE_EITHER.matchEntire(phrase)?.let { m ->
            val base = Filters.noun(m.groupValues[1]) ?: return null
            fun kind(plural: String) = Filters.noun(plural) ?: Filters.noun(plural.removeSuffix("s"))
            return Filters.all(base, ObjectFilter.Or(listOf(kind(m.groupValues[2]) ?: return null, kind(m.groupValues[3]) ?: return null)))
        }
        return null
    }

    /** Trigger events. */
    fun event(text: String, self: Set<String>): EventPattern? {
        // lci chunk 11: "this creature is exiled from the battlefield while you're activating a craft ability" (as a material, CR 702.167a)
        Lci.EXILED_WHILE_ACTIVATING_CRAFT.matchEntire(text)?.let { m ->
            return EventPattern.ZoneChange(Events.subjectOf(m.groupValues[1], self) ?: return null, from = Zone.Battlefield, to = Zone.Exile,
                forCost = arcanum.api.Keyword.Craft.Kind)
        }
        // lci chunk 11: "this creature attacks the player with the most life or tied for most life" (as dethrone, CR 702.105a)
        Lci.ATTACKS_THE_PLAYER_WITH_MOST_LIFE.matchEntire(text)?.let { m ->
            return EventPattern.Attack(Events.subjectOf(m.groupValues[1], self) ?: return null, target = RecipientFilter(players = mostLife))
        }
        // lci chunk 02: "you activate an ability that isn't a mana ability" (CR 605.1a, 602.2)
        if (text == "you activate an ability that isn't a mana ability") return EventPattern.Activate(ObjectFilter.Any, PlayerFilter.You)
        return null
    }

    fun condition(text: String): Condition? {
        if (text == "you descended this turn") return descended
        // lci chunk 11: "you have the most life or are tied for most life" (CR 102.2)
        if (text == "you have the most life or are tied for most life") return Condition.PlayerMatches(Players.You, mostLife)
        // lci chunk 11: "the number of other Caves you control plus the number of Cave cards in your graveyard is three or greater"
        Lci.VALUE_IS_N_OR_GREATER.matchEntire(text)?.let { m ->
            val value = Values.value(m.groupValues[1]) ?: return null
            return Condition.Compare(value, Comparison.AtLeast, Value.Fixed(Numbers.count(m.groupValues[2]) ?: return null))
        }
        // lci chunk 02: "this artifact or another artifact entered the battlefield under your control this turn": any of that kind did
        Lci.THIS_OR_ANOTHER_ENTERED.matchEntire(text)?.let { m ->
            return Condition.HappenedThisTurn(EventPattern.ZoneChange(Filters.all(Filters.noun(m.groupValues[1]) ?: return null,
                ObjectFilter.ControlledBy(PlayerFilter.You)), to = Zone.Battlefield), atLeast = 1, measure = Measure.Events)
        }
        // lci chunk 05: "a player has one or fewer cards in hand" (any one player, CR 402)
        Lci.A_PLAYER_HAS_N_OR_FEWER_IN_HAND.matchEntire(text)?.let { m ->
            return Condition.PlayerExists(PlayerFilter.Where(Condition.Compare(Value.HandSize(Players.Tested), Comparison.AtMost,
                Value.Fixed(Numbers.count(m.groupValues[1]) ?: return null))), atLeast = 1)
        }
        // lci chunk 05: "red sources you controlled dealt 4 or more noncombat damage this turn" (all of it added up, to any recipients, CR 120.1)
        Lci.SOURCES_YOU_CONTROLLED_DEALT_NONCOMBAT.matchEntire(text)?.let { m ->
            val source = Filters.all(Filters.noun(m.groupValues[1]) ?: return null, ObjectFilter.ControlledBy(PlayerFilter.You))
            return Condition.HappenedThisTurn(EventPattern.Damage(source, RecipientFilter(), combat = if (m.groupValues[3].isEmpty()) null else false),
                atLeast = m.groupValues[2].toInt(), measure = Measure.Amount)
        }
        // lci chunk 05: "it has no time counters on it" (this object)
        Lci.IT_HAS_NO_COUNTERS.matchEntire(text)?.let { m ->
            return Condition.Not(Condition.ObjectMatches(Objects.Self, ObjectFilter.HasCounter(Counters.kind(m.groupValues[1]) ?: return null, atLeast = 1)))
        }
        return null
    }
}
