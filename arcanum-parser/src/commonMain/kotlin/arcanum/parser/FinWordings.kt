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
}
