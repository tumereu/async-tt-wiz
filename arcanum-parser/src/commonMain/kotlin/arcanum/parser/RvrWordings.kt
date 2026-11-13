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
    // rvr chunk 04: Lavinia, Azorius Renegade
    val CANT_CAST_MV_GREATER_THAN_THEIR = Regex("""(Each opponent|Each player) can't cast (.+?) spells with mana value greater than the number of (.+?) that player controls\.""")
    // rvr chunk 04: Teferi, Time Raveler
    val CAST_ONLY_AS_SORCERY = Regex("""(Each opponent|Each player|Your opponents) can cast (spells|.+? spells) only any time they could cast a sorcery\.""")
    val UNTIL_MAY_CAST_AS_THOUGH_FLASH = Regex("""(Until your next turn|Until end of turn), you may cast (.+?) as though they had flash\.""")
    // rvr chunk 04: Seal of the Guildpact
    val COSTS_LESS_FOR_EACH_CHOSEN_COLOR = Regex("""Each (?:(.+?) )?spell you cast costs (\{\d+\}) less to cast for each of the chosen colors it is\.""")
    // rvr chunk 04: Rakdos Firewheeler ("up to one target creature or planeswalker"; no "other")
    object UpToOneTargetNoun { operator fun contains(text: String) = text.startsWith("up to one target ") && !text.contains(" other ") }
    // rvr chunk 04: Compulsive Research
    val THAT_PLAYER_DISCARDS_UNLESS = Regex("""(?:Then )?that player discards (\w+) cards unless they discard an? (.+)\.""")
    // rvr chunk 04: Simic Guildmage
    val ATTACH_AURA_TO_ANOTHER_SAME_CONTROLLER = Regex("""Attach target Aura attached to an? (\w+) to another (\w+) with the same controller\.""")
    // rvr chunk 04: Plaguecrafter
    val EACH_PLAYER_WHO_CANT = Regex("""Each player who can't (.+)\.""")
    val DISCARDS_N = Regex("""discards (a|\w+) cards?""")
    // rvr chunk 04: Spark Double
    val MAY_ENTER_AS_COPY_EXCEPT = Regex("""You may have (.+?) enter as a copy of an? (.+?), except (.+)\.""")
    val ADDITIONAL_COUNTER_IF_ITS = Regex("""it enters with an additional (\S+) counter on it if it's an? (\w+)""")
    // rvr chunk 04: Niv-Mizzet Reborn
    val REVEAL_CHOOSE_EACH_COLOR_PAIR = Regex("""Reveal the top (\w+) cards of your library\. For each color pair, choose a card that's exactly those colors from among them\. Put the chosen cards into your hand and the rest on the bottom of your library in a random order\.""")
    // rvr chunk 04: Quicken
    val NEXT_SPELL_AS_THOUGH_FLASH = Regex("""The next (.+?) spell you cast this turn can be cast as though it had flash\.""")
}

/** RVR phrases read by the shared readers. */
internal object RvrPhrases {
    /** Noun phrases. */
    fun noun(phrase: String): ObjectFilter? {
        // rvr chunk 04: Azorius Guildmage, Repudiate: "activated ability", "activated or triggered ability" (abilities on the Stack, CR 113.1c;
        // mana abilities never are, CR 605.3b)
        fun ability(kind: arcanum.api.AbilityKind) = Filters.all(ObjectFilter.Category(arcanum.api.ObjectCategory.StackAbility), ObjectFilter.AbilityOf(kind = kind))
        return when (phrase.removeSuffix("ies").removeSuffix("y")) {
            "activated abilit" -> ability(arcanum.api.AbilityKind.Activated)
            "triggered abilit" -> ability(arcanum.api.AbilityKind.Triggered)
            "activated or triggered abilit" -> ObjectFilter.Or(listOf(ability(arcanum.api.AbilityKind.Activated), ability(arcanum.api.AbilityKind.Triggered)))
            else -> null
        }
    }
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
