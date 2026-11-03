package arcanum.parser

import arcanum.api.Combine
import arcanum.api.Condition
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.Value

/** fin: nouns, values and conditions of Final Fantasy wordings, tried first by [Filters], [Values] and [Conditions]. */
internal object FinPhrases {
    // fin chunk 03: "creature that isn't a Kraken, Leviathan, Merfolk, Octopus, or Serpent"
    private val THAT_ISNT = Regex("""(\w+) that isn't an? (.+)""")
    // fin chunk 03: "the total mana value of other permanents you control"
    private val TOTAL_MANA_VALUE_OF = Regex("""the total mana value of (.+)""")

    // fin chunk 05
    private val MANA_SPENT = Regex("""(?:at least (\w+)|(\w+) or more) mana was spent to cast (?:it|that spell)""")
    private val IT_HAS_COUNTERS = Regex("""it has (\w+) or more (\S+) counters on it""")
    private val PLAYER_DEALT_COMBAT_DAMAGE = Regex("""a player was dealt (\d+) or more combat damage this turn""")
    private val DIED_UNDER_CONTROL = Regex("""an? (\w+) died under (an opponent's|your) control this turn""")
    private val CARDS_ARE_IN_GRAVEYARD = Regex("""(\w+) or more cards are in your graveyard""")
    fun noun(phrase: String): ObjectFilter? {
        THAT_ISNT.matchEntire(phrase)?.let { m ->
            val kind = Filters.noun(m.groupValues[1]) ?: return null
            val not = Filters.noun(m.groupValues[2]) ?: return null
            return Filters.all(kind, ObjectFilter.Not(not))
        }
        // fin-noun-end
        return null
    }

    fun value(text: String): Value? {
        TOTAL_MANA_VALUE_OF.matchEntire(text)?.let { m ->
            return Value.Of(NumericProperty.ManaValue, Objects.Each(Filters.noun(m.groupValues[1]) ?: return null), Combine.Total)
        }
        // fin-value-end
        return null
    }

    fun condition(text: String, self: Set<String>): Condition? {
        // fin chunk 05: "at least four mana was spent to cast it", "four or more mana was spent to cast that spell" (the spell cast, CR 601.2h)
        MANA_SPENT.matchEntire(text)?.let { m ->
            val n = Numbers.count(m.groupValues[1] + m.groupValues[2]) ?: return null
            return Condition.Compare(Value.ManaSpent(of = Objects.EventObject(arcanum.api.EventRole.Object)), arcanum.api.Comparison.AtLeast, Value.Fixed(n))
        }
        // fin chunk 05: "it has three or more +1/+1 counters on it" (this object)
        IT_HAS_COUNTERS.matchEntire(text)?.let { m ->
            val n = Numbers.count(m.groupValues[1]) ?: return null
            return Condition.ObjectMatches(Objects.Self, ObjectFilter.HasCounter(Counters.kind(m.groupValues[2]) ?: return null, atLeast = n))
        }
        // fin chunk 05: "a player was dealt 6 or more combat damage this turn" (one player's damage added up)
        PLAYER_DEALT_COMBAT_DAMAGE.matchEntire(text)?.let { m ->
            val damage = arcanum.api.EventPattern.Damage(ObjectFilter.Any, arcanum.api.RecipientFilter(players = arcanum.api.PlayerFilter.Is(arcanum.api.Players.Tested)),
                combat = true)
            return Condition.PlayerExists(arcanum.api.PlayerFilter.Where(Condition.HappenedThisTurn(damage, atLeast = m.groupValues[1].toInt(),
                measure = arcanum.api.Measure.Amount)), atLeast = 1)
        }
        // fin chunk 05: "your life total is less than or equal to half your starting life total" (CR 103.4)
        if (text == "your life total is less than or equal to half your starting life total")
            return Condition.Compare(Value.LifeOf(arcanum.api.Players.You), arcanum.api.Comparison.AtMost,
                Value.Half(Value.StartingLife(arcanum.api.Players.You), roundUp = false))
        // fin chunk 05: "a creature died under an opponent's control this turn"
        DIED_UNDER_CONTROL.matchEntire(text)?.let { m ->
            val noun = Filters.noun(m.groupValues[1]) ?: return null
            val whose = if (m.groupValues[2] == "an opponent's") arcanum.api.PlayerFilter.Opponent else arcanum.api.PlayerFilter.You
            return Condition.HappenedThisTurn(arcanum.api.EventPattern.ZoneChange(Filters.all(noun, ObjectFilter.ControlledBy(whose)), from = arcanum.api.Zone.Battlefield,
                to = arcanum.api.Zone.Graveyard), atLeast = 1, measure = arcanum.api.Measure.Events)
        }
        // fin chunk 05: "eight or more cards are in your graveyard"
        CARDS_ARE_IN_GRAVEYARD.matchEntire(text)?.let { m ->
            val n = Numbers.count(m.groupValues[1]) ?: return null
            return Condition.Exists(Filters.all(ObjectFilter.InZone(arcanum.api.Zone.Graveyard), ObjectFilter.OwnedBy(arcanum.api.PlayerFilter.You)), atLeast = n)
        }
        // fin-condition-end
        return null
    }
}

