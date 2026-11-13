package arcanum.parser

import arcanum.api.Condition
import arcanum.api.EventPattern
import arcanum.api.EventRole
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.PlayerFilter
import arcanum.api.Zone

/** Wordings first met in Ravnica Remastered (RVR). */
internal object Rvr {
    // rvr chunk 01: Unbreakable Formation (addendum)
    // rvr chunk 02: Glowspore Shaman
    val MAY_PUT_FROM_GRAVEYARD_ON_TOP = Regex("""You may put an? (.+?) from your graveyard on top of your library\.""")
    // rvr chunk 02: Aurelia, Exemplar of Justice
    val UNTIL_EOT_THAT_CREATURE = Regex("""Until end of turn, that creature (.+)\.""")
    val CLAUSE_LIST = Regex(""", and |, """)
    val GETS_PT = Regex("""gets ([+-]\d+)/([+-]\d+)""")
    val GAINS_IF_ITS = Regex("""gains ([a-z ]+?)(?: if it's (\w+))?""")
    // rvr chunk 03: Fblthp, the Lost
    val ENTERED_OR_CAST_FROM = Regex("""(.+?) entered from your (library|graveyard)(?: or was cast from your (library|graveyard))?""")
    fun zone(text: String): Zone? = when (text) { "library" -> Zone.Library; "graveyard" -> Zone.Graveyard; else -> null }
    // rvr chunk 03: Gideon Blackblade
    val PREVENT_DURING_YOUR_TURN = Regex("""(Prevent all damage that would be dealt to .+) during your turn\.""")
    val UP_TO_ONE_TARGET = Regex("""[Uu]p to one (other )?target (.+)""")
    // rvr chunk 03: Karn, the Great Creator
    val REVEAL_FROM_OUTSIDE_OR_CHOOSE_IN_EXILE = Regex("""You may reveal an? (.+?) you own from outside the game or choose a face-up (.+?) you own in exile\.""")
    // rvr chunk 03: Kaya, Orzhov Usurper
    val DEALS_AND_GAIN_THAT_MUCH = Regex("""(.+? deals damage to .+) and you gain that much life\.""")
    // rvr chunk 03: Woebringer Demon
    val IF_CANT = Regex("""If (?:the|that) player can't, (.+)\.""")
    val IF_COUNTER_ON_EACH_OF_THOSE_AND_THEY_GAIN = Regex("""If (.+?), put an? (\S+) counter on each of those creatures and they gain ([a-z ,]+) until end of turn\.""")
}

/** RVR phrases read by the shared readers. */
internal object RvrPhrases {
    /** Conditions. */
    fun condition(text: String, self: Set<String>): Condition? {
        // rvr chunk 01: addendum, "If you cast this spell during your main phase" (CR 207.2c, 505.1)
        if (text == "you cast this spell during your main phase") return Condition.CastDuringMainPhase(Objects.Self)
        // rvr chunk 03: Fblthp, the Lost: "If it entered from your library or was cast from your library" (from its owner's library: yours,
        // CR 108.3; entering from the Stack after a cast from the library counts as cast from it, CR 601.2a)
        Rvr.ENTERED_OR_CAST_FROM.matchEntire(text)?.takeIf { it.groupValues[1] == "it" || it.groupValues[1] in self }?.let { m ->
            val zone = Rvr.zone(m.groupValues[2]) ?: return null
            if (m.groupValues[3].isNotEmpty() && Rvr.zone(m.groupValues[3]) != zone) return null
            val entered = ObjectFilter.ThisTurn(EventPattern.ZoneChange(ObjectFilter.Any, from = zone, to = Zone.Battlefield), role = EventRole.After)
            val from = if (m.groupValues[3].isEmpty()) entered else ObjectFilter.Or(listOf(entered, ObjectFilter.WasCast(from = zone)))
            return Condition.ObjectMatches(Objects.Self, ObjectFilter.And(listOf(ObjectFilter.OwnedBy(PlayerFilter.You), from)))
        }
        return null
    }
}
