package arcanum.parser

/** Regular expressions of Innistrad: Crimson Vow (vow) wordings read in [Sentences]. */
internal object Vow {
    // vow chunk 01: "Then if that creature has toughness 6 or greater, …" (the creature named before, as the ability resolves)
    val IT_HAS_STAT = Regex("""(?:that creature|it) has (power|toughness) (\d+) or (greater|less)""")
    // vow chunk 01: "When you play a card exiled with this creature" (a trigger)
    val YOU_PLAY_A_CARD_EXILED_WITH = Regex("""you play a card exiled with (.+)""")
    // vow chunk 01: Hauken's Insight
    val ONCE_PLAY_FROM_AMONG_EXILED_WITH_FREE = Regex("""Once during each of your turns, you may play a land or cast a spell from among the cards exiled with (.+?) without paying its mana cost\.""")
    // vow chunk 02: Volatile Arsonist
    val DEALS_TO_EACH_OF_UP_TO_ONE_CREATURE_PLAYER_PLANESWALKER = Regex("""(.+?) deals (\d+) damage to each of up to one target creature, up to one target player, and/or up to one target planeswalker\.""")
    // vow chunk 03: "this creature enters or becomes the target of an Aura spell" (a trigger of two events)
    val ENTERS_OR = Regex("""(.+?) enters or (.+)""")
    // vow chunk 03: "as long as it's attacking alone" after "This creature …" or "Enchanted creature …" (the subject)
    val ITS_AT_START = Regex("""it(?:'s| is) (.+)""")
    val SUBJECT_AT_START = Regex("""^(This creature|Enchanted creature|Equipped creature) """)
    // vow chunk 04: cleave (CR 702.148a): words in square brackets, with the space before them
    val CLEAVE_BRACKET = Regex("""( ?)\[([^\]]+)\]""")
    // vow chunk 04: "During that turn, damage can't be prevented." after taking an extra turn
    val DURING_THAT_TURN = Regex("""During that turn, (.+)\.""")
    // vow chunk 04: "At the beginning of that turn's end step, you lose the game." after taking an extra turn
    val AT_THAT_TURNS_STEP = Regex("""At the beginning of that turn's (upkeep|draw step|end step), (.+)""")
    // vow chunk 04: "X is the number of creatures you control." after the sentences that use X
    val X_IS = Regex("""X is (.+)\.""")
    // vow chunk 04: "Your maximum hand size is reduced by three for the rest of the game."
    val MAXIMUM_HAND_SIZE_CHANGED_REST_OF_GAME = Regex("""Your maximum hand size is (increased|reduced) by (\w+) for the rest of the game\.""")
    // vow chunk 05: an Aura's "When enchanted creature dies, return this card to the battlefield transformed under your control attached to target opponent."
    val RETURN_THIS_CARD_TO_BATTLEFIELD = Regex("""[Rr]eturn this card to the battlefield( transformed)?(?: under (your|its owner's) control)?(?: attached to target (player|opponent))?\.""")
}
