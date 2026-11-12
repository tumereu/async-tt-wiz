package arcanum.parser

/** Wordings first met in Outlaws of Thunder Junction (OTJ). */
internal object Otj {
    // otj chunk 01: "It becomes plotted." (a card in exile, CR 702.170c)
    val BECOMES_PLOTTED = Regex("""(.+?) becomes plotted\.""")
    // otj chunk 01: Make Your Own Luck: the card exiled from among the looked-at ones, then the rest into the hand
    val LOOK_TOP_MAY_EXILE_ONE_IF_YOU_DO_REST_INTO_HAND = Regex(
        """Look at the top (\w+) cards of your library\. You may exile (an? .+? card) from among them\. If you do, (.+?\.) Put the rest into your hand\.""")
    // otj chunk 01: Outlaw Stitcher
    val CREATE_THEN_COUNTERS_ON_TOKEN_FOR_EACH = Regex("""Create (an? .+? token), then put (a|an|one|two|three) (\S+) counters? on that token for each (.+)\.""")
    // otj chunk 01: Pitiless Carnage: an effect, not a cost; "that many" are those sacrificed (CR 701.21a)
    val SACRIFICE_ANY_NUMBER_DRAW_THAT_MANY = Regex("""Sacrifice any number of (.+?), then draw that many cards\.""")
    // otj chunk 01: Sheriff of Safe Passage
    val ENTERS_WITH_COUNTER_PLUS_ADDITIONAL_FOR_EACH = Regex(
        """(.+?) enters with (a|an|one|two) (\S+) counters? on it plus an additional (\S+) counter on it for each (.+)\.""")
}
