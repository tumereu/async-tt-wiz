package arcanum.parser

/** Wordings first met in The Brothers' War (BRO). */
internal object Bro {
    // bro chunk 04: Mishra's Domination
    val AS_LONG_AS_IT_OTHERWISE = Regex("""As long as (.+?), it ((?:gets|has|can't) .+?)\. Otherwise, it ((?:gets|has|can't) .+)\.""")
    val YOU_CONTROL_ATTACHED = Regex("""you control (enchanted|equipped) creature""")
    // bro chunk 04: Mishra, Claimed by Gix
    val MELD_IF_ATTACKING = Regex("""If (.+?) and an? (creature|land|artifact) named (.+?) are attacking, and you both own and control them, exile them, then meld them into (.+)\.""")
    // bro chunk 04: Urza, Planeswalker
    val LOYALTY_TWICE = Regex("""You may activate the loyalty abilities of (.+?) twice each turn rather than only once\.""")
    // bro chunk 05: The Temporal Anchor
    val SCRIED_TO_BOTTOM = Regex("""(you|a player|an opponent) chooses? to put one or more cards on the bottom of (?:your|their) library while scrying""")
    val EXILE_FROM_BOTTOM = Regex("""[Ee]xile (that many|\w+) cards from the bottom of your library\.""")
}
