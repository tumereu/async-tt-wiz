package arcanum.parser

/** Wordings first met in Innistrad (ISD). */
internal object Isd {
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
    val IF_A_CARD_IS_DISCARDED_THIS_WAY = Regex("""If an? (.+?) card is discarded this way, (.+)\.""")
}

internal object IsdPhrases {
    /** isd chunk 02: "creatures other than Werewolves and Wolves": the kind, but none of the listed kinds. */
    fun noun(phrase: String): arcanum.api.ObjectFilter? {
        val m = Isd.OTHER_THAN.matchEntire(phrase) ?: return null
        val base = Filters.noun(m.groupValues[1]) ?: return null
        val excluded = m.groupValues[2].split(" and ", " or ", ", ").map { Filters.noun(it.trim().removePrefix("and ").removePrefix("or ")) ?: return null }
        return Filters.all(base, arcanum.api.ObjectFilter.Not(if (excluded.size == 1) excluded[0] else arcanum.api.ObjectFilter.Or(excluded)))
    }

    fun condition(text: String, self: Set<String>): arcanum.api.Condition? {
        // isd chunk 02: "this creature didn't attack this turn" (Homicidal Brute's end step; the opposite of "attacked this turn")
        Isd.DIDNT_ATTACK_THIS_TURN.matchEntire(text)?.takeIf { it.groupValues[1] in self }?.let {
            return arcanum.api.Condition.Not(arcanum.api.Condition.ObjectMatches(arcanum.api.Objects.Self,
                arcanum.api.ObjectFilter.ThisTurn(arcanum.api.EventPattern.Attack(arcanum.api.ObjectFilter.Is(arcanum.api.Objects.Self)))))
        }
        return null
    }
}
