package arcanum.parser

/** Wordings first met in Innistrad (ISD). */
internal object Isd {
    // isd chunk 01: Memory's Journey
    val TARGET_PLAYER_SHUFFLES_UP_TO_FROM_GRAVEYARD = Regex("""Target player shuffles up to (\w+) target cards from their graveyard into their library\.""")
    // isd chunk 01: Divine Reckoning: "Each player chooses a creature they control. Destroy the rest." (the rest of that kind of permanent)
    val EACH_CHOOSES_ONE_THEY_CONTROL_REST = Regex("""Each (player|opponent) chooses an? ([a-z]+) they control\. (Destroy|Exile) the rest\.""")
}
