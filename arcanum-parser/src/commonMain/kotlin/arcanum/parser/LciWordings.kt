package arcanum.parser

import arcanum.api.CardType
import arcanum.api.Condition
import arcanum.api.EventPattern
import arcanum.api.Measure
import arcanum.api.ObjectFilter
import arcanum.api.PlayerFilter
import arcanum.api.Zone

/** Regular expressions of The Lost Caverns of Ixalan (lci) wordings read in [Sentences]. */
internal object Lci {
    // lci chunk 01: "Enchanted artifact is a Golem creature with base power and toughness 5/4 in addition to its other types." (CR 613.1d, 613.4b)
    val ATTACHED_IS_CREATURE_BASE_PT = Regex("""(?:Enchanted|Equipped) (?:artifact|land|permanent|creature) is an? ((?:[A-Z]\w+ )+)creature with base power and toughness (\d+)/(\d+) in addition to its other types\.""")
    // lci chunk 01: "If the discovered card's mana value is less than 10, create a number of tapped Treasure tokens equal to the difference." (CR 701.57c)
    val IF_DISCOVERED_LESS_CREATE_DIFFERENCE = Regex("""If the discovered card's mana value is less than (\d+), create a number of (tapped )?(.+? tokens?) equal to the difference\.""")
    // lci chunk 01: "remove those counters and transform it" after "If there are three or more bore counters on it"
    val REMOVE_THOSE_COUNTERS_AND = Regex("""If there are (\w+) or more (\S+) counters on it, remove those counters and (.+)\.""")
    // lci chunk 01: "Whenever you cast a permanent spell using mana produced by Tecutlan" (CR 106.3)
    val YOU_CAST_USING_MANA_FROM = Regex("""you cast an? (.+?) using mana produced by (.+)""")
}

/** The lci condition phrases, tried by [Conditions.condition]. */
internal object LciPhrases {
    /** CR 700.11: "you descended this turn": a permanent card was put into your graveyard from anywhere this turn (a token isn't a card). */
    val descended: Condition = Condition.HappenedThisTurn(EventPattern.ZoneChange(ObjectFilter.And(listOf(
        ObjectFilter.Or(listOf(CardType.Artifact, CardType.Battle, CardType.Creature, CardType.Enchantment, CardType.Land, CardType.Planeswalker).map { ObjectFilter.Type(it) }),
        ObjectFilter.Category(arcanum.api.ObjectCategory.Card), ObjectFilter.OwnedBy(PlayerFilter.You))), to = Zone.Graveyard), atLeast = 1, measure = Measure.Objects)

    fun condition(text: String): Condition? {
        if (text == "you descended this turn") return descended
        return null
    }
}
