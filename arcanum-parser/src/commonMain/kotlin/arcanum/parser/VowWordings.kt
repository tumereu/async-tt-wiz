package arcanum.parser

/** Regular expressions of Innistrad: Crimson Vow (vow) wordings read in [Sentences]. */
internal object Vow {
    // vow chunk 01: "Then if that creature has toughness 6 or greater, …" (the creature named before, as the ability resolves)
    val IT_HAS_STAT = Regex("""(?:that creature|it) has (power|toughness) (\d+) or (greater|less)""")
    // vow chunk 01: "When you play a card exiled with this creature" (a trigger)
    val YOU_PLAY_A_CARD_EXILED_WITH = Regex("""you play a card exiled with (.+)""")
    // vow chunk 01: Hauken's Insight
    val ONCE_PLAY_FROM_AMONG_EXILED_WITH_FREE = Regex("""Once during each of your turns, you may play a land or cast a spell from among the cards exiled with (.+?) without paying its mana cost\.""")
}
