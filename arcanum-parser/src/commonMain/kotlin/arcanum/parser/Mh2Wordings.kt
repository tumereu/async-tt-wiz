package arcanum.parser

import arcanum.api.Combine
import arcanum.api.Comparison
import arcanum.api.Condition
import arcanum.api.NumericProperty
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Players
import arcanum.api.Value
import arcanum.api.Zone

/** Regular expressions of Modern Horizons 2 (mh2) wordings read in [Sentences]. */
internal object Mh2 {
    // mh2 chunk 01
    val REMOVE_X_COUNTERS_FROM_SELF = Regex("""Remove X (\S+) counters from (.+)""")
    val THEN_ATTACH_SELF_TO_IT = Regex("""(.+), then attach (.+?) to it\.""")
    val IF_ITS_A_THEN = Regex("""If it's an? (.+?), (it .+)""")
    val YOU_GAIN_HEXPROOF_UNTIL_YOUR_NEXT_TURN = Regex("""You gain hexproof until your next turn\.""")
    val PUT_TARGET_CARD_FROM_OPPONENTS_GRAVEYARD_ON_BOTTOM = Regex("""[Pp]ut target card from an opponent's graveyard on the bottom of their library\.""")
    // mh2 chunk 02
    val REVEAL_SELF_FROM_HAND = Regex("""Reveal (.+) from your hand""")
    val UNTAP_ALL_THEN_PHASE_OUT_COUNTER_EACH = Regex("""Untap all (\w+), then those \1 phase out until (.+?) leaves the battlefield\. Put an? (\S+) counter on (.+?) for each (.+?) that phased out this way\.""")
    val EXILE_ALL_CARDS_OF_TWO_COLORS_FROM_ALL_GRAVEYARDS = Regex("""[Ee]xile all cards that are (\w+) or (\w+) from all graveyards\.""")
    val TWO_COLORS_WOULD_BE_PUT_INTO_GRAVEYARD_EXILE = Regex("""If an? (\w+) or (\w+) permanent, spell, or card not on the battlefield would be put into a graveyard, exile it instead\.""")
    val AS_ENTERS_CHOOSE_CARD_TYPE = Regex("""As (.+) enters, choose a card type\.""")
    val YOU_AND_HAVE_PROTECTION_FROM_CHOSEN_CARD_TYPE = Regex("""You and (.+) have protection from the chosen card type\.""")
    val IS_ON_BATTLEFIELD_OR_IN_YOUR_GRAVEYARD = Regex("""(.+) is on the battlefield or in your graveyard""")
    val EXILE_TARGET_FROM_YOUR_GRAVEYARD = Regex("""Exile target (.+? card) from your graveyard\.""")
    val EXILED_CARDS_NUMBER = Regex("""the exiled card's (power|toughness)""")
    val PUT_THAT_MANY_COUNTERS_ON = Regex("""([Yy]ou may )?[Pp]ut that many (\S+) counters on (.+)\.""")
}

/** Values, conditions and nouns of Modern Horizons 2 (mh2) wordings. */
internal object Mh2Phrases {
    fun condition(text: String, self: Set<String>): Condition? {
        fun isSelf(name: String) = name in self || name.replaceFirstChar { it.lowercase() } in self
        // mh2 chunk 01: "you have more life than an opponent": some opponent has less life than you (CR 102.3)
        if (text == "you have more life than an opponent")
            return Condition.PlayerExists(PlayerFilter.And(listOf(PlayerFilter.Opponent, PlayerFilter.Life(Comparison.Less, Value.LifeOf(Players.You)))), atLeast = 1)
        // mh2 chunk 02: "Skyblade's Boon is on the battlefield or in your graveyard" (the zones it works in, CR 113.6b)
        Mh2.IS_ON_BATTLEFIELD_OR_IN_YOUR_GRAVEYARD.matchEntire(text)?.takeIf { isSelf(it.groupValues[1]) }?.let {
            return Condition.ObjectMatches(Objects.Self, ObjectFilter.Or(listOf(ObjectFilter.InZone(Zone.Battlefield), ObjectFilter.InZone(Zone.Graveyard))))
        }
        return null
    }

    /** [exiled]: what "the exiled card" means on this card, when an ability links to it (CR 607.2a). */
    fun value(@Suppress("UNUSED_PARAMETER") text: String, @Suppress("UNUSED_PARAMETER") exiled: Objects?): Value? = null

    fun noun(@Suppress("UNUSED_PARAMETER") phrase: String): ObjectFilter? = null
}
