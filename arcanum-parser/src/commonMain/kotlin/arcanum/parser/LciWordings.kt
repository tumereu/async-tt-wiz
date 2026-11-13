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
    val CRAFT = Regex("""Craft with (.+?) ((?:\{[^}]+\})+)""")
    val CRAFT_EACH_OF = Regex("""(?:an? [A-Z]\w+, )+and an? [A-Z]\w+""")
    val CRAFT_COUNT = Regex("""(\w+)( or more)?(?: (.+))?""")
}

/** The lci condition phrases, tried by [Conditions.condition]. */
internal object LciPhrases {
    /** CR 700.11: "you descended this turn": a permanent card was put into your graveyard from anywhere this turn (a token isn't a card). */
    val descended: Condition = Condition.HappenedThisTurn(EventPattern.ZoneChange(ObjectFilter.And(listOf(
        ObjectFilter.Or(listOf(CardType.Artifact, CardType.Battle, CardType.Creature, CardType.Enchantment, CardType.Land, CardType.Planeswalker).map { ObjectFilter.Type(it) }),
        ObjectFilter.Category(arcanum.api.ObjectCategory.Card), ObjectFilter.OwnedBy(PlayerFilter.You))), to = Zone.Graveyard), atLeast = 1, measure = Measure.Objects)

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
        val m = Lci.CRAFTED_WITH_VALUE.matchEntire(text) ?: return null
        val crafted = craftedWith(m.groupValues[2]) ?: return null
        return when (m.groupValues[1]) {
            "the mana value" -> Value.Of(arcanum.api.NumericProperty.ManaValue, crafted, arcanum.api.Combine.Single)
            "the total power" -> Value.Of(arcanum.api.NumericProperty.Power, crafted, arcanum.api.Combine.Total)
            else -> Value.CountDistinct(crafted, arcanum.api.SharedQuality.Color)
        }
    }

    /** Trigger events. */
    fun event(text: String): EventPattern? {
        // lci chunk 02: "you activate an ability that isn't a mana ability" (CR 605.1a, 602.2)
        if (text == "you activate an ability that isn't a mana ability") return EventPattern.Activate(ObjectFilter.Any, PlayerFilter.You)
        return null
    }

    fun condition(text: String): Condition? {
        if (text == "you descended this turn") return descended
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
