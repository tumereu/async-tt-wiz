package arcanum.parser

/** Wordings first met in Innistrad (ISD). */
internal object Isd {
    // isd chunk 06: Manor Gargoyle
    val LOSES_KEYWORDS = Regex("""loses? ([a-z][a-z ,]*)""")
    // isd chunk 06: Mirror-Mad Phantasm
    val OWNER_SHUFFLES_SELF_REVEALS_UNTIL_NAMED = Regex("""(.+?)'s owner shuffles it into their library\. If that player does, they reveal cards from the top of that library until a card named (.+?) is revealed\. The player puts that card onto the battlefield and all other cards revealed this way into their graveyard\.""")
    // isd chunk 06: Evil Twin
    val WITH_SAME_NAME_AS_SELF = Regex("""(.+) with the same name as this (?:creature|permanent)""")
    // isd chunk 06: Grimoire of the Dead
    val COST_AND_SACRIFICE_IT = Regex("""(.+) and sacrifice it""")
    // isd chunk 06: Essence of the Wild
    val ENTER_AS_COPY_OF_SELF = Regex("""([A-Z][^.]+? you control) enter as a copy of (.+)\.""")
    // isd chunk 06: Unbreathing Horde
    val WOULD_BE_DEALT_DAMAGE_PREVENT_AND = Regex("""If (.+?) would be dealt damage, prevent that damage and (.+)\.""")
    // isd chunk 06: Kessig Cagebreakers
    val CREATE_TAPPED_AND_ATTACKING_FOR_EACH = Regex("""[Cc]reate (an? .+? token) that's tapped and attacking for each (.+)\.""")
    val HAS_KEYWORD = Regex("""(it|this \w+) has ([a-z][a-z ]*)""")
    // isd chunk 01: Memory's Journey
    val TARGET_PLAYER_SHUFFLES_UP_TO_FROM_GRAVEYARD = Regex("""Target player shuffles up to (\w+) target cards from their graveyard into their library\.""")
    // isd chunk 01: Divine Reckoning: "Each player chooses a creature they control. Destroy the rest." (the rest of that kind of permanent)
    val EACH_CHOOSES_ONE_THEY_CONTROL_REST = Regex("""Each (player|opponent) chooses an? ([a-z]+) they control\. (Destroy|Exile) the rest\.""")
    // isd chunk 02: Ludevic's Test Subject
    val THEN_IF_N_COUNTERS_REMOVE_ALL_AND = Regex("""(?:Then )?[Ii]f there are (\w+) or more (\S+) counters on (.+?), remove all of them and (.+)\.""")
    // isd chunk 02: Moonmist
    val OTHER_THAN = Regex("""(creatures?|permanents?) other than ([A-Z][\w-]+(?:(?:,| and| or) (?:and |or )?[A-Z][\w-]+)*)""")
    // isd chunk 02: Civilized Scholar
    val DIDNT_ATTACK_THIS_TURN = Regex("""(.+?) didn't attack this turn""")
    // isd chunk 03: Bonds of Faith
    val AS_LONG_AS_ITS_A_OTHERWISE = Regex("""(.+?) ((?:gets|has|can't) .+?) as long as it's an? ([A-Z]\w+)\. Otherwise, it (.+)\.""")
    // isd chunk 03: Curse of the Pierced Heart
    val DEALS_TO_THAT_PLAYER_OR_THEIR_PLANESWALKER = Regex("""(.+?) deals (\d+) damage to that player or a planeswalker that player controls\.""")
    val IF_A_CARD_IS_DISCARDED_THIS_WAY = Regex("""If an? (.+?) card is discarded this way, (.+)\.""")
    // isd chunk 04: Inquisitor's Flail
    val ATTACHED_WOULD_DEAL_DOUBLE = Regex("""If (equipped creature|enchanted creature|another creature) would deal (combat |noncombat |)damage(?: to (equipped creature|enchanted creature))?, it deals double that damage(?: to (equipped creature|enchanted creature))? instead\.""")
    // isd chunk 04: Runechanter's Pike
    val HAS_KEYWORDS_AND_GETS_X_WHERE = Regex("""(.+?) has ([a-z ,]+?) and (gets [+-]X/[+-](?:X|0), where X is .+\.)""")
    // isd chunk 04: Trepanation Blade
    val REVEAL_UNTIL_GETS_FOR_EACH_REVEALED = Regex("""(.+?) reveals cards from the top of their library until they reveal an? (.+?) card\. (The creature|That creature|It) gets \+(\d+)/\+(\d+) until end of turn for each card revealed this way\. That player puts the revealed cards into their graveyard\.""")
    // isd chunk 04: Wooden Stake
    val BLOCKS_OR_BECOMES_BLOCKED_BY_A = Regex("""(.+?) blocks or becomes blocked by an? (.+)""")
    val BECOMES_BLOCKED_BY_A = Regex("""(.+?) becomes blocked by an? (.+)""")
    // isd chunk 04: Sharpened Pitchfork: "equipped creature is a Human"
    val ATTACHED_IS = Regex("""(equipped|enchanted) creature is (legendary|an? .+)""")
    // isd chunk 05: Undead Alchemist
    val WOULD_DEAL_DAMAGE_TO_PLAYER_INSTEAD = Regex("""If (.+?) would deal (combat |noncombat |)damage to (a player|an opponent), instead (.+)\.""")
    // isd chunk 05: Caravan Vigil
    val MAY_PUT_ONTO_BATTLEFIELD_INSTEAD_OF_HAND_IF = Regex("""(?:([A-Z][A-Za-z ]+) — )?You may put (?:that card|it) onto the battlefield instead of putting it into your hand if (.+)\.""")
    // isd chunk 05: Heretic's Punishment, Mindshrieker: "the milled card's mana value", "the greatest mana value among the milled cards"
    val AN_X = Regex("""\bX\b""")
    val MILLED_CARDS_VALUE = Regex("""the milled card's (mana value|power|toughness)|the greatest (mana value|power|toughness) among the milled cards""")
}

