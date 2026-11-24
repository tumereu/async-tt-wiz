package arcanum.parser

/** Regular expressions of Innistrad: Midnight Hunt (mid) wordings read in [Sentences]. */
internal object Mid {
    // mid chunk 01: "Remove X loyalty counters from among planeswalkers you control" (in a keyword's cost)
    val REMOVE_X_COUNTERS_FROM_AMONG = Regex("""Remove X (\S+) counters from among (.+)""")
    // mid chunk 01: "Tap up to one target creature you don't control, and that creature doesn't untap during its controller's next untap step."
    val AND_DOESNT_UNTAP_NEXT = Regex("""(.+?), and (?:that creature|it) doesn't untap during its controller's next untap step\.""")
    // mid chunk 01: "Light Up the Night deals X damage to any target. It deals X plus 1 damage instead if that target is a creature or planeswalker."
    val DEALS_TO_ANY_INSTEAD_IF_THAT_TARGET_IS = Regex("""(.+?) deals (\d+|X) damage to any target\. It deals (\d+|X plus \d+) damage instead if that target is an? (.+)\.""")
    // mid chunk 01: Siphon Insight
    val LOOK_OPPONENT_EXILE_ONE_FACE_DOWN_OTHER_BOTTOM_MAY_PLAY = Regex("""Look at the top two cards of target opponent's library\. Exile one of them face down and put the other on the bottom of that library\. You may play the exiled card for as long as it remains exiled, and you may spend mana as though it were mana of any color to cast that spell\.""")
    // mid chunk 01: Wake to Slaughter, and the sentences after it
    val CHOOSE_UP_TO_TWO_OPPONENT_CHOOSES_HAND_OTHER_BATTLEFIELD = Regex("""Choose up to two target (.+?) in your graveyard\. An opponent chooses one of them\. Return that card to your hand\. Return the other to the battlefield under your control\.(?: (.+))?""")
}
