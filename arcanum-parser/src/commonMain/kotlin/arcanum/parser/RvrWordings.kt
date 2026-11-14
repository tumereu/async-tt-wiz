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
    // rvr chunk 05: Revival // Revenge
    val DOUBLE_YOUR_LIFE = Regex("""Double your life total\.""")
    val TARGET_LOSES_HALF = Regex("""Target (opponent|player) loses half their life, rounded (up|down)\.""")
    // rvr chunk 05: Massacre Girl
    val EACH_OTHER_THAN = Regex("""(.*?\b)(each|all|Each|All) (.+?) other than (.+?) (gets?|gains?|loses?|has|have)\b(.*)""")
    // rvr chunk 05: Rakdos Guildmage
    val TOKEN_AT_NEXT_END_STEP = Regex("""(Exile|Sacrifice) (it|them|that token|those tokens) at the beginning of the next end step\.""")
    // rvr chunk 05: Ghostway
    // dmu chunk 06: Golden Argosy, "Return them to the battlefield tapped …"
    val EXILE_EACH_RETURN_AT_NEXT_END = Regex("""Exile (each|all) (.+?)\. Return (?:those cards|them) to the battlefield( tapped)? under their owners?'s? control at the beginning of the next end step\.""")
    // rvr chunk 05: Loaming Shaman
    val TARGET_PLAYER_SHUFFLES_TARGET_CARDS = Regex("""[Tt]arget player shuffles (any number of|up to (\w+)) target cards from their graveyard into their library\.""")
    // rvr chunk 05: Crackling Drake
    val NUMBER_YOU_OWN_IN_EXILE_AND_GRAVEYARD = Regex("""the total number of (.+?) cards you own in exile and in your graveyard""")
    // rvr chunk 05: Bottled Cloister
    val EXILE_ALL_CARDS_FROM_YOUR_HAND = Regex("""Exile all cards from your hand( face down)?\.""")
    val RETURN_ALL_YOU_OWN_EXILED_WITH = Regex("""Return all cards you own exiled with (.+?) to your hand\.""")
    // rvr chunk 05: Cloudstone Curio
    val MAY_RETURN_ANOTHER_SHARING_TYPE = Regex("""You may return another (.+?) that shares a (permanent|card) type with it to its owner's hand\.""")
    // rvr chunk 05: Guardian Project
    val IT_DOESNT_HAVE_SAME_NAME_AS = Regex("""it doesn't have the same name as (.+)""")
    // rvr chunk 05: Magewright's Stone
    val THAT_HAS_TAP_ABILITY = Regex("""(.+?) that ha(?:s|ve) an activated ability with \{T\} in (?:its|their) costs?""")
    // rvr chunk 05: Protean Hulk
    val SEARCH_ANY_TOTAL_MV_PUT_SHUFFLE = Regex("""[Ss]earch your library for any number of (.+?) cards with total mana value (\d+) or less, put them (onto the battlefield|into your hand), then shuffle\.""")
    // rvr chunk 05: Ilharg, the Raze-Boar
    val RETURN_PUT_TO_HAND_AT_NEXT_END_STEP = Regex("""Return (that creature|that card|it) to (your|its owner's) hand at the beginning of the next end step\.""")
    // rvr chunk 05: Tomik, Distinguished Advokist
    val CANT_BE_TARGETS_OF_OPPONENTS = Regex("""(.+?) can't be the targets of spells or abilities your opponents control\.""")
    val ON_BATTLEFIELD_OR_IN_GRAVEYARDS = Regex("""(.+?) (on the battlefield|in graveyards)""")
    val OPPONENTS_CANT_PLAY_LANDS_FROM = Regex("""(Your opponents|Each opponent|Each player) can't play (.+?) from (graveyards|exile)\.""")
    // rvr chunk 05: Mindleech Mass
    val MAY_LOOK_AT_HAND_IF_YOU_DO_CAST_FREE = Regex("""You may look at that player's hand\. If you do, you may cast a spell from among those cards without paying its mana cost\.""")
    // rvr chunk 05: Nicol Bolas, Dragon-God
    val HAS_ALL_LOYALTY_ABILITIES_OF = Regex("""(.+?) has all loyalty abilities of all (other )?(.+?)\.""")
    val EACH_OPPONENT_EXILES_FROM_HAND_OR_PERMANENT = Regex("""Each opponent exiles a card from their hand or an? (.+?) they control\.""")
    // rvr fixes: Carom
    val NEXT_DAMAGE_DEALT_TO_INSTEAD = Regex("""The next (\d+) damage that would be dealt to (target .+?) this turn is dealt to (another )?(target .+?) instead\.""")
    val EACH_OPPONENT_WHO_DOESNT_CONTROL_LOSES = Regex("""Each opponent who doesn't control an? (.+?) loses the game\.""")
}

/** RVR phrases read by the shared readers. */
internal object RvrPhrases {
    /** Noun phrases. */
    fun noun(phrase: String): ObjectFilter? {
        // rvr chunk 05: Magewright's Stone: "creature that has an activated ability with {T} in its cost" (CR 107.5)
        Rvr.THAT_HAS_TAP_ABILITY.matchEntire(phrase)?.let { m -> return Filters.all(Filters.noun(m.groupValues[1]) ?: return null, ObjectFilter.HasTapAbility) }
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
    /** Numbers. */
    fun value(text: String): arcanum.api.Value? {
        // rvr chunk 05: Crackling Drake: "the total number of instant and sorcery cards you own in exile and in your graveyard" (face-down
        // exiled cards have no types, CR 708.2a)
        Rvr.NUMBER_YOU_OWN_IN_EXILE_AND_GRAVEYARD.matchEntire(text)?.let { m ->
            val kinds = m.groupValues[1].split(" and ", " or ", ", ").map { Filters.noun(it) ?: return null }
            val kind = if (kinds.size == 1) kinds.single() else ObjectFilter.Or(kinds)
            return arcanum.api.Value.Count(Objects.Each(Filters.all(kind, ObjectFilter.Or(listOf(ObjectFilter.InZone(Zone.Exile), ObjectFilter.InZone(Zone.Graveyard))),
                ObjectFilter.OwnedBy(PlayerFilter.You))))
        }
        return null
    }
    /** Conditions. */
    fun condition(text: String, self: Set<String>): Condition? {
        // rvr chunk 05: Guardian Project: "it doesn't have the same name as another creature you control or a creature card in your graveyard"
        // ("another": other than it; a "when another creature enters" trigger makes it the creature that entered, CR 201.2, 603.4)
        Rvr.IT_DOESNT_HAVE_SAME_NAME_AS.matchEntire(text)?.let { m ->
            val among = m.groupValues[1].split(" or a ", " or an ").map { part ->
                val noun = part.removeArticle()
                if (noun.startsWith("another ")) Filters.all(ObjectFilter.Not(ObjectFilter.Is(Objects.Self)), Filters.noun(noun.removePrefix("another ")) ?: return null)
                else Filters.noun(noun) ?: return null
            }
            val filter = if (among.size == 1) among.single() else ObjectFilter.Or(among)
            return Condition.ObjectMatches(Objects.Self, ObjectFilter.Not(ObjectFilter.SameNameAs(Objects.Each(filter))))
        }
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