internal object IsdPhrases {
    /** isd chunk 05: what a mill just before put into the graveyard, bound as "milled" by the sentence that reads it (CR 701.17c). "The
     * milled card" of a one-card mill that milled several through replacement effects is their total (CR 701.17d). */
    fun value(text: String): arcanum.api.Value? {
        val m = Isd.MILLED_CARDS_VALUE.matchEntire(text) ?: return null
        val word = m.groupValues[1].ifEmpty { m.groupValues[2] }
        val property = when (word) { "power" -> arcanum.api.NumericProperty.Power; "toughness" -> arcanum.api.NumericProperty.Toughness
            else -> arcanum.api.NumericProperty.ManaValue }
        val combine = if (m.groupValues[1].isNotEmpty()) arcanum.api.Combine.Total else arcanum.api.Combine.Greatest
        return arcanum.api.Value.Of(property, arcanum.api.Objects.Bound("milled"), combine)
    }

    /** isd chunk 02: "creatures other than Werewolves and Wolves": the kind, but none of the listed kinds. */
    fun noun(phrase: String): arcanum.api.ObjectFilter? {
        // isd chunk 06: "creature with the same name as this creature" (Evil Twin's granted ability; the name it has now, CR 201.2)
        Isd.WITH_SAME_NAME_AS_SELF.matchEntire(phrase)?.let { m ->
            return Filters.all(Filters.noun(m.groupValues[1]) ?: return null, arcanum.api.ObjectFilter.SameNameAs(arcanum.api.Objects.Self))
        }
        val m = Isd.OTHER_THAN.matchEntire(phrase) ?: return null
        val base = Filters.noun(m.groupValues[1]) ?: return null
        val excluded = m.groupValues[2].split(" and ", " or ", ", ").map { Filters.noun(it.trim().removePrefix("and ").removePrefix("or ")) ?: return null }
        return Filters.all(base, arcanum.api.ObjectFilter.Not(if (excluded.size == 1) excluded[0] else arcanum.api.ObjectFilter.Or(excluded)))
    }

    fun condition(text: String, self: Set<String>): arcanum.api.Condition? {
        // isd chunk 04: "equipped creature is a Human", "enchanted creature is legendary" (the object this permanent is attached to)
        Isd.ATTACHED_IS.matchEntire(text)?.let { m ->
            val filter = if (m.groupValues[2] == "legendary") arcanum.api.ObjectFilter.Super(arcanum.api.Supertype.Legendary)
                else Filters.noun(m.groupValues[2].substringAfter(' ')) ?: return null
            return arcanum.api.Condition.ObjectMatches(arcanum.api.Objects.AttachedTo(arcanum.api.Objects.Self), filter)
        }
        // isd chunk 02: "this creature didn't attack this turn" (Homicidal Brute's end step; the opposite of "attacked this turn")
        Isd.DIDNT_ATTACK_THIS_TURN.matchEntire(text)?.takeIf { it.groupValues[1] in self }?.let {
            return arcanum.api.Condition.Not(arcanum.api.Condition.ObjectMatches(arcanum.api.Objects.Self,
                arcanum.api.ObjectFilter.ThisTurn(arcanum.api.EventPattern.Attack(arcanum.api.ObjectFilter.Is(arcanum.api.Objects.Self)))))
        }
        // isd chunk 06: "it has defender", "this creature has flying" (CR 702.3)
        Isd.HAS_KEYWORD.matchEntire(text)?.takeIf { it.groupValues[1] == "it" || it.groupValues[1] in self }?.let { m ->
            val keyword = OracleParser.keyword(m.groupValues[2]) ?: return null
            return arcanum.api.Condition.ObjectMatches(arcanum.api.Objects.Self, arcanum.api.ObjectFilter.HasKeyword(keyword.kind))
        }
        return null
    }
}