/** fin: regular expressions of Final Fantasy wordings (the set's parser tests, sets/fin/FinChunk*Test.kt). */
internal object Fin {
    // fin chunk 02: a tiered mode "Cure — {0} — [effect]" (CR 702.183a); "3/2." completing a paragraph
    val TIERED_MODE = Regex("""([^—]+) — ((?:\{[^}]+\})+) — (.+)""")
    val PT_MODE = Regex("""(\d+/\d+)\.""")
    // fin chunk 01: "Equipped creature gets +1/+1, has "…," and is a Cleric in addition to its other types."
    val ATTACHED_CLAUSES = Regex("""(Equipped creature|Enchanted creature) ((?:gets|has|is) .+?)\.?""")
    val HAS_QUOTE = Regex("""has "(.+)"""")
    val IS_A_IN_ADDITION = Regex("""is an? ([A-Z][a-z]+(?: [A-Z][a-z]+)*) in addition to its other types""")
    val CLAUSE_SEPARATOR = Regex(""",? and (?=(?:is|has|gets) )|, (?:and )?""")
    // fin chunk 01: "Whenever you cast a noncreature spell and whenever you draw your third card each turn, put …"
    val WHENEVER_AND_WHENEVER = Regex("""Whenever (.+?) and whenever (.+?), (.+)""")
    val IF_THAT_CARD_ENTERS_TAPPED_ATTACKING = Regex("""If that card is an? (.+?), it enters tapped and attacking\.""")
    // fin chunk 02
    val TARGETS_OWNER_SHUFFLES = Regex("""Target (.+?)'s owner shuffles it into their library\.""")
    val REMOVE_COUNTER_FROM_TARGET = Regex("""Remove (a|an|one|two|three) (\S+) counters? from target (.+)\.""")
    val TRIPLE_TARGET_PT = Regex("""Triple target (.+?)'s power and toughness until end of turn\.""")
    val UP_TO_FROM_SINGLE_GRAVEYARD = Regex("""(Exile) up to (\w+) target (.+?) from a single graveyard\.""")
    val GAINS_QUOTE_AND_BASE_PT = Regex("""Until end of turn, target (.+?) gains "(.+?)" and has base power and toughness (\d+)/(\d+)\.""")
    val COUNTER_ON_ONE_OF_THEM = Regex("""Put an? (\S+) counter on one of them\.""")
    // fin chunk 03
    val COPY_IT_TIMES = Regex("""Copy it (twice|three times)\.""")
    val SACRIFICES_AND_LOSES = Regex("""(Each opponent|Each player|Target player|Target opponent) sacrifices (.+?) and loses (\w+) life\.""")
    val SELF_GAINS_QUOTE = Regex("""(.+?) gains "(.+)"\.?""")
    val UNTIL_EOT_GAINS_AND_GETS = Regex("""Until end of turn, (.+?) gains (.+?) and gets (.+)\.""")
    val UNTIL_EOT_WHENEVER = Regex("""Until end of turn, whenever (.+)""")
    val COUNTER_ON_EACH_OF_THEM = Regex("""Put an? (\S+) counter on each of them\.""")
    val WHEN_YOU_NEXT_CAST = Regex("""When you next cast an? (.+?) this turn, (.+)""")
    val THAT_CREATURE_ENTERS_WITH_ADDITIONAL = Regex("""That creature enters with an additional (\S+) counter on it(?: for each (.+?))?\.""")
    val DEALS_DAMAGE_EQUAL_TOTAL = Regex("""(.+?) deals damage equal to (the total mana value of .+?) to (each opponent|any target|target .+)\.""")
    // fin chunk 04
    val TAP_ALL = Regex("""Tap all (.+)\.""")
    val EXILE_SELF_THEN_RETURN = Regex("""[Ee]xile (.+?), then (?:return it to|put it onto) the battlefield( transformed)?( under its owner's control)?(?: with an? (\S+) counter on it)?\.""")
    val RETURN_SELF_TRANSFORMED = Regex("""Return (this card) from your graveyard to the battlefield transformed\.""")
    val RETURN_ANY_NUMBER_TOTAL = Regex("""Return any number of target (.+?) with total mana value (\d+) or less from your graveyard to the battlefield\.""")
    val PUT_UP_TO_ONE_MILLED = Regex("""Put up to one (.+?) milled this way into your hand\.""")
    val DURING_YOUR_TURN_SELF_AND_OTHERS_HAVE = Regex("""During your turn, (.+?) and other (.+?) have (.+)\.""")
    val PUT_IT_ON_LIBRARY = Regex("""Put it on the (top|bottom) of its owner's library\.""")
    val UNTIL_NEXT_TURN_IT_GAINS = Regex("""Until your next turn, it gains (.+)\.""")
    val IF_THAT_CREATURE_IS = Regex("""If that creature is (\w+), (.+)\.""")
    val ADD_MANA_LIST = Regex("""Add ((?:\{[WUBRGC]\})+(?:, (?:and )?(?:\{[WUBRGC]\})+)+)\.""")
    val THOSE_CREATURES_GAIN = Regex("""Those creatures gain (.+) until end of turn\.""")
    val COPY_ENCHANTMENT_HASTE_LORE = Regex("""Create a token that's a copy of target (.+?)\. It gains haste\. If it's a Saga, put up to three lore counters on it\. Sacrifice it at the beginning of your next end step\.""")
    // fin chunk 05
    val SELF_DEALS_DAMAGE = Regex("""(.+?) deals damage""")
    val COUNTERS_EQUAL_TO_THAT_CREATURES_POWER = Regex("""Put a number of (\S+) counters on (.+?) equal to that creature's power\.""")
    val MAY_SACRIFICE_ANY_NUMBER_DRAW = Regex("""You may sacrifice any number of (.+?)\. If you do, draw that many cards\.""")
    val IF_YOU_PUT_A_CARD_INTO_HAND = Regex("""If you put a card into your hand this way, (.+)\.""")
    val IF_IT_HAS_COUNTERS_TRANSFORM_IT = Regex("""If it has (\w+) or more (\S+) counters on it, transform it\.""")
    val RETURN_IT_TO_BATTLEFIELD = Regex("""Return it to the battlefield( tapped)?\.""")
    val TRANSFORM_THEN_ATTACH = Regex("""Transform (.+?), then attach it to an? (.+)\.""")
    val CREATURES_OTHER_THAN_SELF_AND_CHOSEN_GET = Regex("""Until end of turn, creatures other than (.+?) and the chosen creature get ([+-]\d+)/([+-]\d+)\.""")
    val WOULD_DEAL_DAMAGE_DOUBLE = Regex("""If an? (.+?) would deal damage to a permanent or player, it deals double that damage instead\.""")
    val AS_TRANSFORMS_CHOOSE_OPPONENT = Regex("""As (.+?) transforms into (.+), choose an opponent\.""")
    val AS_TRANSFORMS_INTO = Regex("""As (.+?) transforms into (.+)""")
    val WHEN_CHOSEN_LEAVES = Regex("""When the chosen creature leaves the battlefield, (.+)""")
}
